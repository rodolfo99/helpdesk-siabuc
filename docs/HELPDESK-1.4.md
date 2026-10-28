# Funciones Helpdesk 1.4

> **Documento histórico — 1.4.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.5.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), el [índice de documentación](README.md) y las [notas de 1.8.5](VERSION-1.8.5.md).

## Permisos

| Función | Administrativo ADMIN | Cliente USER | Cobranza BILLING |
| --- | --- | --- | --- |
| Casos, ficha, historial y adjuntos públicos | Todos | Sólo su institución | Sin acceso |
| Notas internas y sus adjuntos | Sí | Sin acceso | Sin acceso |
| Clasificación y ficha de caso abierto | Sí | Sólo su institución | Sin acceso |
| Cronómetro y descuento de atención | Sí, cronómetros propios | Sin acceso | Sin acceso |
| Proponer, reprogramar o concluir sesión | Sí | Sin acceso | Sin acceso |
| Confirmar o rechazar propuesta | Sin sustituir al cliente | Sólo su institución | Sin acceso |
| Configurar SLA, alertas y consultar reportes | Sí | Sin acceso | Sin acceso |
| Campana de saldo bajo | Sí | Sólo su institución | Sí |

Cada acción se autoriza en Spring. Ocultar un botón no es la medida de seguridad: un usuario ajeno tampoco puede descargar un archivo mediante su URL. El acceso financiero de Cobranza y su historial de pagos se mantienen como antes.

## Recorrido de un caso

1. El cliente crea un caso con título, descripción, canal, categoría y prioridad. Puede incluir versión de SIABUC, sistema operativo, mensaje de error y pasos para reproducirlo.
2. Un administrativo toma el caso pendiente. La asignación genera un aviso interno; tomarlo por sí solo no cuenta como primera respuesta al cliente.
3. En Seguimiento se registran comentarios, notas internas y atenciones. Cada elemento conserva su pantalla de detalle con enlace **Volver al historial**.
4. La ficha técnica y SLA muestra plazos, primera respuesta y resolución. Consulte [Clasificación y SLA](CLASIFICACION-SLA.md).
5. Use Adjuntos del caso para evidencia general o abra un elemento del historial para adjuntarle evidencia específica. En Agenda remota se gestionan citas asociadas al caso.
6. El administrativo registra minutos manualmente o mediante cronómetro. Cierra el caso al resolverlo; los estados del caso son únicamente Abierto y Cerrado.

El cliente sigue viendo la indicación de revisar su bandeja de entrada y correo no deseado. Puede consultar la campana aunque los avisos por correo estén pausados.

## Archivos adjuntos

Abra Adjuntos del caso o el detalle de un comentario, seleccione el archivo y confirme la carga. El servidor conserva nombre, tamaño, tipo, autor, fecha y hash SHA-256. La descarga requiere una sesión autorizada. Cada carga deja un registro descriptivo de cero minutos y genera el aviso correspondiente; no descuenta saldo.

Se admiten PNG/JPEG, PDF y diagnósticos de texto UTF-8 con extensiones `.txt`, `.log`, `.csv` y `.json`. Se comprueba el contenido; cambiar la extensión no convierte un ejecutable en un archivo permitido. No se admiten ZIP, ejecutables ni HTML. Esta validación no sustituye un antivirus en los equipos que abren los archivos.

Límites: **10 MiB por archivo, 100 MiB acumulados y 100 archivos por caso**. Incluyen los archivos adjuntos a comentarios. Una carga repetida con la misma solicitud se devuelve sin duplicar el archivo. No hay borrado de adjuntos en esta versión para conservar la evidencia. Sólo se puede cargar a un caso abierto de una institución activa; las descargas históricas siguen disponibles con los permisos correspondientes.

Los adjuntos a notas internas heredan su privacidad. El cliente no puede listar, descargar ni agregar archivos a una nota interna. Un comentario de otro caso no puede usarse para vincular un archivo. Los archivos se almacenan como datos binarios en PostgreSQL, por lo que `respaldar.sh` y `respaldar.bat` incluyen su contenido. Prevea espacio adicional para base y copias conforme crezca el histórico.

## Cronómetro de atención

1. Entre como ADMIN al caso abierto y pulse Iniciar.
2. Pause cuando se interrumpa la atención y reanude para continuar. Las marcas se guardan en el servidor; recargar o cerrar el navegador no reinicia el reloj. Si lo deja corriendo seguirá acumulando tiempo.
3. Pulse Finalizar. Esto detiene el reloj y propone minutos redondeados hacia arriba: por ejemplo, 61 segundos propone 2 minutos. **Finalizar todavía no descuenta saldo**.
4. Revise minutos, canal, descripción y visibilidad. Si modifica los minutos sugeridos, indique el motivo del ajuste.
5. Confirme el registro. Spring valida el saldo del canal y guarda una única atención con su descuento. Un reintento de la misma confirmación no vuelve a cobrar.

Cada administrativo sólo puede tener un cronómetro en curso, pausado o pendiente de confirmar, incluso si cambia de caso. Sólo el propietario puede operarlo. Si el saldo es insuficiente o el caso fue cerrado, la confirmación se rechaza y el tiempo finalizado queda sin cobrar. Puede cancelar el cronómetro sin descuento o resolver la condición y confirmar después. Cerrar un caso no confirma el cronómetro automáticamente. Si una atención confirmada fue incorrecta, use la anulación de atención existente, que conserva la evidencia y devuelve los minutos.

## Agenda de sesiones remotas

El administrativo abre Agenda remota, indica fecha/hora de inicio y fin, instrucciones y responsable. Las horas introducidas se interpretan según la zona local del navegador y se guardan como instantes UTC. Los participantes deben revisar la zona y hora que muestra su equipo, especialmente si están en distintas regiones.

La propuesta queda **Propuesta** y el cliente de esa institución puede **Confirmar** o **Rechazar**, agregando una respuesta. El administrativo puede cancelar o marcar como completada una sesión confirmada. Reprogramar conserva la propuesta anterior como cancelada y crea otra que requiere nueva confirmación.

No se permiten citas superpuestas propuestas o confirmadas para el mismo administrativo; las citas de administrativos distintos pueden coincidir. La duración máxima de una cita es 12 horas. El caso debe estar abierto y la institución activa para proponer o confirmar; se puede cancelar una cita pendiente aun si el caso ya se cerró. Los cambios quedan en el historial con cero minutos y generan avisos.

La agenda no inicia una conexión remota ni descuenta automáticamente la duración reservada. Registre el tiempo realmente utilizado mediante atención manual o cronómetro. El horario de la agenda y el calendario SLA son conceptos separados; no se limita una cita al calendario SLA.

## Avisos, saldos y reportes

La campana reúne avisos y casos pendientes de respuesta. Consulte [Avisos internos](AVISOS-INTERNOS.md) para destinatarios, lectura y umbrales por institución. Pausar el correo no pausa la campana.

En Reportes, ADMIN puede consultar casos y consumos por institución, administrativo, canal y fechas, y descargar Excel/PDF. [La guía de reportes](REPORTES.md) explica las diferencias entre fecha de creación del caso y fecha de atención, los registros anulados y los límites de exportación.
