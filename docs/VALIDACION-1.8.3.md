# SIABUC Helpdesk 1.8.3 — evidencia de validación

> **Documento histórico.** Conserva los procedimientos y resultados de la versión indicada en el título.
> Para uso, instalación y validación vigentes, consulte el [manual integrado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), las [notas 1.8.5](VERSION-1.8.5.md) y el [índice](README.md).

Fecha: 15 de octubre de 2026. Revisión funcional comprobada:
`15e9b2c4e1e94d97632b2e90dc389bfc727d5c71`.
Incluye los cinco estados de atención, migración V26 y Docker sin red SMTP dedicada.
Los cambios documentales posteriores no se presentan como nuevas ejecuciones de
esas pruebas; consulte el SHA de cada ejecución antes de integrar o desplegar.

## Resultados reproducibles en GitHub Actions

| Comprobación | Resultado | Fuente |
| --- | --- | --- |
| Java 17 con PostgreSQL 17 nativo | 233 pruebas: 10 unitarias y 223 de integración, cero fallos, errores u omisiones | [CI 1.8.3](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Flyway | V1–V26; prueba de actualización V25→V26 con casos, atenciones, saldos y vistas guardadas anteriores | [Mismo CI](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Python | 58 pruebas de operación y 13 SMTP; 71 satisfactorias | [Mismo CI](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Frontend | 10 pruebas y compilación Angular satisfactorias | [Mismo CI](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Navegador | 4 escenarios de cinco estados, filtros, permisos visibles y móvil de 390 px; API simulada | [Mismo CI](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Compose | Gmail directo y Postfix opcional, única red `default` sin IPAM fijo y puertos restringidos | [Check independiente de red](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017264) |
| Postfix real en Docker | Imagen construida, servicio saludable y conexión desde otro contenedor de `default`; EHLO/MAIL/RCPT/RSET correctos, sin DATA | [CI 1.8.3](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017255) |
| Regresión backend independiente | Ejecución completa satisfactoria sobre PostgreSQL 17 | [Workflow backend](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38807017257) |

La prueba de SMTP no genera ni entrega un mensaje: comprueba que un cliente de
la red asignada por Docker puede iniciar y cancelar una transacción de envío.
Los tests de configuración conservan `permit_mynetworks, reject` y comprueban que
una clave heredada `SMTP_NETWORK_SUBNET` no puede sustituir la detección de red.
El check de red verifica además que el override de desarrollo publica PostgreSQL
y SMTP sólo en `127.0.0.1`.

Las pruebas Java abarcan transiciones válidas e inválidas, permisos, idempotencia,
respuesta del cliente, toma de casos, SLA, reapertura con saldo, pausa y confirmación
de cronómetros, reportes y etiquetas de avisos. V1–V25 no cambian sus bytes.

## Paquete y alcance documental

El empaquetador `scripts/release-1.8.3.py` usa archivos versionados, genera un
manifiesto SHA-256 y comprueba contenido y CRC. Excluye `.env` reales, claves,
respaldos, logs, dependencias descargadas y directorios de compilación; conserva
los scripts de ejecución. El ZIP incluye fuentes y requiere compilar al instalar.

El paquete de la revisión funcional citada contenía 338 archivos más manifiesto,
con SHA-256 `ad556c5761f336dd3f895b387a355025f26d0b04382f533f1a9031c738cbebaa`.
Ese hash corresponde a esa revisión previa a la ampliación documental; cada ZIP
posterior genera su propio manifiesto y archivo `.zip.sha256`.

La revisión documental contrasta README, API, permisos, instalación, correo,
soporte, finanzas y respaldos con los fuentes 1.8.3. El nuevo
[manual detallado](MANUAL-EXTENDIDO-1.8.3.md) y el [índice](README.md) son los
puntos de entrada actuales. Las notas y resultados de versiones previas se
conservan identificados como históricos.

La comprobación documental final abarcó los 52 archivos Markdown del proyecto:
590 enlaces locales y anclas válidos, 80 bloques Bash con sintaxis válida
(`bash -n`) y `git diff --check` sin errores. Esto verifica referencias y sintaxis;
no equivale a ejecutar todos los procedimientos descritos.

## Límites

- No acredita despliegue o actualización de una base productiva.
- No acredita entrega externa SMTP, autenticación Gmail/Google Workspace ni lectura de correo.
- No acredita ejecución nativa de scripts bajo Windows ni acceso LAN del equipo de destino.
- No acredita respaldo/restauración completos en el servidor del usuario; deben comprobarse con sus rutas y permisos.
- Los cuatro escenarios de navegador usan API simulada; no se presentan como un recorrido Angular → Spring → PostgreSQL completo.
- El parche de permisos de destino de respaldos de la PR #17 permanece independiente de esta revisión.

## Repetir las verificaciones

Siga [Operación](OPERACION.md) para preparar una base PostgreSQL desechable y
ejecute `mvn -Ppostgres-it verify` desde `backend`; `mvn package` por sí solo
no ejecuta toda la integración. Ejecute `npm test` y `npm run build` desde
`frontend`, y las suites Python con `python3 -m unittest discover -s tests/ops`
y `python3 -m unittest discover -s tests/smtp` desde la raíz.

[Pruebas de navegador](../tests/e2e/README.md) documenta el comando de los cuatro
escenarios de 1.8.3 y las herramientas auxiliares históricas. El workflow
`.github/workflows/verify-release-183.yml` conserva los pasos exactos, incluida
la prueba SMTP y la generación del paquete. Los reintentos Maven están limitados
a fallos de descarga de dependencias; un fallo de prueba detiene la ejecución.
