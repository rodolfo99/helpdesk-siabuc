# SIABUC Helpdesk 1.9.0

Entrega del 17 de noviembre de 2026, preparada en la [PR #52](https://github.com/rodolfo99/helpdesk-siabuc/pull/52). El paquete conjunto `SIABUC-Helpdesk-1.9.0.zip` contiene los fuentes, documentación y ocho pares de iniciadores Linux `.sh` y Windows `.bat`. Requiere compilar las imágenes; no incluye una base de datos, credenciales, imágenes Docker ni dependencias descargadas.

## Cambios incluidos

- **Acceso separado:** clientes en `/poliza`; Administración, Técnico y Cobranza en `/poliza/admin`. Ambos usan usuario y contraseña locales, con validación de rol en el servidor. Federación UCOL y vinculación de identidad con póliza/correo/licencia SIABUC9 permanecen pendientes.
- **Portal del cliente:** guía inicial persistente por cuenta, consulta de pólizas y saldos de soporte de su institución, historial y respuestas públicas sin leer. La nueva consulta de soporte no incorpora datos financieros.
- **Recursos oficiales:** catálogo editorial con borradores, publicación por Administración y enlaces HTTPS bajo `ucol.mx`. No se incluyen enlaces ficticios publicados ni se verifica la disponibilidad remota de los archivos. Antes de publicarlos deben comprobarse los recursos institucionales reales.
- **Acceso de clientes:** bloqueo configurable, inicialmente cinco intentos y 15 minutos. Administración puede configurar el umbral y desbloquear cuentas desde Usuarios. Los fallos con espacios o tabulaciones alrededor del nombre se contabilizan en la misma cuenta.
- **Recuperación y correo:** solamente Administración restablece contraseñas y edita el correo de contacto desde Usuarios. No existe recuperación automática por correo ni código de licencia. El correo de Helpdesk sigue separado de cualquier correo asociado a una licencia SIABUC9.
- **Correcciones de revisión:** compilación de `UsersComponent`, normalización del contador de acceso, comprobación dinámica de migraciones, redirección Nginx y escenarios de navegador adaptados a los portales. CLI/build Angular 21.2.30 y compiler-cli 21.2.25 alineado con Angular 21.2.25.

Los procedimientos y el alcance de cada requisito están en [Accesos de Adriana](ACCESOS-ADRIANA.md). Los módulos existentes de licencias, cobranza, casos y respaldos se conservan.

## Actualización desde 1.8.5

1. Genere un respaldo y compruebe su restauración en un proyecto aislado antes de actualizar la instalación institucional.
2. Conserve `.env`, `COMPOSE_PROJECT_NAME`, credenciales, volúmenes y carpeta de respaldos. Descomprima el paquete completo en otra carpeta y copie allí únicamente la configuración privada necesaria; revise las rutas relativas.
3. En Linux ejecute `./compilar.sh`; en Windows `./compilar.bat`, con Docker Desktop en modo contenedores Linux. Para reconstruir también la base use `docker compose build --pull db`. Python 3.10+ y Docker Compose son los requisitos del anfitrión.
4. Detenga los servicios anteriores con `./detener.sh` o `./detener.bat` y arranque desde la carpeta nueva con `./iniciar.sh` o `./iniciar.bat`, conservando el proyecto y los volúmenes existentes.
5. Compruebe los portales de cliente y personal interno, los datos de la institución, saldos, apertura de casos y los registros de Flyway. Verifique correo y respaldos según la configuración existente.

Flyway conserva V1–V27 y añade:

| Migración | Datos añadidos |
| --- | --- |
| V28 | Finalización de guía inicial por cuenta |
| V29 | Lectura de respuestas por cuenta y caso |
| V30 | Catálogo de recursos oficiales, vacío inicialmente |
| V31 | Contadores de fallos y configuración de bloqueo de clientes |

No hay V32 de recuperación automática. No modifique las migraciones aplicadas. Para retroceder a una versión anterior, restaure una copia compatible en una base nueva y aislada; no ejecute un backend antiguo sobre el esquema actualizado. No use `docker compose down -v`: elimina los volúmenes. El ZIP no reemplaza un respaldo de los datos.

Las instalaciones nuevas mantienen correo y calendario de respaldos desactivados por defecto. La actualización conserva su configuración persistida. Consulte [Operación](OPERACION.md), [Windows](WINDOWS.md) y [Respaldo y restauración](RESPALDO-RESTAURACION.md).

## Validación y seguridad

Evidencia ejecutada antes de numerar la entrega, sobre el código de la PR en `1f8cee48f8e01a6c66dce7f105e437a91d7e36f1` y su combinación de prueba con `main`:

| Comprobación | Resultado y alcance |
| --- | --- |
| [Verificación integral](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/44189668317) | Aprobada: 264 pruebas Java con PostgreSQL 17, 10 pruebas Node y compilación Angular; 83 pruebas Python aprobadas y una omitida por permisos, con la suite de 12 pruebas de permisos ejecutada también mediante sudo |
| Navegador en la verificación integral | 10 escenarios aprobados, con API simulada; no acreditan una sesión completa contra el backend ni un despliegue institucional |
| [Backend independiente](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/44189668381) | Aprobado: 10 pruebas unitarias y 254 de integración |
| [Iniciadores Linux/Windows](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/44189668276) | Ayuda nativa de BAT, conservación de argumentos y comprobación del paquete aprobadas |
| [Seguridad y actualización Docker](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/44189668363) | Cinco imágenes finales aprobadas frente a HIGH/CRITICAL; auditoría E2E y ensayo aislado de actualización, respaldo/restauración y cola SMTP aprobados. El workflow completo falla por la auditoría npm del frontend |

Los [checks de la PR #52](https://github.com/rodolfo99/helpdesk-siabuc/pull/52/checks) identifican el SHA y resultado de la revisión final de versión y empaquetado. Los resultados anteriores se atribuyen a su revisión concreta; no se presentan como pruebas de producción. Continúan pendientes el despliegue institucional, HTTPS/dominio, recursos oficiales reales y la comprobación de entrega externa de correo.

**Advertencia abierta:** al 17 de noviembre de 2026, `npm audit` del frontend completo reporta **13 hallazgos altos y 14 moderados**. Los 13 altos se derivan de `picomatch` y sus dependientes de desarrollo/compilación; no son 13 vulnerabilidades raíz distintas. [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf) afecta hasta 4.0.7 y no tiene versión corregida publicada al preparar esta entrega. `npm audit --omit=dev` registra **cero hallazgos**. El contenedor final de frontend sirve archivos estáticos con Nginx; Node/npm permanecen en la etapa de construcción.

La entrega mantiene la auditoría completa activa y su fallo visible: no se rebaja el umbral, se excluye `picomatch` ni se declara CI completamente aprobada. Cuando exista una corrección oficial deben actualizarse el lockfile y las herramientas compatibles y repetirse auditoría, compilación y regresiones.

## Paquete verificable

Desde un checkout Git de esta versión:

```bash
python3 scripts/release-1.9.0.py --package --output release-output
```

En Windows puede usarse `py -3` en lugar de `python3`. El empaquetador exige los iniciadores de ambos sistemas y las migraciones V28–V31, excluye archivos privados y dependencias locales, comprueba CRC y contenido y genera `MANIFEST-SHA256.txt` dentro del ZIP, además de la huella SHA-256 externa. La edición opcional `--windows` omite los `.sh`; el ZIP conjunto es la distribución principal.
