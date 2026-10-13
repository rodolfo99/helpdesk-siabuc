# SIABUC Helpdesk 1.6.0

Sistema de atención técnica con servidor **Spring Boot 3.5.16 / Java 17**, cliente **Angular 21.2.25** y base de datos **PostgreSQL 17**. Conserva instituciones, usuarios, casos, pagos, actualizaciones descriptivas y auditoría. El tiempo por correo y por escritorio remoto se administra en **dos bolsas independientes de minutos enteros**.

**Versión 1.6:** cotizaciones, solicitudes de pago por validar, renovación de pólizas, portal financiero institucional configurable, recibos PDF, base de conocimientos, encuestas y respaldos programados. Incluye todas las funciones de soporte y cobranza de 1.4/1.5.

**Los respaldos automáticos están desactivados por defecto**, también al actualizar desde 1.5. Sólo Administración puede activarlos expresamente, configurar su horario y retención o volver a desactivarlos. Los respaldos manuales y las copias existentes se conservan.

**Linux y Windows:** scripts `.sh` y `.bat` para configurar, compilar, iniciar, detener, respaldar y restaurar. Con Docker se requieren Python 3.10+ y Docker Compose; en Windows, Docker Desktop con contenedores Linux. Java, Maven y Node se ejecutan dentro de las imágenes.

| Necesito… | Guía |
| --- | --- |
| Instalar o actualizar conservando mis datos | [Versión 1.6](docs/VERSION-1.6.md) · [Windows](docs/WINDOWS.md) · [Operación Linux/Docker](docs/OPERACION.md) |
| Atender casos, usar adjuntos, cronómetro y agenda | [Manual de uso](docs/MANUAL-USUARIO.md) · [Funciones de soporte](docs/HELPDESK-1.4.md) |
| Registrar pólizas, pagos y horas adquiridas | [Pólizas y cobranza](docs/POLIZAS-COBRANZA.md) |
| Cotizar, renovar o validar pagos pendientes | [Cotizaciones y renovaciones](docs/COTIZACIONES-RENOVACIONES.md) · [Solicitudes de pago](docs/SOLICITUDES-PAGO.md) |
| Habilitar la consulta financiera del cliente | [Portal financiero](docs/PORTAL-FINANCIERO.md) |
| Adjuntar un recibo o una transferencia | [Comprobantes de pago](docs/COMPROBANTES-PAGO.md) |
| Consultar consumos o ingresos y exportarlos | [Reportes técnicos](docs/REPORTES.md) · [Reportes de cobranza](docs/REPORTES-COBRANZA.md) |
| Configurar o pausar los avisos por correo | [SMTP interno](docs/SMTP-INTERNO.md) · [Pausa de envío](docs/DESACTIVAR-CORREO.md) |
| Configurar copias programadas (apagadas por defecto) | [Respaldos automáticos](docs/RESPALDOS-AUTOMATICOS.md) |
| Respaldar, restaurar o conocer las pruebas | [Respaldo y restauración](docs/RESPALDO-RESTAURACION.md) · [Validación y límites](docs/VALIDACION.md) |

## Funciones incluidas

- Alta, edición y baja lógica de instituciones; contacto, notas y presupuestos separados para correo y remoto.
- Tiempo asignado, utilizado y restante, presentado en horas y minutos; todo cálculo persistente usa minutos.
- Usuarios con contraseña y roles `ADMIN` / `USER` / `BILLING` (Cobranza), vinculados a su institución cuando corresponde.
- Casos abiertos/cerrados, descripción inicial y cronología de atenciones, comentarios, cambios de estado y anulaciones.
- Registro administrativo de consumos, validación de saldo y rechazo transaccional de operaciones que lo excedan.
- Comentarios públicos e internos: los internos sólo son visibles para administradores.
- Portal del usuario para consultar saldo e historial de su institución, abrir casos y agregar comentarios públicos.
- Los administrativos pueden **Tomar caso** cuando está abierto y sin responsable; la asignación queda en el historial y genera el aviso correspondiente. El listado permite filtrar casos sin responsable o propios.
- Detalle de cada elemento del historial de atención y de notificaciones, con **Volver al historial**; conserva filtros y posición del listado. Los avisos muestran su correo completo únicamente a administradores.
- Auditoría administrativa de cambios; anular una atención reintegra sus minutos sin borrar su historia.
- Inicio de sesión con cookies de sesión, protección CSRF y contraseñas BCrypt; cambio de contraseña desde la cuenta.
- Inicio de PostgreSQL, Postfix, Spring y Angular con Docker Compose, puertos configurables, respaldo y restauración.
- Asignación de un administrador responsable por institución y caso; correo de contacto por cuenta.
- Registro de pagos y acreditación atómica de minutos, con referencias únicas y protección ante reintentos duplicados.
- Avisos SMTP al cliente y responsable por cambios de casos, incluido el cierre; avisos de pagos y anulaciones, cola persistente y reintentos.
- Cada aviso de caso identifica la institución cliente y el administrativo que atiende, incluye la descripción actual, el detalle de la actualización y el estado **Abierto** o **Cerrado**, e invita a consultar la plataforma Helpdesk.
- Activación y pausa persistente del envío desde **Correo y notificaciones**, exclusiva de administradores, sin reiniciar: los avisos anteriores y los generados durante la pausa quedan sin envío; al reactivar sólo se notifican eventos nuevos.
- Servidor SMTP Postfix interno configurado por defecto, cola persistente, entrega directa o relay saliente configurable. Sin buzones ni bandeja web.
- Categorías, prioridades, ficha técnica y plazos de primera respuesta y resolución calculados con horario laboral configurable.
- Adjuntos protegidos en casos y comentarios: capturas, PDF y diagnósticos; incluidos en el respaldo de PostgreSQL.
- Campana de avisos y respuestas pendientes, independiente del correo; umbrales de saldo bajo por institución y canal.
- Cronómetro persistente: iniciar, pausar, finalizar y confirmar minutos antes de descontarlos.
- Agenda de sesiones remotas: propuesta administrativa, confirmación del cliente y control de horarios ocupados.
- Reportes por institución, administrativo, canal y periodo, con exportación real a Excel y PDF.
- Pólizas por institución con vigencia, importe en MXN y horas contratadas; historial y seguimiento de parcialidades para Cobranza y Administración.
- Pagos vinculados a la acreditación de minutos por canal, con límites de póliza, control de duplicados y reportes financieros por institución, método y periodo.
- Comprobantes PDF/PNG/JPEG adjuntos a cada pago, protegidos por rol e institución e incluidos en respaldos.
- Cotizaciones MXN con emisión y aceptación documentadas, conversión única a póliza y renovaciones relacionadas sin acreditar horas automáticamente.
- Solicitudes de pago con comprobantes: pendientes sin crédito, aprobación por Administración/Cobranza con acreditación atómica, o rechazo con motivo.
- Portal financiero del cliente habilitable por institución, inicialmente desactivado; consulta de sus pólizas, pagos y evidencia y presentación de solicitudes.
- Recibos administrativos PDF con folio y datos preservados desde su primera emisión; anulación visible. No son facturas fiscales.
- Base de conocimientos por categoría/versión de SIABUC, borradores privados, publicación y enlaces desde los casos.
- Encuesta institucional de 1 a 5 al cerrar el caso, con comentario y reportes de satisfacción para Administración.
- Servicio de respaldos automáticos con programación diaria, retención, historial y alertas de error. **Apagado por defecto** y desactivable sin borrar copias ni impedir respaldos manuales.

**Alcance:** registra la atención por correo y las sesiones remotas y envía avisos cuando se configura SMTP. No lee correos entrantes ni abre conexiones de escritorio remoto. Los minutos se confirman manualmente o con ayuda del cronómetro. Los pagos registran dinero recibido y acreditan minutos: no procesan cargos bancarios ni emiten facturas fiscales. Es una aplicación independiente de SIABUC.

## Roles y acceso

| Cuenta | Alcance |
| --- | --- |
| Administración (`ADMIN`) | Instituciones, usuarios, soporte, configuración, reportes técnicos y supervisión financiera. Puede anular pagos si las reglas de saldo lo permiten. |
| Cliente (`USER`) | Saldo, casos e historial público de su institución; abre solicitudes, comenta, adjunta archivos de soporte y confirma sesiones remotas. Sin notas internas. El acceso financiero propio requiere habilitación expresa de Administración. |
| Cobranza (`BILLING`) | Saldos institucionales, pólizas, pagos, acreditación de horas, comprobantes y reportes financieros. Sin acceso a expedientes técnicos, administración de cuentas o anulación de pagos. |

Los permisos se comprueban en el servidor. Los adjuntos de soporte respetan la institución y la visibilidad de la entrada; Administración/Cobranza consultan todos los comprobantes de pago; el portal financiero habilitado permite al cliente consultar sólo los de su institución. La campana sigue funcionando con el correo pausado y muestra a Cobranza sus avisos de saldo.

Para actualizar desde una versión anterior, respalde primero y conserve `.env`, los volúmenes y las migraciones existentes. Siga [Actualizar a 1.6](docs/VERSION-1.6.md); las guías de [migración 1.0/1.1](docs/MIGRACION-1.1.md) y [SMTP interno](docs/SMTP-INTERNO.md#actualizar-una-instalación-anterior) conservan las particularidades de esas versiones. No es necesario cambiar una configuración SMTP que ya funciona.

## Inicio rápido desde cero con Docker

Requisitos en Linux: Docker Engine funcionando, complemento `docker compose` con soporte de `--wait`, Python 3.10 o posterior, acceso de red a los repositorios de imágenes y dependencias. Con Docker no necesita instalar Java, Maven ni Node en el anfitrión.

1. Descomprima el proyecto y abra una terminal en la carpeta `siabuc-helpdesk`.
2. Permita la ejecución de los scripts si el descompresor no conservó los permisos:

   ```bash
   chmod +x ./*.sh
   ```

3. Genere la configuración y revísela en su editor:

   ```bash
   ./configurar.sh
   nano .env
   ```

   Se generan contraseñas aleatorias diferentes para PostgreSQL y el primer administrador. `.env` queda con permisos `600`. La contraseña de acceso está en `ADMIN_PASSWORD`, el usuario en `ADMIN_USERNAME`. No hay una contraseña predeterminada compartida.

   Si prefiere elegir la contraseña de PostgreSQL antes del primer inicio, ejecute:

   ```bash
   ./configurar.sh --password-bd
   ```

   La solicita con entrada oculta y confirmación, y la guarda como `DB_PASSWORD` en `.env`. Este comando sólo configura el archivo; no cambia la contraseña de una base ya creada.

   Configure la IP local del equipo que enviará los avisos:

   ```bash
   ./configurar.sh --smtp-interno
   ```

   Por ejemplo, con `SMTP_LOCAL_IP=192.168.1.50` el remitente será `soporte@[192.168.1.50]`. Sustituya el ejemplo por la IP real de su servidor en la red local. Los corchetes forman parte de la dirección.

4. Compile e inicie los servicios:

   ```bash
   ./iniciar.sh
   ```

5. Abra **http://127.0.0.1:4200** o el puerto `FRONTEND_PORT` que haya configurado. Acceda con la cuenta indicada en `.env`, cambie su contraseña en **Mi cuenta** y comience creando una institución y sus usuarios.

El primer inicio descarga dependencias y puede tardar varios minutos. El script espera a que PostgreSQL, Postfix, Spring, Nginx y el servicio de respaldo respondan correctamente. El servicio de respaldo permanece esperando: su calendario empieza desactivado. Si algún puerto está ocupado, cambie su valor en `.env` y vuelva a iniciar.

Si omite `configurar.sh`, `iniciar.sh` genera `.env` automáticamente. Nunca publique ni comparta ese archivo. Los scripts resuelven su propia carpeta y pueden invocarse desde cualquier directorio.

### Comandos equivalentes en Linux y Windows

En PowerShell use el prefijo `.\`; en CMD puede omitirlo. El desarrollo con Spring/Angular fuera de Docker se mantiene en Linux/WSL2 mediante `iniciar-dev.sh`.

| Operación | Linux / Bash | Windows / PowerShell |
| --- | --- | --- |
| Crear o validar configuración | `./configurar.sh` | `.\configurar.bat` |
| Elegir contraseña de una base nueva | `./configurar.sh --password-bd` | `.\configurar.bat --password-bd` |
| Configurar Postfix interno | `./configurar.sh --smtp-interno` | `.\configurar.bat --smtp-interno` |
| Compilar e iniciar todo | `./iniciar.sh` | `.\iniciar.bat` |
| Compilar imágenes | `./compilar.sh --docker` | `.\compilar.bat --docker` |
| Detener conservando datos | `./detener.sh` | `.\detener.bat` |
| Respaldar con nombre elegido | `./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump` | `.\respaldar.bat --nombre-respaldo backups/mi-respaldo.dump` |
| Restaurar una copia compatible | `./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo` | `.\restaurar.bat --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo` |

Restaurar reemplaza los datos actuales y deja la aplicación detenida. Para recuperar una versión anterior utilice una base nueva y vacía según la [guía de retroceso](docs/RESPALDO-RESTAURACION.md#retroceder-a-una-versión-anterior). La disponibilidad de scripts Windows no equivale a una ejecución nativa validada; consulte [Windows](docs/WINDOWS.md) y [Validación](docs/VALIDACION.md).

## Puertos y configuración

Edite `.env` **antes del primer inicio**. Puede usar, por ejemplo, `DB_PORT=5433`, `SERVER_PORT=8081` y `FRONTEND_PORT=4201` si ya tiene aplicaciones en los valores predeterminados.

| Variable | Predeterminado | Significado |
| --- | --- | --- |
| `DB_PORT` | `5432` | Puerto de PostgreSQL publicado en el anfitrión. |
| `SERVER_PORT` | `8080` | Puerto de Spring publicado en el anfitrión. |
| `FRONTEND_PORT` | `4200` | Puerto de la aplicación web en el anfitrión. |
| `BIND_HOST` | `127.0.0.1` | Dirección de publicación de los tres servicios; `0.0.0.0` permite acceso por red. |
| `DB_NAME` | `siabuc_helpdesk` | Nombre de base de datos inicial. |
| `DB_USER` | `siabuc` | Cuenta PostgreSQL creada en el primer arranque. |
| `DB_PASSWORD` | Aleatoria | Contraseña de esa cuenta. |
| `SMTP_INTERNAL_PORT` | `2525` | Puerto de Postfix, publicado siempre sólo en localhost. |
| `SMTP_LOCAL_IP` | `127.0.0.1` | IPv4 del anfitrión para el remitente literal y la identificación SMTP. Configure su IP LAN mediante el asistente. |
| `ADMIN_USERNAME` | `admin` | Nombre del administrador de arranque. |
| `ADMIN_PASSWORD` | Aleatoria | Contraseña utilizada sólo al crear el administrador inicial. |
| `COOKIE_SECURE` | `false` | `true` cuando el navegador accede por HTTPS. |
| `REPORTING_TIME_ZONE` | `America/Mexico_City` | Zona de fechas en los reportes. El calendario de SLA se administra en la plataforma. |
| `BACKUP_DIRECTORY` | `./backups/automatic` | Carpeta privada para el servicio de respaldos; configurarla no activa el calendario. |
| `BACKUP_UID` / `BACKUP_GID` | Usuario actual en Linux; `0` en Windows | Propietario del proceso que escribe las copias. |
| `COMPOSE_PROJECT_NAME` | `siabuc-helpdesk` | Identidad del proyecto y del volumen de Docker. |

Los puertos deben ser diferentes, enteros entre 1 y 65535. Dentro de Docker, PostgreSQL mantiene `5432`, Spring `8080` y Nginx `80`; los cambios de `.env` afectan los puertos del anfitrión y se aplican automáticamente al proxy en desarrollo. El navegador siempre accede a `/api` desde el mismo origen de Angular.

El formato admite `VARIABLE=valor` sencillo o `VARIABLE='valor literal'` para espacios y símbolos como `$` o `#`; no admite comilla simple dentro del valor, controles ni barra inversa final. Los scripts **no ejecutan `.env`** ni expanden su contenido. El asistente PostgreSQL exige al menos 12 caracteres y admite letras, números y `. _ / : @ % + = , - !`. Use `./configurar.sh --password-bd` para elegirla. `DB_NAME` y `DB_USER` admiten minúsculas, números y guion bajo, comenzando por letra o guion bajo. Las opciones SMTP y sus límites se explican en [Correo](docs/CORREO.md).

Cambiar `ADMIN_PASSWORD` en `.env` **no cambia la contraseña de una cuenta existente**. Use **Mi cuenta** o la administración de usuarios. Cambiar `DB_PASSWORD` después de inicializar el volumen tampoco cambia la contraseña almacenada en PostgreSQL; consulte [Operación](docs/OPERACION.md).

## Correo, responsables y pagos

Una instalación nueva conecta Spring al servidor SMTP interno **Postfix** en `smtp:25`, con envío habilitado. Postfix se inicia automáticamente y conserva su cola en un volumen. El remitente usa la IP local entre corchetes: `soporte@[192.168.1.50]`, por ejemplo. El valor inicial `soporte@[127.0.0.1]` permite arrancar localmente; configure la IP LAN real del anfitrión y, si lo necesita, un relay autorizado:

```bash
./configurar.sh --smtp-interno
./iniciar.sh
```

El asistente solicita de forma oculta el secreto de un relay si corresponde; no envía un mensaje al guardar. Para conectar Spring directamente a un SMTP externo, use `./configurar.sh --correo`. `MAIL_ENABLED` habilita el transporte; la pausa administrativa permanece en la base de datos. Al reanudar desde la plataforma sólo se enviarán avisos de eventos nuevos: los anteriores suprimidos no se recuperan. Capture el correo de cada administrador en **Usuarios**, el correo cliente y responsable en **Instituciones** y revise la asignación de cada caso.

La dirección con IP es una identidad de envío; este servicio no crea un buzón para recibir respuestas o rebotes. Puede configurar `MAIL_REPLY_TO` con el correo de atención real. El servidor receptor debe aceptar remitentes con IP local; los proveedores externos pueden rechazarlos. Consulte la configuración del relay institucional si los avisos deben salir de la red local.

**Notificaciones** permite al administrador consultar la cola, enviar una prueba a su propia dirección y reintentar avisos fallidos u omitidos. Las notas internas sólo notifican al responsable. Consulte [SMTP interno](docs/SMTP-INTERNO.md) para entrega directa, restricciones del puerto 25, relay, cola y red; [Correo](docs/CORREO.md) detalla destinatarios y proveedores. En el helpdesk, SENT significa que Postfix aceptó el aviso; la entrega remota puede continuar pendiente en su cola.

Gmail institucional `@ucol.mx` es una salida opcional de Postfix: `./configurar-gmail.sh` en Linux o `.\configurar-gmail.bat` en PowerShell. Consulte [Gmail UCOL](docs/GMAIL-UCOL.md) para requisitos y diagnóstico. El usuario confirmó SMTP local funcionando e informó que su configuración Gmail falló; no se presenta ese fallo como resuelto ni se requiere migrar a Gmail.

Desde 1.6 puede comenzar con una **Cotización**, guardar la aceptación y convertirla en póliza. Una **Solicitud de pago** permanece pendiente hasta que Administración o Cobranza revise su comprobante y apruebe explícitamente el importe y los minutos. Sólo la aprobación crea el pago y acredita el saldo; cotizar, crear una póliza o adjuntar evidencia no acredita horas. El registro directo de pagos se conserva para dinero ya verificado.

**Pólizas y pagos:** `ADMIN` y `BILLING` registran dinero recibido en MXN, con referencia, fecha, método, descripción y tiempo por acreditar en cada canal. Se admiten transferencia, depósito, efectivo, tarjeta de crédito/débito, cheque y otro. Los registros anteriores conservan su moneda y método. Crear una póliza no acredita horas; **Pagos** confirma juntos el movimiento y los minutos indicados, sin convertir pesos automáticamente a tiempo. Una parcialidad de póliza puede registrar dinero sin minutos; una póliza ya liquidada permite acreditar horas pendientes con importe cero.

En el detalle del pago se adjuntan [comprobantes PDF/PNG/JPEG](docs/COMPROBANTES-PAGO.md), sin repetir el cobro ni la acreditación. **Reportes de cobranza** ofrece historial por institución y totales generales, separados por moneda, con Excel/PDF. Sólo `ADMIN` puede anular un pago conforme a las reglas de saldo y respaldo monetario; se conserva toda la evidencia. Consulte [Pólizas y cobranza](docs/POLIZAS-COBRANZA.md).

## Detener, reiniciar y recompilar

```bash
./detener.sh
./iniciar.sh
```

`detener.sh` retira los contenedores y conserva el volumen con todos los datos. `iniciar.sh` reconstruye las imágenes cuando cambia el código. **No utilice `docker compose down -v` si desea conservar la base de datos.**

Para compilar imágenes sin iniciar los servicios:

```bash
./compilar.sh --docker
```

Para consultar estado y registros, ejecute desde la carpeta del proyecto:

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
docker compose logs --tail=100 db
```

## Compilar y ejecutar con Java y Angular locales

Requiere JDK 17 o posterior, Maven 3.9, Node.js 24 LTS, npm, Python 3.10 y Docker para PostgreSQL y Postfix.

```bash
./configurar.sh
./compilar.sh
./iniciar-dev.sh
```

`compilar.sh` ejecuta `mvn package`, `npm ci` y `npm run build`. Produce:

- `backend/target/siabuc-helpdesk-1.6.0.jar`
- `frontend/dist/helpdesk/browser/`

`iniciar-dev.sh` inicia PostgreSQL y Postfix con Docker, compila Spring, instala las dependencias de Angular y ejecuta Spring y `ng serve` en una terminal. Lee los mismos puertos y contraseñas de `.env`; ambos procesos escuchan en `127.0.0.1`. Los registros se guardan en `.runtime/`. **Ctrl+C** detiene Spring y Angular; después `./detener.sh` detiene PostgreSQL y Postfix. No ejecute el modo Docker completo y el modo de desarrollo al mismo tiempo sobre los mismos puertos.

Para ejecutar todo sin Docker, conectar un PostgreSQL propio o servir los archivos compilados, consulte las instrucciones completas en [Operación](docs/OPERACION.md).

## Flujo inicial recomendado

1. **Instituciones:** registre nombre, contacto, correo y responsable. Si va a acreditar horas mediante pagos, deje inicialmente las bolsas en cero para no duplicarlas con una asignación manual.
2. **Usuarios:** cree cuentas `USER` asociadas a la institución y una cuenta `BILLING` para Cobranza. Entregue las credenciales por un medio privado.
3. **Pólizas:** Cobranza registra el contrato, vigencia, importe MXN y horas por canal, cuando corresponda. Esta operación no incrementa el saldo.
4. **Pagos:** registre el importe recibido y las horas autorizadas de ese movimiento, con póliza opcional. Revise el saldo antes/después y confirme. Adjunte el comprobante desde su detalle.
5. **Casos:** con saldo disponible, el cliente abre una solicitud, indica categoría, prioridad y datos técnicos. Un administrativo toma el caso si quedó sin responsable.
6. **Atención:** registre descripción, canal y minutos, o use el cronómetro y confirme el tiempo antes de descontarlo. Para remoto puede proponer una sesión que confirme el cliente.
7. **Seguimiento:** consulte el detalle del historial, los adjuntos y la campana; responda y cierre el caso al resolverlo. Las notas internas son exclusivas de Administración.
8. **Control:** Administración consulta consumos en Reportes; Administración/Cobranza revisan pagos y pólizas en Reportes de cobranza. Una atención errónea se anula con motivo y devuelve sus minutos; la anulación de pagos tiene reglas independientes.

Una hora equivale exactamente a 60 minutos. El sistema rechaza una atención de 31 minutos si la institución sólo dispone de 30 en ese canal. No toma prestados minutos de la otra bolsa. Crear un caso no descuenta tiempo; se requiere saldo positivo para crearlo o reabrirlo. Los comentarios de usuarios no consumen saldo.

## Respaldo y restauración

**Programación automática:** está desactivada por defecto. Administración puede configurarla y activarla desde **Respaldos**; al desactivarla se cancelan las nuevas ejecuciones programadas y se conservan las copias. Una ejecución ya iniciada puede terminar para no dejar un archivo incompleto. Consulte [Respaldos automáticos](docs/RESPALDOS-AUTOMATICOS.md) para el destino, horario, retención y prueba de restauración.

La [guía paso a paso de respaldo y restauración](docs/RESPALDO-RESTAURACION.md) explica los parámetros, las rutas, la recuperación y el diagnóstico de errores.

Con PostgreSQL en ejecución:

```bash
./respaldar.sh
```

Se genera un archivo `backups/siabuc-FECHA.dump` y su verificación SHA-256. Copie ambos a una ubicación segura distinta del servidor. El respaldo contiene los datos del sistema y los hashes de contraseña; no incluye `.env` ni las imágenes Docker.

Para elegir el nombre y la ubicación del respaldo:

```bash
./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump
```

Este ejemplo crea `backups/mi-respaldo.dump` y `backups/mi-respaldo.dump.sha256`. No se sobrescribe una copia existente; use otro nombre para el siguiente respaldo. Las rutas relativas se interpretan desde la carpeta donde ejecuta el comando, y los nombres con espacios deben ir entre comillas.

Para restaurar una copia de esquema compatible y reemplazar los datos actuales, detenga antes cualquier sesión de desarrollo y cree un respaldo del estado actual. Para retroceder de versión, siga el procedimiento de [base nueva y vacía](docs/RESPALDO-RESTAURACION.md#retroceder-a-una-versión-anterior):

```bash
./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo
./iniciar.sh
```

Indique el archivo `.dump`, no su `.sha256`. También se conserva la forma anterior `./restaurar.sh backups/mi-respaldo.dump --confirmar-reemplazo` y el argumento directo para `respaldar.sh`. Use el argumento directo **o** `--nombre-respaldo`, nunca ambos. Puede consultar las opciones con `./respaldar.sh --help` y `./restaurar.sh --help`.

La restauración detiene Spring, Angular y Postfix de Docker, valida el SHA-256 si está presente y usa una transacción PostgreSQL. Tras restaurar, las cuentas y contraseñas son las de la copia recuperada. El respaldo no incluye la cola SMTP: revise ambas colas antes de reanudar los envíos. Más detalles en [Operación](docs/OPERACION.md).

## Documentación y pruebas

- [Manual de administradores y usuarios](docs/MANUAL-USUARIO.md): permisos, instituciones, minutos y casos.
- [Funciones Helpdesk 1.4](docs/HELPDESK-1.4.md): adjuntos, cronómetro, agenda y permisos.
- [Cronómetro y agenda](docs/CRONOMETRO-AGENDA.md): confirmación de minutos y sesiones con el cliente.
- [Clasificación, ficha técnica y SLA](docs/CLASIFICACION-SLA.md): prioridades y calendario laboral.
- [Avisos internos y saldos](docs/AVISOS-INTERNOS.md): campana, pendientes y umbrales.
- [Reportes](docs/REPORTES.md): filtros, Excel, PDF y zona horaria.
- [Actualizar a 1.4](docs/VERSION-1.4.md): pasos para Windows y Linux, migraciones y respaldo.
- [Pólizas y cobranza](docs/POLIZAS-COBRANZA.md): contratación, abonos, acreditación de horas y permisos BILLING/ADMIN.
- [Reportes de cobranza](docs/REPORTES-COBRANZA.md): ingresos, anulaciones, saldos de pólizas, Excel y PDF.
- [Comprobantes de pago](docs/COMPROBANTES-PAGO.md): carga, descarga, historial y permisos de archivos financieros.
- [Actualizar a 1.5](docs/VERSION-1.5.md): guía histórica de esa versión.
- [Instalar o actualizar a 1.6](docs/VERSION-1.6.md): recorrido comercial, portal y operación, conservando datos.
- [Cotizaciones y renovaciones](docs/COTIZACIONES-RENOVACIONES.md): propuesta, constancias, conversión a póliza y avisos de vencimiento.
- [Solicitudes y portal financiero](docs/SOLICITUDES-PAGO.md): habilitación institucional, comprobantes pendientes y aprobación.
- [Recibos de pago](docs/RECIBOS-PAGO.md): PDF, folios, datos preservados y anulaciones.
- [Base de conocimientos](docs/BASE-CONOCIMIENTOS.md): publicación, búsqueda y soluciones vinculadas a casos.
- [Encuestas de satisfacción](docs/ENCUESTAS-SATISFACCION.md): respuesta del cliente y reportes.
- [Respaldos automáticos](docs/RESPALDOS-AUTOMATICOS.md): activación opcional, pausa, calendario, retención y diagnóstico.
- [Operación y compilación](docs/OPERACION.md): instalación nativa, Docker, red, respaldo y resolución de problemas.
- [Instalación y scripts Windows](docs/WINDOWS.md): Docker Desktop, Python y equivalencias CMD/PowerShell.
- [Respaldo y restauración](docs/RESPALDO-RESTAURACION.md): `--nombre-respaldo`, ejemplos, recuperación paso a paso y alcance de las copias.
- [Arquitectura y modelo de datos](docs/ARQUITECTURA.md): integridad, concurrencia, sesiones y límites.
- [SMTP interno Postfix](docs/SMTP-INTERNO.md): configuración predeterminada, entrega directa/relay y diagnóstico de cola.
- [Correo y notificaciones](docs/CORREO.md): configuración SMTP, proveedores, cola y pruebas Mailpit.
- [Migración de 1.0 a 1.1](docs/MIGRACION-1.1.md): actualización conservando datos, cuentas y saldos.
- [Contrato de API](docs/API-CONTRACT.md): endpoints, estructuras y errores.
- [Pruebas de navegador](tests/e2e/README.md): instalación y ejecución del recorrido completo Angular → Spring → PostgreSQL en un entorno desechable.
- [Validación realizada](docs/VALIDACION.md): resultados efectivamente ejecutados y comprobaciones pendientes.

Las pruebas de integración contra PostgreSQL se ejecutan con `mvn -Ppostgres-it verify` y una **base exclusiva de pruebas, vacía al primer uso**, donde la suite crea su administrador `testadmin`; no forman parte del empaquetado normal con `mvn package`. La suite crea y modifica registros sin limpiar sus datos al finalizar: los datos de prueba persistirán. No use una base productiva ni una base inicializada con otro administrador. Consulte [Operación](docs/OPERACION.md) para preparar la base aislada. Para las pruebas de lógica del cliente, ejecute `npm test` dentro de `frontend`.

## Estructura

```text
siabuc-helpdesk/
  backend/                Spring, seguridad, API y migraciones Flyway
  frontend/               Angular, proxy de desarrollo y Nginx
  smtp/                   Dockerfile y configuración Postfix de salida
  backup/                 Servicio de copias PostgreSQL programadas, inicialmente apagadas
  docs/                   Manuales, contrato API y validación
  scripts/manage.py       Operación y lectura segura de configuración
  docker-compose.yml      PostgreSQL + Postfix + Spring + Nginx
  .env.example            Plantilla sin contraseñas utilizables
  configurar.sh / .bat    Generar / validar .env; --password-bd, --correo y --smtp-interno
  iniciar.sh / .bat       Compilar e iniciar con Docker
  iniciar-dev.sh          PostgreSQL/Postfix Docker + Spring/Angular locales
  detener.sh / .bat       Detener Docker conservando datos
  compilar.sh / .bat      Compilar imágenes; .sh también permite compilación local
  respaldar.sh / .bat     Crear copia PostgreSQL
  restaurar.sh / .bat     Recuperar copia PostgreSQL
```
