# Encuestas de satisfacción

Disponibles desde 1.6.0. Al consultar un caso cerrado, un usuario cliente de su institución puede evaluar la atención con una puntuación de **1 a 5** y un comentario opcional de hasta **2,000 caracteres**. La encuesta es voluntaria y no consume ni acredita minutos.

## Responder desde el portal

1. Inicia sesión con una cuenta de cliente activa de la institución atendida.
2. Abre el caso cerrado y localiza **¿Cómo fue la atención?**.
3. Selecciona la puntuación: 1 Muy mala, 2 Mala, 3 Regular, 4 Buena o 5 Excelente.
4. Escribe un comentario si lo deseas y pulsa **Enviar evaluación**.

El agradecimiento muestra la puntuación, el comentario, quién respondió y cuándo. Se acepta **una respuesta por caso para toda la institución**, no una por usuario. Otro usuario de la misma institución puede consultar esa respuesta, pero no reemplazarla. Los administrativos pueden leerla y reportarla; no pueden contestar en nombre del cliente. Cobranza no accede a este módulo.

La respuesta no se edita ni se elimina desde la aplicación. Reabrir el caso conserva la evaluación y no habilita otra encuesta. Al cerrarlo nuevamente reaparece la misma respuesta. Si un caso se reabre antes del primer envío, el servidor rechaza ese envío hasta que vuelva a estar cerrado. La elegibilidad se verifica en la transacción de guardado, por lo que no basta con mantener un formulario antiguo abierto.

Los reintentos de una misma solicitud usan un UUID y recuperan la respuesta original sin crear otra. El mismo identificador sólo sirve para la misma cuenta, caso, puntuación y comentario. Dos usuarios que contesten simultáneamente no pueden crear dos encuestas: uno obtiene la respuesta guardada y el otro recibe un conflicto.

## Qué información se conserva

La encuesta guarda copias del nombre de la institución, título del caso, nombre y usuario del autor, administrativo asignado y fecha de recepción.

**La atribución corresponde al administrativo asignado en el momento de responder**, que puede ser distinto de quien cerró el caso o de otras personas que lo atendieron. No constituye una evaluación individual de todas las intervenciones. Una reasignación posterior no mueve la evaluación a otra persona. Si no había responsable, se conserva como **Sin administrativo asignado**. Cambios posteriores de nombres o del título tampoco reescriben la respuesta.

El envío queda en la auditoría administrativa con el identificador de encuesta y puntuación. No genera cargos, modificaciones al historial de atención ni correos adicionales. Los avisos de cierre del caso conservan el funcionamiento habitual. El comentario es texto plano, sin interpretar HTML.

## Reportes para administración

En **Satisfacción del soporte**, filtra por fechas de recepción de respuestas, institución y administrativo asignado al responder. Ambos días del periodo se incluyen según `REPORTING_TIME_ZONE` (por defecto `America/Mexico_City`). Se permiten periodos de hasta 3,660 días.

El informe presenta:

- Número de respuestas y suma de puntuaciones.
- Promedio general, calculado como suma de puntuaciones dividida entre número de respuestas.
- Distribución de frecuencias de 1 a 5.
- Muestras, suma y promedio de cada administrativo.
- Cada respuesta con caso, institución, autor, fecha y comentario.

Cada respuesta pesa lo mismo. **No se promedian los promedios de los administrativos.** Por ejemplo, tres respuestas de 5 y una de 1 dan `(5 + 5 + 5 + 1) / 4 = 4`, aunque estén repartidas entre dos administrativos. Un informe sin respuestas muestra **Sin muestras**, no una calificación de cero.

Los promedios se redondean a dos decimales. Se agrupa por identificador del administrativo, aunque su nombre haya cambiado; el grupo muestra el nombre guardado en la respuesta más reciente de ese resultado y cada fila conserva su propia copia histórica. Las personas sin asignación quedan en un grupo separado.

Las respuestas son voluntarias. El resultado describe únicamente las muestras recibidas y no es una tasa de satisfacción de todas las instituciones o de todos los casos. No se calcula una tasa de respuesta con denominadores de periodos distintos.

El límite es de 5,000 respuestas por consulta. Si se supera, se solicita reducir el periodo o agregar filtros; no se truncan muestras ni se calculan promedios con una selección oculta.

**Exportar respuestas CSV** descarga las filas con los filtros del último resultado consultado. Si hay cambios en el servidor entre consulta y descarga, la exportación refleja los datos vigentes al descargar. El CSV incluye encabezados, UTF-8 con BOM, campos entre comillas y protección contra fórmulas de hoja de cálculo en textos introducidos por usuarios. No hay exportación PDF específica de satisfacción en esta versión.

## API y almacenamiento

| Método y ruta | Permiso y finalidad |
|---|---|
| `GET /api/satisfaction/cases/{caseId}` | ADMIN o USER de la institución: estado, elegibilidad y respuesta guardada. |
| `POST /api/satisfaction/cases/{caseId}` | Sólo USER activo de institución activa, caso propio cerrado: `{requestId, score, comment}`. |
| `GET /api/satisfaction/options` | ADMIN: instituciones y responsables para filtros; incluye responsables históricos. |
| `GET /api/satisfaction/reports` | ADMIN: `from`, `to`, `institutionId` y `administratorId` opcionales. |
| `GET /api/satisfaction/reports.csv` | ADMIN: mismas opciones, descarga autenticada. |

Las mutaciones requieren sesión y CSRF. Una institución ajena recibe 404 al solicitar un caso de otra institución. Las lecturas y exportaciones llevan `Cache-Control: no-store`; el CSV también se descarga como adjunto y lleva `nosniff`.

La migración `V15__satisfaction_surveys.sql` crea la tabla con unicidad por caso y por UUID, restricciones de puntuación/comentario y referencias a institución, usuarios y caso. Los respaldos normales de PostgreSQL incluyen estas respuestas. No se requiere habilitar el portal financiero: la encuesta pertenece al soporte, no a la información de cobranza.

`SatisfactionIntegrationIT` cubre permisos, aislamiento, CSRF, validaciones, datos históricos, reintentos, reapertura, respuestas simultáneas, promedio por muestras, límites de fechas y protección del CSV. Se ejecuta dentro de `mvn -Ppostgres-it verify` contra una base de pruebas; la validación concreta del lanzamiento se documenta en las notas de versión.
