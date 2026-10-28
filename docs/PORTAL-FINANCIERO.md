# Portal financiero por institución — versión 1.8.5

Guía vigente de SIABUC Helpdesk 1.8.5. Consulte el [manual detallado](MANUAL-EXTENDIDO-LICENCIAS.md) para el recorrido completo por rol.

Por defecto está **deshabilitado** para todas las instituciones, incluidas las existentes al actualizar. No se publica información financiera por el hecho de tener una cuenta de soporte.

## Habilitación explícita

1. Iniciar sesión como **ADMIN** y abrir **Configuración de soporte → Acceso financiero de clientes**.
2. Buscar la institución y pulsar **Habilitar**.
3. Leer el alcance y confirmar. Desde ese momento todos sus usuarios activos pueden consultar sus propias pólizas, importes, pagos, recibos y **comprobantes históricos** y enviar solicitudes de validación.

Los clientes (`USER`) acceden desde **Finanzas institucionales**. El permiso es institucional, no por usuario. Cobranza (`BILLING`) no puede modificarlo y Técnico (`TECH`) no tiene acceso a este portal. No se permite habilitar instituciones inactivas. El cambio se registra en auditoría.

Deshabilitar impide nuevas consultas, descargas y solicitudes con efecto en las siguientes peticiones, sin borrar historial. Los archivos que una persona ya descargó fuera del sistema no pueden retirarse. Los datos previamente mostrados en una pestaña pueden permanecer visibles hasta actualizarla; el servidor vuelve a verificar la autorización para cada operación. La baja de un usuario o institución también bloquea el acceso mediante la sesión existente.

## Información disponible

- Pólizas: folio, fechas, importe acordado, pagado, pendiente de cobro, minutos contratados y acreditados, estado. Una póliza cancelada muestra pendiente de cobro cero; no se cobra la diferencia contractual cancelada.
- Pagos propios: referencia, fecha, importe y moneda originales, método, minutos acreditados, estado registrado/anulado. Los pagos anulados permanecen visibles, pero no se suman al importe pagado ni a los minutos de la póliza.
- Comprobantes de esos pagos, incluidos los adjuntados antes de habilitar el portal. Se pueden descargar; el cliente no modifica ni complementa la evidencia del pago registrado.
- [Recibos administrativos PDF](RECIBOS-PAGO.md), con folio estable y leyenda no fiscal. La descarga no genera un cobro ni acredita minutos.
- [Solicitudes de pago](SOLICITUDES-PAGO.md), evidencia antes de validación, estado y motivo público de rechazo.

En esta vista se omiten las notas/descripción del pago, la descripción interna de la póliza, los motivos internos de cancelación o anulación, la auditoría, el historial financiero interno y las observaciones privadas de aprobación. La descripción de un pago registrado directamente puede haberse incluido en el correo de pago enviado a la institución: no es un campo privado. La observación de aprobación de una solicitud sí es privada y permanece reservada a Administración/Cobranza. La descripción de una solicitud y el motivo de rechazo se capturan como información visible para la institución. Los archivos financieros autorizados pueden contener información sensible: revisar su contenido antes de habilitar el acceso histórico.

Cada consulta filtra la institución de la cuenta autenticada. No se acepta un parámetro para elegir otra institución. Un identificador ajeno devuelve 404; el portal deshabilitado devuelve 403. Las sesiones o instituciones dadas de baja devuelven 401, conforme al control de autenticación del sistema.

## API

| Ruta | Permiso / resultado |
|---|---|
| `GET /api/client-finance-access` | ADMIN; lista instituciones y configuración |
| `PUT /api/client-finance-access/{institutionId}` | ADMIN; JSON exacto `{"enabled":true}` o `false` |
| `GET /api/client-finance/access` | USER activo; devuelve únicamente `enabled` incluso si está deshabilitado |
| `GET /api/client-finance` | USER habilitado; resumen de institución, pólizas y pagos con proyección pública |
| `GET /api/client-finance/payments/{id}/attachments` | USER habilitado; metadatos de archivos de un pago propio |
| `GET /api/client-finance/payment-attachments/{id}/download` | USER habilitado; archivo financiero propio |
| `GET /api/client-finance/payments/{id}/receipt.pdf` | USER habilitado; recibo propio |

La migración V12 crea `client_finance_access`; la ausencia de fila equivale a deshabilitado. `ClientFinanceAccessService.requireClient()` centraliza rol USER, cuenta e institución activas y habilitación, y se reutiliza en solicitudes y recibos. Las rutas originales de administración/cobranza siguen denegadas al cliente, incluso con portal habilitado.


## Estados de cuenta institucionales

El portal habilitado incorpora la generación y descarga PDF del estado de cuenta de la propia institución. Cada folio preserva su emisión; los cambios posteriores no alteran ese documento. El corte usa el registro en plataforma y la historia temporal empieza con una apertura en V24. Consulte [Estados de cuenta](ESTADOS-CUENTA.md) para interpretar cargos, pagos, pendientes y movimientos de minutos. Deshabilitar el portal también bloquea la lectura y descarga de emisiones anteriores desde cuentas cliente.
