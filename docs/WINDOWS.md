# Instalación y operación en Windows

## Alcance

Los `.bat` permiten operar desde CMD o PowerShell con Python en Windows. Docker Desktop ejecuta PostgreSQL, Spring, Angular/nginx, Postfix opcional y el worker de respaldos como contenedores **Linux**. No se requiere Git Bash ni instalar Java, Maven o Node en el anfitrión. No basta con renombrar `.sh` a `.bat`: el proyecto conserva su lógica común en Python y usa lanzadores específicos por sistema.

El desarrollo nativo (`iniciar-dev.sh`, Java/Angular fuera de Docker) se mantiene para Linux/WSL2. No se proporciona un lanzador de desarrollo nativo de Windows; `compilar.bat` compila dentro de Docker. Los comandos Docker siguen usando la misma configuración y volúmenes del proyecto.

## Instalación en orden

1. Instale Docker Desktop en una edición y versión de Windows admitida por Docker. Active WSL2 y la virtualización si el instalador lo solicita. Reinicie si es necesario.
2. Abra Docker Desktop y espere a que arranque. Use contenedores Linux; si muestra la opción **Switch to Linux containers**, selecciónela.
3. Instale Python 3.10 o posterior para Windows, con `py` o `python` disponible en PATH. Abra una terminal nueva después de instalarlo.
4. Descomprima el ZIP completo, por ejemplo en `C:\SIABUC\helpdesk`. No ejecute scripts dentro de la vista del ZIP. Evite carpetas públicas o sincronizadas porque `.env` y los respaldos contienen información privada.
5. Abra PowerShell y entre en la carpeta descomprimida:

```powershell
cd "C:\SIABUC\helpdesk"
docker info --format '{{.OSType}}'
docker compose version
.\configurar.bat
notepad .env
```

El primer comando Docker debe responder `linux`. En `.env` solo FRONTEND_PORT se publica en el despliegue Docker normal. WEB_BIND_HOST controla la interfaz web (127.0.0.1 por defecto, o la IPv4 LAN real). PostgreSQL y Spring no publican puertos. DB_PORT y SERVER_PORT se reservan para desarrollo nativo y mantenimiento. Al guardar con un editor, conserve UTF-8 sin BOM y no cambie el nombre a `.env.txt`.

6. Para elegir la contraseña de una **base nueva**, use ` .\configurar.bat --password-bd`. Cambiar DB_PASSWORD en `.env` no cambia la contraseña de una base ya inicializada. Con una instalación existente conserve sus credenciales.
7. Inicie todo; la primera ejecución descarga imágenes y dependencias y compila:

```powershell
.\iniciar.bat
```

8. Abra http://127.0.0.1:4200 localmente o, después de configurar WEB_BIND_HOST con su IPv4 LAN y permitir solo FRONTEND_PORT en el firewall de Windows, http://IP-LAN:4200 desde otro equipo autorizado. Para acceso HTTPS consulte OPERACION.md. No abra 5432 ni 8080. El usuario inicial y su contraseña están en ADMIN_USERNAME y ADMIN_PASSWORD de `.env`. Las cuentas cliente se crean desde Usuarios con rol USER e institución; consulte MANUAL-USUARIO.md.

Use las terminales para conservar los mensajes de error; no dependa del doble clic. Los `.bat` localizan Python 3.10+ y funcionan desde otra carpeta usando su ruta completa. Si se invocan desde otro archivo batch, use `call` según las reglas normales de CMD.

## Comandos

| Operación | PowerShell |
|---|---|
| Validar/crear configuración | `.\configurar.bat` |
| Configurar SMTP local | `.\configurar.bat --smtp-interno` |
| Iniciar todo y compilar cambios | `.\iniciar.bat` |
| Iniciar sólo PostgreSQL | `.\iniciar.bat --solo-bd` |
| Compilar imágenes | `.\compilar.bat` |
| Detener conservando volúmenes | `.\detener.bat` |
| Respaldar con nombre automático | `.\respaldar.bat` |
| Respaldar con nombre/ruta | `.\respaldar.bat --nombre-respaldo "C:\Copias SIABUC\lunes.dump"` |
| Restaurar reemplazando datos | `.\restaurar.bat --nombre-respaldo "C:\Copias SIABUC\lunes.dump" --confirmar-reemplazo` |

Los nombres relativos de respaldo manual se resuelven desde la carpeta actual de la terminal. Se escribe el respaldo binario directamente desde Python, sin redirecciones de PowerShell. Conserve `.dump` y `.dump.sha256`; no se sobrescribe un respaldo existente. Restaurar reemplaza los datos actuales de forma transaccional y deja detenidos el worker `backup`, la aplicación y SMTP. Revise la configuración recuperada antes de reiniciar; [RESPALDO-RESTAURACION.md](RESPALDO-RESTAURACION.md) explica cómo arrancar la aplicación primero con el worker detenido.

## Respaldos automáticos en Windows

El calendario se instala **desactivado por defecto**, también al actualizar desde una versión anterior. `iniciar.bat` puede dejar saludable el servicio `backup` sin crear ninguna copia programada. Los respaldos manuales de `respaldar.bat` funcionan independientemente de ese calendario y nunca se eliminan por su retención.

Para elegir una carpeta privada diferente, edite `.env` con una sola definición de cada variable:

```dotenv
BACKUP_DIRECTORY='C:/SIABUC/copias automaticas'
BACKUP_UID=0
BACKUP_GID=0
```

Use barras `/` en este ejemplo y comillas simples del formato `.env` si hay espacios. La ruta predeterminada `./backups/automatic` parte del directorio del proyecto, aunque invoque `iniciar.bat` desde otra carpeta. El script crea la carpeta si falta; Docker Desktop debe poder montarla. Configure sus permisos mediante **Propiedades → Seguridad** y no utilice una carpeta compartida públicamente.

Ejecute `iniciar.bat`, acceda como ADMIN a **Respaldos**, compruebe el destino y guarde hora, zona, retención y confirmación del directorio. **Guardar no activa el calendario**: pulse **Activar respaldos automáticos** cuando lo decida. Puede volver a **Desactivar respaldos automáticos**; conserva copias e historial, cancela los programados pendientes y permite seguir usando **Crear respaldo ahora**. Una ejecución ya iniciada puede completar. Al reactivar no se recuperan horarios perdidos.

El archivo `backup/compose.restore-test.yml` permite ensayar una restauración en otro proyecto y volumen, sin SMTP ni puertos publicados. La guía [RESPALDOS-AUTOMATICOS.md](RESPALDOS-AUTOMATICOS.md) contiene los pasos para PowerShell. Ningún ensayo de restauración se ejecuta automáticamente sobre sus datos.

## SMTP y datos de otra instalación

En 1.8.2 Gmail directo está preseleccionado, con el envío inicialmente desactivado; las instalaciones previas pueden conservar Postfix. El usuario confirmó su funcionamiento en su equipo Linux el 13/10/2026; eso no verifica aún el envío desde Windows. La entrega directa requiere conectividad saliente TCP 25 y depende de las políticas de la red y de los destinatarios.

Gmail es opcional mediante `configurar-gmail.bat`; **el usuario informó que su configuración Gmail falló**, y no se presenta como solucionada por este cambio. No ejecute ese asistente si desea conservar el SMTP local que funciona.

Para trasladar datos de Linux a Windows, cree un respaldo con respaldar.sh y copie `.dump`, `.dump.sha256` y `.env` por un medio privado. Ejecute configurar.bat para validar `.env`, inicie sólo la BD, restaure con restaurar.bat y luego inicie todo. Conservar el nombre del proyecto no traslada volúmenes entre equipos ni entre motores Docker diferentes. El respaldo de PostgreSQL no incluye la cola Postfix pendiente ni la configuración `.env`; no apague definitivamente el origen antes de gestionar esa cola.

## Permisos y diagnóstico

En Linux se utilizan modos 600 para archivos privados. `chmod(600)` de Python **no configura una ACL privada equivalente en Windows**. Use una carpeta de su perfil y controle el acceso mediante Propiedades → Seguridad. No publique `.env`, respaldos ni la contraseña de aplicación. Docker Desktop debe tener acceso a la carpeta del proyecto.

```powershell
docker compose ps -a
docker compose logs --tail=150 backend
docker compose logs --tail=100 smtp
docker compose logs --tail=100 backup
docker compose exec -T smtp postqueue -p
```

Si el backend aparece unhealthy, conserve el registro: no elimine volúmenes ni use `docker compose down -v` como reparación. Las mismas restricciones de puertos y correo aplican en Windows. No desactive el firewall completo.

## Validación y fuentes

Se verifican mediante pruebas automatizadas la conservación de datos binarios de respaldo, las rutas con espacios, la selección del motor Linux y el bloqueo temprano de desarrollo nativo Windows. Las ramas Windows se simulan en las pruebas de Python ejecutadas en Linux. No se ha ejecutado CMD, Docker Desktop ni el sistema completo en una máquina Windows durante este cambio; la validación real queda pendiente.

- Docker Desktop Windows: https://docs.docker.com/desktop/setup/install/windows-install/
- Integración WSL2: https://docs.docker.com/desktop/features/wsl/

## Gmail directo con Helpdesk 1.8.2

En CMD o PowerShell use `configurar.bat --gmail-directo`: ingrese la cuenta de Google y una contraseña de aplicación nueva. El SMTP directo no necesita iniciar el contenedor Postfix. Si `.env` contiene `SMTP_HOST=smtp`, se conserva Postfix automáticamente. Revise antes su cola sin borrar volúmenes. [Detalles](VERSION-1.8.2.md).
