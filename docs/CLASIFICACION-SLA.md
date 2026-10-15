# Clasificación, ficha técnica y plazos de atención

Guía vigente para **SIABUC Helpdesk 1.8.3**. La clasificación y los plazos acompañan los cinco estados del caso: **Recibido**, **En atención**, **Requiere respuesta del cliente**, **Resuelto** y **Cerrado**. Categoría, prioridad y cumplimiento de plazos son datos independientes del estado. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo.

## Clasificar una solicitud

En **Casos → Nuevo caso** pueden indicarse la categoría, prioridad y datos técnicos desde la creación. Un usuario cliente crea solicitudes para su propia institución; Administración o un técnico puede seleccionar la institución.

| Categoría | Ejemplos de uso |
|---|---|
| Instalación | Instalación, actualización y puesta en marcha |
| Catalogación | Captura y modificación de registros bibliográficos |
| Préstamos | Circulación, préstamos y devoluciones |
| Reportes | Generación e interpretación de reportes |
| Importación / exportación | Transferencia y conversión de registros |
| Otro | Solicitudes que no corresponden a las anteriores |

Las prioridades disponibles son **Baja, Normal, Alta y Urgente**. La elección debe reflejar el impacto del problema; una prioridad urgente no modifica el saldo de minutos contratado. Si se omiten estos datos al usar una integración antigua, el servidor asigna **Otro / Normal**.

Dentro del caso, **Ficha técnica y plazos de atención** permite consultar y guardar:

- Categoría y prioridad.
- Versión de SIABUC, hasta 120 caracteres.
- Sistema operativo, hasta 180 caracteres.
- Mensaje de error exacto, hasta 10,000 caracteres.
- Pasos para reproducir el problema, hasta 10,000 caracteres.

Evite copiar contraseñas, claves o datos innecesarios en la ficha. Las capturas y archivos se agregan mediante el apartado de adjuntos, descrito en su guía correspondiente.

Administración, Técnicos y usuarios de la institución propietaria pueden modificar la ficha de un caso activo (Recibido, En atención o Requiere respuesta del cliente). Un caso Resuelto o Cerrado debe reabrirse antes de modificarla; una institución dada de baja no puede recibir cambios de ficha. Cobranza no tiene acceso a las fichas ni a los casos de soporte. Los cambios quedan registrados en auditoría administrativa.

El listado de casos permite filtrar por categoría y prioridad, además de los filtros de institución, responsable y estado.

## Qué miden los plazos —SLA—

Los objetivos de primera respuesta y resolución cuentan **minutos dentro del horario laboral**. No son minutos de atención facturable: ni el transcurso del tiempo ni un vencimiento descuentan saldo. Los minutos contratados se descuentan al registrar una atención con consumo, según las reglas de correo y escritorio remoto del sistema.

**Primera respuesta:** empieza al crear el caso y se completa con el primer comentario o atención **pública** que Administración o un técnico registra en el seguimiento. No cuentan los comentarios del cliente, notas internas, toma o reasignación del caso, cambios de datos, avisos de agenda ni cambios de estado. Una atención pública registrada mediante el cronómetro cuenta como respuesta cuando se confirma su registro. La fecha de primera respuesta se conserva si después se anula el consumo: la comunicación ya ocurrió.

**Resolución:** empieza al crear el caso y termina al pasar a **Resuelto** o al cerrar directamente un caso activo. Pasar después de Resuelto a Cerrado conserva la fecha original de resolución. Al reabrir un caso Resuelto o Cerrado en **En atención**, comienza un nuevo plazo de resolución desde la reapertura; la primera respuesta original se conserva. Los cambios anteriores siguen en el historial y la auditoría.

Cambiar entre Recibido, En atención y Requiere respuesta del cliente **no reinicia el plazo**. **Esperar al cliente no pausa el SLA**: el calendario laboral sigue contando y pueden generarse avisos de vencimiento. Los cronómetros de atención sí se pausan al esperar al cliente; son una medición independiente.

| Indicador | Significado |
|---|---|
| Dentro del plazo | El objetivo todavía está pendiente y la fecha límite no ha pasado |
| Plazo vencido | El objetivo está pendiente y ya pasó su fecha límite |
| Cumplido | La respuesta o resolución ocurrió como máximo en la fecha límite |
| Atendido fuera del plazo | La respuesta o resolución ocurrió después de la fecha límite |
| Sin fecha registrada | El caso está Resuelto o Cerrado y no hay evidencia de una fecha de respuesta o resolución para ese objetivo |

Resolver o cerrar un caso sin una respuesta pública no inventa una fecha de primera respuesta. La resolución y la primera respuesta son mediciones distintas.

## Configuración por Administración

Abra **Configuración de soporte → Horario y plazos de atención** con una cuenta de Administración. La API también admite consultar y actualizar la configuración mediante `/api/support-settings`, únicamente con una sesión ADMIN.

La configuración inicial es:

- Zona horaria: `America/Mexico_City`.
- Días laborales: lunes a viernes.
- Horario: 09:00 a 17:00.
- Fechas inhábiles: ninguna precargada; el administrativo debe registrar las que correspondan.

| Prioridad | Primera respuesta | Resolución |
|---|---:|---:|
| Baja | 480 min laborales | 2,400 min laborales |
| Normal | 240 min laborales | 1,440 min laborales |
| Alta | 120 min laborales | 480 min laborales |
| Urgente | 30 min laborales | 240 min laborales |

Son valores iniciales configurables, no una promesa contractual universal. Con una jornada de ocho horas, 480 minutos equivalen a una jornada completa. Cada objetivo admite de 1 a 43,200 minutos laborales; resolución debe ser igual o mayor que primera respuesta.

Elija una zona horaria IANA, por ejemplo `America/Mexico_City`. El horario de atención no depende de la zona del equipo del cliente ni de la del contenedor. Los objetivos y sus fechas se muestran usando la zona guardada para el caso. Las transiciones de horario estacional se calculan conforme a esa zona.

Puede seleccionar cualquier conjunto no vacío de días de la semana. Se admite una sola ventana diaria, con apertura y cierre dentro del mismo día y al menos 30 minutos de duración; no se admiten turnos que atraviesen medianoche ni horarios distintos para cada día. Para las fechas inhábiles, escriba una fecha `AAAA-MM-DD` por línea, sin repetir. Se permiten hasta 3,660 fechas entre 1900 y 2200. No se consulta un calendario de feriados externo automáticamente.

**Ejemplo:** con lunes a viernes de 09:00 a 17:00, una solicitud del viernes a las 16:30 con objetivo de 120 minutos tiene como límite el lunes a las 10:30. Si ese lunes está marcado inhábil, el límite pasa al martes a las 10:30. Una solicitud durante el fin de semana empieza a contar en la siguiente apertura. Cumplir exactamente en la hora límite cuenta como cumplimiento.

## Conservación de compromisos y casos existentes

Cada caso guarda una copia del calendario y objetivos vigentes al crearse. **Cambiar la configuración general afecta a los casos nuevos.** Los existentes conservan sus días, horario, zona, fechas inhábiles y tabla de objetivos.

Al cambiar la prioridad de un caso activo se usan los objetivos de la nueva prioridad de esa copia guardada. Se recalculan las fechas límite desde la creación para primera respuesta y desde la creación o última reapertura para resolución; cambiar prioridad no reinicia el reloj. La modificación queda en auditoría.

Los casos anteriores a 1.4.0 se clasifican inicialmente como Otro / Normal y reciben el calendario inicial de la migración. Se intenta recuperar la primera respuesta a partir de registros públicos reales de atención administrativa respaldados por `CREATE_ENTRY` en auditoría; los cierres y reaperturas se recuperan de los cambios de estado auditados. No se inventan fechas usando la última modificación del caso. La interfaz marca estos cálculos históricos como **orientativos**, porque esos plazos todavía no estaban configurados cuando se abrieron los casos.

## Integración y permisos

| Método / ruta | Uso | Acceso |
|---|---|---|
| `GET /api/case-profiles` | Resumen `{caseId,category,priority}` para filtros | ADMIN/TECH: todos; USER: su institución |
| `GET /api/cases/{id}/profile` | Ficha completa y mediciones SLA | ADMIN/TECH o USER de la institución |
| `PUT /api/cases/{id}/profile` | Actualizar categoría, prioridad y ficha técnica | ADMIN/TECH o USER de la institución, caso activo |
| `GET /api/support-settings` | Consultar calendario y objetivos | ADMIN |
| `PUT /api/support-settings` | Actualizar calendario y objetivos para nuevos casos | ADMIN |

Los cambios requieren el token CSRF de la sesión, igual que las demás operaciones del sistema. El acceso a una ficha de otra institución responde 404. BILLING no puede consultar estos endpoints de casos.

`POST /api/cases` admite el objeto opcional `profile` con `category`, `priority`, `siabucVersion`, `operatingSystem`, `errorMessage` y `reproductionSteps`. El servidor valida y guarda el caso y la ficha en una sola transacción: una ficha inválida no deja un caso incompleto.

Las categorías de API son `INSTALLATION`, `CATALOGING`, `LOANS`, `REPORTS`, `IMPORT`, `OTHER`; las prioridades son `LOW`, `NORMAL`, `HIGH`, `URGENT`. La configuración utiliza `timezone`, `workDays` (1=lunes a 7=domingo), `startTime`, `endTime`, `holidays` y `goals` con `firstResponseMinutes` / `resolutionMinutes` por prioridad.

Históricamente, `V5__case_profiles_sla.sql` incorporó las tablas `support_settings` y `case_profiles`. La versión 1.8.3 amplía los estados mediante `V26__case_workflow_states.sql`; no cambia las bolsas de tiempo ni reescribe las migraciones anteriores. Las pruebas del módulo están en `BusinessHoursTest` y `CaseProfileIntegrationIT`; consulte el informe de validación de la versión para los resultados efectivamente ejecutados.
