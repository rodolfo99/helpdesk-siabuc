# Vistas guardadas y alertas de vencimiento — 1.8.5

Las vistas permiten repetir una consulta de casos sin reconstruir los filtros. Los avisos SLA recuerdan los objetivos de primera respuesta y resolución. Ninguna de estas funciones consume horas contratadas.

## Guardar y aplicar una vista

1. En **Casos**, ajuste búsqueda, estado, responsable, institución, categoría y prioridad.
2. En **Mis vistas guardadas**, escriba un nombre y pulse **Guardar filtros como nueva vista**.
3. Para reutilizarla, elíjala y pulse **Aplicar vista**. Se consultan los casos actuales, no una copia histórica del listado.
4. Para cambiarla, ajuste los filtros o el nombre y pulse **Actualizar vista seleccionada**.
5. **Eliminar vista** elimina sólo esa configuración personal; los casos e historiales se conservan.

Ejemplos para Administración y técnicos:

| Nombre sugerido | Filtros |
|---|---|
| Mis casos pendientes | Estado Pendientes de resolver; asignación Mis casos |
| Urgentes sin responsable | Estado Pendientes de resolver; asignación Sin responsable; prioridad Urgente |
| Instalaciones pendientes | Estado Pendientes de resolver; categoría Instalación |

**Pendientes de resolver** agrupa Recibido, En atención y Requiere respuesta del cliente. También puede guardar un estado concreto, Resuelto, Cerrado o Todos. Al actualizar a 1.8.3, las vistas antiguas con `status: "OPEN"` se convierten al grupo `active`, conservando la intención de mostrar todo el trabajo pendiente. Una vista nueva con `OPEN` selecciona únicamente **Recibido**.

Cada cuenta puede guardar hasta 50 vistas con nombres distintos (sin distinguir mayúsculas). Son personales: ni siquiera Administración puede leer o editar las vistas de otra cuenta mediante esta API. Los clientes conservan su acceso limitado a la institución asignada; no pueden guardar una institución ajena ni el filtro de asignación reservado al personal. Cobranza no accede al módulo de vistas técnicas.

Una vista guarda filtros, nunca SQL ni permisos. Los nombres no admiten más de 100 caracteres y la búsqueda admite hasta 300. Se rechazan claves y valores desconocidos. Si cambia la institución o el rol de una cuenta, las vistas incompatibles se omiten del listado y no pueden aplicarse. Guardar una revisión desactualizada devuelve conflicto para evitar sobrescribir cambios de otra sesión.

## Avisos de primera respuesta y resolución

Administración configura **Avisos antes del vencimiento** en los ajustes de atención:

- Activación: habilitados por defecto; pueden desactivarse sin borrar avisos anteriores.
- Anticipación: 60 minutos naturales por defecto; admite de 1 a 43200.
- **Revisar plazos ahora** ejecuta una revisión adicional. El ciclo automático se ejecuta cada 60 segundos mientras el servidor está iniciado.

Los objetivos y sus fechas se obtienen de la ficha SLA de cada caso, respetando la configuración de horario laboral, días inhábiles, prioridad y calendario conservado por el caso. **La anticipación del aviso usa minutos naturales**: no cambia el cálculo del plazo laboral.

Se revisan casos **Recibido, En atención y Requiere respuesta del cliente** de instituciones activas. Esperar al cliente **no pausa el SLA** ni sus avisos. Se avisa cuando falta como máximo la anticipación configurada, o cuando el objetivo ya venció y sigue pendiente. No se genera el aviso de primera respuesta si ya se respondió, ni de resolución si ya se resolvió; un caso Resuelto o Cerrado no genera avisos nuevos. Al reactivar la función se evalúan los objetivos que continúan pendientes, incluidos los ya vencidos.

Destinatarios:

- Administración: todos los casos.
- Técnico: casos asignados a su cuenta y casos todavía sin responsable.
- Cliente y Cobranza: no reciben estos avisos técnicos internos.

La campana conserva un aviso por **caso, objetivo, fecha de vencimiento y destinatario**. Leerlo, revisar manualmente o reiniciar el servidor no lo duplica. Una nueva fecha objetivo (por cambio de prioridad o reapertura) puede producir un nuevo aviso. Un nuevo responsable recibe su propio aviso si todavía no lo había recibido para esa fecha. Los avisos ya emitidos son históricos: para conocer el estado actual, abra el caso.

El evento interno es `SLA_DUE`. Funciona aunque SMTP esté detenido o el envío de correo esté pausado. No crea comentarios, atenciones ni notificaciones de correo y no modifica saldos.

## API y almacenamiento

| Ruta | Permiso | Operación |
|---|---|---|
| `GET /api/saved-views` | ADMIN / TECH / USER | Vistas propias compatibles con el acceso actual |
| `POST /api/saved-views` | ADMIN / TECH / USER | Crear vista propia |
| `GET /api/saved-views/{id}` | Propietario | Consultar y validar acceso actual |
| `PUT /api/saved-views/{id}` | Propietario | Actualizar con `expectedRevision` |
| `DELETE /api/saved-views/{id}` | Propietario | Eliminar vista |
| `GET /api/sla-alerts/settings` | ADMIN / TECH | Consultar configuración |
| `PUT /api/sla-alerts/settings` | ADMIN | Guardar activación y anticipación |
| `POST /api/sla-alerts/scan` | ADMIN | Revisar y devolver cantidad de avisos nuevos |

Ejemplo de vista:

```json
{
  "name": "Mis casos pendientes",
  "filters": {
    "search": "",
    "status": "active",
    "assignment": "mine",
    "institutionId": null,
    "category": "all",
    "priority": "all"
  }
}
```

Para actualizar añada `expectedRevision` con la revisión devuelta por el servidor. `status` admite `all`, `active`, `OPEN`, `IN_PROGRESS`, `WAITING_CLIENT`, `RESOLVED` y `CLOSED`; `assignment` admite `all`, `mine`, `unassigned`. Categoría y prioridad admiten `all` o los valores de la ficha técnica. El filtro de asignación de clientes debe ser `all`.

Configuración de avisos: `{"enabled":true,"leadMinutes":60}`. La revisión manual recibe `{}` y devuelve `{"created":0}` si no hay avisos nuevos. Las escrituras requieren sesión y CSRF. La migración `V18__saved_views_sla_alerts.sql` crea las vistas, configuración y registro de deduplicación; los avisos se guardan en la campana existente. No cambia migraciones anteriores.

El [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md) reúne estas funciones con el flujo completo de soporte.

Las pruebas `SavedViewIntegrationIT` y `SlaAlertIntegrationIT` verifican aislamiento, validación, revisiones, roles, desactivación, anticipación, deduplicación y saldos intactos. Consulte [VALIDACION.md](VALIDACION.md) para los resultados efectivamente ejecutados.
