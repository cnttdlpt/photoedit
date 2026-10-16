// Node-only integration tests. All network/storage APIs are local fixtures;
// this harness does not launch a browser or contact the deployed website.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
const require = createRequire(import.meta.url);
const { parseHTML } = require('linkedom');
const { createCanvas, ImageData } = require('@napi-rs/canvas');
const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
const catalog = JSON.parse(await readFile(new URL('./recipes.json', import.meta.url), 'utf8'));
let instance = 0;

async function boot(favorites = '[]', storageBlocked = false) {
  const { window, document } = parseHTML(html);
  const canvases = new WeakMap();
  function native(element) {
    if (!canvases.has(element)) canvases.set(element, createCanvas(element.width, element.height));
    return canvases.get(element);
  }
  const proto = window.HTMLCanvasElement.prototype;
  for (const [key, fallback] of [['width', 300], ['height', 150]]) {
    Object.defineProperty(proto, key, {
      configurable: true,
      get() { return Number(this.getAttribute(key) ?? fallback); },
      set(value) { this.setAttribute(key, value); if (canvases.has(this)) native(this)[key] = Number(value); }
    });
  }
  proto.getContext = function () {
    const context = native(this).getContext('2d');
    return new Proxy(context, { get(target, key) {
      if (key === 'drawImage') return (source, ...args) => target.drawImage(source instanceof window.HTMLCanvasElement ? native(source) : source, ...args);
      const value = target[key];
      return typeof value === 'function' ? value.bind(target) : value;
    }, set(target, key, value) { target[key] = value; return true; } });
  };
  proto.toDataURL = function (...args) { return native(this).toDataURL(...args); };
  proto.toBlob = function (callback) { callback(new Blob([native(this).toBuffer('image/png')], { type: 'image/png' })); };
  // LinkeDOM's select.value is getter-only; emulate the browser setter.
  Object.defineProperty(window.HTMLSelectElement.prototype, 'value', {
    configurable: true,
    get() { return this.querySelector('option[selected]')?.value ?? this.querySelector('option')?.value ?? ''; },
    set(value) { for (const option of this.querySelectorAll('option')) option.toggleAttribute('selected', option.value === String(value)); }
  });
  window.HTMLElement.prototype.getBoundingClientRect = function () {
    return { left: 0, top: 0, width: this.width || 100, height: this.height || 100 };
  };
  window.HTMLElement.prototype.setPointerCapture = function () {};
  window.HTMLElement.prototype.releasePointerCapture = function () {};
  let stored = favorites;
  const localStorage = {
    getItem() { if (storageBlocked) throw new Error('Storage denied'); return stored; },
    setItem(_key, value) { if (storageBlocked) throw new Error('Storage denied'); stored = value; }
  };
  const requests = [];
  const globals = {
    document, window, ImageData, localStorage,
    navigator: { clipboard: { writeText: async () => {} } },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return { ok: true, json: async () => String(url).includes('recipes.json') ? structuredClone(catalog) : { ok: true } };
    },
    // Keep background telemetry/animation inert. These tests assert synchronous
    // editor behavior and never send fixture or user data over a network.
    setTimeout: () => 1, clearTimeout: () => {}, setInterval: () => 1, clearInterval: () => {},
    requestAnimationFrame: callback => callback(),
    prompt: () => null, alert: () => {}
  };
  const previous = new Map(Object.keys(globals).map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  for (const [key, value] of Object.entries(globals)) Object.defineProperty(globalThis, key, { configurable: true, writable: true, value });
  const cleanup = () => {
    for (const [key, descriptor] of previous) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key];
    }
  };
  try {
    await import(`./app.mjs?integration=${++instance}`);
    await new Promise(resolve => setImmediate(resolve));
    const element = selector => assertElement(document.querySelector(selector), selector);
    return {
      document, element, requests, cleanup,
      click(selector) { element(selector).click(); },
      input(selector, value, kind = 'input') {
        const target = element(selector); target.value = String(value);
        target.dispatchEvent(new window.Event(kind, { bubbles: true }));
      },
      pixels() {
        const target = element('#canvas');
        return createHash('sha256').update(native(target).getContext('2d').getImageData(0, 0, target.width, target.height).data).digest('hex');
      },
      favorites() { return JSON.parse(stored); }
    };
  } catch (error) { cleanup(); throw error; }
}

function assertElement(element, selector) { assert.ok(element, `Missing element ${selector}`); return element; }

test('real page starts, renders sample and wires exposure, undo, frames and navigation', async () => {
  const app = await boot();
  try {
    assert.equal(app.element('#canvas').width, 960);
    assert.equal(app.element('#canvas').height, 640);
    assert.match(app.element('#status').textContent, /Sẵn sàng/);
    assert.equal(app.element('#export').disabled, false);
    const before = app.pixels();
    app.input('#in-exposure', 1);
    app.input('#in-exposure', 1, 'change');
    assert.notEqual(app.pixels(), before, 'Exposure must change rendered pixels');
    app.click('#undoBtn');
    assert.equal(app.pixels(), before, 'Undo must restore the previous image');
    app.input('#frameStyle', 1, 'change');
    assert.ok(app.element('#canvas').height > 640, 'Frame must add canvas area');
    app.click('#themeToggleBtn');
    assert.ok(app.document.body.classList.contains('theme-porcelain'));
    app.click('#quickCmdBtn');
    assert.equal(app.element('#cmdPaletteModal').hidden, false);
  } finally { app.cleanup(); }
});

test('catalog search, favorite and recipe application use real catalog records', async () => {
  const app = await boot();
  try {
    app.click('#recipeLibraryBtn');
    assert.equal(app.element('#recipeLibraryModal').hidden, false);
    assert.equal(app.document.querySelectorAll('.recipe-lib-card').length, catalog.recipes.length);
    app.input('#recipeSearchInput', 'not-a-real-film-or-white-balance-927');
    assert.equal(app.document.querySelectorAll('.recipe-lib-card').length, 0);
    app.input('#recipeSearchInput', '');
    app.click('.fav-star-btn');
    assert.equal(app.favorites()[0], catalog.recipes[0].id);
    app.click('.recipe-lib-card');
    assert.equal(app.element('#recipeLibraryModal').hidden, true);
    assert.ok(app.element('#status').textContent.includes(catalog.recipes[0].name));
    assert.doesNotMatch(app.element('#status').textContent, /undefined|NaN/);
    app.click('#lightCraftBtn');
    assert.equal(app.element('#lightCraftModal').hidden, false);
    assert.equal(app.document.querySelectorAll('.hsl-row').length, 8);
    const before = app.pixels();
    app.input('#lumMidtones', 30);
    assert.notEqual(app.pixels(), before, 'Wheel luminance must affect output');
  } finally { app.cleanup(); }
});

for (const [name, value, blocked] of [['malformed favorites', '{broken', false], ['blocked storage', '[]', true], ['non-array favorites', '{}', false]]) {
  test(`startup remains interactive with ${name}`, async () => {
    const app = await boot(value, blocked);
    try {
      assert.match(app.element('#status').textContent, /Sẵn sàng/);
      app.click('#recipeLibraryBtn');
      assert.equal(app.element('#recipeLibraryModal').hidden, false);
    } finally { app.cleanup(); }
  });
}
