# Cronómetro de atención y agenda remota

Guía vigente para **SIABUC Helpdesk 1.8.3**; consulte también el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md). Cronómetro y agenda son funciones independientes: reservar una sesión no descuenta minutos y finalizar un cronómetro tampoco. El cargo requiere confirmar expresamente una atención.

## Medir y confirmar una atención

1. Administración o un técnico abre un caso **Recibido** o **En atención** de una institución activa y pulsa **Iniciar cronómetro**.
2. Usa **Pausar** durante interrupciones y **Continuar** para seguir trabajando.
3. Pulsa **Finalizar atención**. El servidor conserva el tiempo y propone minutos enteros, redondeados hacia arriba: 61 segundos proponen 2 minutos.
4. Selecciona el canal **Correo** o **Escritorio remoto**, describe el trabajo y decide si la nota es interna.
5. Revisa los minutos. Si los ajustas respecto a la propuesta, escribe el motivo.
6. Pulsa **Confirmar y descontar … minutos**. Se crea un elemento del historial y se descuenta únicamente de la bolsa elegida.

Cada integrante de soporte sólo puede operar sus propios cronómetros. Cada uno puede tener un cronómetro pendiente, incluso entre distintos casos. Un cronómetro finalizado pendiente de confirmación también ocupa ese lugar: confírmalo o descártalo antes de iniciar otro.

El tiempo se guarda en PostgreSQL, con marcas del servidor y acumulación de milisegundos. Al recargar se recupera el cronómetro. Cerrar el navegador no lo pausa: si quedó en marcha, seguirá contando hasta que lo pauses, finalices o un cambio de estado del caso lo pause. El reloj visible del navegador es orientativo y no determina el descuento. No existe cobro automático por dejarlo abierto.

Al pasar el caso a **Requiere respuesta del cliente**, **Resuelto** o **Cerrado**, el servidor pausa todos sus cronómetros en marcha, conserva el tiempo acumulado y registra la pausa en auditoría. Volver a En atención no los reanuda automáticamente: usa **Continuar**. El SLA de resolución sigue contando durante la espera del cliente.

Confirmar minutos requiere saldo suficiente y un caso activo: Recibido, En atención o Requiere respuesta del cliente. En espera puedes finalizar y confirmar el tiempo ya medido, pero no iniciar ni continuar el cronómetro. Si falta saldo o el caso está Resuelto/Cerrado, la confirmación falla sin descontar y conserva el cronómetro finalizado. Puedes corregir los minutos con una justificación, disponer de saldo o reabrir el caso antes de confirmar. Finalizar o descartar sigue siendo posible en un caso Resuelto/Cerrado. Descartar requiere confirmar la acción y deja trazabilidad, sin cargo.

Repetir la confirmación de un cronómetro ya cobrado devuelve el mismo registro, sin descontar otra vez; esto también protege ante solicitudes simultáneas. Las notas internas conservan los permisos habituales del historial. La descripción admite 7,500 caracteres y el motivo de ajuste 2,000. Un comentario público registrado mediante el cronómetro puede satisfacer la primera respuesta del SLA; una nota interna no.

## Proponer una sesión remota

1. Abre **Agenda remota** dentro del caso.
2. Administración o un técnico indica inicio, fin y objetivo/preparativos. La interfaz lo establece como responsable de la sesión.
3. El cliente entra a su cuenta institucional y pulsa **Confirmar horario** o **Rechazar propuesta**. El rechazo requiere un motivo.
4. Administración puede gestionar cualquier sesión; un técnico sólo las que tiene asignadas. Puede cancelar, reprogramar o, después de la hora de inicio de una sesión confirmada, marcarla realizada.

Cada cambio se guarda en la agenda y en el historial público del caso. El correo sigue la configuración general: una pausa no impide guardar cambios ni consultar la agenda. El texto de agenda es visible para el cliente; no introduzcas contraseñas ni claves de acceso remoto.

Los horarios se guardan como instantes UTC. La interfaz muestra la zona horaria del navegador y convierte las horas locales al guardar. El historial y los avisos identifican explícitamente UTC para evitar ambigüedad. Usa una fecha futura, fin posterior al inicio y duración máxima de 12 horas.

El servidor rechaza propuestas que se solapen con otra sesión propuesta o confirmada del mismo responsable de soporte, incluso en otro caso o institución. Las sesiones consecutivas sin solapamiento sí están permitidas. Reprogramar cancela la sesión anterior y crea una nueva propuesta; se requiere otra confirmación del cliente. Si el nuevo horario está ocupado, toda la reprogramación se revierte y se conserva la sesión anterior.

Sólo usuarios de la institución del caso pueden confirmar o rechazar. El personal de soporte no confirma en nombre del cliente; Cobranza no accede a la agenda. Proponer, reprogramar, confirmar o rechazar requiere un caso activo de una institución activa. **Resuelto y Cerrado** impiden esas operaciones hasta reabrir. Se pueden cancelar citas pendientes o marcar realizada una sesión confirmada que ya comenzó, con los permisos del responsable. Resolver o cerrar no cancela automáticamente las citas: revisa y cancela las que ya no se necesiten.

Las sesiones tienen estados propios: **Propuesta**, **Confirmada**, **Rechazada**, **Cancelada** y **Realizada**. Son independientes de los cinco estados de atención del caso; una cita no modifica automáticamente su estado. Registrar una cita o un cambio de agenda no cuenta como primera respuesta técnica para el SLA. Para cobrar una sesión realizada, registra una atención por escritorio remoto o confirma su cronómetro. No hay integración con calendarios externos ni recordatorios programados en esta versión.

## API

Todas las operaciones requieren sesión y las mutaciones requieren CSRF. Las respuestas de lectura están limitadas por rol e institución.

| Método y ruta bajo `/api` | Función |
|---|---|
| `GET /cases/{id}/timer` | Último cronómetro propio del caso; vacío si no existe. |
| `POST /cases/{id}/timer/start` | Iniciar o recuperar el cronómetro pendiente propio. |
| `POST /timers/{id}/pause`, `/resume`, `/finish`, `/cancel` | Cambiar estado sin consumir saldo. |
| `POST /timers/{id}/commit` | Confirmar una sola vez con `minutes`, `channel`, `description`, `internal`, `adjustmentReason`. |
| `GET /cases/{id}/remote-sessions` | Agenda autorizada del caso. |
| `POST /cases/{id}/remote-sessions` | Proponer con `startsAt`, `endsAt`, `note` y `adminId` opcional. |
| `POST /remote-sessions/{id}/confirm`, `/decline` | Respuesta del cliente con `response`. |
| `POST /remote-sessions/{id}/cancel`, `/complete` | Cambio administrativo con motivo `response`. |
| `POST /remote-sessions/{id}/reschedule` | Nueva propuesta que sustituye la anterior; mismos datos de creación. |

Administración puede seleccionar otro responsable activo (`ADMIN` o `TECH`) mediante `adminId`; un técnico sólo puede proponer sesiones para sí mismo. La interfaz propone con la cuenta de soporte que inició sesión. Si no se indica `adminId` al reprogramar, se conserva el responsable de la cita anterior. Los estados técnicos del cronómetro son `RUNNING`, `PAUSED`, `FINISHED`, `COMMITTED` y `CANCELLED`.

La migración `V8__timers_remote_sessions.sql` crea ambas tablas. Están incluidas en los respaldos normales de PostgreSQL; no requieren un volumen adicional. No cambies manualmente sus estados o contadores para cobrar atenciones: usa la API para preservar la contabilidad y el historial.
