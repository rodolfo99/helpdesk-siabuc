# SIABUC Helpdesk 1.8.5 — actualización de seguridad

Preparada el 28 de octubre de 2026 sobre `979cbb5dcd94ab999214c056e50abe473743662c`.
La entrega se propone mediante una rama y PR; no implica despliegue productivo.

## Cambios

- Node.js **24.22.1** en el constructor Docker, `.nvmrc` y los workflows que
  compilan el cliente. Se conserva la rama 24 que ya utilizaba 1.8.4.
- Nginx **1.30.6-alpine** en la imagen final. Node y npm permanecen únicamente
  en la etapa de compilación; el servidor web entrega los archivos Angular.
  Se aplican además las actualizaciones disponibles de Alpine mediante
  `apk upgrade --no-cache`, necesarias para corregir Brotli y curl detectados
  dentro de la propia imagen 1.30.6.
- `@angular/build` y `@angular/cli` pasan de 21.2.25 a **21.2.28**. Los paquetes
  de la aplicación Angular continúan en 21.2.25. El lockfile actualiza las
  dependencias transitivas afectadas (incluidos Vite, MCP SDK, Zod, body-parser,
  fast-uri y qs), sin migrar de versión mayor de Angular.
- Override acotado de `@angular/build > undici` a **7.30.1**: build 21.2.28 aún
  fija 7.29.1, afectada por avisos de seguridad. Retirar este override cuando una
  versión compatible de build incorpore una versión corregida, repitiendo
  auditoría, compilación y regresiones. No se utiliza `npm audit fix --force`.
- Versión de aplicación/JAR/empaquetador **1.8.5**, con las funciones y las
  migraciones **V1–V27** conservadas. Sin cambios en reglas de negocio, roles,
  licencias, esquema de datos ni preparación de permisos de respaldos.
- Backend actualizado a **Spring Boot 4.0.9 / Spring 7.0.10 / Tomcat 11.0.27**
  para incorporar las correcciones públicas de Spring MVC. Se conserva Java 17
  y el contrato JSON mediante el módulo transitorio `spring-boot-jackson2`
  (Jackson 2.21.7), con deserialización estricta. PostgreSQL JDBC pasa a 42.7.14.
  Se adaptan los módulos Flyway, pruebas MockMvc y propiedades de errores.
  Jackson 2 sigue soportado como transición en Boot 4.0; su migración futura
  requiere una revisión propia del contrato JSON.
- PostgreSQL continúa en **17**: `db/Dockerfile` conserva el entrypoint, UID 70,
  directorio y volumen originales, y recompila **gosu 1.20 con Go 1.27.3**,
  desde un commit de fuente fijo. La imagen backup elimina su copia de gosu,
  que el worker Python no utiliza. Se actualizan los paquetes Alpine.
- SMTP pasa de Debian 13 a **Alpine 3.24.3** con Postfix, LMDB y Cyrus SASL.
  Se mantienen las variables, restricciones de relay y TLS, el volumen de cola
  y el ajuste obligatorio de permisos de Postfix; cambia la ruta de daemons.
  No se descartan mensajes ni se configuran excepciones de CVE.

## Verificación automática

El workflow [Dependency and image security](https://github.com/rodolfo99/helpdesk-siabuc/actions/workflows/security.yml)
se ejecuta en PR, cambios a `main`, manualmente y cada lunes a las 14:23 UTC.
Usa permisos de lectura; no publica imágenes ni despliega servicios externos.

- `npm ci` y `npm audit --audit-level=high` para **frontend y tests/e2e**,
  incluidas dependencias de desarrollo. Guarda el informe JSON incluso si falla.
  Una alerta alta/crítica o un error de consulta hace fallar el job; no se
  ignoran errores ni se limita el análisis a `--omit=dev`.
- Construye con `--pull` las cinco imágenes finales: frontend, backend, backup,
  SMTP y PostgreSQL derivado.
- Comprueba versión real de Node en el constructor, versión/configuración de
  Nginx, ausencia de Node/npm en la imagen web, respuesta HTTP, rutas SPA y
  cabeceras. Comprueba el usuario no root y JAR de la imagen backend.
- Escanea paquetes de sistema y aplicación de las cinco imágenes con Trivy.
  Las alertas **HIGH/CRITICAL** fallan incluso si aún no tienen corrección.
  Guarda informes e identidad de imágenes; no hay lista de exclusión de CVE.
  La acción de Trivy está fijada al commit de su release v0.36.0 y el scanner
  a v0.76.1.
- Mantiene las regresiones existentes en el workflow `verify-release-184.yml`
  (nombre visible actualizado a 1.8.5): Java/PostgreSQL 17, Python, frontend,
  navegador con API simulada, Compose, SMTP y permisos de respaldos con Docker.

Una prueba adicional usa una red interna y volúmenes desechables: inicia
PostgreSQL 17 oficial, crea registros, cambia a la imagen corregida, verifica
los datos y ejecuta `pg_dump`/`pg_restore` con la imagen backup. También arranca
el backend final sin privilegios con raíz de sólo lectura, aplica V1–V27 y
comprueba su salud. Para SMTP conserva un mensaje sintético en la cola al
cambiar de Debian a Alpine y comprueba salud, mapas LMDB privados y SASL.
No publica puertos ni entrega correo fuera de esa red.

Dependabot revisa semanalmente npm en ambos directorios, Maven, los cinco Dockerfile,
los archivos Compose y GitHub Actions. Propone PRs para revisión; no fusiona
automáticamente. Su configuración se activa al integrarse en la rama por
defecto. Al actualizar Node, mantener coherentes `.nvmrc` y el Dockerfile; la
comprobación del constructor detecta divergencias.

## Evidencia local de preparación

Ejecutada con Node 24.22.1 y npm 11.9.0 sobre los archivos de esta propuesta:

| Comprobación | Resultado |
| --- | --- |
| Auditoría frontend antes de corregir | 6 altas y 3 moderadas; ninguna crítica |
| `npm ci` y auditoría frontend después de corregir | Instalación correcta; 0 vulnerabilidades reportadas |
| `npm ci` y auditoría tests/e2e | Instalación correcta; 0 vulnerabilidades reportadas |
| `npm test` del frontend | 10 aprobadas |
| `npm run build` | Correcto; bundle inicial 818.67 kB |
| `python3 -m unittest discover -s tests/ops` | 70 descubiertas: 69 aprobadas, 1 omitida por restricciones de cambio de UID |
| `python3 -m unittest discover -s tests/smtp` | 13 aprobadas |

No hay Docker ni PostgreSQL nativo en el entorno local. Maven 3.9.9 se preparó
para compilar el backend; la integración y las imágenes se verifican en CI. La descarga de
Chromium para Playwright devolvió un ZIP inválido, por lo que no se atribuye
una ejecución local de las pruebas de navegador. Estas comprobaciones quedan
a cargo de los jobs de GitHub Actions y deben evaluarse por su **SHA y estado**.
Un job omitido, pendiente o fallido no equivale a una validación aprobada.

No se probaron Windows nativo, restauración de una base real, entrega externa
de correo ni instalaciones productivas. La ausencia de hallazgos en una
auditoría es el resultado de esa consulta, no una garantía absoluta de seguridad.
Los resultados históricos permanecen en [Validación](VALIDACION.md).

## Hallazgos de las imágenes y estado de revisión

El [primer escaneo CI](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/40585301717)
del head `5c082d4c7629735db55de37cd6ca70a236b59965` construyó las imágenes y
detectó alertas que una auditoría npm no ve:

- Frontend: Brotli y curl tenían parches disponibles; se añadió la actualización
  de paquetes Alpine y se repite el escaneo en el nuevo head.
- PostgreSQL y backup: el binario `usr/local/bin/gosu` heredado de
  `postgres:17-alpine` estaba compilado con Go 1.24.6. El informe registró una
  alerta crítica y 25 altas para su biblioteca estándar en cada imagen.
- SMTP: se registraron tres alertas críticas y 66 altas de paquetes Debian,
  sin versión corregida indicada por aquel informe. Incluyen LMDB, Python y
  util-linux. Son hallazgos del inventario; requieren evaluación de alcance,
  parches de distribución y alternativas compatibles, no demuestran por sí
  solos explotación a través de Helpdesk.

- Backend: el mismo escaneo detectó Brotli, Jackson, Tomcat, PostgreSQL JDBC
  y Spring MVC. Las correcciones públicas de Spring MVC requieren Spring 7;
  por eso se amplió el cambio a Boot 4.0.9, conservando el contrato JSON.

Estos hallazgos motivaron las correcciones anteriores, solicitadas expresamente
para incluir también las alertas inicialmente documentadas. Se repiten todos
los controles sobre la nueva revisión. Los resultados del primer head no
se atribuyen a esta ampliación: las regresiones iniciales pasaron, pero su
escaneo falló. La [PR #21](https://github.com/rodolfo99/helpdesk-siabuc/pull/21)
permanece en borrador hasta comprobar el head actualizado.

## Actualizar y empaquetar

Desde 1.8.4 no hay migraciones nuevas. Seguir el procedimiento de actualización
del [README](../README.md), conservando `.env`, nombre de proyecto Compose,
volúmenes, configuración SMTP y directorio de respaldos. Validar primero en
un entorno de pruebas y reconstruir las imágenes para incorporar las nuevas
bases; modificar sólo los fuentes no sustituye un contenedor ya ejecutándose.

Para desarrollo nativo, utilizar Node 24.22.1 (`nvm use` si se usa nvm).
La salida del backend es `backend/target/siabuc-helpdesk-1.8.5.jar`.
Desde un checkout Git:

```bash
python3 scripts/release-1.8.5.py --package --output release-output
```

Produce el ZIP de fuentes con manifiesto y SHA-256. No incluye credenciales,
datos ni dependencias compiladas. La PR no crea por sí sola una release pública.

## Fuentes

- [Node.js: correcciones del 21 de octubre de 2026](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases).
- [Avisos oficiales de seguridad de Nginx](https://nginx.org/en/security_advisories.html).
- [Opciones de Dependabot](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference).
- [Trivy Action v0.36.0](https://github.com/aquasecurity/trivy-action/releases/tag/v0.36.0).
- Los identificadores y enlaces de los avisos npm se incluyen en el JSON de auditoría de cada ejecución.

- [Correcciones de Spring MVC](https://spring.io/security/cve-2026-47884/).
- [Migración oficial a Spring Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Soporte de JSON en Boot 4](https://docs.spring.io/spring-boot/4.0/reference/features/json.html).
- [Versiones corregidas de Go](https://go.dev/doc/devel/release).
