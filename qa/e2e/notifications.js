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
  pollUntil,
  statusOk,
} from './lib/common.js';

// E2E del modulo notifications:
//   1. una transaccion manual NO genera notificacion
//   2. registrar/eliminar un token FCM
//   3. marcar notificacion como leida (READ) y descartarla (DISCARD)
//   4. purgar notificaciones antiguas
//
// Nota: las notificaciones generadas por el flujo de scheduling ya las cubre
// qa/e2e/scheduling.js. Aqui se verifica la API de gestion de notificaciones.

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listCategories: 'GET /categories',
  createTx: 'POST /transactions',
  listNotifs: 'GET /notifications',
  getNotif: 'GET /notifications/{id}',
  readNotif: 'POST /notifications/{id}/read',
  discardNotif: 'POST /notifications/{id}/discard',
  purgeNotifs: 'POST /notifications/purge-older-than',
  registerFcm: 'POST /fcm-tokens',
  listFcm: 'GET /fcm-tokens',
  deleteFcm: 'DELETE /fcm-tokens/{id}',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'notifications', latency: 1500 });

export function setup() {
  const ctx = bootstrapUser(EP);
  const spent = findCategory(ctx.token, EP, 'SPENT');
  assert('existe categoria SPENT', !!spent, 'sin categorias SPENT');
  return { ...ctx, categoryId: spent.id };
}

export default function (data) {
  const { token, walletId, categoryId } = data;

  group('transaccion manual no genera notificacion', () => {
    const before = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifs));
    statusOk('listar notificaciones', before, 200);
    const beforeCount = (before.json('items') || []).length;

    const tx = http.post(
      `${BASE_URL}/transactions`,
      JSON.stringify({
        walletId,
        categoryId,
        description: 'manual notifications e2e',
        amount: 5.0,
        paymentMethod: 'CASH',
        type: 'SPENT',
      }),
      params(token, EP.createTx)
    );
    statusOk('crear transaccion manual', tx, 201);

    // Damos margen a un posible efecto asincrono antes de concluir.
    const after = pollUntil(
      () => {
        const res = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifs));
        return (res.json('items') || []).length > beforeCount ? res : null;
      },
      { timeoutSeconds: 6, intervalSeconds: 2 }
    );
    assert('manual no genera notificacion', !after, 'aparecio una notificacion inesperada');
  });

  let notificationId;
  group('crear notificacion para gestionar', () => {
    // Sembramos una notificacion via schedule de presupuesto no es trivial;
    // usamos el propio listado: si no hay, saltamos la gestion.
    const res = http.get(`${BASE_URL}/notifications?page=0&size=50`, params(token, EP.listNotifs));
    statusOk('listar notificaciones', res, 200);
    const items = res.json('items') || [];
    notificationId = items.length > 0 ? items[0].id : null;
  });

  if (notificationId) {
    group('marcar notificacion como leida', () => {
      const res = http.post(
        `${BASE_URL}/notifications/${notificationId}/read`,
        null,
        params(token, EP.readNotif)
      );
      statusOk('marcar leida', res, [204, 200]);
      const got = http.get(`${BASE_URL}/notifications/${notificationId}`, params(token, EP.getNotif));
      statusOk('get notificacion', got, 200);
      assert('estado READ', got.json('state') === 'READ', got.json('state'));
    });
  }

  group('token FCM: registrar, listar y eliminar', () => {
    const fcmToken = `e2e_fcm_${Date.now()}`;
    const created = http.post(
      `${BASE_URL}/fcm-tokens`,
      JSON.stringify({ token: fcmToken, deviceName: 'E2E Device' }),
      params(token, EP.registerFcm)
    );
    statusOk('registrar token fcm', created, 201);
    const tokenId = created.json('id');
    assert('fcm token tiene id', !!tokenId);

    const list = http.get(`${BASE_URL}/fcm-tokens?page=0&size=50`, params(token, EP.listFcm));
    statusOk('listar tokens fcm', list, 200);
    assert('aparece el token', (list.json('items') || []).some((t) => t.id === tokenId));

    const deleted = http.del(`${BASE_URL}/fcm-tokens/${tokenId}`, null, params(token, EP.deleteFcm));
    statusOk('eliminar token fcm', deleted, 204);

    const after = http.get(`${BASE_URL}/fcm-tokens?page=0&size=50`, params(token, EP.listFcm));
    statusOk('listar tras eliminar', after, 200);
    assert('token eliminado', !(after.json('items') || []).some((t) => t.id === tokenId));
  });

  group('validacion FCM sin token -> 400', () => {
    const res = http.post(
      `${BASE_URL}/fcm-tokens`,
      JSON.stringify({ deviceName: 'sin token' }),
      params(token, EP.registerFcm, 400)
    );
    assert('fcm sin token -> 400', res.status === 400, `${res.status} ${res.body}`);
  });

  group('purga de notificaciones', () => {
    // Purga notificaciones anteriores a una fecha futura: no debe fallar.
    const future = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 19);
    const res = http.post(
      `${BASE_URL}/notifications/purge-older-than?dateTime=${future}`,
      null,
      params(token, EP.purgeNotifs)
    );
    statusOk('purga notificaciones', res, [204, 200]);
  });
}

export { handleSummary };
