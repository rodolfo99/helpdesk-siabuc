# Instalación, compilación y operación

En Linux se usan los scripts Bash `.sh`. En Windows se usan los `.bat` desde CMD o PowerShell con Docker Desktop y contenedores Linux; consulte [WINDOWS.md](WINDOWS.md). El modo de desarrollo con Java y Node fuera de contenedores se ejecuta en Linux o dentro de WSL2. El desarrollo nativo requiere JDK 17+, Maven 3.9, Node.js 24 LTS y npm; el despliegue Docker instala esas herramientas dentro de sus imágenes y sólo requiere Docker Compose y Python 3.10+ en el anfitrión.

## Instalación y primer arranque

1. Instale Docker Engine y el complemento `docker compose` mediante el procedimiento oficial de su sistema. Confirme que `docker info` funciona con su usuario. Si requiere privilegios, configure el acceso a Docker conforme a las políticas de su equipo.
2. Descomprima el proyecto. Desde su carpeta, ejecute `chmod +x ./*.sh` si hace falta.
3. Ejecute `./configurar.sh`. Abra `.env` con un editor y ajuste los puertos antes de iniciar. Mantenga el archivo fuera del control de versiones.
4. Ejecute `./iniciar.sh`. Compose compila las imágenes, arranca PostgreSQL y Postfix, espera su salud, inicia Spring con migraciones Flyway y finalmente sirve Angular con Nginx.
5. Abra el puerto `FRONTEND_PORT` del anfitrión. Por defecto es `http://127.0.0.1:4200`.
6. Inicie sesión como `ADMIN_USERNAME` usando `ADMIN_PASSWORD` y cambie la contraseña desde Mi cuenta.

Para elegir la contraseña PostgreSQL antes del primer arranque, ejecute `./configurar.sh --password-bd`: solicita la clave con entrada oculta y confirmación y actualiza `DB_PASSWORD` en `.env`. Debe tener al menos 12 caracteres; se admiten letras, números y `. _ / : @ % + = , - !`, sin espacios, comillas, `$` ni `#`. La opción sólo modifica la configuración local; no altera una base existente. En ese caso siga el procedimiento de cambio con `\password` que se indica más adelante.

La cuenta administrativa se inicializa cuando no existe un administrador activo. Durante la operación normal siempre debe conservar al menos uno. No existe un usuario invitado ni una contraseña fija para la instalación. Las contraseñas de demostraciones o pruebas automatizadas no se usan en este flujo.

La versión 1.1 añade SMTP, cobranza y responsables. Para una instalación 1.0 siga [MIGRACION-1.1.md](MIGRACION-1.1.md): conserve V1 y permita que Flyway aplique V2/V3, sin eliminar los datos. Un `.env` previo conserva su configuración; si no incluía variables de correo, sigue desactivado. La versión 1.2 agrega Postfix sin nuevas migraciones de tablas. Sólo las instalaciones nuevas usan SMTP interno por defecto; las anteriores lo adoptan con `./configurar.sh --smtp-interno`.

Configure el Postfix integrado con `./configurar.sh --smtp-interno` según [SMTP-INTERNO.md](SMTP-INTERNO.md). Para conectar Spring directamente a otro servidor, use `./configurar.sh --correo`; el asistente solicita SMTP, autenticación y remitente, conserva el secreto fuera de logs y sólo guarda el archivo. Reinicie para aplicar. Para nombres o secretos con espacios, `$` o `#`, el archivo admite comillas simples literales; no admite apóstrofes internos, controles ni barra inversa al final. No ejecute `.env` con `source`. Los detalles de proveedores, límites OAuth y sumidero Mailpit están en [CORREO.md](CORREO.md).

`./iniciar.sh --solo-bd` inicia únicamente PostgreSQL. Es útil para desarrollo, mantenimiento y pruebas.

## Qué hace cada compilación

### Docker

```bash
./compilar.sh --docker
```

El Dockerfile del servidor utiliza Maven con JDK 17 y ejecuta `mvn package`; empaqueta un JAR que luego corre en una imagen de JRE 17 con usuario sin privilegios. El Dockerfile del cliente utiliza Node 24, `npm ci` y `npm run build`; copia el resultado a Nginx. PostgreSQL utiliza su imagen oficial 17 Alpine. `smtp/Dockerfile` construye el SMTP Postfix desde Debian 13, con configuración propia, sin buzones ni servicio web.

La primera descarga requiere conexión a Maven Central, npm y al registro de imágenes. La compilación no requiere que haya una base de datos en ejecución. Las pruebas de integración PostgreSQL se ejecutan explícitamente por separado.

### Nativa

```bash
./compilar.sh
```

O, por componente:

```bash
cd backend
mvn -B -ntp package
```

En otra terminal, desde la raíz:

```bash
cd frontend
npm ci
npm run build
npm test
```

Artefactos: `backend/target/siabuc-helpdesk-1.3.1.jar` y `frontend/dist/helpdesk/browser/`. `npm ci` utiliza el archivo `package-lock.json` entregado. No sustituya `npm ci` por actualizaciones de dependencias durante una instalación reproducible.

`mvn package` compila también las clases de prueba y ejecuta pruebas unitarias si existen. Los archivos `*IT.java` se ejecutan únicamente con el perfil `postgres-it`. Una compilación satisfactoria no significa por sí sola que se ejecutaron esas pruebas de integración.

## Desarrollo: PostgreSQL/Postfix Docker, Spring y Angular locales

Después de instalar las herramientas nativas:

```bash
./configurar.sh
./iniciar-dev.sh
```

El script inicia los servicios Docker necesarios. Si `SMTP_HOST=smtp`, lo adapta a `127.0.0.1:SMTP_INTERNAL_PORT` para Spring local. Mantiene la terminal ocupada, ejecuta `mvn package` y `npm ci` e inicia los dos procesos locales. La recarga de Angular funciona con cambios de sus fuentes; para cambios de Java, detenga con Ctrl+C y vuelva a iniciar para recompilar. Los registros están en `.runtime/backend.log` y `.runtime/frontend.log`. Los puertos de Java y Angular deben estar libres.

El proxy `frontend/proxy.conf.cjs` reenvía `/api` hacia el `SERVER_PORT` de `.env`. No requiere CORS porque el navegador sólo conversa con el origen Angular. En este modo `COOKIE_SECURE` debe ser `false` por usar HTTP local.

**Ctrl+C** termina Java y Angular; `./detener.sh` detiene posteriormente PostgreSQL y Postfix sin borrar sus volúmenes. Si una interrupción abrupta dejó un archivo `.runtime/dev.pid`, primero confirme que no quedan procesos del desarrollo anteriores antes de retirarlo. El script limpia automáticamente un marcador de proceso que ya no existe.

## Instalación completamente nativa, sin Docker

### 1. Crear la base en PostgreSQL 17

Instale y arranque PostgreSQL 17 según su sistema. Abra `psql` como una cuenta administradora y cree el usuario y base, ajustando los identificadores si cambió `.env`:

```sql
CREATE ROLE siabuc LOGIN;
\password siabuc
CREATE DATABASE siabuc_helpdesk OWNER siabuc;
```

`\password` solicita interactivamente la contraseña sin escribirla como parte de una sentencia SQL en el historial. Introduzca el mismo valor configurado en `DB_PASSWORD`. Ajuste la autenticación local de PostgreSQL para conexiones TCP con contraseña y confirme su puerto; el ejemplo usa `5432`.

### 2. Configurar y compilar

```bash
./configurar.sh
nano .env
./compilar.sh
```

Sin Docker configure un SMTP accesible desde el anfitrión mediante `./configurar.sh --correo`, o establezca `MAIL_ENABLED=false` hasta disponer de él; el nombre `smtp` sólo resuelve dentro de Docker. El servidor usa `DB_HOST=127.0.0.1` en los siguientes ejemplos. `DB_PORT`, nombre de base, usuario y contraseña deben coincidir con su instalación PostgreSQL.

### 3. Ejecutar Spring

Desde la raíz del proyecto, este comando lee `.env` con el mismo analizador seguro de los scripts y pasa los secretos por el entorno, sin imprimirlos:

```bash
python3 - <<'PY'
import os
import subprocess
from scripts.manage import ROOT, config
env = os.environ.copy()
env.update(config())
env.update(DB_HOST="127.0.0.1", SERVER_ADDRESS="127.0.0.1")
subprocess.run(["java", "-jar", str(ROOT / "backend/target/siabuc-helpdesk-1.3.1.jar")], env=env, check=True)
PY
```

Flyway crea automáticamente las tablas; no necesita ejecutar el archivo SQL de migración manualmente. `/api/health` debe devolver `{"status":"UP"}` cuando Spring y la base respondan.

### 4. Ejecutar Angular

En otra terminal, desde la raíz del proyecto:

```bash
python3 - <<'PY'
import os
import subprocess
from scripts.manage import ROOT, config
env = os.environ.copy()
values = config()
env.update(values)
subprocess.run(["npm", "start", "--", "--host", "127.0.0.1", "--port", values["FRONTEND_PORT"]], cwd=ROOT / "frontend", env=env, check=True)
PY
```

Abra `http://127.0.0.1:FRONTEND_PORT` sustituyendo el puerto elegido. Detenga cada proceso con Ctrl+C; administre PostgreSQL mediante su servicio del sistema.

Para servir la compilación estática fuera de Docker, configure un servidor HTTP que sirva `frontend/dist/helpdesk/browser`, regrese `index.html` para las rutas del cliente y reenvíe `/api/` a Spring. `frontend/nginx.conf` es la referencia Docker; adapte la dirección `backend:8080` y retire el resolver `127.0.0.11` para un Nginx instalado en el anfitrión.

## Pruebas de integración en una base aislada

Nunca apunte el perfil `postgres-it` a una base de datos operativa. Utilice una base exclusiva y vacía en su primer uso: la suite debe crear allí su administrador `testadmin`, sin otra cuenta administrativa preexistente. Las pruebas crean cuentas y registros y ejercitan operaciones de actualización, baja y consumo. No limpian las tablas al terminar; sus datos de prueba permanecen para conservar y verificar la trazabilidad.

1. Inicie PostgreSQL con `./iniciar.sh --solo-bd`.
2. Cree una base nueva de prueba y ejecute Maven leyendo la configuración sin imprimir contraseñas:

   ```bash
   python3 - <<'PY'
   import os
   import subprocess
   from scripts.manage import ROOT, compose, config
   values = config()
   test_name = "siabuc_helpdesk_test"
   compose(values, "exec", "-T", "db", "createdb", "-U", values["DB_USER"], test_name)
   env = os.environ.copy()
   env.update(values)
   env.update(DB_HOST="127.0.0.1", DB_NAME=test_name)
   subprocess.run(["mvn", "-B", "-ntp", "-Ppostgres-it", "verify"], cwd=ROOT / "backend", env=env, check=True)
   PY
   ```

   Si la base ya existe de una ejecución anterior de esta misma suite, use otro nombre de prueba o ejecute únicamente el paso Maven conservando `DB_NAME=siabuc_helpdesk_test` y su administrador `testadmin`. No reutilice una base inicializada previamente por la aplicación normal.

3. Revise `backend/target/failsafe-reports/`. Los resultados efectivamente obtenidos al preparar esta entrega están en [VALIDACION.md](VALIDACION.md).

## Persistencia y mantenimiento de credenciales

Los datos viven en el volumen Docker `<COMPOSE_PROJECT_NAME>_postgres_data`. Cambiar el nombre del proyecto hace que Compose use otro volumen: puede parecer que los datos desaparecieron, aunque permanezcan en el volumen original. Mantenga estable ese nombre.

`detener.sh` no elimina volúmenes. Actualizar o reconstruir imágenes tampoco elimina los datos. El borrado de volúmenes es una operación destructiva y no está incluido en los scripts de operación.

PostgreSQL aplica `POSTGRES_PASSWORD`, `POSTGRES_DB` y `POSTGRES_USER` únicamente cuando inicializa un directorio de datos vacío. Para cambiar la contraseña de una base ya creada:

1. Detenga los servicios Spring/Angular que la utilizan.
2. Abra una sesión PostgreSQL en el contenedor: `docker compose exec db psql -U siabuc -d siabuc_helpdesk`, adaptando los nombres a su configuración.
3. Ejecute `\password siabuc` y establezca la nueva contraseña.
4. Ejecute `./configurar.sh --password-bd` e introduzca el mismo valor para actualizar `DB_PASSWORD` en `.env`, o edítelo manualmente. Después ejecute `./iniciar.sh`.

La opción `--password-bd` no sustituye el paso de PostgreSQL: si cambia sólo `.env` cuando la base ya existe, Spring no podrá autenticarse con la nueva contraseña hasta que ambas coincidan.

Para restablecer una contraseña de usuario del helpdesk, utilice otra cuenta administradora y edite ese usuario. Cambiar `.env` no restablece una cuenta ya creada. Mantenga más de un administrador autorizado si necesita recuperación operativa.

## Respaldo

Consulte también la [guía completa de respaldo y restauración](RESPALDO-RESTAURACION.md), con los pasos en orden, parámetros, ejemplos y solución de errores.

```bash
./respaldar.sh
```

Sin argumentos se crea `backups/siabuc-FECHA.dump` dentro del proyecto. Para elegir un archivo nuevo en una ubicación concreta, utilice `--nombre-respaldo`:

```bash
./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump
```

Puede indicar una ruta absoluta o un nombre con espacios:

```bash
./respaldar.sh --nombre-respaldo '/ruta/segura/respaldo soporte.dump'
```

El parámetro es un nombre de archivo o ruta literal; no se agrega una extensión automáticamente. Se recomienda `.dump`. Una ruta relativa parte de la carpeta desde la que ejecuta el comando: `mi-respaldo.dump` se guarda allí y `backups/mi-respaldo.dump` en su subcarpeta `backups`. Se crean los directorios de destino necesarios. La forma anterior `./respaldar.sh /ruta/segura/mi-respaldo.dump` sigue disponible. No combine argumento directo y `--nombre-respaldo`; los nombres vacíos se rechazan. Use `./respaldar.sh --help` para consultar la sintaxis.

El script utiliza `pg_dump --format=custom --no-owner` del contenedor, que toma una instantánea consistente de la base sin detener el servicio. Conserva un archivo `.dump` con permisos `600` y un `.dump.sha256`. No sobreescribe un archivo existente. Guarde ambos fuera del equipo para protegerse frente a pérdida del disco.

La copia contiene instituciones, cuentas con hashes de contraseña, casos, atenciones, pagos, asignaciones, cola de notificaciones, migraciones y auditoría; no incluye el volumen de cola `smtp_queue` de Postfix, las contraseñas de PostgreSQL/SMTP de `.env`, el código, las imágenes Docker o las sesiones en memoria. Preserve `.env` por separado bajo el procedimiento de secretos de su organización. Los respaldos no están cifrados por el script. Antes de iniciar una copia restaurada, revise `MAIL_ENABLED` y la cola pendiente para evitar reenvíos no previstos.

Este script se conecta al contenedor `db`. Para un PostgreSQL instalado fuera de Docker, use su `pg_dump` nativo con los mismos parámetros y autenticación de su servidor.

## Restauración

1. Detenga cualquier ejecución nativa de Spring y Angular. El script detecta el marcador de `iniciar-dev.sh`, pero no puede detectar todos los procesos lanzados manualmente.
2. Cree un respaldo del estado actual con `./respaldar.sh`.
3. Ejecute:

   ```bash
   ./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo
   ```

4. Inicie la aplicación con `./iniciar.sh` o `./iniciar-dev.sh`.
5. Acceda con una cuenta incluida en el respaldo y verifique instituciones, casos y saldos.

Sin `--confirmar-reemplazo`, el script se niega a modificar la base. Verifica la suma SHA-256 cuando el archivo complementario está presente; ésta verifica integridad del archivo, no la identidad de su remitente. Restaure sólo copias de su propia instalación o de una fuente administrativa de confianza.

`--nombre-respaldo` debe señalar un respaldo existente, **no** el archivo `.sha256`. Admite nombres entre comillas y rutas absolutas o relativas a la carpeta de ejecución, con las mismas reglas de `respaldar.sh`. También funciona `./restaurar.sh backups/mi-respaldo.dump --confirmar-reemplazo`. Se exige una de esas dos formas de indicar el archivo y se rechaza combinarlas. Consulte `./restaurar.sh --help`.

La recuperación usa `pg_restore --clean --if-exists --single-transaction --exit-on-error` contra la base configurada y omite propietarios/privilegios originales. Detiene los contenedores de aplicación y los deja detenidos para revisar el resultado. Si falla una sentencia, PostgreSQL revierte esa transacción. Una copia debe restaurarse con versiones compatibles de PostgreSQL y del código; esta entrega está configurada para PostgreSQL 17.

## Acceso por red y HTTPS

El valor inicial `BIND_HOST=127.0.0.1` sólo publica puertos locales. Para acceso desde otro equipo puede usar un túnel SSH al puerto del cliente, o un proxy HTTPS de su infraestructura que reenvíe a ese puerto. El frontend y `/api` deben permanecer bajo un mismo origen.

También puede configurar `BIND_HOST=0.0.0.0` para publicar en la red, pero ese ajuste afecta los tres puertos, incluido PostgreSQL. Restrinja por firewall los puertos de base de datos y servidor para que sólo accedan los equipos necesarios; normalmente los usuarios sólo necesitan el puerto web.

Al terminar HTTPS en un proxy frontal, configure `COOKIE_SECURE=true`, mantenga el reenvío de `/api` y reinicie con `./iniciar.sh`. Con ese valor, el navegador no enviará la cookie de sesión por HTTP. Las credenciales y sesiones deben circular mediante HTTPS cuando se accede a través de una red no confiable. Esta entrega no emite certificados TLS ni instala un proxy público automáticamente.

## Problemas habituales

| Síntoma | Comprobación y solución |
| --- | --- |
| Puerto ocupado | Cambie el puerto correspondiente en `.env`, valide con `./configurar.sh` y reinicie. Todos los puertos publicados deben ser distintos, incluido `SMTP_INTERNAL_PORT`. |
| Docker no responde | Revise `docker info`, el servicio Docker y los permisos de su usuario. |
| Fallo al descargar dependencias | Revise conectividad con el registro Docker, npm y Maven Central; vuelva a ejecutar el mismo script. |
| Spring no conecta a PostgreSQL | Revise puerto, nombre y contraseña; una edición de `.env` no cambia la contraseña de un volumen existente. |
| No puedo iniciar sesión por HTTP | Para HTTP local use `COOKIE_SECURE=false`. Compruebe la cuenta correcta y la contraseña actual, no necesariamente la inicial de `.env`. |
| Usuario bloqueado | Revise si la cuenta y su institución están activas. |
| Error CSRF / 403 | Abra la aplicación desde su URL habitual, permita cookies y vuelva a iniciar sesión; evite mezclar dominios o puertos para el API. |
| Cambiar una hora muestra saldo distinto al esperado | Los campos de institución son el total histórico asignado, no la recarga adicional ni el disponible. |
| Un caso no admite atenciones | Compruebe que esté abierto, la institución activa y que el canal conserve saldo suficiente. |
| Datos aparentemente ausentes | Revise que `COMPOSE_PROJECT_NAME` y el volumen sean los de la instalación anterior. |
| Migración Flyway falla después de editar SQL | Restaure el archivo de migración original. Añada una nueva migración versionada para futuros cambios. |
| Restauración fallida | Spring, Angular y Postfix siguen detenidos si ya se inició `pg_restore`. Revise el mensaje y la compatibilidad de la copia; no reinicie hasta decidir si repetir o conservar la base anterior. |

Revise `docker compose logs --tail=100 backend` para el servidor Docker y `.runtime/backend.log` para el nativo. No comparta `.env` ni la salida de comandos que expandan la configuración completa con secretos.

## SMTP integrado

Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md) para variables, relay, DNS y mantenimiento. `SMTP_INTERNAL_PORT` siempre se publica en `127.0.0.1`, incluso con `BIND_HOST=0.0.0.0`. El respaldo PostgreSQL no reemplaza el respaldo del volumen SMTP. La restauración detiene también Postfix; revise la cola antes de reiniciar.
