# QA — Escenarios de stress/concurrencia (k6)

Pruebas de **carga y concurrencia** del backend de Mis Monedas. Complementan la
suite funcional e2e ([`../e2e/README.md`](../e2e/README.md)): aquí no se verifica
corrección, se mide **capacidad** bajo distintos modelos de tráfico.

- **Herramienta:** [k6](https://k6.io).
- **Reportes:** `qa/reports/<escenario>_summary.json` + `qa/reports/<escenario>/report.html`.

---

## Cómo ejecutar

Requisitos: **k6** en el `PATH`, **Node.js** y el backend accesible.

```bash
cd qa
npm install                 # solo la primera vez (k6-html-reporter)

npm run stress              # perfil `load` (50 VUs, rampa 1m/3m/1m)
npm run stress:stress       # perfil `stress` (hasta 300 VUs)
npm run stress:soak         # perfil `soak` (30m)
npm run stress:contention   # contención real (mismo wallet / misma idempotency key)
npm run stress:concurrency  # concurrencia de usuarios a tasa baja fija
npm run stress:saturation   # saturación realista (tasa creciente + mix de escritura)
npm run stress:verify-planning  # verifica el recálculo async de planning y el balance
```

### Backend local (Docker + nginx)

En el entorno local el backend corre en Docker detrás de **nginx** (`nginx_proxy`
publica `:80`, `:443`, `:8081`). nginx aplica rate limiting en `/api/v1/auth/`
(`rate=10r/m`), así que aprovisionar un pool grande por `:80` devuelve **429**.

Para QA, `docker-compose.yml` expone el backend **directo al host** en
`127.0.0.1:8082` (saltando nginx):

```bash
npm run stress:saturation -- http://127.0.0.1:8082
```

El mapeo está bindeado a loopback (no expone el backend a la red) y solo se usa
para pruebas locales.

### Apuntar a otro entorno (URL como argumento)

```bash
npm run stress:saturation -- http://127.0.0.1:8082
npm run stress:concurrency -- https://staging.example.com
npm run stress -- 127.0.0.1
npm run stress:contention -- --base-url https://staging.example.com
```

La URL se normaliza (`stress/lib/base-url.js`):

- Sin esquema (`127.0.0.1`) → se asume `http://`.
- Host local (`localhost`, `127.0.0.1`, `0.0.0.0`, `::1`) **sin puerto** → se
  agrega `:8080`.
- Otros hosts (ngrok, staging, ...) se respetan tal cual, con o sin puerto.
- La variable de entorno `BASE_URL` sigue funcionando como alternativa.

---

## Escenarios

### `stress/main.js` — perfiles clásicos

Selecciona el perfil con el nombre del escenario. Cada VU aprovisiona **su propio
usuario** (aislado), por lo que mide **escalabilidad horizontal**, no contención.

| Script | VUs | Modelo |
|---|---|---|
| `load` | 50 | rampa 1m → 3m → 1m |
| `stress` | hasta 300 | rampa por escalones |
| `breakpoint` | maxVUs 400 | ramping-arrival-rate hasta 250 req/s |
| `spike` | 300 | pico súbito |
| `soak` | 80 | 30 min constante |

Escritura configurable con `WRITE_RATIO` (default `0.4`).

### `stress/contention.js` — contención sobre el mismo recurso

Todos los VUs comparten **un mismo usuario, billetera y categoría**. Ejercita los
puntos donde la concurrencia sí puede romper:

- `same_wallet`: N VUs hacen `POST /transactions` contra la misma billetera →
  `updateBalance` + `@Version` (optimistic lock).
- `same_idempotency_key`: N VUs envían la **misma** `Idempotency-Key` →
  carrera de `DataIntegrityViolationException`.

Métricas: `contention_conflicts`, `idempotency_5xx`, `balance_drift`.

### `stress/concurrency-low-rate.js` — usuarios concurrentes a tasa baja fija

**Pregunta:** "con una carga total baja (~5 req/s), ¿cuántos usuarios concurrentes
aguanta la app antes de degradarse?"

- Tasa total **fija** (`constant-arrival-rate`): no crece con los VUs.
- Escalones de VUs preasignados (`STEPS="10,50,100,200"`), 1 min cada uno.
- El pool de usuarios se aprovisiona **una sola vez** en `setup()`; el ciclo no
  hace register/login, solo actividad real. El aprovisionamiento es costoso (N
  usuarios completos), por eso `setupTimeout` sube a `10m` (`SETUP_TIMEOUT`).
- **Foco en `transactions`:** cada ciclo golpea casi exclusivamente el módulo de
  transacciones — `GET /transactions` (listado) + `GET /transactions/{id}`
  (lectura puntual) en paralelo, y `POST /transactions` con probabilidad
  `WRITE_RATIO`. `GET /wallets` solo entra como ruido de fondo con probabilidad
  `TAG_RATIO` (default `0.05`). El resumen imprime el desglose de reqs/p95 por
  endpoint para verificar el foco.

**Cómo leer el resultado:**

| Métrica | Significado |
|---|---|
| `vus_max` | usuarios concurrentes reales en el pico |
| `dropped_iterations` | si > 0, k6 no pudo sostener la tasa → techo alcanzado |
| `cycle_duration p95` | degradación por usuario |

Parámetros: `REQ_PER_S` (default 5), `STEPS`, `STEP_DURATION`, `USER_POOL_SIZE`,
`WRITE_RATIO`, `TAG_RATIO`.

**Aprovisionamiento robusto:** el `setup()` crea el pool con reintentos y
backoff (`PROVISION_RETRIES`, `PROVISION_BACKOFF_MS`) para absorber 429/5xx
transitorios. Contra el backend directo (p. ej. `http://127.0.0.1:8082`) no hace
falta pausa (`PROVISION_DELAY_MS=0`); si aprovisionas a través de un proxy con
rate limit (nginx, ngrok free, WAF), sube `PROVISION_DELAY_MS` (p. ej. `1000`) o
baja `USER_POOL_SIZE`.

### `stress/saturation.js` — saturación realista

**Pregunta:** "con MUCHOS usuarios concurrentes y un ritmo creciente de uso real,
¿en qué punto (req/s) el sistema se degrada?"

Diferencia con `concurrency-low-rate.js`: aquel fija una tasa baja constante y
escala VUs; este **sube la tasa progresivamente** (`ramping-arrival-rate`) con
muchos VUs preasignados, buscando el punto de saturación.

- **Rampa de tasa:** `START_RATE` → `PEAK_RATE` (`START_RATE + RATE_STEP *
  RATE_STEPS`) en escalones de `STEP_DURATION`.
- **Muchos usuarios:** `PRE_VUS` preasignados, `MAX_VUS` máximo. El pool
  (`USER_POOL_SIZE`, default `PRE_VUS`) se aprovisiona una vez en `setup()`.
- **Think-time realista:** `SLEEP` (default `0.5s`) entre ciclos — no es "pocos
  usuarios a todo lo que den", son muchos usuarios a ritmo humano.
- **Mix de carga por pesos** (`W_*`, normalizados por defecto):
  transactions lista/lee/crea/borra (domina), categories lista/crea/actualiza,
  wallets lista (crear con `W_WALLET_CREATE>0`).

**Cómo leer el resultado:**

| Métrica | Significado |
|---|---|
| `dropped_iterations` | si > 0, k6 no pudo emitir la tasa pedida → saturación |
| `vus_max` | usuarios concurrentes realmente activos |
| `p95` por endpoint | dónde se degrada primero |
| `op_failed` / `writes_failed` | fallos de operación bajo carga |

Parámetros: `START_RATE`, `RATE_STEP`, `RATE_STEPS`, `STEP_DURATION`, `SLEEP`,
`PRE_VUS`, `MAX_VUS`, `USER_POOL_SIZE`, `W_*` (pesos), `SETUP_TIMEOUT`.

Ejemplo suave para validar el flujo:

```bash
npm run stress:saturation -- http://127.0.0.1:8082
# override por env:
#   START_RATE=50 RATE_STEP=100 RATE_STEPS=10 PRE_VUS=500 MAX_VUS=1200
```

### `stress/verify-planning.js` — correctitud del recálculo async

**Pregunta:** "el recálculo de presupuestos/objetivos que se movió a `AFTER_COMMIT`
async, ¿produce el mismo resultado que antes y sin duplicar efectos?"

Es un test funcional, no de carga: 1 VU, 1 iteración. Aprovisiona un usuario,
billetera, categoría `SPENT` y un presupuesto pequeño, luego:

1. Crea un gasto que **excede el límite**.
2. Espera `ASYNC_WAIT_S` y verifica que `GET /budgets/{id}` devuelve `health ==
   EXCEEDED` (recalculado en el hilo async **después** del commit).
3. Verifica que existe **exactamente una** notificación de presupuesto y que **no
   se duplica** tras esperar otro intervalo.
4. Verifica que el **balance** de la billetera se descuenta exactamente una vez.
5. Hace `DELETE /transactions/{id}` **dos veces** y comprueba que el segundo
   `disable` es idempotente y que el balance se revierte **exactamente una vez**.

```bash
npm run stress:verify-planning -- http://127.0.0.1:8082
# env: BUDGET_LIMIT=100 SPENT_AMOUNT=150 ASYNC_WAIT_S=4
```

Falla con exit code != 0 si algún check no pasa (`thresholds: checks rate==1`).

---

## Límite conocido del entorno

El backend corre con hilos virtuales (`spring.threads.virtual.enabled=true`). El
pool de HikariCP se fija en `application.yml` con
`spring.datasource.hikari.maximum-pool-size`. Bajo concurrencia, el techo puede
ser el pool de PostgreSQL antes que los hilos. La palanca es esa propiedad
(aumentarla para más paralelismo, o bajarla para liberar conexiones en Postgres).
