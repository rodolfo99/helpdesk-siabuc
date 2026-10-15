# Gmail institucional UCOL · modos disponibles en 1.8.3

Para conectar Spring directamente con Gmail use `./configurar.sh --gmail-directo` o `configurar.bat --gmail-directo`. Esta opción, disponible desde 1.8.2, guarda la cuenta autorizada y su contraseña de aplicación en `.env`, activa STARTTLS en `smtp.gmail.com:587` y establece esa cuenta como remitente. No comprueba autenticación ni envía una prueba; reinicie para aplicar y consulte [CORREO.md](CORREO.md). No requiere Postfix.

El resto de esta guía describe el **asistente heredado con Postfix**, `configurar-gmail.sh`/`.bat`, para cuentas `@ucol.mx` alojadas en Google Workspace. Comprueba autenticación desde el anfitrión y configura Postfix para entregar a `smtp.gmail.com:587`. Spring continúa usando `smtp:25` dentro de Docker; no requiere abrir TCP 25 hacia Internet. Ambas opciones necesitan una credencial y política de cuenta compatibles; ninguna acredita por sí sola entrega al destinatario.

## Preparación y ejecución

1. Entre en su cuenta institucional y active la verificación en dos pasos si está permitida.
2. Abra https://myaccount.google.com/apppasswords y cree una contraseña de aplicación llamada «SIABUC Helpdesk». Si la opción no aparece, consulte al administrador de Google Workspace de UCOL: puede estar deshabilitada por política. Este script no utiliza OAuth ni evita esa restricción. No use su contraseña habitual ni comparta la contraseña generada.
3. Desde la carpeta instalada que contiene `.env` y `docker-compose.yml`, ejecute `bash configurar-gmail.sh`. Ejecútelo con su usuario habitual; solicita sudo únicamente si Docker lo requiere.
4. Escriba la dirección completa y pegue la contraseña de aplicación. La entrada queda oculta; admite los espacios con que Google agrupa los 16 caracteres.

Utilice los archivos completos de la misma versión del proyecto: el asistente importa `scripts/manage.py` y depende de su configuración y Compose. No copie sólo los lanzadores desde otra versión. Requiere Python 3.10+, Docker Compose con `up --wait`, la base `db` iniciada y las imágenes `smtp`/`backend` preparadas. Si aún no existe la imagen Postfix, constrúyala sin iniciar envíos con `docker compose --profile local-smtp build smtp`. Una vez instaladas las imágenes correctas, cambiar únicamente credenciales no requiere recompilarlas.

## Qué hace

Comprueba desde el equipo anfitrión la conexión, el certificado TLS y la autenticación contra Google **sin enviar mensajes de prueba**. Sólo después reemplaza `.env` de forma atómica, con permisos 600 y detección de modificaciones concurrentes. Conserva contraseña de PostgreSQL, puertos, administrador, destinatarios, Reply-To y comentarios. Cambia MAIL_FROM a la cuenta autenticada: no utiliza `root@helpdek` ni una IP como remitente nuevo.

Configura MAIL_ENABLED=true; SMTP_HOST=smtp, SMTP_PORT=25, SMTP_AUTH=false, SMTP_SECURITY=NONE; borra las credenciales SMTP directas de Spring; configura SMTP_RELAY_HOST=smtp.gmail.com, puerto 587, STARTTLS y credenciales de aplicación. En 1.8.3 Postfix confía en los contenedores de la red `default` del proyecto y en loopback; ya no existe una red SMTP dedicada.

Recrea sólo smtp/backend con Compose, espera su salud y solicita `postqueue -f`. No ejecuta `down`, no elimina volúmenes ni altera registros de soporte. Durante la recreación hay una breve interrupción del backend; los avisos pendientes pueden enviarse al reanudarse los servicios. La contraseña queda en `.env` y en el entorno del contenedor, accesible a administradores de Docker; no suba `.env` al repositorio.

`bash configurar-gmail.sh --solo-configurar` valida y guarda sin actuar sobre Docker. Para aplicarlo posteriormente:

```bash
docker compose --profile local-smtp up -d --no-deps --force-recreate --wait smtp backend
docker compose --profile local-smtp exec -T smtp postqueue -f
```

Si falla Google, el contenido de `.env` no cambia. Si falla Docker después de guardar, la configuración queda guardada y el script informa el fallo; corrija Docker y use los comandos anteriores. No hay reversión automática que borre la credencial nueva. Si cambia la contraseña de su cuenta Google, pueden revocarse sus contraseñas de aplicación: genere otra y repita el asistente.

Los comandos manuales se ejecutan con el acceso Docker habitual; anteponga `sudo` sólo si su instalación Linux lo requiere. En Windows no se utiliza `sudo`.

## Verificar entrega y cola anterior

```bash
docker compose --profile local-smtp ps
docker compose --profile local-smtp logs --tail=100 smtp
docker compose --profile local-smtp exec -T smtp postqueue -p
```

La prueba del anfitrión no demuestra conectividad desde Docker ni entrega al destinatario. Compruebe `status=sent` en Postfix, la bandeja de entrada y correo no deseado. SMTP healthy significa que el servicio responde, no que Google haya entregado los correos.

Los mensajes ya encolados conservan su remitente original (por ejemplo, `soporte@[IP]`). El script los conserva y solicita reintento, pero Google podría rechazarlos o reescribir el remitente. No elimina ni duplica avisos para solventarlo. Ante un rechazo, conserve el ID de cola y el diagnóstico para recuperar esos mensajes específicamente. Los avisos nuevos usan la cuenta institucional autenticada. Un estado SENT de la aplicación puede significar aceptación por Postfix local, aunque el mensaje siga pendiente en su cola.

Si no existe la opción de contraseña de aplicación, solicite a UCOL un relay autorizado (`smtp-relay.gmail.com`, configurado por su administrador) o una integración OAuth. No basta con cambiar el servidor sin obtener la autorización institucional.

## Fuentes y validación

- Google Workspace: https://knowledge.workspace.google.com/admin/gmail/send-email-from-a-printer-scanner-or-app
- Contraseñas de aplicación: https://support.google.com/mail/answer/185833?hl=es

Pruebas automatizadas del asistente relay con SMTP/Docker simulados: `python3 -m unittest discover -s tests/ops -p 'test_gmail.py'`. El asistente directo se cubre en `test_direct_gmail_182.py`; el nombre identifica su incorporación y sus pruebas siguen vigentes. Ninguna prueba simulada valida credenciales reales de UCOL ni entrega externa. La evidencia por versión está en [VALIDACION.md](VALIDACION.md).

## Windows y estado de validación real

En Windows use `configurar-gmail.bat` desde CMD o PowerShell, sin sudo, con Docker Desktop iniciado. Consulte [WINDOWS.md](WINDOWS.md). Los permisos 600 descritos para Linux no establecen una ACL privada en NTFS; proteja la carpeta desde Seguridad de Windows.

**Antecedente histórico del 13 de octubre de 2026:** el usuario confirmó que el SMTP local funcionó y que la configuración con Gmail de aquel momento falló. No se ha identificado aquí la causa ni se acredita una corrección por el solo hecho de incorporar Gmail directo después. La configuración de cualquiera de los dos modos es opcional; conserve una copia privada de `.env` antes de cambiar de proveedor.
