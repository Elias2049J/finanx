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
  dashboardBalance: 'POST /analytics/dashboard/balance',
  dashboardBudgets: 'POST /analytics/dashboard/budgets',
  budgetExecution: 'POST /analytics/budgets/{id}/execution',
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
  const period = { start: daysAgo(30), end: today() };

  group('dashboard de balance', () => {
    const res = http.post(
      `${BASE_URL}/analytics/dashboard/balance`,
      JSON.stringify(period),
      params(token, EP.dashboardBalance)
    );
    statusOk('dashboard balance', res, 200);
    const body = res.json();
    assert('tiene type BALANCE', body.type === 'BALANCE', body.type);
    assert('tiene periodo', !!body.periodResponse, JSON.stringify(body.periodResponse));
    assert('balance = 600 (800-200)', Number(body.balance) === 600, `balance=${body.balance}`);
    assert('tiene ranking por categoria', !!body.summaryByCategory, 'sin summaryByCategory');
  });

  group('dashboard de presupuestos', () => {
    const res = http.post(
      `${BASE_URL}/analytics/dashboard/budgets`,
      JSON.stringify(period),
      params(token, EP.dashboardBudgets)
    );
    statusOk('dashboard budgets', res, 200);
    const body = res.json();
    assert('tiene type BUDGETS', body.type === 'BUDGETS', body.type);
    assert('incluye totales de presupuesto', body.totalBudgetLimitAmount !== undefined, JSON.stringify(body));
  });

  group('ejecucion de presupuesto por id', () => {
    const res = http.post(
      `${BASE_URL}/analytics/budgets/${data.budgetId}/execution`,
      null,
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
    const noPeriod = http.post(
      `${BASE_URL}/analytics/dashboard/balance`,
      JSON.stringify({}),
      params(token, EP.dashboardBalance, 400)
    );
    assert('sin periodo -> 400', noPeriod.status === 400, `${noPeriod.status} ${noPeriod.body}`);
  });
}

export { handleSummary };
