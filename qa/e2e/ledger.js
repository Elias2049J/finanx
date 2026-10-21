import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  assert,
  bootstrapUser,
  e2eOptions,
  findCategory,
  handleSummary,
  params,
  statusOk,
  today,
} from './lib/common.js';

// E2E del modulo ledger (transacciones):
//   1. crear SPENT e INCOME actualizan el saldo de la billetera
//   2. listar y filtrar transacciones
//   3. obtener por id
//   4. deshabilitar (DELETE) revierte el saldo
//   5. validacion: tipo de transaccion != tipo de categoria -> 400
//   6. idempotencia: repetir POST con la misma Idempotency-Key no duplica
//   7. validaciones de entrada -> 400

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listWallets: 'GET /wallets',
  listCategories: 'GET /categories',
  createTx: 'POST /transactions',
  listTx: 'GET /transactions',
  getTx: 'GET /transactions/{id}',
  deleteTx: 'DELETE /transactions/{id}',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'ledger', latency: 1500 });

export function setup() {
  const ctx = bootstrapUser(EP);
  const spent = findCategory(ctx.token, EP, 'SPENT', 'Alimentacion') || findCategory(ctx.token, EP, 'SPENT');
  const income = findCategory(ctx.token, EP, 'INCOME', 'Salario') || findCategory(ctx.token, EP, 'INCOME');
  assert('existe categoria SPENT', !!spent, 'sin categorias SPENT');
  assert('existe categoria INCOME', !!income, 'sin categorias INCOME');
  return { ...ctx, spentCategoryId: spent.id, incomeCategoryId: income.id };
}

function walletBalance(token) {
  const res = http.get(`${BASE_URL}/wallets`, params(token, EP.listWallets));
  statusOk('listar wallets', res, 200);
  return Number(res.json('items')[0].balance);
}

export default function (data) {
  const { token, walletId, spentCategoryId, incomeCategoryId } = data;
  let txId;

  group('saldo inicial es 0', () => {
    assert('balance inicial 0', walletBalance(token) === 0);
  });

  group('crear gasto y verificar saldo', () => {
    const res = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId: spentCategoryId,
        description: 'E2E ledger gasto',
        amount: 150.75,
        paymentMethod: 'CREDIT_CARD',
        type: 'SPENT',
      }),
      params(token, EP.createTx)
    );
    statusOk('crear SPENT', res, 201);
    txId = res.json('id');
    assert('SPENT tiene id', !!txId);
    assert('saldo tras gasto = -150.75', walletBalance(token) === -150.75, `balance=${walletBalance(token)}`);
  });

  group('crear ingreso y verificar saldo', () => {
    const res = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId: incomeCategoryId,
        description: 'E2E ledger ingreso',
        amount: 500.0,
        paymentMethod: 'BANK_TRANSFER',
        type: 'INCOME',
      }),
      params(token, EP.createTx)
    );
    statusOk('crear INCOME', res, 201);
    assert('saldo tras ingreso = 349.25', walletBalance(token) === 349.25, `balance=${walletBalance(token)}`);
  });

  group('obtener transaccion por id', () => {
    const res = http.get(`${BASE_URL}/transactions/${txId}`, params(token, EP.getTx));
    statusOk('get transaccion', res, 200);
    assert('monto coincide', Number(res.json('amount')) === 150.75, res.json('amount'));
    assert('tipo SPENT', res.json('type') === 'SPENT', res.json('type'));
  });

  group('listar y filtrar transacciones', () => {
    const all = http.get(`${BASE_URL}/transactions?page=0&size=50`, params(token, EP.listTx));
    statusOk('listar transacciones', all, 200);
    assert(
      'hay exactamente 2 transacciones',
      (all.json('items') || []).length === 2,
      `hay ${(all.json('items') || []).length}`
    );

    const spent = http.get(
      `${BASE_URL}/transactions?type=SPENT&page=0&size=50`,
      params(token, EP.listTx)
    );
    statusOk('filtrar por type', spent, 200);
    const spentItems = spent.json('items') || [];
    assert('filtro SPENT devuelve solo SPENT', spentItems.every((t) => t.type === 'SPENT'));

    const byWallet = http.get(
      `${BASE_URL}/transactions?walletId=${walletId}&page=0&size=50`,
      params(token, EP.listTx)
    );
    statusOk('filtrar por walletId', byWallet, 200);
    assert(
      'filtro walletId todos coinciden',
      (byWallet.json('items') || []).every((t) => t.walletId === walletId)
    );

    const search = http.get(
      `${BASE_URL}/transactions?search=ledger&page=0&size=50`,
      params(token, EP.listTx)
    );
    statusOk('buscar texto', search, 200);
    assert('search encuentra resultados', (search.json('items') || []).length >= 1);
  });

  group('validacion tipo != tipo de categoria -> 400', () => {
    const res = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId: incomeCategoryId,
        description: 'tipo cruzado',
        amount: 10.0,
        paymentMethod: 'CASH',
        type: 'SPENT',
      }),
      params(token, EP.createTx, 400)
    );
    assert('categoria INCOME con type SPENT -> 400', res.status === 400, `${res.status} ${res.body}`);
  });

  group('validaciones de entrada -> 400', () => {
    const noAmount = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({ walletId, categoryId: spentCategoryId, paymentMethod: 'CASH', type: 'SPENT' }),
      params(token, EP.createTx, 400)
    );
    assert('sin amount -> 400', noAmount.status === 400, `${noAmount.status} ${noAmount.body}`);

    const negative = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId: spentCategoryId,
        amount: -5,
        paymentMethod: 'CASH',
        type: 'SPENT',
      }),
      params(token, EP.createTx, 400)
    );
    assert('amount negativo -> 400', negative.status === 400, `${negative.status} ${negative.body}`);
  });

  group('idempotencia: misma key no duplica', () => {
    const key = `idem_${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
    const body = JSON.stringify({
      walletId,
      categoryId: spentCategoryId,
      description: 'E2E ledger idempotente',
      amount: 25.0,
      paymentMethod: 'CASH',
      type: 'SPENT',
    });
    const headers = { ...params(token, EP.createTx).headers, 'Idempotency-Key': key };

    // Con BUG-1 corregido, la key se persiste una sola vez y el flujo funciona.
    const first = http.post(`${BASE_URL}/transactions`, body, {
      headers,
      tags: { endpoint: EP.createTx },
      responseCallback: http.expectedStatuses(200, 201, 400, 409),
    });
    statusOk('primer POST con Idempotency-Key -> 201', first, 201);
    const firstId = first.json('id');

    const second = http.post(`${BASE_URL}/transactions`, body, {
      headers,
      tags: { endpoint: EP.createTx },
      responseCallback: http.expectedStatuses(200, 201, 400, 409),
    });
    statusOk('segundo POST idempotente', second, 201);
    assert('misma key devuelve el mismo id', second.json('id') === firstId, `first=${firstId} second=${second.json('id')}`);

    const list = http.get(`${BASE_URL}/transactions?page=0&size=50&search=idempotente`, params(token, EP.listTx));
    statusOk('listar idempotentes', list, 200);
    assert(
      'solo hay una transaccion idempotente',
      (list.json('items') || []).length === 1,
      `hay ${(list.json('items') || []).length}`
    );

    // limpieza: la transaccion idempotente suma -25 al saldo.
  });

  group('deshabilitar transaccion revierte el saldo', () => {
    const before = walletBalance(token);
    const del = http.del(`${BASE_URL}/transactions/${txId}`, null, params(token, EP.deleteTx));
    statusOk('delete transaccion', del, 204);
    const after = walletBalance(token);
    assert('saldo se revierte (+150.75)', after === before + 150.75, `before=${before} after=${after}`);

    const got = http.get(`${BASE_URL}/transactions/${txId}`, params(token, EP.getTx));
    statusOk('get tras delete', got, 200);
    assert('transaccion quedo inactiva', got.json('active') === false, got.json('active'));
  });

  group('filtro active=false', () => {
    const inactive = http.get(
      `${BASE_URL}/transactions?active=false&page=0&size=50`,
      params(token, EP.listTx)
    );
    statusOk('listar inactivas', inactive, 200);
    assert('hay inactivas', (inactive.json('items') || []).length >= 1);
  });

  // sanity de fecha usada por otros flujos (evita imports sin uso)
  assert('today tiene formato ISO', /^\d{4}-\d{2}-\d{2}$/.test(today()));
}

export { handleSummary };
