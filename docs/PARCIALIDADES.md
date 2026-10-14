# Calendario de parcialidades — versión 1.7

El calendario de Cobranza permite registrar **cuándo se comprometió el cliente a pagar y cuánto**, y relacionar esos compromisos con pagos ya registrados. Está disponible para `ADMIN` y `BILLING`; no expone información a Técnico ni Cliente.

## Programar un acuerdo

1. Abra **Calendario de parcialidades → Programar parcialidad**.
2. Seleccione una póliza activa, fecha comprometida, importe en MXN y descripción del acuerdo.
3. Revise el importe contratado, la suma ya programada y lo disponible para programar. Guarde.
4. Repita para cada fecha del acuerdo. Puede programar una parte o el total; no está obligado a completar todo el contrato de una sola vez.

Cada importe es positivo y tiene como máximo dos decimales. La suma de compromisos no cancelados nunca puede exceder el importe contratado. Por ejemplo, un contrato de **100.03 MXN** puede dividirse en **33.34 + 33.34 + 33.35 MXN** sin redondeos automáticos.

Las fechas son fechas civiles en calendario; pueden registrar acuerdos pasados para documentar vencimientos. Los estados y avisos usan la fecha local de `America/Mexico_City`.

El calendario no crea pagos, comprobantes, adeudos adicionales a la póliza, intereses ni créditos de horas. Si ya existían pagos, deben aplicarse explícitamente al calendario. Por eso el pendiente del calendario puede diferir temporalmente del saldo monetario de la póliza hasta relacionar sus abonos.

## Aplicar pagos existentes

1. Registre y valide primero el pago mediante los procedimientos de Cobranza. Sólo ese proceso acredita los minutos acordados.
2. Abra un compromiso y elija **Aplicar un pago existente**.
3. Seleccione un pago vigente de la misma póliza/institución e indique el importe que cubre ese compromiso.
4. Revise el resultado: importe comprometido, aplicado y pendiente.

Un pago puede repartirse entre varias parcialidades y una parcialidad puede recibir varios pagos. Se comprueba que:

- Todas las aplicaciones vigentes de un pago sumen como máximo su importe.
- Las aplicaciones que cubren una parcialidad no excedan su importe.
- El pago corresponda a esa póliza e institución, sea MXN y no esté anulado.
- No se duplique una aplicación vigente del mismo pago al mismo compromiso. Repetir la misma aplicación devuelve el resultado existente; un importe distinto exige retirar primero la aplicación anterior.

Los movimientos de importe cero usados para acreditar horas después de liquidar una póliza no pueden aplicarse como dinero. El servidor utiliza decimales exactos y bloquea institución y póliza dentro de la transacción; los controles se mantienen con solicitudes concurrentes.

**Aplicar o retirar un pago del calendario no vuelve a sumar ni resta horas, ni modifica el pago original.**

## Corregir, anular y conservar historial

Los compromisos guardados no cambian de importe ni fecha. Para corregirlos:

1. Retire las aplicaciones existentes, capturando un motivo. Incluso una referencia a un pago ya anulado se retira explícitamente.
2. Cancele el compromiso con su motivo.
3. Registre el nuevo compromiso y vuelva a aplicar los pagos pertinentes.

Los compromisos cancelados y aplicaciones retiradas permanecen en el detalle y en el historial, con autor y fecha. Cancelar libera ese importe para programarlo de nuevo, sin borrar evidencia. No se puede modificar el importe contratado de una póliza mientras tenga compromisos no cancelados; las restricciones anteriores para pólizas con pagos también siguen vigentes.

Si Administración anula un pago mediante el procedimiento existente, ese importe deja de cubrir el calendario automáticamente, pero se conserva su referencia como **Pago anulado**. La parcialidad vuelve a estar pendiente o vencida según la fecha. Una póliza cancelada muestra sus compromisos como cancelados y no genera recordatorios; sus datos históricos siguen visibles. Una institución inactiva conserva el registro y deja de recibir nuevos avisos internos de cobro hasta ser reactivada.

## Filtros y recordatorios

El calendario permite buscar por póliza/institución y filtrar por fechas comprometidas y estado:

| Estado | Significado |
|---|---|
| Pendiente | Queda importe por cubrir y la fecha es hoy o posterior. |
| Vencido | Queda importe por cubrir y la fecha es anterior a hoy. |
| Cubierto | Sus aplicaciones de pagos vigentes cubren el compromiso. |
| Cancelado | Se canceló el compromiso o su póliza. |
| Institución inactiva | Se conserva el compromiso sin generar avisos de cobro. |

En **Recordatorios internos**, Administración o Cobranza pueden desactivar/activar los avisos y configurar una anticipación entera de 0 a 365 días. Por defecto están activos con 7 días de anticipación. Esta configuración es independiente de los respaldos automáticos, que continúan **desactivados por defecto**.

El servidor revisa cada 60 segundos; **Revisar ahora** ejecuta la misma evaluación. Se genera como máximo un aviso próximo y uno vencido por compromiso y fecha. Si el registro ya estaba vencido al revisarlo por primera vez, sólo se genera el aviso vencido. Marcar leído afecta únicamente a la cuenta actual.

Los últimos 200 avisos aparecen en **Mis avisos de cobro**, dentro de este módulo. No se envían correos ni mensajes externos. Funcionan aunque el envío de correo esté pausado. Los avisos anteriores no se borran al desactivar la función ni se duplican al reactivarla; los que ya no corresponden al estado actual se identifican como históricos. Al reactivar se revisan compromisos que siguen pendientes, no se reproduce una lista de ejecuciones omitidas.

## API y configuración

Prefijo `/api`; sesión `ADMIN` o `BILLING` y CSRF para escrituras.

| Método y ruta | Datos / propósito |
|---|---|
| `GET /installments` | Configuración, calendario y avisos personales. |
| `POST /installments` | `requestId` UUID, `policyId`, `dueOn`, `amount`, `description`. |
| `GET /installments/{id}` | Compromiso, aplicaciones, historial y pagos de su póliza. |
| `POST /installments/{id}/cancel` | `{ "reason": "…" }`. |
| `POST /installments/{id}/allocations` | `paymentId`, `amount`. |
| `POST /installments/{id}/allocations/{allocation}/remove` | `{ "reason": "…" }`. |
| `PUT /installments/settings` | `enabled`, `daysBeforeDue`. |
| `POST /installments/scan` | Revisión manual de avisos, cuerpo `{}`. |
| `POST /installments/notices/{id}/read` | Lectura personal, cuerpo `{}`. |

La creación usa UUID estable de solicitud: repetir los mismos datos devuelve el mismo compromiso; reutilizarlo con datos diferentes se rechaza. No cambie ese UUID para reintentar una solicitud cuyo resultado no llegó al navegador.

Persistencia: `V22__policy_installments.sql`, tablas `policy_installments`, `installment_allocations`, `installment_history`, `installment_settings`, `installment_notices` e `installment_notice_reads`. Sin servicios Docker nuevos ni variables obligatorias. Las propiedades Spring opcionales `app.installments.scan-ms` y `app.installments.initial-delay-ms` controlan el intervalo y retraso inicial del evaluador; ambos son 60000 por defecto.

## Verificación incluida

`InstallmentIntegrationIT` cubre centavos exactos, límites del contrato, idempotencia, ausencia de créditos/pagos al programar, reparto de pagos, exceso de aplicaciones, aislamiento entre instituciones, anulación, conservación de historial, edición contractual, avisos/lectura personal, configuración/CSRF y carrera entre dos altas que excederían el contrato. Consulte [VALIDACION.md](VALIDACION.md) para resultados ejecutados y limitaciones.
