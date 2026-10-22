import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { resolveBaseUrl } from './lib/base-url.js';
import { sanitizeSummary } from '../lib/sanitize-summary.js';

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);
const SCENARIO = __ENV.SCENARIO || 'load';
const WRITE_RATIO = parseFloat(__ENV.WRITE_RATIO || '0.4');
const SLEEP = parseFloat(__ENV.SLEEP || '0');

const crudDuration = new Trend('crud_duration', true);
const readDuration = new Trend('read_duration', true);
const crudFailRate = new Rate('crud_failed');
const transactionsCreated = new Counter('transactions_created');
const transactionsDeleted = new Counter('transactions_deleted');

function num(envKey, fallback) {
  const v = parseInt(__ENV[envKey] || '', 10);
  return Number.isNaN(v) ? fallback : v;
}

const EP = {
  register: 'POST /auth/register',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  wallets: 'POST /wallets',
  listWallets: 'GET /wallets',
  createCategory: 'POST /categories',
  createReason: 'POST /reasons',
  listTx: 'GET /transactions',
  listTxActive: 'GET /transactions?active',
  listCats: 'GET /categories',
  listBudgets: 'GET /budgets',
  listReasons: 'GET /reasons',
  listSavings: 'GET /savings',
  analyticsBalance: 'POST /analytics/dashboard/balance',
  analyticsBudgets: 'POST /analytics/dashboard/budgets',
  createTx: 'POST /transactions',
  getTx: 'GET /transactions/{id}',
  deleteTx: 'DELETE /transactions/{id}',
};

const profiles = {
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: __ENV.RAMP_UP || '1m', target: num('VUS', 50) },
      { duration: __ENV.HOLD || '3m', target: num('VUS', 50) },
      { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 50 },
      { duration: '3m', target: 100 },
      { duration: '3m', target: 200 },
      { duration: '3m', target: 300 },
      { duration: '1m', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  breakpoint: {
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 400,
    stages: [
      { duration: '2m', target: 50 },
      { duration: '2m', target: 100 },
      { duration: '2m', target: 150 },
      { duration: '2m', target: 200 },
      { duration: '2m', target: 250 },
    ],
    gracefulStop: '30s',
  },
  spike: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '10s', target: 300 },
      { duration: '1m', target: 300 },
      { duration: '10s', target: 10 },
      { duration: '30s', target: 0 },
    ],
  },
  soak: {
    executor: 'constant-vus',
    vus: num('VUS', 80),
    duration: __ENV.DURATION || '30m',
  },
};

if (!profiles[SCENARIO]) {
  throw new Error(
    `SCENARIO invalido: "${SCENARIO}". Validos: ${Object.keys(profiles).join(', ')}`
  );
}

const isBreakpoint = SCENARIO === 'breakpoint';

const endpointThresholds = {};
Object.values(EP).forEach((name) => {
  endpointThresholds[`http_req_duration{endpoint:${name}}`] = ['p(95)<800'];
  endpointThresholds[`http_req_failed{endpoint:${name}}`] = ['rate<0.01'];
  endpointThresholds[`http_reqs{endpoint:${name}}`] = ['count>0'];
});

export const options = {
  scenarios: { [SCENARIO]: profiles[SCENARIO] },
  thresholds: {
    http_req_duration: isBreakpoint
      ? [{ threshold: 'p(95)<800', abortOnFail: true, delayAbortEval: '20s' }]
      : ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    ...endpointThresholds,
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function headers(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

function paramsFor(token, endpoint, type, phase) {
  return {
    headers: headers(token),
    tags: { phase: phase || 'normal', type, endpoint },
  };
}

let vuCtx = null;

// Reintento con backoff para el bootstrap de cada VU (no forma parte de la
// medicion util: el primer ciclo es de setup). Absorbe 429 de proxies (ngrok) y
// 5xx transitorios.
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

function bootstrap() {
  const rnd = Math.random().toString(36).slice(2, 10);
  const email = `qa_${rnd}@perf.local`;

  const register = withRetry('register', () =>
    http.post(
      `${BASE_URL}/auth/register`,
      JSON.stringify({
        name: 'QA',
        lastname: 'Load',
        email,
        password: 'Password123!',
      }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { phase: 'vu_setup', endpoint: EP.register },
      }
    )
  );
  if (register.status !== 200 && register.status !== 201) {
    throw new Error(`vu bootstrap register fallo: ${register.status} ${register.body}`);
  }
  const token = register.json('accessToken');

  const me = withRetry('users/me', () =>
    http.get(`${BASE_URL}/users/me`, paramsFor(token, EP.me, 'read', 'vu_setup'))
  );
  if (me.status !== 200) {
    throw new Error(`vu bootstrap users/me fallo: ${me.status} ${me.body}`);
  }
  const userId = me.json('id');

  const prefs = withRetry('preferences', () =>
    http.post(
      `${BASE_URL}/users/me/preferences`,
      JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode: 'PEN', emailNotifications: true }),
      paramsFor(token, EP.prefs, 'write', 'vu_setup')
    )
  );
  if (prefs.status !== 201) {
    throw new Error(`vu bootstrap preferences fallo: ${prefs.status} ${prefs.body}`);
  }

  const wallet = withRetry('wallets', () =>
    http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'PEN', primary: true }),
      paramsFor(token, EP.wallets, 'write', 'vu_setup')
    )
  );
  if (wallet.status !== 201) {
    throw new Error(`vu bootstrap wallet fallo: ${wallet.status} ${wallet.body}`);
  }

  const category = withRetry('categories', () =>
    http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ name: `QA Cat ${rnd}`, description: 'categoria perf', userId, type: 'SPENT' }),
      paramsFor(token, EP.createCategory, 'write', 'vu_setup')
    )
  );
  if (category.status !== 201) {
    throw new Error(`vu bootstrap category fallo: ${category.status} ${category.body}`);
  }

  const reason = withRetry('reasons', () =>
    http.post(
      `${BASE_URL}/reasons`,
      JSON.stringify({ description: `motivo perf ${rnd}`, userId }),
      paramsFor(token, EP.createReason, 'write', 'vu_setup')
    )
  );

  vuCtx = {
    token,
    userId,
    walletId: wallet.json('id'),
    categoryId: category.json('id'),
    reasonId: reason.status === 201 ? reason.json('id') : null,
  };
  return vuCtx;
}

function ctx() {
  return vuCtx || bootstrap();
}

function readRequest(method, url, body, token, endpoint) {
  return {
    method,
    url,
    body: body === null || body === undefined ? null : JSON.stringify(body),
    params: paramsFor(token, endpoint, 'read'),
  };
}

function readFlow(data) {
  const { token } = ctx();
  const requests = [
    readRequest('GET', `${BASE_URL}/transactions?page=0&size=20`, null, token, EP.listTx),
    readRequest('GET', `${BASE_URL}/transactions?active=true&page=0&size=20`, null, token, EP.listTxActive),
    readRequest('GET', `${BASE_URL}/wallets?page=0&size=50`, null, token, EP.listWallets),
    readRequest('GET', `${BASE_URL}/categories?page=0&size=100`, null, token, EP.listCats),
    readRequest('GET', `${BASE_URL}/budgets?page=0&size=50`, null, token, EP.listBudgets),
    readRequest('GET', `${BASE_URL}/reasons?page=0&size=100`, null, token, EP.listReasons),
    readRequest('GET', `${BASE_URL}/savings?page=0&size=50`, null, token, EP.listSavings),
    readRequest('POST', `${BASE_URL}/analytics/dashboard/balance`, { start: data.start, end: data.end }, token, EP.analyticsBalance),
    readRequest('POST', `${BASE_URL}/analytics/dashboard/budgets`, { start: data.start, end: data.end }, token, EP.analyticsBudgets),
  ];

  const responses = http.batch(requests);
  responses.forEach((r) => readDuration.add(r.timings.duration));
  check(responses, {
    'reads 200': (rs) => rs.every((r) => r.status === 200),
  });
}

function crudFlow() {
  const { token, walletId, categoryId, reasonId } = ctx();

  const body = {
    walletId,
    categoryId,
    reasonId,
    description: `perf tx ${Math.random().toString(36).slice(2, 8)}`,
    amount: Math.round((Math.random() * 500 + 1) * 100) / 100,
    paymentMethod: 'CREDIT_CARD',
    type: 'SPENT',
  };

  group('crud_transactions', () => {
    const createParams = paramsFor(token, EP.createTx, 'write');
    const create = http.post(`${BASE_URL}/transactions`, JSON.stringify(body), createParams);
    const created = check(create, { 'create tx 201': (r) => r.status === 201 });
    crudFailRate.add(!created);
    crudDuration.add(create.timings.duration);
    if (!created) {
      return;
    }
    transactionsCreated.add(1);
    const id = create.json('id');

    const get = http.get(`${BASE_URL}/transactions/${id}`, paramsFor(token, EP.getTx, 'write'));
    check(get, { 'get tx 200': (r) => r.status === 200 });
    crudDuration.add(get.timings.duration);

    const del = http.del(`${BASE_URL}/transactions/${id}`, null, paramsFor(token, EP.deleteTx, 'write'));
    const deleted = check(del, { 'delete tx 204': (r) => r.status === 204 });
    crudDuration.add(del.timings.duration);
    if (deleted) {
      transactionsDeleted.add(1);
    }
  });
}

export function setup() {
  return {
    start: '2026-01-01',
    end: new Date().toISOString().slice(0, 10),
  };
}

export default function (data) {
  readFlow(data);

  if (Math.random() < WRITE_RATIO) {
    crudFlow();
  }

  if (SLEEP > 0) {
    sleep(SLEEP);
  }
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || `reports/${SCENARIO}_summary.json`;
  return {
    [jsonPath]: JSON.stringify(sanitizeSummary(data)),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const m = data.metrics;
  const pick = (key, field) => (m[key] && m[key].values[field] !== undefined ? m[key].values[field] : 'n/a');
  return `\n=== Resumen [${SCENARIO}] ===\n  p95: ${pick('http_req_duration', 'p(95)')} ms\n  http_req_failed: ${pick('http_req_failed', 'rate')}\n  req/s: ${pick('http_reqs', 'rate')}\n  iteraciones: ${pick('iterations', 'count')}\n`;
}
