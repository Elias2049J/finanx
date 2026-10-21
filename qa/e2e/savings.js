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
  pollUntil,
  statusOk,
} from './lib/common.js';

// E2E del modulo planning / metas de ahorro:
//   1. crear meta y obtenerla por id (state RUNNING)
//   2. asociar transacciones INCOME -> progreso y completado automatico
//   3. validacion: asociar un SPENT a una meta -> 400
//   4. validacion: crear meta sin targetAmount -> 400
//   5. cancelar meta
//   6. listar con filtros

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listCategories: 'GET /categories',
  createTx: 'POST /transactions',
  createGoal: 'POST /savings',
  getGoal: 'GET /savings/{id}',
  listGoals: 'GET /savings',
  cancelGoal: 'POST /savings/{id}/cancel',
  disableGoal: 'POST /savings/{id}/disable',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'savings', latency: 1500 });

export function setup() {
  const ctx = bootstrapUser(EP);
  const income = findCategory(ctx.token, EP, 'INCOME');
  const spent = findCategory(ctx.token, EP, 'SPENT');
  assert('existe categoria INCOME', !!income, 'sin categorias INCOME');
  assert('existe categoria SPENT', !!spent, 'sin categorias SPENT');
  return { ...ctx, incomeCategoryId: income.id, spentCategoryId: spent.id };
}

function deposit(token, data, amount, goalId) {
  return http.post(
    `${BASE_URL}/transactions`,
    JSON.stringify({
      walletId: data.walletId,
      categoryId: data.incomeCategoryId,
      savingGoalId: goalId,
      description: `aporte meta ${amount}`,
      amount,
      paymentMethod: 'BANK_TRANSFER',
      type: 'INCOME',
    }),
    params(token, EP.createTx, [200, 201, 400])
  );
}

export default function (data) {
  const { token, userId } = data;
  const deadline = daysFromNow(90);
  let goalId;

  group('crear meta de ahorro', () => {
    const res = http.post(
      `${BASE_URL}/savings`,
      JSON.stringify({ description: 'E2E ahorro laptop', targetAmount: 1000.0, deadline, userId }),
      params(token, EP.createGoal)
    );
    statusOk('crear meta', res, 201);
    goalId = res.json('id');
    assert('meta tiene id', !!goalId);
    assert('estado RUNNING', res.json('state') === 'RUNNING', res.json('state'));
    assert('progreso inicial 0', Number(res.json('progressPercentage')) === 0, res.json('progressPercentage'));
  });

  group('aporte parcial actualiza progreso', () => {
    statusOk('aporte 400', deposit(token, data, 400.0, goalId), 201);
    const res = http.get(`${BASE_URL}/savings/${goalId}`, params(token, EP.getGoal));
    statusOk('get meta', res, 200);
    assert('acumulado 400', Number(res.json('accumulated')) === 400, res.json('accumulated'));
    assert('progreso 40%', Number(res.json('progressPercentage')) === 40, res.json('progressPercentage'));
    assert('sigue RUNNING', res.json('state') === 'RUNNING', res.json('state'));
  });

  group('validacion: SPENT no puede asociarse a meta -> 400', () => {
    const res = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId: data.walletId,
        categoryId: data.spentCategoryId,
        savingGoalId: goalId,
        description: 'gasto asociado a meta',
        amount: 10.0,
        paymentMethod: 'CASH',
        type: 'SPENT',
      }),
      params(token, EP.createTx, 400)
    );
    assert('SPENT a meta -> 400', res.status === 400, `${res.status} ${res.body}`);
  });

  group('alcanzar objetivo -> COMPLETED automatico', () => {
    statusOk('aporte 600 (total 1000)', deposit(token, data, 600.0, goalId), 201);

    const res = pollUntil(() => {
      const got = http.get(`${BASE_URL}/savings/${goalId}`, params(token, EP.getGoal));
      return got.json('state') === 'COMPLETED' ? got : null;
    }, { timeoutSeconds: 20, intervalSeconds: 2 });

    assert('meta COMPLETED', !!res, 'no paso a COMPLETED en 20s');
    if (res) {
      assert('acumulado congelado 1000', Number(res.json('accumulated')) === 1000, res.json('accumulated'));
      assert('completedAt presente', !!res.json('completedAt'), JSON.stringify(res.json('completedAt')));
    }
  });

  group('no se puede aportar a meta completada -> 400', () => {
    const res = deposit(token, data, 50.0, goalId);
    assert('aporte a meta completada -> 400', res.status === 400, `${res.status} ${res.body}`);
  });

  group('listar metas', () => {
    const all = http.get(`${BASE_URL}/savings?page=0&size=50`, params(token, EP.listGoals));
    statusOk('listar metas', all, 200);
    assert('aparece la meta creada', (all.json('items') || []).some((g) => g.id === goalId));
  });

  group('cancelar meta', () => {
    const created = http.post(
      `${BASE_URL}/savings`,
      JSON.stringify({ description: 'E2E meta a cancelar', targetAmount: 500.0, deadline, userId }),
      params(token, EP.createGoal)
    );
    statusOk('crear meta a cancelar', created, 201);
    const cancelId = created.json('id');

    const res = http.post(`${BASE_URL}/savings/${cancelId}/cancel`, null, params(token, EP.cancelGoal));
    statusOk('cancelar meta', res, 204);
    const got = http.get(`${BASE_URL}/savings/${cancelId}`, params(token, EP.getGoal));
    statusOk('get meta cancelada', got, 200);
    assert('estado CANCELLED', got.json('state') === 'CANCELLED', got.json('state'));
  });

  group('validaciones de entrada -> 400', () => {
    const noTarget = http.post(
      `${BASE_URL}/savings`,
      JSON.stringify({ description: 'sin objetivo', deadline, userId }),
      params(token, EP.createGoal, 400)
    );
    assert('sin targetAmount -> 400', noTarget.status === 400, `${noTarget.status} ${noTarget.body}`);
  });
}

export { handleSummary };
