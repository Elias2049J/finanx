import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  assert,
  bootstrapUser,
  daysFromNow,
  e2eOptions,
  findCategory,
  handleSummary,
  params,
  statusOk,
  today,
} from './lib/common.js';

// E2E del modulo planning / presupuestos:
//   1. crear presupuesto (alertable) y obtenerlo por id
//   2. registrar gastos y verificar health OK -> NEAR_LIMIT -> EXCEEDED
//   3. listar con filtros de estado
//   4. cancelar presupuesto
//   5. crear y deshabilitar un segundo presupuesto
//   6. validaciones de entrada -> 400

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listWallets: 'GET /wallets',
  listCategories: 'GET /categories',
  createTx: 'POST /transactions',
  createBudget: 'POST /budgets',
  getBudget: 'GET /budgets/{id}',
  listBudgets: 'GET /budgets',
  cancelBudget: 'POST /budgets/{id}/cancel',
  disableBudget: 'POST /budgets/{id}/disable',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'budgets', latency: 1500 });

export function setup() {
  const ctx = bootstrapUser(EP);
  const category = findCategory(ctx.token, EP, 'SPENT');
  assert('existe categoria SPENT', !!category, 'sin categorias SPENT');
  return { ...ctx, categoryId: category.id };
}

function spent(token, data, amount) {
  return http.post(
    `${BASE_URL}/transactions`,
    JSON.stringify({
      walletId: data.walletId,
      categoryId: data.categoryId,
      description: `gasto presupuesto ${amount}`,
      amount,
      paymentMethod: 'CASH',
      type: 'SPENT',
    }),
    params(token, EP.createTx)
  );
}

export default function (data) {
  const { token, userId, categoryId } = data;
  const start = today();
  const end = daysFromNow(30);
  let budgetId;

  group('crear presupuesto', () => {
    const res = http.post(
      `${BASE_URL}/budgets`,
      JSON.stringify({
        alertable: true,
        percentAlert: 80,
        limitAmount: 1000.0,
        description: 'E2E presupuesto alimentacion',
        userId,
        categoryId,
        start,
        end,
      }),
      params(token, EP.createBudget)
    );
    statusOk('crear presupuesto', res, 201);
    budgetId = res.json('id');
    assert('presupuesto tiene id', !!budgetId);
    assert('estado ACTIVE', res.json('state') === 'ACTIVE', res.json('state'));
    assert('salud OK inicial', res.json('health') === 'OK', res.json('health'));
    assert('currencyCode presente', !!res.json('currencyCode'), JSON.stringify(res.json()));
  });

  group('gasto parcial mantiene OK', () => {
    statusOk('gasto 300', spent(token, data, 300), 201);
    const res = http.get(`${BASE_URL}/budgets/${budgetId}`, params(token, EP.getBudget));
    statusOk('get presupuesto', res, 200);
    assert('salud OK con 300/1000', res.json('health') === 'OK', `health=${res.json('health')}`);
  });

  group('gasto supera umbral -> NEAR_LIMIT', () => {
    statusOk('gasto 550 (total 850)', spent(token, data, 550), 201);
    const res = http.get(`${BASE_URL}/budgets/${budgetId}`, params(token, EP.getBudget));
    statusOk('get presupuesto', res, 200);
    assert(
      'salud NEAR_LIMIT con 850/1000 (umbral 80%)',
      res.json('health') === 'NEAR_LIMIT',
      `health=${res.json('health')}`
    );
  });

  group('gasto supera limite -> EXCEEDED', () => {
    statusOk('gasto 300 (total 1150)', spent(token, data, 300), 201);
    const res = http.get(`${BASE_URL}/budgets/${budgetId}`, params(token, EP.getBudget));
    statusOk('get presupuesto', res, 200);
    // Este si debe ser EXCEEDED incluso con el bug (gasto real muy por encima).
    assert('salud EXCEEDED', res.json('health') === 'EXCEEDED', `health=${res.json('health')}`);
  });

  group('listar presupuestos', () => {
    const res = http.get(`${BASE_URL}/budgets?page=0&size=50`, params(token, EP.listBudgets));
    statusOk('listar presupuestos', res, 200);
    assert('aparece el presupuesto creado', (res.json('items') || []).some((b) => b.id === budgetId));

    const active = http.get(`${BASE_URL}/budgets?state=ACTIVE&page=0&size=50`, params(token, EP.listBudgets));
    statusOk('listar ACTIVE', active, 200);
    assert('filtro ACTIVE todos coinciden', (active.json('items') || []).every((b) => b.state === 'ACTIVE'));
  });

  group('cancelar presupuesto', () => {
    const res = http.post(`${BASE_URL}/budgets/${budgetId}/cancel`, null, params(token, EP.cancelBudget));
    statusOk('cancelar presupuesto', res, 204);
    const got = http.get(`${BASE_URL}/budgets/${budgetId}`, params(token, EP.getBudget));
    statusOk('get tras cancelar', got, 200);
    assert('estado CANCELLED', got.json('state') === 'CANCELLED', got.json('state'));
  });

  group('deshabilitar presupuesto', () => {
    const created = http.post(
      `${BASE_URL}/budgets`,
      JSON.stringify({
        alertable: false,
        limitAmount: 200.0,
        description: 'E2E presupuesto desechable',
        userId,
        categoryId,
        start,
        end,
      }),
      params(token, EP.createBudget)
    );
    statusOk('crear presupuesto desechable', created, 201);
    const disposableId = created.json('id');

    const res = http.post(
      `${BASE_URL}/budgets/${disposableId}/disable`,
      null,
      params(token, EP.disableBudget)
    );
    statusOk('deshabilitar presupuesto', res, 204);
  });

  group('validaciones de entrada -> 400', () => {
    const noLimit = http.post(
      `${BASE_URL}/budgets`,
      JSON.stringify({ alertable: true, description: 'sin limite', userId, categoryId, start, end }),
      params(token, EP.createBudget, 400)
    );
    assert('sin limitAmount -> 400', noLimit.status === 400, `${noLimit.status} ${noLimit.body}`);

    const badPercent = http.post(
      `${BASE_URL}/budgets`,
      JSON.stringify({
        alertable: true,
        percentAlert: 150,
        limitAmount: 100,
        description: 'percent invalido',
        userId,
        categoryId,
        start,
        end,
      }),
      params(token, EP.createBudget, 400)
    );
    assert('percentAlert > 100 -> 400', badPercent.status === 400, `${badPercent.status} ${badPercent.body}`);
  });
}

export { handleSummary };
