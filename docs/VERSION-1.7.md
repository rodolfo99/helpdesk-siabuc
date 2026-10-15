# SIABUC Helpdesk 1.7.0: equipo técnico y control financiero

> **Documento histórico — 1.7.0.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.3.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.3](MANUAL-EXTENDIDO-1.8.3.md), el [índice de documentación](README.md) y las [notas de 1.8.3](VERSION-1.8.3.md).

La versión 1.7 incorpora las ocho mejoras solicitadas y conserva soporte, cobranza, portal, SMTP y herramientas de operación de las versiones anteriores. Los estados del caso siguen siendo **Abierto/Cerrado**. Los respaldos automáticos permanecen **desactivados por defecto**; una instalación que ya los activó expresamente conserva su decisión.

## Funciones y acceso

| Función | Uso y permisos |
| --- | --- |
| [Técnico de soporte](ROL-TECNICO.md) | Administración crea cuentas TECH. Atienden todas las instituciones, toman casos sin responsable, registran atención y cierran/reabren. No administran cuentas, finanzas, respaldos o configuración; la reasignación y anulación de consumos siguen reservadas a ADMIN. |
| [Vistas y avisos SLA](VISTAS-ALERTAS.md) | Cada cuenta de soporte guarda sus filtros personales. Administración configura la anticipación de avisos; la campana avisa a ADMIN y a técnicos asignados o ante casos sin responsable. Los plazos se calculan con el calendario del caso; la anticipación se expresa en minutos naturales. |
| [Plantillas](PLANTILLAS-RESPUESTA.md) | Administración mantiene instrucciones por categoría y versión. ADMIN/TECH consultan la vista previa y las insertan en un borrador editable; la publicación requiere pulsar Agregar al historial. |
| [Incidencias](INCIDENCIAS.md) | ADMIN/TECH agrupan casos y registran avances internos. Sólo se muestra al cliente un resumen público expresamente publicado en uno de sus casos. Resolver una incidencia no cierra casos ni consume tiempo. |
| [Paquetes y tarifas](PAQUETES-TARIFAS.md) | ADMIN/BILLING definen paquetes MXN y minutos por canal. Cada revisión conserva sus valores; la cotización guarda la versión elegida. Actualizar tarifas no reescribe acuerdos anteriores. |
| [Parcialidades](PARCIALIDADES.md) | ADMIN/BILLING programan importes/fechas por póliza y asignan abonos ya registrados. Los recordatorios configurables se consultan en esta pantalla, incluso con SMTP pausado. |
| [Conciliación CSV](CONCILIACION-BANCARIA.md) | ADMIN/BILLING revisan la vista previa, importan movimientos y confirman coincidencias con pagos existentes. Duplicados controlados y correcciones con motivo. Importar o conciliar nunca acredita minutos. |
| [Estado de cuenta](ESTADOS-CUENTA.md) | ADMIN/BILLING generan por institución; el cliente sólo para la propia y con el portal financiero habilitado. Cada emisión preserva sus cargos, pagos, pendientes y movimientos de minutos y permite PDF. |

Los estados de cuenta usan fecha de **registro en plataforma**, no la fecha declarada del depósito. Los datos anteriores forman una apertura desde la migración V24; no se inventa un detalle de movimientos previo. Consulte su guía para entender el corte, las monedas separadas y el significado de una nueva emisión.

## Recorrido recomendado

1. Cree una cuenta de técnico en **Usuarios**. Asigne responsables ADMIN/TECH a instituciones y casos según corresponda.
2. En **Casos de soporte**, filtre Abiertos y Mis casos, escriba «Mis casos pendientes» y guarde la vista. También puede guardar Urgente + Sin responsable.
3. Mantenga las instrucciones en **Plantillas**. Dentro de un caso abierto, revise la plantilla y use **Insertar en borrador**; ajuste el mensaje antes de registrarlo.
4. En **Incidencias generales**, agrupe casos del mismo problema. Redacte un resumen público independiente, revíselo y publique sólo la información que todas las instituciones vinculadas puedan conocer.
5. En **Paquetes y tarifas**, defina precios y minutos. Seleccione el paquete en una cotización; cualquier personalización queda señalada y la tarifa anterior permanece consultable.
6. Cree la póliza desde una cotización aceptada o mediante el flujo existente. En **Parcialidades**, programe compromisos cuyo total no exceda el contrato.
7. Verifique el dinero recibido y registre el pago, o apruebe su solicitud. Esta acción acredita los minutos indicados. Después puede asignar el abono a parcialidades; no se acredita por segunda vez.
8. Importe el CSV bancario con las columnas documentadas, revise sugerencias y confirme la relación con el pago. Una coincidencia es una constancia de conciliación, no una orden de cobro.
9. En **Estados de cuenta**, seleccione institución y corte y genere la emisión. El cliente autorizado dispone del mismo recorrido dentro de **Finanzas institucionales**.

## Instalación nueva en orden

1. Instale Docker con Compose y Python 3.10+. En Windows utilice Docker Desktop con contenedores Linux. Con Docker no necesita instalar Java, Maven o Node en el equipo anfitrión.
2. Descomprima `SIABUC-Helpdesk-1.7.0.zip` y abra una terminal en `siabuc-helpdesk`.
3. Linux: `chmod +x ./*.sh` y `./configurar.sh`. PowerShell: `.\configurar.bat`.
4. Revise `.env`, especialmente `DB_PORT`, `SERVER_PORT`, `FRONTEND_PORT`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` y SMTP. Para elegir la contraseña de una base nueva: `./configurar.sh --password-bd` o `.\configurar.bat --password-bd`.
5. Compile e inicie: `./iniciar.sh` o `.\iniciar.bat`. Docker construye Spring/Angular y el servidor aplica V1–V24 con Flyway.
6. Abra `http://127.0.0.1:4200` o el puerto configurado. Inicie sesión con el administrador de `.env`, cambie su contraseña y cree instituciones/cuentas.
7. Habilite el portal financiero únicamente para las instituciones que corresponda. Los respaldos automáticos siguen apagados hasta que Administración decida activarlos; el respaldo manual está disponible.

SMTP local se mantiene como configuración predeterminada. No cambie un SMTP que ya funciona. La pausa de correo conserva su comportamiento: al continuar sólo se envían eventos nuevos. Los avisos internos de SLA y parcialidades no dependen del correo.

## Actualización desde 1.6 conservando datos

1. Reserve una ventana de mantenimiento, suspenda escrituras y haga una copia con la versión instalada. Ejemplo Linux: `./respaldar.sh --nombre-respaldo backups/antes-1.7.dump`. Windows: `.\respaldar.bat --nombre-respaldo backups/antes-1.7.dump`.
2. Detenga con `./detener.sh` o `.\detener.bat`. Conserve la copia, `.env`, los volúmenes PostgreSQL/SMTP, la carpeta de respaldos automáticos y el mismo `COMPOSE_PROJECT_NAME`.
3. Extraiga el nuevo código en una carpeta independiente y transfiera la configuración conservada. Si la ruta de respaldos es relativa, copie su contenido o apunte `BACKUP_DIRECTORY` a la ubicación anterior.
4. No ejecute `docker compose down -v`, no borre la base y no modifique migraciones anteriores. Mantenga los puertos configurados para evitar conflictos con otras aplicaciones.
5. Ejecute `./iniciar.sh` o `.\iniciar.bat`: se recompilan imágenes y se agregan V17–V24. Los usuarios, contraseñas, pagos, consumos, casos, adjuntos y pausa de SMTP se conservan.
6. Compruebe saldos y registros conocidos, pruebe una cuenta TECH y una cliente y revise `docker compose ps` y `docker compose logs backend`. Una cuenta existente no cambia de rol automáticamente.
7. Para retroceder, restaure el respaldo anterior en una base nueva compatible con la versión anterior, siguiendo [respaldo y restauración](RESPALDO-RESTAURACION.md). Reemplazar el JAR no revierte el esquema.

## Compilación, pruebas y contenido

- Docker: `./compilar.sh --docker` o `.\compilar.bat --docker`; `iniciar` también compila.
- Compilación local Linux/WSL2: `./compilar.sh`, con Java 17, Maven y Node conforme a [Operación](OPERACION.md). Resultado: `backend/target/siabuc-helpdesk-1.7.0.jar` y `frontend/dist/helpdesk/browser/`.
- Pruebas backend: `mvn -Ppostgres-it verify`, sólo contra una base exclusiva de pruebas. Pruebas cliente: `npm test` y `npm run build` en `frontend`.
- El ZIP contiene fuentes completas, migraciones, scripts Linux/Windows, Docker, documentación y pruebas. No contiene contraseñas, `.env` privado, base productiva, `node_modules`, `target` o `dist`.
- [Validación](VALIDACION.md) distingue lo ejecutado de las comprobaciones pendientes en Docker, PostgreSQL nativo y Windows. Los scripts disponibles no equivalen por sí solos a una ejecución validada en esos entornos.
