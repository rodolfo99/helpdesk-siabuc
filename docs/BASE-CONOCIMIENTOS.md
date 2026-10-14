# Base de conocimientos y soluciones de casos

Disponible desde la versión 1.6. Reúne procedimientos de SIABUC en texto plano, con búsqueda por título, resumen o contenido y filtros por categoría y versión. Las categorías originales (Instalación, Catalogación, Préstamos, Reportes, Importación / exportación y Otro) se conservan. La versión 1.8 agrega Guías y primeros pasos, Preguntas frecuentes, Soporte técnico, Pólizas y licencias, Cursos y capacitación y Contacto, sin publicar automáticamente contenidos no aprobados. La versión es texto descriptivo; por ejemplo, `SIABUC 9.1` o `SIABUC 8 y 9`. Dejarla vacía indica una solución general.

## Accesos

| Función | Administrativo | Cliente institucional | Cobranza |
|---|---|---|---|
| Buscar y consultar artículos publicados | Sí | Sí, con cuenta e institución activas | No |
| Consultar borradores, archivados y revisiones editoriales | Sí | No | No |
| Crear, editar, publicar y archivar | Sí | No | No |
| Vincular o retirar soluciones de un caso | Sí | No | No |
| Consultar soluciones vinculadas | Todos los casos | Sólo casos de su institución y artículos publicados | No |

Las publicaciones son comunes a todas las instituciones; no incluir datos de clientes, contraseñas ni secretos. La API comprueba los permisos aunque se invoque directamente sin la interfaz. Un ID de artículo en borrador o archivado devuelve 404 al cliente. Los enlaces a esos artículos se excluyen por completo de su respuesta: no aparecen títulos, IDs ni metadatos de vínculo. El historial editorial es exclusivo del administrativo.

## Flujo editorial

1. Abrir **Base de conocimientos → Nuevo artículo**.
2. Capturar título, categoría, versiones aplicables, resumen y solución con pasos claros. La nota editorial permite explicar el cambio.
3. **Guardar borrador**. Todavía no se muestra a las instituciones.
4. Revisar el texto y seleccionar **Publicar artículo**. Capturar un motivo y confirmar. Desde ese momento aparece en la consulta global.
5. Para corregir un artículo publicado, seleccionar **Pasar a borrador** y explicar el motivo. Mientras esté en borrador deja de aparecer al cliente, incluso en casos donde fue vinculado. Editar y volver a publicar después de revisarlo.
6. **Archivar artículo** lo retira de la consulta sin borrar sus versiones. Para recuperarlo, usar **Recuperar como borrador**, revisar y publicarlo nuevamente.

No hay borrado físico de artículos ni revisiones. Cada alta, edición y cambio de estado incrementa la revisión y guarda una copia completa del contenido, el estado, el autor, la fecha y la nota editorial. La revisión actual se muestra en pantalla. Si otro administrativo guardó antes, el servidor responde 409 y no sobrescribe su trabajo: copiar primero cualquier texto local que se quiera conservar y seleccionar **Recargar versión actual** antes de editar de nuevo.

Se admiten hasta 180 caracteres de título, 120 de versión, 1,000 de resumen o nota y 50,000 de solución. Todo se muestra mediante texto escapado; etiquetas como `<script>` se muestran literalmente, sin ejecutar HTML, JavaScript ni cargar recursos externos. El contenido no admite formato enriquecido ni adjuntos en esta versión. Los archivos de diagnóstico continúan asociados a los casos.

## Vincular soluciones a un caso

En el apartado **Soluciones vinculadas al caso**, el administrativo busca un artículo publicado y selecciona **Vincular solución**. El cliente puede abrirlo para consultar todos los pasos y regresar al listado mediante **Volver al listado de soluciones**. Vincular o retirar una solución no cierra el caso, no modifica el saldo y no descuenta minutos de soporte.

Los enlaces apuntan a la publicación actual, no a una copia congelada de la revisión original. Si la solución se archiva o pasa a borrador, se oculta al cliente; al publicarse nuevamente, el vínculo existente muestra la revisión vigente. El administrativo conserva a la vista los vínculos ocultos y puede retirarlos. **Retirar vínculo** conserva el artículo y la auditoría. Una petición repetida de vinculación o retiro no duplica el evento; un retiro seguido de una nueva vinculación sí deja nuevos eventos auditables. Las instituciones dadas de baja no admiten nuevos cambios de vínculos.

## API y datos

Todas las rutas usan el prefijo `/api`, sesión autenticada y protección CSRF en operaciones que modifican datos.

| Método y ruta | Uso |
|---|---|
| `GET /knowledge` | Lista paginada. Parámetros `q`, `category`, `siabucVersion`, `status`, `page`, `pageSize`. |
| `GET /knowledge/{id}` | Artículo actual con contenido completo; cliente sólo publicado. |
| `POST /knowledge` | Alta administrativa de borrador. |
| `PUT /knowledge/{id}` | Edición administrativa de un borrador. Requiere `expectedRevision`. |
| `POST /knowledge/{id}/status` | Cambio editorial con `status`, `expectedRevision` y `changeNote` obligatorio. |
| `GET /knowledge/{id}/history` | Revisiones completas, sólo administrativo. |
| `GET /cases/{id}/knowledge-links` | Soluciones autorizadas del caso. |
| `POST /cases/{id}/knowledge-links` | Vincular mediante `{ "articleId": 123 }`, sólo administrativo. |
| `DELETE /cases/{id}/knowledge-links/{articleId}` | Retirar el vínculo conservando evidencia, sólo administrativo. |

La lista devuelve `{items,total,page,pageSize}`. El tamaño predeterminado es 20 y el máximo 100. La búsqueda de texto ignora mayúsculas y trata `%` y `_` como caracteres literales; no promete equivalencia de acentos. Para clientes, cualquier filtro de estado sigue limitado a publicados. La búsqueda de versión es una coincidencia parcial de texto, no una comparación semántica de números de versión.

El cuerpo de alta/edición contiene `title`, `category`, `siabucVersion`, `summary`, `body`, `changeNote` y, al editar, `expectedRevision`. Los estados son `DRAFT`, `PUBLISHED` y `ARCHIVED`. La migración `V14__knowledge_base.sql` agrega artículos, historial editorial, vínculos y eventos de vínculos; no transforma ni elimina registros existentes. Estas tablas se incluyen en el respaldo PostgreSQL normal y en su restauración. No hay archivos adicionales que copiar para este módulo.

## Pruebas

`KnowledgeIntegrationIT` comprueba publicación y ocultamiento, roles, aislamiento de casos por institución, filtros, revisión obsoleta, transiciones válidas, snapshots, validación de texto, vinculación idempotente, retiro auditable y conservación de minutos. Usa el harness HTTP real con sesión y CSRF, correo deshabilitado y una base PostgreSQL de pruebas configurada como las demás pruebas de integración del proyecto.

Desde `backend`, con las variables de conexión de una base desechable, ejecutar:

```bash
mvn -Ppostgres-it -Dit.test=KnowledgeIntegrationIT verify
```

La suite no envía correo, no aplica cobros ni modifica una instalación de producción. Las pruebas de servidor almacenan las etiquetas como texto; la verificación visual del cliente debe confirmar que sólo aparecen como caracteres y no como elementos ejecutables.

La interfaz tiene además `tests/e2e/knowledge-v16.ui.mjs`, con 10 recorridos sobre Angular compilado y API simulada. Incluye publicación, control de revisión, regreso a listados, vínculos y vista móvil; comprueba que etiquetas sintéticas permanezcan como texto en el DOM. Ver `tests/e2e/README.md` para preparar el navegador y ejecutar. Estas simulaciones no sustituyen las comprobaciones HTTP del servidor.
