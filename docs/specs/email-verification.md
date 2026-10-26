# Especificación: verificación de email en el registro

## Objetivo

Exigir que las cuentas locales demuestren control de su correo antes de poder iniciar sesión. El usuario solicita el registro, recibe un código numérico de seis dígitos, lo ingresa en Android y, solo si es válido, se crea la cuenta. La confirmación no inicia sesión automáticamente: al terminar, la app vuelve al login.

## Stack y estructura

- Backend: Java 21, Spring Boot 4.0.6, Spring Security, PostgreSQL y Flyway.
- Android: Kotlin, Jetpack Compose y cliente Retrofit generado desde OpenAPI.
- Backend: `src/main/java/com/elias/mismonedas/identity`, migraciones en `src/main/resources/db/migration`, pruebas en `src/test/java`.
- Android: `app/src/main/java/com/elias/mismonedas/ui/screens/auth`, `ui/viewmodel` y `data/repository`; pruebas en `app/src/test`.
- Contrato: endpoints documentados por Springdoc; contrato/cliente Android regenerados con `scripts/sync-openapi.ps1`. No editar artefactos generados a mano.

## Contrato y comportamiento

- `POST /api/v1/auth/register` inicia la verificación y responde `202 Accepted`; no crea usuario ni emite tokens.
- `POST /api/v1/auth/register/verify` recibe los datos de registro y el código. Si la verificación es válida, crea el usuario local con `email_verified=true` y responde `204 No Content`.
- `POST /api/v1/auth/email-verification/resend` solicita un código para una cuenta local existente sin verificar; su respuesta no revela si el correo existe.
- `POST /api/v1/auth/email-verification/confirm` confirma una cuenta local existente sin verificar y responde `204 No Content`.
- Login con contraseña correcta pero correo local no verificado responde `401` con código `EMAIL_NOT_VERIFIED`, sin emitir tokens. Android ofrece reenvío e ingreso del código; luego vuelve al login.
- Login/registro Google mantiene la validación vigente de `email_verified` del proveedor y no usa OTP.

## Reglas y seguridad

- Código criptográficamente aleatorio, exactamente seis dígitos (se permiten ceros iniciales), válido durante 15 minutos, máximo cinco intentos fallidos y espera mínima de 60 segundos para reenvío. Agotar intentos conserva el desafío bloqueado hasta que expire; el reenvío debe seguir respetando el cooldown.
- Persistir solo el desafío temporal: correo normalizado, propósito, HMAC del código con clave secreta, vencimiento, intentos y marca de último envío. Nunca persistir el código en claro ni el borrador de registro. El HMAC se contextualiza con email y propósito; no se usa un hash simple ni un hash adaptativo costoso para OTP.
- Un código vencido no crea ni habilita cuenta. La fila vencida deja de ser utilizable de inmediato y se elimina mediante limpieza programada.
- Serializar la primera emisión por email/propósito con lock transaccional PostgreSQL; las filas existentes se leen con lock y timeout acotado. Persistir el desafío y confirmar la transacción antes de invocar SMTP; si el envío falla, invalidar el desafío solo si sigue siendo el mismo.
- Validar email normalizado, longitud máxima 320, una sola dirección estricta y código de seis dígitos antes de consultar/alterar el desafío. Aplicar además el límite per-IP ya existente en Nginx y un límite global configurable de envíos para proteger el SMTP compartido.
- Android conserva los campos del formulario solo en memoria hasta confirmar/reintentar. Si se pierde el estado de pantalla, la persona vuelve a completar el formulario.
- El email de verificación es transaccional y no depende de la preferencia de notificaciones. SMTP debe usar timeouts finitos y validar identidad TLS; los errores de envío no deben dejar un desafío que bloquee el reenvío.
- Las respuestas de inicio/reenvío no deben revelar la existencia de una dirección. El correo debe normalizarse como en el registro actual.
- No se requiere compatibilidad con cuentas o clientes de producción; se conserva Flyway como mecanismo normal de esquema.

## Estilo

Seguir los servicios, DTOs, manejo de errores, migraciones Flyway y componentes Compose existentes. Ejemplo conceptual del criterio de persistencia:

```java
if (!challenge.isValidAt(now) || !passwordEncoder.matches(code, challenge.getCodeHash())) {
    throw new InvalidVerificationCodeException();
}
```

La verificación debe ocurrir antes de invocar la creación del usuario o emitir tokens.

## Estrategia de pruebas

- Backend unitario: emisión/hash, expiración, intentos, cooldown, reenvío y confirmación; SMTP sustituido por un fake/mock, sin enviar correos reales.
- Backend integración/API: registro no crea usuario ni tokens; código válido crea una única cuenta verificada; login local no verificado no emite tokens; Google conserva el comportamiento existente; OpenAPI incluye los contratos.
- Android: estado de registro pendiente, entrada limitada a seis dígitos, reenvío con espera, confirmación que vuelve al login y manejo de `EMAIL_NOT_VERIFIED`.
- Comandos: `./mvnw test`; `./gradlew.bat :app:testDebugUnitTest`; `./gradlew.bat :app:assembleDebug`; cliente generado: `./gradlew.bat test` desde `kotlin-client`.

## Límites

- Siempre: no emitir tokens antes de verificar; no enviar correo real durante pruebas; cubrir lógica nueva con pruebas; generar OpenAPI/cliente desde la fuente.
- Pedir aprobación antes de: añadir dependencias o cambiar el flujo ya aprobado (código de seis dígitos, 15 minutos, cinco intentos, espera de 60 segundos, login posterior).
- Nunca: guardar OTP en claro, guardar el borrador de registro en el backend, editar OpenAPI/cliente Kotlin generados manualmente, ni enviar correos a direcciones reales en pruebas.

## Criterios de éxito

1. Antes de confirmar el código no existe fila de usuario, billetera, preferencias ni tokens de sesión.
2. Un código válido crea una cuenta local verificada; expirado, incorrecto, agotado o reutilizado no la crea. Agotar intentos no elimina la marca temporal del cooldown.
3. Una cuenta local no verificada no obtiene sesión; puede reenviar y confirmar, y después ingresar mediante login normal.
4. Android implementa el flujo completo y conserva el borrador solo en memoria.
5. Google sigue funcionando y los tests de backend, Android y cliente generado pasan.

## Decisiones confirmadas

- OTP de seis dígitos, 15 minutos, cinco intentos fallidos, cooldown de reenvío de 60 segundos.
- Crear la cuenta solo después de confirmar; luego autenticar con el formulario normal de login.
- No considerar compatibilidad con datos reales/prod.
- Reutilizar SMTP existente; no enviar correos reales en pruebas.
- HMAC OTP con clave secreta; enviar solo tras commit; persistir límites de intentos y reenvío sin mantener una transacción abierta durante SMTP.
