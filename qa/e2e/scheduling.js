import http from 'k6/http';
import { check, group, fail, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';
import { sanitizeSummary } from '../lib/sanitize-summary.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080') + '/api/v1';
const WAIT_SECONDS = parseInt(__ENV.WAIT_SECONDS || '80', 10);
const POLL_SECONDS = parseInt(__ENV.POLL_SECONDS || '10', 10);
const SEED_CATEGORY = parseInt(__ENV.SEED_CATEGORY || '9', 10);

const e2eDuration = new Trend('e2e_duration', true);
const e2eFailRate = new Rate('e2e_failed');
const e2eAssertions = new Counter('e2e_assertions');

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listWallets: 'GET /wallets',
  listCategories: 'GET /categories',
  createSchedule: 'POST /schedule',
  listSchedules: 'GET /schedule',
  listTransactions: 'GET /transactions',
  createTransaction: 'POST /transactions',
  listBudgets: 'GET /budgets',
  listNotifications: 'GET /notifications',
};

const endpointThresholds = {};
Object.values(EP).forEach((name) => {
  endpointThresholds[`http_req_duration{endpoint:${name}}`] = ['p(95)<2000'];
  endpointThresholds[`http_req_failed{endpoint:${name}}`] = ['rate<0.01'];
});

export const options = {
  scenarios: {
    e2e: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || '5m',
    },
  },
  thresholds: {
    e2e_failed: ['rate==0'],
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

function params(token, endpoint) {
  return { headers: headers(token), tags: { endpoint } };
}

function assert(name, ok, detail) {
  e2eAssertions.add(1);
  e2eFailRate.add(ok ? 0 : 1);
  check(null, { [name]: () => ok });
  if (!ok) {
    throw new Error(`ASSERT FAILED: ${name}${detail ? ' -> ' + detail : ''}`);
  }
}

function today() {
  const LIMA_OFFSET_MINUTES = parseInt(__ENV.TZ_OFFSET_MINUTES || '-300', 10);
  const shifted = new Date(Date.now() + LIMA_OFFSET_MINUTES * 60 * 1000);
  return shifted.toISOString().slice(0, 10);
}

function daysAgo(n) {
  const LIMA_OFFSET_MINUTES = parseInt(__ENV.TZ_OFFSET_MINUTES || '-300', 10);
  const shifted = new Date(Date.now() + LIMA_OFFSET_MINUTES * 60 * 1000 - n * 24 * 60 * 60 * 1000);
  return shifted.toISOString().slice(0, 10);
}

function bootstrap() {
  const rnd = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const email = `e2e_${rnd}@e2e.local`;

  const register = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ name: 'E2E', lastname: 'Scheduling', email, password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: EP.register } }
  );
  assert('register 200/201', register.status === 200 || register.status === 201, `${register.status} ${register.body}`);

  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: EP.login } }
  );
  assert('login 200', login.status === 200, `${login.status} ${login.body}`);
  const token = login.json('accessToken');
  assert('login devuelve accessToken', typeof token === 'string' && token.length > 0);

  const me = http.get(`${BASE_URL}/users/me`, params(token, EP.me));
  assert('users/me 200', me.status === 200, `${me.status} ${me.body}`);
  const userId = me.json('id');
  assert('users/me devuelve id', !!userId);

  const prefs = http.post(
    `${BASE_URL}/users/me/preferences`,
    JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode: 'PEN', emailNotifications: true }),
    params(token, EP.prefs)
  );
  assert('preferences 201', prefs.status === 201, `${prefs.status} ${prefs.body}`);

  const wallet = http.post(
    `${BASE_URL}/wallets`,
    JSON.stringify({ currencyCode: 'PEN', primary: true }),
    params(token, EP.createWallet)
  );
  assert('wallet 201', wallet.status === 201, `${wallet.status} ${wallet.body}`);
  const walletId = wallet.json('id');

  const categories = http.get(`${BASE_URL}/categories?page=0&size=50`, params(token, EP.listCategories));
  assert('categories 200', categories.status === 200, `${categories.status} ${categories.body}`);
  const items = categories.json('items') || [];
  const category = items.find((c) => c.type === 'SPENT' && c.id === SEED_CATEGORY) || items.find((c) => c.type === 'SPENT');
  assert('hay categoria SPENT', !!category, 'sin categorias SPENT');
  const categoryId = category.id;

  return { token, userId, walletId, categoryId };
}

export function setup() {
  return bootstrap();
}

function waitForScheduler() {
  sleep(WAIT_SECONDS);
}

export default function (data) {
  const { token, userId, walletId, categoryId } = data;
  const due = daysAgo(1);
  const hardEnd = today().slice(0, 4) + '-12-31';

  let transactionScheduleId;
  let budgetScheduleId;

  group('crear programaciones', () => {
    const txSchedule = http.post(
      `${BASE_URL}/schedule`,
      JSON.stringify({
        type: 'TRANSACTION',
        userId,
        categoryId,
        walletId,
        description: 'E2E scheduled tx',
        amount: 10.0,
        paymentMethod: 'CASH',
        transactionType: 'SPENT',
        recurrenceRule: { recurrenceType: 'NONE', interval: 1, start: due, end: due },
      }),
      params(token, EP.createSchedule)
    );
    assert('schedule transaccion 201', txSchedule.status === 201, `${txSchedule.status} ${txSchedule.body}`);
    transactionScheduleId = txSchedule.json('id');

    const budgetSchedule = http.post(
      `${BASE_URL}/schedule`,
      JSON.stringify({
        type: 'BUDGET',
        userId,
        categoryId,
        alertable: true,
        percentAlert: 80,
        limitAmount: 1500.0,
        description: 'E2E scheduled budget',
        recurrenceRule: { recurrenceType: 'MONTHLY', interval: 1, start: due, end: hardEnd },
      }),
      params(token, EP.createSchedule)
    );
    assert('schedule presupuesto 201', budgetSchedule.status === 201, `${budgetSchedule.status} ${budgetSchedule.body}`);
    budgetScheduleId = budgetSchedule.json('id');
  });

  waitForScheduler();

  group('verificar transaccion programada', () => {
    const wallets = http.get(`${BASE_URL}/wallets`, params(token, EP.listWallets));
    assert('wallets 200', wallets.status === 200);
    const wallet = (wallets.json('items') || []).find((w) => w.id === walletId);
    assert('saldo de billetera aplicado (-10)', wallet && Number(wallet.balance) === -10, wallet ? `balance=${wallet.balance}` : 'wallet no encontrada');

    const txs = http.get(`${BASE_URL}/transactions?page=0&size=50`, params(token, EP.listTransactions));
    assert('transactions 200', txs.status === 200);
    const scheduledTx = (txs.json('items') || []).find((t) => t.scheduleId === transactionScheduleId);
    assert('existe transaccion con scheduleId', !!scheduledTx);
  });

  group('verificar presupuesto programado', () => {
    const budgets = http.get(`${BASE_URL}/budgets?page=0&size=50`, params(token, EP.listBudgets));
    assert('budgets 200', budgets.status === 200);
    const scheduledBudget = (budgets.json('items') || []).find((b) => b.scheduleId === budgetScheduleId);
    assert('existe presupuesto con scheduleId', !!scheduledBudget);
    if (scheduledBudget) {
      assert('presupuesto tiene currencyCode', !!scheduledBudget.currencyCode, JSON.stringify(scheduledBudget));
    }
  });

  group('verificar notificaciones', () => {
    const notifs = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifications));
    assert('notifications 200', notifs.status === 200);
    const items = notifs.json('items') || [];

    const txNotif = items.find((n) => n.scheduleId === transactionScheduleId);
    assert('notificacion de movimiento programado', !!txNotif, JSON.stringify(items));
    if (txNotif) {
      assert('mensaje movimiento', txNotif.message === 'Se ha registrado un nuevo movimiento', txNotif.message);
      assert('tipo movimiento INFO', txNotif.type === 'INFO', txNotif.type);
    }

    const budgetNotif = items.find((n) => n.budgetId && n.message === 'Se ha registrado un nuevo presupuesto');
    assert('notificacion de presupuesto programado', !!budgetNotif, JSON.stringify(items));
    if (budgetNotif) {
      assert('tipo presupuesto INFO', budgetNotif.type === 'INFO', budgetNotif.type);
    }
  });

  group('transaccion manual no notifica', () => {
    const before = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifications));
    const beforeCount = (before.json('items') || []).length;

    const manual = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId,
        description: 'E2E manual tx',
        amount: 5.0,
        paymentMethod: 'CASH',
        type: 'SPENT',
      }),
      params(token, EP.createTransaction)
    );
    assert('transaccion manual 201', manual.status === 201, `${manual.status} ${manual.body}`);
    assert('transaccion manual sin scheduleId', manual.json('scheduleId') === null);

    sleep(5);
    const after = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifications));
    const afterCount = (after.json('items') || []).length;
    assert('transaccion manual no genera notificacion', afterCount === beforeCount, `antes=${beforeCount} despues=${afterCount}`);
  });
}

export function teardown() {
  e2eDuration.add(0);
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/e2e_summary.json';
  return {
    [jsonPath]: JSON.stringify(sanitizeSummary(data)),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
