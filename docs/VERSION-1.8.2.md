# Helpdesk SIABUC 1.8.2 — SMTP directo con Gmail

## Alcance
Spring Boot envia avisos a `smtp.gmail.com:587` con STARTTLS y autenticacion SMTP
sin necesitar el contenedor Postfix. En instalaciones nuevas el correo comienza
desactivado (`MAIL_ENABLED=false`) hasta introducir la contraseña de aplicacion
de manera privada. No se implementa OAuth/XOAUTH2: Google Workspace debe permitir
contraseñas de aplicacion.

El envio por Postfix sigue disponible para instalaciones anteriores:
si el `.env` privado conserva `SMTP_HOST=smtp`, los scripts habilitan el perfil
Docker `local-smtp`. El contenedor y el volumen `smtp_queue` no se eliminan
automaticamente al cambiar de proveedor. No se cambian migraciones de base,
saldo, cuentas o identificadores de volumen.

## Instalacion o actualizacion (sin publicar secretos)

1. Obtenga una copia segura de su `.env` y de sus respaldos, sin subirlos a Git.
2. Use `./configurar.sh` para generar `.env` con contraseñas aleatorias si es
   una instalacion nueva. El archivo `.env.gmail.example` ilustra la configuracion
   SMTP sin contraseña de aplicacion ni claves de administrador/base.
3. Si utiliza Postfix, revise su cola **antes** del cambio:

   ```bash
   docker compose --profile local-smtp exec -T smtp postqueue -p
   ```

   Si el contenedor no esta en marcha, inspeccione su estado y el volumen
   `smtp_queue` antes de iniciar Postfix temporalmente o retirar el servicio.
   La cola de Postfix **no es** la cola de notificaciones de la base de datos.
4. Revoque la contraseña de aplicacion anteriormente compartida y genere una nueva
   en Google, si su administrador Workspace lo permite. Desde el proyecto:

   ```bash
   ./configurar.sh --gmail-directo
   ```

   En Windows, `configurar.bat --gmail-directo`.
   Solicita cuenta Gmail y secreto por entrada oculta, confirma el secreto y
   conserva los datos no relacionados del `.env`. No hace conexion ni envio.
5. Compruebe que `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`,
   `SMTP_AUTH=true`, `SMTP_SECURITY=STARTTLS` y `MAIL_ENABLED=true`.
   **No imprima ni comparta** SMTP_PASSWORD, DB_PASSWORD ni ADMIN_PASSWORD.
6. En una ventana de mantenimiento, aplique la version con `./iniciar.sh`.
   Realice una prueba de correo en **Notificaciones** a una cuenta autorizada;
   revise el historial y la entrega. La prueba automatizada no autentica
   ni envia correos a Google.
7. Cuando haya comprobado la entrega directa y gestionado la cola de Postfix,
   puede detener y retirar **solo el contenedor**, conservando el volumen:

   ```bash
   docker compose --profile local-smtp stop smtp
   docker compose --profile local-smtp rm -f smtp
   ```

   No ejecute `down -v`, `volume rm` ni `docker volume prune`.
   Si quedan mensajes en Postfix no retire el contenedor.

## Compatibilidad y seguridad

- `COMPOSE_PROJECT_NAME` debe mantenerse para conservar los volúmenes existentes.
- El contenedor Postfix usa el perfil opcional `local-smtp`; no se inicia en
  despliegue directo por defecto. El backend solo espera la salud de PostgreSQL.
- En modo Docker solo Angular/Nginx se publica en el host; `/api/` alcanza
  Spring dentro de Compose, y PostgreSQL no tiene puerto publicado.
- La configuracion Gmail sigue usando SMTP con contraseña de aplicación;
  cuentas de Google Workspace pueden prohibir ese metodo.
- El ejemplo no incluye secretos y no es una credencial operativa. No versionar
  `.env` privado ni copiar las contraseñas al control de versiones.
- Cambiar el servidor de correo puede hacer que se intenten procesar mensajes
  PENDING de la cola de notificaciones interna: revise antes el estado y la pausa.
- HTTPS web y acceso LAN requieren su propia configuracion de proxy TLS y firewall.

## Validación

Consulte la PR y GitHub Actions de 1.8.2 para el resultado de las pruebas
de Java/PostgreSQL, Angular, Python, Docker Compose y el paquete de código.
No considere validado el acceso a Gmail real, despliegue de producción o
recuperación de datos sin realizar las pruebas en un entorno autorizado.
