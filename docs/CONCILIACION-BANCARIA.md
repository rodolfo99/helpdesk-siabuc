# Conciliación bancaria por CSV — versión 1.8.3

Guía vigente de SIABUC Helpdesk 1.8.3. Consulte el [manual detallado](MANUAL-EXTENDIDO-1.8.3.md) para el recorrido completo por rol.

Disponible para **Administración (ADMIN)** y **Cobranza (BILLING)**. Los clientes y técnicos no pueden consultar ni modificar este módulo. La conciliación documenta una relación entre un ingreso bancario y un pago que ya existe en Helpdesk. **No conecta con el banco, no registra un pago, no cobra, no acredita minutos y no envía correos.**

## Preparar el CSV

Exporta los ingresos del banco y adapta una copia al formato siguiente. Conserva el estado de cuenta original fuera del sistema. Se aceptan archivos UTF-8, con o sin BOM, separados por coma, hasta **1 MiB y 1000 movimientos**. No se admite Excel binario ni un CSV con punto y coma.

```csv
fecha,referencia,importe,moneda
2026-10-13,TRANSFERENCIA-001,1250.50,MXN
2026-10-14,"DEPÓSITO, FOLIO 002",800.00,MXN
```

- El encabezado y el orden deben ser exactamente `fecha,referencia,importe,moneda`.
- `fecha`: fecha bancaria `AAAA-MM-DD`, desde 1900. Se rechazan fechas inexistentes.
- `referencia`: de 1 a 200 caracteres. Usa una referencia bancaria estable y que distinga el movimiento. Una coma dentro del campo requiere comillas; una comilla interna se duplica, como `"FOLIO ""A"""`. Se interpreta el formato RFC 4180 con saltos LF o CRLF. Aunque el parser reconoce campos entre comillas, los campos de negocio no admiten controles ni saltos internos.
- `importe`: positivo, hasta `999999999.99`, sin símbolos de moneda ni separadores de miles, con punto decimal y hasta dos decimales. No se redondean importes con mayor precisión. Cargos, comisiones, negativos e importes cero deben excluirse del archivo.
- `moneda`: tres letras mayúsculas; habitualmente `MXN`. Los registros históricos de otras monedas pueden conciliarse únicamente con un pago de esa misma moneda. No hay conversión de divisas.

El contenido se trata como texto: no se ejecutan fórmulas ni instrucciones incluidas en referencias. No se conservan los bytes originales del CSV; quedan los movimientos normalizados, nombre del archivo, huella de contenido, cuenta identificadora, autor y fecha. Para evidencia bancaria original utiliza el procedimiento documental de tu institución.

## Importación y duplicados

1. Abre **Conciliación bancaria** y escribe un identificador estable de cuenta, por ejemplo `Cuenta soporte terminación 1234`. No es una conexión ni una contraseña bancaria. Usa siempre el mismo identificador para una misma cuenta.
2. Selecciona el archivo y pulsa **Validar y mostrar vista previa**. Esta operación no guarda movimientos. Si alguna fila es inválida, se rechaza el archivo completo con la causa y el número de registro CSV, incluyendo el encabezado.
3. Revisa cuenta, fechas, referencias, importes, monedas y cuántos movimientos son nuevos o existentes.
4. Pulsa **Confirmar importación**. El servidor vuelve a validar el contenido y verifica la huella de la vista previa; cambiar el archivo o la cuenta exige otra vista previa. La importación es atómica: ante un error no se conserva una importación parcial.
5. Abre la importación para consultar los movimientos. El historial principal muestra las últimas 200 importaciones; una importación antigua puede abrirse nuevamente seleccionando su mismo contenido CSV, sin duplicarlo.

Las huellas SHA-256 se calculan con **identificador de cuenta + fecha + referencia + importe + moneda**. Cuenta y referencia se comparan sin mayúsculas/minúsculas y sin espacios iniciales o finales; los importes se normalizan a dos decimales. Reordenar filas, cambiar el nombre de archivo, BOM o saltos de línea no crea otra importación del mismo contenido. Los CSV con periodos superpuestos comparten el movimiento ya registrado, sin perder su historial de conciliación. Una repetición dentro del mismo archivo se rechaza para que puedas revisarla.

**Límite del formato:** dos ingresos de la misma cuenta que compartan exactamente fecha, referencia, importe y moneda son indistinguibles. Verifica el identificador único del banco antes de preparar el archivo. No cambies la cuenta o inventes una referencia sólo para evadir el control de duplicados. Los nombres de cuenta son identificadores locales; el sistema no sabe que dos alias diferentes corresponden a una misma cuenta bancaria.

## Sugerir y confirmar coincidencias

Abre **Ver movimiento**. Se proponen sólo pagos que:

- están vigentes, sin anulación;
- tienen exactamente el mismo importe y moneda;
- no están conciliados con ningún otro movimiento.

Primero aparecen las referencias iguales y después las fechas más cercanas. Se puede buscar por referencia, institución o número de pago, con paginación de 20 resultados. La fecha del pago puede ser diferente de la fecha bancaria; se muestra la diferencia para revisión humana. No se asume que una coincidencia de importe identifica por sí sola al cliente.

Pulsa **Elegir pago**, verifica la institución, referencia, importe y moneda, escribe el motivo o evidencia y marca la confirmación antes de **Confirmar conciliación**. El servidor revalida todo dentro de una transacción, incluso si otra persona opera al mismo tiempo. Un pago sólo puede conciliarse con un movimiento. Reintentar la misma confirmación conserva la relación y no duplica el evento.

Si no existe un pago adecuado, primero sigue el procedimiento normal de cobranza o aprobación de solicitud. Esta pantalla no crea pagos ni libera horas. Tampoco combina varios movimientos con un pago ni divide un movimiento entre varios pagos: la relación es uno a uno por importe exacto.

## Correcciones e historial

Cada conciliación y desconciliación conserva actor, momento, pago y motivo de al menos cinco caracteres. **Deshacer conciliación** requiere un motivo y verifica que la relación siga siendo la mostrada; no modifica ni anula el pago. Después puedes seleccionar el pago correcto. El historial anterior permanece disponible y existe también una entrada en la auditoría general.

Si Administración anula después un pago conciliado, el movimiento se marca **Revisar: pago anulado**. La relación histórica no se elimina ni se cambia silenciosamente. Revisa el motivo de anulación en Cobranza y deshaz la relación cuando corresponda; el pago anulado nunca vuelve a ser candidato. El vínculo y sus cambios no afectan los saldos de correo o remoto.

## API

Todas las rutas comienzan con `/api/reconciliation` y exigen sesión ADMIN/BILLING. Las escrituras exigen CSRF.

| Método y ruta | Uso |
|---|---|
| `POST /preview` | Multipart `account`, `file`; valida sin escritura y devuelve `hash`, existentes y filas. |
| `POST /imports` | Multipart `account`, `file`, `expectedHash`; importa de forma atómica o devuelve la importación original. |
| `GET /imports` | Últimas 200 importaciones. |
| `GET /imports/{id}` | Resumen y movimientos del archivo. |
| `GET /movements/{id}` | Detalle y eventos de conciliación. |
| `GET /movements/{id}/candidates?q=&page=0` | Pagos candidatos; 20 por página, total y criterios visibles. |
| `POST /movements/{id}/match` | JSON `paymentId`, `reason`; confirma una relación. |
| `POST /movements/{id}/unmatch` | JSON `paymentId`, `reason`; el ID debe coincidir con el vínculo actual. |

La migración **V23** añade `bank_imports`, `bank_movements`, `bank_import_rows` y `bank_reconciliation_events`. No altera pagos, pólizas, minutos ni ajustes de respaldo. La integridad incluye una restricción única de huella de movimiento y un índice único para el pago conciliado.

## Verificación reproducible

`BankCsvTest` cubre codificación, BOM, campos entre comillas, huellas estables, límites y validación estricta. `BankReconciliationIntegrationIT` cubre permisos/CSRF, vista previa sin cambios, importaciones repetidas y superpuestas, rechazo atómico, candidaturas, confirmación/deshacer, anulación posterior, monedas y competencia concurrente por un pago. El resultado de la ejecución conjunta se documenta en [Validación](VALIDACION.md) y en las [notas de 1.8.3](VERSION-1.8.3.md); la presencia de pruebas en el repositorio no implica por sí misma que hayan sido ejecutadas en todas las plataformas.
