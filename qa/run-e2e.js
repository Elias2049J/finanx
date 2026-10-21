const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { parseArgs } = require('./lib/args');

const qaDir = __dirname;
const e2eDir = path.resolve(qaDir, 'e2e');
const reportsDir = path.resolve(qaDir, 'reports');
const { baseUrl } = parseArgs(process.argv.slice(2));
const effectiveBaseUrl = baseUrl || process.env.BASE_URL;

// Orden de ejecucion de la suite completa.
const SUITE_ORDER = [
  'auth',
  'wallets',
  'catalog',
  'ledger',
  'budgets',
  'savings',
  'analytics',
  'billing',
  'notifications',
  'scheduling',
];

function listScripts() {
  return fs
    .readdirSync(e2eDir)
    .filter((f) => f.endsWith('.js'))
    .map((f) => f.replace(/\.js$/, ''));
}

function runOne(script) {
  const jsonFile = path.resolve(reportsDir, `e2e_${script}_summary.json`);
  const scriptPath = path.resolve(e2eDir, `${script}.js`);

  if (!fs.existsSync(scriptPath)) {
    console.error(`No existe el script e2e: ${scriptPath}`);
    return 1;
  }
  if (fs.existsSync(jsonFile)) {
    fs.unlinkSync(jsonFile);
  }

  console.log(`\n==================== e2e: ${script} ====================`);
  if (effectiveBaseUrl) {
    console.log(`Base URL: ${effectiveBaseUrl}`);
  }
  const result = spawnSync(
    'k6',
    [
      'run',
      '-e', `SUMMARY_FILE=reports/e2e_${script}_summary.json`,
      ...(effectiveBaseUrl ? ['-e', `BASE_URL=${effectiveBaseUrl}`] : []),
      path.resolve('e2e', `${script}.js`),
    ],
    { cwd: qaDir, stdio: 'inherit', shell: true }
  );

  if (result.error) {
    console.error('No se pudo ejecutar k6:', result.error.message);
    return 1;
  }

  if (!fs.existsSync(jsonFile)) {
    console.error(`[${script}] k6 no genero el resumen JSON; no se crea reporte HTML.`);
    return result.status || 1;
  }

  const report = spawnSync(process.execPath, [path.resolve(qaDir, 'generate-report.js'), `e2e_${script}`], {
    cwd: qaDir,
    stdio: 'inherit',
  });
  return report.status || result.status || 0;
}

function main() {
  const arg = parseArgs(process.argv.slice(2)).positional[0] || 'scheduling';

  if (!fs.existsSync(reportsDir)) {
    fs.mkdirSync(reportsDir, { recursive: true });
  }

  if (arg === 'all') {
    const available = listScripts();
    const scripts = SUITE_ORDER.filter((s) => available.includes(s));
    // Incluye scripts fuera del orden conocido (por si se agregaron nuevos).
    available.forEach((s) => {
      if (!scripts.includes(s)) scripts.push(s);
    });

    console.log(`Ejecutando suite e2e completa: ${scripts.join(', ')}`);
    const failed = [];
    for (const script of scripts) {
      const code = runOne(script);
      if (code !== 0) failed.push(script);
    }

    console.log('\n==================== RESUMEN SUITE ====================');
    if (failed.length === 0) {
      console.log(`OK: los ${scripts.length} escenarios e2e pasaron.`);
      process.exit(0);
    }
    console.error(`FALLARON (${failed.length}/${scripts.length}): ${failed.join(', ')}`);
    process.exit(1);
  }

  if (arg === 'list') {
    console.log('Scripts e2e disponibles:');
    listScripts().forEach((s) => console.log(`  - ${s}`));
    return;
  }

  process.exit(runOne(arg));
}

main();
