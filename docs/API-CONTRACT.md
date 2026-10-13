# Contrato de integración (v1.6)

Base `/api`. Instantes ISO 8601 UTC; fechas civiles de pagos y pólizas `YYYY-MM-DD`, sin conversión de zona horaria. Identificadores numéricos. Unidad de soporte: minutos enteros (no flotantes). Roles `ADMIN`, `USER` y `BILLING`; canales `EMAIL` y `REMOTE`; estados `OPEN` y `CLOSED`.

Autenticación: sesión HttpOnly, cookie de CSRF `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN`. Angular usa proxy mismo origen. GET `/auth/csrf` inicializa cookie y retorna `{token}`; llamar antes de login y después de login/logout. POST `/auth/login` usa formulario URL encoded `username`, `password`, devuelve 200. POST `/auth/logout` devuelve 204. GET `/auth/me` devuelve User. PUT `/auth/password` JSON `{currentPassword,newPassword}`.

User: `{id,username,displayName,role,institutionId,active,email}`.
Institution: `{id,name,contactName,email,phone,notes,active,emailAllocatedMinutes,emailUsedMinutes,emailRemainingMinutes,remoteAllocatedMinutes,remoteUsedMinutes,remoteRemainingMinutes,supportAdminId}`.
Case: `{id,institutionId,institutionName,title,description,channel,status,createdBy,createdAt,updatedAt,totalMinutes,assignedAdminId,assignedAdminName}`.
Entry: `{id,caseId,description,channel,minutes,internal,createdBy,createdAt,voided,voidReason}`. `channel` null si comentario sin consumo.
CaseDetail: campos de Case más `entries:Entry[]`.
Audit: `{id,actor,action,entityType,entityId,details,createdAt}`.
Error: `{message}` y HTTP 400 validación, 401 sin sesión, 403 no autorizado, 404 inexistente, 409 conflicto/saldo insuficiente.

- GET `/institutions`: array (ADMIN/BILLING todas incluidas bajas; USER solo propia activa).
- POST `/institutions` ADMIN: `{name,contactName,email,phone,notes,emailAllocatedMinutes,remoteAllocatedMinutes,supportAdminId}`.
- PUT `/institutions/{id}` ADMIN: campos anteriores más `active`; no permite asignado menor que usado.
- DELETE `/institutions/{id}` ADMIN: baja lógica sin perder casos.
- GET `/users` ADMIN: User[].
- POST `/users` ADMIN: `{username,displayName,password,role,institutionId,active,email}`. USER requiere institución; ADMIN null.
- PUT `/users/{id}` ADMIN: mismos campos, password opcional vacío conserva. Username, rol e institución son inmutables. No desactivar al último administrador. Para cambiar el ámbito de acceso, crear otra cuenta y desactivar la anterior. Restablecer la contraseña revoca las sesiones de esa cuenta.
- GET `/cases`: Case[] filtrados en servidor por institución del usuario; ADMIN todos.
- POST `/cases`: `{institutionId,title,description,channel,assignedAdminId}`; USER solo propia institución. Rechaza si saldo del canal <=0. Devuelve CaseDetail.
- GET `/cases/{id}`: CaseDetail, USER no recibe entradas internas.
- PUT `/cases/{id}` ADMIN: `{title,description,channel,assignedAdminId}`; audita modificaciones.
- POST `/cases/{id}/status` ADMIN: `{status,reason}`; cerrar siempre permitido; reabrir exige saldo del canal.
- POST `/cases/{id}/entries`: `{description,channel,minutes,internal}`. ADMIN registra minutos >0 y canal o comentario 0/null; USER únicamente comentario 0/null y público. Casos cerrados no aceptan entradas. Devuelve CaseDetail. Consumo y escritura en una transacción con bloqueo de institución; no sobregiro.
- POST `/cases/{id}/entries/{entryId}/void` ADMIN: `{reason}` anula atención y devuelve minutos una única vez; conserva entrada y auditoría. Devuelve CaseDetail.
- GET `/audit` ADMIN: Audit[] (últimas 500).
- GET `/health` público: `{status:"UP"}`.

Listados de instituciones, usuarios y casos completos; interfaz filtra búsquedas y estado. Auditoría muestra los últimos 500 eventos, sin eliminar los anteriores de PostgreSQL. Presupuestos son totales históricos asignados: recargar implica aumentar el total. Bajas bloquean acceso de usuarios de esa institución; historial queda consultable por admin. Nunca borrar físicamente trazabilidad. Contraseñas de al menos 12 caracteres Unicode y como máximo 72 bytes UTF-8; BCrypt. Cambiar la contraseña propia conserva la sesión actual y revoca las otras. Variables backend: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, SERVER_PORT, ADMIN_USERNAME, ADMIN_PASSWORD, COOKIE_SECURE.

`notes` de Institution se devuelve vacío para USER y BILLING. Las entradas internas no se devuelven a USER; sus minutos sí forman parte del consumo total institucional y del caso.


## Contactos y responsables

`email` acepta una dirección individual válida o vacío. En `PUT /users/{id}`, omitir `email` o enviarlo como `null` conserva el actual; `""` lo vacía. `ADMIN_EMAIL` únicamente establece el correo al crear el administrador inicial: no modifica cuentas existentes.

`supportAdminId` es un ADMIN activo predeterminado para casos nuevos. `assignedAdminId` identifica al responsable del caso. En los PUT respectivos, omitir el campo conserva el valor; `null` elimina la asignación. En POST de casos, si no se indica responsable se usa el predeterminado activo; si falta y crea un ADMIN, se asigna el creador. Un USER no puede indicar un responsable. Cambiar el predeterminado institucional no reasigna casos previos. El responsable no limita el acceso: los ADMIN consultan todos los casos.

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

Sin póliza se requieren importe y algún canal positivos. Con póliza se permite un abono positivo sin minutos; una acreditación con importe cero exige póliza ya liquidada y minutos pendientes. No se permite un movimiento totalmente en cero ni superar los importes/minutos contratados vigentes. Cada bolsa institucional mantiene el máximo de mil millones de minutos asignados. Notas y referencia obligatorias. Consulte [Pólizas y cobranza 1.5](#pólizas-y-cobranza-15) para límites e historial.

`requestId` es UUID persistente durante reintentos de la misma operación: repetir el mismo contenido devuelve el pago previo (también HTTP 201) sin acreditar ni notificar otra vez. Reutilizarlo con contenido diferente da 409. La referencia no puede repetirse en la misma institución, ignorando mayúsculas y espacios exteriores, incluso después de una anulación. No hay edición ni borrado del registro: anular y registrar una corrección con otra referencia. No se realizan cargos bancarios ni se genera factura fiscal.

Los ADMIN conservan el ajuste directo del presupuesto institucional como operación auditada independiente de pagos. Cobranza sólo incrementa minutos mediante pagos. Los acumulados monetarios se separan por moneda y excluyen anulados.

## Notificaciones

- GET `/notifications` ADMIN: últimos 500 avisos sin cuerpo del mensaje ni secretos. Campos `{id,caseId,paymentId,eventType,recipient,subject,status,attempts,lastError,createdAt,sentAt}`.
- GET `/notifications/{id}` ADMIN: los campos del listado más `{body,internal,recipientType,recipientEntityId}`. Devuelve el contenido persistido del aviso, no una regeneración con datos actuales. HTTP 404 si no existe; 401 sin sesión y 403 para USER/BILLING. Sólo lectura: no cambia estados, intentos, auditoría ni genera envíos. No expone tokens de procesamiento ni configuración secreta.
- GET `/notifications/config` ADMIN: `{enabled,configuredEnabled,paused,host,port,username,fromAddress,fromName,security,auth,replyTo,pendingCount,failedCount,suppressedCount}`. Nunca incluye contraseña SMTP.
- POST `/notifications/test` ADMIN: `{}`; HTTP 201, encola prueba exclusivamente al correo de la propia cuenta. Rechaza destinatarios o contenido arbitrarios.
- POST `/notifications/{id}/retry` ADMIN: `{}`; sólo FAILED/SKIPPED. No permite reenviar SENT. Conserva el destinatario original; si faltaba su email, consulta el contacto actual de esa misma entidad. No permite elegir otro destinatario. Avisos sin responsable identificado requieren asignación para eventos futuros.

Eventos `CREATE_CASE`, `CLAIM_CASE`, `UPDATE_CASE`, `CHANGE_CASE_STATUS`, `CREATE_ENTRY`, `VOID_ENTRY`, `CREATE_PAYMENT`, `VOID_PAYMENT`, `TEST_MAIL`. Un cambio de estado al mismo estado es una operación sin cambios: no crea entrada ni correo duplicados. Un cambio a CLOSED siempre registra motivo y encola aviso público para institución y responsable. Los eventos internos y su anulación únicamente se encolan al ADMIN responsable; si su dirección coincide con la institucional, se omiten para evitar divulgar notas privadas.

Los mensajes públicos se envían por separado al correo institucional y al ADMIN asignado; direcciones idénticas se deduplican por evento. Los pagos se notifican a la institución y al ADMIN/BILLING autor del pago. Las notas del pago forman parte del aviso. Las direcciones se capturan al registrar el evento: cambiarlas no modifica mensajes ya pendientes. Las operaciones fallidas no dejan mensajes en la cola.

Estados `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED`, `SUPPRESSED`. SUPPRESSED es terminal y no permite reintento. MAIL_ENABLED=false conserva PENDING si no hay pausa administrativa; esa pausa utiliza SUPPRESSED. Fuera de la pausa, una dirección ausente o no válida deja SKIPPED visible. SENT significa aceptación por el servidor SMTP, no confirma entrega final ni lectura. Los reintentos automáticos son limitados (máximo cinco), con espera creciente y recuperación de bloqueos vencidos. La entrega es al menos una vez: una caída entre aceptación SMTP y confirmación en base puede duplicar un aviso.

Configuración, proveedores, puertos, TLS, pruebas locales y puesta en operación en [CORREO.md](CORREO.md). Migración sin borrar datos en [MIGRACION-1.1.md](MIGRACION-1.1.md).


La versión 1.2 conserva este contrato y añade SMTP Postfix interno predeterminado en nuevas instalaciones. Su configuración de relay no se expone por la API; se administra con `--smtp-interno`. `/notifications/config` describe el primer salto de Spring (`smtp:25`), no el relay posterior de Postfix. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md).

## Control de envío añadido en 1.3.1

POST `/notifications/config` ADMIN: cuerpo exacto `{"paused":true}` para pausar o `{"paused":false}` para reactivar. Exige sesión y CSRF, rechaza campos extra y valores ausentes, null o distintos de boolean. Devuelve Configuration; `enabled` es el estado efectivo, `configuredEnabled` refleja MAIL_ENABLED al arranque y `paused` el estado persistido en PostgreSQL. Repetir el mismo valor no añade auditoría duplicada. Una pausa/reanudación real registra PAUSE_NOTIFICATIONS/RESUME_NOTIFICATIONS. El control no puede superar MAIL_ENABLED=false. Al pausar se suprimen PENDING/FAILED/SKIPPED existentes y los avisos nuevos se guardan SUPPRESSED. Al reactivar sólo se envían eventos nuevos; pruebas/reintentos se bloquean durante la pausa y ningún SUPPRESSED puede reintentarse. Los ya reclamados pueden terminar; véase [DESACTIVAR-CORREO.md](DESACTIVAR-CORREO.md).

## Toma de casos añadida en 1.3.3

POST `/cases/{id}/claim` ADMIN con sesión y CSRF; no requiere datos (la interfaz envía `{}`). Devuelve HTTP 200 con `CaseDetail`. Asigna el caso abierto y sin responsable al administrativo de la sesión, con bloqueo transaccional de institución y caso; registra entrada pública, auditoría y notificación `CLAIM_CASE`. La pausa de correo se respeta como en cualquier otro evento.

Un caso cerrado, una institución inactiva o un responsable diferente ya asignado producen 409. Repetir la toma de un caso abierto que ya pertenece al mismo administrativo devuelve su detalle sin duplicar efectos. USER/BILLING reciben 403. El cliente mantiene POST `/cases` para su propia institución y con saldo en el canal: si existe un responsable institucional, se asigna automáticamente; de lo contrario, el caso queda disponible para tomar. No puede asignar responsables ajenos ni abrir solicitudes para otra institución.


## Extensiones 1.4

Todas las rutas siguientes usan el prefijo `/api`, sesión y CSRF en las escrituras. USER sólo accede a su institución; los IDs ajenos devuelven 404. BILLING no accede a casos, adjuntos de soporte, cronómetro, agenda ni reportes técnicos. Los avisos propios de saldo sí están disponibles para Cobranza; sus reportes financieros y comprobantes usan las rutas 1.5 descritas más adelante.

| Método y ruta | Uso / rol |
| --- | --- |
| `GET /case-profiles` | Clasificación resumida por caso autorizado; ADMIN / USER. |
| `GET/PUT /cases/{id}/profile` | Ficha y SLA / actualizar ficha de caso abierto; ADMIN / USER. |
| `GET/PUT /support-settings` | Calendario y objetivos SLA; ADMIN. |
| `GET /cases/{id}/attachments` | Adjuntos del caso; query `entryId` para un comentario concreto. |
| `POST /cases/{id}/attachments` | Multipart `file` + `requestId` UUID, query `entryId` opcional; ADMIN / USER autorizado. |
| `GET /attachments/{id}/download` | Archivo binario; autenticación y privacidad del comentario. |
| `GET /inbox` | `{items,unreadCount,pendingReplies}` del usuario actual. |
| `POST /inbox/{id}/read`, `POST /inbox/read-all` | Marcar leído; cuerpo vacío o `{}`, respuesta 204. |
| `GET /balance-alert-settings` | Umbrales y saldos por institución; ADMIN. |
| `PUT /balance-alert-settings/{institutionId}` | `{emailThresholdMinutes,remoteThresholdMinutes,enabled}`; ADMIN. |
| `GET /cases/{id}/timer` | Último cronómetro propio, o null; ADMIN. |
| `POST /cases/{id}/timer/start` | Iniciar cronómetro propio; ADMIN. |
| `POST /timers/{id}/pause`, `/resume`, `/finish`, `/cancel` | Operación del propietario ADMIN. |
| `POST /timers/{id}/commit` | Confirmar `{minutes,channel,description,internal,adjustmentReason}`; descuento idempotente. |
| `GET/POST /cases/{id}/remote-sessions` | Listar agenda autorizada / proponer como ADMIN. |
| `POST /remote-sessions/{id}/confirm`, `/decline` | Responder como USER de la institución; `{response}`. |
| `POST /remote-sessions/{id}/cancel`, `/complete` | Operación ADMIN; `{response}`. |
| `POST /remote-sessions/{id}/reschedule` | Nueva propuesta ADMIN, cancelando la anterior. |
| `GET /reports/support`, `/reports/support.xlsx`, `/reports/support.pdf` | ADMIN; filtros `institutionId`, `administratorId`, `channel`, `from`, `to`. |

`POST /cases` admite `profile` opcional con `category`, `priority`, `siabucVersion`, `operatingSystem`, `errorMessage` y `reproductionSteps`. La creación del caso y su ficha es atómica; una ficha inválida revierte toda la solicitud. Si no se incluye, se conserva compatibilidad con clientes anteriores: OTHER/NORMAL y datos técnicos vacíos. Los mismos campos se usan en PUT de ficha.

Categorías: INSTALLATION, CATALOGING, LOANS, REPORTS, IMPORT, OTHER. Prioridades: LOW, NORMAL, HIGH, URGENT. `CaseDetail` conserva su estructura previa: la ficha detallada y sus plazos se consultan por la ruta de perfil. [Clasificación y SLA](CLASIFICACION-SLA.md) documenta objetivos, calendario y estados de cumplimiento.

La subida utiliza multipart, no JSON ni Base64, y no debe fijarse manualmente el boundary en el navegador. El cliente genera un UUID por carga; reintentar con el mismo UUID y contenido no duplica el archivo. Un UUID reutilizado con otro archivo o ubicación se rechaza. Superar 10 MiB produce 413; tipos/contenidos no admitidos producen 400; caso cerrado o límites acumulados producen conflicto. Las descargas incluyen `Content-Disposition: attachment`, protección contra interpretación de tipos y control de caché. Consulte [Adjuntos](HELPDESK-1.4.md#archivos-adjuntos).

La propuesta de agenda usa `{startsAt,endsAt,note,adminId?}` con fechas ISO-8601 que identifican un instante; el responsable predeterminado al crear es el administrativo actor. Reprogramar requiere confirmación nueva. [Cronómetro y agenda](CRONOMETRO-AGENDA.md) detalla transiciones y reintentos.

Los informes devuelven 400 si exceden 366 días, 5,000 filas combinadas o sus límites de contenido; nunca se recortan silenciosamente. Excel utiliza el tipo MIME OOXML, PDF `application/pdf`; ambos usan descarga adjunta. Las fechas finales son inclusivas en `REPORTING_TIME_ZONE`. Consulte [Reportes](REPORTES.md) para la semántica exacta de filtros y anulaciones.


## Pólizas y cobranza 1.5

BILLING y ADMIN pueden gestionar pólizas y consultar reportes financieros. USER recibe 403. El acceso financiero no concede a BILLING acceso a casos, notas internas, adjuntos de soporte ni reportes técnicos. Todas las escrituras conservan sesión y CSRF.

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

BILLING y ADMIN pueden `GET /api/payments/{id}/attachments`, subir mediante `POST` a la misma ruta con multipart `file` y `requestId` UUID, y descargar mediante `GET /api/payment-attachments/{id}/download`. USER recibe 403. Los adjuntos financieros no usan las rutas de expedientes de soporte.

Se admiten PDF, PNG y JPEG; 10 MiB por archivo, 10 archivos y 50 MiB acumulados por pago. El servidor valida contenido, cuotas y nombre. Reintentar la misma solicitud no duplica el comprobante. La evidencia puede agregarse a pagos históricos, incluidos anulados y de instituciones inactivas. No cambia importes ni minutos. Las descargas requieren sesión autorizada y se entregan como archivo adjunto con protección de tipo/caché.

El contenido binario se guarda en PostgreSQL. Se conservan autor, fecha y SHA-256; no hay borrado físico. Cuando el pago tiene póliza, la carga se registra también en su historial como `ATTACH_PAYMENT_PROOF`. Consulte [Comprobantes de pago](COMPROBANTES-PAGO.md).

## Extensiones 1.6

Las funciones anteriores se conservan. Cotizaciones y renovaciones usan `/api/quotes` y `/api/renewals`; las solicitudes pendientes usan `/api/payment-requests` para ADMIN/BILLING. Su aprobación es la operación que crea el pago y acredita los minutos, una sola vez.

El portal USER usa exclusivamente `/api/client-finance/**`, requiere institución propia activa y habilitación expresa mediante `/api/client-finance-access` (ADMIN, inicialmente deshabilitado). Las rutas financieras anteriores siguen restringidas a ADMIN/BILLING. Los recibos tienen ruta `/api/payments/{id}/receipt.pdf` para el personal y `/api/client-finance/payments/{id}/receipt.pdf` para el cliente habilitado.

Conocimiento usa `/api/knowledge/**` y `/api/cases/{id}/knowledge-links`; sólo ADMIN edita/publica y USER únicamente consulta contenido publicado autorizado. Encuestas usa `/api/satisfaction/cases/{caseId}` para casos propios y `/api/satisfaction/reports` para ADMIN. Respaldos usa `/api/backups/**`, exclusivo ADMIN, con programación desactivada por defecto.

Las guías de cada módulo enumeran DTOs, rutas, estados, límites y errores: [cotizaciones/renovaciones](COTIZACIONES-RENOVACIONES.md), [solicitudes y portal](SOLICITUDES-PAGO.md), [recibos](RECIBOS-PAGO.md), [conocimiento](BASE-CONOCIMIENTOS.md), [encuestas](ENCUESTAS-SATISFACCION.md) y [respaldos](RESPALDOS-AUTOMATICOS.md). No se cambia el mecanismo de sesión ni la protección CSRF.
