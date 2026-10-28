# SIABUC Helpdesk 1.8.1 — resultados de validación

> **Documento histórico — validación de 1.8.1 del 14 de octubre de 2026.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.5.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), el [índice de documentación](README.md) y las [notas de 1.8.5](VERSION-1.8.5.md).

Fecha: 14 de octubre de 2026. Fuentes preparadas por GitHub Actions desde el commit 2c2a39ca7a0ac60795111a7aa6be9d10b644c42c.

- Angular: compilación completa y pruebas del frontend terminadas satisfactoriamente.
- Backend: Java compilado y JAR empaquetado; se ejecutaron 225 pruebas (10 unitarias y 215 de integración) sobre PostgreSQL 17 nativo y Flyway. 223 satisfactorias y 2 fallidas, sin errores ni omisiones.
- Python: paso de CI satisfactorio. Verificación local adicional: 49 pruebas de operación y 13 de SMTP satisfactorias.
- ZIP: CRC y todos los SHA-256 del manifiesto verificados; 319 entradas, incluyendo el manifiesto. Versiones de Maven, Docker, npm y scripts coherentes con 1.8.1.

## Fallos que impiden considerar satisfactoria toda la suite

1. HelpdeskIntegrationIT.lastAdministratorCannotBeDeactivated: la prueba esperaba un administrador activo y encontró 15. Requiere revisar el aislamiento de la suite; no se ha modificado la protección de la última cuenta administradora.
2. InstitutionStatementIntegrationIT.concurrentDuplicateEmissionReturnsOneDocumentOrExplicitRetryConflict: obtuvo HTTP 503 donde la prueba admite 200 o 409. Requiere revisar el comportamiento concurrente de la emisión; no se descarta como simple problema de pruebas sin investigarlo.

No se afirma que las 225 pruebas hayan pasado. La ejecución global de CI terminó con fallo. No se ha probado el arranque Docker Compose completo, Windows nativo, despliegue productivo, respaldo/restauración del destino ni navegación integral.

## Instalación

Descomprimir en una carpeta nueva. Conservar .env y volúmenes, y mantener COMPOSE_PROJECT_NAME. No ejecutar docker compose down -v. Antes de actualizar una instalación operativa, usar una base de pruebas y revisar los dos fallos anteriores.

La configuración de red permanece limitada a 127.0.0.1. Para acceso LAN, cambiar únicamente la publicación del puerto del servicio frontend; no abrir indiscriminadamente la base de datos ni Spring.

## Evidencia

Ejecución: https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/38611221528
El ZIP incluye docs/VALIDACION-1.8.1.json con los resultados completos por clase.