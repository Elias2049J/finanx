# Especificación — P1-14: sesiones y access tokens

## Objetivo

Reducir la ventana de uso de un access token robado y hacer efectiva su revocación sin expulsar otros dispositivos. El backend emitirá JWT de 15 minutos ligados a una familia de refresh token ya persistida. Android renovará el par cuando expire el access token y devolverá al login si la renovación deja de ser válida.

## Decisiones confirmadas

- Access token: **900 segundos**. El refresh token conserva su expiración deslizante de 30 días y su rotación actual; no se añade un límite absoluto.
- Logout revoca la familia asociada al refresh token recibido; las otras familias del usuario siguen activas.
- Un cambio efectivo de contraseña revoca todas las familias del usuario.
- JWT sin el identificador de familia requerido se rechazan, incluidos los emitidos antes del despliegue. Android puede renovar usando el refresh token aún válido.
- El secreto JWT debe contener al menos 32 bytes UTF-8 y no puede ser un placeholder conocido. La aplicación falla al iniciar si no cumple.
- No se agrega una API de cambio de contraseña ni se cambia la forma de `AuthResponse`.

## Contrato y comportamiento

1. Cada token de acceso nuevo contiene `sid`, cuyo valor es el `familyId` de su refresh token. Login local, registro, Google y refresh usan el mismo mecanismo de emisión.
2. El `JwtDecoder` valida firma/emisor/expiración y el tipo/formato de `sid`. Antes de autorizar, `ActiveUserFilter` combina la lectura del usuario habilitado con la existencia de una fila refresh activa, no revocada y no vencida de esa misma familia. La consulta usa el datasource primario y no caché.
3. Refresh, logout, issue y revocación global comparten orden de locks `usuario → refresh token`; después de adquirir los locks se revalida el refresh. Una rotación no puede insertar una fila activa después de revocar la familia.
4. Logout conserva el flujo P1-12: revoca solo la familia recibida. Si el endpoint administrativo cambia efectivamente la contraseña, se revocan todas las familias; una actualización sin cambio no desconecta sesiones.
5. Flyway V9 crea/valida `refresh_tokens` desde una BD limpia y añade un índice único parcial que limita a una fila no revocada por familia. Aborta si encuentra duplicados activos; no los repara.
6. El entry point distingue JWT inválido (`401` + `WWW-Authenticate: Bearer error="invalid_token"`) de falta de credenciales y de fallos de infraestructura (`503`).
7. Android envía las rutas `/api/v1/auth/**` sin bearer y solo renueva una API protegida al recibir el marcador `invalid_token`, no ante cualquier 401. Serializa la renovación y reusa el token nuevo si otra petición ya rotó.
8. Android reintenta la petición original como máximo una vez y solo si el body puede reproducirse. Un refresh terminalmente inválido limpia sesión/UI y cancela llamadas en vuelo; errores de red/5xx preservan la sesión.
9. El timeout de inactividad de 30 minutos permanece sin cambios. Logging de red no incluye cuerpos ni credenciales/tokens.

## Stack, estructura y estilo

- Backend: Java 21, Spring Boot 4.0.6, Spring Security, Auth0 Java JWT, PostgreSQL 17/Flyway.
- Cliente: Android/Kotlin, OkHttp 4.12.0, Retrofit, Hilt y JUnit.
- Backend afectado: `identity/service`, `identity/security`, `identity/repository` y sus pruebas.
- Android afectado: `app/data/auth`, `app/data/api`, `app/ui/viewmodel` y pruebas unitarias.
- V1–V8 no declaran la tabla `refresh_tokens`. La BD local sí la contiene por creación fuera de Flyway; V9 debe hacer el esquema reproducible desde cero y validar sin modificar las filas existentes.
- La comprobación de familia se integra con la consulta de usuario que ya realiza `ActiveUserFilter`; no se agrega una segunda ida independiente a PostgreSQL por request.

Estilo esperado: preservar una familia al rotar refresh y asociarla al nuevo access token:

```java
String familyId = currentRefreshToken.getFamilyId();
return respond(user, rotatedRefreshToken, familyId);
```

La revocación se valida en el límite de autenticación; no se confía en estado local del cliente ni en claims sin firma.

## Pruebas y comandos

- Backend dirigido: `./mvnw.cmd -o test "-Dtest=JwtTokenServiceImplTest,AuthTokenServiceImplTest,JwtResourceServerConfigTest,UserServiceImplTest"`
- Backend completa/empaquetado: `./mvnw.cmd -o package`
- Android + cliente Kotlin: `./gradlew.bat :app:testDebugUnitTest :kotlin-client:test --rerun-tasks`
- Probar V9 desde esquema vacío y con tabla existente, duplicados activos (abort sin reparación), locks concurrentes, familias activa/revocada/vencida, JWT heredado, logout por dispositivo, cambio de contraseña y secretos inválidos.
- Probar `401 invalid_token` versus 401 genérico/403/503; refresh Android concurrente, auth routes sin bearer, bodies one-shot, retry único y refresh terminal/transitorio.
- Verificar que el endpoint de refresh sigue público y que APIs protegidas consultan el datasource primario.

## Límites

- Siempre: no loggear secretos, no cambiar los 30 días de refresh, no eliminar el timeout de inactividad; validar revocación en servidor.
- Aprobado para este cambio: 900 s, invalidación de JWT heredados y revocación de todas las sesiones ante cambio de contraseña.
- No hacer: crear un rol, endpoint de cambio de contraseña, tabla de blacklist separada, guardar tokens en claro, reintentar automáticamente un refresh antiguo tras ambigüedad de red, o versionar manualmente archivos OpenAPI/Kotlin generados.
- El `.env` local es configuración privada: si se actualiza `JWT_EXPIRATION_SECONDS` a 900 para la verificación local, no se stagea ni se imprime su secreto.

## Criterios de aceptación

- [ ] Todo access token nuevo expira a los 900 segundos y contiene `sid` de su familia refresh activa.
- [ ] Access tokens antiguos o con familia revocada/vencida reciben 401 `invalid_token`; revocar un dispositivo no invalida otra familia.
- [ ] Cambio efectivo de contraseña invalida las sesiones del usuario.
- [ ] Se rechaza en startup secreto JWT placeholder o menor de 32 bytes UTF-8, sin exponerlo en logs.
- [ ] Android renueva solo ante `invalid_token`, evita loops/races y bodies no reproducibles, conserva sesión ante red/5xx y limpia sesión ante refresh inválido.
- [ ] V9 crea el esquema desde cero, valida el existente y su índice impide nuevas familias activas duplicadas sin cambiar datos automáticamente.
- [ ] La suite backend y los tests Android/client Kotlin pasan; `.env` no aparece staged.

## Riesgos conocidos

- La autenticación depende de PostgreSQL primario; el control de familia se combina con la consulta de usuario existente y falla con 503 si la BD no está disponible.
- El despliegue invalida JWT heredados intencionalmente. Los clientes que no puedan usar el refresh token tendrán que autenticarse de nuevo.
- El Compose actual tiene una instancia. Un despliegue rodante con varios emisores exige drenar/actualizar todos los emisores antiguos antes de exigir `sid`.
- Si el servidor confirma una rotación pero se pierde la respuesta, reutilizar el refresh anterior activa la detección de replay existente y puede terminar la familia; no se incluye idempotencia de refresh en este alcance.
