# Servidor SMTP interno · versión 1.2

El servicio Docker `smtp` contiene **Postfix para enviar correo**. Spring entrega allí los avisos y Postfix los transmite al servidor del destinatario o a un relay autorizado. No proporciona buzones, IMAP, POP3 ni una bandeja web. El puerto SMTP no se publica hacia Internet.

## Inicio predeterminado

En una instalación nueva, `./iniciar.sh` genera `.env`, construye Postfix e inicia PostgreSQL, SMTP, Spring y Angular. Spring usa estos valores:

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
SMTP_NETWORK_SUBNET=172.30.55.0/24
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
| Directa, predeterminada | Spring → Postfix → servidor MX del destinatario | `SMTP_RELAY_HOST` vacío. Salida TCP 25 y resolución DNS disponibles. |
| Mediante relay | Spring → Postfix → relay autorizado → destinatario | Host, puerto, TLS y credenciales que facilite el proveedor o administrador. |

Postfix distingue [entrega directa y uso de relay](https://www.postfix.org/BASIC_CONFIGURATION_README.html). La entrega directa depende de la red y de la aceptación del servidor remoto. Un remitente con IP privada puede ser aceptado por un servidor institucional configurado para ello, pero los servicios de correo externos pueden rechazarlo. En redes con puerto 25 bloqueado o direcciones dinámicas, configure un relay autorizado que acepte el remitente solicitado. La aplicación no puede eliminar las restricciones del receptor o del proveedor de Internet.

En modo directo, Postfix usa TLS oportunista (`smtp_tls_security_level=may`): cifra cuando el destino lo ofrece. Para un relay con STARTTLS o SSL exige certificado válido para su nombre (`secure`, coincidencia `nexthop`). Estas políticas siguen la [documentación TLS de Postfix](https://www.postfix.org/TLS_README.html).

## Variables del servidor Postfix

Estas variables son distintas de `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME` y `SMTP_PASSWORD`, que controlan la conexión de **Spring** a su primer servidor SMTP.

| Variable | Predeterminado | Significado |
| --- | --- | --- |
| `SMTP_LOCAL_IP` | `127.0.0.1` | IPv4 del anfitrión utilizada en el saludo saliente. El asistente deriva el remitente de esta IP. |
| `SMTP_HOSTNAME` | `mail.siabuc.internal` | Nombre técnico completo de Postfix; no es el dominio del remitente. |
| `SMTP_INTERNAL_PORT` | `2525` | Puerto publicado únicamente en `127.0.0.1`; dentro de Docker es 25. |
| `SMTP_NETWORK_SUBNET` | `172.30.55.0/24` | Red privada dedicada a Spring y Postfix. Admite red IPv4 RFC1918 de /24 a /28. |
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

Sólo se permite enviar a los clientes de la red Docker dedicada y a loopback; los demás clientes se rechazan. La configuración sigue el [control de acceso SMTP de Postfix](https://www.postfix.org/SMTPD_ACCESS_README.html). No se usa una red permitida universal ni se publica el servicio en `0.0.0.0`.

Spring y Postfix se comunican sin autenticación ni TLS dentro de esa red privada. No hay credenciales en ese tramo. Si utiliza `./iniciar-dev.sh`, Spring se conecta a `127.0.0.1:SMTP_INTERNAL_PORT` y el script inicia Postfix junto con la base. Los procesos locales del mismo anfitrión pueden utilizar ese puerto: administre el acceso al equipo.

Si Docker informa solapamiento de direcciones, elija otra subred privada libre en `SMTP_NETWORK_SUBNET`. Debe conservar la misma variable tanto para IPAM como para la lista de confianza del contenedor; Compose lo hace automáticamente. No conecte contenedores ajenos a esa red.

## Cola y diagnóstico

Hay dos etapas persistentes:

1. La cola del helpdesk, en PostgreSQL, espera que el primer SMTP acepte el aviso.
2. La cola de Postfix, en el volumen `smtp_queue`, espera la entrega al siguiente servidor.

Por eso, **SENT en el helpdesk sólo confirma aceptación por Postfix**. Un destino temporalmente caído puede seguir pendiente en la segunda cola. Para consultar:

```bash
docker compose ps smtp
docker compose logs --tail=100 smtp
docker compose exec smtp postqueue -p
```

Después de corregir un problema de red o del relay, puede solicitar un nuevo intento de la cola Postfix:

```bash
docker compose exec smtp postqueue -f
```

Postfix gestiona reintentos y mensajes no entregables. **El remitente con IP no crea un buzón:** este contenedor no recibe respuestas ni rebotes para `soporte@[IP]`. Revise la cola y los registros para detectar fallos de entrega. Puede establecer `MAIL_REPLY_TO` con el buzón de atención real para las respuestas humanas; esa cabecera no cambia el destinatario de los rebotes SMTP. El helpdesk no importa respuestas ni rebotes. No interprete un aviso SENT como confirmación de lectura ni borre y recree avisos para forzar su reenvío.

Los registros se escriben en la salida del contenedor mediante el [mecanismo oficial de registro de Postfix](https://www.postfix.org/MAILLOG_README.html). Pueden contener direcciones y metadatos de entrega; no publique esos registros.

`./detener.sh` conserva la cola. No use `docker compose down -v` si desea conservar datos. El respaldo `respaldar.sh` cubre PostgreSQL, **no el volumen SMTP**. Preserve también `.env` y `smtp_queue` siguiendo el procedimiento de respaldo de volúmenes de su anfitrión. Al restaurar una base antigua revise ambas colas para evitar duplicar notificaciones.

## Recepción de remitentes con IP local

El formato literal es válido en SMTP, pero no garantiza aceptación por cualquier proveedor. Una IP privada no identifica un dominio público verificable ni proporciona por sí misma SPF, DKIM o DMARC. Para destinatarios institucionales, confirme que su servidor admite este remitente y permite al Postfix enviar desde la red de origen. Puede usar el servidor institucional como `SMTP_RELAY_HOST`, conservando `MAIL_FROM=soporte@[IP local]` si su política lo permite.

Si necesita enviar a servicios públicos que exigen un dominio autenticado, el administrador del relay deberá aplicar una política de remitente admitida o se deberá configurar una dirección autorizada en `MAIL_FROM`. Esto es una alternativa de interoperabilidad: el valor predeterminado de esta entrega sigue siendo la IP local. Las [directrices de remitentes de Google](https://support.google.com/mail/answer/81126?hl=es) explican los requisitos de autenticación y DNS de ese receptor. La imagen no crea dominios, buzones ni claves DKIM.

La configuración de contenedores y los resultados de pruebas constan en [VALIDACION.md](VALIDACION.md). No se han enviado correos a destinatarios externos durante la preparación de esta entrega.

## Actualizar una instalación anterior

Los `.env` existentes mantienen su SMTP y su estado de activación. No se cambia automáticamente el proveedor de una instalación 1.0/1.1. Para adoptar el interno, preserve datos y configuración, actualice los archivos y ejecute `./configurar.sh --smtp-interno`, seguido de `./iniciar.sh`. El cambio no requiere una migración nueva de tablas.

Para conectar Spring directamente a un proveedor externo, continúa disponible `./configurar.sh --correo`. En ese caso los avisos omiten Postfix, aunque su contenedor esté iniciado.
