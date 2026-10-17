# Pólizas, cobranza y acreditación de horas — versión 1.8.3

**Ampliación posterior a 1.8.3:** consulte [Licencias y horas predeterminadas](LICENCIAS-HORAS.md). Las pólizas pueden vincularse a una licencia y sus pagos acreditan esa bolsa; los saldos anteriores permanecen sin licencia.

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

El módulo, incorporado en SIABUC Helpdesk 1.5.0, relaciona las pólizas de una institución, sus pagos y los minutos de soporte efectivamente acreditados.

**Cada pago guarda en una sola operación su importe, método, institución, póliza opcional y minutos de correo/remoto.** El servidor actualiza las bolsas de soporte y el historial dentro de la misma transacción. Si el movimiento es inválido o falla su registro, no se guarda parcialmente el dinero ni se acreditan minutos sueltos.

Desde 1.6 se añaden [cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md), [solicitudes pendientes de validación](SOLICITUDES-PAGO.md), [recibos administrativos](RECIBOS-PAGO.md) y un [portal financiero institucional](PORTAL-FINANCIERO.md) deshabilitado por defecto. La versión 1.8.3 conserva estos flujos e incluye [paquetes y tarifas](PAQUETES-TARIFAS.md), [calendario de parcialidades](PARCIALIDADES.md), [conciliación bancaria](CONCILIACION-BANCARIA.md) y [estados de cuenta](ESTADOS-CUENTA.md). Las reglas de acreditación y anulación se describen a continuación.

## Permisos

| Operación | Cobranza —BILLING— | Administrativo —ADMIN— | Cliente —USER— |
|---|---|---|---|
| Consultar pólizas e historial administrativo | Sí | Sí | No; sólo resumen propio si se habilita el portal |
| Crear o modificar una póliza sin historial de pagos | Sí | Sí | No |
| Cancelar una póliza sin pagos vigentes | Sí | Sí | No |
| Registrar pagos y acreditar soporte | Sí | Sí | No |
| Consultar pagos propios y resumen contractual | Sí | Sí | Sólo con portal institucional habilitado |
| Consultar/exportar reportes financieros generales | Sí | Sí | No |
| Adjuntar comprobantes a pagos registrados | Sí | Sí | No |
| Descargar comprobantes y recibos de pagos | Sí | Sí | Propios, sólo con portal habilitado |
| Crear solicitudes y adjuntar evidencia pendiente | Sí | Sí | Propias, sólo con portal habilitado |
| Aprobar o rechazar solicitudes | Sí | Sí | No |
| Gestionar cotizaciones y renovaciones | Sí | Sí | No |
| Habilitar acceso financiero por institución | No | Sí | No |
| Anular un pago | No | Sí, si quedan los minutos necesarios | No |

El rol Técnico (`TECH`) no tiene acceso a estos módulos financieros. El nombre de rol del cliente en la API es `USER`. Cobranza conserva su acceso a los saldos institucionales; estas funciones no le otorgan acceso a notas internas, expedientes de soporte ni administración de usuarios. Los correos de pagos continúan usando los destinatarios y la política de pausa de notificaciones existentes.

Los cinco estados de casos de soporte de 1.8.3 no cambian los estados financieros: una póliza sigue **Activa/Cancelada**, un pago **Vigente/Anulado** y una solicitud **Pendiente/Aprobada/Rechazada**. Cambiar un caso a Resuelto o Cerrado no registra dinero ni devuelve automáticamente minutos consumidos.

## Crear una póliza

Desde el apartado **Pólizas**, seleccione la institución y capture:

- **Folio:** identificador único de hasta 100 caracteres. No se puede repetir aunque cambie el uso de mayúsculas, existan espacios exteriores o la póliza anterior haya sido cancelada.
- **Descripción:** qué soporte se contrató, hasta 10,000 caracteres.
- **Vigencia desde / hasta:** fechas inclusivas; el fin no puede ser anterior al inicio.
- **Importe contratado:** pesos mexicanos —MXN—, positivo y con hasta dos decimales; máximo $999,999,999.99.
- **Soporte contratado por correo y remoto:** horas/minutos que se pactaron, almacenados como minutos enteros. Al menos un canal debe tener tiempo positivo. Cada canal admite hasta 1,000,000,000 minutos.

**Crear la póliza no acredita horas ni registra un pago.** El contrato describe lo convenido; la acreditación ocurre al registrar cada movimiento de cobranza.

La póliza tiene estado administrativo **Activa** o **Cancelada**. Además, muestra una vigencia informativa: **Por iniciar**, **Vigente** o **Vencida**, calculada con la fecha de `America/Mexico_City`. El vencimiento no elimina minutos ya acreditados ni cierra casos: los saldos de soporte pertenecen a las bolsas de la institución. Se permiten registros de pago de pólizas pasadas o futuras para documentar operaciones retrospectivas o anticipos; una póliza cancelada sí rechaza nuevos movimientos.

## Cotizaciones y renovaciones

Una cotización aceptada puede convertirse en una póliza con los términos registrados. Administración o Cobranza documentan la emisión y la respuesta del cliente; aceptar no es una firma digital. La conversión es idempotente y no registra dinero ni acredita soporte. Los pagos directos o aprobados desde solicitudes usan después las mismas reglas de acreditación descritas en esta guía.

La renovación enlaza la póliza anterior con su sucesora y conserva la cotización, el responsable y la fecha. Cada antecedente admite una sucesora. Si ésta se cancela, la relación permanece; para reemplazarla se crea una cotización desde esa última póliza cancelada, sin reactivarla. Los avisos de vencimiento se consultan en Renovaciones, con umbral configurable, sin depender de SMTP. No trasladan ni vencen automáticamente los minutos. Consulte [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md).

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

## Validar una solicitud antes de acreditar

Desde **Validar pagos**, Administración o Cobranza puede revisar una solicitud y sus comprobantes antes de crear el movimiento financiero. Un cliente también puede presentarla para su institución si `ADMIN` habilitó el portal financiero; ese acceso está desactivado por defecto. Una solicitud **Pendiente** no registra ingreso ni acredita minutos.

La aprobación requiere evidencia y vuelve a comprobar la institución, la póliza y sus límites. Crea un solo pago, acredita los minutos aprobados y copia los comprobantes en una misma transacción. No se alteran las cantidades durante la decisión. Si son incorrectas, se rechaza con motivo y se presenta otra solicitud. Las observaciones privadas de aprobación no se muestran al cliente; el motivo de rechazo sí. Una solicitud resuelta y su evidencia permanecen congeladas.

El registro directo de **Pagos** continúa disponible para cobros ya validados fuera de la plataforma. No registre directamente un movimiento que también va a aprobar como solicitud: ambas vías representan el mismo hecho de cobranza. Consulte [Solicitudes de pago](SOLICITUDES-PAGO.md).

## Historial y saldos

El detalle de cada póliza conserva, en orden cronológico descendente:

- Creación y modificaciones, con descripción de los valores anteriores y posteriores.
- Cada pago o acreditación, su referencia, importe, moneda, método, minutos de ambos canales, autor y fecha.
- Anulaciones de pagos y motivo.
- Comprobantes adjuntos a pagos de la póliza, con autor y fecha; no cambian dinero ni minutos.
- Cancelación de la póliza y motivo.

Puede consultar el importe recibido, el importe contractual pendiente, los minutos acreditados y los minutos contratados aún pendientes de acreditar. **Los minutos pendientes de acreditar no son el saldo disponible para soporte:** el saldo institucional también considera minutos ya utilizados y acreditaciones de otras pólizas o pagos libres.

El historial general de pagos conserva los movimientos libres y los ligados a pólizas. Desde el detalle de cada pago puede adjuntar o descargar [comprobantes PDF/PNG/JPEG](COMPROBANTES-PAGO.md), sin repetir el pago ni acreditar minutos. Los [reportes financieros](REPORTES-COBRANZA.md) permiten consultar institución, póliza, método y periodo, y exportar Excel o PDF. Los importes históricos en otras monedas se conservan sin conversión y deben mantenerse separados en los totales.

Desde 1.6, el detalle del pago permite descargar un [recibo administrativo PDF](RECIBOS-PAGO.md) con folio estable. Su primera emisión conserva los datos de ese momento; no crea otro ingreso ni acredita minutos. Un recibo no es CFDI ni factura.

Una póliza cancelada puede conservar información del importe originalmente contratado y sus minutos, pero **no se considera una deuda activa** en los reportes.

## Consulta institucional con acceso explícito

Sólo Administración habilita **Configuración de soporte → Acceso financiero de clientes** para una institución activa. El permiso comienza desactivado y se aplica a todas sus cuentas cliente. El cliente habilitado consulta un resumen propio de contratos/pagos y descarga recibos y comprobantes, incluidos archivos históricos, pero no recibe auditoría ni historial financiero interno. Tampoco modifica pólizas, anula pagos ni agrega evidencia a pagos registrados.

Deshabilitar bloquea nuevas peticiones sin borrar los registros. Los archivos ya descargados permanecen fuera de la plataforma. Los reportes generales de cobranza, las cotizaciones y las renovaciones siguen siendo exclusivos de ADMIN/BILLING aun con el portal habilitado. Consulte [Portal financiero](PORTAL-FINANCIERO.md).

## Correcciones y cancelaciones

La póliza puede modificarse mientras esté activa y **nunca haya tenido un pago**. La institución no puede cambiarse. Si existen parcialidades no canceladas, tampoco puede cambiarse el importe contratado: primero hay que retirar sus aplicaciones y cancelar el calendario conforme a [Parcialidades](PARCIALIDADES.md). Después del primer pago, incluso si este se anula, el contrato queda congelado para que los movimientos históricos no cambien de significado.

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

Las rutas de la tabla siguiente requieren ADMIN/BILLING, salvo anular pagos, que exige ADMIN. Todas requieren sesión y las mutaciones requieren el token CSRF. El portal cliente utiliza rutas separadas `/api/client-finance/**` y no abre estas rutas administrativas.

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

Desde 1.6 también puede configurarse el servicio de [respaldos automáticos](RESPALDOS-AUTOMATICOS.md), con calendario desactivado por defecto; no elimina ni reemplaza los scripts manuales existentes.

Las pruebas de las reglas incorporadas en 1.5 están en `PolicyIntegrationIT` y en `tests/migrations/upgrade-v15.mjs`. Consulte [Validación](VALIDACION.md) y las [notas de 1.8.3](VERSION-1.8.3.md) para resultados realmente ejecutados. Los informes de 1.5.0 y otras entregas anteriores se conservan como evidencia histórica, sin atribuirles cobertura de los cambios nuevos.
