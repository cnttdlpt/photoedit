import test from 'node:test';
import assert from 'node:assert/strict';

test('AI Film Stylist API endpoint validates and recommends Fujifilm recipes', async t => {
  const base = 'http://127.0.0.1:5000';

  await t.test('GET /api/ai/film-stylist returns matching recipes and aesthetic critique', async () => {
    const res = await fetch(`${base}/api/ai/film-stylist?prompt=quán%20cafe%20vintage%20ấm%20áp`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.ok, true);
    assert.equal(typeof data.film, 'string');
    assert.equal(typeof data.tone, 'string');
    assert.equal(typeof data.advice, 'string');
    assert.ok(Array.isArray(data.recipes));
    assert.equal(data.recipes.length, 3);
    assert.ok(data.recipes[0].name.length > 0);
    assert.ok(data.recipes[0].film.length > 0);
    assert.ok(data.recipes[0].matchReason.length > 0);
  });

  await t.test('POST /api/ai/film-stylist handles JSON payload', async () => {
    const res = await fetch(`${base}/api/ai/film-stylist`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'chiều mưa đà lạt sương mù điện ảnh' })
    });
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.ok, true);
    assert.equal(data.film, 'Eterna');
    assert.equal(data.recipes.length, 3);
  });

  await t.test('Empty prompt returns 400 Bad Request', async () => {
    const res = await fetch(`${base}/api/ai/film-stylist?prompt=`);
    assert.equal(res.status, 400);
    const data = await res.json();
    assert.equal(data.ok, false);
  });
});
