# Licencias y horas predeterminadas de soporte

Funciones incluidas en 1.8.4, incorporadas sobre la base 1.8.3 mediante V27. La revisión 1.8.4 no añade migraciones. Los manuales y resultados publicados de versiones anteriores conservan su alcance histórico.

## Configurar las horas incluidas

Con una cuenta **ADMIN**, abre **Configuración de soporte → Horas predeterminadas de las licencias**.

1. Define horas y minutos de **Soporte por correo**.
2. Define por separado horas y minutos de **Escritorio remoto**.
3. Pulsa **Guardar horas predeterminadas**.

Se admiten horas enteras no negativas y minutos de 0 a 59, con máximo de 1 000 000 000 de minutos por canal. Cada canal puede quedar en cero. La primera instalación queda **sin configurar**: no se inventa una dotación comercial de horas. Un administrador debe guardar los valores antes de crear licencias.

Guardar cambia exclusivamente la dotación de las licencias que se creen después. No modifica saldos, horas iniciales ni consumos anteriores. Si otro administrador cambió la configuración, el servidor rechaza la edición desactualizada; usa **Volver a cargar valores**, revisa y vuelve a guardar.

## Registrar una licencia

1. Crea o identifica la institución titular.
2. Como ADMIN, abre **Licencias → Nueva licencia**.
3. Selecciona la institución y captura un número único de licencia.
4. Revisa las horas incluidas que muestra el formulario y pulsa **Guardar licencia y acreditar horas**.

La institución admite varias licencias. Cada licencia mantiene dos bolsas independientes, su dotación inicial y el historial de asignaciones, pagos y consumos. El alta acredita las horas exactamente una vez y aumenta también el total institucional. Una clave duplicada se rechaza sin recargar saldo. Si cambiaron los valores predeterminados mientras estaba abierto el formulario, vuelve a abrir **Nueva licencia** para revisar la nueva dotación.

Este registro sirve para el control local de soporte. No valida claves de activación contra los sistemas comerciales de SIABUC9, no genera licencias del software ni modifica su titularidad externa.

## Agregar horas a una licencia existente

Para una ampliación administrativa, ADMIN abre la licencia y elige **Agregar horas**. Indica horas/minutos por canal y el motivo; confirma una vez. Se suman al saldo, conservando las horas incluidas al alta y el historial. Un reintento idéntico con el mismo identificador de solicitud devuelve el resultado previo sin volver a acreditar. Esta acción no registra dinero.

Para una compra, ADMIN o BILLING crea una **Póliza** de la institución y selecciona **Licencia de la póliza**. El alta del contrato no acredita minutos. Sus pagos o solicitudes de pago aprobadas acreditan la licencia seleccionada y el total institucional en la misma transacción. No hagas además una asignación manual por esa misma compra.

La licencia de una póliza se fija al crearla y no se cambia después. Una renovación convertida desde la póliza anterior conserva esa licencia. Una cotización nueva sin antecedente sigue creando una póliza sin licencia; para contratar directamente soporte para una licencia utiliza **Pólizas → Nueva póliza**. Los pagos libres, sin póliza, conservan el saldo institucional sin licencia.

## Registrar atención

En **Nuevo caso**, selecciona **Licencia para esta solicitud**. La cuenta cliente sólo puede utilizar licencias de su propia institución. El caso conserva la licencia elegida; los consumos de correo y remoto, también los confirmados desde cronómetro, se descuentan de esa licencia y del total institucional.

Crear un caso no consume minutos. Requiere saldo positivo del canal elegido. No se toma tiempo de otra licencia ni del otro canal. Anular una atención devuelve los minutos a la misma bolsa una sola vez. Para anular un pago debe quedar todo el tiempo correspondiente en la licencia que recibió la acreditación; las horas de otra licencia no sirven para respaldar la anulación.

## Compatibilidad con los saldos anteriores

V27 crea las tablas nuevas y vínculos opcionales; **no inventa licencias, no reparte saldos previos y no acredita horas durante la migración**. Los casos, pólizas y pagos existentes quedan sin licencia.

El total institucional incluye el saldo sin licencia y todas sus licencias. Un caso sin licencia sólo utiliza el saldo sin asignar. La edición del presupuesto institucional no puede retirar las horas reservadas para licencias; los incrementos manuales desde **Instituciones** alimentan el saldo sin licencia.

No registres como nueva dotación horas que ya están incluidas en el saldo anterior. Esta ampliación no incluye una herramienta de traslado de saldos históricos a licencias. Conserva el uso del saldo anterior y registra solamente dotaciones nuevas hasta definir un traslado explícito y auditable.

Antes de aplicar V27, respalda y ensaya en una copia aislada. No vuelvas a ejecutar el backend anterior sobre una base con V27: ese backend no respeta la reserva por licencia. Un retroceso requiere restaurar una copia compatible en una base nueva.

## Permisos y trazabilidad

| Operación | ADMIN | BILLING | TECH | USER |
| --- | --- | --- | --- | --- |
| Modificar horas predeterminadas | Sí | No | No | No |
| Registrar licencia o agregar horas manuales | Sí | No | No | No |
| Consultar licencias y saldos | Todas | Todas | Todas | Sólo su institución |
| Consultar historial de la licencia | Sí | No | No | No |
| Acreditar mediante pago de póliza | Sí | Sí | No | No |
| Consumir minutos en una atención | Sí | No | Sí | No |

El historial conserva autor, fecha, canal, minutos y motivo. Los vínculos de pago y atención permiten consultar el movimiento de origen. Cambiar la configuración registra una auditoría separada y no genera acreditaciones.

## Verificación

`LicenseIntegrationIT` verifica persistencia y cambios de configuración, altas únicas, topups idempotentes y concurrentes, aislamiento de instituciones y licencias, consumo/devolución por canal, permisos, límites y rollback de pagos con PostgreSQL real. `tests/e2e/licenses.ui.mjs` verifica formularios y navegación Angular con una API simulada; no demuestra persistencia en PostgreSQL.

Comandos, en entornos exclusivos de prueba:

```bash
cd backend
mvn -Ppostgres-it verify
```

```bash
cd frontend
npm ci
npm test
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node licenses.ui.mjs
```

Consulta los resultados vinculados al commit de la PR. No se sustituye la validación de la instalación productiva por compilación o pruebas de interfaz simuladas.
