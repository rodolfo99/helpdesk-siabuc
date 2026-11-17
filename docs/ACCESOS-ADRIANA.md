# Accesos separados para clientes y personal SIABUC

Adaptación inicial basada en los requerimientos `RF-LOG-001` y `RF-LOG-002` enviados por Adriana Barreda Beltrán.

## Rutas

| Ruta | Público | Cuentas permitidas |
| --- | --- | --- |
| `/poliza` | Clientes de instituciones | `USER` |
| `/poliza/admin` | Personal del Centro SIABUC | `TECH`, `BILLING`, `ADMIN` |

La raíz `/` redirige a `/poliza` mediante Nginx. Las rutas sirven el mismo frontend Angular, que presenta un formulario y un mensaje distintos en cada una. El backend comprueba además el rol después de autenticar: si no pertenece al portal seleccionado, responde 403, invalida la sesión y no permite entrar en el portal incorrecto.

## Alcance y compatibilidad

- Se mantienen **usuario y contraseña** con las cuentas y roles existentes. La separación inicial no modificó credenciales, pero las etapas posteriores incorporan migraciones V28–V31 para guía, avisos, recursos y bloqueo de clientes. La recuperación continúa siendo exclusivamente administrativa, sin migración V32.
- La Federación de Identidades UCOL (`RF-LOG-004`) **no** está implementada.
- El acceso por número de póliza y correo registrado (`RF-LOG-003`) **no** está implementado. No se aceptan esos valores como sustitutos de contraseña.
- El endpoint previo `POST /api/auth/login` sin parámetro `portal` sigue disponible para clientes anteriores y suites de pruebas. Los nuevos formularios envían `portal=client` o `portal=staff`.
- La selección de portal es una comprobación adicional en el servidor. Los permisos de API siguen basándose en el rol autenticado y en la institución, no en la URL visitada.
- La redirección de la raíz pertenece al despliegue Nginx de Docker; en `ng serve`, la ruta `/` aún muestra el acceso del cliente. Ambos accesos funcionan como rutas del SPA gracias a su fallback.
- No se ha desplegado `https://siabuc.ucol.mx/poliza`; esa publicación exige configuración institucional de dominio, certificado y proxy. Los recursos Angular usan rutas absolutas bajo `/`, y el API sigue en `/api`, por lo que el proxy debe exponerlos en el mismo origen.

## Validación sugerida

1. Iniciar el proyecto y entrar en `/poliza`. Comprobar que aparece «Acceso de clientes».
2. Autenticarse con una cuenta `USER` y comprobar que se ve únicamente la institución y las funcionalidades del cliente.
3. Intentar iniciar sesión con una cuenta `ADMIN`, `TECH` o `BILLING` desde `/poliza`: debe aparecer un mensaje de error y ninguna sesión puede quedar activa.
4. Entrar en `/poliza/admin` y comprobar que aparece «Acceso de personal».
5. Autenticarse con `ADMIN`, `TECH` o `BILLING` y comprobar las restricciones actuales por rol.
6. Intentar ingresar con `USER` desde `/poliza/admin`: debe responder con error y sin sesión activa.
7. Con una sesión preexistente y un enlace al portal equivocado, comprobar que se redirige a la ruta correspondiente.
8. Comprobar que el acceso previo sin parámetro `portal` sigue funcionando para los clientes existentes.
9. Validar que el endpoint `/api/auth/me` devuelve 401 después del intento de acceso por el portal incorrecto.
10. Probar navegación directa y recarga de ambas rutas detrás de Nginx, así como pantallas móviles.

## Pruebas agregadas

`PortalLoginIntegrationIT.java` comprueba:
- Acceso de clientes con `USER` y rechazo de cuentas internas.
- Acceso del personal con `ADMIN` y rechazo de `USER`.
- Rechazo de nombres de portal desconocidos.
- Compatibilidad del inicio de sesión anterior.

Ejecutar en una **base de datos de pruebas aislada** con los requisitos documentados en `docs/OPERACION.md`:

```bash
cd backend
mvn -Ppostgres-it -Dit.test=PortalLoginIntegrationIT verify
```

Esta guía no afirma que las pruebas nuevas ya se hayan ejecutado. La integración con proveedores institucionales requiere diseño de identidad, verificación adicional y pruebas de aceptación independientes.

## Segunda etapa: consulta de información del cliente

Se agregó `GET /api/client-portal/my-information` y la vista **Mi información SIABUC**, visible sólo para `USER`.

- El backend obtiene la institución directamente de la sesión autenticada, sin aceptar un ID de institución o póliza por parámetro.
- La respuesta contiene las pólizas de soporte registradas (folio, descripción, vigencia, estado, licencia relacionada) y las licencias locales (número y tiempo restante por canal).
- No expone importes, pagos, justificantes, usuarios internos ni motivos de cancelación.
- Una institución sin registros recibe listas vacías, sin inventar licencias comerciales.
- El personal interno recibe HTTP 403 al consultar esta ruta. El acceso sin sesión recibe HTTP 401.
- La interfaz cliente muestra expresamente que estos registros pertenecen al Helpdesk y **no** representan verificación comercial del sistema SIABUC9.
- La portada pública de clientes incluye contacto de Soporte Técnico, Comercialización, correo y enlace institucional proporcionados en el documento de requisitos. Los logotipos oficiales siguen pendientes de recibir como activos aprobados.

### Alcance pendiente para RF-CLI-004 y RF-CLI-005

El acceso actual de clientes sigue asociado a una **institución**, no a una póliza. Por ello esta etapa muestra todas las pólizas y licencias de soporte registradas a nombre de esa institución, no únicamente una póliza con la que se inició sesión. La limitación por póliza individual debe implementarse junto con el flujo de autenticación y recuperación aprobado; no sería correcto presentarla como completada.

### Pruebas agregadas

`ClientInformationIntegrationIT` incluye casos para:
- Aislamiento entre dos instituciones con pólizas y licencias diferentes.
- Exclusión de información financiera y privada de la respuesta.
- Denegación de acceso al endpoint para personal interno y sesiones anónimas.
- Respuestas vacías en instituciones sin registros.

Ejecutar con una base PostgreSQL de pruebas aislada:
```bash
cd backend
mvn -Ppostgres-it -Dit.test=PortalLoginIntegrationIT,ClientInformationIntegrationIT verify
```

**Validación pendiente:** en esta sesión no se ejecutaron Java, PostgreSQL, compilación Angular ni pruebas reales de navegador. El código requiere revisión por CI y prueba manual antes de fusionar a `main`.

## Tercera etapa: bienvenida y acceso a solicitudes (RF-AYU-002, RF-CLI-002, RF-CLI-003)

- Se añadió `ClientGuideComponent` al panel del rol `USER`: la guía muestra acciones hacia **Mis solicitudes**, **Nueva solicitud**, **Mi información SIABUC** y **Centro de ayuda**.
- La migración `V28__client_guide_completed.sql` registra la finalización **por cuenta de usuario**, con fecha en PostgreSQL. No depende de localStorage ni del navegador.
- `GET /api/client-portal/guide` permite consultar si ya se terminó; `POST /api/client-portal/guide/complete` marca la finalización de forma idempotente. Ambas operaciones requieren rol `USER`.
- La guía aparece automáticamente si la cuenta nunca la completó. Después se puede abrir manualmente con **Primeros pasos** en la barra superior sin borrar el estado persistido.
- El resumen de actividad del cliente muestra las últimas cinco solicitudes disponibles a su cuenta/institución y enlaza directamente al expediente de cada caso.
- **Nueva solicitud** lleva al formulario actual de casos, que continúa aplicando sus controles existentes de canal, saldo, validación y licencia; no se duplicó la creación de tickets.
- Se añadió un estado vacío con botón de nueva solicitud cuando el cliente no tiene casos.
- El portal del personal interno conserva sus credenciales locales, su navegación y sus funcionalidades.

**Alcance preciso:** la guía se guarda por **cuenta**, todavía no por póliza (la identidad por póliza sigue pendiente). El resumen de solicitudes incorpora ya el indicador de respuestas públicas sin leer, descrito en la cuarta etapa. Los avisos por correo o notificaciones en tiempo real siguen fuera del alcance de esa mejora. No se han modificado estados, consumos ni reglas comerciales.

### Pruebas

`ClientGuideIntegrationIT` incluye:
1. Finalización persistida entre dos sesiones independientes.
2. Idempotencia al finalizar varias veces.
3. Aislamiento entre cuentas, incluso dentro de una misma institución.
4. Acceso prohibido para `ADMIN` y sesiones anónimas.

Ejecución propuesta en base PostgreSQL de pruebas:
```bash
cd backend
mvn -Ppostgres-it -Dit.test=PortalLoginIntegrationIT,ClientInformationIntegrationIT,ClientGuideIntegrationIT verify
```

**Estado de validación:** los cambios se han confirmado en la rama de revisión; falta compilación y ejecución de pruebas para declarar esta tercera etapa verificada.

## Cuarta etapa: respuestas de soporte sin leer

Se amplía el resumen de solicitudes `RF-CLI-003` con indicadores por cliente y caso:

- La migración `V29__client_case_reads.sql` conserva en PostgreSQL la última entrada visible por cuenta y caso. No depende de `localStorage` ni de cookies.
- `GET /api/client-portal/tickets/unread` devuelve únicamente casos de la institución de la sesión autenticada con sus cantidades de respuestas pendientes.
- Se contabilizan solo entradas públicas, no anuladas y creadas por cuentas internas. **No** cuentan comentarios del propio cliente ni notas internas.
- `POST /api/client-portal/tickets/{caseId}/read` recibe `{"lastVisibleEntryId":123}`. El servidor valida institución, caso y pertenencia de la entrada al historial público; no permite marcar como leído un mensaje privado ni una entrada ajena.
- Los marcadores solo avanzan (`GREATEST`) para que una petición atrasada no deshaga el progreso.
- El frontend muestra insignias de respuestas nuevas en el resumen y en la tabla de casos. Solo tras cargar el expediente se marca como visto el último registro realmente recibido en la respuesta; un mensaje generado después permanece sin leer.
- La funcionalidad no cambia cuentas, contraseñas, roles ni el acceso interno. No envía notificaciones push ni correo: las insignias se actualizan al cargar o actualizar las vistas.

### Pruebas agregadas

`ClientUnreadIntegrationIT` contiene pruebas para: aislamiento por cuenta e institución, exclusión de notas internas y respuestas propias, rechazo de marcadores internos o ajenos, idempotencia, marcador que nunca retrocede, persistencia entre sesiones y rechazo de roles no autorizados.

Validar en ambiente PostgreSQL aislado:

```bash
cd backend
mvn -Ppostgres-it -Dit.test=ClientUnreadIntegrationIT verify
```

**Pendiente antes de integrar:** compilación Spring/Angular, pruebas de integración reales, migraciones desde una base de prueba, navegador y regresión de acceso del personal. Los trabajos CI actuales de GitHub Actions continúan sin resultado confirmado.

## Quinta etapa: Centro de ayuda y catálogo de recursos oficiales

La base de conocimientos existente se conserva y se presenta a clientes como **Centro de ayuda**, enlazado a la sección **Descargas y recursos oficiales**.

- Migración `V30__official_resources.sql`: catálogo vacío por defecto, con tipos Instalador, Actualización, Reporte, Documento y Video.
- Sólo `ADMIN` puede crear, editar, publicar, retirar o archivar recursos; el catálogo está en modo borrador hasta que un administrador publique el enlace. `USER` y `TECH` solo ven publicaciones.
- Los enlaces registrados deben usar **HTTPS** y un dominio `ucol.mx` o subdominio `*.ucol.mx`. Se impiden esquemas no seguros, puertos externos, credenciales embebidas y hosts que suplantan dominios institucionales.
- El catálogo **no descarga, almacena ni valida la existencia** de archivos remotos; tampoco afirma que el recurso esté realmente alojado en un servidor SIABUC. Es responsabilidad de la administración comprobar el recurso antes de publicarlo.
- No se añadieron instaladores, parches ni enlaces de demostración a los datos de producción. Se requieren URL institucionales verificadas para ponerlo en servicio.
- Los clientes abren los recursos publicados directamente en la dirección institucional aprobada. No se requiere un segundo inicio de sesión en Helpdesk; las restricciones que pudiera tener el servidor destino son independientes.

### Pruebas locales frente a la validación HTTPS

El front y API de **desarrollo pueden seguir ejecutándose sobre HTTP en localhost**: por ejemplo `http://localhost:4200` y el puerto del backend según la instalación. La restricción HTTPS **sólo se verifica en el campo `targetUrl` de cada recurso**.

`OfficialResourceIntegrationIT` crea enlaces institucionales **sintéticos y no existentes** únicamente en la base de integración, para probar las reglas sin acceder por red. Comprueba visibilidad de borradores/publicaciones, permisos de administración, filtros por tipo, rechazo de URL no autorizadas y revisiones editoriales. No comprueba la entrega de bytes del archivo ni la seguridad del contenido remoto.

```bash
cd backend
mvn -Ppostgres-it -Dit.test=OfficialResourceIntegrationIT verify
```

**Pendiente:** ejecutar CI, validar compilación de Angular/Spring Boot, migraciones con PostgreSQL, verificar enlaces oficiales reales antes de publicarlos y probar apertura manual desde navegador. No se necesita publicar un servidor HTTPS para ejecutar las pruebas del catálogo.

## Sexta etapa revisada: acceso local, bloqueo y recuperación exclusivamente administrativa

**Criterio confirmado:** clientes y personal interno siguen usando **usuario y contraseña** del sistema, con pantallas de acceso separadas. No se vincula aún la identidad con póliza, correo ni licencia SIABUC9; tampoco se incorpora Federación UCOL.

### RF-LOG-005 y RF-LOG-006 — bloqueo configurable de cuentas clientes

- La migración `V31__client_login_lockout.sql` añade contador por cuenta `USER` y parámetros globales de bloqueo.
- Inicialmente se permiten cinco intentos fallidos y se bloquea la cuenta durante 15 minutos; el administrador puede ajustar el umbral de 3 a 10 y la duración de 1 a 1440 minutos.
- La configuración y el desbloqueo manual están disponibles exclusivamente a `ADMIN` desde **Administración → Usuarios**. El acceso interno local no se somete a esta nueva política.
- La API evita enumerar cuentas mediante mensajes de error específicos. Se requiere probar concurrencia, expiración de bloqueos y límites en un entorno aislado.

### RF-LOG-007 — recuperación exclusivamente mediante administrador interno

**No se ofrece ninguna recuperación automática o autoservicio.** Los clientes deben contactar al personal interno del Centro SIABUC para solicitar el restablecimiento. Únicamente una cuenta con rol `ADMIN` puede cambiar la contraseña de otra cuenta mediante **Administración → Usuarios → Editar acceso → Restablecer contraseña por administrador**. Los técnicos y cobranza no cuentan con permisos de gestión de usuarios.

El administrador debe comprobar la identidad y la autorización del solicitante por el procedimiento interno aplicable antes de realizar el cambio. La contraseña nueva se almacena con el algoritmo de hash habitual de Helpdesk y se invalida la sesión anterior mediante `auth_version`. Para cuentas cliente, el restablecimiento también reinicia el contador de errores y el bloqueo temporal.

- Se retiraron los endpoints públicos `/api/auth/recovery/request` y `/api/auth/recovery/reset` y se añadió una regla explícita de denegación.
- Se eliminó el servicio de envío de códigos de recuperación, su tabla y la migración V32. No se envían correos de recuperación, ni se generan códigos, ni se solicitan claves `MX-XXXX-XXX`.
- **La recuperación de número de póliza/correo mediante clave de licencia, planteada en el documento original de Adriana, sigue pendiente** por decisión de alcance.

### RF-LOG-008 — correo de contacto desde formulario de usuarios

Sólo `ADMIN` puede actualizar `app_users.email`, el correo de contacto de una cuenta de Helpdesk, mediante el formulario administrativo de usuarios. La edición de correo de cuentas cliente invalida la sesión anterior para requerir nueva autenticación; no inicia verificaciones automáticas ni envía mensajes.

**Terminología:** cualquier dirección registrada como asociada a una licencia SIABUC9 es un **correo asociado a la licencia**, no necesariamente un «correo comercial». Actualmente ese dato no está vinculado automáticamente a `app_users.email`, por lo que cambiar el correo de la cuenta de Helpdesk **no actualiza ni verifica el correo asociado a la licencia**. Esa vinculación sigue pendiente.

### Pruebas que deben ejecutarse

`ClientAccessIntegrationIT` verifica el bloqueo configurado, el desbloqueo administrativo, permisos de configuración, ausencia de endpoints de recuperación pública, restablecimiento de contraseña por administrador, invalidación de sesiones y edición de correo de contacto.

```bash
cd backend
mvn -Ppostgres-it -Dit.test=ClientAccessIntegrationIT verify
```

Los cambios están en la rama del PR #52, sin fusionar. Las notas de «validación pendiente» anteriores corresponden a cada entrega inicial; el estado vigente se consulta en los checks de la PR.

## Correcciones de revisión — 17 de noviembre de 2026

- El decorador de `UsersComponent` vuelve a estar unido a su clase, para permitir la compilación Angular.
- El contador de intentos fallidos normaliza el usuario con el mismo `trim()` del formulario de Spring Security. Una prueba de integración comprueba espacios iniciales/finales, tabulaciones, bloqueo y desbloqueo administrativo.
- La prueba Docker compara los nombres de todas las migraciones SQL aplicadas con los archivos de esta revisión, en lugar de esperar siempre 27.
- La comprobación Nginx valida el HTTP 302 de `/` y que `/poliza`, `/poliza/admin` y `/cases` sirven el frontend. Las regresiones de navegador conservan API simulada y comprueban la redirección de cada rol a su portal.
- Las herramientas Angular CLI/build se actualizan dentro de la rama 21.2 y `compiler-cli` se fija a la versión del compilador del proyecto. El lockfile incorpora las correcciones compatibles de dependencias disponibles.

**Bloqueo de seguridad pendiente:** `picomatch` está afectado por [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf). Al revisar el 17 de noviembre, la última versión publicada es 4.0.7 y el aviso no registra una versión corregida. La auditoría completa de dependencias de desarrollo continúa siendo obligatoria: no se ha omitido el paquete, rebajado el umbral ni añadido una excepción para aprobar CI. Este pendiente debe resolverse y volver a validarse antes de integrar.
