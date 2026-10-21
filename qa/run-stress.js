const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { parseArgs } = require('./lib/args');

const { positional, baseUrl } = parseArgs(process.argv.slice(2));
const scenario = positional[0] || process.env.SCENARIO || 'load';
// Los perfiles de stress/main.js usan el nombre del escenario. Los escenarios
// dedicados viven en sus propios archivos y se seleccionan por nombre.
const DEDICATED = {
  contention: 'stress/contention.js',
  concurrency: 'stress/concurrency-low-rate.js',
};
const isContention = scenario === 'contention';
const script = process.env.STRESS_SCRIPT || DEDICATED[scenario] || 'stress/main.js';
const qaDir = __dirname;
const reportsDir = path.resolve(qaDir, 'reports');
const jsonFile = path.resolve(reportsDir, `${scenario}_summary.json`);
const effectiveBaseUrl = baseUrl || process.env.BASE_URL;

if (fs.existsSync(jsonFile)) {
  fs.unlinkSync(jsonFile);
}

console.log(`Ejecutando escenario k6: ${scenario} (${script})`);
if (effectiveBaseUrl) {
  console.log(`Base URL: ${effectiveBaseUrl}`);
}
const result = spawnSync(
  'k6',
  [
    'run',
    '-e', `SCENARIO=${scenario}`,
    '-e', `SUMMARY_FILE=reports/${scenario}_summary.json`,
    ...(effectiveBaseUrl ? ['-e', `BASE_URL=${effectiveBaseUrl}`] : []),
    ...(isContention ? ['-e', `RUN_ID=${Date.now()}`] : []),
    script,
  ],
  { cwd: qaDir, stdio: 'inherit', shell: true }
);

if (result.error) {
  console.error('No se pudo ejecutar k6:', result.error.message);
  process.exit(1);
}

if (!fs.existsSync(jsonFile)) {
  console.error('k6 no genero el resumen JSON; no se puede crear el reporte HTML.');
  process.exit(1);
}

const report = spawnSync(process.execPath, [path.resolve(qaDir, 'generate-report.js'), scenario], {
  cwd: qaDir,
  stdio: 'inherit',
});
process.exit(report.status || 0);
