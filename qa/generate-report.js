const fs = require('fs');
const path = require('path');

const scenario = process.argv[2] || process.env.SCENARIO || 'load';
const reportsDir = path.resolve(__dirname, 'reports');
const jsonFile = path.resolve(reportsDir, `${scenario}_summary.json`);
const outDir = path.resolve(reportsDir, scenario);
const outFile = path.join(outDir, 'report.html');

if (!fs.existsSync(jsonFile)) {
  console.error(`No existe el resumen: ${jsonFile}`);
  process.exit(1);
}

const data = JSON.parse(fs.readFileSync(jsonFile, 'utf8'));
const metrics = data.metrics || {};

function value(metric, field) {
  if (metric && metric.values && metric.values[field] !== undefined) {
    return metric.values[field];
  }
  return null;
}

const ms = (v) => (v === null ? '-' : Number(v).toFixed(2));
const pct = (v) => (v === null ? '-' : (Number(v) * 100).toFixed(2) + '%');
const raw = (v) => (v === null ? '-' : v);
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const epDur = {};
const epFail = {};
const epReqs = {};
for (const key of Object.keys(metrics)) {
  let m;
  if ((m = key.match(/^http_req_duration\{endpoint:(.+)\}$/))) epDur[m[1]] = metrics[key];
  else if ((m = key.match(/^http_req_failed\{endpoint:(.+)\}$/))) epFail[m[1]] = metrics[key];
  else if ((m = key.match(/^http_reqs\{endpoint:(.+)\}$/))) epReqs[m[1]] = metrics[key];
}

// Un endpoint se considera "vacio" cuando no se ejercito: sin reqs y sin
// latencia real (todo 0/null). Ocurre cuando un endpoint esta declarado como
// threshold pero su peso es 0, o cuando el escenario no llego a ejecutarse.
// Excluirlos evita filas con "0.00 ms" que parecen un exito y no lo son.
const isEmptyEndpoint = (ep) => {
  const d = epDur[ep];
  const r = epReqs[ep];
  const count = value(r, 'count');
  const max = value(d, 'max');
  const avg = value(d, 'avg');
  const zeroish = (v) => v === null || Number(v) === 0;
  const noReqs = count === null || Number(count) === 0;
  return noReqs && zeroish(max) && zeroish(avg);
};

const endpoints = Object.keys(epDur)
  .filter((ep) => !isEmptyEndpoint(ep))
  .sort((a, b) => a.localeCompare(b));

const SLO_P95 = 800;

const endpointRows = endpoints
  .map((ep, i) => {
    const d = epDur[ep];
    const f = epFail[ep];
    const r = epReqs[ep];
    const p95 = value(d, 'p(95)');
    const failed = value(f, 'rate');
    const bad = (p95 !== null && p95 >= SLO_P95) || (failed !== null && failed >= 0.01);
    return `<tr class="${bad ? 'fail' : ''}">
      <td>${i + 1}</td>
      <td class="ep">${esc(ep)}</td>
      <td>${raw(value(r, 'count'))}</td>
      <td>${pct(failed)}</td>
      <td>${ms(value(d, 'avg'))}</td>
      <td>${ms(value(d, 'min'))}</td>
      <td>${ms(value(d, 'med'))}</td>
      <td>${ms(value(d, 'p(90)'))}</td>
      <td class="${p95 !== null && p95 >= SLO_P95 ? 'bad' : ''}"><b>${ms(p95)}</b></td>
      <td>${ms(value(d, 'p(99)'))}</td>
      <td>${ms(value(d, 'max'))}</td>
      <td>${bad ? 'FALLA' : 'OK'}</td>
    </tr>`;
  })
  .join('\n');

const thresholds = [];
for (const [name, metric] of Object.entries(metrics)) {
  if (metric.thresholds) {
    for (const [expr, res] of Object.entries(metric.thresholds)) {
      thresholds.push({ name, expr, ok: res.ok === true });
    }
  }
}
const thresholdFails = thresholds.filter((t) => !t.ok).length;
const thresholdRows = thresholds
  .map(
    (t) =>
      `<tr class="${t.ok ? '' : 'fail'}"><td class="ep">${esc(t.name)}</td><td>${esc(t.expr)}</td><td>${t.ok ? 'OK' : 'FALLA'}</td></tr>`
  )
  .join('\n');

function collectChecks(group, out, prefix) {
  for (const c of Object.values(group.checks || {})) {
    out.push({ name: prefix ? `${prefix} \u21C0 ${c.name}` : c.name, passes: c.passes, fails: c.fails });
  }
  for (const g of Object.values(group.groups || {})) {
    collectChecks(g, out, prefix ? `${prefix} \u21C0 ${g.name}` : g.name);
  }
  return out;
}
const checks = collectChecks(data.root_group || {}, [], '');
const checkRows = checks
  .map(
    (c) =>
      `<tr class="${c.fails > 0 ? 'fail' : ''}"><td class="ep">${esc(c.name)}</td><td>${c.passes}</td><td>${c.fails}</td></tr>`
  )
  .join('\n');

const gDur = metrics['http_req_duration'];
const gFailed = metrics['http_req_failed'];
const gReqs = metrics['http_reqs'];
const gIters = metrics['iterations'];
const gVusMax = metrics['vus_max'];

const globalP95 = value(gDur, 'p(95)');
const globalFailed = value(gFailed, 'rate');
const allOk = thresholdFails === 0;

const html = `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Reporte k6 - ${esc(scenario)}</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: -apple-system, Segoe UI, Roboto, Arial, sans-serif; margin: 0; background: #f5f6f8; color: #1f2933; }
  .wrap { max-width: 1100px; margin: 0 auto; padding: 32px 20px 60px; }
  h1 { font-size: 28px; margin: 0 0 4px; }
  h2 { font-size: 19px; margin: 34px 0 12px; padding-bottom: 6px; border-bottom: 2px solid #e3e8ee; }
  .sub { color: #62717f; margin: 0 0 20px; }
  .cards { display: flex; flex-wrap: wrap; gap: 14px; margin: 18px 0 6px; }
  .card { flex: 1 1 150px; background: #fff; border: 1px solid #e3e8ee; border-radius: 10px; padding: 14px 16px; }
  .card .k { font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: #7b8794; }
  .card .v { font-size: 24px; font-weight: 700; margin-top: 6px; }
  .banner { border-radius: 10px; padding: 14px 18px; font-weight: 600; margin-top: 8px; }
  .ok { background: #d7f0dd; color: #1d6b33; }
  .ko { background: #f8d7da; color: #9b1c24; }
  table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #e3e8ee; border-radius: 10px; overflow: hidden; font-size: 13px; }
  th, td { padding: 8px 10px; text-align: right; border-bottom: 1px solid #eef1f4; white-space: nowrap; }
  th { background: #eef2f6; font-size: 12px; text-transform: uppercase; letter-spacing: .03em; color: #52606d; text-align: right; }
  th:nth-child(2), td.ep { text-align: left; }
  td.ep { font-family: ui-monospace, Consolas, monospace; color: #243b53; }
  tr.fail td { background: #fdecee; }
  td.bad { color: #9b1c24; }
  tr:last-child td { border-bottom: none; }
  .muted { color: #7b8794; font-size: 12px; margin-top: 6px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>Reporte de performance k6</h1>
  <p class="sub">Escenario: <b>${esc(scenario)}</b> &nbsp;|&nbsp; Generado: ${new Date().toLocaleString()} &nbsp;|&nbsp; SLO p95 &lt; ${SLO_P95} ms &amp; errores &lt; 1%</p>

  <div class="banner ${allOk ? 'ok' : 'ko'}">${allOk ? 'Todos los thresholds se cumplieron' : thresholdFails + ' threshold(s) NO se cumplieron'}</div>

  <div class="cards">
    <div class="card"><div class="k">p95 global</div><div class="v">${ms(globalP95)} ms</div></div>
    <div class="card"><div class="k">Errores</div><div class="v">${pct(globalFailed)}</div></div>
    <div class="card"><div class="k">req/s</div><div class="v">${raw(value(gReqs, 'rate'))}</div></div>
    <div class="card"><div class="k">Iteraciones</div><div class="v">${raw(value(gIters, 'count'))}</div></div>
    <div class="card"><div class="k">VUs max</div><div class="v">${raw(value(gVusMax, 'value'))}</div></div>
    <div class="card"><div class="k">Peticiones</div><div class="v">${raw(value(gReqs, 'count'))}</div></div>
  </div>

  <h2>Por endpoint</h2>
  <table>
    <thead><tr><th>#</th><th>endpoint</th><th>reqs</th><th>err%</th><th>avg</th><th>min</th><th>med</th><th>p90</th><th>p95</th><th>p99</th><th>max</th><th>estado</th></tr></thead>
    <tbody>
${endpointRows || '<tr><td colspan="12">Sin datos de endpoints</td></tr>'}
    </tbody>
  </table>
  <p class="muted">Tiempos en ms. err% = http_req_failed. estado eval&uacute;a p95 &lt; ${SLO_P95} ms y error &lt; 1%.</p>

  <h2>Thresholds</h2>
  <table>
    <thead><tr><th>m&eacute;trica</th><th>umbral</th><th>resultado</th></tr></thead>
    <tbody>
${thresholdRows || '<tr><td colspan="3">Sin thresholds</td></tr>'}
    </tbody>
  </table>

  <h2>Checks</h2>
  <table>
    <thead><tr><th>check</th><th>passes</th><th>fails</th></tr></thead>
    <tbody>
${checkRows || '<tr><td colspan="3">Sin checks</td></tr>'}
    </tbody>
  </table>
</div>
</body>
</html>`;

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(outFile, html);
console.log(`Reporte HTML: ${outFile}`);

try {
  const { generateSummaryReport } = require('k6-html-reporter');
  generateSummaryReport({ jsonFile, output: path.resolve(outDir, 'k6-html') });
} catch (e) {
  console.warn('Aviso: no se pudo generar el reporte k6-html-reporter:', e.message);
}
