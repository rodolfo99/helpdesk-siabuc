# Pólizas, cobranza y acreditación de horas

Disponible desde SIABUC Helpdesk 1.5.0. El módulo relaciona las pólizas de una institución, sus pagos y los minutos de soporte efectivamente acreditados.

**Cada pago guarda en una sola operación su importe, método, institución, póliza opcional y minutos de correo/remoto.** El servidor actualiza las bolsas de soporte y el historial dentro de la misma transacción. Si el movimiento es inválido o falla su registro, no se guarda parcialmente el dinero ni se acreditan minutos sueltos.

## Permisos

| Operación | Cobranza —BILLING— | Administrativo —ADMIN— | Cliente —USER— |
|---|---|---|---|
| Consultar pólizas y su historial | Sí | Sí | No |
| Crear o modificar una póliza sin historial de pagos | Sí | Sí | No |
| Cancelar una póliza sin pagos vigentes | Sí | Sí | No |
| Registrar pagos y acreditar soporte | Sí | Sí | No |
| Consultar pagos y reportes financieros | Sí | Sí | No |
| Adjuntar y descargar comprobantes de pago | Sí | Sí | No |
| Anular un pago | No | Sí, si quedan los minutos necesarios | No |

Cobranza conserva su acceso a los saldos institucionales; estas funciones no le otorgan acceso a notas internas, expedientes de soporte ni administración de usuarios. Los correos de pagos continúan usando los destinatarios y la política de pausa de notificaciones existentes.

## Crear una póliza

Desde el apartado **Pólizas**, seleccione la institución y capture:

- **Folio:** identificador único de hasta 100 caracteres. No se puede repetir aunque cambie el uso de mayúsculas, existan espacios exteriores o la póliza anterior haya sido cancelada.
- **Descripción:** qué soporte se contrató, hasta 10,000 caracteres.
- **Vigencia desde / hasta:** fechas inclusivas; el fin no puede ser anterior al inicio.
- **Importe contratado:** pesos mexicanos —MXN—, positivo y con hasta dos decimales; máximo $999,999,999.99.
- **Soporte contratado por correo y remoto:** horas/minutos que se pactaron, almacenados como minutos enteros. Al menos un canal debe tener tiempo positivo. Cada canal admite hasta 1,000,000,000 minutos.

**Crear la póliza no acredita horas ni registra un pago.** El contrato describe lo convenido; la acreditación ocurre al registrar cada movimiento de cobranza.

La póliza tiene estado administrativo **Activa** o **Cancelada**. Además, muestra una vigencia informativa: **Por iniciar**, **Vigente** o **Vencida**, calculada con la fecha de `America/Mexico_City`. El vencimiento no elimina minutos ya acreditados ni cierra casos: los saldos de soporte pertenecen a las bolsas de la institución. Se permiten registros de pago de pólizas pasadas o futuras para documentar operaciones retrospectivas o anticipos; una póliza cancelada sí rechaza nuevos movimientos.

## Registrar un pago y acreditar soporte

En **Pagos**, seleccione la institución y, cuando corresponda, su póliza activa. Capture la referencia del pago, fecha, importe recibido, método, descripción y tiempo por acreditar en cada canal.

Los nuevos pagos se registran en **MXN**. Los métodos son transferencia, depósito, efectivo, tarjeta de crédito, tarjeta de débito, cheque y otro. Los registros antiguos con método genérico **Tarjeta —CARD—** se conservan; la API aún reconoce ese valor para compatibilidad, pero la captura nueva distingue crédito y débito.

El importe no convierte horas automáticamente. **Indique los minutos de correo y remoto que corresponden a ese movimiento.** Así puede registrar parcialidades sin asumir una tarifa o proporción no pactada.

| Movimiento | Importe | Minutos | Condición |
|---|---|---|---|
| Pago libre, sin póliza | Positivo | Algún canal positivo | Comportamiento existente |
| Pago ligado a póliza con acreditación | Positivo | Algún canal positivo | No exceder lo contratado pendiente |
| Abono monetario de póliza | Positivo | Ambos canales en cero | Completa dinero sin acreditar nuevas horas |
| Acreditación de horas ya pagadas | Cero | Algún canal positivo | La póliza ya debe estar liquidada monetariamente y tener minutos pendientes |

Un movimiento con importe cero y ambos tiempos en cero se rechaza. El sistema tampoco permite acreditar minutos sin cobro a una póliza que aún no está liquidada monetariamente.

### Ejemplo de parcialidades

Una institución contrata una póliza de **$1,000 MXN**, con **100 minutos de correo y 60 minutos remotos**:

1. Registra $200 y acredita explícitamente 20 minutos de correo y 10 remotos.
2. Registra $800 y cero minutos en ambos canales, completando el importe contratado.
3. Registra una acreditación de $0 con los 80 minutos de correo y 50 remotos restantes, que ya estaban pagados.

El total final es $1,000, 100 minutos de correo y 60 remotos. También puede registrar todo en un solo pago. La decisión de cuántos minutos acompañan cada parcialidad es administrativa; el sistema verifica los límites monetarios y de tiempo.

La suma de pagos **vigentes, sin anular**, no puede superar el importe contratado ni los minutos pactados de cada canal. La póliza y el pago deben pertenecer a la misma institución. Además, la acreditación no puede llevar la bolsa institucional por encima de su máximo permitido.

## Historial y saldos

El detalle de cada póliza conserva, en orden cronológico descendente:

- Creación y modificaciones, con descripción de los valores anteriores y posteriores.
- Cada pago o acreditación, su referencia, importe, moneda, método, minutos de ambos canales, autor y fecha.
- Anulaciones de pagos y motivo.
- Comprobantes adjuntos a pagos de la póliza, con autor y fecha; no cambian dinero ni minutos.
- Cancelación de la póliza y motivo.

Puede consultar el importe recibido, el importe contractual pendiente, los minutos acreditados y los minutos contratados aún pendientes de acreditar. **Los minutos pendientes de acreditar no son el saldo disponible para soporte:** el saldo institucional también considera minutos ya utilizados y acreditaciones de otras pólizas o pagos libres.

El historial general de pagos conserva los movimientos libres y los ligados a pólizas. Desde el detalle de cada pago puede adjuntar o descargar [comprobantes PDF/PNG/JPEG](COMPROBANTES-PAGO.md), sin repetir el pago ni acreditar minutos. Los [reportes financieros](REPORTES-COBRANZA.md) permiten consultar institución, póliza, método y periodo, y exportar Excel o PDF. Los importes históricos en otras monedas se conservan sin conversión y deben mantenerse separados en los totales.

Una póliza cancelada puede conservar información del importe originalmente contratado y sus minutos, pero **no se considera una deuda activa** en los reportes.

## Correcciones y cancelaciones

La póliza puede modificarse mientras esté activa y **nunca haya tenido un pago**. La institución no puede cambiarse. Después del primer pago, incluso si este se anula, el contrato queda congelado para que los movimientos históricos no cambien de significado.

Para anular un pago, un ADMIN debe indicar el motivo. El sistema verifica que la institución tenga disponibles todos los minutos acreditados por ese movimiento en ambos canales. Si parte del crédito ya es necesario para cubrir atenciones consumidas, la anulación se rechaza completa; no retira solo una parte ni deja saldos negativos.

Al anular correctamente:

1. El pago permanece visible como anulado, con autor, motivo y fecha.
2. Se retiran exactamente sus minutos de las bolsas institucionales.
3. Se liberan los límites monetarios y de minutos de la póliza para movimientos posteriores.
4. Se agrega el evento al historial y se generan los avisos de pago según la configuración de correo.

Un abono de dinero que originalmente tenía cero minutos no retira minutos de otras acreditaciones al anularse. Sin embargo, **no puede anularse un pago monetario si con ello se pierde la liquidación que respalda acreditaciones vigentes de importe cero**. Primero debe anular esas acreditaciones; cada una exige saldo institucional suficiente. Si sus minutos ya se consumieron y no hay saldo disponible, tampoco puede eludirse esa protección anulando el abono monetario. El sistema sigue cada movimiento explícito; no recalcula proporcionalmente ni retira otros pagos automáticamente.

Cobranza o ADMIN pueden cancelar una póliza solamente cuando no tenga ningún pago vigente. Si tuvo pagos que fueron anulados, conserva todo su historial y no recupera la posibilidad de editar el contrato. No hay borrado físico ni reactivación de una póliza cancelada: para un nuevo acuerdo, cree una nueva póliza con otro folio.

## Evitar duplicidades y operaciones simultáneas

La captura de pagos usa un identificador de solicitud UUID. Repetir la misma solicitud devuelve el movimiento ya registrado y no duplica importe, acreditación, historial ni avisos. Cambiar el contenido, incluida la póliza vinculada, usando ese mismo identificador produce un conflicto.

La referencia también debe ser única dentro de la institución, sin distinguir mayúsculas ni espacios exteriores; queda reservada incluso después de anular el pago.

El servidor bloquea primero la institución, luego la póliza y después el pago cuando corresponde. Dos cobranzas simultáneas no pueden utilizar el mismo remanente contractual para exceder los límites. Las anulaciones y el consumo de soporte utilizan bloqueos compatibles con este orden.

## API y actualización técnica

Todos los endpoints requieren sesión y las mutaciones requieren el token CSRF.

| Método / ruta | Resultado |
|---|---|
| `GET /api/policies` | Pólizas con totales contractuales; filtro opcional `institutionId` |
| `POST /api/policies` | Crea una póliza sin acreditar tiempo |
| `GET /api/policies/{id}` | `{policy, history, payments}` con lectura consistente |
| `PUT /api/policies/{id}` | Modifica una póliza activa sin ningún pago histórico |
| `POST /api/policies/{id}/cancel` | Cancela con `{reason}` si no hay pagos vigentes |
| `GET /api/payments` | Mantiene el historial general; añade `policyId` y `policyFolio` |
| `POST /api/payments` | Mantiene el contrato anterior y añade `policyId` opcional |
| `POST /api/payments/{id}/void` | Anulación por ADMIN con `{reason}` |

El objeto de creación/edición de póliza contiene `institutionId`, `folio`, `description`, `validFrom`, `validUntil`, `agreedAmount`, `emailMinutes` y `remoteMinutes`. La moneda contractual es siempre MXN. El registro de pago sigue usando `requestId`, `institutionId`, `reference`, `amount`, `currency`, `paidOn`, `method`, `emailMinutes`, `remoteMinutes`, `notes` y el nuevo `policyId` opcional.

La migración `V9__support_policies.sql` crea `support_policies` y `policy_history`, agrega `payments.policy_id`, amplía los métodos y adapta las restricciones para parcialidades. No convierte monedas, no inventa pólizas para pagos anteriores, no cambia los importes ni los saldos existentes. Los pagos anteriores continúan con póliza vacía. El reintento exacto de un pago histórico ya guardado en otra moneda devuelve ese registro sin crear uno nuevo; cualquier pago nuevo debe ser MXN.

Los datos de pólizas e historial se almacenan en PostgreSQL y forman parte del respaldo normal con `respaldar.sh` / `respaldar.bat` y de su restauración. Antes de actualizar, genere y conserve un respaldo con la versión instalada. Las migraciones se aplican al iniciar el servidor actualizado; no elimine los volúmenes de Docker.

Las pruebas están en `PolicyIntegrationIT` y en `tests/migrations/upgrade-v15.mjs`. Consulte el informe de validación de 1.5.0 para resultados realmente ejecutados, incluyendo concurrencia, reversión de errores, conservación de registros anteriores y exportaciones financieras.
