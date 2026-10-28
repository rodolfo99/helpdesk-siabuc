# Casos relacionados e incidencias generales — 1.8.5

Administración y el rol Técnico pueden coordinar una falla común de SIABUC en **Incidencias**, relacionando los casos de distintas instituciones. Cada caso conserva su institución, responsable, historial, estado y consumo de soporte.

Una incidencia puede estar **Abierta**, **En atención** o **Resuelta**. Estos estados describen la coordinación del problema general. Los casos relacionados muestran sus cinco estados propios: **Recibido**, **En atención**, **Requiere respuesta del cliente**, **Resuelto** y **Cerrado**. Resolver una incidencia no resuelve ni cierra sus casos: soporte actualiza cada caso por separado según su atención. Crear, modificar, publicar o vincular una incidencia no acredita ni descuenta minutos y no genera pagos, entradas de atención ni correos automáticos.

Consulte el [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md) para combinar incidencias con clasificación, estados y atención individual.

## Flujo del equipo de soporte

1. En **Incidencias → Nueva incidencia**, capture título, descripción interna y estado. Puede agregar una nota de creación.
2. Abra la incidencia y use **Buscar un caso para vincular**. La búsqueda admite número de caso, título o nombre de institución; muestra hasta 20 resultados de instituciones activas. Vincule cada caso identificado.
3. También puede abrir un caso y utilizar **Incidencias relacionadas → Buscar incidencias**, elegir una existente y vincularla. Si necesita crear una nueva, hágalo desde el apartado Incidencias.
4. Registre avances internos con **Guardar avance**. Si cambia el diagnóstico o el estado general, use **Editar datos internos**.
5. Abra un caso relacionado para registrar allí la atención realizada, confirmar minutos cuando corresponda y actualizar su estado. El sistema no copia las descripciones privadas entre casos.
6. Si un caso fue asociado por error, seleccione **Retirar vínculo**. Se conserva el evento en la auditoría y puede volver a vincularlo posteriormente.

Los enlaces a un mismo caso/incidencia son idempotentes: repetir una vinculación activa o retirar un vínculo ya retirado no duplica el evento. Se pueden relacionar varios casos con una incidencia y varias incidencias con un caso.

## Publicar un aviso para clientes

El título, descripción y avances internos **no se convierten automáticamente en texto público**. Para compartir un aviso:

1. Abra **Resumen para clientes** dentro de la incidencia.
2. Escriba un **Título público** y un **Resumen público** independientes del diagnóstico interno.
3. Revise la vista previa. Omita nombres, números de casos, contactos, credenciales y cualquier información privada de otras instituciones.
4. Marque que revisó el contenido y pulse **Publicar resumen para clientes**.

La publicación guarda una copia del texto y del estado actual de la incidencia. Un cambio posterior del diagnóstico o estado interno no modifica esa copia; use **Actualizar publicación** para publicar nuevamente. La pantalla muestra la publicación vigente, su estado y fecha para evitar confundirla con los datos internos actuales.

**Retirar publicación** oculta el aviso en todos los casos vinculados. El texto anterior y los eventos quedan en el historial interno. Esta acción tampoco envía correos ni modifica casos.

La comprobación de privacidad del texto público es responsabilidad del personal que lo publica: el sistema separa campos y permisos, pero no interpreta si una frase escrita manualmente contiene datos privados.

## Lo que ve la institución

Un usuario cliente entra a **sus propios casos** y ve los avisos publicados en **Incidencias relacionadas**. Sólo recibe título público, resumen público, estado publicado y fecha de publicación. No recibe el identificador de la incidencia, lista de otros casos, conteos, nombres o identificadores de otras instituciones, autores internos, diagnósticos privados ni historial de la incidencia.

Si no existe una publicación vigente, la respuesta es una lista vacía; no se revela cuántas incidencias internas están relacionadas. El usuario no puede consultar el listado general ni abrir una incidencia por URL/API adivinando su identificador. Si intenta consultar los vínculos de un caso de otra institución, recibe un recurso no encontrado.

El personal autorizado podría escribir manualmente datos privados en el texto público; por eso la publicación requiere una revisión explícita en la interfaz. No se utiliza contenido privado del caso para completar automáticamente el formulario público.

## Historial y permisos

Cada creación, modificación, avance, publicación, retiro de publicación, vínculo y retiro de vínculo conserva autor, fecha y datos descriptivos. En **Historial interno de la incidencia**, pulse un elemento para ver su detalle y utilice **Volver al listado de historial** para regresar.

Las modificaciones, avances y publicaciones requieren la revisión vigente. Si otra persona guardó un cambio, se rechaza la versión anterior para no sobrescribirla; recargue la incidencia y revise antes de volver a guardar.

| Acción | Administración | Técnico | Cobranza | Cliente |
|---|---|---|---|---|
| Crear, editar, registrar avances y publicar | Sí | Sí | No | No |
| Vincular/retirar casos de instituciones activas | Sí | Sí | No | No |
| Ver datos internos e historial | Sí | Sí | No | No |
| Ver el aviso público desde su caso | Sí | Sí | No | Sólo de su institución |

Los datos internos son texto literal; la interfaz no ejecuta HTML escrito en títulos, resúmenes o notas. Las operaciones de escritura requieren sesión y token CSRF.

## API y migración

La migración `V20__related_incidents.sql` crea `support_incidents`, `incident_case_links` e `incident_history`. No modifica datos de casos, pagos, saldos ni configuración SMTP.

Rutas de Administración/Técnico:

- `GET /api/incidents?q=&status=&page=1&pageSize=20`: búsqueda paginada; estados `OPEN`, `IN_PROGRESS`, `RESOLVED`.
- `POST /api/incidents`: `{title, description, status, note?}`.
- `GET /api/incidents/{id}`: datos internos, casos relacionados e historial.
- `PUT /api/incidents/{id}`: datos del registro más `expectedRevision`.
- `POST /api/incidents/{id}/progress`: `{note, expectedRevision}`.
- `POST /api/incidents/{id}/publication`: `{published, publicTitle, publicSummary, expectedRevision}`. Para retirar basta `published:false` y la revisión vigente.
- `GET /api/incidents/{id}/case-options?q=...`: búsqueda de casos para vincular.
- `POST /api/incidents/{id}/cases`: `{caseId}`.
- `DELETE /api/incidents/{id}/cases/{caseId}`.
- `POST /api/cases/{id}/incidents`: `{incidentId}`.
- `DELETE /api/cases/{id}/incidents/{incidentId}`.

Lectura por caso: `GET /api/cases/{id}/incidents`. Administración/Técnico reciben vínculos internos; Cliente recibe exclusivamente la lista pública reducida tras comprobar su institución. Cobranza no accede a este módulo.

`IncidentIntegrationIT` cubre permisos, aislamiento institucional, publicación explícita, conservación de la copia pública, concurrencia por revisión, vínculos idempotentes, auditoría, validación, CSRF y ausencia de efectos en pagos, minutos, entradas y correos. La ejecución y sus resultados se registran en [VALIDACION.md](VALIDACION.md); la existencia del archivo de pruebas por sí sola no afirma que hayan pasado.
