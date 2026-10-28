# Corrección de compilación de SIABUC Helpdesk 1.8.0

> **Documento histórico — corrección de 1.8.0 del 14 de octubre de 2026.** Se conservan el alcance, los procedimientos y los resultados de esa entrega. Las referencias a la versión «actual», a la «última» evidencia y a funciones o fallos pendientes corresponden a ese momento; no describen el estado de 1.8.5.
>
> Para uso e instalación vigentes, consulte el [manual detallado 1.8.5](MANUAL-EXTENDIDO-LICENCIAS.md), el [índice de documentación](README.md) y las [notas de 1.8.5](VERSION-1.8.5.md).

14 de octubre de 2026. Se conserva la versión 1.8.0 y la PR #12 en borrador;
esta corrección no integra cambios en main ni aprueba requerimientos institucionales.

## Error comunicado y causa

El usuario ejecutó Docker y Angular detuvo `npm run build` con TS2345 en tres
expresiones de `case-knowledge.component.ts`. Los artículos admitían doce
categorías editoriales, pero `categoryLabel` y el filtro importaban el tipo y
el catálogo de seis categorías de la ficha del caso. GUIDES, FAQ, SUPPORT,
POLICIES_LICENSES, TRAINING y CONTACT no pertenecen a ese tipo de casos.

## Cambios acotados

- `knowledge-models.ts` declara `KnowledgeCategory` y `KNOWLEDGE_CATEGORIES`;
  mantiene el alias editorial `CaseCategory` por compatibilidad con el otro
  componente de conocimiento. No amplía las categorías de la ficha de casos.
- `case-knowledge.component.ts` usa el tipo y catálogo editorial para las tres
  expresiones de etiqueta y para el filtro. Se conservan plantillas, permisos
  y operaciones de vinculación; no se usa `any` ni se desactiva strictTemplates.
- `backend/Dockerfile` copia `siabuc-helpdesk-1.8.0.jar`, coincidiendo con el
  finalName que ya define Maven. El archivo anterior todavía apuntaba a 1.7.0.
- `frontend/tests/knowledge-categories.test.mjs` añade cuatro pruebas de
  categorías, etiquetas, valor inicial y referencias del componente.

No cambia `.env`, la versión de dependencias, la autenticación, el SQL de las
migraciones, los roles, los volúmenes ni las reglas financieras.

## Evidencia ejecutada y límites

- Cuatro pruebas Node satisfactorias sobre copias de los archivos corregidos
  verificadas mediante sus hashes de blob de GitHub. Node 22.16.0 con
  `--experimental-strip-types`; las pruebas se incorporan a la suite existente.
- Reproducción aislada del conflicto TypeScript antes del cambio y resultado
  sin errores después del cambio, mediante tsc 5.8.3 en modo estricto, extrayendo
  el método categoryLabel del componente real. Esto no ejecuta el compilador de
  plantillas Angular ni equivale al build completo con la versión del lockfile.
- Comprobación del reparador local entregado en la conversación: validación
  previa, aplicación, repetición sin cambios, conservación de `.env` y rechazo
  de archivos con modificaciones no reconocidas antes de escribir.
- No ejecutados en esta revisión: npm ci/ng build completo, Docker, Maven,
  Flyway o PostgreSQL. En el contenedor no se resuelven los dominios externos
  necesarios para descargar dependencias; Docker y Maven no están instalados.
  No se afirma que el sistema completo haya arrancado.

## Reintento local

Actualizar los archivos de esta rama o aplicar el reparador recibido. Conservar
la corrección local del nombre del JAR si ya se había realizado.

Desde la carpeta del proyecto:

```bash
bash ./compilar.sh --docker
```

Este paso construye imágenes; no inicia servicios ni aplica migraciones a una
base existente. No borrar volúmenes ni ejecutar `docker compose down -v` para
resolver un error de compilación. Las pruebas de ejecución y migración deben
realizarse en una instalación desechable antes de actualizar datos importantes.
