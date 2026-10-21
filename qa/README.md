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
```

### Apuntar a otro entorno (URL como argumento)

```bash
npm run stress:concurrency -- https://a249-132-191-2-250.ngrok-free.app
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
- Escalones de VUs preasignados (`STEPS="10,50,100,200,400"`), 1 min cada uno.
- El pool de usuarios se aprovisiona **una sola vez** en `setup()`; el ciclo no
  hace register/login, solo actividad real.

**Cómo leer el resultado:**

| Métrica | Significado |
|---|---|
| `vus_max` | usuarios concurrentes reales en el pico |
| `dropped_iterations` | si > 0, k6 no pudo sostener la tasa → techo alcanzado |
| `cycle_duration p95` | degradación por usuario |

Parámetros: `REQ_PER_S` (default 5), `STEPS`, `STEP_DURATION`, `USER_POOL_SIZE`,
`WRITE_RATIO`.

---

## Límite conocido del entorno

El backend corre con hilos virtuales (`spring.threads.virtual.enabled=true`) pero
**HikariCP usa el default de 10 conexiones** (no hay `maximum-pool-size` en
`application.yml`). El cuello de botella bajo concurrencia será el pool de
PostgreSQL, no los hilos. La palanca es
`spring.datasource.hikari.maximum-pool-size`.
