# Respaldos automáticos · 1.8.3

Desde 1.6.0, SIABUC Helpdesk incluye un servicio Docker llamado **`backup`** y una sección **Respaldos**, exclusiva de **ADMIN**. El calendario comienza **desactivado** en una instalación nueva o al aplicar V16 por primera vez. Una actualización posterior, incluida 1.8.3, conserva el estado guardado; un respaldo también lo recupera al restaurar. Iniciar Docker o abrir la página no cambia ese estado.

El administrativo puede activar y desactivar el calendario. **Desactivarlo conserva las copias y el historial**, cancela los programados todavía pendientes y mantiene disponible **Crear respaldo ahora**. Una copia que ya comenzó puede terminar; no se trunca su archivo. Al reactivar se espera el siguiente horario futuro, sin recuperar horarios perdidos durante la pausa.

## 1. Elegir el destino

Conserva tu `.env` existente y añade o ajusta:

```dotenv
BACKUP_DIRECTORY=./backups/automatic
BACKUP_UID=0
BACKUP_GID=0
```

`BACKUP_DIRECTORY` es una carpeta privada del equipo, montada únicamente en el contenedor de respaldos. Una ruta relativa se resuelve desde el proyecto. Puedes elegir una ruta absoluta; por ejemplo:

```dotenv
# Linux:
BACKUP_DIRECTORY=/srv/siabuc/copias
# Windows, ejemplo alternativo; conserva sólo una definición:
# BACKUP_DIRECTORY='C:/SIABUC/copias automaticas'
```

Usa una carpeta exclusiva para estas copias. Evita la raíz del disco, la del proyecto o una carpeta pública. Las copias contienen datos de clientes, historial, cuentas y comprobantes. No incluyas `.env` ni copias reales en el repositorio.

Al generar una configuración nueva, los scripts eligen el UID/GID del usuario actual en Linux; en Windows usan `0`. Si copias `.env.example`, puedes ajustar `BACKUP_UID` y `BACKUP_GID` al dueño de la carpeta. El proceso con UID `0` dispone sólo de la capacidad de acceso a archivos necesaria para escribir en ese destino privado; no tiene acceso al socket Docker ni al volumen de PostgreSQL. Si eliges otro UID/GID, prepara los permisos del directorio para ese usuario. No se cambian automáticamente los permisos de carpetas preexistentes. En Windows se aplican también los permisos y el acceso a archivos compartidos de Docker Desktop.

## 2. Iniciar el servicio

Desde el proyecto:

```bash
# Linux
bash iniciar.sh
```

```bat
rem Windows, Docker Desktop en modo contenedores Linux
iniciar.bat
```

Los scripts crean el directorio si falta y construyen el servicio `backup`. No corrigen propietario ni permisos de una carpeta existente, ni sustituyen la comprobación de escritura del worker. El worker espera a que el backend esté saludable y haya aplicado sus migraciones. Si ejecutas `docker compose` directamente, crea primero la carpeta indicada: el montaje usa `create_host_path: false` para no crear silenciosamente una ruta equivocada.

Para revisar el servicio:

```bash
docker compose ps backup
docker compose logs --tail=100 backup
```

En Linux ante falta de permisos del daemon, utiliza `sudo` según tu instalación habitual. Con conexión a la base y destino escribible, el contenedor queda saludable incluso con el calendario desactivado: permanece esperando. El worker y PostgreSQL no publican puertos en Compose ordinario; el override de desarrollo publica sólo PostgreSQL en `127.0.0.1:DB_PORT` y SMTP si se selecciona. `backup` comparte la red `default` del proyecto.

`iniciar-dev.sh` mantiene su flujo nativo y no arranca este worker automáticamente: la programación descrita aquí corresponde a la instalación completa con Compose. Los scripts manuales `respaldar.sh` y `respaldar.bat` siguen disponibles.

## 3. Configurar y activar desde la plataforma

1. Inicia sesión como administrativo y abre **Respaldos**.
2. Comprueba que el destino mostrado sea correcto y que el servicio esté conectado y pueda escribir.
3. Configura **hora diaria**, **zona horaria IANA**, **retención en días** y **mínimo de copias automáticas**. Valores iniciales: `03:00`, `America/Mexico_City`, 30 días y 7 copias.
4. Marca la confirmación del destino privado y pulsa **Guardar configuración**. Si estaba desactivado, sigue desactivado; guardar conserva el estado actual y no es el botón de activación.
5. Opcionalmente pulsa **Crear respaldo ahora** y verifica que llegue a **Completado**.
6. Para programar los siguientes, pulsa **Activar respaldos automáticos**.

El interruptor **Desactivar respaldos automáticos** funciona independientemente de los campos que estés editando: no exige corregir un horario nuevo ni pierde la confirmación previamente guardada del destino. El respaldo manual requiere destino confirmado y servicio disponible, pero no requiere calendario activado.

El calendario usa la zona configurada, no la hora del navegador. Se registra como máximo una ejecución programada por fecha local. Si el servicio estaba apagado y vuelve el mismo día después de la hora prevista, puede ejecutar la fecha actual cuando el calendario llevaba activado desde antes de esa hora. No recupera fechas anteriores. Activar o modificar el calendario después de la hora prevista espera el siguiente horario futuro. En un cambio de horario estacional, una hora repetida se atiende una vez y una hora inexistente se desplaza hacia delante según la zona.

## Qué contiene y cómo se crea cada respaldo

El worker usa **`pg_dump` de PostgreSQL 17**, formato **custom**, sin propietarios ni privilegios. Incluye las tablas de PostgreSQL: casos, instituciones, usuarios, pagos, políticas, bitácoras, configuraciones y adjuntos almacenados como bytes en la base de datos.

No incluye `.env`, certificados externos, archivos del equipo ajenos a PostgreSQL, imágenes Docker ni la cola de Postfix. Conserva por separado la configuración necesaria para reconstruir la instalación.

La copia se escribe con un nombre temporal privado, se comprueba su estructura con `pg_restore --list`, se calcula **SHA-256** y se publica mediante renombrado atómico. Sólo después se marca como completada. El par final es:

```text
siabuc-auto-<ejecucion>-<identificador>.dump
siabuc-auto-<ejecucion>-<identificador>.dump.sha256
```

El prefijo identifica archivos generados por este worker, también cuando se solicitan manualmente desde la plataforma; el historial distingue **Manual** y **Programado**. La retención sólo toma los de tipo **Programado**. Los scripts manuales existentes conservan sus propios nombres y nunca se incluyen en esta limpieza.

Los archivos se crean con permisos privados `0600` en sistemas que admiten esos permisos. El worker mantiene una exclusión en PostgreSQL para impedir ejecuciones simultáneas. Si un proceso se interrumpe, su intento queda fallido y se reconsidera hasta el límite de tres intentos; los intentos ordinarios se espacian cinco minutos. Un programado de una fecha perdida o cancelado por desactivación no se recupera. El límite de una ejecución de `pg_dump` es una hora. No se publican contraseñas ni diagnósticos crudos de los procesos en la interfaz o los logs del worker.

Un apagado abrupto entre la publicación del archivo y el registro de éxito puede dejar un archivo sin ejecución completada. El sistema no lo presenta como copia validada ni lo elimina automáticamente. Revísalo con las herramientas de PostgreSQL antes de conservarlo o retirarlo.

## Retención y espacio disponible

La limpieza sólo se intenta **después de un respaldo programado exitoso**, si el calendario continúa activado al terminar. Crear una copia manual o desactivar el calendario no limpia archivos existentes.

Para retirar una copia deben cumplirse todas estas condiciones:

- Su ejecución está registrada como **Programada** y **Completada**.
- Su antigüedad supera la retención configurada.
- Quedan al menos las copias íntegras indicadas en **Conservar al menos** dentro del destino actual.
- El nombre, el archivo y su SHA-256 corresponden al registro; no son enlaces simbólicos.

El mínimo cuenta archivos presentes y verificados, no registros cuyos archivos se borraron o corrompieron. Las copias manuales, los archivos ajenos, los incompletos y los hashes que no coinciden se conservan para revisión. El historial de una copia retirada permanece con la fecha de retención.

Ejemplo: con 30 días y un mínimo de 7, una copia automática de 40 días sólo se retira después de otra copia automática correcta y si permanece ese mínimo de copias válidas. La retención no limita el tamaño máximo del disco: supervisa el espacio disponible, especialmente con adjuntos grandes y copias manuales.

## Historial y avisos administrativos

La página muestra las últimas 100 ejecuciones, sus estados, intentos, nombre de archivo, tamaño, hash y errores. **Ver detalle** muestra los intentos anteriores. Los cambios de configuración y solicitudes manuales quedan en la auditoría.

Un fallo o reintento pendiente aparece como alerta administrativa hasta que una ejecución posterior finaliza correctamente. También se muestra un servicio sin comunicación reciente cuando el calendario está activado. Estos avisos internos no dependen del SMTP y no envían mensajes externos. No se consideran copias correctas los estados **Pendiente**, **En ejecución**, **Reintento pendiente**, **Fallido** o **Cancelado**.

Para un error de permisos, revisa el directorio y UID/GID. Para falta de espacio, libera espacio ajeno a las copias necesarias o cambia a un destino adecuado. Para errores de conexión, confirma que `backup` y `db` pertenezcan al mismo proyecto Compose y que `DB_NAME`, `DB_USER` y `DB_PASSWORD` correspondan a la base real. Si cambias el destino, reinicia el servicio y confirma el nuevo destino en la plataforma: al detectar un cambio de ruta, el worker desactiva el calendario y requiere otra confirmación antes de usarla para copias.

## Prueba de restauración en una base aislada

Una copia con hash correcto y un catálogo legible todavía necesita una prueba de restauración para demostrar que es recuperable. La siguiente prueba utiliza `backup/compose.restore-test.yml`: **otro proyecto**, **otro volumen**, **otra base**, sin puertos publicados, backend, SMTP o worker. No utiliza el volumen de producción. Se ejecuta manualmente; no fue ejecutada en Docker durante la validación de esta entrega.

Trabaja desde el directorio del proyecto. Elige una copia finalizada y su checksum. Verifica el hash:

```bash
# Linux: compara el hash mostrado con el archivo .sha256.
sha256sum /ruta/siabuc-auto-123-identificador.dump
```

```powershell
# Windows PowerShell: compara Hash con el archivo .sha256.
Get-FileHash 'C:/SIABUC/copias/siabuc-auto-123-identificador.dump' -Algorithm SHA256
```

Crea una contraseña temporal **sólo para el ensayo**, sin cambiar `DB_PASSWORD` ni el `.env` productivo:

```bash
# Linux
export RESTORE_TEST_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
```

```powershell
# Windows PowerShell
$env:RESTORE_TEST_PASSWORD = python -c "import secrets; print(secrets.token_hex(24))"
```

Los siguientes comandos son iguales en Linux y PowerShell; reemplaza únicamente la ruta de la copia en `cp`:

```text
docker compose -p siabuc-restore-test -f backup/compose.restore-test.yml up -d --wait
docker compose -p siabuc-restore-test -f backup/compose.restore-test.yml cp "/ruta/de/la/copia.dump" db:/tmp/rehearsal.dump
docker compose -p siabuc-restore-test -f backup/compose.restore-test.yml exec -T db pg_restore -U restorecheck -d siabuc_restore_test --no-owner --no-privileges --single-transaction --exit-on-error /tmp/rehearsal.dump
docker compose -p siabuc-restore-test -f backup/compose.restore-test.yml exec -T db psql -U restorecheck -d siabuc_restore_test -v ON_ERROR_STOP=1 -c "UPDATE backup_settings SET enabled=FALSE,destination_confirmed=FALSE; UPDATE notification_settings SET paused=TRUE; SELECT COUNT(*) AS instituciones FROM institutions; SELECT COUNT(*) AS casos FROM support_cases; SELECT COUNT(*) AS pagos FROM payments;"
```

Confirma que `pg_restore` termine con código 0. Compara cantidades y muestras de instituciones, casos, pagos, minutos y archivos adjuntos contra el origen correspondiente a la fecha de la copia. La base de ensayo puede contener solicitudes de respaldo que estaban en ejecución al tomar la instantánea: no arranques el worker ni un SMTP contra ella. Si quieres repetir el ensayo, usa una base de ensayo nueva.

Al terminar, el siguiente comando elimina **únicamente el contenedor y volumen del proyecto de ensayo indicado**. Conserva el `-p` y el archivo completo; no uses este comando sobre el Compose principal:

```text
docker compose -p siabuc-restore-test -f backup/compose.restore-test.yml down -v
```

Para restaurar realmente la instalación utiliza la guía [RESPALDO-RESTAURACION.md](RESPALDO-RESTAURACION.md) y sus scripts con confirmación de reemplazo. Estos scripts detienen también `backup` antes de restaurar. La restauración real conserva la configuración incluida en la copia: revisa el calendario y destino antes de volver a iniciar un worker.

## API y validación reproducible

Todos los endpoints son exclusivos de ADMIN y las escrituras requieren CSRF:

| Método y ruta | Función |
| --- | --- |
| `GET /api/backups/status` | Configuración, servicio, últimos 100 registros y fallo pendiente. |
| `PUT /api/backups/config` | Hora, zona, retención, mínimo, confirmación del destino y estado actual. |
| `POST /api/backups/enabled` | `{"enabled":false}` desactiva sin perder los otros datos; `true` activa con destino confirmado y servicio disponible. |
| `POST /api/backups/run` | Cuerpo `{}`; solicita una copia manual y rechaza otra mientras exista una pendiente. |
| `GET /api/backups/{id}` | Registro e intentos de una ejecución. |

Las tablas de la migración V16 son `backup_settings`, `backup_runtime`, `backup_runs` y `backup_attempts`. `backup_settings.enabled` se inserta como **FALSE**, también al migrar una instalación previa.

Pruebas deterministas de calendario, publicación de archivos, fallo, bloqueo entre workers simulados, retención e independencia del respaldo manual:

```text
python3 -m unittest tests.ops.test_automatic_backups -v
```

Prueba del SQL real que genera el worker con PostgreSQL aislado en memoria (PGlite), sin conectar a la instalación ni ejecutar `pg_dump`:

```bash
npm install --prefix /tmp/siabuc-pglite @electric-sql/pglite@0.5.9
SIABUC_PGLITE_MODULE=/tmp/siabuc-pglite/node_modules/@electric-sql/pglite/dist/index.js python3 tests/ops/verify_backup_worker_sql.py
```

En PowerShell define primero `$env:SIABUC_PGLITE_MODULE` con la ruta local al módulo y usa `python`. Las pruebas HTTP están en `BackupIntegrationIT` y se ejecutan con la suite PostgreSQL de Maven. Las pruebas de SQL y archivos no equivalen a arrancar Docker ni a restaurar una base real. Consulta [VALIDACION.md](VALIDACION.md) para la evidencia de la versión.

Referencias técnicas primarias: [pg_dump 17](https://www.postgresql.org/docs/17/app-pgdump.html), [pg_restore 17](https://www.postgresql.org/docs/17/app-pgrestore.html), [montajes bind de Docker](https://docs.docker.com/engine/storage/bind-mounts/) y [servicios de Docker Compose](https://docs.docker.com/reference/compose-file/services/).
