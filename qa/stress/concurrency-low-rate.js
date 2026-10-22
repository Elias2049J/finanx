import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter, Gauge } from 'k6/metrics';
import { resolveBaseUrl } from './lib/base-url.js';

// ============================================================================
// Escenario de CONCURRENCIA DE USUARIOS A TASA BAJA FIJA.
//
// Pregunta que responde:
//   "Con una carga total baja (~5 req/s en el sistema), cuantos usuarios
//    concurrentes (VUs activos) aguanta la app antes de degradarse?"
//
// Modelo:
//   - constant-arrival-rate: la TASA TOTAL de peticiones esta fijada en REQ_PER_S
//     (default 5 req/s). No crece con los VUs.
//   - preAllocatedVUs sube en escalones (STEPS): cuantos mas VUs puede k6
//     sostener sin caer por debajo de la tasa objetivo, mas usuarios
//     concurrentes aguanta el sistema A ESA CARGA.
//   - Cada iteracion = 1 usuario autenticado haciendo un ciclo de actividad
//     CENTRADO EN TRANSACTIONS: lista + lectura puntual + escritura. k6 reutiliza
//     VUs para cumplir la tasa; el pico real de VUs activos es `vus_max`.
//
// FOCO EN TRANSACTIONS:
//   El ciclo golpea casi exclusivamente el modulo de transacciones para medir su
//   comportamiento bajo concurrencia (el resto de endpoints solo aparece como
//   ruido de fondo marginal). Reparto por ciclo:
//     - 1x GET  /transactions        (listado paginado)
//     - 1x GET  /transactions/{id}   (lectura puntual de una tx existente)
//     - 1x POST /transactions        (escritura, con probabilidad WRITE_RATIO)
//     - 1x GET  /wallets             (ruido de fondo ocasional, TAG_RATIO)
//   Los READS de transactions son el 100% de las lecturas; wallets solo entra
//   con probabilidad TAG_RATIO (default 0.05).
//
// REALISMO (importante):
//   Los usuarios se aprovisionan UNA SOLA VEZ en setup() (register + prefs +
//   wallet + category) y se les pre-genera el token. El ciclo NO hace register
//   ni login: solo actividad normal de un usuario ya autenticado. Asi la tasa
//   de 5 req/s mide uso real y no altas de usuario.
//
// Lectura del resultado:
//   - `dropped_iterations` > 0  => k6 no pudo sostener 5 req/s con los VUs dados
//                                  => techo de concurrencia alcanzado.
//   - p95 creciente             => el sistema empieza a degradarse.
//   - vus_max                   => usuarios realmente concurrentes en el pico.
//
// LIMITE CONOCIDO DEL ENTORNO:
//   El backend corre con hilos virtuales (spring.threads.virtual.enabled=true)
//   y el pool de HikariCP se fija en application.yml con
//   spring.datasource.hikari.maximum-pool-size. El techo puede ser el pool de
//   Postgres antes que los hilos. Anotarlo al interpretar vus_max.
// ============================================================================

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);
const REQ_PER_S = parseInt(__ENV.REQ_PER_S || __ENV.RATE || '5', 10); // req/s TOTAL objetivo
const STEP_DURATION = __ENV.STEP_DURATION || __ENV.DURATION || '60s';
const WRITE_RATIO = parseFloat(__ENV.WRITE_RATIO || '0.2'); // fraccion de ciclos con escritura
const TAG_RATIO = parseFloat(__ENV.TAG_RATIO || '0.05'); // fraccion de ciclos con ruido (wallets)

// Cada ciclo emite 2 lecturas de transactions + 1 escritura de transaction con
// probabilidad WRITE_RATIO + 1 lectura de wallets con probabilidad TAG_RATIO.
// Para fijar una tasa de REQUESTS/segundo, convertimos a iteraciones/segundo.
const REQS_PER_CYCLE = 2 + WRITE_RATIO + TAG_RATIO;
const CYCLE_RATE = Math.max(1, Math.round(REQ_PER_S / REQS_PER_CYCLE));

// Escalones de VUs preasignados. Se puede sobreescribir con STEPS="10,50,100".
const STEPS = (__ENV.STEPS || '10,50,100,200')
  .split(',')
  .map((s) => parseInt(s.trim(), 10))
  .filter((n) => !Number.isNaN(n));

const MAX_VUS = parseInt(__ENV.MAX_VUS || String(STEPS[STEPS.length - 1] * 2), 10);

// Tamano del pool de usuarios ya aprovisionados. Debe cubrir el pico de VUs,
// porque cada usuario del pool representa UNA sesion concurrente distinta.
const USER_POOL_SIZE = parseInt(__ENV.USER_POOL_SIZE || String(Math.max(...STEPS)), 10);

// setup() aprovisiona TODO el pool antes de medir; por defecto k6 lo corta a 60s
// y con un pool grande eso no alcanza. Se sube (configurable) porque el
// aprovisionamiento es intencionalmente costoso (N usuarios completos).
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '10m';

const usersInPool = new Gauge('users_in_pool');
const cycleDuration = new Trend('cycle_duration', true);
const readFailed = new Rate('read_failed');
const writeFailed = new Rate('write_failed');
const writeOk = new Counter('writes_ok');

// Endpoints que se taggean en el ciclo. Declararlos como thresholds fuerza a k6
// a materializar las sub-metricas por tag en el summary JSON (si no, no se
// exportan y el reporte muestra "Sin datos de endpoints").
const ENDPOINTS = [
  'GET /transactions',
  'GET /transactions/{id}',
  'POST /transactions',
  'GET /wallets',
];
const endpointThresholds = {};
ENDPOINTS.forEach((name) => {
  endpointThresholds[`http_req_duration{endpoint:${name}}`] = ['p(95)<800'];
  endpointThresholds[`http_req_failed{endpoint:${name}}`] = ['rate<0.01'];
  endpointThresholds[`http_reqs{endpoint:${name}}`] = ['count>0'];
});

export const options = {
  scenarios: buildScenarios(),
  setupTimeout: SETUP_TIMEOUT,
  teardownTimeout: __ENV.TEARDOWN_TIMEOUT || '2m',
  thresholds: {
    // La tasa real debe mantenerse: si se cae, ese escalon no sostiene la
    // concurrencia pedida a esta tasa.
    dropped_iterations: ['count<1'],
    read_failed: ['rate<0.01'],
    write_failed: ['rate<0.01'],
    ...endpointThresholds,
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function buildScenarios() {
  const out = {};
  STEPS.forEach((vus, i) => {
    out[`step_${vus}vus`] = {
      executor: 'constant-arrival-rate',
      rate: CYCLE_RATE,
      timeUnit: '1s',
      duration: STEP_DURATION,
      preAllocatedVUs: vus,
      maxVUs: Math.max(MAX_VUS, vus),
      startTime: `${i * durationToSeconds(STEP_DURATION)}s`,
      exec: 'userCycle',
      tags: { step: String(vus) },
    };
  });
  return out;
}

function durationToSeconds(d) {
  const m = String(d).match(/^(\d+)(s|m|h)$/);
  if (!m) return 60;
  const n = parseInt(m[1], 10);
  return m[2] === 'm' ? n * 60 : m[2] === 'h' ? n * 3600 : n;
}

function jsonHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

// --- Reintento con backoff para el aprovisionamiento ------------------------
// El aprovisionamiento NO forma parte de la medicion de concurrencia, asi que
// puede permitirse esperar. Reintenta ante 429 (rate limit de un proxy/ngrok) y
// 5xx transitorios, con backoff exponencial. En 4xx "de negocio" (400/409) no
// reintenta porque no se arregla insistiendo.
const PROVISION_RETRIES = parseInt(__ENV.PROVISION_RETRIES || '6', 10);
const PROVISION_BACKOFF_MS = parseInt(__ENV.PROVISION_BACKOFF_MS || '1000', 10);
// Pausa entre aprovisionamientos de usuarios distintos (ms). Contra localhost no
// hace falta (0); subirlo solo si aprovisionas a traves de un proxy con rate
// limit (ngrok free, WAF, ...).
const PROVISION_DELAY_MS = parseInt(__ENV.PROVISION_DELAY_MS || '0', 10);

function withRetry(label, fn) {
  let last;
  for (let attempt = 0; attempt <= PROVISION_RETRIES; attempt++) {
    last = fn();
    const retriable = last.status === 429 || last.status >= 500;
    if (!retriable) return last;
    if (attempt < PROVISION_RETRIES) {
      const wait = PROVISION_BACKOFF_MS * Math.pow(2, attempt);
      console.warn(
        `${label}: status=${last.status} (intento ${attempt + 1}/${PROVISION_RETRIES + 1}), ` +
          `reintento en ${wait} ms`
      );
      sleep(wait / 1000);
    }
  }
  return last;
}

// --- Aprovisiona UN usuario completo y devuelve su sesion --------------------
// Contrato real del backend:
//   POST /auth/register        {name, lastname, email, password} -> {accessToken}
//   GET  /users/me                                              -> {id}
//   POST /users/me/preferences {timeZone, currencyCode, emailNotifications}
//   POST /wallets              {currencyCode, primary}          -> {id}
//   POST /categories           {name, description, userId, type}-> {id}
function provisionUser(index) {
  const rnd = `${Math.random().toString(36).slice(2, 10)}${index}`;
  const email = `qa_cc_${rnd}@perf.local`;

  const register = withRetry('register', () =>
    http.post(
      `${BASE_URL}/auth/register`,
      JSON.stringify({
        name: 'QA',
        lastname: 'Concurrency',
        email,
        password: 'Password123!',
      }),
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
      JSON.stringify({ name: `QA CC ${rnd}`, description: 'concurrency', userId, type: 'SPENT' }),
      { headers: jsonHeaders(token) }
    )
  );

  const walletId = wallet.status === 201 ? wallet.json('id') : null;
  const categoryId = category.status === 201 ? category.json('id') : null;

  // Sembramos UNA transaccion para que el ciclo pueda hacer un GET puntual
  // (GET /transactions/{id}) ademas del listado paginado.
  let txId = null;
  if (walletId && categoryId) {
    const tx = withRetry('transactions(seed)', () =>
      http.post(
        `${BASE_URL}/transactions`,
        JSON.stringify({
          walletId,
          categoryId,
          description: `cc seed ${rnd}`,
          amount: 10.0,
          paymentMethod: 'CASH',
          type: 'SPENT',
        }),
        { headers: jsonHeaders(token) }
      )
    );
    if (tx.status === 201) txId = tx.json('id');
  }

  return { token, userId, walletId, categoryId, txId };
}

// --- setup(): aprovisiona el pool UNA vez, fuera de la medicion --------------
export function setup() {
  const users = [];
  for (let i = 0; i < USER_POOL_SIZE; i++) {
    if (i > 0 && PROVISION_DELAY_MS > 0) {
      sleep(PROVISION_DELAY_MS / 1000);
    }
    users.push(provisionUser(i));
    if ((i + 1) % 10 === 0 || i + 1 === USER_POOL_SIZE) {
      console.log(`Aprovisionados ${i + 1}/${USER_POOL_SIZE} usuarios...`);
    }
  }
  usersInPool.add(users.length);
  console.log(
    `Pool listo: ${users.length} usuarios aprovisionados. ` +
      `Escalones=${STEPS.join(',')} vus, objetivo=${REQ_PER_S} req/s (~${CYCLE_RATE} ciclos/s), ` +
      `duracion/escalon=${STEP_DURATION}.`
  );
  return { users };
}

// --- Un ciclo CENTRADO EN TRANSACTIONS (sin register/login) ------------------
export function userCycle(data) {
  const users = data.users;
  // Cada VU usa de forma estable UN usuario del pool (sesion ya autenticada).
  // Al ser constante por VU, no hay register/login dentro de la medicion.
  const session = users[(__VU - 1) % users.length];

  group('cycle', () => {
    const t0 = Date.now();

    // 1) Lecturas de transactions en paralelo: listado + lectura puntual.
    //    Ambas van contra /transactions => el modulo de transactions es el que
    //    recibe toda la carga de lectura.
    const txReads = [
      ['GET', `${BASE_URL}/transactions?page=0&size=20`, null, {
        headers: jsonHeaders(session.token),
        tags: { endpoint: 'GET /transactions' },
      }],
    ];
    if (session.txId) {
      txReads.push([
        'GET',
        `${BASE_URL}/transactions/${session.txId}`,
        null,
        {
          headers: jsonHeaders(session.token),
          tags: { endpoint: 'GET /transactions/{id}' },
        },
      ]);
    }

    // 2) Ruido de fondo: wallets solo en una minoria de ciclos (TAG_RATIO).
    const reqs = txReads;
    if (Math.random() < TAG_RATIO) {
      reqs.push([
        'GET',
        `${BASE_URL}/wallets?page=0&size=20`,
        null,
        {
          headers: jsonHeaders(session.token),
          tags: { endpoint: 'GET /wallets' },
        },
      ]);
    }

    const reads = http.batch(reqs);
    readFailed.add(!reads.every((r) => r.status === 200));

    // 3) Escritura de transaction (POST /transactions) con probabilidad WRITE_RATIO.
    if (Math.random() < WRITE_RATIO && session.walletId && session.categoryId) {
      const res = http.post(
        `${BASE_URL}/transactions`,
        JSON.stringify({
          walletId: session.walletId,
          categoryId: session.categoryId,
          description: 'cc write',
          amount: 1.0,
          paymentMethod: 'CASH',
          type: 'SPENT',
        }),
        {
          headers: jsonHeaders(session.token),
          tags: { endpoint: 'POST /transactions' },
        }
      );
      writeFailed.add(res.status !== 201);
      if (res.status === 201) writeOk.add(1);
    }

    cycleDuration.add(Date.now() - t0);
  });
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/concurrency_summary.json';
  const m = data.metrics;
  const pick = (key, field) => (m[key] && m[key].values[field] !== undefined ? m[key].values[field] : 'n/a');

  return {
    [jsonPath]: JSON.stringify(data),
    stdout: `\n=== Resumen [concurrency @ ${REQ_PER_S} req/s total (~${CYCLE_RATE} ciclos/s)] ===
  usuarios en pool:                ${pick('users_in_pool', 'value')}
  usuarios concurrentes (vus_max): ${pick('vus_max', 'max')}
  tasa real de ciclos (iter/s):    ${pick('iterations', 'rate')}
  iteraciones descartadas:         ${pick('dropped_iterations', 'count')}   (0 = k6 sostuvo la tasa)
  p95 ciclo:                       ${pick('cycle_duration', 'p(95)')} ms
  p95 http:                        ${pick('http_req_duration', 'p(95)')} ms
  read_failed:                     ${pick('read_failed', 'rate')}
  write_failed:                    ${pick('write_failed', 'rate')}
  http_req_failed:                 ${pick('http_req_failed', 'rate')}

  -- Desglose por endpoint (foco: transactions) --
  reqs {endpoint:GET /transactions}:       ${pick('http_reqs{endpoint:GET /transactions}', 'count')}
  reqs {endpoint:GET /transactions/{id}}:  ${pick('http_reqs{endpoint:GET /transactions/{id}}', 'count')}
  reqs {endpoint:POST /transactions}:      ${pick('http_reqs{endpoint:POST /transactions}', 'count')}
  reqs {endpoint:GET /wallets}:            ${pick('http_reqs{endpoint:GET /wallets}', 'count')}
  p95 {endpoint:GET /transactions}:        ${pick('http_req_duration{endpoint:GET /transactions}', 'p(95)')} ms
  p95 {endpoint:POST /transactions}:       ${pick('http_req_duration{endpoint:POST /transactions}', 'p(95)')} ms
=================================================================\n`,
  };
}
