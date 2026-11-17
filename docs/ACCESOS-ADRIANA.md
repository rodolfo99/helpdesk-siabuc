# Accesos separados para clientes y personal SIABUC

Adaptación inicial basada en los requerimientos `RF-LOG-001` y `RF-LOG-002` enviados por Adriana Barreda Beltrán.

## Rutas

| Ruta | Público | Cuentas permitidas |
| --- | --- | --- |
| `/poliza` | Clientes de instituciones | `USER` |
| `/poliza/admin` | Personal del Centro SIABUC | `TECH`, `BILLING`, `ADMIN` |

La raíz `/` redirige a `/poliza` mediante Nginx. Las rutas sirven el mismo frontend Angular, que presenta un formulario y un mensaje distintos en cada una. El backend comprueba además el rol después de autenticar: si no pertenece al portal seleccionado, responde 403, invalida la sesión y no permite entrar en el portal incorrecto.

## Alcance y compatibilidad

- Esta **primera etapa mantiene usuario y contraseña** con las cuentas y roles existentes. No se cambia el esquema PostgreSQL ni las credenciales guardadas.
- La Federación de Identidades UCOL (`RF-LOG-004`) **no** está implementada.
- El acceso por número de póliza y correo registrado (`RF-LOG-003`) **no** está implementado. No se aceptan esos valores como sustitutos de contraseña.
- El endpoint previo `POST /api/auth/login` sin parámetro `portal` sigue disponible para clientes anteriores y suites de pruebas. Los nuevos formularios envían `portal=client` o `portal=staff`.
- La selección de portal es una comprobación adicional en el servidor. Los permisos de API siguen basándose en el rol autenticado y en la institución, no en la URL visitada.
- La redirección de la raíz pertenece al despliegue Nginx de Docker; en `ng serve`, la ruta `/` aún muestra el acceso del cliente. Ambos accesos funcionan como rutas del SPA gracias a su fallback.
- No se ha desplegado `https://siabuc.ucol.mx/poliza`; esa publicación exige configuración institucional de dominio, certificado y proxy. Los recursos Angular usan rutas absolutas bajo `/`, y el API sigue en `/api`, por lo que el proxy debe exponerlos en el mismo origen.

## Validación sugerida

1. Iniciar el proyecto y entrar en `/poliza`. Comprobar que aparece «Acceso de clientes».
2. Autenticarse con una cuenta `USER` y comprobar que se ve únicamente la institución y las funcionalidades del cliente.
3. Intentar iniciar sesión con una cuenta `ADMIN`, `TECH` o `BILLING` desde `/poliza`: debe aparecer un mensaje de error y ninguna sesión puede quedar activa.
4. Entrar en `/poliza/admin` y comprobar que aparece «Acceso de personal».
5. Autenticarse con `ADMIN`, `TECH` o `BILLING` y comprobar las restricciones actuales por rol.
6. Intentar ingresar con `USER` desde `/poliza/admin`: debe responder con error y sin sesión activa.
7. Con una sesión preexistente y un enlace al portal equivocado, comprobar que se redirige a la ruta correspondiente.
8. Comprobar que el acceso previo sin parámetro `portal` sigue funcionando para los clientes existentes.
9. Validar que el endpoint `/api/auth/me` devuelve 401 después del intento de acceso por el portal incorrecto.
10. Probar navegación directa y recarga de ambas rutas detrás de Nginx, así como pantallas móviles.

## Pruebas agregadas

`PortalLoginIntegrationIT.java` comprueba:
- Acceso de clientes con `USER` y rechazo de cuentas internas.
- Acceso del personal con `ADMIN` y rechazo de `USER`.
- Rechazo de nombres de portal desconocidos.
- Compatibilidad del inicio de sesión anterior.

Ejecutar en una **base de datos de pruebas aislada** con los requisitos documentados en `docs/OPERACION.md`:

```bash
cd backend
mvn -Ppostgres-it -Dtest=PortalLoginIntegrationIT verify
```

Esta guía no afirma que las pruebas nuevas ya se hayan ejecutado. La integración con proveedores institucionales requiere diseño de identidad, verificación adicional y pruebas de aceptación independientes.
