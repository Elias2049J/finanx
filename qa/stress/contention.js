import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { resolveBaseUrl } from './lib/base-url.js';
import { sanitizeSummary } from '../lib/sanitize-summary.js';

// ============================================================================
// Escenario de CONTENCION REAL.
//
// A diferencia de stress/main.js (donde cada VU es un usuario aislado), aqui
// TODOS los VUs comparten un mismo usuario, billetera y categoria. El objetivo
// es ejercitar los puntos donde la concurrencia SI puede romper:
//
//   1) grupo `same_wallet`: N VUs hacen POST /transactions contra LA MISMA
//      billetera en paralelo -> updateBalance + @Version (optimistic lock).
//   2) grupo `same_idempotency_key`: N VUs envian la MISMA Idempotency-Key
//      -> carrera de DataIntegrityViolationException en la restriccion unica.
//
// Metricas propias:
//   - contention_conflicts : respuestas 409/500 que son fallos de lock (safety OK
//                            porque @Version aborta, pero NO hay retry -> 5xx).
//   - idempotency_5xx      : 5xx en el grupo de idempotencia (race mal manejada).
//   - balance_drift        : |saldo_esperado - saldo_real| al final (lost updates).
// ============================================================================

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);
const VUS = parseInt(__ENV.VUS || '50', 10);
const WRITES_PER_VU = parseInt(__ENV.WRITES_PER_VU || '20', 10);
const IDEM_VUS = parseInt(__ENV.IDEM_VUS || '50', 10);
const AMOUNT_PER_TX = parseFloat(__ENV.AMOUNT_PER_TX || '1.00');

const contentionConflicts = new Counter('contention_conflicts');
const idempotency5xx = new Counter('idempotency_5xx');
const idempotencyReplays = new Counter('idempotency_replays');
const balanceDrift = new Trend('balance_drift');
const txCreateDuration = new Trend('tx_create_duration', true);

export const options = {
  scenarios: {
    same_wallet: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: WRITES_PER_VU,
      maxDuration: __ENV.MAX_DURATION || '3m',
      exec: 'sameWalletFlow',
      tags: { flow: 'same_wallet' },
    },
    same_idempotency_key: {
      executor: 'per-vu-iterations',
      vus: IDEM_VUS,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '3m',
      startTime: '0s',
      exec: 'sameIdempotencyKeyFlow',
      tags: { flow: 'same_idempotency_key' },
    },
  },
  thresholds: {
    // Safety: la billetera NO debe quedar corrupta. El drift debe ser 0.
    balance_drift: ['value==0'],
    // La carrera de idempotencia no debe producir 5xx.
    idempotency_5xx: ['count==0'],
    tx_create_duration: ['p(95)<1500'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function jsonHeaders(token, extra) {
  return Object.assign(
    { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    extra || {}
  );
}

// Reintento con backoff para el bootstrap (no forma parte de la medicion).
const PROVISION_RETRIES = parseInt(__ENV.PROVISION_RETRIES || '6', 10);
const PROVISION_BACKOFF_MS = parseInt(__ENV.PROVISION_BACKOFF_MS || '1000', 10);

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

function bootstrapShared() {
  const rnd = Math.random().toString(36).slice(2, 10);
  const email = `qa_cont_${rnd}@perf.local`;

  const register = withRetry('register', () =>
    http.post(
      `${BASE_URL}/auth/register`,
      JSON.stringify({
        name: 'QA',
        lastname: 'Contention',
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
  if (wallet.status !== 201) {
    throw new Error(`wallet fallo: ${wallet.status} ${wallet.body}`);
  }

  const category = withRetry('categories', () =>
    http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ name: `QA Cont ${rnd}`, description: 'contencion', userId, type: 'SPENT' }),
      { headers: jsonHeaders(token) }
    )
  );
  if (category.status !== 201) {
    throw new Error(`category fallo: ${category.status} ${category.body}`);
  }

  return {
    token,
    userId,
    walletId: wallet.json('id'),
    categoryId: category.json('id'),
  };
}

export function setup() {
  const shared = bootstrapShared();
  console.log(
    `Contencion: userId=${shared.userId} walletId=${shared.walletId} ` +
      `vus=${VUS} writes/vu=${WRITES_PER_VU} idemVus=${IDEM_VUS} amount=${AMOUNT_PER_TX}`
  );
  return shared;
}

// --- Grupo 1: todos escriben contra la MISMA billetera -----------------------
export function sameWalletFlow(data) {
  const body = JSON.stringify({
    walletId: data.walletId,
    categoryId: data.categoryId,
    description: `cont contention ${Math.random().toString(36).slice(2, 8)}`,
    amount: AMOUNT_PER_TX,
    paymentMethod: 'CASH',
    type: 'SPENT',
  });

  group('same_wallet', () => {
    const res = http.post(`${BASE_URL}/transactions`, body, {
      headers: jsonHeaders(data.token),
      tags: { endpoint: 'POST /transactions', flow: 'same_wallet' },
    });
    txCreateDuration.add(res.timings.duration);

    if (res.status === 201) {
      check(res, { 'created 201': () => true });
      return;
    }

    // Un fallo de optimistic lock se manifiesta como 409 (ConflictException) o
    // 500 (optimistic lock / DataIntegrityViolation no traducida). Ambos son
    // "seguros" (no corrompen) pero cuentan como contención no manejada.
    if (res.status === 409 || res.status >= 500) {
      contentionConflicts.add(1);
    }
    check(res, { 'created 201': () => false }, `status=${res.status} body=${res.body}`);
  });
}

// --- Grupo 2: todos usan la MISMA Idempotency-Key en paralelo ---------------
export function sameIdempotencyKeyFlow(data) {
  const key = `contention-${data.userId}-${__ENV.RUN_ID || 'run'}`;
  const body = JSON.stringify({
    walletId: data.walletId,
    categoryId: data.categoryId,
    description: 'cont same key',
    amount: AMOUNT_PER_TX,
    paymentMethod: 'CASH',
    type: 'SPENT',
  });

  group('same_idempotency_key', () => {
    const res = http.post(`${BASE_URL}/transactions`, body, {
      headers: jsonHeaders(data.token, { 'Idempotency-Key': key }),
      tags: { endpoint: 'POST /transactions (idem)', flow: 'same_idempotency_key' },
    });

    // Correcto: exactamente uno crea (201) y el resto hace replay (201 mismo id)
    // o 409 si la key pertenece a otro usuario. Lo que NO se acepta: 5xx.
    if (res.status >= 500) {
      idempotency5xx.add(1);
      check(res, { 'no 5xx on idempotency race': () => false }, `status=${res.status} ${res.body}`);
      return;
    }
    if (res.status === 201) {
      idempotencyReplays.add(1);
      check(res, { 'idempotency settled (201/409)': () => res.status === 201 || res.status === 409 });
    } else {
      check(res, { 'idempotency settled (201/409)': () => res.status === 409 }, `status=${res.status}`);
    }
  });
}

// --- Verificacion final: el saldo debe cuadrar ------------------------------
export function teardown(data) {
  const created = (VUS * WRITES_PER_VU) + 1; // +1 por el grupo de idempotencia
  const expected = -(created * AMOUNT_PER_TX);

  const res = http.get(`${BASE_URL}/wallets?page=0&size=20`, {
    headers: jsonHeaders(data.token),
  });

  if (res.status !== 200) {
    console.warn(`No se pudo leer wallets para verificar saldo: ${res.status}`);
    return;
  }

  const wallets = res.json();
  // PageResponse<T> => { items: [...], pagination: {...} }
  const list = Array.isArray(wallets) ? wallets : (wallets.items || wallets.content || []);
  const primary = list.find((w) => w.id === data.walletId) || list[0];
  if (!primary) {
    console.warn('No se encontro la billetera compartida en la respuesta.');
    return;
  }

  // OJO: si hubo conflicts, menos transacciones se crearon realmente, asi que
  // este drift mide cuantas escrituras se perdieron respecto al ideal.
  const actual = parseFloat(primary.balance);
  const drift = Math.abs(expected - actual);
  balanceDrift.add(drift);

  console.log(
    `Saldo esperado(ideal)=${expected.toFixed(2)} real=${actual.toFixed(2)} ` +
      `drift=${drift.toFixed(2)} (drift>0 => escrituras rechazadas por lock, NO corrupcion)`
  );
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/contention_summary.json';
  const m = data.metrics;
  const pick = (key, field) => (m[key] && m[key].values[field] !== undefined ? m[key].values[field] : 'n/a');
  return {
    [jsonPath]: JSON.stringify(sanitizeSummary(data)),
    stdout: `\n=== Resumen [contention] ===
  tx creadas (201):        ${pick('idempotency_replays', 'count')} (idem) / total reqs ${pick('http_reqs', 'count')}
  conflicts de lock (409/5xx): ${pick('contention_conflicts', 'count')}
  idempotency 5xx:         ${pick('idempotency_5xx', 'count')}
  balance drift:           ${pick('balance_drift', 'value')}
  tx_create p95:           ${pick('tx_create_duration', 'p(95)')} ms
  http_req_failed rate:    ${pick('http_req_failed', 'rate')}
  req/s:                   ${pick('http_reqs', 'rate')}\n`,
  };
}
