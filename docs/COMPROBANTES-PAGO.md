# Comprobantes de pago

Desde la versión 1.5.0, **Administración y Cobranza** pueden adjuntar evidencia a un pago ya registrado: fichas de depósito, transferencias, recibos o capturas. El archivo es opcional; adjuntarlo **no registra otro cobro, no acredita minutos y no cambia el importe del pago**.

## Uso desde la plataforma

1. Registre el pago o abono en **Pagos** y abra su detalle. También puede abrir un pago desde el detalle de una póliza.
2. En **Comprobantes de pago**, pulse **Seleccionar comprobante** y elija un PDF, PNG o JPG/JPEG.
3. Pulse **Adjuntar comprobante**. El sistema confirma que el importe y los minutos permanecen iguales.
4. El listado conserva nombre, tamaño, administrativo que lo adjuntó, usuario, fecha y huella SHA-256. Pulse **Descargar** para recuperar los bytes originales.

Puede agregar evidencia a pagos anulados y de instituciones dadas de baja. Su estado financiero permanece intacto. Los archivos se conservan: no hay funciones para sustituirlos ni eliminarlos. Si necesita corregir una evidencia, adjunte el documento correcto; ambos quedarán registrados.

Los usuarios clientes no tienen acceso a estos archivos, ni siquiera a los comprobantes de su propia institución. Administración y Cobranza pueden consultar todos los comprobantes del historial financiero. Esto es independiente de los adjuntos de casos y de los permisos del portal de soporte.

## Formatos y límites

| Concepto | Límite |
|---|---|
| Formatos | PDF, PNG, JPG y JPEG |
| Tamaño individual | 10 MiB: 10,485,760 bytes |
| Archivos por pago | 10 |
| Tamaño total por pago | 50 MiB: 52,428,800 bytes |

El servidor verifica la extensión y la firma del contenido; no confía en el tipo MIME indicado por el navegador. No se admiten archivos vacíos, diagnósticos TXT/LOG/CSV/JSON, HTML, ejecutables ni ZIP. Se eliminan componentes de ruta y caracteres de control del nombre; el nombre resultante admite hasta 180 caracteres.

Las descargas requieren sesión y rol autorizado, utilizan `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` y `Cache-Control: no-store`. La verificación de formato no constituye un análisis antivirus.

## Historial y reintentos

Cada comprobante registra autor, fecha, nombre, tamaño y SHA-256. Se añade un evento `ATTACH_PAYMENT_PROOF` a la auditoría administrativa. Cuando el pago está asociado a una póliza, su historial incluye el mismo evento con referencia al pago y una descripción que indica que no cambió dinero ni minutos.

La carga utiliza un identificador UUID de solicitud. Repetir el mismo envío con ese identificador, autor, pago, nombre y contenido devuelve el comprobante existente: no duplica el archivo ni el historial. Reutilizar el identificador con datos distintos devuelve un conflicto. El formulario conserva el UUID si falla el envío y evita envíos simultáneos desde el mismo botón.

Agregar un comprobante **no genera correos, avisos de casos ni movimientos de soporte**. La pausa del correo no afecta su carga o consulta.

## Respaldo y actualización

La migración `V10__payment_attachments.sql` crea `payment_attachments` y amplía los eventos admitidos en `policy_history`. No modifica pagos anteriores, sus importes, monedas, minutos ni estados. La tabla empieza vacía; no se inventan comprobantes.

Los bytes se guardan en PostgreSQL como `BYTEA`. Los respaldos y restauraciones normales de la base incluyen los comprobantes; no hace falta un volumen adicional ni copiar carpetas de archivos. El tamaño del respaldo aumenta con la evidencia adjuntada. El ZIP del código no contiene los archivos financieros de una instalación.

Linux y Windows usan los mismos scripts documentados de inicio, respaldo y restauración. Se conservan los límites multipart existentes de Spring —10 MiB por archivo y 12 MiB por petición— y nginx —12 MiB por petición—; no es necesario configurar SMTP para esta función.

## API

| Método y ruta | Resultado |
|---|---|
| `GET /api/payments/{id}/attachments` | Metadatos de los comprobantes, del más reciente al más antiguo |
| `POST /api/payments/{id}/attachments` | Carga multipart con campos `file` y `requestId` UUID; devuelve HTTP 201 |
| `GET /api/payment-attachments/{id}/download` | Descarga autenticada del archivo |

Las tres rutas requieren `ADMIN` o `BILLING`; `USER` recibe 403. Las peticiones POST requieren CSRF, igual que los demás formularios. Un pago o comprobante inexistente devuelve 404; formato inválido, 400; archivo demasiado grande, 413; cuota agotada o UUID reutilizado con otro contenido, 409.

Los metadatos contienen `id`, `paymentId`, `filename`, `contentType`, `sizeBytes`, `sha256`, `uploadedBy`, `uploadedName` y `createdAt`. Los listados no incluyen los bytes. El servidor serializa la carga con el mismo orden de bloqueo de cobranza: institución, póliza si existe y pago. Archivo, auditoría e historial se guardan en una sola transacción.

## Verificación reproducible

`PaymentAttachmentIntegrationIT` verifica permisos, descargas y hash, reintentos, historial de póliza, conservación de importes/minutos, pagos anulados e instituciones inactivas, formatos y cuotas. La prueba `tests/migrations/upgrade-v15.mjs` aplica los SQL exactos V1–V10 en una base aislada en memoria y comprueba la conservación del historial previo y la nueva tabla de comprobantes. Consulte `VALIDACION.md` para conocer las pruebas realmente ejecutadas y las limitaciones del entorno.
