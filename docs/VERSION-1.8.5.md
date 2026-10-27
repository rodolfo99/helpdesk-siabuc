# SIABUC Helpdesk 1.8.5 — actualización de seguridad

Preparada el 27 de octubre de 2026 sobre `979cbb5dcd94ab999214c056e50abe473743662c`.
La entrega se propone mediante una rama y PR; no implica despliegue productivo.

## Cambios

- Node.js **24.22.1** en el constructor Docker, `.nvmrc` y los workflows que
  compilan el cliente. Se conserva la rama 24 que ya utilizaba 1.8.4.
- Nginx **1.30.6-alpine** en la imagen final. Node y npm permanecen únicamente
  en la etapa de compilación; el servidor web entrega los archivos Angular.
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
  licencias, SMTP, datos ni preparación de permisos de respaldos.

## Verificación automática

El workflow [Dependency and image security](https://github.com/rodolfo99/helpdesk-siabuc/actions/workflows/security.yml)
se ejecuta en PR, cambios a `main`, manualmente y cada lunes a las 14:23 UTC.
Usa permisos de lectura; no publica imágenes ni despliega servicios externos.

- `npm ci` y `npm audit --audit-level=high` para **frontend y tests/e2e**,
  incluidas dependencias de desarrollo. Guarda el informe JSON incluso si falla.
  Una alerta alta/crítica o un error de consulta hace fallar el job; no se
  ignoran errores ni se limita el análisis a `--omit=dev`.
- Construye con `--pull` las imágenes finales de frontend, backend, backup y
  SMTP; descarga también la imagen de PostgreSQL declarada en Compose.
- Comprueba versión real de Node en el constructor, versión/configuración de
  Nginx, ausencia de Node/npm en la imagen web, respuesta HTTP, rutas SPA y
  cabeceras. Comprueba el usuario no root y JAR de la imagen backend.
- Escanea paquetes de sistema y aplicación de las cinco imágenes con Trivy.
  Las alertas **HIGH/CRITICAL** fallan incluso si aún no tienen corrección.
  Guarda informes e identidad de imágenes; no hay lista de exclusión de CVE.
  La acción de Trivy está fijada al commit de su release v0.36.0.
- Mantiene las regresiones existentes en el workflow `verify-release-184.yml`
  (nombre visible actualizado a 1.8.5): Java/PostgreSQL 17, Python, frontend,
  navegador con API simulada, Compose, SMTP y permisos de respaldos con Docker.

Dependabot revisa semanalmente npm en ambos directorios, los cuatro Dockerfile,
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

No hay Docker, Maven ni PostgreSQL nativo en el entorno local. La descarga de
Chromium para Playwright devolvió un ZIP inválido, por lo que no se atribuye
una ejecución local de las pruebas de navegador. Estas comprobaciones quedan
a cargo de los jobs de GitHub Actions y deben evaluarse por su **SHA y estado**.
Un job omitido, pendiente o fallido no equivale a una validación aprobada.

No se probaron Windows nativo, restauración de una base real, entrega externa
de correo ni instalaciones productivas. La ausencia de hallazgos en una
auditoría es el resultado de esa consulta, no una garantía absoluta de seguridad.
Los resultados históricos permanecen en [Validación](VALIDACION.md).

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
