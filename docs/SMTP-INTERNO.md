# Servidor SMTP interno · configuración vigente en 1.8.3

El servicio Docker `smtp` contiene **Postfix para enviar correo**. Spring entrega allí los avisos y Postfix los transmite al servidor del destinatario o a un relay autorizado. No proporciona buzones, IMAP, POP3 ni una bandeja web. El puerto SMTP no se publica hacia Internet.

## Inicio opcional

Desde 1.8.2, una instalación nueva tiene el correo desactivado y Gmail directo preseleccionado. Postfix se activa opcionalmente con `./configurar.sh --smtp-interno`; después, `./iniciar.sh` habilita el perfil `local-smtp`. Spring usa estos valores para conectarse al servicio interno:

```dotenv
MAIL_ENABLED=true
SMTP_HOST=smtp
SMTP_PORT=25
SMTP_AUTH=false
SMTP_SECURITY=NONE
SMTP_LOCAL_IP=127.0.0.1
MAIL_FROM=soporte@[127.0.0.1]
SMTP_HOSTNAME=mail.siabuc.internal
SMTP_INTERNAL_PORT=2525
SMTP_RELAY_HOST=
```

El remitente usa **la IPv4 local del anfitrión**, escrita entre corchetes después de `@`. El valor inicial `127.0.0.1` permite iniciar una instalación local; seleccione la IP LAN del servidor antes de enviar a otros equipos. Por ejemplo: `SMTP_LOCAL_IP=192.168.1.50` y `MAIL_FROM=soporte@[192.168.1.50]`. La IP de este ejemplo debe sustituirse; no es una dirección descubierta de su red. No use la dirección transitoria del contenedor. Los correos del administrador y de sus instituciones se capturan por separado en Usuarios e Instituciones.

```bash
./configurar.sh --smtp-interno
./iniciar.sh
```

El asistente solicita la IP local, propone el remitente con esa IP y permite configurar el nombre técnico del servidor y un relay opcional. Guardar la configuración no envía un mensaje. Al iniciar Spring con correo habilitado, se procesan también los avisos pendientes. La prueba desde **Notificaciones** se dirige únicamente al correo de la propia cuenta administrativa.

Los corchetes son obligatorios para el dominio literal: use `soporte@[192.168.1.50]`, no `soporte@192.168.1.50`. La sintaxis está definida en [RFC 5321, secciones 4.1.2 y 4.1.3](https://www.rfc-editor.org/rfc/rfc5321.html). Postfix anuncia `[192.168.1.50]` en el saludo saliente mediante [`smtp_helo_name`](https://www.postfix.org/postconf.5.html#smtp_helo_name); `myhostname` conserva un nombre técnico independiente. `SMTP_LOCAL_IP` identifica al remitente, no cambia la interfaz de escucha, las rutas ni la IP de salida/NAT.

Si edita `.env` manualmente, actualice **ambos** valores: `SMTP_LOCAL_IP` y `MAIL_FROM`. El asistente los coordina cuando conserva el remitente automático y respeta remitentes personalizados. Su detección sólo propone una IP del equipo al ejecutar el asistente; confirme la interfaz correcta si hay VPN, varias tarjetas o una IP asignada por DHCP.

## Dos formas de entregar

| Modalidad | Recorrido | Configuración |
| --- | --- | --- |
| Directa, si se elige Postfix sin relay | Spring → Postfix → servidor MX del destinatario | `SMTP_RELAY_HOST` vacío. Salida TCP 25 y resolución DNS disponibles. |
| Mediante relay | Spring → Postfix → relay autorizado → destinatario | Host, puerto, TLS y credenciales que facilite el proveedor o administrador. |

Postfix distingue [entrega directa y uso de relay](https://www.postfix.org/BASIC_CONFIGURATION_README.html). La entrega directa depende de la red y de la aceptación del servidor remoto. Un remitente con IP privada puede ser aceptado por un servidor institucional configurado para ello, pero los servicios de correo externos pueden rechazarlo. En redes con puerto 25 bloqueado o direcciones dinámicas, configure un relay autorizado que acepte el remitente solicitado. La aplicación no puede eliminar las restricciones del receptor o del proveedor de Internet.

En modo directo, Postfix usa TLS oportunista (`smtp_tls_security_level=may`): cifra cuando el destino lo ofrece. Para un relay con STARTTLS o SSL exige certificado válido para su nombre (`secure`, coincidencia `nexthop`). Estas políticas siguen la [documentación TLS de Postfix](https://www.postfix.org/TLS_README.html).

## Variables del servidor Postfix

Estas variables son distintas de `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME` y `SMTP_PASSWORD`, que controlan la conexión de **Spring** a su primer servidor SMTP.

| Variable | Predeterminado | Significado |
| --- | --- | --- |
| `SMTP_LOCAL_IP` | `127.0.0.1` | IPv4 del anfitrión utilizada en el saludo saliente. El asistente deriva el remitente de esta IP. |
| `SMTP_HOSTNAME` | `mail.siabuc.internal` | Nombre técnico completo de Postfix; no es el dominio del remitente. |
| `SMTP_INTERNAL_PORT` | `2525` | Puerto del override de desarrollo, publicado únicamente en `127.0.0.1`; Docker ordinario no lo publica. Dentro de Docker es 25. |
| `SMTP_RELAY_HOST` | Vacío | Vacío: entrega directa. Con valor: relay saliente, sin protocolo ni puerto. |
| `SMTP_RELAY_PORT` | `587` | Puerto del relay. |
| `SMTP_RELAY_SECURITY` | `STARTTLS` | `STARTTLS`, `SSL` o `NONE`. |
| `SMTP_RELAY_USERNAME` | Vacío | Usuario del relay, si exige autenticación. |
| `SMTP_RELAY_PASSWORD` | Vacío | Secreto del relay, capturado de forma oculta por el asistente. |

Usuario y contraseña deben configurarse juntos. No se admite autenticación con `NONE`. Tampoco se admite whitespace al final de la contraseña del relay: `postmap` lo recortaría; se rechaza el valor en vez de alterarlo silenciosamente. Una conexión a un relay sin autenticación requiere que su administrador haya autorizado la IP o la red de origen. Este sistema no configura conectores externos ni negocia OAuth.

Ejemplo de conexión a un relay institucional; sustituya los valores de ejemplo:

```dotenv
SMTP_HOST=smtp
SMTP_PORT=25
SMTP_AUTH=false
SMTP_SECURITY=NONE
MAIL_ENABLED=true
SMTP_LOCAL_IP=192.168.1.50
SMTP_HOSTNAME=mail.siabuc.internal
MAIL_FROM=soporte@[192.168.1.50]
SMTP_RELAY_HOST=smtp.su-proveedor.example
SMTP_RELAY_PORT=587
SMTP_RELAY_SECURITY=STARTTLS
SMTP_RELAY_USERNAME=cuenta-autorizada
SMTP_RELAY_PASSWORD=
```

Establezca el secreto con `./configurar.sh --smtp-interno`. Para TLS implícito use el puerto que indique el proveedor, normalmente 465, y `SMTP_RELAY_SECURITY=SSL`. La contraseña se guarda en `.env` con permisos 600 y dentro del contenedor en un mapa SASL con acceso restringido; no se devuelve por la API ni se pasa como argumento de un comando. Consulte [SASL de Postfix](https://www.postfix.org/SASL_README.html).

## Acceso restringido y red

Desde 1.8.3 no existe una red `smtp_net` ni una subred SMTP fija. Postfix comparte la red `default` del proyecto con los demás servicios. Docker asigna la subred y Postfix obtiene las redes conectadas mediante [`mynetworks_style=subnet`](https://www.postfix.org/postconf.5.html#mynetworks_style). Se permite enviar a los contenedores de esa red y a loopback; los demás clientes se rechazan con `permit_mynetworks, reject`. El servicio SMTP no publica puertos en el despliegue normal.

Spring y Postfix se comunican sin autenticación ni TLS dentro de la red del proyecto. No hay credenciales en ese tramo. Si utiliza `./iniciar-dev.sh` con SMTP interno, Spring se conecta a `127.0.0.1:SMTP_INTERNAL_PORT` y el script inicia Postfix junto con la base. Los procesos locales del mismo anfitrión pueden utilizar ese puerto: administre el acceso al equipo.

`SMTP_NETWORK_SUBNET` ya no se genera ni configura la red; si existe en un `.env` anterior, se acepta y se ignora. No conecte contenedores ajenos a la red del proyecto ni utilice `network_mode: host`: Postfix confía en las subredes de sus interfaces.

Al actualizar con `./iniciar.sh`, Compose recrea los servicios afectados. Una red antigua vacía puede permanecer en Docker; después de verificar que no tiene contenedores conectados, puede retirarla con `docker network inspect NOMBRE_DEL_PROYECTO_smtp_net` y `docker network rm NOMBRE_DEL_PROYECTO_smtp_net`, usando el nombre real del proyecto. No borre volúmenes ni use una limpieza global de redes.

## Cola y diagnóstico

### Reinicio por `mailq.1.gz` ausente

Si el registro muestra `chown: cannot access '/usr/share/man/man1/mailq.1.gz'` seguido de `No se pudo preparar Postfix`, el fallo ocurre en `postfix set-permissions`, antes de iniciar el servicio SMTP. La imagen Debian slim excluye manuales al instalar paquetes y puede dejar sin destino los enlaces de manual creados para `mailq` y `newaliases`.

La corrección conserva `/usr/share/man/*` mediante una regla de `dpkg` antes de instalar Postfix. La construcción comprueba que `sendmail.1.gz` exista y que los enlaces `mailq.1.gz` y `newaliases.1.gz` resuelvan. Se mantiene el ajuste estricto de permisos; no se omite su código de error ni se crean manuales vacíos.

Esta corrección ya forma parte de 1.8.3. Conserve `.env` y los volúmenes, y actualice el conjunto completo de archivos de la versión. Si su instalación utiliza Postfix, desde la raíz del proyecto reconstruya y compruebe el servicio:

```bash
docker compose --profile local-smtp build smtp
docker compose --profile local-smtp up -d --no-deps --wait --wait-timeout 180 smtp
docker compose --profile local-smtp ps smtp
```

Después use `./iniciar.sh` o `iniciar.bat` para reanudar el despliegue completo con la configuración guardada. Estos pasos no requieren borrar la base, la cola ni ejecutar `docker compose down -v`.

El resultado esperado es `smtp` saludable. Si persiste otro error, consulte `docker compose --profile local-smtp logs --tail=100 smtp`. La salud SMTP sólo comprueba la respuesta local a EHLO/NOOP; no demuestra entrega externa de correo. El backend no depende de la salud Postfix para arrancar; sus fallos se reflejan en los intentos de la cola de notificaciones.

### Consultar las colas

Hay dos etapas persistentes:

1. La cola del helpdesk, en PostgreSQL, espera que el primer SMTP acepte el aviso.
2. La cola de Postfix, en el volumen `smtp_queue`, espera la entrega al siguiente servidor.

Por eso, **SENT en el helpdesk sólo confirma aceptación por Postfix**. Un destino temporalmente caído puede seguir pendiente en la segunda cola. Para consultar:

```bash
docker compose --profile local-smtp ps smtp
docker compose --profile local-smtp logs --tail=100 smtp
docker compose --profile local-smtp exec smtp postqueue -p
```

Después de corregir un problema de red o del relay, puede solicitar un nuevo intento de la cola Postfix:

```bash
docker compose --profile local-smtp exec smtp postqueue -f
```

Postfix gestiona reintentos y mensajes no entregables. **El remitente con IP no crea un buzón:** este contenedor no recibe respuestas ni rebotes para `soporte@[IP]`. Revise la cola y los registros para detectar fallos de entrega. Puede establecer `MAIL_REPLY_TO` con el buzón de atención real para las respuestas humanas; esa cabecera no cambia el destinatario de los rebotes SMTP. El helpdesk no importa respuestas ni rebotes. No interprete un aviso SENT como confirmación de lectura ni borre y recree avisos para forzar su reenvío.

Los registros se escriben en la salida del contenedor mediante el [mecanismo oficial de registro de Postfix](https://www.postfix.org/MAILLOG_README.html). Pueden contener direcciones y metadatos de entrega; no publique esos registros.

`./detener.sh` conserva la cola. No use `docker compose down -v` si desea conservar datos. El respaldo `respaldar.sh` cubre PostgreSQL, **no el volumen SMTP**. Preserve también `.env` y `smtp_queue` siguiendo el procedimiento de respaldo de volúmenes de su anfitrión. Al restaurar una base antigua revise ambas colas para evitar duplicar notificaciones.

## Recepción de remitentes con IP local

El formato literal es válido en SMTP, pero no garantiza aceptación por cualquier proveedor. Una IP privada no identifica un dominio público verificable ni proporciona por sí misma SPF, DKIM o DMARC. Para destinatarios institucionales, confirme que su servidor admite este remitente y permite al Postfix enviar desde la red de origen. Puede usar el servidor institucional como `SMTP_RELAY_HOST`, conservando `MAIL_FROM=soporte@[IP local]` si su política lo permite.

Si necesita enviar a servicios públicos que exigen un dominio autenticado, el administrador del relay deberá aplicar una política de remitente admitida o se deberá configurar una dirección autorizada en `MAIL_FROM`. El asistente Postfix propone IP local cuando no hay un remitente personalizado; una instalación nueva de 1.8.3 deja el remitente vacío y el correo desactivado hasta elegir proveedor. Las [directrices de remitentes de Google](https://support.google.com/mail/answer/81126?hl=es) explican los requisitos de autenticación y DNS de ese receptor. La imagen no crea dominios, buzones ni claves DKIM.

La configuración de contenedores y los resultados de pruebas constan en [VALIDACION.md](VALIDACION.md). No se han enviado correos a destinatarios externos durante la preparación de esta entrega.

## Actualizar una instalación anterior

Los `.env` existentes mantienen su SMTP y su estado de activación. Para adoptar el interno, preserve datos y configuración, actualice los archivos y ejecute `./configurar.sh --smtp-interno`, seguido de `./iniciar.sh`. Elegir proveedor de correo no requiere una migración de tablas; actualizar la aplicación a 1.8.3 sí aplica las migraciones de negocio pendientes hasta V26.

Para conectar Spring directamente a un proveedor externo, continúa disponible `./configurar.sh --correo`. En ese caso los avisos omiten Postfix, aunque su contenedor esté iniciado.
