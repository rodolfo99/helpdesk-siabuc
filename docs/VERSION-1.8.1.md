# SIABUC Helpdesk 1.8.1 — versión de integración

> **Documento histórico — 1.8.1.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.3.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.3](MANUAL-EXTENDIDO-1.8.3.md), el [índice de documentación](README.md) y las [notas de 1.8.3](VERSION-1.8.3.md).

Fecha: 14 de octubre de 2026. Se integra la evolución 1.8 sobre Helpdesk 1.7, sin aprobar las propuestas preliminares del Centro SIABUC ni cambiar mecanismos de identidad o contabilidad.

## Cambios
- Nueva API de tickets para clientes con asociación institucional desde sesión, validaciones y revisión previa.
- Confirmación con folio, guía manual inicial, acceso a conocimientos y filtros de fechas para soporte.
- Nuevas categorías editoriales con migración Flyway `V25__knowledge_help_categories.sql`, aditiva y sin alterar migraciones históricas.
- Corrección de tipos Angular `KnowledgeCategory` frente a categorías de casos, que causó TS2345.
- Corrección del nombre JAR 1.8.1 en Maven, Dockerfile y scripts operativos, con versiones npm sincronizadas.
- [Manual extendido por rol](MANUAL-EXTENDIDO-1.8.1.md) y [evidencia de validación](VALIDACION-1.8.1.md).

## Alcance reservado / no aprobado
No están integrados: acceso por póliza y correo, Federación UCOL, recuperación por clave de licencia, cambio de correo de licencia, licencias SIABUC9, descargas oficiales, nuevos estados de ticket, cambios en apertura sin saldo, GA4, HTTPS institucional ni migración de datos externos.

## Ejecución
Conservar `.env`, `COMPOSE_PROJECT_NAME`, volúmenes y respaldos. Antes de iniciar la nueva versión, respaldar la base y comprobar un arranque y restauración con datos de prueba. No ejecutar `docker compose down -v`.
La rama GitHub conserva los recursos tipográficos originales para PDF; los paquetes mínimos distribuidos por ZIP pueden recuperarlos desde un commit fijado y comprobar su SHA-256 según su configuración de construcción.

## Calidad y limitaciones
Angular compiló en CI, se ejecutaron 225 pruebas Java en PostgreSQL 17 (223 correctas, 2 fallidas) y las pruebas de scripts pasaron. El CI general marcó fallo. Permanecen pendientes las pruebas integrales en el ambiente real y la resolución de las incidencias documentadas. La integración de fuentes en `main` no equivale al despliegue ni a su aceptación institucional.
