# Manual de uso — SIABUC Helpdesk 1.8.3

Esta guía resume la operación vigente. El [manual detallado de SIABUC Helpdesk 1.8.3](MANUAL-EXTENDIDO-1.8.3.md) desarrolla la instalación, los recorridos por rol, los casos de uso y la resolución de problemas. Las [notas 1.8.3](VERSION-1.8.3.md) explican la actualización y sus límites de validación.

El soporte utiliza cinco estados: **Recibido**, **En atención**, **Requiere respuesta del cliente**, **Resuelto** y **Cerrado**. En el detalle se cambian mediante **Cambiar estado**, seleccionando el destino y escribiendo el motivo. **Pendientes de resolver** agrupa los primeros tres; Resuelto y Cerrado se muestran separados.

Guías complementarias:

- Atención: [clasificación, ficha técnica y SLA](CLASIFICACION-SLA.md), [adjuntos y recorridos del caso](MANUAL-EXTENDIDO-1.8.3.md), [cronómetro y agenda](CRONOMETRO-AGENDA.md), [rol Técnico](ROL-TECNICO.md), [vistas y alertas](VISTAS-ALERTAS.md), [plantillas](PLANTILLAS-RESPUESTA.md), [incidencias](INCIDENCIAS.md), [campana y saldo](AVISOS-INTERNOS.md) y [reportes Excel/PDF](REPORTES.md).
- Cobranza: [pólizas y pagos](POLIZAS-COBRANZA.md), [comprobantes](COMPROBANTES-PAGO.md), [reportes financieros](REPORTES-COBRANZA.md), [cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md), [validación de solicitudes](SOLICITUDES-PAGO.md), [portal institucional](PORTAL-FINANCIERO.md) y [recibos administrativos](RECIBOS-PAGO.md).
- Conocimiento, calidad y operación: [soluciones publicadas](BASE-CONOCIMIENTOS.md), [encuestas](ENCUESTAS-SATISFACCION.md), [correo](CORREO.md) y [respaldos automáticos](RESPALDOS-AUTOMATICOS.md).

El portal financiero de clientes y el calendario de respaldos comienzan **desactivados por defecto**; Administración los habilita expresamente. Las cuentas de soporte y los respaldos manuales mantienen su operación independiente.

## Acceso y cuentas

La aplicación abre en la pantalla de inicio de sesión. El administrador inicial se configura mediante `.env`; las cuentas posteriores se crean desde **Usuarios**. Las contraseñas deben tener al menos 12 caracteres y no exceder 72 bytes UTF-8, límite de BCrypt. Una contraseña con caracteres acentuados o emojis puede ocupar más bytes que caracteres.

La sesión termina tras 30 minutos de inactividad del servidor o al cerrar sesión. Reiniciar Spring invalida las sesiones y obliga a identificarse de nuevo. **Mi cuenta** permite cambiar la contraseña propia introduciendo la actual: se conserva esa sesión y se revocan las demás. Si un administrador restablece la contraseña de otra cuenta, sus sesiones anteriores se revocan.

| Acción | Administrador (`ADMIN`) | Usuario (`USER`) | Cobranza (`BILLING`) | Técnico (`TECH`) |
| --- | --- | --- | --- | --- |
| Ver instituciones/saldos | Todas | Sólo la propia activa | Todas, sin notas administrativas | Todas, sin notas administrativas |
| Crear, editar y dar de baja instituciones | Sí | No | No | No |
| Ajustar manualmente presupuestos | Sí | No | No | No |
| Administrar usuarios | Sí | No | No | No |
| Ver historial de casos | Todos | Los de su institución | No | Todos |
| Abrir caso con saldo | Cualquier institución activa | Su institución | No | Cualquier institución activa; asignado a sí mismo |
| Comentar caso activo | Sí | Público y sin consumo | No | Sí |
| Registrar consumo | Sí | No | No | Sí |
| Notas internas | Sí | No | No | Sí |
| Editar, asignar y cambiar estados de casos | Sí | No | No | Editar/cambiar estados y tomar sin responsable; no reasignar |
| Anular atención | Sí | No | No | No |
| Registrar pagos directos y acreditar soporte | Sí | No | Sí | No |
| Consultar pagos y resumen de pólizas | Sí | Propios, sólo con portal financiero habilitado | Sí | No |
| Gestionar pólizas e historial financiero interno | Sí | No | Sí | No |
| Adjuntar comprobantes a pagos ya registrados | Sí | No | Sí | No |
| Descargar comprobantes y recibos administrativos | Sí | Propios, sólo con portal financiero habilitado | Sí | No |
| Crear solicitudes y adjuntar evidencia pendiente de validación | Sí | Propias, sólo con portal financiero habilitado | Sí | No |
| Aprobar o rechazar solicitudes de pago | Sí | No | Sí | No |
| Gestionar cotizaciones, renovaciones y avisos de vencimiento | Sí | No | Sí | No |
| Habilitar el portal financiero por institución | Sí | No | No | No |
| Consultar/exportar reportes financieros | Sí | No | Sí | No |
| Consultar/exportar reportes técnicos | Sí | No | No | No |
| Anular pagos | Sí | No | No | No |
| Auditoría y cola/configuración de correo | Sí | No | No | No |
| Campana de avisos propios | Casos según asignación, saldo y alertas SLA | Casos y saldos de su institución | Avisos de saldo | Casos según asignación, saldo y alertas SLA |
| Consultar artículos publicados de conocimiento | Sí | Sí | No | Desde el caso y por API; pantalla global limitada |
| Editar/publicar artículos y vincular soluciones a casos | Sí | No; consulta vínculos publicados de casos propios | No | Sólo vincular soluciones publicadas; sin edición editorial |
| Responder encuesta de caso cerrado | No | Una respuesta por caso de su institución | No | No |
| Consultar reportes y exportar satisfacción | Sí | No | No | No |
| Configurar o solicitar respaldos desde la plataforma | Sí | No | No | No |
| Cambiar su propia contraseña | Sí | Sí | Sí | Sí |

Los permisos se verifican en Spring en cada operación. Ocultar un botón en Angular no es el control de acceso. Varios usuarios de una misma institución comparten sus saldos y el historial institucional; no existe aislamiento individual por usuario dentro de esa institución.

## Instituciones y presupuesto

En **Instituciones → Nueva institución**, capture nombre, contacto, correo, teléfono y las notas administrativas necesarias. Asigne los dos presupuestos en campos separados de horas y minutos. Los minutos de cada campo deben estar entre 0 y 59.

Para cada canal se distinguen:

- **Asignado:** total histórico autorizado a la institución.
- **Utilizado:** suma del tiempo registrado y no anulado.
- **Disponible:** asignado menos utilizado.

Ejemplo: asignado de correo 10 h = 600 min; una atención de 1 h 25 min utiliza 85 min; quedan 515 min = 8 h 35 min. Remoto no cambia. Para añadir otras 5 h al presupuesto, cambie el **total asignado a 15 h**, no a 5 h. No se permite reducir el presupuesto por debajo de lo ya utilizado.

Ese ajuste manual es independiente de los pagos. Si las horas se adquieren mediante Cobranza, registre el pago con sus minutos y deje que el sistema los acredite: no aumente también el presupuesto manualmente por la misma compra.

El consumo inicial de una institución nueva es cero. Si necesita incorporar consumo histórico, registre casos y atenciones con una descripción que indique que son antecedentes migrados. Los registros conservan la fecha real de captura; esta versión no permite falsificar ni retroceder fechas.

Puede asignar un **responsable predeterminado** a la institución: debe ser una cuenta `ADMIN` o `TECH` activa. Compruebe también el correo de contacto institucional y el del responsable, pues se utilizan como destinatarios de los avisos. Las cuentas `BILLING` y `USER` no pueden ser responsables de soporte.

**Dar de baja** desactiva la institución: sus usuarios pierden acceso y ya no se pueden abrir casos ni registrar consumo nuevo. Los casos y su historia se conservan para consulta del personal de soporte. Puede reactivarla mediante **Editar → Activa**. No hay borrado físico de instituciones o casos desde la interfaz.

## Administración de usuarios

En **Usuarios**, cree cada cuenta con nombre visible, identificador, contraseña, rol y estado. Los usuarios `USER` requieren una institución; las cuentas `ADMIN`, `TECH` y `BILLING` no están vinculadas a una. Capture también el correo de cada cuenta que deba recibir avisos. El identificador de cuenta no se modifica después del alta.

Al editar una cuenta, deje la contraseña vacía para conservarla o capture una nueva para restablecerla. Puede modificar su nombre visible e inactivarla; sus registros anteriores conservan la autoría. El sistema impide desactivar al último administrador activo. El rol, la institución y el identificador son inmutables después del alta. Para cambiar el ámbito de acceso de una persona, cree una cuenta nueva con el rol/institución correspondiente y desactive la anterior, preservando el historial de cada una.

Una institución inactiva bloquea también a sus usuarios aunque éstos mantengan la marca de cuenta activa. Las notas administrativas de instituciones y las entradas internas de casos no se entregan en las vistas de usuario.

## Casos y atención

En **Casos**, seleccione una institución, canal inicial, título y descripción. Para abrir un caso el canal elegido debe tener al menos un minuto disponible. La creación no reserva ni descuenta minutos: el consumo ocurre cuando Administración o un técnico registra una atención con minutos o confirma un cronómetro.

Administración puede elegir un responsable activo `ADMIN` o `TECH`. Si no lo elige al crear el caso, se toma el responsable institucional y, si tampoco hay uno, al administrador creador. Un técnico crea casos asignados a sí mismo. Un usuario institucional no puede asignar responsables; se usa el configurado en su institución, si continúa activo. Los casos históricos pueden estar sin asignación hasta que un administrador los edite. Administración puede cambiar al responsable incluso en un caso Resuelto o Cerrado; el cambio conserva historial. Una reasignación institucional no cambia automáticamente todos sus casos existentes.

Un cliente puede acceder a **Casos de soporte → Nuevo caso**, describir su solicitud, revisar el resumen y crearla para su propia institución si tiene saldo en el canal elegido. También dispone del acceso de nueva solicitud en su resumen institucional. El caso nuevo queda **Recibido**. Si la institución no tiene responsable predeterminado activo, queda sin asignación. Administración o un técnico puede filtrar **Asignación → Sin responsable**, abrirlo y pulsar **Tomar caso**. El sistema lo asigna a esa cuenta y, si estaba Recibido, lo coloca en **En atención**. Registra el movimiento y el motivo en el historial y auditoría, y genera las notificaciones según la configuración vigente. **Mis casos** consulta los asignados a la cuenta de soporte conectada.

Sólo se pueden tomar casos activos de instituciones activas que estén sin responsable. Si otro integrante de soporte se adelantó, se informa el conflicto y se muestra la asignación vigente. Tomar de nuevo un caso ya propio no duplica el historial ni los avisos. Las instituciones con responsable predeterminado conservan su asignación automática. El cliente no puede tomar ni reasignar casos.

Dentro del caso, use las acciones de seguimiento:

1. **Registrar atención:** describa lo realizado, elija correo o remoto e indique horas/minutos. Puede atender por cualquiera de los dos canales y cada registro descuenta la bolsa correspondiente, independientemente del canal inicial del caso.
2. **Agregar comentario:** actualice información sin consumo. Los usuarios sólo pueden usar esta modalidad pública.
3. **Nota interna:** conserve observaciones administrativas que no debe ver la institución. Administración y Técnicos pueden crearlas y consultarlas; el cliente no las recibe.
4. **Cambiar estado:** Administración o Técnico selecciona el destino, indica un motivo y pulsa **Confirmar cambio**. No consume minutos. Resolver o cerrar está permitido aunque no quede saldo. Reabrir desde Resuelto/Cerrado en **En atención** exige institución activa y al menos un minuto en el canal principal actual del caso.
5. **Editar caso:** Administración y Técnico pueden corregir título, descripción inicial y canal; la acción queda auditada. No cambia los canales ni minutos de atenciones existentes.

| Estado | Operación habitual |
|---|---|
| Recibido | Solicitud registrada; permite atención, comentarios, ficha y adjuntos. |
| En atención | Soporte trabaja en el caso. |
| Requiere respuesta del cliente | Se solicita información; admite comentarios y evidencia. El comentario del cliente lo devuelve automáticamente a En atención. |
| Resuelto | La solución quedó registrada; para agregar atención, comentarios, ficha o adjuntos se requiere reapertura. |
| Cerrado | Concluye administrativamente y habilita la encuesta al cliente. Para continuar atención se requiere reapertura. |

Cambiar entre los tres estados activos no exige saldo adicional ni reinicia plazos. **Esperar al cliente no pausa el SLA**. Un comentario de soporte no modifica automáticamente el estado; use el selector para indicar que falta información o que el problema quedó resuelto. Se permite cerrar directamente un caso activo. Resuelto puede pasar a Cerrado o reabrirse en En atención; Cerrado sólo ofrece En atención como reapertura. Consulte [todas las transiciones](VERSION-1.8.3.md#transiciones).

Resolver, cerrar o esperar al cliente pausa los cronómetros que estén en marcha y conserva lo medido; **no confirma ni cobra tiempo**. Iniciar o continuar un cronómetro requiere Recibido o En atención. Los [cronómetros y la agenda](CRONOMETRO-AGENDA.md) tienen reglas propias, distintas del SLA.

Las entradas incluyen fecha, autor, descripción, canal, minutos y condición pública/interna. Las fechas se almacenan en UTC y la interfaz las presenta en el formato local del navegador.

Haga clic en una entrada del **Historial de atención** o en **Ver detalle** para abrir toda su información: folio, autor, fecha con segundos, descripción completa, canal, minutos, visibilidad y anulación con su motivo cuando corresponda. La información actual del caso se presenta separada del registro histórico. Use **← Volver al historial** para regresar al mismo caso y recuperar la posición de la lista; se conservan filtros y borradores. Los enlaces pueden operarse con el teclado. Consultar el detalle no modifica el registro ni descuenta tiempo. Las entradas internas siguen siendo exclusivas del personal de soporte (Administración y Técnicos).

Un caso Resuelto o Cerrado no admite atenciones ni comentarios nuevos. Debe reabrirse para continuar su seguimiento. Con saldo agotado se pueden conservar y consultar casos anteriores, comentar casos activos sin consumo y resolver o cerrar casos; no se autoriza nuevo consumo ni crear/reabrir un caso del canal agotado. Agotar correo no impide utilizar remoto cuando éste mantiene saldo.

## Corrección de consumos e historial

Para corregir una atención, el administrador selecciona **Anular**, escribe una razón y confirma. El registro sigue visible marcado como anulado y sus minutos se devuelven una sola vez. Después puede crear una atención correcta, siempre que el caso esté activo y la institución habilitada.

No se edita destructivamente una atención ni se elimina de la cronología. Dos solicitudes simultáneas para anular la misma entrada no duplican el reintegro. Una anulación sigue siendo posible en casos Resueltos o Cerrados para corregir un error administrativo; no implica reabrir automáticamente el caso.

La pantalla **Auditoría** permite al administrador revisar quién realizó cambios de instituciones, usuarios, casos, estados y consumos. Presenta los últimos 500 eventos de auditoría; los registros completos permanecen en PostgreSQL. El historial de cada caso es independiente de ese límite de presentación.

## Cobranza y acreditación por pagos

En **Pagos**, `ADMIN` y `BILLING` pueden consultar pagos y registrar dinero recibido de una institución activa. Capture referencia, importe con máximo dos decimales, fecha, método, descripción y minutos por acreditar en correo/remoto. Los registros nuevos utilizan **MXN**; se conservan las monedas de pagos anteriores. Los métodos disponibles son transferencia, depósito, efectivo, tarjeta de crédito, tarjeta de débito, cheque y otro. La referencia es única por institución, sin distinguir mayúsculas o espacios extremos, incluso si posteriormente se anula el pago.

Desde **Pólizas**, puede registrar el contrato de una institución, su vigencia, importe y minutos de cada canal. Crear la póliza no acredita tiempo; su vencimiento es informativo y no retira saldo automáticamente. Al registrar el pago, seleccione esa póliza o deje la opción **Sin póliza** para una compra directa.

| Movimiento | Importe recibido | Tiempo acreditado |
| --- | --- | --- |
| Compra directa sin póliza | Positivo | Al menos un canal positivo |
| Pago con póliza | Positivo | Puede acreditar minutos o dejarlos en cero como abono monetario |
| Liberación de horas pendientes de una póliza ya liquidada | Cero | Al menos un canal positivo, dentro del tiempo contratado pendiente |

Los pagos vigentes acumulados no pueden exceder el importe ni los minutos contratados de la póliza. Los minutos pendientes de acreditar son distintos del saldo institucional disponible para atender casos.

Al guardar, el pago y el aumento de tiempo asignado se confirman en una misma transacción. No cambia lo ya consumido. Por ejemplo: si quedan 20 min de correo y registra un pago que compra 120 min, el disponible pasa a 140 min. El importe monetario no se convierte automáticamente a minutos: el administrativo captura los minutos autorizados según las condiciones comerciales.

Un identificador de solicitud evita acreditar dos veces al reintentar una misma operación; además, una referencia ya registrada no se acepta en otro pago de esa institución. Si recibe un error de comunicación, consulte los pagos antes de iniciar otra captura con una referencia distinta.

Sólo `ADMIN` puede **anular** un pago, indicando el motivo. Debe quedar disponible todo el tiempo acreditado por ese pago, en ambos canales, para retirarlo. Si parte de esa capacidad ya no está disponible, la anulación se rechaza sin modificar el pago ni los saldos. La anulación se permite una sola vez y conserva autor, fecha, importes y motivo. No devuelve dinero mediante un banco: registra una corrección administrativa.

Si una póliza tiene acreditaciones de importe cero respaldadas por su liquidación, no puede anularse un pago monetario que la deje sin liquidar antes de anular esas acreditaciones. Cada anulación requiere saldo suficiente. Consulte [Pólizas y cobranza](POLIZAS-COBRANZA.md) para edición, cancelación, parcialidades y ejemplos completos.

### Comprobantes e informes financieros

Abra el detalle de un pago, pulse **Seleccionar comprobante** y después **Adjuntar comprobante**. Se permiten PDF, PNG y JPG/JPEG: hasta 10 MiB por archivo, 10 archivos y 50 MiB por pago. Se conservan nombre, autor, fecha, tamaño y huella SHA-256; los archivos se descargan con sesión autorizada. No hay sustitución ni borrado: para corregir evidencia se adjunta otro archivo.

Adjuntar un comprobante no cambia importes ni minutos, no repite el pago y no genera correo. También se admite en pagos anulados o de instituciones inactivas. La carga al pago registrado sigue siendo exclusiva de `ADMIN` y `BILLING`. Desde 1.6, los clientes pueden descargar los comprobantes de sus propios pagos, incluidos los históricos, únicamente si `ADMIN` habilitó el portal financiero de la institución. No pueden agregar archivos al pago registrado. Consulte [Comprobantes de pago](COMPROBANTES-PAGO.md).

Desde el detalle del pago también puede descargar un [recibo administrativo PDF](RECIBOS-PAGO.md) con folio estable. No es CFDI ni factura y no crea otro pago. Si el pago se anula, una nueva descarga indica ese estado; un PDF ya guardado no se actualiza automáticamente. El cliente habilitado recibe una versión que omite notas internas y motivo/responsable interno de anulación.

En **Reportes de cobranza**, filtre institución, póliza, método, moneda, estado y periodo. Consulte totales por institución y generales, historial y exportaciones Excel/PDF. Las monedas no se mezclan; los pagos se filtran por su fecha de pago. La situación actual de las pólizas considera todo su histórico y se muestra aparte del periodo. Consulte [Reportes de cobranza](REPORTES-COBRANZA.md). Los reportes técnicos de casos y consumos siguen siendo exclusivos de `ADMIN`.

Los ajustes manuales de presupuesto siguen disponibles para `ADMIN` y no crean un pago ficticio. Para una recarga respaldada por dinero recibido, utilice Cobranza. El módulo no es una pasarela de cobro ni emite CFDI/factura fiscal.

## Cotizaciones, renovaciones y validación de pagos

En **Cotizaciones**, Administración y Cobranza crean una oferta con institución, vigencia, importe MXN y minutos de cada canal. Emitirla congela sus condiciones y registra una constancia; no envía automáticamente un correo. La aceptación o rechazo se registra en nombre del cliente, con contacto, fecha y evidencia: no es una firma digital. Una aceptación puede convertirse una sola vez en póliza. **Ni emitir, aceptar, convertir ni renovar acredita horas.** El pago registrado es el que acredita los minutos expresamente indicados.

**Renovaciones** muestra pólizas próximas a vencer o vencidas que todavía no tienen sucesora. Cada renovación conserva su cotización y la relación entre póliza anterior y nueva. Si se cancela la nueva, el vínculo histórico permanece; un reemplazo continúa desde esa última póliza cancelada. Los avisos de vencimiento son internos al módulo, con umbral configurable e independientes del correo. La vigencia no retira saldo. Consulte [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md).

Para revisar evidencia antes de acreditar, abra **Validar pagos → Nueva solicitud**. Capture referencia, importe, fecha, método, póliza opcional y minutos solicitados; adjunte la evidencia. La solicitud queda **Pendiente** y no modifica saldos. Administración o Cobranza verifica los documentos, el dinero y los minutos antes de **Aprobar y registrar pago**. Se exige al menos un comprobante. La aprobación registra el pago, acredita sus minutos, copia los archivos y conserva la decisión en una transacción. Repetirla devuelve el mismo pago.

**Rechazar** exige un motivo visible para la institución y no acredita tiempo. Las solicitudes resueltas no se editan; la observación privada de aprobación sólo la ve el personal autorizado. Si las cantidades eran incorrectas, rechace y cree otra solicitud. Los pagos directos de 1.5 siguen disponibles cuando la validación ya ocurrió fuera del sistema; no capture un pago directo adicional para la misma solicitud que va a aprobar. Consulte [Solicitudes de pago](SOLICITUDES-PAGO.md).

## Portal financiero de clientes

Tener una cuenta de soporte no habilita información financiera. El portal comienza **desactivado para cada institución**, incluidas las existentes al actualizar desde 1.5. Sólo `ADMIN` puede abrir **Configuración de soporte → Acceso financiero de clientes**, seleccionar una institución activa y confirmar la habilitación. Cobranza no cambia este permiso.

Al habilitarlo, todos los usuarios activos de esa institución pueden entrar a **Finanzas institucionales** para consultar sus pólizas, importes, pagos y estados, descargar recibos y comprobantes históricos y crear solicitudes de pago. Pueden adjuntar evidencia a solicitudes pendientes; no pueden aprobarlas, registrar pagos directos, cambiar contratos ni aumentar su saldo. Los datos de otras instituciones, notas internas e historial administrativo no se entregan. El recibo cliente conserva el estado **Anulado** cuando corresponde, pero omite la información interna de la anulación.

La confirmación incluye el acceso a comprobantes cargados antes de habilitar el portal: revise su contenido antes de conceder acceso. Deshabilitar bloquea las siguientes consultas, descargas y solicitudes sin borrar registros. No retira archivos ya descargados ni información ya abierta en una pestaña. Consulte [Portal financiero](PORTAL-FINANCIERO.md).

## Soluciones y evaluación del soporte

En **Base de conocimientos**, el cliente busca soluciones publicadas por texto, categoría o versión de SIABUC. Administración crea borradores, publica, archiva y consulta las revisiones editoriales. Un artículo publicado debe pasar a borrador para editarlo; mientras sea borrador o esté archivado queda oculto a los clientes. Cada modificación conserva autor, fecha y revisión.

Dentro del caso, Administración y Técnicos pueden **Vincular solución** desde los artículos publicados. El cliente abre el contenido y utiliza **Volver al listado de soluciones** para regresar. Sólo se muestran vínculos de casos de su institución y artículos actualmente publicados. Consultar, vincular o retirar una solución no consume tiempo ni cierra el caso. Consulte [Base de conocimientos](BASE-CONOCIMIENTOS.md).

En 1.8.3, la pantalla global **Base de conocimientos** muestra un aviso de falta de acceso para TECH aunque el menú sea visible. El técnico consulta y busca publicaciones desde **Soluciones vinculadas al caso**; su acceso por API sí está autorizado. Administración y Cliente pueden utilizar la pantalla global.

Únicamente en un caso **Cerrado**, **¿Cómo fue la atención?** permite al cliente enviar una calificación de 1 a 5 y un comentario opcional. Se acepta una respuesta por caso para toda la institución. Reabrir conserva la respuesta y no permite otra; la atribución corresponde al responsable de soporte (`ADMIN` o `TECH`) asignado cuando se responde. Administración consulta los resultados por periodo/institución/responsable y exporta CSV desde **Satisfacción**. El estado **Resuelto** aún no habilita una encuesta nueva. Técnico y Cobranza no acceden a las respuestas individuales ni a estos reportes. La encuesta no exige portal financiero ni afecta minutos o correos. Consulte [Encuestas de satisfacción](ENCUESTAS-SATISFACCION.md).

## Respaldos manuales y automáticos

Los scripts **`respaldar.sh` / `respaldar.bat`** y **`restaurar.sh` / `restaurar.bat`** se conservan; consulte [Respaldo y restauración](RESPALDO-RESTAURACION.md) para nombre de respaldo, configuración y restauración. Actualizar a 1.8.3 no sustituye esos procedimientos.

El calendario automático comienza **desactivado**. Sólo Administración puede usar **Respaldos** para verificar el destino configurado en `BACKUP_DIRECTORY`, confirmar que es privado y escribible, guardar hora/zona/retención y activar expresamente los respaldos automáticos. **Crear respaldo ahora** permite solicitar una copia manual con destino confirmado y servicio disponible, aunque el calendario esté desactivado.

Desactivar el calendario conserva los archivos y el historial, cancela los programados pendientes y no interrumpe una copia ya iniciada. Reactivar espera el siguiente horario futuro. La limpieza por retención sólo afecta copias programadas propias, finalizadas y verificadas, después de otro respaldo programado exitoso; no elimina copias manuales. Un aviso administrativo informa fallos sin depender del correo.

Las copias de PostgreSQL incluyen historiales, comprobantes, artículos, encuestas y configuraciones almacenadas en la base. Conserve `.env` y otros archivos externos por separado. Una copia finalizada debe probarse restaurándola en un destino aislado antes de considerarla recuperable; la guía explica ese procedimiento sin usar el volumen de producción. Consulte [Respaldos automáticos](RESPALDOS-AUTOMATICOS.md) y [Validación](VALIDACION.md) para el alcance y las comprobaciones ejecutadas.

## Avisos y notificaciones

En **Correo y notificaciones → Historial de notificaciones**, haga clic en una fila o en **Ver detalle** para leer el asunto, destinatario, referencia, evento, estado de envío, intentos, fechas, último error o motivo y **contenido completo del correo** guardado. **← Volver al historial** recupera la búsqueda, el filtro de estado, la posición y el foco del enlace de origen. Esta consulta es exclusiva de `ADMIN`, no envía mensajes ni provoca reintentos. Si el servidor no permite consultar el registro o éste no existe, se muestra el error sin abrir un detalle ficticio. La información del correo refleja el evento original; no se reemplaza por la descripción actual de un caso editado posteriormente.

Cuando SMTP está configurado y activado, los cambios públicos de casos, incluidos sus cambios de estado, generan avisos al correo institucional y al responsable asignado (ADMIN o TECH). Las notas internas sólo avisan al responsable; si su dirección coincide con la institucional, se omiten hasta separar las direcciones para preservar su privacidad. Registrar o anular un pago avisa a la institución y al autor original del pago, aunque otro administrador lo anule. Las direcciones iguales se deduplican en eventos públicos.

En **Historial de atención**, el usuario institucional ve el recordatorio: «Revisa tu bandeja de entrada y la carpeta de correo no deseado para consultar las notificaciones de este caso». Aparece en los cinco estados del caso.

El administrador puede revisar **Notificaciones**, solicitar una prueba para su propia cuenta y reintentar fallidos u omitidos cuando su causa sea recuperable. Si faltaba correo, se resuelve el contacto de la misma entidad identificada originalmente. Si no existía responsable identificado, asignarlo después sólo habilita avisos de eventos futuros; no recupera ese aviso anterior. Cambiar un correo no modifica mensajes pendientes que ya guardaron su destinatario. Los avisos enviados quedan en el historial. `SENT` indica aceptación del servidor SMTP, no lectura del destinatario.

La instalación admite **SMTP directo** (por ejemplo Gmail) o **Postfix local opcional**. Para Gmail use `./configurar.sh --gmail-directo`; para otro proveedor directo use `./configurar.sh --correo`. El perfil `local-smtp` sólo se activa cuando la configuración necesita Postfix; su identidad/remitente/relay se configura con `./configurar.sh --smtp-interno`. Aplique los cambios con `./iniciar.sh`. Docker utiliza la red predeterminada del proyecto, sin `smtp_net` ni subred SMTP fija. Si usa Postfix, un aviso SENT puede seguir pendiente en su cola; consulte [SMTP-INTERNO.md](SMTP-INTERNO.md). La pantalla muestra valores de conexión sin revelar la contraseña. Consulte [CORREO.md](CORREO.md) para configuración, cola persistente, reintentos y pruebas locales Mailpit.

## Ejemplo de aceptación funcional

Con una institución de prueba que tenga 1 h de correo y 30 min remotos:

1. Abra un caso por correo: quedan 60 min.
2. Registre 20 min por correo: quedan 40 min de correo y 30 min remotos.
3. Registre 15 min remotos en ese mismo caso: quedan 40 min de correo y 15 min remotos.
4. Intente registrar 41 min de correo: el sistema debe rechazarlo y conservar ambos saldos.
5. Anule la atención de 20 min: correo vuelve a 60 min y la entrada sigue en el historial.
6. Agregue una nota interna y acceda como usuario institucional: la nota no debe aparecer.
7. Pase el caso a **Requiere respuesta del cliente**. Publique un comentario como cliente: debe volver a **En atención**, sin consumir minutos.
8. Pase a **Resuelto**: ya no se pueden agregar entradas; todavía no aparece una encuesta nueva.
9. Pase a **Cerrado**: el cliente puede evaluar una vez. Reabra en **En atención**, indicando el motivo y con saldo en el canal principal, para continuar el seguimiento.
10. Acceda como usuario de otra institución: no debe poder ver ese caso.

Este recorrido es una guía de uso y de aceptación manual. Los resultados de comprobaciones realmente ejecutadas se encuentran en [VALIDACION.md](VALIDACION.md).

## Dar acceso al historial a una institución cliente

1. Inicie sesión como administrador y registre o seleccione la institución en **Instituciones**.
2. En **Usuarios**, cree una cuenta activa con identificador de usuario, nombre, contraseña, correo y rol **USER**. Seleccione la institución cliente correspondiente.
3. Entregue a esa persona la dirección del portal y sus credenciales por un medio privado. El correo de contacto institucional por sí solo no crea una cuenta de acceso.
4. El cliente inicia sesión en el mismo portal y abre **Casos** para consultar sus casos en cualquiera de los cinco estados y su historial público, fechas y consumos. Puede cambiar su contraseña desde **Mi cuenta**.

Puede crear varias cuentas personales por institución; todas consultan el historial institucional. Spring restringe la consulta a la institución asociada incluso si alguien modifica el identificador del caso en una petición. Las notas internas quedan reservadas a Administración y Técnicos. El agotamiento del saldo impide nuevas atenciones, pero no impide consultar el historial de una institución activa.

## Desactivar o reactivar los correos (administración)

En **Correo y notificaciones**, un administrador puede pausar o reactivar el envío sin reiniciar. La selección permanece en PostgreSQL y queda auditada. Al desactivar, los avisos pendientes y recuperables pasan a «No enviado por desactivación». Durante la pausa, casos, pagos e historial siguen funcionando y los avisos nuevos se guardan con ese estado. Al reactivar sólo se notifican eventos nuevos, sin enviar los anteriores ni permitir reintentarlos. Si MAIL_ENABLED=false, debe habilitarse primero en el servidor. Técnicos, usuarios institucionales y Cobranza no pueden cambiar esta opción. Los avisos ya en curso o aceptados por el servidor SMTP, incluido Postfix cuando se utiliza, pueden terminar de entregarse. Consulte [Desactivar correo](DESACTIVAR-CORREO.md).
