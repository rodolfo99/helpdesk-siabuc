# Pruebas del sistema en navegador

La suite ejecuta **13 verificaciones secuenciales** de Angular, Spring y la base de datos. No intercepta ni simula las respuestas de la aplicación. Comprueba acceso con sesión, CRUD institucional con baja lógica, alta de usuario, conversión de horas a minutos, historial, notas internas, consumos independientes, corrección de consumos, cierre/reapertura, sobregiro, permisos y diseño adaptable. La preparación de la segunda institución y las comprobaciones de seguridad utilizan la API real desde la misma sesión del navegador.

**Se requiere una base de datos desechable de pruebas, separada de producción:** la suite crea instituciones, usuarios, casos e historial de auditoría y conserva esos registros al terminar. Cada ejecución usa nombres únicos; termina dando de baja la institución principal. No modifica el usuario administrativo suministrado. No elimina registros históricos ni restablece la base de datos automáticamente.

Con el sistema iniciado y un administrador configurado:

```bash
cd tests/e2e
npm ci
npx playwright install chromium
export E2E_BASE_URL=http://localhost:4200
read -r -p 'Usuario administrador de pruebas: ' E2E_ADMIN_USER
export E2E_ADMIN_USER
read -r -s -p 'Contraseña: ' E2E_ADMIN_PASSWORD
export E2E_ADMIN_PASSWORD
npm test
unset E2E_ADMIN_PASSWORD
```

Usa la dirección pública del cliente Angular, que debe reenviar `/api` a Spring. Ajusta el puerto a tu configuración; en Docker el predeterminado de la interfaz puede ser distinto. El proceso devuelve código 0 si todas las verificaciones pasan, o 1 ante un fallo. La contraseña del usuario institucional se genera en memoria en cada ejecución y no se imprime ni se guarda.

Variables opcionales:

| Variable | Uso |
| --- | --- |
| `E2E_REPORT_DIR` | Carpeta de diagnóstico para `results.json`; ante un fallo también guarda `failure.png`. Los diagnósticos pueden contener nombres e historial de pruebas. |
| `E2E_HEADLESS=false` | Muestra el navegador en equipos con entorno gráfico. |
| `E2E_CHROMIUM_PATH` | Ejecutable Chromium ya instalado, si no se usa el descargado por Playwright. |
| `E2E_CHROMIUM_ARGS` | Arreglo JSON de argumentos de lanzamiento para entornos controlados. |
| `E2E_PLAYWRIGHT_PATH` | Carpeta absoluta de un módulo Playwright ya instalado; omitir para usar la dependencia local. |

Los casos de uso se ejecutan secuencialmente porque verifican una misma historia de consumo y saldo. Las pruebas de concurrencia transaccional y los permisos adicionales se encuentran en el servidor; esta suite comprueba su integración con la interfaz.

## Ampliación 1.1: cobranza y notificaciones

Con las mismas variables de entorno y la migración 1.1 aplicada, ejecutar:

```bash
npm run test:billing-mail
```

La suite independiente `billing-mail.e2e.mjs` exige **`MAIL_ENABLED=false`** y lo comprueba antes de crear datos. Todos sus correos utilizan el dominio reservado `example.test`. Encola mensajes sin enviarlos; la entrega SMTP contra un sumidero local se comprueba en las pruebas del servidor.

Comprueba 12 escenarios: configuración sin secretos; cuentas de Cobranza y responsable creadas por interfaz; contactos y responsable institucional; permisos de Cobranza; pago y acreditación de 65/30 minutos por interfaz; idempotencia y referencia única; anulación administrativa por interfaz; asignación de casos; privacidad de notas internas y avisos de cierre; prueba al correo propio; recuperación de un aviso omitido; y acceso limitado del cliente. Captura el UUID de la petición real de pago para comprobar el reintento idempotente, sin simular respuestas.

Crea nuevas cuentas de pruebas y no cambia la cuenta administrativa suministrada. Conserva pagos, anulaciones, casos y cola para inspección. Si se define `E2E_REPORT_DIR`, genera `billing-mail-results.json`, capturas de la pantalla de pagos en escritorio y móvil de 390 píxeles (`payments-desktop.png` y `payments-mobile.png`) y, sólo ante fallo, `billing-mail-failure.png`. Las capturas contienen exclusivamente los datos de la instalación de pruebas; no se incluyen automáticamente en la distribución.

## Control de correo 1.3.1 con API simulada

`mail-control.ui.mjs` prueba la pantalla Angular con Chromium e intercepta la API: confirma desactivación, reactivación sólo para avisos nuevos, estado SUPPRESSED sin reintento, límite del servidor, errores, permisos visibles y diseño móvil. No usa credenciales reales ni envía correo. Es independiente de las suites con servidor real descritas arriba.

```bash
cd frontend
npm ci
npm run build
cd ../tests/e2e
npm ci
npx playwright install chromium
node mail-control.ui.mjs
```

Admite E2E_PLAYWRIGHT_PATH, E2E_CHROMIUM_PATH y E2E_CHROMIUM_ARGS para instalaciones de navegador propias. El resultado de esta prueba de interfaz no reemplaza las pruebas de permisos en Spring ni demuestra entrega SMTP.
