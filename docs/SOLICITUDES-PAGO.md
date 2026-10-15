# Solicitudes de pago pendientes — versión 1.8.3

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

Las solicitudes permiten revisar la evidencia **antes de registrar un pago o acreditar minutos**. El registro directo de pagos sigue disponible para Administración (`ADMIN`) y Cobranza (`BILLING`) cuando la validación ya se hizo fuera de la plataforma. El rol Técnico (`TECH`) no tiene acceso a solicitudes ni decisiones financieras.

## Flujo de administración y cobranza

1. Abrir **Validar pagos → Nueva solicitud**. Elegir institución activa y, opcionalmente, una póliza activa de esa institución.
2. Capturar referencia, importe en pesos mexicanos (MXN), fecha, método, minutos de correo y remoto solicitados, y una descripción **visible para la institución**. Los minutos son una petición, no saldo disponible.
3. Guardar. Se crea el estado **Pendiente**; no se crea un pago, no se acreditan minutos y no se envía un aviso de pago.
4. Adjuntar y descargar los comprobantes PDF, PNG o JPG/JPEG para verificarlos. Se requiere al menos un archivo para aprobar.
5. La sección de validación presenta el importe y los dos conceptos de minutos completos. Marcar la casilla que confirma haber revisado la evidencia, el dinero y los minutos antes de pulsar **Aprobar y registrar pago**.
6. La aprobación crea el pago, acredita sus minutos, copia la evidencia al pago y conserva la decisión en una sola transacción. El aviso de pago normal sigue la configuración de correo vigente. Si una validación o el guardado falla, permanece pendiente y no hay crédito parcial.

No se editan importes ni minutos durante la aprobación. Si están equivocados, rechazar con un motivo y registrar una nueva solicitud. La observación de **aprobación es privada** para administración/cobranza. El motivo de **rechazo es visible** para la institución y es obligatorio. Se puede rechazar sin comprobantes; rechazar nunca modifica el saldo y conserva la evidencia.

## Flujo del cliente

El administrador debe habilitar primero el [portal financiero](PORTAL-FINANCIERO.md) para la institución. Desde **Finanzas institucionales → Solicitudes de pago**, sus usuarios activos (`USER`) podrán crear solicitudes para su institución, adjuntar evidencia mientras estén pendientes, consultar el estado y descargar los archivos. No pueden aprobar, rechazar, registrar pagos directos ni adjuntar archivos nuevos a pagos ya registrados. El rechazo muestra su motivo; la observación privada de aprobación no se devuelve al cliente en ninguna vista ni en reintentos del envío.

## Reglas de dinero, pólizas e historial

- Sin póliza se solicita un importe positivo y al menos un minuto de correo o remoto.
- Con póliza puede solicitarse un abono monetario sin minutos, o minutos con importe cero. La acreditación con importe cero se autoriza solamente si la póliza ya está completamente pagada, conforme a las reglas del módulo de pagos.
- La aprobación comprueba nuevamente institución activa, póliza, monto pendiente y minutos contratados disponibles. Que se haya aceptado la solicitud no garantiza su aprobación posterior.
- No se cambian ni eliminan solicitudes resueltas. La evidencia queda congelada, conserva sus bytes y puede descargarse. Un pago posteriormente anulado permanece asociado a la solicitud aprobada; repetir la aprobación no lo reactiva ni genera otro pago. La anulación se consulta en el historial del pago.
- Repetir una aprobación devuelve el mismo pago. Repetir un rechazo con el mismo motivo devuelve la decisión anterior; cambiar el motivo después de resolver produce conflicto.
- El alta tiene un UUID por usuario y datos normalizados. Reutilizarlo con datos diferentes produce conflicto. La aprobación usa otro UUID interno estable que nunca se expone al cliente.

## Archivos y respaldo

Máximo **10 MiB por archivo, 10 archivos y 50 MiB por solicitud**. Se aceptan únicamente PDF, PNG y JPG/JPEG con firma reconocida y extensión compatible; no se confía en el MIME del navegador. No se aceptan ZIP, ejecutables, HTML ni archivos de diagnóstico en este módulo. Esta comprobación identifica formatos, no sustituye un análisis antivirus.

Los nombres se normalizan, los archivos se entregan como descarga con `nosniff`, CSP y `no-store`. Se conserva SHA-256, fecha y autor. Los bytes de solicitud y las copias del pago están en PostgreSQL `BYTEA`, por lo que los respaldos/restauraciones de la base de datos los incluyen; no hay carpeta adicional que sincronizar. No se envían adjuntos por SMTP. El original conserva al autor del envío; la copia del pago registra como responsable al administrativo que aprueba.

El alta, cada evidencia y la decisión se registran en la auditoría administrativa. La copia al pago también registra `ATTACH_PAYMENT_PROOF` en el historial de la póliza, si corresponde. No se mezclan comprobantes financieros con comentarios o adjuntos de casos de soporte.

## API y concurrencia

| Operación | Administración / cobranza | Cliente habilitado |
|---|---|---|
| Listar / crear | `GET/POST /api/payment-requests` | `GET/POST /api/client-finance/payment-requests` |
| Detalle | `GET /api/payment-requests/{id}` | `GET /api/client-finance/payment-requests/{id}` |
| Listar / adjuntar | `GET/POST /api/payment-requests/{id}/attachments` | `GET/POST /api/client-finance/payment-requests/{id}/attachments` |
| Descargar | `GET /api/payment-requests/attachments/{id}/download` | `GET /api/client-finance/payment-requests/attachments/{id}/download` |
| Aprobar / rechazar | `POST /api/payment-requests/{id}/approve` o `/reject` | No permitido |

Alta JSON: `requestId` UUID, `institutionId`, `policyId` opcional, `reference`, `amount` decimal con hasta dos decimales, `paidOn` fecha ISO, `method`, `emailMinutes`, `remoteMinutes`, `description`. La moneda es siempre MXN. El multipart de evidencia contiene `file` y `requestId` UUID. Las decisiones reciben `{"reason":"..."}`. Las rutas mutables usan sesión y CSRF existentes.

Orden de bloqueos: institución → póliza opcional → solicitud; la aprobación llama al servicio de pagos con la institución/póliza ya bloqueadas y después el bloqueo del pago. Límites de adjuntos, estado y reintentos se resuelven dentro de esa transacción. El cliente no puede seleccionar una institución o póliza ajena, ni descargar su evidencia adivinando identificadores.

## Pruebas

`PaymentRequestIntegrationIT` cubre permisos, PDF e imagen antes de aprobar, conservación de bytes, reintentos, rechazo sin evidencia, límites y formatos, políticas de importe/minutos, reversión después de un fallo controlado del aviso y dos aprobaciones simultáneas. `ClientFinanceIntegrationIT` cubre proyecciones privadas, acceso institucional y revocación. La ejecución conjunta y sus limitaciones reales se documentan en [Validación](VALIDACION.md) y en las [notas de 1.8.3](VERSION-1.8.3.md); una prueba con un único backend de base de datos no demuestra contención real entre varias conexiones PostgreSQL.
