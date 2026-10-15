# Cotizaciones, renovaciones y vencimientos — versión 1.8.3

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

Los módulos **Cotizaciones** y **Renovaciones** están disponibles para Administración (`ADMIN`) y Cobranza (`BILLING`). Permiten documentar una oferta, registrar la respuesta del cliente y convertir lo aceptado en una póliza. El rol Técnico (`TECH`) y los usuarios de instituciones (`USER`) no pueden acceder a estos módulos, aunque el cliente tenga habilitado el portal financiero.

**Crear una cotización, aceptarla, convertirla o renovar una póliza no acredita soporte ni registra dinero recibido.** La acreditación de minutos ocurre al registrar y validar el pago mediante los flujos de Cobranza. Los saldos institucionales existentes permanecen intactos durante una renovación.

## Crear y emitir una cotización

1. Entra en **Cotizaciones → Nueva cotización**.
2. Selecciona la institución activa, un folio único y la descripción de las condiciones.
3. Captura el importe en pesos mexicanos, el tiempo de correo y el tiempo remoto. También puedes seleccionar un [paquete y tarifa](PAQUETES-TARIFAS.md) activo: se copian las condiciones de su versión vigente y debes indicar **Motivo de ajuste al paquete** si cambias precio, descripción o minutos. El formulario admite horas y minutos; el servidor guarda minutos enteros. Debe haber tiempo en al menos un canal.
4. Indica **Oferta válida hasta**, que es el límite para aceptar la oferta. Las fechas **Inicio de cobertura** y **Fin de cobertura** indican la vigencia propuesta de la póliza y son independientes del vencimiento de la oferta.
5. Si corresponde a una renovación, selecciona la póliza anterior de esa misma institución. El formulario sugiere sus términos y el siguiente periodo anual; revísalos y corrígelos antes de guardar. El sistema permite fechas explícitas, sin imponer que los periodos no se superpongan.
6. Guarda el borrador. Puedes editar sus condiciones, fechas, tiempo e importe mientras continúe en **Borrador**. La institución y la póliza anterior quedan fijadas al crearlo; si están equivocadas, crea otra cotización.
7. Entrega la oferta al cliente por el medio que corresponda. Pulsa **Registrar emisión** y describe el medio, destinatario y referencia que acreditan esa entrega.

Registrar la emisión **no envía correo automáticamente**. La constancia se guarda en el historial y las condiciones quedan congeladas. Un borrador cuya oferta ya venció no puede emitirse; actualiza primero su fecha.

Los folios de cotización son únicos sin distinguir mayúsculas y minúsculas ni espacios en los extremos. Pueden tener hasta 100 caracteres; la descripción y las constancias admiten hasta 10,000 caracteres cada una. El importe debe ser positivo, con un máximo de dos decimales y de $999,999,999.99 MXN. Cada canal permite de 0 a 1,000,000,000 minutos.

## Registrar aceptación o rechazo

Abre una cotización emitida y elige **Registrar aceptación** o **Registrar rechazo**. Captura:

- Nombre del contacto que respondió, hasta 180 caracteres.
- Fecha real de la respuesta.
- Constancia descriptiva: medio, referencia del mensaje o documento y respuesta recibida.

La constancia la captura un administrativo en nombre del cliente. Conserva quién hizo el registro y cuándo; **no constituye una firma digital** ni demuestra por sí sola la autenticidad de un documento externo. El sistema no afirma que haya enviado o entregado una oferta por correo.

La fecha de respuesta debe estar entre el día de emisión y el día actual. Para una aceptación también debe estar dentro de la vigencia de la oferta. Puedes registrar hoy una aceptación que ocurrió antes, siempre que su fecha real cumpla esos límites. Un rechazo puede registrarse después del vencimiento de la oferta.

Las fechas se comparan con el calendario de `America/Mexico_City`. Los estados disponibles son:

| Estado en pantalla | Valor API | Operación siguiente |
|---|---|---|
| Borrador | `DRAFT` | Editar o registrar emisión |
| Emitida | `SENT` | Registrar aceptación o rechazo |
| Aceptada | `ACCEPTED` | Convertir a póliza |
| Rechazada | `REJECTED` | Consultar su historial; preparar una nueva oferta si procede |
| Convertida a póliza | `CONVERTED` | Consultar póliza e historial |

El vencimiento de una oferta se muestra como una advertencia, sin inventar un estado adicional ni borrar su historial. Una cotización rechazada no puede convertirse. No hay borrado físico de cotizaciones, decisiones ni historial.

## Convertir a póliza y registrar el pago

En una cotización aceptada, pulsa **Convertir a póliza** e indica un folio único para la nueva póliza. El servidor copia la institución, descripción, cobertura, importe y minutos aceptados. La póliza se crea activa y sin pagos; sus bolsas de soporte aún no se acreditan.

Continúa en **Pagos** para registrar el cobro y los minutos que libera, o procesa la solicitud de pago correspondiente. Se mantienen las reglas documentadas en [Pólizas y cobranza](POLIZAS-COBRANZA.md): parcialidades explícitas, límites monetarios y de tiempo, movimientos atómicos y anulaciones autorizadas. Una cotización no sustituye un comprobante de pago ni un recibo.

La conversión es idempotente: repetir la petición con el mismo folio de conversión devuelve la misma póliza, sin crear otra póliza, otro vínculo o más historial. Si después se modifica el folio de la póliza antes de sus pagos, el reintento debe seguir usando el **folio original de conversión**; este queda conservado internamente. La vista muestra el folio actual y el historial conserva el original. Una petición repetida con un folio diferente se rechaza.

## Renovar y conservar la cadena de pólizas

En **Renovaciones**, consulta las pólizas activas de instituciones activas cuyo vencimiento sea anterior o igual a la fecha actual más el umbral configurado. La lista incluye pólizas ya vencidas sin renovación registrada. No incluye pólizas canceladas ni las que ya tienen una póliza sucesora.

Para renovar, crea una cotización y elige la póliza anterior. Cuando se acepta y convierte, se registra en una misma transacción el enlace **póliza anterior → nueva póliza**, junto con la cotización, el administrativo y la fecha. Cada póliza anterior admite una única sucesora; dos cotizaciones distintas no pueden generar dos renovaciones de ese mismo antecedente.

El vínculo histórico no se elimina si se cancela la nueva póliza. La póliza anterior tampoco vuelve a aparecer como pendiente ni vuelve a generar avisos. Para reemplazar una renovación cancelada:

1. Conserva la cancelación y su motivo en la póliza nueva.
2. Crea una nueva cotización y selecciona **esa última póliza cancelada** como antecedente. El selector la identifica como cancelada.
3. Revisa las fechas y condiciones; acepta y convierte conforme a la nueva constancia.

Así queda una cadena completa, por ejemplo A → B cancelada → C. **B no se reactiva**, A no adquiere una segunda sucesora y no se altera ningún saldo por crear estos vínculos. El historial de renovaciones muestra el estado de la nueva póliza para reconocer cancelaciones.

La vigencia continúa siendo informativa: no vence, elimina ni traslada automáticamente los minutos de las bolsas institucionales. Las reglas de cancelación de pólizas y anulación de pagos siguen vigentes.

## Avisos de vencimiento

En **Renovaciones → Avisos de vencimiento**, Administración o Cobranza pueden ajustar:

| Parámetro | Valor inicial | Efecto |
|---|---|---|
| Generar avisos | Activado | Permite crear nuevos avisos internos |
| Anticipación en días | 30 | Entero entre 0 y 365; incluye vencidas y las que vencen hasta el límite |

Con el servidor en ejecución se revisan los vencimientos aproximadamente cada 60 segundos. **Revisar vencimientos ahora** realiza la misma revisión de inmediato. El calendario usado es `America/Mexico_City`; una póliza que vence hoy está dentro de un umbral de 0 días.

Los avisos aparecen en **Mis avisos de vencimiento**, dentro del módulo de Renovaciones. Son independientes de la campana de soporte y del envío de correo; no generan mensajes SMTP. Funcionan aunque el correo esté pausado y no incorporan una cola de correo pendiente.

Se registra una sola notificación por combinación de póliza y fecha de vencimiento, aunque se repita la revisión, se cambie el umbral o se desactive y reactive la generación. Al desactivar los avisos no se crean nuevos; los anteriores permanecen. Al reactivar, la siguiente revisión evalúa las pólizas que corresponden al umbral actual.

Cada aviso conserva una copia del nombre de institución, folio y fecha que tenía al crearse. Si la póliza se renueva, se cancela, cambia de vencimiento o su institución se desactiva, el aviso conserva su evidencia y se marca como histórico. Un nuevo vencimiento puede generar un nuevo aviso; volver exactamente a una fecha ya avisada no duplica aquella notificación.

**Marcar leído** solo afecta al usuario actual; los demás administrativos mantienen sus propias lecturas. La pantalla presenta los últimos 200 avisos. El historial completo sigue en PostgreSQL; el listado de pólizas por vencer y el historial de renovaciones son independientes de ese límite de presentación.

## API e integración

Todos los endpoints siguientes exigen sesión de `ADMIN` o `BILLING`; las escrituras requieren el token CSRF habitual. El servicio también comprueba el rol, además de la seguridad por URL.

| Método y ruta | Entrada o resultado |
|---|---|
| `GET /api/quotes?institutionId=...` | Lista de cotizaciones, filtro institucional opcional |
| `POST /api/quotes` | Crea borrador; devuelve detalle y HTTP 201 |
| `GET /api/quotes/{id}` | `{quote, history}` |
| `PUT /api/quotes/{id}` | Edita únicamente un borrador |
| `POST /api/quotes/{id}/send` | `{evidence}` |
| `POST /api/quotes/{id}/decision` | `{decision, contactName, evidence, decidedOn}`; decisión `ACCEPTED` o `REJECTED` |
| `POST /api/quotes/{id}/convert` | `{policyFolio}`; creación idempotente de póliza |
| `GET /api/renewals` | `{settings, upcoming, links, notices}` |
| `GET /api/renewals/settings` | `{enabled, daysBeforeExpiry}` |
| `PUT /api/renewals/settings` | Configuración con esos dos campos |
| `POST /api/renewals/scan` | Cuerpo `{}`; respuesta `{created}` |
| `POST /api/renewals/notices/{id}/read` | Cuerpo `{}`; respuesta `{read: true}` |

El cuerpo de creación y actualización de cotizaciones es:

```json
{
  "institutionId": 1,
  "folio": "COT-2026-001",
  "description": "Soporte SIABUC contratado para la biblioteca",
  "expiresOn": "2026-11-15",
  "validFrom": "2026-12-01",
  "validUntil": "2027-11-30",
  "agreedAmount": 2500.50,
  "emailMinutes": 180,
  "remoteMinutes": 120,
  "previousPolicyId": null
}
```

Los ejemplos son ilustrativos: usa instituciones, fechas y folios reales. `previousPolicyId` puede omitirse o ser `null` para una oferta nueva. Para usar una tarifa del catálogo se añaden `packageVersionId` y, si se negocian términos distintos, `packageOverrideReason` (hasta 2000 caracteres). La versión se verifica en el servidor; el detalle devuelve `packageSnapshot` con las condiciones originales. Omitir la versión al editar conserva el vínculo previo; no elimina su procedencia. El servidor no acepta campos adicionales no reconocidos ni minutos fraccionarios. Los conflictos de estado, institución o reintento devuelven HTTP 409; una entrada inválida, 400; falta de permiso, 403; identificador inexistente, 404.

Cada detalle de cotización incluye fechas de emisión y registro de la decisión, el contacto, las constancias, el administrativo y sus vínculos con pólizas. El historial descriptivo conserva los términos originales y los cambios al borrador. El historial de renovaciones devuelve además `newPolicyStatus`, para identificar la póliza sucesora cancelada.

La migración `V11__quotes_and_renewals.sql` agrega `support_quotes`, `quote_history`, `policy_renewals`, `renewal_settings`, `renewal_notices` y `renewal_notice_reads`. No reconstruye cotizaciones inexistentes para pólizas antiguas ni modifica pagos, saldos o minutos anteriores. Las pólizas ya existentes participan en las revisiones de vencimiento según su estado y fechas.

Las conversiones bloquean institución, póliza anterior —si existe— y cotización, y crean la póliza, vínculo e historial en una transacción. Restricciones únicas y claves foráneas impiden duplicar una conversión o enlazar instituciones diferentes. El escáner serializa la generación mediante la configuración y la unicidad de póliza/fecha.

Los componentes Angular independientes son `QuotesComponent` (`app-quotes`) y `RenewalsComponent` (`app-renewals`), sin entradas obligatorias. Renovaciones emite `openQuotes` para navegar al módulo de cotizaciones. La periodicidad interna del escáner admite las propiedades Spring `app.renewals.scan-ms` y `app.renewals.initial-delay-ms`, ambas con valor predeterminado de 60000 milisegundos; no es necesario modificarlas para una instalación habitual.

## Verificación

`QuoteIntegrationIT` cubre permisos, edición y congelación, validación de evidencia y fechas, precisión monetaria, conversión sin acreditación, reintentos, concurrencia y reemplazo de una renovación cancelada. `RenewalIntegrationIT` cubre umbrales y exclusiones, avisos con correo pausado, desactivación, deduplicación, lectura individual e historial tras cambio de vigencia.

Se ejecutan con una base exclusiva de pruebas, preparada según [Operación](OPERACION.md), y el mismo entorno PostgreSQL de integración del proyecto. Para una ejecución acotada:

```bash
cd backend
mvn -Ppostgres-it -Dit.test=QuoteIntegrationIT,RenewalIntegrationIT verify
```

No ejecutes esta suite apuntando a la base productiva: crea registros de prueba. Consulte [Validación](VALIDACION.md) y las [notas de 1.8.3](VERSION-1.8.3.md) para los resultados realmente ejecutados. Los informes de versiones anteriores conservan su valor histórico; esta guía no afirma por sí sola que se hayan ejecutado restauraciones Docker ni Windows.
