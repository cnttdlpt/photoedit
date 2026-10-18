import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, copyFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import http from 'node:http';
import { gunzipSync } from 'node:zlib';

let directory, child, port;
const moduleSource = 'export const release = "current";\n' + '// compression fixture\n'.repeat(100);

before(async () => {
  directory = await mkdtemp(path.join(tmpdir(), 'iro-http-tests-'));
  await copyFile(new URL('./server.mjs', import.meta.url), path.join(directory, 'server.mjs'));
  await Promise.all(Object.entries({
    'index.html': '<!doctype html><script type="module" src="app.mjs"></script>',
    'app.mjs': moduleSource,
    'engine.mjs': 'export const engine = 1;',
    'script.js': 'window.ready = true;',
    'style.css': 'body { color: black; }',
    'recipes.json': '{"recipes":[]}',
    'photo.png': Buffer.from([137, 80, 78, 71]),
    'font.woff2': Buffer.from([119, 79, 70, 50])
  }).map(([name, data]) => writeFile(path.join(directory, name), data)));
  child = spawn(process.execPath, [path.join(directory, 'server.mjs')], {
    cwd: directory,
    env: { ...process.env, HOST: '127.0.0.1', PORT: '0' },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  let output = '';
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Server did not start: ' + output)), 10_000);
    child.stderr.on('data', data => { output += data; });
    child.once('error', error => { clearTimeout(timeout); reject(error); });
    child.once('exit', code => {
      clearTimeout(timeout);
      if (!port) reject(new Error(`Server exited before listening (${code}): ${output}`));
    });
    child.stdout.on('data', data => {
      output += data;
      const match = output.match(/Local:\s+http:\/\/localhost:(\d+)/);
      if (match && !port) {
        port = Number(match[1]);
        clearTimeout(timeout);
        resolve();
      }
    });
  });
});

after(async () => {
  if (child && child.exitCode === null && child.signalCode === null) {
    const exited = once(child, 'exit');
    child.kill('SIGTERM');
    await exited;
  }
  if (directory) await rm(directory, { recursive: true, force: true });
});

function request(urlPath, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.get({ host: '127.0.0.1', port, path: urlPath, headers }, response => {
      const chunks = [];
      response.on('data', chunk => chunks.push(chunk));
      response.on('error', reject);
      response.on('end', () => resolve({ status: response.statusCode, headers: response.headers, body: Buffer.concat(chunks) }));
    });
    req.on('error', reject);
  });
}

test('HTML, modules, CSS and JSON revalidate, including versioned module URLs', async () => {
  for (const url of ['/', '/index.html', '/app.mjs', '/app.mjs?v=next-release', '/engine.mjs', '/script.js', '/style.css', '/recipes.json']) {
    const response = await request(url);
    assert.equal(response.status, 200, url);
    assert.equal(response.headers['cache-control'], 'no-cache', url);
    if (url.includes('.mjs') || url.endsWith('.js')) {
      assert.match(response.headers['content-type'], /^text\/javascript\b/, url);
    }
  }
});

test('images and fonts retain their existing cache lifetime', async () => {
  for (const url of ['/photo.png', '/font.woff2']) {
    const response = await request(url);
    assert.equal(response.status, 200, url);
    assert.equal(response.headers['cache-control'], 'public, max-age=86400', url);
  }
});

test('gzip and identity module responses declare their encoding variant and preserve content', async () => {
  const identity = await request('/app.mjs', { 'Accept-Encoding': 'identity' });
  const compressed = await request('/app.mjs', { 'Accept-Encoding': 'gzip' });
  assert.equal(identity.headers['content-encoding'], undefined);
  assert.equal(compressed.headers['content-encoding'], 'gzip');
  for (const response of [identity, compressed]) {
    assert.equal(response.status, 200);
    assert.equal(response.headers.vary, 'Accept-Encoding');
    assert.equal(response.headers['cache-control'], 'no-cache');
  }
  assert.equal(identity.body.toString(), moduleSource);
  assert.equal(gunzipSync(compressed.body).toString(), moduleSource);
});
