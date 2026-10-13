# SIABUC Helpdesk 1.3.0 · 13 de octubre de 2026

## Cambios

- Operación en Windows desde CMD/PowerShell mediante configurar.bat, iniciar.bat, detener.bat, compilar.bat, respaldar.bat, restaurar.bat y configurar-gmail.bat.
- Docker Desktop con contenedores Linux y Python 3.10+; no requiere Java/Maven/Node en el anfitrión. Los scripts Linux siguen disponibles.
- Respaldos abiertos explícitamente en modo binario también en Windows; conservación de rutas con espacios y parámetros de nombre de respaldo.
- Diagnóstico del motor Docker en Windows y rechazo temprano del desarrollo nativo no compatible; para ese modo se utiliza WSL2.
- Documentación de instalación, permisos NTFS, traslado de datos y diagnóstico. Se conserva el SMTP local por defecto.
- Código fuente Spring y Angular identificado como 1.3.0; JAR siabuc-helpdesk-1.3.0.jar. No se actualizaron dependencias por este cambio.

Se incluyen las correcciones anteriores de Postfix sobre Debian slim, el asistente Gmail y la guía de acceso de clientes con usuario/contraseña e historial institucional. El usuario confirmó SMTP local funcionando; Gmail falló en su instalación y continúa pendiente de diagnóstico.

## Actualizar una instalación existente

1. Cree un respaldo con el script de su sistema y conserve `.dump`, `.dump.sha256` y una copia privada de `.env`. El respaldo de BD no contiene la cola SMTP ni `.env`.
2. Extraiga el ZIP 1.3.0 completo en una carpeta nueva. Copie allí su `.env` vigente, sin reemplazar sus contraseñas ni la configuración SMTP que funciona.
3. Si permanece en el mismo motor Docker, conserve COMPOSE_PROJECT_NAME para reutilizar los volúmenes existentes. No ejecute `down -v`. En otra máquina/motor, restaure el respaldo como explica WINDOWS.md.
4. En Linux ejecute `bash iniciar.sh` con acceso a Docker; en Windows ejecute `.\iniciar.bat` desde PowerShell. Esto reconstruye las imágenes e inicia los servicios. Si usa sudo en Linux, ejecútelo como `sudo bash iniciar.sh`.
5. Compruebe acceso, historial de un usuario cliente, saldos y avisos con un caso de prueba. Consulte `docker compose ps` y los logs si algún servicio no supera su salud. El cambio de versión no presupone resuelto un error unhealthy anterior.

No hay migraciones SQL nuevas: se conservan V1, V2 y V3. Las cuentas y claves están en PostgreSQL; ADMIN_PASSWORD de `.env` sólo sirve para el administrador inicial de una base nueva.

## Contenido del ZIP

El paquete contiene fuentes, lockfile npm, Dockerfiles, Compose, scripts de ambos sistemas, pruebas, manuales y MANIFEST-SHA256.txt. No incluye credenciales, datos de clientes, respaldos, cola SMTP, node_modules, JAR compilado ni imágenes Docker. Se requiere Internet para descargar imágenes/dependencias durante la compilación. Los `.bat` usan CRLF y los `.sh` mantienen sus permisos ejecutables en el ZIP.

Cada entrada del manifiesto indica SHA-256 y ruta relativa dentro de `siabuc-helpdesk`. El manifiesto no se incluye a sí mismo. La integridad del ZIP y de cada archivo del manifiesto se comprueba al empaquetar.

## Guías

- [Windows: instalación y comandos](WINDOWS.md)
- [Operación Linux y Docker](OPERACION.md)
- [Respaldar y restaurar](RESPALDO-RESTAURACION.md)
- [SMTP local](SMTP-INTERNO.md)
- [Gmail opcional y límites](GMAIL-UCOL.md)
- [Usuarios, instituciones e historial](MANUAL-USUARIO.md)
- [Evidencia de pruebas](VALIDACION.md)

## Actualización 1.3.1: pausa administrativa de correo

Añade el control de pausa/reactivación desde Correo y notificaciones, persistencia PostgreSQL V4, auditoría y permiso exclusivo ADMIN. Conserva el historial y los scripts Windows/Linux; desactivar suprime los avisos anteriores recuperables y los nuevos durante la pausa. Reactivar sólo notifica eventos nuevos; los correos ya en curso o aceptados por SMTP pueden completar su entrega. MAIL_ENABLED=false permanece como límite del servidor. El artefacto Java pasa a siabuc-helpdesk-1.3.1.jar. Consulte [DESACTIVAR-CORREO.md](DESACTIVAR-CORREO.md) para actualizar, pausar y reanudar. La afirmación anterior de ausencia de migraciones nuevas corresponde a 1.3.0; 1.3.1 sí requiere V4.

## Actualización 1.3.2: contenido de los correos de casos

Los avisos identifican al administrativo responsable y a la institución cliente. Incluyen la descripción vigente del caso, separada del detalle de la actualización, y el estado **Abierto** o **Cerrado**. Terminan con «Para mayor información, consulte la plataforma Helpdesk.». Consulte el [formato y ejemplo de correo](CORREO.md#contenido-de-los-avisos-de-casos).

Se conserva la privacidad de las notas internas y la pausa sin envío de avisos anteriores. No se modifican avisos ya guardados ni la configuración SMTP. El JAR es `siabuc-helpdesk-1.3.2.jar`; no hay migraciones adicionales a V1–V4 ni cambios de dependencias.

Para actualizar, respalde y conserve su `.env`, extraiga el ZIP 1.3.2 completo y use el mismo `COMPOSE_PROJECT_NAME` y motor Docker para reutilizar sus volúmenes. Ejecute `bash iniciar.sh` en Linux o `.\iniciar.bat` en PowerShell desde la nueva carpeta. No use `down -v`. Después de reiniciar, el nuevo formato se aplica a los eventos nuevos; compruébelo con un caso de prueba y los correos de su instalación.

## Actualización 1.3.3: detalle de historiales y toma de casos

- Hacer clic en un elemento del historial de atención abre su detalle completo. **← Volver al historial** devuelve al mismo caso y recupera posición y foco; conserva filtros y borradores.
- Los administrativos pueden abrir cada notificación para leer el correo persistido y todos sus datos de entrega. La vuelta conserva búsqueda y filtro. El nuevo endpoint de detalle exige `ADMIN` y no provoca envíos ni cambios en la cola.
- Los clientes conservan **Nuevo caso** para su propia institución. Los administrativos pueden **Tomar caso** si está abierto y sin responsable; la operación comprueba la asignación dentro de la transacción, conserva el historial y genera el aviso. Se añaden filtros **Sin responsable** y **Mis casos**. No cambia la asignación institucional predeterminada.

No se añaden migraciones ni dependencias. El JAR es `siabuc-helpdesk-1.3.3.jar`. Para actualizar, respalde, conserve `.env`, extraiga el ZIP 1.3.3 completo, mantenga el mismo `COMPOSE_PROJECT_NAME` y motor Docker y ejecute `bash iniciar.sh` en Linux o `.\iniciar.bat` en PowerShell. No elimine volúmenes. Consulte [Manual de usuario](MANUAL-USUARIO.md) y [Validación](VALIDACION.md) para el alcance de las pruebas.
