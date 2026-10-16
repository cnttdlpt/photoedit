import { fork } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log(`\n======================================================`);
console.log(`  Đang khởi động IRO Studio 3.0 (Fullstack Node.js)`);
console.log(`======================================================\n`);

const backend = fork(path.join(__dirname, 'backend', 'server.mjs'), [], {
  env: { ...process.env, PORT: '5000' },
  stdio: 'inherit'
});

const frontend = fork(path.join(__dirname, 'frontend', 'server.mjs'), [], {
  env: { ...process.env, PORT: '3000', BACKEND_URL: 'http://127.0.0.1:5000' },
  stdio: 'inherit'
});

const cleanup = () => {
  console.log('\n[IRO Studio] Đang tắt các dịch vụ...');
  try { backend.kill(); } catch (e) {}
  try { frontend.kill(); } catch (e) {}
  process.exit(0);
};

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);
process.on('exit', cleanup);
