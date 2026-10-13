# SIABUC Helpdesk 1.4.0

Esta versión agrega clasificación y prioridad de casos, ficha técnica, SLA con horario laboral, archivos adjuntos, campana interna, alertas de saldo, cronómetro confirmado, reportes Excel/PDF y agenda remota. Los casos siguen usando **Abierto / Cerrado**. Se conservan los roles, pagos, bolsas independientes de minutos y pausa del correo.

## Instalar desde cero

El ZIP contiene el código, migraciones, configuración de ejemplo, documentación, pruebas y scripts para Linux y Windows. No contiene credenciales, datos de clientes, dependencias descargadas ni imágenes compiladas. La primera compilación requiere Internet.

1. Instale Docker Engine + Compose en Linux, o Docker Desktop en Windows con contenedores Linux y WSL 2. Instale Python 3.10 o posterior.
2. Descomprima el ZIP y abra una terminal en `siabuc-helpdesk`.
3. En Linux ejecute `chmod +x ./*.sh`, seguido de `./configurar.sh`. En Windows ejecute `configurar.bat` desde CMD o `./configurar.bat` desde PowerShell.
4. Revise `.env`: contraseñas, puertos, dirección de escucha y correo. Para elegir la contraseña inicial de PostgreSQL use `./configurar.sh --password-bd` o `configurar.bat --password-bd`. No cambie este valor para una base existente sin seguir la guía de operación.
5. Ejecute `./iniciar.sh` o `iniciar.bat`. Ambos compilan las imágenes e inician PostgreSQL, Postfix, Spring y Angular/Nginx. No necesita Java, Maven ni Node en el anfitrión para este modo.
6. Abra `http://127.0.0.1:4200`, o el puerto configurado. Use `ADMIN_USERNAME` y `ADMIN_PASSWORD` de `.env`. Cambie la contraseña en Mi cuenta.
7. Cree instituciones y usuarios cliente; registre sus correos, responsables y bolsas de tiempo. En la configuración de soporte revise el calendario SLA y los umbrales de saldo. Siga el [manual 1.4](HELPDESK-1.4.md).

Los scripts existentes de compilación, inicio, detención, correo, respaldo y restauración conservan sus variantes `.sh` y `.bat`. Consulte [Windows](WINDOWS.md) y [Operación](OPERACION.md) para requisitos y compilación nativa de desarrollo en Linux. `compilar.sh` genera `backend/target/siabuc-helpdesk-1.4.0.jar` y `frontend/dist/helpdesk/browser/`; `compilar.bat --docker` construye las imágenes.

## Actualizar conservando datos

No borre volúmenes y no ejecute `docker compose down -v`. Detenga cualquier proceso de desarrollo que escriba en la misma base. Programe una ventana de mantenimiento.

1. Con la base actual funcionando, cree un respaldo:

   Linux:
   ```bash
   ./respaldar.sh --nombre-respaldo backups/antes-1.4.dump
   ./detener.sh
   ```

   Windows:
   ```bat
   respaldar.bat --nombre-respaldo backups\antes-1.4.dump
   detener.bat
   ```

2. Guarde fuera de la carpeta una copia privada de `.env`, del `.dump` y del `.sha256`. Conserve el mismo `COMPOSE_PROJECT_NAME`: identifica los volúmenes existentes.
3. Reemplace el código por el del ZIP 1.4.0, conservando `.env` y los respaldos. No copie `.env.example` sobre su configuración. Puede extraer en otra carpeta y trasladar su `.env`; utilice el mismo Docker y nombre de proyecto.
4. Inicie con `./iniciar.sh` o `iniciar.bat`. Flyway aplica V5–V8 después de las migraciones anteriores. No edite ni vuelva a numerar migraciones ya aplicadas.
5. Compruebe acceso, instituciones, saldos, pagos e historial. Abra la ficha de un caso, revise los avisos y pruebe un adjunto de diagnóstico sin información sensible. Los casos anteriores se clasifican inicialmente como Otro / Normal; los SLA heredados se marcan como reconstruidos y no inventan horas de respuesta desconocidas.
6. Revise calendario y objetivos. Los cambios globales de SLA se aplican a casos nuevos; cada caso guarda su configuración. Puede agregar `REPORTING_TIME_ZONE=America/Mexico_City` a `.env`; si se omite, ése es el valor predeterminado. Cambiar esta variable requiere reiniciar el backend.

Los adjuntos se guardan en PostgreSQL y quedan incluidos en los respaldos nuevos. No se necesita un volumen adicional. Las notificaciones internas tampoco dependen de la cola SMTP. La configuración y los avisos suprimidos por pausa de correo se conservan.

## Recuperación

Ante un error de actualización, conserve la salida de `docker compose ps` y `docker compose logs --tail=150 backend`. No borre los datos para solucionar un estado unhealthy.

Para regresar a la versión anterior, use su código anterior, su `.env` y el respaldo **anterior a 1.4**; restaure mediante `restaurar.sh` o `restaurar.bat` con `--nombre-respaldo` y `--confirmar-reemplazo`. Restaurar reemplaza los datos actuales y pierde las operaciones posteriores al respaldo. No ejecute binarios anteriores directamente sobre una base ya migrada. Consulte [Restauración](RESPALDO-RESTAURACION.md).

La evidencia de compilación y pruebas de esta entrega se registra en [Validación](VALIDACION.md), separada de las pruebas históricas.
