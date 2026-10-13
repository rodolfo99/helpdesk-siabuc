# Manual de uso

## Acceso y cuentas

La aplicación abre en la pantalla de inicio de sesión. El administrador inicial se configura mediante `.env`; las cuentas posteriores se crean desde **Usuarios**. Las contraseñas deben tener al menos 12 caracteres y no exceder 72 bytes UTF-8, límite de BCrypt. Una contraseña con caracteres acentuados o emojis puede ocupar más bytes que caracteres.

La sesión termina tras 30 minutos de inactividad del servidor o al cerrar sesión. Reiniciar Spring invalida las sesiones y obliga a identificarse de nuevo. **Mi cuenta** permite cambiar la contraseña propia introduciendo la actual: se conserva esa sesión y se revocan las demás. Si un administrador restablece la contraseña de otra cuenta, sus sesiones anteriores se revocan.

| Acción | Administrador (`ADMIN`) | Usuario (`USER`) | Cobranza (`BILLING`) |
| --- | --- | --- | --- |
| Ver instituciones/saldos | Todas | Sólo la propia activa | Todas, sin notas administrativas |
| Crear, editar y dar de baja instituciones | Sí | No | No |
| Ajustar manualmente presupuestos | Sí | No | No |
| Administrar usuarios | Sí | No | No |
| Ver historial de casos | Todos | Los de su institución | No |
| Abrir caso con saldo | Cualquier institución activa | Su institución | No |
| Comentar caso abierto | Sí | Público y sin consumo | No |
| Registrar consumo | Sí | No | No |
| Notas internas | Sí | No | No |
| Editar, asignar, cerrar/reabrir casos | Sí | No | No |
| Anular atención | Sí | No | No |
| Registrar y consultar pagos | Sí | No | Sí |
| Anular pagos | Sí | No | No |
| Auditoría y notificaciones | Sí | No | No |
| Cambiar su propia contraseña | Sí | Sí | Sí |

Los permisos se verifican en Spring en cada operación. Ocultar un botón en Angular no es el control de acceso. Varios usuarios de una misma institución comparten sus saldos y el historial institucional; no existe aislamiento individual por usuario dentro de esa institución.

## Instituciones y presupuesto

En **Instituciones → Nueva institución**, capture nombre, contacto, correo, teléfono y las notas administrativas necesarias. Asigne los dos presupuestos en campos separados de horas y minutos. Los minutos de cada campo deben estar entre 0 y 59.

Para cada canal se distinguen:

- **Asignado:** total histórico autorizado a la institución.
- **Utilizado:** suma del tiempo registrado y no anulado.
- **Disponible:** asignado menos utilizado.

Ejemplo: asignado de correo 10 h = 600 min; una atención de 1 h 25 min utiliza 85 min; quedan 515 min = 8 h 35 min. Remoto no cambia. Para añadir otras 5 h al presupuesto, cambie el **total asignado a 15 h**, no a 5 h. No se permite reducir el presupuesto por debajo de lo ya utilizado.

El consumo inicial de una institución nueva es cero. Si necesita incorporar consumo histórico, registre casos y atenciones con una descripción que indique que son antecedentes migrados. Los registros conservan la fecha real de captura; esta versión no permite falsificar ni retroceder fechas.

Puede asignar un **responsable predeterminado** a la institución: debe ser una cuenta `ADMIN` activa. Compruebe también el correo de contacto institucional y el del responsable, pues se utilizan como destinatarios de los avisos. Las cuentas `BILLING` no pueden ser responsables de soporte.

**Dar de baja** equivale a borrar la institución del uso operativo: sus usuarios pierden acceso y ya no se pueden abrir casos ni registrar consumo nuevo. Los casos y su historia se conservan para consulta del administrador. Puede reactivarla mediante **Editar → Activa**. No hay borrado físico de instituciones o casos desde la interfaz.

## Administración de usuarios

En **Usuarios**, cree cada cuenta con nombre visible, identificador, contraseña, rol y estado. Los usuarios `USER` requieren una institución; administradores y personal `BILLING` no están vinculados a una. Capture también el correo de cada cuenta que deba recibir avisos. El identificador de cuenta no se modifica después del alta.

Al editar una cuenta, deje la contraseña vacía para conservarla o capture una nueva para restablecerla. Puede modificar su nombre visible e inactivarla; sus registros anteriores conservan la autoría. El sistema impide desactivar al último administrador activo. El rol, la institución y el identificador son inmutables después del alta. Para cambiar el ámbito de acceso de una persona, cree una cuenta nueva con el rol/institución correspondiente y desactive la anterior, preservando el historial de cada una.

Una institución inactiva bloquea también a sus usuarios aunque éstos mantengan la marca de cuenta activa. Las notas administrativas de instituciones y las entradas internas de casos no se entregan en las vistas de usuario.

## Casos y atención

En **Casos**, seleccione una institución, canal inicial, título y descripción. Para abrir un caso el canal elegido debe tener al menos un minuto disponible. La creación no reserva ni descuenta minutos: el consumo ocurre cuando un administrador registra la atención.

El administrador puede elegir un responsable activo. Si no lo elige al crear el caso, se toma el responsable institucional y, si tampoco hay uno, al administrador creador. Un usuario institucional no puede asignar responsables; se usa el configurado en su institución. Los casos históricos pueden estar sin asignación hasta que un administrador los edite. El responsable puede cambiarse incluso en un caso cerrado; el cambio conserva historial. Una reasignación institucional no cambia automáticamente todos sus casos existentes.

Dentro del caso, use las acciones de seguimiento:

1. **Registrar atención:** describa lo realizado, elija correo o remoto e indique horas/minutos. Puede atender por cualquiera de los dos canales y cada registro descuenta la bolsa correspondiente, independientemente del canal inicial del caso.
2. **Agregar comentario:** actualice información sin consumo. Los usuarios sólo pueden usar esta modalidad pública.
3. **Nota interna:** conserve observaciones administrativas que no debe ver la institución. Únicamente administradores pueden crearlas y consultarlas.
4. **Cerrar / reabrir:** el administrador indica un motivo. Cerrar no consume minutos y está permitido aunque no quede saldo. Reabrir exige saldo en el canal inicial del caso.
5. **Editar caso:** el administrador puede corregir título, descripción inicial y canal; la acción queda auditada. No cambia los canales ni minutos de atenciones existentes.

Las entradas incluyen fecha, autor, descripción, canal, minutos y condición pública/interna. Las fechas se almacenan en UTC y la interfaz las presenta en el formato local del navegador.

Un caso cerrado no admite atenciones ni comentarios nuevos. Debe reabrirse para continuar su seguimiento. Con saldo agotado se pueden conservar y consultar casos anteriores, comentar casos aún abiertos sin consumo y cerrar casos; no se autoriza nuevo consumo ni crear/reabrir un caso del canal agotado. Agotar correo no impide utilizar remoto cuando éste mantiene saldo.

## Corrección de consumos e historial

Para corregir una atención, el administrador selecciona **Anular**, escribe una razón y confirma. El registro sigue visible marcado como anulado y sus minutos se devuelven una sola vez. Después puede crear una atención correcta, siempre que el caso esté abierto.

No se edita destructivamente una atención ni se elimina de la cronología. Dos solicitudes simultáneas para anular la misma entrada no duplican el reintegro. Una anulación sigue siendo posible en casos cerrados para corregir un error administrativo; no implica reabrir automáticamente el caso.

La pantalla **Auditoría** permite al administrador revisar quién realizó cambios de instituciones, usuarios, casos, estados y consumos. Presenta los últimos 500 eventos de auditoría; los registros completos permanecen en PostgreSQL. El historial de cada caso es independiente de ese límite de presentación.

## Cobranza y acreditación por pagos

En **Cobranza**, `ADMIN` y `BILLING` pueden consultar pagos y registrar dinero recibido de una institución activa. Capture referencia, importe positivo con máximo dos decimales, moneda de tres letras (por ejemplo `MXN`), fecha, método, descripción y minutos comprados de correo/remoto. Al menos un canal debe recibir minutos. La referencia es única por institución, sin distinguir mayúsculas o espacios extremos, incluso si posteriormente se anula el pago.

Al guardar, el pago y el aumento de tiempo asignado se confirman en una misma transacción. No cambia lo ya consumido. Por ejemplo: si quedan 20 min de correo y registra un pago que compra 120 min, el disponible pasa a 140 min. El importe monetario no se convierte automáticamente a minutos: el administrativo captura los minutos autorizados según las condiciones comerciales.

Un identificador de solicitud evita acreditar dos veces al reintentar una misma operación; además, una referencia ya registrada no se acepta en otro pago de esa institución. Si recibe un error de comunicación, consulte los pagos antes de iniciar otra captura con una referencia distinta.

Sólo `ADMIN` puede **anular** un pago, indicando el motivo. Debe quedar disponible todo el tiempo acreditado por ese pago, en ambos canales, para retirarlo. Si parte de esa capacidad ya no está disponible, la anulación se rechaza sin modificar el pago ni los saldos. La anulación se permite una sola vez y conserva autor, fecha, importes y motivo. No devuelve dinero mediante un banco: registra una corrección administrativa.

Los ajustes manuales de presupuesto siguen disponibles para `ADMIN` y no crean un pago ficticio. Para una recarga respaldada por dinero recibido, utilice Cobranza. El módulo no es una pasarela de cobro ni emite CFDI/factura fiscal.

## Avisos y notificaciones

Cuando SMTP está configurado y activado, los cambios públicos de casos, incluido su cierre, generan avisos al correo institucional y al administrador asignado. Las notas internas sólo avisan al responsable; si su dirección coincide con la institucional, se omiten hasta separar las direcciones para preservar su privacidad. Registrar o anular un pago avisa a la institución y al autor original del pago, aunque otro administrador lo anule. Las direcciones iguales se deduplican en eventos públicos.

En **Historial de atención**, el usuario institucional ve el recordatorio: «Revisa tu bandeja de entrada y la carpeta de correo no deseado para consultar las notificaciones de este caso». Aparece tanto en casos abiertos como cerrados.

El administrador puede revisar **Notificaciones**, solicitar una prueba para su propia cuenta y reintentar fallidos u omitidos cuando su causa sea recuperable. Si faltaba correo, se resuelve el contacto de la misma entidad identificada originalmente. Si no existía responsable identificado, asignarlo después sólo habilita avisos de eventos futuros; no recupera ese aviso anterior. Cambiar un correo no modifica mensajes pendientes que ya guardaron su destinatario. Los avisos enviados quedan en el historial. `SENT` indica aceptación del servidor SMTP, no lectura del destinatario.

La instalación nueva usa Postfix interno. Sus cambios de identidad/remitente/relay se hacen con `./configurar.sh --smtp-interno`; para conectar Spring directamente a un proveedor se usa `./configurar.sh --correo`. Reinicie después de guardar. Con Postfix, un aviso SENT puede seguir pendiente en la cola del servidor SMTP; consulte [SMTP-INTERNO.md](SMTP-INTERNO.md). La pantalla muestra valores de conexión sin revelar la contraseña. Consulte [CORREO.md](CORREO.md) para configuración, cola persistente, reintentos y pruebas locales Mailpit.

## Ejemplo de aceptación funcional

Con una institución de prueba que tenga 1 h de correo y 30 min remotos:

1. Abra un caso por correo: quedan 60 min.
2. Registre 20 min por correo: quedan 40 min de correo y 30 min remotos.
3. Registre 15 min remotos en ese mismo caso: quedan 40 min de correo y 15 min remotos.
4. Intente registrar 41 min de correo: el sistema debe rechazarlo y conservar ambos saldos.
5. Anule la atención de 20 min: correo vuelve a 60 min y la entrada sigue en el historial.
6. Agregue una nota interna y acceda como usuario institucional: la nota no debe aparecer.
7. Cierre el caso: no se pueden agregar entradas hasta reabrirlo.
8. Acceda como usuario de otra institución: no debe poder ver ese caso.

Este recorrido es una guía de uso y de aceptación manual. Los resultados de comprobaciones realmente ejecutadas se encuentran en [VALIDACION.md](VALIDACION.md).
