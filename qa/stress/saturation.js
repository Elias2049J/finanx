import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter, Gauge } from 'k6/metrics';
import { resolveBaseUrl } from './lib/base-url.js';

// ============================================================================
// Escenario de SATURACION REALISTA.
//
// Pregunta que responde:
//   "Con MUCHOS usuarios concurrentes y un ritmo creciente de uso real, en que
//    punto (req/s) el sistema empieza a degradarse?"
//
// Diferencia con concurrency-low-rate.js:
//   - Aquel fija una tasa BAJA (~5 req/s) y escala VUs para medir el techo de
//     concurrencia a carga constante.
//   - Este SUBE la tasa progresivamente (ramping-arrival-rate) con muchos VUs
//     preasignados, para encontrar el punto de saturacion real del sistema.
//
// REALISMO (importante):
//   - Pool de usuarios aprovisionado UNA vez en setup() (register + prefs +
//     wallet + categories + seed). El ciclo NO hace register/login.
//   - Cada usuario hace un ciclo con `SLEEP` entre iteraciones, imitando el
//     think-time de una persona. No es "pocos usuarios a todo lo que den":
//     son muchos usuarios a un ritmo humano, con la tasa global creciendo.
//   - La carga se reparte por modulos con pesos explicitos (WEIGHTS): las
//     escrituras de transactions dominan, categories y wallets acompanan.
//
// Lectura del resultado:
//   - `dropped_iterations` > 0  => k6 ya no pudo emitir la tasa pedida con los
//                                  VUs disponibles => saturacion/techo.
//   - p95 creciente por escalon => degradacion progresiva.
//   - `vus_max`                 => usuarios concurrentes realmente activos.
//   - El resumen imprime req/s reales y p95 por endpoint, por escalon.
//
// LIMITE CONOCIDO DEL ENTORNO:
//   El pool de HikariCP se fija en application.yml con
//   spring.datasource.hikari.maximum-pool-size. Bajo saturacion el techo puede
//   ser el pool de Postgres antes que los hilos virtuales. Anotarlo al
//   interpretar el techo.
// ============================================================================

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);

// --- Ritmo: la tasa SUBE por escalones hasta saturar ------------------------
// START_RATE  -> req/s inicial
// RATE_STEP   -> incremento por escalon
// STEP_DURATION -> duracion de cada escalon
// RATE_STEPS  -> cuantos escalones sube la rampa
const START_RATE = parseInt(__ENV.START_RATE || '20', 10);
const RATE_STEP = parseInt(__ENV.RATE_STEP || '30', 10);
const RATE_STEPS = parseInt(__ENV.RATE_STEPS || '8', 10);
const STEP_DURATION = __ENV.STEP_DURATION || '45s';
const PEAK_RATE = START_RATE + RATE_STEP * RATE_STEPS;

// Think-time de cada usuario entre ciclos (s). Realismo: un usuario no dispara
// sin parar; espera un poco entre acciones.
const SLEEP = parseFloat(__ENV.SLEEP || '0.5');

// --- Muchos usuarios: VUs preasignados amplios ------------------------------
// La tasa creciente necesita VUs suficientes para no frenarse por falta de
// workers. El default es generoso a proposito ("muchos usuarios").
const PRE_VUS = parseInt(__ENV.PRE_VUS || '300', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '800', 10);

// Tamano del pool de usuarios ya aprovisionados. Debe cubrir el pico de VUs
// concurrentes: cada usuario del pool representa UNA sesion distinta.
const USER_POOL_SIZE = parseInt(__ENV.USER_POOL_SIZE || String(PRE_VUS), 10);

// setup() aprovisiona TODO el pool antes de medir; el default de k6 (60s) no
// alcanza para pools grandes.
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '15m';

// --- Mezcla de carga por modulo (pesos relativos) ---------------------------
// El reparto se normaliza. Por defecto: transactions domina (lectura+escritura),
// categories acompaña, wallets es minoritario.
const WEIGHTS = {
  txList: parseFloat(__ENV.W_TX_LIST || '30'),
  txGet: parseFloat(__ENV.W_TX_GET || '15'),
  txCreate: parseFloat(__ENV.W_TX_CREATE || '25'),
  txDelete: parseFloat(__ENV.W_TX_DELETE || '5'),
  catList: parseFloat(__ENV.W_CAT_LIST || '10'),
  catCreate: parseFloat(__ENV.W_CAT_CREATE || '6'),
  catUpdate: parseFloat(__ENV.W_CAT_UPDATE || '4'),
  walletList: parseFloat(__ENV.W_WALLET_LIST || '5'),
  // Por defecto 0: crear wallets sin parar no es realista; activalo con
  // W_WALLET_CREATE>0 si quieres castigar la escritura de wallets.
  walletCreate: parseFloat(__ENV.W_WALLET_CREATE || '0'),
};
const WEIGHT_TOTAL = Object.values(WEIGHTS).reduce((a, b) => a + b, 0);

// --- Metricas propias -------------------------------------------------------
const usersInPool = new Gauge('users_in_pool');
const cycleDuration = new Trend('cycle_duration', true);
const opFailed = new Rate('op_failed');
const writesOk = new Counter('writes_ok');
const writesFailed = new Counter('writes_failed');

// Endpoints taggeados: declararlos como thresholds fuerza a k6 a materializar
// las sub-metricas por tag en el summary JSON (si no, el reporte muestra
// "Sin datos de endpoints"). Ver qa/generate-report.js.
const ENDPOINTS = [
  'GET /transactions',
  'GET /transactions/{id}',
  'POST /transactions',
  'DELETE /transactions/{id}',
  'GET /categories',
  'POST /categories',
  'PUT /categories/{id}',
  'GET /wallets',
  'POST /wallets',
];
const endpointThresholds = {};
ENDPOINTS.forEach((name) => {
  endpointThresholds[`http_req_duration{endpoint:${name}}`] = ['p(95)<1500'];
  endpointThresholds[`http_req_failed{endpoint:${name}}`] = ['rate<0.02'];
  // Materializa el conteo por endpoint para la columna `reqs` del reporte.
  // `count>=0` siempre pasa; solo fuerza a k6 a exportar la sub-metrica.
  endpointThresholds[`http_reqs{endpoint:${name}}`] = ['count>=0'];
});

export const options = {
  scenarios: {
    saturation: {
      executor: 'ramping-arrival-rate',
      startRate: START_RATE,
      timeUnit: '1s',
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
      stages: buildStages(),
      gracefulStop: '30s',
      exec: 'userCycle',
    },
  },
  setupTimeout: SETUP_TIMEOUT,
  teardownTimeout: __ENV.TEARDOWN_TIMEOUT || '2m',
  thresholds: {
    // La tasa pedida se debe sostener. Si se cae, ese escalon satura el sistema.
    dropped_iterations: ['count<1'],
    op_failed: ['rate<0.05'],
    ...endpointThresholds,
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Sube la tasa en escalones hasta PEAK_RATE; luego la baja para cerrar.
function buildStages() {
  const stages = [];
  for (let i = 1; i <= RATE_STEPS; i++) {
    stages.push({ duration: STEP_DURATION, target: START_RATE + RATE_STEP * i });
  }
  // Cierre suave tras el pico (10% del tiempo de un escalon).
  stages.push({ duration: '15s', target: Math.max(1, Math.round(PEAK_RATE / 4)) });
  return stages;
}

function jsonHeaders(token, extra) {
  return Object.assign(
    { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    extra || {}
  );
}

// --- Reintento con backoff para el aprovisionamiento ------------------------
const PROVISION_RETRIES = parseInt(__ENV.PROVISION_RETRIES || '6', 10);
const PROVISION_BACKOFF_MS = parseInt(__ENV.PROVISION_BACKOFF_MS || '1000', 10);
const PROVISION_DELAY_MS = parseInt(__ENV.PROVISION_DELAY_MS || '0', 10);

function withRetry(label, fn) {
  let last;
  for (let attempt = 0; attempt <= PROVISION_RETRIES; attempt++) {
    last = fn();
    const retriable = last.status === 429 || last.status >= 500;
    if (!retriable) return last;
    if (attempt < PROVISION_RETRIES) {
      const wait = PROVISION_BACKOFF_MS * Math.pow(2, attempt);
      console.warn(`${label}: status=${last.status}, reintento en ${wait} ms`);
      sleep(wait / 1000);
    }
  }
  return last;
}

// --- Aprovisiona UN usuario completo y devuelve su sesion -------------------
function provisionUser(index) {
  const rnd = `${Math.random().toString(36).slice(2, 10)}${index}`;
  const email = `qa_sat_${rnd}@perf.local`;

  const register = withRetry('register', () =>
    http.post(
      `${BASE_URL}/auth/register`,
      JSON.stringify({ name: 'QA', lastname: 'Saturation', email, password: 'Password123!' }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  );
  if (register.status !== 200 && register.status !== 201) {
    throw new Error(`register fallo: ${register.status} ${register.body}`);
  }
  const token = register.json('accessToken');

  const me = withRetry('users/me', () =>
    http.get(`${BASE_URL}/users/me`, { headers: jsonHeaders(token) })
  );
  if (me.status !== 200) {
    throw new Error(`users/me fallo: ${me.status} ${me.body}`);
  }
  const userId = me.json('id');

  withRetry('preferences', () =>
    http.post(
      `${BASE_URL}/users/me/preferences`,
      JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode: 'PEN', emailNotifications: false }),
      { headers: jsonHeaders(token) }
    )
  );

  const wallet = withRetry('wallets', () =>
    http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'PEN', primary: true }),
      { headers: jsonHeaders(token) }
    )
  );

  const category = withRetry('categories', () =>
    http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ name: `QA Sat ${rnd}`, description: 'saturation', userId, type: 'SPENT' }),
      { headers: jsonHeaders(token) }
    )
  );

  const walletId = wallet.status === 201 ? wallet.json('id') : null;
  const categoryId = category.status === 201 ? category.json('id') : null;

  // Sembramos transactions para poder leer/borrar por id durante la medicion.
  const txIds = [];
  if (walletId && categoryId) {
    for (let s = 0; s < 3; s++) {
      const tx = withRetry('transactions(seed)', () =>
        http.post(
          `${BASE_URL}/transactions`,
          JSON.stringify({
            walletId,
            categoryId,
            description: `sat seed ${rnd}-${s}`,
            amount: 10.0,
            paymentMethod: 'CASH',
            type: 'SPENT',
          }),
          { headers: jsonHeaders(token) }
        )
      );
      if (tx.status === 201) txIds.push(tx.json('id'));
    }
  }

  return { token, userId, walletId, categoryId, txIds };
}

// --- setup(): aprovisiona el pool UNA vez, fuera de la medicion -------------
export function setup() {
  const users = [];
  for (let i = 0; i < USER_POOL_SIZE; i++) {
    if (i > 0 && PROVISION_DELAY_MS > 0) {
      sleep(PROVISION_DELAY_MS / 1000);
    }
    users.push(provisionUser(i));
    if ((i + 1) % 25 === 0 || i + 1 === USER_POOL_SIZE) {
      console.log(`Aprovisionados ${i + 1}/${USER_POOL_SIZE} usuarios...`);
    }
  }
  usersInPool.add(users.length);
  console.log(
    `Pool listo: ${users.length} usuarios. Rampa ${START_RATE}->${PEAK_RATE} req/s ` +
      `en ${RATE_STEPS} escalones de ${STEP_DURATION}. VUs pre=${PRE_VUS} max=${MAX_VUS} sleep=${SLEEP}s.`
  );
  return { users };
}

// --- Elige una operacion segun los pesos ------------------------------------
function pickOperation() {
  let r = Math.random() * WEIGHT_TOTAL;
  for (const [op, w] of Object.entries(WEIGHTS)) {
    if (w <= 0) continue;
    r -= w;
    if (r <= 0) return op;
  }
  return 'txList';
}

// --- Un ciclo de usuario autenticado (sin register/login) --------------------
export function userCycle(data) {
  const users = data.users;
  // Cada VU usa de forma estable UN usuario del pool (sesion ya autenticada).
  const session = users[(__VU - 1) % users.length];
  const op = pickOperation();

  group('cycle', () => {
    const t0 = Date.now();
    const ok = runOperation(op, session);
    opFailed.add(!ok);
    cycleDuration.add(Date.now() - t0);
  });

  if (SLEEP > 0) {
    sleep(SLEEP);
  }
}

function runOperation(op, s) {
  switch (op) {
    case 'txList': {
      const r = http.get(`${BASE_URL}/transactions?page=0&size=20`, {
        headers: jsonHeaders(s.token),
        tags: { endpoint: 'GET /transactions' },
      });
      return r.status === 200;
    }
    case 'txGet': {
      const id = pickTxId(s);
      if (id == null) return true; // nada que leer; no cuenta como fallo
      const r = http.get(`${BASE_URL}/transactions/${id}`, {
        headers: jsonHeaders(s.token),
        tags: { endpoint: 'GET /transactions/{id}' },
      });
      return r.status === 200 || r.status === 404;
    }
    case 'txCreate': {
      if (!s.walletId || !s.categoryId) return true;
      const r = http.post(
        `${BASE_URL}/transactions`,
        JSON.stringify({
          walletId: s.walletId,
          categoryId: s.categoryId,
          description: `sat tx ${Math.random().toString(36).slice(2, 8)}`,
          amount: Math.round((Math.random() * 50 + 1) * 100) / 100,
          paymentMethod: 'CASH',
          type: 'SPENT',
        }),
        { headers: jsonHeaders(s.token), tags: { endpoint: 'POST /transactions' } }
      );
      if (r.status === 201) writesOk.add(1);
      else writesFailed.add(1);
      return r.status === 201;
    }
    case 'txDelete': {
      // Borra una tx recien creada (si la hay) para no vaciar el pool sembrado.
      if (!s.walletId || !s.categoryId) return true;
      const created = http.post(
        `${BASE_URL}/transactions`,
        JSON.stringify({
          walletId: s.walletId,
          categoryId: s.categoryId,
          description: 'sat tx to delete',
          amount: 1.0,
          paymentMethod: 'CASH',
          type: 'SPENT',
        }),
        { headers: jsonHeaders(s.token), tags: { endpoint: 'POST /transactions' } }
      );
      if (created.status !== 201) return false;
      const r = http.del(`${BASE_URL}/transactions/${created.json('id')}`, null, {
        headers: jsonHeaders(s.token),
        tags: { endpoint: 'DELETE /transactions/{id}' },
      });
      writesOk.add(1);
      return r.status === 204;
    }
    case 'catList': {
      const r = http.get(`${BASE_URL}/categories?page=0&size=50`, {
        headers: jsonHeaders(s.token),
        tags: { endpoint: 'GET /categories' },
      });
      return r.status === 200;
    }
    case 'catCreate': {
      const r = http.post(
        `${BASE_URL}/categories`,
        JSON.stringify({
          name: `QA Sat Cat ${Math.random().toString(36).slice(2, 8)}`,
          description: 'saturation',
          userId: s.userId,
          type: 'SPENT',
        }),
        { headers: jsonHeaders(s.token), tags: { endpoint: 'POST /categories' } }
      );
      if (r.status === 201) writesOk.add(1);
      else writesFailed.add(1);
      return r.status === 201;
    }
    case 'catUpdate': {
      if (!s.categoryId) return true;
      const r = http.put(
        `${BASE_URL}/categories/${s.categoryId}`,
        JSON.stringify({ name: `QA Sat Upd ${Math.random().toString(36).slice(2, 8)}`, description: 'saturation upd', userId: s.userId, type: 'SPENT' }),
        { headers: jsonHeaders(s.token), tags: { endpoint: 'PUT /categories/{id}' } }
      );
      writesOk.add(1);
      return r.status === 200;
    }
    case 'walletList': {
      const r = http.get(`${BASE_URL}/wallets?page=0&size=20`, {
        headers: jsonHeaders(s.token),
        tags: { endpoint: 'GET /wallets' },
      });
      return r.status === 200;
    }
    case 'walletCreate': {
      const r = http.post(
        `${BASE_URL}/wallets`,
        JSON.stringify({ currencyCode: 'USD', primary: false }),
        { headers: jsonHeaders(s.token), tags: { endpoint: 'POST /wallets' } }
      );
      writesOk.add(1);
      return r.status === 201;
    }
    default:
      return true;
  }
}

function pickTxId(s) {
  if (!s.txIds || s.txIds.length === 0) return null;
  return s.txIds[Math.floor(Math.random() * s.txIds.length)];
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/saturation_summary.json';
  const m = data.metrics;
  const pick = (key, field) => (m[key] && m[key].values[field] !== undefined ? m[key].values[field] : 'n/a');

  return {
    [jsonPath]: JSON.stringify(data),
    stdout: `\n=== Resumen [saturation ${START_RATE}->${PEAK_RATE} req/s] ===
  usuarios en pool:                ${pick('users_in_pool', 'value')}
  usuarios concurrentes (vus_max): ${pick('vus_max', 'max')}
  req/s reales:                    ${pick('http_reqs', 'rate')}
  iteraciones:                     ${pick('iterations', 'count')}
  iteraciones descartadas:         ${pick('dropped_iterations', 'count')}   (0 = sostuvo la rampa)
  p95 ciclo:                       ${pick('cycle_duration', 'p(95)')} ms
  p95 http:                        ${pick('http_req_duration', 'p(95)')} ms
  op_failed:                       ${pick('op_failed', 'rate')}
  writes_ok:                       ${pick('writes_ok', 'count')}
  writes_failed:                   ${pick('writes_failed', 'count')}
  http_req_failed:                 ${pick('http_req_failed', 'rate')}

  -- p95 por endpoint (foco: transactions + categories + wallets) --
  p95 {GET /transactions}:         ${pick('http_req_duration{endpoint:GET /transactions}', 'p(95)')} ms
  p95 {GET /transactions/{id}}:    ${pick('http_req_duration{endpoint:GET /transactions/{id}}', 'p(95)')} ms
  p95 {POST /transactions}:        ${pick('http_req_duration{endpoint:POST /transactions}', 'p(95)')} ms
  p95 {DELETE /transactions/{id}}: ${pick('http_req_duration{endpoint:DELETE /transactions/{id}}', 'p(95)')} ms
  p95 {GET /categories}:           ${pick('http_req_duration{endpoint:GET /categories}', 'p(95)')} ms
  p95 {POST /categories}:          ${pick('http_req_duration{endpoint:POST /categories}', 'p(95)')} ms
  p95 {PUT /categories/{id}}:      ${pick('http_req_duration{endpoint:PUT /categories/{id}}', 'p(95)')} ms
  p95 {GET /wallets}:              ${pick('http_req_duration{endpoint:GET /wallets}', 'p(95)')} ms
=================================================================\n`,
  };
}
