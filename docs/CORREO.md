# Correo y notificaciones · versión 1.3.3

El sistema guarda notificaciones junto con cada operación y las envía posteriormente por SMTP. **En 1.8.2 las instalaciones nuevas utilizan Gmail directo, con envío inicialmente desactivado hasta configurar credenciales**, con la IP local como dominio literal del remitente, por ejemplo `soporte@[192.168.1.50]`. Configure la IP real con `./configurar.sh --smtp-interno`. Los `.env` anteriores conservan su configuración y activación. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md) para el servidor Docker, su cola y las condiciones de recepción. Configurar el servidor no envía un mensaje de prueba; al activar `MAIL_ENABLED` y reiniciar, se procesa también la cola pendiente acumulada si no existe una pausa administrativa. Desde 1.3.1 puede [pausar el envío desde la aplicación](DESACTIVAR-CORREO.md).

## Conectar Spring directamente a otro SMTP

Este apartado es la alternativa al Postfix integrado. Desde la carpeta del proyecto:

```bash
./configurar.sh --correo
```

El asistente solicita activación, servidor, puerto, seguridad, autenticación, usuario, contraseña, dirección y nombre del remitente, dirección de respuesta y correo del administrador inicial. La contraseña se introduce de forma oculta y se confirma. Enter conserva un valor existente; `-` vacía campos de texto. Desactivar autenticación vacía usuario y contraseña SMTP. Los cambios sólo se guardan tras validar todo el formulario.

No se realiza una conexión SMTP ni se envía correo durante el asistente. Reinicie con `./iniciar.sh`, o cierre/reabra `iniciar-dev.sh`, para aplicar los cambios. `ADMIN_EMAIL` sólo se utiliza al crear el administrador inicial: para cuentas ya existentes, el administrador debe editar su correo desde **Usuarios**.

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| `MAIL_ENABLED` | `false` (instalación nueva 1.8.2) | Habilita correo en el servidor; la pausa administrativa también debe estar desactivada. |
| `SMTP_HOST` | `smtp.gmail.com` (nuevo) o `smtp` (heredado) | Nombre SMTP o IPv4, sin `smtp://` ni puerto. |
| `SMTP_PORT` | `587` (nuevo) o `25` (heredado) | Puerto del servidor SMTP. |
| `SMTP_SECURITY` | `STARTTLS` (Gmail directo) | `STARTTLS`, `SSL` o `NONE`. |
| `SMTP_AUTH` | `true` (Gmail directo) | Si exige usuario y contraseña. |
| `SMTP_USERNAME` | Vacío | Usuario autorizado por el servidor. |
| `SMTP_PASSWORD` | Vacío | Contraseña SMTP o contraseña de aplicación. |
| `MAIL_FROM` | `soporte@[127.0.0.1]` | Remitente literal inicial; el asistente propone `soporte@[IP local]`. Para un proveedor externo, debe admitir esa dirección. |
| `MAIL_FROM_NAME` | `Soporte SIABUC` (instalación nueva) | Nombre visible, hasta 200 caracteres. |
| `MAIL_REPLY_TO` | Vacío | Dirección individual para recibir respuestas, opcional. |
| `MAIL_POLL_MS` | `15000` | Intervalo de revisión de cola; 1000–3600000 ms. |
| `MAIL_MAX_ATTEMPTS` | `5` | Máximo de intentos automáticos; 1–5. |
| `ADMIN_EMAIL` | Vacío | Correo de la cuenta administrativa inicial. |
| `MAILPIT_SMTP_PORT` | `1025` | Puerto local del sumidero de pruebas. |
| `MAILPIT_UI_PORT` | `8025` | Puerto local de su bandeja web. |

`STARTTLS` exige la actualización a TLS; `SSL` inicia TLS desde la conexión. Use la combinación que indique su proveedor, normalmente 587/STARTTLS o 465/SSL. `NONE` se utiliza para la conexión privada con Postfix, Mailpit local o un relay expresamente autorizado; no lo utilice para enviar credenciales a través de Internet. Se comprueba el nombre del certificado TLS y se fijan tiempos de espera finitos; [Spring documenta por qué deben configurarse los timeouts](https://docs.spring.io/spring-boot/reference/io/email.html).

## Formato seguro de `.env`

Los scripts leen datos, nunca ejecutan el archivo con `source` ni evalúan shell. Valores sencillos pueden escribirse sin comillas; nombres con espacios o secretos con `$`, `#`, comillas dobles y otros símbolos deben encerrarse en **comillas simples**. El asistente escribe el formato adecuado:

```dotenv
MAIL_FROM_NAME='Soporte Técnico SIABUC'
MAIL_ENABLED=false
SMTP_PASSWORD=
```

No se admiten apóstrofes dentro del valor, caracteres de control, saltos de línea ni una barra inversa al final. Las barras inversas interiores se conservan. Las comillas dobles no se usan como delimitador. El archivo conserva permisos `600`; los secretos no aparecen en los mensajes del asistente, en la API de configuración ni en auditoría. No comparta `.env` o la salida expandida de `docker compose config`. Las comillas simples evitan la interpolación de valores según la [documentación de Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

Las restricciones de longitud de contraseñas del helpdesk y PostgreSQL no se aplican como mínimo al secreto SMTP: éste debe coincidir exactamente con la credencial entregada por su proveedor. El máximo admitido es 4096 caracteres. En el relay de Postfix tampoco se admiten espacios en blanco al final del secreto porque `postmap` los recortaría; el asistente los rechaza expresamente, sin modificar el valor silenciosamente.

## Servidor institucional

Solicite a su administrador de correo: host, puerto, seguridad TLS, método de autenticación, cuenta autorizada, dirección remitente y límites de envío. Configure esos valores con el asistente. Si el servidor exige OAuth, use un relay institucional aprobado que acepte el método implementado por este sistema; esta versión no negocia OAuth ni certificados de cliente SMTP.

La cuenta autenticada debe tener permiso para utilizar `MAIL_FROM`. `MAIL_REPLY_TO` puede apuntar al buzón de atención real, pero las respuestas no se importan automáticamente al helpdesk. El personal deberá registrar en el caso lo recibido.

## Gmail y Google Workspace

Cuando la política de la cuenta lo permita, use `smtp.gmail.com`, puerto `587`, `STARTTLS`, autenticación activada, dirección completa como usuario y una **contraseña de aplicación**. La alternativa TLS implícita es puerto `465` con `SSL`. No introduzca la contraseña habitual de Google. Google documenta estas opciones en su [guía de SMTP para aplicaciones](https://knowledge.workspace.google.com/admin/gmail/send-email-from-a-printer-scanner-or-app?hl=en).

Las contraseñas de aplicación requieren verificación en dos pasos y pueden no estar disponibles en cuentas organizacionales o con políticas especiales. Google las revoca al cambiar la contraseña principal. Consulte su [guía oficial de contraseñas de aplicaciones](https://support.google.com/mail/answer/185833?hl=es-419).

En Workspace puede usar `smtp-relay.gmail.com` cuando el administrador haya configurado y autorizado ese relay. La autenticación y el puerto deben coincidir con la política de la organización; el host por sí solo no habilita el servicio. Google recomienda su relay para aplicaciones, como explica la misma guía oficial.

## Microsoft 365

Esta versión **no implementa OAuth/XOAUTH2 ni renovación de tokens**. Microsoft recomienda autenticación moderna y documenta limitaciones de la autenticación básica; no se garantiza que usuario y contraseña funcionen con `smtp.office365.com`. No se requiere desactivar las protecciones del tenant para usar el helpdesk.

Pida a su administrador un relay aprobado compatible: por ejemplo, un servidor institucional intermediario que se encargue de autenticar ante Microsoft 365. Los conectores de relay directo requieren condiciones específicas, como IP pública autorizada o certificado; este sistema no configura esos conectores ni presenta certificados de cliente. Fuentes: [opciones oficiales para enviar desde aplicaciones](https://learn.microsoft.com/en-us/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365) y [autenticación OAuth para SMTP](https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth).

## Prueba local con Mailpit, sin destinatarios reales

Use una **instalación y base de pruebas separadas**. Mailpit captura mensajes localmente y permite inspeccionarlos en el navegador; Mailpit en el perfil de ensayo no entrega a Internet. El servicio Postfix predeterminado sí es un servidor saliente real: al probar con Mailpit asegúrese de cambiar `SMTP_HOST` y `SMTP_PORT` como se indica abajo.

1. Inicie el sumidero desde la raíz del proyecto:

   ```bash
   docker compose --profile mail-test up -d mailpit
   ```

2. Ejecute `./configurar.sh --correo` y use los siguientes valores:

   | Campo | Spring dentro de Docker | Spring nativo/desarrollo |
   | --- | --- | --- |
   | Activar | `true` | `true` |
   | Host | `mailpit` | `127.0.0.1` |
   | Puerto | `1025` | Valor de `MAILPIT_SMTP_PORT` |
   | Seguridad | `NONE` | `NONE` |
   | Autenticación | `false` | `false` |
   | Remitente | `soporte@example.test` | `soporte@example.test` |
   | Correo administrador de prueba | `admin@example.test` | `admin@example.test` |

3. Reinicie la aplicación. En cuentas existentes establezca el correo desde **Usuarios**. Registre una institución de prueba con `cliente@example.test` y asígnele un administrador con correo.
4. Abra **Notificaciones** y solicite una prueba. Sólo se encola para el correo de la cuenta administrativa que la solicita. Cree o actualice un caso para probar avisos al cliente y responsable.
5. Abra **http://127.0.0.1:8025**, o su `MAILPIT_UI_PORT`. Espere el intervalo de cola y compruebe contenido y destinatario de cada mensaje.
6. Agregue una nota interna: el mensaje sólo debe dirigirse al responsable administrativo, sin una copia institucional. Pruebe también cierre de caso, pago y anulación.

Los puertos Mailpit se publican únicamente en `127.0.0.1`, incluso si `BIND_HOST=0.0.0.0`. El volumen `mailpit_data` conserva los mensajes de prueba al detener contenedores. `./detener.sh` detiene también este perfil y conserva los volúmenes. No transporte esos datos de ensayo a producción.

El perfil usa la imagen `axllent/mailpit:v1.27.4`; [Mailpit documenta sus imágenes y puertos](https://mailpit.axllent.org/docs/install/docker/) y que su [SMTP local predeterminado no requiere autenticación ni cifrado](https://mailpit.axllent.org/docs/configuration/smtp/). Este recorrido describe cómo probar; la ejecución realmente realizada consta en [VALIDACION.md](VALIDACION.md).

## Destinatarios y consulta administrativa

- Un evento público de caso genera notificaciones individuales para `institución.email` y para el correo del administrador asignado.
- Una nota interna sólo se dirige al administrador asignado.
- Un pago o su anulación avisa a la institución y al autor original del pago (`ADMIN` o `BILLING`), aunque otro administrador efectúe la anulación.
- En eventos públicos, cuando las direcciones coinciden, se evita duplicar destinatario dentro del evento. Si el correo del responsable coincide con el institucional, un evento interno queda `SKIPPED` para evitar divulgar notas privadas; se deben separar las direcciones antes de reintentarlo.
- Si falta correo o responsable, se conserva el registro `SKIPPED` y su explicación. No se inventan destinatarios.

Los usuarios institucionales no eligen destinatarios arbitrarios. **Notificaciones** es exclusiva para administradores, muestra los últimos 500 registros y permite consultar configuración sin secretos, encolar una prueba propia y reintentar registros fallidos u omitidos cuando su causa puede corregirse. Si faltaba el correo, el reintento consulta el contacto actual de la misma institución o cuenta identificada originalmente; no permite sustituirla por otra entidad. Un aviso omitido porque no existía responsable identificado no puede recuperarse asignando uno después: la nueva asignación sirve para eventos futuros. No se reenvía un registro marcado como `SENT` desde esta acción.

Las direcciones se guardan al crear cada evento. Cambiar después el correo de una institución o cuenta no modifica las notificaciones `PENDING` ya creadas. Los reintentos de avisos `FAILED` conservan su destinatario original.

### Contenido de los avisos de casos

Desde 1.3.3, en **Historial de notificaciones** un administrador puede abrir cada fila con un clic o con **Ver detalle**, leer el correo completo y sus datos de entrega, y usar **← Volver al historial** para recuperar filtros y posición. Abrir el detalle sólo consulta el registro: no envía ni reintenta. Los usuarios institucionales consultan las entradas públicas en el historial de sus propios casos; no acceden a esta cola administrativa.

Desde 1.3.2 cada aviso de apertura, actualización, atención/comentario, anulación y cambio de estado incluye lo siguiente; los avisos de **Caso tomado** añadidos en 1.3.3 usan el mismo formato:

- Número y título del caso, institución cliente y nombre del administrativo asignado que atiende.
- Estado **Abierto** o **Cerrado**, canal principal y usuario que registró el evento. El responsable asignado puede ser distinto de quien registró la actualización.
- **Descripción actual del caso**, tomada de la ficha en el momento del evento, y **Detalle de la actualización**, con el comentario, atención o motivo correspondiente.
- La leyenda **«Para mayor información, consulte la plataforma Helpdesk.»**

Ejemplo ilustrativo de un aviso de cierre:

```text
SIABUC — Notificación de soporte

Caso: #123
Institución cliente: Biblioteca de ejemplo
Administrativo que atiende: Ana Pérez
Título: Consulta de catálogo
Estado: Cerrado
Canal principal: Correo
Evento: Cambio de estado
Registrado por: administrador

Descripción actual del caso:
La búsqueda del catálogo no muestra los registros recientes.

Detalle de la actualización:
Se confirmó que la búsqueda funciona después de actualizar el índice.

Para mayor información, consulte la plataforma Helpdesk.
```

Los datos del cuerpo quedan guardados con el evento: una edición posterior no reescribe avisos anteriores ni sus reintentos. Si no hay responsable, se indica «Sin administrativo asignado»; si la cuenta asignada ya está desactivada al registrar el evento, se conserva su nombre pero el nuevo aviso para esa cuenta queda omitido. Los avisos encolados antes de desactivar una cuenta mantienen su destinatario original. Las notas internas y sus anulaciones mantienen su visibilidad exclusiva para personal autorizado. Los avisos de pagos conservan su formato financiero, sin un estado de caso que no les corresponde.

## Cola, fallos y entrega

Los estados son `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED` y `SUPPRESSED`. Guardar un caso, consumo o pago y crear su notificación se realiza en la misma transacción; una caída de SMTP no deshace una atención ni una acreditación. El envío se ejecuta fuera de esa transacción, con reintentos limitados y recuperación de bloqueos vencidos tras una caída.

La semántica es **al menos una vez**: si SMTP aceptó el mensaje pero el proceso se detuvo antes de confirmar el resultado en PostgreSQL, un reintento puede producir un duplicado. `SENT` significa aceptación por el servidor SMTP; no certifica llegada a bandeja principal, lectura ni ausencia de rebote posterior. No hay recepción automática de rebotes ni sincronización IMAP.

Con `MAIL_ENABLED=false` la cola queda conservada y no se procesa. Al activar correo pueden salir notificaciones pendientes de eventos anteriores: revise la cola y use un entorno de pruebas separado antes de habilitar un servidor real.

## Remitente y diagnóstico

La configuración predeterminada utiliza IP local, por ejemplo `soporte@[192.168.1.50]`, y depende de que el receptor admita esa identidad. No crea un buzón ni un dominio público autenticado. Si configura alternativamente un dominio público para entregar mediante un proveedor externo, su administrador debe aplicar SPF, DKIM y DMARC según las políticas de ese servicio. Estas políticas se establecen en DNS/servidor de correo, no dentro del helpdesk. Google describe su papel en las [directrices oficiales para remitentes](https://support.google.com/mail/answer/81126?hl=es).

| Problema | Revisar |
| --- | --- |
| Pendientes sin intentos | `MAIL_ENABLED`, pausa administrativa, reinicio aplicado e intervalo `MAIL_POLL_MS`. |
| Autenticación rechazada | Usuario SMTP, secreto vigente, política del proveedor y si exige OAuth. |
| Error TLS | Combinación puerto/STARTTLS/SSL, nombre del host y certificado del servidor. |
| Registro `SKIPPED` | Corrija el correo de la entidad original o separe las direcciones para avisos internos. Si originalmente no había responsable identificado, asigne uno para eventos futuros; ese aviso anterior no se recupera. |
| `SENT` pero sin mensaje visible | Antispam, cuarentena, permisos de remitente y registros del servidor SMTP. |
| Rechazo del remitente con IP | Política del receptor/relay para direcciones como `soporte@[IP]`; que admita sintaxis literal no garantiza recepción. |
| Sólo falla desde Docker | Host SMTP accesible desde el contenedor; `localhost` apunta al propio contenedor. |

La configuración de correo y los secretos se conservan en `.env`/entorno. La copia PostgreSQL incluye la cola del helpdesk y su historial, pero no la cola del volumen `smtp_queue` de Postfix; después de restaurar, revise `MAIL_ENABLED` y la pausa administrativa restaurada antes de iniciar el procesamiento.

## Desactivar sin enviar avisos acumulados

Desde 1.3.1 el control administrativo de [desactivación de correo](DESACTIVAR-CORREO.md) marca SUPPRESSED los avisos recuperables anteriores y los generados durante la pausa. Al reactivar sólo se envían eventos nuevos. Se conserva historial, no se permite reintentar SUPPRESSED y pruebas/reintentos están bloqueados mientras se pausa. Esto es distinto de MAIL_ENABLED=false por sí solo, que conserva la cola para un envío posterior.

## Novedad 1.8.2: Gmail sin Postfix

Ejecute `./configurar.sh --gmail-directo` o `configurar.bat --gmail-directo` para introducir la cuenta y una nueva contraseña de aplicación por entrada oculta. El cambio no comprueba autenticación ni envía correo y conserva las opciones `SMTP_RELAY_*` antiguas solo como datos de configuración inactivos. Spring Boot enviará directamente a `smtp.gmail.com:587` mediante STARTTLS. La cuenta Workspace debe permitir SMTP con contraseña de aplicación. No publique el archivo `.env`.

Antes de pasar de `SMTP_HOST=smtp` a Gmail directo inspeccione y resuelva la cola Postfix; el volumen `smtp_queue` permanece intacto aunque el servicio deje de iniciarse por defecto. Consulte [migración 1.8.2](VERSION-1.8.2.md).
