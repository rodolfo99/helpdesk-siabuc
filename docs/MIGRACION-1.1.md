# Actualizar de 1.0 a 1.1

> **Documento histórico — migración de 1.0 a 1.1 y notas posteriores de 1.3.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.5.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), el [índice de documentación](README.md) y las [notas de 1.8.5](VERSION-1.8.5.md).

La versión 1.1 añade cobranza, acreditación por pagos, asignación de responsables y notificaciones SMTP. Conserva instituciones, usuarios, casos, saldos, historiales y auditoría existentes. **No cambie ni elimine `V1__initial_schema.sql`**: Flyway aplica las nuevas migraciones V2 y V3 al iniciar el servidor actualizado.

**Corrección documental en 1.8.3:** este apartado contenía una referencia inconsistente a «Entrega actual 1.3» junto con el JAR `siabuc-helpdesk-1.6.0.jar`. Esa combinación no identifica una entrega válida y no debe utilizarse para seleccionar código, JAR o migraciones. Esta guía conserva la incorporación original del esquema 1.1; para actualizar una instalación consulte el [manual vigente de 1.8.3](MANUAL-EXTENDIDO-1.8.3.md) y sus [notas de versión](VERSION-1.8.3.md).

## Procedimiento con Docker

1. Programe una ventana de mantenimiento y detenga cualquier proceso nativo de la aplicación. Con la base todavía disponible, ejecute `./respaldar.sh` desde la instalación 1.0. Copie el respaldo y su SHA-256 a una ubicación segura. Preserve también `.env` por separado.
2. Ejecute `./detener.sh`. Conserva el volumen PostgreSQL. No utilice `down -v`.
3. Sustituya los archivos de código y scripts por los de la entrega 1.1. Conserve el `.env` propio, los respaldos y el mismo `COMPOSE_PROJECT_NAME`. No sustituya `.env` por `.env.example` ni mezcle manualmente migraciones antiguas con otras editadas.
4. Ejecute `./configurar.sh`. Un `.env` 1.0 sin variables nuevas sigue siendo válido: el correo queda desactivado por defecto. El asistente no cambia la contraseña PostgreSQL ya configurada.
5. Ejecute `./iniciar.sh`. Compila el JAR `siabuc-helpdesk-1.1.0.jar`, reconstruye Angular y aplica V2/V3 sobre la base existente.
6. Revise el estado de los servicios, acceda con una cuenta existente y compruebe instituciones, saldos y casos anteriores antes de reanudar la operación.

No se necesita un script destructivo de recreación de tablas. Para una instalación completamente nativa, el procedimiento equivalente es respaldar, detener Spring, actualizar/compilar, conservar las variables de conexión y ejecutar el JAR 1.1. PostgreSQL y Flyway gestionan las migraciones al arrancar.

## Configuración funcional posterior

- **Usuarios:** capture los correos de las cuentas administrativas y de cobranza que deban recibir avisos. `ADMIN_EMAIL` sólo se usa en una cuenta de arranque nueva; no actualiza cuentas ya existentes.
- **Instituciones:** compruebe el correo cliente y elija su responsable administrativo predeterminado.
- **Casos existentes:** inicialmente pueden quedar sin responsable. Asígnelos desde su edición. Cambiar el responsable institucional no reasigna automáticamente cada caso anterior.
- **Cobranza:** cree cuentas con rol `BILLING` cuando corresponda. El rol y el ámbito de una cuenta previa siguen siendo inmutables: cree otra cuenta para un ámbito distinto y desactive la anterior.
- **Pagos:** los saldos asignados antes de 1.1 se conservan; no se inventan pagos para explicar ajustes históricos. Los pagos nuevos generan su propio registro y acreditación.
- **Correo:** siga [CORREO.md](CORREO.md). Pruebe con Mailpit en una base separada antes de activar un SMTP real. Activar correo procesa la cola pendiente.

## Revisión de aceptación

Compruebe que las cuentas previas acceden con sus contraseñas, los saldos anteriores coinciden, se conserva el historial y una institución sigue aislada de las demás. En un entorno de pruebas, registre un pago y verifique la acreditación de ambos canales, su anulación permitida y el rechazo si ya no queda saldo suficiente para retirar lo acreditado. Confirme que `BILLING` no accede a casos, usuarios, auditoría o notificaciones.

Revise también asignación de responsables y avisos públicos/internos según [CORREO.md](CORREO.md). Este recorrido es una guía para su instalación; los resultados comprobados al preparar la entrega se documentan en [VALIDACION.md](VALIDACION.md).

## Restauración de una copia

Para volver a un estado anterior, detenga la aplicación, conserve una copia del estado 1.1 y use `restaurar.sh` con el respaldo adecuado y `--confirmar-reemplazo`. Mantenga juntos una versión del código y una copia de base compatibles. Arrancar de nuevo el código 1.1 sobre una copia 1.0 volverá a aplicar V2/V3; para recuperar íntegramente una instalación 1.0 debe restaurar también su código y configuración correspondiente.

La cola de notificaciones forma parte de los respaldos 1.1. Tras restaurar, mantenga `MAIL_ENABLED=false` hasta revisar los pendientes para evitar reenvíos no previstos. Las migraciones no eliminan V1 ni el historial de Flyway y los scripts de actualización no borran los volúmenes.

La versión 1.3 añade operación en Windows con Docker Desktop, sin migraciones de base nuevas. Consulte [Versión 1.3](VERSION-1.3.md) y [Windows](WINDOWS.md).

**Actualización 1.3.1:** añade V4 para la pausa persistente de correo; no modifique V1/V2/V3. V4 comienza sin pausa, respetando MAIL_ENABLED. Consulte [Desactivar correo](DESACTIVAR-CORREO.md), incluidos los cuidados para restaurar respaldos anteriores a V4.
