# Auditoría de mejoras — Finanx (monolito)

**Fecha:** 2026-10-24
**Commit auditado:** `6f73218` (`master`)
**Re-auditoría:** 2026-10-24 — segunda pasada ampliada sobre `identity`, `billing`, `document/OCR`, `notifications` y `shared`.
**Alcance:** bugs, consistencia, rendimiento y operación del monolito Spring Boot.
**Método:** lectura directa del código + contraste con la segunda pasada de auditoría + resultados de las pruebas de saturación k6 de esta sesión. Cada hallazgo crítico fue verificado en el árbol de trabajo; no se ejecutaron exploits contra un entorno levantado.

> **Nota sobre rutas.** El informe original referencia `services/MisMonedas`. En este repositorio el monolito es la raíz: `src/main/java/com/elias/mismonedas`.
>
> **Nota sobre fuentes.** La auditoría comparativa base de la sesión anterior no está versionada en el repositorio. Este documento sintetiza: (a) la segunda pasada de profundización, (b) la auditoría de rendimiento de esta sesión, y (c) los hallazgos QA ya registrados en `qa/e2e/KNOWN-ISSUES.md`. Donde una afirmación del informe recibido no se sostiene contra el código, se corrige explícitamente en la sección 7.

> **Actualización (2026-11-03):** las referencias a `ManualPaymentGatewayAdapter` y al gateway manual son históricas. Ese adaptador se eliminó junto con el proveedor de transferencia manual; el flujo actual usa un `PaymentPort` opcional y un verificador fake de Google Play (`app.billing.google-play.mode=fake`). Los hallazgos P0-2, P0-6 y P2-15 se conservan como contexto de la auditoría original, no como descripción del flujo de pago actual.
>
> **Actualización (2026-11-04):** P0-1 quedó corregido. `POST /subscriptions/{id}/activate` y `/{id}/renew` exigen `ROLE_SYSTEM` a nivel de filtro y de método; un usuario normal ya no puede autoactivar PREMIUM. La activación de usuarios debe pasar por un pago verificado. También se añadieron validaciones de arranque para producción, Play Billing deshabilitado por defecto y documentación en `docs/production-runbook.md`.

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

> **Estado (2026-11-04): corregido.** `activate` y `renew` ahora tienen
> `@PreAuthorize("hasRole('SYSTEM')")` y una regla equivalente en
> `WebSecurityConfig`. El test
> `SubscriptionControllerSecurityTest` verifica que `ROLE_USER` recibe 403 y
> que solo `ROLE_SYSTEM` puede invocarlos. La creación de suscripciones sigue
> siendo pública, pero ya no permite activar el plan sin un pago verificado.

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
- **Semanal (revisado, sin cambio):** la fórmula actual (`delta <= 0 → delta += 7 * interval`) es equivalente a `Math.floorMod(target - base, 7 * interval)` para todo `delta` en `[-6, 6]`; el caso `delta == 0` ya suma el intervalo. No se reprodujo un escenario distinto del mensual/anual, así que se deja igual y se cubre con tests de regresión (`+intervalo` con base en el día objetivo y búsqueda del día objetivo desde `start`).
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

**Aplicado:** dos consultas agregadas (dimensiones y día local), `LEFT JOIN` para categoría/motivo nulos, timelines sin `context` y conversión de totales de grupo. El tope máximo de rango queda pendiente por decisión de producto. Ver sección 12.

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
6. **N+1 de schedules** (`ScheduleServiceImpl.java:152-173`: una query de wallet primaria por fila). Analytics ya agrega en SQL (P1-8); se acepta rango sin tope por decisión de producto; queda la réplica de lectura.
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

- ✅ P1-1/P1-2: ancla de recurrencia (`RecurrenceServiceImpl`) + tests de 31-ene y ancla mensual/anual.
- ✅ P1-4: claim atómico de ocurrencia + clave determinista en `ScheduleServiceImpl` (`de41784`).
- ✅ P1-5: recuperación de idempotencia fuera de la transacción abortada (`e04c29a`).
- ✅ P1-9: el gasto analítico filtra movimientos por el usuario propietario del presupuesto (`27b710d`).
- ✅ P1-6 por decisión explícita: sin fallback; el fallo del modelo principal sigue siendo 503 y no persiste respuestas sintéticas.
- ✅ P1-7: sin denylist heurístico; mensajes/contexto son entradas no confiables y el contexto va delimitado/escapado en el input de usuario. No garantiza resistencia total a prompt injection.
- Catch de `CurrencyConversionException` en saving goals (degradar a `partial`).
- ✅ P1-10: rechazar montos OCR no positivos y validar el monto efectivo antes de persistir (`86038ae`). La precedencia del monto del request frente al OCR queda como decisión separada.
- Notificaciones: enviar fuera de la TX, FCM solo borra `UNREGISTERED`, token único por usuario, `AFTER_COMMIT`, purga con `threshold` y DLQ con replay (P1-11/12/13).
- Sesiones: access TTL 5-15 min, `jti`/`tokenVersion`, revocación en logout/password change y secreto fail-fast (P1-14).
- Borrado/reset de cuenta completo y en orden de dependencias (P1-15).
- Billing: validar monto/moneda, comparar webhook, dunning de `PAST_DUE`, plan = `ACTIVE && currentPeriodEnd > now`, cancel-at-period-end (P1-16/17).
- ✅ P1-18: límite de 5 MiB y 20 megapíxeles comprobado antes de decodificar (`d502ef6`).
- ✅ P1-19: nginx conserva el host y puerto requeridos por SigV4 (`6521d01`); falta validar la descarga end-to-end con Docker.
- ✅ P1-20: normalización única de email y unique case-insensitive (`255b21d`).

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
- ✅ Orden de `PlanExceptionHandler`, `checkExpirations` por ítem, dispatcher de outbox atómico y purga correcta (P2-17/18/19/20).
- ✅ `improveWithAi` con `@RequiresPlan`, parser regex sin match vacío, parseo tolerante y contrato de mejora con IA (P2-23).
- ✅ Worker OCR con confirms/queue de resultado y clasificación de errores retryables (P2-24).
- ✅ Credenciales S3 fail-fast, `weed mini` con auth explícita y endpoint público alineado con nginx (P2-25).

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

## 12. Estado de aplicación

Aplicado sobre `master` el 2026-10-26. Verificación del lote: `.\mvnw.cmd -q test` → **459 tests backend, 0 fallos**; `python -m unittest discover -s services/doc-processing/tests` → 7 tests; `docker compose config` parsea correctamente; Android `:app:compileDebugKotlin :app:testDebugUnitTest` → 34 tests y cliente Kotlin `:kotlin-client:test` → 590 tests, 0 fallos. Smoke con Docker: V10–V13 se aplicaron sobre el esquema v9; un escaneo sintético terminó `DONE` con monto 45.00 PEN y el contrato devolvió `improved=false`, `aiImprovedAt=null` y `requiresReview=false`; la mejora explícita devolvió 200 con `improved=true`, `aiImprovedAt` con offset y `requiresReview=false`; con confianza 0.60 el GET devolvió `requiresReview=true`; con estado `PROCESSING` la mejora respondió 409 `CONFLICT`. La descarga prefirmada por nginx `8081` devolvió 200, la lectura sin firma 403 y un contenedor sin `APP_STORAGE_*` falló al arrancar por `app.storage.seaweedfs.internal-endpoint is required`. Android y el cliente Kotlin se regeneraron/actualizaron en este lote.

| ID | Cambio | Commit | Tests |
|---|---|---|---|
| P0-3 | `BillingListener` condicionado a `app.billing.auto-premium` (default `false`; `true` en `docker-compose.yml` y `.env.example`) | `586c510` | `BillingListenerTest` (3) |
| P0-4 | `Budget.effectivePercentAlert()` con default 50 y rango [0,100]; `checkAllBudgets` aísla cada presupuesto en un `try/catch` | `91a103a` | `BudgetTest` (+2), `BudgetServiceImplTest` (1) |
| P0-5 | `TransactionRepository.deactivateIfActive` (UPDATE condicional) y `disable()` revierte saldo solo si gana el claim | `8402e0a` | `TransactionServiceImplTest$DisableTest` (5) |
| P0-7 | `create()` resuelve `subscriptionId` con ownership y `markCompleted`/webhook aplican `BillingService.applyConfirmedPayment()` (idempotente) | `6dd147d` | `PaymentServiceImplTest` (+6), `BillingServiceImplTest` (+6) |
| P0-8 | La cuenta de Google se identifica por `sub`; si el email ya pertenece a una cuenta local se rechaza con 409 en vez de auto-vincular; `email_verified` obligatorio | `45980ed` | `GoogleAuthServiceImplTest` (5), `GoogleIdTokenVerifierTest` (5) |
| P0-2 | `refund()` valida `COMPLETED/DISPUTED → REFUNDED` antes de llamar al gateway y cancela la suscripción con `cancelForRefund()` | `388a08e` | `PaymentServiceImplTest$RefundTest` (3), `BillingServiceImplTest$CancelForRefundTest` (3) |
| P0-6 | `WebhookController` en `/api/v1/webhooks`, solo existe con `app.billing.webhooks.enabled=true` (default `false` → 404); `permitAll()` para cuando se encienda; `PaymentExpirationScheduler` expira `PENDING` > 24 h y `PROCESSING` > 1 h; `NoResourceFoundException` → 404 | `a63d93f`, `7fc3cda` | `PaymentServiceImplTest$ExpireStalePaymentsTest` (2), `WebhookEndpointDisabledTest` (1), `GlobalExceptionHandlerTest` (+1) |
| P1-1 | Un presupuesto recurrente se finaliza cuando `now >= end` y congela el gasto de su propia ventana `[start, end]` | `bbb3d00` | `BudgetServiceImplTest` (+2) |
| P1-2 | Mensual y anual toman el día (y el mes) del ancla `rule.start`, no de la ocurrencia previa | `aafe06e` | `RecurrenceServiceImplTest` (4) |
| P1-3 | `totalsOf` degrada a parcial si falta una tasa: omite la fila, marca `partial` y expone `missingRates`; sin 503 | `a2aa2d7` | `SavingGoalServiceImplTest` (2), `SavingGoalTest` (+0) |
| P1-4 | Claim condicional atómico para transacciones y presupuestos; clave `schedule:{id}:{epochMilli}`; V4 versiona la columna y unique de idempotencia de presupuestos | `de41784` | `ScheduleClaimConcurrencyTest` (4, PostgreSQL), `ScheduleServiceImplBatchTest` |
| P1-5 | La recuperación de idempotencia corre fuera de la transacción abortada: el camino HTTP usa `TransactionTemplate` y recupera en una transacción limpia; el scheduler sigue `@Transactional` | `e04c29a` | `TransactionServiceImplTest` (adaptado), `TransactionIdempotencyConcurrencyTest` (2) |
| P1-6 | Decisión cerrada: no hay modelo fallback; ante fallo del principal se propaga 503, sin persistir respuestas sintéticas; bulkhead espera 1 s | `8ab025b` + decisión de producto | `ChatGeminiAdapterTest`, `GlobalExceptionHandlerTest` |
| P1-7 | Se elimina el denylist de lenguaje natural; el contexto financiero va como datos no confiables en el input de usuario, con delimitadores escapados, separado de las instrucciones del sistema | este tramo | `AiUtilsTest`, `ChatGeminiAdapterTest`; sin llamadas reales al modelo |
| P1-8 | `/dashboard/balance` agrega en SQL por dimensiones y por día local; timelines sin `context`; conversión de totales de grupo; `LEFT JOIN` para categoría/motivo nulos; decisión: sin límite máximo de duración en filtros de fecha; Android elige meses/año porque backend amplía a meses completos | `e02f2aa`, `786aa47`, `8bc9e0e`, `10479b5`; cliente `d01c8aa`; UI `b774ea0` | `TransactionAnalyticsServiceImplTest` (8), `TransactionAnalyticsAggregationTest` (3, PostgreSQL), `TimeSeriesPointContractTest` (1), `MonthYearRangeTest` (6) |
| P1-18 | Rechazo de imágenes mayores de 5 MiB o 20 MP; dimensiones inspeccionadas con `ImageReader` antes de decodificar | `d502ef6` | `ImageServiceImplTest` (+2) |
| P1-19 | Nginx reenvía `$http_host` en el proxy de storage para preservar el puerto de la URL SigV4 | `6521d01` | `NginxStorageProxyConfigurationTest`, `SeaweedFsConfigurationTest`; descarga real pendiente por Docker no disponible |
| P1-9 | El gasto de presupuesto suma solo movimientos cuyo `id_usuario` coincide con el dueño del presupuesto | `27b710d` | `TransactionSpecificationsTest` (+1, PostgreSQL), `BudgetAnalyticsServiceImplTest` |
| P1-10 | OCR y `TransactionServiceImpl` rechazan importes no positivos antes de completar el recibo o persistir/actualizar saldo | `86038ae` | `ReceiptScanServiceImplTest` (+3), `TransactionServiceImplTest` (+2) |
| P1-14 | Access tokens de 900 s ligados al `sid` de la familia refresh; el guard combina usuario activo y familia vigente en el datasource primario; logout revoca solo su familia y el cambio efectivo de contraseña revoca todas; JWT heredado o mal formado → 401 `invalid_token`, fallo de BD → 503; secreto fail-fast de ≥32 bytes UTF-8; Android renueva solo ante `invalid_token`, con single-flight, reintento único y bodies reproducibles, conservando la sesión ante red/5xx; logging HTTP sin cuerpos | `9ba17a5`, `03c7aeb`, `b8678b7`, `13d5ed1`, `879b939`, `3c541b6`, `ebdc127`, `dccca58`, `eef1717`, `09398f5` | `JwtTokenServiceImplTest` (9), `AuthTokenServiceImplTest` (9), `JwtResourceServerConfigTest` (4), `RestSecurityErrorHandlersTest` (4), `SecurityServiceImplTest` (5), `RefreshTokenFamilyConcurrencyTest` (2, PostgreSQL), `SessionAuthenticatorTest` (11), `SessionRefreshCoordinatorTest` (5), `SessionRefreshResponseClassifierTest` (5) |
| P1-20 | Registro, actualización y Google normalizan email; V6 backfillea y añade unique sobre `lower(email)`, con guard que aborta ante duplicados | `255b21d` | `UserServiceImplTest` (3), `GoogleAuthServiceImplTest` (5), `UserEmailNormalizationTest` (PostgreSQL) |
| P2-6 | Fallos transitorios conservan `RUNNING` y se reintentan en el siguiente ciclo; presupuesto ya materializado avanza sin duplicar; los fallos permanentes quedan `ERROR` con log estructurado | `de41784` | `ScheduleServiceImplBatchTest` |
| P2-17 | `PlanExceptionHandler` corre antes del handler genérico; un plan insuficiente responde 403 y no 500 | `3465bf2` | `PlanExceptionHandlerTest` (1) |
| P2-18 | `autoRenew` nulo se trata como `false`; cada suscripción se procesa en su propia transacción y un fallo no aborta el lote | `4c853f8` | `BillingServiceImplTest` (+2) |
| P2-21 | `markAsRead` usa un UPDATE condicional que excluye `DISCARD`; si no actualiza, conserva el 404 y no resucita avisos descartados | `c477cf0` | `NotificationServiceImplTest` (+3) |
| P2-22 | Solo se refresca `updatedAt` de los tokens FCM entregados; los inválidos se siguen borrando | `ab6480a` | `FCMNotificationAdapterTest` (+1) |
| P2-29 | Los JWT emiten y validan `aud` desde `spring.security.oauth2.resourceserver.jwt.audiences`; los tokens sin audiencia se rechazan | `50ab152` | `JwtTokenServiceImplTest` (+2), `JwtResourceServerConfigTest` (actualizado) |
| P2-30 / rendimiento | `@EntityGraph` también en el `findAll(Specification, Pageable)` del listado paginado, para evitar el N+1 de `wallet`, `category`, `reason`, `savingGoal` y `receipt.image` | `a300f40` | `TransactionSpecificationsTest` |
| Rendimiento | La moneda primaria se resuelve una vez por página en los listados de programaciones, no una vez por fila | `5ce47bb` | `ScheduleMapperTest` / suite de scheduling |
| P1-16 parcial | Validación de monto positivo, máximo 2 decimales y moneda ISO-4217; el webhook compara monto y moneda antes de transicionar y marca `IGNORED` si no coinciden. El precio de plan server-side sigue pendiente por decisión de producto | `604ad0d`; spec `fb8a980` | `PaymentServiceImplTest` (+4) |
| P2-15 | El pago se persiste antes de pedir el intento a la pasarela; el replay compara monto/moneda y una key en blanco se regenera | `d46965d` | `PaymentServiceImplTest` (+4) |
| P2-16 | `WebhookEvent` es único por `(proveedor, id_evento_externo)`; la deduplicación consulta ambos campos y V10 migra el constraint | `35267fb` | `PaymentServiceImplTest` (+1), V10 aplicada en PostgreSQL |
| P2-19 | Claim atómico del outbox con `claimed_until` (sin nuevo estado), lease de 2 min, publish fuera de TX, liberación del claim ante fallo y limpieza al transicionar; V12 crea la columna y el índice parcial | `5ca5774` | `NotificationOutboxDispatcherTest` (6), `NotificationOutboxClaimRepositoryTest` (3, PostgreSQL), `NotificationSchedulerTest` (4) |
| P2-20 | Retención separada: 90 días para terminales, 365 para `FAILED`/`PUBLISHED` obsoletos; se excluyen `FAILED` con `next_retry_at` futuro; borrado por lotes de 500; V11 limpia huérfanos y crea FK `ON DELETE CASCADE` de delivery a outbox | `d5783fe`, `3270bd0` | `NotificationOutboxRetentionRepositoryTest` (3, PostgreSQL), `NotificationSchedulerTest` (4) |
| P2-23 | Parser total y sin excepciones (`AMOUNT_TOKEN`, `parseMoney`, fechas `uuuu`), `improveWithAi` con `@RequiresPlan(PREMIUM)`, Gemini tolerante a campos inválidos y 503 para errores del proveedor. Contrato aditivo con `improved` derivado de `GEMINI`, `aiImprovedAt` persistido en V13 y `requiresReview = DONE && (confianza nula o < 0.7) && sin transacción`; `improveWithAi` solo acepta `DONE` no vinculados, bloquea la fila y devuelve el recibo sin cambios si Gemini no mejora | `20f537a`, `bed39b5`, `98fc8b2`, `8e0706e` | `RegexReceiptParserAdapterTest` (43), `GeminiReceiptParserAdapterTest` (4), `GlobalExceptionHandlerTest` (10), `ReceiptMapperTest`, `ReceiptScanServiceImplTest`, suite backend 459 |
| P2-24 | El worker declara exchange/cola/DLX/DLQ y bindings de resultado, activa `confirm_delivery`, publica con `mandatory=True`, hace ACK solo tras publish exitoso y NACK con requeue si falla; los jobs malformados se descartan | `019260e` | `services/doc-processing/tests/test_messaging.py` (4) |
| P2-25 | Sin defaults S3; claves S3 en Compose file secrets montados en SeaweedFS/backend/worker; healthcheck de master y S3; backup offline del volumen. `weed mini` sigue single-node, sin HA, cifrado en reposo de Compose secrets, backup remoto ni scheduler | `3a9d3af` + este tramo | `SeaweedFsPropertiesTest` (4), worker tests, compose config y smoke de contenedores |
| Deuda | Flyway versiona V1–V13; V4 añade idempotencia de presupuesto; V5 reconcilia columnas de `movimientos`; V6 normaliza email y crea unique case-insensitive; V7/V8 añaden claim/reintento y unicidad FCM; V9 versiona familias de refresh; V10 cambia el unique de webhooks; V11 añade la cascada de deliveries; V12 añade el claim del dispatcher; V13 añade `ai_improved_at` | `dc078ef`, `de41784`, `27b710d`, `255b21d`, `85d8936`, `f38e03a`, `6d2b6c1`, `35267fb`, `d5783fe`, `5ca5774`, `98fc8b2` | V10–V13 aplicadas sobre el esquema local; V1–V9 ya se probaron desde vacío; validación de checksum |

**Pendiente en P0:** P0-1 (pospuesto por decisión: exigir un pago `COMPLETED` sin pasarela real dejaría el premium inalcanzable). C-05 (`activate`/`renew` a `SYSTEM`) sigue pospuesto por la misma razón: P0-7 dejó el camino de activación listo, pero la pasarela manual no puede completar un pago.

**Aplicado en P1:** P1-1 a P1-14, P1-18, P1-19 y P1-20. **P1-6 cerrado por decisión:** se conserva el error 503 y no se incorpora modelo fallback. **P1-7 aplicado:** se elimina el denylist y se separan/escapan los datos financieros del system prompt; esto es defensa en profundidad, no garantía frente a prompt injection. **P1-8 sin tope por decisión explícita:** SQL agrega todo el rango, y la UI analítica ahora elige mes/año; los filtros de fechas existentes no tienen máximo. **P1-16 parcial:** monto/moneda y comparación del webhook aplicados; el catálogo de Google Play y sus productos todavía no existen. P1-15 y P1-17 siguen abiertos por decisión de producto.

**Aplicado en P2:** P2-6, P2-15 a P2-25 y P2-29. Los fallos transitorios de acceso a datos se reintentan en el siguiente ciclo fijo de 60 s; no hay backoff exponencial ni canal de alertas dedicado. Los logs estructurados incluyen `event`, `schedule_id` y tipo. Los errores no transitorios siguen pasando a `ERROR`.

**Riesgo residual de P0-8 (decisión explícita: no ampliar alcance):**

- El registro local sigue sin verificar el email, así que persiste el *email squatting*: un atacante puede registrar el email de otra persona y bloquearle el alta. Ya no obtiene acceso a la cuenta de la víctima, porque Google nunca auto-vincula.
- No existe flujo de vinculación explícita, así que una cuenta local no puede pasar a usar Google.
- La solución de raíz (verificación de email en el registro + `POST /auth/google/link` autenticado) queda como follow-up.

**Efectos secundarios verificados:**

- El contexto completo arranca sin `BillingListener` cuando la property está ausente (`TransactionSpecificationsTest`, 14,3 s de bootstrap).
- El JPQL nuevo se valida al crear el repositorio durante ese mismo bootstrap.
- `disable()` ya no escribe la entidad desde el persistence context: el UPDATE va directo a la base. Los listeners de `TransactionDisabledEvent` son `AFTER_COMMIT` y solo consumen el payload, así que no leen una entidad obsoleta.
- El `catch` por presupuesto aísla errores de dominio (NPE, datos corruptos); si el fallo viene de la base, la transacción ya está rollback-only y el commit falla de forma explícita.
- Flyway valida el V1 existente sin baseline y aplica V2/V3 sobre la base de desarrollo; una base limpia se crea desde cero con las tres migraciones.
- Los scripts de `database/manual/` quedan como histórico: su contenido está versionado en V2.
- Un reembolso deja la suscripción en `CANCELED` (decisión de producto). Es terminal: para volver a PREMIUM hace falta crear una suscripción nueva.
- `WebhookController` estaba en `/webhooks`, sin el prefijo `/api/v1` que usan todos los controladores; el e2e nunca llegaba a él. Ahora la ruta es `/api/v1/webhooks/{provider}`.
- Una ruta sin controlador devuelve 404, no 500: el catch-all de `Exception` convertía `NoResourceFoundException` en 500.
- Habilitar los webhooks exige implementar `PaymentPort.verifyWebhookSignature` y `parseWebhookPayload`; con el adaptador manual todo request recibe 401 aunque el endpoint esté encendido.
- La expiración de pagos no toca la suscripción: un pago `FAILED` por timeout deja la suscripción en `PENDING` y el usuario puede reintentar con un pago nuevo.
- Un presupuesto recurrente finaliza con el gasto de su ventana `[start, end]`, no con el del periodo actual; `BudgetScheduler` sigue creando una fila por ocurrencia.
- El ancla mensual/anual se toma de `rule.start`; si el día no existe en el mes destino se usa el último día (31-ene → 28/29-feb → 31-mar).
- Una tasa ausente en una meta de ahorro ya no devuelve 503: la meta se marca `partial`, se omite la fila no convertible y el par afectado aparece en `missingRates` (mismo contrato que `WalletBalanceResponse`).
- `POST /transactions` ya no devuelve 500 si dos peticiones con la misma `Idempotency-Key` chocan: la segunda recupera la fila de la primera en una transacción limpia. El camino del scheduler no cambia y sigue unido a su transacción.
- El claim de scheduler usa `UPDATE programacion ... WHERE proxima_ejecucion = :expected`; la concurrencia real confirma un ganador para transacciones y presupuestos. Se descartó JPQL sobre `Schedule`: Hibernate 7 traduce el update de herencia `JOINED` a un CTE cuya selección no revalida el predicado después de esperar el lock de fila.
- El claim, materialización de la ocurrencia y avance de `nextRunAt` siguen en la misma transacción. Si el presupuesto determinista ya existe, el scheduler avanza sin publicar otra vez el evento.
- Un fallo transitorio reconocido por Spring/JDBC deja intacta la ocurrencia y la programación `RUNNING`; el próximo ciclo la reintenta. Los errores permanentes quedan `ERROR` y se registran con campos estructurados.
- Analytics de presupuesto ahora filtra también por `Transaction.user.id`; una prueba PostgreSQL con una categoría compartida de forma inválida confirma que el movimiento de otro usuario no se suma.
- V1 no creaba `movimientos.id_usuario` ni `movimientos.clave_idempotencia`, aunque `Transaction` los mapea. V5 backfillea el usuario desde `billeteras`, valida la propiedad y crea FK, unique e índice compuesto; V1–V5 se probaron desde una base vacía.
- El OCR falla el recibo ante monto cero/negativo; `improveWithAi` no sustituye un resultado válido por uno no positivo. `TransactionServiceImpl` verifica el monto efectivo antes de `save`, por lo que no se registra movimiento ni se modifica saldo; no se cambia la precedencia request/OCR.
- El diagnóstico previo a V6 dio cero grupos duplicados por `lower(email)` y por `lower(trim(email))`, cero emails mixtos y cero emails con espacios en la BD local. V6 vuelve a comprobar duplicados antes del backfill y aborta si encuentra alguno, sin fusionar cuentas; registro, actualización, carga de usuario y Google comparten la normalización.
- Chat sin fallback por decisión explícita: ante el fallo del modelo primario, `ChatUnavailableException` produce 503 y evita persistir una respuesta sintética como 200. `max-wait-duration` es 1 s.
- P1-7: se elimina el denylist heurístico que confundía referencias a consejos anteriores con instrucciones maliciosas. El contexto financiero del usuario ya no se incrusta en el system prompt: se envía en el input de usuario como bloque delimitado y con `<`, `>` y `&` escapados. Las instrucciones del sistema lo tratan como datos no confiables; el test verifica que el contenido no pueda cerrar el delimitador y que CHITCHAT no reciba contexto financiero.
- El filtro anterior se evaluó offline con 40 ejemplos y mostró FP/FN; esa métrica ya no aplica porque se eliminó la clasificación por denylist. Ninguna prueba offline demuestra que un LLM resista toda prompt injection. El chat no tiene herramientas para ejecutar acciones; no se colocan credenciales en el contexto, pero el modelo puede seguir producir respuestas incorrectas o revelar texto de instrucciones.
- `ImageServiceImpl` rechaza cargas sobre 5 MiB y usa `ImageReader` para comprobar dimensiones antes de decodificar; el máximo es 20 MP. Ambos límites se refuerzan en el servicio, además del límite HTTP.
- El presigner conserva el puerto público configurado (`localhost:8081`) y nginx ahora reenvía el `Host` original (`$http_host`) al upstream de SeaweedFS. Se verificó con tests locales la URL firmada y la directiva; la descarga real a través de Docker no pudo probarse porque el daemon de Docker Desktop no estaba activo.
- P1-14: el access token dura 900 s y lleva `sid`; cada request protegida exige una fila refresh activa, no revocada y no vencida de esa familia, leída del datasource primario junto con el usuario. Logout revoca solo la familia recibida; el cambio efectivo de contraseña revoca todas. Los JWT emitidos antes del despliegue dejan de aceptarse y el refresh de 30 días sigue siendo la vía de renovación.
- V9 crea/valida `refresh_tokens` y un unique parcial de una fila activa por familia; aborta ante duplicados activos sin repararlos.
- Android: el `Authenticator` solo actúa ante `WWW-Authenticate: Bearer error="invalid_token"`, serializa la rotación y reutiliza el par nuevo si otra petición ya renovó. Un refresh terminal (4xx salvo 408/429) limpia la sesión y notifica a Compose; red/5xx conserva la sesión. Un refresh cuyo resultado se pierde no se reintenta automáticamente: el reuso del token anterior activa la detección de replay y puede cerrar la familia, así que el usuario tendría que volver a iniciar sesión. No hay idempotencia de refresh en este alcance.
- P1-8: `/analytics/dashboard/balance` ya no carga entidades; usa una consulta JPQL por dimensiones (con `LEFT JOIN` para no perder filas sin categoría/motivo) y una consulta nativa diaria con `AT TIME ZONE` y `GROUP BY` por ordinal. La conversión FX se aplica a totales de grupo, por lo que puede haber diferencias de céntimos frente al cálculo fila por fila (aceptado). `TimeSeriesPoint.context` desaparece y el cliente Kotlin se regeneró; Android solo usaba `date`/`amount`. Se aprobó explícitamente no limitar rangos. La pantalla de analíticas ahora selecciona mes/año y envía el día 1 y último día de cada mes; la lista de transacciones conserva selección diaria porque su endpoint no amplía a meses.
- P2-15/P2-16: el pago se guarda con `saveAndFlush` antes de crear el intento externo, así que la pasarela recibe un id real; si un replay de idempotencia trae otro monto o moneda, se rechaza con 409. La deduplicación de webhooks y el unique de V10 incluyen el proveedor, por lo que dos proveedores pueden reutilizar el mismo id de evento.
- P1-16: la validación de `PaymentRequest` cubre monto positivo, hasta dos decimales y código ISO-4217; el servicio repite la validación para llamadas internas. Un webhook con monto/moneda distintos no cambia el pago: el evento queda `IGNORED` con `mensaje_error`. El precio de plan server-side no se implementa porque no existe una tabla de precios y requiere decisión de producto.
- P2-19: el dispatcher reclama la fila con un UPDATE condicional (`state = PENDING` y `claimed_until` libre/vencido) antes de publicar; el publish ocurre fuera de la transacción. El lease de 2 min supera el timeout de confirmación de 5 s. Si el publish falla se libera el claim; si la instancia muere, el poller puede reclamar al vencer el lease. Un duplicado solo es posible si el lease vence durante un publish excepcionalmente lento, y el consumo sigue siendo idempotente por canal.
- P2-20: la retención ya no borra `FAILED` a los 90 días. Los terminales (`SENT`/`SKIPPED`) se purgan a 90 días; `FAILED` y `PUBLISHED` obsoletos a 365, excluyendo `FAILED` con `next_retry_at` futuro. El borrado va por lotes de 500 y V11 elimina huérfanos antes de crear la FK de delivery a outbox con `ON DELETE CASCADE`.
- P2-23: el parser regex es total (importes, fechas y campos nulos no lanzan) y `improveWithAi` exige PREMIUM; Gemini se sanea y sus fallos se mapean a 503. El contrato expone `improved` (derivado de `parserEngineUsed = GEMINI`), `aiImprovedAt` (V13) y `requiresReview` (`DONE`, confianza nula o < 0.7 y sin transacción); las filas históricas con Gemini conservan `improved = true` y `aiImprovedAt = null` porque no se backfillea. `improveWithAi` solo acepta recibos `DONE` no vinculados, toma un lock pesimista, valida el monto y devuelve 200 sin cambios si Gemini no supera la confianza actual. Smoke real: un recibo sintético terminó `DONE` y la mejora explícita devolvió `improved = true`, `aiImprovedAt` con offset y `requiresReview = false`; con confianza 0.60 el mismo recibo devolvió `requiresReview = true`; con estado `PROCESSING` la mejora respondió 409 `CONFLICT`.
- P2-24: el worker declara la topología completa de resultado (exchange, cola, DLX/DLQ y bindings), habilita confirms y publica con `mandatory=True`. Hace ACK solo después de un publish confirmado; si el publish falla, hace NACK con requeue. Un watchdog basado solo en `createdAt` se descartó porque podría fallar jobs que aún están en cola.
- P2-25: `SeaweedFsProperties` falla al arrancar si falta configuración; las claves S3 se montan como Compose file secrets en `weed mini`, backend y worker. Estos archivos se bind-mount desde el host y no quedan cifrados en reposo por Compose (a diferencia de secrets de Swarm), por lo que se restringe su ACL/cifra el disco. Healthcheck revisa master y S3; `scripts/backup-seaweedfs.ps1` crea una copia offline consistente deteniendo temporalmente API/worker/storage. Single-node: sin HA, backup remoto ni programación automática. Smoke con SeaweedFS 4.48: subida S3 autenticada con put/get/delete desde el worker, aplicación healthy con secretos montados y healthcheck de SeaweedFS healthy; smoke previo validó descarga prefirmada (200), rechazo sin firma (403) y fail-fast sin `APP_STORAGE_*`.

---

**Conclusión:** la arquitectura aguanta; lo que falla son los bordes. Queda P0-1 (gate de pago) hasta integrar Play Billing y productos reales. P1-1 a P1-14, P1-18 a P1-20 están aplicados; P1-6 quedó cerrado con 503 sin fallback y P1-7 usa límites estructurales con el riesgo residual de prompt injection documentado. P1-8 queda sin tope por decisión explícita y la UI selecciona meses/año; los demás filtros de fecha también quedan sin máximo. P1-16 está parcial hasta crear el catálogo de Play. P1-15 y P1-17 siguen pendientes. P2-6, P2-15 a P2-25 y P2-29 están aplicados. V10–V13 están aplicadas y validadas por checksum, y V1–V9 ya se reprodujeron desde cero.

---

### Changelog

| Versión | Fecha | Cambio |
|---|---|---|
| v1 | 2026-10-24 | Auditoría inicial: 6 P0, 9 P1, 14 P2; verificación del informe y auditoría k6 |
| v2 | 2026-10-24 | Re-auditoría ampliada: +2 P0, +11 P1, +15 P2; correcciones de identity, billing, document, notifications y shared |
| v2.1 | 2026-10-24 | Aplicados P0-3, P0-4 y P0-5 con tests; nueva sección 12 con el estado de aplicación |
| v2.2 | 2026-10-24 | Aplicados P0-7 y P0-8; Flyway reactivado con V1/V2/V3 y riesgo residual de identidad documentado |
| v2.3 | 2026-10-25 | Aplicado P0-2 (validación previa al gateway + cancelación de suscripción); P0-1 pospuesto por dependencia de pasarela |
| v2.4 | 2026-10-25 | Aplicado P0-6 (webhooks apagados por flag + expiración de pagos estancados); corregida la ruta `/api/v1/webhooks` y el 404 de rutas inexistentes; P0-1 sigue pospuesto |
| v2.5 | 2026-10-25 | Aplicados P1-1 (finalización de presupuestos recurrentes) y P1-2 (ancla mensual/anual); semanal revisado sin cambio |
| v2.6 | 2026-10-25 | Aplicado P1-3 (degradación parcial de metas de ahorro ante tasas ausentes) |
| v2.7 | 2026-10-25 | Aplicado P1-5 (recuperación de idempotencia tras el rollback; scheduler sin cambios) |
| v2.8 | 2026-10-25 | Aplicados P1-4 (claim realmente atómico, clave determinista y V4) y P2-6 (reintento de fallos transitorios y replay de presupuestos); 290 tests pasan |
| v2.9 | 2026-10-25 | Aplicado P1-9 (scope por dueño del presupuesto); V5 reconcilia columnas faltantes de `movimientos`; V1–V5 probadas en BD vacía; 291 tests pasan |
| v2.10 | 2026-10-25 | Aplicado P1-10: rechazar montos no positivos en OCR y antes de persistir; 296 tests pasan |
| v2.11 | 2026-10-25 | Aplicado P1-20: normalización canónica, diagnóstico sin duplicados en BD local y unique `lower(email)` con guard de migración; V1–V6 probadas en BD vacía; 300 tests pasan |
| v2.12 | 2026-10-25 | P1-6 parcial sin fallback model (error 503 y bulkhead 1 s); P1-7 deja pasar lenguaje natural; 305 tests pasan |
| v2.13 | 2026-10-25 | Evaluación offline del filtro de prompts sin invocar IA: 40 casos balanceados, matriz de confusión y métricas ES/EN; P1-7 registrado como parcial; 306 tests pasan |
| v2.14 | 2026-10-25 | Aplicado P1-18 (5 MiB/20 MP pre-decodificación) y P1-19 (`$http_host` con puerto); la descarga end-to-end queda pendiente por Docker inactivo; 310 tests pasan |
| v2.15 | 2026-10-25 | Aplicados P1-11/12/13: delivery por canal fuera de TX, unicidad/transferencia FCM y logout por dispositivo, listeners `AFTER_COMMIT`, purga por umbral, DLQ con hasta 5 reintentos exponenciales y replay `SYSTEM`; V1–V8 verificadas y suites backend/Android/cliente Kotlin pasan |
| v2.16 | 2026-10-25 | Aplicado P1-14: access tokens de 900 s con `sid` de familia, guard de sesión en PostgreSQL primario, logout por familia, password change revoca todas, JWT heredado → 401 `invalid_token`, fallo de BD → 503, secreto fail-fast y renovación Android single-flight solo ante `invalid_token`; V9 versiona `refresh_tokens`; 376 tests backend, 28 Android y 583 del cliente Kotlin pasan |
| v2.17 | 2026-10-25 | Aplicado P1-8: dashboard de analytics sobre agregados SQL por dimensiones y día local, sin `context` en timelines, `LEFT JOIN` para categoría/motivo nulos, contrato y cliente regenerados; 382 tests backend, 28 Android y 581 del cliente Kotlin pasan; tope de rango pendiente por decisión de producto |
| v2.18 | 2026-10-26 | Aplicados P2-17/18/21/22/29, el `@EntityGraph` paginado, la moneda primaria por página en schedules, la validación y comparación de webhooks de P1-16 (parcial), P2-15 y P2-16 con V10; 400 tests backend pasan; precio de plan server-side y P1-15/P1-17 siguen pendientes por decisión |
| v2.19 | 2026-10-26 | Aplicados P2-19 (claim atómico del outbox con `claimed_until` y V12), P2-20 (retención 90/365, purga por lotes y cascada de deliveries con V11), P2-24 (topología completa de resultado, confirms, `mandatory` y ACK tras publish) y P2-25 (credenciales S3 sin defaults, fail-fast, `AWS_*` y endpoint público 8081); P2-23 queda parcial (parser y guard de IA, contrato de mejora pendiente); 444 tests backend, 4 tests del worker y smoke real de OCR/SeaweedFS/nginx pasan |
| v2.20 | 2026-10-26 | Cerrado P2-23: contrato `improved`/`aiImprovedAt`/`requiresReview`, V13, `improveWithAi` solo para `DONE` no vinculados con lock y 409 para estados inválidos, cliente Kotlin regenerado y Android usando los campos del servidor; 454 tests backend, tests Android/cliente Kotlin y smoke real de mejora (200, `improved=true`, timestamp, `requiresReview` y 409) pasan; se elimina el snapshot `apidoc` obsoleto |
| v2.21 | 2026-10-26 | P2-25 MVP de producción: claves S3 migradas a Compose file secrets, healthcheck de master+S3 y backup offline con restauración de servicios; single-node/no-HA y límites de cifrado/backup quedan explícitos; 7 tests del worker, `docker compose config`, backend/SeaweedFS healthy y S3 put/get/delete autenticado |
| v2.22 | 2026-10-26 | P1-7: retirado el denylist heurístico; contexto financiero separado del system prompt, delimitado/escapado y no enviado a CHITCHAT; tests locales prueban la frontera sin llamar a Gemini. P1-6 queda cerrado por decisión: sin fallback y con 503; 459 tests backend pasan |
| v2.23 | 2026-10-26 | P1-8 sin máximo por decisión explícita; Android analíticas cambia atajos diarios por selector mes/año y envía meses calendario completos; se amplía el DateRangePicker diario a años 1–9999 sin límite de duración; 34 tests Android y 590 cliente Kotlin pasan (`b774ea0`) |
