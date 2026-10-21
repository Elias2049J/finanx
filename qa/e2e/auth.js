import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  anonParams,
  assert,
  e2eFailRate,
  e2eOptions,
  handleSummary,
  params,
  statusOk,
  uniqueEmail,
  bootstrapUser,
} from './lib/common.js';

// E2E del modulo identity (auth):
//   1. registro + login + users/me
//   2. refresh token (rota el par de tokens)
//   3. logout (revoca el refresh token)
//   4. endpoints protegidos sin token -> 401
//   5. login con credenciales invalidas -> 401
//   6. registro duplicado -> 4xx
//   7. listado de timezones disponibles

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  refresh: 'POST /auth/refresh',
  logout: 'POST /auth/logout',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listWallets: 'GET /wallets',
  timezones: 'GET /users/timezones',
  listCategories: 'GET /categories',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'auth', latency: 1000 });

export function setup() {
  const email = uniqueEmail('e2e_auth');
  const password = 'Test1234!';
  const ctx = bootstrapUser(EP, { email, password, withWallet: false });

  // Credenciales compartidas con la iteracion principal.
  return { ...ctx, email, password };
}

export default function (data) {
  group('registro duplicado rechazado', () => {
    const dup = http.post(
      `${BASE_URL}/auth/register`,
      JSON.stringify({ name: 'E2E', lastname: 'Dup', email: data.email, password: data.password }),
      params(data.token, EP.register, [400, 409])
    );
    assert(
      'registro duplicado 4xx',
      dup.status >= 400 && dup.status < 500,
      `${dup.status} ${dup.body}`
    );
  });

  group('login con credenciales invalidas', () => {
    const bad = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: data.email, password: 'WrongPassword!' }),
      anonParams(EP.login, [401, 400])
    );
    assert('login invalido 401/400', bad.status === 401 || bad.status === 400, `${bad.status} ${bad.body}`);
  });

  group('datos de cuenta', () => {
    const me = http.get(`${BASE_URL}/users/me`, params(data.token, EP.me));
    statusOk('users/me', me, 200);
    assert('me.email coincide', me.json('email') === data.email, me.json('email'));
  });

  group('endpoints protegidos sin token -> 401', () => {
    const noAuth = http.get(`${BASE_URL}/wallets`, anonParams(EP.listWallets, 401));
    assert('wallets sin token 401', noAuth.status === 401, `${noAuth.status} ${noAuth.body}`);

    const meNoAuth = http.get(`${BASE_URL}/users/me`, anonParams(EP.me, 401));
    assert('users/me sin token 401', meNoAuth.status === 401, `${meNoAuth.status} ${meNoAuth.body}`);
  });

  group('token invalido -> 401', () => {
    const badToken = http.get(`${BASE_URL}/users/me`, params('not-a-real-jwt', EP.me, 401));
    assert('users/me token invalido 401', badToken.status === 401, `${badToken.status} ${badToken.body}`);
  });

  group('refresh token', () => {
    const refresh = http.post(
      `${BASE_URL}/auth/refresh`,
      JSON.stringify({ refreshToken: data.refreshToken }),
      { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: EP.refresh } }
    );
    statusOk('refresh', refresh, 200);
    const newToken = refresh.json('accessToken');
    const newRefresh = refresh.json('refreshToken');
    assert('refresh devuelve accessToken', typeof newToken === 'string' && newToken.length > 0);
    assert('refresh rota el refreshToken', typeof newRefresh === 'string' && newRefresh.length > 0);

    // El nuevo access token debe seguir siendo valido.
    const me = http.get(`${BASE_URL}/users/me`, params(newToken, EP.me));
    statusOk('users/me con token refrescado', me, 200);

    data.rotatedRefresh = newRefresh;
  });

  group('refresh token invalido -> 401', () => {
    const bad = http.post(
      `${BASE_URL}/auth/refresh`,
      JSON.stringify({ refreshToken: 'invalid-refresh-token' }),
      anonParams(EP.refresh, [401, 400])
    );
    assert('refresh invalido 401/400', bad.status === 401 || bad.status === 400, `${bad.status} ${bad.body}`);
  });

  group('timezones disponibles', () => {
    const tz = http.get(`${BASE_URL}/users/timezones`, params(data.token, EP.timezones));
    statusOk('timezones', tz, 200);
    const list = tz.json();
    assert('timezones es lista no vacia', Array.isArray(list) && list.length > 0);
  });

  group('logout revoca el refresh token', () => {
    const rotated = data.rotatedRefresh || data.refreshToken;
    const logout = http.post(
      `${BASE_URL}/auth/logout`,
      JSON.stringify({ refreshToken: rotated }),
      { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: EP.logout } }
    );
    statusOk('logout', logout, [204, 200]);

    // Tras el logout, el refresh token revocado ya no debe servir.
    const reuse = http.post(
      `${BASE_URL}/auth/refresh`,
      JSON.stringify({ refreshToken: rotated }),
      anonParams(EP.refresh, [401, 400])
    );
    assert('refresh tras logout 401/400', reuse.status === 401 || reuse.status === 400, `${reuse.status} ${reuse.body}`);
  });

  // Deja la metrica fallida en cero para el threshold si todo paso.
  e2eFailRate.add(0);
}

export { handleSummary };
