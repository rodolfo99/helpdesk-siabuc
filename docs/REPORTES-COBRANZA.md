# Reportes de cobranza

Desde la versión 1.5.0, **Reportes de cobranza** está disponible para los usuarios de **Cobranza (BILLING)** y los **Administrativos (ADMIN)**. Permite consultar el historial de pagos, totalizar por institución y obtener totales generales, siempre separando las monedas. También muestra la situación actual de las pólizas institucionales.

Las instituciones cliente no acceden a estos reportes, tampoco cuando se habilita el portal financiero de 1.6. Ese portal empieza desactivado por defecto y presenta un resumen propio de pólizas y pagos, con sus recibos/comprobantes y solicitudes, sin abrir informes generales ni historiales administrativos. Consulte [Portal financiero](PORTAL-FINANCIERO.md). Dar acceso financiero a Cobranza no abre los casos, sus notas internas, adjuntos ni los reportes técnicos de soporte.

## Consultar y filtrar

1. Inicia sesión con una cuenta de Cobranza o Administrativo.
2. Abre **Reportes de cobranza**.
3. Selecciona institución, póliza, método, moneda, estado y fechas.
4. Pulsa **Consultar reporte**.

| Filtro | Comportamiento |
|---|---|
| Institución | Todas o una institución, incluidas las inactivas para consultar su historial |
| Póliza | Todas las pólizas y pagos libres, o una póliza concreta; también permite consultar canceladas |
| Método | Transferencia, depósito, efectivo, tarjeta de crédito, tarjeta de débito, cheque, otro o tarjeta histórica |
| Moneda | Todas sin mezclarlas, MXN u otra moneda histórica encontrada en los resultados |
| Estado | Vigentes y anulados, solo vigentes o solo anulados |
| Fechas | Fecha del pago (`paidOn`), incluyendo Desde y Hasta; no se usa la fecha en que se capturó el registro |

Al entrar se consulta desde el primer día del mes hasta hoy. Si modificas un filtro, pulsa **Consultar reporte** para actualizar el resultado. La lista de monedas históricas se amplía conforme aparecen en las consultas realizadas con todas las monedas.

Un pago recibido el 15 de enero y registrado el 20 de enero pertenece al reporte de pagos del **15 de enero**. Las fechas de registro, autor y anulación también están disponibles como información de trazabilidad.

## Totales e historial

La pantalla y las exportaciones incluyen:

- **Totales generales por moneda:** dinero cobrado vigente, importes anulados, número de movimientos vigentes y anulados y minutos acreditados vigentes.
- **Totales por institución y moneda:** suman todos los métodos para esa institución. Por ejemplo, 100.10 MXN en efectivo y 200.20 MXN por depósito suman 300.30 MXN para la institución.
- **Detalle por institución, método y moneda:** permite identificar cómo se recibieron los pagos.
- **Historial de pagos del periodo:** referencia, institución, póliza, importe, moneda, fecha, método, minutos, descripción, autor y anulación con motivo, responsable y fecha.
- **Situación actual de las pólizas:** contratación, pagos acumulados y pendiente por cobrar, descritos en el apartado siguiente.

Los pagos anulados permanecen visibles, pero sus importes no se suman a lo cobrado vigente y sus minutos no se suman al tiempo acreditado vigente. Si seleccionas **Solo anulados**, los totales vigentes del resultado serán cero.

Los registros históricos en otras monedas se conservan. **No se convierten ni se suman monedas distintas.** Un total de 300.30 MXN y otro de 12.34 USD se muestran por separado; no se presenta un supuesto total de 312.64 pesos.

Los cálculos monetarios se realizan en el servidor con aritmética decimal (`BigDecimal`). La interfaz presenta esos resultados y no suma importes con operaciones de coma flotante.

### Abonos y acreditación posterior

Una póliza puede tener abonos de dinero que todavía no liberan minutos. Esos abonos cuentan como dinero cobrado y aportan cero minutos al reporte.

Cuando la póliza ya está totalmente pagada, un registro permitido de acreditación posterior puede liberar minutos pendientes con importe cero. Cuenta como **movimiento vigente**, aumenta los minutos acreditados y no aumenta el dinero cobrado. Por eso el contador usa la palabra movimientos y no equivale necesariamente al número de depósitos bancarios.

Crear la póliza por sí sola no acredita horas ni constituye un pago. Los pagos y sus anulaciones son los movimientos que afectan los totales correspondientes.

## Qué cambia con las solicitudes, cotizaciones y recibos de 1.6

Los informes conservan el criterio de 1.5: suman **pagos registrados**, incluyendo los creados al aprobar una solicitud. Una solicitud pendiente o rechazada, sus comprobantes o una cotización aceptada no representan por sí solos un ingreso ni minutos acreditados. Al aprobar, el pago generado entra en los reportes con su fecha `paidOn`, que puede ser anterior al día de aprobación; no se suma también la solicitud como un segundo movimiento.

Convertir una cotización crea una póliza sin pagos: su contrato puede aparecer en la situación actual de pólizas y su pendiente de cobro, pero todavía no añade dinero recibido ni tiempo acreditado al periodo. Una renovación conserva la relación con la póliza anterior sin trasladar ni duplicar pagos. Las canceladas continúan excluidas de la deuda computable.

Emitir o descargar un [recibo administrativo PDF](RECIBOS-PAGO.md) tampoco añade un ingreso ni altera los totales. El recibo acredita un registro administrativo y no es CFDI, factura ni constancia de una devolución bancaria. Los comprobantes descargables conservan evidencia documental; no se agregan como filas monetarias adicionales.

Consulte [Solicitudes de pago](SOLICITUDES-PAGO.md), [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md) y [Pólizas y cobranza](POLIZAS-COBRANZA.md) para los estados y sus efectos.

## Situación actual de pólizas: todo el histórico

Este apartado está separado de los pagos del periodo. Muestra el estado **actual** de las pólizas considerando todos sus pagos vigentes, sin limitarse a las fechas consultadas.

Solo se aplican los filtros **Institución** y **Póliza**. No se aplican los filtros de fecha, método, moneda ni estado de los pagos.

Ejemplo:

- Póliza contratada por 1000.00 MXN.
- Abono vigente de 100.00 MXN el 1 de enero.
- Abono vigente de 200.00 MXN el 15 de enero.
- Consulta del 15 de enero: pagos del periodo = **200.00 MXN**.
- Situación actual de la póliza: pagado = **300.00 MXN**, pendiente = **700.00 MXN**.

Se muestran el total contratado, pagado y pendiente de las pólizas activas, además de su detalle: folio, institución, vigencia, estado y minutos contratados y acreditados por canal.

Las pólizas canceladas se conservan en la tabla, pero **no suman deuda**. Una cancelada puede mostrar un remanente contractual sin utilizar; la interfaz lo marca como no computable como deuda. Excel añade una columna explícita de deuda computable, con cero para las canceladas.

La vigencia se muestra para consulta. Estos reportes no caducan ni retiran automáticamente minutos de las bolsas institucionales. El apartado de pólizas **no es un corte contable a la fecha final**: si se registra otro abono después de ese periodo, su situación actual lo incluirá.

## Descargar Excel o PDF

Después de consultar, pulsa **Exportar Excel** o **Exportar PDF**. El archivo se llama, por ejemplo:

```text
SIABUC-cobranza-2026-10-01-2026-10-13.xlsx
SIABUC-cobranza-2026-10-01-2026-10-13.pdf
```

Las descargas utilizan los filtros del último resultado mostrado. Cada descarga consulta los datos vigentes dentro de una transacción de solo lectura con una instantánea consistente. Si otro usuario registra un pago entre la consulta y la descarga, el archivo puede reflejar ese movimiento. Consulta de nuevo para actualizar la pantalla.

### Excel

El archivo `.xlsx` es un libro OOXML real con cinco hojas:

| Hoja | Contenido |
|---|---|
| Resumen | Filtros, criterios, totales por moneda y resumen de pólizas activas |
| Pagos | Historial completo de los pagos incluidos, con descripciones y anulaciones |
| Totales por institución | Consolidado por institución y moneda, sumando todos los métodos |
| Agrupado | Desglose por institución, método y moneda |
| Pólizas situación actual | Saldos contractuales de todo el histórico, incluyendo deuda computable |

Los importes son celdas numéricas con dos decimales. Los datos capturados como texto, incluidas las referencias que empiezan por `=`, `+`, `-` o `@`, se guardan como texto: no se crean fórmulas, macros ni enlaces ejecutables con ellos. Se conserva Unicode original en las descripciones. Las hojas de datos incluyen filtros y encabezados inmovilizados.

### PDF

El PDF incluye los mismos apartados con descripciones completas, encabezados y numeración de páginas. El contenido pasa a las páginas siguientes cuando hace falta.

Los acentos españoles y la `ñ` se representan directamente. Los caracteres que la fuente no incluye, como ciertos emojis, se conservan mediante códigos `[U+...]`, acompañados de una nota al final. Excel conserva el texto Unicode original.

## Configuración y límites

Se utiliza la misma configuración de zona horaria de los reportes de soporte:

```dotenv
REPORTING_TIME_ZONE=America/Mexico_City
```

Determina el día actual utilizado en el periodo inicial y la presentación de las marcas de tiempo de registro y generación. La fecha de pago es un dato de calendario y se filtra directamente, sin desplazarla por zona horaria.

Límites de cada consulta:

- Hasta **3660 días**, incluyendo ambos extremos.
- Hasta **5000 filas**, sumando pagos del periodo y pólizas de la situación actual.
- Hasta **2 millones de caracteres** entre descripciones de pagos y motivos de anulación.
- Hasta **1000 páginas** en PDF.

Cuando se supera un límite, la consulta falla con una indicación para reducir filtros. No se generan archivos incompletos ni se omiten filas silenciosamente. Como las pólizas se consultan con todo su histórico, si el límite se debe a muchas pólizas hay que filtrar institución o póliza; reducir solo el periodo de pagos no reduce ese apartado.

Consultar o descargar no modifica pagos, pólizas ni minutos disponibles.

## API

```text
GET /api/reports/billing
GET /api/reports/billing.xlsx
GET /api/reports/billing.pdf
```

Parámetros opcionales compartidos:

```text
?institutionId=12&policyId=7&method=DEPOSIT&currency=MXN&status=ACTIVE&from=2026-10-01&to=2026-10-13
```

- `institutionId`, `policyId`: enteros positivos.
- `method`: `TRANSFER`, `DEPOSIT`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `CHECK`, `OTHER`, `CARD`; omitir o `ALL` para todos. `CARD` conserva el método genérico de pagos históricos.
- `currency`: `ALL` por defecto o un código de tres letras mayúsculas. No hay conversión de moneda.
- `status`: `ALL` por defecto, `ACTIVE` o `VOIDED`.
- `from`, `to`: `AAAA-MM-DD`; por defecto primer día del mes y día actual.

JSON devuelve `generatedAt`, `timeZone`, `filters`, `payments`, `totals`, `institutionTotals`, `groups`, `policyBalances` y `policyTotals`. Los importes de `totals`, `institutionTotals` y `groups` siempre llevan moneda. `policyTotals` expresa su moneda en los nombres `agreedMXN`, `paidMXN` y `pendingMXN` porque las pólizas se contratan en pesos mexicanos.

Las tres rutas verifican rol BILLING o ADMIN. La habilitación del portal institucional no cambia esta restricción. Devuelven 403 a una cuenta cliente y 401 sin sesión. Las respuestas se marcan `Cache-Control: no-store`; los archivos usan `Content-Disposition: attachment` y el tipo MIME correspondiente.

La exportación reutiliza las dependencias de Apache POI y Apache PDFBox incorporadas en la versión 1.4. No requiere instalar Excel, LibreOffice ni un visor PDF en el servidor.

`BillingReportIntegrationIT` verifica separación de monedas históricas, suma decimal exacta por institución entre métodos diferentes, filtros por fecha del pago, anulaciones, situación contractual independiente del periodo, permisos de los tres formatos y separación del contenido técnico. También abre el libro con POI y el PDF multipágina con PDFBox, comprueba texto seguro para Excel y rechaza resultados excesivos. La evidencia de ejecución se publica en la validación de la versión.

## Conservación de la información

Los pagos, solicitudes, pólizas y relaciones de renovación se conservan en PostgreSQL. Los archivos exportados son consultas de esos datos y no sustituyen un respaldo de la base. Los scripts manuales de [respaldo y restauración](RESPALDO-RESTAURACION.md) siguen vigentes; los [respaldos automáticos](RESPALDOS-AUTOMATICOS.md) de 1.6 requieren configuración y activación expresa de ADMIN, con calendario desactivado por defecto. Los importes, monedas y evidencias históricas de 1.5 se mantienen sin conversión ni recreación de movimientos.
