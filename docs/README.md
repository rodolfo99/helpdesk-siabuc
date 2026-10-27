# Documentación de SIABUC Helpdesk 1.8.4

**Manual integrado de 1.8.4:** [Manual extendido con licencias](MANUAL-EXTENDIDO-LICENCIAS.md).

**Licencias incluidas en 1.8.4:** consulte [Licencias y horas predeterminadas](LICENCIAS-HORAS.md). Las pólizas pueden vincularse a una licencia y sus pagos acreditan esa bolsa; los saldos anteriores permanecen sin licencia.

El [manual integrado 1.8.4](MANUAL-EXTENDIDO-LICENCIAS.md) es la guía principal de uso,
instalación y operación. Estas guías describen el código de la versión indicada
en `VERSION`; los resultados de pruebas se asocian a una revisión concreta en
[Validación](VALIDACION.md). La existencia de un procedimiento no acredita que
se haya ejecutado en el servidor de destino.

## Empezar y actualizar

| Necesidad | Documento vigente |
| --- | --- |
| Instalación limpia en Ubuntu con Docker Compose | [Instalación desde cero](INSTALACION-DESDE-CERO.md) |
| Recorrido completo por rol | [Manual integrado 1.8.4](MANUAL-EXTENDIDO-LICENCIAS.md) |
| Actualización y permisos de respaldos en el arranque | [Versión 1.8.4](VERSION-1.8.4.md) |
| Uso cotidiano y primeros pasos | [Manual de usuario](MANUAL-USUARIO.md) |
| Cinco estados, migración V26 y eliminación de la red SMTP dedicada | [Cambios y actualización 1.8.3](VERSION-1.8.3.md) |
| Instalación, compilación y operación Linux/Docker | [Operación](OPERACION.md) |
| Instalación y comandos Windows | [Windows](WINDOWS.md) |
| Modelo, seguridad y servicios | [Arquitectura](ARQUITECTURA.md) |
| Integración con el servidor | [Contrato API](API-CONTRACT.md) |

## Soporte y atención

| Tema | Guía |
| --- | --- |
| Permisos del personal de soporte | [Rol técnico](ROL-TECNICO.md) |
| Clasificación, ficha técnica y plazos laborales | [Clasificación y SLA](CLASIFICACION-SLA.md) |
| Medición de trabajo y citas | [Cronómetro y agenda](CRONOMETRO-AGENDA.md) |
| Filtros personales y alertas de vencimiento | [Vistas y alertas](VISTAS-ALERTAS.md) |
| Respuestas reutilizables | [Plantillas](PLANTILLAS-RESPUESTA.md) |
| Problemas compartidos entre instituciones | [Incidencias](INCIDENCIAS.md) |
| Artículos publicados y soluciones de casos | [Base de conocimientos](BASE-CONOCIMIENTOS.md) |
| Evaluación después del cierre | [Encuestas](ENCUESTAS-SATISFACCION.md) |
| Campana, respuestas pendientes y saldos | [Avisos internos](AVISOS-INTERNOS.md) |
| Consumos, filtros y exportación | [Reportes técnicos](REPORTES.md) |

Los estados del caso son Recibido, En atención, Requiere respuesta del cliente,
Resuelto y Cerrado. Los tres primeros se agrupan como pendientes de resolver.
Los estados financieros, de incidencias, citas y notificaciones pertenecen a
sus propios módulos y no se sustituyen por los estados de soporte.

## Administración financiera

| Tema | Guía |
| --- | --- |
| Contratos, pagos y acreditación explícita de horas | [Pólizas y cobranza](POLIZAS-COBRANZA.md) |
| Tarifas reutilizables y versiones | [Paquetes y tarifas](PAQUETES-TARIFAS.md) |
| Propuestas comerciales y vencimientos | [Cotizaciones y renovaciones](COTIZACIONES-RENOVACIONES.md) |
| Verificación y aprobación de pagos pendientes | [Solicitudes de pago](SOLICITUDES-PAGO.md) |
| Consulta habilitada para cada institución | [Portal financiero](PORTAL-FINANCIERO.md) |
| Archivos de evidencia | [Comprobantes](COMPROBANTES-PAGO.md) |
| PDF administrativo sin validez de factura fiscal | [Recibos](RECIBOS-PAGO.md) |
| Compromisos y aplicación de abonos | [Parcialidades](PARCIALIDADES.md) |
| Importación y confirmación de coincidencias | [Conciliación CSV](CONCILIACION-BANCARIA.md) |
| Documentos emitidos con fecha de corte | [Estados de cuenta](ESTADOS-CUENTA.md) |
| Ingresos, pólizas y exportaciones | [Reportes de cobranza](REPORTES-COBRANZA.md) |

## Correo, respaldos y verificación

| Tema | Guía |
| --- | --- |
| Configuración, cola y significado de cada estado de correo | [Correo y notificaciones](CORREO.md) |
| Envío directo desde Spring o relay por Postfix | [Gmail/Google Workspace](GMAIL-UCOL.md) |
| Servicio opcional en la red predeterminada de Compose | [SMTP interno](SMTP-INTERNO.md) |
| Pausa persistente y reanudación sólo de eventos nuevos | [Desactivar correo](DESACTIVAR-CORREO.md) |
| Copias, recuperación y retroceso de versión | [Respaldo y restauración](RESPALDO-RESTAURACION.md) |
| Calendario, retención y diagnóstico del worker | [Respaldos automáticos](RESPALDOS-AUTOMATICOS.md) |
| Evidencia por revisión y sus límites | [Validación 1.8.3](VALIDACION-1.8.3.md) · [Historial](VALIDACION.md) |
| Pruebas con API real y con API simulada | [Pruebas de navegador](../tests/e2e/README.md) |

## Archivo histórico

Los documentos siguientes conservan las funciones, comandos y resultados de
su propia versión. Para operar 1.8.4 use las guías anteriores; las instrucciones
históricas pueden citar otros JAR, roles, redes o estados.

- [Migración 1.0 a 1.1](MIGRACION-1.1.md).
- [Versión 1.3](VERSION-1.3.md), [1.4](VERSION-1.4.md) y [funciones de soporte 1.4](HELPDESK-1.4.md).
- [Versión 1.5](VERSION-1.5.md), [1.6](VERSION-1.6.md) y [1.7](VERSION-1.7.md).
- [Alcance 1.8](VERSION-1.8.md) y [corrección de compilación 1.8](CORRECCION-COMPILACION-1.8.md).
- [Versión 1.8.1](VERSION-1.8.1.md), [manual 1.8.1](MANUAL-EXTENDIDO-1.8.1.md) y [validación original 1.8.1](VALIDACION-1.8.1.md).
- [Introducción de Gmail directo en 1.8.2](VERSION-1.8.2.md).

Los resultados históricos no se sustituyen por los de una ejecución posterior.
Las propuestas institucionales aún no implementadas se distinguen de las
funciones disponibles en el README y el manual vigente.
