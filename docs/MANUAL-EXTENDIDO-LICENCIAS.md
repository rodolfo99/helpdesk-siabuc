# Manual extendido de SIABUC Helpdesk con licencias

**Cliente institucional · Soporte técnico · Cobranza · Administración · Operación**
Edición documental: 16 de octubre de 2026. Ampliación de licencias y horas de soporte.
Base de aplicación: **1.8.3**, con la ampliación de licencias y migraciones **V1-V27**. Esta edición describe la rama `feature/licencias-horas-predeterminadas` y la [PR #19](https://github.com/rodolfo99/helpdesk-siabuc/pull/19); no implica una nueva versión publicada ni un despliegue en producción.

Este manual integra el manual 1.8.3 y la ampliación de licencias. Para configurar las horas incluidas, empieza en el apartado 5.5; para adquirir horas mediante una póliza, consulta el capítulo 15. Describe la interfaz, las reglas de negocio y los scripts incluidos en el proyecto. La referencia de resultados, entorno y límites de las pruebas es [Validación vigente](VALIDACION.md). Los informes y manuales de versiones anteriores son antecedentes históricos, no instrucciones de esta versión.

## Índice

- [1. Cómo utilizar este manual](#1-cómo-utilizar-este-manual)
- [2. Roles, permisos y navegación](#2-roles-permisos-y-navegación)
- [3. Instalación, actualización y acceso por red](#3-instalación-actualización-y-acceso-por-red)
- [4. Inicio de sesión y cuenta personal](#4-inicio-de-sesión-y-cuenta-personal)
- [5. Instituciones, usuarios y licencias — ADMIN](#5-instituciones-usuarios-y-licencias--admin)
- [6. Recorrido del cliente: crear y seguir una solicitud](#6-recorrido-del-cliente-crear-y-seguir-una-solicitud)
- [7. Atención de casos — ADMIN y TECH](#7-atención-de-casos--admin-y-tech)
- [8. Adjuntos y evidencias de soporte](#8-adjuntos-y-evidencias-de-soporte)
- [9. Ficha técnica, prioridades y plazos de atención](#9-ficha-técnica-prioridades-y-plazos-de-atención)
- [10. Cronómetro y agenda remota](#10-cronómetro-y-agenda-remota)
- [11. Campana, respuestas pendientes, vistas y alertas](#11-campana-respuestas-pendientes-vistas-y-alertas)
- [12. Plantillas e incidencias generales](#12-plantillas-e-incidencias-generales)
- [13. Base de conocimientos y centro de ayuda](#13-base-de-conocimientos-y-centro-de-ayuda)
- [14. Paquetes, cotizaciones y renovaciones](#14-paquetes-cotizaciones-y-renovaciones)
- [15. Pólizas y pagos](#15-pólizas-y-pagos)
- [16. Solicitudes de pago, comprobantes y recibos](#16-solicitudes-de-pago-comprobantes-y-recibos)
- [17. Portal financiero de la institución](#17-portal-financiero-de-la-institución)
- [18. Calendario de parcialidades](#18-calendario-de-parcialidades)
- [19. Conciliación bancaria por CSV](#19-conciliación-bancaria-por-csv)
- [20. Reportes y estados de cuenta](#20-reportes-y-estados-de-cuenta)
- [21. Satisfacción y auditoría](#21-satisfacción-y-auditoría)
- [22. Correo y notificaciones — ADMIN / operador](#22-correo-y-notificaciones--admin--operador)
- [23. Respaldos y restauración](#23-respaldos-y-restauración)
- [24. Rutinas recomendadas y caso práctico](#24-rutinas-recomendadas-y-caso-práctico)
- [25. Solución de problemas y límites conocidos](#25-solución-de-problemas-y-límites-conocidos)
- [26. Referencia rápida para el operador](#26-referencia-rápida-para-el-operador)
- [27. Fuentes, trazabilidad y control documental](#27-fuentes-trazabilidad-y-control-documental)

**Consultas frecuentes:** [estados y transición](#75-cambiar-el-estado-con-motivo) · [actualización V27](#33-actualizar-conservando-datos-y-aplicar-v27) · [Gmail directo](#222-configurar-gmail-directo-otro-smtp-o-postfix) · [restauración](#235-restaurar-operación-que-reemplaza-datos).

## 1. Cómo utilizar este manual

### 1.1 Objetivo y alcance

SIABUC Helpdesk centraliza solicitudes de soporte, instituciones, cuentas, tiempo de atención, pólizas y registros de cobranza. Permite seguir un caso desde su recepción hasta su cierre y conservar evidencia de atenciones, comentarios, pagos y correcciones. «Póliza» significa contrato de soporte; no es una clave de licencia SIABUC9.

Las instrucciones se contrastaron con las pantallas Angular, los permisos y validaciones del backend y los scripts de esta versión. Los ejemplos de instituciones, importes, folios, referencias e IP son ficticios; sustitúyelos por los autorizados para tu instalación. Las cantidades no constituyen tarifas comerciales oficiales.

Cada apartado indica quién puede realizarlo y el resultado esperado. Si no ves una opción, revisa primero el rol, la institución y el estado del registro. Una operación autorizada por API no implica que exista una pantalla completa para ejecutarla; las diferencias conocidas se explican expresamente.

### 1.2 Ruta de lectura según tu actividad

| Actividad | Secciones recomendadas |
|---|---|
| Cliente que necesita soporte | 2, 4, 6, 8, 10–11, 13, 17 y 21 |
| Técnico de soporte | 2 y 7–13; rutinas 24 y diagnóstico 25 |
| Cobranza | 2, 14–20 y 24–25 |
| Administración de cuentas y configuración | 3–5, 9–13, 17 y 21–26 |
| Instalación, actualización y mantenimiento | 1, 3, 22–23 y 25–27 |

En este documento, **caso activo** significa Recibido, En atención o Requiere respuesta del cliente. **Estado final** significa Resuelto o Cerrado. Las entidades financieras, la agenda y las incidencias tienen estados propios que no deben confundirse con los del caso.

### 1.3 Funciones y cambios de la versión

La ampliación V27 agrega licencias vinculadas a instituciones, dotaciones iniciales configurables por canal, asignaciones adicionales e historial de movimientos. Los casos y las pólizas pueden vincularse a una licencia; el consumo y los pagos respetan su saldo. Consulta los apartados 5.5 a 5.11.

La versión 1.8.3 incorpora cinco estados de atención, filtros individuales y el grupo **Pendientes de resolver**. Tomar un caso recibido lo mueve a En atención; un comentario del cliente en un caso que requiere respuesta también lo devuelve a En atención. Los cambios manuales requieren motivo y quedan en historial y auditoría.

Los cronómetros se pausan al esperar al cliente, resolver o cerrar, conservando el tiempo medido. Los minutos se cobran sólo al confirmar una atención. Esperar al cliente **no pausa el SLA**. Resuelto registra la resolución y Cerrado permite posteriormente la encuesta de satisfacción.

Docker publica sólo el frontend en el despliegue normal. Gmail directo conserva la configuración incorporada en 1.8.2 y Postfix es opcional. Se eliminó la red dedicada `smtp_net` y la subred fija: los servicios usan la red `default` del proyecto. Una instalación nueva preselecciona Gmail con **envío desactivado** y sin contraseña.

Se conservan los módulos de soporte, conocimiento, agenda, avisos, cobranza, portal financiero, reportes y respaldos. El acceso continúa con usuario y contraseña y el ámbito del cliente es su institución.

### 1.4 Evidencia y uso de ejemplos

Consulta [VALIDACION.md](VALIDACION.md) para conocer qué pruebas se ejecutaron, sobre qué revisión y qué entornos se comprobaron. Una compilación, un ensayo de interfaz con API simulada y una prueba sobre PostgreSQL son evidencias diferentes. Las pruebas de SMTP local no prueban entrega a Gmail ni recepción final de un correo externo.

Los recorridos de capacitación de este manual son instrucciones y resultados esperados, no resultados de una ejecución productiva. Antes de actualizar una instalación con datos, conserva un respaldo y ensaya la recuperación en un destino separado. No ejecutes pruebas automatizadas del proyecto contra tu base de trabajo.

La [guía de versión 1.8.3](VERSION-1.8.3.md) reúne las reglas de transición y la compatibilidad; este manual las desarrolla con los recorridos cotidianos.

## 2. Roles, permisos y navegación

### 2.1 Los cuatro tipos de cuenta

**ADMIN — Administración.** Gestiona instituciones y cuentas, supervisa soporte y cobranza, consulta auditoría, configura correo, horarios, alertas, acceso financiero y respaldos. Es el único rol que puede anular consumos o pagos; TECH registra consumo y BILLING registra pagos dentro de sus respectivos ámbitos.

**TECH — Técnico de soporte.** Trabaja con casos de todas las instituciones: consulta datos técnicos, toma casos libres, registra atención, usa cronómetro y agenda y cierra o reabre. No administra cuentas, dinero, respaldos ni configuración global. Puede atender un caso asignado a otra persona, pero no cambiarle el responsable.

**BILLING — Cobranza.** Opera pólizas, pagos, cotizaciones, renovaciones, solicitudes de pago, paquetes, parcialidades, conciliación, reportes financieros y estados de cuenta. No obtiene acceso a expedientes de soporte ni notas técnicas internas por tener facultades financieras.

**USER — Cliente institucional.** Consulta saldos e historial de su institución, abre solicitudes y agrega comentarios públicos. Las funciones financieras sólo están disponibles cuando Administración habilita expresamente el portal de su institución.

### 2.2 Matriz de permisos operativos

| Función | ADMIN | TECH | BILLING | USER |
|---|---|---|---|---|
| Consultar saldos institucionales y licencias | Todas | Todas | Todas | Su institución |
| Configurar horas incluidas y crear licencias | Sí | No | No | No |
| Agregar horas manuales e historial de licencia | Sí | No | No | No |
| Crear/editar instituciones y cuentas | Sí | No | No | No |
| Consultar casos y ficha técnica | Todos | Todos | No | Su institución |
| Abrir caso con saldo | Sí | Sí, propio responsable | No | Su institución |
| Agregar comentario público | Sí | Sí | No | Su institución |
| Notas internas y consumo de minutos | Sí | Sí | No | No |
| Tomar caso sin responsable | Sí | Sí | No | No |
| Reasignar caso | Sí | No | No | No |
| Cambiar entre los cinco estados, resolver, cerrar y reabrir | Sí | Sí | No | No |
| Anular consumo | Sí | No | No | No |
| Operación financiera administrativa | Sí | No | Sí | No |
| Anular pago | Sí | No | No | No |
| Habilitar finanzas al cliente | Sí | No | No | No |
| Consulta financiera propia | Supervisión | No | Gestión | Si se habilita |
| Crear/publicar artículos | Sí | No | No | No |
| Vincular soluciones publicadas al caso | Sí | Sí | No | No |
| Incidencias y plantillas de respuesta | Gestiona ambas | Opera incidencias; consulta y usa plantillas activas | No | Sólo avisos públicos en sus casos |
| Reportes técnicos Excel/PDF | Sí | No | No | No |
| Proponer y gestionar agenda remota | Sí | Sus sesiones | No | Confirma/rechaza las de su institución |
| Responder satisfacción de un caso cerrado | No | No | No | Una respuesta institucional |
| Auditoría, configuración y respaldos | Sí | No | No | No |
| Cambiar contraseña propia | Sí | Sí | Sí | Sí |

**El aislamiento es institucional.** Dos clientes de la misma institución comparten sus casos, saldos y, cuando está habilitado, el portal financiero. No hay privacidad individual entre esos usuarios. Una cuenta USER no puede cambiar su institución mediante el formulario de una solicitud.

La asignación de responsable organiza el trabajo, pero no convierte el caso en exclusivo de ese técnico. ADMIN y TECH tienen alcance técnico global. Los permisos de consulta de una institución no implican autorización para realizar operaciones financieras.

### 2.3 Mapa de la interfaz

El menú lateral cambia según el rol. Las opciones disponibles incluyen **Resumen** —**Resumen financiero** para BILLING—, **Casos de soporte**, **Base de conocimientos**, **Finanzas institucionales**, **Pagos**, **Pólizas**, **Reportes de cobranza**, **Cotizaciones**, **Validar pagos**, **Renovaciones**, **Incidencias generales**, **Plantillas**, **Paquetes y tarifas**, **Parcialidades**, **Conciliación bancaria**, **Estados de cuenta**, **Satisfacción**, **Respaldos**, **Instituciones**, **Licencias**, **Usuarios**, **Auditoría**, **Notificaciones** y **Configuración de soporte**.

En **Configuración de soporte**, ADMIN encuentra el horario/SLA, avisos de vencimiento, avisos de saldo, el acceso financiero de clientes y las horas predeterminadas de las licencias para correo y escritorio remoto. El perfil de la cuenta abre **Mi cuenta**. La campana del encabezado es una bandeja de avisos distinta del apartado administrativo de correo.

En pantallas pequeñas, abre la navegación con el botón de menú. Algunas tablas amplias conservan desplazamiento horizontal interno; no debe suponerse que todos sus campos caben a la vez en un teléfono. La navegación del paquete se realiza principalmente mediante botones de la aplicación: no se documentan enlaces directos independientes para cada sección.

Base documental: [Manual breve](MANUAL-USUARIO.md), [Rol técnico](ROL-TECNICO.md), [Portal financiero](PORTAL-FINANCIERO.md).

## 3. Instalación, actualización y acceso por red

### 3.1 Requisitos y elección del modo

| Modalidad | Herramientas del anfitrión | Qué se ejecuta |
|---|---|---|
| Docker en Linux | Python 3.10+, Docker Engine y complemento Compose | PostgreSQL 17, Spring, Nginx/Angular, worker de copias y Postfix si se selecciona |
| Docker en Windows | Python 3.10+, Docker Desktop con contenedores Linux | Los mismos servicios Linux, mediante lanzadores `.bat` |
| Desarrollo Linux/WSL2 | JDK 17+, Maven 3.9, Node.js 24 LTS, npm, Python y Docker Compose | Java y Angular locales; PostgreSQL y SMTP opcional en Docker |

El despliegue Docker descarga las herramientas de compilación dentro de las imágenes; no requiere Java, Maven o Node instalados en el anfitrión. La primera compilación necesita acceso a los registros de imágenes, Maven y npm. Las fuentes tipográficas utilizadas para PDF están incluidas en el proyecto.

El ZIP distribuye fuentes, scripts y documentación. No incluye una base de producción, contraseñas privadas, imágenes Docker ya construidas ni un instalador que omita las compilaciones. No mezcles archivos sueltos de paquetes distintos.

### 3.2 Instalación nueva en Linux con Docker

Descomprime el paquete completo y abre una terminal en la carpeta que contiene `VERSION`, `docker-compose.yml` e `iniciar.sh`:

```bash
python3 --version
docker info
docker compose version
cat VERSION
bash ./configurar.sh
```

Comprueba que `VERSION` indique `1.8.3`. El asistente crea o valida `.env`. Revisa con un editor privado la cuenta inicial, base de datos, puertos, dirección de la web y carpeta de respaldos. Una instalación nueva queda con correo desactivado; configura el medio de envío por separado cuando estés preparado.

Para elegir la contraseña PostgreSQL **antes de inicializar una base nueva**:

```bash
bash ./configurar.sh --password-bd
```

El asistente cambia el valor de configuración; no cambia la contraseña de un rol PostgreSQL ya creado. No uses esta opción como reparación de una instalación existente sin seguir el procedimiento de [Operación](OPERACION.md).

```bash
bash ./compilar.sh --docker
bash ./iniciar.sh
```

La compilación construye imágenes sin iniciar los servicios de negocio ni aplicar migraciones a una base. El inicio arranca y espera los servicios, aplica las migraciones mediante Spring y sirve el frontend. Abre `http://127.0.0.1:4200`, o el puerto elegido en `FRONTEND_PORT`.

Los servicios habituales son `db`, `backend`, `frontend` y `backup`. `smtp` se agrega cuando `.env` contiene `SMTP_HOST=smtp`; el script activa el perfil `local-smtp`. El worker `backup` iniciado no implica que esté activado su calendario.

### 3.3 Actualizar conservando datos y aplicar V27

La continuidad en el mismo motor Docker depende de conservar los volúmenes y `COMPOSE_PROJECT_NAME`. `.env` no contiene los datos de la base. Una ruta relativa de respaldos cambia de destino al mover la carpeta: revisa `BACKUP_DIRECTORY` antes del arranque.

1. Conserva el paquete y la configuración anteriores. Comprueba el alcance y las pruebas de 1.8.3.
2. Con la instalación anterior operativa, crea una copia con nombre nuevo y conserva el `.dump` y su `.sha256` fuera del directorio de código.
3. Ensaya la restauración y la actualización sobre una copia aislada, sin enviar correos a destinatarios reales.
4. Descomprime el paquete 1.8.3 con la ampliación de licencias en otra carpeta y copia allí el `.env` privado. Mantén nombre de proyecto, credenciales, puertos y volúmenes. Revisa rutas de respaldos y cualquier configuración local.
5. Compila con `bash ./compilar.sh --docker`.
6. En una ventana sin escrituras, detén la instalación anterior con `bash ./detener.sh`. Si existen procesos nativos, detenlos también.
7. Inicia desde la carpeta nueva con `bash ./iniciar.sh`. Revisa logs del backend y comprueba acceso, institución, saldo, caso, pago y evidencia conocidos.
8. Revisa las vistas guardadas, los plazos, la cola de correo y el destino de respaldos antes de reanudar la atención habitual.

Ejemplo de copia previa:

```bash
bash ./respaldar.sh --nombre-respaldo backups/antes-licencias-20261016.dump
```

Flyway aplica las migraciones faltantes hasta **V27**. V27 crea el control de licencias y los vínculos opcionales con casos y pólizas; no reparte saldos previos ni acredita horas al migrar. Antes de crear la primera licencia, ADMIN debe guardar los valores predeterminados de correo y remoto. Las instituciones y movimientos anteriores quedan sin licencia.

 V26 amplía el campo y la restricción de estados sin borrar los casos anteriores. Mantiene folios, responsables, atenciones, fechas y saldos. Los antiguos `OPEN` se muestran como **Recibido**: el sistema no deduce retrospectivamente cuáles ya estaban en atención o esperando al cliente. El equipo debe clasificarlos según su situación real.

Las vistas antiguas con filtro `OPEN` se convierten al grupo **Pendientes de resolver**, que incluye los tres estados activos. Las migraciones V1–V25 conservan su contenido original; no se editan para una actualización.

Tras aplicar V27, no ejecutes un backend anterior sobre esa base: no respeta la reserva de saldo de cada licencia. Además, las versiones anteriores a V26 no reconocen los estados nuevos. Para volver a la versión anterior, restaura el respaldo previo en una base nueva compatible y utiliza su código y configuración correspondientes. Copiar un JAR anterior no revierte el esquema ni los movimientos posteriores. No uses `docker compose down -v` para actualizar o resolver una compilación.

### 3.4 Publicar Angular en la LAN

En el despliegue normal sólo el frontend tiene puerto publicado. PostgreSQL se comunica como `db:5432`, Spring como `backend:8080` y el Postfix opcional como `smtp:25` dentro de Docker. Nginx reenvía `/api/` a Spring.

Identifica la IPv4 del anfitrión y ajusta el archivo privado `.env`; no es necesario modificar YAML para la publicación habitual:

```bash
ip -br -4 address
```

```dotenv
WEB_BIND_HOST=192.168.1.50
FRONTEND_PORT=4200
COOKIE_SECURE=false
```

La dirección es ficticia: usa la real del servidor. Aplica mediante `bash ./iniciar.sh` y abre `http://192.168.1.50:4200` desde otro equipo autorizado. En ese equipo, `127.0.0.1` corresponde a sí mismo, no al servidor.

`WEB_BIND_HOST=0.0.0.0` escucha en todas las interfaces; limita la conectividad a la red autorizada. `BIND_HOST` se conserva como alias sólo para el frontend cuando no se define `WEB_BIND_HOST`. `SERVER_PORT` y `DB_PORT` se usan en desarrollo/mantenimiento; no publican esos servicios en el Compose normal.

Para HTTPS, coloca un proxy TLS con certificado válido delante del frontend, conserva el reenvío de `/api/` y usa `COOKIE_SECURE=true` cuando los clientes accedan por HTTPS. Esa variable no genera certificados. La publicación web no constituye por sí sola configuración de HTTPS o acceso por Internet.

### 3.5 Comprobar y detener el servicio

Desde la carpeta correcta:

```bash
docker compose ps
docker compose logs --tail=100 backend frontend
```

Comprueba una sesión y una consulta autorizada; servir la pantalla inicial no demuestra que la base y las migraciones estén listas. Para detener conservando datos:

```bash
bash ./detener.sh
```

Este comando detiene y retira contenedores del proyecto y conserva los volúmenes. No borres volúmenes para diagnosticar fallos. El desarrollo nativo se detiene primero con Ctrl+C en su terminal.

### 3.6 Instalación en Windows

Usa Docker Desktop en modo de **contenedores Linux** y Python 3.10+ disponible mediante `py` o `python`. Descomprime fuera de la vista del ZIP, por ejemplo en `C:\SIABUC\helpdesk`. Desde PowerShell:

```powershell
cd "C:\SIABUC\helpdesk"
docker info --format '{{.OSType}}'
docker compose version
.\configurar.bat
notepad .env
.\compilar.bat
.\iniciar.bat
```

El primer comando Docker debe responder `linux`. Mantén `.env` en UTF-8 sin BOM y evita guardarlo como `.env.txt`. Abre localmente `http://127.0.0.1:4200`. Para LAN configura `WEB_BIND_HOST` y permite sólo el puerto web en el firewall del equipo.

Los scripts equivalentes son `detener.bat`, `respaldar.bat` y `restaurar.bat`. En CMD se invocan sin el prefijo `.\`. Al llamarlos desde otro batch, utiliza `call`. Para Gmail directo:

```powershell
.\configurar.bat --gmail-directo
```

Los permisos POSIX de Linux no equivalen a las ACL de Windows; protege la carpeta del proyecto, `.env` y copias mediante las propiedades de seguridad del sistema. El desarrollo Java/Angular fuera de contenedores se proporciona para Linux/WSL2, no con un lanzador nativo de Windows. Consulta [Windows](WINDOWS.md) y la evidencia de entornos en [Validación](VALIDACION.md).

### 3.7 Compilación y desarrollo nativos en Linux o WSL2

Comprueba Java, Maven, Node y npm antes de empezar. Desde la raíz:

```bash
java -version
mvn -version
node --version
npm --version
bash ./compilar.sh
```

La salida esperada se encuentra en `backend/target/siabuc-helpdesk-1.8.3.jar` y `frontend/dist/helpdesk/browser/`. `mvn package` no ejecuta automáticamente las pruebas de integración del perfil `postgres-it`.

Para iniciar el entorno de desarrollo:

```bash
bash ./configurar.sh
bash ./iniciar-dev.sh
```

El script usa PostgreSQL Docker, compila Java e instala dependencias del frontend, mantiene la terminal ocupada y arranca Java/Angular locales. Si se usa Postfix, adapta `smtp:25` a loopback y `SMTP_INTERNAL_PORT` para Java local. El override `docker-compose.dev.yml` publica PostgreSQL y SMTP sólo en `127.0.0.1`. Los puertos locales deben estar libres.

Angular usa un proxy hacia `SERVER_PORT`; accede por el origen del frontend. Los registros se guardan en `.runtime/backend.log` y `.runtime/frontend.log`. Ctrl+C termina los procesos; los cambios Java requieren reiniciar para recompilar. Después usa `detener.sh` para los servicios Docker.

`bash ./iniciar.sh --solo-bd` sirve para mantenimiento y publica sólo PostgreSQL en loopback. Para una base completamente externa a Docker se necesita configurar y operar PostgreSQL por separado; `respaldar.sh` y `restaurar.sh` están diseñados para el servicio Docker `db`.

### 3.8 Red SMTP y transición de instalaciones anteriores

1.8.3 ya no declara `smtp_net` ni IPAM fijo. Postfix opcional y backend comparten `default`; Postfix determina los clientes de confianza desde la subred real del contenedor. No necesitas escoger una subred SMTP para una instalación nueva.

Si un `.env` anterior contiene `SMTP_NETWORK_SUBNET`, el parser acepta esa clave y la ignora. No se utiliza para crear redes ni obliga a editar la configuración para arrancar. Una red vieja puede quedar sin contenedores después de recrearlos. Comprueba antes su nombre exacto y sus conexiones; el procedimiento de limpieza está en [SMTP interno](SMTP-INTERNO.md#acceso-restringido-y-red). No uses una limpieza global de redes ni elimines una red conectada para actualizar.

Si cambias de Postfix a Gmail, revisa primero los mensajes de la cola anterior. El volumen `smtp_queue` no forma parte del respaldo PostgreSQL y no debe borrarse como parte del cambio de configuración.

## 4. Inicio de sesión y cuenta personal

### 4.1 Acceder

Abre la dirección que te proporcione el administrador, introduce **Usuario** y **Contraseña** y pulsa el botón de entrada. Las cuentas posteriores al administrador inicial se crean desde **Usuarios**. No hay acceso de invitado ni un mecanismo operativo que use sólo número de póliza y correo.

En una instalación nueva, la cuenta de arranque se obtiene de `ADMIN_USERNAME` y `ADMIN_PASSWORD`. Después de crearla, cambiar esos valores en `.env` no sustituye la administración de contraseñas de las cuentas existentes. Un usuario de prueba incluido en código de tests no es una credencial para la instalación normal.

### 4.2 Contraseña y sesión

Desde tu perfil abre **Mi cuenta**, introduce la contraseña actual y la nueva y confirma. La contraseña debe tener al menos 12 caracteres y no superar 72 bytes UTF-8. Acentos y emojis pueden ocupar varios bytes. El cambio propio conserva esa sesión y revoca las otras; un restablecimiento realizado por ADMIN revoca las sesiones anteriores de la cuenta.

El servidor configura una expiración de 30 minutos sin actividad de sesión. No la interpretes como 30 minutos exactos sin tocar el teclado: las solicitudes automáticas del navegador pueden mantener actividad. Usa **Cerrar sesión** al terminar, especialmente en equipos compartidos. Reiniciar Spring también invalida las sesiones almacenadas en memoria.

Si olvidaste la contraseña, solicita un restablecimiento a ADMIN. No existe una recuperación pública por correo o clave de licencia en esta entrega. Si la cuenta o su institución se desactivaron, la sesión anterior no conserva acceso.

### 4.3 Evitar pérdidas del trabajo no guardado

Revisa la institución y el folio antes de escribir. Conserva temporalmente un borrador importante en un medio institucional seguro antes de cambiar de sección o refrescar. Si el servidor devuelve un conflicto por otra edición, recarga el registro, compara los cambios y vuelve a guardar sólo lo necesario. No repitas a ciegas operaciones de dinero o consumo tras una interrupción: consulta primero si quedaron registradas.

Base documental: [Manual breve](MANUAL-USUARIO.md), [Estados de atención](VERSION-1.8.3.md).

## 5. Instituciones, usuarios y licencias — ADMIN

### 5.1 Alta de institución

En **Instituciones**, abre el formulario de nueva institución. Captura nombre, contacto, correo, teléfono y notas administrativas. Revisa el registro existente antes de crear otro con un nombre parecido: duplicar una institución puede dividir accidentalmente sus saldos e historial.

Configura por separado los presupuestos de **Correo** y **Escritorio remoto**. Si vas a conceder las horas incluidas mediante el alta de una licencia, no las anticipes en este presupuesto: se acreditarían dos veces. El formulario admite horas y minutos; el componente de minutos va de 0 a 59. El valor persistente es un entero en minutos, no una cantidad decimal de horas.

Selecciona, cuando corresponda, un responsable predeterminado ADMIN o TECH activo. Debe tener un correo correcto si recibirá avisos. La documentación antigua que menciona exclusivamente ADMIN se amplió con TECH en 1.7. Cambiar al responsable institucional no reasigna automáticamente los casos anteriores.

### 5.2 Interpretar y ajustar el saldo

| Concepto | Significado |
|---|---|
| Asignado | Total histórico autorizado/acreditado de un canal. |
| Utilizado | Tiempo registrado y no anulado en ese canal. |
| Disponible | Asignado menos utilizado. |
| Contratado en una póliza | Tiempo acordado; puede no estar acreditado todavía. |
| Pendiente de acreditar | Contratado menos acreditado por los pagos de esa póliza; no es el saldo disponible institucional. |

Ejemplo: 10 horas asignadas de correo son 600 minutos. Una atención de 85 minutos deja 515, equivalentes a 8 h 35 min. La bolsa remota no cambia. Para agregar 5 horas mediante ajuste manual, el total asignado pasaría a 15 horas, no a 5.

**No dupliques la acreditación.** Si las horas se adquieren por un pago, utiliza el módulo financiero para acreditarlas y no aumentes además el presupuesto por esa misma compra. La aplicación impide fijar un total asignado inferior al utilizado o retirar las horas reservadas en licencias. El saldo institucional disponible incluye el saldo sin licencia y el de todas sus licencias; un aumento manual desde Instituciones alimenta únicamente la parte sin licencia.

### 5.3 Crear y administrar cuentas

En **Usuarios**, captura identificador, nombre visible, contraseña, rol, estado y correo. USER requiere institución; ADMIN, TECH y BILLING no deben tener una institución cliente asignada. Decide bien el ámbito antes del alta.

El identificador, rol e institución son inmutables mediante la edición normal. Para cambiar el ámbito de una persona, crea una cuenta con la configuración correcta y desactiva la anterior. Sus registros históricos conservan la autoría. Al editar, deja la contraseña vacía para mantenerla o introduce otra para restablecerla.

La aplicación impide desactivar al último ADMIN activo. Conserva más de una cuenta administradora autorizada si tu procedimiento de recuperación lo requiere. El resultado vigente de la regresión se registra en [Validación](VALIDACION.md); los fallos del informe 1.8.1 pertenecen a aquella revisión.

### 5.4 Baja y reactivación institucional

La baja es lógica: bloquea la operación del cliente y evita casos o consumos nuevos, sin borrar antecedentes. El equipo autorizado conserva consulta histórica. Para reactivar, edita la institución y cambia su estado. La reactivación no implica cambiar contraseñas, generar pagos ni renovar pólizas.

Base documental: [Manual breve](MANUAL-USUARIO.md), [Rol técnico](ROL-TECNICO.md), [Pólizas y cobranza](POLIZAS-COBRANZA.md), [Validación vigente](VALIDACION.md).

### 5.5 Configurar las horas incluidas
Con una cuenta **ADMIN**, abre **Configuración de soporte → Horas predeterminadas de las licencias**.

1. Define horas y minutos de **Soporte por correo**.
2. Define por separado horas y minutos de **Escritorio remoto**.
3. Pulsa **Guardar horas predeterminadas**.

Se admiten horas enteras no negativas y minutos de 0 a 59, con máximo de 1 000 000 000 de minutos por canal. Cada canal puede quedar en cero. La primera instalación queda **sin configurar**: no se inventa una dotación comercial de horas. Un administrador debe guardar los valores antes de crear licencias.

Guardar cambia exclusivamente la dotación de las licencias que se creen después. No modifica saldos, horas iniciales ni consumos anteriores. Si otro administrador cambió la configuración, el servidor rechaza la edición desactualizada; usa **Volver a cargar valores**, revisa y vuelve a guardar.

### 5.6 Registrar una licencia
1. Crea o identifica la institución titular.
2. Como ADMIN, abre **Licencias → Nueva licencia**.
3. Selecciona la institución y captura un número único de licencia.
4. Revisa las horas incluidas que muestra el formulario y pulsa **Guardar licencia y acreditar horas**.

La institución admite varias licencias. Cada licencia mantiene dos bolsas independientes, su dotación inicial y el historial de asignaciones, pagos y consumos. El alta acredita las horas exactamente una vez y aumenta también el total institucional. Una clave duplicada se rechaza sin recargar saldo. Si cambiaron los valores predeterminados mientras estaba abierto el formulario, vuelve a abrir **Nueva licencia** para revisar la nueva dotación.

Este registro sirve para el control local de soporte. No valida claves de activación contra los sistemas comerciales de SIABUC9, no genera licencias del software ni modifica su titularidad externa.

### 5.7 Agregar horas a una licencia existente
Para una ampliación administrativa, ADMIN abre la licencia y elige **Agregar horas**. Indica horas/minutos por canal y el motivo; confirma una vez. Se suman al saldo, conservando las horas incluidas al alta y el historial. Un reintento idéntico con el mismo identificador de solicitud devuelve el resultado previo sin volver a acreditar. Esta acción no registra dinero.

Para una compra, ADMIN o BILLING crea una **Póliza** de la institución y selecciona **Licencia de la póliza**. El alta del contrato no acredita minutos. Sus pagos o solicitudes de pago aprobadas acreditan la licencia seleccionada y el total institucional en la misma transacción. No hagas además una asignación manual por esa misma compra.

La licencia de una póliza se fija al crearla y no se cambia después. Una renovación convertida desde la póliza anterior conserva esa licencia. Una cotización nueva sin antecedente sigue creando una póliza sin licencia; para contratar directamente soporte para una licencia utiliza **Pólizas → Nueva póliza**. Los pagos libres, sin póliza, conservan el saldo institucional sin licencia.

### 5.8 Registrar atención
En **Nuevo caso**, selecciona **Licencia para esta solicitud**. La cuenta cliente sólo puede utilizar licencias de su propia institución. El caso conserva la licencia elegida; los consumos de correo y remoto, también los confirmados desde cronómetro, se descuentan de esa licencia y del total institucional.

Crear un caso no consume minutos. Requiere saldo positivo del canal elegido. No se toma tiempo de otra licencia ni del otro canal. Anular una atención devuelve los minutos a la misma bolsa una sola vez. Para anular un pago debe quedar todo el tiempo correspondiente en la licencia que recibió la acreditación; las horas de otra licencia no sirven para respaldar la anulación.

### 5.9 Compatibilidad con los saldos anteriores
V27 crea las tablas nuevas y vínculos opcionales; **no inventa licencias, no reparte saldos previos y no acredita horas durante la migración**. Los casos, pólizas y pagos existentes quedan sin licencia.

El total institucional incluye el saldo sin licencia y todas sus licencias. Un caso sin licencia sólo utiliza el saldo sin asignar. La edición del presupuesto institucional no puede retirar las horas reservadas para licencias; los incrementos manuales desde **Instituciones** alimentan el saldo sin licencia.

No registres como nueva dotación horas que ya están incluidas en el saldo anterior. Esta ampliación no incluye una herramienta de traslado de saldos históricos a licencias. Conserva el uso del saldo anterior y registra solamente dotaciones nuevas hasta definir un traslado explícito y auditable.

Antes de aplicar V27, respalda y ensaya en una copia aislada. No vuelvas a ejecutar el backend anterior sobre una base con V27: ese backend no respeta la reserva por licencia. Un retroceso requiere restaurar una copia compatible en una base nueva.

### 5.10 Permisos y trazabilidad
| Operación | ADMIN | BILLING | TECH | USER |
| --- | --- | --- | --- | --- |
| Modificar horas predeterminadas | Sí | No | No | No |
| Registrar licencia o agregar horas manuales | Sí | No | No | No |
| Consultar licencias y saldos | Todas | Todas | Todas | Sólo su institución |
| Consultar historial de la licencia | Sí | No | No | No |
| Acreditar mediante pago de póliza | Sí | Sí | No | No |
| Consumir minutos en una atención | Sí | No | Sí | No |

El historial conserva autor, fecha, canal, minutos y motivo. Los vínculos de pago y atención permiten consultar el movimiento de origen. Cambiar la configuración registra una auditoría separada y no genera acreditaciones.

### 5.11 Ejemplo de saldos por licencia

Ejemplo ficticio, no una tarifa: ADMIN guarda 2 h de correo y 1 h de remoto. Al crear L-001 se acreditan 120 y 60 minutos. Agrega después 30 minutos de correo con motivo; el disponible es 150/60, mientras la dotación inicial conserva 120/60. Una atención de correo de 20 minutos deja 130/60.

Si cambia los valores predeterminados a 3 h de correo y 1 h 30 min de remoto, L-001 conserva 130/60 disponibles. La siguiente licencia, L-002, nace con 180/90. El saldo de L-002 no puede utilizarse en un caso vinculado a L-001.

## 6. Recorrido del cliente: crear y seguir una solicitud

### 6.1 Revisar el resumen

Después de entrar, consulta **Resumen**. Se muestran casos pendientes, cerrados, el número de resueltos pendientes de cierre, saldos y actividad reciente. «Pendientes» reúne Recibido, En atención y Requiere respuesta del cliente. La guía de primeros pasos se despliega manualmente desde ese resumen; no se guarda una condición de «primera visita» por usuario o póliza.

En esta versión, pulsar una tarjeta de actividad reciente abre el listado de casos, no necesariamente ese folio. Utiliza **Casos de soporte** y la búsqueda por número para llegar al expediente concreto. La campana sí ofrece navegación a los casos relacionados con sus avisos.

### 6.2 Capturar una solicitud nueva

Entra a **Casos de soporte → Nuevo caso**. La institución del cliente proviene de la sesión. No es necesario volver a introducir tu identidad ni elegir otro centro.

| Campo | Cómo llenarlo |
|---|---|
| Licencia para esta solicitud | Elige la licencia de tu institución o Saldo institucional sin licencia. Se conserva en el caso. |
| Canal | Correo o Escritorio remoto. Debe tener saldo en la licencia elegida o en el saldo institucional sin licencia, según la selección. |
| Categoría | Instalación, Catalogación, Préstamos, Reportes, Importación/exportación u Otro. |
| Prioridad | Baja, Normal, Alta o Urgente, según el impacto real. |
| Título del caso | Entre 5 y 150 caracteres en el flujo cliente nuevo. Describe el problema de forma identificable. |
| Descripción | Entre 20 y 5,000 caracteres. Incluye situación, pasos, resultado observado y resultado esperado. |
| Ficha técnica opcional | Versión de SIABUC, sistema operativo, error y pasos para reproducirlo. |

Ejemplo ficticio de título: **«La importación no incorpora registros con ISBN»**. En la descripción, indica el archivo usado, la secuencia de acciones, el mensaje exacto y cuándo comenzó. No copies contraseñas ni datos de otras instituciones.

Pulsa **Revisar solicitud**, revisa el contenido mostrado y después **Confirmar y crear caso**. Si cambias título o descripción, vuelve a revisar. El botón **Cancelar** cierra el formulario sin crear la solicitud. Los archivos se agregan después de crear el caso; no hay un envío inicial de cinco adjuntos integrado en este formulario.

**Resultado esperado:** aparece la confirmación con el folio y se abre el detalle. Se crea el caso en **Recibido** y su ficha, pero no se descuentan minutos por enviarlo. Guarda el folio para futuras consultas.

El flujo nuevo utiliza `/api/client-portal/tickets` y valida sus longitudes en el servidor. El contrato anterior `/api/cases` permanece por compatibilidad, con sus límites anteriores; no se ha unificado toda integración externa bajo las validaciones nuevas.

### 6.3 Consultar, comentar y aportar evidencia

En **Casos de soporte**, busca por folio, título o descripción y filtra por estado, categoría o prioridad. El orden es por última actualización, no estrictamente por fecha inicial de creación. Abre el caso y revisa descripción, responsable e historial.

Para aportar información, abre un caso activo, escribe en **Tu comentario** y pulsa **Agregar al historial**. El cliente sólo publica comentarios visibles para la institución y sin consumo; no puede crear notas internas, registrar minutos, cerrar o reabrir por sí mismo. Las notas privadas del equipo no se entregan al portal cliente.

Abre **Adjuntos del caso** para cargar evidencia general. Desde el detalle de una entrada del historial puedes consultar sus archivos autorizados. El apartado **Ficha técnica y plazos de atención** permite completar datos de un caso activo; la ficha no reemplaza una respuesta dentro del historial.

### 6.4 Entender el estado y quién debe responder

| Estado que ves | Qué significa para el cliente | Qué puedes hacer |
|---|---|---|
| Recibido | La solicitud está registrada | Consultar, comentar, adjuntar y completar ficha |
| En atención | Soporte está dando seguimiento | Aportar información o responder dentro del mismo caso |
| Requiere respuesta del cliente | Soporte solicita información o una comprobación | Escribir la respuesta; un comentario devuelve el caso a En atención |
| Resuelto | Se registró una solución | Consultar antecedentes; pedir a soporte reapertura si necesita más atención |
| Cerrado | Concluyó el seguimiento | Consultar y, si aún no existe evaluación institucional, responder la encuesta |

Para responder una solicitud de información, explica el resultado de las pruebas y pulsa **Agregar al historial**. Adjuntar un archivo o editar la ficha por sí solo no ejecuta la transición automática: acompaña la evidencia con un comentario. El cliente no dispone de selector manual de estado.

La campana muestra además conversaciones pendientes. Leer el aviso no responde ni cambia el estado. Una respuesta pública de soporte y la última intervención del cliente ayudan a identificar de quién se espera continuación; el estado explícito Requiere respuesta del cliente deja constancia de esa espera.

Si no tienes saldo, puedes consultar los antecedentes autorizados y comentar un caso activo, pero no abrir otro en un canal agotado. La reapertura de uno Resuelto o Cerrado la realiza soporte y requiere saldo del canal principal en su licencia o parte institucional sin licencia. Una póliza vencida no elimina por sí misma minutos ya acreditados.

Véanse [Estados de atención](VERSION-1.8.3.md), [Avisos internos](AVISOS-INTERNOS.md) y [Pólizas y cobranza](POLIZAS-COBRANZA.md).

## 7. Atención de casos — ADMIN y TECH

### 7.1 Localizar, tomar y asignar trabajo

Abre **Casos de soporte**. Combina filtros de institución, **Pendientes de resolver**, estado concreto, categoría, prioridad y asignación. **Sin responsable** ayuda a localizar trabajo pendiente; **Mis casos** muestra la asignación de tu cuenta.

Para tomar un caso, abre uno libre, activo y de institución activa y pulsa **Tomar caso**. El sistema registra responsable, historial y aviso según la configuración. Si estaba Recibido, pasa automáticamente a **En atención**. Tomar un caso que ya está En atención o Requiere respuesta del cliente conserva ese estado. No consume minutos ni cuenta como primera respuesta pública. Si otra persona se adelantó, recarga y revisa la asignación vigente.

ADMIN puede asignar o reasignar a otro ADMIN/TECH activo mediante la edición del caso. TECH puede tomar uno libre y atender expedientes de otras asignaciones, pero no reasignarlos. Cuando un técnico crea un caso, queda como responsable de ese nuevo caso. Un cambio del responsable no transfiere propiedad institucional.

### 7.2 Registrar atención o comentario

Desde el detalle de un caso activo, utiliza **Registrar seguimiento**. Elige **Comentario** para una comunicación sin cargo; para una atención con consumo, elige Correo o Escritorio remoto y escribe las horas/minutos reales que vas a registrar. Describe el trabajo realizado de forma suficiente para una revisión posterior.

Antes de confirmar, comprueba **caso, institución, licencia, canal, minutos y visibilidad**. La atención se carga siempre a la licencia que conserva el caso, o a la parte institucional sin licencia si no se vinculó ninguna. No toma horas prestadas de otra licencia. Una atención puede usar un canal distinto al canal inicial del caso, siempre que disponga de saldo en la bolsa que se cargará. La separación de bolsas se mantiene: minutos de correo no se toman automáticamente de remoto.

Al registrar una atención válida, el historial y el descuento se guardan conjuntamente. Si el saldo es insuficiente o el caso está Resuelto o Cerrado, la operación se rechaza sin dejar un descuento parcial. La pérdida de conexión exige revisar el historial antes de volver a registrar el mismo trabajo.

### 7.3 Notas internas y privacidad

Marca **nota interna** sólo para información de trabajo que no deba ver el cliente. ADMIN y TECH pueden consultarla; USER no. El tiempo de una atención interna sigue formando parte del consumo del caso y del saldo institucional, aunque su descripción no sea pública.

Una entrada guardada no permite cambiar su visibilidad. Si hace falta responder públicamente, redacta otra entrada después de revisar su texto y archivos. Los avisos internos no deben enviarse al contacto del cliente. No escribas secretos de acceso en notas, plantillas o incidencias: su carácter interno no sustituye un sistema de custodia de credenciales.

### 7.4 Corregir una atención

La corrección de consumo se realiza mediante **Anular atención y devolver tiempo**, reservada a ADMIN. Selecciona la atención correspondiente, introduce el motivo y confirma con **Anular y devolver tiempo**. El registro permanece visible como anulado y devuelve una sola vez los minutos de su canal y a la misma licencia del caso, cuando existe. Los comentarios de cero minutos no se anulan por esta vía.

Si procede registrar el tiempo correcto, crea una nueva atención con explicación que permita relacionarla con la anulada. No borres antecedentes ni ajustes manualmente el presupuesto para esconder un error de consumo.

### 7.5 Cambiar el estado con motivo

1. Abre el expediente y revisa su estado, últimos mensajes, cronómetros y citas.
2. Pulsa **Cambiar estado**.
3. Elige el destino en **Nuevo estado**.
4. Escribe un **Motivo** comprensible para la institución. El formulario admite hasta 2,000 caracteres y el texto queda público en el historial.
5. Pulsa **Confirmar cambio** y comprueba el estado resultante y la entrada de historial.

| Origen | Destinos que ofrece la interfaz |
|---|---|
| Recibido | En atención · Requiere respuesta del cliente · Resuelto · Cerrado |
| En atención | Requiere respuesta del cliente · Resuelto · Cerrado |
| Requiere respuesta del cliente | En atención · Resuelto · Cerrado |
| Resuelto | En atención · Cerrado |
| Cerrado | En atención |

ADMIN y TECH pueden ejecutar estos cambios aunque no sean el responsable asignado. Los clientes y Cobranza no pueden hacerlo. Un envío repetido del mismo estado no duplica avisos ni historial. No hay un botón separado «Cerrar caso» o «Reabrir caso» en este flujo: se utiliza el selector.

### 7.6 Pedir información y recibir la respuesta

Registra primero una respuesta pública que explique lo necesario, por ejemplo: «Confirma la versión de SIABUC y adjunta el mensaje completo de error». Después cambia a **Requiere respuesta del cliente** con un motivo. El comentario de soporte por sí solo no selecciona ese estado.

Al recibir un comentario del cliente, el caso vuelve automáticamente a **En atención** y conserva el motivo automático en el historial. El técnico debe revisar la nueva evidencia. Un comentario interno de soporte, un archivo aislado o leer un aviso no ejecuta esa devolución.

Esta espera pausa los cronómetros que estén corriendo en el caso, pero **no pausa ni reinicia el SLA**. Los objetivos siguen el calendario laboral conservado del caso.

### 7.7 Resolver, cerrar y reabrir

**Resolver:** registra la solución en el historial y confirma los minutos que correspondan; después selecciona Resuelto. Queda registrada la fecha de resolución y se bloquean nuevos seguimientos, ficha, adjuntos y confirmaciones de consumo hasta reabrir. Se conservan consulta y descargas autorizadas. La edición administrativa de título, descripción, canal y asignación mantiene sus permisos previos.

**Cerrar:** cuando proceda completar el seguimiento administrativo, selecciona Cerrado con motivo. Desde Resuelto conserva la fecha de resolución anterior. También se permite cerrar directamente un caso activo. La encuesta se habilita en Cerrado, no solamente en Resuelto.

**Reabrir:** desde Resuelto o Cerrado, selecciona En atención y explica por qué. Exige institución activa y saldo positivo del canal principal en la licencia del caso o en la parte institucional sin licencia. No descuenta tiempo por reabrir. Reinicia la ventana de resolución y conserva la primera respuesta histórica.

Pasar entre estados activos exige institución activa, pero no nuevo saldo ni consumo. Resolver o cerrar conserva la posibilidad de concluir un caso sin saldo. Los cambios no liquidan cronómetros ni cancelan citas automáticamente: los relojes se pausan y las citas deben revisarse por separado. Cerrado no pasa directamente a Resuelto; requiere reapertura.

### 7.8 Ejemplo de un ciclo de atención

| Paso | Acción | Resultado |
|---|---|---|
| 1 | USER registra un problema de importación | Recibido, sin minutos consumidos |
| 2 | TECH toma el caso libre | En atención, responsable asignado |
| 3 | TECH pide el archivo de ejemplo y selecciona espera | Requiere respuesta del cliente; cronómetro pausado, SLA continúa |
| 4 | USER adjunta archivo y agrega comentario | En atención, respuesta visible en historial |
| 5 | TECH registra la solución y confirma 15 minutos | Se descuentan 15 del canal elegido, una sola vez |
| 6 | TECH selecciona Resuelto | Fecha de resolución registrada |
| 7 | TECH selecciona Cerrado | Conserva resolución y habilita encuesta |
| 8 | Aparece una regresión y TECH reabre con saldo | En atención; nuevo plazo de resolución |

Los pasos son un ejemplo de uso; el sistema no obliga a recorrer todos los estados para poder cerrar. Consulta [Reglas de 1.8.3](VERSION-1.8.3.md).

## 8. Adjuntos y evidencias de soporte

### 8.1 Cargar y descargar archivos

En un caso activo, entra a su apartado de adjuntos, selecciona el archivo y pulsa **Adjuntar archivo**. El listado muestra nombre, tamaño, autor y fecha. Para descargarlo, utiliza **Descargar** junto al archivo. Adjuntar no registra tiempo de atención.

Hay dos ubicaciones: evidencia general del caso y archivos asociados a una entrada concreta del historial. Verifica dónde estás trabajando. Un archivo relacionado con una nota interna conserva las restricciones de esa nota. Los comprobantes financieros se cargan en los módulos de pagos o solicitudes de pago, no como sustituto del expediente financiero.

| Evidencia | Formatos | Límite individual | Límite agregado |
|---|---|---|---|
| Caso de soporte | PNG, JPG/JPEG, PDF, TXT, LOG, CSV y JSON UTF-8 | 10 MiB | 100 archivos o 100 MiB por caso |
| Pago registrado | PDF, PNG y JPG/JPEG | 10 MiB | 10 archivos y 50 MiB por pago |
| Solicitud de pago | PDF, PNG y JPG/JPEG | 10 MiB | 10 archivos y 50 MiB por solicitud |
| Artículo de conocimiento | Texto plano | Sin carga de adjuntos implementada | No aplica |

Los límites reales del paquete no son los cinco archivos por solicitud propuestos en el borrador de requerimientos. No confundas MiB con MB decimales: 10 MiB equivalen a 10,485,760 bytes.

### 8.2 Reglas y precauciones

El servidor revisa tamaño, extensión y señales de contenido. Renombrar un ejecutable a `.pdf` no lo convierte en evidencia aceptable. La comprobación de formato no es un análisis antivirus. Revisa los archivos antes de compartirlos y evita incluir datos de terceros, contraseñas o respaldos completos innecesarios.

Un caso Resuelto o Cerrado conserva descargas autorizadas, pero no permite nuevos adjuntos hasta reabrirlo. Las evidencias no se eliminan desde el formulario ordinario. Si cargaste un archivo equivocado, informa al administrador antes de intentar una corrección; no supongas que existe un botón de borrado o sustitución.

Los bytes están almacenados en PostgreSQL. El respaldo de la base incluye la evidencia; el ZIP de código y el `.env` no la contienen.

Base documental: [Contrato API](API-CONTRACT.md), [Comprobantes](COMPROBANTES-PAGO.md), [Respaldos](RESPALDO-RESTAURACION.md).

## 9. Ficha técnica, prioridades y plazos de atención

### 9.1 Completar la ficha

Desde **Ficha técnica y plazos de atención**, registra categoría, prioridad, versión de SIABUC, sistema operativo, mensaje de error y pasos reproducibles. Usa el error exacto, pero elimina secretos innecesarios. ADMIN, TECH y clientes autorizados de la institución pueden intervenir conforme a los permisos del caso activo; BILLING no participa en esa ficha.

Las seis categorías técnicas del caso son independientes de las doce categorías editoriales de la base de conocimientos. Elegir «Guías» o «Pólizas y licencias» en un artículo no agrega esas opciones al formulario del caso.

### 9.2 Interpretar el SLA

El SLA es el objetivo de tiempo laboral para responder y resolver. **No es consumo facturable.** Que transcurra una hora o venza un plazo no descuenta una hora de la póliza.

| Indicador | Lectura operativa |
|---|---|
| Dentro del plazo | El objetivo sigue pendiente y todavía no vence. |
| Plazo vencido | El objetivo sigue pendiente y la fecha límite pasó. |
| Cumplido | Se respondió, resolvió o cerró como máximo en la fecha límite, según el objetivo. |
| Atendido fuera del plazo | La acción ocurrió después de la fecha límite. |
| Sin fecha registrada | No existe evidencia suficiente de fecha para esa medición. |

La primera respuesta se cumple con una comunicación pública registrada por ADMIN/TECH. No cuentan las notas internas, la toma del caso, las modificaciones de datos, la agenda ni un comentario del cliente. Cerrar el caso no inventa una respuesta pública. La resolución se completa al pasar a Resuelto o al cerrar directamente. Cerrar después de Resuelto conserva la fecha de resolución. Una reapertura inicia otro plazo de resolución. Cambiar entre Recibido, En atención y Requiere respuesta del cliente no reinicia plazos; la espera del cliente tampoco los pausa.

### 9.3 Configuración por Administración

Abre **Configuración de soporte → Horario y plazos de atención**. Ajusta zona horaria, días laborales, apertura/cierre, fechas inhábiles y objetivos por prioridad. La configuración inicial utiliza `America/Mexico_City`, lunes a viernes de 09:00 a 17:00 y no precarga feriados.

| Prioridad | Primera respuesta inicial | Resolución inicial |
|---|---:|---:|
| Baja | 480 min laborales | 2,400 min laborales |
| Normal | 240 min laborales | 1,440 min laborales |
| Alta | 120 min laborales | 480 min laborales |
| Urgente | 30 min laborales | 240 min laborales |

Estos valores son iniciales configurables, no horarios comerciales ni compromisos aprobados universalmente. Por ejemplo, con esa jornada, un objetivo de 120 minutos iniciado el viernes a las 16:30 vence el lunes a las 10:30, salvo que el lunes sea inhábil.

Cada caso conserva una copia de sus reglas al crearse. Cambiar la configuración general afecta a casos nuevos, no reescribe compromisos previos. Cambiar la prioridad utiliza la tabla conservada del caso y no reinicia la primera respuesta. Los cálculos de expedientes antiguos pueden estar marcados como orientativos.

Base documental: [Clasificación y SLA](CLASIFICACION-SLA.md), [Rol técnico](ROL-TECNICO.md).

## 10. Cronómetro y agenda remota

### 10.1 Medir trabajo sin cobrarlo automáticamente

ADMIN y TECH pueden **Iniciar cronómetro** en un caso Recibido o En atención, de institución activa. Usa **Pausar** al interrumpir y **Continuar** cuando retomes. Al pulsar **Finalizar atención**, el reloj queda guardado y aparece el formulario de confirmación.

1. Revisa los minutos propuestos, redondeados hacia arriba: 61 segundos proponen 2 minutos.
2. Elige Correo o Escritorio remoto, según la bolsa que corresponda al trabajo.
3. Escribe la descripción y revisa si debe ser pública o nota interna.
4. Si ajustas los minutos propuestos, explica el motivo de la diferencia.
5. Pulsa **Confirmar y descontar N minutos** y comprueba la atención en el historial.

**Finalizar no descuenta; confirmar sí.** No registres otra atención manual por el mismo tiempo. La confirmación repetida de un cronómetro ya confirmado no vuelve a descontarlo.

Cada integrante puede mantener un único cronómetro pendiente, incluso entre casos distintos. Un reloj pausado o finalizado sin confirmar ocupa ese lugar: confírmalo o usa **Descartar cronómetro → Sí, descartar** antes de iniciar otro. Descartar no consume minutos y deja trazabilidad. Nadie opera el cronómetro de otro integrante desde esta pantalla.

El servidor conserva el tiempo medido. Recargar recupera el reloj y cerrar una pestaña no lo pausa. Al pasar el caso a Requiere respuesta del cliente, Resuelto o Cerrado, todos sus relojes en marcha se pausan automáticamente y conservan el tiempo; no se confirman ni cobran.

| Estado del caso | Iniciar/continuar | Finalizar o descartar uno pendiente | Confirmar minutos |
|---|---|---|---|
| Recibido / En atención | Sí | Sí | Sí, con saldo y condiciones válidas |
| Requiere respuesta del cliente | No | Sí | Sí, con saldo y condiciones válidas |
| Resuelto / Cerrado | No | Sí | No; requiere reapertura |

Si falla una confirmación por saldo o estado, el reloj finalizado permanece guardado y no deja un cargo parcial. Corrige la condición y revisa el historial antes de reintentar.

### 10.2 Proponer y gestionar una sesión

En **Agenda de sesiones remotas**, con un caso activo, ADMIN/TECH registra **Inicio**, **Fin** y **Objetivo y preparativos**, y pulsa **Proponer al cliente**. La pantalla muestra como responsable a la cuenta que propone. El horario debe ser futuro, con fin posterior al inicio y duración máxima de 12 horas. El cliente de la institución confirma o rechaza la propuesta. ADMIN puede gestionar el alcance autorizado; TECH gestiona las sesiones de las que es responsable, conforme a las reglas existentes.

Las sesiones tienen estados propios: **Propuesta**, **Confirmada**, **Rechazada**, **Cancelada** y **Realizada**. No son estados del ticket. La agenda rechaza solapamientos para el mismo responsable; las citas consecutivas sin cruce sí son admisibles.

Para cambiar un horario utiliza **Reprogramar**: cancela la cita anterior y crea una nueva propuesta que requiere confirmación del cliente. Si el horario nuevo no es admisible, la operación se revierte. Revisa la zona horaria mostrada y confirma con el cliente la interpretación del horario.

Marcar una sesión realizada no cobra tiempo. Registra después la atención remota o confirma el cronómetro correspondiente. Al resolver o cerrar el caso no se pueden crear ni reprogramar sesiones, ni confirmar o rechazar propuestas, hasta reabrir. Las citas pendientes no se cancelan automáticamente: usa **Cancelar sesión** cuando ya no procedan. Una confirmada que ya comenzó puede marcarse realizada por el personal autorizado con su comentario. No hay conexión con calendarios externos ni una herramienta de control remoto embebida: la agenda coordina el trabajo, no abre por sí sola el escritorio del cliente.

Base documental: [Rol técnico](ROL-TECNICO.md).

## 11. Campana, respuestas pendientes, vistas y alertas

### 11.1 Consultar avisos personales

Pulsa la campana del encabezado. Revisa avisos recientes y conversaciones pendientes; **Ver caso** abre el expediente relacionado y marca el aviso como leído. **Marcar leída** y **Marcar todas como leídas** afectan sólo a tu cuenta, no a la bandeja de los demás.

La campana se actualiza periódicamente y dispone de actualización manual. La presentación habitual contiene hasta 100 avisos recientes; el contador puede incluir más avisos accesibles sin leer. Un aviso histórico no certifica que el caso siga igual: abre el detalle para consultar la situación actual.

La bandeja funciona sin SMTP. Los clientes no reciben notas técnicas internas. BILLING recibe los avisos propios que correspondan, como saldo, pero no acceso a los casos por esa vía. No confundas campana con la cola administrativa de correo.

### 11.2 Guardar filtros frecuentes

En **Casos de soporte**, combina búsqueda, estado, asignación, institución, categoría y prioridad. En **Mis vistas guardadas**, escribe un nombre y pulsa **Guardar filtros como nueva vista**. Después puedes aplicar, actualizar o eliminar esa configuración sin borrar casos.

Ejemplos útiles son «Mis casos pendientes», «Urgentes sin responsable» o «Instalaciones pendientes». Las vistas son personales, con hasta 50 por cuenta; no copian resultados históricos ni amplían permisos. Puedes guardar un estado concreto o **Pendientes de resolver**. La migración convierte el antiguo filtro `OPEN` en este grupo para no ocultar casos en atención o en espera.

**Filtros de fechas en 1.8.3:** los filtros Desde/Hasta agregados al listado del personal se aplican localmente sobre la fecha UTC de creación. No están incluidos en el objeto de vista guardada. Al aplicar una vista, revisa también las fechas que quedaron en pantalla. Para cortes por horario de México o informes formales utiliza los módulos de reportes, no supongas que ese filtro local equivale a un corte contable.

### 11.3 Alertas de saldo y de SLA

En **Configuración de soporte**, ADMIN define umbrales de saldo por institución y canal. Los valores iniciales son 60 minutos de correo y 30 de remoto. El aviso se genera al estar igual o debajo del umbral; no se repite en cada consumo del mismo periodo de saldo bajo. Una recarga que supera el umbral permite advertir una nueva caída posterior.

Los **Avisos antes del vencimiento** de SLA utilizan inicialmente 60 minutos naturales de anticipación. El plazo del caso se calcula con su calendario laboral, pero la anticipación del aviso es natural. ADMIN recibe los avisos previstos para todos los casos; TECH, los de sus asignados y los todavía sin responsable. Clientes y Cobranza no reciben esos avisos técnicos internos.

La revisión periódica y **Revisar plazos ahora** no modifican saldos ni crean una atención. Pausar el correo no apaga estas alertas.

Base documental: [Avisos internos](AVISOS-INTERNOS.md), [Vistas y alertas](VISTAS-ALERTAS.md), [Estados de atención](VERSION-1.8.3.md).

## 12. Plantillas e incidencias generales

### 12.1 Preparar y utilizar plantillas

ADMIN abre **Plantillas → Nueva plantilla**, captura título, categoría técnica, versión opcional y contenido en texto plano. Las nuevas quedan activas. Puede editarlas, archivarlas, reactivarlas y consultar sus revisiones sin cambiar respuestas que ya estén guardadas en casos.

Para usarlas, ADMIN/TECH abre un caso y el formulario de seguimiento, pulsa **Usar una plantilla de respuesta**, busca y revisa la vista previa. **Insertar en borrador** copia el contenido a un texto editable. Ajusta nombres, instrucciones, visibilidad y minutos antes de registrar el seguimiento.

**Seleccionar, previsualizar o insertar no publica, no envía correo y no descuenta minutos.** No hay sustitución automática de variables ni ejecución de HTML. Un texto ya copiado al borrador no se retira cuando después se archiva la plantilla; revisa siempre lo que vas a enviar.

### 12.2 Coordinar una incidencia común

En **Incidencias generales**, ADMIN/TECH puede crear una incidencia, describir el problema interno, establecer su estado y vincular casos de distintas instituciones. Utiliza la búsqueda y verifica cada expediente antes de asociarlo. Una vinculación errónea se retira conservando historial.

Registra avances y diagnóstico dentro de la incidencia. Después abre cada caso para documentar la atención, el tiempo y su cierre. Los estados de incidencia **Abierta/En atención/Resuelta** coordinan el problema global; no cambian los cinco estados de sus casos vinculados.

Resolver una incidencia no cierra expedientes ni distribuye automáticamente consumos. Vincular casos tampoco copia sus mensajes privados ni crea pagos.

### 12.3 Publicar información para los clientes

Desde **Resumen para clientes**, escribe título y resumen públicos separados del diagnóstico interno. Revisa la vista previa y la confirmación de privacidad antes de publicar. Omite nombres, números de caso, contactos o credenciales de otras instituciones.

El cliente sólo ve el aviso publicado dentro de sus propios casos, sin acceso al listado global ni al historial interno de la incidencia. Cambiar el diagnóstico interno no actualiza automáticamente la publicación: utiliza **Actualizar publicación** cuando proceda. **Retirar publicación** la oculta sin borrar sus antecedentes internos.

La aplicación separa campos y permisos, pero no interpreta si el texto que redactaste contiene información privada. Esa revisión corresponde al personal que publica. Estas operaciones no generan por sí solas correos ni consumo.

Base documental: [Plantillas](PLANTILLAS-RESPUESTA.md), [Incidencias](INCIDENCIAS.md).

## 13. Base de conocimientos y centro de ayuda

### 13.1 Consultar contenidos

El cliente accede desde **Centro de ayuda** en el encabezado o **Base de conocimientos** en la navegación. Busca por título/contenido, categoría o versión de SIABUC. Abre el artículo, lee las instrucciones y vuelve al listado para refinar la búsqueda. Sólo se muestran publicaciones autorizadas, no borradores ni revisiones internas.

Hay doce categorías editoriales: las seis tradicionales —Instalación, Catalogación, Préstamos, Reportes, Importación/exportación y Otro— más **Guías y primeros pasos**, **Preguntas frecuentes**, **Solicitudes de soporte**, **Pólizas y licencias**, **Cursos y capacitación** y **Contacto y horarios**. Tener una categoría no significa que incluya contenido precargado ni que se haya implementado el servicio descrito en su nombre.

### 13.2 Elaborar y publicar un artículo — ADMIN

Abre **Nuevo artículo**. Captura título, categoría, versiones aplicables, resumen y solución. Guarda como borrador. Revisa el texto, selecciona **Publicar artículo**, escribe el motivo y confirma. Desde ese momento lo pueden consultar los clientes autorizados.

Para corregir una publicación, pásala a borrador, edita y vuelve a publicar. Mientras permanece en borrador deja de aparecer al cliente, incluso desde los casos vinculados. **Archivar artículo** retira su consulta sin borrar revisiones; puede recuperarse como borrador.

Los límites son 180 caracteres para título, 120 para versión, 1,000 para resumen o nota editorial y 50,000 para cuerpo. El contenido se muestra como texto plano escapado. No hay editor enriquecido ni administración de adjuntos de artículos; una URL escrita dentro del texto no debe documentarse como un recurso descargable integrado garantizado.

### 13.3 Vincular una solución a un caso

ADMIN/TECH abre **Soluciones vinculadas al caso**, busca una publicación y pulsa **Vincular solución**. El cliente puede consultarla desde el expediente. Retirar el vínculo no elimina el artículo, no cierra el caso y no cambia el saldo.

El vínculo muestra la publicación vigente, no una copia congelada del contenido. Si el artículo se despublica, se oculta al cliente; al volver a publicarlo, el vínculo refleja la versión actual.

**Limitación de interfaz que continúa en 1.8.3:** el menú global muestra Base de conocimientos a TECH, pero el componente completo permite entrada sólo a ADMIN/USER y puede mostrar «No tienes acceso». El técnico sí dispone del componente de soluciones dentro de los casos y de las operaciones publicadas autorizadas. No eleves a un técnico a ADMIN para sortear este defecto de interfaz.

Base documental: [Base de conocimientos](BASE-CONOCIMIENTOS.md), [Rol técnico](ROL-TECNICO.md), [Manual breve](MANUAL-USUARIO.md).

## 14. Paquetes, cotizaciones y renovaciones

### 14.1 Mantener paquetes y tarifas

ADMIN/BILLING abre **Paquetes y tarifas → Nuevo paquete**. Define código estable, nombre, descripción, importe MXN y minutos de correo/remoto. El precio debe ser positivo y al menos un canal debe tener tiempo. Para cambiar la oferta usa **Crear nueva versión**; el código y las revisiones anteriores se conservan.

**Desactivar paquete** impide elegirlo en ofertas nuevas sin borrar el historial. Una revisión nueva no cambia contratos, cotizaciones emitidas ni pagos anteriores. Crear un paquete nunca acredita tiempo.

### 14.2 Preparar una cotización

En **Cotizaciones → Nueva cotización**, selecciona institución, folio y, opcionalmente, paquete. Revisa importe, descripción y minutos copiados. Si negocias condiciones distintas, documenta el motivo de ajuste al paquete.

Distingue **Oferta válida hasta**, que limita la aceptación, de **Inicio/Fin de cobertura**, que describen el contrato propuesto. En una renovación, selecciona el antecedente correcto de la misma institución y revisa las fechas sugeridas.

Guarda el borrador. Puedes editarlo mientras siga en ese estado; su institución y antecedente quedan fijados. Entrega la oferta por el medio institucional apropiado y usa **Registrar emisión** para documentar la entrega. Ese botón no envía automáticamente el correo.

### 14.3 Registrar respuesta y convertir

| Estado de cotización | Uso |
|---|---|
| Borrador | Preparar y modificar términos. |
| Emitida | Registrar aceptación o rechazo recibido. |
| Aceptada | Convertir el acuerdo en póliza. |
| Rechazada | Consultar la constancia; elaborar otra oferta si procede. |
| Convertida | Consultar la póliza creada y su origen. |

ADMIN/BILLING registra contacto, fecha y constancia de aceptación/rechazo. Es una anotación administrativa, no una firma digital. La aceptación debe cumplir los límites de fecha de la oferta. No se fuerza una aceptación vencida cambiando fechas reales.

Una renovación conserva la licencia de su póliza anterior al convertirse. Una cotización nueva sin antecedente genera una póliza sin licencia; para contratar directamente horas para una licencia, utiliza Pólizas → Nueva póliza y selecciona la licencia.

En una aceptada, pulsa **Convertir a póliza** e indica un folio único. Se crea una póliza sin pago ni crédito de minutos. Continúa con **Pagos** o **Validar pagos**. Repetir la conversión en las condiciones admitidas devuelve el mismo contrato, no otro.

### 14.4 Renovación y avisos

**Renovaciones** reúne pólizas activas próximas a vencer o vencidas sin sucesora. El umbral inicial de aviso es 30 días y se puede configurar. **Mis avisos de vencimiento** conserva avisos internos y lecturas personales; no depende de SMTP.

La relación de renovación forma una cadena y cada antecedente admite una sucesora. Si ésta se cancela, no se elimina el vínculo; una nueva renovación puede partir de esa última póliza cancelada y conservar la historia. Renovar no transfiere, vence ni duplica minutos institucionales.

Base documental: [Paquetes y tarifas](PAQUETES-TARIFAS.md), [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md), [Pólizas y cobranza](POLIZAS-COBRANZA.md).

## 15. Pólizas y pagos

### 15.1 Registrar una póliza

ADMIN/BILLING abre **Pólizas** y crea el contrato de una institución activa: folio único, descripción, vigencia, importe MXN y tiempo por canal. La fecha final no puede preceder al inicio y debe haber minutos contratados en al menos un canal. En **Licencia de la póliza**, selecciona la licencia de esa institución que recibirá las horas pagadas. El vínculo se fija al alta; si no se elige licencia, la acreditación se dirigirá al saldo institucional sin licencia.

Una póliza tiene **estado administrativo** Activa/Cancelada y **vigencia calculada** Por iniciar/Vigente/Vencida. Son datos distintos. La vigencia es informativa y no retira minutos ya acreditados; una cancelación sí impide movimientos nuevos en ese contrato.

**Crear póliza no crea un pago ni aumenta saldo.** Para registrar otro acuerdo no reutilices el folio de una póliza cancelada.

### 15.2 Registrar dinero y minutos de forma explícita

En **Pagos**, selecciona institución y póliza opcional. Captura referencia, fecha del pago, método, importe, descripción y minutos por acreditar en ambos canales. Comprueba los datos y confirma una sola vez.

Si la póliza tiene licencia, los minutos se acreditan a esa licencia y al total institucional en una misma transacción. Los pagos libres o de pólizas sin licencia alimentan el saldo institucional sin licencia. No uses además Agregar horas por esa misma compra.

El importe no se convierte automáticamente en horas por una tarifa. El operador debe especificar los minutos que libera ese movimiento, dentro de los límites contractuales y de saldo.

| Tipo de movimiento | Importe | Minutos | Condición |
|---|---:|---|---|
| Compra directa sin póliza | Positivo | Algún canal positivo | Compra de soporte sin contrato ligado. |
| Pago a póliza con horas | Positivo | Algún canal positivo | No exceder pendientes de dinero ni tiempo contratado. |
| Abono a póliza sin horas | Positivo | Ambos en cero | Registra dinero sin liberar más soporte. |
| Liberación de horas ya pagadas | Cero | Algún canal positivo | Póliza ya liquidada monetariamente y con tiempo pendiente. |

Los nuevos pagos utilizan MXN; las monedas históricas permanecen sin conversión. Los métodos disponibles distinguen transferencia, depósito, efectivo, tarjeta de crédito, tarjeta de débito, cheque y otro. La referencia debe ser única dentro de la institución incluso después de anular un movimiento.

### 15.3 Ejemplo completo de acreditación

Ejemplo ficticio: póliza por $1,000 MXN con 100 minutos de correo y 60 remotos.

| Paso | Importe registrado | Correo acreditado | Remoto acreditado |
|---|---:|---:|---:|
| Primer pago | $200 | 20 min | 10 min |
| Pago que liquida el dinero | $800 | 0 min | 0 min |
| Liberación de tiempo pendiente ya pagado | $0 | 80 min | 50 min |
| Total | $1,000 | 100 min | 60 min |

El cliente dispondrá del saldo acreditado menos el que ya haya consumido. El tercer movimiento aumenta minutos, pero no es otro depósito ni aumenta ingresos.

### 15.4 Edición, cancelación y anulación

Una póliza sólo admite edición contractual si está activa y nunca tuvo pagos, incluso anulados. Además, tener parcialidades no canceladas restringe modificar el importe acordado. La institución y la licencia de la póliza no se cambian después del alta.

Para corregir un pago, ADMIN utiliza **Anular pago** con un motivo. La aplicación conserva la evidencia y retira sus minutos sólo si hay saldo suficiente en ambos canales de la licencia que recibió el pago, o de la parte sin licencia cuando corresponde. Las horas de otra licencia no respaldan esa anulación. No se permite una retirada parcial que deje la bolsa negativa.

Si anular un abono monetario deja sin respaldo una acreditación vigente de importe cero, deben resolverse primero esas acreditaciones, respetando su saldo. No uses ajustes artificiales para eludir la protección.

ADMIN/BILLING puede cancelar una póliza cuando no tiene pagos vigentes. Un contrato con pagos anulados conserva la historia y no recupera la edición. Anular o cancelar no ejecuta un reembolso bancario ni borra documentos ya descargados.

### 15.5 Prevenir doble registro

La API de pagos incorpora mecanismos de reintento, pero el operador debe verificar el historial antes de crear otra captura con nueva referencia. No registres un pago directo y después apruebes una solicitud que representa el mismo depósito. Tampoco ajustes el presupuesto manualmente por una compra ya acreditada.

Base documental: [Pólizas y cobranza](POLIZAS-COBRANZA.md), [Solicitudes de pago](SOLICITUDES-PAGO.md), [Comprobantes](COMPROBANTES-PAGO.md), [Parcialidades](PARCIALIDADES.md).

## 16. Solicitudes de pago, comprobantes y recibos

### 16.1 Revisar evidencia antes de acreditar

ADMIN/BILLING trabaja en **Validar pagos**. El cliente autorizado lo hace desde **Finanzas institucionales**. Para presentar una solicitud, el personal selecciona la institución; en el portal cliente está fijada por la sesión y no aparece un selector. Captura póliza opcional, referencia, fecha, método, importe, minutos y descripción visible para la institución.

Al guardar queda **Pendiente**: no se registra un ingreso ni se acredita soporte. Carga PDF, PNG o JPG/JPEG y revisa su legibilidad. Se requiere al menos un comprobante para aprobar.

El personal verifica la evidencia, importe y los dos conceptos de minutos, marca la confirmación correspondiente y pulsa **Aprobar y registrar pago**. La aprobación crea el pago, acredita el tiempo y copia los comprobantes en una transacción. La observación de aprobación es privada.

Si el importe o los minutos son incorrectos, rechaza con motivo y presenta una nueva solicitud; no se modifican durante la aprobación. El motivo de rechazo sí es visible para el cliente. Una solicitud resuelta y su evidencia se conservan sin edición. Rechazar no cambia saldo.

### 16.2 Adjuntar comprobantes a un pago ya registrado

ADMIN/BILLING abre el pago y usa **Comprobantes de pago → Adjuntar comprobante**. Puede consultar nombre, tamaño, autor, fecha y huella y descargar el archivo original. Agregar evidencia no registra otro pago ni cambia dinero o minutos.

Los comprobantes se conservan incluso si el pago se anula. No se sustituyen ni borran desde la interfaz: agrega la evidencia correcta cuando corresponda y conserva la trazabilidad. El cliente con acceso financiero puede descargar los comprobantes propios, pero no agregar archivos a un pago ya registrado.

### 16.3 Descargar un recibo administrativo

En el detalle del pago, **Recibo administrativo → Descargar recibo PDF** genera un documento con folio estable del tipo `SIABUC-REC-0000000042`. La primera emisión conserva una copia de los datos conocidos en ese momento; no reconstruye automáticamente datos históricos anteriores.

Hay una presentación administrativa y una institucional. La del cliente omite notas de pago, identidades del personal y motivos internos de anulación. Una descarga posterior comprueba el estado actual del pago: si fue anulado, el PDF muestra **ANULADO**, conservando sus cantidades originales como antecedente.

**Comprobante adjunto, recibo y estado de cuenta son documentos diferentes.** El adjunto es evidencia aportada; el recibo describe un movimiento registrado; el estado de cuenta reúne situación y movimientos a un corte. El recibo y el estado de cuenta generados por Helpdesk no son CFDI. Un archivo adjunto puede ser un documento externo, pero cargarlo no verifica su naturaleza ni validez fiscal. Ninguno de estos procesos ejecuta una devolución de dinero.

Base documental: [Solicitudes de pago](SOLICITUDES-PAGO.md), [Comprobantes](COMPROBANTES-PAGO.md), [Recibos administrativos](RECIBOS-PAGO.md).

## 17. Portal financiero de la institución

### 17.1 Habilitación y alcance — ADMIN

En **Configuración de soporte → Acceso financiero de clientes**, localiza la institución y selecciona **Habilitar**. Lee y confirma el alcance antes de aceptar. Por defecto el permiso está desactivado y no lo concede el rol BILLING.

La habilitación es **por institución, no por cuenta individual**. Todos sus USER activos podrán consultar sus pólizas, pagos, recibos y comprobantes históricos, además de presentar solicitudes. Revisa primero si los archivos antiguos contienen información que no deba compartirse con esos usuarios.

Revocar impide consultas y descargas posteriores, pero no recupera archivos descargados ni borra lo que alguien ya vio. No autoriza el acceso a finanzas de otra institución ni a rutas administrativas.

### 17.2 Recorrido del cliente

Abre **Finanzas institucionales**. Si está deshabilitado, aparecerá una indicación para contactar a Administración; soporte sigue siendo un módulo independiente.

Cuando está habilitado, revisa el resumen de pólizas —folio, fechas, importes, minutos y estado— y los pagos propios. Abre un pago para consultar comprobantes y recibo institucional. Puedes crear una solicitud pendiente de validación y adjuntar su evidencia, pero no aprobarla.

El saldo contractual pendiente no equivale a minutos disponibles. Si deseas conocer cuánto soporte queda, consulta también **Resumen**. El módulo incluye los estados de cuenta de la institución conforme a su autorización.

**La consulta es institucional:** no se limita automáticamente a una póliza usada como credencial, porque ese nuevo mecanismo de autenticación no está implementado. El menú Licencias permite al cliente consultar las licencias y saldos de su institución, pero no administra activaciones comerciales externas de SIABUC9.

Base documental: [Portal financiero](PORTAL-FINANCIERO.md), [Pólizas y cobranza](POLIZAS-COBRANZA.md), [Estados de cuenta](ESTADOS-CUENTA.md).

## 18. Calendario de parcialidades

### 18.1 Programar compromisos

ADMIN/BILLING abre **Parcialidades** y programa una fecha, importe MXN y descripción sobre una póliza activa. Revisa cuánto está contratado, cuánto ya programaste y cuánto queda. La suma de compromisos no cancelados no puede superar el importe del contrato.

Por ejemplo, $100.03 pueden dividirse en $33.34, $33.34 y $33.35. No se genera un interés ni un cargo adicional: el calendario distribuye compromisos, no aumenta el contrato. Programarlo tampoco crea pagos o minutos.

### 18.2 Relacionar pagos recibidos

Registra o aprueba primero el pago. Después abre la parcialidad y utiliza **Aplicar un pago existente**. Selecciona un pago vigente de la misma póliza e institución e indica el importe que cubre.

Un pago puede cubrir varios compromisos y uno puede recibir varios pagos, sin exceder el dinero del pago ni el compromiso. Un movimiento de importe cero no se aplica como dinero. Aplicar o retirar el vínculo no vuelve a acreditar ni retira horas.

La deuda contractual y el pendiente del calendario pueden diferir si todavía no relacionaste los pagos. No registres un cobro nuevo sólo para hacer coincidir ambas pantallas.

### 18.3 Corregir y recibir avisos

Las fechas e importes guardados no se editan directamente. Retira aplicaciones con motivo, cancela el compromiso y crea el correcto. La cancelación y las aplicaciones retiradas quedan como historial.

Si se anula un pago, deja de cubrir el compromiso y la parcialidad vuelve a pendiente o vencida según corresponda. Se conserva la referencia al pago anulado. Una póliza cancelada deja de generar recordatorios de sus compromisos.

Los estados son **Pendiente**, **Vencido**, **Cubierto**, **Cancelado** y la indicación de institución inactiva. Los recordatorios internos se configuran en el módulo, inicialmente con siete días de anticipación. **Mis avisos de cobro** no es una cola SMTP; funciona con correo pausado y marcar leído afecta sólo a la cuenta actual.

Base documental: [Parcialidades](PARCIALIDADES.md).

## 19. Conciliación bancaria por CSV

### 19.1 Preparar un archivo válido

ADMIN/BILLING puede importar una copia normalizada de ingresos bancarios para relacionarlos con pagos ya registrados. No es una conexión al banco ni una herramienta para generar cargos.

El archivo debe ser UTF-8, separado por comas, de hasta 1 MiB y 1,000 movimientos. Encabezado y orden exactos:

```csv
fecha,referencia,importe,moneda
2026-10-14,TRANSFERENCIA-DEMO-001,1250.50,MXN
2026-10-14,"DEPOSITO, FOLIO DEMO-002",800.00,MXN
```

Usa fechas válidas `AAAA-MM-DD`, referencias estables, importes positivos con punto decimal y máximo dos decimales, sin símbolo monetario ni separador de miles. Excluye cargos, comisiones, cero y negativos. No renombres un Excel a CSV sin exportarlo al formato correcto.

### 19.2 Previsualizar e importar

En **Conciliación bancaria**, escribe un identificador estable de cuenta —por ejemplo, «Cuenta soporte terminación 1234»—, selecciona el CSV y pulsa **Validar y mostrar vista previa**. No uses contraseñas ni números completos innecesarios.

Revisa cuenta, fechas, referencias, cantidades y duplicados. **Confirmar importación** guarda los movimientos después de volver a validar; cambiar archivo o cuenta requiere otra vista previa. Una fila inválida impide importar parcialmente el archivo.

El sistema normaliza datos y evita duplicados mediante cuenta, fecha, referencia, importe y moneda. Si dos ingresos reales tienen todos esos valores idénticos, el formato no los distingue: revisa la referencia bancaria auténtica. No inventes un alias o referencia para burlar el control. Los bytes originales del CSV no quedan guardados como documento: conserva el estado bancario original según tu procedimiento institucional.

### 19.3 Conciliar y corregir

Abre **Ver movimiento** y revisa candidatos. Se proponen pagos vigentes del mismo importe y moneda y no conciliados con otro movimiento. La referencia y la proximidad de fechas ayudan a ordenar, pero no prueban por sí solas la identidad del pago.

Elige el pago, verifica institución y referencia, escribe el motivo/evidencia y confirma. La relación es uno a uno: no divide movimientos ni agrupa varios depósitos para un pago. Si no existe el pago, regístralo primero por el proceso habitual de Cobranza.

**Deshacer conciliación** exige motivo y conserva historial. No anula el pago ni modifica minutos. Si el pago se anula posteriormente, aparecerá una advertencia de revisión; la relación histórica no desaparece por sí sola.

Base documental: [Conciliación bancaria](CONCILIACION-BANCARIA.md).

## 20. Reportes y estados de cuenta

### 20.1 Reportes técnicos — ADMIN

Abre **Reportes**, selecciona institución, administrativo, canal y fechas y pulsa **Consultar reporte**. Revisa casos, atenciones y consumo agrupado; exporta Excel o PDF cuando corresponda. La consulta y la exportación están protegidas por rol.

El selector **Administrativo** de esta pantalla enumera cuentas ADMIN, incluidas las inactivas. Sin ese filtro, el reporte también incluye el trabajo registrado por TECH. El backend admite un identificador TECH en el filtro de la API, pero la interfaz actual no lo ofrece en ese selector; no interpretes esa ausencia como pérdida de sus atenciones.

El resumen diferencia **pendientes**, **resueltos** y **cerrados**. Pendientes incluye Recibido, En atención y Requiere respuesta del cliente; las filas y exportaciones muestran el nombre visible del estado.

Distingue quién figura como responsable del caso de quién registró una atención. Los filtros del listado de casos y los filtros de consumo no representan necesariamente la misma atribución ni la misma fecha. Las atenciones anuladas quedan como antecedente, pero no forman consumo vigente.

La consulta técnica admite hasta 366 días y 5,000 filas combinadas de casos y atenciones, además de límites de contenido y páginas. Si los rebasa, reduce el periodo o filtra; no se debe interpretar un error como un informe vacío.

### 20.2 Reportes de cobranza — ADMIN/BILLING

En **Reportes de cobranza**, filtra institución, póliza, método, moneda, vigentes/anulados y periodo. Después de cambiar filtros, vuelve a consultar. El periodo del pago utiliza la **fecha declarada del pago (`paidOn`)**, no su fecha de captura.

El informe separa monedas y pagos anulados. Una liberación de horas con importe cero puede contar como movimiento y sumar minutos, pero no dinero. Solicitudes pendientes, cotizaciones y comprobantes no se agregan como ingresos.

La sección de **situación actual de pólizas** utiliza todo el historial y los filtros de institución/póliza, no sólo el periodo de pagos. Por eso sus totales pueden diferir del dinero recibido en el mes. Se admiten hasta 3,660 días y 5,000 filas combinadas de pagos/pólizas por consulta, con límites adicionales de exportación.

### 20.3 Generar un estado de cuenta

ADMIN/BILLING abre **Estados de cuenta**; USER lo consulta desde su portal financiero habilitado. El personal selecciona una institución; para USER se fija automáticamente la de su sesión. Selecciona el corte, pulsa **Generar estado de cuenta**, revisa la emisión y descarga el PDF. El folio tiene el formato `SIABUC-EC-##########`.

Generar conserva un documento, no un pago. Una emisión guardada no se edita ni se recalcula cuando cambian datos. Para la situación actual genera otra; para reproducir un documento anterior abre su folio en **Emisiones conservadas**.

Si pierdes conexión al emitir, revisa **Emisiones conservadas** antes de intentar otra emisión. La solicitud utiliza un identificador de reintento; una nueva captura no equivale necesariamente a recuperar la anterior. Los resultados de las regresiones de concurrencia se consultan en [Validación](VALIDACION.md), sin trasladar a esta versión el fallo histórico de 1.8.1.

### 20.4 Comprender las fechas y no mezclar totales

| Pantalla o dato | Fecha relevante |
|---|---|
| Actividad y orden habitual de casos | Última actualización. |
| Desde/Hasta del listado de soporte | Fecha UTC de creación, filtrada localmente. |
| Reporte de pagos | Fecha declarada del depósito/pago. |
| Parcialidades | Fecha comprometida. |
| Estado de cuenta | Registro en la plataforma y corte efectivo. |
| Póliza | Vigencia contractual, separada de la fecha de pagos. |

Si hoy registras un pago con fecha declarada de la semana anterior, entra en el reporte de pagos de esa fecha, pero no aparece artificialmente en un estado de cuenta previo a su registro. Para un corte de hoy se usa el instante de emisión, no el final de un día que aún no concluye.

La apertura histórica de V24 conserva valores conocidos al migrar, sin inventar movimientos detallados anteriores. No se admiten cortes anteriores a su origen temporal. Las monedas se totalizan por separado y el saldo institucional distingue asignado, usado y disponible por canal.

Base documental: [Reportes](REPORTES.md), [Estados de cuenta](ESTADOS-CUENTA.md), [Estados de atención](VERSION-1.8.3.md), [Validación vigente](VALIDACION.md).

## 21. Satisfacción y auditoría

### 21.1 Evaluar atención — USER

Abre un caso cerrado y localiza **¿Cómo fue la atención?**. Selecciona de 1 a 5, añade un comentario opcional de hasta 2,000 caracteres y pulsa **Enviar evaluación**. Es voluntaria y no cambia el saldo.

Se acepta una respuesta por caso para toda la institución, no una por persona. No se edita ni elimina desde la aplicación. Reabrir y cerrar conserva la evaluación anterior. ADMIN no responde en nombre del cliente.

### 21.2 Consultar satisfacción — ADMIN

En **Satisfacción**, filtra fecha de recepción, institución y responsable asignado al momento de responder. Revisa número de muestras, promedio, distribución y comentarios. Un resultado sin respuestas no significa calificación cero.

El promedio describe únicamente las muestras recibidas. La atribución al responsable no evalúa por separado a todas las personas que participaron. La exportación disponible es CSV de respuestas; no existe un PDF específico de satisfacción.

### 21.3 Revisar auditoría

En **Auditoría**, ADMIN consulta actor, acción, entidad, detalle y fecha de operaciones relevantes. El listado presenta los últimos 500 eventos; ese límite no significa que los anteriores hayan sido borrados de PostgreSQL.

Para investigar una diferencia, reúne folio, institución, hora, usuario y operación y compáralos con el historial del objeto. No todas las pantallas usan la misma fecha de negocio: contrasta pagos, cortes y registros antes de concluir que falta información. No modifiques directamente la base para «corregir» la bitácora.

Base documental: [Satisfacción](ENCUESTAS-SATISFACCION.md), [Manual breve](MANUAL-USUARIO.md), [Contrato API](API-CONTRACT.md).

## 22. Correo y notificaciones — ADMIN / operador

### 22.1 Identificar las tres piezas

La **campana** contiene avisos internos por usuario. La **cola de la aplicación** registra mensajes pendientes y su procesamiento. **Postfix**, cuando se utiliza, mantiene otra cola después de aceptar los mensajes de Spring. Que la campana funcione no demuestra entrega de correo.

En **Notificaciones / Correo y notificaciones**, ADMIN puede revisar configuración, cola y detalle del mensaje guardado. La prueba de correo se dirige al correo de la propia cuenta administradora. No introduce destinatarios arbitrarios en esa prueba.

### 22.2 Configurar Gmail directo, otro SMTP o Postfix

La instalación nueva preselecciona Gmail directo (`smtp.gmail.com`, puerto 587, STARTTLS), con **MAIL_ENABLED=false** y sin contraseña. Esto evita iniciar envíos antes de completar la configuración. Las instalaciones anteriores conservan su `.env`.

Para Gmail directo, desde una terminal interactiva:

```bash
bash ./configurar.sh --gmail-directo
```

Introduce la cuenta completa y la contraseña de aplicación en la entrada oculta; confirma el secreto. El asistente configura autenticación, STARTTLS, remitente igual a la cuenta y `MAIL_ENABLED=true`. No comprueba la autenticación ni envía una prueba por sí mismo. Los cambios se aplican al recrear/reiniciar el servicio mediante el procedimiento de la instalación. En Windows, usa `.\configurar.bat --gmail-directo`.

La disponibilidad de contraseñas de aplicación depende de las políticas de la cuenta. No se implementa OAuth interactivo ni se promete que cualquier cuenta institucional lo permita. Para comprobar el mecanismo y las consideraciones del proveedor, consulta [Gmail y UCOL](GMAIL-UCOL.md).

Alternativas, para elegir según la instalación:

```bash
# Spring se conecta directamente al servidor SMTP elegido
bash ./configurar.sh --correo

# Postfix opcional de Docker, con entrega directa o relay
bash ./configurar.sh --smtp-interno
```

No es necesario ejecutar todos los asistentes. `SMTP_HOST=smtp` activa el contenedor Postfix opcional a través de los scripts; una conexión directa a Gmail no lo necesita. `configurar-gmail.sh`/`.bat` corresponde a la alternativa de Gmail mediante Postfix; no lo confundas con `--gmail-directo`.

En 1.8.3 Postfix comparte la red `default` con el backend, sin `smtp_net` ni subred fija. El puerto SMTP no se publica en el despliegue ordinario. El override de desarrollo lo expone sólo en `127.0.0.1:SMTP_INTERNAL_PORT` para Java local.

Antes de pasar de Postfix a SMTP directo, revisa su cola y preserva el volumen hasta decidir qué hacer con mensajes pendientes. Una configuración nueva no vacía ni traslada automáticamente esa cola. Después del cambio valida la aplicación, revisa su cola y realiza la prueba administrativa al correo de tu propia cuenta.

Postfix es saliente: no crea buzones, IMAP, POP3 ni bandeja web. Una dirección de remitente de laboratorio no garantiza aceptación externa. La entrega directa al destinatario requiere las condiciones de red y DNS del entorno; usar relay cambia esa ruta.

Mantén `.env` privado y no lo ejecutes con `source`. El formato admite valores literales con comillas simples conforme al parser del proyecto; no añadas comandos de shell. Para una prueba local sin entrega exterior se puede usar el sumidero Mailpit descrito en [Correo](CORREO.md).

### 22.3 Interpretar estados

| Estado técnico | Significado práctico |
|---|---|
| PENDING | Pendiente de procesamiento. |
| SENDING | Se reclamó para enviar; puede estar en curso. |
| SENT | El primer servidor SMTP lo aceptó. No prueba lectura ni llegada final. |
| FAILED | Falló el intento o se agotó su proceso; revisar diagnóstico. |
| SKIPPED | Se omitió por falta de datos u otra condición registrada. |
| SUPPRESSED | No se enviará por la desactivación administrativa. |

La aplicación reintenta según límites. En ciertas caídas posteriores a la aceptación SMTP puede haber un correo duplicado, sin que eso implique un segundo pago o consumo. Antes de reintentar un aviso, revisa su estado y destinatario; los enviados o suprimidos no se reenvían por la operación normal de reintento.

### 22.4 Pausar sin reenviar acumulados

Usa el control administrativo de desactivación y confirma. Los avisos previos recuperables y los generados durante la pausa quedan **SUPPRESSED**. Al reactivar sólo se enviarán eventos nuevos; la bitácora conserva los suprimidos.

`MAIL_ENABLED=false` es distinto: bloquea el procesamiento por configuración del servidor, pero conserva pendientes que podrían salir al habilitarlo después. Para suprimir el intervalo usa el control de la aplicación.

Un mensaje ya aceptado por Postfix o por otro servidor puede seguir su curso; la pausa de Spring no lo recupera. Pausar no cancela pagos, atenciones ni avisos de la campana. Tras restaurar una base, revisa ambas colas antes de reactivar envíos.

Base documental: [Correo](CORREO.md), [Avisos internos](AVISOS-INTERNOS.md).

## 23. Respaldos y restauración

### 23.1 Qué debe conservarse

| Elemento | Forma de conservación |
|---|---|
| Instituciones, cuentas, hashes, casos y saldos | Respaldo PostgreSQL. |
| Pagos, pólizas, cotizaciones y bitácoras | Respaldo PostgreSQL. |
| Adjuntos de casos y comprobantes | Dentro del respaldo PostgreSQL. |
| Configuración persistente y cola de Spring | Dentro de PostgreSQL. |
| `.env`, credenciales de conexión y certificados | Copia separada con acceso restringido. |
| Cola Postfix `smtp_queue` | Conservación separada del volumen. |
| Archivos `.dump` previos | Copias externas; no se respaldan recursivamente dentro de la base. |
| Código y versión | Paquete fuente compatible y documentación de despliegue. |

Los respaldos contienen datos sensibles y no están cifrados automáticamente por estos scripts. Guarda una copia fuera del equipo y ensaya una restauración en un destino separado. Tener un archivo `.dump` no demuestra por sí mismo recuperación correcta.

### 23.2 Respaldo manual por terminal

Con la base iniciada, desde la carpeta de la instalación correcta:

```bash
bash ./respaldar.sh --nombre-respaldo backups/antes-actualizacion.dump
```

El script crea el `.dump` y un `.sha256`; no sobrescribe un archivo con el mismo nombre. Elige nombres nuevos. El contenido utiliza una instantánea de PostgreSQL y los archivos quedan privados en sistemas que admiten esos permisos.

Para iniciar sólo PostgreSQL cuando está detenido:

```bash
bash ./iniciar.sh --solo-bd
```

No publiques ni adjuntes estos archivos a un repositorio. Usa `bash ./respaldar.sh --help` para revisar opciones antes de aplicarlas.

### 23.3 Configurar respaldos automáticos

El calendario se crea **desactivado** en instalaciones nuevas y al aplicar por primera vez su migración V16. Una actualización posterior conserva el estado que ya guardaste; una restauración recupera el estado incluido en la copia. El worker `backup` puede estar en ejecución sin generar copias programadas cuando el calendario está desactivado.

En `.env`, revisa `BACKUP_DIRECTORY`, `BACKUP_UID` y `BACKUP_GID`. Usa una carpeta privada, no pública ni la raíz del proyecto. Una ruta relativa se interpreta desde la instalación. Inicia mediante los scripts para que preparen el destino cuando corresponde. Comprueba que la identidad del worker pueda escribir en la carpeta y que el disco esté montado; la existencia del directorio no demuestra permiso de escritura. No presupongas que iniciar corrige automáticamente todos los permisos de una carpeta existente.

ADMIN abre **Respaldos**, comprueba destino y disponibilidad del servicio y configura hora, zona, retención y mínimo de copias. Los valores iniciales son 03:00, `America/Mexico_City`, 30 días y siete copias. Confirma el destino y guarda: guardar no activa por sí solo la programación.

Prueba **Crear respaldo ahora** y verifica el estado **Completado**. Después, si procede, pulsa **Activar respaldos automáticos**. Desactivar conserva copias e historial y no impide respaldar manualmente; una copia ya iniciada puede terminar.

### 23.4 Retención y revisión

La limpieza automática se intenta tras un respaldo programado exitoso y respeta el mínimo de copias íntegras presentes. No elimina las copias manuales ni los archivos ajenos. Una retención configurada no garantiza espacio infinito: revisa tamaño, errores y capacidad del destino.

Si cambias carpeta, permisos o disco, vuelve a comprobar el destino y su confirmación. Una copia programada fallida no debe presentarse como respaldo válido. Revisa la pantalla y los registros del worker, sin exponer credenciales.

### 23.5 Restaurar: operación que reemplaza datos

> **Precaución.** Restaurar sustituye la base por el estado del respaldo. Los movimientos posteriores se perderán del destino. Haz primero un ensayo aislado y una copia del estado actual. No uses una copia antigua sobre un esquema posterior sin revisar compatibilidad; para volver a una versión previa se requiere una base nueva compatible.

Después de confirmar el destino y la ventana de servicio, detén cualquier desarrollo nativo y conserva un respaldo actual. El comando de restauración es explícito:

```bash
# Sólo después de verificar destino, copia y autorización de reemplazo
bash ./restaurar.sh \
  --nombre-respaldo backups/copia-a-restaurar.dump \
  --confirmar-reemplazo
```

Indica el `.dump`, no el `.sha256`. Si está su archivo de huella, el script verifica integridad; la huella no prueba la confianza de quien proporcionó el respaldo. El procedimiento detiene servicios de aplicación/correo/worker y los deja detenidos para su revisión posterior.

Antes de reanudar, verifica configuración SMTP, colas y calendario de respaldos recuperado: la copia puede contener programación activada y mensajes pendientes. Para revisar sin iniciar Postfix ni el worker, establece temporalmente `MAIL_ENABLED=false` en el `.env` del destino y arranca solamente estos servicios:

```bash
docker compose up -d --build --wait db backend frontend
```

Accede con una cuenta administrativa incluida en la copia. En **Respaldos**, revisa el calendario restaurado, destino y retención; desactiva la programación si corresponde. Revisa también la pausa y cola de correo de la aplicación y la cola Postfix conservada. Comprueba instituciones, saldos, pagos, casos y archivos conocidos.

Cuando esté preparado el destino, usa `iniciar.sh` o `iniciar.bat` para arrancar los demás servicios seleccionados. Restablece `MAIL_ENABLED=true` sólo cuando proceda reanudar envíos y recrea el backend para aplicar ese valor. Cambiar la contraseña administrativa en `.env` no modifica automáticamente las cuentas restauradas. La guía de [respaldo y restauración](RESPALDO-RESTAURACION.md) desarrolla el ensayo aislado y los pasos de recuperación.

Base documental: [Respaldos](RESPALDO-RESTAURACION.md), [Operación](OPERACION.md), [Correo](CORREO.md).

## 24. Rutinas recomendadas y caso práctico

### 24.1 Rutina del cliente

Al entrar, revisa los avisos y las respuestas pendientes. Consulta el saldo del canal de la licencia que vas a utilizar, o la parte sin licencia, antes de abrir otra solicitud. Mantén un problema identificable por caso, aporta contexto suficiente y continúa el seguimiento en el mismo expediente cuando se trate del mismo asunto. No dupliques solicitudes porque el correo todavía no llegó: revisa primero el folio y su historial.

Después de una atención, comprueba el resultado en SIABUC y registra la información complementaria que necesite soporte. Si se cerró el caso, la encuesta es opcional. Cierra sesión al terminar.

### 24.2 Rutina del técnico

Comienza por «Mis casos pendientes» y los casos urgentes sin responsable. Revisa plazos, institución, historial y archivos antes de iniciar trabajo. Si tomas un caso, verifica la asignación guardada. Registra atención con canal y minutos correctos; separa diagnóstico interno de respuesta pública.

Antes de terminar, pausa o resuelve cronómetros pendientes, revisa citas remotas y deja una respuesta que indique lo realizado y lo que falta del cliente. No cierres automáticamente todos los casos de una incidencia al resolver el problema general.

### 24.3 Rutina de Cobranza

Revisa solicitudes pendientes, compromisos vencidos y renovaciones. Verifica el depósito y los minutos antes de aprobar. Registra el pago una sola vez, aplica el importe a parcialidades cuando corresponda y concilia contra el movimiento bancario disponible.

Comprueba diferencias entre ingreso, deuda contractual, pendiente del calendario y saldo de minutos; no son indicadores intercambiables. Ante una anulación o corrección que requiera ADMIN, documenta el motivo y conserva la evidencia.

### 24.4 Rutina de Administración

Revisa cuentas, responsables y permisos; cola de correo, avisos SLA y resultados de respaldos. Confirma que continúe existiendo un acceso administrativo autorizado. Mantén el contenido público sin datos de clientes ajenos y revisa los archivos históricos antes de habilitar finanzas a una institución.

Antes de actualizar, conserva código, configuración y copia de datos y ensaya restauración. Después, realiza una verificación funcional por rol. Estas rutinas son recomendaciones de uso; no son tareas automáticas agregadas al programa.

### 24.5 Ejercicio de capacitación, sin datos reales

Supuesto ficticio: «Biblioteca de práctica» inicia con cero minutos. ADMIN crea la institución y un USER; crea o utiliza un TECH y BILLING autorizados. Todo se hace en una base desechable.

1. En este ejemplo se utiliza saldo institucional sin licencia. BILLING crea una póliza de $1,200 MXN con 120 minutos de correo y 60 remotos. Comprueba que el saldo todavía sea cero.
2. Registra un pago verificado de $1,200 con acreditación de 120/60 minutos. Consulta el saldo: 120 correo, 60 remoto.
3. USER crea una solicitud por correo y anota el folio. Comprueba que la creación no consuma tiempo.
4. TECH toma el caso si está libre; de Recibido pasa a En atención. Registra una atención pública de 25 minutos por correo. Quedan 95 de correo y 60 remotos.
5. TECH añade una nota interna sin consumo. USER debe seguir viendo el saldo, pero no esa descripción privada.
6. Si los 25 minutos se registraron por error y debieron ser 20, ADMIN anula la atención con motivo y el técnico registra la correcta. Quedan 100 de correo y 60 remotos, con ambos antecedentes visibles según sus permisos.
7. El equipo pasa a Resuelto con motivo y posteriormente a Cerrado. USER consulta el resultado y puede enviar la encuesta; Resuelto por sí solo no la habilita.
8. Si se habilita el portal financiero de práctica, USER revisa su pago y recibo, sin acceder a datos de otra institución.
9. El operador hace una copia y ensaya restauración en otro destino de pruebas. No elimina datos de producción.

Resultado de control: dinero registrado una vez, correcciones trazables, consumo vigente de 20 minutos de correo, bolsa remota intacta y notas internas no divulgadas. Son cantidades de ejemplo, no resultados de una ejecución nueva realizada para este manual.

Base documental para las reglas del ejercicio: [Manual breve](MANUAL-USUARIO.md), [Estados de atención](VERSION-1.8.3.md), [Pólizas y cobranza](POLIZAS-COBRANZA.md), [Portal financiero](PORTAL-FINANCIERO.md), [Satisfacción](ENCUESTAS-SATISFACCION.md), [Respaldos](RESPALDO-RESTAURACION.md).

## 25. Solución de problemas y límites conocidos

### 25.1 Acceso y arranque

| Síntoma | Qué comprobar | Qué evitar |
|---|---|---|
| «Usuario o contraseña incorrectos» | Credenciales, cuenta activa, institución activa y versión de la instalación. Solicitar a ADMIN un restablecimiento si procede. | Cambiar `ADMIN_PASSWORD` esperando que reemplace una cuenta existente. |
| La sesión pide entrar de nuevo | Reinicio de Spring, expiración o contraseña restablecida. | Desactivar CSRF o protección de sesión para evitar identificarse. |
| La web abre pero no carga datos | Salud y logs del backend, base y proxy `/api/`. | Asumir que servir HTML prueba que todo funciona. |
| Otro equipo no abre Angular | IP del anfitrión, `WEB_BIND_HOST`, `FRONTEND_PORT` y conectividad de red. | Usar `127.0.0.1` desde otro equipo o exponer toda la base para resolverlo. |
| Falla la cookie al probar por HTTP | Configuración `COOKIE_SECURE` y protocolo usado. | Desactivar seguridad en una publicación real en vez de preparar HTTPS. |
| Puerto ocupado | Otras instalaciones o servicios, y valores del `.env`. | Borrar la base o reutilizar al azar el proyecto de otra instalación. |
| Falla la descarga de dependencias | Conectividad a registros de imágenes, Maven y npm. | Cambiar dependencias o desactivar verificaciones de integridad sin revisión. |
| TS2345 con `GUIDES` | Que todo el código sea de la misma versión 1.8.3 y no una mezcla de componentes viejos. | Añadir `any`, quitar comprobaciones de plantillas o ampliar categorías técnicas indebidamente. |
| Docker busca un JAR 1.7/1.8.0 | Coherencia de POM, Dockerfile y scripts de la copia utilizada. | Aplicar parches parciales sobre una carpeta de versión desconocida. |

En una compilación paralela, una etapa marcada **CANCELED** puede haber sido interrumpida después del fallo de otra. Identifica primero el bloque **ERROR** y su causa; no atribuyas un fallo independiente a cada cancelación.

### 25.2 Operación diaria

| Situación | Explicación y actuación |
|---|---|
| No permite crear un caso | Comprueba el saldo del canal y licencia seleccionados, la institución activa, campos válidos y revisión previa. |
| No permite crear la primera licencia | ADMIN debe guardar primero las horas predeterminadas, incluso si se desea cero en un canal. |
| Cambié los valores y una licencia antigua no aumentó | Es correcto: el cambio sólo afecta altas nuevas. Usa Agregar horas con motivo para una ampliación administrativa. |
| Hay saldo total, pero mi licencia está agotada | Los saldos están separados. Revisa la licencia del caso; no consume crédito de otra. |
| La edición de valores predeterminados fue rechazada | Otra persona pudo guardar una revisión nueva. Vuelve a cargar, revisa y guarda. |
| No deja agregar comentarios o archivos | El caso puede estar Resuelto o Cerrado, la sesión haber caducado o el archivo incumplir formato/cuota. |
| Un técnico no puede reasignar | Está reservado a ADMIN; tomar un caso libre es una operación distinta. |
| TECH ve el menú de conocimiento pero no entra | Limitación del componente global que persiste en esta versión. Usa las soluciones dentro del caso; no cambies su rol para eludirla. |
| La vista guardada no conserva fechas | Desde/Hasta no forman parte de la vista persistente de esta revisión. |
| Un cronómetro sigue contando al volver | Cerrar la pestaña no lo pausa; revisa antes de confirmar minutos. |
| No se puede continuar el cronómetro | Requiere Recibido o En atención. En espera se conserva pausado; Resuelto/Cerrado requiere reabrir para cobrar. |
| Un caso antiguo aparece Recibido | V26 conserva los antiguos OPEN; el equipo clasifica su situación real después de actualizar. |
| Subir evidencia no quitó la espera del cliente | La transición automática requiere un comentario USER; adjuntar por sí solo no cambia el estado. |
| No aparece encuesta en Resuelto | Se habilita al pasar a Cerrado y sólo admite una respuesta de la institución. |
| El SLA siguió corriendo mientras se esperaba al cliente | Es el comportamiento implementado: la espera pausa cronómetros, no los objetivos laborales. |
| El pago no liberó horas | Puede ser un abono con minutos cero; revisar qué tiempo se acreditó explícitamente. |
| La póliza está vencida y aún hay saldo | La vigencia es informativa; no elimina crédito institucional ya disponible. |
| El calendario muestra deuda distinta | Puede faltar aplicar los pagos existentes a los compromisos. |
| Una referencia de pago se rechaza | Ya puede estar registrada, incluso en un pago anulado. Investiga antes de crear otra referencia. |
| Una emisión de estado de cuenta falla o se interrumpe | Consulta emisiones existentes, conserva el mensaje y su contexto y no asumas éxito ni ausencia de documento. Consulta Validación para la evidencia de regresión vigente. |
| No llegan correos aunque se ven avisos | Campana y SMTP son mecanismos independientes; revisa pausa, configuración, cola y destinatarios. |
| Un aviso dice SENT y no está en bandeja | Sólo acredita aceptación del primer SMTP, no entrega final. Revisar Postfix/proveedor y correo no deseado. |
| No se crean respaldos programados | Verifica activación, destino confirmado, horario, permisos y disponibilidad del worker. |

### 25.3 Recopilar diagnóstico sin exponer secretos

Anota versión, fecha/hora, rol, folio afectado, acción intentada y texto exacto del error. Incluye una captura que no revele contraseñas, sesiones o datos innecesarios. Para el operador:

```bash
cat VERSION
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
```

Revisa y minimiza los logs antes de compartirlos; pueden contener referencias operativas. No compartas `.env`, dumps de producción, cookies `JSESSIONID`, tokens CSRF ni credenciales SMTP. No ejecutes `docker compose config` para enviarlo completo: puede mostrar valores interpolados de configuración.

### 25.4 Funciones que no deben buscarse como si ya existieran

No hay autoservicio público de recuperación por licencia, integración federada UCOL, validación ni administración externa de activaciones SIABUC9, sincronización de descargas oficiales, editor de adjuntos de artículos, tutorial automático de primera visita ni administración granular de roles desde un catálogo editable. El formulario nuevo no contiene aún los catálogos completos de tipo/producto/módulo del borrador.

Tampoco hay una garantía de número máximo de usuarios simultáneos o una validación integral de la instalación productiva. El alcance de pruebas y sus resultados actuales se conserva en [Validación](VALIDACION.md). Este manual no convierte una función no implementada en disponible ni una lectura de código en una prueba productiva.

Véanse las [fuentes de esta edición](#27-fuentes-trazabilidad-y-control-documental) y la [validación vigente](VALIDACION.md).

## 26. Referencia rápida para el operador

### 26.1 Comandos principales

Todos los ejemplos presuponen la carpeta correcta del proyecto y un usuario autorizado a operar Docker. Los comandos de restauración o detención no son pasos de diagnóstico inocuos: revisa su efecto antes de ejecutarlos.

| Objetivo | Linux |
|---|---|
| Revisar versión del código | `cat VERSION` |
| Configurar instalación nueva | `bash ./configurar.sh` |
| Construir imágenes, sin arrancar negocio | `bash ./compilar.sh --docker` |
| Construir/iniciar servicios | `bash ./iniciar.sh` |
| Iniciar sólo PostgreSQL | `bash ./iniciar.sh --solo-bd` |
| Detener conservando volúmenes | `bash ./detener.sh` |
| Ver estado | `docker compose ps` |
| Ver últimos mensajes del servidor | `docker compose logs --tail=100 backend` |
| Copia manual con nombre automático | `bash ./respaldar.sh` |
| Ayuda de restauración | `bash ./restaurar.sh --help` |
| Configurar Gmail directo sin Postfix | `bash ./configurar.sh --gmail-directo` |
| Configurar otro SMTP directo | `bash ./configurar.sh --correo` |
| Configurar Postfix integrado | `bash ./configurar.sh --smtp-interno` |

En PowerShell, utiliza los archivos `.bat` equivalentes con el prefijo `.` seguido de barra inversa. Por ejemplo:

```powershell
.\compilar.bat
.\iniciar.bat
.\respaldar.bat --help
```

Los procedimientos Windows usan Docker Desktop con contenedores Linux; revisa en [Validación](VALIDACION.md) cuáles se ensayaron realmente y cuáles se verificaron mediante simulación. No es necesario ejecutar el modo de desarrollo Angular para servir la interfaz compilada: el despliegue Docker utiliza Nginx.

### 26.2 Configuración que conviene identificar

| Variable | Uso y precaución |
|---|---|
| `COMPOSE_PROJECT_NAME` | Identifica el conjunto de recursos de instalación; conservar al actualizar. |
| `WEB_BIND_HOST` | Interfaz del anfitrión para publicar únicamente el frontend. |
| `BIND_HOST` | Alias heredado de la dirección del frontend si no se define WEB_BIND_HOST. |
| `FRONTEND_PORT` | Puerto de entrada web del anfitrión. |
| `SERVER_PORT` / `DB_PORT` | Puertos de desarrollo/mantenimiento; no publican backend ni base en Compose normal. |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexión de base; cambiar `.env` no cambia la contraseña interna de una base ya creada. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Cuenta de inicialización; no son un mecanismo general para resetear usuarios existentes. |
| `COOKIE_SECURE` | Exige HTTPS para la cookie cuando está activo. |
| `MAIL_ENABLED` | Permite procesamiento SMTP; no equivale a supresión administrativa de avisos. |
| `SMTP_HOST` | `smtp.gmail.com` u otro proveedor para conexión directa; `smtp` para Postfix opcional. |
| `SMTP_NETWORK_SUBNET` | Clave antigua aceptada e ignorada; la red SMTP dedicada fue eliminada. |
| `BACKUP_DIRECTORY` | Destino privado de copias en el anfitrión. |
| `BACKUP_UID`, `BACKUP_GID` | Identidad de acceso al destino de copias, según el sistema. |
| `REPORTING_TIME_ZONE` | Zona de interpretación de reportes; distinta de fechas civiles de pagos y del calendario conservado del caso. |

### 26.3 API: referencia orientativa, no atajo de permisos

| Ruta | Finalidad |
|---|---|
| `GET /api/health` | Salud de la API. No acredita todos los procesos. |
| `GET /api/auth/me` | Cuenta autenticada. |
| `GET /api/cases` | Casos del ámbito autorizado. |
| `GET /api/cases/{id}` | Detalle autorizado y entradas visibles. |
| `POST /api/cases/{id}/status` | Cambio de estado de ADMIN/TECH con motivo. |
| `POST /api/client-portal/tickets` | Alta de solicitud para USER con institución derivada de sesión. |
| `GET /api/knowledge` | Consulta paginada de artículos según permisos. |
| `GET /api/policies` | Gestión financiera de pólizas para ADMIN/BILLING. |
| `GET /api/licenses` | Licencias y saldos según el ámbito de la sesión. |
| `GET/PUT /api/license-support-defaults` | Consultar o guardar dotaciones futuras; ADMIN. |
| `POST /api/licenses` | Registrar licencia y acreditar su dotación inicial; ADMIN. |
| `POST /api/licenses/{id}/hours` | Ampliación administrativa con motivo y reintento idempotente; ADMIN. |
| `GET /api/licenses/{id}/history` | Historial de movimientos de una licencia; ADMIN. |

Las escrituras requieren sesión, CSRF y permisos del servidor. La autorización no se elimina por llamar a una API sin navegador. El contrato técnico está en [API-CONTRACT.md](API-CONTRACT.md). Los identificadores `OPEN`, `IN_PROGRESS`, `WAITING_CLIENT`, `RESOLVED` y `CLOSED` son valores de API; las etiquetas mostradas se traducen. `OPEN` se conserva por compatibilidad y significa Recibido en esta versión.

### 26.4 Glosario breve

**Licencia de soporte:** registro local ligado a una institución con bolsas independientes de correo y remoto. **Dotación inicial:** minutos incluidos que se copian al crearla. **Póliza:** contrato de soporte. **Acreditar:** agregar minutos autorizados a una bolsa mediante el proceso previsto. **Consumir:** registrar tiempo de atención que disminuye el disponible. **Anular:** conservar un movimiento y revertir su efecto permitido con motivo. **Cancelar póliza:** cerrar administrativamente el contrato, sin borrar su historia.

**SLA:** objetivos de respuesta/resolución en tiempo laboral. **CSRF:** protección para comprobar el origen legítimo de una operación de sesión. **SMTP:** mecanismo de envío de correo. **Outbox:** cola persistente de avisos de la aplicación. **Migración:** cambio versionado del esquema de datos. **UUID:** identificador utilizado para reconocer ciertos reintentos. **SHA-256:** huella de integridad de un archivo; no cifra su contenido. **MiB:** unidad de tamaño de 1,048,576 bytes.

Base documental: [Operación](OPERACION.md), [Arquitectura](ARQUITECTURA.md), [Correo](CORREO.md), [Respaldos](RESPALDO-RESTAURACION.md), [Contrato API](API-CONTRACT.md).

## 27. Fuentes, trazabilidad y control documental

### 27.1 Versión y alcance de esta edición

Este manual corresponde a la base `VERSION=1.8.3` con la ampliación de licencias, migraciones V1-V27, flujo de cinco estados y Docker sin red SMTP dedicada. Se construyó a partir del manual 1.8.3, conservando los procedimientos de los módulos que siguen vigentes e integrando las reglas de licencias. La [PR #19](https://github.com/rodolfo99/helpdesk-siabuc/pull/19) identifica los cambios y sus comprobaciones. Los informes anteriores de validación conservan su alcance histórico; no prueban esta ampliación.

La fecha del documento no implica que una revisión concreta ya esté fusionada o desplegada. La identificación de commits, ejecuciones y resultados está en [VALIDACION.md](VALIDACION.md) y en la PR de la entrega. El manifiesto del ZIP identifica sus archivos; una huella de otro paquete no se utiliza para certificar éste.

El [manual 1.8.1](MANUAL-EXTENDIDO-1.8.1.md), los informes históricos y las notas de versión conservan contexto de sus fechas. Sus dos estados y advertencias de pruebas anteriores no son la especificación de 1.8.3.

### 27.2 Documentación complementaria

| Tema | Guías del proyecto |
|---|---|
| Inicio y alcance | [Manual breve](MANUAL-USUARIO.md), [Versión 1.8.3](VERSION-1.8.3.md), [Validación](VALIDACION.md) |
| Instalación | [Operación](OPERACION.md), [Windows](WINDOWS.md), [Arquitectura](ARQUITECTURA.md) |
| Roles y atención | [Técnico](ROL-TECNICO.md), [SLA](CLASIFICACION-SLA.md), [Cronómetro y agenda](CRONOMETRO-AGENDA.md) |
| Bandejas y trabajo repetitivo | [Avisos internos](AVISOS-INTERNOS.md), [Vistas y alertas](VISTAS-ALERTAS.md), [Plantillas](PLANTILLAS-RESPUESTA.md) |
| Ayuda y coordinación | [Conocimiento](BASE-CONOCIMIENTOS.md), [Incidencias](INCIDENCIAS.md), [Satisfacción](ENCUESTAS-SATISFACCION.md) |
| Licencias y dotación inicial | [Licencias y horas](LICENCIAS-HORAS.md) |
| Oferta y contratación | [Paquetes](PAQUETES-TARIFAS.md), [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md), [Pólizas](POLIZAS-COBRANZA.md) |
| Cobro y evidencia | [Solicitudes de pago](SOLICITUDES-PAGO.md), [Comprobantes](COMPROBANTES-PAGO.md), [Recibos](RECIBOS-PAGO.md), [Parcialidades](PARCIALIDADES.md) |
| Consulta financiera | [Portal cliente](PORTAL-FINANCIERO.md), [Conciliación CSV](CONCILIACION-BANCARIA.md), [Estados de cuenta](ESTADOS-CUENTA.md) |
| Informes | [Reportes técnicos](REPORTES.md), [Reportes de cobranza](REPORTES-COBRANZA.md) |
| Correo | [Configuración](CORREO.md), [Gmail](GMAIL-UCOL.md), [Postfix](SMTP-INTERNO.md), [Pausa administrativa](DESACTIVAR-CORREO.md) |
| Protección de datos | [Respaldo/restauración](RESPALDO-RESTAURACION.md), [Copias automáticas](RESPALDOS-AUTOMATICOS.md) |
| Integraciones técnicas | [Contrato API](API-CONTRACT.md) |

### 27.3 Fuentes verificables de las reglas

Las rutas de este cuadro parten de la raíz del proyecto. No contienen datos privados de una instalación.

| Regla | Fuente |
|---|---|
| Permisos y sesiones | `backend/src/main/java/mx/siabuc/helpdesk/SecurityConfig.java`, `HelpdeskService.java`, `frontend/src/app/app.component.ts` |
| Estados y cambios automáticos | `Models.java`, `HelpdeskService.java`, `frontend/src/app/case-status.ts`, `cases.component.html` |
| Migraciones compatibles | `V26__case_workflow_states.sql` y `V27__license_support_balances.sql`, en `backend/src/main/resources/db/migration/` |
| Licencias y minutos incluidos | `LicenseService.java`, `LicenseController.java`, `license-defaults.component.ts`, `licenses.component.ts` y `LicenseIntegrationIT.java` |
| Plazos y cronómetros | `CaseProfileService.java`, `WorkTimerService.java`, `case-timer.component.ts` |
| Agenda | `RemoteSessionService.java`, `remote-sessions.component.ts` |
| Archivos | `AttachmentService.java`, `PaymentAttachmentService.java`, `PaymentRequestService.java` |
| Cobranza | `PolicyService.java`, `QuoteService.java`, `InstallmentService.java`, `BankCsv.java`, `BankReconciliationService.java` |
| Documentos financieros | `PaymentReceiptService.java`, `InstitutionStatementService.java`, `client-finance.component.ts` |
| Informes | `ReportService.java`, `BillingReportService.java`, `SatisfactionService.java` |
| Conocimiento y plantillas | `KnowledgeService.java`, `knowledge-base.component.ts`, `ReplyTemplateService.java`, `IncidentService.java` |
| Instalación y correo | `docker-compose.yml`, `docker-compose.dev.yml`, `scripts/manage.py`, `smtp/entrypoint.py` |
| Copias automáticas | `backup/worker.py`, servicios de respaldos y `scripts/manage.py` |

Los servicios Java citados sin ruta completa están en `backend/src/main/java/mx/siabuc/helpdesk/`; los componentes Angular, en `frontend/src/app/`.

### 27.4 Mantener el manual actualizado

Al modificar comportamiento, actualiza la guía de módulo y este manual en la misma entrega. Comprueba nombres de controles, permisos del servidor, migraciones y scripts; no sustituyas una verificación por el nombre de una versión. Mantén la evidencia histórica con su fecha y enlaza la validación vigente.

Para una distribución nueva, verifica que el paquete incluya este manual y que los enlaces relativos apunten a archivos presentes. Evita incluir `.env`, contraseñas, copias de datos o logs operativos. El ZIP fuente no sustituye al respaldo de una instalación.

**Fin del manual.**
