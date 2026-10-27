# Auditoría de verificación por email y borrado de cuenta

**Fecha:** 2026-10-26

**Repositorios auditados:** `G:\repos\finanx` (backend), `G:\repos\Finanx_frontend` (Android)

**Commits backend:** implementación de email/registro y borrado hasta `2402860`

**Commits Android:** retiro de reset-data hasta pruebas de auth/borrado en `d132b02`

**Tipo:** auditoría de código; no se hicieron cambios al documento histórico `docs/auditoria-mejoras.md`.

## Resumen

Los cambios principales existen, pero no están listos para producción. El OTP se implementó en backend, pero el flujo Android no permite completar el registro/verificación. El borrado programado tiene fallas de autorización, cancelación y eliminación de dependencias.

### Verificación ejecutada

| Comando | Resultado |
|---|---|
| `\.\mvnw.cmd test` en backend | 487 tests, 0 fallos |
| `\.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` en Android | 40 tests, 0 fallos |
| `\.\gradlew.bat assembleDebug` en Android | BUILD SUCCESSFUL |

Las pruebas Android recién añadidas usan mocks. No verifican navegación Compose, intercambio HTTP real ni el recorrido OTP/borrado de extremo a extremo. No se probó entrega SMTP real ni borrado contra una BD de despliegue.

## Inventario de implementación

### Backend

- **Email OTP:** entidad y migraciones V14/V15, código aleatorio de seis dígitos, hash HMAC, vencimiento de 15 minutos, cinco intentos, cooldown, rate limit global por nodo, locks PostgreSQL, limpieza programada, envío SMTP y endpoints de inicio/verificación/reenvío.
- **Registro/login:** `/auth/register` emite el OTP; `/auth/register/verify` crea al usuario verificado; el login local rechaza cuentas no verificadas. Google Sign-In no recibió el flujo OTP.
- **Borrado:** V16 agrega fechas de solicitud/programación; solicitud deshabilita la cuenta y revoca sesiones; existen endpoints de solicitud/cancelación y scheduler.
- **Reset-data:** endpoint y método de servicio fueron retirados del runtime.

### Android

- Cliente OpenAPI regenerado y métodos OTP en `AuthRepository`.
- `AuthViewModel` y `EmailVerificationScreen` implementan parte de los estados/pantalla OTP, pero `MainScreen` no presenta esa pantalla.
- `DeleteAccountScreen` solicita el borrado con advertencia de siete días. Repositorio y ViewModel tienen método de cancelación, pero ninguna pantalla lo invoca.
- Se retiró la UI de reset-data; quedaron referencias en documentación generada.

## Hallazgos y estado

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-01 | P1 | Los endpoints de solicitud/cancelación aceptan un `{id}` arbitrario para cualquier rol USER; no comprueban ownership. | Abierto |
| AUD-02 | P1 | No se puede cancelar el borrado ingresando: la cuenta ya está DISABLED antes de autenticarse; se revocan refresh tokens y el filtro rechaza access tokens existentes. | Abierto |
| AUD-03 | P1 | La eliminación física puede fallar por FKs a datos no limpiados (incluidas billeteras); el fallo revierte la transacción del lote. | Abierto |
| AUD-04 | P1 | Tras registrar, `VerificationPending` muestra LoginScreen. `EmailVerificationScreen` no tiene caller; no existe flujo Android para confirmar cuentas existentes. | Abierto |
| AUD-05 | P2 | El endpoint público de reenvío manda correo a cualquier dirección, sin comprobar que sea una cuenta local no verificada. | Abierto |
| AUD-06 | P2 | Cooldown, rate limit y fallo de entrega caen en el handler genérico 500; login consume `errorBody` antes de intentar parsearlo. | Abierto |
| AUD-07 | P2 | Las nuevas pruebas llamadas “Integration” son pruebas unitarias con mocks; no cubren los recorridos de UI/API. | Abierto |
| AUD-08 | P2 | El texto afirma que no se podrá volver a usar el email y que la acción no se puede deshacer, pero el servidor no reserva el email y sí pretende permitir cancelar durante siete días. | Abierto |
| AUD-09 | P2 | Artefactos aún anuncian reset-data: `apidoc/oas3v18.yaml`, colección Postman y `kotlin-client/README.md`; queda `UserDataResetEvent` sin uso runtime. | Abierto |
| AUD-10 | P2 / política | `/users/register` sigue siendo una ruta SYSTEM-only que crea usuario y emite tokens sin verificar email. No es un bypass para USER, pero hay que aclarar si esta excepción administrativa debe conservarse. | Requiere decisión |

### Prioridad de corrección

1. AUD-01: autorización por ownership, preservando SYSTEM para operaciones administrativas.
2. AUD-02: hacer realmente cancelable el borrado y exponer la cancelación en Android.
3. AUD-03: inventariar y limpiar las dependencias de usuario; proteger el scheduler contra rollback de lotes.
4. AUD-04: conectar la pantalla OTP para registro y para cuentas existentes, con pruebas de navegación/estado.
5. AUD-05/06: limitar abuso de correo y mapear errores a contratos HTTP accionables.
6. AUD-07/08/09: mejorar pruebas, corregir copy y retirar referencias obsoletas.
7. AUD-10: resolver con producto/seguridad antes de cambiar la ruta administrativa.

## Estado de hallazgos previos relacionados

Este documento nuevo no cambia el histórico `docs/auditoria-mejoras.md` ni cierra hallazgos ajenos a email/borrado.

- **P0-8 (registro sin verificación + vinculación Google): PARCIAL / SUPERSEDIDO EN PARTE.** El backend de registro local ahora tiene OTP y el login local revisa verificación, pero Android no permite completar el flujo. El riesgo de vinculación Google es independiente y no se considera resuelto por esta implementación.
- **P1-15 (reset/borrado): DEPRECATED / REEMPLAZADO PARA EL FLUJO ANTIGUO, NO TERMINADO FUNCIONALMENTE.** El endpoint reset-data fue retirado y el borrado inmediato se reemplazó por programación de siete días. La nueva eliminación/cancelación presenta AUD-01/02/03.
- **Reset-data:** retirado del runtime; sus referencias documentales/clientes siguen obsoletas (AUD-09).

## Criterios de término

- Un usuario USER no puede solicitar ni cancelar acciones sobre otro ID; SYSTEM conserva su alcance administrativo.
- Un usuario puede cancelar su borrado antes del vencimiento mediante el flujo definido y la app ofrece una ruta clara.
- El scheduler borra una cuenta vencida junto con todas sus dependencias; un error de una cuenta no bloquea el lote completo.
- Un usuario nuevo puede registrarse e ingresar el OTP desde Android; una cuenta existente no verificada puede confirmar correo por su flujo correspondiente.
- Los errores de cooldown/rate limit/entrega tienen estados HTTP y mensajes coherentes.
- Pruebas de regresión cubren autorización, transición/cancelación, limpieza de FK, navegación OTP y estado de error.
- OpenAPI/clientes/documentación se sincronizan usando los generadores/scripts existentes; no editar artefactos generados manualmente.

## Comandos de verificación

- Backend: `\.\mvnw.cmd test`
- Android: `\.\gradlew.bat :app:testDebugUnitTest`
- Android build: `\.\gradlew.bat assembleDebug`
- Sincronización OpenAPI Android: `\.\scripts\sync-openapi.ps1` desde el flujo/repositorio que ya lo usa.
