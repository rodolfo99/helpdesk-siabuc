# SIABUC Helpdesk 1.9.1 para Windows

**Portales de acceso:** clientes en `/poliza`; la cuenta inicial y el personal interno ingresan en `/poliza/admin`, con el usuario y la contraseña configurados en el sistema. La raíz `/` redirige al portal de clientes.

El paquete conjunto `SIABUC-Helpdesk-1.9.1.zip` incluye el proyecto completo con archivos `.sh` para Linux y `.bat` para CMD y PowerShell en la misma carpeta. En Windows use los `.bat`; no necesita ejecutar los `.sh` ni instalar Git Bash. Requiere **Python 3.10 o posterior** y **Docker Desktop iniciado con contenedores Linux**. Java, Maven y Node se ejecutan dentro de Docker. La edición opcional `SIABUC-Helpdesk-1.9.1-Windows.zip` conserva solo los lanzadores `.bat`.

## Primer inicio

1. Extraiga todo el ZIP, por ejemplo en `C:\SIABUC\helpdesk`.
2. Abra Docker Desktop y espere a que esté listo.
3. Abra PowerShell en la carpeta que contiene `iniciar.bat` y ejecute:

```powershell
.\configurar.bat
.\iniciar.bat
```

El primer inicio descarga dependencias y compila los servicios. Al terminar, abra **http://127.0.0.1:4200** (o el puerto `FRONTEND_PORT` de su configuración).

Abra `.env` con `notepad .env`: el usuario inicial figura en `ADMIN_USERNAME` y su contraseña en `ADMIN_PASSWORD`. Cambie la contraseña al entrar. El correo y el calendario de respaldos automáticos comienzan desactivados en instalaciones nuevas.

Use PowerShell o CMD para mantener visibles los mensajes. En CMD puede omitir el prefijo `.\`. Si llama un `.bat` desde otro archivo batch, use `call`. Los scripts aceptan rutas con espacios entre comillas y localizan el proyecto aunque se invoquen desde otra carpeta.

## Archivos equivalentes

| Archivo Linux | Archivo Windows | Función en Windows |
| --- | --- | --- |
| `configurar.sh` | `configurar.bat` | Crear o validar `.env`; admite los mismos parámetros. |
| `iniciar.sh` | `iniciar.bat` | Compilar e iniciar el sistema completo con Docker. |
| `detener.sh` | `detener.bat` | Detener el sistema y conservar los volúmenes. |
| `compilar.sh` | `compilar.bat` | Compilar las imágenes Docker. |
| `respaldar.sh` | `respaldar.bat` | Crear un respaldo y su archivo de comprobación. |
| `restaurar.sh` | `restaurar.bat` | Restaurar un respaldo con confirmación explícita. |
| `configurar-gmail.sh` | `configurar-gmail.bat` | Asistente heredado de Gmail mediante Postfix. |
| `iniciar-dev.sh` | `iniciar-dev.bat` | Arranque de compatibilidad con Docker, sin recarga automática. |

El desarrollo nativo con recarga de Spring/Angular sigue disponible en Linux/WSL2 desde la [distribución multiplataforma](https://github.com/rodolfo99/helpdesk-siabuc). El lanzador `iniciar-dev.bat` de esta edición utiliza Docker igual que `iniciar.bat`.

## Comandos frecuentes

```powershell
.\iniciar.bat
.\detener.bat
.\compilar.bat
.\respaldar.bat
.\respaldar.bat --nombre-respaldo "C:\Copias SIABUC\mi-respaldo.dump"
.\configurar.bat --gmail-directo
```

Para consultar parámetros, añada `--help` al comando. Para Gmail directo utilice `configurar.bat --gmail-directo`; el asistente `configurar-gmail.bat` corresponde al relay heredado mediante Postfix.

## Si ya tiene una instalación

Conserve un respaldo comprobado, `.env`, `COMPOSE_PROJECT_NAME` y los volúmenes existentes. Extraiga esta edición en otra carpeta y siga la [guía de actualización para Windows](docs/WINDOWS.md#actualizar-a-185). Cambiar de Linux a Windows o de equipo requiere trasladar los datos mediante respaldo y restauración; copiar `.env` no traslada PostgreSQL.

## Si falla el inicio

Compruebe desde una terminal nueva:

```powershell
py -3 --version
docker info --format '{{.OSType}}'
docker compose version
```

Docker debe responder `linux`. Si Python solo está disponible como `python`, los `.bat` lo detectan como alternativa a `py`. Más diagnóstico, respaldos y configuración en [docs/WINDOWS.md](docs/WINDOWS.md).

La comprobación de este paquete en Linux no sustituye una prueba real con CMD y Docker Desktop en su equipo Windows. Los resultados históricos del proyecto permanecen en [docs/VALIDACION.md](docs/VALIDACION.md).
