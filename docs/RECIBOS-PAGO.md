# Recibos administrativos de pago — versión 1.6

El sistema genera un **recibo administrativo PDF** para cada pago o movimiento de horas registrado. Incluye un folio estable, la institución, referencia, importe y moneda originales, método, fecha, póliza asociada y minutos de correo y escritorio remoto. La versión de Administración y Cobranza incluye también responsable del registro y descripción del pago. **No es comprobante fiscal, CFDI ni factura.**

El recibo generado es distinto del [comprobante de pago adjunto](COMPROBANTES-PAGO.md): el adjunto es la evidencia que una persona carga, mientras que el recibo describe un movimiento ya registrado en Helpdesk. Un recibo tampoco acredita que el banco haya devuelto dinero cuando se anula un registro.

## Descargar desde la plataforma

1. Con un usuario **Cobranza (`BILLING`)** o **Administrador (`ADMIN`)**, abra **Pagos** y el detalle del pago.
2. En **Recibo administrativo**, pulse **Descargar recibo PDF**.
3. Guarde el archivo cuyo nombre es el folio, por ejemplo `SIABUC-REC-0000000042.pdf`.

Los clientes (`USER`) pueden descargar únicamente la **versión institucional** de los recibos de su propia institución desde el portal financiero, si un administrador habilitó expresamente ese portal. Esta versión conserva folio, importes, tiempos y estado; omite notas de pago, identidades del personal y motivos internos de anulación. La habilitación comienza desactivada y se verifica en cada descarga. Deshabilitarla impide nuevas descargas, incluso de recibos emitidos anteriormente; los archivos ya descargados permanecen en el equipo del usuario.

Cobranza y Administración conservan acceso a recibos de pagos anulados e instituciones dadas de baja para consultar su evidencia histórica. El cliente necesita su cuenta, su institución y el acceso al portal habilitados.

## Primera emisión, folio e información conservada

La primera descarga autorizada emite el recibo y guarda en PostgreSQL una copia de los datos disponibles **en ese momento**. No se reconstruyen nombres o contactos históricos anteriores a esa emisión. Se conservan:

- Nombre, contacto, correo y teléfono de la institución.
- Identificador del pago, referencia, importe, moneda, método, fecha y descripción del registro.
- Identificador y folio de la póliza, cuando el pago está asociado a una.
- Minutos de correo y remoto acreditados en ese movimiento.
- Usuario y nombre del responsable que registró el pago, y fecha del registro.
- Estado del pago en la primera emisión, fecha de emisión y zona horaria.

Las ediciones posteriores del nombre de la institución o del responsable no reemplazan esos datos. El **responsable del registro** mostrado en el PDF de Administración/Cobranza es quien registró el pago; puede ser distinto de quien descargó y emitió el recibo por primera vez. El sistema guarda también el identificador de este primer emisor y una entrada de auditoría `ISSUE_PAYMENT_RECEIPT`. Se conserva una sola copia histórica completa, aunque la primera emisión la solicite el cliente; la versión institucional se prepara con los campos autorizados, sin modificar esa copia ni emitir otro folio.

El folio usa `SIABUC-REC-` seguido del identificador del pago con al menos diez dígitos. Es único y estable; no es una serie fiscal ni garantiza numeración consecutiva sin huecos. Descargas repetidas o concurrentes del mismo pago conservan un solo folio y una sola copia de emisión. La descarga inicial no ocurre automáticamente al abrir el detalle: se inicia al pulsar el botón.

## Pagos anulados y tipos de movimiento

Cada descarga consulta el estado actual del pago. Si está anulado, ambas versiones del PDF muestran **ANULADO en todas las páginas** y la fecha de anulación. Únicamente la versión de Administración/Cobranza muestra el motivo y responsable de esa anulación. Se conservan el importe, los tiempos originales y el estado que tenía cuando se emitió por primera vez. Un PDF guardado antes de la anulación no puede actualizarse automáticamente; descargue una copia nueva para consultar el estado vigente al momento de descargar.

El documento diferencia tres conceptos:

| Movimiento registrado | Texto y significado del recibo |
|---|---|
| Importe positivo con minutos | Pago recibido con acreditación de soporte. |
| Abono de póliza con importe positivo y cero minutos | Abono monetario sin nueva acreditación de minutos. |
| Importe cero con minutos, permitido por una póliza liquidada | Acreditación de horas previamente pagadas; muestra **sin nuevo cobro**. |

Los minutos y horas son los correspondientes al movimiento, **no el saldo actual disponible** de la institución. Los pagos históricos conservan su moneda y método originales, sin conversión a pesos. Consulte [Pólizas y cobranza](POLIZAS-COBRANZA.md) para las reglas de registro y anulación, y [Reportes de cobranza](REPORTES-COBRANZA.md) para los totales vigentes y anulados por moneda.

## Privacidad y efectos de la descarga

El recibo de Administración/Cobranza incluye la descripción del registro y los detalles de anulación. La versión institucional **no incluye notas del pago, motivo de anulación, nombres o usuarios internos del registrador, emisor o anulador**. Ambas versiones omiten auditoría, comentarios privados de aprobación y contenido de casos técnicos. El servidor elimina los campos privados antes de preparar el PDF del cliente, sin depender de que la interfaz los oculte. Conocer un identificador o una dirección de descarga no concede acceso a otra institución.

Emitir o descargar un recibo **no registra otro pago, no modifica saldos, no acredita minutos y no envía correos**. La primera emisión únicamente conserva el recibo y su auditoría. No existe una opción para borrar o regenerar la copia de emisión con nombres nuevos. La anulación del pago sigue su procedimiento habitual y no borra el recibo.

## Configuración, almacenamiento y API

No se requiere un servicio externo ni una dependencia adicional. El backend utiliza PDFBox para producir el PDF en memoria. Las fechas con hora usan `REPORTING_TIME_ZONE`, predeterminado `America/Mexico_City`; esa zona queda conservada en la primera emisión. La fecha contable del pago (`paidOn`) sigue siendo una fecha sin hora.

La migración **V13** crea `payment_receipts`, con una copia JSONB versionada, folios únicos, fecha y emisor, y un solo recibo por pago. Las copias de emisión forman parte del respaldo de PostgreSQL. El PDF se genera al descargar a partir de esos datos y del estado actual de anulación; no se guarda un archivo duplicado en un volumen. Los pagos anteriores a la actualización reciben un recibo cuando se solicita por primera vez.

| Operación | Ruta | Acceso |
|---|---|---|
| Emitir o descargar recibo administrativo | `GET /api/payments/{id}/receipt.pdf` | `ADMIN`, `BILLING` |
| Emitir o descargar recibo propio | `GET /api/client-finance/payments/{id}/receipt.pdf` | `USER`, institución propia y portal habilitado |

Una descarga correcta devuelve `application/pdf`, `Content-Disposition: attachment`, `Cache-Control: no-store, private`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'` y el folio en `X-Receipt-Folio`. La primera petición guarda la emisión; las siguientes reutilizan esa copia sin duplicar el movimiento financiero. Una solicitud sin sesión devuelve `401`; un rol o portal no autorizado, `403`; un pago inexistente o de otra institución en el endpoint cliente, `404`.

El PDF admite acentos españoles, saltos de línea y descripciones largas en varias páginas, con folio, estado y número de página repetidos. Los caracteres no disponibles en la fuente, como algunos emojis, se representan con su código Unicode (`[U+1F600]`) para evitar que la descarga falle. Las direcciones escritas en una descripción son texto sin enlaces activos; no se cargan imágenes ni contenido externo. El límite defensivo es de 100 páginas.

## Pruebas incluidas

`PaymentReceiptIntegrationIT` cubre emisión repetida y simultánea sin duplicados, conservación del nombre y responsable originales, ausencia de cambios financieros o correos, anulaciones con PDF multipágina, cuentas y portales no autorizados, aislamiento institucional, instituciones inactivas, pagos históricos en otra moneda, abonos sin horas y acreditaciones sin nuevo cobro. También verifica que la versión cliente excluya notas, motivos e identidades privadas, mantenga el mismo folio y siga mostrando ANULADO, tanto si emite primero el cliente como el personal. Los PDF se vuelven a abrir con PDFBox para verificar texto, páginas y ausencia de acciones o anotaciones. La evidencia de ejecución de la versión publicada se documenta en [VALIDACION.md](VALIDACION.md).
