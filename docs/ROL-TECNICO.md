# Rol de técnico de soporte — versión 1.7

El rol `TECH` separa la atención operativa de la administración del sistema y la cobranza. Una cuenta técnica pertenece al equipo de soporte; no tiene institución cliente asignada. Administración crea y desactiva estas cuentas en **Usuarios**. Las cuentas existentes conservan su rol: para dar otro ámbito a una persona se crea una cuenta distinta.

## Acceso permitido

- Consultar los casos de todas las instituciones, sus fichas técnicas, prioridades, plazos SLA, registros públicos e internos y archivos autorizados por la visibilidad del registro.
- Consultar datos de contacto y saldos en minutos de las instituciones; las notas administrativas de la institución no se incluyen en la respuesta para TECH.
- Abrir un caso en una institución activa con saldo; el caso queda asignado al técnico que lo crea. Puede tomar un caso abierto sin responsable. Reintentar la misma toma no duplica el historial.
- Actualizar datos y ficha técnica de casos, agregar comentarios públicos o internos, registrar consumo y cerrar o reabrir un caso. Esta atención puede abarcar casos asignados a otro integrante del equipo; **no modifica su responsable**.
- Usar un cronómetro propio y confirmar los minutos antes de descontarlos. El control de saldo y la confirmación idempotente también se aplican a TECH. Un técnico no opera el cronómetro de otro usuario.
- Consultar la agenda de los casos y proponer sesiones para sí mismo. Puede reprogramar, cancelar o completar las sesiones de las que es responsable. Administración puede gestionar la agenda de cualquier responsable. Confirmar o rechazar una propuesta como cliente sigue correspondiendo a la institución.
- Consultar artículos publicados de la base de conocimientos y vincular o retirar vínculos de artículos en los casos. Borradores, artículos archivados e historial editorial no son visibles para TECH.
- Consultar su campana, marcar sus avisos como leídos y recibir avisos de comentarios, asignación, saldo bajo y plazos de atención. La campana funciona aunque esté pausado el correo.
- Cambiar su propia contraseña con la contraseña actual.

La primera respuesta pública de un técnico cuenta para el objetivo SLA de primera respuesta. Una nota interna no lo satisface. El SLA continúa separado del saldo contratado.

## Facultades que permanecen restringidas

| Acción | Quién puede realizarla |
|---|---|
| Crear, actualizar o dar de baja instituciones y cuentas | ADMIN |
| Cambiar el responsable de un caso ya asignado, dejarlo sin responsable o crear un caso asignado a otra persona | ADMIN |
| Anular una atención y reintegrar minutos | ADMIN |
| Configurar correo, calendario SLA, umbrales de saldo y respaldos | ADMIN |
| Consultar auditoría global, reportes generales de soporte y reportes de satisfacción | ADMIN |
| Registrar pagos, pólizas, cotizaciones, solicitudes de pago y operaciones financieras | ADMIN/BILLING, según las reglas propias de cada operación |
| Crear, editar, publicar o archivar artículos de conocimiento | ADMIN |
| Consultar portal financiero institucional | USER de la institución cuando ADMIN lo habilita |

Las restricciones se comprueban en el servidor; ocultar menús no es el mecanismo de autorización. Las modificaciones requieren sesión vigente y token CSRF. Una cuenta desactivada deja de funcionar también en sesiones abiertas.

## Responsables y avisos por correo

Administración puede elegir como responsable de una institución, caso o sesión a un `ADMIN` o `TECH` activo. Se mantienen los nombres de campos `supportAdminId`, `assignedAdminId` y `adminId` para compatibilidad con las versiones previas, pero desde 1.7 admiten ambos roles.

Los avisos de un caso toman el nombre y correo del responsable asignado, aunque sea TECH. Las notas internas sólo se notifican al responsable y no se envían al correo institucional. Se conserva la protección que evita enviar una nota interna si el correo del responsable coincide con el de la institución. TECH no accede a la cola de correo ni cambia su configuración. Al pausar y reanudar el correo se mantienen las reglas existentes: los avisos de la pausa no se envían retrospectivamente.

## API y actualización

- `GET /api/support-staff`: sólo ADMIN/TECH. Devuelve cuentas activas de soporte como `{id, displayName, role}` para seleccionar responsables; no expone correo, usuario de acceso, contraseña ni opciones de administración de cuentas.
- Las rutas existentes de casos, adjuntos, perfiles, cronómetros y agenda admiten TECH con los límites descritos. La anulación de consumos sigue verificando ADMIN dentro del servicio.
- `V17__technical_role.sql` amplía las restricciones de la tabla de usuarios para aceptar TECH sin institución. No transforma cuentas, cambia saldos ni otorga permisos a usuarios anteriores.
- No se modifica el valor por defecto de los respaldos automáticos: siguen **desactivados**.

## Verificación incluida

`TechnicianIntegrationIT` contiene ocho recorridos de integración para cuentas y permisos, atención entre instituciones, creación y asignación, cronómetro y agenda, privacidad de adjuntos, conocimiento publicado, avisos con correo pausado y desactivación de sesiones. La guía de validación de la versión indica cuáles se ejecutaron y en qué entorno; este documento no supone una ejecución de Docker o SMTP externo.
