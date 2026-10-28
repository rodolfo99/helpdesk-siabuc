# SIABUC Helpdesk 1.8.4: permisos de respaldos durante el arranque

> **Documento histórico.** Conserva los procedimientos y resultados de la versión indicada en el título.
> Para uso, instalación y validación vigentes, consulte el [manual integrado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), las [notas 1.8.5](VERSION-1.8.5.md) y el [índice](README.md).

Esta revisión conserva las licencias y V27 e incorpora una corrección de operación.
No añade migraciones de base de datos ni cambia el proveedor de correo.

## Problema corregido

Si una carpeta de respaldos quedaba con propietario `root:root` y permisos `0700`,
el servicio configurado como `1000:1000` no podía escribir. Esto podía ocurrir al
crear la carpeta con `sudo` conservando el `.env` de una instalación anterior.
La aplicación arrancaba y el error se descubría después en la página de respaldos.

## Comportamiento nuevo

`bash iniciar.sh` (o `sudo bash iniciar.sh` si se necesita acceso a Docker):

1. Conserva y valida el `.env` existente.
2. Crea el destino si falta y construye la imagen de respaldos.
3. Prueba escritura con el UID/GID normal de `backup` en su montaje real.
4. Sólo ante acceso denegado en Linux, ajusta la carpeta a `BACKUP_UID:BACKUP_GID`
   y asegura acceso del propietario. No cambia los archivos contenidos.
5. Repite la prueba como el worker y continúa el arranque sólo si funciona.

La comprobación no inicia PostgreSQL, Spring ni SMTP. No concede nuevas
capacidades permanentes al worker. Si el destino ya permite escribir, no cambia
su propietario ni sus permisos. Los errores de espacio, disco de sólo lectura,
ACL o montaje que no se puedan resolver detienen el arranque con un diagnóstico.
En Windows se verifica escritura y se informa sobre permisos de Docker Desktop;
no se aplica la reparación POSIX. Windows nativo y Docker rootless requieren
validación específica en el entorno de destino.

## Actualizar una instalación

Respalde y ensaye primero en una copia. Conserve `.env`, `COMPOSE_PROJECT_NAME`,
credenciales PostgreSQL y los volúmenes. Durante el mantenimiento, detenga la
instalación anterior sin borrar volúmenes, coloque los fuentes nuevos y ejecute
`sudo bash iniciar.sh` si su usuario requiere `sudo` para Docker.

Si descomprime en otra carpeta, una ruta relativa como `./backups/automatic`
cambia de ubicación. Para seguir usando las copias anteriores, configure
`BACKUP_DIRECTORY` con la ruta absoluta de su carpeta exclusiva existente.
El script no mueve copias anteriores automáticamente.

El calendario conserva su estado. Si cambió el destino, confirme la nueva
carpeta en la plataforma, guarde la configuración y solicite **Crear respaldo
ahora**. El aviso de un error anterior se limpia tras una copia completada.

Conserve el correo según su uso: `MAIL_ENABLED=false` pausa envíos, mientras que
`SMTP_HOST=smtp` activa Postfix al arrancar. Para no iniciar Postfix, deje
`SMTP_HOST` vacío con correo desactivado, o indique su servidor externo.
La red SMTP dedicada sigue eliminada; se utiliza la red común de Compose.

## Validación reproducible

```bash
python3 -m unittest discover -s tests/ops
python3 -m unittest discover -s tests/smtp
sudo python3 -m unittest tests.ops.test_backup_permissions -v
sudo python3 tests/ops/backup_permissions_smoke.py
```

La última prueba usa un proyecto Docker temporal, reproduce `root:0700` frente
a `1000:1000`, verifica la reparación y un segundo arranque sin reparación,
y conserva una copia de prueba con sus bytes, dueño y permisos originales.
No arranca una base de datos, no envía correo ni sustituye una prueba de
restauración. CI ejecuta este escenario y publica el resultado de cada commit.
Los resultados históricos de 1.8.3 conservan su alcance original.
