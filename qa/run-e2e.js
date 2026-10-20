const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const script = process.argv[2] || 'scheduling';
const qaDir = __dirname;
const reportsDir = path.resolve(qaDir, 'reports');
const jsonFile = path.resolve(reportsDir, `e2e_${script}_summary.json`);

if (!fs.existsSync(reportsDir)) {
  fs.mkdirSync(reportsDir, { recursive: true });
}
if (fs.existsSync(jsonFile)) {
  fs.unlinkSync(jsonFile);
}

const scriptPath = path.resolve(qaDir, 'e2e', `${script}.js`);
if (!fs.existsSync(scriptPath)) {
  console.error(`No existe el script e2e: ${scriptPath}`);
  process.exit(1);
}

console.log(`Ejecutando e2e k6: ${script}`);
const result = spawnSync(
  'k6',
  [
    'run',
    '-e', `SUMMARY_FILE=reports/e2e_${script}_summary.json`,
    path.resolve('e2e', `${script}.js`),
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

const report = spawnSync(process.execPath, [path.resolve(qaDir, 'generate-report.js'), `e2e_${script}`], {
  cwd: qaDir,
  stdio: 'inherit',
});
process.exit(report.status || result.status || 0);
