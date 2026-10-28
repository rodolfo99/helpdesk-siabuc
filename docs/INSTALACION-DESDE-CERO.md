# Instalación de SIABUC Helpdesk 1.8.5 desde cero en Ubuntu

Guía para una **instalación nueva** de SIABUC Helpdesk con Docker Compose en **Ubuntu 24.04 o 26.04 LTS**. Se basa en los scripts y el `docker-compose.yml` de la versión 1.8.5 (disponible en su rama/PR hasta que se integre en `main`); no describe una migración de datos.

> **¿Ya tienes una instalación con información?** No sigas estos pasos como si fuera nueva: realiza un respaldo y consulta [actualización a 1.8.5](VERSION-1.8.5.md) y [respaldo y restauración](RESPALDO-RESTAURACION.md). Reutilizar `COMPOSE_PROJECT_NAME` puede conectar con volúmenes existentes; cambiarlo crea un proyecto independiente, pero no migra datos.

## 1. Requisitos y comprobaciones previas

- Ubuntu con acceso a Internet para descargar imágenes y dependencias.
- Python 3.10 o posterior, Git, Docker Engine funcionando y complemento `docker compose` con soporte de `up --wait`.
- Espacio libre suficiente para las imágenes, la compilación, PostgreSQL y las copias.
- **No** se requieren Java, Maven, Node ni Angular instalados en Ubuntu para el despliegue Docker: se incluyen en las imágenes de compilación y ejecución.

~~~bash
python3 --version
git --version
sudo docker --version
sudo docker compose version
sudo docker info
~~~

Si ya funciona Docker, salta al paso 3. Si aparece `permission denied` al conectarte al socket de Docker, utiliza `sudo` en los comandos que acceden al motor, conforme a las políticas de tu equipo. Evita modificar los permisos de `/var/run/docker.sock`.

## 2. Instalar Docker cuando el equipo está limpio (opcional)

Si Python, Git o las utilidades básicas faltan:

~~~bash
sudo apt update
sudo apt install -y python3 git curl ca-certificates
~~~

Para instalar Docker Engine desde el repositorio oficial:

~~~bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "$UBUNTU_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker compose version
~~~

Estas instrucciones son para un **sistema limpio**. Si hay otra distribución de Docker instalada, revisa los conflictos de paquetes antes de sustituirla; no desinstales paquetes ni elimines volúmenes de una máquina con otros proyectos activos. Consulta la [guía oficial de Docker para Ubuntu](https://docs.docker.com/engine/install/ubuntu/) para los cambios de soporte o paquetes de tu distribución.

## 3. Obtener el código desde GitHub

Desde tu cuenta normal (sin `sudo`):

~~~bash
mkdir -p "$HOME/Aplicaciones"
cd "$HOME/Aplicaciones"
git clone https://github.com/rodolfo99/helpdesk-siabuc.git
cd helpdesk-siabuc
head -n 1 README.md
~~~

La primera línea para esta entrega debe indicar `SIABUC Helpdesk 1.8.5`. Para una versión posterior, revisa los cambios de esa versión antes de seguir esta guía. No es necesario usar una rama de una PR antigua: la revisión 1.8.4 de permisos de respaldos ya está integrada en `main`.

Si la carpeta `helpdesk-siabuc` ya existe, no sobrescribas su contenido; identifica primero si contiene una instalación con datos o configuración.

## 4. Preparar scripts y configuración

~~~bash
chmod +x ./*.sh
./configurar.sh
nano .env
~~~

`configurar.sh` genera `.env` con dos secretos **aleatorios y distintos**: `DB_PASSWORD` para PostgreSQL y `ADMIN_PASSWORD` para el primer administrador. En Linux protege `.env` con modo `0600`. No subas, compartas ni ejecutes `.env` con `source`.

Revisa, como mínimo, estas variables (conserva las contraseñas generadas):

~~~dotenv
COMPOSE_PROJECT_NAME=siabuc-helpdesk
WEB_BIND_HOST=127.0.0.1
FRONTEND_PORT=4200
DB_NAME=siabuc_helpdesk
DB_USER=siabuc
ADMIN_USERNAME=admin
MAIL_ENABLED=false
REPORTING_TIME_ZONE=America/Mexico_City
BACKUP_DIRECTORY=./backups/automatic
~~~

- `WEB_BIND_HOST=127.0.0.1` permite abrir el sistema **solo en ese equipo**; `0.0.0.0` permite conexiones desde otras interfaces. Limita el acceso con firewall y evita exponer HTTP directamente a Internet.
- `FRONTEND_PORT` puede cambiarse si el 4200 está ocupado. El despliegue habitual publica solo el frontend; PostgreSQL y Spring quedan dentro de la red Docker.
- `COMPOSE_PROJECT_NAME` identifica los volúmenes. Si **quieres crear un entorno adicional independiente**, usa un nombre distinto, por ejemplo `siabuc-helpdesk-pruebas`, y otro `FRONTEND_PORT`, por ejemplo `4201`. Esto **no** importa los datos de una instalación anterior.
- `BACKUP_DIRECTORY` es una **carpeta exclusiva** y privada para las copias automáticas. Una ruta relativa se interpreta desde la carpeta del proyecto; se puede usar una ruta absoluta como `/srv/siabuc/copias`. Conserva los `BACKUP_UID` y `BACKUP_GID` generados salvo necesidad específica.
- Las instalaciones nuevas empiezan con `MAIL_ENABLED=false`. No es necesario configurar SMTP para iniciar y utilizar el sistema.

Para elegir la clave de **una base todavía no inicializada** antes del primer inicio, opcionalmente ejecuta:

~~~bash
./configurar.sh --password-bd
~~~

Esta opción solo modifica `.env` y **no** cambia la contraseña de una base de datos ya creada. Para detalles consulta [Operación](OPERACION.md).

## 5. Iniciar el sistema completo

Si tu usuario tiene acceso autorizado a Docker sin `sudo`:

~~~bash
./iniciar.sh
~~~

Si tu equipo requiere privilegios para acceder a Docker:

~~~bash
sudo bash iniciar.sh
~~~

El script valida `.env`, crea el directorio privado de respaldos cuando hace falta, compila su imagen, comprueba la escritura con el UID/GID real del servicio `backup` y, **solo si hay denegación de acceso en Linux**, intenta reparar los permisos de **esa carpeta**. Después inicia los servicios con `docker compose up -d --build --wait` y aplica las migraciones Flyway hasta V27 en una base nueva.

**No** utilices `chmod 777`, `chown -R` ni cambies recursivamente los propietarios de la carpeta de respaldos: la 1.8.4 ya verifica y ajusta el destino cuando procede, preservando los archivos que contenga. Si el montaje es de solo lectura, no hay espacio o la ACL bloquea el acceso, el arranque se detiene con un diagnóstico.

La primera compilación puede descargar imágenes, dependencias Maven y paquetes npm. No actives Postfix ni Gmail antes de comprobar que la plataforma funciona si no necesitas correo.

## 6. Verificar que todo está saludable

Desde la carpeta del proyecto:

~~~bash
sudo docker compose ps
sudo docker compose logs --tail=100 backend
sudo docker compose logs --tail=100 backup
~~~

En una instalación estándar con correo desactivado deben aparecer los servicios **`db`, `backend`, `frontend` y `backup`** saludables. El contenedor `smtp` es opcional: solo arranca al elegir `SMTP_HOST=smtp`.

Comprueba la API a través del frontend (si conservaste el puerto inicial):

~~~bash
curl http://127.0.0.1:4200/api/health
~~~

Respuesta esperada: `{"status":"UP"}`.

Comprueba además que el contenedor de respaldos **realmente** puede escribir en el directorio montado:

~~~bash
sudo docker compose exec -T backup python3 -c 'import tempfile; f=tempfile.TemporaryFile(dir="/backups"); f.close(); print("Escritura correcta")'
~~~

Debe imprimir `Escritura correcta`. Esto confirma **escritura**, no que se haya producido ni restaurado un respaldo real.

> Usa `sudo docker compose ...` en los ejemplos si tu usuario no tiene permiso para conectarse al daemon. Si cambiaste `FRONTEND_PORT`, sustituye `4200` en la URL.

## 7. Primer acceso y configuración funcional

1. Abre [http://127.0.0.1:4200](http://127.0.0.1:4200) desde el mismo equipo (o la IP/puerto configurados).
2. Inicia sesión con `ADMIN_USERNAME` y `ADMIN_PASSWORD` del archivo `.env`. **No hay contraseña administrativa universal**.
3. Cambia la contraseña desde **Mi cuenta**; las variables de `.env` solo sirven para inicializar la primera cuenta y no restablecen una contraseña existente.
4. Desde Administración, revisa las **horas predeterminadas de licencias** antes de registrar las primeras licencias. Después crea una institución y sus usuarios.
5. Valida el flujo de un caso, los saldos y los permisos de los roles con información de prueba.

## 8. Correo: configurar únicamente cuando se vaya a utilizar

El sistema arranca sin enviar correos. Para Gmail directo:

~~~bash
./configurar.sh --gmail-directo
sudo bash detener.sh
sudo bash iniciar.sh
~~~

El asistente solicita credenciales autorizadas (habitualmente una contraseña de aplicación) y guarda la configuración sin enviar una prueba. Para otro SMTP usa `./configurar.sh --correo`; para Postfix opcional, `./configurar.sh --smtp-interno`. Revisa el estado de la pausa administrativa y las notificaciones pendientes antes de habilitar el envío. Consulta [Correo y notificaciones](CORREO.md) y [SMTP interno](SMTP-INTERNO.md).

## 9. Respaldo manual y automatización

Con PostgreSQL en ejecución, crea una primera copia:

~~~bash
sudo bash respaldar.sh
ls -lh backups/
~~~

El script crea una copia en formato PostgreSQL `.dump` y una suma `.sha256`. Conserva ambos fuera del servidor, con acceso restringido. El respaldo **no** incluye el archivo `.env`, las imágenes Docker ni la cola Postfix: resguarda la configuración y credenciales por separado en un medio seguro.

Los **respaldos automáticos comienzan desactivados**. Para activarlos, entra como `ADMIN`, abre **Respaldos**, confirma el destino, configura horario, zona horaria y retención, guarda, realiza **Crear respaldo ahora** y comprueba que finaliza como **Completado**; después pulsa **Activar respaldos automáticos**. La comprobación de escritura del paso 6 no sustituye esta prueba real. La restauración debe probarse en otra instalación desechable, no sobre datos activos. Consulta [Respaldos automáticos](RESPALDOS-AUTOMATICOS.md) y [Respaldo y restauración](RESPALDO-RESTAURACION.md).

## 10. Operación habitual y diagnóstico

~~~bash
# Consultar estado y errores
sudo docker compose ps
sudo docker compose logs --tail=100
sudo docker compose logs --tail=100 db
sudo docker compose logs --tail=100 frontend

# Parar sin borrar los volúmenes de datos
sudo bash detener.sh

# Volver a iniciar (también recompila cuando corresponda)
sudo bash iniciar.sh

# Compilar imágenes sin levantar servicios
sudo bash compilar.sh --docker
~~~

**Nunca uses `docker compose down -v` para detener o actualizar la aplicación**: el indicador `-v` elimina los volúmenes de PostgreSQL y otros servicios. `detener.sh` conserva los volúmenes.

| Síntoma | Comprobación |
| --- | --- |
| `permission denied` al usar Docker | Usa `sudo` con los comandos Docker/scripts de arranque o configura el acceso al daemon según las políticas del equipo. |
| Puerto web ocupado | Cambia `FRONTEND_PORT` en `.env` (y el nombre del proyecto si necesitas otra instalación independiente). |
| No responde el navegador | Revisa `sudo docker compose ps` y `logs backend`/`logs frontend`; verifica `WEB_BIND_HOST` y el puerto. |
| No inicia `backup` | Usa `sudo bash iniciar.sh` para ejecutar la verificación/reparación 1.8.4. Revisa el diagnóstico, la ruta `BACKUP_DIRECTORY`, espacio, permisos y ACL del montaje. |
| Credenciales iniciales desconocidas | Revisa `ADMIN_USERNAME` y `ADMIN_PASSWORD` en tu `.env` local, sin compartirlo. Si ya existe una cuenta, cambiar `.env` no cambia su clave. |
| Quiero conservar datos de otra versión | **No** repitas la instalación sobre un proyecto existente: lee [Actualización a 1.8.4](VERSION-1.8.4.md). |

## Comandos mínimos para un equipo que ya tiene Docker

~~~bash
mkdir -p "$HOME/Aplicaciones"
cd "$HOME/Aplicaciones"
git clone https://github.com/rodolfo99/helpdesk-siabuc.git
cd helpdesk-siabuc
chmod +x ./*.sh
./configurar.sh
nano .env
sudo bash iniciar.sh
sudo docker compose ps
curl http://127.0.0.1:4200/api/health
~~~

**Alcance de esta guía:** comandos revisados contra los scripts y la configuración de la versión 1.8.5; **no** representan una ejecución certificada en el equipo de quien la lea. Antes del uso institucional, valida red, permisos, HTTPS, credenciales, notificaciones, respaldos y una restauración aislada. Para Windows consulta [Windows](WINDOWS.md); para desarrollo nativo y diagnóstico avanzado, [Operación](OPERACION.md).
