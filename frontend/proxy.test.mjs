import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

test('frontend routes legacy API, health and uploads to backend instead of exposing PHP', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'iro-proxy-test-'));
  await fs.copyFile(new URL('./server.mjs', import.meta.url), path.join(root, 'server.mjs'));
  await fs.writeFile(path.join(root, 'index.html'), '<h1>Studio</h1>');
  await fs.writeFile(path.join(root, 'api.php'), '<?php should never be served');
  const backend = http.createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ url: req.url, method: req.method, body: Buffer.concat(chunks).toString(), authorization: req.headers.authorization }));
  });
  backend.listen(0, '127.0.0.1'); await once(backend, 'listening');
  const child = spawn(process.execPath, [path.join(root, 'server.mjs')], { env: {
    ...process.env, HOST: '127.0.0.1', PORT: '15572', BACKEND_URL: `http://127.0.0.1:${backend.address().port}`
  }, stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Frontend timeout')), 10000);
      child.stdout.on('data', () => { clearTimeout(timer); resolve(); });
      child.on('error', reject);
    });
    const base = 'http://127.0.0.1:15572';
    assert.match(await (await fetch(base)).text(), /Studio/);
    for (const endpoint of ['/api.php?action=auth_login', '/api/health', '/uploads/fixture.png']) {
      const res = await fetch(base + endpoint, { method: 'POST', headers: { Authorization: 'Bearer fixture-only' }, body: 'test-body' });
      assert.equal(res.status, 200);
      assert.match(res.headers.get('content-type'), /json/);
      assert.deepEqual(await res.json(), { url: endpoint, method: 'POST', body: 'test-body', authorization: 'Bearer fixture-only' });
    }
    await new Promise(resolve => backend.close(resolve));
    const unavailable = await fetch(base + '/api/health');
    assert.equal(unavailable.status, 502);
    assert.equal((await unavailable.json()).ok, false);
  } finally {
    if (child.exitCode === null) { child.kill(); await once(child, 'exit'); }
    backend.closeAllConnections(); backend.close();
    await fs.rm(root, { recursive: true, force: true });
  }
});
