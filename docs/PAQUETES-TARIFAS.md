# Catálogo de paquetes y tarifas — versión 1.8.3

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

Administración y Cobranza pueden preparar paquetes de soporte, consultar sus revisiones y usarlos como punto de partida para cotizaciones. Técnico y Cliente no tienen acceso al catálogo financiero ni a sus APIs.

## Crear y mantener paquetes

1. Abra **Paquetes y tarifas → Nuevo paquete**.
2. Capture un código estable de hasta 60 caracteres (letras, números, guiones o guion bajo), nombre, descripción/condiciones, importe en MXN y minutos de correo y remoto. El código se normaliza a mayúsculas y debe ser único.
3. Guarde. El importe debe ser positivo con hasta dos decimales; cada canal admite de 0 a 1 000 000 000 minutos y al menos uno debe ser positivo.
4. Para cambiar la oferta, abra el paquete y elija **Crear nueva versión**. El código no cambia; nombre, condiciones, precio y minutos quedan en una nueva revisión.
5. **Desactivar paquete** impide usarlo en nuevas cotizaciones. El paquete y todas sus versiones siguen disponibles para consulta histórica. Puede reactivarlo posteriormente.

El servidor comprueba la revisión que está editando. Si otra persona publicó una revisión antes, rechaza la edición para que revise la tarifa actual. No existe borrado de paquetes ni sobrescritura de sus revisiones mediante la API.

## Cotizar con una tarifa

1. Abra **Cotizaciones → Nueva cotización** y elija la institución.
2. Seleccione un paquete. Se copian importe, descripción y minutos de la versión vigente recibida del servidor.
3. Complete folio, fechas de oferta y cobertura. Revise todos los términos antes de guardar.
4. Si negocia otro precio, minutos o descripción, capture el **Motivo de ajuste al paquete**. Se conservan tanto la versión original como los términos negociados.
5. Al registrar la emisión se congelan los términos de la cotización. La aceptación y conversión posterior generan la póliza con los términos negociados.

El detalle muestra código, revisión, tarifa original, minutos, condiciones originales y motivo del ajuste. El historial de la cotización registra la versión seleccionada y los términos pactados. No se admite que el navegador mande una copia arbitraria del paquete: debe referenciar un identificador de versión existente.

Una nueva cotización debe usar la revisión vigente de un paquete activo. Si cambió la tarifa mientras tenía abierto el formulario, se solicita actualizar y volver a seleccionarla. Un borrador existente puede conservar su revisión histórica aunque el paquete cambie o sea desactivado; al modificarlo se sigue exigiendo el motivo cuando sus términos difieren de esa revisión.

Una vez vinculado un paquete, el borrador conserva su procedencia. Se puede seleccionar otra versión vigente de forma explícita, quedando el cambio en el historial; para una oferta sin procedencia cree una cotización nueva sin paquete. La edición de fechas de cobertura no requiere motivo de tarifa porque el catálogo no define un periodo contractual.

**Crear, modificar o seleccionar un paquete no registra un pago ni acredita soporte.** Tampoco modifica cotizaciones emitidas, pólizas o pagos anteriores. La conversión mantiene el vínculo con la cotización que conserva la versión del paquete.

## API

Todas las rutas tienen prefijo `/api` y requieren sesión de `ADMIN` o `BILLING`. Las escrituras usan el mecanismo CSRF existente.

| Método y ruta | Uso |
|---|---|
| `GET /support-packages` | Versiones vigentes de todos los paquetes, activos y desactivados. |
| `POST /support-packages` | Crear código y primera versión. |
| `GET /support-packages/{id}` | Versión vigente e historial de versiones. |
| `PUT /support-packages/{id}` | Publicar una revisión con `expectedRevision`. |
| `POST /support-packages/{id}/active` | Activar/desactivar con `{ "active": true/false }`. |

Cuerpo de creación/edición: `code`, `name`, `description`, `amount`, `emailMinutes`, `remoteMinutes`; la edición añade `expectedRevision`. La moneda es MXN y no se recibe como valor modificable.

Las rutas de cotizaciones existentes admiten opcionalmente `packageVersionId` y `packageOverrideReason`. Su detalle añade `packageSnapshot` y `packageOverrideReason`; las solicitudes anteriores sin estos campos siguen siendo válidas. Si se omite la versión al editar un borrador ya vinculado, conserva la anterior.

Persistencia: migración aditiva `V21__support_packages.sql`, tablas `support_packages`, `support_package_versions` y dos columnas en `support_quotes`. No requiere nuevas variables `.env`, servicios Docker ni dependencias externas. Las versiones y vínculos forman parte del respaldo PostgreSQL.

## Verificación incluida

`SupportPackageIntegrationIT` cubre revisiones, conflicto por edición simultánea, desactivación, permisos/CSRF, importes y minutos inválidos, conservación de tarifa histórica, rechazo de tarifa obsoleta, negociación explícita y conversión sin crédito de minutos. Consulte [VALIDACION.md](VALIDACION.md) para distinguir pruebas ejecutadas de pruebas incluidas y límites del entorno.
