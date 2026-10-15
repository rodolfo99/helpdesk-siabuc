# Estados de cuenta institucionales — versión 1.8.3

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

El estado de cuenta reúne pólizas, pagos, importes pendientes y movimientos de minutos de **una institución**. Administración y Cobranza pueden generarlo; los usuarios clientes sólo pueden hacerlo y consultarlo para su propia institución cuando Administración habilita su portal financiero. El rol Técnico no tiene acceso financiero.

## Generar y consultar

1. Abra **Estados de cuenta** (Administración/Cobranza), o **Finanzas institucionales → Estado de cuenta** para clientes habilitados.
2. Seleccione institución y fecha de corte. Para clientes la institución está determinada por su cuenta.
3. Pulse **Generar estado de cuenta**. Se conserva una emisión con folio `SIABUC-EC-##########`.
4. Revise los importes por moneda, pólizas, pagos y movimientos de minutos; descargue el PDF si lo necesita.
5. Abra una emisión anterior desde **Emisiones conservadas**. La lista muestra las 100 más recientes de esa institución; una emisión anterior sigue accesible por su identificador mediante API autorizada.

Generar conserva únicamente el documento. **No crea pagos, acredita minutos, consume soporte ni envía correos.** Descargar o volver a leer un documento no modifica información financiera. Una emisión conservada no tiene acción de editar o borrar.

## Qué significa la fecha de corte

El corte sigue la **fecha y hora de registro en la plataforma**, no la fecha declarada del pago (`paidOn`), ni el comienzo/final de vigencia de una póliza. Si el 20 de octubre se registra un pago con fecha declarada 1 de octubre, ese pago entra en el estado registrado el 20; no se traslada a cortes del día 1. Esto evita que una captura retroactiva altere la historia ya emitida.

- Para un día anterior a hoy, el límite es el final de ese día en la zona configurada mediante `app.reporting-time-zone` (por defecto `America/Mexico_City`).
- Para hoy, el límite efectivo es el instante de emisión; **no** representa un día completo que aún no termina.
- El documento muestra fecha solicitada, corte efectivo, hora de emisión y zona horaria.
- Cada cambio conserva la marca de inicio de su transacción (`transaction_timestamp()`), compartida por pago, póliza y contadores. Una operación que cruza medianoche entra completa en el mismo corte; nunca se divide entre dinero y minutos.
- La consulta utiliza una transacción consistente (`REPEATABLE_READ`). Se consideran las versiones registradas hasta el corte y visibles en esa transacción. Una operación que todavía no ha confirmado no forma parte de la emisión.
- Si una operación confirma después, una nueva emisión puede incluirla. El **folio emitido conserva exactamente sus datos**: se vuelve a consultar/descargar desde su copia inmutable, sin recalcular con los saldos actuales. La identidad reproducible del documento es su folio/ID, no sólo una fecha de corte.

Una edición posterior de una póliza, una anulación de pago o un ajuste del presupuesto no reescriben la emisión anterior. Genere otra para consultar la situación actual. El PDF de una emisión antigua presenta expresamente la situación conservada al emitir, y **no** constituye comprobante de saldo actual.

## Apertura de datos anteriores a 1.7

La migración V24 guarda una apertura con los valores conocidos de instituciones, pólizas y pagos existentes, sin cambiar sus registros originales. Desde entonces captura versiones públicas de los cambios relevantes dentro de las mismas transacciones de negocio.

**No hay reconstrucción inventada de movimientos anteriores a 1.7.** Los presupuestos iniciales pudieron tener ajustes manuales no estructurados; no se atribuyen fechas ni motivos desconocidos. Por ello:

- La primera fecha admitida es el día local de la migración; cortes anteriores se rechazan.
- El documento señala el instante exacto desde el cual existe la historia temporal.
- Las instituciones preexistentes muestran **Apertura anterior a 1.7**, con sus minutos asignados/usados en ese momento.
- Las pólizas y pagos previos entran con el estado que tenían en la apertura. Un pago anulado antes de migrar se conserva como anulado; su antigua fecha declarada se muestra sólo como referencia.
- La apertura no significa que todos esos pagos, contratos o consumos ocurrieron el día de la actualización.
- Las instituciones creadas después muestran **Saldo inicial de institución**. Sus cambios posteriores sí quedan estructurados.

## Importes y minutos

Cada moneda se totaliza por separado, conservando monedas históricas; no se convierten ni suman pesos y otras divisas. Las capturas nuevas mantienen las reglas financieras de MXN existentes.

| Campo | Interpretación |
|---|---|
| Contratos activos | Suma del importe acordado de pólizas cuyo estado registrado al corte es `ACTIVE`; la vigencia se muestra por separado y no cancela automáticamente el contrato. |
| Pagos a pólizas | Pagos no anulados vinculados a una póliza. |
| Compras directas | Pagos no anulados sin póliza. Ya están pagados; no crean una deuda contractual adicional. |
| Pagos vigentes totales | Pagos a pólizas + compras directas, en la misma moneda. |
| Pendiente contractual | Para cada póliza activa, contratado menos pagos no anulados; las canceladas aportan cero. |
| Anulados excluidos | Importe original de los pagos anulados, presentado como antecedente y excluido de pagos vigentes y acreditaciones. |

Los pagos con importe cero que liberan horas de una póliza ya liquidada aparecen como movimientos de acreditación; no generan otro ingreso. Las solicitudes pendientes de aprobación, cotizaciones y movimientos bancarios sin pago confirmado no son pagos y no se suman.

Las pólizas muestran minutos contratados y acreditados por sus pagos. El saldo de institución muestra **asignados, usados y disponibles**, separados entre correo y remoto. Los cambios de los contadores institucionales se conservan como movimientos; variación disponible = cambio asignado − cambio usado. Esto incluye créditos, ajustes, consumos y sus reversos; **no se adivina una causa ni se atribuye a un técnico** a partir de un simple cambio numérico. No incluye textos de casos ni notas internas.

En el estado de cuenta, las aplicaciones de pago son los vínculos de pagos con pólizas. El calendario de parcialidades tiene su propio detalle de distribución y recordatorios; sus fechas comprometidas no cambian el corte del estado de cuenta.

## Privacidad y conservación

La proyección financiera pública es la misma para clientes y personal autorizado: no contiene notas privadas, descripciones internas de pólizas/pagos, motivos de anulación, contenido de casos, identidades de administrativos ni datos de autenticación. Los nombres institucionales se conservan con el corte. Cada descarga vuelve a comprobar sesión, rol, institución y habilitación actual del portal; revocar el portal bloquea también las emisiones antiguas.

V24 añade `statement_history_origin`, `statement_versions` e `institution_statements`. Los disparadores de PostgreSQL capturan las versiones y rechazan actualizaciones/borrados del historial y de las emisiones. Los registros forman parte del respaldo PostgreSQL existente. No hay nueva tarea de respaldo; los **respaldos automáticos siguen desactivados por defecto**.

Límites: 5000 pólizas y pagos combinados, 20000 versiones de contadores por institución, 4 millones de caracteres para la emisión y 1500 páginas PDF. Si un documento rebasa un límite se rechaza con explicación; nunca se recortan filas silenciosamente. Los documentos son administrativos, **no CFDI, factura ni constancia de conciliación bancaria**.

## API

Administración/Cobranza:

- `GET /api/statements/options`: instituciones, zona horaria y límites temporales.
- `GET /api/statements?institutionId=ID`: últimas 100 emisiones.
- `POST /api/statements` con `{ "requestId": "UUID", "institutionId": 123, "cutoff": "2026-10-20" }`: emisión idempotente, con CSRF.
- `GET /api/statements/ID`: datos inmutables de una emisión.
- `GET /api/statements/ID.pdf`: PDF generado desde esa misma emisión; `Cache-Control: no-store, private`.

Cliente autorizado: los mismos recursos bajo `/api/client-finance/statements`. La lista no acepta una selección de institución. En el cuerpo de creación se omite `institutionId`; si se intenta indicar otra institución, se rechaza. La reutilización de un `requestId` con otra institución o fecha de corte devuelve conflicto.

## Verificación reproducible

`InstitutionStatementIntegrationIT` cubre proyección pública, permisos/CSRF, efectos de ediciones y anulaciones sobre emisiones anteriores, límites temporales, idempotencia, PDF y ausencia de movimientos financieros al emitir. `tests/migrations/statements-v17.mjs` aplica SQL en una base aislada y comprueba la apertura, versiones, rollback e inmutabilidad. Son procedimientos de prueba; el resultado ejecutado de cada entorno se registra en [Validación](VALIDACION.md) y en las [notas de 1.8.3](VERSION-1.8.3.md).
