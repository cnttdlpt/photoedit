import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';

test('auth rejects malformed inputs without breaking valid registration and login', { timeout: 30000 }, async t => {
  const source = path.dirname(fileURLToPath(import.meta.url));
  const sandbox = await fs.mkdtemp(path.join(os.tmpdir(), 'iro-auth-test-'));
  let child;
  let exited;
  t.after(async () => {
    if (child && child.exitCode === null) child.kill();
    if (exited) await exited;
    await fs.rm(sandbox, { recursive: true, force: true });
  });
  await fs.copyFile(path.join(source, 'server.mjs'), path.join(sandbox, 'server.mjs'));
  // Reuse installed sqlite3 when available; otherwise the server uses node:sqlite.
  try {
    await fs.access(path.join(source, 'node_modules'));
    await fs.symlink(path.join(source, 'node_modules'), path.join(sandbox, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  const reservation = net.createServer();
  reservation.listen(0, '127.0.0.1');
  await once(reservation, 'listening');
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  child = spawn(process.execPath, [path.join(sandbox, 'server.mjs')], {
    env: { ...process.env, HOST: '127.0.0.1', PORT: String(port) }, stdio: ['ignore', 'pipe', 'pipe']
  });
  exited = once(child, 'close');
  let log = '';
  child.stdout.on('data', data => { log += data; });
  child.stderr.on('data', data => { log += data; });
  const base = `http://127.0.0.1:${port}`;
  let ready = false;
  for (let i = 0; i < 100; i++) {
    if (child.exitCode !== null) throw new Error(log);
    try {
      const response = await fetch(base + '/api/health', { signal: AbortSignal.timeout(500) });
      if (response.ok) { ready = true; break; }
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert.ok(ready, 'Backend must start: ' + log);

  let client = 0;
  const post = (url, body) => fetch(base + url, {
    method: 'POST',
    // Isolate validation cases from the per-client login/register rate limits.
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `127.0.1.${++client}` },
    body: typeof body === 'string' ? body : JSON.stringify(body)
  });
  const user = { username: 'qa_auth_user', email: 'qa-auth@example.invalid', password: 'qa fixture password 2026' };
  const routes = [
    ['/api/auth/login', ['account', 'login', 'username', 'email', 'password']],
    ['/api/auth/register', ['username', 'email', 'password', 'full_name', 'fullName']]
  ];
  for (const [route, fields] of routes) {
    for (const raw of ['{broken', 'null', '[]', '123', 'true', '"text"', '']) {
      await t.test(`${route} rejects body ${JSON.stringify(raw)}`, async () => {
        const response = await post(route, raw);
        assert.equal(response.status, 400);
        const body = await response.json();
        assert.equal(body.ok, false);
        assert.equal(typeof body.error, 'string');
        assert.doesNotMatch(body.error, /Internal Server Error|TypeError|SyntaxError/);
      });
    }
    for (const field of fields) {
      for (const value of [123, null, false, [], {}]) {
        await t.test(`${route} rejects ${field}=${JSON.stringify(value)}`, async () => {
          const response = await post(route, { ...user, [field]: value });
          assert.equal(response.status, 400);
          assert.equal((await response.json()).ok, false);
        });
      }
    }
  }
  for (const action of ['auth_login', 'auth_register']) {
    await t.test(`legacy ${action} also rejects malformed JSON`, async () => {
      assert.equal((await post(`/api.php?action=${action}`, '{broken')).status, 400);
    });
  }
  await t.test('valid registration still creates a regular user without exposing secrets', async () => {
    const response = await post('/api/auth/register', { ...user, fullName: 'QA User' });
    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.user.role, 'user');
    assert.equal(body.user.full_name, 'QA User');
    assert.ok(body.token);
    assert.ok(!('password_hash' in body.user));
    assert.ok(!('salt' in body.user));
  });
  await t.test('duplicate registration still returns 409', async () => {
    assert.equal((await post('/api/auth/register', user)).status, 409);
  });
  for (const field of ['account', 'login', 'username', 'email']) {
    await t.test(`valid ${field} login preserves normalization and token flow`, async () => {
      const value = field === 'email' ? user.email : user.username;
      const response = await post('/api/auth/login', { [field]: ` ${value.toUpperCase()} `, password: user.password });
      assert.equal(response.status, 200);
      const body = await response.json();
      assert.equal(body.user.username, user.username);
      const me = await fetch(base + '/api/auth/me', { headers: { Authorization: `Bearer ${body.token}` } });
      assert.equal((await me.json()).authenticated, true);
    });
  }
  await t.test('wrong password remains 401', async () => {
    assert.equal((await post('/api/auth/login', { username: user.username, password: 'wrong' })).status, 401);
  });
});
