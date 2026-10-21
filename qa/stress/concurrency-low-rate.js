import http from 'k6/http';
import { check, group } from 'k6';
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
//   - Cada iteracion = 1 usuario autenticado haciendo un ciclo funcional
//     (leer tablero + escritura ocasional). k6 reutiliza VUs para cumplir la
//     tasa; el pico real de VUs activos concurrentes es `vus_max`.
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
//   pero HikariCP usa el default de 10 conexiones (no hay maximum-pool-size en
//   application.yml). El cuello de botella sera el pool de Postgres, no los
//   hilos. Anotarlo al interpretar vus_max.
// ============================================================================

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);
const REQ_PER_S = parseInt(__ENV.REQ_PER_S || __ENV.RATE || '5', 10); // req/s TOTAL objetivo
const STEP_DURATION = __ENV.STEP_DURATION || __ENV.DURATION || '60s';
const WRITE_RATIO = parseFloat(__ENV.WRITE_RATIO || '0.2'); // fraccion de escrituras

// Cada ciclo emite 2 lecturas (batch) + 1 escritura con probabilidad WRITE_RATIO.
// Para fijar una tasa de REQUESTS/segundo, convertimos a iteraciones/segundo.
const REQS_PER_CYCLE = 2 + WRITE_RATIO;
const CYCLE_RATE = Math.max(1, Math.round(REQ_PER_S / REQS_PER_CYCLE));

// Escalones de VUs preasignados. Se puede sobreescribir con STEPS="10,50,100".
const STEPS = (__ENV.STEPS || '10,50,100,200,400')
  .split(',')
  .map((s) => parseInt(s.trim(), 10))
  .filter((n) => !Number.isNaN(n));

const MAX_VUS = parseInt(__ENV.MAX_VUS || String(STEPS[STEPS.length - 1] * 2), 10);

// Tamano del pool de usuarios ya aprovisionados. Debe cubrir el pico de VUs,
// porque cada usuario del pool representa UNA sesion concurrente distinta.
const USER_POOL_SIZE = parseInt(__ENV.USER_POOL_SIZE || String(Math.max(...STEPS)), 10);

const usersInPool = new Gauge('users_in_pool');
const cycleDuration = new Trend('cycle_duration', true);
const readFailed = new Rate('read_failed');
const writeFailed = new Rate('write_failed');
const writeOk = new Counter('writes_ok');

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    // La tasa real debe mantenerse: si se cae, ese escalon no sostiene la
    // concurrencia pedida a esta tasa.
    dropped_iterations: ['count<1'],
    read_failed: ['rate<0.01'],
    write_failed: ['rate<0.01'],
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

  const register = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({
      name: 'QA',
      lastname: 'Concurrency',
      email,
      password: 'Password123!',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (register.status !== 200 && register.status !== 201) {
    throw new Error(`register fallo: ${register.status} ${register.body}`);
  }
  const token = register.json('accessToken');

  const me = http.get(`${BASE_URL}/users/me`, { headers: jsonHeaders(token) });
  if (me.status !== 200) {
    throw new Error(`users/me fallo: ${me.status} ${me.body}`);
  }
  const userId = me.json('id');

  http.post(
    `${BASE_URL}/users/me/preferences`,
    JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode: 'PEN', emailNotifications: false }),
    { headers: jsonHeaders(token) }
  );

  const wallet = http.post(
    `${BASE_URL}/wallets`,
    JSON.stringify({ currencyCode: 'PEN', primary: true }),
    { headers: jsonHeaders(token) }
  );

  const category = http.post(
    `${BASE_URL}/categories`,
    JSON.stringify({ name: `QA CC ${rnd}`, description: 'concurrency', userId, type: 'SPENT' }),
    { headers: jsonHeaders(token) }
  );

  return {
    token,
    userId,
    walletId: wallet.status === 201 ? wallet.json('id') : null,
    categoryId: category.status === 201 ? category.json('id') : null,
  };
}

// --- setup(): aprovisiona el pool UNA vez, fuera de la medicion --------------
export function setup() {
  const users = [];
  for (let i = 0; i < USER_POOL_SIZE; i++) {
    users.push(provisionUser(i));
  }
  usersInPool.add(users.length);
  console.log(
    `Pool listo: ${users.length} usuarios aprovisionados. ` +
      `Escalones=${STEPS.join(',')} vus, objetivo=${REQ_PER_S} req/s (~${CYCLE_RATE} ciclos/s), ` +
      `duracion/escalon=${STEP_DURATION}.`
  );
  return { users };
}

// --- Un ciclo de usuario autenticado (sin register/login) --------------------
export function userCycle(data) {
  const users = data.users;
  // Cada VU usa de forma estable UN usuario del pool (sesion ya autenticada).
  // Al ser constante por VU, no hay register/login dentro de la medicion.
  const session = users[(__VU - 1) % users.length];

  group('cycle', () => {
    const t0 = Date.now();

    const reads = http.batch([
      ['GET', `${BASE_URL}/transactions?page=0&size=20`, null, {
        headers: jsonHeaders(session.token),
        tags: { endpoint: 'GET /transactions' },
      }],
      ['GET', `${BASE_URL}/wallets?page=0&size=20`, null, {
        headers: jsonHeaders(session.token),
        tags: { endpoint: 'GET /wallets' },
      }],
    ]);
    readFailed.add(!reads.every((r) => r.status === 200));

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
=================================================================\n`,
  };
}
