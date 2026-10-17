# Arquitectura y reglas de integridad · 1.8.3

## Componentes

- **Angular:** interfaz de administración y portal institucional. Conversión de horas/minutos, formularios, búsquedas y vistas según rol.
- **Nginx:** sirve la compilación de Angular y reenvía `/api/` a Spring por la red de Docker. Las rutas del cliente regresan a `index.html`.
- **Spring Boot:** autenticación, autorización, reglas de negocio, acceso JDBC, transacciones y auditoría. El cliente no decide permisos ni saldos.
- **PostgreSQL:** persistencia relacional, restricciones de integridad y bloqueos de filas. Las migraciones Flyway crean el esquema en el primer inicio y aplican extensiones versionadas al actualizar.
- **SMTP/outbox:** cola PostgreSQL creada junto con los eventos, procesador periódico y conexión de correo configurable. No se realiza I/O SMTP dentro de la transacción de negocio.
- **Postfix opcional:** SMTP de salida en el perfil `local-smtp`, autorizado para loopback y los contenedores de la red `default` del proyecto. Docker asigna la subred; no se crea una red SMTP dedicada. Entrega directamente por MX o mediante relay. Cola durable `smtp_queue`, sin buzones ni bandeja web.
- **Worker de respaldos:** servicio Docker `backup`, con acceso a PostgreSQL y a un directorio privado del anfitrión. Ejecuta `pg_dump` y conserva estado/hash/retención sin montar el socket Docker ni el volumen de datos de PostgreSQL. Su calendario se instala desactivado.
- **Mailpit opcional:** sumidero y bandeja local para pruebas de correo, perfil Docker `mail-test`.

En desarrollo, el servidor Angular ocupa el lugar de Nginx y su proxy toma `SERVER_PORT` del entorno. En Docker, los puertos internos son estables; sólo se configuran sus publicaciones en el anfitrión.

## Modelo lógico

Una **institución** contiene contacto, estado, responsable predeterminado y dos presupuestos. Un **usuario** tiene correo y pertenece a una institución cuando su rol es `USER`; `ADMIN`, `TECH` y `BILLING` no tienen institución. Cada **caso** pertenece a una institución y contiene título, descripción, canal inicial, estado, responsable asignado, autor y fechas. Cada **entrada** pertenece a un caso y describe una atención o comentario. Un **pago** contiene importe, referencia, institución, acreditación y trazabilidad de anulación. Una **notificación** conserva evento, destinatario individual, contenido y estado de entrega. Un **evento de auditoría** identifica actor, acción, entidad, detalle y fecha.

El esquema concreto y sus restricciones se encuentran en `backend/src/main/resources/db/migration/`: V1 original, V2 de cobranza/contactos/asignaciones, V3 de notificaciones y V4 de control persistente del envío; V5–V10 incorporan los módulos de 1.4/1.5 y V11–V16 los de 1.6. V17–V24 corresponden a 1.7, V25 amplía las categorías de conocimiento y V26 incorpora el flujo de casos de 1.8.3. No cambie migraciones ya aplicadas; añada la siguiente migración versionada. Las guías anteriores conservan el contexto de cada incorporación.

## Minutos e independencia de canales

Se emplean enteros. La interfaz calcula `minutos = horas × 60 + minutos_adicionales`. No usa horas decimales ni redondeos de fracciones para la persistencia. Las dos identidades fundamentales son:

```text
correo_disponible = correo_asignado - correo_utilizado
remoto_disponible = remoto_asignado - remoto_utilizado
```

Una atención descuenta únicamente el canal registrado en esa entrada. El canal inicial del caso determina la validación al abrir/reabrir, pero no restringe la atención posterior a un único medio. Las notas sin consumo usan `minutes=0` y `channel=null`.

Los presupuestos se expresan como total histórico; recargar significa incrementar el asignado. El saldo acreditado no caduca ni se renueva mensualmente de forma automática y no se transfiere entre canales. Las pólizas tienen vigencia informativa; las renovaciones de 1.6 crean otro contrato mediante una cotización aceptada, sin aumentar ni retirar minutos por sí mismas.

## Concurrencia, errores y trazabilidad

Registrar o anular consumo se realiza en una transacción. Se bloquea la institución antes de comprobar o modificar el saldo. Dos integrantes de soporte (`ADMIN` o `TECH`) que intenten gastar al mismo tiempo compiten por el mismo bloqueo: el segundo verifica el saldo ya actualizado y recibe un conflicto si no alcanza. La operación fallida no deja una atención parcial ni un saldo negativo.

Las anulaciones conservan descripción y minutos originales, añaden un motivo y devuelven el saldo una única vez. Los contadores de tiempo utilizado sólo se modifican por las operaciones de atención/anulación del servicio. La baja institucional es lógica: no elimina los datos referenciados por casos y usuarios.

La API responde `400` a entradas inválidas, `401` a solicitudes sin sesión, `403` a operaciones no autorizadas y `409` a conflictos de saldo o estado; los recursos no accesibles pueden responder `404` para no revelar su existencia. El cliente muestra el mensaje del servidor. El contrato completo está en [API-CONTRACT.md](API-CONTRACT.md).

Los pagos utilizan importes decimales y minutos enteros. Registrar el pago, acreditar las bolsas, auditar y crear sus notificaciones es atómico. Un UUID de solicitud protege ante reintentos y una referencia única normalizada por institución evita duplicados operativos. Anular requiere saldo disponible suficiente para retirar toda la acreditación, conserva el registro y nunca ejecuta un reembolso bancario.

La cola conserva los estados `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED` y el terminal `SUPPRESSED`; procesa fuera de la transacción de negocio, limita intentos y recupera bloqueos vencidos tras caídas. La entrega es al menos una vez: puede duplicarse si SMTP acepta y el proceso cae antes de confirmar el envío en la base. La falta de correo o responsable queda registrada como omisión. Más detalles y fuentes primarias en [CORREO.md](CORREO.md).

## Sesiones y permisos

La autenticación usa sesión del servidor, cookie de sesión `HttpOnly` y `SameSite=Strict`. El cliente obtiene el token CSRF mediante `GET /api/auth/csrf` y lo reenvía en `X-XSRF-TOKEN`; repite la obtención al cambiar el estado de autenticación. La cookie CSRF debe ser legible por Angular y no contiene una contraseña. La configuración `COOKIE_SECURE=true` exige HTTPS para la cookie de sesión.

Las contraseñas se almacenan como hashes BCrypt, nunca como texto recuperable. Las respuestas de usuarios no incluyen el hash. Spring restringe las consultas al ámbito institucional del usuario y elimina información interna de las respuestas de ese rol. Un usuario desactivado o vinculado a una institución dada de baja no conserva autorización por tener una sesión previa.

`BILLING` puede consultar instituciones sin notas administrativas, administrar pólizas, pagos, comprobantes, recibos y reportes de cobranza, gestionar cotizaciones/renovaciones, decidir solicitudes de pago y cambiar su contraseña. No puede operar casos, conocimiento, encuestas, usuarios, auditoría, configuración de correo, portal financiero o respaldos ni dar de baja instituciones o anular pagos. Su campana conserva avisos propios de saldo; los avisos de vencimiento se consultan en Renovaciones. La asignación como responsable de soporte admite un `ADMIN` o `TECH` activo.

`TECH` opera casos, atenciones, estados, cronómetros, agenda, plantillas e incidencias. Puede tomar un caso sin responsable y crear casos propios como responsable; no reasigna un caso a otra persona. Consulta instituciones sin notas administrativas y artículos publicados, y puede vincularlos a casos. No administra usuarios, configuración de correo, respaldos, finanzas ni la publicación editorial de conocimiento.

El portal financiero `USER` tiene autorización adicional por institución, desactivada por defecto. `ClientFinanceAccessService` comprueba cuenta e institución activas y la habilitación en cada operación; las proyecciones de consulta omiten información interna. Las rutas `/api/client-finance/**` no conceden acceso a `/api/payments`, `/api/policies`, cotizaciones o reportes administrativos. Habilitar también autoriza comprobantes históricos de la institución; revocar no puede recuperar archivos ya descargados. El PDF cliente omite notas internas y motivo/responsable interno de anulación, manteniendo visible el estado ANULADO. Consulte [Portal financiero](PORTAL-FINANCIERO.md) y [Recibos](RECIBOS-PAGO.md).

Las sesiones residen en memoria de Spring: un reinicio cierra las sesiones. La arquitectura entregada es de una instancia y no incluye balanceo, sesiones distribuidas, MFA, SSO ni recuperación de contraseña por correo. Los administradores pueden restablecer contraseñas desde Usuarios.

## Operación y capacidad

La persistencia de negocio usa un volumen Docker de PostgreSQL. Cuando se utiliza Postfix, conserva una segunda cola en `smtp_queue`; Gmail directo omite esa etapa. SENT en PostgreSQL significa aceptación del primer SMTP, no finalización de la entrega remota. Una instalación nueva tiene el correo desactivado y Gmail directo preseleccionado; el estado administrativo de pausa se conserva aparte en la base. Consulte [SMTP-INTERNO.md](SMTP-INTERNO.md) y [CORREO.md](CORREO.md). Los archivos compilados de Angular y el JAR se reconstruyen a partir del código. La inicialización administrativa utiliza una contraseña de `.env` sólo cuando no existe un administrador activo; no sustituye la administración regular de contraseñas.

El objetivo es un helpdesk institucional de volumen moderado. Varios listados operativos se cargan en el cliente y se filtran en la interfaz; conocimiento ya utiliza búsqueda y paginación del servidor, y reportes aplican filtros/límites del servidor. No se ha realizado una prueba de carga productiva ni se declara un número máximo validado de usuarios simultáneos. Antes de un crecimiento grande, conviene ampliar la paginación a los listados restantes, ajustar índices según consultas reales y añadir monitoreo. La auditoría de interfaz presenta los últimos 500 eventos.

Las publicaciones de puertos de la configuración inicial usan localhost; el worker de respaldos no publica puertos. Para acceso externo necesita terminación HTTPS, políticas de red, procedimiento de respaldo y administración de cuentas acordes con su instalación. Consulte [OPERACION.md](OPERACION.md).

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


V10 incorpora `payment_attachments`: archivos BYTEA asociados a pagos, con UUID de carga, digest y metadatos. Sus rutas originales y la carga usan permisos financieros (BILLING/ADMIN), independientes de los adjuntos de casos. Desde V12, rutas separadas permiten al cliente habilitado descargar sólo la evidencia de su institución. Las cuotas se verifican bajo bloqueo del pago, respetando el orden institución → póliza → pago. La carga no acredita ni retira minutos, y el respaldo PostgreSQL incluye los bytes. El historial financiero agrega ATTACH_PAYMENT_PROOF; los avisos SMTP existentes no transportan estos archivos.


## Extensiones de 1.6 y fronteras de autorización

| Migración | Persistencia y regla principal |
|---|---|
| V11 | `support_quotes`, `quote_history`, `policy_renewals` y configuración/avisos de renovación. Términos congelados al emitir; decisión con evidencia; conversión idempotente a póliza sin pagos ni crédito. |
| V12 | `client_finance_access`, `payment_requests` y `payment_request_attachments`. Portal institucional desactivado por defecto; solicitud pendiente sin efectos monetarios; aprobación atómica y archivos BYTEA. |
| V13 | `payment_receipts`: folio único y snapshot JSONB conservado en la primera descarga autorizada. Generación PDF sin otro pago ni incremento del saldo. |
| V14 | Artículos, revisiones editoriales, vínculos a casos y eventos. Borrador/publicado/archivado, revisión esperada para evitar sobrescrituras y texto plano escapado. |
| V15 | `satisfaction_surveys`: una respuesta institucional por caso, puntuación 1–5, autor/asignación conservados al responder; reabrir no genera otra encuesta. |
| V16 | Configuración, estado del worker, ejecuciones e intentos de respaldo. Programación diaria desactivada hasta confirmar destino y activarla explícitamente. |

### Cotización, validación y acreditación

Las cotizaciones y solicitudes conservan términos y evidencia. La aceptación de una oferta es un registro hecho por el personal en nombre del cliente, no una firma digital. Convertir utiliza los bloqueos contables y restricciones únicas para crear una sola póliza. Las claves foráneas compuestas mantienen la misma institución en cotización, antecedente, sucesora y vínculo de renovación. Cancelar una sucesora conserva la cadena; su reemplazo continúa desde la última cancelada. Los avisos de vencimiento son internos y deduplicados por póliza/fecha, independientes de SMTP. Consulte [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md).

Aprobar una solicitud bloquea institución, póliza opcional y solicitud, y reutiliza `HelpdeskService.createPayment` con un UUID interno estable. Pago, acreditación, decisión y copia de comprobantes se confirman o revierten juntos. Se comprueban nuevamente límites contractuales y evidencia; el cliente no decide ni modifica los importes al aprobar. Las solicitudes rechazadas no tocan saldo. Los pagos directos de 1.5 siguen disponibles. Consulte [Solicitudes de pago](SOLICITUDES-PAGO.md).

Un recibo conserva datos de emisión, pero cada descarga obtiene el estado vigente de anulación. El folio permanece estable; el PDF se genera desde PostgreSQL sin guardar un archivo adicional. Las versiones cliente y administrativa respetan sus permisos. Un PDF descargado anteriormente no puede actualizarse fuera del sistema. El recibo no es CFDI ni factura y no implica comunicación bancaria.

### Conocimiento y satisfacción

Conocimiento autoriza ADMIN/TECH/USER; Cobranza queda excluida. Las consultas TECH y USER filtran artículos publicados antes de mapearlos, incluso en vínculos a casos. IDs adivinados de borradores o archivados no entregan contenido. La edición y el historial editorial quedan reservados a ADMIN. Los vínculos apuntan a la publicación actual y no descuentan minutos. La interfaz interpola texto, sin `innerHTML` ni recursos externos. Consulte [Base de conocimientos](BASE-CONOCIMIENTOS.md).

La encuesta exige un caso propio cerrado y una cuenta/institución activas. La unicidad por caso y UUID protege respuestas simultáneas y reintentos. Guarda nombres y asignación al responder; una reasignación posterior no reatribuye la evaluación. Los informes ADMIN calculan medias sobre todas las respuestas del resultado y exportan CSV con protección de fórmulas. No requiere habilitar finanzas ni genera consumo o correo. Consulte [Encuestas de satisfacción](ENCUESTAS-SATISFACCION.md).

### Respaldos y restauración

La configuración del worker persiste en PostgreSQL. En una instalación nueva o al incorporar V16, `enabled=false`; iniciar Compose no activa la programación. ADMIN confirma el destino, configura hora/zona/retención y activa expresamente el calendario. El worker publica archivos finalizados mediante escritura temporal y cambio de nombre, conserva SHA-256 y evita ejecuciones simultáneas. Desactivar cancela programados pendientes, conserva historia/copias y deja terminar una ejecución en curso; reactivar no recupera los horarios de la pausa.

La retención se aplica sólo después de un respaldo programado exitoso, con calendario activo, a copias programadas propias y verificadas; respeta edad y mínimo de copias. Las copias manuales y archivos ajenos no se limpian. Los fallos se muestran a ADMIN en la plataforma sin depender de SMTP. No se devuelve la contraseña de PostgreSQL ni se monta el socket Docker en el worker.

El dump incluye datos y adjuntos BYTEA, recibos JSONB y configuración de módulos. No incluye `.env`, certificados, imágenes Docker o cola de Postfix; requieren conservación aparte. Se mantienen `respaldar.sh`/`.bat` y `restaurar.sh`/`.bat`; la copia manual desde la plataforma también funciona con calendario desactivado si el destino está confirmado y el worker disponible. La programación corresponde al despliegue Compose; el inicio nativo de desarrollo no levanta ese worker.

Una comprobación de hash y catálogo no sustituye restaurar en una base aislada. El procedimiento documentado no utiliza el volumen productivo. Consulte [Respaldos automáticos](RESPALDOS-AUTOMATICOS.md), [Respaldo y restauración manual](RESPALDO-RESTAURACION.md) y [Validación](VALIDACION.md); describir la arquitectura no afirma una ejecución de Docker o restauración que no conste allí.


## Extensiones de 1.7

V17–V24 son migraciones aditivas; V1–V16 permanecen intactas. TECH es un rol operativo sin poderes administrativos o financieros. Las vistas pertenecen a una cuenta; los filtros se validan como datos y nunca como SQL. Las plantillas sólo insertan un borrador editable. Las incidencias tienen una proyección pública explícita, separada de vínculos e historiales privados.

El catálogo conserva versiones; las cotizaciones referencian una versión inmutable. Las parcialidades asignan pagos existentes dentro de sus importes y no generan dinero o minutos. La conciliación importa con hashes y exige confirmación de una relación uno a uno con un pago existente. Los estados de cuenta usan apertura temporal y versiones públicas de filas desde V24; cada emisión conserva una instantánea, sin reconstruir historia previa que la aplicación no registró.

Los avisos de SLA se publican en la campana; los recordatorios de parcialidades se consultan en su propia pantalla. Ambos mecanismos son internos e independientes de SMTP. Ninguna extensión modifica la pausa de correo ni activa el calendario de respaldos.

## Flujo de casos en 1.8.3

V26 amplía la restricción de estado y su longitud a 20 caracteres. Conserva `OPEN` como identificador de **Recibido**, junto con `IN_PROGRESS` (**En atención**), `WAITING_CLIENT` (**Requiere respuesta del cliente**), `RESOLVED` (**Resuelto**) y `CLOSED` (**Cerrado**). No reclasifica retrospectivamente los casos `OPEN`; mantiene IDs, entradas, responsables y saldos. Los filtros guardados `OPEN` se convierten al grupo `active` para conservar su intención de mostrar todo el trabajo pendiente.

Los tres primeros estados permanecen activos. Los cambios manuales son de ADMIN/TECH, requieren motivo y registran historial, auditoría y avisos sin consumo. Tomar un caso Recibido lo pone En atención; una respuesta pública del cliente hace lo mismo desde Requiere respuesta del cliente. La reapertura desde Resuelto/Cerrado exige institución activa y saldo positivo en el canal principal.

Requiere respuesta del cliente pausa cronómetros en ejecución conservando el tiempo medido, pero **no pausa ni reinicia el SLA**. Resuelto y Cerrado detienen el SLA de resolución y bloquean nuevas atenciones hasta reabrir; cerrar después de resolver conserva la fecha de resolución. Reabrir inicia una nueva ventana de resolución sin borrar la primera respuesta registrada. Los cambios de estado nunca confirman ni cobran un cronómetro automáticamente. Consulte [VERSION-1.8.3.md](VERSION-1.8.3.md) para transiciones y validación.


## Ampliación V27: subcuentas de soporte por licencia

`license_support_defaults` conserva valores por canal, configuración explícita y revisión para detectar ediciones concurrentes. `support_licenses` conserva titular institucional, número único sin distinción de mayúsculas/espacios exteriores, dotación inicial y contadores por canal. `license_support_history` registra dotación, recarga manual, pago, consumo y sus anulaciones.

Los vínculos opcionales `support_cases.license_id` y `support_policies.license_id` tienen claves foráneas compuestas con `institution_id`: no pueden referir a licencias de otra institución. Los vínculos son inmutables en la edición normal. La licencia de un pago se obtiene de su póliza, incluidos los pagos aprobados desde solicitudes y las renovaciones.

Se preserva `institutions` como total agregado para reportes existentes. El saldo disponible sin licencia es el total restante menos la suma de saldos restantes de licencias. El bloqueo de la institución serializa alta de licencia, asignación, consumo, pago y anulación; no se modifica el orden previo institución → póliza/caso → pago/entrada. Los ajustes institucionales no pueden retirar saldo reservado a licencias. El alta toma además un bloqueo compartido de la configuración para conservar la revisión mostrada y acreditarla una sola vez.

V27 no altera filas históricas: no infiere titulares, claves ni distribución de saldos. Una actualización requiere respaldo y ensayo; un backend anterior a V27 no debe operar sobre esta base porque no conoce los saldos reservados. Véase [Licencias y horas](LICENCIAS-HORAS.md).
