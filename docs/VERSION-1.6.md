# SIABUC Helpdesk 1.6.0: contratación, portal y operación

La versión 1.6 incluye las funciones de 1.5 y completa ocho mejoras: cotizaciones, solicitudes de pago por validar, renovación de pólizas, portal financiero institucional, recibos PDF, base de conocimientos, encuestas y respaldos automáticos.

**Los respaldos automáticos están desactivados por defecto.** Instalar 1.6 o migrar desde 1.5 no activa la programación. Administración debe configurar el destino y horario y activarla expresamente. El respaldo manual continúa disponible.

## Funciones y permisos

| Función | Uso |
| --- | --- |
| Cotizaciones | Administración/Cobranza preparan la propuesta en MXN, registran su emisión y la aceptación o rechazo del cliente y convierten la aceptada en póliza una sola vez. No acredita horas. |
| Pagos por validar | Se conserva la solicitud con comprobante. Cobranza/Administración aprueban los valores mostrados o rechazan con motivo; sólo la aprobación registra el pago y acredita sus minutos. |
| Renovación de pólizas | Próximos vencimientos, avisos y relación entre póliza anterior y nueva. La renovación no elimina la anterior ni crea saldo por sí sola. |
| Portal financiero del cliente | Administración lo habilita por institución. El cliente consulta sólo sus datos financieros y presenta solicitudes con evidencia. La habilitación está desactivada por defecto. |
| Recibos PDF | Administración/Cobranza y clientes autorizados descargan un recibo con folio, importe, método y minutos. La versión administrativa incluye sus datos internos; la del cliente los omite. Se conserva su instantánea; una anulación posterior se identifica al descargar. No es una factura fiscal. |
| Base de conocimientos | Administración prepara, publica o archiva artículos por categoría y versión de SIABUC y los vincula a casos. Los clientes sólo consultan artículos publicados. |
| Encuestas | Un usuario de la institución evalúa un caso cerrado, de 1 a 5, con comentario opcional. Se admite una respuesta institucional por caso; Administración consulta muestras y resultados. |
| Respaldos programados | Administración configura horario, zona y retención; consulta ejecuciones y fallos y puede desactivar la programación. Desactivarla conserva las copias existentes. |

La emisión y aceptación de cotizaciones se documentan con una constancia administrativa: no se simula una firma electrónica ni se afirma que el sistema haya enviado un correo. Los pagos directos de 1.5 siguen disponibles para dinero ya verificado; el circuito de solicitud permite revisar evidencia antes de acreditar.

## Recorrido de cotización a soporte

1. Administración crea la institución y las cuentas. Si la compra se registrará mediante pagos, no asigne también las mismas horas manualmente.
2. Cobranza prepara una cotización con importe, horas de correo/remoto, vigencia propuesta y vencimiento de la oferta.
3. Registra la emisión y la respuesta del cliente con su constancia. Convierte la aceptada en póliza. El saldo institucional permanece igual.
4. Si desea que el cliente consulte finanzas o presente comprobantes, Administración habilita el acceso de su institución en la configuración del portal financiero.
5. El cliente o Cobranza presenta la solicitud de pago con sus datos y evidencia. Mientras esté pendiente, no hay acreditación.
6. Cobranza comprueba el documento, importe y minutos solicitados. Aprueba para registrar el pago y el crédito conjuntamente, o rechaza indicando la razón. Una corrección requiere otra solicitud; la anterior conserva su resultado.
7. Consulte el pago y descargue el recibo PDF. Los comprobantes aportados por el cliente y el recibo generado son documentos diferentes.
8. Con saldo disponible, el cliente abre su caso. El administrativo lo atiende y puede vincular un artículo de la base de conocimientos. Al cerrar, la institución puede responder la encuesta.

Las bolsas de correo y remoto siguen siendo independientes. El importe no se convierte automáticamente a minutos; se acredita el tiempo confirmado de cada movimiento, respetando los límites contractuales y la protección contra duplicados.

## Instalación nueva: Linux y Windows

1. Prepare Docker con Compose y Python 3.10 o posterior. En Windows use Docker Desktop con contenedores Linux. Java, Maven y Node se compilan dentro de las imágenes.
2. Descomprima el ZIP completo y abra una terminal en la carpeta `siabuc-helpdesk`.
3. Linux: ejecute `chmod +x ./*.sh` y `./configurar.sh`. PowerShell: ejecute `.\configurar.bat`.
4. Revise `.env`: `DB_PORT`, `SERVER_PORT`, `FRONTEND_PORT`, credenciales y SMTP. Para elegir la contraseña de una base nueva use `./configurar.sh --password-bd` o `.\configurar.bat --password-bd`.
5. Inicie con `./iniciar.sh` o `.\iniciar.bat`. El primer inicio descarga dependencias, compila y aplica las migraciones.
6. Abra `http://127.0.0.1:4200` o el puerto configurado. Identifíquese con `ADMIN_USERNAME` y `ADMIN_PASSWORD` de `.env` y cambie la contraseña inicial.
7. Cree las cuentas de Cobranza y clientes. Habilite únicamente los accesos financieros que corresponda conceder a cada institución.
8. Los respaldos automáticos permanecen apagados. En **Respaldos**, configure y confirme el destino, el horario y la retención si desea utilizarlos; luego active la programación. También puede dejarlos apagados y usar `respaldar.sh` / `respaldar.bat`.

Consulte [Windows](WINDOWS.md) y [Operación](OPERACION.md) para requisitos, puertos, compilación y diagnóstico. El modo de desarrollo nativo de Spring/Angular sigue disponible en Linux/WSL2 con `./compilar.sh` y `./iniciar-dev.sh`; el JAR de esta versión es `backend/target/siabuc-helpdesk-1.6.0.jar`.

## Actualizar conservando la instalación

1. Suspenda escrituras durante una ventana de mantenimiento y cree un respaldo con la versión instalada:

   ```bash
   ./respaldar.sh --nombre-respaldo backups/antes-1.6.dump
   ./detener.sh
   ```

   En PowerShell:

   ```powershell
   .\respaldar.bat --nombre-respaldo backups/antes-1.6.dump
   .\detener.bat
   ```

2. Conserve `.env`, `.dump` y `.sha256` en una ubicación privada fuera de la carpeta del proyecto. Reemplace el código por el paquete nuevo, conservando su configuración y respaldos.
3. Mantenga el mismo motor Docker y `COMPOSE_PROJECT_NAME` para reutilizar el volumen existente. No use `docker compose down -v`.
4. Ejecute `./iniciar.sh` o `.\iniciar.bat`. Flyway aplica V11–V16, además de las migraciones anteriores que falten. No edite migraciones ya aplicadas.
5. Verifique instituciones, cuentas, casos, saldos, pagos, pólizas y comprobantes existentes. No se convierten pagos anteriores en solicitudes pendientes ni se vuelven a acreditar sus horas.
6. Compruebe que el portal financiero institucional y los respaldos automáticos permanecen desactivados inicialmente. La pausa y configuración de correo existentes se conservan; no es necesario cambiar un SMTP que ya funciona.
7. Configure las nuevas funciones que vaya a utilizar. Pruebe una restauración en una instalación aislada antes de depender de la política automática de copias.

Para volver a una versión anterior, restaure su respaldo anterior en una **base nueva y vacía** con el código correspondiente. No intente restaurarlo directamente sobre el esquema V11–V16: `pg_restore --clean` sólo conoce los objetos incluidos en esa copia. Preserve la instalación migrada y siga [Retroceder a una versión anterior](RESPALDO-RESTAURACION.md#retroceder-a-una-versión-anterior).

## Alcance de la entrega

El sistema registra operaciones administrativas; no realiza cargos bancarios, validación bancaria automática, facturación fiscal ni firma electrónica. El correo y la campana conservan sus permisos y reglas de pausa. Las funciones financieras del cliente dependen de la habilitación de su institución y no conceden acceso a otras instituciones ni a notas internas de soporte.

La existencia de scripts Linux/Windows y de servicios Docker no sustituye su comprobación en el entorno destino. Consulte [Validación](VALIDACION.md) para distinguir pruebas ejecutadas, simulaciones y comprobaciones pendientes.
