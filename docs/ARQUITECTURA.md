# Arquitectura y reglas de integridad

## Componentes

- **Angular:** interfaz de administración y portal institucional. Conversión de horas/minutos, formularios, búsquedas y vistas según rol.
- **Nginx:** sirve la compilación de Angular y reenvía `/api/` a Spring por la red de Docker. Las rutas del cliente regresan a `index.html`.
- **Spring Boot:** autenticación, autorización, reglas de negocio, acceso JDBC, transacciones y auditoría. El cliente no decide permisos ni saldos.
- **PostgreSQL:** persistencia relacional, restricciones de integridad y bloqueos de filas. Las migraciones Flyway crean el esquema en el primer inicio.
- **SMTP/outbox:** cola PostgreSQL creada junto con los eventos, procesador periódico y conexión de correo configurable. No se realiza I/O SMTP dentro de la transacción de negocio.
- **Postfix interno:** SMTP de salida en Docker, autorizado únicamente para loopback y la subred dedicada al backend. Entrega directamente por MX o mediante relay. Cola durable `smtp_queue`, sin buzones ni bandeja web.
- **Mailpit opcional:** sumidero y bandeja local para pruebas de correo, perfil Docker `mail-test`.

En desarrollo, el servidor Angular ocupa el lugar de Nginx y su proxy toma `SERVER_PORT` del entorno. En Docker, los puertos internos son estables; sólo se configuran sus publicaciones en el anfitrión.

## Modelo lógico

Una **institución** contiene contacto, estado, responsable predeterminado y dos presupuestos. Un **usuario** tiene correo y pertenece a una institución cuando su rol es `USER`; `ADMIN` y `BILLING` no tienen institución. Cada **caso** pertenece a una institución y contiene título, descripción, canal inicial, estado, responsable asignado, autor y fechas. Cada **entrada** pertenece a un caso y describe una atención o comentario. Un **pago** contiene importe, referencia, institución, acreditación y trazabilidad de anulación. Una **notificación** conserva evento, destinatario individual, contenido y estado de entrega. Un **evento de auditoría** identifica actor, acción, entidad, detalle y fecha.

El esquema concreto y sus restricciones se encuentran en `backend/src/main/resources/db/migration/`: V1 original, V2 de cobranza/contactos/asignaciones y V3 de notificaciones y V4 de control persistente del envío. No cambie una migración que ya se haya aplicado a una base existente; añada la siguiente migración versionada. Consulte [MIGRACION-1.1.md](MIGRACION-1.1.md).

## Minutos e independencia de canales

Se emplean enteros. La interfaz calcula `minutos = horas × 60 + minutos_adicionales`. No usa horas decimales ni redondeos de fracciones para la persistencia. Las dos identidades fundamentales son:

```text
correo_disponible = correo_asignado - correo_utilizado
remoto_disponible = remoto_asignado - remoto_utilizado
```

Una atención descuenta únicamente el canal registrado en esa entrada. El canal inicial del caso determina la validación al abrir/reabrir, pero no restringe la atención posterior a un único medio. Las notas sin consumo usan `minutes=0` y `channel=null`.

Los presupuestos se expresan como total histórico; recargar significa incrementar el asignado. No hay vencimientos, renovación mensual automática ni trasvase de saldo entre canales en esta versión.

## Concurrencia, errores y trazabilidad

Registrar o anular consumo se realiza en una transacción. Se bloquea la institución antes de comprobar o modificar el saldo. Dos administradores que intenten gastar al mismo tiempo compiten por el mismo bloqueo: el segundo verifica el saldo ya actualizado y recibe un conflicto si no alcanza. La operación fallida no deja una atención parcial ni un saldo negativo.

Las anulaciones conservan descripción y minutos originales, añaden un motivo y devuelven el saldo una única vez. Los contadores de tiempo utilizado sólo se modifican por las operaciones de atención/anulación del servicio. La baja institucional es lógica: no elimina los datos referenciados por casos y usuarios.

La API responde `400` a entradas inválidas, `401` a solicitudes sin sesión, `403` a operaciones no autorizadas y `409` a conflictos de saldo o estado; los recursos no accesibles pueden responder `404` para no revelar su existencia. El cliente muestra el mensaje del servidor. El contrato completo está en [API-CONTRACT.md](API-CONTRACT.md).

Los pagos utilizan importes decimales y minutos enteros. Registrar el pago, acreditar las bolsas, auditar y crear sus notificaciones es atómico. Un UUID de solicitud protege ante reintentos y una referencia única normalizada por institución evita duplicados operativos. Anular requiere saldo disponible suficiente para retirar toda la acreditación, conserva el registro y nunca ejecuta un reembolso bancario.

La cola conserva los estados `PENDING`, `SENDING`, `SENT`, `FAILED` y `SKIPPED`; procesa fuera de la transacción de negocio, limita intentos y recupera bloqueos vencidos tras caídas. La entrega es al menos una vez: puede duplicarse si SMTP acepta y el proceso cae antes de confirmar el envío en la base. La falta de correo o responsable queda registrada como omisión. Más detalles y fuentes primarias en [CORREO.md](CORREO.md).

## Sesiones y permisos

La autenticación usa sesión del servidor, cookie de sesión `HttpOnly` y `SameSite=Strict`. El cliente obtiene el token CSRF mediante `GET /api/auth/csrf` y lo reenvía en `X-XSRF-TOKEN`; repite la obtención al cambiar el estado de autenticación. La cookie CSRF debe ser legible por Angular y no contiene una contraseña. La configuración `COOKIE_SECURE=true` exige HTTPS para la cookie de sesión.

Las contraseñas se almacenan como hashes BCrypt, nunca como texto recuperable. Las respuestas de usuarios no incluyen el hash. Spring restringe las consultas al ámbito institucional del usuario y elimina información interna de las respuestas de ese rol. Un usuario desactivado o vinculado a una institución dada de baja no conserva autorización por tener una sesión previa.

`BILLING` puede consultar instituciones sin notas administrativas, registrar/consultar pagos y cambiar su contraseña. No puede operar casos, usuarios, auditoría, notificaciones o bajas institucionales ni anular pagos. La asignación como responsable exige un `ADMIN` activo.

Las sesiones residen en memoria de Spring: un reinicio cierra las sesiones. La arquitectura entregada es de una instancia y no incluye balanceo, sesiones distribuidas, MFA, SSO ni recuperación de contraseña por correo. Los administradores pueden restablecer contraseñas desde Usuarios.

## Operación y capacidad

La persistencia de negocio usa un volumen Docker de PostgreSQL. Postfix conserva una segunda cola en `smtp_queue`; SENT en PostgreSQL significa aceptación del primer SMTP, no finalización de la entrega remota. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md). Los archivos compilados de Angular y el JAR se reconstruyen a partir del código. La inicialización administrativa utiliza una contraseña de `.env` sólo cuando falta la cuenta de arranque según la lógica de inicialización; no sustituye la administración regular de contraseñas.

El objetivo es un helpdesk institucional de volumen moderado. Los listados se cargan en el cliente y se filtran en la interfaz; no se ha realizado una prueba de carga productiva ni se declara un número máximo validado de usuarios simultáneos. Antes de un crecimiento grande, conviene añadir paginación del servidor, índices según consultas reales y monitoreo. La auditoría de interfaz presenta los últimos 500 eventos.

La configuración inicial publica todos los servicios sólo en localhost. Para acceso externo necesita terminación HTTPS, políticas de red, procedimiento de respaldo y administración de cuentas acordes con su instalación. Consulte [OPERACION.md](OPERACION.md).

## Referencias técnicas

Documentación primaria revisada durante la preparación de esta entrega:

- [Requisitos de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- [Compatibilidad de versiones Angular, Node.js y TypeScript](https://angular.dev/reference/versions).
- [Bloqueos explícitos y bloqueos de filas en PostgreSQL 17](https://www.postgresql.org/docs/17/explicit-locking.html).

Estas referencias sustentan las decisiones de compatibilidad y concurrencia. Los resultados de ejecución y sus límites se documentan por separado en [VALIDACION.md](VALIDACION.md).

## Pausa administrativa del envío

La fila única de notification_settings conserva paused y suppressed_through_id. El envío requiere MAIL_ENABLED=true y paused=false. La misma fila se bloquea para cambiar el control, encolar eventos y reclamar avisos; fija una frontera de desactivación. Los PENDING/FAILED/SKIPPED anteriores pasan a SUPPRESSED y los avisos nuevos durante la pausa nacen SUPPRESSED, terminal y sin reintento. Los SENDING en curso pueden completar, pero un fallo o lease vencido anterior a la frontera no vuelve a PENDING. No se mantiene el bloqueo durante SMTP ni se retiran mensajes de Postfix. El cambio se audita y se incluye en respaldos de PostgreSQL.


## Módulos y persistencia desde 1.4

- **V5:** `support_settings` conserva calendario y objetivos globales; `case_profiles` guarda clasificación, ficha, snapshot de calendario y marcas de primera respuesta/resolución. El cálculo usa minutos laborales y no modifica las bolsas contratadas. La migración reconstruye datos heredados sólo a partir de evidencia de auditoría disponible.
- **V6:** `case_attachments` guarda datos binarios BYTEA, ubicación caso/comentario, identidad de solicitud y hash. Autorización por caso y visibilidad del comentario; validación de contenido, tamaño individual y cuotas agregadas bajo bloqueo. El respaldo PostgreSQL incluye los archivos.
- **V7:** `portal_notifications`, configuración/estado de alertas y `support_cases.waiting_for` implementan campana, pendientes y aviso de saldo bajo. Se registran dentro de la transacción del evento, antes de cualquier decisión de SMTP. La lectura vuelve a comprobar destinatario, rol, institución y privacidad; un aviso almacenado no otorga permiso por sí mismo.
- **V8:** `work_timers` persiste segmentos y un índice único parcial permite un solo reloj pendiente por administrativo. `remote_sessions` conserva propuestas y respuestas. La confirmación del cronómetro reutiliza el registro contable existente, bajo bloqueos institución → caso → cronómetro, y vincula una sola atención. Proponer o confirmar una reserva no cobra minutos.

La agenda serializa decisiones de solapamiento por administrativo con bloqueo `FOR NO KEY UPDATE`, compatible con referencias de claves foráneas. La consulta y creación ocurren en la misma transacción. Reprogramar adquiere ese bloqueo antes de generar historial/notificaciones, conservando el orden de las otras operaciones de agenda.

Los informes se consultan con transacción de sólo lectura y aislamiento REPEATABLE READ; el conjunto de cada solicitud usa una instantánea consistente. La consulta y la descarga son solicitudes distintas, de modo que pueden reflejar cambios posteriores. Apache POI genera XLSX con celdas de texto para descripciones; PDFBox genera PDF sin recursos externos. Los filtros usan parámetros SQL y las salidas se limitan explícitamente.

Los clientes Angular consultan componentes separados para seguimiento, ficha/SLA, adjuntos y agenda. La campana se actualiza periódicamente y no depende de un envío SMTP exitoso. Las rutas nuevas están protegidas tanto por Spring Security como por comprobaciones de rol/propiedad en servicios. Los detalles operativos y límites figuran en [Funciones Helpdesk 1.4](HELPDESK-1.4.md).


## Pólizas y cobranza desde 1.5

V9 agrega `support_policies`, `policy_history` y el vínculo opcional `payments.policy_id`. Una clave foránea compuesta exige que póliza y pago pertenezcan a la misma institución; los pagos vinculados deben usar MXN. La migración conserva las filas antiguas, sin inventar contratos ni modificar monedas, importes o acreditaciones.

La póliza describe lo contratado; crearla no toca las bolsas institucionales. Un pago bloquea institución y póliza, verifica acumulados vigentes y registra el movimiento financiero, la acreditación, el historial y los avisos en una transacción. La clave de solicitud de pago protege frente a reintentos y también compara el vínculo de póliza. Los objetivos contratados no se pueden editar después de cualquier pago, aun anulado. La anulación autorizada conserva la evidencia y retira minutos sólo si el saldo institucional lo permite.

Los saldos financieros se derivan de pagos vigentes; no se mantienen contadores monetarios independientes que puedan desincronizarse. Se usan `BigDecimal` y columnas NUMERIC con dos decimales. Los reportes separan monedas históricas y calculan importes en Spring; una solicitud de reporte usa una instantánea REPEATABLE READ. Los periodos de cobro y la situación actual de pólizas se presentan separadamente.

Las rutas financieras permiten BILLING/ADMIN antes de la regla general que restringe reportes técnicos a ADMIN. Los servicios vuelven a comprobar permisos. Los registros de pagos y pólizas son informativos de operaciones recibidas; no se integró una pasarela bancaria ni facturación fiscal.


V10 incorpora `payment_attachments`: archivos BYTEA asociados a pagos, con UUID de carga, digest y metadatos. Sus permisos son financieros (BILLING/ADMIN), independientes de los adjuntos de casos. Las cuotas se verifican bajo bloqueo del pago, respetando el orden institución → póliza → pago. La carga no acredita ni retira minutos, y el respaldo PostgreSQL incluye los bytes. El historial financiero agrega ATTACH_PAYMENT_PROOF; los avisos SMTP existentes no transportan estos archivos.
