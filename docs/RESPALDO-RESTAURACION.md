# Respaldo y restauración de SIABUC Helpdesk · 1.8.5

En Windows utilice `respaldar.bat` y `restaurar.bat` con los mismos argumentos; consulte [WINDOWS.md](WINDOWS.md). Los scripts `respaldar.sh` y `restaurar.sh` operan sobre la base PostgreSQL del servicio Docker `db`, utilizando los valores de conexión de `.env`. La versión 1.8.5 usa PostgreSQL 17 y migraciones hasta V27, sin migraciones nuevas respecto de 1.8.4.

Desde 1.6 existe también un calendario de respaldos **desactivado por defecto**, administrable en **Respaldos**. El servicio `backup` puede permanecer iniciado sin producir copias programadas. Activarlo requiere configurar y confirmar el destino; desactivarlo conserva copias e historial y no impide respaldar manualmente. La programación, retención y prueba de restauración en un proyecto aislado se documentan en [RESPALDOS-AUTOMATICOS.md](RESPALDOS-AUTOMATICOS.md). Los comandos de esta guía siguen siendo respaldos **manuales**, fuera de la limpieza automática.

Desde 1.5 las pólizas, su historial, los vínculos de pagos y el contenido binario de sus comprobantes también se incluyen automáticamente en la copia PostgreSQL. Restaurar una copia anterior pierde las operaciones posteriores a su fecha.

## 1. Preparar el entorno

Desde 1.4 los archivos adjuntos se almacenan dentro de PostgreSQL: una copia nueva incluye su contenido, permisos, comentarios asociados y metadatos. También incluye avisos internos, calendario SLA, umbrales, cronómetros y agenda. No debe copiar una carpeta externa de adjuntos. El tamaño de los respaldos crecerá con la evidencia guardada. Una copia anterior a 1.4 no contiene estas funciones ni datos posteriores a su fecha.

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

Se crean los directorios de destino que falten y ambos archivos quedan con permisos `600` en Linux. En Windows proteja la carpeta con una ACL privada; `chmod` no establece una ACL equivalente. El respaldo utiliza una instantánea de PostgreSQL; no necesita detener la aplicación. Si el archivo de respaldo ya existe, la operación se rechaza: elija un nombre nuevo. Si falla `pg_dump`, el script elimina el archivo incompleto que acaba de crear.

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

Este procedimiento sobre la instalación actual requiere un esquema compatible con la copia. Para recuperar una versión anterior sobre una instalación ya migrada, siga [Retroceder a una versión anterior](#retroceder-a-una-versión-anterior) y use una base nueva y vacía.

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

4. Espere el mensaje de restauración terminada. Una copia con V16 recupera también el estado guardado del calendario: podría estar activado. Para revisar la instalación antes de reanudar correo y respaldos, establezca temporalmente `MAIL_ENABLED=false` en el `.env` del destino e inicie sólo base, backend y frontend; mantenga Postfix y `backup` detenidos:

   ```bash
   docker compose up -d --build --wait db backend frontend
   ```

5. Acceda con una cuenta administrativa incluida en la copia, abra **Respaldos**, desactive el calendario si corresponde y compruebe destino y retención. Revise también la pausa y cola de **Correo y notificaciones** y, si utiliza Postfix, su cola conservada. Use `./iniciar.sh` o `iniciar.bat` cuando esté listo para iniciar el worker y los demás servicios seleccionados. Restablezca `MAIL_ENABLED=true` sólo si quiere reanudar el envío y recree el backend para aplicarlo. Compruebe instituciones, usuarios, saldos, pagos y casos. Los cambios posteriores a la fecha del respaldo no estarán en la base recuperada.

### Qué comprueba y ejecuta el script

Primero exige el nombre del archivo y `--confirmar-reemplazo`, comprueba su existencia y verifica que no haya una sesión de desarrollo marcada como activa. Si está presente el archivo complementario `.sha256`, valida su formato y compara la huella. Un archivo vacío, mal formado o con una huella distinta impide continuar antes de detener los servicios.

Si no existe el `.sha256`, la restauración puede continuar sin esa comprobación; conservarlo junto al respaldo permite detectar alteraciones. La huella comprueba integridad, no la identidad ni la confianza de quien proporcionó la copia.

Después inicia PostgreSQL si es necesario y detiene los contenedores `backup`, `frontend`, `backend` y `smtp`. Ejecuta `pg_restore` con `--clean`, `--if-exists`, `--single-transaction` y `--exit-on-error`, omitiendo propietarios y privilegios originales. Si una sentencia de restauración falla, PostgreSQL revierte esa transacción. Los servicios de respaldos automáticos, aplicación y correo quedan detenidos tanto al terminar correctamente como si falla `pg_restore`; no se reinician automáticamente.

## 4. Alcance de la copia

| Incluido en PostgreSQL | No incluido |
| --- | --- |
| Instituciones, usuarios y hashes de sus contraseñas | Archivo `.env` y credenciales de conexión PostgreSQL/SMTP |
| Saldos, casos con sus cinco estados e historial de atenciones | Código fuente e imágenes Docker |
| Pagos, anulaciones, responsables y auditoría | Sesiones de acceso en memoria |
| Cola de notificaciones de Spring y migraciones | Cola Postfix del volumen `smtp_queue` |
| Configuración e historial de respaldos programados | Archivos `.dump` y `.sha256` externos del destino de copias |
| Adjuntos BYTEA, pólizas, cotizaciones, comprobantes, recibos, conocimiento y módulos financieros | Configuración del sistema operativo, certificados y permisos del destino |

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
| Falla `pg_restore` | Revise su salida y la compatibilidad de PostgreSQL/código. Respaldos automáticos, Spring, Angular y Postfix quedan detenidos; corrija la causa antes de reanudar. |

## 6. Validación disponible

Como evidencia histórica anterior a 1.6, la suite Python de aquella revisión obtuvo **37 pruebas satisfactorias**, incluidas 12 de respaldo/restauración. Comprobó argumentos, archivos, permisos, SHA-256, protección de copias existentes, fallos y parámetros transaccionales con una llamada a Compose simulada. También se comprobó la ayuda de los scripts y el rechazo de argumentos inválidos desde otro directorio. Estos resultados no son conteos de 1.8.5. Consulte la evidencia por versión en [VALIDACION.md](VALIDACION.md) y los procedimientos vigentes en [RESPALDOS-AUTOMATICOS.md](RESPALDOS-AUTOMATICOS.md).

En [CI de 1.8.5](VERSION-1.8.5.md#evidencia-ci-de-las-correcciones) se ejecutaron `pg_dump` y `pg_restore` reales con registros sintéticos, PostgreSQL 17 e imágenes finales en una red y volúmenes desechables. Ese ensayo no restaura una copia productiva ni valida automáticamente su archivo de respaldo; debe probarse por separado en el destino aislado. Los resultados de las suites simuladas anteriores se conservan en [VALIDACION.md](VALIDACION.md). Para otras tareas de instalación y operación, consulte [OPERACION.md](OPERACION.md).

## Pausa de correo desde 1.3.1

Los respaldos con V4 incluyen notification_settings y su pausa administrativa. Revise ese valor y MAIL_ENABLED antes de reanudar. Para restaurar un respaldo anterior a V4 use una base nueva: el borrado selectivo de pg_restore no elimina objetos que el archivo antiguo no conoce. Consulte [Desactivar correo](DESACTIVAR-CORREO.md).

## Retroceder a una versión anterior

`pg_restore --clean` borra únicamente los objetos incluidos en el respaldo. Por ejemplo, una copia anterior a 1.5 no conoce las tablas V9/V10; una anterior a 1.6 tampoco conoce V11–V16. Sus relaciones pueden impedir restaurar esa copia sobre la base actual. Además, el código anterior a V27 no respeta el saldo reservado para licencias y el anterior a 1.8.3 no interpreta los estados de V26. El script no realiza una migración inversa ni vacía completamente el esquema: use una base nueva con el respaldo y código compatibles.

1. Detenga los servicios de la instalación actual y conserve su volumen, un respaldo reciente y su `.env`. Manténgalos disponibles para volver a la versión actual.
2. Descomprima el código de la versión que desea recuperar en **otra carpeta**. Configure un `.env` propio con un `COMPOSE_PROJECT_NAME` nuevo y puertos libres para evitar reutilizar volúmenes o entrar en conflicto con la instalación conservada.
3. Inicie **únicamente PostgreSQL** en esa nueva instalación: `./iniciar.sh --solo-bd` en Linux o `iniciar.bat --solo-bd` en Windows. Compruebe que corresponde al proyecto nuevo. No arranque Spring todavía: la base debe estar vacía de tablas de la aplicación.
4. Copie el `.dump` anterior y su `.sha256` a esa carpeta. Restaure con `./restaurar.sh --nombre-respaldo backups/antes-de-actualizar.dump --confirmar-reemplazo` o su equivalente `restaurar.bat`. Use el nombre real de su archivo.
5. Compruebe la restauración y la configuración de correo antes de iniciar el código anterior con `./iniciar.sh` o `iniciar.bat`. Ese código debe conectarse exclusivamente a la base recuperada, nunca a la base migrada que conserva para revisión.

Las operaciones posteriores al respaldo no estarán en la base recuperada. Este procedimiento conserva el entorno migrado para revisión; no utilice `docker compose down -v` para hacer el retroceso. La ejecución real de esta recuperación Docker sigue pendiente de validación en el entorno destino.
