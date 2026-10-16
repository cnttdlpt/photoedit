import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseCube, sampleLut, processPixels, defaults,
  rotatePixels90, buildMonotoneCurveLut, computeHistogramAndStats,
  autoBalanceSettings, encodeRecipeCode, decodeRecipeCode,
  bakeRecipeToCube, extractDominantPalette, matchColorFromReference,
  promptToRecipeVi, embedRecipeIntoPixels, extractRecipeFromPixels,
  parseFujiRecipe, PHOTO_FRAMES
} from './engine.mjs';

const identity = 'LUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1';

test('Identity LUT preserves interpolated RGB and clamps input', () => {
  const l = parseCube('TITLE "Fuji Test"\n' + identity);
  assert.equal(l.title, 'Fuji Test');
  for (const rgb of [[12, 43, 178], [255, 255, 255], [0, 0, 0], [129, 100, 42]]) {
    sampleLut(l, ...rgb).forEach((v, i) => assert.ok(Math.abs(v - rgb[i]) < .0001));
  }
  assert.deepEqual(sampleLut(l, -100, 300, 0), [0, 255, 0]);
});

test('Reject broken and unsupported LUTs', () => {
  assert.throws(() => parseCube(identity + '\n0 0 0'));
  assert.throws(() => parseCube('LUT_1D_SIZE 2'));
  assert.throws(() => parseCube('DOMAIN_MAX 0 1 1\n' + identity));
  assert.throws(() => parseCube(identity.replace('1 1 1', 'NaN 1 1')));
});

test('Neutral processing is byte-identical including alpha', () => {
  const p = new Uint8ClampedArray([12, 43, 178, 128, 255, 21, 0, 255]);
  assert.deepEqual(processPixels(p, 2, 1, defaults), p);
});

test('Optical & Analog Alchemy: Pro-Mist, Bleach Bypass, Light Leak, Chromatic, Skin Protect, ZoneMap & Zebra', () => {
  const p = new Uint8ClampedArray([
    210, 160, 130, 255,  30, 110, 235, 255,   252, 250, 248, 200,
    120, 120, 120, 255,  180, 90, 50, 255,    40, 130, 240, 255,
    4, 4, 4, 255,        255, 255, 255, 255,  100, 140, 180, 255
  ]);
  const r = processPixels(p, 3, 3, {
    ...defaults,
    film: 'Classic Neg.',
    proMist: 40,
    bleachBypass: 30,
    lightLeak: 35,
    chromatic: 1,
    skinProtect: 80,
    halation: 40,
    grain: 8,
    grainSize: 2
  });
  assert.equal(r[11], 200);
  assert.notDeepEqual(r, p);

  const zMap = processPixels(p, 3, 3, { ...defaults, diagnosticMode: 'ZoneMap' });
  assert.notDeepEqual(zMap, p);

  const zebra = processPixels(p, 3, 3, { ...defaults, diagnosticMode: 'Zebra' });
  assert.notDeepEqual(zebra, p);
});

test('Prompt-to-Recipe Vietnamese NLP & LSB Invisible Recipe Steganography round-trip', () => {
  const dalat = promptToRecipeVi('Chiều mưa Đà Lạt buồn điện ảnh');
  assert.equal(dalat.settings.film, 'ETERNA');
  assert.ok(dalat.settings.proMist > 0);

  const canvasPixels = new Uint8ClampedArray(600 * 4);
  for (let i = 0; i < canvasPixels.length; i++) canvasPixels[i] = 128;

  const stegoPixels = embedRecipeIntoPixels(canvasPixels, dalat.settings);
  const recovered = extractRecipeFromPixels(stegoPixels);
  assert.ok(recovered);
  assert.equal(recovered.film, 'ETERNA');
  assert.equal(recovered.proMist, dalat.settings.proMist);
});

test('Creative Lab: bakeRecipeToCube, extractDominantPalette, and matchColorFromReference', () => {
  const cubeText = bakeRecipeToCube({ ...defaults, film: 'Classic Chrome', color: 2 }, 5, 'Classic Chrome Export');
  const parsed = parseCube(cubeText);
  assert.equal(parsed.size, 5);

  const sample = new Uint8ClampedArray([
    220, 80, 40, 255,   218, 82, 42, 255,
    30, 120, 210, 255,  240, 210, 90, 255
  ]);
  const palette = extractDominantPalette(sample, 3);
  assert.equal(palette.length, 3);

  const ref = new Uint8ClampedArray([
    180, 140, 90, 255,  190, 150, 95, 255,
    170, 130, 85, 255,  200, 160, 100, 255
  ]);
  const matched = matchColorFromReference(sample, ref, defaults);
  assert.ok(matched.reasonVi.includes('Đã sao chép bảng màu'));
});

test('IRO Studio 3.0: Clarity, Texture, Dehaze, Swirly Bokeh, Depth Fog, Darkroom Print & 24fps Living Film', () => {
  const p = new Uint8ClampedArray(5 * 5 * 4);
  for (let i = 0; i < p.length; i += 4) {
    p[i] = (i * 17) % 255;
    p[i + 1] = (i * 31) % 255;
    p[i + 2] = (i * 47) % 255;
    p[i + 3] = 255;
  }
  const f0 = processPixels(p, 5, 5, {
    ...defaults,
    clarity: 40,
    texture: 25,
    dehaze: 35,
    swirlyBokeh: 50,
    depthFog: 30,
    darkroomProcess: 'Cyanotype',
    grain: 8,
    grainSize: 2
  }, null, 0);
  const f1 = processPixels(p, 5, 5, {
    ...defaults,
    clarity: 40,
    texture: 25,
    dehaze: 35,
    swirlyBokeh: 50,
    depthFog: 30,
    darkroomProcess: 'Cyanotype',
    grain: 8,
    grainSize: 2
  }, null, 1);
  assert.notDeepEqual(f0, p);
  assert.notDeepEqual(f0, f1); // Frame 0 and Frame 1 have distinct organic 24fps grain/breath
});

test('Web Studio Flagship: 392 Fuji Recipes Parser, 30 Photo Frames & 3-Way Color Wheels', () => {
  // Test PHOTO_FRAMES catalog
  assert.equal(PHOTO_FRAMES.length, 31);
  assert.equal(PHOTO_FRAMES[0].key, 'None');
  assert.equal(PHOTO_FRAMES[1].key, 'Classic');
  assert.equal(PHOTO_FRAMES[10].key, 'Film35');
  assert.equal(PHOTO_FRAMES[30].key, 'Corners');

  // Test parseFujiRecipe
  const sampleFuji = {
    name: 'Golden Negative',
    sensors: ['BAYER'],
    film: 'Classic Chrome',
    rawSettings: {
      'Highlight': '+1',
      'Shadow': '-2',
      'Color': '-2',
      'Noise Reduction': '-2',
      'Sharpening': '0',
      'White Balance': 'Daylight, +4 Red & -2 Blue',
      'Dynamic Range': 'DR-Auto',
      'Exposure Compensation': '-1/3 to +1/3'
    }
  };
  const parsed = parseFujiRecipe(sampleFuji);
  assert.equal(parsed.settings.film, 'Classic Chrome');
  assert.equal(parsed.settings.highlight, 1);
  assert.equal(parsed.settings.shadow, -2);
  assert.equal(parsed.settings.color, -2);
  assert.equal(parsed.settings.red, 4);
  assert.equal(parsed.settings.blue, -2);

  // Test 3-Way Color Wheels
  const pixels = new Uint8ClampedArray([128, 128, 128, 255, 30, 30, 30, 255, 230, 230, 230, 255]);
  const graded = processPixels(pixels, 3, 1, {
    ...defaults,
    shadowsHue: 240, // Cool blue shadows
    shadowsSat: 50,
    highlightsHue: 35, // Warm amber highlights
    highlightsSat: 50
  });
  assert.notDeepEqual(graded, pixels);
  assert.ok(graded[6] >= graded[4]); // Blue channel higher than Red channel in shadows
});
