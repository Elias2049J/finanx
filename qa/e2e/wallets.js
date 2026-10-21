import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  assert,
  bootstrapUser,
  e2eOptions,
  expect,
  handleSummary,
  params,
  statusOk,
} from './lib/common.js';

// E2E del modulo wallets:
//   1. la billetera principal se puede crear y listar
//   2. un usuario FREE no puede crear una segunda billetera (gating por plan)
//   3. obtener billetera por id
//   4. validaciones de entrada -> 400 (codigo de moneda invalido)
//   5. aislamiento: otro usuario no ve mi billetera
//
// KNOWN BUG (BUG-2): al registrar un usuario se le asigna una suscripcion
// PREMIUM ACTIVE, por lo que el gating FREE no bloquea la segunda billetera.
// Ver qa/e2e/KNOWN-ISSUES.md.

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listWallets: 'GET /wallets',
  getWallet: 'GET /wallets/{id}',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'wallets', latency: 1000 });

export function setup() {
  return bootstrapUser(EP);
}

export default function (data) {
  const { token, walletId } = data;

  group('billetera principal creada', () => {
    const res = http.get(`${BASE_URL}/wallets?page=0&size=50`, params(token, EP.listWallets));
    statusOk('listar wallets', res, 200);
    const items = res.json('items') || [];
    assert('hay 1 billetera (limite FREE)', items.length === 1, `hay ${items.length}`);
    assert('es la principal', items[0].primary === true, items[0].primary);
    assert('moneda PEN', items[0].currencyCode === 'PEN', items[0].currencyCode);
  });

  group('obtener billetera por id', () => {
    const res = http.get(`${BASE_URL}/wallets/${walletId}`, params(token, EP.getWallet));
    statusOk('get wallet', res, 200);
    assert('id coincide', res.json('id') === walletId, res.json('id'));
    assert('saldo 0', Number(res.json('balance')) === 0, res.json('balance'));
  });

  group('usuario FREE no puede crear segunda billetera', () => {
    const res = http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'USD', primary: false }),
      params(token, EP.createWallet, [201, 403, 409, 400])
    );
    // KNOWN BUG (BUG-2): el plan por defecto es PREMIUM, asi que la 2da
    // billetera se crea (201) en lugar de rechazarse.
    expect(
      'segunda billetera rechazada en FREE (403/409/400)',
      [403, 409, 400].includes(res.status),
      `${res.status} ${res.body} (BUG-2 plan por defecto PREMIUM)`
    );
  });

  group('validaciones de entrada -> 400', () => {
    const res = http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'PESOS', primary: false }),
      params(token, EP.createWallet, 400)
    );
    assert('moneda no ISO 4217 -> 400', res.status === 400, `${res.status} ${res.body}`);
  });

  group('aislamiento entre usuarios', () => {
    const other = bootstrapUser(EP, { prefix: 'e2e_wallets_other' });
    const res = http.get(`${BASE_URL}/wallets?page=0&size=50`, params(other.token, EP.listWallets));
    statusOk('otro usuario lista wallets', res, 200);
    const items = res.json('items') || [];
    assert('el otro usuario no ve mi billetera', !items.some((w) => w.id === walletId), JSON.stringify(items));
  });
}

export { handleSummary };
