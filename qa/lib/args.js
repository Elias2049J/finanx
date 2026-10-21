// ---------------------------------------------------------------------------
// Parser de argumentos CLI para los runners de QA.
//
// Extrae la URL base del backend de `process.argv`, aceptando:
//   node run-stress.js concurrency --base-url https://host
//   node run-stress.js concurrency https://host
//   node run-stress.js --base-url=https://host concurrency
//
// Devuelve:
//   { positional: ['concurrency', ...], baseUrl: 'https://host' | undefined }
//
// La deteccion de URL no depende de un esquema obligatorio: un solo token que
// parezca host (con punto, o localhost/ip) cuenta como baseUrl si no se paso
// como flag.
// ---------------------------------------------------------------------------

function looksLikeUrl(token) {
  if (!token) return false;
  if (/^https?:\/\//i.test(token)) return true;
  // host o host:puerto (localhost, 127.0.0.1:8080, ejemplo.com, sub.dom.com:443)
  return /^(localhost|(\d{1,3}\.){3}\d{1,3}|\[[0-9a-fA-F:]+\]|[a-z0-9-]+(\.[a-z0-9-]+)+)(:\d+)?(\/.*)?$/i.test(
    token
  );
}

function parseArgs(argv) {
  const positional = [];
  let baseUrl;

  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];

    if (token === '--base-url' || token === '--url') {
      baseUrl = argv[++i];
      continue;
    }
    if (token.startsWith('--base-url=')) {
      baseUrl = token.slice('--base-url='.length);
      continue;
    }
    if (token.startsWith('--url=')) {
      baseUrl = token.slice('--url='.length);
      continue;
    }
    if (token.startsWith('-e') || token.startsWith('--env')) {
      // Ignora overrides de env que no nos interesan aqui.
      if (token === '-e' || token === '--env') i++;
      continue;
    }
    if (token.startsWith('--')) {
      continue;
    }

    // URL sin flag: solo la primera que parezca host.
    if (baseUrl === undefined && looksLikeUrl(token)) {
      baseUrl = token;
      continue;
    }

    positional.push(token);
  }

  return { positional, baseUrl };
}

module.exports = { parseArgs, looksLikeUrl };
