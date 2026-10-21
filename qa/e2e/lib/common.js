import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// ---------------------------------------------------------------------------
// Libreria compartida de los e2e k6.
//
// Centraliza el bootstrap de usuario, las aserciones, los headers, el manejo
// de fechas y los tags de metricas por endpoint para que cada script de
// modulo se limite a describir el flujo funcional que verifica.
// ---------------------------------------------------------------------------

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080') + '/api/v1';

// Offset horario por defecto (America/Lima = UTC-5) para calcular fechas
// "hoy"/"ayer" alineadas con la zona del usuario sembrado en el backend.
const TZ_OFFSET_MINUTES = parseInt(__ENV.TZ_OFFSET_MINUTES || '-300', 10);

// Metricas de negocio compartidas por todos los e2e.
export const e2eDuration = new Trend('e2e_duration', true);
export const e2eFailRate = new Rate('e2e_failed');
export const e2eAssertions = new Counter('e2e_assertions');

// Fallos de aserciones `expect` (comportamientos conocidos como defectuosos,
// documentados en KNOWN-ISSUES.md). No cruzan el threshold que rompe la suite,
// pero quedan medidos para el reporte.
export const e2eKnownIssues = new Rate('e2e_known_issues');

// ---------------------------------------------------------------------------
// Fechas
// ---------------------------------------------------------------------------

export function today() {
  return new Date(Date.now() + TZ_OFFSET_MINUTES * 60 * 1000).toISOString().slice(0, 10);
}

export function daysAgo(n) {
  return new Date(Date.now() + TZ_OFFSET_MINUTES * 60 * 1000 - n * 24 * 60 * 60 * 1000)
    .toISOString()
    .slice(0, 10);
}

export function daysFromNow(n) {
  return new Date(Date.now() + TZ_OFFSET_MINUTES * 60 * 1000 + n * 24 * 60 * 60 * 1000)
    .toISOString()
    .slice(0, 10);
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

export function jsonHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

// Callback base: 2xx y 3xx (redirects, p.ej. GET /images/{id} -> 302) cuentan
// como esperados. Los 4xx/5xx que un test provoca a proposito deben declararse
// con `expected` para que no ensucien http_req_failed.
export const OK_CALLBACK = http.expectedStatuses({ min: 200, max: 399 });

// params(token, endpoint [, expected]) donde `expected` es un status concreto
// o un array de statuses que el test considera respuestas validas (400, 401...).
// Ej: params(token, EP.login, 401) para un login invalido intencional.
export function params(token, endpoint, expected) {
  const p = { headers: jsonHeaders(token), tags: { endpoint }, responseCallback: OK_CALLBACK };
  if (expected !== undefined && expected !== null) {
    const codes = Array.isArray(expected) ? expected : [expected];
    const accepted = codes.every((c) => c >= 200 && c <= 399);
    p.responseCallback = accepted ? OK_CALLBACK : http.expectedStatuses(...codes, { min: 200, max: 399 });
  }
  return p;
}

// params sin autenticacion (para probar 401 / endpoints publicos).
export function anonParams(endpoint, expected) {
  const p = { headers: { 'Content-Type': 'application/json' }, tags: { endpoint }, responseCallback: OK_CALLBACK };
  if (expected !== undefined && expected !== null) {
    const codes = Array.isArray(expected) ? expected : [expected];
    const accepted = codes.every((c) => c >= 200 && c <= 399);
    p.responseCallback = accepted ? OK_CALLBACK : http.expectedStatuses(...codes, { min: 200, max: 399 });
  }
  return p;
}

// ---------------------------------------------------------------------------
// Aserciones
// ---------------------------------------------------------------------------

// `assert` aborta el escenario (throw) si la condicion falla; registra la
// metrica e2e_failed y un check de k6 para que el reporte HTML la liste.
export function assert(name, ok, detail) {
  e2eAssertions.add(1);
  e2eFailRate.add(ok ? 0 : 1);
  check(null, { [name]: () => ok });
  if (!ok) {
    throw new Error(`ASSERT FAILED: ${name}${detail ? ' -> ' + detail : ''}`);
  }
}

// Variante que NO aborta: registra el resultado como "known issue" (comportamiento
// documentado en KNOWN-ISSUES.md) sin cruzar el threshold de fallo de la suite.
export function expect(name, ok, detail) {
  e2eAssertions.add(1);
  e2eKnownIssues.add(ok ? 0 : 1);
  check(null, { [name]: () => ok });
  if (!ok && detail) {
    console.warn(`KNOWN ISSUE: ${name} -> ${detail}`);
  }
  return ok;
}

export function statusOk(name, res, expected) {
  const codes = Array.isArray(expected) ? expected : [expected];
  return assert(
    `${name} [${codes.join('/')}]`,
    codes.includes(res.status),
    `${res.status} ${truncate(res.body)}`
  );
}

export function truncate(body, max = 400) {
  if (body === null || body === undefined) return '';
  const s = String(body);
  return s.length > max ? s.slice(0, max) + '...' : s;
}

// ---------------------------------------------------------------------------
// Bootstrap: crea un usuario fresco y lo deja listo para operar
// ---------------------------------------------------------------------------

export function uniqueEmail(prefix = 'e2e') {
  const rnd = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  return `${prefix}_${rnd}@e2e.local`;
}

// Registra un usuario, hace login, crea sus preferencias y su billetera
// principal. Devuelve el contexto minimo para operar con la API.
//
// opts.currencyCode  moneda base (default 'PEN')
// opts.withWallet    crear la billetera principal (default true)
// opts.password      password (default 'Test1234!')
export function bootstrapUser(endpoints, opts = {}) {
  const password = opts.password || 'Test1234!';
  const currencyCode = opts.currencyCode || 'PEN';
  const email = opts.email || uniqueEmail(opts.prefix || 'e2e');

  const register = http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ name: 'E2E', lastname: 'Suite', email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: endpoints.register },
      responseCallback: http.expectedStatuses(200, 201, 400, 409),
    }
  );
  statusOk('register', register, [200, 201]);

  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: endpoints.login },
      responseCallback: http.expectedStatuses(200, 401, 400),
    }
  );
  statusOk('login', login, 200);
  const token = login.json('accessToken');
  const refreshToken = login.json('refreshToken');
  assert('login devuelve accessToken', typeof token === 'string' && token.length > 0);

  const me = http.get(`${BASE_URL}/users/me`, params(token, endpoints.me));
  statusOk('users/me', me, 200);
  const userId = me.json('id');
  assert('users/me devuelve id', !!userId);

  if (opts.withPreferences !== false) {
    const prefs = http.post(
      `${BASE_URL}/users/me/preferences`,
      JSON.stringify({ timeZone: 'AMERICA_LIMA', currencyCode, emailNotifications: false }),
      params(token, endpoints.prefs)
    );
    statusOk('preferences', prefs, 201);
  }

  let walletId = null;
  let wallet = null;
  if (opts.withWallet !== false) {
    const created = http.post(
      `${BASE_URL}/wallets`,
      JSON.stringify({ currencyCode, primary: true }),
      params(token, endpoints.createWallet)
    );
    statusOk('wallet', created, 201);
    walletId = created.json('id');
    wallet = created.json();
  }

  return { email, password, token, refreshToken, userId, walletId, wallet, currencyCode };
}

// Devuelve una categoria del usuario por tipo (INCOME/SPENT); opcionalmente
// exige un nombre concreto.
export function findCategory(token, endpoints, type, name) {
  const res = http.get(`${BASE_URL}/categories?page=0&size=100`, params(token, endpoints.listCategories));
  statusOk('listar categorias', res, 200);
  const items = res.json('items') || [];
  if (name) {
    return items.find((c) => c.type === type && c.name === name) || null;
  }
  return items.find((c) => c.type === type) || null;
}

// ---------------------------------------------------------------------------
// Polling
// ---------------------------------------------------------------------------

// Reintenta `fn` hasta que devuelva un valor "truthy" o se agote el timeout.
// Util para esperar efectos asincronos (notificaciones por RabbitMQ, saldos).
export function pollUntil(fn, { timeoutSeconds = 20, intervalSeconds = 2 } = {}) {
  const deadline = Date.now() + timeoutSeconds * 1000;
  let last;
  while (Date.now() < deadline) {
    last = fn();
    if (last) return last;
    sleep(intervalSeconds);
  }
  return last;
}

// ---------------------------------------------------------------------------
// Thresholds / opciones / resumen
// ---------------------------------------------------------------------------

// Construye thresholds por endpoint a partir de un mapa { nombre: 'VERBO /path' }.
export function endpointThresholds(endpoints, { latency = 2000 } = {}) {
  const out = {};
  Object.values(endpoints).forEach((name) => {
    out[`http_req_duration{endpoint:${name}}`] = [`p(95)<${latency}`];
    out[`http_req_failed{endpoint:${name}}`] = ['rate<0.01'];
  });
  return out;
}

// Opciones estandar de un e2e: 1 VU, 1 iteracion, thresholds estrictos.
export function e2eOptions(endpoints, { maxDuration = '5m', label = 'e2e', latency } = {}) {
  return {
    scenarios: {
      [label]: {
        executor: 'shared-iterations',
        vus: 1,
        iterations: 1,
        maxDuration: __ENV.MAX_DURATION || maxDuration,
      },
    },
    thresholds: {
      // Solo las aserciones estrictas (`assert`) cruzan este threshold.
      // Los comportamientos conocidos como defectuosos usan `expect` y se
      // miden aparte en e2e_known_issues (ver KNOWN-ISSUES.md).
      e2e_failed: ['rate==0'],
      ...endpointThresholds(endpoints, { latency }),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  };
}

// handleSummary compatible con run-e2e.js (escribe el JSON en SUMMARY_FILE).
export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_FILE || 'reports/e2e_summary.json';
  return {
    [jsonPath]: JSON.stringify(data),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

export { http, check, fail, sleep };
