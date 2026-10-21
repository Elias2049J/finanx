// ---------------------------------------------------------------------------
// Normalizacion de BASE_URL para los escenarios de stress.
//
// Reglas:
//   - Sin esquema (p. ej. "127.0.0.1")  -> se asume http://
//   - Host local (localhost / 127.0.0.1 / 0.0.0.0 / ::1) SIN puerto -> :8080
//   - Cualquier otro host (ngrok, staging, ...) se respeta tal cual, con o sin
//     puerto. Ej.: https://a249-132-191-2-250.ngrok-free.app (sin puerto) queda
//     igual porque ngrok expone el 443.
//   - Se anexa el prefijo /api/v1 (el backend monta todo bajo esa ruta). Si la
//     URL ya lo trae, no se duplica.
//
// Uso:
//   import { resolveBaseUrl } from './lib/base-url.js';
//   const BASE_URL = resolveBaseUrl();          // -> http://localhost:8080/api/v1
// ---------------------------------------------------------------------------

const LOCAL_HOSTS = ['localhost', '127.0.0.1', '0.0.0.0', '[::1]', '::1'];

function hasPort(host) {
  // host puede ser "localhost", "127.0.0.1:8080" o "[::1]:8080".
  if (host.startsWith('[')) {
    // IPv6 entre corchetes: hay puerto si aparece ":<digits>" tras "]".
    return /^\[[^\]]+\]:\d+$/.test(host);
  }
  return /:\d+$/.test(host);
}

function isLocal(host) {
  const bare = host.replace(/:\d+$/, '').toLowerCase();
  return LOCAL_HOSTS.includes(bare);
}

export function resolveBaseUrl(raw) {
  let value = (raw || '').trim();

  if (value === '') {
    value = 'http://localhost:8080';
  }

  // Sin esquema: asumimos http.
  if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(value)) {
    value = `http://${value}`;
  }

  // Quita barra final para no generar "//".
  value = value.replace(/\/+$/, '');

  // Separa host del path (por si pasan algo como http://host/api/v1).
  const schemeEnd = value.indexOf('://') + 3;
  const rest = value.slice(schemeEnd);
  const slash = rest.indexOf('/');
  const host = slash === -1 ? rest : rest.slice(0, slash);
  const path = slash === -1 ? '' : rest.slice(slash);

  const normalized = isLocal(host) && !hasPort(host)
    ? `${value.slice(0, schemeEnd)}${host}:8080${path}`
    : value;

  // Anexa /api/v1 solo si aun no esta presente.
  return /\/api\/v1$/.test(normalized) ? normalized : `${normalized}/api/v1`;
}
