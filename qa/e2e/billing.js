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

// E2E del modulo billing + gating de plan:
//   1. el plan actual por defecto es FREE
//   2. crear (PENDING) y activar suscripcion PREMIUM
//   3. el plan activo pasa a PREMIUM
//   4. una feature premium (segunda billetera) se desbloquea
//   5. cancelar la suscripcion
//   6. crear y refundar un pago
//   7. webhook: firma invalida -> 401
//
// KNOWN BUG (BUG-2): al registrar un usuario se le asigna automaticamente una
// suscripcion PREMIUM ACTIVE (BillingListener), asi que no existe el estado
// FREE por defecto. Ver qa/e2e/KNOWN-ISSUES.md.

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  currentSub: 'GET /subscriptions/current',
  createSub: 'POST /subscriptions',
  activateSub: 'POST /subscriptions/{id}/activate',
  cancelSub: 'POST /subscriptions/{id}/cancel',
  listSubs: 'GET /subscriptions',
  createPayment: 'POST /payments',
  getPayment: 'GET /payments/{id}',
  refundPayment: 'POST /payments/{id}/refund',
  webhook: 'POST /webhooks/{provider}',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'billing', latency: 2000 });

export function setup() {
  return bootstrapUser(EP);
}

export default function (data) {
  const { token, userId } = data;

  group('plan por defecto deberia ser FREE', () => {
    // BUG-2: se espera que sin suscripcion activa /subscriptions/current de 404.
    const res = http.get(`${BASE_URL}/subscriptions/current`, params(token, EP.currentSub, [200, 404]));
    expect(
      'sin suscripcion -> 404',
      res.status === 404,
      `${res.status} ${res.body} (BUG-2: plan auto-asignado al registrar)`
    );
    if (res.status === 200) {
      // Deja constancia del plan realmente asignado por defecto.
      expect(
        'plan auto-asignado deberia ser FREE',
        res.json('plan') === 'FREE',
        `plan=${res.json('plan')} (BUG-2)`
      );
    }
  });

  group('segunda billetera bloqueada en FREE', () => {
    const res = http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'USD', primary: false }),
      params(token, EP.createWallet, [201, 403, 409, 400])
    );
    // KNOWN BUG (BUG-2): el usuario ya es PREMIUM, por lo que se permite (201).
    expect(
      'FREE no crea 2da billetera',
      [403, 409, 400].includes(res.status),
      `${res.status} ${res.body} (BUG-2)`
    );
  });

  let subscriptionId;
  group('crear y activar suscripcion PREMIUM', () => {
    // BUG-2: puede haber ya una suscripcion auto-asignada; la usamos si existe.
    const current = http.get(`${BASE_URL}/subscriptions/current`, params(token, EP.currentSub, [200, 404]));
    if (current.status === 200) {
      subscriptionId = current.json('id');
      assert('suscripcion activa reutilizada', !!subscriptionId, JSON.stringify(current.json()));
    } else {
      const created = http.post(
        `${BASE_URL}/subscriptions`,
        JSON.stringify({
          userId,
          plan: 'PREMIUM',
          billingCycle: 'MONTHLY',
          amount: 29.9,
          currency: 'PEN',
          autoRenew: true,
        }),
        params(token, EP.createSub)
      );
      statusOk('crear suscripcion', created, 201);
      subscriptionId = created.json('id');
      assert('suscripcion tiene id', !!subscriptionId);
      assert('estado PENDING', created.json('state') === 'PENDING', created.json('state'));

      const activated = http.post(
        `${BASE_URL}/subscriptions/${subscriptionId}/activate`,
        null,
        params(token, EP.activateSub)
      );
      statusOk('activar suscripcion', activated, 200);
      assert('estado ACTIVE', activated.json('state') === 'ACTIVE', activated.json('state'));
      assert('periodo definido', !!activated.json('currentPeriodEnd'), JSON.stringify(activated.json()));
    }
  });

  group('plan actual es PREMIUM', () => {
    const res = http.get(`${BASE_URL}/subscriptions/current`, params(token, EP.currentSub));
    statusOk('suscripcion actual', res, 200);
    assert('plan PREMIUM', res.json('plan') === 'PREMIUM', res.json('plan'));
  });

  group('feature premium desbloqueada (2da billetera)', () => {
    const res = http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode: 'EUR', primary: false }),
      params(token, EP.createWallet, [201, 409, 403, 400])
    );
    // Con PREMIUM la 2da billetera debe crearse; 409 solo si ya existe esa moneda.
    assert(
      'PREMIUM crea 2da billetera',
      res.status === 201,
      `${res.status} ${res.body}`
    );
    if (res.status === 201) {
      assert('moneda EUR', res.json('currencyCode') === 'EUR', res.json('currencyCode'));
    }
  });

  group('listar suscripciones', () => {
    const res = http.get(`${BASE_URL}/subscriptions?page=0&size=50`, params(token, EP.listSubs));
    statusOk('listar suscripciones', res, 200);
    assert('aparece la suscripcion', (res.json('items') || []).some((s) => s.id === subscriptionId));
  });

  group('crear pago y refundar', () => {
    const created = http.post(
      `${BASE_URL}/payments`,
      JSON.stringify({
        userId,
        subscriptionId,
        amount: 29.9,
        currency: 'PEN',
        provider: 'STRIPE',
        idempotencyKey: `pay_${Date.now()}`,
      }),
      params(token, EP.createPayment)
    );
    statusOk('crear pago', created, 201);
    const paymentId = created.json('id');
    assert('pago tiene id', !!paymentId);
    assert('estado PENDING', created.json('state') === 'PENDING', created.json('state'));

    const got = http.get(`${BASE_URL}/payments/${paymentId}`, params(token, EP.getPayment));
    statusOk('get pago', got, 200);

    const refund = http.post(
      `${BASE_URL}/payments/${paymentId}/refund`,
      null,
      params(token, EP.refundPayment, 409)
    );
    // Un pago PENDING no puede pasar directo a REFUNDED -> 409 esperado.
    assert('refund de PENDING -> 409', refund.status === 409, `${refund.status} ${refund.body}`);
  });

  group('webhook con firma invalida -> 401', () => {
    const res = http.post(
      `${BASE_URL}/webhooks/stripe`,
      JSON.stringify({ id: 'evt_test', type: 'payment.completed' }),
      {
        headers: { 'Content-Type': 'application/json', 'X-Signature': 'invalid' },
        tags: { endpoint: EP.webhook },
        responseCallback: http.expectedStatuses(200, 202, 401),
      }
    );
    assert('webhook firma invalida -> 401', res.status === 401, `${res.status} ${res.body}`);
  });

  group('cancelar suscripcion', () => {
    const res = http.post(
      `${BASE_URL}/subscriptions/${subscriptionId}/cancel`,
      null,
      params(token, EP.cancelSub)
    );
    statusOk('cancelar suscripcion', res, 200);
    assert('estado CANCELED', res.json('state') === 'CANCELED', res.json('state'));
    assert('autoRenew false', res.json('autoRenew') === false, res.json('autoRenew'));
  });
}

export { handleSummary };
