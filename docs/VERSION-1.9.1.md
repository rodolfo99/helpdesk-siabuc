# SIABUC Helpdesk 1.9.1

Revisión del 23 de noviembre de 2026. El paquete conjunto `SIABUC-Helpdesk-1.9.1.zip` conserva los fuentes, documentación y ocho pares de iniciadores Linux `.sh` y Windows `.bat`.

## Cambios

- El menú de Administración empieza por **Configuración de soporte → Usuarios → Instituciones → Licencias → Pólizas → Pagos → Casos de soporte**. Los módulos complementarios quedan debajo y se mantienen las condiciones de visibilidad de los cuatro roles.
- Usuarios conserva una sola entrada: se utiliza para crear personal y se vuelve después de registrar la institución para dar de alta al cliente. Lo mismo ocurre con Licencias al consultar el saldo. La pantalla inicial sigue siendo Resumen.
- Se incorpora la corrección de la [PR #55](https://github.com/rodolfo99/helpdesk-siabuc/pull/55): Nginx devuelve una redirección relativa a `/poliza`, conservando el host, puerto externo y esquema del navegador. Corrige el salto que perdía `4200` en el ZIP original 1.9.0.
- Se sincronizan VERSION, Maven/JAR, npm, iniciadores de desarrollo, empaquetador, documentación y comprobaciones CI en 1.9.1.

No se modifican permisos de API, credenciales, saldos ni reglas de pólizas/pagos. El esquema permanece en **V31**, sin nuevas migraciones ni cambios de dependencias. La identidad por póliza/correo/licencia y la Federación UCOL siguen aplazadas conforme al [alcance de Adriana](ACCESOS-ADRIANA.md).

El [manual de usuario](MANUAL-USUARIO.md#orden-del-menú-y-recorrido-inicial) describe la secuencia. Una licencia con saldo permite crear casos sin recorrer cobranza. Una póliza nueva no acredita horas; cada pago debe indicar las horas autorizadas y no duplicar una dotación ya registrada.

## Actualización desde 1.9.0

1. Conserve un respaldo comprobado, su `.env`, `COMPOSE_PROJECT_NAME`, los volúmenes y la carpeta de respaldos de la instalación existente.
2. Extraiga el paquete en otra carpeta y copie la configuración privada necesaria, revisando las rutas relativas. No cree una base nueva si desea conservar la existente.
3. Compile con `./compilar.sh --docker` en Linux o `.\compilar.bat --docker` en Windows. El backend generado se llama `siabuc-helpdesk-1.9.1.jar`.
4. Después de validar una copia de prueba, detenga la instalación anterior e inicie la nueva con `./iniciar.sh` o `.\iniciar.bat`, usando el mismo proyecto y volúmenes. Una base 1.9.0 ya está en V31 y no necesita migraciones adicionales.
5. Verifique orden del menú, acceso por rol, creación/consulta de casos y redirección. Para instalaciones anteriores a 1.9.0, revise también sus [migraciones y compatibilidad](VERSION-1.9.0.md#actualización-desde-185).

En local, personal interno accede a `http://127.0.0.1:4200/poliza/admin` y clientes a `http://127.0.0.1:4200/poliza`, ajustando FRONTEND_PORT si corresponde. El puerto local sirve HTTP; HTTPS institucional requiere proxy y certificado. La redirección corregida no añade TLS ni cambia las preferencias de Firefox.

## Validación

La entrega se comprueba con los workflows [verificación integral](https://github.com/rodolfo99/helpdesk-siabuc/actions/workflows/verify-release-184.yml), [Linux/Windows](https://github.com/rodolfo99/helpdesk-siabuc/actions/workflows/verify-launchers.yml) y [seguridad](https://github.com/rodolfo99/helpdesk-siabuc/actions/workflows/security.yml). Cada ejecución identifica el commit validado; los resultados de otra revisión no sustituyen los checks de esta entrega.

La regresión Nginx sigue la redirección desde un puerto Docker distinto de 80, comprueba el destino y el contenido Angular, y exige cabeceras relativas con distintos Host. Las suites de navegador de licencias y casos utilizan API simulada; no acreditan una sesión institucional con datos reales. Java se verifica aparte con PostgreSQL en CI.

Se mantienen las auditorías completas y sus umbrales. El aviso de `picomatch` del 17 de noviembre sigue conservado como evidencia histórica en [1.9.0](VERSION-1.9.0.md#validación-y-seguridad). El [workflow de seguridad de la corrección de redirección](https://github.com/rodolfo99/helpdesk-siabuc/actions/runs/44413210056) pasó el 18 de noviembre; esta revisión requiere su propia ejecución, sin atribuirle de antemano ese resultado.

## Paquete reproducible

Desde el checkout Git de la versión:

```bash
python3 scripts/release-1.9.1.py --package --output release-output
```

En Windows puede usarse `py -3`. Se generan el ZIP y su SHA-256; el ZIP contiene `MANIFEST-SHA256.txt`, conserva permisos de los `.sh` y los `.bat` con finales CRLF. Excluye `.env`, bases, respaldos, dependencias y compilados. Requiere compilar las imágenes para instalarse.
