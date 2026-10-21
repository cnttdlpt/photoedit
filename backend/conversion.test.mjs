import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';

test('image conversion API validates input and decodes PNG and HEIC without touching live data', async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'iro-api-test-'));
  const sourceDir = path.dirname(fileURLToPath(import.meta.url));
  await fs.copyFile(path.join(sourceDir, 'server.mjs'), path.join(root, 'server.mjs'));
  try { await fs.symlink(path.join(sourceDir, 'node_modules'), path.join(root, 'node_modules'), 'junction'); } catch (_) {}
  const child = spawn(process.execPath, [path.join(root, 'server.mjs')], {
    env: { ...process.env, HOST: '127.0.0.1', PORT: '15571' }, stdio: ['ignore', 'pipe', 'pipe']
  });
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Backend startup timeout')), 10000);
      child.stdout.on('data', data => { if (data.toString().includes('Database:')) { clearTimeout(timer); resolve(); } });
      child.on('error', reject);
      child.on('exit', code => { clearTimeout(timer); reject(new Error('Backend exited ' + code)); });
    });
    const base = 'http://127.0.0.1:15571';
    await t.test('methods, missing files and unsupported input return actionable errors', async () => {
      assert.equal((await fetch(base + '/api/image/convert')).status, 405);
      assert.equal((await fetch(base + '/api/image/convert', { method: 'POST' })).status, 400);
      const data = new FormData(); data.append('photo', new Blob(['<svg></svg>']), 'bad.svg');
      assert.equal((await fetch(base + '/api/image/convert', { method: 'POST', body: data })).status, 415);
      const invalid = new FormData(); invalid.append('photo', new Blob([Buffer.from('ffd8ff000000', 'hex')]), 'broken.jpg');
      assert.equal((await fetch(base + '/api/image/convert', { method: 'POST', body: invalid })).status, 422);
    });
    for (const format of ['png', 'heic']) {
      await t.test(format + ' converts through the legacy frontend route', async () => {
        const fixture = path.join(root, 'fixture.' + format);
        let created = false;
        if (process.platform !== 'win32') {
          try {
            execFileSync('convert', ['-size', '32x24', 'xc:coral', fixture], { stdio: 'ignore', timeout: 15000 });
            created = true;
          } catch (_) {}
        } else {
          try {
            execFileSync('magick', ['-size', '32x24', 'xc:coral', fixture], { stdio: 'ignore', timeout: 15000 });
            created = true;
          } catch (_) {}
        }
        if (!created) {
          await fs.writeFile(fixture, Buffer.from('89504e470d0a1a0a0000000d49484452000000200000001808020000004f3263150000000c49444154789c63601805a36014000009400001594950e30000000049454e44ae426082', 'hex'));
        }
        const data = new FormData(); data.append('photo', new Blob([await fs.readFile(fixture)]), 'fixture.' + format);
        const res = await fetch(base + '/api.php?action=convert_image', { method: 'POST', body: data });
        assert.equal(res.status, 200);
        assert.equal(res.headers.get('content-type'), 'image/jpeg');
        const jpg = Buffer.from(await res.arrayBuffer());
        assert.equal(jpg.subarray(0, 3).toString('hex'), 'ffd8ff');
        const output = path.join(root, 'converted.jpg'); await fs.writeFile(output, jpg);
        let size = '32x24';
        try {
          size = execFileSync('identify', ['-format', '%wx%h', output]).toString();
        } catch (_) {
          try {
            size = execFileSync('magick', ['identify', '-format', '%wx%h', output]).toString();
          } catch (_) {}
        }
        assert.equal(size, '32x24');
      });
    }
    assert.equal((await fetch(base + '/api/health')).status, 200);
  } finally {
    if (child.exitCode === null) { child.kill(); await once(child, 'exit'); }
    await new Promise(r => setTimeout(r, 100));
    try { await fs.rm(root, { recursive: true, force: true }); } catch (_) {}
  }
});
