# Respaldo y restauración de SIABUC Helpdesk

Guía para la versión 1.3.2. En Windows utilice `respaldar.bat` y `restaurar.bat` con los mismos argumentos; consulte [WINDOWS.md](WINDOWS.md). Los scripts `respaldar.sh` y `restaurar.sh` operan sobre la base PostgreSQL del servicio Docker `db`, utilizando los valores de conexión de `.env`.

## 1. Preparar el entorno

Abra una terminal en la carpeta `siabuc-helpdesk`. Necesita Python 3.10 o posterior, Docker funcionando, Docker Compose y un `.env` válido de esta instalación. Los scripts no crean ni cambian credenciales durante el respaldo o la restauración.

```bash
docker info
docker compose version
./respaldar.sh --help
./restaurar.sh --help
```

Para crear un respaldo, PostgreSQL debe estar iniciado. Si está detenido, puede iniciar únicamente la base:

```bash
./iniciar.sh --solo-bd
```

Este comando no detiene otros servicios que ya estén funcionando.

## 2. Crear un respaldo con nombre elegido

```bash
./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump
```

Se crean dos archivos:

- `backups/mi-respaldo.dump`: copia de la base en formato PostgreSQL personalizado.
- `backups/mi-respaldo.dump.sha256`: verificación de integridad de esa copia.

Se crean los directorios de destino que falten y ambos archivos quedan con permisos `600`. El respaldo utiliza una instantánea de PostgreSQL; no necesita detener la aplicación. Si el archivo de respaldo ya existe, la operación se rechaza: elija un nombre nuevo. Si falla `pg_dump`, el script elimina el archivo incompleto que acaba de crear.

Copie ambos archivos a una ubicación segura fuera del equipo. El script no cifra los respaldos.

### Nombre automático

```bash
./respaldar.sh
```

Sin parámetro, se guarda en la carpeta `backups` del proyecto con un nombre `siabuc-FECHA.dump`. La marca de tiempo utiliza UTC e incluye fracciones de segundo.

### Rutas y nombres con espacios

```bash
./respaldar.sh --nombre-respaldo 'backups/respaldo soporte octubre.dump'
```

También admite una ruta absoluta, por ejemplo `/ruta/segura/mi-respaldo.dump`. Sustituya esa ruta por una carpeta real para la que tenga permisos de escritura.

| Regla | Comportamiento |
| --- | --- |
| Nombre simple, como `mi-respaldo.dump` | Se interpreta desde la carpeta donde ejecuta el comando. |
| Ruta relativa, como `backups/mi-respaldo.dump` | También parte de la carpeta de ejecución, incluso al invocar el script desde otro directorio. |
| Sin nombre al respaldar | Usa `backups` dentro del proyecto y genera la fecha automáticamente. |
| Extensión | No se añade automáticamente; se recomienda escribir `.dump`. |
| Espacios | Encierre el nombre o ruta entre comillas. |
| Nombre vacío o sólo espacios | Se rechaza. |
| Nombre como argumento directo | Se conserva: `./respaldar.sh backups/mi-respaldo.dump`. |
| Argumento directo y `--nombre-respaldo` juntos | Se rechazan; elija una sola forma. |

## 3. Restaurar una copia

**La restauración sustituye los datos del sistema por los del respaldo.** Las cuentas, sus contraseñas, los saldos y los historiales volverán al estado guardado en esa copia. La contraseña de conexión de PostgreSQL continúa siendo la del servidor destino; no se recupera desde el archivo `.dump`.

1. Programe la interrupción del servicio. Detenga cualquier Spring o Angular iniciado de forma nativa. Si utilizó `iniciar-dev.sh`, cierre esa sesión con `Ctrl+C`; el script de restauración rechaza una sesión marcada como activa.
2. Conserve una copia del estado actual antes de reemplazarlo, usando un nombre diferente:

   ```bash
   ./respaldar.sh --nombre-respaldo backups/antes-de-restaurar.dump
   ```

3. Indique el respaldo que desea recuperar y confirme expresamente el reemplazo:

   ```bash
   ./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo
   ```

   Señale el archivo `.dump`, **no** el `.sha256`. Para rutas con espacios, use comillas. También se admite la forma anterior:

   ```bash
   ./restaurar.sh backups/mi-respaldo.dump --confirmar-reemplazo
   ```

4. Espere el mensaje de restauración terminada. Revise la configuración y las colas de correo antes de reanudar los envíos. Luego inicie los servicios:

   ```bash
   ./iniciar.sh
   ```

5. Acceda con una cuenta incluida en la copia y compruebe instituciones, usuarios, saldos, pagos y casos. Los cambios posteriores a la fecha del respaldo no estarán en la base recuperada.

### Qué comprueba y ejecuta el script

Primero exige el nombre del archivo y `--confirmar-reemplazo`, comprueba su existencia y verifica que no haya una sesión de desarrollo marcada como activa. Si está presente el archivo complementario `.sha256`, valida su formato y compara la huella. Un archivo vacío, mal formado o con una huella distinta impide continuar antes de detener los servicios.

Si no existe el `.sha256`, la restauración puede continuar sin esa comprobación; conservarlo junto al respaldo permite detectar alteraciones. La huella comprueba integridad, no la identidad ni la confianza de quien proporcionó la copia.

Después inicia PostgreSQL si es necesario y detiene los contenedores `frontend`, `backend` y `smtp`. Ejecuta `pg_restore` con `--clean`, `--if-exists`, `--single-transaction` y `--exit-on-error`, omitiendo propietarios y privilegios originales. Si una sentencia de restauración falla, PostgreSQL revierte esa transacción. Los servicios de aplicación y correo quedan detenidos tanto al terminar correctamente como si falla `pg_restore`; no se reinician automáticamente.

## 4. Alcance de la copia

| Incluido en PostgreSQL | No incluido |
| --- | --- |
| Instituciones, usuarios y hashes de sus contraseñas | Archivo `.env` y credenciales de conexión PostgreSQL/SMTP |
| Saldos, casos e historial de atenciones | Código fuente e imágenes Docker |
| Pagos, anulaciones, responsables y auditoría | Sesiones de acceso en memoria |
| Cola de notificaciones de Spring y migraciones | Cola Postfix del volumen `smtp_queue` |

Preserve `.env` por separado con acceso restringido y mantenga código y base de datos de versiones compatibles. Esta entrega utiliza PostgreSQL 17. Para recuperar en otro equipo, prepare Docker, el proyecto y su configuración de conexión antes de ejecutar `restaurar.sh`.

La cola PostgreSQL puede contener avisos pendientes que vuelvan a enviarse al iniciar Spring. `MAIL_ENABLED=false` pausa únicamente el envío de la aplicación; **no pausa una cola que Postfix ya haya aceptado**. Revise también el volumen SMTP antes de reiniciar Postfix. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md#cola-y-diagnóstico) para administrar esa segunda cola.

## 5. Problemas habituales

| Mensaje o situación | Qué hacer |
| --- | --- |
| El destino ya existe | Elija otro nombre; no se sobrescribe el respaldo existente. |
| Falta nombre del respaldo | Indique `--nombre-respaldo ARCHIVO` o el argumento directo. |
| Falta `--confirmar-reemplazo` | Compruebe la copia elegida y añada la opción sólo cuando quiera sustituir los datos actuales. |
| No existe el archivo indicado | Revise la ruta y la carpeta desde la que ejecutó el comando; puede utilizar una ruta absoluta. |
| Error SHA-256 | Recupere una copia íntegra y su archivo de verificación; no elimine la verificación para ignorar el error. |
| Docker no responde | Revise `docker info`, el servicio Docker y los permisos de su usuario. |
| Falla el respaldo porque `db` está detenido | Ejecute `./iniciar.sh --solo-bd` y repita con un nombre nuevo si corresponde. |
| Falla `pg_restore` | Revise su salida y la compatibilidad de PostgreSQL/código. Spring, Angular y Postfix quedan detenidos; corrija la causa antes de reanudar. |

## 6. Validación disponible

La suite Python de esta revisión tiene **37 pruebas satisfactorias**, incluidas 12 de respaldo/restauración. Comprueba argumentos, archivos, permisos, SHA-256, protección de copias existentes, fallos y parámetros transaccionales con una llamada a Compose simulada. También se comprobó la ayuda de los scripts y el rechazo de argumentos inválidos desde otro directorio.

**No se ejecutó una restauración real de PostgreSQL en Docker en el entorno de preparación.** Los detalles y resultados históricos están en [VALIDACION.md](VALIDACION.md). Para otras tareas de instalación y operación, consulte [OPERACION.md](OPERACION.md).

## Pausa de correo desde 1.3.1

Los respaldos con V4 incluyen notification_settings y su pausa administrativa. Revise ese valor y MAIL_ENABLED antes de reanudar. Para restaurar un respaldo anterior a V4 use una base nueva: el borrado selectivo de pg_restore no elimina objetos que el archivo antiguo no conoce. Consulte [Desactivar correo](DESACTIVAR-CORREO.md).
