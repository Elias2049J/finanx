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
| `.\mvnw.cmd test` en backend | 487 tests, 0 fallos |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` en Android | 40 tests, 0 fallos |
| `.\gradlew.bat assembleDebug` en Android | BUILD SUCCESSFUL |

Las seis pruebas Android unitarias añadidas anteriormente usan mocks. No verifican intercambio HTTP real ni recorridos completos contra el backend. Para AUD-02 se agregó una prueba instrumentada del acceso de cancelación; compila, pero no pudo ejecutarse porque `adb` no está disponible en este entorno. No se probó entrega SMTP real ni borrado contra una BD de despliegue.

**Actualización 2026-10-27 (AUD-01):** se añadió cobertura de ownership y `@PreAuthorize`; el test de regresión falló antes del cambio para los dos endpoints. Suite backend posterior: 492 tests, 0 fallos.

**Actualización 2026-10-27 (AUD-02):** login local y Google permiten cancelar una solicitud pendiente solo antes del vencimiento; la cancelación bloquea la fila y tokens/rutas de recursos siguen rechazando cuentas pendientes. El scheduler bloquea las filas candidatas y vuelve a evaluar la consulta tras una cancelación concurrente. Suite backend: 508 tests, 0 fallos. Android: 40 unit tests, `assembleDebug` y compilación instrumentada exitosos; el E2E instrumentado no se ejecutó por falta de `adb`.

**Actualización 2026-10-27 (AUD-03):** la eliminación programada limpia movimientos, presupuestos/metas, schedules/reglas, categorías/motivos, billeteras, contexto financiero, pagos/suscripciones, sesiones/mensajes de chat, notificaciones/FCM/outbox, desafíos de verificación de email, recibos/outboxes y objetos de imágenes con orden explícito de listeners. La prueba PostgreSQL ampliada cubre el grafo de dependencias; el scheduler aísla cada borrado en transacción independiente.

**Actualización 2026-10-27 (AUD-07 y sincronización):** se corrigió el contexto de la prueba de flujo OTP; ahora cubre inicio/verificación de registro, reenvío/confirmación para cuenta local existente no verificada y rechazo de login no verificado. Backend: `514 tests, 0 fallos`. Se sincronizó el contrato desde `/v3/api-docs.yaml` y se regeneró `kotlin-client`; `:app:testDebugUnitTest` y `:app:assembleDebug` pasan. Se eliminó `apidoc/oas3v18.yaml` y la solicitud antigua de registro SYSTEM de Postman.

## Inventario de implementación

### Backend

- **Email OTP:** entidad y migraciones V14/V15, código aleatorio de seis dígitos, hash HMAC, vencimiento de 15 minutos, cinco intentos, cooldown, rate limit global por nodo, locks PostgreSQL, limpieza programada, envío SMTP y endpoints de inicio/verificación/reenvío.
- **Registro/login:** `/auth/register` emite el OTP; `/auth/register/verify` crea al usuario verificado; el login local rechaza cuentas no verificadas. Google Sign-In no recibió el flujo OTP.
- **Borrado:** V16 agrega fechas de solicitud/programación; solicitud deshabilita la cuenta y revoca sesiones; existen endpoints de solicitud/cancelación y scheduler. La limpieza relacional está conectada por listeners ordenados y aislada por usuario/transacción.
- **Reset-data:** endpoint y método de servicio fueron retirados del runtime.

### Android

- Cliente OpenAPI regenerado y métodos OTP en `AuthRepository`.
- `MainScreen` presenta `EmailVerificationScreen` tras el registro y ante `EMAIL_NOT_VERIFIED`; la pantalla permite confirmar y reenviar códigos.
- `DeleteAccountScreen` solicita el borrado con advertencia de siete días. `LoginScreen` ofrece una acción explícita de recuperación que vuelve a autenticar; el método Android `cancelAccountDeletion` sigue sin caller porque el login cancela en backend.
- Se retiró la UI de reset-data, sus referencias de cliente y `UserDataResetEvent`.

## Hallazgos y estado

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-01 | P1 | Los endpoints de solicitud/cancelación aceptaban un `{id}` arbitrario para cualquier rol USER; no comprobaban ownership. | **Terminado 2026-10-27** |
| AUD-02 | P1 | No se podía cancelar el borrado ingresando: la cuenta DISABLED no podía autenticarse, y la cancelación competía con el scheduler. | **Implementado 2026-10-27; prueba instrumentada pendiente de ejecución** |
| AUD-03 | P1 | La eliminación física puede fallar por FKs a dependencias aún no limpiadas; el fallo revierte la transacción del lote. | **Implementado 2026-10-27 (AUD-03a/b/c); aislamiento por fila/transacción independiente** |
| AUD-04 | P1 | Tras registrar, `VerificationPending` muestra LoginScreen. `EmailVerificationScreen` no tiene caller; no existe flujo Android para confirmar cuentas existentes. | **Implementado 2026-10-27** |
| AUD-05 | P2 | El endpoint público de reenvío manda correo a cualquier dirección, sin comprobar que sea una cuenta local no verificada. | **Implementado 2026-10-27** |
| AUD-06 | P2 | Cooldown, rate limit y fallo de entrega caen en el handler genérico 500; login consume `errorBody` antes de intentar parsearlo. | **Implementado 2026-10-27** |
| AUD-07 | P2 | Las nuevas pruebas llamadas “Integration” son pruebas unitarias con mocks; no cubren los recorridos de UI/API. | **Implementado 2026-10-27 (flujos HTTP OTP y cancelación durante gracia)** |
| AUD-08 | P2 | El texto afirma que no se podrá volver a usar el email y que la acción no se puede deshacer, pero el servidor no reserva el email y sí pretende permitir cancelar durante siete días. | **Revisado 2026-10-27 (política: cancelable en 7 días, sin reserva de email)** |
| AUD-09 | P2 | Artefactos aún anuncian reset-data: `apidoc/oas3v18.yaml`, colección Postman y `kotlin-client/README.md`; queda `UserDataResetEvent` sin uso runtime. | **Corregido 2026-10-27 (artefactos y solicitud Postman retirados; evento huérfano eliminado)** |
| AUD-10 | P2 / política | `/users/register` sigue siendo una ruta SYSTEM-only que crea usuario y emite tokens sin verificar email. No es un bypass para USER, pero hay que aclarar si esta excepción administrativa debe conservarse. | **Implementado 2026-10-27 (eliminado; todo pasa por `/auth/register` + `/auth/register/verify`)** |

### Prioridad de corrección

1. AUD-01: autorización por ownership, preservando SYSTEM para operaciones administrativas.
2. AUD-02: **implementado**; queda ejecutar la prueba instrumentada en un entorno Android con `adb`.
3. AUD-03: **implementado** (limpieza por dominios + aislamiento por fila/transacción independiente).
4. AUD-04: **implementado**; OTP Android conectado para registro y cuentas existentes no verificadas.
5. AUD-05/06: **implementados**; reenvío limitado a cuentas elegibles y errores OTP con HTTP/Retry-After coherentes.
6. AUD-07/08/09: **implementados**; pruebas de flujo, copy corregido y artefactos obsoletos retirados.
7. AUD-10: **decisión aplicada**; se eliminó `/users/register`, y el alta local pasa por OTP.

## Estado de hallazgos previos relacionados

Este documento nuevo no cambia el histórico `docs/auditoria-mejoras.md` ni cierra hallazgos ajenos a email/borrado.

- **P0-8 (registro sin verificación + vinculación Google): PARCIAL / SUPERSEDIDO EN PARTE.** El registro local pasa por OTP y el login rechaza cuentas no verificadas; Google Sign-In permanece fuera de este flujo y su riesgo independiente no se considera resuelto aquí.
- **P1-15 (reset/borrado): DEPRECATED / REEMPLAZADO PARA EL FLUJO ANTIGUO.** El endpoint reset-data fue retirado y el borrado inmediato se reemplazó por programación de siete días; AUD-01/02/03 implementan ownership, cancelación y limpieza.
- **Reset-data:** retirado del runtime; quedan solo menciones históricas en auditorías anteriores.

## Criterios de término

- Un usuario USER no puede solicitar ni cancelar acciones sobre otro ID; SYSTEM conserva su alcance administrativo.
- Un usuario puede cancelar su borrado antes del vencimiento mediante el flujo definido y la app ofrece una ruta clara.
- El scheduler borra una cuenta vencida junto con todas sus dependencias; un error de una cuenta no bloquea el lote completo.
- Un usuario nuevo puede registrarse e ingresar el OTP desde Android; una cuenta existente no verificada puede confirmar correo por su flujo correspondiente.
- Los errores de cooldown/rate limit/entrega tienen estados HTTP y mensajes coherentes.
- Pruebas de regresión cubren autorización, transición/cancelación, limpieza de FK, navegación OTP y estado de error.
- OpenAPI/clientes/documentación se sincronizan usando los generadores/scripts existentes; no editar artefactos generados manualmente.

## Comandos de verificación

- Backend: `.\mvnw.cmd test`
- Android: `.\gradlew.bat :app:testDebugUnitTest`
- Android build: `.\gradlew.bat assembleDebug`
- Sincronización OpenAPI Android: `.\scripts\sync-openapi.ps1` desde el flujo/repositorio que ya lo usa.
