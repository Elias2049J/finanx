# Especificación y plan — P1-11/12/13 de notificaciones

## Objetivo y criterios de aceptación

Hacer fiables las notificaciones SMTP/FCM sin mantener transacciones abiertas durante llamadas externas, impedir que un token FCM quede asociado a cuentas distintas, y recuperar fallos desde la DLQ con límites explícitos. El logout del Android debe revocar la sesión y eliminar solo el token del dispositivo que la cerró.

Se considera listo cuando:

1. El claim y la finalización de cada canal se persisten en transacciones separadas del envío externo; una entrega concurrente no adquiere el mismo claim.
2. Una clave estable identifica cada combinación outbox/canal. El contrato es *at-least-once*, no exactly-once.
3. Una migración Flyway impone unicidad global de FCM y aborta con diagnóstico si ya hay tokens compartidos; no corrige datos automáticamente.
4. Registrar un token existente lo transfiere al usuario autenticado; respuestas `INVALID_ARGUMENT` no borran tokens.
5. Logout conserva `refreshToken` requerido, acepta `fcmToken` opcional y elimina solo el token del propietario identificado por ese refresh token. El cliente Android actualiza el contrato y envía ambos valores.
6. Los eventos de negocio crean notificaciones después del commit; la purga usa el umbral solicitado.
7. La DLQ se consume; el outbox programa como máximo cinco reintentos (1, 2, 4, 8 y 16 minutos). Después queda `FAILED` para replay manual exclusivo de `SYSTEM`.

## Decisiones y límites

- Stack backend: Java 21, Spring Boot 4, PostgreSQL 17, RabbitMQ 4, Flyway y JUnit. Cliente: Android/Kotlin/Compose, OpenAPI y Kotlin Retrofit generado.
- Clave por envío: estable para `outboxId + channel`; puede correlacionar/deduplicar donde el proveedor lo soporte, pero no cierra la ventana de caída después de aceptación del proveedor.
- La migración de tokens detecta duplicados entre usuarios y aborta; nunca elige propietario automáticamente.
- El campo `fcmToken` del logout es opcional y aditivo para compatibilidad. El cliente borra localmente aunque falle la llamada remota.
- Se mantienen roles existentes; replay será `SYSTEM`, no se crea rol nuevo.
- No se alteran otras reglas de sesión ni se añaden proveedores de notificaciones.

## Orden de trabajo

### 1. P1-11 — esquema y entrega por canal

- Flyway V7: ampliar estados/campos necesarios para claim, clave estable y retry del outbox.
- Implementar claim `SENDING` en TX corta, enviar SMTP/FCM fuera de TX y persistir resultado/fallo en una TX nueva.
- Cubrir recuperación de claims abandonados y estados terminales.
- Tests: fallos, reintentos, concurrencia y migración.

### 2. P1-12 — propiedad y logout FCM

- Flyway V8: comprobar grupos duplicados, retirar unique `(user_id, token)` y crear unique global por token.
- Implementar transferencia atómica al usuario actual y eliminación condicional por token + propietario del envío.
- Limitar borrado FCM a `UNREGISTERED`/`SENDER_ID_MISMATCH`.
- Agregar `fcmToken` opcional al contrato de logout; resolver el propietario vía refresh token y actualizar repositorio/ViewModel Android.
- Tests: migración con y sin duplicados, transferencia/race, clasificación de error y logout de un dispositivo sin tocar otros.

### 3. P1-13 — fase de eventos y purga

- Cambiar listeners transaccionales de presupuesto, programación y ahorro a `AFTER_COMMIT`; crear notification/outbox en transacción independiente.
- Convertir el umbral `LocalDateTime` a la zona del usuario para la consulta de purga.
- Tests: generación posterior al commit y cutoff.

### 4. P1-13 — DLQ y replay

- Añadir consumidor DLQ que registre el fallo del outbox y permita reintento diferido.
- Hacer que el scheduler publique solo fallos vencidos, respetando el máximo y el backoff acordados.
- Añadir replay idempotente de entradas `FAILED` con autorización `SYSTEM`.
- Tests: consumo/ACK, intervalos, límite, replay manual y denegación a `USER`.

### 5. Verificación y commits

- Backend: `./mvnw.cmd -o test` desde `G:\repos\finanx`.
- Android + cliente: `./gradlew.bat :app:testDebugUnitTest :kotlin-client:test` desde `G:\repos\Finanx_frontend`.
- Validar V7/V8 en PostgreSQL desechable y correr ambas suites.
- Commit atómico por slice; registrar resultados en `docs/auditoria-mejoras.md`.

## Riesgos conocidos

- SMTP/FCM son sistemas externos sin garantía general de exactly-once; puede haber duplicados en la ventana entre aceptación remota y persistencia local.
- Si la migración V8 encuentra tokens compartidos preexistentes, se debe resolver el diagnóstico explícitamente antes de desplegar V8.
- El daemon Docker está activo pero los contenedores del compose aparecen detenidos; cualquier prueba que requiera servicios se hará en un entorno desechable o tras confirmar que no altera volúmenes existentes.
