const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const scenario = process.argv[2] || process.env.SCENARIO || 'load';
const qaDir = __dirname;
const reportsDir = path.resolve(qaDir, 'reports');
const jsonFile = path.resolve(reportsDir, `${scenario}_summary.json`);

if (fs.existsSync(jsonFile)) {
  fs.unlinkSync(jsonFile);
}

console.log(`Ejecutando escenario k6: ${scenario}`);
const result = spawnSync(
  'k6',
  [
    'run',
    '-e', `SCENARIO=${scenario}`,
    '-e', `SUMMARY_FILE=reports/${scenario}_summary.json`,
    'stress/main.js',
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
