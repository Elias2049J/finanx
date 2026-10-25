# Auditoría de mejoras — Finanx (monolito)

**Fecha:** 2026-10-24
**Commit auditado:** `6f73218` (`master`)
**Re-auditoría:** 2026-10-24 — segunda pasada ampliada sobre `identity`, `billing`, `document/OCR`, `notifications` y `shared`.
**Alcance:** bugs, consistencia, rendimiento y operación del monolito Spring Boot.
**Método:** lectura directa del código + contraste con la segunda pasada de auditoría + resultados de las pruebas de saturación k6 de esta sesión. Cada hallazgo crítico fue verificado en el árbol de trabajo; no se ejecutaron exploits contra un entorno levantado.

> **Nota sobre rutas.** El informe original referencia `services/MisMonedas`. En este repositorio el monolito es la raíz: `src/main/java/com/elias/mismonedas`.
>
> **Nota sobre fuentes.** La auditoría comparativa base de la sesión anterior no está versionada en el repositorio. Este documento sintetiza: (a) la segunda pasada de profundización, (b) la auditoría de rendimiento de esta sesión, y (c) los hallazgos QA ya registrados en `qa/e2e/KNOWN-ISSUES.md`. Donde una afirmación del informe recibido no se sostiene contra el código, se corrige explícitamente en la sección 7.

---

## 1. Resumen ejecutivo

El monolito tiene una base arquitectónica sólida: ownership derivado del `SecurityContext`, outbox/DLQ, Resilience4j, ShedLock, idempotencia de webhooks y paginación acotada. Los problemas no son de diseño global sino de **condiciones excepcionales y caminos de activación**.

| Prioridad | Cantidad | Impacto |
|---|---|---|
| **P0** | 8 | Monetización burlable **y rota**, pre-hijack de cuentas, corrupción de saldo, feature que se apaga sola, webhooks inutilizables |
| **P1** | 20 | Recurrencias incorrectas, duplicados de scheduler, chat degradado, rendimiento de analytics, notificaciones y OCR con pérdida/corrupción de datos |
| **P2** | 29 | Consistencia, contrato de API, operación y calidad |
| **Corregido / esperado** | 4 | BUG-1, BUG-1B, BUG-3 corregidos; BUG-2 (PREMIUM por defecto) decisión de dev |

**Los cinco riesgos que yo atacaría primero:**

1. **El premium se obtiene con dos llamadas** (`POST /subscriptions` + `POST /subscriptions/{id}/activate`) porque `activate`/`renew` no exigen pago, y además `BillingListener` regala PREMIUM a todo registro.
2. **Pagar no activa nada:** `PaymentRequest.subscriptionId` se ignora en `PaymentMapper`, ningún pago queda vinculado a una suscripción y `markCompleted` no se invoca desde ningún controlador ni listener. El flujo de pago está incompleto, no solo bypassable.
3. **`DELETE /transactions/{id}` concurrente revierte el saldo dos veces** (check-then-act sin `@Version` ni UPDATE condicional).
4. **Un presupuesto con `percentAlert=null` aborta el recheck de todos los presupuestos del usuario** de forma silenciosa y permanente.
5. **Pre-hijack de cuentas:** el registro no verifica el email y el login con Google auto-vincula cualquier cuenta existente con ese email; el atacante conserva la contraseña original.

> La re-auditoría ampliada añadió **2 P0, 11 P1 y 15 P2** sobre identity, billing, document/OCR, notifications y shared. Los detalles están en la sección 11.

---

## 2. Matriz de verificación

Veredictos: **CONFIRMADO** (el código hace lo descrito), **PARCIAL** (el hecho existe pero el alcance o el matiz es distinto), **NO VERIFICABLE** (depende del esquema real de BD, que no está versionado), **FALSO** (no se sostiene).

### 2.1 Críticos

| ID | Hallazgo | Veredicto | Evidencia | Esfuerzo |
|---|---|---|---|---|
| P0-1 | `activate`/`renew` no exigen pago COMPLETED; el plan lo elige el cliente | CONFIRMADO | `billing/service/impl/BillingServiceImpl.java:108-123,138-163`; `billing/controller/SubscriptionController.java:107-111`; `billing/dto/subscription/SubscriptionRequest.java:25-27` | Bajo |
| P0-2 | `refund` lo puede ejecutar el dueño y el gateway manual es no-op; la suscripción sigue ACTIVE | CONFIRMADO | `billing/service/impl/PaymentServiceImpl.java:170-179`; `billing/adapter/payment/ManualPaymentGatewayAdapter.java:20-33` | Bajo |
| P0-3 | Todo usuario nuevo nace PREMIUM, sin `@Profile` ni flag | CONFIRMADO | `billing/listener/BillingListener.java:15-34`; `identity/service/impl/UserServiceImpl.java:63` | Bajo |
| P0-4 | `percentAlert=null` → NPE que aborta el recheck completo del usuario | CONFIRMADO | `planning/domain/entity/Budget.java:93-103`; `planning/dto/budget/BudgetRequest.java:20-26`; `planning/service/impl/BudgetServiceImpl.java:171-254`; `planning/service/impl/PlanningRecheckServiceImpl.java:55-62` | Trivial |
| P0-5 | `disable()` concurrente revierte saldo dos veces | CONFIRMADO | `ledger/service/impl/TransactionServiceImpl.java:203-227`; `ledger/domain/entity/Transaction.java:29-33`; `wallets/repository/WalletRepository.java:31-38` | Bajo |
| P0-6 | `/webhooks/**` exige JWT; además el gateway manual rechaza todo webhook | CONFIRMADO | `identity/security/WebSecurityConfig.java:55-92`; `billing/controller/WebhookController.java:15,31`; `ManualPaymentGatewayAdapter.java:20-28` | Medio |
| P0-7 | Un pago `COMPLETED` no vincula ni activa la suscripción; `subscriptionId` se ignora y `markCompleted` no se llama | CONFIRMADO | `billing/mapper/PaymentMapper.java:26-36`; `billing/service/impl/PaymentServiceImpl.java:78-84,149-157`; `billing/dto/payment/PaymentRequest.java:26-27` | Medio |
| P0-8 | Registro sin verificación de email + auto-link de Google → pre-hijack de cuenta | CONFIRMADO | `identity/service/impl/UserServiceImpl.java:53-66`; `identity/adapter/auth/GoogleAuthServiceImpl.java:32-84` | Medio |

### 2.2 Altos

| ID | Hallazgo | Veredicto | Evidencia | Esfuerzo |
|---|---|---|---|---|
| P1-1 | Presupuestos recurrentes nunca pasan a `FINALIZED` y se evalúan contra el período actual | CONFIRMADO | `planning/service/impl/BudgetServiceImpl.java:175-198` | Medio |
| P1-2 | Recurrencia mensual pierde el día ancla (31 → 28 permanente); semanal ignora el intervalo si `delta > 0`; anual pierde el 29-feb con `interval=1` | CONFIRMADO (matices) | `scheduling/service/impl/RecurrenceServiceImpl.java:95-126` | Medio |
| P1-3 | `GET /savings` devuelve 503 si falta una tasa de cambio | CONFIRMADO (503, no 500) | `planning/service/impl/SavingGoalServiceImpl.java:107-110`; `shared/config/GlobalExceptionHandler.java:87-90` | Bajo |
| P1-4 | El scheduler puede ejecutar dos veces la misma ocurrencia; clave de idempotencia aleatoria por intento | CONFIRMADO | `scheduling/scheduler/TransactionScheduler.java:16-20`; `scheduling/service/impl/ScheduleServiceImpl.java:248-259,366,425-434` | Medio |
| P1-5 | La recuperación de la carrera de idempotencia corre sobre una transacción abortada | CONFIRMADO | `ledger/service/impl/TransactionServiceImpl.java:142-168` | Medio |
| P1-6 | El modelo fallback de Gemini es código muerto; bulkhead de 8 con espera 0; el fallback se persiste y se responde 200 | CONFIRMADO | `chat/adapter/ai/gemini/ChatGeminiAdapter.java:44-64`; `application.yml:156-160`; `chat/service/impl/ChatBotServiceImpl.java:180-183` | Bajo |
| P1-7 | El sanitizer bloquea la palabra «consulta» y persiste el placeholder | CONFIRMADO | `shared/ai/AiUtils.java:21,59-73`; `chat/service/impl/ChatBotServiceImpl.java:154` | Bajo |
| P1-8 | Analytics carga toda la historia del usuario en memoria y agrega en Java | CONFIRMADO (FX cacheado) | `insights/service/impl/analytics/TransactionAnalyticsServiceImpl.java:115-122`; `insights/controller/AnalyticsController.java:51-56` | Alto |
| P1-9 | El gasto de presupuestos en analytics no filtra por usuario | CONFIRMADO (latente) | `insights/service/impl/analytics/BudgetAnalyticsServiceImpl.java:131-138`; `ledger/repository/TransactionRepository.java:32` | Bajo |
| P1-10 | El monto del OCR se confía sin validar; un monto negativo acredita la wallet | CONFIRMADO | `document/service/impl/ReceiptScanServiceImpl.java:162-167`; `ledger/service/impl/TransactionServiceImpl.java:92-98`; `wallets/service/impl/WalletServiceImpl.java:213-215` | Bajo |
| P1-11 | El envío SMTP/FCM ocurre dentro de la transacción de entrega | CONFIRMADO | `notifications/service/impl/NotificationDeliveryService.java:88-118` | Medio |
| P1-12 | FCM borra tokens válidos en `INVALID_ARGUMENT`; un token puede pertenecer a dos usuarios | CONFIRMADO | `notifications/adapter/fcm/FCMNotificationAdapter.java:115-123`; `notifications/service/impl/FcmTokenServiceImpl.java:38-59` | Medio |
| P1-13 | Notificaciones en `BEFORE_COMMIT`, purga que ignora el umbral y DLQ sin consumidor | CONFIRMADO | `notifications/listener/BudgetNotificationListener.java:26`; `NotificationServiceImpl.java:154-159`; `NotificationMessageRecoverer.java:19-33` | Medio |
| P1-14 | El template fija un access token de 23,5 h sin `jti`/revocación; el secreto débil solo genera un warning | CONFIRMADO | `.env.example:55,58`; `identity/service/impl/JwtTokenServiceImpl.java:38-47`; `AuthTokenServiceImpl.java:80-91` | Medio |
| P1-15 | `deleteUserAccount`/`resetAccountData` dejan wallets, billing, documentos, chat, insights y reglas de recurrencia; el reset borra el historial pero no resetea saldos | CONFIRMADO | `identity/service/impl/UserServiceImpl.java:158-176`; listeners en `catalog`, `ledger`, `planning`, `scheduling`, `notifications` | Alto |
| P1-16 | Pagos sin validación de monto/moneda y webhooks que ignoran el monto notificado | CONFIRMADO | `billing/dto/payment/PaymentRequest.java:29-35`; `billing/service/impl/PaymentServiceImpl.java:287-299` | Medio |
| P1-17 | `PAST_DUE` no tiene dunning; el plan se considera activo aunque el período haya vencido; cancelar revoca el período pagado | CONFIRMADO | `billing/service/impl/BillingServiceImpl.java:125-136,165-178`; `SubscriptionServiceImpl.java:21-34` | Medio |
| P1-18 | `ImageIO.read` decodifica la imagen completa antes de cualquier límite de píxeles | CONFIRMADO | `document/service/impl/ImageServiceImpl.java:109-116` | Bajo |
| P1-19 | La URL prefirmada de S3 se firma con `:8081` pero nginx reenvía `Host` sin puerto | CONFIRMADO | `nginx.conf:74`; `docker-compose.yml:129-132`; `document/config/SeaweedFsProperties.java` | Bajo |
| P1-20 | Normalización de email inconsistente: registro comprueba antes de `toLowerCase()` y Google usa el email crudo | CONFIRMADO | `identity/service/impl/UserServiceImpl.java:55,58`; `GoogleAuthServiceImpl.java:44` | Bajo |

### 2.3 Medios y operación

| ID | Hallazgo | Veredicto | Evidencia |
|---|---|---|---|
| P2-1 | Solo `Wallet` tiene `@Version`; rechecks pueden pisar cambios del usuario | CONFIRMADO | `wallets/domain/entity/Wallet.java:34-35`; grep `@Version` en `src/main/java` |
| P2-2 | Dos wallets primarias posibles; no hay constraint que lo impida | CONFIRMADO | `wallets/service/impl/WalletServiceImpl.java:177-193`; `Wallet.java:11-12` |
| P2-3 | `ReasonResolver` no valida ownership de `reasonId` | CONFIRMADO | `catalog/service/ReasonResolver.java:23-27` |
| P2-4 | `CurrencyConverter` re-etiqueta moneda si `fromCurrency` es null; receipt puede NPE | CONFIRMADO | `shared/money/CurrencyConverter.java:24-27`; `ledger/service/impl/TransactionServiceImpl.java:92-96` |
| P2-5 | `JacksonConfig` reemplaza el mapper Jackson 2, no el MVC de Boot 4 (Jackson 3) | PARCIAL | `shared/config/JacksonConfig.java:10-12`; `pom.xml` (Boot 4.0.6) |
| P2-6 | Un error transitorio deja el schedule en `ERROR` para siempre | CONFIRMADO | `scheduling/service/impl/ScheduleServiceImpl.java:222-228,261-274` |
| P2-7 | `walletId=null` documentado como «usar la principal» pero devuelve 404; `POST /schedule` sin regla NPEa | CONFIRMADO | `scheduling/dto/schedule/TransactionScheduleRequest.java:22-26`; `ScheduleServiceImpl.java:99,436-445` |
| P2-8 | `GlobalExceptionHandler` no mapea locks ni rutas inexistentes a 409/404 | CONFIRMADO | `shared/config/GlobalExceptionHandler.java:178-181` |
| P2-9 | Esquema de BD no reproducible: Flyway comentado, migraciones vacías | CONFIRMADO | `application.yml:30-34`; `pom.xml:53-60`; `src/main/resources/db/migration` vacío |
| P2-10 | Sin CI | CONFIRMADO | `.github` no existe |
| P2-11 | `catalog` y `chat` sin tests; `identity` sí tiene 10 | PARCIAL (corrige el informe) | 229 `@Test`; `identity`: 10, `catalog`: 0, `chat`: 0 |
| P2-12 | `.pyc` de Python 3.14 commiteados contra imagen 3.12 | CONFIRMADO | `services/doc-processing/app/__pycache__/`; `services/doc-processing/Dockerfile:1` |
| P2-13 | Swagger parcialmente público: faltan `/swagger-ui/**` y `/v3/api-docs` | CONFIRMADO | `WebSecurityConfig.java:56-64` |
| P2-14 | API key de CurrencyBeacon en query string y retry sobre 4xx | CONFIRMADO | `shared/adapter/exchange/currencybeacon/CurrencyBeaconClient.java:33-34,48-50,64`; `application.yml:117-125` |

---

## 3. P0 — Detalle y parche recomendado

### P0-1 · Activación self-service de suscripciones

```java
// BillingServiceImpl.java:108-123
public SubscriptionResponse activate(Long subscriptionId) {
    Subscription subscription = getOwned(subscriptionId);
    activate(subscription);              // no mira ningún Payment
    ...
}
```

- `getOwned` **sí** valida que la suscripción sea del usuario (`BillingServiceImpl.java:184-187`), así que no hay IDOR entre usuarios.
- El agujero es de negocio: `POST /subscriptions` acepta `plan`, `amount` y `currency` del cliente (`SubscriptionRequest.java:25-39`); `POST /{id}/activate` la activa sin comprobar un `Payment` en `COMPLETED`.
- `renew` es no-op mientras el período esté abierto (`:144-148`), pero crear otra suscripción y activarla vuelve a funcionar.

**Parche:** mover `activate`/`renew` a `SYSTEM` (o quitarlos de la API pública) y activar solo desde el flujo de pago (`markCompleted`, que hoy no se invoca: ver P0-7) con una regla `payment.state == COMPLETED && payment.subscriptionId == subscription.id`. Añadir test de integración: usuario normal → `POST /activate` → 403.

### P0-2 · Refund sin efecto real

`PaymentServiceImpl.refund` cambia el pago a `REFUNDED`, pero `ManualPaymentGatewayAdapter.refund` solo loguea y **no toca la suscripción**. El estado del pago y el de la suscripción quedan divergentes.

**Parche:** si el pago estaba asociado a una suscripción (ver P0-7), cancelarla o marcarla `PAST_DUE` en la misma transacción. Si el gateway es manual, el refund debe ser una operación `SYSTEM` con auditoría, no un endpoint de usuario. Validar la transición `COMPLETED/DISPUTED → REFUNDED` **antes** de llamar al proveedor.

### P0-3 · PREMIUM automático para todos

```java
// BillingListener.java:22-33
@EventListener
public void on(UserRegisteredEvent event) {
    ... .plan(SubscriptionPlan.PREMIUM).amount(BigDecimal.ZERO)
    billingService.subscribeAndActivate(subRequest);
}
```

No hay `@Profile` ni `@ConditionalOnProperty` en todo el paquete `billing`. `KNOWN-ISSUES.md` lo documenta como «esperado en dev»; el problema es que **no hay nada que lo apague en producción**.

**Parche:** `@ConditionalOnProperty(name = "app.billing.auto-premium", havingValue = "true")`, por defecto `false`, y activarlo solo en `application-dev.yml`/compose local. Test: contexto sin la property → no se crea suscripción.

### P0-4 · NPE de `percentAlert` que apaga todos los presupuestos

```java
// Budget.java:93-103
public BudgetHealth updateHealth(BigDecimal spent) {
    if (!Boolean.TRUE.equals(alertable)) { ... return health; }
    int alertPercent = Math.clamp(percentAlert, 0, 100); // Integer null → NPE
```

`BudgetRequest` valida `@Min(0) @Max(100)` pero **no `@NotNull`**; ambos ignoran `null`. El NPE ocurre en el `for` de `checkAllBudgets` (`BudgetServiceImpl.java:171-254`), que **no tiene try/catch por presupuesto**, así que aborta el resto y nunca llega a `saveAll` (`:254`). La excepción solo se registra en el executor (`PlanningRecheckServiceImpl.java:55-62`): el usuario deja de tener presupuestos actualizados sin ningún aviso.

**Parche (3 capas):**
1. `@NotNull` condicional a `alertable=true` (validación de clase) o `@Builder.Default percentAlert = 50`.
2. `Math.clamp(percentAlert == null ? 50 : percentAlert, 0, 100)` en entidad y servicio.
3. `try/catch` por iteración en `checkAllBudgets`, logueando el `budgetId` y continuando.

Test: dos presupuestos, uno con `percentAlert=null`; el segundo debe actualizarse igualmente.

### P0-5 · Doble reversión de saldo en `DELETE /transactions/{id}`

```java
// TransactionServiceImpl.java:205-227
Transaction transaction = ...findByIdAndUser_Id(id, userId).orElseThrow();
if (!Boolean.TRUE.equals(transaction.getActive())) return; // check
...
transaction.setActive(false);                             // act
walletService.updateBalance(...);                         // reversión
```

`Transaction` no tiene `@Version`. Peor: `WalletRepository.adjustBalance` es un UPDATE atómico que **incrementa `version` manualmente** (`WalletRepository.java:31-38`), saltándose la comprobación optimista de JPA. Dos DELETE concurrentes leen `active=true`, ambos pasan el guard y ambos revierten.

**Parche:** claim atómico antes de revertir:

```java
@Modifying
@Query("UPDATE Transaction t SET t.active=false, t.disabledAt=:now " +
       "WHERE t.id=:id AND t.user.id=:userId AND t.active=true")
int deactivate(...);
```

Si `updated == 0`, devolver el estado actual sin revertir. Alternativa: `@Version` en `Transaction` + reintento. Test de concurrencia con dos hilos y `CountDownLatch`.

### P0-6 · Webhooks inutilizables por dos motivos

1. `WebSecurityConfig` no tiene matcher para `/webhooks/**`; cae en `anyRequest().authenticated()` (`:92`) y el proveedor recibe 401 antes de la validación HMAC.
2. Aunque se abriera, `ManualPaymentGatewayAdapter.verifyWebhookSignature` **siempre devuelve `false`** y `parseWebhookPayload` lanza `UnsupportedOperationException` (`:20-28`). No existe otro `PaymentPort`.

**Parche:** `permitAll()` para `/webhooks/**` + validación HMAC dentro del controlador; y decidir explícitamente el gateway: si es manual, el endpoint no debe existir; si se integra un proveedor real, implementar firma y parseo. Además, definir expiración de `Payment` en `PENDING/PROCESSING` (no hay timeout hoy; `PaymentRepository.findAllByState` no se usa).

### P0-7 · Pagar no activa la suscripción

`PaymentRequest` declara `subscriptionId` (`PaymentRequest.java:26-27`), pero `PaymentMapper.toEntity` lo ignora (`@Mapping(target = "subscription", ignore = true)`, `PaymentMapper.java:26-36`) y **no existe ningún `setSubscription`** en el módulo. `PaymentServiceImpl.markCompleted` solo cambia el estado del pago (`:149-157`); no toca la suscripción. Y `markCompleted` no se invoca desde ningún controlador ni listener: solo aparece en la interfaz y la implementación.

Consecuencia: en un flujo sin el auto-PREMIUM de dev, un pago `COMPLETED` deja la suscripción en `PENDING` y `getActivePlanFor` sigue devolviendo `FREE`. El webhook que transiciona el pago a `COMPLETED` tampoco la activa: `applyNotification` solo cambia el estado del pago (`:287-299`). El único camino que activa es el endpoint manual `POST /subscriptions/{id}/activate` (P0-1). El flujo de pago no está solo bypassable: está **incompleto**.

**Parche:** resolver `subscriptionId` con ownership, asignarlo al `Payment` antes de guardar, y al pasar a `COMPLETED` activar/renovar la suscripción en la misma transacción (o con un evento after-commit idempotente). Test de integración: crear pago con `subscriptionId` → completar → `getActivePlanFor` devuelve PREMIUM y el pago apunta a la suscripción.

### P0-8 · Pre-hijack de cuentas vía Google

`register` crea la cuenta sin verificar el email (`UserServiceImpl.java:53-66`). `GoogleAuthServiceImpl.authenticate` busca por email y, si existe, llama a `login`, que emite tokens sin comprobar que la cuenta sea de Google ni que el usuario controle la contraseña (`:32-59`). El atacante puede registrar `victima@correo.com` con una contraseña propia; cuando la víctima entra con Google, recibe acceso a esa cuenta, pero el atacante **conserva la contraseña** y puede seguir entrando por login clásico. En la rama `register` de Google también se sobrescriben `name`/`lastname` de una cuenta existente (`:63-75`).

**Parche:** exigir verificación de email en el registro; persistir `authProvider`/`emailVerified`; no auto-vincular: pedir prueba de posesión (login con contraseña o email de confirmación) antes de asociar Google; al vincular, rotar contraseña y revocar sesiones. Exigir `Boolean.TRUE.equals(emailVerified)` en el verificador (ver P2-28).

---

## 4. P1 — Detalle

### P1-1 · Presupuestos recurrentes sin finalización

La rama `rr != null` calcula `periodStart` y sigue; la transición a `FINALIZED` solo existe en la rama sin recurrencia (`BudgetServiceImpl.java:175-198`). Un presupuesto materializado por `BudgetScheduler` queda `ACTIVE` para siempre y cada recheck lo evalúa contra el período actual (`computeCurrentPeriodStart`), generando alertas del período equivocado.

**Parche:** comparar `computeCurrentPeriodStart(rr, now)` con `b.getEnd()` y finalizar/frozen cuando el período haya cerrado; o modelar cada materialización como una fila con su propio `start/end`. Test: presupuesto mensual de enero, reloj en marzo → `FINALIZED` y `frozenSpent` de enero.

### P1-2 · Recurrencias

- **Mensual:** `desiredDay = base.getDayOfMonth()` usa la ocurrencia previa, no el ancla de la regla. 31-ene → 28-feb → 28-mar… para siempre (`RecurrenceServiceImpl.java:113-118`).
- **Semanal:** `delta = target - base`; si `delta > 0` no suma `7 * interval` (`:95-111`). En el flujo normal `base` ya cae en el día objetivo, así que el bug aparece cuando `nextRunAt`/`start` no es el día objetivo.
- **Anual:** mismo patrón; **sí maneja mal el 29-feb**. Con `interval=1`, 2024-02-29 → 2025-02-28 y a partir de ahí la base queda en 28, así que nunca vuelve al 29 (ni siquiera en 2028). No lanza excepción: deriva en silencio. Con `interval=4` sí conserva el 29 si la base aún es 29-feb. Verificado ejecutando el método: `2024-02-29 2025-02-28 2026-02-28 2027-02-28 2028-02-28 …`.

**Parche:** calcular siempre desde `rule.start` (ancla) con `YearMonth`/`LocalDate` y `Math.min(anchorDay, lengthOfMonth())`; para semanal, `delta = Math.floorMod(target - base, 7 * interval)`.

### P1-3 · FX en saving goals

`SavingGoalServiceImpl.totalsOf` llama a `currencyConverter.convert` sin try/catch (`:107-110`). `GlobalExceptionHandler` mapea `CurrencyConversionException` a **503**, no 500. `WalletServiceImpl.getConsolidatedBalance` ya degrada a `partial` (`:142-149`); replicar ese patrón.

### P1-4 · Scheduler: doble ocurrencia

`@SchedulerLock` evita solapamiento entre nodos mientras el lock vive (`PT5M`), pero `Schedule` no tiene `@Version` ni claim atómico; `processTransactionOccurrence` es check-then-act (`ScheduleServiceImpl.java:248-259`). La clave de idempotencia es `UUID.randomUUID()` por intento (`:366`), así que un reintento no deduplica. Es una regresión: el commit `4fb8768` introdujo una clave determinista `schedule:<id>:<fecha>` y `2094a80` la sustituyó por UUID.

**Parche:** claim `UPDATE ... SET state=RUNNING, nextRunAt=... WHERE id=? AND nextRunAt=?` y clave determinista `schedule:{id}:{nextRunAt}`.

### P1-5 · Idempotencia: recuperación imposible

```java
// TransactionServiceImpl.java:161-168
} catch (DataIntegrityViolationException race) {
    Transaction existing = transactionRepository.findByIdempotencyKey(key)...
```

Con `GenerationType.IDENTITY` el INSERT ocurre en `save()`; en PostgreSQL la transacción queda abortada y la query de recuperación falla («current transaction is aborted»). Spring además marca la transacción como rollback-only. **Parche:** mover la recuperación a un método `@Transactional(propagation = REQUIRES_NEW)` o a un servicio externo que consulte tras el rollback.

### P1-6 · Chat: fallback muerto y bulkhead sin espera

`ChatGeminiAdapter` inyecta `fallbackModel` pero solo llama a `tryModel(primaryModel, ...)`; el log dice «Ambos modelos fallaron» sin haber intentado el segundo (`:57-64`). El fallback se persiste como respuesta (`ChatBotServiceImpl.java:180-183`) y el controller responde 200. Con `bulkhead.gemini.max-concurrent-calls: 8` y `max-wait-duration: 0s`, el noveno usuario recibe el mensaje de emergencia al instante.

**Parche:** intentar `primary` y, si devuelve `null`, intentar `fallbackModel`; `max-wait-duration` > 0; propagar un error tipado (503/429) en vez de persistir el fallback; test con 9 llamadas concurrentes.

### P1-7 · Sanitizer contra el propio usuario

`AiUtils` bloquea `SELECT|...|SQL|consulta|base de datos` con `(?i)` (`:21`) y sustituye por `"[consulta no disponible]"` (`:69-73`). «Consulta mis gastos de julio» es una frase normal en esta app. El placeholder se guarda como mensaje del usuario y se envía al modelo, sin feedback. Además `sanitizeUserPrompt("")` devuelve `""` (`:59-60`).

**Parche:** no filtrar lenguaje natural; si se quiere defensa anti-prompt-injection, usar delimitadores y un system prompt, no una blocklist de palabras. Validar prompt vacío con 400.

### P1-8 · Analytics en memoria

`findAll(spec)` sin límite + `map` a DTO + `sorted` en Java (`TransactionAnalyticsServiceImpl.java:115-122`), con rango `from/to` controlado por el usuario (`AnalyticsController.java:51-56`). La conversión FX está cacheada 8 h (`CacheConfig.java:18-21`), así que no hay HTTP por transacción, pero sí por moneda y cache miss; el coste dominante es memoria/CPU y la conexión retenida.

**Parche:** agregaciones SQL (`SUM`, `GROUP BY`) con proyecciones, límite máximo de rango (p. ej. 24 meses) y conversión de totales, no de filas. `TransactionRepository` ya tiene ejemplos (`sumAmountsByCurrencyForCategory`, `:52-64`).

### P1-9 · Gasto de presupuesto sin `user.id`

`findActiveTransactions` filtra por categoría y fechas, sin usuario (`BudgetAnalyticsServiceImpl.java:131-138`). Hoy las categorías son por usuario, pero `Category.user` es nullable (`Category.java:49-51`). `Category.onCreate` desreferencia `user`, así que una categoría global no se puede crear por el flujo normal; el riesgo es latente para filas preexistentes o inserciones manuales. Añadir `user.id` al query y un índice compuesto.

### P1-10 a P1-20 · Re-auditoría ampliada

Los detalles completos están en la sección 11. Los cuatro que cambiaría esta semana:

- **P1-10 · OCR y saldo:** `ReceiptScanServiceImpl` solo comprueba `amount != null` (`:162-167`) y `TransactionServiceImpl` reemplaza el monto validado del request por el del recibo (`:92-98`). Si el OCR devuelve un negativo y el tipo es `SPENT`, `WalletServiceImpl.updateBalance` lo niega (`:213-215`) y **acredita** la wallet. El `@DecimalMin` del request no protege porque la sobrescritura ocurre después.
- **P1-11 · Notificaciones dentro de la TX:** `NotificationDeliveryService.deliverChannel` toma un lock pesimista y llama a `port.send(dto)` dentro de `@Transactional` (`:88-118`). Un rollback después del envío deja el flag sin commitear y el reintento duplica el correo/push; el lock se mantiene durante todo el I/O.
- **P1-14 · Sesiones:** el template fija `JWT_EXPIRATION_SECONDS=84600` (23,5 h) y el logout solo revoca refresh tokens (`AuthTokenServiceImpl.java:80-91`). El access token robado sigue siendo válido hasta su expiración; no hay `jti` ni `tokenVersion`. Además, `JwtTokenServiceImpl` solo **advierte** si el secreto mide menos de 32 caracteres (`:38-47`) y el template trae `REEMPLAZAR`.
- **P1-15 · Borrado/reset:** `deleteUserAccount` borra preferencias y publica `UserDeletedEvent` (`UserServiceImpl.java:158-176`); ese evento sí elimina transacciones, presupuestos, metas, schedules, categorías, razones y notificaciones, pero **no hay listeners para wallets, billing, documentos, chat ni insights**. `resetAccountData` reutiliza el mismo evento y luego publica `UserDataResetEvent` (resiembra categorías): borra el historial financiero pero deja wallets con saldos, billing, documentos, chat, insights y reglas de recurrencia. El borrado puede fallar por FKs (wallets, refresh tokens) o dejar PII.

---

## 5. Rendimiento y capacidad (auditoría k6)

### 5.1 Resultados de la corrida de saturación

Configuración: rampa `100 → 1900 req/s` en 12 escalones de 45 s, `PRE_VUS=1200`, `MAX_VUS=2500`, `USER_POOL_SIZE=1200`, `SLEEP=0.2`, pool de **20 conexiones** durante la prueba.

| Métrica | Valor |
|---|---|
| Peticiones | 429.741 |
| Iteraciones | 400.043 |
| req/s medios | 626,5 |
| p95 HTTP | **5.326,67 ms** |
| p99 HTTP | 9.471 ms |
| max | 33.093 ms |
| VUs máx | 2.500 |
| Iteraciones descartadas | 157.758 (**28,3 %**) |
| Errores HTTP / `op_failed` | 0 / 0 |

Comparación con la corrida anterior (818 VUs): ~520 req/s con p95 50 ms. El salto a 2.500 VUs solo dio +20 % de throughput y multiplicó la latencia por 100: **congestión, no escalado**.

### 5.2 Diagnóstico

- `http_req_blocked` p95 = 0 y `http_req_connecting` p95 = 0 → no es red ni el generador.
- `http_req_waiting` ≈ `http_req_duration` → el tiempo se va en el servidor.
- `GET /wallets` (consulta trivial) p95 = 5.234 ms → la petición espera una conexión, no ejecuta SQL costoso.
- `max` ≈ 30 s = `connectionTimeout` por defecto de Hikari → cola del pool.
- Con 20 conexiones, ~600 req/s equivale a ~31 req/s por conexión, coherente con un tiempo de retención de ~30-35 ms por request.

### 5.3 Mejoras de capacidad, por ROI

1. **Pool y timeouts:** `maximum-pool-size` 20 → 50 (`max_connections=100` en Postgres), `minimum-idle=10`, `connection-timeout=3-5s`, `max-lifetime=30m`, `keepalive-time=2m`.
2. **N+1 en `GET /transactions`:** el `@EntityGraph` existe solo en la variante `List findAll(Specification)` (`TransactionRepository.java:24-26`), no en la paginada que usa el listado (`TransactionServiceImpl.java:197`). El mapper accede a `wallet`, `wallet.user`, `category`, `reason`, `savingGoal` y `receipt.image` (`TransactionMapper.java:14,42-44`). Añadir `@EntityGraph` a la variante paginada (todas las rutas son *to-one*) o usar proyección DTO.
3. **Quitar el `COUNT(*)`:** `PageResponse` expone `totalElements`; si el frontend no lo usa, pasar a `Slice`/keyset.
4. **EAGER accidentales:** `Transaction.savingGoal` (`@ManyToOne`) y `Transaction.receipt` (`@OneToOne`) son EAGER por defecto (`Transaction.java:71-79`). Poner `fetch = LAZY`.
5. **Llamada HTTP dentro de la transacción:** la conversión de receipt (`TransactionServiceImpl.java:92-96`) puede llamar a CurrencyBeacon con la conexión retenida. Resolverla antes de abrir la transacción.
6. **Analytics a SQL** (P1-8) y **N+1 de schedules** (`ScheduleServiceImpl.java:152-173`: una query de wallet primaria por fila).
7. **PgBouncer** en modo `transaction` si se escala a varias instancias; `prepareThreshold=0` en la URL.
8. **Réplica de lectura** para analytics/listados y **cache** de catálogos (`categories`, `reasons`, `wallets`) con TTL.
9. **Backpressure:** bulkhead/time limiter y `connection-timeout` corto para devolver 503 rápido en vez de 5 s de espera.

> La app no falla bajo saturación (0 errores), pero degrada a 5 s. Un 503 rápido es mejor experiencia y protege la BD.

---

## 6. Consistencia, contrato y operación

### 6.1 Consistencia

- **`@Version` solo en `Wallet`** (`Wallet.java:34-35`). `Budget`, `SavingGoal`, `Schedule` y `Transaction` no lo tienen; los rechecks asincrónicos pueden pisar cambios del usuario. Añadir `@Version` y manejar `OptimisticLockingFailureException` con 409 + reintento acotado.
- **Wallet primaria:** la promoción es read-modify-write (`WalletServiceImpl.java:177-193`) y solo hay unique `(id_usuario, moneda)` (`Wallet.java:11-12`). Añadir índice parcial `UNIQUE (id_usuario) WHERE es_primaria = true` (PostgreSQL).
- **`ReasonResolver`:** `getReferenceById` sin ownership (`ReasonResolver.java:23-27`); usar `findByIdAndUser_Id` y devolver 404.
- **`CurrencyConverter`:** si `fromCurrency == null`, devuelve el monto re-etiquetado (`CurrencyConverter.java:24-27`). Validar moneda o fallar; `Money.of(null, ...)` lanza NPE en la ruta de receipts.
- **`JacksonConfig`:** el bean es Jackson 2; Boot 4 usa Jackson 3 para MVC. El efecto «global» del informe es incorrecto, pero el bean sigue afectando a los componentes que lo inyectan (clientes REST, consumidores Rabbit). Revisar si se quiere `FAIL_ON_UNKNOWN_PROPERTIES=false` explícito.

### 6.2 Contrato de API

- `walletId=null` documentado como «usar la principal» pero `getOwned(null)` → 404 (`TransactionScheduleRequest.java:22-26`; `ScheduleServiceImpl.java:99`).
- `POST /schedule` sin `recurrenceRule` → NPE (`ScheduleServiceImpl.java:436-445`); añadir `@NotNull`.
- `ChatBotServiceImpl` lanza `IllegalStateException` (500) donde el OpenAPI promete 409.
- `BudgetServiceImpl.update` aplica `start` y luego lo restaura (`:84-95`): código muerto o bug de mapeo; aclarar que `start` es inmutable.
- Fin de presupuesto a las 00:00 del último día (`BudgetServiceImpl.java:275`) y finalización con `now.isAfter(end)` (`:187`): el último día queda excluido. Usar `endExclusive` (día siguiente a las 00:00).

### 6.3 Excepciones

`GlobalExceptionHandler` no mapea `OptimisticLockingFailureException`, `PessimisticLockingFailureException` ni `NoResourceFoundException`; caen en el handler genérico → 500 en vez de 409/404 (`:178-181`). El handler 400 **no** concatena `ex.getMessage()` (usa el catálogo i18n); esa afirmación del informe es falsa.

### 6.4 Operación y calidad

- **Flyway desactivado** (`application.yml:30-34`), `db/migration` vacío, `ddl-auto: none`: el esquema vive solo en la BD real. Es el mayor riesgo operacional. Plan: baseline del esquema actual + migraciones versionadas + `shedlock` incluido.
- **Sin CI** (`.github` no existe). Añadir workflow de `./mvnw verify` + `qa` (k6) en PR.
- **Tests:** 229 `@Test`; `catalog` y `chat` con 0, `identity` con 10. El informe dice «identity, catalog y chat sin tests»: **identity es incorrecto**.
- **Graceful shutdown** ausente (`server.shutdown`), Hikari sin `connection-timeout`/`max-lifetime`/`leak-detection-threshold`.
- **`.pyc` de Python 3.14** commiteados contra `python:3.12-slim` (`services/doc-processing/Dockerfile:1`): borrar y añadir `.gitignore`.
- **Swagger:** `permitAll` solo para `/v3/api-docs.yaml` y `/swagger-ui.html`; la UI redirige a 401 porque faltan `/swagger-ui/**` y `/v3/api-docs`.
- **Seguridad:** `/api/v1/users/{id}/delete` se permite a `ROLE_USER` a nivel URL (`WebSecurityConfig.java:68`) y se confía en `@PreAuthorize("hasRole('SYSTEM')")` (`UserController.java:93-94`). `@EnableMethodSecurity` está activo (`WebSecurityConfig.java:30`), así que no hay escalada hoy; es defensa en profundidad, no un P0.
- **CurrencyBeacon:** API key en query string (`CurrencyBeaconClient.java:33-34`) y retry sobre 4xx (`application.yml:117-125`); `max-attempts: 3` son 3 intentos totales (2 reintentos), no 3 reintentos. Mover la key a header y excluir 4xx del retry.
- **PII en logs / OCR a Gemini:** reportado, no re-verificado en esta pasada; mantener en el backlog.
- **`UserDataContext`:** sin `@Transactional` propio (`UserDataContextServiceImpl.java:31-32`) y `@OneToOne` sin unique explícito (`UserDataContext.java:40-42`); normalmente se invoca dentro de la transacción de `ChatBotServiceImpl.startSession`, así que el riesgo es en llamadas standalone. Añadir constraint en BD y `@Transactional`.

---

## 7. Correcciones al informe recibido

| Afirmación del informe | Realidad verificada | Evidencia |
|---|---|---|
| «El handler 400 concatena `ex.getMessage()`» | **Falso.** Usa el catálogo i18n; `ex` solo se loguea. | `GlobalExceptionHandler.java:167-171,190-225` |
| «`JacksonConfig` reactiva `FAIL_ON_UNKNOWN_PROPERTIES` globalmente» | **Parcial.** Es un `ObjectMapper` Jackson 2; Boot 4 usa Jackson 3 para MVC. Afecta solo a componentes que lo inyectan. | `JacksonConfig.java:10-12`; `pom.xml` |
| «Doble escaping de quotes/HTML» | **Falso** como doble pasada: hay una sola llamada a `escapeForPrompt`; el JSON de transporte añade su escape normal. | `AiUtils.java:76-88` |
| «El retry FX reintenta 3 veces» | **Impreciso.** `max-attempts: 3` = 3 intentos totales (2 reintentos). | `application.yml:117-125` |
| «`AbortPolicy` inalcanzable» | **Efectivamente**, no literalmente: puede dispararse en shutdown o fallo de creación de worker. | `AsyncConfig.java:22-32` |
| «`BudgetMapper` inicializa `user` por fila (N+1)» | **Falso** como query por fila: el persistence context reutiliza el mismo `User`. El N+1 real es `category.name`. | `BudgetMapper.java:64-66`; `DateMapper.java:37-41` |
| «`nextYearly` maneja mal el 29-feb» | **Confirmado.** No lanza excepción, pero con `interval=1` pierde el 29-feb de forma silenciosa y permanente; solo `interval=4` lo conserva si la base sigue siendo 29-feb. | `RecurrenceServiceImpl.java:120-126` |
| «`GET /savings` → 500» | **503**, no 500. | `GlobalExceptionHandler.java:87-90` |
| «identity, catalog y chat sin tests» | **Parcial.** `identity` tiene 10 tests; `catalog` y `chat` sí están a cero. | `src/test/java/.../identity` |
| «Sin unique constraint en `ChatMessage.turnNumber`» | **No verificable** desde el repo: `ddl-auto: none` y migraciones vacías. La entidad no lo declara. | `ChatMessage.java:27-28` |
| «`Category.user` nullable → fuga entre usuarios» | **Latente**, no explotable por el flujo normal: `Category.onCreate` desreferencia `user`. | `Category.java:49-57` |
| «`renew` extiende indefinidamente» | **Matiz.** Es no-op mientras el período está abierto; para renovar hay que esperar o crear otra suscripción. | `BillingServiceImpl.java:144-148` |
| «`activate` reactiva cualquier estado» | **Matiz.** La state machine impide revivir `CANCELED/EXPIRED`. | `SubscriptionState.java`; `BillingServiceImpl.java:118-123` |
| «El problema de webhooks es solo el 401» | **Incompleto.** Aunque se abra, el gateway manual rechaza todo webhook. | `ManualPaymentGatewayAdapter.java:20-28` |
| «`/users/{id}/delete` permite escalada» | **Parcial.** `@PreAuthorize("hasRole('SYSTEM')")` se aplica con method security activo; es defensa en profundidad. | `WebSecurityConfig.java:30,68`; `UserController.java:93-94` |

---

## 8. Roadmap priorizado

### Fase 0 — P0 (esta semana, horas/días)

| Tarea | Criterio de aceptación |
|---|---|
| Gate de pago en `activate`/`renew` o moverlos a `SYSTEM` | Usuario normal → 403; activación solo desde pago `COMPLETED` |
| `BillingListener` condicionado por property (default off) | Registro sin property → sin suscripción |
| `@NotNull`/default de `percentAlert` + try/catch por presupuesto | Test con un presupuesto nulo y otro válido: el válido se actualiza |
| Claim atómico en `disable()` | Test de 2 hilos: una sola reversión, balance correcto |
| `permitAll` de `/webhooks/**` + decisión sobre gateway manual | Webhook con firma válida → 200; sin firma → 401/400, nunca 401 de Spring Security |
| Timeout de `Payment` en `PENDING/PROCESSING` | Pago abandonado → `EXPIRED`/`FAILED` y evento |
| Vincular `Payment.subscriptionId` y activar/renovar al pasar a `COMPLETED` | Pago completado → suscripción `ACTIVE` y `getActivePlanFor=PREMIUM` |
| Verificación de email + link explícito de Google (sin auto-vinculo) | Cuenta con password no accesible por Google sin prueba de posesión; `email_verified=true` obligatorio |

### Fase 1 — P1 (1-2 semanas)

- Ancla de recurrencia (`RecurrenceServiceImpl`) + tests de 31-ene, 29-feb, bisemanal.
- Claim atómico de ocurrencia + clave determinista en `ScheduleServiceImpl`.
- `REQUIRES_NEW` para la recuperación de idempotencia.
- Fallback real de Gemini + bulkhead con espera + error tipado.
- Sanitizer sin blocklist de lenguaje natural.
- Catch de `CurrencyConversionException` en saving goals (degradar a `partial`).
- Guard de monto positivo en OCR + no sobrescribir el monto del request (P1-10).
- Notificaciones: enviar fuera de la TX, FCM solo borra `UNREGISTERED`, token único por usuario, `AFTER_COMMIT`, purga con `threshold` y DLQ con replay (P1-11/12/13).
- Sesiones: access TTL 5-15 min, `jti`/`tokenVersion`, revocación en logout/password change y secreto fail-fast (P1-14).
- Borrado/reset de cuenta completo y en orden de dependencias (P1-15).
- Billing: validar monto/moneda, comparar webhook, dunning de `PAST_DUE`, plan = `ACTIVE && currentPeriodEnd > now`, cancel-at-period-end (P1-16/17).
- Límite de píxeles en imágenes y `Host $http_host` para S3 (P1-18/19).
- Normalización única de email (P1-20).

### Fase 2 — Rendimiento y consistencia (2-4 semanas)

- `@EntityGraph` en el listado paginado + `FetchType.LAZY` en `savingGoal`/`receipt`.
- Quitar `COUNT` o pasar a keyset.
- Analytics a SQL + límite de rango.
- Chat: sacar la llamada a Gemini de la transacción; limitar historia.
- `AsyncConfig` con `SynchronousQueue` o `corePoolSize` de hilos virtuales.
- `@Version` en `Budget/SavingGoal/Schedule/Transaction`; unique parcial de wallet primaria.
- Hikari tuning + `server.shutdown: graceful`.
- Idempotencia de pago: hash de request, comparar monto/moneda, key no vacía (P2-15).
- `WebhookEvent` unique por `(proveedor, externalEventId)` (P2-16).
- Orden de `PlanExceptionHandler`, `checkExpirations` por ítem, dispatcher de outbox atómico y purga correcta (P2-17/18/19/20).
- `improveWithAi` con `@RequiresPlan`, parser regex sin match vacío y parseo tolerante (P2-23).
- Worker OCR con confirms/queue de resultado y clasificación de errores retryables (P2-24).

### Fase 3 — Operación (1-2 meses)

- Flyway con baseline + migraciones versionadas + `shedlock`.
- CI (build + tests + k6 smoke) y tests para `catalog`/`chat`.
- PgBouncer y/o réplica de lectura según el tráfico real.
- Limpiar `.pyc`, arreglar Swagger, mover API key a header, excluir 4xx del retry.

---

## 9. Anexo A — Evidencia de rendimiento

- Reporte HTML: `qa/reports/saturation/report.html`
- Resumen crudo: `qa/reports/saturation_summary.json`
- Script: `qa/stress/saturation.js`
- Guía: `qa/README.md`

Comando reproducible:

```powershell
cd qa
$env:START_RATE=100; $env:RATE_STEP=150; $env:RATE_STEPS=12
$env:PRE_VUS=1200; $env:MAX_VUS=2500; $env:USER_POOL_SIZE=1200; $env:SLEEP=0.2
npm run stress:saturation -- http://127.0.0.1:8082
```

Antes de comparar, fijar el pool de forma determinista:

```powershell
docker compose up -d --force-recreate mismonedas
docker inspect spring_app --format '{{range .Config.Env}}{{println .}}{{end}}' | Select-String HIKARI
docker exec postgres_db psql -U postgres -d db_mismonedas -c "show max_connections;"
```

---

## 10. Anexo B — Hallazgos QA ya corregidos

De `qa/e2e/KNOWN-ISSUES.md`:

| ID | Estado | Nota |
|---|---|---|
| BUG-1 | Corregido | Doble persistencia de transacciones eliminada |
| BUG-1B | Corregido | `Idempotency-Key` ya no devuelve 409 por doble insert |
| BUG-3 | Corregido | Tipo categoría ≠ transacción → 400 determinista |
| BUG-2 | Esperado en dev | PREMIUM por defecto; ver P0-3 para producción |

---

## 11. Re-auditoría ampliada (2026-10-24)

### 11.1 Alcance y método

Segunda pasada línea por línea sobre los módulos que la primera auditoría no cubrió a fondo: `identity` (auth, JWT, refresh, borrado de cuenta), `billing` (pagos, suscripciones, webhooks, gating), `document` (OCR, imágenes, S3), `notifications` (outbox, FCM, email, DLQ) y `shared`. Se contrastó cada hallazgo con el árbol de trabajo en `6f73218`; los ítems que dependen del esquema real de BD se marcan como sospechosos.

No se repiten los hallazgos de las secciones 3-6. Esta sección añade **2 P0, 11 P1 y 15 P2**.

### 11.2 Los dos P0 nuevos

- **P0-7 · Pagar no activa la suscripción.** `PaymentRequest.subscriptionId` existe (`PaymentRequest.java:26-27`) pero `PaymentMapper` lo ignora (`:26-36`) y no hay ningún `setSubscription` en el módulo. `PaymentServiceImpl.markCompleted` solo transiciona el pago (`:149-157`) y **no se llama desde ningún controlador ni listener**; el webhook tampoco activa la suscripción (`applyNotification`, `:287-299`). En un flujo sin el auto-PREMIUM de dev, un pago `COMPLETED` deja la suscripción en `PENDING` y `getActivePlanFor` sigue devolviendo `FREE`. Es el complemento de P0-1: el premium se puede obtener gratis, pero pagarlo no sirve.
- **P0-8 · Pre-hijack de cuentas vía Google.** El registro no verifica el email (`UserServiceImpl.java:53-66`). `GoogleAuthServiceImpl.authenticate` busca por email y auto-vincula (`:32-39`); `login` emite tokens sin comprobar proveedor ni posesión de la contraseña (`:43-59`). Un atacante registra el email de la víctima con su propia contraseña; cuando la víctima entra con Google, el atacante conserva el acceso por password.

### 11.3 Nuevos P1

| ID | Hallazgo | Evidencia | Fix |
|---|---|---|---|
| P1-10 | OCR confía en el monto; un negativo acredita la wallet | `ReceiptScanServiceImpl.java:162-167`; `TransactionServiceImpl.java:92-98`; `WalletServiceImpl.java:213-215` | Exigir monto positivo y acotado tras OCR; no sobrescribir el monto del request; guardar `delta > 0` en la wallet |
| P1-11 | Envío SMTP/FCM dentro de la TX de entrega | `NotificationDeliveryService.java:88-118` | Claim `SENDING` en TX, enviar fuera, registrar resultado en TX nueva, clave de idempotencia |
| P1-12 | FCM borra tokens válidos y permite token compartido entre usuarios | `FCMNotificationAdapter.java:115-123`; `FcmTokenServiceImpl.java:38-59` | Borrar solo en `UNREGISTERED`/`SENDER_ID_MISMATCH`; transferir token al registrar; borrar en logout; unique por token |
| P1-13 | Notificaciones en `BEFORE_COMMIT`, purga que ignora el umbral y DLQ sin consumidor | `BudgetNotificationListener.java:26`; `NotificationServiceImpl.java:154-159`; `NotificationMessageRecoverer.java:19-33`; `NotificationScheduler.java:29-42` | `AFTER_COMMIT` o `REQUIRES_NEW` con try/catch; usar `threshold`; consumidor/replay de DLQ y reintento de `FAILED` |
| P1-14 | El template fija un access token de 23,5 h sin `jti`/revocación; secreto débil solo advierte | `.env.example:55,58`; `JwtTokenServiceImpl.java:38-47,77-101`; `AuthTokenServiceImpl.java:80-91` | TTL 5-15 min; `jti`/`tokenVersion` en el filtro; revocar en logout/password change; fallar si el secreto es corto/placeholder |
| P1-15 | Borrado/reset deja wallets, billing, documentos, chat, insights y reglas de recurrencia; el reset borra el historial pero no resetea saldos | `UserServiceImpl.java:158-176`; listeners solo en `catalog`, `ledger`, `planning`, `scheduling`, `notifications` | Servicio único de borrado en orden de dependencias + cascadas; test con datos en todos los módulos |
| P1-16 | Pagos sin validación de monto/moneda; webhook ignora el monto notificado | `PaymentRequest.java:29-35`; `PaymentServiceImpl.java:287-299` | `@DecimalMin`, `@Digits`, ISO-4217, precio de plan server-side; comparar monto/moneda del webhook |
| P1-17 | `PAST_DUE` sin dunning; plan activo tras vencer; cancelar revoca el período pagado | `BillingServiceImpl.java:125-136,165-178`; `SubscriptionServiceImpl.java:21-34` | Job de cobro/reintento/expiración; plan = `ACTIVE && currentPeriodEnd > now`; cancel-at-period-end idempotente |
| P1-18 | `ImageIO.read` decodifica antes de limitar píxeles | `ImageServiceImpl.java:109-116` | Leer dimensiones con `ImageReader` antes de decodificar; límite de píxeles y bytes |
| P1-19 | URL prefirmada de S3 firmada con `:8081`, nginx reenvía `Host` sin puerto | `nginx.conf:74`; `docker-compose.yml:29,129-132` | `proxy_set_header Host $http_host`; alinear endpoint público; test de descarga siguiendo redirects |
| P1-20 | Normalización de email inconsistente | `UserServiceImpl.java:55,58,140`; `GoogleAuthServiceImpl.java:44` | Normalizar antes de `existsByEmail`, guardar y buscar; `findByEmailIgnoreCase` o unique `lower(email)` |

### 11.4 Nuevos P2

| ID | Hallazgo | Evidencia |
|---|---|---|
| P2-15 | Intento de pago externo antes del `save` → referencia `MANUAL-null-<millis>`; replay de idempotencia no compara monto/moneda; key en blanco aceptada | `PaymentServiceImpl.java:65-84`; `ManualPaymentGatewayAdapter.java:13-17` |
| P2-16 | `WebhookEvent.externalEventId` es unique global y la búsqueda ignora el proveedor | `WebhookEvent.java:30-31`; `WebhookEventRepository.java:18`; `PaymentServiceImpl.java:194` |
| P2-17 | `PlanExceptionHandler` sin orden compite con el handler genérico → 500 en vez de 403 | `PlanExceptionHandler.java:8-15`; `GlobalExceptionHandler.java:178-181` |
| P2-18 | `checkExpirations` procesa todo en una TX sin catch por ítem; `autoRenew` Boolean puede desboxear null | `BillingServiceImpl.java:165-178`; `Subscription.java:52-53` |
| P2-19 | Dispatcher de outbox: `isPending` → `publish` → `markPublished` no atómico | `NotificationOutboxDispatcher.java:42-58` |
| P2-20 | Purga borra outbox `FAILED` (mata replay/auditoría) y no borra `notification_outbox_delivery` | `NotificationScheduler.java:44-59` |
| P2-21 | `markAsRead` sin `@Transactional`; merge detached puede pisar `DISCARD` | `NotificationServiceImpl.java:125-130` |
| P2-22 | Limpieza de tokens FCM usa `updatedAt` que nunca se refresca al enviar | `FcmTokenCleanupJob.java:20-29`; `FcmToken.java:48-49` |
| P2-23 | `improveWithAi` sin `@RequiresPlan`; parser regex hace match vacío de PEN; fechas/montos lanzan unchecked | `ReceiptScanServiceImpl.java:208-242`; `RegexReceiptParserAdapter.java:60,188-196` |
| P2-24 | Worker OCR declara exchange de resultado pero no queue/binding; ACK tras publish sin confirms | `services/doc-processing/app/messaging.py:56-72,85-87,115-126` |
| P2-25 | Credenciales S3 con defaults de dev si faltan; SeaweedFS `mini` sin auth explícita (sospechoso) | `SeaweedFsProperties.java:17-22`; `docker-compose.yml:112-124`; `nginx.conf:60-81` |
| P2-26 | `UserServiceImpl.update` re-codifica la contraseña siempre y no revoca sesiones | `UserServiceImpl.java:125-132` |
| P2-27 | Refresh concurrente con el mismo token revoca la familia recién emitida | `AuthTokenServiceImpl.java:52-78` |
| P2-28 | `email_verified` nulo se acepta como válido en Google | `GoogleIdTokenVerifier.java:67-74` |
| P2-29 | `audiences` configurado pero el decoder no valida `aud`; los tokens no lo emiten | `JwtResourceServerConfig.java:27-55`; `application.yml:47-48` |

### 11.5 Ajustes a la versión anterior

- **`resetAccountData` no es un reset completo.** `UserServiceImpl.deleteUserData` publica `UserDeletedEvent` (`:173-176`), que sí elimina transacciones, presupuestos, metas, schedules, categorías, razones y notificaciones; después `resetAccountData` publica `UserDataResetEvent` y resiembra categorías (`CatalogListener.java:31-34`). Pero **no** toca wallets (quedan con saldos), billing, documentos, chat, insights ni reglas de recurrencia. El nombre del endpoint promete más de lo que hace.
- **`refund` no es completamente no-op.** El adapter manual no reembolsa, pero el pago sí pasa a `REFUNDED` (`PaymentServiceImpl.java:170-179`). La suscripción no se toca. Además, `PaymentState` solo permite `REFUNDED` desde `COMPLETED`/`DISPUTED` (`:23-30`), pero el provider se llama **antes** de validar la transición: un refund sobre `PENDING` invoca al proveedor y luego lanza 409.
- **`autoRenew` puede desboxear null.** `Subscription.autoRenew` es `Boolean` (`Subscription.java:52-53`) y `checkExpirations` hace `subscription.getAutoRenew() ? ...` (`BillingServiceImpl.java:173`). El `nullable=false` de la columna no protege contra una entidad construida sin el campo.
- **La purga de notificaciones ignora el parámetro.** `purgeOlderThan(threshold)` consulta con `OffsetDateTime.now()` (`NotificationServiceImpl.java:154-159`), así que borra todas las `SENT` sin importar la fecha pedida.
- **FCM `INVALID_ARGUMENT` no es un token inválido.** `FCMNotificationAdapter` lo agrupa con `UNREGISTERED` y borra el token (`:115-123`); un payload mal formado puede vaciar los tokens del usuario.
- **El registro comprueba el email antes de normalizarlo.** `existsByEmail(request.getEmail())` usa el valor crudo y recién después hace `setEmail(...toLowerCase())` (`UserServiceImpl.java:55,58`). Con un email en mayúsculas se puede duplicar cuenta o romper el unique.
- **`PaymentMapper` ignora `subscriptionId`.** No es solo que falte la llamada a `activate`: el pago nunca queda vinculado a la suscripción (`PaymentMapper.java:26-36`).
- **`ImageIO.read` no tiene límite de píxeles.** Decodifica la imagen completa antes de crear el `BufferedImage` RGB (`ImageServiceImpl.java:109-116`); una imagen comprimida pequeña puede agotar heap.
- **El `Host` de nginx rompe la firma S3.** `proxy_set_header Host $host` (`nginx.conf:74`) elimina `:8081`; la URL prefirmada se generó con `http://localhost:8081` (`docker-compose.yml:29`). El navegador recibe `SignatureDoesNotMatch`.
- **`GoogleIdTokenVerifier` acepta `email_verified=null`.** La condición es `emailVerified != null && !emailVerified` (`:72`), así que un token sin el claim pasa.
- **El decoder JWT no valida `aud`.** `audiences` está en `application.yml:47-48`, pero `JwtResourceServerConfig` no lo aplica y `JwtTokenServiceImpl` no emite `aud`.
- **`NotificationOutboxDispatcher` tiene check-then-act.** `isPending` y `markPublishedIfPending` no son un claim atómico; el poller y el dispatch async pueden publicar dos veces (`:42-58`).

### 11.6 Lo que sigue OK (para no sobrecorregir)

- Rotación de refresh tokens con familia y revocación al reusar un token revocado (`AuthTokenServiceImpl.java:52-78`), salvo la carrera P2-27.
- Idempotencia de webhooks: `externalEventId` unique + re-check (`PaymentServiceImpl.java:194-197`), salvo el alcance global por proveedor P2-16.
- Ownership en servicios y controladores; el usuario sale del `SecurityContext` (la excepción es `ReasonResolver`, P2-3).
- Idempotencia de pagos con verificación de dueño (`PaymentServiceImpl.java:65-75`).
- Outbox/DLQ/ShedLock existen y están bien planteados; el problema es que el DLQ no tiene consumidor y el envío externo está dentro de la TX.
- Plantillas Thymeleaf con `th:text` (auto-escape) y rate limit en nginx (aunque falte el límite por cuenta en la app).

---

**Conclusión:** la arquitectura aguanta; lo que falla son los bordes. Los P0 son de bajo esfuerzo y alto impacto, y ahora son **ocho**: además de la monetización burlable y la corrupción de saldo, el flujo de pago está incompleto (pagar no activa) y el auto-link de Google permite pre-hijack. La deuda operacional más cara sigue siendo el esquema sin Flyway, porque impide reproducir cualquiera de estos bugs en un entorno limpio.

---

### Changelog

| Versión | Fecha | Cambio |
|---|---|---|
| v1 | 2026-10-24 | Auditoría inicial: 6 P0, 9 P1, 14 P2; verificación del informe y auditoría k6 |
| v2 | 2026-10-24 | Re-auditoría ampliada: +2 P0, +11 P1, +15 P2; correcciones de identity, billing, document, notifications y shared |
