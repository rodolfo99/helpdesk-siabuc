# Campana, respuestas pendientes y avisos de saldo

Disponible desde SIABUC Helpdesk 1.4.0. Los avisos internos se guardan en PostgreSQL y aparecen en la campana de la plataforma. No requieren SMTP: continúan funcionando con el envío de correo pausado o con `MAIL_ENABLED=false`.

## Consultar avisos

1. Inicia sesión y pulsa **Notificaciones** en el encabezado.
2. Revisa los avisos recientes y, si eres administrativo o cliente, las respuestas pendientes de tu atención.
3. Pulsa **Ver caso** para abrir el caso relacionado. La plataforma marca ese aviso como leído.
4. Usa **Marcar leída** o **Marcar todas como leídas** para actualizar tu bandeja. La lectura se conserva al cerrar sesión y no modifica la bandeja de otro usuario.

La campana se actualiza cada 30 segundos mientras la cuenta está conectada. También puedes pulsar **Actualizar**. Muestra hasta 100 avisos recientes; el contador incluye todos los avisos accesibles sin leer. Las respuestas pendientes se muestran de la más antigua a la más reciente, hasta 100 casos. Cerrar la sesión detiene la actualización de esa cuenta.

## Quién recibe cada aviso

| Evento | Destinatarios dentro de la plataforma |
| --- | --- |
| Alta o actualización pública del caso, comentario, cambio de estado o asignación | Administrativo responsable y usuarios activos de la institución cliente. Si el caso no tiene responsable, todos los administrativos activos. |
| Nota interna y otras actualizaciones internas | Administrativo responsable; si no hay responsable, administrativos activos. Nunca clientes ni cobranza. |
| Saldo bajo de correo o remoto | Administrativos, cobranza y usuarios activos de la institución afectada. |

Quien realiza una actualización del caso no recibe un aviso de su propia acción. Los avisos de saldo son automáticos y sí pueden llegar al administrativo que registró el consumo. Los usuarios de cobranza no reciben información de casos ni enlaces a casos.

Los permisos se comprueban de nuevo al consultar o marcar un aviso. La API habitual no permite cambiar el rol o la institución de una cuenta existente: debe crearse otra cuenta. Como defensa adicional para datos heredados o importados, si esos datos cambian en la base de datos, los avisos de otra institución o de un ámbito administrativo anterior quedan inaccesibles. Los avisos no permiten consultar casos de otra institución.

## Respuestas pendientes

El estado del caso sigue siendo **Abierto** o **Cerrado**. La plataforma conserva por separado quién debe continuar la conversación:

| Acción | Respuesta pendiente de |
| --- | --- |
| Abrir o reabrir un caso | Administrativo |
| El cliente publica un comentario | Administrativo |
| El administrativo publica un comentario o atención visible para el cliente | Cliente |
| Cerrar el caso | Nadie |

**Tomar caso**, cambiar los datos del caso o escribir una nota interna no cuentan como una respuesta pública. El administrativo ve pendientes de casos asignados a él y de los que aún no tienen responsable. El cliente ve los pendientes de su institución. Al actualizar desde una versión anterior, los casos que estaban abiertos se presentan inicialmente como pendientes de revisión administrativa; los siguientes comentarios públicos actualizan el lado pendiente.

## Configurar avisos por saldo bajo

Como **ADMIN**, abre **Avisos de saldo**, selecciona una institución activa y guarda:

- **Activar avisos:** habilita o deshabilita nuevos avisos de saldo de esa institución.
- **Minutos de correo:** por defecto, 60.
- **Minutos de remoto:** por defecto, 30.

Los valores se expresan en minutos enteros entre 0 y 1 000 000 000. Un umbral de **0** avisa cuando el saldo se agota. El aviso se genera cuando los minutos restantes son **iguales o menores** al umbral. Guardar evalúa el saldo inmediatamente; un proceso automático también revisa las instituciones activas cada 60 segundos, incluyendo saldos preexistentes después de la actualización.

Ejemplo con umbral de correo de **20 minutos**:

1. El saldo baja de 25 a 20 minutos: se genera un aviso.
2. Baja de 20 a 15: no se repite el aviso del mismo periodo de saldo bajo.
3. Se registra una compra y sube a 75: la alerta queda preparada para un nuevo periodo.
4. Más adelante vuelve a bajar a 20 o menos: se genera un nuevo aviso.

El control se lleva por separado para correo y remoto. Las compras, anulaciones y devoluciones de minutos vuelven a evaluar el saldo. Modificar un umbral también puede iniciar o terminar un periodo de saldo bajo. Desactivar los avisos evita nuevos avisos; los que ya estaban guardados permanecen como historial. Reactivar evalúa el saldo actual y respeta el periodo de saldo bajo ya registrado.

Estos avisos no descuentan tiempo, no sustituyen el saldo real ni bloquean la plataforma. Las reglas existentes siguen impidiendo registrar una atención sin minutos suficientes.

## API y operación

Todos los endpoints requieren sesión. Las escrituras requieren el token CSRF habitual del sistema.

| Método y ruta | Uso |
| --- | --- |
| `GET /api/inbox` | Avisos de la cuenta actual, contador sin leer y respuestas pendientes. |
| `POST /api/inbox/{id}/read` | Marca un aviso propio accesible como leído; cuerpo `{}`. |
| `POST /api/inbox/read-all` | Marca todos los avisos propios accesibles como leídos; cuerpo `{}`. |
| `GET /api/balance-alert-settings` | Configuración y saldos de instituciones activas, sólo ADMIN. |
| `PUT /api/balance-alert-settings/{institutionId}` | Actualiza los umbrales de una institución, sólo ADMIN. |

Ejemplo de cuerpo para guardar umbrales:

```json
{
  "emailThresholdMinutes": 20,
  "remoteThresholdMinutes": 10,
  "enabled": true
}
```

Intentar marcar un aviso ajeno o al que ya no se tiene acceso devuelve `404`. La modificación de umbrales queda en la auditoría como `UPDATE_BALANCE_ALERTS`.

La migración `V7__portal_notifications.sql` agrega `support_cases.waiting_for`, `portal_notifications` y `balance_alert_settings`. Los respaldos de PostgreSQL incluyen esta información; no hay un volumen de archivos separado para la campana. El proceso de actualización aplica la migración mediante Flyway. No elimines volúmenes para instalar esta versión.

La revisión automática utiliza la propiedad Spring `app.balance-alert-scan-ms`, con valor predeterminado `60000`. La creación de avisos de caso y las evaluaciones de saldo se ejecutan dentro de la misma transacción que la operación principal: si la operación se revierte, no queda un aviso de una atención o compra que no se guardó.

El módulo de correo mantiene sus propias reglas: al pausar, se suprimen los envíos pendientes y los generados durante la pausa; reanudar envía los avisos nuevos desde ese momento. La campana conserva sus avisos internos independientemente de esa decisión. Consulta [DESACTIVAR-CORREO.md](DESACTIVAR-CORREO.md).
