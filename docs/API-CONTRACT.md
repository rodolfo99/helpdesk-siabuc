# Contrato de integración — SIABUC Helpdesk 1.8.3

Base `/api`. Instantes ISO 8601 UTC; fechas civiles de pagos y pólizas `YYYY-MM-DD`, sin conversión de zona horaria. Identificadores numéricos. Unidad de soporte: minutos enteros (no flotantes). Roles `ADMIN`, `TECH`, `USER` y `BILLING`; canales `EMAIL` y `REMOTE`; estados `OPEN`, `IN_PROGRESS`, `WAITING_CLIENT`, `RESOLVED` y `CLOSED`. `OPEN` conserva el identificador del contrato anterior y se muestra como «Recibido».

Autenticación: sesión HttpOnly, cookie de CSRF `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN`. Angular usa proxy mismo origen. GET `/auth/csrf` inicializa cookie y retorna `{token}`; llamar antes de login y después de login/logout. POST `/auth/login` usa formulario URL encoded `username`, `password`, devuelve 200. POST `/auth/logout` devuelve 204. GET `/auth/me` devuelve User. PUT `/auth/password` JSON `{currentPassword,newPassword}`.

User: `{id,username,displayName,role,institutionId,active,email}`.
Institution: `{id,name,contactName,email,phone,notes,active,emailAllocatedMinutes,emailUsedMinutes,emailRemainingMinutes,remoteAllocatedMinutes,remoteUsedMinutes,remoteRemainingMinutes,supportAdminId}`.
Case: `{id,institutionId,institutionName,title,description,channel,status,createdBy,createdAt,updatedAt,totalMinutes,assignedAdminId,assignedAdminName}`.
Entry: `{id,caseId,description,channel,minutes,internal,createdBy,createdAt,voided,voidReason}`. `channel` null si comentario sin consumo.
CaseDetail: campos de Case más `entries:Entry[]`.
Audit: `{id,actor,action,entityType,entityId,details,createdAt}`.
Error: `{message}` y HTTP 400 validación, 401 sin sesión o cuenta no disponible, 403 no autorizado o CSRF inválido, 404 inexistente o fuera del ámbito autorizado, 409 conflicto/saldo insuficiente, 413 adjunto excesivo, 503 base de datos no disponible y 500 error inesperado. Los códigos son HTTP; el cuerpo no incorpora un campo `code`.

- GET `/institutions`: array (ADMIN/TECH/BILLING todas incluidas bajas; USER solo propia activa).
- POST `/institutions` ADMIN: `{name,contactName,email,phone,notes,emailAllocatedMinutes,remoteAllocatedMinutes,supportAdminId}`.
- PUT `/institutions/{id}` ADMIN: campos anteriores más `active`; no permite asignado menor que usado.
- DELETE `/institutions/{id}` ADMIN: baja lógica sin perder casos.
- GET `/users` ADMIN: User[].
- POST `/users` ADMIN: `{username,displayName,password,role,institutionId,active,email}`. USER requiere institución; ADMIN/TECH/BILLING null.
- PUT `/users/{id}` ADMIN: mismos campos, password opcional vacío conserva. Username, rol e institución son inmutables. No desactivar al último administrador. Para cambiar el ámbito de acceso, crear otra cuenta y desactivar la anterior. Restablecer la contraseña revoca las sesiones de esa cuenta.
- GET `/cases`: Case[] filtrados en servidor por institución del usuario; ADMIN/TECH todos.
- POST `/cases`: `{institutionId,title,description,channel,assignedAdminId,profile?}`; USER sólo su institución. Requiere institución activa y saldo del canal mayor que cero. Crea en `OPEN` y devuelve HTTP 201 con `CaseDetail`; no consume minutos por crear el caso. El portal cliente dispone además de `/client-portal/tickets`, descrito al final.
- GET `/cases/{id}`: CaseDetail, USER no recibe entradas internas.
- PUT `/cases/{id}` ADMIN/TECH: `{title,description,channel,assignedAdminId}`; registra historial público, auditoría y aviso. TECH puede editar los datos pero no cambiar el responsable. Esta edición de datos se permite también en casos finales; no reabre el caso.
- POST `/cases/{id}/status` ADMIN/TECH: `{status,reason}`; devuelve HTTP 200 con `CaseDetail`. El motivo es obligatorio. Las transiciones y condiciones de reapertura se detallan en [Estados de atención](#estados-de-atención).
- POST `/cases/{id}/entries`: `{description,channel,minutes,internal}`. ADMIN/TECH registra minutos >0 y canal o comentario 0/null; USER únicamente comentario 0/null y público. Casos `RESOLVED` y `CLOSED` no aceptan entradas nuevas. Devuelve CaseDetail. Consumo y escritura en una transacción con bloqueo de institución; no sobregiro.
- POST `/cases/{id}/entries/{entryId}/void` ADMIN: `{reason}` anula atención y devuelve minutos una única vez; conserva entrada y auditoría. Devuelve CaseDetail.
- GET `/audit` ADMIN: Audit[] (últimas 500).
- GET `/health` público: `{status:"UP"}`.

Listados de instituciones, usuarios y casos completos; interfaz filtra búsquedas y estado. Auditoría muestra los últimos 500 eventos, sin eliminar los anteriores de PostgreSQL. Presupuestos son totales históricos asignados: recargar implica aumentar el total. Bajas bloquean acceso de usuarios de esa institución; el historial queda consultable por ADMIN/TECH. Nunca borrar físicamente trazabilidad. Contraseñas de al menos 12 caracteres Unicode y como máximo 72 bytes UTF-8; BCrypt. Cambiar la contraseña propia conserva la sesión actual y revoca las otras. Variables backend: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, SERVER_PORT, ADMIN_USERNAME, ADMIN_PASSWORD, COOKIE_SECURE.

`notes` de Institution se devuelve vacío para USER, TECH y BILLING. Las entradas internas no se devuelven a USER; sus minutos sí forman parte del consumo total institucional y del caso.


## Estados de atención

El JSON transmite los identificadores siguientes; las etiquetas son para presentación. `OPEN` no se renombra a `RECEIVED` en la API ni en PostgreSQL.

| Identificador | Etiqueta visible | Grupo | Iniciar/reanudar cronómetro |
| --- | --- | --- | --- |
| `OPEN` | Recibido | Activo / pendiente de resolver | Sí |
| `IN_PROGRESS` | En atención | Activo / pendiente de resolver | Sí |
| `WAITING_CLIENT` | Requiere respuesta del cliente | Activo / pendiente de resolver | No |
| `RESOLVED` | Resuelto | Final | No |
| `CLOSED` | Cerrado | Final | No |

Un POST `/cases/{id}/status` válido devuelve `CaseDetail` y exige ADMIN/TECH, CSRF, un `status` reconocido y un `reason` no vacío de hasta 10,000 caracteres. Ejemplo:

```json
{"status":"WAITING_CLIENT","reason":"Se solicitó el registro de error para continuar el diagnóstico."}
```

| Estado actual | Destinos distintos admitidos por la API |
| --- | --- |
| `OPEN` | `IN_PROGRESS`, `WAITING_CLIENT`, `RESOLVED`, `CLOSED` |
| `IN_PROGRESS` | `WAITING_CLIENT`, `RESOLVED`, `CLOSED` |
| `WAITING_CLIENT` | `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `RESOLVED` | `IN_PROGRESS`, `CLOSED`, `OPEN` por compatibilidad |
| `CLOSED` | `IN_PROGRESS`, `OPEN` por compatibilidad |

La interfaz reabre hacia `IN_PROGRESS`; los clientes antiguos todavía pueden solicitar `OPEN` desde un estado final. Desde `CLOSED`, pasar a `RESOLVED` exige primero reabrir. Se permite cerrar directamente un caso activo. Solicitar el estado que ya tiene es idempotente: después de validar estado, motivo y acceso, devuelve el detalle sin añadir entrada, auditoría, aviso ni alterar plazos.

Cambiar a un estado activo requiere institución activa. Reabrir desde `RESOLVED`/`CLOSED` también requiere al menos un minuto disponible del canal principal; no lo descuenta. Cambiar entre estados activos no exige saldo nuevo. Resolver/cerrar un caso no consume minutos ni requiere saldo. Una transición no admitida, institución inactiva al activar o saldo insuficiente al reabrir produce 409; estado desconocido, ausente o motivo inválido produce 400. USER/BILLING no cambian manualmente estados (403).

Cada cambio manual real registra historial público de cero minutos y auditoría, conserva responsable, caso y consumos previos, y genera el evento `CHANGE_CASE_STATUS` para avisos del portal y cola de correo. Si USER añade un comentario público a `WAITING_CLIENT`, el servidor pasa automáticamente a `IN_PROGRESS`, registra el cambio y envía el aviso `CREATE_ENTRY` con el estado actualizado. Un comentario de soporte no cambia por sí solo a `WAITING_CLIENT`; debe solicitarse el estado explícitamente. Tomar un caso `OPEN` también lo pasa a `IN_PROGRESS`, con las reglas de [Toma de casos](#toma-de-casos).

En `RESOLVED`/`CLOSED` se bloquean nuevas entradas, archivos y modificaciones de ficha técnica, así como nuevas propuestas/reprogramaciones y respuestas nuevas del cliente a la agenda. Se conservan consulta e historial. Editar datos generales, anular un consumo como ADMIN, gestionar enlaces de conocimiento y cancelar/completar sesiones existentes conservan sus reglas específicas; no deben bloquearse en un cliente de integración simplemente por tratarse de un estado final.

SLA: los cambios entre estados activos conservan el inicio del plazo de resolución. `WAITING_CLIENT` **no pausa el SLA**; transcurre según el calendario laboral capturado en el caso. Resolver registra `resolvedAt`; cerrar después conserva ese instante. Cerrar directamente también registra resolución. Reabrir borra `resolvedAt` y reinicia sólo la ventana de resolución, conservando la primera respuesta pública previa. La toma, el cambio de estado y la respuesta del cliente no satisfacen el objetivo de primera respuesta de soporte.

Al pasar a `WAITING_CLIENT`, `RESOLVED` o `CLOSED`, todos los cronómetros en marcha del caso quedan `PAUSED` y retienen el tiempo medido. No se reanudan solos al volver a atención ni descuentan saldo. Su propietario puede finalizarlos o descartarlos; confirmar minutos requiere cronómetro `FINISHED`, caso activo e institución activa, además del saldo del canal de la atención. Por ello puede confirmar tiempo ya medido en `WAITING_CLIENT`, pero debe reabrir un caso final. Repetir la confirmación de un cronómetro ya `COMMITTED` devuelve el resultado guardado sin volver a consumir minutos.

La migración V26 conserva los valores, identificadores y datos anteriores: los casos `OPEN` existentes se muestran Recibido, sin inferir automáticamente una etapa de atención. Los filtros guardados que antes usaban `OPEN` se convierten al grupo `active`, para mantener su intención de mostrar todos los pendientes. Las migraciones anteriores mantienen su contenido y orden; véase [VERSION-1.8.3.md](VERSION-1.8.3.md).


## Contactos y responsables

`email` acepta una dirección individual válida o vacío. En `PUT /users/{id}`, omitir `email` o enviarlo como `null` conserva el actual; `""` lo vacía. `ADMIN_EMAIL` únicamente establece el correo al crear el administrador inicial: no modifica cuentas existentes.

`supportAdminId` es un ADMIN/TECH activo predeterminado para casos nuevos. `assignedAdminId` identifica al responsable del caso. En los PUT respectivos, omitir el campo conserva el valor; ADMIN puede enviar `null` para eliminar la asignación. En POST de casos, TECH queda asignado a sí mismo; sólo puede enviar su propio id o dejar el campo sin valor. Para ADMIN/USER, si no se indica responsable se usa el predeterminado activo; si falta y crea un ADMIN, se asigna el creador. Un USER no puede indicar un responsable. Cambiar el predeterminado institucional no reasigna casos previos. El responsable no limita el acceso: ADMIN y TECH consultan todos los casos.

## Pagos y acreditación

`BILLING` puede consultar instituciones sin notas privadas, gestionar pólizas, registrar/consultar pagos, adjuntar comprobantes y consultar reportes financieros. No accede a casos, administración de usuarios, auditoría, cola/configuración de correo ni modificaciones institucionales. Puede cambiar su propia contraseña y consultar sus avisos internos de saldo mediante `/inbox`.

- GET `/payments` ADMIN/BILLING: lista completa de pagos, incluidos anulados, orden descendente de id.
- POST `/payments` ADMIN/BILLING: registra un pago recibido y acredita sus minutos en una única transacción; HTTP 201.
- POST `/payments/{id}/void` ADMIN: `{reason}`; retira la acreditación una sola vez si hay saldo disponible suficiente en ambos canales; conserva el pago y su auditoría. HTTP 200; segundo intento 409.

Entrada de pago:

```json
{
  "requestId": "45104c2a-c447-4cac-b9c3-405d6e1d5683",
  "institutionId": 1,
  "policyId": null,
  "reference": "TRANSFERENCIA-0001",
  "amount": 250.00,
  "currency": "MXN",
  "paidOn": "2026-10-13",
  "method": "TRANSFER",
  "emailMinutes": 65,
  "remoteMinutes": 30,
  "notes": "Pago recibido por soporte; descripción incluida en el aviso."
}
```

Respuesta: campos de entrada más `{id,institutionName,policyFolio,createdBy,createdAt,voided,voidReason,voidedBy,voidedAt}`. `policyId` es opcional; `null` indica compra directa. Importe máximo `999999999.99` con hasta dos decimales; los movimientos nuevos deben usar `MXN`. Métodos `TRANSFER`, `DEPOSIT`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `CHECK`, `OTHER`; `CARD` sigue aceptado por compatibilidad. Los registros anteriores conservan moneda y método.

Sin póliza se requieren importe y algún canal positivos. Con póliza se permite un abono positivo sin minutos; una acreditación con importe cero exige póliza ya liquidada y minutos pendientes. No se permite un movimiento totalmente en cero ni superar los importes/minutos contratados vigentes. Cada bolsa institucional mantiene el máximo de mil millones de minutos asignados. Notas y referencia obligatorias. Consulte [Pólizas y cobranza](#pólizas-y-cobranza) para límites e historial.

`requestId` es UUID persistente durante reintentos de la misma operación: repetir el mismo contenido devuelve el pago previo (también HTTP 201) sin acreditar ni notificar otra vez. Reutilizarlo con contenido diferente da 409. La referencia no puede repetirse en la misma institución, ignorando mayúsculas y espacios exteriores, incluso después de una anulación. No hay edición ni borrado del registro: anular y registrar una corrección con otra referencia. No se realizan cargos bancarios ni se genera factura fiscal.

Los ADMIN conservan el ajuste directo del presupuesto institucional como operación auditada independiente de pagos. Cobranza sólo incrementa minutos mediante pagos. Los acumulados monetarios se separan por moneda y excluyen anulados.

## Notificaciones

- GET `/notifications` ADMIN: últimos 500 avisos sin cuerpo del mensaje ni secretos. Campos `{id,caseId,paymentId,eventType,recipient,subject,status,attempts,lastError,createdAt,sentAt}`.
- GET `/notifications/{id}` ADMIN: los campos del listado más `{body,internal,recipientType,recipientEntityId}`. Devuelve el contenido persistido del aviso, no una regeneración con datos actuales. HTTP 404 si no existe; 401 sin sesión y 403 para TECH/USER/BILLING. Sólo lectura: no cambia estados, intentos, auditoría ni genera envíos. No expone tokens de procesamiento ni configuración secreta.
- GET `/notifications/config` ADMIN: `{enabled,configuredEnabled,paused,host,port,username,fromAddress,fromName,security,auth,replyTo,pendingCount,failedCount,suppressedCount}`. Nunca incluye contraseña SMTP.
- POST `/notifications/test` ADMIN: `{}`; HTTP 201, encola prueba exclusivamente al correo de la propia cuenta. Rechaza destinatarios o contenido arbitrarios.
- POST `/notifications/{id}/retry` ADMIN: `{}`; sólo FAILED/SKIPPED. No permite reenviar SENT. Conserva el destinatario original; si faltaba su email, consulta el contacto actual de esa misma entidad. No permite elegir otro destinatario. Avisos sin responsable identificado requieren asignación para eventos futuros.

Eventos principales: `CREATE_CASE`, `CLAIM_CASE`, `UPDATE_CASE`, `CHANGE_CASE_STATUS`, `CREATE_ENTRY`, `VOID_ENTRY`, `CREATE_PAYMENT`, `VOID_PAYMENT`, `TEST_MAIL`; adjuntos y agenda añaden `ATTACHMENT_ADDED` y `REMOTE_SESSION_*`. Un cambio de estado al mismo estado es una operación sin cambios: no crea entrada ni correo duplicados. Cada cambio manual real de estado registra motivo y encola aviso público para institución y responsable. Los eventos internos y su anulación únicamente se encolan al ADMIN/TECH responsable; si su dirección coincide con la institucional, se omiten para evitar divulgar notas privadas.

Los mensajes públicos se envían por separado al correo institucional y al ADMIN/TECH asignado; direcciones idénticas se deduplican por evento. Los pagos se notifican a la institución y al ADMIN/BILLING autor del pago. Las notas del pago forman parte del aviso. Las direcciones se capturan al registrar el evento: cambiarlas no modifica mensajes ya pendientes. Las operaciones fallidas no dejan mensajes en la cola.

Estados `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED`, `SUPPRESSED`. SUPPRESSED es terminal y no permite reintento. MAIL_ENABLED=false conserva PENDING si no hay pausa administrativa; esa pausa utiliza SUPPRESSED. Fuera de la pausa, una dirección ausente o no válida deja SKIPPED visible. SENT significa aceptación por el servidor SMTP, no confirma entrega final ni lectura. Los reintentos automáticos son limitados (máximo cinco), con espera creciente y recuperación de bloqueos vencidos. La entrega es al menos una vez: una caída entre aceptación SMTP y confirmación en base puede duplicar un aviso.

Configuración, proveedores, puertos, TLS, pruebas locales y puesta en operación en [CORREO.md](CORREO.md). Actualización compatible del flujo de casos en [VERSION-1.8.3.md](VERSION-1.8.3.md).


En 1.8.3, el backend puede enviar directamente al SMTP configurado, incluido Gmail mediante `--gmail-directo`. Postfix es opcional: el servicio Docker `smtp` se activa con el perfil `local-smtp` al usar `--smtp-interno`; comparte la red predeterminada de Compose y ya no requiere `smtp_net` ni una subred fija. `/notifications/config` describe el primer salto de Spring: el proveedor remoto en modo directo, o `smtp:25` con Postfix. La configuración del relay posterior de Postfix se administra fuera de la API. Consulte [CORREO.md](CORREO.md) y [SMTP-INTERNO.md](SMTP-INTERNO.md).

## Control de envío

POST `/notifications/config` ADMIN: cuerpo exacto `{"paused":true}` para pausar o `{"paused":false}` para reactivar. Exige sesión y CSRF, rechaza campos extra y valores ausentes, null o distintos de boolean. Devuelve Configuration; `enabled` es el estado efectivo, `configuredEnabled` refleja MAIL_ENABLED al arranque y `paused` el estado persistido en PostgreSQL. Repetir el mismo valor no añade auditoría duplicada. Una pausa/reanudación real registra PAUSE_NOTIFICATIONS/RESUME_NOTIFICATIONS. El control no puede superar MAIL_ENABLED=false. Al pausar se suprimen PENDING/FAILED/SKIPPED existentes y los avisos nuevos se guardan SUPPRESSED. Al reactivar sólo se envían eventos nuevos; pruebas/reintentos se bloquean durante la pausa y ningún SUPPRESSED puede reintentarse. Los ya reclamados pueden terminar; véase [DESACTIVAR-CORREO.md](DESACTIVAR-CORREO.md).

## Toma de casos

POST `/cases/{id}/claim` ADMIN/TECH con sesión y CSRF; no requiere datos (la interfaz envía `{}`). Devuelve HTTP 200 con `CaseDetail`. Asigna un caso activo y sin responsable al integrante de soporte de la sesión, con bloqueo transaccional de institución y caso. Si estaba `OPEN`, pasa automáticamente a `IN_PROGRESS`; si estaba `IN_PROGRESS` o `WAITING_CLIENT`, conserva ese estado. Registra una entrada pública de toma, auditoría y notificación `CLAIM_CASE`; cuando cambia el estado, también registra su auditoría y lo describe en esa misma entrada. La pausa de correo se respeta como en cualquier otro evento.

Un caso `RESOLVED`/`CLOSED`, una institución inactiva o un responsable diferente ya asignado producen 409. Repetir la toma de un caso activo de institución activa que ya pertenece al mismo actor devuelve su detalle sin duplicar efectos ni cambiar su estado. USER/BILLING reciben 403. El cliente mantiene POST `/cases` para su propia institución y con saldo en el canal: si existe un responsable institucional, se asigna automáticamente; de lo contrario, el caso queda disponible para tomar. No puede asignar responsables ajenos ni abrir solicitudes para otra institución.


## Ficha técnica, SLA, adjuntos, cronómetro, agenda e informes

Todas las rutas siguientes usan el prefijo `/api`, sesión y CSRF en las escrituras. USER sólo accede a su institución; los IDs ajenos devuelven 404. BILLING no accede a casos, adjuntos de soporte, cronómetro, agenda ni reportes técnicos. Los avisos propios de saldo sí están disponibles para Cobranza; sus reportes financieros y comprobantes usan las rutas financieras descritas más adelante.

| Método y ruta | Uso / rol |
| --- | --- |
| `GET /case-profiles` | Clasificación resumida por caso autorizado; ADMIN / TECH / USER. |
| `GET/PUT /cases/{id}/profile` | Ficha y SLA / actualizar ficha de caso activo; ADMIN / TECH / USER. |
| `GET/PUT /support-settings` | Calendario y objetivos SLA; ADMIN. |
| `GET /cases/{id}/attachments` | Adjuntos del caso; query `entryId` para un comentario concreto. |
| `POST /cases/{id}/attachments` | Multipart `file` + `requestId` UUID, query `entryId` opcional; ADMIN / TECH / USER autorizado. |
| `GET /attachments/{id}/download` | Archivo binario; autenticación y privacidad del comentario. |
| `GET /inbox` | `{items,unreadCount,pendingReplies}` del usuario actual. |
| `POST /inbox/{id}/read`, `POST /inbox/read-all` | Marcar leído; cuerpo vacío o `{}`, respuesta 204. |
| `GET /balance-alert-settings` | Umbrales y saldos por institución; ADMIN. |
| `PUT /balance-alert-settings/{institutionId}` | `{emailThresholdMinutes,remoteThresholdMinutes,enabled}`; ADMIN. |
| `GET /cases/{id}/timer` | Último cronómetro propio, o null; ADMIN / TECH. |
| `POST /cases/{id}/timer/start` | Iniciar cronómetro propio; ADMIN / TECH. |
| `POST /timers/{id}/pause`, `/resume`, `/finish`, `/cancel` | Operación del propietario ADMIN / TECH; un cronómetro ajeno devuelve 404. |
| `POST /timers/{id}/commit` | Propietario ADMIN / TECH; confirmar `{minutes,channel,description,internal,adjustmentReason}`; descuento idempotente. |
| `GET/POST /cases/{id}/remote-sessions` | Listar: ADMIN / TECH / USER autorizado. Proponer: ADMIN / TECH; TECH sólo se asigna a sí mismo. |
| `POST /remote-sessions/{id}/confirm`, `/decline` | Responder como USER de la institución; `{response}`. |
| `POST /remote-sessions/{id}/cancel`, `/complete` | ADMIN, o TECH responsable de esa sesión; `{response}`. |
| `POST /remote-sessions/{id}/reschedule` | ADMIN, o TECH responsable; nueva propuesta que cancela la anterior. TECH no puede transferirla a otro responsable. |
| `GET /reports/support`, `/reports/support.xlsx`, `/reports/support.pdf` | ADMIN; filtros `institutionId`, `administratorId`, `channel`, `from`, `to`. |

`POST /cases` admite `profile` opcional con `category`, `priority`, `siabucVersion`, `operatingSystem`, `errorMessage` y `reproductionSteps`. La creación del caso y su ficha es atómica; una ficha inválida revierte toda la solicitud. Si no se incluye, se conserva compatibilidad con clientes anteriores: OTHER/NORMAL y datos técnicos vacíos. Los mismos campos se usan en PUT de ficha.

Categorías: INSTALLATION, CATALOGING, LOANS, REPORTS, IMPORT, OTHER. Prioridades: LOW, NORMAL, HIGH, URGENT. `CaseDetail` conserva su estructura previa: la ficha detallada y sus plazos se consultan por la ruta de perfil. [Clasificación y SLA](CLASIFICACION-SLA.md) documenta objetivos, calendario y estados de cumplimiento.

La subida utiliza multipart, no JSON ni Base64, y no debe fijarse manualmente el boundary en el navegador. El cliente genera un UUID por carga; reintentar con el mismo UUID y contenido no duplica el archivo. Un UUID reutilizado con otro archivo o ubicación se rechaza. Superar 10 MiB produce 413; tipos/contenidos no admitidos producen 400; caso `RESOLVED`/`CLOSED` o límites acumulados producen 409. Una repetición idéntica de una carga ya registrada devuelve el adjunto existente incluso si después se finalizó el caso. Las descargas incluyen `Content-Disposition: attachment`, protección contra interpretación de tipos y control de caché. Consulte [Manual de usuario](MANUAL-USUARIO.md).

La propuesta de agenda usa `{startsAt,endsAt,note,adminId?}` con fechas ISO-8601 que identifican un instante; el responsable predeterminado al crear es el ADMIN/TECH actor. ADMIN puede elegir otro ADMIN/TECH activo; TECH sólo puede proponer para sí mismo y cancelar, completar o reprogramar las sesiones de las que es responsable. Crear/reprogramar y la primera confirmación o rechazo del cliente requieren caso activo e institución activa. Cancelar o completar una sesión existente tiene sus propias condiciones de agenda y puede hacerse aun después de resolver o cerrar el caso; completar no descuenta minutos. Reprogramar requiere confirmación nueva. [Cronómetro y agenda](CRONOMETRO-AGENDA.md) detalla transiciones y reintentos.

El JSON de reportes conserva `cases[].status` con los identificadores de la API. `totals` contiene `{cases,openCases,closedCases,resolvedCases,attentionEntries,voidedEntries,emailMinutes,remoteMinutes}`. `openCases` agrupa `OPEN` + `IN_PROGRESS` + `WAITING_CLIENT`; `resolvedCases` cuenta sólo `RESOLVED`, y `closedCases` sólo `CLOSED`, por lo que `cases = openCases + resolvedCases + closedCases`. Excel/PDF muestran las etiquetas en español. El filtro `administratorId` se aplica al responsable actual para los casos y al autor para las atenciones; `from`/`to` se aplican a la fecha de creación de cada conjunto, no a la fecha de resolución.

Los informes devuelven 400 si exceden 366 días, 5,000 filas combinadas o sus límites de contenido; nunca se recortan silenciosamente. Excel utiliza el tipo MIME OOXML, PDF `application/pdf`; ambos usan descarga adjunta. Las fechas finales son inclusivas en `REPORTING_TIME_ZONE`. Consulte [Reportes](REPORTES.md) para la semántica exacta de filtros y anulaciones.


## Pólizas y cobranza

BILLING y ADMIN pueden gestionar pólizas y consultar reportes financieros. TECH/USER reciben 403. El acceso financiero no concede a BILLING acceso a casos, notas internas, adjuntos de soporte ni reportes técnicos. Todas las escrituras conservan sesión y CSRF.

| Método y ruta (prefijo `/api`) | Resultado |
| --- | --- |
| `GET /policies?institutionId=...` | Pólizas, con importes y minutos contratados, acreditados y pendientes. |
| `POST /policies` | Crea póliza e historial; devuelve 201 con `PolicyDetail`. No acredita saldo. |
| `GET /policies/{id}` | `{policy,history,payments}` con situación actual e histórico. |
| `PUT /policies/{id}` | Edita sólo una póliza activa que nunca tuvo pagos, incluidos los anulados. |
| `POST /policies/{id}/cancel` | `{reason}`; requiere motivo y ausencia de pagos vigentes. Conserva evidencia. |
| `POST /payments` | Campos existentes más `policyId` opcional. Pago y acreditación comparten transacción. |
| `GET /reports/billing`, `/reports/billing.xlsx`, `/reports/billing.pdf` | Reportes financieros para BILLING/ADMIN. |

Entrada de póliza: `{institutionId,folio,description,validFrom,validUntil,agreedAmount,emailMinutes,remoteMinutes}`. La moneda es MXN; importe positivo con máximo dos decimales. Las fechas son `AAAA-MM-DD`, final igual o posterior al inicio. Debe contratarse tiempo en al menos un canal. El folio es único globalmente, sin distinguir mayúsculas ni espacios exteriores. La institución no cambia después de crear la póliza.

El historial registra CREATE_POLICY, UPDATE_POLICY, CANCEL_POLICY, CREATE_PAYMENT y VOID_PAYMENT, con descripción, autor, fecha y pago asociado cuando corresponde. Los saldos de póliza se calculan con pagos no anulados de todas las fechas. `remainingEmailMinutes` y `remainingRemoteMinutes` son minutos aún no acreditados; no representan el saldo institucional después de consumir soporte.

Los nuevos pagos usan MXN; los métodos son TRANSFER, DEPOSIT, CASH, CREDIT_CARD, DEBIT_CARD, CHECK y OTHER. CARD permanece compatible para datos anteriores. Las respuestas de pagos añaden `policyId` y `policyFolio`, ambos null en pagos libres/anteriores.

Una parcialidad puede incluir importe sin minutos. La acreditación con importe cero sólo se permite vinculada a una póliza ya liquidada y con minutos pendientes; siempre debe existir un valor positivo entre importe y minutos. En pagos libres se mantienen importe y minutos positivos. Los importes/minutos vigentes acumulados nunca pueden superar la póliza. La vigencia es informativa; una póliza cancelada rechaza nuevos pagos. Reintentar la misma solicitud sólo devuelve el pago existente si también coincide `policyId`; no vuelve a acreditar.

Se conserva `POST /payments/{id}/void` exclusivo ADMIN: si la institución no tiene suficientes minutos disponibles no se anula ni se modifica ninguna bolsa. Una anulación autorizada retira la acreditación, conserva la fila y actualiza los saldos derivados de la póliza dentro de la misma transacción.

Cuando una póliza tiene acreditaciones vigentes de importe cero que dependen de su liquidación, primero deben anularse esas acreditaciones antes de anular un pago positivo que la deje sin liquidar. El servidor devuelve 409 y no cambia ningún saldo si se intenta alterar ese orden.

Reportes financieros: filtros `institutionId`, `policyId`, `method`, `currency`, `status` (ALL/ACTIVE/VOIDED), `from`, `to`. Las fechas filtran `paidOn`, con extremos inclusivos. Se admiten hasta 3660 días y 5000 filas combinadas; un exceso se rechaza sin truncar. Los totales monetarios se separan por moneda. La situación actual de pólizas considera todo el histórico y se filtra sólo por institución/póliza; se presenta separada de los ingresos del periodo. Consulte [Pólizas y cobranza](POLIZAS-COBRANZA.md) y [Reportes financieros](REPORTES-COBRANZA.md).


### Comprobantes de pago

BILLING y ADMIN pueden `GET /api/payments/{id}/attachments`, subir mediante `POST` a la misma ruta con multipart `file` y `requestId` UUID, y descargar mediante `GET /api/payment-attachments/{id}/download`. TECH/USER reciben 403. Los adjuntos financieros no usan las rutas de expedientes de soporte.

Se admiten PDF, PNG y JPEG; 10 MiB por archivo, 10 archivos y 50 MiB acumulados por pago. El servidor valida contenido, cuotas y nombre. Reintentar la misma solicitud no duplica el comprobante. La evidencia puede agregarse a pagos históricos, incluidos anulados y de instituciones inactivas. No cambia importes ni minutos. Las descargas requieren sesión autorizada y se entregan como archivo adjunto con protección de tipo/caché.

El contenido binario se guarda en PostgreSQL. Se conservan autor, fecha y SHA-256; no hay borrado físico. Cuando el pago tiene póliza, la carga se registra también en su historial como `ATTACH_PAYMENT_PROOF`. Consulte [Comprobantes de pago](COMPROBANTES-PAGO.md).

## Cotizaciones, portal financiero, conocimiento, encuestas y respaldos

Las funciones anteriores se conservan. Cotizaciones y renovaciones usan `/api/quotes` y `/api/renewals`; las solicitudes pendientes usan `/api/payment-requests` para ADMIN/BILLING. Su aprobación es la operación que crea el pago y acredita los minutos, una sola vez.

El portal USER usa exclusivamente `/api/client-finance/**`, requiere institución propia activa y habilitación expresa mediante `/api/client-finance-access` (ADMIN, inicialmente deshabilitado). Las rutas financieras anteriores siguen restringidas a ADMIN/BILLING. Los recibos tienen ruta `/api/payments/{id}/receipt.pdf` para el personal y `/api/client-finance/payments/{id}/receipt.pdf` para el cliente habilitado.

Conocimiento usa `/api/knowledge/**` y `/api/cases/{id}/knowledge-links`. Sólo ADMIN crea, edita, publica o archiva artículos y consulta su historial editorial. TECH/USER sólo consultan artículos publicados; ADMIN/TECH pueden vincular o desvincular artículos publicados en casos de instituciones activas, incluidos casos finales. USER consulta los enlaces publicados de sus casos. Las categorías editoriales son `INSTALLATION`, `CATALOGING`, `LOANS`, `REPORTS`, `IMPORT`, `OTHER`, `GUIDES`, `FAQ`, `SUPPORT`, `POLICIES_LICENSES`, `TRAINING` y `CONTACT`; son distintas del catálogo de seis categorías de ficha técnica.

Encuestas usa `/api/satisfaction/cases/{caseId}`: ADMIN consulta, y USER consulta/responde para su propia institución. Una respuesta nueva requiere caso `CLOSED`, institución activa y ausencia de una encuesta previa; `RESOLVED` todavía no la habilita. POST recibe `{requestId,score,comment}`, con UUID, puntuación entera de 1 a 5 y comentario opcional de hasta 2,000 caracteres. Repetir exactamente la misma respuesta con el mismo UUID y usuario devuelve la existente; una respuesta diferente produce 409. Los reportes `/api/satisfaction/reports`, `/api/satisfaction/reports.csv` y las opciones `/api/satisfaction/options` son exclusivos ADMIN; TECH/BILLING no acceden a encuestas. Respaldos usa `/api/backups/**`, exclusivo ADMIN, con programación desactivada por defecto.

Las guías de cada módulo enumeran DTOs, rutas, estados, límites y errores: [cotizaciones/renovaciones](COTIZACIONES-RENOVACIONES.md), [solicitudes y portal](SOLICITUDES-PAGO.md), [recibos](RECIBOS-PAGO.md), [conocimiento](BASE-CONOCIMIENTOS.md), [encuestas](ENCUESTAS-SATISFACCION.md) y [respaldos](RESPALDOS-AUTOMATICOS.md). No se cambia el mecanismo de sesión ni la protección CSRF.


## Rol técnico y módulos operativos y financieros

`TECH` opera casos y sus recursos técnicos. No administra usuarios, instituciones, cobranza, respaldos o configuración. En un caso nuevo queda asignado a sí mismo; puede tomar uno sin responsable. Sólo ADMIN reasigna a otro responsable y anula consumos. `GET /support-staff` (ADMIN/TECH) devuelve exclusivamente `{id,displayName,role}` de responsables activos. Todos los permisos se verifican en servidor, incluso al invocar la API directamente.

Los contratos adicionales se documentan junto con sus reglas y ejemplos en [vistas y alertas](VISTAS-ALERTAS.md), [plantillas](PLANTILLAS-RESPUESTA.md), [incidencias](INCIDENCIAS.md), [paquetes](PAQUETES-TARIFAS.md), [parcialidades](PARCIALIDADES.md), [conciliación](CONCILIACION-BANCARIA.md) y [estados de cuenta](ESTADOS-CUENTA.md). Las mutaciones exigen sesión y CSRF. Importar/conciliar un CSV no invoca la acreditación de horas.


## Vistas guardadas y bandeja de pendientes

ADMIN/TECH/USER usan `GET /saved-views`, `GET /saved-views/{id}`, `POST /saved-views`, `PUT /saved-views/{id}` y `DELETE /saved-views/{id}` para sus propias vistas. POST devuelve 201; PUT requiere `expectedRevision` y devuelve 409 si la vista cambió. Los identificadores ajenos devuelven 404. Entrada: `{name,filters,expectedRevision?}`; respuesta: `{id,name,filters,revision,updatedAt}`. Se admiten hasta 50 vistas por cuenta, nombres de 1 a 100 caracteres y nombres únicos por propietario.

`filters` acepta exclusivamente `{search,status,assignment,institutionId,category,priority}`:

| Campo | Valores |
| --- | --- |
| `search` | Texto de hasta 300 caracteres; predeterminado vacío. |
| `status` | `all`, `active`, `OPEN`, `IN_PROGRESS`, `WAITING_CLIENT`, `RESOLVED`, `CLOSED`. |
| `assignment` | `all`, `mine`, `unassigned`. USER sólo admite `all`. |
| `institutionId` | Id positivo existente o `null`; USER sólo su propia institución o `null`. |
| `category` | `all` o una de las seis categorías de ficha técnica. |
| `priority` | `all`, `LOW`, `NORMAL`, `HIGH`, `URGENT`. |

Los selectores omitidos usan `all`. `active` es el grupo «Pendientes de resolver»; un filtro nuevo `OPEN` selecciona sólo Recibido. `GET /cases` sigue devolviendo el listado autorizado completo; la interfaz aplica los filtros, y guardar una vista no amplía los permisos ni añade filtros de servidor a esa ruta.

`GET /inbox` devuelve hasta 100 avisos, el total de no leídos y hasta 100 pendientes de respuesta. Los pendientes sólo incluyen casos activos de instituciones activas. `waitingFor` mantiene los códigos `ADMIN`/`CLIENT` para soporte/cliente; `ADMIN` incluye atención por TECH. El listado de soporte considera casos propios o sin responsable a la espera de soporte; USER recibe los de su institución a la espera del cliente. BILLING obtiene una lista vacía de pendientes de casos y conserva sus avisos de saldo. La pausa SMTP no desactiva los avisos del portal.

## Alta asistida de solicitudes de clientes

`POST /api/client-portal/tickets` (rol USER, sesión y CSRF): JSON
`{title, description, channel, profile?}`. Asunto: 5–150 caracteres;
descripción: 20–5,000. La institución se obtiene exclusivamente del usuario
autenticado, no se admite seleccionar responsable y la transacción reutiliza
`HelpdeskService.createCase`: saldo previo obligatorio, id único, historial,
auditoría y notificación existentes. Respuesta 201 con `CaseDetail`.
`POST /api/cases` se conserva para compatibilidad; la interfaz cliente actual
usa `/api/client-portal/tickets`. Los casos nuevos nacen en `OPEN`
(Recibido) y utilizan el flujo de cinco estados de 1.8.3. La autenticación
continúa basada en usuarios y sesiones; una póliza no es una credencial.
