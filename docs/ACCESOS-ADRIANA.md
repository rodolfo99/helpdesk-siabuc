# Accesos separados para clientes y personal SIABUC

Adaptación inicial basada en los requerimientos `RF-LOG-001` y `RF-LOG-002` enviados por Adriana Barreda Beltrán.

## Rutas

| Ruta | Público | Cuentas permitidas |
| --- | --- | --- |
| `/poliza` | Clientes de instituciones | `USER` |
| `/poliza/admin` | Personal del Centro SIABUC | `TECH`, `BILLING`, `ADMIN` |

La raíz `/` redirige a `/poliza` mediante Nginx. Las rutas sirven el mismo frontend Angular, que presenta un formulario y un mensaje distintos en cada una. El backend comprueba además el rol después de autenticar: si no pertenece al portal seleccionado, responde 403, invalida la sesión y no permite entrar en el portal incorrecto.

## Alcance y compatibilidad

- Esta **primera etapa mantiene usuario y contraseña** con las cuentas y roles existentes. No se cambia el esquema PostgreSQL ni las credenciales guardadas.
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

## Sexta etapa: acceso local de clientes, bloqueo y recuperación (RF-LOG-005 a RF-LOG-008)

**Decisión funcional vigente:** el cliente inicia sesión mediante usuario y contraseña de Helpdesk; **no** se exige vincular número de póliza, correo y licencia SIABUC9. El personal interno (`ADMIN`, `TECH`, `BILLING`) conserva usuario/contraseña local y su ruta separada `/poliza/admin`; no se ha integrado Federación UCOL.

### RF-LOG-005 y RF-LOG-006 — bloqueo temporal de cuentas USER

- La migración `V31__client_login_lockout.sql` añade un contador de errores y fecha de desbloqueo por cuenta `USER` y los parámetros globales revisables `max_attempts` y `lock_minutes`.
- Configuración inicial: **5 errores** y **15 minutos** de bloqueo. El administrador puede escoger entre 3–10 errores y 1–1440 minutos.
- Spring Security deniega nuevas autenticaciones cuando el bloqueo está vigente; el handler registra las fallas atómicamente mediante bloqueos de fila PostgreSQL. El éxito limpia el contador y el bloqueo. Al terminar el periodo se permite iniciar sesión de nuevo.
- Un intento durante el bloqueo no extiende su duración.
- `GET/PUT /api/auth/client-login-settings` permite consultar/editar los límites sólo a `ADMIN`; `GET /api/auth/client-login-locks` muestra los contadores; `POST /api/auth/client-login-locks/{userId}/unlock` permite desbloquear manualmente, con auditoría. Las opciones están en **Administración → Usuarios**.
- Para no permitir enumerar cuentas existentes, los errores de inicio de sesión del cliente son genéricos. El bloqueo por cuenta no reemplaza futuras medidas de protección por IP, proxy o infraestructura institucional.
- La política se aplica únicamente a cuentas `USER`. Los roles internos conservan el inicio de sesión local sin el nuevo bloqueo.

### RF-LOG-007 — recuperación transitoria por correo (PARCIAL)

El requisito original describe recuperar número de póliza/correo mediante la clave `MX-XXXX-XXX`. **Eso sigue pendiente** de la integración de póliza, licencia y correo SIABUC9, que expresamente se pospuso.

Se implementó, en su lugar, recuperación segura de la **contraseña de la cuenta local de cliente**:
1. Desde `/poliza`, el cliente pulsa **¿Olvidaste tu contraseña?** y facilita usuario y correo guardado en `app_users.email`.
2. `POST /api/auth/recovery/request` responde siempre 202, exista o no la cuenta. Sólo cuando existe una cuenta `USER` activa con ese correo, el envío SMTP está habilitado y la cola no está pausada se genera un código aleatorio. Se emite como aviso por el sistema de correo existente.
3. El código se conserva como hash SHA-256 en `client_password_recovery` (`V32`), caduca en **15 minutos** y se limita la solicitud a una por cuenta cada cinco minutos. Los correos de recuperación contienen el código, pero los detalles de notificaciones administrativas lo ocultan y no permiten reintentos manuales.
4. `POST /api/auth/recovery/reset` valida usuario, código y contraseña nueva; el código se consume en una única operación transaccional, se cambia la contraseña cifrada con BCrypt, se incrementa `auth_version` para invalidar sesiones y se reinicia el bloqueo.

**Condición de uso:** mientras `MAIL_ENABLED=false` o el envío esté pausado, no se emiten códigos. El administrador puede utilizar el formulario existente de usuarios para restablecer la contraseña y corregir el correo. Debe activarse SMTP y comprobarse la entrega real antes de dar por disponible la recuperación al público.

### RF-LOG-008 — gestión del correo desde Administración → Usuarios (PARCIAL)

El formulario de usuarios permite cambiar el **correo de contacto y recuperación de la cuenta local** de cliente; su actualización queda bajo permisos de `ADMIN`. Cambiar correo, contraseña o desactivar una cuenta cliente revoca cualquier código de recuperación pendiente; un cambio de correo invalida sesiones anteriores.

**No** cambia el correo registrado en una licencia SIABUC9, ni verifica automáticamente que el nuevo buzón pertenezca al cliente. Es una adaptación administrativa temporal conforme al alcance solicitado; la verificación de correo nuevo y la vinculación comercial quedan pendientes.

### Pruebas de integración

`ClientAccessIntegrationIT` incluye escenarios para:
- Bloqueo después de tres errores cuando el umbral se configura a tres, rechazo durante el bloqueo y desbloqueo administrativo.
- Independencia del inicio de sesión de personal interno.
- Autorización y rangos de parámetros globales.
- Uso único de un código temporal de recuperación (registro preparado en la base de pruebas).
- Respuesta genérica 202 de solicitud y edición administrativa del correo.
- Revocación de códigos antiguos cuando un administrador modifica el correo.

Para ejecutar en PostgreSQL aislado:
```bash
cd backend
mvn -Ppostgres-it -Dit.test=ClientAccessIntegrationIT verify
```

**Estado:** cambios guardados en la rama del PR #52; aún faltan compilación, CI y pruebas reales de correo SMTP. No deben fusionarse ni considerarse completados hasta revisar resultados, migraciones y flujo de navegador.
