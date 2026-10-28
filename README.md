# SIABUC Helpdesk 1.8.5

**Actualización de seguridad:** Node.js 24.22.1, Nginx 1.30.6, herramientas Angular 21 corregidas, backend Spring actualizado, bases PostgreSQL/SMTP corregidas, auditorías npm e imágenes en CI y Dependabot. [Cambios, política de auditoría y validación](docs/VERSION-1.8.5.md).

**Revisión de arranque — permisos de respaldos:** `iniciar.sh` verifica escritura con la identidad del contenedor. En Linux, si recibe acceso denegado, ajusta el propietario de la carpeta configurada a `BACKUP_UID:BACKUP_GID`, asegura acceso del propietario y vuelve a comprobar. Conserva los archivos existentes y el estado del calendario. [Actualización y alcance](docs/VERSION-1.8.4.md).

Sistema de atención técnica con servidor **Spring Boot 4.0.9 / Java 17**, cliente **Angular 21.2.25** y base de datos **PostgreSQL 17**. Conserva instituciones, usuarios, casos, pagos, actualizaciones descriptivas y auditoría. El tiempo por correo y por escritorio remoto se administra en **dos bolsas independientes de minutos enteros**.

## Horas por licencia

Administración puede cambiar por separado las horas incluidas de **correo** y **escritorio remoto** desde **Configuración de soporte → Horas predeterminadas de las licencias**. Se aplican una sola vez al crear cada licencia; las existentes conservan su saldo. **Licencias** permite registrar el número institucional, agregar horas con motivo y consultar el historial. Los casos y las pólizas pueden vincularse a una licencia.

La versión 1.8.5 conserva las licencias de **V27** y la preparación de permisos de respaldos de 1.8.4. No añade nuevas migraciones sobre V27. Los saldos anteriores quedan sin licencia, sin reparto ni acreditación automática. La dotación inicial debe configurarse antes del primer alta. Consulte [Licencias y horas](docs/LICENCIAS-HORAS.md), incluido el cuidado para no duplicar horas ya registradas y la restricción de retroceso a un backend anterior.

## Estados de casos en 1.8.3

**Recibido → En atención → Requiere respuesta del cliente → Resuelto → Cerrado.**
Administración y técnicos pueden cambiar el estado con un motivo desde el detalle.
La respuesta del cliente en espera devuelve el caso a En atención. Resolver o cerrar
conserva el historial y exige reabrir antes de registrar más trabajo. Los cronómetros
se pausan al esperar, resolver o cerrar; los minutos se descuentan sólo al confirmar
una atención. Esperar al cliente no suspende el SLA; resolver registra la resolución
sin que el cierre posterior cambie su fecha.

Consulte [transiciones, migración y uso de 1.8.3](docs/VERSION-1.8.3.md).
Los identificadores `OPEN` y `CLOSED` existentes se conservan; `OPEN` se muestra
como Recibido. La migración V26 conserva casos, saldos y fechas, y amplía las
vistas guardadas antiguas de abiertos a todos los casos pendientes.

**Documentación vigente:** [manual integrado 1.8.5](docs/MANUAL-EXTENDIDO-LICENCIAS.md) · [índice completo](docs/README.md) · [actualización y cambios](docs/VERSION-1.8.5.md) · [resultados verificables](docs/VALIDACION.md) · [contrato API](docs/API-CONTRACT.md).

Docker usa una sola red predeterminada del proyecto, sin `smtp_net` ni subred SMTP fija. Las instalaciones nuevas tienen el correo desactivado y Gmail directo preseleccionado. Postfix sólo se inicia al elegir `SMTP_HOST=smtp`; conserva su cola en un volumen.

## Alcance institucional pendiente

El registro local de soporte por licencia no implementa validación o activación comercial de SIABUC9. Continúan pendientes las descargas oficiales de instaladores, Federación de Identidades UCOL, ingreso únicamente con número de póliza/correo, recuperación mediante clave de licencia, modificación verificada de correo de licencia, cambios a las reglas de atención sin saldo, publicación HTTPS institucional, Google Analytics ni importación automática de sistemas anteriores. Que el frontend pueda mostrar una ruta como `/poliza` no significa que exista ya una integración institucional o un sistema de autenticación distinto.

**Funciones heredadas de 1.7 preservadas:** rol de técnico, vistas guardadas y alertas SLA, plantillas de respuesta, incidencias generales, catálogo de paquetes y tarifas, calendario de parcialidades, conciliación bancaria CSV y estados de cuenta institucionales.

**Los respaldos automáticos están desactivados en instalaciones nuevas.** Al actualizar una instalación que ya los configuró, se conserva su estado. Sólo Administración puede activarlos expresamente, configurar su horario y retención o volver a desactivarlos. Los respaldos manuales y las copias existentes se conservan.

**Linux y Windows:** scripts `.sh` y `.bat` para configurar, compilar, iniciar, detener, respaldar y restaurar. Con Docker se requieren Python 3.10+ y Docker Compose; en Windows, Docker Desktop con contenedores Linux. Java, Maven y Node se ejecutan dentro de las imágenes.

| Necesito… | Guía |
| --- | --- |
| Instalar desde cero en Ubuntu | [Guía paso a paso para instalación nueva](docs/INSTALACION-DESDE-CERO.md) |
| Instalar o actualizar conservando mis datos | [Actualización 1.8.5](docs/VERSION-1.8.5.md) · [Windows](docs/WINDOWS.md) · [Operación Linux/Docker](docs/OPERACION.md) |
| Atender casos, usar adjuntos, cronómetro y agenda | [Manual integrado 1.8.5](docs/MANUAL-EXTENDIDO-LICENCIAS.md) · [Guía de uso](docs/MANUAL-USUARIO.md) |
| Registrar pólizas, pagos y horas adquiridas | [Pólizas y cobranza](docs/POLIZAS-COBRANZA.md) |
| Cotizar, renovar o validar pagos pendientes | [Cotizaciones y renovaciones](docs/COTIZACIONES-RENOVACIONES.md) · [Solicitudes de pago](docs/SOLICITUDES-PAGO.md) |
| Habilitar la consulta financiera del cliente | [Portal financiero](docs/PORTAL-FINANCIERO.md) |
| Adjuntar un recibo o una transferencia | [Comprobantes de pago](docs/COMPROBANTES-PAGO.md) |
| Consultar consumos o ingresos y exportarlos | [Reportes técnicos](docs/REPORTES.md) · [Reportes de cobranza](docs/REPORTES-COBRANZA.md) |
| Configurar o pausar los avisos por correo | [SMTP interno](docs/SMTP-INTERNO.md) · [Pausa de envío](docs/DESACTIVAR-CORREO.md) |
| Configurar copias programadas (apagadas por defecto) | [Respaldos automáticos](docs/RESPALDOS-AUTOMATICOS.md) |
| Respaldar, restaurar o conocer las pruebas | [Respaldo y restauración](docs/RESPALDO-RESTAURACION.md) · [Validación vigente e histórica](docs/VALIDACION.md) |

## Novedades incorporadas desde 1.7

| Función | Guía |
| --- | --- |
| Técnico de soporte con permisos limitados | [Rol técnico](docs/ROL-TECNICO.md) |
| Filtros personales y alertas antes del SLA | [Vistas y alertas](docs/VISTAS-ALERTAS.md) |
| Instrucciones reutilizables con revisión previa | [Plantillas](docs/PLANTILLAS-RESPUESTA.md) |
| Incidencias comunes e historiales privados | [Incidencias](docs/INCIDENCIAS.md) |
| Tarifas MXN con versiones preservadas | [Paquetes](docs/PAQUETES-TARIFAS.md) |
| Compromisos, vencimientos y asignación de abonos | [Parcialidades](docs/PARCIALIDADES.md) |
| Movimientos bancarios sin acreditación automática | [Conciliación CSV](docs/CONCILIACION-BANCARIA.md) |
| Documentos con corte y descarga autorizada | [Estados de cuenta](docs/ESTADOS-CUENTA.md) |

Crear paquetes, cotizar, programar parcialidades, importar o conciliar movimientos bancarios **no acredita minutos**. La acreditación conserva su confirmación explícita en Pagos o al aprobar una solicitud. Los estados de cuenta preservan cada emisión y explican la apertura de datos anteriores.

## Funciones incluidas

- Alta, edición y baja lógica de instituciones; contacto, notas y presupuestos separados para correo y remoto.
- Tiempo asignado, utilizado y restante, presentado en horas y minutos; todo cálculo persistente usa minutos.
- Usuarios con contraseña y roles `ADMIN` / `TECH` (Técnico) / `USER` / `BILLING` (Cobranza), vinculados a su institución cuando corresponde.
- Casos con cinco estados de atención, descripción inicial y cronología de atenciones, comentarios, cambios de estado y anulaciones.
- Registro de consumos por Administración y técnicos, validación de saldo y rechazo transaccional de operaciones que lo excedan.
- Comentarios públicos e internos: los internos sólo son visibles para Administración y técnicos de soporte.
- Portal del usuario para consultar saldo e historial de su institución, abrir casos y agregar comentarios públicos.
- Administración y técnicos pueden **Tomar caso** cuando está pendiente de resolver y sin responsable; la asignación queda en el historial y genera el aviso correspondiente. El listado permite filtrar casos sin responsable o propios.
- Detalle de cada elemento del historial de atención y de notificaciones, con **Volver al historial**; conserva filtros y posición del listado. Los avisos muestran su correo completo únicamente a administradores.
- Auditoría administrativa de cambios; anular una atención reintegra sus minutos sin borrar su historia.
- Inicio de sesión con cookies de sesión, protección CSRF y contraseñas BCrypt; cambio de contraseña desde la cuenta.
- Inicio de PostgreSQL, Spring y Angular con Docker Compose; Postfix opcional, puertos configurables, respaldo y restauración.
- Asignación de un responsable ADMIN/TECH por institución y caso; correo de contacto por cuenta.
- Registro de pagos y acreditación atómica de minutos, con referencias únicas y protección ante reintentos duplicados.
- Avisos SMTP al cliente y responsable por cambios de casos, incluido el cierre; avisos de pagos y anulaciones, cola persistente y reintentos.
- Cada aviso de caso identifica la institución cliente y el administrativo que atiende, incluye la descripción actual, el detalle de la actualización y el estado actual: **Recibido**, **En atención**, **Requiere respuesta del cliente**, **Resuelto** o **Cerrado**, e invita a consultar la plataforma Helpdesk.
- Activación y pausa persistente del envío desde **Correo y notificaciones**, exclusiva de administradores, sin reiniciar: los avisos anteriores y los generados durante la pausa quedan sin envío; al reactivar sólo se notifican eventos nuevos.
- Gmail SMTP directo configurable; Postfix es un servicio heredado opcional, con cola persistente.
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
| Técnico (`TECH`) | Atiende, toma, edita, cierra y reabre casos; registra consumos y notas internas, usa cronómetro, adjuntos y agenda propia. Sin administración de cuentas, finanzas, configuración ni anulación de consumos. |
| Cliente (`USER`) | Saldo, casos e historial público de su institución; abre solicitudes, comenta, adjunta archivos de soporte y confirma sesiones remotas. Sin notas internas. El acceso financiero propio requiere habilitación expresa de Administración. |
| Cobranza (`BILLING`) | Saldos institucionales, pólizas, pagos, acreditación de horas, comprobantes y reportes financieros. Sin acceso a expedientes técnicos, administración de cuentas o anulación de pagos. |

Los permisos se comprueban en el servidor. Los adjuntos de soporte respetan la institución y la visibilidad de la entrada; Administración/Cobranza consultan todos los comprobantes de pago; el portal financiero habilitado permite al cliente consultar sólo los de su institución. La campana sigue funcionando con el correo pausado y muestra a Cobranza sus avisos de saldo.

Para actualizar desde una versión anterior, respalde primero y conserve `.env`, `COMPOSE_PROJECT_NAME`, los volúmenes y las migraciones existentes. Siga la [guía de 1.8.5](docs/VERSION-1.8.5.md); las guías de [1.7](docs/VERSION-1.7.md), [migración 1.0/1.1](docs/MIGRACION-1.1.md) y [SMTP interno](docs/SMTP-INTERNO.md#actualizar-una-instalación-anterior) siguen disponibles para instalaciones históricas. No cambie una configuración SMTP que ya funciona sin necesidad comprobada.

## Actualizar una instalación existente a 1.8.5

La actualización conserva usuarios, casos, pólizas y registros **únicamente si se utiliza la misma base de datos**. Copiar `.env` por sí solo no copia PostgreSQL; tampoco incluye respaldos ni colas SMTP. Para actualizar desde una instalación previa:

1. **Antes de reemplazar nada**, cree y compruebe un respaldo con `./respaldar.sh`. Almacénelo fuera del servidor. Pruebe la restauración **en una instalación desechable** y no en la base en uso.
2. Descomprima el código 1.8.5 en **otra carpeta**. Copie allí su `.env` sin compartirlo ni subirlo al repositorio. Conserve los mismos identificadores `COMPOSE_PROJECT_NAME`, `DB_NAME`, `DB_USER` y parámetros efectivos de conexión; revise `BACKUP_DIRECTORY` si usa una ruta relativa.
3. Para detectar errores antes del arranque, ejecute `./compilar.sh --docker` en la carpeta nueva. Este paso **no aplica las migraciones** a la base ni implica que las pruebas de integración hayan pasado.
4. Después de validar en una **base de pruebas**, detenga la instalación anterior con `./detener.sh` y, desde la carpeta nueva, ejecute `./iniciar.sh`. Flyway aplicará las migraciones pendientes hasta **V27**. La entrega histórica 1.8.3 aplicaba hasta V26 y la ampliación de licencias añadió V27; 1.8.5 conserva estas migraciones. No modifique ni borre los scripts V1–V25. Los casos antiguos OPEN se muestran como Recibido; no se infiere su etapa de atención.
5. Verifique inicio de sesión, aislamiento de instituciones, saldos, casos, pólizas, cobranza, adjuntos, correo y respaldos. **Verifique en el destino SMTP, Docker, LAN y respaldos antes de actualizar producción** y completar la validación del entorno.

Después de V27, no ejecute un backend anterior sobre esa base: no respeta los saldos reservados para licencias; los anteriores a V26 tampoco reconocen los nuevos estados. Para retroceder restaure una copia compatible en una base nueva. No ejecute `docker compose down -v` ni destruya el volumen de PostgreSQL. La operación `./restaurar.sh` **reemplaza datos** y no forma parte de una actualización normal. Consulte [respaldo y restauración](docs/RESPALDO-RESTAURACION.md).

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

   El correo queda desactivado. Puede configurar Gmail directo con `./configurar.sh --gmail-directo`, otro proveedor con `./configurar.sh --correo` o Postfix opcional con `./configurar.sh --smtp-interno`. No necesita activar correo para iniciar y usar el sistema. Consulte [Correo](docs/CORREO.md).

4. Compile e inicie los servicios:

   ```bash
   ./iniciar.sh
   ```

5. Abra **http://127.0.0.1:4200** o el puerto `FRONTEND_PORT` que haya configurado. Acceda con la cuenta indicada en `.env`, cambie su contraseña en **Mi cuenta** y comience creando una institución y sus usuarios.

El primer inicio descarga dependencias. El script espera a PostgreSQL, Spring, Nginx y el servicio de respaldo. Postfix solo arranca con SMTP_HOST=smtp. El servicio de respaldo permanece esperando: su calendario empieza desactivado. Si algún puerto está ocupado, cambie su valor en `.env` y vuelva a iniciar.

Si omite `configurar.sh`, `iniciar.sh` genera `.env` automáticamente. Nunca publique ni comparta ese archivo. Los scripts resuelven su propia carpeta y pueden invocarse desde cualquier directorio.

### Comandos equivalentes en Linux y Windows

En PowerShell use el prefijo `.\`; en CMD puede omitirlo. El desarrollo con Spring/Angular fuera de Docker se mantiene en Linux/WSL2 mediante `iniciar-dev.sh`.

| Operación | Linux / Bash | Windows / PowerShell |
| --- | --- | --- |
| Crear o validar configuración | `./configurar.sh` | `.\configurar.bat` |
| Elegir contraseña de una base nueva | `./configurar.sh --password-bd` | `.\configurar.bat --password-bd` |
| Configurar Gmail directo | `./configurar.sh --gmail-directo` | `.\configurar.bat --gmail-directo` |
| Configurar Postfix interno | `./configurar.sh --smtp-interno` | `.\configurar.bat --smtp-interno` |
| Compilar e iniciar todo | `./iniciar.sh` | `.\iniciar.bat` |
| Compilar imágenes | `./compilar.sh --docker` | `.\compilar.bat --docker` |
| Detener conservando datos | `./detener.sh` | `.\detener.bat` |
| Respaldar con nombre elegido | `./respaldar.sh --nombre-respaldo backups/mi-respaldo.dump` | `.\respaldar.bat --nombre-respaldo backups/mi-respaldo.dump` |
| Restaurar una copia compatible | `./restaurar.sh --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo` | `.\restaurar.bat --nombre-respaldo backups/mi-respaldo.dump --confirmar-reemplazo` |

Restaurar reemplaza los datos actuales y deja la aplicación detenida. Para recuperar una versión anterior utilice una base nueva y vacía según la [guía de retroceso](docs/RESPALDO-RESTAURACION.md#retroceder-a-una-versión-anterior). La disponibilidad de scripts Windows no equivale a una ejecución nativa validada; consulte [Windows](docs/WINDOWS.md) y [Validación](docs/VALIDACION.md).

## Gmail directo y Postfix opcional

Spring Boot puede conectar directamente con Gmail/Google Workspace por SMTP (587/STARTTLS) y **Postfix ya no es una dependencia obligatoria**. En instalaciones nuevas el envío empieza desactivado; configure `./configurar.sh --gmail-directo` (o `.\configurar.bat --gmail-directo`) para introducir una nueva contraseña de aplicación en privado. El asistente no envía correo. Active solo tras revisar la cola heredada y confirmar la política de autenticación de Google Workspace.

Consulte [el ejemplo sin contraseñas](.env.gmail.example) y [la guía vigente de Gmail](docs/GMAIL-UCOL.md). Las instalaciones previas con `SMTP_HOST=smtp` mantienen Postfix automáticamente mediante el perfil opcional `local-smtp`. No se sobrescriben `.env`, volúmenes ni colas. Para usar Gmail directo en una instalación existente, respalde su `.env`, revise `docker compose --profile local-smtp exec smtp postqueue -p` y, una vez gestionados los mensajes pendientes, use el asistente. Si una contraseña de aplicación quedó expuesta, revóquela y genere otra.

[Guía de Gmail directo y migración](docs/GMAIL-UCOL.md). La integración con Gmail real necesita una prueba de envío autorizada en su instalación; la suite no autentica contra Google.

## Puertos y configuración

En Docker normal solamente se publica la web Angular/Nginx. Spring Boot, PostgreSQL y Postfix se comunican dentro de Docker sin puertos del host; /api/ se reenvía por frontend/nginx.conf.

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| WEB_BIND_HOST | 127.0.0.1 o BIND_HOST heredado | Dirección web. IP LAN o 0.0.0.0 para acceso remoto. |
| FRONTEND_PORT | 4200 | Único puerto publicado para usuarios. |
| BIND_HOST | 127.0.0.1 | Compatibilidad con .env antiguos: aplica solo a frontend. |
| DB_PORT / SERVER_PORT | 5432 / 8080 | Reservados para el modo de desarrollo nativo. |
| SMTP_INTERNAL_PORT | 2525 | Solo loopback mediante override de desarrollo. |
| COOKIE_SECURE | false | true al publicar mediante HTTPS. |

Para acceso web LAN, cambie únicamente WEB_BIND_HOST a la IP real del anfitrión en su .env privado; por ejemplo WEB_BIND_HOST=192.168.1.50. Tras aplicar la nueva configuración en una ventana de mantenimiento, acceda desde otro equipo autorizado a http://192.168.1.50:4200. Mantenga el firewall restringido y no abra puertos de Spring/PostgreSQL. WEB_BIND_HOST=0.0.0.0 permite escuchar en todas las interfaces, no implica publicación en Internet. Para HTTPS, utilice un proxy con certificado que reenvíe el sitio completo (incluido /api/) a frontend local y configure COOKIE_SECURE=true.

No es necesario editar docker-compose.yml ni cambiar el nombre del proyecto. Los .env anteriores conservan BIND_HOST, pero ya no pueden publicar db/backend desde el despliegue normal. El override docker-compose.dev.yml permite acceso a db y smtp exclusivamente desde localhost en desarrollo. Consulte [Operación](docs/OPERACION.md).

## Correo, responsables y pagos

Desde 1.8.2 una instalación nueva inicia con envío desactivado y Gmail directo preseleccionado; configure `./configurar.sh --gmail-directo` para activarlo. Las instalaciones heredadas pueden conservar Postfix en `smtp:25`. En ese modo, Postfix se inicia automáticamente y conserva su cola en un volumen. Desde 1.8.3 comparte la red predeterminada del proyecto: se elimina `smtp_net` y no se reserva una subred SMTP fija. El remitente usa la IP local entre corchetes: `soporte@[192.168.1.50]`, por ejemplo. El valor inicial `soporte@[127.0.0.1]` permite arrancar localmente; configure la IP LAN real del anfitrión y, si lo necesita, un relay autorizado:

```bash
./configurar.sh --smtp-interno
./iniciar.sh
```

El asistente solicita de forma oculta el secreto de un relay si corresponde; no envía un mensaje al guardar. Para conectar Spring directamente a un SMTP externo, use `./configurar.sh --correo`. `MAIL_ENABLED` habilita el transporte; la pausa administrativa permanece en la base de datos. Al reanudar desde la plataforma sólo se enviarán avisos de eventos nuevos: los anteriores suprimidos no se recuperan. Capture el correo de cada administrador en **Usuarios**, el correo cliente y responsable en **Instituciones** y revise la asignación de cada caso.

La dirección con IP es una identidad de envío; este servicio no crea un buzón para recibir respuestas o rebotes. Puede configurar `MAIL_REPLY_TO` con el correo de atención real. El servidor receptor debe aceptar remitentes con IP local; los proveedores externos pueden rechazarlos. Consulte la configuración del relay institucional si los avisos deben salir de la red local.

**Notificaciones** permite al administrador consultar la cola, enviar una prueba a su propia dirección y reintentar avisos fallidos u omitidos. Las notas internas sólo notifican al responsable. Consulte [SMTP interno](docs/SMTP-INTERNO.md) para entrega directa, restricciones del puerto 25, relay, cola y red; [Correo](docs/CORREO.md) detalla destinatarios y proveedores. En el helpdesk, SENT significa que el servidor SMTP configurado aceptó el aviso. No acredita entrega final ni lectura; con Postfix, la entrega remota puede continuar pendiente en su cola.

Gmail institucional `@ucol.mx` también puede configurarse directamente mediante `--gmail-directo`; como alternativa histórica, Gmail es una salida de Postfix: `./configurar-gmail.sh` en Linux o `.\configurar-gmail.bat` en PowerShell. Consulte [Gmail UCOL](docs/GMAIL-UCOL.md) para requisitos y diagnóstico. El registro de validación conserva la experiencia anterior con SMTP local y el fallo informado de Gmail; el CI actual no acredita entrega externa con Google.

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

Requiere JDK 17 o posterior, Maven 3.9, Node.js 24.22.1 LTS (ver `.nvmrc`), npm, Python 3.10+ y Docker para PostgreSQL; Postfix sólo si se eligió el modo interno.

```bash
./configurar.sh
./compilar.sh
./iniciar-dev.sh
```

`compilar.sh` ejecuta `mvn package`, `npm ci` y `npm run build`. Produce:

- `backend/target/siabuc-helpdesk-1.8.5.jar`
- `frontend/dist/helpdesk/browser/`

`iniciar-dev.sh` inicia PostgreSQL y, con `SMTP_HOST=smtp`, Postfix con Docker, compila Spring, instala las dependencias de Angular y ejecuta Spring y `ng serve` en una terminal. Lee los mismos puertos y contraseñas de `.env`; ambos procesos escuchan en `127.0.0.1`. Los registros se guardan en `.runtime/`. **Ctrl+C** detiene Spring y Angular; después `./detener.sh` detiene PostgreSQL y Postfix. No ejecute el modo Docker completo y el modo de desarrollo al mismo tiempo sobre los mismos puertos.

Para ejecutar todo sin Docker, conectar un PostgreSQL propio o servir los archivos compilados, consulte las instrucciones completas en [Operación](docs/OPERACION.md).

## Guía rápida de uso por rol

El [manual integrado 1.8.5](docs/MANUAL-EXTENDIDO-LICENCIAS.md) detalla procedimientos, pantallas, permisos y casos prácticos. Este resumen permite comenzar según la cuenta:

| Rol | Primeras acciones | Restricciones importantes |
| --- | --- | --- |
| **Cliente (`USER`)** | Revisar saldo; abrir «Casos de soporte»; indicar asunto, descripción y canal; **revisar y confirmar** para obtener el folio; consultar historial, comentarios públicos y ayuda. | Solo su institución. No puede leer notas internas. La consulta financiera propia está desactivada salvo habilitación administrativa. |
| **Técnico (`TECH`)** | Consultar casos pendientes, tomar los que no tienen responsable, registrar atención y minutos; gestionar su agenda, usar plantillas e intervenir en incidencias. | No administra usuarios, catálogos de plantillas, tarifas, pagos ni respaldos. |
| **Cobranza (`BILLING`)** | Registrar pólizas, revisar abonos y solicitudes de pago, acreditar minutos autorizados, consultar parcialidades y conciliaciones. | No accede al historial privado de soporte ni acredita horas sólo por importar un CSV. |
| **Administración (`ADMIN`)** | Crear instituciones y cuentas; designar responsables; supervisar soporte, finanzas, auditoría, correo y respaldo. | Debe verificar permisos de portal financiero, mantener respaldo y justificar anulaciones. |

**Ejemplo de ticket cliente:** asunto «Error al importar registros» (5–150 caracteres), descripción técnica con pasos reproducibles (20–5,000) y canal con saldo. La institución proviene de la **sesión**; el cliente no puede elegir una institución distinta enviando un identificador en la API. El folio se muestra tras guardar. Los archivos se agregan desde el caso y no se convierten en descargas oficiales de SIABUC.

## Flujo inicial recomendado

1. **Instituciones:** registre nombre, contacto, correo y responsable. Si va a acreditar horas mediante pagos, deje inicialmente las bolsas en cero para no duplicarlas con una asignación manual.
2. **Usuarios:** cree cuentas `USER` asociadas a la institución y una cuenta `BILLING` para Cobranza. Entregue las credenciales por un medio privado.
3. **Pólizas:** Cobranza registra el contrato, vigencia, importe MXN y horas por canal, cuando corresponda. Esta operación no incrementa el saldo.
4. **Pagos:** registre el importe recibido y las horas autorizadas de ese movimiento, con póliza opcional. Revise el saldo antes/después y confirme. Adjunte el comprobante desde su detalle.
5. **Casos:** con saldo disponible, el cliente abre una solicitud, indica categoría, prioridad y datos técnicos. Un administrativo toma el caso si quedó sin responsable.
6. **Atención:** registre descripción, canal y minutos, o use el cronómetro y confirme el tiempo antes de descontarlo. Para remoto puede proponer una sesión que confirme el cliente.
7. **Seguimiento:** consulte el detalle del historial, los adjuntos y la campana; cambie a En atención, Requiere respuesta del cliente o Resuelto según el avance, y cierre al concluir. Las notas internas son visibles para Administración y Técnicos.
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
```

Antes de reanudar todo con `./iniciar.sh`, siga la comprobación de recuperación de la [guía](docs/RESPALDO-RESTAURACION.md): inicie sólo web/backend/base con correo de arranque desactivado, revise la programación de respaldos restaurada y las colas, y después reactive los servicios necesarios.

Indique el archivo `.dump`, no su `.sha256`. También se conserva la forma anterior `./restaurar.sh backups/mi-respaldo.dump --confirmar-reemplazo` y el argumento directo para `respaldar.sh`. Use el argumento directo **o** `--nombre-respaldo`, nunca ambos. Puede consultar las opciones con `./respaldar.sh --help` y `./restaurar.sh --help`.

La restauración detiene Spring, Angular y Postfix de Docker, valida el SHA-256 si está presente y usa una transacción PostgreSQL. Tras restaurar, las cuentas y contraseñas son las de la copia recuperada. El respaldo no incluye la cola SMTP: revise ambas colas antes de reanudar los envíos. Más detalles en [Operación](docs/OPERACION.md).

## Documentación y pruebas

El [índice documental](docs/README.md) reúne todas las guías vigentes y el archivo histórico.

- **[Manual integrado SIABUC Helpdesk 1.8.5](docs/MANUAL-EXTENDIDO-LICENCIAS.md)**: procedimientos por rol, instalación, soporte, finanzas, correo, respaldos y diagnóstico.
- [Guía de uso](docs/MANUAL-USUARIO.md) y [cambios/migración 1.8.3](docs/VERSION-1.8.3.md).
- [Operación](docs/OPERACION.md), [Windows](docs/WINDOWS.md) y [respaldo/restauración](docs/RESPALDO-RESTAURACION.md).
- [Contrato API](docs/API-CONTRACT.md), [arquitectura](docs/ARQUITECTURA.md) y [pruebas de navegador](tests/e2e/README.md).
- [Validación vigente e histórica](docs/VALIDACION.md): resultados con revisión exacta, entorno y límites.

**Evidencia histórica preservada:** la primera ejecución documentada de 1.8.1 registró 223/225 pruebas Java satisfactorias y dos fallos. El [informe original](docs/VALIDACION-1.8.1.md) conserva ese resultado; no describe el estado de la suite 1.8.3. Las notas y el manual 1.8.1 permanecen como archivos de esa versión.

Las pruebas de integración contra PostgreSQL se ejecutan con `mvn -Ppostgres-it verify` y una **base exclusiva de pruebas, vacía al primer uso**, donde la suite crea su administrador `testadmin`; no forman parte del empaquetado normal con `mvn package`. La suite crea y modifica registros que persistirán al finalizar. No use una base productiva ni una base con otros administradores. Consulte [Operación](docs/OPERACION.md) para preparar el entorno aislado. Para la lógica del cliente, ejecute `npm test` dentro de `frontend`. Consulte los resultados asociados al SHA en [Validación](docs/VALIDACION.md); compilar no equivale a ejecutar la suite de integración.

## Generar el paquete de fuentes

Desde una copia clonada de Git con los cambios versionados:

```bash
python3 scripts/release-1.8.5.py --package --output release-output
```

Genera `SIABUC-Helpdesk-1.8.5.zip` y su `.zip.sha256`; comprueba CRC y contenido e incluye un manifiesto por archivo. El ZIP no contiene credenciales ni datos de la instalación y requiere compilar para ejecutarlo. Este empaquetador usa `git ls-files`: para regenerar el paquete desde una entrega ZIP, obtenga primero un checkout del repositorio; el ZIP no incluye `.git`.

## Estructura

```text
siabuc-helpdesk/
  backend/                Spring, seguridad, API y migraciones Flyway
  frontend/               Angular, proxy de desarrollo y Nginx
  smtp/                   Dockerfile y configuración Postfix de salida
  backup/                 Servicio de copias PostgreSQL programadas, inicialmente apagadas
  docs/                   Manual integrado 1.8.5, índice, guías, API y validación
  scripts/manage.py       Operación y lectura segura de configuración
  docker-compose.yml      PostgreSQL + Spring + Nginx + backup; Postfix opcional
  .env.example            Plantilla sin contraseñas utilizables
  configurar.sh / .bat    Generar / validar .env; --password-bd, --gmail-directo, --correo y --smtp-interno
  iniciar.sh / .bat       Compilar e iniciar con Docker
  iniciar-dev.sh          PostgreSQL y Postfix opcional en Docker + Spring/Angular locales
  detener.sh / .bat       Detener Docker conservando datos
  compilar.sh / .bat      Compilar imágenes; .sh también permite compilación local
  respaldar.sh / .bat     Crear copia PostgreSQL
  restaurar.sh / .bat     Recuperar copia PostgreSQL
  scripts/release-1.8.5.py Empaquetar fuentes 1.8.5 con manifiesto SHA-256
```
