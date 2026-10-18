import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as engine from './engine.mjs';

const { defaults, processPixels, CURATED_PRESETS, LIGHTCRAFT_PRESETS, computeHistogramAndStats,
  encodeRecipeCode, decodeRecipeCode, embedRecipeIntoPixels, extractRecipeFromPixels,
  parseFujiRecipe, parseCube, sampleLut, bakeRecipeToCube, defaultPhotoInfo } = engine;
const fixture = new Uint8ClampedArray([
  210, 160, 130, 255, 30, 110, 235, 72, 252, 250, 248, 255,
  120, 120, 120, 255, 180, 90, 50, 255, 40, 130, 240, 255,
  4, 4, 4, 255, 255, 255, 255, 100, 100, 140, 180, 0
]);

test('Every engine symbol imported by the browser entry point exists', async () => {
  const app = await readFile(new URL('./app.mjs', import.meta.url), 'utf8');
  const imports = [...app.matchAll(/import\s*\{([\s\S]*?)\}\s*from\s*['"]\.\/engine\.mjs(?:\?[^'"]*)?['"]/g)];
  assert.ok(imports.length > 0);
  for (const match of imports) for (const entry of match[1].split(',')) {
    const name = entry.trim().split(/\s+as\s+/)[0];
    assert.ok(Object.hasOwn(engine, name), `Missing browser import: ${name}`);
  }
});

test('Ribbon and LightCraft presets use working controls and change rendered pixels', () => {
  assert.equal(CURATED_PRESETS.length, 12, 'Contact sheet promises twelve previews');
  assert.ok(LIGHTCRAFT_PRESETS.length > 0);
  const renderedPresets = new Set();
  for (const preset of CURATED_PRESETS) {
    assert.ok(preset.name && preset.subtitle);
    for (const key of Object.keys(preset.settings)) assert.ok(key in defaults, `${preset.name}: ${key}`);
    const rendered = processPixels(fixture, 3, 3, { ...defaults, ...preset.settings });
    assert.notDeepEqual(rendered, fixture, preset.name);
    const signature = Array.from(rendered).join(',');
    assert.ok(!renderedPresets.has(signature), `Duplicate rendered preset: ${preset.name}`);
    renderedPresets.add(signature);
  }
  for (const preset of LIGHTCRAFT_PRESETS) {
    const settings = structuredClone(defaults);
    if (preset.curve) settings.curvePoints = preset.curve;
    for (const [tone, values] of Object.entries(preset.wheels || {})) {
      for (const [key, value] of Object.entries(values)) settings[tone + key[0].toUpperCase() + key.slice(1)] = value;
    }
    for (const key of ['hslHue', 'hslSat', 'hslLum', 'clarity', 'vignette']) if (key in preset) settings[key] = preset[key];
    assert.notDeepEqual(processPixels(fixture, 3, 3, settings), fixture, preset.name);
  }
});

test('Prompt-generated recipes own their editable nested controls', () => {
  const defaultSnapshot = structuredClone(defaults);
  const generated = engine.promptToRecipeVi('phố đêm').settings;
  generated.hslHue[0] = 80;
  generated.curvePoints[0][1] = 0.25;
  assert.deepEqual(defaults, defaultSnapshot);
  const base = structuredClone(defaults);
  const custom = engine.promptToRecipeVi('đen trắng', base).settings;
  custom.hslSat[1] = -40;
  assert.deepEqual(base, defaultSnapshot);
});

test('Every film option in the editor selects a real implemented look', async () => {
  const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
  const options = html.match(/<select\s+id="film">([\s\S]*?)<\/select>/)[1];
  for (const match of options.matchAll(/<option(?:\s[^>]*)?>([^<]+)<\/option>/g)) {
    const film = match[1];
    assert.ok(Object.keys(engine.films).some(name => name.toLowerCase() === film.toLowerCase()), film);
    if (film !== 'Original') assert.notDeepEqual(processPixels(fixture, 3, 3, { ...defaults, film }), fixture, film);
  }
});

test('All three luminance wheels work without requiring saturation', () => {
  const gray = new Uint8ClampedArray([128, 128, 128, 91]);
  for (const tone of ['shadows', 'midtones', 'highlights']) {
    const result = processPixels(gray, 1, 1, { ...defaults, [tone + 'Lum']: 20 });
    assert.ok(result[0] > gray[0], tone);
    assert.equal(result[0], result[1]);
    assert.equal(result[1], result[2]);
    assert.equal(result[3], 91);
  }
});

test('Eight-band HSL changes its selected colors, preserves other bands and alpha', () => {
  const input = new Uint8ClampedArray([255, 0, 0, 77, 0, 255, 0, 55, 128, 128, 128, 90]);
  for (const [key, amount] of [['hslHue', 100], ['hslSat', -100], ['hslLum', 50]]) {
    const values = Array(8).fill(0); values[3] = amount;
    const result = processPixels(input, 3, 1, { ...defaults, [key]: values });
    assert.deepEqual(result.slice(0, 4), input.slice(0, 4));
    assert.notDeepEqual(result.slice(4, 7), input.slice(4, 7));
    assert.equal(result[7], 55);
    assert.deepEqual(result.slice(8), input.slice(8));
  }
  assert.deepEqual(processPixels(input, 3, 1, defaults), input);
});

test('Vignette darkens or brightens corners without altering the center/alpha', () => {
  const pixels = new Uint8ClampedArray(Array(9).fill([128, 128, 128, 63]).flat());
  const dark = processPixels(pixels, 3, 3, { ...defaults, vignette: 50 });
  const light = processPixels(pixels, 3, 3, { ...defaults, vignette: -50 });
  assert.ok(dark[0] < 128 && light[0] > 128);
  assert.equal(dark[16], 128); assert.equal(light[16], 128);
  assert.equal(dark[3], 63); assert.equal(light[3], 63);
});

test('Histogram fields consumed by the UI are finite, and black/midpoint quantiles are correct', () => {
  const pixels = new Uint8ClampedArray([0, 0, 0, 255, 128, 128, 128, 255, 255, 255, 255, 255]);
  const stats = computeHistogramAndStats(pixels);
  assert.equal(stats.rHist.length, 256);
  assert.equal(stats.p05, 0); assert.equal(stats.p50, 128); assert.equal(stats.p95, 255);
  for (const key of ['maxBin', 'evShift', 'highlightClipPct']) assert.ok(Number.isFinite(stats[key]), key);
  assert.ok(Math.abs(stats.highlightClipPct - 100 / 3) < 1e-10);
});

test('Catalog parsing supports Array.map and retains identity/search/display metadata', async () => {
  const catalog = JSON.parse(await readFile(new URL('./recipes.json', import.meta.url), 'utf8'));
  const recipes = catalog.recipes.map(parseFujiRecipe);
  assert.equal(recipes.length, catalog.recipeCount);
  for (const [index, recipe] of recipes.entries()) {
    assert.equal(recipe.id, catalog.recipes[index].id);
    assert.equal(typeof recipe.wb, 'string'); assert.ok([100, 200, 400].includes(recipe.dr));
    assert.equal(recipe.settings.lutAmount, 100);
  }
  assert.equal(Object.values(defaultPhotoInfo).every(value => value === ''), true);
});

test('Recipe codes preserve current LightCraft controls and still decode legacy codes', () => {
  const settings = {
    ...structuredClone(defaults), grain: 15, film: 'Sepia', vignette: 19,
    curvePoints: [[0, 0.1], [0.5, 0.6], [1, 0.95]],
    shadowsHue: 230, shadowsSat: 24, midtonesLum: 8, highlightsLum: -6,
    hslHue: [0, 12, 0, 0, 0, -24, 0, 0], hslSat: [0, -10, 0, 0, 0, 0, 0, 0], hslLum: [10, 0, 0, 0, 0, 0, 0, 0]
  };
  const code = encodeRecipeCode(settings);
  const restored = decodeRecipeCode(code);
  for (const key of ['grain', 'vignette', 'curvePoints', 'shadowsHue', 'shadowsSat', 'midtonesLum', 'highlightsLum', 'hslHue', 'hslSat', 'hslLum']) {
    assert.deepEqual(restored[key], settings[key], key);
  }
  assert.deepEqual(processPixels(fixture, 3, 3, restored), processPixels(fixture, 3, 3, settings));
  const legacy = Buffer.from(code.slice(5), 'base64url').toString('utf8').split('|').slice(0, 31).join('|');
  assert.equal(decodeRecipeCode('IRO1-' + Buffer.from(legacy).toString('base64url')).grain, 15);
  const embedded = embedRecipeIntoPixels(new Uint8ClampedArray(10_000 * 4).fill(128), settings);
  assert.deepEqual(extractRecipeFromPixels(embedded).hslHue, settings.hslHue);
  assert.deepEqual(extractRecipeFromPixels(embedded).curvePoints, settings.curvePoints);
});

test('LUT export includes imported LUT colors but excludes spatial vignette', () => {
  const inverse = parseCube('LUT_3D_SIZE 2\n1 1 1\n0 1 1\n1 0 1\n0 0 1\n1 1 0\n0 1 0\n1 0 0\n0 0 0');
  const baked = parseCube(bakeRecipeToCube({ ...defaults, vignette: 50 }, 2, 'Combined', inverse));
  assert.deepEqual(sampleLut(baked, 0, 0, 0), [255, 255, 255]);
  assert.deepEqual(sampleLut(baked, 255, 255, 255), [0, 0, 0]);
});
