# Instalación y operación en Windows

## Alcance

Los `.bat` permiten operar desde CMD o PowerShell con Python en Windows. Docker Desktop ejecuta PostgreSQL, Spring, Angular/nginx y Postfix como contenedores **Linux**. No se requiere Git Bash ni instalar Java, Maven o Node en el anfitrión. No basta con renombrar `.sh` a `.bat`: el proyecto conserva su lógica común en Python y usa lanzadores específicos por sistema.

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

El primer comando Docker debe responder `linux`. En `.env` configure los puertos publicados mediante DB_PORT, SERVER_PORT y FRONTEND_PORT. Si 8080 ya está ocupado, use SERVER_PORT=8081. Al guardar con un editor, conserve UTF-8 sin BOM y no cambie el nombre a `.env.txt`.

6. Para elegir la contraseña de una **base nueva**, use ` .\configurar.bat --password-bd`. Cambiar DB_PASSWORD en `.env` no cambia la contraseña de una base ya inicializada. Con una instalación existente conserve sus credenciales.
7. Inicie todo; la primera ejecución descarga imágenes y dependencias y compila:

```powershell
.\iniciar.bat
```

8. Abra http://127.0.0.1:4200 (o FRONTEND_PORT configurado). El usuario inicial y su contraseña están en ADMIN_USERNAME y ADMIN_PASSWORD de `.env`. Las cuentas cliente se crean desde Usuarios con rol USER e institución; consulte MANUAL-USUARIO.md.

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

Los nombres relativos de respaldo se resuelven desde la carpeta actual de la terminal. Se escribe el respaldo binario directamente desde Python, sin redirecciones de PowerShell. Conserve `.dump` y `.dump.sha256`; no se sobrescribe un respaldo existente. Restaurar reemplaza los datos actuales de forma transaccional y deja los servicios de aplicación detenidos; después ejecute iniciar.bat.

## SMTP y datos de otra instalación

La configuración predeterminada sigue siendo Postfix local. El usuario confirmó su funcionamiento en su equipo Linux el 13/10/2026; eso no verifica aún el envío desde Windows. La entrega directa requiere conectividad saliente TCP 25 y depende de las políticas de la red y de los destinatarios.

Gmail es opcional mediante `configurar-gmail.bat`; **el usuario informó que su configuración Gmail falló**, y no se presenta como solucionada por este cambio. No ejecute ese asistente si desea conservar el SMTP local que funciona.

Para trasladar datos de Linux a Windows, cree un respaldo con respaldar.sh y copie `.dump`, `.dump.sha256` y `.env` por un medio privado. Ejecute configurar.bat para validar `.env`, inicie sólo la BD, restaure con restaurar.bat y luego inicie todo. Conservar el nombre del proyecto no traslada volúmenes entre equipos ni entre motores Docker diferentes. El respaldo de PostgreSQL no incluye la cola Postfix pendiente ni la configuración `.env`; no apague definitivamente el origen antes de gestionar esa cola.

## Permisos y diagnóstico

En Linux se utilizan modos 600 para archivos privados. `chmod(600)` de Python **no configura una ACL privada equivalente en Windows**. Use una carpeta de su perfil y controle el acceso mediante Propiedades → Seguridad. No publique `.env`, respaldos ni la contraseña de aplicación. Docker Desktop debe tener acceso a la carpeta del proyecto.

```powershell
docker compose ps -a
docker compose logs --tail=150 backend
docker compose logs --tail=100 smtp
docker compose exec -T smtp postqueue -p
```

Si el backend aparece unhealthy, conserve el registro: no elimine volúmenes ni use `docker compose down -v` como reparación. Las mismas restricciones de puertos y correo aplican en Windows. No desactive el firewall completo.

## Validación y fuentes

Se verifican mediante pruebas automatizadas la conservación de datos binarios de respaldo, las rutas con espacios, la selección del motor Linux y el bloqueo temprano de desarrollo nativo Windows. Las ramas Windows se simulan en las pruebas de Python ejecutadas en Linux. No se ha ejecutado CMD, Docker Desktop ni el sistema completo en una máquina Windows durante este cambio; la validación real queda pendiente.

- Docker Desktop Windows: https://docs.docker.com/desktop/setup/install/windows-install/
- Integración WSL2: https://docs.docker.com/desktop/features/wsl/
