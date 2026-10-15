# SIABUC Helpdesk 1.8.3 — estados de atención y red Docker

[Manual detallado](MANUAL-EXTENDIDO-1.8.3.md) · [Índice de guías](README.md) ·
[Evidencia de validación](VALIDACION-1.8.3.md).

## Uso

1. Abra **Casos de soporte** y seleccione el caso.
2. Con una cuenta de Administración o Técnico, pulse **Cambiar estado**.
3. Seleccione el destino, escriba un motivo y confirme. El cambio queda en el
   historial y en auditoría, y genera avisos del portal y de la cola SMTP según
   la configuración vigente de correo. No implica que el correo haya sido entregado.

| Estado visible | Identificador API/BD | Comportamiento |
| --- | --- | --- |
| Recibido | `OPEN` | Estado de una solicitud nueva. Permite comentarios, ficha, adjuntos y atención. |
| En atención | `IN_PROGRESS` | Trabajo en curso. Tomar un caso recibido lo coloca en este estado. |
| Requiere respuesta del cliente | `WAITING_CLIENT` | Solicitud de información. El cliente puede responder o adjuntar evidencia; al comentar, vuelve a En atención. |
| Resuelto | `RESOLVED` | Registra la resolución y conserva historial. Requiere reapertura para agregar seguimientos, archivos, ficha o consumo. |
| Cerrado | `CLOSED` | Cierre administrativo; habilita la encuesta bajo las reglas anteriores. Conserva historial. |

Los estados se muestran en el listado, detalle, tablero, casos vinculados a
incidencias, avisos SMTP y reportes Excel/PDF. Los estados de las incidencias
generales tienen su propio flujo y no cambian automáticamente los casos.

## Transiciones

| Origen | Destinos del selector |
| --- | --- |
| Recibido | En atención, Requiere respuesta del cliente, Resuelto, Cerrado |
| En atención | Requiere respuesta del cliente, Resuelto, Cerrado |
| Requiere respuesta del cliente | En atención, Resuelto, Cerrado |
| Resuelto | En atención, Cerrado |
| Cerrado | En atención |

Se permite cerrar directamente un caso activo para conservar el flujo previo.
Un envío repetido del mismo estado no duplica historial ni avisos. No se permite
pasar de Cerrado a Resuelto sin reabrir. La API mantiene la reapertura heredada
con `OPEN` por compatibilidad; la interfaz utiliza En atención.

Sólo Administración y Técnicos cambian estados. Los clientes conservan acceso
exclusivo a su institución; su respuesta en un caso en espera es la única
transición automática por comentario. Una nota de soporte no declara por sí sola
que el caso está resuelto ni que requiere respuesta: utilice el selector.

Reabrir desde Resuelto o Cerrado exige institución activa y saldo positivo en el
canal principal. Cambiar entre estados activos no exige saldo adicional ni consume
minutos. Cerrar y resolver mantienen la posibilidad previa de finalizar un caso
sin saldo. Un cambio de estado no cuenta como primera respuesta pública.

## SLA y cronómetros

- Cambiar entre los tres estados activos **no reinicia** los plazos.
- Esperar respuesta del cliente **no pausa el SLA**. Continúa el calendario
  laboral configurado y siguen disponibles los avisos de vencimiento.
- Resuelto registra `resolved_at`. El cierre posterior conserva esa fecha;
  cerrar directamente también registra la resolución.
- Reabrir inicia una nueva ventana de resolución, conservando la primera
  respuesta histórica, igual que en la reapertura anterior.
- Al esperar al cliente, resolver o cerrar, todos los cronómetros en marcha del
  caso se pausan con su tiempo medido y una entrada de auditoría. No se cobran.
- Iniciar o continuar un cronómetro requiere Recibido o En atención. Puede
  finalizarlo o descartarlo estando pausado. Confirmar minutos requiere un caso
  activo; en Resuelto/Cerrado debe reabrir primero. Nada confirma o descuenta
  minutos automáticamente al cambiar de estado.

## Actualización desde 1.8.2

La migración aditiva Flyway **V26** amplía la restricción del campo `status`.
Las migraciones V1–V25 conservan sus bytes. Los valores anteriores `OPEN` y
`CLOSED`, folios, responsables, fechas, atenciones y saldos se conservan. No se
infiere retrospectivamente qué casos abiertos estaban en atención o esperando:
permanecen como Recibido hasta que soporte actualice su estado.

Las vistas guardadas antiguas cuyo estado era `OPEN` pasan al filtro `active`
(**Pendientes de resolver**) para seguir incluyendo todo el trabajo pendiente.
Las vistas nuevas pueden guardar cualquiera de los cinco estados o ese grupo.
Los reportes cuentan pendientes, resueltos y cerrados por separado.

Respalde y compruebe la restauración antes de actualizar. Conserve `.env`,
`COMPOSE_PROJECT_NAME` y los volúmenes. Compile y pruebe esta versión en un
entorno separado y después use `./iniciar.sh` según su proceso de mantenimiento.
No utilice `docker compose down -v`. Tras aplicar V26, no inicie un backend
anterior contra esa base: no reconoce los estados nuevos. Para retroceder,
consulte [respaldo y restauración](RESPALDO-RESTAURACION.md) y restaure en una
base nueva el respaldo compatible con la versión anterior.

Se conserva Gmail SMTP directo y Postfix opcional de 1.8.2. Esta versión no
modifica credenciales ni incorpora otros requisitos preliminares institucionales.

## Docker sin red SMTP dedicada

Se elimina `smtp_net` y su IPAM con subred fija. El backend y el Postfix opcional
usan la red `default` que Compose crea para el proyecto. Postfix deriva sus
clientes permitidos de la subred real del contenedor, compartida con los demás
servicios; el puerto SMTP sigue sin publicarse en el despliegue normal y sólo
escucha en loopback del anfitrión con el override de desarrollo.

La clave heredada `SMTP_NETWORK_SUBNET` se acepta y se ignora; no se genera en
configuraciones nuevas ni requiere editar un `.env` existente. Consulte
[SMTP interno](SMTP-INTERNO.md#acceso-restringido-y-red) para operación y limpieza
de una red antigua vacía tras recrear los contenedores.

## Validación

La suite Java incorpora el recorrido de los cinco estados, permisos, idempotencia,
SLA, reapertura con saldo, pausa/conservación del cronómetro, reportes y avisos.
Una prueba de migración aplica V1–V25 con datos y después V26 en un esquema
desechable. La prueba de navegador `tests/e2e/case-workflow-v183.ui.mjs` comprueba
selector, filtros, historial, permisos visuales y visualización móvil con API
simulada. Consulte los checks de la PR para resultados y SHA exactos.

CI también valida que Compose use una sola red sin subred fija en ambos modos
de correo. Un smoke con Postfix real comprueba EHLO, MAIL, RCPT y RSET desde otro
contenedor de esa red, sin DATA ni envío de un mensaje al exterior.

Estas verificaciones no acreditan por sí solas entrega SMTP real, actualización
de producción ni restauración en el equipo de destino. La evidencia histórica
de versiones previas conserva su fecha y no se atribuye a 1.8.3.
