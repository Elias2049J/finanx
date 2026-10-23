import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  assert,
  bootstrapUser,
  daysAgo,
  daysFromNow,
  e2eOptions,
  findCategory,
  handleSummary,
  params,
  statusOk,
  today,
} from './lib/common.js';

// E2E del modulo insights (analytics):
//   1. dashboard de balance refleja ingresos/gastos del periodo
//   2. dashboard de presupuestos incluye el presupuesto del periodo
//   3. ejecucion de un presupuesto por id
//   4. listado de ejecuciones por periodo
//   5. validaciones de entrada -> 400

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listCategories: 'GET /categories',
  createTx: 'POST /transactions',
  createBudget: 'POST /budgets',
  dashboardBalance: 'GET /analytics/dashboard/balance',
  dashboardBudgets: 'GET /analytics/dashboard/budgets',
  budgetExecution: 'GET /analytics/budgets/{id}/execution',
  budgetExecutions: 'GET /analytics/budgets/executions',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'analytics', latency: 3000 });

export function setup() {
  const ctx = bootstrapUser(EP);
  const spent = findCategory(ctx.token, EP, 'SPENT');
  const income = findCategory(ctx.token, EP, 'INCOME');
  assert('existe categoria SPENT', !!spent, 'sin categorias SPENT');
  assert('existe categoria INCOME', !!income, 'sin categorias INCOME');

  // Sembramos movimientos y un presupuesto para que los dashboards tengan datos.
  http.post(
    `${BASE_URL}/transactions`,
    JSON.stringify({
      walletId: ctx.walletId,
      categoryId: spent.id,
      description: 'gasto analytics',
      amount: 200.0,
      paymentMethod: 'CASH',
      type: 'SPENT',
    }),
    params(ctx.token, EP.createTx)
  );
  http.post(
    `${BASE_URL}/transactions`,
    JSON.stringify({
      walletId: ctx.walletId,
      categoryId: income.id,
      description: 'ingreso analytics',
      amount: 800.0,
      paymentMethod: 'BANK_TRANSFER',
      type: 'INCOME',
    }),
    params(ctx.token, EP.createTx)
  );
  const budget = http.post(
    `${BASE_URL}/budgets`,
    JSON.stringify({
      alertable: true,
      percentAlert: 80,
      limitAmount: 1000.0,
      description: 'presupuesto analytics',
      userId: ctx.userId,
      categoryId: spent.id,
      start: daysAgo(5),
      end: daysFromNow(25),
    }),
    params(ctx.token, EP.createBudget)
  );
  assert('presupuesto sembrado', budget.status === 201, `${budget.status} ${budget.body}`);

  return { ...ctx, spentCategoryId: spent.id, budgetId: budget.json('id') };
}

export default function (data) {
  const { token } = data;
  const query = `from=${daysAgo(30)}&to=${today()}`;

  group('dashboard de balance', () => {
    const res = http.get(
      `${BASE_URL}/analytics/dashboard/balance?${query}`,
      params(token, EP.dashboardBalance)
    );
    statusOk('dashboard balance', res, 200);
    const body = res.json();
    assert('tiene type BALANCE', body.type === 'BALANCE', body.type);
    assert('tiene periodo', !!body.dateRangeLabel, JSON.stringify(body.dateRangeLabel));
    assert('expone moneda base', body.currencyCode === data.currencyCode, `${body.currencyCode} vs ${data.currencyCode}`);
    assert('balance = 600 (800-200)', Number(body.balance) === 600, `balance=${body.balance}`);
    assert('consolidado completo', body.partial === false, `partial=${body.partial}`);
    assert('sin tasas faltantes', Array.isArray(body.missingRates) && body.missingRates.length === 0, JSON.stringify(body.missingRates));
    assert('tiene ranking por categoria', !!body.summaryByCategory, 'sin summaryByCategory');
  });

  group('dashboard de balance filtrado por billetera', () => {
    const filtered = http.get(
      `${BASE_URL}/analytics/dashboard/balance?${query}&walletId=${data.walletId}`,
      params(token, EP.dashboardBalance)
    );
    statusOk('balance por billetera', filtered, 200);
    const body = filtered.json();
    assert('filtrado mantiene moneda base', body.currencyCode === data.currencyCode, `${body.currencyCode}`);
    assert('balance filtrado = 600', Number(body.balance) === 600, `balance=${body.balance}`);

    const foreign = http.get(
      `${BASE_URL}/analytics/dashboard/balance?${query}&walletId=999999999`,
      params(token, EP.dashboardBalance, 404)
    );
    assert('billetera ajena -> 404', foreign.status === 404, `${foreign.status} ${foreign.body}`);
  });

  group('dashboard de presupuestos', () => {
    const res = http.get(
      `${BASE_URL}/analytics/dashboard/budgets?${query}`,
      params(token, EP.dashboardBudgets)
    );
    statusOk('dashboard budgets', res, 200);
    const body = res.json();
    assert('tiene type BUDGETS', body.type === 'BUDGETS', body.type);
    assert('expone moneda base', body.currencyCode === data.currencyCode, `${body.currencyCode} vs ${data.currencyCode}`);
    assert('consolidado completo', body.partial === false, `partial=${body.partial}`);
    assert('sin tasas faltantes', Array.isArray(body.missingRates) && body.missingRates.length === 0, JSON.stringify(body.missingRates));
    assert('incluye totales de presupuesto', body.totalBudgetLimitAmount !== undefined, JSON.stringify(body));
  });

  group('ejecucion de presupuesto por id', () => {
    const res = http.get(
      `${BASE_URL}/analytics/budgets/${data.budgetId}/execution`,
      params(token, EP.budgetExecution)
    );
    statusOk('ejecucion presupuesto', res, 200);
    const body = res.json();
    assert('tiene spentAmount', body.spentAmount !== undefined, JSON.stringify(body));
    assert('gasto registrado 200', Number(body.spentAmount) === 200, `spentAmount=${body.spentAmount}`);
  });

  group('listado de ejecuciones por periodo', () => {
    const res = http.get(
      `${BASE_URL}/analytics/budgets/executions?from=${daysAgo(30)}&to=${today()}&page=0&size=50`,
      params(token, EP.budgetExecutions)
    );
    statusOk('ejecuciones por periodo', res, 200);
    assert('devuelve items', Array.isArray(res.json('items')));
  });

  group('validaciones de entrada -> 400', () => {
    const noPeriod = http.get(
      `${BASE_URL}/analytics/dashboard/balance`,
      params(token, EP.dashboardBalance, 400)
    );
    assert('sin periodo -> 400', noPeriod.status === 400, `${noPeriod.status} ${noPeriod.body}`);
  });
}

export { handleSummary };
