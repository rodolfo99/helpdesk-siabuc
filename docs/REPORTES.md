# Reportes de casos y consumo — 1.8.5

Disponible desde la versión 1.4.0 en **Reportes**, para cuentas con rol **ADMIN**. El módulo permite consultar y descargar casos y atenciones por institución, administrativo, canal y periodo. Las cuentas de Técnico, Cliente y Cobranza no pueden acceder a estos reportes ni a sus archivos. Cobranza conserva su acceso al historial de pagos.

## Consultar un reporte

1. Inicia sesión como Administrador y abre **Reportes**.
2. Selecciona una institución o **Todas las instituciones**. Las instituciones inactivas permanecen disponibles para consultar su historial.
3. Selecciona un administrativo o **Todos los administrativos**. En 1.8.5 el selector de la interfaz ofrece cuentas `ADMIN`, también inactivas. El resultado sin filtro sí incluye casos y consumos de técnicos; para filtrar exclusivamente una cuenta `TECH`, la API admite su identificador en `administratorId`.
4. Selecciona correo, escritorio remoto o ambos canales.
5. Indica **Desde** y **Hasta**, incluyendo ambos días, y pulsa **Consultar reporte**.

Al entrar al módulo se consulta desde el primer día del mes hasta hoy, según la zona horaria del servidor de reportes. La pantalla indica el periodo, la zona horaria y la fecha de generación del resultado. Si modificas los filtros, debes volver a pulsar **Consultar reporte** para aplicarlos.

El resultado incluye:

- Número total de casos, **pendientes**, **resueltos** y **cerrados** por separado. Pendientes reúne Recibido, En atención y Requiere respuesta del cliente.
- Minutos de correo y escritorio remoto; la pantalla muestra también su equivalencia en horas y minutos.
- Atenciones vigentes y anuladas.
- Consumo agrupado por institución, administrativo que registró la atención y canal.
- Listado de casos con descripción desplegable.
- Listado de atenciones con descripción, visibilidad interna o pública, anulación y su motivo.

## Qué significa cada filtro

Los casos y los consumos son conjuntos distintos. Un caso puede haberse abierto el mes anterior y tener atenciones durante el mes consultado.

| Filtro o dato | Listado de casos | Listado de atenciones y consumo |
|---|---|---|
| Periodo | Fecha de creación del caso | Fecha en que se registró la atención |
| Institución | Institución propietaria del caso | Institución propietaria del caso atendido |
| Administrativo | Responsable **actual** del caso | Autor que registró la atención |
| Canal | Canal principal **actual** del caso | Canal al que se cargaron los minutos de la atención |
| Estado | Estado **actual**: Recibido, En atención, Requiere respuesta del cliente, Resuelto o Cerrado | Atención vigente o anulada |

Por ejemplo, un caso creado en enero y atendido por correo en febrero aparecerá en los **consumos de febrero**, aunque no aparezca en los **casos creados en febrero**. Si luego se cambia de responsable, el filtro de casos usará al nuevo responsable; los minutos siguen atribuidos al autor de cada atención.

Estos reportes no reconstruyen quién era responsable o qué estado tenía el caso en una fecha pasada. Muestran los casos creados en el periodo con sus datos actuales y las atenciones registradas en el periodo.

## Minutos, anulaciones y permisos

Solo las atenciones con minutos mayores que cero forman parte del listado de consumo. Los comentarios sin consumo permanecen en el historial del caso y no cuentan como atenciones consumidas en el reporte.

Una atención anulada se conserva en el listado, junto con sus minutos originales y el motivo de anulación, pero **no suma** a los minutos consumidos, las atenciones vigentes ni el consumo agrupado. La anulación se evalúa conforme al estado actual de la atención; un reporte histórico puede cambiar si posteriormente se anula una atención incluida en él.

Los administrativos pueden consultar las atenciones internas. Por ello, los archivos exportados son documentos administrativos: pueden contener información que no se muestra a las instituciones cliente. La API verifica el permiso ADMIN para el JSON, Excel y PDF; no basta con ocultar el botón en la interfaz.

Los totales no cuentan un caso Resuelto como Cerrado. En la API, `openCases` conserva su nombre por compatibilidad y ahora cuenta los tres estados pendientes; `resolvedCases` y `closedCases` cuentan sus estados respectivos. Excel y PDF utilizan las etiquetas españolas de los cinco estados.

Consultar o descargar un reporte no modifica saldos, casos, pagos ni atenciones. Los minutos de los reportes corresponden al consumo registrado; los plazos de primera respuesta y resolución se gestionan por separado.

## Exportar a Excel y PDF

Después de consultar, pulsa **Exportar Excel** o **Exportar PDF**. Se descargará un archivo con nombre semejante a:

```text
SIABUC-soporte-2026-10-01-2026-10-13.xlsx
SIABUC-soporte-2026-10-01-2026-10-13.pdf
```

Cada descarga usa los filtros del último resultado mostrado y consulta nuevamente los datos vigentes. Si alguien agrega o anula una atención entre la consulta y la descarga, los valores pueden cambiar. Pulsa **Consultar reporte** para actualizar la pantalla. Cada solicitud obtiene una instantánea consistente de casos y atenciones dentro de una transacción de solo lectura.

### Libro Excel

El `.xlsx` es un libro OOXML real, compatible con Excel y LibreOffice Calc, con cuatro hojas:

| Hoja | Contenido |
|---|---|
| Resumen | Periodo, zona horaria, filtros, criterios y totales |
| Casos | Casos creados en el periodo, datos actuales y descripción completa |
| Consumos | Atenciones, autor, canal, minutos, descripción, visibilidad y anulaciones |
| Consumo agrupado | Minutos vigentes por institución, administrativo y canal |

Las hojas de datos incluyen filtros y encabezado inmovilizado. Los minutos se exportan como números. Las descripciones y otros textos se escriben como celdas de texto, incluso si empiezan con `=`, `+`, `-` o `@`; no se crean fórmulas, macros ni enlaces ejecutables a partir del contenido capturado. Se conserva el texto Unicode original.

### Documento PDF

El PDF incluye criterios, totales, consumo agrupado, casos y atenciones con sus descripciones completas. El contenido pasa a las páginas siguientes cuando es necesario; cada página lleva encabezado y número.

La fuente incluye los acentos españoles y la `ñ`. Los caracteres fuera de su repertorio, por ejemplo ciertos emojis, se representan con su código Unicode, como `[U+1F600]`, y el documento agrega una nota explicativa. Para conservar su representación textual Unicode original utiliza Excel.

## Zona horaria y límites

En `.env`:

```dotenv
REPORTING_TIME_ZONE=America/Mexico_City
```

Se utiliza una zona válida de la base IANA. Esta configuración determina el día actual y los límites del periodo del reporte, así como las fechas mostradas y exportadas. Las marcas de tiempo originales continúan almacenadas en PostgreSQL con zona horaria. Si se ejecuta Spring fuera de Docker, la propiedad correspondiente es `app.reporting-time-zone`.

Después de modificar la configuración, aplica el inicio habitual con `bash iniciar.sh` en Linux o `iniciar.bat` en Windows para recrear los servicios con los valores de `.env`.

El periodo comienza a las 00:00 de **Desde** y termina justo antes de las 00:00 del día siguiente a **Hasta**, en la zona configurada. Esto evita omitir atenciones de las últimas horas del día o incluir las del día siguiente.

Límites por consulta:

- Hasta **366 días**, contando ambos extremos.
- Hasta **5000 filas**, sumando casos y atenciones.
- Hasta **2 millones de caracteres** entre descripciones de casos, atenciones y motivos de anulación.
- Hasta **1000 páginas** en PDF.

Si se supera un límite, el sistema devuelve un error y pide reducir el periodo o agregar filtros. **No genera un reporte incompleto ni elimina filas silenciosamente.**

## API y mantenimiento

Las tres rutas admiten los mismos parámetros opcionales:

```text
GET /api/reports/support
GET /api/reports/support.xlsx
GET /api/reports/support.pdf

?institutionId=12&administratorId=4&channel=EMAIL&from=2026-10-01&to=2026-10-13
```

`channel` admite `EMAIL` o `REMOTE`; omitirlo incluye ambos canales. Los identificadores deben ser positivos. Las fechas usan `AAAA-MM-DD`, con años entre 1900 y 9998. Fechas inválidas, periodos invertidos y límites excedidos devuelven HTTP 400. Un usuario sin permiso recibe 403; una solicitud sin sesión recibe 401.

Los archivos usan encabezado `Content-Disposition: attachment`, tipos MIME correspondientes a XLSX o PDF y `Cache-Control: no-store`. El JSON también se marca `no-store`.

La exportación utiliza [Apache POI 5.5.1](https://poi.apache.org/download.cgi) y [Apache PDFBox 3.0.7](https://pdfbox.apache.org/). Sus dependencias se resuelven al compilar el servidor con Maven. No requiere instalar Excel, LibreOffice ni un visor PDF en el servidor.

El [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md) incluye ejemplos para interpretar estos resultados junto con los saldos institucionales.

`ReportIntegrationIT` cubre permisos en los tres formatos, límites de fecha en Ciudad de México, consumo de casos anteriores, diferencias entre autor y responsable, exclusión de minutos anulados, rechazo de resultados excesivos, lectura del libro real con POI y lectura del PDF multipágina con PDFBox. La evidencia de ejecución del conjunto de pruebas se documenta en la validación de la versión.
