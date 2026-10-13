# Validación de SIABUC Helpdesk

## Versión 1.5.0 · 13 de octubre de 2026

- **104 pruebas Java de integración y 5 unitarias aprobadas**, sin fallos, errores ni omisiones en la ejecución final completa. Java 17 / Maven 3.9.9, perfil `postgres-it`; JAR `siabuc-helpdesk-1.5.0.jar` generado.
- Cobertura financiera: permisos ADMIN/BILLING/USER, pólizas e historial, parcialidades MXN, acreditación por canal, límites contractuales, idempotencia, anulación y protección de horas consumidas. Se comprueba el rollback conjunto de pago, saldo, historial y outbox mediante un fallo controlado de prueba después de generar el aviso.
- Comprobantes: PDF/PNG/JPEG, firmas y cuotas, descarga autenticada, aislamiento de permisos, idempotencia, auditoría e historial sin modificar dinero ni minutos. Los reportes validan filtros, totales por moneda, datos históricos, XLSX real y PDF multipágina.
- Spring/JDBC/sesiones/CSRF reales sobre PGlite efímero con SQL exacto V1–V10 y una conexión. Flyway desactivado únicamente en el ensayo. Esto **no acredita PostgreSQL 17 nativo, ejecución real de Flyway ni contención entre varias conexiones**; se conserva abajo la limitación del entorno identificada en 1.4.
- `tests/migrations/upgrade-v15.mjs`: **454 comprobaciones aprobadas** sobre SQL V1–V10. Conserva datos de 16 tablas anteriores a V9 y 18 anteriores a V10, pagos históricos MXN/USD/CARD, anulaciones, saldos, secuencias y evidencia binaria; comprueba restricciones y relaciones nuevas.
- **61 escenarios Chromium con API simulada aprobados:** 18 de cobranza, 16 de funciones 1.4, 17 de historial/toma de casos y 10 de pausa de correo. Capturas de escritorio/móvil revisadas, incluido formulario de pago a 390 px y comprobantes.
- **13 recorridos de navegador contra Angular → Spring → base de datos reales aprobados, sin interceptar la API y sin errores JavaScript.** Cubren póliza sin crédito inicial, pagos/acreditación, idempotencia, comprobante PDF con descarga exacta y rechazo al cliente, reportes XLSX/PDF, saldo utilizable por el cliente, caso/adjunto/perfil técnico, cronómetro persistente con cargo único, agenda y campana/saldo bajo con correo pausado. La base sigue siendo PGlite de pruebas, no PostgreSQL 17 nativo.
- **48 pruebas Python y 6 pruebas de lógica del cliente aprobadas.** Angular producción compilado: 520.14 kB iniciales, transferencia estimada de 117.13 kB. Sin cambios de dependencias.
- SMTP JavaMail probado sólo en loopback; sin correos externos. Docker, Windows, Compose completo y respaldo/restauración Docker reales **no ejecutados**. Las simulaciones de argumentos Windows no acreditan ejecución nativa de `.bat`.
- Fuentes empaquetadas con manifiesto SHA-256 y verificación CRC, scripts `.sh` ejecutables y `.bat` CRLF. El paquete excluye `.env`, datos, dependencias descargadas y artefactos de QA.

Las instrucciones reproducibles están en [pruebas de navegador](../tests/e2e/README.md), la cabecera de `tests/migrations/upgrade-v15.mjs` y el procedimiento PostgreSQL del final de este documento. Consulte también [actualización 1.5](VERSION-1.5.md).

---

## Versión 1.4.0 · 13 de octubre de 2026

- **81 pruebas Java de integración distintas aprobadas y 5 unitarias de calendario**, sin fallos finales ni omisiones. Ejecución completa: 79 IT + 5 unitarias; después de añadir dos escenarios de concurrencia se ejecutaron las 9 IT de cronómetro/agenda y las 5 unitarias. Java 17 / Maven 3.9.9; JAR 1.4.0 generado.
- Se cubren permisos por institución/rol, adjuntos internos, tipos y cuotas de archivos, idempotencia, calendario laboral/feriados/DST, snapshots SLA, avisos con correo pausado, ciclos de saldo bajo, cronómetro y cargo único, propuesta/confirmación/reprogramación y reservas superpuestas. Las exportaciones se abrieron con POI/PDFBox para comprobar XLSX real, texto no ejecutable como fórmula, acentos y PDF multipágina.
- Spring, JDBC, sesiones y CSRF reales sobre PGlite efímero con SQL exacto V1–V8. Flyway desactivado sólo en el ensayo; pool de una conexión. Las solicitudes HTTP concurrentes se serializan en ese pool: **no demuestra contención de PostgreSQL 17 con varias conexiones**.
- Se intentó preparar PostgreSQL 17.11 nativo con binarios oficiales verificados; el entorno sólo permite UID 0 y bloquea la creación del usuario/namespace no privilegiado requerido por PostgreSQL. No se eludió esa protección. Arranque nativo/Flyway real y contención con varias conexiones quedan pendientes.
- `tests/migrations/upgrade-v14.mjs` pasó con fixtures anteriores a V5: conservación exacta de ocho tablas existentes, saldos, pagos, hashes, historial, auditoría, cola SMTP y pausa; reconstrucción de cinco casos abiertos/cerrados/reabiertos/desconocidos. Usa SQL exacto V1–V8 en otra base PGlite de memoria.
- **43 escenarios Chromium con API simulada aprobados:** 16 de funciones 1.4, 17 de historial y toma de casos y 10 de pausa de correo. Incluyen perfil atómico de creación, filtros, adjuntos de caso/comentario, confirmación del cronómetro, agenda cliente/admin, campana, exportaciones, configuración y pantalla móvil de 390 px sin desbordamiento horizontal. Estos mocks no sustituyen las comprobaciones reales del servidor.
- **48 pruebas Python** de operación, configuración SMTP, respaldo/restauración simulados y adaptaciones Windows aprobadas; **6 pruebas de lógica del cliente** aprobadas. Angular producción compilado. Capturas de escritorio/móvil revisadas; se corrigieron el ancho móvil y el icono de campana.
- SMTP JavaMail validado con sockets de loopback, sin correo externo. Docker/Windows, arranque Compose y respaldo/restauración Docker reales no ejecutados. Las pruebas de Windows verifican lógica y argumentos; no acreditan ejecución nativa de `.bat` en Windows.
- Código empaquetado con manifiesto SHA-256 y verificación CRC; incluye migraciones, pruebas y documentación, conserva `.sh` ejecutables y `.bat` CRLF. No incluye `.env`, datos, dependencias descargadas ni artefactos de QA.

Para repetir las suites del servidor use una base **exclusiva de pruebas** con PostgreSQL y `mvn -Ppostgres-it verify`, según las instrucciones siguientes. Para Chromium consulte [Pruebas de navegador](../tests/e2e/README.md). La cabecera de `tests/migrations/upgrade-v14.mjs` explica su dependencia de PGlite y la variable `SIABUC_PGLITE_MODULE`.

---

# Registro histórico: versión 1.3.3

## Detalle de historiales y toma de casos · 13 de octubre de 2026

- **48 pruebas Java de integración aprobadas**, sin fallos, errores ni omisiones: 14 de Helpdesk, 4 de configuración de correo, 3 de detalle de notificaciones, 7 de outbox, 5 de toma de casos, 8 de SMTP y 7 de pagos. Java 17 / Maven 3.9.9, perfil `postgres-it`; JAR 1.3.3 generado.
- Detalle de notificaciones: sólo ADMIN, errores 401/403/404, cuerpo histórico exacto y lectura sin modificar ninguno de los seis estados de entrega ni generar avisos. La lista conserva su respuesta compacta.
- Toma de casos: creación por cliente para su institución, asignación exclusiva administrativa sin consumo de minutos, historial/auditoría/avisos, idempotencia, rechazo de otra asignación, caso cerrado o institución inactiva, saldo y responsable predeterminado. Dos peticiones simultáneas obtuvieron un único éxito y un conflicto, con un solo evento de toma.
- Spring, JDBC, sesiones y CSRF reales sobre PGlite efímero y una conexión; SQL V1–V4 exacto. Flyway se desactivó sólo para el ensayo. La prueba concurrente queda serializada por ese pool: no acredita contención nativa de PostgreSQL 17 con varias conexiones. SMTP JavaMail sobre socket de loopback; sin correo externo.
- **27 pruebas Chromium aprobadas** con API simulada: 17 de `history-detail.ui.mjs` y 10 de regresión `mail-control.ui.mjs`. Incluyen clic en fila/tarjeta, contenido completo multilínea, HTML escapado, teclado/foco, filtros y posición al volver, privacidad visible, notas anuladas, errores, acciones separadas, cliente que abre caso, toma administrativa, conflicto 409 y respuesta tardía tras cambiar de pantalla. No sustituyen los permisos reales probados en backend.
- Angular producción compilado: **368.87 kB** antes de compresión. Capturas de prueba de escritorio y móvil de 390 px revisadas; sin cambios de dependencias.
- Enlaces locales revisados y ZIP 1.3.3 comprobado mediante CRC/manifiesto SHA-256, con `.sh` ejecutables y `.bat` CRLF. No incluye secretos, datos de clientes ni artefactos de pruebas locales.
- Docker Desktop/Windows, migración Flyway sobre PostgreSQL 17 y respaldo/restauración reales no se ejecutaron en este entorno. Se conserva por separado la evidencia histórica siguiente.

## Contenido de avisos de casos · versión 1.3.2 · 13 de octubre de 2026

- **40 pruebas Java de integración aprobadas**, sin fallos, errores ni omisiones: 14 de Helpdesk, 4 de configuración, 7 de outbox, 8 de SMTP y 7 de pagos. Java 17 y Maven 3.9.9, `-Ppostgres-it verify`; JAR 1.3.2 generado.
- Se comprobó responsable asignado distinto del autor del evento, institución cliente, descripción vigente separada del detalle de actualización, estado únicamente Abierto/Cerrado al cerrar y reabrir y leyenda de consulta en Helpdesk. Los avisos anteriores conservan su cuerpo original.
- También se verificó ausencia de asignación, identificación del responsable desactivado sin generar un envío nuevo para su cuenta, privacidad de notas internas y continuidad de la pausa sin reenvío de avisos anteriores.
- Spring, JDBC, sesiones y CSRF reales con PGlite temporal, una conexión y SQL exacto V1–V4. Flyway se desactivó sólo durante el ensayo. SMTP probado con JavaMail y socket real de loopback, sin correos externos. Esto no prueba PostgreSQL 17 nativo ni entrega a proveedores externos.
- Angular producción 1.3.2 compilado: **353.36 kB** antes de compresión. No se modificó la interfaz ni se repitieron las pruebas Chromium de 1.3.1; sus resultados se conservan abajo.
- Dependencias sin cambios y enlaces locales de los documentos modificados comprobados. El ZIP 1.3.2 se verificó mediante CRC y manifiesto SHA-256, con scripts `.sh` ejecutables y `.bat` CRLF.
- Docker/Windows y respaldo/restauración reales no se ejecutaron en este entorno. Las pruebas Python y de navegador de 1.3.1 siguientes son evidencia histórica, no nuevas ejecuciones de 1.3.2.

## Desactivación sin reenvío posterior · versión 1.3.1 · 13 de octubre de 2026

- **38 pruebas Java de integración aprobadas**, sin fallos ni omisiones: 14 de Helpdesk, 4 de configuración de correo, 5 de outbox, 8 de SMTP y 7 de pagos. Maven `-Ppostgres-it verify` genera el JAR 1.3.1.
- Spring, JDBC, sesiones y CSRF reales con PGlite temporal, una conexión y SQL exacto V1–V4. Flyway se desactivó sólo en ese ensayo. No equivale a validación de migración Flyway sobre PostgreSQL 17 ni a contención entre varias conexiones.
- SMTP se verificó con JavaMail y socket local real: desactivar/reanudar mientras un envío espera respuesta DATA, rechazo 451 y ausencia de reintento posterior. No se enviaron correos externos.
- La política comprobada convierte los avisos anteriores recuperables y los generados durante pausa en SUPPRESSED; reactivar sólo envía eventos nuevos. Incluye permisos, JSON estricto, auditoría idempotente, persistencia, conservación de negocio/historial, pruebas manuales bloqueadas y reclamaciones antiguas fallidas o vencidas sin reaparición.
- **10 pruebas Chromium de interfaz aprobadas**, con API simulada (`tests/e2e/mail-control.ui.mjs`): confirmación, cancelación, reactivación, errores, permisos visibles, límite del servidor, filtros, bloqueo de acciones y pantalla móvil. No sustituyen la prueba de backend.
- Angular producción compilado: **353.36 kB** antes de compresión.
- **35 pruebas Python de operación y 13 de SMTP aprobadas**. Docker Desktop/Windows y la restauración real siguen fuera de este ensayo.
- Paquete de fuentes 1.3.1 comprobado mediante CRC y manifiesto SHA-256; no incluye credenciales, respaldos, datos ni binarios compilados.

## Evidencia histórica: versión 1.3.0


## Entrega Windows/Linux · 13 de octubre de 2026

- 35 pruebas Python de operación aprobadas, incluidas cinco nuevas para rutas, respaldo binario y ramas Windows simuladas; comando `python3 -m unittest discover -s tests/ops`.
- 13 pruebas Python de configuración Postfix aprobadas; comando `python3 -m unittest discover -s tests/smtp`.
- Compilación Maven `package` satisfactoria con Java 17: `backend/target/siabuc-helpdesk-1.3.0.jar`. No se activó el perfil postgres-it ni se repitieron las pruebas Java de integración de versiones anteriores. El primer intento offline no pudo resolver el POM padre; se completó usando la conexión configurada para Maven.
- Angular 1.3.0 compiló para producción mediante `npm run build`: 349.06 kB antes de compresión. No se repitieron las pruebas de navegador.
- El ZIP se comprueba por CRC y SHA-256 de cada entrada del manifiesto, excluyendo datos, contraseñas, cachés y binarios de compilación.
- No se ejecutaron CMD, Docker Desktop, Compose completo ni una restauración real en Windows. Las pruebas de ramas Windows usan simulaciones en Linux; no equivalen a validación real en Windows.
- El usuario confirmó SMTP local funcionando en su instalación y Gmail fallido. Se conserva esa distinción; no se atribuye ese resultado a una prueba propia ni se da por resuelta la causa del fallo de Gmail.

## Evidencia histórica: versión 1.2.0


Fecha: 13 de octubre de 2026 UTC. Esta entrega añade Postfix saliente en Docker y remitente con IP local configurable. No cambia el esquema V1/V2/V3 ni las operaciones de casos, saldos o pagos.

## Corrección de arranque SMTP: manuales excluidos en Debian slim

El 13 de octubre de 2026 el usuario comunicó un arranque real con PostgreSQL saludable y SMTP en reinicio. Los registros mostraron `chown: cannot access '/usr/share/man/man1/mailq.1.gz'` y salida 1 durante la preparación de Postfix. Este reporte identifica un fallo de la imagen original; no constituye una validación completa del conjunto Compose.

La corrección añade una excepción `path-include=/usr/share/man/*` en `zz-postfix-manpages` antes de instalar paquetes. La [lista de exclusiones de Debian slim](https://github.com/debuerreotype/debuerreotype/blob/master/scripts/.slimify-excludes) incluye los manuales; [dpkg](https://manpages.debian.org/trixie/dpkg/dpkg.1.en.html) aplica la última regla coincidente. La construcción comprueba el manual real `sendmail.1.gz` y los destinos de `mailq.1.gz` y `newaliases.1.gz`. El ajuste de permisos y la comprobación de salud de Postfix continúan siendo obligatorios.

Validación ejecutada para esta corrección:

- **Regresión aislada con dpkg 1.22.6:** se construyó un paquete mínimo, sin scripts, con un manual no vacío y enlaces relativos de `mailq` y `newaliases`. Se desempaquetó en dos raíces temporales, sin alterar los paquetes del sistema. Con la exclusión de manuales falta el destino; al añadir después la excepción, el manual existe y ambos enlaces resuelven. Las reglas se pasaron por argumentos equivalentes a la configuración de dpkg; esta prueba no ejecuta los scripts de instalación del paquete Postfix de Debian 13.
- **Sintaxis:** las tres instrucciones `RUN` del Dockerfile pasan `sh -n`; se comprobó que la excepción precede a la instalación y se revisaron los cambios y comandos de recuperación.
- **Pendiente:** construir y arrancar la imagen corregida con Docker, comprobar SMTP saludable y completar el inicio de Spring/Angular. Docker no está disponible en el entorno de preparación. No se enviaron mensajes externos ni se repitieron las pruebas históricas del servidor, cliente o respaldos.

El procedimiento de actualización conserva `.env`, PostgreSQL y la cola SMTP y está en [diagnóstico SMTP](SMTP-INTERNO.md#reinicio-por-mailq1gz-ausente).

## Comprobaciones anteriores de la versión 1.2.0

Revisión de texto posterior: se añadió al historial de atención un recordatorio para revisar la bandeja de entrada y el correo no deseado, visible únicamente para cuentas `USER`, en casos abiertos y cerrados. Se revisó la condición de la plantilla y se recompiló Angular correctamente (349.06 kB). Las pruebas de servidor y configuración de la tabla siguiente corresponden a la validación previa, sin cambios en ese código; no se repitieron para esta leyenda.

Revisión posterior de respaldo/restauración: ambos scripts admiten `--nombre-respaldo ARCHIVO` y conservan su argumento posicional. Se ejecutaron **37/37 pruebas Python**, incluidas 12 nuevas de argumentos, nombres con espacios, ubicación de archivos, permisos 600, SHA-256, rechazo de sobrescritura, limpieza de copias incompletas y condiciones previas a restaurar. También se comprobó la ayuda de ambos wrappers y tres rechazos de argumentos desde otra carpeta. Se corrigió el manejo de un archivo SHA-256 vacío o mal formado para rechazarlo con un diagnóstico claro antes de detener servicios. Las pruebas de respaldo/restauración simulan la llamada a Docker Compose y comprueban archivos y flujos binarios locales; **no ejecutan `pg_dump` ni `pg_restore` reales**. La restauración Docker/PostgreSQL real sigue pendiente. No cambió el código Spring ni Angular en esta revisión.

| Comprobación | Resultado | Alcance |
| --- | --- | --- |
| Spring 1.2.0 | Correcto | Empaquetado JAR con Java 17 y Maven 3.9.9. |
| Integración del servidor | **31/31** | 14 del helpdesk, 7 de pagos, 5 de cola y 5 de SMTP; sin fallos, errores ni omisiones. |
| Remitente con IPv4 literal | Correcto | JavaMail real envía `soporte@[192.168.1.50]` a un receptor SMTP de prueba en loopback; verifica `MAIL FROM`, cabecera MIME `From` y transición a SENT. |
| Configuración y operación Python | **25/25** | 12 del asistente/compatibilidad y 13 del generador Postfix. |
| Angular producción | Correcto | Angular 21.2.25; salida de 348.80 kB antes de compresión; textos de configuración SMTP actualizados. |
| Scripts y Compose | Correcto en análisis estático | Sintaxis Bash/Python y YAML; 4 servicios predeterminados, Mailpit sólo en perfil de pruebas, puerto SMTP limitado a localhost. |
| Consulta nativa de parámetros Postfix | Parcial | Postfix 3.8.6 reconoció 60 nombres de parámetros en cuatro configuraciones anteriores a añadir `smtp_helo_name`; ese parámetro se contrastó además con documentación oficial. No demuestra arranque ni validez operativa completa. |

Las 25 pruebas Python incluyen el remitente automático con IP, preservación de remitentes personalizados, `.env` anterior sin activar correo, conservación de proveedor existente, secretos ocultos y permisos 600, escritura atómica y archivo intacto ante error, límites de puertos/red privada, IP literal en EHLO, relay STARTTLS/SSL/NONE y rechazo de credenciales sin TLS o espacios finales que `postmap` recortaría.

La suite Java utiliza Spring, JDBC, sesiones y CSRF reales con PGlite mediante protocolo PostgreSQL y un pool de una conexión. El SQL exacto V1/V2/V3 se ejecutó antes de los tests; Flyway sólo se desactivó en ese entorno temporal. El nuevo ensayo de remitente con IP utiliza JavaMail y socket SMTP reales, **no un contenedor Postfix**. Todos los destinatarios son de pruebas locales; no se enviaron mensajes externos.

## Límites de la entrega 1.2

Docker no está instalado en el entorno de preparación: **no se ejecutó la construcción ni el arranque de la imagen Postfix de Debian 13**, el conjunto Compose ni PostgreSQL 17 nativo. La comprobación nativa parcial usó binarios extraídos de Postfix 3.8.6: las verificaciones completas quedaron bloqueadas por las restricciones de cuentas/interfaces del entorno. Tampoco se probó TLS contra un relay real, recepción institucional ni entrega a proveedores públicos. La aceptación de un remitente con IP local debe verificarse en el receptor que utilizará su institución.

Las pruebas de navegador 25/25 y de lógica cliente 6/6 que figuran abajo corresponden a la versión 1.1; en 1.2 se recompiló Angular para cambios de texto y versión, sin repetir ese recorrido. Permanecen pendientes las pruebas de contención sobre PostgreSQL con múltiples conexiones y el respaldo/restauración Docker reales.

Para repetir los tests de configuración sin Docker, desde la raíz:

```bash
python3 -m unittest discover -s tests -p 'test_*.py' -v
```

Para las pruebas Java sobre PostgreSQL nativo, utilice las indicaciones de base aislada al final de este documento. Para comprobar el contenedor, siga [SMTP-INTERNO.md](SMTP-INTERNO.md): arranque, healthcheck, registros y cola. No confunda aceptación SMTP con entrega final.

---

# Registro histórico: versión 1.1.0

Fecha: 13 de octubre de 2026 UTC. Se conservan abajo los resultados históricos de 1.0; esta sección corresponde al código ampliado con contactos, cobranza y correo.

| Comprobación | Resultado | Alcance |
| --- | --- | --- |
| Empaquetado Spring 1.1.0 | Correcto | Java 17.0.20, Maven 3.9.9; JAR ejecutable generado. |
| Integración del servidor | **30/30**, sin fallos, errores ni omisiones | 14 del helpdesk, 7 de pagos/migración, 5 de cola y 4 de SMTP local. |
| SMTP real local | **4/4** | JavaMailSender real contra servidor SMTP de prueba en loopback: destinatarios separados, MIME/cuerpo, privacidad, pagos, fallo/reintento/límite y recuperación de bloqueo. |
| SQL V1 → V2 → V3 | Correcto | SQL exacto sobre PGlite; conserva instituciones, cuentas, hashes, saldos, casos, entradas, auditoría y fechas previas. V1 mantiene identidad byte a byte con la entrega anterior. |
| Angular producción | Correcto | Angular 21.2.25; salida de 348.30 kB antes de compresión. |
| Lógica del cliente | **6/6** | Tiempo, contraseñas UTF-8, importes con dos decimales, totales separados por moneda y UUID. |
| Navegador | **25/25** | 13 del helpdesk y 12 de cobranza/correo sobre Chromium, Angular, Spring y base temporal reales. Sin excepciones JavaScript. |
| Asistente SMTP | Correcto | Copias temporales: entrada oculta, confirmación, escritura atómica, permisos 600, conservación de DB_PASSWORD y archivo intacto ante error. |
| Compatibilidad de configuración | Correcto | `.env` 1.0 válido con correo desactivado; valores SMTP literales con símbolos; rechazo de siete configuraciones SMTP y cinco formatos inválidos. |

Las pruebas de servidor utilizan Spring, autenticación, CSRF y JDBC reales. La base de ensayo es PGlite mediante protocolo PostgreSQL con una conexión; sólo allí se desactiva Flyway y se ejecuta manualmente el SQL exacto de sus tres migraciones. Las cuatro pruebas SMTP instancian el despachador productivo con la misma base y gestor de transacciones, apuntando exclusivamente al servidor de correo local. No se enviaron mensajes a destinatarios externos ni se usaron credenciales reales de proveedores.

## Casos nuevos cubiertos

- Cobranza accede a pagos y saldos, pero recibe 403 al intentar modificar instituciones o consultar casos, usuarios, auditoría y notificaciones. Los usuarios institucionales no acceden a pagos.
- Un pago incrementa las dos bolsas exactamente, conserva moneda/importe/fecha/referencia y evita doble acreditación por UUID o referencia repetida.
- Reintentos simultáneos de pago dejan una acreditación; la observación está limitada por el pool de una conexión. La anulación sólo retira minutos una vez y rechaza saldo insuficiente.
- Avisos públicos de apertura, edición, atención y cierre se encolan para institución y responsable; notas internas y su anulación sólo para el responsable. Si ambos correos coinciden, el aviso privado queda omitido.
- La falta de dirección queda visible; su corrección permite reintento para la entidad original. La prueba administrativa sólo admite el correo propio, nunca destinatarios libres, y la API no expone secretos SMTP.
- SMTP desactivado mantiene pendientes. El servidor local confirma entrega de contenido descriptivo, recepción por destinatario separado, límites de intentos, ausencia de reenvío de SENT y recuperación tras vencer un bloqueo.
- Una operación rechazada no deja avisos ni acreditación parcial. Migrar de V1 conserva datos y habilita la nueva restricción de cuentas BILLING sin institución.

En navegador se verificaron formularios de contactos y responsables, alta de Cobranza, pago de 65/30 minutos, consulta y anulación, destinatarios del cierre, privacidad, prueba propia y reintento de un aviso omitido. Las vistas de 320, 390 y 768 píxeles del flujo previo y la nueva vista de pagos a 390 píxeles no presentaron desbordamiento horizontal del documento; las tablas conservan desplazamiento interno.

## Límites de esta validación

No se ejecutó Docker Compose, PostgreSQL 17 nativo con varias conexiones, Flyway sobre ese servidor nativo ni respaldo/restauración real. Tampoco se verificaron credenciales, TLS o entrega final de Gmail, Microsoft 365 ni de un servidor institucional externo. El ensayo SMTP usa conexión local sin TLS; la configuración productiva incluye STARTTLS/SSL, comprobación de nombre de certificado y timeouts finitos, pero debe comprobarse contra el proveedor elegido.

En PostgreSQL nativo deben repetirse las pruebas de contención, acreditación/anulación simultánea y reclamación de cola con más de un trabajador. La prueba PGlite no demuestra esos bloqueos bajo varias conexiones. Los comandos para repetir integración están al final de este documento; para correo real/local consulte [CORREO.md](CORREO.md), para actualizar [MIGRACION-1.1.md](MIGRACION-1.1.md), y para navegador [tests/e2e/README.md](../tests/e2e/README.md).

---

# Registro histórico: versión 1.0.0

## Actualización: contraseña de PostgreSQL

Se añadió `./configurar.sh --password-bd` para solicitar y confirmar `DB_PASSWORD` con entrada oculta. Se ejecutaron seis escenarios en una copia temporal mediante pseudoterminal: configuración válida, confirmación distinta, contraseña corta, símbolo no admitido, ejecución sin terminal interactiva y sustitución de una contraseña de plantilla. Todos produjeron el resultado esperado. Se comprobó que la contraseña no aparece en la salida, que se preservan las otras variables y comentarios y que el archivo mantiene permisos `600`. La escritura es atómica.

Esta opción configura `.env`; no altera el usuario de una base existente. El procedimiento con `\password` se documenta en [OPERACION.md](OPERACION.md). No se ejecutó una rotación real contra PostgreSQL nativo. El servidor y el cliente no se modificaron en esta actualización, por lo que los resultados siguientes corresponden a la validación funcional anterior.

Fecha: 13 de octubre de 2026 UTC (12 de octubre por la tarde/noche en Ciudad de México).

Este documento distingue las comprobaciones ejecutadas de las que requieren un servidor Docker/PostgreSQL nativo. No declara una instalación productiva, una prueba de carga ni un número máximo de usuarios concurrentes.

## Resultados ejecutados

| Comprobación | Resultado | Entorno y alcance |
| --- | --- | --- |
| Compilación y empaquetado Spring | Correcto | OpenJDK 17.0.20, Maven 3.9.9, Spring Boot 3.5.16. JAR ejecutable generado. |
| Pruebas de integración del servidor | **14/14**, sin errores ni omisiones | Spring real, MockMvc, autenticación y CSRF reales; JDBC sobre PGlite mediante protocolo PostgreSQL, pool de una conexión. |
| Esquema SQL V1 | Aplicación correcta | Se ejecutó sin modificar el SQL de la migración sobre PGlite antes de iniciar el servidor de prueba. |
| Compilación de producción Angular | Correcto | Angular 21.2.25, Node.js 24.19.0. Salida `frontend/dist/helpdesk/browser/`. |
| Pruebas de conversión y contraseñas | **3/3** | `npm test`: horas/minutos, rangos, negativos, decimales y longitud UTF-8 para BCrypt. |
| Flujo integrado en navegador | **13/13**, sin excepciones JavaScript | Chromium automatizado mediante Playwright, Angular y servidor Spring reales; base temporal PGlite. |
| Diseño adaptable | Correcto en el flujo probado | Sin desbordamiento horizontal a 320, 390 y 768 píxeles; también se comprobó la vista de escritorio. |
| Scripts y configuración | Correcto | Sintaxis de los siete scripts Bash y Python, lectura YAML de tres servicios, generación/preservación de secretos y permisos `600`. |
| Configuraciones inválidas | Rechazadas | Puertos duplicados/fuera de rango, contenido de shell, contraseñas de plantilla, variables desconocidas/duplicadas y administrador inválido. |
| Operación desde otra carpeta | Correcto | Wrappers ejecutados desde `/tmp` sobre una copia temporal; resuelven la carpeta del proyecto. |
| Diagnósticos de operación | Correcto | Ausencia de Docker informada sin traceback; restauración sin bandera explícita rechazada. |

Las comprobaciones de scripts utilizaron copias temporales; no se incluye una `.env` con credenciales en la entrega. Se retiró la fuente externa de la interfaz y se repitieron la compilación de producción y las tres pruebas del cliente sobre ese cambio final. El cambio fue exclusivamente de tipografía y no modificó los flujos E2E ya comprobados.

## Qué cubren las pruebas del servidor

`backend/src/test/java/mx/siabuc/helpdesk/HelpdeskIntegrationIT.java` comprueba:

- Inicio de sesión, protección CSRF y rechazo de operaciones administrativas desde cuentas institucionales.
- Aislamiento entre instituciones; entradas internas y notas administrativas no expuestas a sus usuarios.
- Bolsas independientes por correo y remoto, actualización exacta y rechazo sin consumo parcial al exceder el saldo.
- Dos solicitudes simultáneas que intentan consumir el último saldo desde casos distintos.
- Anulación con devolución única; rechazo de una anulación con un caso y una entrada que no corresponden.
- Casos cerrados, reapertura, agotamiento y recarga de saldo.
- Rechazo de presupuestos menores que el tiempo utilizado.
- Baja de institución o desactivación de cuenta aplicada a sesiones existentes.
- Cambio de contraseña: conserva la sesión titular y revoca otras sesiones; acceso posterior con la contraseña nueva.
- Edición de casos y auditoría; protección del último administrador.

En esta ejecución, el pool de una conexión serializó el acceso a la base. El test de solicitudes simultáneas confirmó el resultado de negocio bajo esa condición; **no demuestra la contención de bloqueos entre varias conexiones de PostgreSQL nativo**. La implementación usa transacciones y `SELECT ... FOR UPDATE` con orden institución → caso → entrada y fue revisada estáticamente.

## Qué cubren las pruebas de navegador

`tests/e2e/helpdesk.e2e.mjs` prueba el flujo mediante la interfaz y verifica las respuestas reales de la API: sesión persistente, alta/edición institucional, conversión de 1 h 5 min en 65 minutos, alta de usuario, apertura de caso, notas privadas, consumos por canal, anulación, edición, cierre/reapertura, agotamiento, rechazo de sobregiro, permisos del portal, baja lógica y tamaños de pantalla. Los datos se crean en una instalación de pruebas desechable. Consulte su [README](../tests/e2e/README.md).

## Pendiente de comprobar en su instalación

- Construcción y arranque de las tres imágenes mediante Docker Compose; Docker no estaba disponible en el entorno de creación.
- Migración automática Flyway sobre PostgreSQL 17 nativo. El SQL exacto sí se aplicó en la prueba; Flyway se desactivó **sólo durante la prueba PGlite** porque necesita más de una conexión. En el proyecto entregado, Flyway está habilitado y el pool tiene 12 conexiones.
- Pruebas de integración contra PostgreSQL 17 con múltiples conexiones, incluidos los bloqueos bajo contención real.
- Respaldo y restauración reales con `pg_dump`/`pg_restore`; se verificaron los scripts, validaciones y parámetros, pero no se ejecutaron estas operaciones sin Docker.
- Configuración HTTPS, acceso de red y capacidad bajo la carga real de su organización.

El sistema entregado **usa PostgreSQL 17** en Docker Compose; PGlite no se incluye como dependencia ni reemplaza su base de datos. Fue una herramienta de validación temporal.

## Repetir las pruebas contra PostgreSQL 17

Use una base exclusiva y vacía al primer inicio. El perfil de pruebas crea el administrador `testadmin`; no debe existir otro administrador. Las pruebas insertan registros con identificadores únicos y los conservan. Nunca ejecute esta suite contra una base de producción.

Con PostgreSQL disponible, configure las variables para esa base de prueba:

```bash
export DB_HOST=127.0.0.1
export DB_PORT=5432
export DB_NAME=siabuc_helpdesk_test
export DB_USER=siabuc_test
read -rsp 'Contraseña de la base de pruebas: ' DB_PASSWORD
export DB_PASSWORD
cd backend
mvn -Ppostgres-it verify
```

La base y su usuario deben existir y ese usuario debe poder crear el esquema; Flyway lo inicializa. No configure `SPRING_FLYWAY_ENABLED=false` ni reduzca el pool para esta comprobación nativa. Los informes aparecen en `backend/target/failsafe-reports/`.

Para las pruebas de lógica del cliente, desde la raíz:

```bash
cd frontend
npm ci
npm test
npm run build
```

El empaquetado normal `mvn package` no ejecuta las pruebas de integración, por lo que construir una imagen no necesita una base de datos en ejecución. Para ejecutar el navegador contra una instalación de pruebas con sus tres servicios activos, siga [tests/e2e/README.md](../tests/e2e/README.md).
