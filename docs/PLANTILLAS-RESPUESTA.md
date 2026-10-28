# Plantillas de respuesta — 1.8.5

Las plantillas reúnen instrucciones frecuentes de instalación, diagnóstico y solución. El texto se copia a un borrador que el personal revisa y adapta antes de registrar la respuesta.

## Crear y mantener plantillas

Administración abre **Plantillas**, pulsa **Nueva plantilla** y captura:

- Título (hasta 180 caracteres).
- Categoría: instalación, catalogación, préstamos, reportes, importación u otro.
- Versión de SIABUC opcional (hasta 120 caracteres).
- Contenido en texto plano (de 1 a 10000 caracteres).

Las plantillas nuevas quedan activas. Se puede buscar por título o contenido, categoría y versión. Administración puede incluir las archivadas, consultar su historial, editar las activas, archivarlas o reactivarlas. No se eliminan las revisiones ni se modifican respuestas que ya se registraron en casos.

El historial conserva el contenido completo, título, categoría, versión, estado, número de revisión, autor, acción y fecha. Una edición desde una pantalla desactualizada devuelve un conflicto; recargue el detalle antes de intentar guardar otra vez.

## Usar una plantilla en un caso

1. Administración o un técnico abre un caso activo (Recibido, En atención o Requiere respuesta del cliente) y el formulario para registrar un comentario o atención.
2. Pulsa **Usar una plantilla de respuesta** y busca por texto, categoría o versión.
3. Selecciona una plantilla activa y revisa la **Vista previa**.
4. Pulsa **Insertar en borrador**. El texto se añade al contenido existente, separado por una línea en blanco.
5. Personaliza los datos e instrucciones, verifica si la entrada será pública o interna y confirma los minutos sólo si corresponde una atención con cargo.
6. Utiliza el botón habitual para registrar el comentario o atención.

**Elegir, previsualizar o insertar una plantilla no registra una entrada, no envía correo y no descuenta minutos.** La confirmación del formulario de atención mantiene las reglas de saldo, permisos y pausa del correo existentes.

En un caso Resuelto o Cerrado, primero debe reabrir desde **Cambiar estado → En atención**, con motivo, institución activa y saldo en el canal principal. Insertar una plantilla no cambia el estado del caso. La suma del borrador y la plantilla no puede superar 10,000 caracteres.

El contenido se muestra como texto literal. No hay ejecución HTML ni sustitución automática de variables: revise nombres, rutas y demás datos antes de publicar. Una plantilla archivada desaparece de la selección del técnico. Una copia que ya estuviera en un borrador sigue siendo texto editable; archivar no borra ese borrador ni una respuesta histórica.

El [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md) reúne el procedimiento de respuesta, los estados y las reglas de cargo.

## Permisos y API

| Operación | Administración | Técnico | Cliente / Cobranza |
|---|---|---|---|
| Consultar plantillas activas y previsualizar | Sí | Sí | No |
| Insertar texto en borrador de caso | Sí | Sí | No |
| Crear y editar | Sí | No | No |
| Archivar / reactivar / ver revisiones | Sí | No | No |

- `GET /api/reply-templates?q=&category=&siabucVersion=&includeArchived=false`: resúmenes sin el contenido completo. El técnico siempre recibe sólo activas aunque solicite archivadas. `q` y versión se buscan como texto literal, sin comodines SQL.
- `GET /api/reply-templates/{id}`: contenido completo autorizado; una archivada devuelve 404 al técnico.
- `POST /api/reply-templates`: `{title,category,siabucVersion,body}`.
- `PUT /api/reply-templates/{id}`: mismos campos y `expectedRevision`.
- `POST /api/reply-templates/{id}/status`: `{status:"ACTIVE"|"ARCHIVED",expectedRevision}`.
- `GET /api/reply-templates/{id}/history`: revisiones completas, sólo Administración.

Las escrituras exigen CSRF y sesión. Los permisos se verifican en servidor, además de la interfaz. No existe un endpoint que envíe una plantilla directamente al cliente. La migración `V19__reply_templates.sql` crea catálogo e historial sin modificar casos, contabilidad o correo.

`ReplyTemplateIntegrationIT` cubre permisos, contenido privado, archivos históricos, revisiones, archivo/reactivación, búsqueda literal, validación, CSRF y ausencia de consumo al consultar. Los resultados de ejecución se registran en [VALIDACION.md](VALIDACION.md).
