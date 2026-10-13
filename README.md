# SIABUC Helpdesk 1.3.0

Sistema de atención técnica con servidor **Spring Boot 3.5.16 / Java 17**, cliente **Angular 21.2.25** y base de datos **PostgreSQL 17**. Conserva instituciones, usuarios, casos, pagos, actualizaciones descriptivas y auditoría. El tiempo por correo y por escritorio remoto se administra en **dos bolsas independientes de minutos enteros**.

Para configurar Gmail institucional `@ucol.mx` como salida de Postfix, use `bash configurar-gmail.sh`. Consulte [Gmail UCOL](docs/GMAIL-UCOL.md) para crear la contraseña de aplicación y verificar la entrega.

**Windows:** incluye scripts `.bat` para Docker Desktop con contenedores Linux. Consulte [Instalación en Windows](docs/WINDOWS.md). No necesita instalar Java, Maven ni Node en Windows.

Consulte las [notas de versión 1.3 y actualización](docs/VERSION-1.3.md).

## Funciones incluidas

- Alta, edición y baja lógica de instituciones; contacto, notas y presupuestos separados para correo y remoto.
- Tiempo asignado, utilizado y restante, presentado en horas y minutos; todo cálculo persistente usa minutos.
- Usuarios con contraseña y roles `ADMIN` / `USER` / `BILLING` (Cobranza), vinculados a su institución cuando corresponde.
- Casos abiertos/cerrados, descripción inicial y cronología de atenciones, comentarios, cambios de estado y anulaciones.
- Registro administrativo de consumos, validación de saldo y rechazo transaccional de operaciones que lo excedan.
- Comentarios públicos e internos: los internos sólo son visibles para administradores.
- Portal del usuario para consultar saldo e historial de su institución, abrir casos y agregar comentarios públicos.
- Auditoría administrativa de cambios; anular una atención reintegra sus minutos sin borrar su historia.
- Inicio de sesión con cookies de sesión, protección CSRF y contraseñas BCrypt; cambio de contraseña desde la cuenta.
- Inicio de PostgreSQL, Postfix, Spring y Angular con Docker Compose, puertos configurables, respaldo y restauración.
- Asignación de un administrador responsable por institución y caso; correo de contacto por cuenta.
- Registro de pagos y acreditación atómica de minutos, con referencias únicas y protección ante reintentos duplicados.
- Avisos SMTP al cliente y responsable por cambios de casos, incluido el cierre; avisos de pagos y anulaciones, cola persistente y reintentos.
- Servidor SMTP Postfix interno configurado por defecto, cola persistente, entrega directa o relay saliente configurable. Sin buzones ni bandeja web.

**Alcance:** registra la atención por correo y las sesiones remotas y envía avisos cuando se configura SMTP. No lee correos entrantes ni abre conexiones de escritorio remoto. Los minutos se registran manualmente después de la atención. Los pagos registran dinero recibido y acreditan minutos: no procesan cargos bancarios ni emiten facturas fiscales. No incluye adjuntos ni una instalación dentro de SIABUC: es una aplicación independiente.

Si ya instaló 1.0/1.1, conserve `.env`, los volúmenes y las migraciones existentes. Consulte [Migración de datos](docs/MIGRACION-1.1.md) y [SMTP interno](docs/SMTP-INTERNO.md#actualizar-una-instalación-anterior). Un `.env` anterior conserva su conexión de correo; adoptar Postfix se hace con `./configurar.sh --smtp-interno`.

## Inicio rápido desde cero con Docker

Requisitos en Linux: Docker Engine funcionando, complemento `docker compose` con soporte de `--wait`, Python 3.10 o posterior, acceso de red a los repositorios de imágenes y dependencias. Con Docker no necesita instalar Java, Maven ni Node en el anfitrión.

1. Descomprima el proyecto y abra una terminal en la carpeta `siabuc-helpdesk`.
2. Permita la ejecución de los scripts si el descompresor no conservó los permisos:

   ```bash
   chmod +x ./*.sh
   ```

3. Genere la configuración y revísela en su editor:

   ```bash
   ./configurar.sh
   nano .env
   ```

   Se generan contraseñas aleatorias diferentes para PostgreSQL y el primer administrador. `.env` queda con permisos `600`. La contraseña de acceso está en `ADMIN_PASSWORD`, el usuario en `ADMIN_USERNAME`. No hay una contraseña predeterminada compartida.

   Si prefiere elegir la contraseña de PostgreSQL antes del primer inicio, ejecute:

   ```bash
   ./configurar.sh --password-bd
   ```

   La solicita con entrada oculta y confirmación, y la guarda como `DB_PASSWORD` en `.env`. Este comando sólo configura el archivo; no cambia la contraseña de una base ya creada.

   Configure la IP local del equipo que enviará los avisos:

   ```bash
   ./configurar.sh --smtp-interno
   ```

   Por ejemplo, con `SMTP_LOCAL_IP=192.168.1.50` el remitente será `soporte@[192.168.1.50]`. Sustituya el ejemplo por la IP real de su servidor en la red local. Los corchetes forman parte de la dirección.

4. Compile e inicie los servicios:

   ```bash
   ./iniciar.sh
   ```

5. Abra **http://127.0.0.1:4200** o el puerto `FRONTEND_PORT` que haya configurado. Acceda con la cuenta indicada en `.env`, cambie su contraseña en **Mi cuenta** y comience creando una institución y sus usuarios.

El primer inicio descarga dependencias y puede tardar varios minutos. El script espera a que PostgreSQL, Postfix, Spring y Nginx respondan correctamente. Si algún puerto está ocupado, cambie su valor en `.env` y vuelva a iniciar.

Si omite `configurar.sh`, `iniciar.sh` genera `.env` automáticamente. Nunca publique ni comparta ese archivo. Los scripts resuelven su propia carpeta y pueden invocarse desde cualquier directorio.

## Puertos y configuración

Edite `.env` **antes del primer inicio**. Puede usar, por ejemplo, `DB_PORT=5433`, `SERVER_PORT=8081` y `FRONTEND_PORT=4201` si ya tiene aplicaciones en los valores predeterminados.

| Variable | Predeterminado | Significado |
| --- | --- | --- |
| `DB_PORT` | `5432` | Puerto de PostgreSQL publicado en el anfitrión. |
| `SERVER_PORT` | `8080` | Puerto de Spring publicado en el anfitrión. |
| `FRONTEND_PORT` | `4200` | Puerto de la aplicación web en el anfitrión. |
| `BIND_HOST` | `127.0.0.1` | Dirección de publicación de los tres servicios; `0.0.0.0` permite acceso por red. |
| `DB_NAME` | `siabuc_helpdesk` | Nombre de base de datos inicial. |
| `DB_USER` | `siabuc` | Cuenta PostgreSQL creada en el primer arranque. |
| `DB_PASSWORD` | Aleatoria | Contraseña de esa cuenta. |
| `SMTP_INTERNAL_PORT` | `2525` | Puerto de Postfix, publicado siempre sólo en localhost. |
| `SMTP_LOCAL_IP` | `127.0.0.1` | IPv4 del anfitrión para el remitente literal y la identificación SMTP. Configure su IP LAN mediante el asistente. |
| `ADMIN_USERNAME` | `admin` | Nombre del administrador de arranque. |
| `ADMIN_PASSWORD` | Aleatoria | Contraseña utilizada sólo al crear el administrador inicial. |
| `COOKIE_SECURE` | `false` | `true` cuando el navegador accede por HTTPS. |
| `COMPOSE_PROJECT_NAME` | `siabuc-helpdesk` | Identidad del proyecto y del volumen de Docker. |

Los puertos deben ser diferentes, enteros entre 1 y 65535. Dentro de Docker, PostgreSQL mantiene `5432`, Spring `8080` y Nginx `80`; los cambios de `.env` afectan los puertos del anfitrión y se aplican automáticamente al proxy en desarrollo. El navegador siempre accede a `/api` desde el mismo origen de Angular.

El formato admite `VARIABLE=valor` sencillo o `VARIABLE='valor literal'` para espacios y símbolos como `$` o `#`; no admite comilla simple dentro del valor, controles ni barra inversa final. Los scripts **no ejecutan `.env`** ni expanden su contenido. El asistente PostgreSQL exige al menos 12 caracteres y admite letras, números y `. _ / : @ % + = , - !`. Use `./configurar.sh --password-bd` para elegirla. `DB_NAME` y `DB_USER` admiten minúsculas, números y guion bajo, comenzando por letra o guion bajo. Las opciones SMTP y sus límites se explican en [Correo](docs/CORREO.md).

Cambiar `ADMIN_PASSWORD` en `.env` **no cambia la contraseña de una cuenta existente**. Use **Mi cuenta** o la administración de usuarios. Cambiar `DB_PASSWORD` después de inicializar el volumen tampoco cambia la contraseña almacenada en PostgreSQL; consulte [Operación](docs/OPERACION.md).

## Correo, responsables y pagos

Una instalación nueva conecta Spring al servidor SMTP interno **Postfix** en `smtp:25`, con envío habilitado. Postfix se inicia automáticamente y conserva su cola en un volumen. El remitente usa la IP local entre corchetes: `soporte@[192.168.1.50]`, por ejemplo. El valor inicial `soporte@[127.0.0.1]` permite arrancar localmente; configure la IP LAN real del anfitrión y, si lo necesita, un relay autorizado:

```bash
./configurar.sh --smtp-interno
./iniciar.sh
```

El asistente solicita de forma oculta el secreto de un relay si corresponde; no envía un mensaje al guardar. Para conectar Spring directamente a un SMTP externo, use `./configurar.sh --correo`. Cuando active `MAIL_ENABLED`, el servidor procesará también las notificaciones pendientes. Capture el correo de cada administrador en **Usuarios**, el correo cliente y responsable en **Instituciones** y revise la asignación de cada caso.

La dirección con IP es una identidad de envío; este servicio no crea un buzón para recibir respuestas o rebotes. Puede configurar `MAIL_REPLY_TO` con el correo de atención real. El servidor receptor debe aceptar remitentes con IP local; los proveedores externos pueden rechazarlos. Consulte la configuración del relay institucional si los avisos deben salir de la red local.

**Notificaciones** permite al administrador consultar la cola, enviar una prueba a su propia dirección y reintentar avisos fallidos u omitidos. Las notas internas sólo notifican al responsable. Consulte [SMTP interno](docs/SMTP-INTERNO.md) para entrega directa, restricciones del puerto 25, relay, cola y red; [Correo](docs/CORREO.md) detalla destinatarios y proveedores. En el helpdesk, SENT significa que Postfix aceptó el aviso; la entrega remota puede continuar pendiente en su cola.

**Cobranza** permite a `ADMIN` y `BILLING` registrar institución, referencia, fecha, importe, moneda, método, descripción y minutos adquiridos para cada canal. El pago y la acreditación se guardan juntos. Sólo un administrador puede anular el pago y retirar los minutos, siempre que ambos canales conserven suficiente saldo disponible. El historial financiero no se borra.

## Detener, reiniciar y recompilar

```bash
./detener.sh
./iniciar.sh
```

`detener.sh` retira los contenedores y conserva el volumen con todos los datos. `iniciar.sh` reconstruye las imágenes cuando cambia el código. **No utilice `docker compose down -v` si desea conservar la base de datos.**

Para compilar imágenes sin iniciar los servicios:

```bash
./compilar.sh --docker
```

Para consultar estado y registros, ejecute desde la carpeta del proyecto:

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
docker compose logs --tail=100 db
```

## Compilar y ejecutar con Java y Angular locales

Requiere JDK 17 o posterior, Maven 3.9, Node.js 24 LTS, npm, Python 3.10 y Docker para PostgreSQL y Postfix.

```bash
./configurar.sh
./compilar.sh
./iniciar-dev.sh
```

`compilar.sh` ejecuta `mvn package`, `npm ci` y `npm run build`. Produce:

- `backend/target/siabuc-helpdesk-1.3.0.jar`
- `frontend/dist/helpdesk/browser/`

`iniciar-dev.sh` inicia PostgreSQL y Postfix con Docker, compila Spring, instala las dependencias de Angular y ejecuta Spring y `ng serve` en una terminal. Lee los mismos puertos y contraseñas de `.env`; ambos procesos escuchan en `127.0.0.1`. Los registros se guardan en `.runtime/`. **Ctrl+C** detiene Spring y Angular; después `./detener.sh` detiene PostgreSQL y Postfix. No ejecute el modo Docker completo y el modo de desarrollo al mismo tiempo sobre los mismos puertos.

Para ejecutar todo sin Docker, conectar un PostgreSQL propio o servir los archivos compilados, consulte las instrucciones completas en [Operación](docs/OPERACION.md).

## Flujo inicial recomendado

1. **Instituciones:** registre nombre y contacto; asigne por separado, por ejemplo, 10 h de correo y 2 h de remoto.
2. **Usuarios:** cree un usuario `USER` asociado a esa institución. Entregue su contraseña inicial por el procedimiento interno de su organización.
3. **Casos:** abra un caso por correo o remoto, con título y descripción del problema.
4. **Atención:** registre descripción, canal y minutos utilizados. El canal de cada atención determina qué bolsa se descuenta.
5. **Seguimiento:** consulte la cronología, agregue comentarios o notas internas y cierre el caso al resolverlo.
6. **Corrección:** si una atención se capturó mal, anúlela indicando la razón y registre la correcta. La anulación conserva la evidencia y devuelve el saldo.

Una hora equivale exactamente a 60 minutos. El sistema rechaza una atención de 31 minutos si la institución sólo dispone de 30 en ese canal. No toma prestados minutos de la otra bolsa. Crear un caso no descuenta tiempo; se requiere saldo positivo para crearlo o reabrirlo. Los comentarios de usuarios no consumen saldo.

## Respaldo y restauración

La [guía paso a paso de respaldo y restauración](docs/RESPALDO-RESTAURACION.md) explica los parámetros, las rutas, la recuperación y el diagnóstico de errores.

Con PostgreSQL en ejecución:

```bash
./respaldar.sh
```

Se genera un archivo `backups/siabuc-FECHA.dump` y su verificación SHA-256. Copie ambos a una ubicación segura distinta del servidor. El respaldo contiene los datos del sistema y los hashes de contraseña; no incluye `.env` ni las imágenes Docker.

Para elegir el nombre y la ubicación del respaldo:

```bash
./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump
```

Este ejemplo crea `backups/mi-respaldo.dump` y `backups/mi-respaldo.dump.sha256`. No se sobrescribe una copia existente; use otro nombre para el siguiente respaldo. Las rutas relativas se interpretan desde la carpeta donde ejecuta el comando, y los nombres con espacios deben ir entre comillas.

Para restaurar una copia y reemplazar los datos actuales, detenga antes cualquier sesión de desarrollo y cree un respaldo del estado actual:

```bash
./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo
./iniciar.sh
```

Indique el archivo `.dump`, no su `.sha256`. También se conserva la forma anterior `./restaurar.sh backups/mi-respaldo.dump --confirmar-reemplazo` y el argumento directo para `respaldar.sh`. Use el argumento directo **o** `--nombre-respaldo`, nunca ambos. Puede consultar las opciones con `./respaldar.sh --help` y `./restaurar.sh --help`.

La restauración detiene Spring, Angular y Postfix de Docker, valida el SHA-256 si está presente y usa una transacción PostgreSQL. Tras restaurar, las cuentas y contraseñas son las de la copia recuperada. El respaldo no incluye la cola SMTP: revise ambas colas antes de reanudar los envíos. Más detalles en [Operación](docs/OPERACION.md).

## Documentación y pruebas

- [Manual de administradores y usuarios](docs/MANUAL-USUARIO.md): permisos, instituciones, minutos y casos.
- [Operación y compilación](docs/OPERACION.md): instalación nativa, Docker, red, respaldo y resolución de problemas.
- [Respaldo y restauración](docs/RESPALDO-RESTAURACION.md): `--nombre-respaldo`, ejemplos, recuperación paso a paso y alcance de las copias.
- [Arquitectura y modelo de datos](docs/ARQUITECTURA.md): integridad, concurrencia, sesiones y límites.
- [SMTP interno Postfix](docs/SMTP-INTERNO.md): configuración predeterminada, entrega directa/relay y diagnóstico de cola.
- [Correo y notificaciones](docs/CORREO.md): configuración SMTP, proveedores, cola y pruebas Mailpit.
- [Migración de 1.0 a 1.1](docs/MIGRACION-1.1.md): actualización conservando datos, cuentas y saldos.
- [Contrato de API](docs/API-CONTRACT.md): endpoints, estructuras y errores.
- [Pruebas de navegador](tests/e2e/README.md): instalación y ejecución del recorrido completo Angular → Spring → PostgreSQL en un entorno desechable.
- [Validación realizada](docs/VALIDACION.md): resultados efectivamente ejecutados y comprobaciones pendientes.

Las pruebas de integración contra PostgreSQL se ejecutan con `mvn -Ppostgres-it verify` y una **base exclusiva de pruebas, vacía al primer uso**, donde la suite crea su administrador `testadmin`; no forman parte del empaquetado normal con `mvn package`. La suite crea y modifica registros sin limpiar sus datos al finalizar: los datos de prueba persistirán. No use una base productiva ni una base inicializada con otro administrador. Consulte [Operación](docs/OPERACION.md) para preparar la base aislada. Para las pruebas de lógica del cliente, ejecute `npm test` dentro de `frontend`.

## Estructura

```text
siabuc-helpdesk/
  backend/                Spring, seguridad, API y migraciones Flyway
  frontend/               Angular, proxy de desarrollo y Nginx
  smtp/                   Dockerfile y configuración Postfix de salida
  docs/                   Manuales, contrato API y validación
  scripts/manage.py       Operación y lectura segura de configuración
  docker-compose.yml      PostgreSQL + Postfix + Spring + Nginx
  .env.example            Plantilla sin contraseñas utilizables
  configurar.sh           Generar / validar .env; --password-bd, --correo y --smtp-interno
  iniciar.sh              Compilar e iniciar con Docker
  iniciar-dev.sh          PostgreSQL/Postfix Docker + Spring/Angular locales
  detener.sh              Detener Docker conservando datos
  compilar.sh             Compilar localmente o con --docker
  respaldar.sh            Crear copia PostgreSQL
  restaurar.sh            Recuperar copia PostgreSQL
```
