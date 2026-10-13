# Gmail institucional UCOL como relay SMTP

Para una cuenta `@ucol.mx` alojada en Google Workspace, el asistente configura Postfix interno para entregar a `smtp.gmail.com:587` con STARTTLS y contraseña de aplicación. La aplicación continúa usando `smtp:25` dentro de Docker; no requiere abrir TCP 25 hacia Internet.

## Preparación y ejecución

1. Entre en su cuenta institucional y active la verificación en dos pasos si está permitida.
2. Abra https://myaccount.google.com/apppasswords y cree una contraseña de aplicación llamada «SIABUC Helpdesk». Si la opción no aparece, consulte al administrador de Google Workspace de UCOL: puede estar deshabilitada por política. Este script no utiliza OAuth ni evita esa restricción. No use su contraseña habitual ni comparta la contraseña generada.
3. Desde la carpeta instalada que contiene `.env` y `docker-compose.yml`, ejecute `bash configurar-gmail.sh`. Ejecútelo con su usuario habitual; solicita sudo únicamente si Docker lo requiere.
4. Escriba la dirección completa y pegue la contraseña de aplicación. La entrada queda oculta; admite los espacios con que Google agrupa los 16 caracteres.

Para agregar sólo el asistente a una instalación 1.2 existente, descargue sus dos archivos desde el mismo commit/revisión del repositorio:

```bash
curl -fL https://raw.githubusercontent.com/rodolfo99/helpdesk-siabuc/main/configurar-gmail.py -o configurar-gmail.py
curl -fL https://raw.githubusercontent.com/rodolfo99/helpdesk-siabuc/main/configurar-gmail.sh -o configurar-gmail.sh
bash configurar-gmail.sh
```

Requiere Python 3.10+, Docker Compose con `up --wait`, servicios db/smtp/backend ya instalados y la corrección de Postfix para Debian slim. No hace falta recompilar Angular, Spring ni la imagen SMTP para cambiar estas variables.

## Qué hace

Comprueba desde el equipo anfitrión la conexión, el certificado TLS y la autenticación contra Google **sin enviar mensajes de prueba**. Sólo después reemplaza `.env` de forma atómica, con permisos 600 y detección de modificaciones concurrentes. Conserva contraseña de PostgreSQL, puertos, administrador, destinatarios, Reply-To y comentarios. Cambia MAIL_FROM a la cuenta autenticada: no utiliza `root@helpdek` ni una IP como remitente nuevo.

Configura MAIL_ENABLED=true; SMTP_HOST=smtp, SMTP_PORT=25, SMTP_AUTH=false, SMTP_SECURITY=NONE; borra las credenciales SMTP directas de Spring; configura SMTP_RELAY_HOST=smtp.gmail.com, puerto 587, STARTTLS y credenciales de aplicación. La conexión interna sigue confiando únicamente en la red privada prevista por el proyecto.

Recrea sólo smtp/backend con Compose, espera su salud y solicita `postqueue -f`. No ejecuta `down`, no elimina volúmenes ni altera registros de soporte. Durante la recreación hay una breve interrupción del backend; los avisos pendientes pueden enviarse al reanudarse los servicios. La contraseña queda en `.env` y en el entorno del contenedor, accesible a administradores de Docker; no suba `.env` al repositorio.

`bash configurar-gmail.sh --solo-configurar` valida y guarda sin actuar sobre Docker. Para aplicarlo posteriormente:

```bash
sudo docker compose up -d --no-deps --force-recreate --wait smtp backend
sudo docker compose exec -T smtp postqueue -f
```

Si falla Google, el contenido de `.env` no cambia. Si falla Docker después de guardar, la configuración queda guardada y el script informa el fallo; corrija Docker y use los comandos anteriores. No hay reversión automática que borre la credencial nueva. Si cambia la contraseña de su cuenta Google, pueden revocarse sus contraseñas de aplicación: genere otra y repita el asistente.

## Verificar entrega y cola anterior

```bash
sudo docker compose ps
sudo docker compose logs --tail=100 smtp
sudo docker compose exec -T smtp postqueue -p
```

La prueba del anfitrión no demuestra conectividad desde Docker ni entrega al destinatario. Compruebe `status=sent` en Postfix, la bandeja de entrada y correo no deseado. SMTP healthy significa que el servicio responde, no que Google haya entregado los correos.

Los mensajes ya encolados conservan su remitente original (por ejemplo, `soporte@[IP]`). El script los conserva y solicita reintento, pero Google podría rechazarlos o reescribir el remitente. No elimina ni duplica avisos para solventarlo. Ante un rechazo, conserve el ID de cola y el diagnóstico para recuperar esos mensajes específicamente. Los avisos nuevos usan la cuenta institucional autenticada. Un estado SENT de la aplicación puede significar aceptación por Postfix local, aunque el mensaje siga pendiente en su cola.

Si no existe la opción de contraseña de aplicación, solicite a UCOL un relay autorizado (`smtp-relay.gmail.com`, configurado por su administrador) o una integración OAuth. No basta con cambiar el servidor sin obtener la autorización institucional.

## Fuentes y validación

- Google Workspace: https://knowledge.workspace.google.com/admin/gmail/send-email-from-a-printer-scanner-or-app
- Contraseñas de aplicación: https://support.google.com/mail/answer/185833?hl=es

Pruebas automatizadas con SMTP/Docker simulados: `python3 -m unittest discover -s tests/ops -p 'test_gmail.py'`. No se ha probado con credenciales reales de UCOL ni ejecutado Docker en el entorno de desarrollo de este cambio.

## Windows y estado de validación real

En Windows use `configurar-gmail.bat` desde CMD o PowerShell, sin sudo, con Docker Desktop iniciado. Consulte [WINDOWS.md](WINDOWS.md). Los permisos 600 descritos para Linux no establecen una ACL privada en NTFS; proteja la carpeta desde Seguridad de Windows.

El 13 de octubre de 2026 el usuario confirmó que el SMTP local funcionó y que la configuración con Gmail no funcionó. No se ha identificado aquí la causa de ese fallo ni se presenta Gmail como validado en su instalación. Mantenga la configuración local que ya funciona; ejecutar el configurador de Gmail es opcional.
