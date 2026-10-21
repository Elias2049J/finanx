# Hallazgos QA — suite e2e (`qa/e2e`)

Este documento registra los defectos que la suite e2e detectó contra el backend.
Cada hallazgo incluye la evidencia (endpoint + aserción) y la causa raíz en el
código. Cuando un defecto se corrige, el test asociado pasa de `expect(...)`
(tolerante) a `assert(...)` (estricto).

> Convención en los scripts: las aserciones marcadas con `KNOWN BUG` usan
> `expect(...)` para no abortar el escenario; el resto usa `assert(...)`.

---

## Estado actual

| ID | Descripción | Estado |
|---|---|---|
| BUG-1 | Doble persistencia de transacciones | ✅ **Corregido** |
| BUG-1B | `Idempotency-Key` siempre responde 409 | ✅ **Corregido** (misma causa que BUG-1) |
| BUG-3 | Tipo categoría ≠ tipo transacción → 500 en vez de 400 | ✅ **Corregido** |
| BUG-2 | El plan por defecto es PREMIUM | ⚪ **Esperado en dev** (no es defecto) |

---

## BUG-1 — Doble persistencia de transacciones (crítico) — ✅ CORREGIDO

**Endpoints afectados:** `POST /api/v1/transactions` (y todo lo que agrega
transacciones: budgets, savings, analytics).

**Evidencia (antes).** Un único `POST /transactions` de 100.00 devolvía un `id`
pero en la base quedaban **dos filas idénticas**:

```
POST /transactions (100, SPENT) -> devuelto id=21
GET /transactions => totalItems=2
  id=21 amount=100.00 type=SPENT active=true
  id=20 amount=100.00 type=SPENT active=true
```

**Causa raíz.** `TransactionServiceImpl.create` persistía dos veces:
1. `TransactionServiceImpl.java:112` → `transactionRepository.save(transaction)`
2. `TransactionServiceImpl.java:150` → un segundo servicio de persistencia, que
   creaba una **nueva** `Transaction` y la guardaba internamente.

**Solución aplicada.** Se eliminó el `save` temprano de `create()` y se dejó que
el **único** punto de persistencia sea `TransactionServiceImpl.saveAndPost(...)`.
El manejo de la carrera de idempotencia (`DataIntegrityViolationException`) se
movió para envolver esa llamada. Resultado: un solo `save` por alta.

**Verificación.** `ledger.js` → `hay exactamente 2 transacciones` (assert) ✅.
Los agregados de `budgets.js` (health), `savings.js` (acumulado) y `analytics.js`
(balance/spentAmount) también pasaron a `assert` y pasan.

---

## BUG-1B — `Idempotency-Key` siempre respondía 409 (crítico) — ✅ CORREGIDO

**Endpoints afectados:** `POST /api/v1/transactions` con header `Idempotency-Key`.

**Evidencia (antes).** Cualquier alta con `Idempotency-Key` fallaba:

```
POST /transactions (Idempotency-Key: idem_xxx) -> 409
{ "message":"Violación de integridad de datos","status":409 }

POST /transactions (sin header Idempotency-Key) -> 201
```

**Causa raíz.** Manifestación directa de BUG-1: las **dos** inserciones usaban la
**misma** `idempotency_key`, violando la restricción única.

**Solución aplicada.** Al quedar un único `save`, la key se inserta una sola vez y
el replay (`findByIdempotencyKey` al inicio de `create()`) funciona.

**Verificación.** `ledger.js` → grupo `idempotencia` (assert) ✅: primer POST 201,
segundo POST con la misma key devuelve el mismo `id`, y solo existe una fila.

---

## BUG-3 — Tipo de categoría ≠ tipo de transacción respondía 500 en vez de 400 — ✅ CORREGIDO

**Endpoints afectados:** `POST /api/v1/transactions`.

**Evidencia (antes).** Enviar `type: SPENT` con una categoría `INCOME` devolvía,
de forma intermitente, **500**:

```
POST /transactions { type: SPENT, categoryId: <INCOME> } -> 500
{ "message":"Ocurrio un error","status":500 }
```

**Causa raíz.** La validación se ejecutaba **después** del primer `save`; el
rollback envolvía la `IllegalArgumentException` y caía en el handler genérico → 500.

**Solución aplicada.** La validación `transaction.getType() != category.getType()`
se movió a `TransactionServiceImpl.create()` **antes** de persistir. Al no haber
transacción sucia, la `IllegalArgumentException` llega limpia al
`@ExceptionHandler` y se devuelve **400** de forma determinista.

**Verificación.** `ledger.js` → `categoria INCOME con type SPENT -> 400` (assert) ✅.

---

## BUG-2 — El plan por defecto es PREMIUM — ⚪ ESPERADO EN DEV

**Decisión:** en el entorno de desarrollo el usuario se aprovisiona como
**PREMIUM**, por lo que esto **no se considera un defecto** para las pruebas.

**Comportamiento.** Un usuario recién registrado obtiene una suscripción
**PREMIUM ACTIVE**:

```
GET /subscriptions/current -> 200
{ "plan":"PREMIUM", "state":"ACTIVE", "amount":0.00, "autoRenew":false, ... }
```

**Causa.** `BillingListener.on(UserRegisteredEvent)` (`BillingListener.java:22-34`)
crea y activa una suscripción PREMIUM al registrar.

**Impacto en dev.** El gating de features premium (p. ej. segunda billetera en
`wallets.js` / `billing.js`) no bloquea, lo cual es **esperado** con plan PREMIUM.
Los tests conservan aserciones tolerantes (`expect`) para documentar que el plan
por defecto en dev es PREMIUM y no FREE.

**Tests que lo cubren:** `billing.js`, `wallets.js` (aserciones `expect`).
