import http from 'k6/http';
import { group } from 'k6';
import {
  BASE_URL,
  assert,
  bootstrapUser,
  e2eOptions,
  handleSummary,
  params,
  statusOk,
} from './lib/common.js';

// E2E del modulo catalog (categorias y motivos):
//   1. categorias sembradas por defecto al registrarse (9)
//   2. CRUD de categoria + disable
//   3. CRUD de motivo + disable
//   4. filtros de listado (type, active, search)
//   5. validaciones de entrada -> 400

const EP = {
  register: 'POST /auth/register',
  login: 'POST /auth/login',
  me: 'GET /users/me',
  prefs: 'POST /users/me/preferences',
  createWallet: 'POST /wallets',
  listCategories: 'GET /categories',
  getCategory: 'GET /categories/{id}',
  createCategory: 'POST /categories',
  updateCategory: 'PUT /categories/{id}',
  disableCategory: 'POST /categories/{id}/disable',
  listReasons: 'GET /reasons',
  getReason: 'GET /reasons/{id}',
  createReason: 'POST /reasons',
  updateReason: 'PUT /reasons/{id}',
  disableReason: 'POST /reasons/{id}/disable',
};

export const options = e2eOptions(EP, { maxDuration: '1m', label: 'catalog', latency: 1000 });

export function setup() {
  return bootstrapUser(EP);
}

export default function (data) {
  const { token, userId } = data;

  group('categorias sembradas por defecto', () => {
    const res = http.get(`${BASE_URL}/categories?page=0&size=100`, params(token, EP.listCategories));
    statusOk('listar categorias', res, 200);
    const items = res.json('items') || [];
    assert('hay 9 categorias por defecto', items.length === 9, `esperadas 9, hay ${items.length}`);
    assert('incluye Alimentacion', items.some((c) => c.name === 'Alimentacion'));
    assert('incluye Salario (INCOME)', items.some((c) => c.name === 'Salario' && c.type === 'INCOME'));
  });

  let categoryId;
  group('CRUD de categoria', () => {
    const created = http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ name: 'E2E Cat', description: 'categoria e2e', userId, type: 'SPENT' }),
      params(token, EP.createCategory)
    );
    statusOk('crear categoria', created, 201);
    categoryId = created.json('id');
    assert('categoria tiene id', !!categoryId);
    assert('categoria activa', created.json('active') === true);

    const got = http.get(`${BASE_URL}/categories/${categoryId}`, params(token, EP.getCategory));
    statusOk('get categoria', got, 200);
    assert('nombre coincide', got.json('name') === 'E2E Cat', got.json('name'));

    const updated = http.put(
      `${BASE_URL}/categories/${categoryId}`,
      JSON.stringify({ name: 'E2E Cat v2', description: 'actualizada', userId, type: 'SPENT' }),
      params(token, EP.updateCategory)
    );
    statusOk('actualizar categoria', updated, 200);
    assert('nombre actualizado', updated.json('name') === 'E2E Cat v2', updated.json('name'));

    const disabled = http.post(
      `${BASE_URL}/categories/${categoryId}/disable`,
      null,
      params(token, EP.disableCategory)
    );
    statusOk('deshabilitar categoria', disabled, 204);
    const afterDisable = http.get(`${BASE_URL}/categories/${categoryId}`, params(token, EP.getCategory));
    statusOk('get categoria deshabilitada', afterDisable, 200);
    assert('categoria inactiva', afterDisable.json('active') === false, afterDisable.json('active'));
  });

  group('filtros de categorias', () => {
    const byType = http.get(
      `${BASE_URL}/categories?type=INCOME&page=0&size=100`,
      params(token, EP.listCategories)
    );
    statusOk('filtrar categorias por tipo', byType, 200);
    assert('filtro INCOME todos coinciden', (byType.json('items') || []).every((c) => c.type === 'INCOME'));

    const byName = http.get(
      `${BASE_URL}/categories?search=Alimentacion&page=0&size=100`,
      params(token, EP.listCategories)
    );
    statusOk('buscar categoria', byName, 200);
    assert('search encuentra Alimentacion', (byName.json('items') || []).some((c) => c.name === 'Alimentacion'));
  });

  group('validaciones de categoria -> 400', () => {
    const noName = http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ description: 'sin nombre', userId, type: 'SPENT' }),
      params(token, EP.createCategory, 400)
    );
    assert('sin name -> 400', noName.status === 400, `${noName.status} ${noName.body}`);

    const noType = http.post(
      `${BASE_URL}/categories`,
      JSON.stringify({ name: 'sin tipo', description: 'x', userId }),
      params(token, EP.createCategory, 400)
    );
    assert('sin type -> 400', noType.status === 400, `${noType.status} ${noType.body}`);
  });

  let reasonId;
  group('CRUD de motivo', () => {
    const created = http.post(
      `${BASE_URL}/reasons`,
      JSON.stringify({ description: 'E2E motivo', userId }),
      params(token, EP.createReason)
    );
    statusOk('crear motivo', created, 201);
    reasonId = created.json('id');
    assert('motivo tiene id', !!reasonId);

    const got = http.get(`${BASE_URL}/reasons/${reasonId}`, params(token, EP.getReason));
    statusOk('get motivo', got, 200);
    assert(
      'descripcion coincide (case-insensitive)',
      String(got.json('description')).toLowerCase() === 'e2e motivo',
      got.json('description')
    );

    const updated = http.put(
      `${BASE_URL}/reasons/${reasonId}`,
      JSON.stringify({ description: 'E2E motivo v2', userId }),
      params(token, EP.updateReason)
    );
    statusOk('actualizar motivo', updated, 200);
    assert(
      'descripcion actualizada (case-insensitive)',
      String(updated.json('description')).toLowerCase() === 'e2e motivo v2',
      updated.json('description')
    );

    const disabled = http.post(`${BASE_URL}/reasons/${reasonId}/disable`, null, params(token, EP.disableReason));
    statusOk('deshabilitar motivo', disabled, 204);
    const afterDisable = http.get(`${BASE_URL}/reasons/${reasonId}`, params(token, EP.getReason));
    statusOk('get motivo deshabilitado', afterDisable, 200);
    assert('motivo inactivo', afterDisable.json('active') === false, afterDisable.json('active'));
  });

  group('listar motivos activos', () => {
    const res = http.get(`${BASE_URL}/reasons?page=0&size=100`, params(token, EP.listReasons));
    statusOk('listar motivos', res, 200);
    assert('motivos es lista', Array.isArray(res.json('items')));
  });

  group('validaciones de motivo -> 400', () => {
    const noDesc = http.post(
      `${BASE_URL}/reasons`,
      JSON.stringify({ userId }),
      params(token, EP.createReason, 400)
    );
    assert('motivo sin description -> 400', noDesc.status === 400, `${noDesc.status} ${noDesc.body}`);
  });
}

export { handleSummary };
