# Contrato de integración (v1.2)

Base `/api`. Fechas ISO 8601 UTC. Identificadores numéricos. Unidad de soporte: minutos enteros (no flotantes). Roles `ADMIN`, `USER` y `BILLING`; canales `EMAIL` y `REMOTE`; estados `OPEN` y `CLOSED`.

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

`BILLING` puede consultar instituciones sin notas privadas y registrar/consultar pagos. No accede a casos, usuarios, auditoría, notificaciones ni modificaciones institucionales. Puede cambiar su propia contraseña.

- GET `/payments` ADMIN/BILLING: lista completa de pagos, incluidos anulados, orden descendente de id.
- POST `/payments` ADMIN/BILLING: registra un pago recibido y acredita sus minutos en una única transacción; HTTP 201.
- POST `/payments/{id}/void` ADMIN: `{reason}`; retira la acreditación una sola vez si hay saldo disponible suficiente en ambos canales; conserva el pago y su auditoría. HTTP 200; segundo intento 409.

Entrada de pago:

```json
{
  "requestId": "45104c2a-c447-4cac-b9c3-405d6e1d5683",
  "institutionId": 1,
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

Respuesta: campos de entrada más `{id,institutionName,createdBy,createdAt,voided,voidReason,voidedBy,voidedAt}`. Importe positivo, máximo `999999999.99` y dos decimales; moneda de tres letras mayúsculas. Métodos `TRANSFER`, `CASH`, `CARD`, `OTHER`. Debe acreditar al menos un minuto; cada bolsa mantiene el máximo de mil millones de minutos asignados. Notas y referencia obligatorias. Fecha civil `YYYY-MM-DD`, sin conversión de zona horaria.

`requestId` es UUID persistente durante reintentos de la misma operación: repetir el mismo contenido devuelve el pago previo (también HTTP 201) sin acreditar ni notificar otra vez. Reutilizarlo con contenido diferente da 409. La referencia no puede repetirse en la misma institución, ignorando mayúsculas y espacios exteriores, incluso después de una anulación. No hay edición ni borrado del registro: anular y registrar una corrección con otra referencia. No se realizan cargos bancarios ni se genera factura fiscal.

Los ADMIN conservan el ajuste directo del presupuesto institucional como operación auditada independiente de pagos. Cobranza sólo incrementa minutos mediante pagos. Los acumulados monetarios se separan por moneda y excluyen anulados.

## Notificaciones

- GET `/notifications` ADMIN: últimos 500 avisos sin cuerpo del mensaje ni secretos. Campos `{id,caseId,paymentId,eventType,recipient,subject,status,attempts,lastError,createdAt,sentAt}`.
- GET `/notifications/config` ADMIN: `{enabled,host,port,username,fromAddress,fromName,security,auth,replyTo,pendingCount,failedCount}`. Nunca incluye contraseña SMTP.
- POST `/notifications/test` ADMIN: `{}`; HTTP 201, encola prueba exclusivamente al correo de la propia cuenta. Rechaza destinatarios o contenido arbitrarios.
- POST `/notifications/{id}/retry` ADMIN: `{}`; sólo FAILED/SKIPPED. No permite reenviar SENT. Conserva el destinatario original; si faltaba su email, consulta el contacto actual de esa misma entidad. No permite elegir otro destinatario. Avisos sin responsable identificado requieren asignación para eventos futuros.

Eventos `CREATE_CASE`, `UPDATE_CASE`, `CHANGE_CASE_STATUS`, `CREATE_ENTRY`, `VOID_ENTRY`, `CREATE_PAYMENT`, `VOID_PAYMENT`, `TEST_MAIL`. Un cambio de estado al mismo estado es una operación sin cambios: no crea entrada ni correo duplicados. Un cambio a CLOSED siempre registra motivo y encola aviso público para institución y responsable. Los eventos internos y su anulación únicamente se encolan al ADMIN responsable; si su dirección coincide con la institucional, se omiten para evitar divulgar notas privadas.

Los mensajes públicos se envían por separado al correo institucional y al ADMIN asignado; direcciones idénticas se deduplican por evento. Los pagos se notifican a la institución y al ADMIN/BILLING autor del pago. Las notas del pago forman parte del aviso. Las direcciones se capturan al registrar el evento: cambiarlas no modifica mensajes ya pendientes. Las operaciones fallidas no dejan mensajes en la cola.

Estados `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED`. Correo deshabilitado conserva PENDING; dirección ausente o no válida deja SKIPPED visible. SENT significa aceptación por el servidor SMTP, no confirma entrega final ni lectura. Los reintentos automáticos son limitados (máximo cinco), con espera creciente y recuperación de bloqueos vencidos. La entrega es al menos una vez: una caída entre aceptación SMTP y confirmación en base puede duplicar un aviso.

Configuración, proveedores, puertos, TLS, pruebas locales y puesta en operación en [CORREO.md](CORREO.md). Migración sin borrar datos en [MIGRACION-1.1.md](MIGRACION-1.1.md).


La versión 1.2 conserva este contrato y añade SMTP Postfix interno predeterminado en nuevas instalaciones. Su configuración de relay no se expone por la API; se administra con `--smtp-interno`. `/notifications/config` describe el primer salto de Spring (`smtp:25`), no el relay posterior de Postfix. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md).
