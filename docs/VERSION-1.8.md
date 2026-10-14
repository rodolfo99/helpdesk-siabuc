# SIABUC Helpdesk 1.8.0 — mejoras autónomas y compatibles

Fecha de alcance: 14 de octubre de 2026. Implementación técnica **sin aprobar**
la versión preliminar de requerimientos recibida por correo.

## Incluido

- La SPA existente puede responder a rutas locales `/poliza` y `/poliza/admin`
  por su fallback de Nginx. **No hay aún portales de autenticación separados**:
  se conserva el inicio de sesión de Spring Security.
- Bienvenida contextual, navegación diferenciada, enlaces de información pública
  existentes y acceso al centro de ayuda desde cualquier sección del cliente.
- Guía de primeros pasos accesible manualmente desde el resumen del cliente,
  sin guardar una preferencia arbitraria por póliza/usuario ni activación automática.
- Resumen de casos existente y filtro local de fechas para el personal de soporte.
  Queda pendiente vincular cada tarjeta del resumen a un folio concreto.
- Creación de solicitudes cliente mediante `/api/client-portal/tickets`:
  longitud 5–150/20–5000 comprobada también por servidor, asignación automática
  de institución desde sesión y confirmación de folio; se conservan las reglas
  preexistentes de saldo, CSRF, historial, visibilidad y transacciones.
- Nuevas categorías editoriales de ayuda mediante V25, sin modificar categorías,
  artículos o revisiones anteriores.
- Conservación de roles ADMIN, TECH, USER, BILLING y funcionalidades 1.7.

## Pendiente de resolución institucional o de terceros

**No implementado, aprobado ni simulado como operativo:** Federación de
Identidades UCOL, ingreso de clientes por número de póliza/correo, recuperación
de información mediante clave MX de licencia, verificación del cambio de correo,
modelo y fuente oficial de licencias SIABUC9, asociación póliza/licencia, nuevas
reglas de acceso por póliza, catálogo oficial de descargas, despliegue real
bajo siabuc.ucol.mx, certificado TLS institucional, GA4/consentimiento,
migración de fuentes externas, ampliación a cinco estados o cambio del modelo
de minutos y apertura sin saldo. El requisito de primera visita no se
automatiza: hay dos reglas contradictorias en el borrador.

La aplicación local sigue funcionando por `/`, `/api` y
`http://127.0.0.1:4200`; los alias no significan que las URLs
institucionales estén publicadas. El proxy institucional debe decidir y
validar la distribución real de recursos y rutas.

## Instalación y actualización

1. Respaldar PostgreSQL, SMTP y `.env` antes del cambio. No usar `down -v`.
2. Conservar volúmenes y parámetros de la versión instalada.
3. Actualizar código e iniciar como en 1.7; Flyway agrega
   `V25__knowledge_help_categories.sql`.
4. Revisar casos, pólizas, saldos, adjuntos, roles y portales sin habilitar
   permisos o acceso financiero adicional.
5. Ejecutar pruebas del backend y frontend contra una base desechable,
   usando las instrucciones de `docs/VALIDACION.md`.

Para generar el ZIP completo de fuentes en cualquier copia de esta rama use
`python3 scripts/package-1.8.py` (Windows: `py scripts/package-1.8.py`).
El empaquetador crea un manifiesto SHA-256 nuevo dentro del ZIP y valida su CRC.
El archivo histórico `MANIFEST-SHA256.txt` de la raíz corresponde a 1.7;
no se utiliza para validar esta entrega. **El ZIP creado no es una compilación
ni certifica pruebas de ejecución.**

El ZIP de fuentes `SIABUC-Helpdesk-1.8.0.zip` no debe contener credenciales,
entornos locales, datos productivos, `node_modules`, `target` o `dist`.

## Verificación

La documentación de 1.7 describe la última evidencia efectivamente ejecutada;
**no se atribuyen esos resultados a 1.8**. Los nuevos cambios exigen compilación
Java y Angular, pruebas de roles y aislamiento, migración V25 de PostgreSQL
17 y regresión antes de producción.
