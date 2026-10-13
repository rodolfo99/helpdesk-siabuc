# Desactivar el envío de correos · versión 1.3.1

## Desde la aplicación

1. Inicie sesión como **ADMIN** y entre en **Correo y notificaciones**.
2. Pulse el control para desactivar/pausar el envío y confirme. Los avisos pendientes, fallidos u omitidos que todavía pudieran reintentarse pasan a **No enviado por desactivación**.
3. Puede seguir registrando instituciones, casos, atenciones y pagos. Los avisos generados durante la desactivación quedan en el historial con ese mismo estado, sin intentar entregarlos.
4. Al reactivar, **sólo se enviarán avisos de eventos nuevos**. Los anteriores no se reenvían automáticamente ni admiten reintento manual.

No se eliminan notificaciones ni registros de soporte. El estado SUPPRESSED permite distinguir avisos que no se enviarán por esta decisión administrativa. Mientras está pausado, las pruebas de correo y los reintentos manuales se bloquean. Un aviso ya enviado no cambia su historial.

El control es global para la instalación, exclusivo de ADMIN, queda auditado y permanece en PostgreSQL después de reiniciar o reconstruir contenedores. Funciona desde el navegador en Windows y Linux. Los roles USER y BILLING no pueden cambiarlo. Desactivar repetidamente no duplica la auditoría si el estado no cambió.

## Límite del servidor

`MAIL_ENABLED=false` en `.env` bloquea los envíos aunque el control administrativo esté activado. La aplicación no puede superar ese límite. Cambiar la pausa desde la interfaz no necesita reiniciar; modificar `.env` sí requiere recrear el backend.

```dotenv
MAIL_ENABLED=false
```

Windows, dentro de la carpeta del proyecto:

```powershell
docker compose up -d --no-deps --force-recreate backend
```

Linux, si Docker requiere sudo:

```bash
sudo docker compose up -d --no-deps --force-recreate backend
```

**Son dos controles distintos:** MAIL_ENABLED=false por sí solo conserva pendientes para un envío posterior, como en versiones anteriores. Para que los avisos del intervalo no se envíen al volver a habilitar, utilice la desactivación administrativa de esta versión. Volver a MAIL_ENABLED=true no elimina la selección administrativa guardada ni habilita los avisos SUPPRESSED. La configuración SMTP, remitente y credenciales se conservan.

## Mensajes en curso y Postfix

La desactivación impide nuevas reclamaciones de envío. Un mensaje que ya estuviera en curso puede terminar de entregarse. Si falla y aún podría reintentarse, el sistema conserva la frontera de desactivación y lo marca SUPPRESSED: no reaparece después de reactivar.

Si Postfix ya aceptó el mensaje, éste pertenece a su propia cola y puede seguir entregándose. Esta opción no elimina ni cancela mensajes ya aceptados por SMTP. Si necesita detener también los intentos del SMTP interno, puede usar `docker compose stop smtp` (con sudo cuando corresponda). Eso conserva su cola y volverá a procesarla al iniciarlo; no equivale a suprimir los avisos de la aplicación. Un mensaje aceptado por un servidor externo no puede recuperarse con estos controles.

## Actualización y restauración

Conserve `.env` y un respaldo. Instale los fuentes 1.3.1 y ejecute iniciar.bat en Windows o iniciar.sh en Linux. Flyway añade V4 sin modificar V1/V2/V3 ni eliminar los volúmenes.

V4 comienza con envío administrativo activado, respetando MAIL_ENABLED y el comportamiento anterior. Si necesita evitar entregas durante la actualización, establezca MAIL_ENABLED=false antes del arranque y después desactive el envío en la interfaz para suprimir los avisos previos. La pausa, su frontera de avisos y los estados SUPPRESSED se incluyen en el respaldo PostgreSQL.

Restaure un respaldo anterior a V4 en una base nueva: pg_restore --clean no elimina tablas que no existen en el archivo antiguo. El siguiente arranque aplica V4 inicialmente sin pausa; revise MAIL_ENABLED antes de arrancar. Un respaldo con V4 restaura también su selección administrativa. El respaldo de BD no contiene la cola Postfix ni `.env`.
