# QA — Suite E2E (k6)

Análisis de cobertura end-to-end del backend de **Mis Monedas** (`finanx`).

- **Herramienta:** [k6](https://k6.io) (VUs=1, 1 iteración por escenario).
- **Target por defecto:** `http://localhost:8080/api/v1` (override con `BASE_URL`).
- **Reportes:** `qa/reports/e2e_<escenario>/report.html` (+ resumen JSON).
- **Defectos detectados:** ver [`e2e/KNOWN-ISSUES.md`](e2e/KNOWN-ISSUES.md).

---

## Cómo ejecutar

Requisitos: **k6** en el `PATH`, **Node.js** y el backend corriendo en `:8080`.

```bash
cd qa
npm install            # solo la primera vez (k6-html-reporter)

npm run e2e            # toda la suite (10 escenarios) -> OK si todos pasan
npm run e2e:list       # lista los escenarios disponibles
npm run e2e:ledger     # un escenario puntual
npm run e2e:scheduling # el e2e preexistente
```

Apuntar a otro entorno:

```bash
$env:BASE_URL="https://staging.example.com"; npm run e2e
```

`BASE_URL` no incluye `/api/v1` (el helper lo añade). El offset horario para
fechas se controla con `TZ_OFFSET_MINUTES` (default `-300`, America/Lima).

---

## Estructura

```
qa/e2e/
  lib/common.js        # helper compartido: bootstrap, asserts, fechas, métricas
  auth.js              # registro/login/refresh/logout, 401
  wallets.js           # billeteras, gating de plan, aislamiento
  catalog.js           # categorías (seed) y motivos, CRUD + disable
  ledger.js            # transacciones: CRUD, saldo, validación, idempotencia
  budgets.js           # presupuestos: health OK/NEAR_LIMIT/EXCEEDED
  savings.js           # metas de ahorro: progreso y completado automático
  analytics.js         # dashboards de balance y presupuestos
  billing.js           # suscripciones, pagos, webhook, gating premium
  notifications.js     # notificaciones + tokens FCM
  scheduling.js        # (preexistente) schedule -> transacción/presupuesto
  KNOWN-ISSUES.md      # defectos detectados por la suite
```

### Convenciones

- **`assert(name, ok, detail)`** — aserción **estricta**: si falla, aborta el
  escenario y cruza el threshold `e2e_failed: rate==0`. Úsala para el
  comportamiento correcto esperado.
- **`expect(name, ok, detail)`** — aserción **tolerante** para comportamientos
  **conocidos como defectuosos** (documentados en `KNOWN-ISSUES.md`). Registra el
  resultado en la métrica `e2e_known_issues` y un `console.warn` con la causa, pero
  **no** rompe la suite. Cuando el defecto se corrija, se promueve a `assert`.
- **`params(token, endpoint, expected?)`** — headers JWT + tag `endpoint` para
  métricas por endpoint. `expected` declara los status válidos (p. ej. `400`,
  `[401, 400]`) para que las respuestas de error **intencionales** no cuenten como
  `http_req_failed`.
- **`bootstrapUser()`** — registra un usuario fresco, login, preferencias y
  billetera principal; devuelve `{ token, refreshToken, userId, walletId, ... }`.

---

## Matriz de cobertura

| Módulo | Endpoints | Escenario | Estado |
|---|---|---|---|
| **identity** — auth | register, login, refresh, logout, me | `auth.js` | ✅ |
| **identity** — preferencias | POST/GET `/users/me/preferences`, `/timezones` | `auth.js` | ✅ |
| **wallets** | GET, GET/{id}, POST | `wallets.js`, `billing.js` | ✅ |
| **catalog** — categorías | seed, GET, GET/{id}, POST, PUT, POST/{id}/disable | `catalog.js` | ✅ |
| **catalog** — motivos | GET, GET/{id}, POST, PUT, POST/{id}/disable | `catalog.js` | ✅ |
| **ledger** | POST, GET list+filtros, GET/{id}, DELETE/{id} | `ledger.js` | ✅ |
| **ledger** — idempotencia | header `Idempotency-Key` | `ledger.js` | ✅ |
| **planning** — presupuestos | POST, GET, GET/{id}, cancel, disable, health | `budgets.js` | ✅ |
| **planning** — metas | POST, GET, GET/{id}, cancel, progreso | `savings.js` | ✅ |
| **insights** — analytics | dashboard/balance, dashboard/budgets, executions | `analytics.js` | ✅ |
| **billing** — suscripciones | current, list, POST, activate, cancel | `billing.js` | ✅ |
| **billing** — pagos | POST, GET, refund | `billing.js` | ✅ |
| **billing** — webhooks | POST `/webhooks/{provider}` (firma inválida) | `billing.js` | ✅ |
| **notifications** | GET, GET/{id}, read, discard, purge | `notifications.js` | ✅ |
| **notifications** — FCM | POST, GET, DELETE/{id} | `notifications.js` | ✅ |
| **scheduling** | POST, pause, resume, cancel, disable, ejecución | `scheduling.js` | ✅ |

### Gaps de cobertura (fuera de alcance de esta suite)

| Área | Motivo |
|---|---|
| `chat` (sesiones/mensajes) | Depende de un LLM externo; requiere mock/contrato propio. |
| `receipts` / `images` (OCR) | Depende de Gemini + microservicio `doc-processing` (RabbitMQ). |
| Conversión de moneda | Depende de `CurrencyBeacon` (API key real + red). |
| `reports` (CSV/PDF) | Export binario; validar en tests de contrato dedicados. |
| `users` admin (`/users`, `/users/search`) | Requieren rol `SYSTEM` (no hay usuario sistema sembrado). |
| Recuperación de contraseña | Flujo por email/Gmail externo. |

> Los e2e de esta suite evitan dependencias externas a propósito (montos en la
> misma moneda, sin OCR, sin LLM). Los flujos asíncronos se verifican con
> polling (`pollUntil`) tal como hace `scheduling.js`.

---

## Resultados

- **10/10 escenarios** con aserciones estrictas en verde (`npm run e2e` → exit 0).
- **3 defectos corregidos** (verificados por la suite):
  - **BUG-1** — doble persistencia de transacciones (1 POST → 2 filas). ✅ Corregido.
  - **BUG-1B** — `Idempotency-Key` respondía 409. ✅ Corregido (misma causa que BUG-1).
  - **BUG-3** — tipo categoría ≠ tipo transacción respondía 500 en vez de 400. ✅ Corregido.
- **BUG-2** — plan PREMIUM por defecto: **esperado en dev**, no es defecto.

Las correcciones se aplicaron en `TransactionServiceImpl` (un único punto de
persistencia en `saveAndPost(...)` y validación de tipo antes de persistir).
Ver detalle en `KNOWN-ISSUES.md`.
