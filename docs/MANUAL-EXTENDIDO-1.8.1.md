# Manual extendido de uso de SIABUC Helpdesk 1.8.1

> **Documento histórico — manual de 1.8.1 del 14 de octubre de 2026.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.3.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.3](MANUAL-EXTENDIDO-1.8.3.md), el [índice de documentación](README.md) y las [notas de 1.8.3](VERSION-1.8.3.md).

**Cliente institucional · Soporte técnico · Cobranza · Administración**  
Edición documental: 14 de octubre de 2026.  
Base: paquete `SIABUC-Helpdesk-1.8.1.zip` entregado en esta conversación.

> **Estado de la entrega.** Este manual describe funciones presentes en el código y los procedimientos incluidos en el paquete. La compilación de Angular y Java está documentada; la suite Java registra 223 resultados satisfactorios y dos fallidos de 225. No es una certificación de operación productiva ni una aprobación de los requerimientos institucionales preliminares.

## 1. Cómo utilizar este manual

### 1.1 Objetivo y alcance

SIABUC Helpdesk centraliza solicitudes de soporte, instituciones, cuentas, tiempo de atención, pólizas y registros de cobranza. Permite seguir una solicitud desde su creación hasta su cierre y conservar la evidencia de las atenciones, comentarios, pagos y correcciones. El nombre «póliza» identifica aquí un contrato de soporte: no significa que el sistema ya administre claves de licencia SIABUC9.

Este manual reúne los procedimientos dispersos de las versiones anteriores y los ajusta al paquete 1.8.1. Los nombres de opciones se contrastaron con el cliente Angular entregado. Cuando una guía antigua y la interfaz no coinciden, se indica la limitación; no se presenta una función de API como si necesariamente tuviera una pantalla completa.

Los ejemplos de instituciones, importes, folios, referencias e IP son ficticios. Deben sustituirse por los datos autorizados de cada instalación. Los ejemplos monetarios explican el comportamiento del programa; no son tarifas comerciales oficiales.

Las referencias **[S01]–[S28]** identifican documentos o archivos del paquete y se detallan al final. Las referencias **[E01]–[E02]** corresponden a documentación oficial de Docker consultada para el apartado de red. Este documento no modifica la aplicación, la base de datos ni el documento preliminar de requerimientos.

### 1.2 Ruta de lectura según tu actividad

| Actividad | Secciones recomendadas |
|---|---|
| Soy cliente y necesito solicitar soporte | 2, 4, 6, 8, 10, 12 y 17 |
| Soy técnico y atenderé solicitudes | 2 y 7–13; consultar también 24 y 25 |
| Trabajo en Cobranza | 2, 14–20, 21 y 25 |
| Administro cuentas y configuración | 3–5, 9–13, 17 y 21–26 |
| Instalo, actualizo o mantengo el servidor | 1, 3, 22, 23, 25 y 26 |

### 1.3 Qué incorpora 1.8.1 y qué no

La revisión conserva las funciones de 1.7 e incorpora las mejoras parciales de 1.8: creación de solicitudes cliente con validación específica y revisión previa; confirmación con folio; acceso a ayuda desde el encabezado del cliente; guía manual; categorías editoriales adicionales y filtros locales de fechas para el personal. También corrige el conflicto de categorías `TS2345` y la referencia al nombre del JAR en Docker.

**No están disponibles como funciones completas:** autenticación por número de póliza y correo, Federación de Identidades UCOL, recuperación pública mediante clave de licencia, cambio verificado del correo de licencia, administración de licencias SIABUC9, servicios adicionales ligados a ellas, catálogo oficial de descargas integrado, cinco estados de tickets ni tutorial automático por primer ingreso. La publicación institucional con HTTPS y Google Analytics tampoco queda configurada por instalar el ZIP.

El acceso continúa con **usuario y contraseña**. Los casos mantienen **Abierto/Cerrado**. El tutorial se abre manualmente. Abrir o reabrir un caso sigue requiriendo saldo del canal correspondiente. Los clientes consultan el ámbito de su institución, no un ámbito nuevo de autorización por póliza.

### 1.4 Advertencias de validación

| Comprobación documentada | Resultado y alcance |
|---|---|
| Angular | Compilación completa y pruebas del frontend satisfactorias. No equivale a navegación integral por todas las pantallas. |
| Java | 225 pruebas: 10 unitarias y 215 de integración; 223 satisfactorias y dos fallidas. PostgreSQL 17 nativo y Flyway en el entorno de CI. |
| Scripts | 49 pruebas de operación y 13 de SMTP satisfactorias en la verificación local documentada. |
| Distribución | ZIP, CRC y manifiesto SHA-256 verificados. La configuración privada y los datos de una instalación no forman parte del código. |
| Pendiente | Arranque Docker Compose completo, Windows nativo, despliegue productivo, navegación integral y restauración en el destino. |

Los fallos registrados son `HelpdeskIntegrationIT.lastAdministratorCannotBeDeactivated` —la prueba esperaba una cuenta administradora activa y encontró 15— e `InstitutionStatementIntegrationIT.concurrentDuplicateEmissionReturnsOneDocumentOrExplicitRetryConflict` —recibió HTTP 503 donde esperaba 200 o 409—. El primero requiere revisar aislamiento y protección; el segundo requiere investigar concurrencia. No deben declararse irrelevantes sin revisión.

**Recomendación de operación:** utiliza primero una instalación de pruebas y no tu única base importante. En particular, evita ensayos de desactivación de administradores y emisiones simultáneas de estados de cuenta sobre datos operativos hasta resolver los fallos.

Base documental: [S01], [S28].

## 2. Roles, permisos y navegación

### 2.1 Los cuatro tipos de cuenta

**ADMIN — Administración.** Gestiona instituciones y cuentas, supervisa soporte y cobranza, consulta auditoría, configura correo, horarios, alertas, acceso financiero y respaldos. Es el único rol que puede anular consumos o pagos.

**TECH — Técnico de soporte.** Trabaja con casos de todas las instituciones: consulta datos técnicos, toma casos libres, registra atención, usa cronómetro y agenda y cierra o reabre. No administra cuentas, dinero, respaldos ni configuración global. Puede atender un caso asignado a otra persona, pero no cambiarle el responsable.

**BILLING — Cobranza.** Opera pólizas, pagos, cotizaciones, renovaciones, solicitudes de pago, paquetes, parcialidades, conciliación, reportes financieros y estados de cuenta. No obtiene acceso a expedientes de soporte ni notas técnicas internas por tener facultades financieras.

**USER — Cliente institucional.** Consulta saldos e historial de su institución, abre solicitudes y agrega comentarios públicos. Las funciones financieras sólo están disponibles cuando Administración habilita expresamente el portal de su institución.

### 2.2 Matriz de permisos operativos

| Función | ADMIN | TECH | BILLING | USER |
|---|---|---|---|---|
| Consultar saldos institucionales | Todas | Todas | Todas | Su institución |
| Crear/editar instituciones y cuentas | Sí | No | No | No |
| Consultar casos y ficha técnica | Todos | Todos | No | Su institución |
| Abrir caso con saldo | Sí | Sí, propio responsable | No | Su institución |
| Agregar comentario público | Sí | Sí | No | Su institución |
| Notas internas y consumo de minutos | Sí | Sí | No | No |
| Tomar caso sin responsable | Sí | Sí | No | No |
| Reasignar caso | Sí | No | No | No |
| Cerrar/reabrir | Sí | Sí | No | No |
| Anular consumo | Sí | No | No | No |
| Operación financiera administrativa | Sí | No | Sí | No |
| Anular pago | Sí | No | No | No |
| Habilitar finanzas al cliente | Sí | No | No | No |
| Consulta financiera propia | Supervisión | No | Gestión | Si se habilita |
| Crear/publicar artículos | Sí | No | No | No |
| Vincular soluciones publicadas al caso | Sí | Sí | No | No |
| Incidencias y plantillas de respuesta | Gestiona ambas | Opera incidencias; usa plantillas | No | Sólo avisos públicos en sus casos |
| Auditoría, configuración y respaldos | Sí | No | No | No |
| Cambiar contraseña propia | Sí | Sí | Sí | Sí |

**El aislamiento es institucional.** Dos clientes de la misma institución comparten sus casos, saldos y, cuando está habilitado, el portal financiero. No hay privacidad individual entre esos usuarios. Una cuenta USER no puede cambiar su institución mediante el formulario de una solicitud.

La asignación de responsable organiza el trabajo, pero no convierte el caso en exclusivo de ese técnico. ADMIN y TECH tienen alcance técnico global. Los permisos de consulta de una institución no implican autorización para realizar operaciones financieras.

### 2.3 Mapa de la interfaz

El menú lateral cambia según el rol. Las opciones disponibles incluyen **Resumen**, **Casos de soporte**, **Base de conocimientos**, **Finanzas institucionales**, **Pagos**, **Pólizas**, **Reportes de cobranza**, **Cotizaciones**, **Validar pagos**, **Renovaciones**, **Incidencias generales**, **Plantillas**, **Paquetes y tarifas**, **Parcialidades**, **Conciliación bancaria**, **Estados de cuenta**, **Satisfacción**, **Respaldos**, **Instituciones**, **Usuarios**, **Auditoría**, **Notificaciones** y **Configuración de soporte**.

En **Configuración de soporte**, ADMIN encuentra el horario/SLA, avisos de vencimiento, avisos de saldo y el acceso financiero de clientes. El perfil de la cuenta abre **Mi cuenta**. La campana del encabezado es una bandeja de avisos distinta del apartado administrativo de correo.

En pantallas pequeñas, abre la navegación con el botón de menú. Algunas tablas amplias conservan desplazamiento horizontal interno; no debe suponerse que todos sus campos caben a la vez en un teléfono. La navegación del paquete se realiza principalmente mediante botones de la aplicación: no se documentan enlaces directos independientes para cada sección.

Base documental: [S02], [S08], [S18].

## 3. Instalación, actualización y acceso por red

### 3.1 Requisitos del equipo que hospeda el sistema

Para el despliegue con los scripts Docker se requieren Python 3.10 o posterior, Docker en funcionamiento y el complemento Docker Compose. Java, Maven, Node y Nginx se obtienen dentro de las imágenes; no es necesario instalarlos en el anfitrión para ese recorrido. En Windows se utilizan los `.bat` con Docker Desktop en modo de contenedores Linux.

La compilación descarga dependencias e imágenes. En el ZIP 1.8.1, Maven también descarga los recursos tipográficos PDF del repositorio fijado y verifica su SHA-256. Se necesita conectividad hacia los registros de imágenes, Maven, npm y el origen HTTPS señalado en el proyecto. Esto no implica que la aplicación tenga que consultar licencias o servicios institucionales para operar sus módulos locales.

### 3.2 Instalación nueva de pruebas en Linux

Descomprime el ZIP en una carpeta nueva y abre una terminal en `siabuc-helpdesk`, donde están `VERSION`, `docker-compose.yml` e `iniciar.sh`.

```bash
python3 --version
docker info
docker compose version
cat VERSION
bash ./configurar.sh
```

Comprueba que `VERSION` sea `1.8.1`. Revisa el `.env` generado con un editor privado: puertos, credenciales de la base, cuenta inicial y correo. Para un laboratorio sin destinatarios reales, configura `MAIL_ENABLED=false` antes de arrancar. No publiques el contenido de `.env` en capturas, mensajes o repositorios.

```bash
bash ./compilar.sh --docker
bash ./iniciar.sh
```

El primer comando construye imágenes sin iniciar los servicios de negocio ni aplicar migraciones a una base. El segundo construye cuando corresponde, inicia los servicios y espera sus comprobaciones de salud. Por defecto, el navegador entra a `http://127.0.0.1:4200`; utiliza el valor de `FRONTEND_PORT` si lo cambiaste.

Los servicios normales son `db` —PostgreSQL—, `smtp` —Postfix saliente—, `backend` —Spring—, `frontend` —Angular servido por Nginx— y `backup` —worker de copias—. El worker iniciado no significa que los respaldos programados estén activados.

### 3.3 Actualizar conservando información

El `.env` no es una copia de la base. En el mismo equipo y con el mismo Docker, la continuidad depende de conservar los volúmenes y la identidad de proyecto. Mantén `COMPOSE_PROJECT_NAME`, credenciales, puertos y la ubicación de respaldos. La ruta relativa `BACKUP_DIRECTORY=./backups/automatic` cambia de destino al mover la carpeta de código; utiliza la ruta correcta o una absoluta.

Procedimiento recomendado una vez revisados los límites de 1.8.1:

1. Ensaya la nueva versión sobre una base de pruebas. Conserva el código anterior y una copia de la configuración.
2. En la carpeta anterior y con la base iniciada, genera un respaldo con un nombre nuevo. Guarda `.dump` y `.sha256` fuera del equipo.
3. Descomprime el código nuevo en otra carpeta. Copia allí el `.env` existente sin regenerar contraseñas.
4. Revisa el destino de respaldos y cualquier modificación local de red. Compila con `bash ./compilar.sh --docker`.
5. En una ventana sin escrituras de usuarios, detén la instalación anterior con `bash ./detener.sh`.
6. En la carpeta nueva, ejecuta `bash ./iniciar.sh`. Verifica acceso, instituciones, saldos, un caso, un pago y un archivo conocido.

No ejecutes `docker compose down -v` ni borres volúmenes para corregir una compilación. Desde 1.7 se agregará V25; desde la rama 1.8 corregida no hay una migración adicional de esquema para 1.8.1. No modifiques archivos de migración ya aplicados. Reemplazar el JAR no deshace una actualización de base; una reversión requiere una copia compatible y un destino adecuado.

### 3.4 Abrir Angular desde otra computadora

En el paquete, `BIND_HOST` se usa para publicar frontend, backend y PostgreSQL. **No lo cambies globalmente a `0.0.0.0` sólo para mostrar la web.** Mantén la publicación de los servicios internos en localhost y ajusta únicamente el bloque `ports` de `frontend`.

Primero identifica la IP LAN del anfitrión:

```bash
ip -br -4 address
```

Ejemplo ficticio para un anfitrión con IP `192.168.1.50`:

```yaml
  frontend:
    build:
      context: ./frontend
    ports:
      - "192.168.1.50:${FRONTEND_PORT:-4200}:80"
```

Conserva las otras propiedades del servicio. Aplica el cambio en la instalación de pruebas y abre desde el otro equipo `http://192.168.1.50:4200`. El navegador debe alcanzar la IP y el puerto; en un equipo remoto, `127.0.0.1` representa a ese equipo, no al servidor.

Nginx reenvía `/api/` hacia Spring dentro de Docker, por lo que no es necesario exponer 8080 ni 5432 a los clientes web. Publicar el puerto permite conexiones desde las redes que puedan alcanzarlo; no es una lista de acceso por usuario ni configura HTTPS. Docker advierte que los puertos publicados pueden evitar las reglas habituales de UFW: coordina las restricciones de red con quien administre el anfitrión. [E01], [E02]

Para datos reales a través de redes no confiables, prepara un despliegue con HTTPS y controles institucionales. `COOKIE_SECURE=true` requiere acceso HTTPS para la cookie de sesión; no habilita por sí solo un certificado. No publiques esta entrega experimental en Internet como si fuera una instalación validada.

### 3.5 Comprobar el arranque

Ejecuta los comandos desde la carpeta de la instalación correcta:

```bash
docker compose ps
docker compose logs --tail=100 backend frontend
```

Una construcción satisfactoria no garantiza que PostgreSQL, las migraciones o el servidor estén saludables. No confíes únicamente en que aparezca la página de login: prueba una cuenta y una consulta autorizada. No ejecutes pruebas automatizadas contra tu base real.

Base documental: [S03], [S04], [S26], [S01].

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

Base documental: [S02], [S05].

## 5. Preparación de instituciones y usuarios — ADMIN

### 5.1 Alta de institución

En **Instituciones**, abre el formulario de nueva institución. Captura nombre, contacto, correo, teléfono y notas administrativas. Revisa el registro existente antes de crear otro con un nombre parecido: duplicar una institución puede dividir accidentalmente sus saldos e historial.

Configura por separado los presupuestos de **Correo** y **Escritorio remoto**. El formulario admite horas y minutos; el componente de minutos va de 0 a 59. El valor persistente es un entero en minutos, no una cantidad decimal de horas.

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

**No dupliques la acreditación.** Si las horas se adquieren por un pago, utiliza el módulo financiero para acreditarlas y no aumentes además el presupuesto por esa misma compra. La aplicación impide fijar un total asignado inferior al utilizado.

### 5.3 Crear y administrar cuentas

En **Usuarios**, captura identificador, nombre visible, contraseña, rol, estado y correo. USER requiere institución; ADMIN, TECH y BILLING no deben tener una institución cliente asignada. Decide bien el ámbito antes del alta.

El identificador, rol e institución son inmutables mediante la edición normal. Para cambiar el ámbito de una persona, crea una cuenta con la configuración correcta y desactiva la anterior. Sus registros históricos conservan la autoría. Al editar, deja la contraseña vacía para mantenerla o introduce otra para restablecerla.

La protección del último ADMIN existe en el código, pero su prueba no pasó en el conjunto de 1.8.1. No pruebes la desactivación de la única cuenta administradora sobre tu instalación real; conserva acceso administrativo y revisa el fallo en un entorno aislado.

### 5.4 Baja y reactivación institucional

La baja es lógica: bloquea la operación del cliente y evita casos o consumos nuevos, sin borrar antecedentes. El equipo autorizado conserva consulta histórica. Para reactivar, edita la institución y cambia su estado. La reactivación no implica cambiar contraseñas, generar pagos ni renovar pólizas.

Base documental: [S02], [S08], [S16], [S28].

## 6. Recorrido del cliente: crear y seguir una solicitud

### 6.1 Revisar el resumen

Después de entrar, consulta **Resumen**. Se muestran casos abiertos/cerrados, saldos y actividad reciente. La guía de primeros pasos se despliega manualmente desde ese resumen; no se guarda una condición de «primera visita» por usuario o póliza.

En esta versión, pulsar una tarjeta de actividad reciente abre el listado de casos, no necesariamente ese folio. Utiliza **Casos de soporte** y la búsqueda por número para llegar al expediente concreto. La campana sí ofrece navegación a los casos relacionados con sus avisos.

### 6.2 Capturar una solicitud nueva

Entra a **Casos de soporte → Nuevo caso**. La institución del cliente proviene de la sesión. No es necesario volver a introducir tu identidad ni elegir otro centro.

| Campo | Cómo llenarlo |
|---|---|
| Canal | Correo o Escritorio remoto. Debe tener saldo disponible. |
| Categoría | Instalación, Catalogación, Préstamos, Reportes, Importación/exportación u Otro. |
| Prioridad | Baja, Normal, Alta o Urgente, según el impacto real. |
| Título del caso | Entre 5 y 150 caracteres en el flujo cliente nuevo. Describe el problema de forma identificable. |
| Descripción | Entre 20 y 5,000 caracteres. Incluye situación, pasos, resultado observado y resultado esperado. |
| Ficha técnica opcional | Versión de SIABUC, sistema operativo, error y pasos para reproducirlo. |

Ejemplo ficticio de título: **«La importación no incorpora registros con ISBN»**. En la descripción, indica el archivo usado, la secuencia de acciones, el mensaje exacto y cuándo comenzó. No copies contraseñas ni datos de otras instituciones.

Pulsa **Revisar solicitud**, revisa el contenido mostrado y después **Confirmar y crear caso**. Si cambias título o descripción, vuelve a revisar. El botón **Cancelar** cierra el formulario sin crear la solicitud. Los archivos se agregan después de crear el caso; no hay un envío inicial de cinco adjuntos integrado en este formulario.

**Resultado esperado:** aparece la confirmación con el folio y se abre el detalle. Se crea el caso abierto y su ficha, pero no se descuentan minutos por enviarlo. Guarda el folio para futuras consultas.

El flujo nuevo utiliza `/api/client-portal/tickets` y valida sus longitudes en el servidor. El contrato anterior `/api/cases` permanece por compatibilidad, con sus límites anteriores; no se ha unificado toda integración externa bajo las validaciones nuevas.

### 6.3 Consultar, comentar y aportar evidencia

En **Casos de soporte**, busca por folio, título o descripción y filtra por estado, categoría o prioridad. El orden es por última actualización, no estrictamente por fecha inicial de creación. Abre el caso y revisa descripción, responsable e historial.

Para aportar información, escribe un comentario en un caso abierto y confirma. El cliente sólo publica comentarios visibles para la institución y sin consumo; no puede crear notas internas, registrar minutos, cerrar o reabrir por sí mismo. Las notas privadas del equipo no se entregan al portal cliente.

Abre **Adjuntos del caso** para cargar evidencia general. Desde el detalle de una entrada del historial puedes consultar sus archivos autorizados. El apartado **Ficha técnica y plazos de atención** permite completar datos de un caso abierto; la ficha no reemplaza una respuesta dentro del historial.

### 6.4 Entender quién debe responder

El estado del caso sigue siendo Abierto o Cerrado. Por separado, la campana indica conversaciones pendientes del equipo o de la institución. Una respuesta pública de soporte deja la continuación del lado del cliente; un comentario del cliente la devuelve al equipo. Leer un aviso no equivale a responder ni a cerrar el caso.

Si ya no hay saldo, puedes consultar antecedentes autorizados, pero no abrir otro caso en un canal agotado. Solicita a Administración/Cobranza la revisión correspondiente. Que una póliza aparezca vencida no implica por sí mismo que sus minutos ya acreditados hayan desaparecido.

Base documental: [S05], [S06], [S07], [S09], [S16].

## 7. Atención de casos — ADMIN y TECH

### 7.1 Localizar, tomar y asignar trabajo

Abre **Casos de soporte**. Combina filtros de institución, Abiertos, categoría, prioridad y asignación. **Sin responsable** ayuda a localizar trabajo pendiente; **Mis casos** muestra la asignación de tu cuenta.

Para tomar un caso, abre uno libre, abierto y de institución activa y pulsa **Tomar caso**. El sistema registra responsable, historial y aviso según la configuración. No consume minutos. Si otra persona se adelantó, recarga y revisa la asignación vigente.

ADMIN puede asignar o reasignar a otro ADMIN/TECH activo mediante la edición del caso. TECH puede tomar uno libre y atender expedientes de otras asignaciones, pero no reasignarlos. Cuando un técnico crea un caso, queda como responsable de ese nuevo caso. Un cambio del responsable no transfiere propiedad institucional.

### 7.2 Registrar atención o comentario

Desde el detalle de un caso abierto, utiliza el formulario de seguimiento. Elige **Comentario** para una comunicación sin cargo; para una atención con consumo, elige Correo o Escritorio remoto y escribe las horas/minutos reales que vas a registrar. Describe el trabajo realizado de forma suficiente para una revisión posterior.

Antes de confirmar, comprueba **caso, institución, canal, minutos y visibilidad**. Una atención puede usar un canal distinto al canal inicial del caso, siempre que disponga de saldo en la bolsa que se cargará. La separación de bolsas se mantiene: minutos de correo no se toman automáticamente de remoto.

Al registrar una atención válida, el historial y el descuento se guardan conjuntamente. Si el saldo es insuficiente o el caso está cerrado, la operación se rechaza sin dejar un descuento parcial. La pérdida de conexión exige revisar el historial antes de volver a registrar el mismo trabajo.

### 7.3 Notas internas y privacidad

Marca **nota interna** sólo para información de trabajo que no deba ver el cliente. ADMIN y TECH pueden consultarla; USER no. El tiempo de una atención interna sigue formando parte del consumo del caso y del saldo institucional, aunque su descripción no sea pública.

No conviertas una nota en pública sin revisar su texto y archivos. Los avisos internos no deben enviarse al contacto del cliente. No escribas secretos de acceso en notas, plantillas o incidencias: su carácter interno no sustituye un sistema de custodia de credenciales.

### 7.4 Corregir una atención

La corrección de consumo se realiza mediante **Anular atención**, reservada a ADMIN. Selecciona la atención correspondiente, introduce el motivo y confirma. El registro permanece visible como anulado y devuelve una sola vez los minutos de su canal. Los comentarios de cero minutos no se anulan por esta vía.

Si procede registrar el tiempo correcto, crea una nueva atención con explicación que permita relacionarla con la anulada. No borres antecedentes ni ajustes manualmente el presupuesto para esconder un error de consumo.

### 7.5 Cerrar y reabrir

ADMIN o TECH puede **Cerrar caso**, introduciendo un motivo. El cierre conserva el historial y detiene nuevas entradas ordinarias hasta una reapertura. Antes de cerrar, revisa si quedaron cronómetros por confirmar o citas remotas pendientes; el cierre no los liquida ni cancela automáticamente.

**Reabrir caso** exige institución activa y saldo en el canal inicial. Introduce el motivo. Se reinicia el objetivo de resolución desde la reapertura y se conserva la primera respuesta original. El cliente no puede forzar una reapertura mediante un comentario en una pantalla antigua.

Base documental: [S02], [S05], [S06], [S08].

## 8. Adjuntos y evidencias de soporte

### 8.1 Cargar y descargar archivos

En un caso abierto, entra a su apartado de adjuntos, selecciona el archivo y pulsa **Adjuntar archivo**. El listado muestra nombre, tamaño, autor y fecha. Para descargarlo, utiliza **Descargar** junto al archivo. Adjuntar no registra tiempo de atención.

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

Un caso cerrado conserva descargas autorizadas, pero no permite nuevos adjuntos hasta reabrirlo. Las evidencias no se eliminan desde el formulario ordinario. Si cargaste un archivo equivocado, informa al administrador antes de intentar una corrección; no supongas que existe un botón de borrado o sustitución.

Los bytes están almacenados en PostgreSQL. El respaldo de la base incluye la evidencia; el ZIP de código y el `.env` no la contienen.

Base documental: [S07], [S19], [S26].

## 9. Ficha técnica, prioridades y plazos de atención

### 9.1 Completar la ficha

Desde **Ficha técnica y plazos de atención**, registra categoría, prioridad, versión de SIABUC, sistema operativo, mensaje de error y pasos reproducibles. Usa el error exacto, pero elimina secretos innecesarios. ADMIN, TECH y clientes autorizados de la institución pueden intervenir conforme a los permisos del caso abierto; BILLING no participa en esa ficha.

Las seis categorías técnicas del caso son independientes de las doce categorías editoriales de la base de conocimientos. Elegir «Guías» o «Pólizas y licencias» en un artículo no agrega esas opciones al formulario del caso.

### 9.2 Interpretar el SLA

El SLA es el objetivo de tiempo laboral para responder y resolver. **No es consumo facturable.** Que transcurra una hora o venza un plazo no descuenta una hora de la póliza.

| Indicador | Lectura operativa |
|---|---|
| Dentro del plazo | El objetivo sigue pendiente y todavía no vence. |
| Plazo vencido | El objetivo sigue pendiente y la fecha límite pasó. |
| Cumplido | Se respondió o cerró como máximo en la fecha límite. |
| Atendido fuera del plazo | La acción ocurrió después de la fecha límite. |
| Sin fecha registrada | No existe evidencia suficiente de fecha para esa medición. |

La primera respuesta se cumple con una comunicación pública registrada por ADMIN/TECH. No cuentan las notas internas, la toma del caso, las modificaciones de datos, la agenda ni un comentario del cliente. Cerrar el caso no inventa una respuesta pública. La resolución se completa al cerrar; una reapertura inicia otro plazo de resolución.

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

Base documental: [S06], [S08].

## 10. Cronómetro y agenda remota

### 10.1 Medir trabajo sin cobrarlo automáticamente

ADMIN y TECH pueden abrir un caso e **Iniciar cronómetro**. Usa **Pausar** para interrupciones y **Continuar** al retomar. Al pulsar **Finalizar atención**, revisa los minutos propuestos, canal, descripción y visibilidad; después confirma con el control que indica el descuento.

El sistema propone minutos enteros redondeados hacia arriba: 61 segundos proponen 2 minutos. Puedes ajustar la propuesta justificando la diferencia. **Finalizar no descuenta; confirmar sí.** No registres además otra atención manual por el mismo trabajo.

Cada integrante puede tener un cronómetro pendiente, incluso entre casos distintos. Un reloj finalizado sin confirmar también ocupa ese lugar. Confírmalo o descártalo antes de iniciar otro. El servidor guarda el tiempo: recargar recupera el reloj y cerrar la pestaña no lo pausa.

Si falta saldo o el caso se cerró, el reloj finalizado se conserva y la confirmación falla sin cargo parcial. Revisa las condiciones antes de reintentar. Un técnico no opera el cronómetro de otra persona.

### 10.2 Proponer y gestionar una sesión

En la agenda del caso, el personal registra inicio, fin y preparativos. El cliente de la institución confirma o rechaza la propuesta. ADMIN puede gestionar el alcance autorizado; TECH gestiona las sesiones de las que es responsable, conforme a las reglas existentes.

Las sesiones tienen estados propios: **Propuesta**, **Confirmada**, **Rechazada**, **Cancelada** y **Realizada**. No son estados del ticket. La agenda rechaza solapamientos para el mismo responsable; las citas consecutivas sin cruce sí son admisibles.

Para cambiar un horario utiliza **Reprogramar**: cancela la cita anterior y crea una nueva propuesta que requiere confirmación del cliente. Si el horario nuevo no es admisible, la operación se revierte. Revisa la zona horaria mostrada y confirma con el cliente la interpretación del horario.

Marcar una sesión realizada no cobra tiempo. Registra después la atención remota o confirma el cronómetro correspondiente. Al cerrar el caso, cancela expresamente las citas pendientes que ya no procedan. No hay conexión con calendarios externos ni una herramienta de control remoto embebida: la agenda coordina el trabajo, no abre por sí sola el escritorio del cliente.

Base documental: [S08].

## 11. Campana, respuestas pendientes, vistas y alertas

### 11.1 Consultar avisos personales

Pulsa la campana del encabezado. Revisa avisos recientes y conversaciones pendientes; **Ver caso** abre el expediente relacionado y marca el aviso como leído. **Marcar leída** y **Marcar todas como leídas** afectan sólo a tu cuenta, no a la bandeja de los demás.

La campana se actualiza periódicamente y dispone de actualización manual. La presentación habitual contiene hasta 100 avisos recientes; el contador puede incluir más avisos accesibles sin leer. Un aviso histórico no certifica que el caso siga igual: abre el detalle para consultar la situación actual.

La bandeja funciona sin SMTP. Los clientes no reciben notas técnicas internas. BILLING recibe los avisos propios que correspondan, como saldo, pero no acceso a los casos por esa vía. No confundas campana con la cola administrativa de correo.

### 11.2 Guardar filtros frecuentes

En **Casos de soporte**, combina búsqueda, estado, asignación, institución, categoría y prioridad. En **Mis vistas guardadas**, escribe un nombre y pulsa **Guardar filtros como nueva vista**. Después puedes aplicar, actualizar o eliminar esa configuración sin borrar casos.

Ejemplos útiles son «Mis casos pendientes», «Urgentes sin responsable» o «Instalaciones abiertas». Las vistas son personales, con hasta 50 por cuenta; no copian resultados históricos ni amplían permisos.

**Particularidad de 1.8.1:** los filtros Desde/Hasta agregados al listado del personal se aplican localmente sobre la fecha UTC de creación. No están incluidos en el objeto de vista guardada. Al aplicar una vista, revisa también las fechas que quedaron en pantalla. Para cortes por horario de México o informes formales utiliza los módulos de reportes, no supongas que ese filtro local equivale a un corte contable.

### 11.3 Alertas de saldo y de SLA

En **Configuración de soporte**, ADMIN define umbrales de saldo por institución y canal. Los valores iniciales son 60 minutos de correo y 30 de remoto. El aviso se genera al estar igual o debajo del umbral; no se repite en cada consumo del mismo periodo de saldo bajo. Una recarga que supera el umbral permite advertir una nueva caída posterior.

Los **Avisos antes del vencimiento** de SLA utilizan inicialmente 60 minutos naturales de anticipación. El plazo del caso se calcula con su calendario laboral, pero la anticipación del aviso es natural. ADMIN recibe los avisos previstos para todos los casos; TECH, los de sus asignados y los todavía sin responsable. Clientes y Cobranza no reciben esos avisos técnicos internos.

La revisión periódica y **Revisar plazos ahora** no modifican saldos ni crean una atención. Pausar el correo no apaga estas alertas.

Base documental: [S09], [S10], [S05].

## 12. Plantillas e incidencias generales

### 12.1 Preparar y utilizar plantillas

ADMIN abre **Plantillas → Nueva plantilla**, captura título, categoría técnica, versión opcional y contenido en texto plano. Las nuevas quedan activas. Puede editarlas, archivarlas, reactivarlas y consultar sus revisiones sin cambiar respuestas que ya estén guardadas en casos.

Para usarlas, ADMIN/TECH abre un caso y el formulario de seguimiento, pulsa **Usar una plantilla de respuesta**, busca y revisa la vista previa. **Insertar en borrador** copia el contenido a un texto editable. Ajusta nombres, instrucciones, visibilidad y minutos antes de registrar el seguimiento.

**Seleccionar, previsualizar o insertar no publica, no envía correo y no descuenta minutos.** No hay sustitución automática de variables ni ejecución de HTML. Un texto ya copiado al borrador no se retira cuando después se archiva la plantilla; revisa siempre lo que vas a enviar.

### 12.2 Coordinar una incidencia común

En **Incidencias generales**, ADMIN/TECH puede crear una incidencia, describir el problema interno, establecer su estado y vincular casos de distintas instituciones. Utiliza la búsqueda y verifica cada expediente antes de asociarlo. Una vinculación errónea se retira conservando historial.

Registra avances y diagnóstico dentro de la incidencia. Después abre cada caso para documentar la atención, el tiempo y su cierre. Los estados de incidencia **Abierta/En atención/Resuelta** coordinan el problema global; no cambian los estados Abierto/Cerrado de sus tickets.

Resolver una incidencia no cierra expedientes ni distribuye automáticamente consumos. Vincular casos tampoco copia sus mensajes privados ni crea pagos.

### 12.3 Publicar información para los clientes

Desde **Resumen para clientes**, escribe título y resumen públicos separados del diagnóstico interno. Revisa la vista previa y la confirmación de privacidad antes de publicar. Omite nombres, números de caso, contactos o credenciales de otras instituciones.

El cliente sólo ve el aviso publicado dentro de sus propios casos, sin acceso al listado global ni al historial interno de la incidencia. Cambiar el diagnóstico interno no actualiza automáticamente la publicación: utiliza **Actualizar publicación** cuando proceda. **Retirar publicación** la oculta sin borrar sus antecedentes internos.

La aplicación separa campos y permisos, pero no interpreta si el texto que redactaste contiene información privada. Esa revisión corresponde al personal que publica. Estas operaciones no generan por sí solas correos ni consumo.

Base documental: [S11], [S12].

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

**Limitación de interfaz observada en 1.8.1:** el menú global muestra Base de conocimientos a TECH, pero el componente completo permite entrada sólo a ADMIN/USER y puede mostrar «No tienes acceso». El técnico sí dispone del componente de soluciones dentro de los casos y de las operaciones publicadas autorizadas. No eleves a un técnico a ADMIN para sortear este defecto de interfaz.

Base documental: [S13], [S08], [S02].

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

En una aceptada, pulsa **Convertir a póliza** e indica un folio único. Se crea una póliza sin pago ni crédito de minutos. Continúa con **Pagos** o **Validar pagos**. Repetir la conversión en las condiciones admitidas devuelve el mismo contrato, no otro.

### 14.4 Renovación y avisos

**Renovaciones** reúne pólizas activas próximas a vencer o vencidas sin sucesora. El umbral inicial de aviso es 30 días y se puede configurar. **Mis avisos de vencimiento** conserva avisos internos y lecturas personales; no depende de SMTP.

La relación de renovación forma una cadena y cada antecedente admite una sucesora. Si ésta se cancela, no se elimina el vínculo; una nueva renovación puede partir de esa última póliza cancelada y conservar la historia. Renovar no transfiere, vence ni duplica minutos institucionales.

Base documental: [S14], [S15], [S16].

## 15. Pólizas y pagos

### 15.1 Registrar una póliza

ADMIN/BILLING abre **Pólizas** y crea el contrato de una institución activa: folio único, descripción, vigencia, importe MXN y tiempo por canal. La fecha final no puede preceder al inicio y debe haber minutos contratados en al menos un canal.

Una póliza tiene **estado administrativo** Activa/Cancelada y **vigencia calculada** Por iniciar/Vigente/Vencida. Son datos distintos. La vigencia es informativa y no retira minutos ya acreditados; una cancelación sí impide movimientos nuevos en ese contrato.

**Crear póliza no crea un pago ni aumenta saldo.** Para registrar otro acuerdo no reutilices el folio de una póliza cancelada.

### 15.2 Registrar dinero y minutos de forma explícita

En **Pagos**, selecciona institución y póliza opcional. Captura referencia, fecha del pago, método, importe, descripción y minutos por acreditar en ambos canales. Comprueba los datos y confirma una sola vez.

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

Una póliza sólo admite edición contractual si está activa y nunca tuvo pagos, incluso anulados. Además, tener parcialidades no canceladas restringe modificar el importe acordado. La institución de la póliza no se cambia después del alta.

Para corregir un pago, ADMIN utiliza **Anular pago** con un motivo. La aplicación conserva la evidencia y retira sus minutos sólo si hay saldo suficiente en ambos canales. No se permite una retirada parcial que deje la bolsa negativa.

Si anular un abono monetario deja sin respaldo una acreditación vigente de importe cero, deben resolverse primero esas acreditaciones, respetando su saldo. No uses ajustes artificiales para eludir la protección.

ADMIN/BILLING puede cancelar una póliza cuando no tiene pagos vigentes. Un contrato con pagos anulados conserva la historia y no recupera la edición. Anular o cancelar no ejecuta un reembolso bancario ni borra documentos ya descargados.

### 15.5 Prevenir doble registro

La API de pagos incorpora mecanismos de reintento, pero el operador debe verificar el historial antes de crear otra captura con nueva referencia. No registres un pago directo y después apruebes una solicitud que representa el mismo depósito. Tampoco ajustes el presupuesto manualmente por una compra ya acreditada.

Base documental: [S16], [S17], [S19], [S20].

## 16. Solicitudes de pago, comprobantes y recibos

### 16.1 Revisar evidencia antes de acreditar

ADMIN/BILLING trabaja en **Validar pagos**. El cliente autorizado lo hace desde **Finanzas institucionales**. Para presentar una solicitud, captura institución autorizada, póliza opcional, referencia, fecha, método, importe, minutos y descripción visible para la institución.

Al guardar queda **Pendiente**: no se registra un ingreso ni se acredita soporte. Carga PDF, PNG o JPG/JPEG y revisa su legibilidad. Se requiere al menos un comprobante para aprobar.

El personal verifica la evidencia, importe y los dos conceptos de minutos, marca la confirmación correspondiente y pulsa **Aprobar y registrar pago**. La aprobación crea el pago, acredita el tiempo y copia los comprobantes en una transacción. La observación de aprobación es privada.

Si el importe o los minutos son incorrectos, rechaza con motivo y presenta una nueva solicitud; no se modifican durante la aprobación. El motivo de rechazo sí es visible para el cliente. Una solicitud resuelta y su evidencia se conservan sin edición. Rechazar no cambia saldo.

### 16.2 Adjuntar comprobantes a un pago ya registrado

ADMIN/BILLING abre el pago y usa **Comprobantes de pago → Adjuntar comprobante**. Puede consultar nombre, tamaño, autor, fecha y huella y descargar el archivo original. Agregar evidencia no registra otro pago ni cambia dinero o minutos.

Los comprobantes se conservan incluso si el pago se anula. No se sustituyen ni borran desde la interfaz: agrega la evidencia correcta cuando corresponda y conserva la trazabilidad. El cliente con acceso financiero puede descargar los comprobantes propios, pero no agregar archivos a un pago ya registrado.

### 16.3 Descargar un recibo administrativo

En el detalle del pago, **Recibo administrativo → Descargar recibo PDF** genera un documento con folio estable del tipo `SIABUC-REC-0000000042`. La primera emisión conserva una copia de los datos conocidos en ese momento; no reconstruye automáticamente datos históricos anteriores.

Hay una presentación administrativa y una institucional. La del cliente omite notas de pago, identidades del personal y motivos internos de anulación. Una descarga posterior comprueba el estado actual del pago: si fue anulado, el PDF muestra **ANULADO**, conservando sus cantidades originales como antecedente.

**Comprobante adjunto, recibo y estado de cuenta son documentos diferentes.** El adjunto es evidencia aportada; el recibo describe un movimiento registrado; el estado de cuenta reúne situación y movimientos a un corte. No son CFDI ni ejecutan devolución de dinero.

Base documental: [S17], [S19].

## 17. Portal financiero de la institución

### 17.1 Habilitación y alcance — ADMIN

En **Configuración de soporte → Acceso financiero de clientes**, localiza la institución y selecciona **Habilitar**. Lee y confirma el alcance antes de aceptar. Por defecto el permiso está desactivado y no lo concede el rol BILLING.

La habilitación es **por institución, no por cuenta individual**. Todos sus USER activos podrán consultar sus pólizas, pagos, recibos y comprobantes históricos, además de presentar solicitudes. Revisa primero si los archivos antiguos contienen información que no deba compartirse con esos usuarios.

Revocar impide consultas y descargas posteriores, pero no recupera archivos descargados ni borra lo que alguien ya vio. No autoriza el acceso a finanzas de otra institución ni a rutas administrativas.

### 17.2 Recorrido del cliente

Abre **Finanzas institucionales**. Si está deshabilitado, aparecerá una indicación para contactar a Administración; soporte sigue siendo un módulo independiente.

Cuando está habilitado, revisa el resumen de pólizas —folio, fechas, importes, minutos y estado— y los pagos propios. Abre un pago para consultar comprobantes y recibo institucional. Puedes crear una solicitud pendiente de validación y adjuntar su evidencia, pero no aprobarla.

El saldo contractual pendiente no equivale a minutos disponibles. Si deseas conocer cuánto soporte queda, consulta también **Resumen**. El módulo incluye los estados de cuenta de la institución conforme a su autorización.

**La consulta es institucional:** no se limita automáticamente a una póliza usada como credencial, porque ese nuevo mecanismo de autenticación no está implementado. Tampoco hay un apartado operativo de «Mi licencia SIABUC9».

Base documental: [S18], [S16], [S23].

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

Base documental: [S20].

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

Base documental: [S21].

## 20. Reportes y estados de cuenta

### 20.1 Reportes técnicos — ADMIN

Abre **Reportes**, selecciona institución, administrativo, canal y fechas y pulsa **Consultar reporte**. Revisa casos, atenciones y consumo agrupado; exporta Excel o PDF cuando corresponda. La consulta y la exportación están protegidas por rol.

Distingue quién figura como responsable del caso de quién registró una atención. Los filtros del listado de casos y los filtros de consumo no representan necesariamente la misma atribución ni la misma fecha. Las atenciones anuladas quedan como antecedente, pero no forman consumo vigente.

La consulta técnica admite hasta 366 días y 5,000 filas combinadas de casos y atenciones, además de límites de contenido y páginas. Si los rebasa, reduce el periodo o filtra; no se debe interpretar un error como un informe vacío.

### 20.2 Reportes de cobranza — ADMIN/BILLING

En **Reportes de cobranza**, filtra institución, póliza, método, moneda, vigentes/anulados y periodo. Después de cambiar filtros, vuelve a consultar. El periodo del pago utiliza la **fecha declarada del pago (`paidOn`)**, no su fecha de captura.

El informe separa monedas y pagos anulados. Una liberación de horas con importe cero puede contar como movimiento y sumar minutos, pero no dinero. Solicitudes pendientes, cotizaciones y comprobantes no se agregan como ingresos.

La sección de **situación actual de pólizas** utiliza todo el historial y los filtros de institución/póliza, no sólo el periodo de pagos. Por eso sus totales pueden diferir del dinero recibido en el mes. Se admiten hasta 3,660 días y 5,000 filas combinadas de pagos/pólizas por consulta, con límites adicionales de exportación.

### 20.3 Generar un estado de cuenta

ADMIN/BILLING abre **Estados de cuenta**; USER lo consulta desde su portal financiero habilitado. Selecciona institución autorizada y corte, pulsa **Generar estado de cuenta**, revisa la emisión y descarga el PDF. El folio tiene el formato `SIABUC-EC-##########`.

Generar conserva un documento, no un pago. Una emisión guardada no se edita ni se recalcula cuando cambian datos. Para la situación actual genera otra; para reproducir un documento anterior abre su folio en **Emisiones conservadas**.

**Advertencia 1.8.1:** hay un fallo de prueba de emisión concurrente con respuesta 503. Evita generar simultáneamente el mismo documento en pruebas de uso ordinario y, tras un error, consulta las emisiones antes de repetir. Esto es una precaución operativa, no una corrección del defecto.

### 20.4 Comprender las fechas y no mezclar totales

| Pantalla o dato | Fecha relevante |
|---|---|
| Actividad y orden habitual de casos | Última actualización. |
| Desde/Hasta del listado de soporte 1.8.1 | Fecha UTC de creación, filtrada localmente. |
| Reporte de pagos | Fecha declarada del depósito/pago. |
| Parcialidades | Fecha comprometida. |
| Estado de cuenta | Registro en la plataforma y corte efectivo. |
| Póliza | Vigencia contractual, separada de la fecha de pagos. |

Si hoy registras un pago con fecha declarada de la semana anterior, entra en el reporte de pagos de esa fecha, pero no aparece artificialmente en un estado de cuenta previo a su registro. Para un corte de hoy se usa el instante de emisión, no el final de un día que aún no concluye.

La apertura histórica de V24 conserva valores conocidos al migrar, sin inventar movimientos detallados anteriores. No se admiten cortes anteriores a su origen temporal. Las monedas se totalizan por separado y el saldo institucional distingue asignado, usado y disponible por canal.

Base documental: [S22], [S23], [S05], [S28].

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

Base documental: [S24], [S02], [S27].

## 22. Correo y notificaciones — ADMIN / operador

### 22.1 Identificar las tres piezas

La **campana** contiene avisos internos por usuario. La **cola de la aplicación** registra mensajes pendientes y su procesamiento. **Postfix**, cuando se utiliza, mantiene otra cola después de aceptar los mensajes de Spring. Que la campana funcione no demuestra entrega de correo.

En **Notificaciones / Correo y notificaciones**, ADMIN puede revisar configuración, cola y detalle del mensaje guardado. La prueba de correo se dirige al correo de la propia cuenta administradora. No introduce destinatarios arbitrarios en esa prueba.

### 22.2 Configurar el medio de salida

Conserva un SMTP que ya funciona. Desde la carpeta de instalación, el operador puede utilizar uno de los asistentes pertinentes:

```bash
# Configurar Postfix integrado y su salida directa o relay
bash ./configurar.sh --smtp-interno

# Alternativa: conexión de Spring directamente a otro SMTP
bash ./configurar.sh --correo
```

Son alternativas de configuración; no es necesario ejecutar ambas. Revisa la información guardada y aplica mediante el procedimiento de inicio/recreación de la instalación. Los cambios de `.env` requieren aplicar la configuración al servicio; cambiar la pausa en la interfaz no requiere ese reinicio.

El paquete incluye un asistente específico `configurar-gmail.sh`; su viabilidad depende de la cuenta y las políticas del proveedor. No implica que toda cuenta UCOL pueda usar contraseña de aplicación o relay. No sustituyas el SMTP actual por Gmail sin verificar autorización y conectividad. No ejecutes `.env` con `source`: el archivo es configuración y sus secretos deben mantenerse privados.

Postfix es saliente, sin buzones, IMAP, POP3 o bandeja web de correo. El remitente de laboratorio con IP no garantiza entrega en servicios externos. Los contactos institucionales y del responsable deben ser válidos para los avisos que correspondan.

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

Base documental: [S25], [S09].

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

El calendario está **desactivado por defecto**. El worker `backup` puede estar en ejecución sin generar copias programadas.

En `.env`, revisa `BACKUP_DIRECTORY`, `BACKUP_UID` y `BACKUP_GID`. Usa una carpeta privada, no pública ni la raíz del proyecto. Una ruta relativa se interpreta desde la instalación. Inicia mediante los scripts para que preparen el destino cuando corresponde.

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

Antes de reanudar, verifica configuración SMTP, colas y calendario de respaldos recuperado: la copia puede contener programación activada y mensajes pendientes. Comprueba datos conocidos con una cuenta incluida en la copia. Cambiar la contraseña administrativa en `.env` no modifica automáticamente las cuentas restauradas.

Base documental: [S26], [S03], [S25].

## 24. Rutinas recomendadas y caso práctico

### 24.1 Rutina del cliente

Al entrar, revisa los avisos y las respuestas pendientes. Consulta el saldo del canal antes de abrir otra solicitud. Mantén un problema identificable por caso, aporta contexto suficiente y continúa el seguimiento en el mismo expediente cuando se trate del mismo asunto. No dupliques solicitudes porque el correo todavía no llegó: revisa primero el folio y su historial.

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

1. BILLING crea una póliza de $1,200 MXN con 120 minutos de correo y 60 remotos. Comprueba que el saldo todavía sea cero.
2. Registra un pago verificado de $1,200 con acreditación de 120/60 minutos. Consulta el saldo: 120 correo, 60 remoto.
3. USER crea una solicitud por correo y anota el folio. Comprueba que la creación no consuma tiempo.
4. TECH toma el caso si está libre y registra una atención pública de 25 minutos por correo. Quedan 95 de correo y 60 remotos.
5. TECH añade una nota interna sin consumo. USER debe seguir viendo el saldo, pero no esa descripción privada.
6. Si los 25 minutos se registraron por error y debieron ser 20, ADMIN anula la atención con motivo y el técnico registra la correcta. Quedan 100 de correo y 60 remotos, con ambos antecedentes visibles según sus permisos.
7. El equipo cierra con motivo. USER consulta el resultado y puede enviar la encuesta.
8. Si se habilita el portal financiero de práctica, USER revisa su pago y recibo, sin acceder a datos de otra institución.
9. El operador hace una copia y ensaya restauración en otro destino de pruebas. No elimina datos de producción.

Resultado de control: dinero registrado una vez, correcciones trazables, consumo vigente de 20 minutos de correo, bolsa remota intacta y notas internas no divulgadas. Son cantidades de ejemplo, no resultados de una ejecución nueva realizada para este manual.

Base documental para las reglas del ejercicio: [S02], [S05], [S16], [S18], [S24], [S26].

## 25. Solución de problemas y límites conocidos

### 25.1 Acceso y arranque

| Síntoma | Qué comprobar | Qué evitar |
|---|---|---|
| «Usuario o contraseña incorrectos» | Credenciales, cuenta activa, institución activa y versión de la instalación. Solicitar a ADMIN un restablecimiento si procede. | Cambiar `ADMIN_PASSWORD` esperando que reemplace una cuenta existente. |
| La sesión pide entrar de nuevo | Reinicio de Spring, expiración o contraseña restablecida. | Desactivar CSRF o protección de sesión para evitar identificarse. |
| La web abre pero no carga datos | Salud y logs del backend, base y proxy `/api/`. | Asumir que servir HTML prueba que todo funciona. |
| Otro equipo no abre Angular | IP del anfitrión, `ports` de frontend, puerto real y conectividad de red. | Usar `127.0.0.1` desde otro equipo o exponer toda la base para resolverlo. |
| Falla la cookie al probar por HTTP | Configuración `COOKIE_SECURE` y protocolo usado. | Desactivar seguridad en una publicación real en vez de preparar HTTPS. |
| Puerto ocupado | Otras instalaciones o servicios, y valores del `.env`. | Borrar la base o reutilizar al azar el proyecto de otra instalación. |
| Falla la descarga de dependencias | Conectividad a registros, Maven, npm y recursos PDF fijados. | Desactivar verificaciones SHA-256 o cambiar recursos sin revisión. |
| TS2345 con `GUIDES` | Que todo el código sea del ZIP 1.8.1 y no una mezcla de componentes viejos. | Añadir `any`, quitar comprobaciones de plantillas o ampliar categorías técnicas indebidamente. |
| Docker busca un JAR 1.7/1.8.0 | Coherencia de POM, Dockerfile y scripts de la copia utilizada. | Aplicar parches parciales sobre una carpeta de versión desconocida. |

En una compilación paralela, una etapa marcada **CANCELED** puede haber sido interrumpida después del fallo de otra. Identifica primero el bloque **ERROR** y su causa; no atribuyas un fallo independiente a cada cancelación.

### 25.2 Operación diaria

| Situación | Explicación y actuación |
|---|---|
| No permite crear un caso | Comprueba el saldo del canal, la institución activa, campos válidos y revisión previa. |
| No deja agregar comentarios o archivos | El caso puede estar cerrado, la sesión haber caducado o el archivo incumplir formato/cuota. |
| Un técnico no puede reasignar | Está reservado a ADMIN; tomar un caso libre es una operación distinta. |
| TECH ve el menú de conocimiento pero no entra | Limitación del componente global 1.8.1. Usa las soluciones dentro del caso; no cambies su rol para eludirla. |
| La vista guardada no conserva fechas | Desde/Hasta no forman parte de la vista persistente de esta revisión. |
| Un cronómetro sigue contando al volver | Cerrar la pestaña no lo pausa; revisa antes de confirmar minutos. |
| El pago no liberó horas | Puede ser un abono con minutos cero; revisar qué tiempo se acreditó explícitamente. |
| La póliza está vencida y aún hay saldo | La vigencia es informativa; no elimina crédito institucional ya disponible. |
| El calendario muestra deuda distinta | Puede faltar aplicar los pagos existentes a los compromisos. |
| Una referencia de pago se rechaza | Ya puede estar registrada, incluso en un pago anulado. Investiga antes de crear otra referencia. |
| El PDF de estado de cuenta devuelve 503 | Fallo concurrente pendiente de revisar. Consulta emisiones existentes, registra contexto y no asumas éxito ni ausencia de documento. |
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

No hay autoservicio público de recuperación por licencia, integración federada UCOL, administración de licencias SIABUC9, sincronización de descargas oficiales, editor de adjuntos de artículos, tutorial automático de primera visita ni administración granular de roles desde un catálogo editable. El formulario nuevo no contiene aún los catálogos completos de tipo/producto/módulo del borrador.

Tampoco hay una garantía de número máximo de usuarios simultáneos o una validación integral de la instalación productiva. Las dos pruebas Java fallidas siguen siendo pendientes de revisión. Este manual no modifica ni resuelve esos aspectos por documentarlos.

Base documental: [S01]–[S28], con revisión puntual de [S05], [S13] y [S28].

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
| Configurar SMTP directo | `bash ./configurar.sh --correo` |
| Configurar Postfix integrado | `bash ./configurar.sh --smtp-interno` |

En PowerShell, utiliza los archivos `.bat` equivalentes con el prefijo `.` seguido de barra inversa. Por ejemplo:

```powershell
.\compilar.bat --docker
.\iniciar.bat
.\respaldar.bat --help
```

La comprobación con Windows nativo sigue pendiente en la evidencia de 1.8.1. No es necesario ejecutar el modo de desarrollo Angular para servir la interfaz compilada: el despliegue Docker utiliza Nginx.

### 26.2 Configuración que conviene identificar

| Variable | Uso y precaución |
|---|---|
| `COMPOSE_PROJECT_NAME` | Identifica el conjunto de recursos de instalación; conservar al actualizar. |
| `BIND_HOST` | Afecta varias publicaciones de puerto; no abrirlo globalmente por necesidad de frontend. |
| `FRONTEND_PORT` | Puerto de entrada web del anfitrión. |
| `SERVER_PORT` / `DB_PORT` | Publicaciones del backend y PostgreSQL. No equivalen necesariamente a sus puertos internos Docker. |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexión de base; cambiar `.env` no cambia la contraseña interna de una base ya creada. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Cuenta de inicialización; no son un mecanismo general para resetear usuarios existentes. |
| `COOKIE_SECURE` | Exige HTTPS para la cookie cuando está activo. |
| `MAIL_ENABLED` | Permite procesamiento SMTP; no equivale a supresión administrativa de avisos. |
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
| `POST /api/client-portal/tickets` | Alta de solicitud para USER con institución derivada de sesión. |
| `GET /api/knowledge` | Consulta paginada de artículos según permisos. |
| `GET /api/policies` | Gestión financiera de pólizas para ADMIN/BILLING. |

Las escrituras requieren sesión, CSRF y permisos del servidor. La autorización no se elimina por llamar a una API sin navegador. El contrato técnico completo está en `docs/API-CONTRACT.md`; es necesario contrastar sus secciones históricas con el código 1.8.1 para integraciones nuevas.

### 26.4 Glosario breve

**Póliza:** contrato de soporte. **Acreditar:** agregar minutos autorizados a una bolsa mediante el proceso previsto. **Consumir:** registrar tiempo de atención que disminuye el disponible. **Anular:** conservar un movimiento y revertir su efecto permitido con motivo. **Cancelar póliza:** cerrar administrativamente el contrato, sin borrar su historia.

**SLA:** objetivos de respuesta/resolución en tiempo laboral. **CSRF:** protección para comprobar el origen legítimo de una operación de sesión. **SMTP:** mecanismo de envío de correo. **Outbox:** cola persistente de avisos de la aplicación. **Migración:** cambio versionado del esquema de datos. **UUID:** identificador utilizado para reconocer ciertos reintentos. **SHA-256:** huella de integridad de un archivo; no cifra su contenido. **MiB:** unidad de tamaño de 1,048,576 bytes.

Base documental: [S03], [S04], [S25], [S26], [S27].

## 27. Fuentes, trazabilidad y control documental

### 27.1 Paquete examinado

Este manual se elaboró leyendo los documentos y componentes del ZIP entregado, no suponiendo que la rama `main` ya contenga todos los cambios. El nombre interno de su carpeta raíz es `siabuc-helpdesk` y `VERSION` declara `1.8.1`.

**SHA-256 del paquete examinado:**

```text
25d305de3d5e1dbde43d1fe3fd767116b9bf1ba159508add5a7c4c0806e1b888
```

El ZIP contiene 319 entradas, incluido el manifiesto. Para elaborar este manual se comprobó su estructura y se contrastaron los procedimientos con código; no se ejecutó de nuevo la aplicación ni se convirtió la lectura de sus fuentes en una prueba funcional.

La evidencia de CI incluida identifica el commit de preparación `2c2a39ca7a0ac60795111a7aa6be9d10b644c42c` y la ejecución `38611221528`. El paquete incluye archivos preparados para distribución; no debe suponerse identidad byte a byte de todo su contenido con una descarga arbitraria de `main`.

### 27.2 Índice de fuentes del paquete

Las rutas siguientes son relativas a `siabuc-helpdesk/`. Cuando se indica un componente o servicio sin la ruta completa, los componentes están en `frontend/src/app/` y los servicios Java en `backend/src/main/java/mx/siabuc/helpdesk/`.

| Código | Fuente principal y uso |
|---|---|
| S01 | `VERSION`; `docs/VERSION-1.8.md`; `docs/VERSION-1.8.1.md`; `docs/VALIDACION-1.8.1.json`. Alcance y evidencia de versión. |
| S02 | `docs/MANUAL-USUARIO.md`; `app.component.ts`; `users.component.ts`; `HelpdeskService.java`; `SecurityConfig.java`; `backend/src/main/resources/application.yml`. Roles, acceso y administración. |
| S03 | `docs/OPERACION.md`; `docs/WINDOWS.md`; `scripts/manage.py`; Dockerfiles y `backend/pom.xml`. Instalación y comandos. |
| S04 | `docker-compose.yml`; `frontend/nginx.conf`; `api.service.ts`. Publicaciones y proxy. |
| S05 | `cases.component.ts`; `cases.component.html`; `dashboard.component.ts`; `ClientPortalController.java`; `HelpdeskService.java`. Captura, navegación, filtros y seguimiento. |
| S06 | `docs/CLASIFICACION-SLA.md`; `CaseProfileService.java`; `case-profile.component.ts`. Clasificación y SLA. |
| S07 | `docs/HELPDESK-1.4.md`; `AttachmentService.java`; `attachments.component.ts`. Evidencia de soporte y cuotas. |
| S08 | `docs/ROL-TECNICO.md`; `docs/CRONOMETRO-AGENDA.md`; servicios de cronómetro y sesiones remotas. Facultades del técnico. |
| S09 | `docs/AVISOS-INTERNOS.md`; `PortalEventService.java`; `PortalNotificationService.java`. Bandeja y pendientes. |
| S10 | `docs/VISTAS-ALERTAS.md`; `SavedViewService.java`; `cases.component.ts`. Vistas y fechas no persistidas. |
| S11 | `docs/PLANTILLAS-RESPUESTA.md`; componentes y servicio de plantillas. Borradores y publicación. |
| S12 | `docs/INCIDENCIAS.md`; `IncidentService.java`; componentes de incidencias. Privacidad y coordinación. |
| S13 | `docs/BASE-CONOCIMIENTOS.md`; `KnowledgeService.java`; `knowledge-models.ts`; `knowledge-base.component.ts`; `case-knowledge.component.ts`. Categorías, publicación y limitación TECH. |
| S14 | `docs/PAQUETES-TARIFAS.md`; `SupportPackageService.java`. Tarifas y revisiones. |
| S15 | `docs/COTIZACIONES-RENOVACIONES.md`; servicios y componentes de cotización/renovación. Estados y constancias. |
| S16 | `docs/POLIZAS-COBRANZA.md`; `PolicyService.java`; `HelpdeskService.java`. Contratos, pagos y minutos. |
| S17 | `docs/SOLICITUDES-PAGO.md`; `PaymentRequestService.java`. Aprobación y evidencia pendiente. |
| S18 | `docs/PORTAL-FINANCIERO.md`; `ClientFinanceAccessService.java`; `client-finance.component.ts`. Habilitación institucional. |
| S19 | `docs/COMPROBANTES-PAGO.md`; `docs/RECIBOS-PAGO.md`; servicios de archivos y recibos. Formatos, conservación y privacidad. |
| S20 | `docs/PARCIALIDADES.md`; `InstallmentService.java`. Compromisos y aplicaciones. |
| S21 | `docs/CONCILIACION-BANCARIA.md`; `BankCsv.java`; `BankReconciliationService.java`. Importación y conciliación. |
| S22 | `docs/REPORTES.md`; `docs/REPORTES-COBRANZA.md`. Fechas, límites y exportaciones. |
| S23 | `docs/ESTADOS-CUENTA.md`; `InstitutionStatementService.java`. Cortes, apertura y emisiones. |
| S24 | `docs/ENCUESTAS-SATISFACCION.md`; `SatisfactionService.java`. Encuestas y reportes. |
| S25 | `docs/CORREO.md`; `docs/SMTP-INTERNO.md`; `docs/DESACTIVAR-CORREO.md`; `docs/GMAIL-UCOL.md`. Envío, asistentes y pausa. |
| S26 | `docs/RESPALDO-RESTAURACION.md`; `docs/RESPALDOS-AUTOMATICOS.md`; `scripts/manage.py`; `backup/worker.py`. Copias y recuperación. |
| S27 | `docs/API-CONTRACT.md`; `docs/ARQUITECTURA.md`; migraciones V1–V25. Integridad y referencia técnica. |
| S28 | `VALIDACION-SIABUC-1.8.1.md`, entregado junto al ZIP, y `docs/VALIDACION-1.8.1.json` dentro del paquete. Resultados y fallos exactos. |

### 27.3 Documentación externa consultada

**[E01] Docker — Port publishing and mapping.** Publicación de puertos por dirección del anfitrión; consulta del 14 de octubre de 2026. https://docs.docker.com/engine/network/port-publishing/

**[E02] Docker — Packet filtering and firewalls.** Interacción de Docker con reglas de filtrado y UFW; consulta del 14 de octubre de 2026. https://docs.docker.com/engine/network/packet-filtering-firewalls/

**Ejecución de CI referenciada por el paquete:** https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38611221528

### 27.4 Mantener el manual actualizado

Cuando se corrija un defecto, cambie una regla o se incorpore una integración institucional, actualiza primero el código y su evidencia y después esta documentación. Distingue fecha de documento, versión del programa y entorno probado. No elimines advertencias porque una compilación aislada haya pasado: sustitúyelas por resultados que comprueben el alcance correspondiente.

**Fin del manual.**