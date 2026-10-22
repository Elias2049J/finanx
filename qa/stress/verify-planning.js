import http from 'k6/http';
import { check, sleep } from 'k6';
import { resolveBaseUrl } from './lib/base-url.js';

const BASE_URL = resolveBaseUrl(__ENV.BASE_URL);

const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '2m';

const BUDGET_LIMIT = parseFloat(__ENV.BUDGET_LIMIT || '100');
const SPENT_AMOUNT = parseFloat(__ENV.SPENT_AMOUNT || '150');
const ALERT_PERCENT = parseInt(__ENV.ALERT_PERCENT || '80', 10);

const ASYNC_WAIT_S = parseFloat(__ENV.ASYNC_WAIT_S || '4');

export const options = {
  scenarios: {
    verify: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      exec: 'verify',
    },
  },
  setupTimeout: SETUP_TIMEOUT,
  thresholds: {
    checks: ['rate==1'],
  },
};

function jsonHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

const LIMA_OFFSET_HOURS = -5;

function dateISO(offsetDays) {
  const ms = Date.now() + offsetDays * 24 * 60 * 60 * 1000 + LIMA_OFFSET_HOURS * 60 * 60 * 1000;
  return new Date(ms).toISOString().slice(0, 10);
}

function todayISO() {
  return dateISO(0);
}

function plusDaysISO(days) {
  return dateISO(days);
}

export function setup() {
  const rnd = `${Math.random().toString(36).slice(2, 10)}`;
  const email = `qa_recheck_${rnd}@perf.local`;

  const register = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ name: 'QA', lastname: 'Recheck', email, password: 'Password123!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (register.status !== 200 && register.status !== 201) {
    throw new Error(`register fallo: ${register.status} ${register.body}`);
  }
  const token = register.json('accessToken');
  const headers = jsonHeaders(token);

  const me = http.get(`${BASE_URL}/users/me`, { headers });
  if (me.status !== 200) throw new Error(`users/me fallo: ${me.status} ${me.body}`);
  const userId = me.json('id');

  http.post(
    `${BASE_URL}/users/me/preferences`,
    JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode: 'PEN', emailNotifications: false }),
    { headers }
  );

  const wallet = http.post(
    `${BASE_URL}/wallets`,
    JSON.stringify({ currencyCode: 'PEN', primary: true }),
    { headers }
  );
  if (wallet.status !== 201) throw new Error(`wallets fallo: ${wallet.status} ${wallet.body}`);
  const walletId = wallet.json('id');

  const category = http.post(
    `${BASE_URL}/categories`,
    JSON.stringify({ name: `QA Recheck ${rnd}`, description: 'recheck', userId, type: 'SPENT' }),
    { headers }
  );
  if (category.status !== 201) throw new Error(`categories fallo: ${category.status} ${category.body}`);
  const categoryId = category.json('id');

  const budget = http.post(
    `${BASE_URL}/budgets`,
    JSON.stringify({
      alertable: true,
      percentAlert: ALERT_PERCENT,
      limitAmount: BUDGET_LIMIT,
      description: `QA Recheck budget ${rnd}`,
      userId,
      categoryId,
      start: todayISO(),
      end: plusDaysISO(30),
    }),
    { headers }
  );
  if (budget.status !== 201) throw new Error(`budgets fallo: ${budget.status} ${budget.body}`);
  const budgetId = budget.json('id');

  const initial = http.get(`${BASE_URL}/budgets/${budgetId}`, { headers });
  if (initial.status !== 200) throw new Error(`budgets/{id} fallo: ${initial.status} ${initial.body}`);

  return {
    token,
    userId,
    walletId,
    categoryId,
    budgetId,
    initialHealth: initial.json('health'),
  };
}

function countBudgetNotifications(data, budgetId) {
  const r = http.get(`${BASE_URL}/notifications?page=0&size=100`, {
    headers: jsonHeaders(data.token),
  });
  if (r.status !== 200) return { status: r.status, count: null, total: null };
  const body = r.json();
  const items = (body && (body.content || body.items)) || [];
  const matching = items.filter((n) => {
    const text = JSON.stringify(n).toLowerCase();
    return text.includes('presupuesto') || text.includes('budget');
  });
  return { status: r.status, count: matching.length, total: items.length };
}

function walletBalance(data) {
  const r = http.get(`${BASE_URL}/wallets/${data.walletId}`, { headers: jsonHeaders(data.token) });
  return r.status === 200 ? parseFloat(r.json('balance')) : null;
}

export function verify(data) {
  const headers = jsonHeaders(data.token);

  check(data, {
    'budget arranca en OK': (d) => d.initialHealth === 'OK',
  });

  const balanceBefore = walletBalance(data);

  const spent = http.post(
    `${BASE_URL}/transactions`,
    JSON.stringify({
      walletId: data.walletId,
      categoryId: data.categoryId,
      description: 'QA recheck overspend',
      amount: SPENT_AMOUNT,
      paymentMethod: 'CASH',
      type: 'SPENT',
    }),
    { headers }
  );
  check(spent, {
    'gasto creado (201)': (r) => r.status === 201,
  });
  const transactionId = spent.json('id');

  const balanceAfterSpend = walletBalance(data);
  check({ before: balanceBefore, after: balanceAfterSpend }, {
    'balance descontado exactamente una vez': (v) =>
      v.before !== null &&
      v.after !== null &&
      Math.abs(v.before - SPENT_AMOUNT - v.after) < 0.01,
  });

  sleep(ASYNC_WAIT_S);

  const after = http.get(`${BASE_URL}/budgets/${data.budgetId}`, { headers });
  check(after, {
    'budget consultado (200)': (r) => r.status === 200,
    'health recalculado asincronamente a EXCEEDED': (r) => r.json('health') === 'EXCEEDED',
  });

  sleep(ASYNC_WAIT_S);

  const notif1 = countBudgetNotifications(data, data.budgetId);
  sleep(ASYNC_WAIT_S);
  const notif2 = countBudgetNotifications(data, data.budgetId);

  check(notif1, {
    'notificaciones consultables (200)': (r) => r.status === 200,
  });
  check(notif2, {
    'no se duplican notificaciones tras esperar': (r) =>
      notif1.count !== null && r.count === notif1.count,
    'se genero al menos una notificacion de presupuesto': (r) =>
      r.count !== null && r.count >= 1,
  });

  const del1 = http.del(`${BASE_URL}/transactions/${transactionId}`, null, { headers });
  check(del1, {
    'gasto desactivado (200/204)': (r) => r.status === 200 || r.status === 204,
  });

  const del2 = http.del(`${BASE_URL}/transactions/${transactionId}`, null, { headers });
  check(del2, {
    'segundo disable es idempotente (200/204)': (r) => r.status === 200 || r.status === 204,
  });

  const balanceAfterDisable = walletBalance(data);
  check({ before: balanceBefore, after: balanceAfterDisable }, {
    'balance revertido exactamente una vez': (v) =>
      v.before !== null && v.after !== null && Math.abs(v.before - v.after) < 0.01,
  });
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/verify-planning_summary.json';
  const checks = data.metrics.checks && data.metrics.checks.values ? data.metrics.checks.values : {};
  const passes = checks.passes !== undefined ? checks.passes : 'n/a';
  const fails = checks.fails !== undefined ? checks.fails : 'n/a';
  return {
    [jsonPath]: JSON.stringify(data),
    stdout: `\n=== Verificacion recalcado asincrono de planning ===
  checks pasados: ${passes}
  checks fallados: ${fails}
=====================================================\n`,
  };
}
