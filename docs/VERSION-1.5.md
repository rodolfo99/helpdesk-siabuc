# SIABUC Helpdesk 1.5.0: pólizas y cobranza

> **Documento histórico — 1.5.0.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.3.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.3](MANUAL-EXTENDIDO-1.8.3.md), el [índice de documentación](README.md) y las [notas de 1.8.3](VERSION-1.8.3.md).

La versión 1.5 incluye todas las funciones de 1.4 y amplía el trabajo del usuario **Cobranza (`BILLING`)**. Administración conserva acceso para supervisión. Las instituciones cliente siguen consultando su saldo y soporte; los módulos financieros están protegidos por rol.

## Qué cambia

- Pólizas vinculadas a una institución: folio, descripción, vigencia, importe contratado en pesos mexicanos y minutos de correo/remoto. Crear la póliza no acredita saldo.
- Pagos completos o parciales vinculados opcionalmente a una póliza. Registrar el pago y acreditar sus minutos es una sola transacción; una operación rechazada no deja acreditación parcial.
- Métodos: transferencia, depósito, efectivo, tarjeta de crédito, tarjeta de débito, cheque y otro. Se conservan registros anteriores con el método genérico Tarjeta.
- Historial de póliza y pagos, importes recibidos/pendientes y minutos acreditados/restantes. Repetir una solicitud de pago no acredita dos veces.
- Comprobantes de pago en PDF o imagen PNG/JPEG, con autor y fecha de carga; conservados en PostgreSQL y protegidos para BILLING/ADMIN.
- Reportes de cobranza por institución, póliza, método, estado, moneda y periodo, con Excel/PDF. Las monedas históricas permanecen separadas; no se convierten a pesos ni se suman como si fueran MXN.

Los pagos nuevos usan MXN. La vigencia de la póliza es informativa: no retira horas automáticamente al vencer. Los minutos consumidos siguen perteneciendo a las bolsas institucionales existentes. La anulación de pagos conserva el permiso ADMIN y exige suficientes minutos disponibles para retirar lo acreditado; Cobranza puede consultar su resultado histórico.

## Instalación nueva

1. Instale Docker con Compose y Python 3.10 o posterior; en Windows use Docker Desktop con contenedores Linux.
2. Descomprima el ZIP completo y abra la carpeta `siabuc-helpdesk`.
3. Linux: `chmod +x ./*.sh` y `./configurar.sh`. Windows: `configurar.bat` en CMD o `.\configurar.bat` en PowerShell.
4. Revise `.env`: puertos, contraseña PostgreSQL, administrador inicial y SMTP. `configurar.sh --password-bd` / `configurar.bat --password-bd` permiten elegir la contraseña antes de crear la base. No publique `.env`.
5. Ejecute `./iniciar.sh` o `iniciar.bat`. Se compilan e inician PostgreSQL, Postfix, Spring y Angular/Nginx; la primera ejecución requiere Internet.
6. Abra `http://127.0.0.1:4200` o su puerto, entre con las credenciales de `.env` y cambie la contraseña inicial. Administración crea las instituciones y cuentas BILLING/USER.
7. Entre como Cobranza, cree una póliza y registre un pago con las horas adquiridas. Compruebe su acreditación en el saldo institucional y el historial.

Para compilación de desarrollo Linux use `./compilar.sh`: genera `backend/target/siabuc-helpdesk-1.5.0.jar` y `frontend/dist/helpdesk/browser/`. Para imágenes Docker use `compilar.sh --docker` o `compilar.bat --docker`. Las instrucciones completas están en [Operación](OPERACION.md) y [Windows](WINDOWS.md).

## Actualizar desde una versión anterior

Detenga sesiones de desarrollo que escriban en la base y prepare una ventana de mantenimiento. Mantenga el mismo motor Docker y `COMPOSE_PROJECT_NAME` para reutilizar los volúmenes.

1. Cree una copia antes de actualizar, mientras la base está funcionando:

   ```bash
   ./respaldar.sh --nombre-respaldo backups/antes-1.5.dump
   ./detener.sh
   ```

   En Windows:

   ```bat
   respaldar.bat --nombre-respaldo backups\antes-1.5.dump
   detener.bat
   ```

2. Guarde una copia privada de `.env`, `.dump` y `.sha256` fuera de la carpeta. Reemplace el código por el ZIP completo; conserve su `.env` y los respaldos.
3. Ejecute `./iniciar.sh` o `iniciar.bat`. Flyway aplica V9/V10 y las migraciones anteriores que falten. No edite migraciones ya aplicadas ni use `docker compose down -v`.
4. Verifique instituciones, saldos, pagos anteriores y acceso de Cobranza. Los pagos existentes conservan importes, moneda, fecha, método, autor y anulaciones. Quedan sin póliza: no se les asigna un contrato inventado ni se vuelven a acreditar minutos.
5. Revise los nuevos menús de pólizas y reportes financieros, y el apartado de comprobantes en el detalle de un pago. Las cuentas y la configuración SMTP/pausa se conservan. No es necesario volver a configurar Gmail ni cambiar la salida SMTP que ya funciona.

Para recuperar la versión previa, prepare el código anterior en otra carpeta y restaure el respaldo **anterior a 1.5 en una base nueva y vacía**, conservando intacta la instalación migrada. Use otro `COMPOSE_PROJECT_NAME` y puertos libres; inicie sólo la base antes de restaurar con `--nombre-respaldo` y `--confirmar-reemplazo`. No restaure ese respaldo directamente sobre la base V9/V10: `pg_restore --clean` no elimina las tablas nuevas que el archivo antiguo desconoce. El código anterior debe apuntar a la base recuperada, que no contendrá operaciones posteriores a la copia. Consulte el [procedimiento de retroceso](RESPALDO-RESTAURACION.md#retroceder-a-una-versión-anterior).

## Uso y alcance

Consulte [Pólizas y cobranza](POLIZAS-COBRANZA.md) para reglas de parcialidades, horas, cancelaciones e historial, y [Reportes financieros](REPORTES-COBRANZA.md) para totales y fechas. Los registros documentan pagos recibidos; el sistema no realiza cargos bancarios ni emite facturas fiscales. La evidencia de las pruebas de esta entrega está en [Validación](VALIDACION.md).
