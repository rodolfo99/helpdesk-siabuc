# Pruebas del sistema en navegador

## Flujo de casos 1.8.3 validado en CI

`case-workflow-v183.ui.mjs` ejecuta cuatro escenarios sobre Angular compilado
con API simulada: cinco etiquetas y filtros/vistas, cambio de estado con motivo
y reapertura, respuesta del cliente en espera sin controles administrativos, y
pantalla móvil de 390 px. No requiere una cuenta real ni crea datos en PostgreSQL.

Desde la raíz del proyecto:

```bash
cd frontend
npm ci
npm test
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node case-workflow-v183.ui.mjs
```

Admite `E2E_PLAYWRIGHT_PATH`, `E2E_CHROMIUM_PATH` y `E2E_CHROMIUM_ARGS`.
El [registro 1.8.3](../../docs/VALIDACION-1.8.3.md) identifica el SHA, ejecución
y resultado. Estas cuatro pruebas visuales no sustituyen los permisos,
transacciones y migraciones que comprueba Java con PostgreSQL 17.

## Herramientas de regresión de versiones anteriores

Los apartados siguientes describen suites originadas en versiones previas y
su alcance individual. Se conservan como herramientas auxiliares; la presencia
de un comando o una cifra de escenarios no afirma que esa suite se haya ejecutado
contra el head actual. Compruebe su compatibilidad con la interfaz elegida y
consulte [Validación](../../docs/VALIDACION.md) para la evidencia por revisión.
En 1.8.3 se adaptaron los selectores de estado de `helpdesk.e2e.mjs`,
`workflows-v16.e2e.mjs` y `workflows-v17.e2e.mjs`; la ejecución completa de esas
tres suites sobre 1.8.3 no se atribuye a los cuatro escenarios simulados anteriores.

### Recorrido general con servidor real

La suite ejecuta **13 verificaciones secuenciales** de Angular, Spring y la base de datos. No intercepta ni simula las respuestas de la aplicación. Comprueba acceso con sesión, CRUD institucional con baja lógica, alta de usuario, conversión de horas a minutos, historial, notas internas, consumos independientes, corrección de consumos, cierre/reapertura, sobregiro, permisos y diseño adaptable. La preparación de la segunda institución y las comprobaciones de seguridad utilizan la API real desde la misma sesión del navegador.

**Se requiere una base de datos desechable de pruebas, separada de producción:** la suite crea instituciones, usuarios, casos e historial de auditoría y conserva esos registros al terminar. Cada ejecución usa nombres únicos; termina dando de baja la institución principal. No modifica el usuario administrativo suministrado. No elimina registros históricos ni restablece la base de datos automáticamente.

Con el sistema iniciado y un administrador configurado:

```bash
cd tests/e2e
npm ci
npx playwright install chromium
export E2E_BASE_URL=http://localhost:4200
read -r -p 'Usuario administrador de pruebas: ' E2E_ADMIN_USER
export E2E_ADMIN_USER
read -r -s -p 'Contraseña: ' E2E_ADMIN_PASSWORD
export E2E_ADMIN_PASSWORD
npm test
unset E2E_ADMIN_PASSWORD
```

Usa la dirección pública del cliente Angular, que debe reenviar `/api` a Spring. Ajusta el puerto a tu configuración; en Docker el predeterminado de la interfaz puede ser distinto. El proceso devuelve código 0 si todas las verificaciones pasan, o 1 ante un fallo. La contraseña del usuario institucional se genera en memoria en cada ejecución y no se imprime ni se guarda.

Variables opcionales:

| Variable | Uso |
| --- | --- |
| `E2E_REPORT_DIR` | Carpeta de diagnóstico para `results.json`; ante un fallo también guarda `failure.png`. Los diagnósticos pueden contener nombres e historial de pruebas. |
| `E2E_HEADLESS=false` | Muestra el navegador en equipos con entorno gráfico. |
| `E2E_CHROMIUM_PATH` | Ejecutable Chromium ya instalado, si no se usa el descargado por Playwright. |
| `E2E_CHROMIUM_ARGS` | Arreglo JSON de argumentos de lanzamiento para entornos controlados. |
| `E2E_PLAYWRIGHT_PATH` | Carpeta absoluta de un módulo Playwright ya instalado; omitir para usar la dependencia local. |

Los casos de uso se ejecutan secuencialmente porque verifican una misma historia de consumo y saldo. Las pruebas de concurrencia transaccional y los permisos adicionales se encuentran en el servidor; esta suite comprueba su integración con la interfaz.

## Ampliación 1.1: cobranza y notificaciones

Con las mismas variables de entorno y la migración 1.1 aplicada, ejecutar:

```bash
npm run test:billing-mail
```

La suite independiente `billing-mail.e2e.mjs` exige **`MAIL_ENABLED=false`** y lo comprueba antes de crear datos. Todos sus correos utilizan el dominio reservado `example.test`. Encola mensajes sin enviarlos; la entrega SMTP contra un sumidero local se comprueba en las pruebas del servidor.

Comprueba 12 escenarios: configuración sin secretos; cuentas de Cobranza y responsable creadas por interfaz; contactos y responsable institucional; permisos de Cobranza; pago y acreditación de 65/30 minutos por interfaz; idempotencia y referencia única; anulación administrativa por interfaz; asignación de casos; privacidad de notas internas y avisos de cierre; prueba al correo propio; recuperación de un aviso omitido; y acceso limitado del cliente. Captura el UUID de la petición real de pago para comprobar el reintento idempotente, sin simular respuestas.

Crea nuevas cuentas de pruebas y no cambia la cuenta administrativa suministrada. Conserva pagos, anulaciones, casos y cola para inspección. Si se define `E2E_REPORT_DIR`, genera `billing-mail-results.json`, capturas de la pantalla de pagos en escritorio y móvil de 390 píxeles (`payments-desktop.png` y `payments-mobile.png`) y, sólo ante fallo, `billing-mail-failure.png`. Las capturas contienen exclusivamente los datos de la instalación de pruebas; no se incluyen automáticamente en la distribución.

## Control de correo 1.3.1 con API simulada

`mail-control.ui.mjs` prueba la pantalla Angular con Chromium e intercepta la API: confirma desactivación, reactivación sólo para avisos nuevos, estado SUPPRESSED sin reintento, límite del servidor, errores, permisos visibles y diseño móvil. No usa credenciales reales ni envía correo. Es independiente de las suites con servidor real descritas arriba.

```bash
cd frontend
npm ci
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node mail-control.ui.mjs
```

Admite E2E_PLAYWRIGHT_PATH, E2E_CHROMIUM_PATH y E2E_CHROMIUM_ARGS para instalaciones de navegador propias. El resultado de esta prueba de interfaz no reemplaza las pruebas de permisos en Spring ni demuestra entrega SMTP.

## Detalle de historiales y toma de casos 1.3.3 con API simulada

`history-detail.ui.mjs` prueba la aplicación Angular compilada en Chromium: clic en filas y tarjetas, contenido completo de avisos y atenciones, texto multilínea escapado, navegación por teclado, regreso al listado con filtros y posición, notas internas, anulaciones, errores de consulta y pantalla móvil. También comprueba que un cliente puede abrir un caso y que un administrador puede tomar uno sin responsable, incluidos el bloqueo durante la petición y el conflicto cuando otro administrativo se adelanta.

Tras compilar Angular e instalar Playwright como en el apartado anterior, ejecute desde `tests/e2e`:

```bash
node history-detail.ui.mjs
node mail-control.ui.mjs
```

Estas pruebas interceptan la API con datos de prueba: no sustituyen `mvn -Ppostgres-it verify`, no conectan con PostgreSQL ni envían correo. Admiten las mismas variables E2E_PLAYWRIGHT_PATH, E2E_CHROMIUM_PATH y E2E_CHROMIUM_ARGS. E2E_REPORT_DIR permite conservar el informe y capturas de prueba fuera del paquete distribuido.

## Funciones Helpdesk 1.4 con API simulada

`helpdesk-v14.ui.mjs` ejecuta **16 escenarios** sobre Angular compilado: alta de caso con ficha técnica en una única solicitud; filtros combinados de categoría, prioridad y asignación; edición de ficha y manejo de errores; separación de adjuntos del caso y del elemento del historial; carga multipart y descarga; cronómetro con pausa, continuación y confirmación antes del consumo; propuesta administrativa y confirmación institucional de sesiones remotas; campana, lectura de avisos y navegación a otro caso; filtros de exportación; calendario, umbrales de saldo y permisos visibles de cada rol. La comprobación móvil usa 390 píxeles y rechaza el desbordamiento horizontal del caso.

Desde la raíz del proyecto:

```bash
cd frontend
npm ci
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node helpdesk-v14.ui.mjs
node history-detail.ui.mjs
node mail-control.ui.mjs
```

Las tres suites comprueban respectivamente **16, 17 y 10 escenarios** de interfaz. Cada archivo levanta un servidor estático temporal en `127.0.0.1`, intercepta `/api` y utiliza únicamente datos sintéticos. No requiere una instalación de Spring, PostgreSQL ni cuentas reales. Las regresiones de historial y correo incluyen respuestas vacías para la nueva campana y los nuevos apartados; mantienen sus verificaciones anteriores.

`E2E_REPORT_DIR` permite guardar siete capturas de la suite 1.4: ficha/SLA, adjuntos, cronómetro, agenda, reportes, configuración y campana móvil. Las capturas no forman parte del ZIP del proyecto. También admite `E2E_PLAYWRIGHT_PATH`, `E2E_CHROMIUM_PATH` y `E2E_CHROMIUM_ARGS`.

**Alcance de la evidencia:** las simulaciones comprueban navegación, formularios, peticiones y estados de pantalla. No demuestran permisos del servidor, contenido binario válido de Excel/PDF, validación real de archivos, exactitud del reloj del servidor, concurrencia ni entrega de correo. Esas comprobaciones corresponden a las pruebas del servidor con base de datos. La prueba de cronómetro verifica que la interfaz no solicita el descuento hasta confirmar; no sustituye la comprobación transaccional e idempotente del servidor. No se conecta con un SMTP ni se envían mensajes reales.

## Pólizas, cobranza y soporte 1.5 con servidor real

`billing-v15.e2e.mjs` usa la interfaz Angular y su API Spring real, sin interceptores ni respuestas simuladas. Requiere una instalación **1.5.0 o posterior**, migraciones aplicadas, una base **desechable** y `MAIL_ENABLED=false`. Esta última condición se verifica leyendo la configuración efectiva de arranque, aunque el correo ya estuviera pausado.

La suite crea cuentas de Cobranza y cliente, instituciones y registros con nombres únicos. Activa la pausa administrativa de correo en esa instalación de pruebas y conserva los registros para inspección. **No debe ejecutarse en producción:** la variable `E2E_DISPOSABLE_DATABASE=true` confirma expresamente el uso de una base de pruebas, pero no sustituye esa separación. El administrador proporcionado se utiliza para preparar cuentas y atender el caso; su contraseña no se cambia ni se imprime.

Con Docker Compose u otra instalación de pruebas ya iniciada, ejecutar en Linux:

```bash
cd tests/e2e
npm ci
npx playwright install chromium
export E2E_BASE_URL=http://localhost:4200
export E2E_DISPOSABLE_DATABASE=true
read -r -p 'Usuario administrador de pruebas: ' E2E_ADMIN_USER
export E2E_ADMIN_USER
read -r -s -p 'Contraseña de pruebas: ' E2E_ADMIN_PASSWORD
export E2E_ADMIN_PASSWORD
node billing-v15.e2e.mjs
unset E2E_ADMIN_PASSWORD
```

En PowerShell, después de instalar las dependencias y Chromium:

```powershell
$env:E2E_BASE_URL = 'http://localhost:4200'
$env:E2E_DISPOSABLE_DATABASE = 'true'
$env:E2E_ADMIN_USER = Read-Host 'Usuario administrador de pruebas'
$qaPassword = Read-Host 'Contraseña de pruebas' -AsSecureString
$env:E2E_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $qaPassword).Password
try { node billing-v15.e2e.mjs }
finally { Remove-Item Env:E2E_ADMIN_PASSWORD; Remove-Variable qaPassword }
```

Ajusta `E2E_BASE_URL` al puerto del cliente. Debe reenviar `/api` al servidor probado. El script utiliza sesiones, cookies y protección CSRF reales. Admite las variables de navegador y `E2E_REPORT_DIR` descritas arriba.

Recorrido cubierto:

- Alta de póliza en MXN sin crédito automático de minutos.
- Abono sólo monetario, parcialidad con minutos explícitos, liquidación y liberación posterior de horas con importe cero.
- Saldo antes/después, historial de contrato y pagos, reintento idempotente, rechazo de sobrepago y de moneda distinta de MXN en nuevos registros.
- Comprobante PDF sintético adjunto al pago, descarga íntegra y separación de permisos respecto al cliente institucional.
- Reporte financiero de la institución, importes separados por moneda y descargas reales XLSX/PDF. Los binarios deben tener firmas de archivo válidas; las pruebas Java verifican su estructura y contenido detallado.
- Cliente sin menús ni acceso API financiero, apertura de caso con categoría/prioridad/ficha, archivo de diagnóstico y aislamiento institucional.
- Administrativo toma el caso, recupera un cronómetro pausado y confirma sus minutos; no hay descuento antes de confirmar.
- Propuesta y confirmación de sesión remota, historial con regreso al listado, alerta de saldo y campana interna con correo pausado.

Cuando `E2E_REPORT_DIR` está definida se guarda `billing-v15-results.json`, capturas, archivos descargados y una captura ante fallo. Sólo deben contener datos sintéticos de pruebas. No se incluyen automáticamente en el ZIP distribuido.

Estas pruebas pueden ejecutarse contra PostgreSQL de una instalación Docker de pruebas. La ejecución local de desarrollo mediante PGlite usa una base en memoria con el SQL exacto de las migraciones; debe identificarse como tal en los resultados y **no demuestra ejecución nativa de PostgreSQL 17 ni de Docker**. La suite no entrega correos, no cobra dinero ni contacta bancos. Las pruebas de servidor siguen siendo necesarias para concurrencia, reversión transaccional y validación exhaustiva de archivos.

## Pólizas y cobranza 1.5 con API simulada

`billing-v15.ui.mjs` comprueba **18 escenarios** sobre el cliente Angular compilado. Levanta un servidor estático temporal en `127.0.0.1` e intercepta `/api` con registros sintéticos; no requiere Spring, PostgreSQL ni cuentas reales.

Desde la raíz del proyecto:

```bash
cd frontend
npm ci
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node billing-v15.ui.mjs
```

Puede ejecutarse también en Windows desde PowerShell con los mismos comandos. Admite `E2E_PLAYWRIGHT_PATH`, `E2E_CHROMIUM_PATH`, `E2E_CHROMIUM_ARGS` y `E2E_REPORT_DIR`. Esta última variable guarda cinco capturas: póliza, pago acreditado, reporte de cobranza, formulario móvil y comprobante de pago.

La suite verifica creación de pólizas sin acreditar minutos, edición y cancelación según su historial, pagos vinculados, parcialidades sin minutos, liberación de horas ya pagadas sin un nuevo cobro, límites de póliza, saldo institucional antes/después y fechas civiles sin desfase por zona horaria. También prueba protección contra doble clic, identificador de reintento estable, métodos de pago, MXN en nuevos cobros, conservación de registros históricos en otras monedas, filtros y descargas del último reporte consultado, permisos visibles por rol y el formulario móvil a 390 píxeles.

Los comprobantes se adjuntan y descargan tanto en pagos vigentes como anulados. La prueba comprueba que el archivo utiliza el mismo pago y que no se envía otra petición de cobro ni se vuelven a acreditar horas. Las simulaciones de las suites anteriores incluyen ahora `/policies` para conservar sus comprobaciones al cargar el resumen de Cobranza.

**Límites:** los permisos comprobados aquí son los de la interfaz; el servidor necesita sus propias pruebas de autorización. Los archivos de descarga simulados no validan el contenido real de Excel/PDF, la persistencia de adjuntos, el control de concurrencia ni las transacciones. Esta suite no entrega correo, cobra dinero ni contacta bancos. Para esos comportamientos del servidor, ejecutar las pruebas de integración y `billing-v15.e2e.mjs` contra una base desechable.

## Flujos 1.6 con interfaz y servidor reales

`workflows-v16.e2e.mjs` nació en 1.6; su selector de estados actual requiere la interfaz 1.8.3 y una base desechable nueva con todas sus migraciones y correo de arranque desactivado (`MAIL_ENABLED=false`). Usa Angular, sesiones, cookies, CSRF, Spring y persistencia reales: no intercepta `/api` ni simula respuestas. Crea cuentas e instituciones propias y conserva sus registros para diagnóstico.

Utiliza la misma instalación de Playwright y variables de la sección «Pólizas, cobranza y soporte 1.5 con servidor real». Cambia únicamente el comando por:

```bash
node workflows-v16.e2e.mjs
```

El comando funciona también en PowerShell con las variables allí documentadas. Exige `E2E_DISPOSABLE_DATABASE=true` y credenciales de un administrador de pruebas; `E2E_REPORT_DIR` guarda `workflows-v16-results.json`, capturas y descargas sintéticas. No ejecutarlo sobre datos de producción.

El recorrido contiene 14 etapas y cubre:

- Cotización preparada por Cobranza, constancias de emisión y aceptación, conversión idempotente a póliza sin acreditar minutos. Aviso de vencimiento con umbral configurable, lectura persistente y sin duplicados al revisar nuevamente.
- Portal financiero deshabilitado inicialmente, habilitación explícita por administración y separación entre instituciones.
- Cliente envía solicitud pendiente y comprobante PDF sintético; no aumenta saldo. Cobranza verifica la evidencia y aprueba una vez: pago, póliza y minutos se actualizan juntos.
- Recibo administrativo PDF con folio estable, accesible al personal financiero y al cliente autorizado. Las descargas y reintentos no vuelven a acreditar minutos; la evidencia conserva sus bytes. Un pago auxiliar sin consumo se anula para verificar que el PDF cliente muestra ANULADO, conserva folio y oculta las notas del pago y el motivo privado de anulación; el saldo neto queda igual.
- Publicación de conocimiento, consulta del cliente y enlace al caso; los borradores e historial editorial no son públicos. Retirar una publicación oculta también su enlace al cliente.
- Evaluación institucional del caso cerrado, rechazo en casos abiertos y a usuarios sin permiso; reabrir conserva la evaluación. Reporte por periodo, institución y responsable con conteo, promedio y CSV.
- Respaldos automáticos inicialmente desactivados: guardar horario conserva ese estado. Sin worker conectado no permite activar ni solicitar ejecución; el diagnóstico y la explicación del respaldo manual permanecen visibles. USER y BILLING no pueden administrar respaldos.

Para la comprobación de respaldos, esta suite requiere **no iniciar el worker** y una base de pruebas nueva: verifica configuración y permisos, no crea un archivo de respaldo. El manual con calendario desactivado se verifica en las pruebas del servicio con fixtures identificadas. Este recorrido no demuestra un `pg_dump`, programación Docker ni restauración real; esos procedimientos se verifican por separado siguiendo la documentación de respaldos. PGlite, si se usa como motor de pruebas, debe declararse expresamente y no equivale a PostgreSQL nativo.

## Base de conocimientos 1.6 con API simulada

`knowledge-v16.ui.mjs` comprueba **10 escenarios** de interfaz sobre Angular compilado. Desde la raíz, compilar el cliente y preparar Chromium con los mismos comandos de las suites anteriores; después ejecutar:

```bash
cd tests/e2e
node knowledge-v16.ui.mjs
```

Comprueba búsqueda y paginación, filtros por categoría y versión, alta de borrador, publicación con confirmación, edición con revisión esperada, manejo de conflicto conservando el texto local, archivo, vínculo y retiro de soluciones del caso, controles visibles por rol y regreso al listado. La prueba de texto literal introduce etiquetas sintéticas y verifica que no se convierten en elementos HTML ejecutables. La vista móvil se verifica a 390 píxeles, incluyendo texto largo sin espacios.

`E2E_REPORT_DIR` guarda cuatro capturas: administración editorial, consulta de cliente, solución vinculada a caso y consulta móvil. Las variables `E2E_PLAYWRIGHT_PATH`, `E2E_CHROMIUM_PATH` y `E2E_CHROMIUM_ARGS` permiten usar un navegador ya instalado.

Las cuatro suites previas (`helpdesk-v14.ui.mjs`, `billing-v15.ui.mjs`, `history-detail.ui.mjs`, `mail-control.ui.mjs`) conservan sus 61 escenarios e incorporan respuestas sintéticas para el aviso global de respaldos, la configuración de acceso financiero, los vínculos de conocimiento y las encuestas de casos cerrados. Esto permite comprobar las funciones anteriores dentro de la aplicación 1.6 completa. Las cinco suites juntas cubren 71 escenarios simulados.

**Alcance:** estas pruebas ejecutan la interfaz real compilada con API interceptada. No demuestran seguridad del servidor, persistencia PostgreSQL, restauración de respaldos, entrega de correo ni operaciones monetarias reales. `KnowledgeIntegrationIT` valida los permisos y estados del servidor; los flujos con API y base de datos reales se ejecutan y reportan por separado. Los datos y textos de estas pruebas son sintéticos.

## Flujos integrados de 1.7 con API real

`workflows-v17.e2e.mjs` recorre Angular, Spring y una base desechable sin interceptar respuestas. Cubre:

- Sesión TECH sin menús ni acceso a rutas privilegiadas; instituciones sin notas administrativas.
- Avisos de plazos SLA en la campana con SMTP pausado, vistas personales guardadas y reaplicadas después de recargar.
- Toma de caso, vista previa e inserción de una plantilla sin publicar ni consumir, registro explícito de cinco minutos y cierre/reapertura por técnico; ausencia de controles de reasignación y anulación de consumo.
- Incidencia con dos instituciones, avance interno y publicación revisada; el cliente consulta sólo el resumen de su caso, sin IDs ni datos de la otra institución.
- Paquete, cotización desde su versión y cambio de tarifa que conserva los términos previos.
- Calendario de parcialidad y aplicación de un abono existente sin duplicar minutos.
- CSV UTF-8 con BOM, vista previa, importación, confirmación de conciliación y reapertura de importación repetida sin duplicados.
- Estado de cuenta emitido por Cobranza y por cliente autorizado, PDF descargable y denegación de acceso cruzado; capturas en escritorio y a 390 píxeles.

El recorrido nació en 1.7; el script actual, adaptado al selector de cinco estados, debe apuntar a una instalación de pruebas nueva de 1.8.3, con `MAIL_ENABLED=false` y respaldos automáticos desactivados. Usa las variables de navegador y credenciales descritas arriba:

```bash
export E2E_DISPOSABLE_DATABASE=true
export E2E_REPORT_DIR=/tmp/siabuc-e2e-v17
node tests/e2e/workflows-v17.e2e.mjs
```

La suite crea fixtures mediante la API real, modifica la configuración de avisos SLA y pausa el correo en la base **desechable**. No la ejecutes contra producción. Guarda resultados detallados en `workflows-v17-results.json`, capturas y PDFs cuando se configura `E2E_REPORT_DIR`. Las cuentas de prueba usan contraseñas aleatorias que no se imprimen ni incluyen en el informe.

El entorno y resultados efectivamente ejecutados se registran en `docs/VALIDACION.md`. Esta suite no acredita entrega SMTP, respaldo/restauración Docker ni ejecución nativa de scripts Windows. Si se ejecuta sobre PGlite, ese motor se declara como tal.
