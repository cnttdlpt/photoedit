export const defaults = {
  film: 'Original', amount: 100, lutAmount: 100, dr: 100,
  grain: 0, grainSize: 1, chrome: 0, chromeBlue: 0, halation: 0,
  proMist: 0, bleachBypass: 0, lightLeak: 0, chromatic: 0, skinProtect: 0,
  clarity: 0, texture: 0, dehaze: 0,
  swirlyBokeh: 0, depthFog: 0,
  darkroomProcess: 'None',
  diagnosticMode: 'Off',
  dateStamp: false,
  wb: 'Auto', red: 0, blue: 0, highlight: 0, shadow: 0, color: 0,
  sharpness: 0, nr: -4, exposure: 0, temperature: 0, rotation: 0,
  frame: 'None', frameStyle: 0,
  curvePoints: [[0, 0], [0.25, 0.25], [0.5, 0.5], [0.75, 0.75], [1, 1]],
  shadowsHue: 0, shadowsSat: 0,
  midtonesHue: 0, midtonesSat: 0,
  highlightsHue: 0, highlightsSat: 0
};

export const films = {
  'Original': [1, 1, 0, 0, 0],
  'Classic Chrome': [.77, 1.12, 3, 0, -3],
  'Classic Neg.': [.85, 1.22, 5, -3, -5],
  'PROVIA': [1.05, 1.05, 0, 0, 0],
  'Velvia': [1.4, 1.15, 2, 0, -2],
  'ASTIA': [.95, .91, 4, 0, 1],
  'ETERNA': [.7, .82, 1, 2, 4],
  'ACROS': [0, 1.17, 0, 0, 0],
  'Nostalgic Neg.': [.87, .96, 9, 3, -5]
};

const clamp = (v, lo = 0, hi = 255) => Math.max(lo, Math.min(hi, v));

export function parseCube(text) {
  let size = 0, rows = [], min = [0, 0, 0], max = [1, 1, 1], title = 'Custom LUT';
  for (let line of text.split(/\r?\n/)) {
    line = line.replace(/#.*/, '').trim();
    if (!line) continue;
    const p = line.split(/\s+/);
    if (p[0] === 'TITLE') {
      title = line.slice(5).trim().replace(/^"|"$/g, '');
      continue;
    }
    if (p[0] === 'LUT_1D_SIZE') throw Error('Chỉ hỗ trợ LUT 3D .cube, chưa hỗ trợ LUT 1D.');
    if (p[0] === 'LUT_3D_SIZE') { size = Number(p[1]); continue; }
    if (p[0] === 'DOMAIN_MIN') { min = p.slice(1).map(Number); continue; }
    if (p[0] === 'DOMAIN_MAX') { max = p.slice(1).map(Number); continue; }
    if (p.length !== 3 || p.some(v => !Number.isFinite(Number(v)))) {
      throw Error('Dòng LUT không hợp lệ: ' + line.slice(0, 60));
    }
    rows.push(...p.map(Number));
  }
  if (!Number.isInteger(size) || size < 2 || size > 65) throw Error('LUT cần kích thước từ 2 đến 65.');
  if (rows.length !== size ** 3 * 3) throw Error('Số điểm màu trong LUT không khớp kích thước.');
  if (min.length !== 3 || max.length !== 3 || min.some((x, i) => !Number.isFinite(x) || !Number.isFinite(max[i]) || max[i] <= x)) {
    throw Error('Miền màu LUT không hợp lệ.');
  }
  return { size, data: new Float32Array(rows), min, max, title };
}

export function sampleLut(lut, r, g, b) {
  const v = [r, g, b].map((x, i) => clamp((x / 255 - lut.min[i]) / (lut.max[i] - lut.min[i]), 0, 1) * (lut.size - 1));
  const low = v.map(Math.floor), f = v.map((x, i) => x - low[i]);
  let out = [0, 0, 0];
  for (let z = 0; z < 2; z++)
    for (let y = 0; y < 2; y++)
      for (let x = 0; x < 2; x++) {
        const idx = (Math.min(low[0] + x, lut.size - 1) + Math.min(low[1] + y, lut.size - 1) * lut.size + Math.min(low[2] + z, lut.size - 1) * lut.size ** 2) * 3;
        const w = (x ? f[0] : 1 - f[0]) * (y ? f[1] : 1 - f[1]) * (z ? f[2] : 1 - f[2]);
        for (let c = 0; c < 3; c++) out[c] += lut.data[idx + c] * 255 * w;
      }
  return out;
}

export function buildMonotoneCurveLut(points = defaults.curvePoints) {
  const lut = new Float32Array(256);
  if (!Array.isArray(points) || points.length < 2) {
    for (let i = 0; i < 256; i++) lut[i] = i;
    return lut;
  }
  const sorted = [...points]
    .map(p => [clamp(Number(p[0]), 0, 1), clamp(Number(p[1]), 0, 1)])
    .sort((a, b) => a[0] - b[0]);
  const n = sorted.length;
  const xs = sorted.map(p => p[0]);
  const ys = sorted.map(p => p[1]);
  const d = new Float32Array(n - 1);
  const ms = new Float32Array(n);

  for (let i = 0; i < n - 1; i++) {
    const dx = Math.max(1e-5, xs[i + 1] - xs[i]);
    d[i] = (ys[i + 1] - ys[i]) / dx;
  }
  ms[0] = d[0];
  for (let i = 1; i < n - 1; i++) {
    ms[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) * 0.5;
  }
  ms[n - 1] = d[n - 2];

  for (let i = 0; i < n - 1; i++) {
    if (Math.abs(d[i]) < 1e-6) {
      ms[i] = 0;
      ms[i + 1] = 0;
      continue;
    }
    const a = ms[i] / d[i];
    const b = ms[i + 1] / d[i];
    const h = Math.hypot(a, b);
    if (h > 3) {
      const t = 3 / h;
      ms[i] = t * a * d[i];
      ms[i + 1] = t * b * d[i];
    }
  }

  for (let idx = 0; idx < 256; idx++) {
    const x = idx / 255;
    if (x <= xs[0]) { lut[idx] = ys[0] * 255; continue; }
    if (x >= xs[n - 1]) { lut[idx] = ys[n - 1] * 255; continue; }
    let i = 0;
    while (i < n - 2 && xs[i + 1] < x) i++;
    const h = Math.max(1e-5, xs[i + 1] - xs[i]);
    const t = (x - xs[i]) / h;
    const t2 = t * t, t3 = t2 * t;
    const h00 = 2 * t3 - 3 * t2 + 1;
    const h10 = t3 - 2 * t2 + t;
    const h01 = -2 * t3 + 3 * t2;
    const h11 = t3 - t2;
    const y = h00 * ys[i] + h10 * h * ms[i] + h01 * ys[i + 1] + h11 * h * ms[i + 1];
    lut[idx] = clamp(y * 255, 0, 255);
  }
  return lut;
}

function hashNoise(x, y, seed = 0) {
  let h = Math.imul((x + seed * 131) ^ 0x27d4eb2d, 0x165667b1) ^ Math.imul((y + seed * 97) ^ 0x85ebca6b, 0xc2b2ae35);
  h ^= h >>> 15;
  h = Math.imul(h, 0x2c1b3c6d);
  h ^= h >>> 12;
  return ((h >>> 0) % 1024) / 1023 - 0.5;
}

function detectSkinWeight(r, g, b) {
  if (r < 60 || g < 40 || b < 20 || r <= g || g <= b * 0.85) return 0;
  const maxC = Math.max(r, g, b), minC = Math.min(r, g, b);
  if (maxC - minC < 15) return 0;
  const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
  const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
  if (cr >= 135 && cr <= 180 && cb >= 85 && cb <= 135) {
    const dist = Math.hypot((cr - 154) / 22, (cb - 112) / 22);
    return Math.max(0, 1 - dist);
  }
  return 0;
}

const ZONE_COLORS = [
  [18, 8, 36],     // Zone 0
  [48, 24, 96],    // Zone I
  [30, 75, 165],   // Zone II
  [32, 138, 196],  // Zone III
  [42, 178, 132],  // Zone IV
  [92, 204, 72],   // Zone V (18% Grey)
  [185, 222, 58],  // Zone VI
  [245, 210, 52],  // Zone VII
  [252, 152, 42],  // Zone VIII
  [245, 82, 52],   // Zone IX
  [255, 25, 145]   // Zone X
];

export function rotatePixels90(input, width, height, rotation = 0) {
  const steps = (((Math.round(rotation / 90) % 4) + 4) % 4);
  if (steps === 0) return { pixels: new Uint8ClampedArray(input), width, height };
  const outWidth = steps % 2 === 1 ? height : width;
  const outHeight = steps % 2 === 1 ? width : height;
  const out = new Uint8ClampedArray(input.length);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let nx = x, ny = y;
      if (steps === 1) { nx = height - 1 - y; ny = x; }
      else if (steps === 2) { nx = width - 1 - x; ny = height - 1 - y; }
      else if (steps === 3) { nx = y; ny = width - 1 - x; }
      const srcIdx = (y * width + x) * 4;
      const dstIdx = (ny * outWidth + nx) * 4;
      out[dstIdx] = input[srcIdx];
      out[dstIdx + 1] = input[srcIdx + 1];
      out[dstIdx + 2] = input[srcIdx + 2];
      out[dstIdx + 3] = input[srcIdx + 3];
    }
  }
  return { pixels: out, width: outWidth, height: outHeight };
}

export function processPixels(input, width, height, rawSettings, lut, frameSeed = 0) {
  const s = { ...defaults, ...rawSettings };
  const out = new Uint8ClampedArray(input.length), film = films[s.film] || films.Original;
  const exp = 2 ** s.exposure, wb = { Auto: 0, Daylight: 3, Cloudy: 9, Tungsten: -15 }[s.wb] || 0;
  const hasCurve = Array.isArray(s.curvePoints) && s.curvePoints.some((p, idx) => Math.abs(p[0] - p[1]) > 1e-3 || (idx === 0 && p[0] > 0));
  const curveLut = hasCurve ? buildMonotoneCurveLut(s.curvePoints) : null;
  const caShift = Math.round(clamp(s.chromatic || 0, 0, 4));
  const invW = 1 / Math.max(1, width), invH = 1 / Math.max(1, height);
  const cx = width * 0.5, cy = height * 0.5, maxRad = Math.max(1, Math.hypot(cx, cy));

  for (let i = 0; i < input.length; i += 4) {
    const pxIndex = i >>> 2;
    const x = pxIndex % width;
    const y = (pxIndex / width) | 0;

    let sampleX = x, sampleY = y;
    // Swirly Bokeh Ống kính Cổ Helios 44-2 58mm f/2 ở rìa ảnh
    if (s.swirlyBokeh > 0 && width > 4 && height > 4) {
      const dx = x - cx, dy = y - cy;
      const rNorm = Math.hypot(dx, dy) / maxRad;
      if (rNorm > 0.35) {
        const angle = ((rNorm - 0.35) ** 2) * (s.swirlyBokeh / 100) * 0.65;
        const cosA = Math.cos(angle), sinA = Math.sin(angle);
        sampleX = clamp(Math.round(cx + dx * cosA - dy * sinA), 0, width - 1);
        sampleY = clamp(Math.round(cy + dx * sinA + dy * cosA), 0, height - 1);
      }
    }

    const baseIdx = (sampleY * width + sampleX) * 4;
    let rIn = input[baseIdx], gIn = input[baseIdx + 1], bIn = input[baseIdx + 2];
    if (caShift > 0 && width > caShift * 2) {
      const rx = clamp(sampleX + (sampleX < cx ? -caShift : caShift), 0, width - 1);
      const bx = clamp(sampleX - (sampleX < cx ? -caShift : caShift), 0, width - 1);
      rIn = input[(sampleY * width + rx) * 4];
      bIn = input[(sampleY * width + bx) * 4 + 2];
    }

    let a = [rIn, gIn, bIn];
    const skinW = s.skinProtect > 0 ? detectSkinWeight(rIn, gIn, bIn) * (s.skinProtect / 100) : 0;

    // Dehaze (Khử mù sương dựa trên Dark Channel Prior)
    if (s.dehaze !== 0) {
      const dh = s.dehaze / 100;
      const darkChannel = Math.min(a[0], a[1], a[2]) / 255;
      if (dh > 0) {
        const trans = Math.max(0.22, 1 - dh * 0.72 * darkChannel);
        const atm = 235;
        a[0] = (a[0] - atm * (1 - trans)) / trans;
        a[1] = (a[1] - atm * (1 - trans)) / trans;
        a[2] = (a[2] - atm * (1 - trans)) / trans;
      } else {
        const mist = -dh * 0.55;
        a[0] = a[0] * (1 - mist) + 230 * mist;
        a[1] = a[1] * (1 - mist) + 235 * mist;
        a[2] = a[2] * (1 - mist) + 242 * mist;
      }
    }

    a[0] += s.red * 2 + s.temperature * .15 + wb;
    a[2] += s.blue * 2 - s.temperature * .15 - wb;
    const l = a[0] * .2126 + a[1] * .7152 + a[2] * .0722;
    const effectiveAmount = (s.amount / 100) * (1 - skinW * 0.45);

    let v = a.map((c, k) => {
      let filmc = ((l + (c - l) * film[0]) - 128) * film[1] + 128 + film[k + 2];
      let value = c + (filmc - c) * effectiveAmount;
      value *= exp;
      const lum = clamp(value / 255, 0, 1);
      value += s.highlight * 10 * lum ** 2 - s.shadow * 10 * (1 - lum) ** 2;
      value -= ((s.dr - 100) / 300) * 28 * lum ** 3;
      if (curveLut) value = curveLut[Math.round(clamp(value, 0, 255))];
      return value;
    });

    const lum = v[0] * .2126 + v[1] * .7152 + v[2] * .0722;
    v = v.map(c => lum + (c - lum) * (1 + s.color * .09 * (1 - skinW * 0.5)));

    if (s.chrome > 0 && film[0] > 0) {
      const maxC = Math.max(v[0], v[1], v[2]), minC = Math.min(v[0], v[1], v[2]);
      const sat = maxC > 1 ? (maxC - minC) / maxC : 0;
      if (sat > 0.45) {
        const factor = 1 - (sat - 0.45) * 0.14 * s.chrome;
        v[0] *= factor; v[1] *= factor; v[2] *= factor;
      }
    }

    if (s.chromeBlue > 0 && film[0] > 0) {
      const blueDom = Math.max(0, (v[2] - Math.max(v[0], v[1] * 0.85)) / 255);
      if (blueDom > 0.08) {
        const depth = 1 - blueDom * 0.16 * s.chromeBlue;
        v[0] *= depth; v[1] *= depth;
        v[2] = clamp(v[2] * (1 + blueDom * 0.06 * s.chromeBlue));
      }
    }

    if (s.bleachBypass > 0) {
      const bb = s.bleachBypass / 100;
      const curLum = clamp((v[0] * .2126 + v[1] * .7152 + v[2] * .0722) / 255, 0, 1);
      const silver = curLum < 0.5
        ? 2 * curLum * curLum * 255
        : (1 - 2 * (1 - curLum) * (1 - curLum)) * 255;
      for (let k = 0; k < 3; k++) {
        const desat = v[k] * (1 - 0.55 * bb) + (curLum * 255) * (0.55 * bb);
        v[k] = desat * (1 - 0.65 * bb) + silver * (0.65 * bb);
      }
    }

    // 3-Way Color Grading Wheels (Shadows · Midtones · Highlights OkLab/Luminance-weighted)
    if ((s.shadowsSat && s.shadowsSat > 0) || (s.midtonesSat && s.midtonesSat > 0) || (s.highlightsSat && s.highlightsSat > 0)) {
      const curL = clamp((v[0] * .2126 + v[1] * .7152 + v[2] * .0722) / 255, 0, 1);
      const wSh = Math.pow(1 - curL, 2);
      const wHi = Math.pow(curL, 2);
      const wMid = 4 * curL * (1 - curL);

      const applyWheel = (hueDeg, satPct, weight) => {
        if (!satPct || satPct <= 0 || weight <= 0) return;
        const rad = ((hueDeg || 0) * Math.PI) / 180;
        const rShift = Math.cos(rad);
        const gShift = Math.cos(rad - (2 * Math.PI / 3));
        const bShift = Math.cos(rad + (2 * Math.PI / 3));
        const mag = (satPct / 100) * weight * 60;
        v[0] = clamp(v[0] + rShift * mag, 0, 255);
        v[1] = clamp(v[1] + gShift * mag, 0, 255);
        v[2] = clamp(v[2] + bShift * mag, 0, 255);
      };

      applyWheel(s.shadowsHue, s.shadowsSat, wSh);
      applyWheel(s.midtonesHue, s.midtonesSat, wMid);
      applyWheel(s.highlightsHue, s.highlightsSat, wHi);
    }

    // Hóa chất Giấy Ảnh Phòng Tối Cổ Điển (Historic Darkroom Print Processes)
    if (s.darkroomProcess && s.darkroomProcess !== 'None') {
      const pLum = clamp((v[0] * .2126 + v[1] * .7152 + v[2] * .0722) / 255, 0, 1);
      if (s.darkroomProcess === 'SplitGrade') {
        // Rửa ảnh Đen Trắng 2 bộ lọc: Selenium lạnh ở vùng tối + Sepia kem ấm ở vùng sáng
        const shR = 215, shG = 222, shB = 238;
        const hiR = 252, hiG = 242, hiB = 218;
        const toneR = shR * (1 - pLum) + hiR * pLum;
        const toneG = shG * (1 - pLum) + hiG * pLum;
        const toneB = shB * (1 - pLum) + hiB * pLum;
        v[0] = pLum * toneR;
        v[1] = pLum * toneG;
        v[2] = pLum * toneB;
      } else if (s.darkroomProcess === 'Cyanotype') {
        // Bản in Xanh Phổ 1842 (Prussian Blue Cyanotype)
        v[0] = Math.pow(pLum, 1.35) * 238 + (1 - pLum) * 12;
        v[1] = Math.pow(pLum, 1.12) * 244 + (1 - pLum) * 58;
        v[2] = Math.pow(pLum, 0.85) * 248 + (1 - pLum) * 118;
      } else if (s.darkroomProcess === 'Platinum') {
        // Bản in Bạch Kim - Palladium cổ điển
        const curveP = pLum * 0.92 + 0.04;
        v[0] = curveP * 248;
        v[1] = curveP * 240;
        v[2] = curveP * 226;
      }
    }

    // 3D Depth Atmospheric Fog (Sương mù theo chiều sâu hậu cảnh)
    if (s.depthFog > 0) {
      const ny = y * invH;
      const distWeight = Math.max(0, 1 - ny * 0.85) * (1 - clamp(lum / 255, 0, 1) * 0.45);
      const fog = distWeight * (s.depthFog / 100) * 0.65;
      v[0] = v[0] * (1 - fog) + 225 * fog;
      v[1] = v[1] * (1 - fog) + 232 * fog;
      v[2] = v[2] * (1 - fog) + 240 * fog;
    }

    if (lut) {
      const mapped = sampleLut(lut, ...v);
      const lutWeight = (s.lutAmount / 100) * (1 - skinW * 0.45);
      v = v.map((c, k) => c + (mapped[k] - c) * lutWeight);
    }

    if (s.lightLeak > 0) {
      const nx = x * invW, ny = y * invH;
      const breath = 1 + Math.sin(frameSeed * 0.26) * 0.06;
      const leakLeft = Math.max(0, 1 - nx * 3.2) * (0.6 + 0.4 * Math.sin(ny * 4.5));
      const leakCorner = Math.max(0, 1 - Math.hypot(1 - nx, ny * 0.8) * 2.1);
      const leak = (leakLeft + leakCorner * 0.75) * (s.lightLeak / 100) * breath;
      if (leak > 0.01) {
        v[0] = 255 - (255 - clamp(v[0])) * Math.max(0, 1 - leak * 0.95);
        v[1] = 255 - (255 - clamp(v[1])) * Math.max(0, 1 - leak * 0.48);
        v[2] = 255 - (255 - clamp(v[2])) * Math.max(0, 1 - leak * 0.12);
      }
    }

    // Organic Dual-Band Film Grain (hỗ trợ frameSeed cho Living Film 24fps)
    let n = 0;
    if (s.grain > 0) {
      const seedOffset = Math.imul(frameSeed + 1, 747796405);
      const fine = (((Math.imul(i + 11 + seedOffset, 1597334677) >>> 0) % 1024) / 1023 - .5);
      const coarse = s.grainSize > 1 ? hashNoise(x >>> 1, y >>> 1, frameSeed) : fine;
      const normL = clamp(lum / 255, 0, 1);
      const midtoneMask = 0.35 + 0.65 * (4 * normL * (1 - normL));
      n = (fine * 0.65 + coarse * 0.35) * s.grain * midtoneMask;
    }

    for (let k = 0; k < 3; k++) out[i + k] = clamp(v[k] + n);
    out[i + 3] = input[i + 3];
  }

  // Black Pro-Mist Optical Diffusion & Halation
  if ((s.proMist > 0 || s.halation > 0) && width > 2 && height > 2) {
    const src = out.slice();
    const mistStrength = (s.proMist / 100) * 0.55;
    const halStrength = (s.halation / 100) * 0.45 * (1 + Math.cos(frameSeed * 0.26) * 0.05);
    for (let y = 0; y < height; y++) {
      const y0 = Math.max(0, y - 2), y1 = Math.min(height - 1, y + 2);
      for (let x = 0; x < width; x++) {
        const x0 = Math.max(0, x - 2), x1 = Math.min(width - 1, x + 2);
        const p = (y * width + x) * 4;
        const p1 = (y0 * width + x0) * 4, p2 = (y0 * width + x1) * 4;
        const p3 = (y1 * width + x0) * 4, p4 = (y1 * width + x1) * 4;

        if (mistStrength > 0) {
          const avgR = (src[p1] + src[p2] + src[p3] + src[p4]) * 0.25;
          const avgG = (src[p1 + 1] + src[p2 + 1] + src[p3 + 1] + src[p4 + 1]) * 0.25;
          const avgB = (src[p1 + 2] + src[p2 + 2] + src[p3 + 2] + src[p4 + 2]) * 0.25;
          const avgLum = avgR * .2126 + avgG * .7152 + avgB * .0722;
          if (avgLum > 135) {
            const bloom = ((avgLum - 135) / 120) * mistStrength;
            out[p]     = clamp(255 - (255 - out[p]) * (1 - bloom * 0.65));
            out[p + 1] = clamp(255 - (255 - out[p + 1]) * (1 - bloom * 0.62));
            out[p + 2] = clamp(255 - (255 - out[p + 2]) * (1 - bloom * 0.58));
          }
        }

        if (halStrength > 0) {
          const maxLum = Math.max(
            src[p1] * .2126 + src[p1 + 1] * .7152 + src[p1 + 2] * .0722,
            src[p2] * .2126 + src[p2 + 1] * .7152 + src[p2 + 2] * .0722,
            src[p3] * .2126 + src[p3 + 1] * .7152 + src[p3 + 2] * .0722,
            src[p4] * .2126 + src[p4 + 1] * .7152 + src[p4 + 2] * .0722
          );
          if (maxLum > 205) {
            const glow = (maxLum - 205) * halStrength;
            out[p] = clamp(out[p] + glow * 1.15);
            out[p + 1] = clamp(out[p + 1] + glow * 0.38);
          }
        }
      }
    }
  }

  // Tách tần số Clarity, Texture, Khử nhiễu & Làm nét
  if (s.sharpness !== 0 || s.nr > -4 || s.clarity !== 0 || s.texture !== 0) {
    const source = out.slice();
    const denoise = Math.max(0, (s.nr + 4) / 8) * .65;
    const sh = s.sharpness * .18;
    const texAmt = (s.texture / 100) * 0.55;
    const clarAmt = (s.clarity / 100) * 0.65;

    for (let y = 0; y < height; y++) {
      const yUp = Math.max(0, y - 1), yDown = Math.min(height - 1, y + 1);
      const yFarUp = Math.max(0, y - 2), yFarDown = Math.min(height - 1, y + 2);
      for (let x = 0; x < width; x++) {
        const xLeft = Math.max(0, x - 1), xRight = Math.min(width - 1, x + 1);
        const xFarLeft = Math.max(0, x - 2), xFarRight = Math.min(width - 1, x + 2);
        const p = (y * width + x) * 4;
        const pLeft = (y * width + xLeft) * 4, pRight = (y * width + xRight) * 4;
        const pUp = (yUp * width + x) * 4, pDown = (yDown * width + x) * 4;
        const pf1 = (yFarUp * width + xFarLeft) * 4, pf2 = (yFarUp * width + xFarRight) * 4;
        const pf3 = (yFarDown * width + xFarLeft) * 4, pf4 = (yFarDown * width + xFarRight) * 4;

        const normL = clamp((source[p] * .2126 + source[p + 1] * .7152 + source[p + 2] * .0722) / 255, 0, 1);
        const midMask = 4 * normL * (1 - normL);

        for (let c = 0; c < 3; c++) {
          const avgNear = (source[pLeft + c] + source[pRight + c] + source[pUp + c] + source[pDown + c]) * 0.25;
          const avgFar = (source[pf1 + c] + source[pf2 + c] + source[pf3 + c] + source[pf4 + c]) * 0.25;
          let v = source[p + c];
          if (s.nr > -4) v += (avgNear - v) * denoise;
          const highFreq = v - avgNear;
          const midFreq = avgNear - avgFar;
          v += highFreq * (sh + texAmt) + midFreq * clarAmt * midMask;
          out[p + c] = clamp(v);
        }
      }
    }
  }

  if (s.diagnosticMode === 'ZoneMap' || s.diagnosticMode === 'Zebra') {
    for (let i = 0; i < out.length; i += 4) {
      const pxIndex = i >>> 2;
      const x = pxIndex % width, y = (pxIndex / width) | 0;
      const lum = out[i] * .2126 + out[i + 1] * .7152 + out[i + 2] * .0722;
      if (s.diagnosticMode === 'ZoneMap') {
        const zone = Math.min(10, Math.max(0, Math.floor((lum / 255) * 11)));
        const zc = ZONE_COLORS[zone];
        out[i] = zc[0]; out[i + 1] = zc[1]; out[i + 2] = zc[2];
      } else if (s.diagnosticMode === 'Zebra') {
        const stripe = ((x + y) & 1) === 0;
        if (lum >= 246 && stripe) {
          out[i] = 255; out[i + 1] = 40; out[i + 2] = 40;
        } else if (lum <= 8 && stripe) {
          out[i] = 40; out[i + 1] = 140; out[i + 2] = 255;
        }
      }
    }
  }

  return out;
}

export function computeHistogramAndStats(pixels) {
  const rHist = new Uint32Array(256), gHist = new Uint32Array(256), bHist = new Uint32Array(256), lHist = new Uint32Array(256);
  const totalPixels = Math.max(1, pixels.length >>> 2);
  let rSum = 0, gSum = 0, bSum = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    const r = pixels[i], g = pixels[i + 1], b = pixels[i + 2];
    const l = clamp(Math.round(r * .2126 + g * .7152 + b * .0722));
    rHist[r]++; gHist[g]++; bHist[b]++; lHist[l]++;
    rSum += r; gSum += g; bSum += b;
  }
  let acc = 0, p05 = 0, p50 = 128, p95 = 255;
  const t05 = totalPixels * 0.05, t50 = totalPixels * 0.50, t95 = totalPixels * 0.95;
  for (let i = 0; i < 256; i++) {
    acc += lHist[i];
    if (acc >= t05 && p05 === 0) p05 = i;
    if (acc >= t50 && p50 === 128) p50 = i;
    if (acc >= t95) { p95 = i; break; }
  }
  const rMean = rSum / totalPixels, gMean = gSum / totalPixels, bMean = bSum / totalPixels;
  return {
    rHist, gHist, bHist, lHist,
    p05, p50, p95,
    rMean, gMean, bMean,
    redCast: (rMean - gMean) / 255,
    blueCast: (bMean - gMean) / 255
  };
}

export function autoBalanceSettings(pixels, current = defaults) {
  if (!pixels || pixels.length < 4) return { settings: { ...current }, reasonVi: 'Ảnh rỗng, giữ nguyên thông số.' };
  const stats = computeHistogramAndStats(pixels);
  const exposure = clamp(Math.round(((126 - stats.p50) / 96) * 3) / 3, -1.33, 1.33);
  const highlight = stats.p95 > 235 ? -1 : stats.p95 < 185 ? 1 : 0;
  const shadow = stats.p05 < 20 ? 1 : stats.p05 > 65 ? -1 : 0;
  const dr = stats.p95 - stats.p05 > 210 ? 400 : stats.p95 - stats.p05 > 165 ? 200 : 100;
  const red = clamp(Math.round(-stats.redCast * 18), -4, 4);
  const blue = clamp(Math.round(-stats.blueCast * 18), -4, 4);
  const recommendedFilm = stats.redCast > 0.05 ? 'Classic Chrome' : stats.blueCast > 0.05 ? 'Nostalgic Neg.' : 'PROVIA';
  return {
    settings: {
      ...current,
      film: current.film === 'Original' ? recommendedFilm : current.film,
      exposure, highlight, shadow, dr, red, blue,
      chrome: dr >= 200 ? 1 : 0
    },
    reasonVi: `AutoBalance: trung vị sáng ${stats.p50}/255 (EV ${exposure >= 0 ? '+' : ''}${exposure.toFixed(2)}), DR${dr}, gợi ý phim ${recommendedFilm}.`
  };
}

export function promptToRecipeVi(promptText, base = defaults) {
  const q = (promptText || '').toLowerCase();
  const next = { ...defaults, ...base, diagnosticMode: 'Off' };
  if (q.includes('cyanotype') || q.includes('xanh phổ') || q.includes('1842')) {
    Object.assign(next, { darkroomProcess: 'Cyanotype', contrast: 1, clarity: 25, grain: 8 });
    return { settings: next, reasonVi: 'Đã áp dụng quy trình in Xanh Phổ Cyanotype 1842.' };
  }
  if (q.includes('đen trắng') || q.includes('acros') || q.includes('phóng sự') || q.includes('bw')) {
    Object.assign(next, { film: 'ACROS', darkroomProcess: 'SplitGrade', grain: 18, grainSize: 2, highlight: 1, shadow: 2, clarity: 30, sharpness: 2 });
    return { settings: next, reasonVi: 'Đã tạo công thức Đen Trắng Phóng Sự (ACROS + Split-Grade Selenium/Sepia + Clarity +30).' };
  }
  if (q.includes('đà lạt') || q.includes('mưa') || q.includes('buồn') || q.includes('lạnh') || q.includes('sương mù')) {
    Object.assign(next, { film: 'ETERNA', wb: 'Tungsten', temperature: -18, blue: 3, red: -2, dr: 400, proMist: 35, depthFog: 30, grain: 8, shadow: -1 });
    return { settings: next, reasonVi: 'Đã tạo công thức Điện ảnh Trầm Lạnh (ETERNA + Pro-Mist + Sương mù 3D Depth Fog).' };
  }
  if (q.includes('hà nội') || q.includes('hoài niệm') || q.includes('cổ điển') || q.includes('nắng') || q.includes('hoàng hôn') || q.includes('helios')) {
    Object.assign(next, { film: 'Nostalgic Neg.', wb: 'Cloudy', temperature: 24, red: 3, blue: -3, chrome: 2, proMist: 30, halation: 35, lightLeak: 20, swirlyBokeh: 35, dateStamp: true, grain: 8, grainSize: 2 });
    return { settings: next, reasonVi: 'Đã tạo công thức Hoài Niệm Nắng Ấm (Nostalgic Neg. + Bokeh xoáy Helios + Dấu ngày tháng Quartz).' };
  }
  if (q.includes('đêm') || q.includes('neon') || q.includes('cyber') || q.includes('phố đêm') || q.includes('cinestill')) {
    Object.assign(next, { film: 'Classic Neg.', wb: 'Tungsten', red: 2, blue: 3, highlight: -1, shadow: 1, halation: 65, proMist: 40, clarity: 20, grain: 8, chromatic: 1 });
    return { settings: next, reasonVi: 'Đã tạo công thức Phim Đêm CineStill 800T (Classic Neg. + Quầng đỏ Halation 65% + Pro-Mist).' };
  }
  Object.assign(next, { film: 'Classic Chrome', dr: 200, chrome: 1, chromeBlue: 1, grain: 8, proMist: 15, clarity: 15, color: 1 });
  return { settings: next, reasonVi: 'Đã tạo công thức Fujifilm Đường Phố Đa Dụng (Classic Chrome + Clarity +15 + Pro-Mist nhẹ).' };
}

export function encodeRecipeCode(s) {
  const payload = [
    s.film, s.amount, s.lutAmount, s.dr, s.grain, s.grainSize || 1,
    s.chrome || 0, s.chromeBlue || 0, s.halation || 0, s.wb,
    s.red, s.blue, s.highlight, s.shadow, s.color, s.sharpness, s.nr,
    Number(s.exposure).toFixed(2), s.temperature,
    s.proMist || 0, s.bleachBypass || 0, s.lightLeak || 0, s.chromatic || 0, s.skinProtect || 0,
    s.clarity || 0, s.texture || 0, s.dehaze || 0, s.swirlyBokeh || 0, s.depthFog || 0,
    s.darkroomProcess || 'None', s.dateStamp ? 1 : 0
  ].join('|');
  const b64 = typeof Buffer !== 'undefined'
    ? Buffer.from(payload, 'utf8').toString('base64url')
    : btoa(unescape(encodeURIComponent(payload))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `IRO1-${b64}`;
}

export function decodeRecipeCode(code) {
  if (typeof code !== 'string' || !code.startsWith('IRO1-')) throw Error('Mã IRO Recipe không hợp lệ.');
  const raw = code.slice(5);
  const str = typeof Buffer !== 'undefined'
    ? Buffer.from(raw, 'base64url').toString('utf8')
    : decodeURIComponent(escape(atob(raw.replace(/-/g, '+').replace(/_/g, '/'))));
  const p = str.split('|');
  if (p.length < 19) throw Error('Mã IRO Recipe bị thiếu trường dữ liệu.');
  return {
    ...defaults,
    film: films[p[0]] ? p[0] : 'Original',
    amount: clamp(Number(p[1]), 0, 100),
    lutAmount: clamp(Number(p[2]), 0, 100),
    dr: [100, 200, 400].includes(Number(p[3])) ? Number(p[3]) : 100,
    grain: [0, 8, 18].includes(Number(p[4])) ? Number(p[4]) : 0,
    grainSize: [1, 2].includes(Number(p[5])) ? Number(p[5]) : 1,
    chrome: [0, 1, 2].includes(Number(p[6])) ? Number(p[6]) : 0,
    chromeBlue: [0, 1, 2].includes(Number(p[7])) ? Number(p[7]) : 0,
    halation: clamp(Number(p[8]), 0, 100),
    wb: ['Auto', 'Daylight', 'Cloudy', 'Tungsten'].includes(p[9]) ? p[9] : 'Auto',
    red: clamp(Number(p[10]), -9, 9),
    blue: clamp(Number(p[11]), -9, 9),
    highlight: clamp(Number(p[12]), -2, 4),
    shadow: clamp(Number(p[13]), -2, 4),
    color: clamp(Number(p[14]), -4, 4),
    sharpness: clamp(Number(p[15]), -4, 4),
    nr: clamp(Number(p[16]), -4, 4),
    exposure: clamp(Number(p[17]), -3, 3),
    temperature: clamp(Number(p[18]), -100, 100),
    proMist: p[19] !== undefined ? clamp(Number(p[19]), 0, 100) : 0,
    bleachBypass: p[20] !== undefined ? clamp(Number(p[20]), 0, 100) : 0,
    lightLeak: p[21] !== undefined ? clamp(Number(p[21]), 0, 100) : 0,
    chromatic: p[22] !== undefined ? clamp(Number(p[22]), 0, 4) : 0,
    skinProtect: p[23] !== undefined ? clamp(Number(p[23]), 0, 100) : 0,
    clarity: p[24] !== undefined ? clamp(Number(p[24]), -100, 100) : 0,
    texture: p[25] !== undefined ? clamp(Number(p[25]), -100, 100) : 0,
    dehaze: p[26] !== undefined ? clamp(Number(p[26]), -100, 100) : 0,
    swirlyBokeh: p[27] !== undefined ? clamp(Number(p[27]), 0, 100) : 0,
    depthFog: p[28] !== undefined ? clamp(Number(p[28]), 0, 100) : 0,
    darkroomProcess: ['None', 'SplitGrade', 'Cyanotype', 'Platinum'].includes(p[29]) ? p[29] : 'None',
    dateStamp: p[30] === '1'
  };
}

export function embedRecipeIntoPixels(pixels, settings) {
  const out = new Uint8ClampedArray(pixels);
  const code = encodeRecipeCode(settings);
  const magic = `IRO_STEGO:${code}\0`;
  const maxBits = magic.length * 8;
  if (Math.floor(out.length / 4) * 3 < maxBits) return out;
  for (let bitIdx = 0; bitIdx < maxBits; bitIdx++) {
    const charCode = magic.charCodeAt(bitIdx >>> 3);
    const bit = (charCode >>> (7 - (bitIdx & 7))) & 1;
    const px = Math.floor(bitIdx / 3);
    const ch = bitIdx % 3;
    const idx = px * 4 + ch;
    out[idx] = (out[idx] & 0xFE) | bit;
  }
  return out;
}

export function extractRecipeFromPixels(pixels) {
  if (!pixels || pixels.length < 400 * 4) return null;
  const maxBits = Math.floor(pixels.length / 4) * 3;
  const maxChars = Math.min(320, Math.floor(maxBits / 8));
  let chars = '';
  for (let c = 0; c < maxChars; c++) {
    let byte = 0;
    for (let b = 0; b < 8; b++) {
      const bitIdx = c * 8 + b;
      const px = Math.floor(bitIdx / 3);
      const ch = bitIdx % 3;
      const bit = pixels[px * 4 + ch] & 1;
      byte = (byte << 1) | bit;
    }
    if (byte === 0) break;
    chars += String.fromCharCode(byte);
    if (c === 9 && !chars.startsWith('IRO_STEGO:')) return null;
  }
  if (!chars.startsWith('IRO_STEGO:')) return null;
  try {
    return decodeRecipeCode(chars.slice(10));
  } catch {
    return null;
  }
}

export function bakeRecipeToCube(rawSettings, size = 17, title = 'IRO Studio Custom Recipe') {
  if (!Number.isInteger(size) || size < 2 || size > 65) throw Error('Kích thước LUT xuất cần từ 2 đến 65.');
  const total = size ** 3;
  const grid = new Uint8ClampedArray(total * 4);
  let ptr = 0;
  for (let b = 0; b < size; b++) {
    for (let g = 0; g < size; g++) {
      for (let r = 0; r < size; r++) {
        grid[ptr++] = Math.round((r / (size - 1)) * 255);
        grid[ptr++] = Math.round((g / (size - 1)) * 255);
        grid[ptr++] = Math.round((b / (size - 1)) * 255);
        grid[ptr++] = 255;
      }
    }
  }
  const cleanSettings = {
    ...defaults, ...rawSettings,
    grain: 0, halation: 0, proMist: 0, lightLeak: 0, chromatic: 0,
    clarity: 0, texture: 0, swirlyBokeh: 0, depthFog: 0,
    sharpness: 0, nr: -4, diagnosticMode: 'Off'
  };
  const baked = processPixels(grid, total, 1, cleanSettings);
  const lines = [
    `TITLE "${title.replace(/"/g, '')}"`,
    `# Generated by IRO Studio 3.0 Flagship`,
    `LUT_3D_SIZE ${size}`,
    `DOMAIN_MIN 0.0 0.0 0.0`,
    `DOMAIN_MAX 1.0 1.0 1.0`
  ];
  for (let i = 0; i < baked.length; i += 4) {
    lines.push(`${(baked[i] / 255).toFixed(6)} ${(baked[i + 1] / 255).toFixed(6)} ${(baked[i + 2] / 255).toFixed(6)}`);
  }
  return lines.join('\n');
}

export function extractDominantPalette(pixels, count = 5) {
  const buckets = new Map();
  const step = Math.max(4, Math.floor(pixels.length / (4 * 2000)) * 4);
  for (let i = 0; i < pixels.length; i += step) {
    if (pixels[i + 3] < 128) continue;
    const rq = pixels[i] >>> 5, gq = pixels[i + 1] >>> 5, bq = pixels[i + 2] >>> 5;
    const key = (rq << 6) | (gq << 3) | bq;
    const entry = buckets.get(key) || { count: 0, r: 0, g: 0, b: 0 };
    entry.count++;
    entry.r += pixels[i];
    entry.g += pixels[i + 1];
    entry.b += pixels[i + 2];
    buckets.set(key, entry);
  }
  const sorted = [...buckets.values()].sort((a, b) => b.count - a.count);
  const selected = [];
  for (const item of sorted) {
    if (selected.length >= count) break;
    const r = Math.round(item.r / item.count);
    const g = Math.round(item.g / item.count);
    const b = Math.round(item.b / item.count);
    const tooClose = selected.some(s => Math.hypot(s.r - r, s.g - g, s.b - b) < 38);
    if (!tooClose) {
      const hex = '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
      selected.push({ r, g, b, hex });
    }
  }
  while (selected.length < count && sorted[selected.length]) {
    const item = sorted[selected.length];
    const r = Math.round(item.r / item.count), g = Math.round(item.g / item.count), b = Math.round(item.b / item.count);
    const hex = '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
    selected.push({ r, g, b, hex });
  }
  return selected;
}

export function matchColorFromReference(sourcePixels, refPixels, baseSettings = defaults) {
  const srcStats = computeHistogramAndStats(sourcePixels);
  const refStats = computeHistogramAndStats(refPixels);
  const evDiff = clamp(((refStats.p50 - srcStats.p50) / 85), -2, 2);
  const exposure = Math.round(evDiff * 3) / 3;
  const redShift = clamp(Math.round((refStats.redCast - srcStats.redCast) * 22), -9, 9);
  const blueShift = clamp(Math.round((refStats.blueCast - srcStats.blueCast) * 22), -9, 9);
  const tempShift = clamp(Math.round(((refStats.rMean - refStats.bMean) - (srcStats.rMean - srcStats.bMean)) * 0.6), -100, 100);
  const refSpan = refStats.p95 - refStats.p05;
  const film = refSpan < 140 ? 'ETERNA' : refStats.redCast > 0.06 ? 'Nostalgic Neg.' : 'Classic Chrome';
  return {
    settings: {
      ...baseSettings,
      film,
      exposure,
      red: redShift,
      blue: blueShift,
      temperature: tempShift,
      dr: refSpan > 200 ? 400 : 200,
      chrome: 1
    },
    reasonVi: `Đã sao chép bảng màu từ ảnh tham chiếu (gợi ý ${film}, EV ${exposure >= 0 ? '+' : ''}${exposure.toFixed(2)}, Temp ${tempShift >= 0 ? '+' : ''}${tempShift}).`
  };
}

export const PHOTO_FRAMES = [
  { id: 0, key: 'None', name: 'Không chèn thông tin' },
  { id: 1, key: 'Classic', name: 'Classic · dải trắng' },
  { id: 2, key: 'White', name: 'White · cột thông tin' },
  { id: 3, key: 'Glass', name: 'Glass · chữ trên ảnh' },
  { id: 4, key: 'Dark', name: 'Dark · dải đen' },
  { id: 5, key: 'Centered', name: 'Centered · thông số giữa' },
  { id: 6, key: 'Gallery', name: 'Gallery · viền trắng rộng' },
  { id: 7, key: 'Noir', name: 'Noir · viền đen rộng' },
  { id: 8, key: 'Instant', name: 'Instant · ảnh lấy liền' },
  { id: 9, key: 'Vintage', name: 'Vintage · giấy kem' },
  { id: 10, key: 'Film35', name: 'Film 35 · viền lỗ phim' },
  { id: 11, key: 'Cinema', name: 'Cinema · thanh đen' },
  { id: 12, key: 'Blur', name: 'Blur · nền ảnh mờ' },
  { id: 13, key: 'Palette', name: 'Palette · bảng màu ảnh' },
  { id: 14, key: 'Postcard', name: 'Postcard · bưu thiếp' },
  { id: 15, key: 'Editorial', name: 'Editorial · trang tạp chí' },
  { id: 16, key: 'Double', name: 'Double · viền kép' },
  { id: 17, key: 'Date', name: 'Date · dấu ngày màu cam' },
  { id: 18, key: 'Signature', name: 'Signature · chữ ký tối giản' },
  { id: 19, key: 'Float', name: 'Float · ảnh nổi đổ bóng' },
  { id: 20, key: 'Soft', name: 'Soft · viền bo tròn' },
  { id: 21, key: 'Torn', name: 'Torn · giấy xé' },
  { id: 22, key: 'Tape', name: 'Tape · băng dính scrapbook' },
  { id: 23, key: 'Wood', name: 'Wood · khung gỗ' },
  { id: 24, key: 'Gold', name: 'Gold · viền kim loại' },
  { id: 25, key: 'Neon', name: 'Neon · ánh sáng viền' },
  { id: 26, key: 'Notebook', name: 'Notebook · giấy kẻ ô' },
  { id: 27, key: 'Ticket', name: 'Ticket · vé kỷ niệm' },
  { id: 28, key: 'Blueprint', name: 'Blueprint · bản vẽ xanh' },
  { id: 29, key: 'Arch', name: 'Arch · vòm triển lãm' },
  { id: 30, key: 'Corners', name: 'Corners · góc giữ ảnh' }
];

export const defaultPhotoInfo = {
  camera: 'FUJIFILM X-T5',
  lens: 'XF 35mm F1.4 R',
  focal: '35mm',
  aperture: 'f/1.4',
  shutter: '1/250s',
  iso: 'ISO 400',
  date: '2026.10.14',
  author: 'IRO Studio'
};

export function parseFujiRecipe(recipe, base = defaults) {
  const raw = recipe.rawSettings || {};
  const s = { ...base };
  const notes = [];

  if (recipe.film) s.film = recipe.film;

  const parseNum = (text, fallback = 0) => {
    if (!text) return fallback;
    const m = String(text).trim().match(/^[+−–-]?\d+(?:\.\d+)?(?:\/\d+)?/);
    if (!m) return fallback;
    try {
      const clean = m[0].replace(/[−–]/g, '-');
      if (clean.includes('/')) {
        const [num, den] = clean.split('/').map(Number);
        return den !== 0 ? num / den : fallback;
      }
      return Number(clean);
    } catch {
      return fallback;
    }
  };

  const bound = (v, lo, hi) => Math.max(lo, Math.min(hi, Number.isFinite(v) ? v : lo));

  for (const [key, value] of Object.entries(raw)) {
    if (typeof value !== 'string') continue;
    const k = key.toLowerCase().trim();
    const v = value.toLowerCase().trim();
    const num = parseNum(value, 0);

    if (/\b(?:to|or|sometimes|depending|usually|occasionally)\b/.test(v)) {
      notes.push(`${key}: có khoảng lựa chọn; xem ghi chú gốc.`);
    }

    if (k === 'film simulation') {
      s.film = value;
    } else if (k === 'highlight' || k === 'highlights' || k === 'highlight tone') {
      s.highlight = bound(num, -2, 4);
    } else if (k === 'shadow' || k === 'shadows' || k === 'shadow tone') {
      s.shadow = bound(num, -2, 4);
    } else if (k === 'highlight & shadow') {
      s.highlight = bound(num, -2, 4);
      s.shadow = bound(num, -2, 4);
    } else if (k === 'color' || k === 'saturation') {
      s.color = bound(num, -4, 4);
    } else if (k === 'sharpness' || k === 'sharpeness' || k === 'sharpening') {
      s.sharpness = bound(num, -4, 4);
    } else if (k === 'noise reduction' || k === 'noise-reduction' || k === 'high iso nr') {
      s.nr = bound(num, -4, 4);
    } else if (k === 'clarity') {
      s.clarity = bound(num * 20, -100, 100);
    } else if (k === 'grain' || k === 'grain effect') {
      s.grain = v.includes('strong') ? 18 : (v.includes('weak') ? 8 : 0);
      s.grainSize = v.includes('large') ? 2 : 1;
    } else if (k === 'color chrome' || k === 'color chrome effect') {
      s.chrome = v.includes('strong') ? 2 : (v.includes('weak') ? 1 : 0);
    } else if (k === 'color chrome fx blue' || k === 'color chrome effect blue') {
      s.chromeBlue = v.includes('strong') ? 2 : (v.includes('weak') ? 1 : 0);
    } else if (k === 'dynamic range' || k === 'dr') {
      s.dr = v.includes('400') ? 400 : (v.includes('200') ? 200 : 100);
      if (v.includes('auto')) notes.push('DR Auto: giữ DR100 trong web editor.');
    } else if (k === 'white balance' || k === 'wb') {
      s.wb = value.split(',')[0].trim();
      const redM = value.match(/([+−–-]?\d+)\s*Red/i);
      const blueM = value.match(/([+−–-]?\d+)\s*Blue/i);
      if (redM) s.red = bound(parseNum(redM[1], 0), -9, 9);
      if (blueM) s.blue = bound(parseNum(blueM[1], 0), -9, 9);
    } else if (k === 'exposure compensation' || k === 'exposure') {
      if (v.includes(' to ') || v.includes(' or ')) {
        s.exposure = 0;
      } else {
        s.exposure = bound(parseNum(value, 0), -3, 3);
      }
    }
  }

  return { settings: s, notes, name: recipe.name || 'Fuji Recipe', film: s.film, sensor: (recipe.sensors || []).join(' / ') };
}

function resolveFrameIndex(styleIdOrKey) {
  if (typeof styleIdOrKey === 'number') {
    return Math.max(0, Math.min(30, Math.round(styleIdOrKey)));
  }
  if (!styleIdOrKey || styleIdOrKey === 'None') return 0;
  const legacyMap = {
    'GalleryWhite': 6,
    'DarkroomBlack': 4,
    'Film35mm': 10,
    'HasselbladV': 16,
    'PostcardGPS': 14
  };
  if (legacyMap[styleIdOrKey]) return legacyMap[styleIdOrKey];
  const found = PHOTO_FRAMES.findIndex(f => f.key.toLowerCase() === String(styleIdOrKey).toLowerCase() || f.name.toLowerCase().includes(String(styleIdOrKey).toLowerCase()));
  return found >= 0 ? found : 0;
}

function drawFittedText(ctx, text, x, y, maxWidth, size, color, bold = false, align = 'left') {
  if (!text || maxWidth <= 0) return;
  ctx.save();
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = 'alphabetic';
  let curSize = Math.max(9, size);
  ctx.font = `${bold ? '700 ' : '400 '}${curSize}px system-ui, -apple-system, sans-serif`;
  let measured = ctx.measureText(text).width;
  if (measured > maxWidth) {
    curSize = Math.max(9, curSize * (maxWidth / measured));
    ctx.font = `${bold ? '700 ' : '400 '}${curSize}px system-ui, -apple-system, sans-serif`;
    measured = ctx.measureText(text).width;
  }
  let str = text;
  if (measured > maxWidth) {
    while (str.length > 3 && ctx.measureText(str + '...').width > maxWidth) {
      str = str.slice(0, -1);
    }
    str += '...';
  }
  ctx.fillText(str, x, y);
  ctx.restore();
}

export function renderPhotoFrame(sourceCanvas, pixels, settings = defaults, info = defaultPhotoInfo, styleInput = 0) {
  const style = resolveFrameIndex(styleInput);
  if (style === 0 || typeof document === 'undefined') return sourceCanvas;

  const w = sourceCanvas.width;
  const h = sourceCanvas.height;
  const safeInfo = {
    camera: info?.camera || 'FUJIFILM X-T5',
    lens: info?.lens || 'XF 35mm F1.4 R',
    focal: info?.focal || '35mm',
    aperture: info?.aperture || 'f/1.4',
    shutter: info?.shutter || '1/250s',
    iso: info?.iso || 'ISO 400',
    date: info?.date || '2026.10.14',
    author: info?.author || (settings.film ? `FUJIFILM ${settings.film}` : 'IRO Studio')
  };
  const settingsStr = [safeInfo.focal, safeInfo.aperture, safeInfo.shutter, safeInfo.iso].filter(Boolean).join('  ');
  const title = safeInfo.camera || 'PHOTO';
  const bottomStr = [safeInfo.date, safeInfo.author].filter(Boolean).join('  ·  ');

  // Nhóm cơ bản 1..3
  if (style === 1) { // Classic
    const pad = Math.max(1, Math.round(w * 0.025));
    const footer = Math.max(1, Math.round(w * 0.125));
    const out = Object.assign(document.createElement('canvas'), { width: w, height: h + footer });
    const ctx = out.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, out.width, out.height);
    ctx.drawImage(sourceCanvas, 0, 0);

    drawFittedText(ctx, title, pad, h + footer * 0.37, w * 0.43, w * 0.028, '#202520', true);
    drawFittedText(ctx, safeInfo.author, pad, h + footer * 0.70, w * 0.43, w * 0.018, '#687068', false);
    drawFittedText(ctx, settingsStr, w * 0.49, h + footer * 0.37, w * 0.485, w * 0.020, '#353b35', false);
    drawFittedText(ctx, safeInfo.date, w * 0.49, h + footer * 0.70, w * 0.485, w * 0.018, '#687068', false);
    return out;
  }

  if (style === 2) { // White (Cột thông tin bên phải)
    const pad = Math.max(1, Math.round(w * 0.025));
    const side = Math.max(1, Math.round(w * 0.30));
    const ow = w + side + pad * 2, oh = h + pad * 2;
    const out = Object.assign(document.createElement('canvas'), { width: ow, height: oh });
    const ctx = out.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, ow, oh);
    ctx.drawImage(sourceCanvas, pad, pad);

    const x = w + pad * 2, width = side - pad, center = oh * 0.5;
    const unit = Math.min(w, h * 1.7);
    drawFittedText(ctx, title, x, center - unit * 0.07, width, unit * 0.032, '#202520', true);
    const rows = [safeInfo.focal, [safeInfo.aperture, safeInfo.shutter].filter(Boolean).join('  '), safeInfo.iso, safeInfo.date, safeInfo.author];
    let y = center - unit * 0.025;
    for (const row of rows) {
      if (row) {
        drawFittedText(ctx, row, x, y, width, unit * 0.019, '#687068', false);
        y += unit * 0.032;
      }
    }
    return out;
  }

  if (style === 3) { // Glass (Dải mờ đè chân ảnh)
    const out = Object.assign(document.createElement('canvas'), { width: w, height: h });
    const ctx = out.getContext('2d');
    ctx.drawImage(sourceCanvas, 0, 0);

    const pad = Math.max(1, Math.round(w * 0.025));
    const bar = Math.min(h * 0.35, w * 0.15);
    const top = h - bar;
    const unit = bar / 0.15;
    const grad = ctx.createLinearGradient(0, top, 0, h);
    grad.addColorStop(0, 'rgba(0, 0, 0, 0.03)');
    grad.addColorStop(1, 'rgba(0, 0, 0, 0.82)');
    ctx.fillStyle = grad;
    ctx.fillRect(0, top, w, bar);

    drawFittedText(ctx, title, pad, top + bar * 0.39, w - pad * 2, unit * 0.031, '#ffffff', true);
    drawFittedText(ctx, settingsStr, pad, top + bar * 0.65, w - pad * 2, unit * 0.020, '#eeeeee', false);
    drawFittedText(ctx, bottomStr, pad, top + bar * 0.87, w - pad * 2, unit * 0.018, '#dddddd', false);
    return out;
  }

  // Nhóm mở rộng 4..18
  if (style >= 4 && style <= 18) {
    const unit = Math.min(w, h * 1.7);
    let pad = Math.max(1, Math.round(w * 0.05));
    let foot = Math.max(1, Math.round(w * 0.15));
    const overlay = (style === 17 || style === 18);

    if (style === 4 || style === 5) { pad = 0; foot = Math.max(1, Math.round(w * 0.14)); }
    if (style === 6 || style === 7) pad = Math.max(1, Math.round(w * 0.09));
    if (style === 8 || style === 9) foot = Math.max(1, Math.round(w * 0.23));
    if (style === 10) pad = Math.max(1, Math.round(w * 0.055));
    if (style === 11) { pad = 0; foot = Math.max(1, Math.round(w * 0.12)); }
    if (style === 15) { pad = Math.max(1, Math.round(w * 0.04)); foot = Math.max(1, Math.round(w * 0.20)); }
    if (overlay) { pad = 0; foot = 0; }

    const top = (style === 11) ? foot : pad;
    const ow = w + pad * 2;
    const oh = h + top + pad + foot;

    const dark = (style === 4 || style === 7 || style === 10 || style === 11 || style === 12);
    const bg = dark ? '#151716' : (style === 9 || style === 14 ? '#f1eadb' : '#ffffff');
    const ink = dark ? '#f2f0e7' : '#252b28';
    const muted = dark ? '#b6bcb4' : '#68716a';

    const out = Object.assign(document.createElement('canvas'), { width: ow, height: oh });
    const ctx = out.getContext('2d');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, ow, oh);

    if (style === 12) { // Blur
      ctx.drawImage(sourceCanvas, 0, 0, ow, oh);
      ctx.fillStyle = 'rgba(0,0,0,0.42)';
      ctx.fillRect(0, 0, ow, oh);
      ctx.fillStyle = 'rgba(0,0,0,0.35)';
      ctx.fillRect(pad - w * 0.008, top - w * 0.008, w + w * 0.016, h + w * 0.026);
    }

    ctx.drawImage(sourceCanvas, pad, top);

    if (overlay) {
      const margin = w * 0.035;
      const base = h - unit * 0.035;
      ctx.save();
      ctx.shadowColor = 'rgba(0, 0, 0, 0.75)';
      ctx.shadowBlur = Math.round(unit * 0.01);
      if (style === 17) {
        drawFittedText(ctx, `'26 10 14  ${safeInfo.date}`, margin, base, w - margin * 2, unit * 0.036, '#ffaa54', true, 'left');
      } else {
        drawFittedText(ctx, safeInfo.author || title, margin, base, w - margin * 2, unit * 0.028, '#ffffff', false, 'left');
      }
      ctx.restore();
      return out;
    }

    const x = Math.max(pad, w * 0.025);
    let area = ow - x * 2;
    const y = top + h + foot * 0.35;
    if (style === 13) area -= w * 0.25;

    if (style === 8 || style === 9) { // Instant / Vintage
      drawFittedText(ctx, safeInfo.author || title, x, top + h + foot * 0.40, area, w * 0.038, ink, true);
      drawFittedText(ctx, settingsStr, x, top + h + foot * 0.65, area, w * 0.020, muted, false);
      drawFittedText(ctx, safeInfo.date, x, top + h + foot * 0.82, area, w * 0.018, muted, false);
    } else if (style === 14) { // Postcard
      ctx.strokeStyle = '#b9ad98';
      ctx.lineWidth = Math.max(1, w * 0.001);
      ctx.beginPath();
      ctx.moveTo(ow * 0.62, top + h + foot * 0.15);
      ctx.lineTo(ow * 0.62, oh - pad * 0.3);
      ctx.stroke();

      drawFittedText(ctx, title, x, y, ow * 0.55, w * 0.030, ink, true);
      drawFittedText(ctx, safeInfo.author, x, y + foot * 0.27, ow * 0.55, w * 0.019, muted, false);
      drawFittedText(ctx, settingsStr, ow * 0.65, y, ow * 0.31, w * 0.017, muted, false);
      drawFittedText(ctx, safeInfo.date, ow * 0.65, y + foot * 0.27, ow * 0.31, w * 0.017, muted, false);
    } else if (style === 15) { // Editorial
      drawFittedText(ctx, 'PHOTOGRAPH / ' + title, x, top + h + foot * 0.30, area, w * 0.032, ink, true);
      ctx.fillStyle = ink;
      ctx.fillRect(x, top + h + foot * 0.43, ow - x * 2, Math.max(1, w * 0.002));
      drawFittedText(ctx, settingsStr, x, top + h + foot * 0.64, area, w * 0.020, muted, false);
      drawFittedText(ctx, bottomStr, x, top + h + foot * 0.84, area, w * 0.018, muted, false);
    } else {
      const centered = (style === 5 || style === 6 || style === 7 || style === 11 || style === 12 || style === 16);
      const align = centered ? 'center' : 'left';
      const targetX = centered ? ow * 0.5 : x;
      drawFittedText(ctx, title, targetX, y, area, w * 0.028, ink, true, align);
      drawFittedText(ctx, settingsStr, targetX, y + foot * 0.25, area, w * 0.020, muted, false, align);
      drawFittedText(ctx, bottomStr, targetX, y + foot * 0.47, area, w * 0.017, muted, false, align);
    }

    if (style === 10) { // Film 35 (Sprocket holes)
      ctx.fillStyle = '#dad4c5';
      const holeW = pad * 0.45, holeH = w * 0.025, step = Math.max(3, holeH * 1.8);
      for (let yy = top; yy < top + h - holeH; yy += step) {
        ctx.beginPath();
        ctx.roundRect(pad * 0.25, yy, holeW, holeH, holeW * 0.2);
        ctx.fill();
        ctx.beginPath();
        ctx.roundRect(ow - pad * 0.70, yy, holeW, holeH, holeW * 0.2);
        ctx.fill();
      }
      ctx.fillStyle = '#f59e0b';
      drawFittedText(ctx, `FUJIFILM ${settings.film.toUpperCase()} 400 · IRO FILM`, pad, top - 4, w, Math.max(10, pad * 0.22), '#f59e0b', true);
    }

    if (style === 13 && pixels) { // Palette
      const palette = extractDominantPalette(pixels, 5);
      const swatch = w * 0.032, gap = w * 0.007;
      const start = ow - pad - 5 * swatch - 4 * gap;
      palette.forEach((sw, i) => {
        ctx.fillStyle = sw.hex;
        ctx.fillRect(start + i * (swatch + gap), top + h + foot * 0.2, swatch, swatch);
      });
    }

    if (style === 16) { // Double
      ctx.strokeStyle = '#bebeb5';
      ctx.lineWidth = Math.max(1, w * 0.001);
      const inset = pad * 0.4;
      ctx.strokeRect(inset, inset, ow - inset * 2, oh - inset * 2);
    }

    return out;
  }

  // Nhóm trang trí nghệ thuật 19..30
  const pad = Math.max(1, Math.round(w * 0.07));
  const foot = Math.max(1, Math.round(w * 0.17));
  const top = (style === 29) ? pad * 2.2 : pad;
  const ow = Math.round(w + pad * 2);
  const oh = Math.round(h + top + pad + foot);
  const dark = (style === 25 || style === 28);

  const bg = (style === 23) ? '#9d7750'
    : (style === 24) ? '#f2eee5'
    : (style === 25) ? '#101122'
    : (style === 28) ? '#153553'
    : (style === 26) ? '#f7f3e7'
    : (style === 21 || style === 22 || style === 27) ? '#ded6c7'
    : '#e9edf0';
  const ink = dark ? '#eef5ff' : '#2e3433';
  const muted = dark ? '#b8cde2' : '#707572';

  const out = Object.assign(document.createElement('canvas'), { width: ow, height: oh });
  const ctx = out.getContext('2d');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, ow, oh);

  if (style === 23) { // Wood grain
    ctx.lineWidth = Math.max(1, w * 0.0015);
    for (let i = 0; i < 24; i++) {
      const gx = (i + 0.5) * ow / 24;
      ctx.strokeStyle = i % 2 === 0 ? 'rgba(128,69,36,0.19)' : 'rgba(255,230,170,0.15)';
      ctx.beginPath();
      ctx.moveTo(gx, 0);
      ctx.bezierCurveTo(gx + pad * 0.4, oh * 0.33, gx - pad * 0.3, oh * 0.66, gx, oh);
      ctx.stroke();
    }
  }

  if (style === 26 || style === 28) { // Notebook / Blueprint
    ctx.strokeStyle = style === 28 ? 'rgba(72,200,255,0.19)' : 'rgba(76,129,144,0.16)';
    ctx.lineWidth = Math.max(1, w * 0.001);
    const step = Math.max(4, w * 0.035);
    for (let x = 0; x < ow; x += step) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, oh); ctx.stroke();
    }
    for (let y = 0; y < oh; y += step) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(ow, y); ctx.stroke();
    }
  }

  const paperX = pad * 0.5, paperY = top - pad * 0.5;
  const paperW = w + pad, paperH = oh - pad * 0.45 - paperY;

  if (style === 19) { // Float
    for (let i = 10; i > 0; i--) {
      ctx.fillStyle = 'rgba(0,0,0,0.015)';
      const d = pad * i * 0.035;
      ctx.beginPath();
      ctx.roundRect(paperX - d, paperY + pad * 0.2 - d, paperW + d * 2, paperH + d * 2, pad * 0.15);
      ctx.fill();
    }
  }

  ctx.fillStyle = dark ? '#112334' : '#ffffff';
  if (style === 21) { // Torn
    ctx.beginPath();
    const step = Math.max(2, w * 0.022);
    ctx.moveTo(paperX, paperY);
    for (let x = paperX; x < paperX + paperW; x += step) {
      ctx.lineTo(x, paperY + pad * 0.09 * Math.sin(x * 1.3));
    }
    ctx.lineTo(paperX + paperW, paperY + paperH);
    for (let x = paperX + paperW; x > paperX; x -= step) {
      ctx.lineTo(x, paperY + paperH + pad * 0.09 * Math.sin(x * 0.7));
    }
    ctx.closePath();
    ctx.fill();
  } else if (style === 20) { // Soft
    ctx.beginPath();
    ctx.roundRect(paperX, paperY, paperW, paperH, pad * 0.42);
    ctx.fill();
  } else if (style === 29) { // Arch
    ctx.beginPath();
    ctx.moveTo(paperX, paperY + paperH);
    ctx.lineTo(paperX, top);
    ctx.bezierCurveTo(paperX, paperY - pad * 0.8, paperX + paperW, paperY - pad * 0.8, paperX + paperW, top);
    ctx.lineTo(paperX + paperW, paperY + paperH);
    ctx.closePath();
    ctx.fill();
  } else {
    ctx.fillRect(paperX, paperY, paperW, paperH);
  }

  if (style === 25) { // Neon
    for (let i = 8; i > 0; i--) {
      ctx.strokeStyle = 'rgba(105, 220, 255, 0.06)';
      ctx.lineWidth = Math.max(1, w * 0.0015 * i);
      ctx.strokeRect(pad - pad * 0.12, top - pad * 0.12, w + pad * 0.24, h + pad * 0.24);
    }
    ctx.strokeStyle = '#b7f0ff';
    ctx.lineWidth = Math.max(1, w * 0.002);
    ctx.strokeRect(pad - pad * 0.12, top - pad * 0.12, w + pad * 0.24, h + pad * 0.24);
  }

  ctx.drawImage(sourceCanvas, pad, top);

  if (style === 24) { // Gold
    const goldGrad = ctx.createLinearGradient(0, 0, ow, oh);
    goldGrad.addColorStop(0, '#9c7637');
    goldGrad.addColorStop(0.5, '#efd99b');
    goldGrad.addColorStop(1, '#ae8742');
    ctx.strokeStyle = goldGrad;
    ctx.lineWidth = Math.max(1, w * 0.008);
    ctx.strokeRect(paperX, paperY, paperW, paperH);
  }

  if (style === 22) { // Tape
    ctx.fillStyle = 'rgba(225, 199, 145, 0.74)';
    for (let side = 0; side < 2; side++) {
      const cx = (side === 0) ? pad * 1.15 : pad + w - pad * 0.15;
      ctx.save();
      ctx.translate(cx, top);
      ctx.rotate((side === 0 ? -24 : 23) * Math.PI / 180);
      ctx.fillRect(-pad * 0.58, -pad * 0.22, pad * 1.16, pad * 0.44);
      ctx.restore();
    }
  }

  if (style === 30) { // Corners
    ctx.fillStyle = '#3d4944';
    const len = pad * 0.55;
    for (const x of [0, 1]) {
      for (const y of [0, 1]) {
        const cx = pad + x * w, cy = top + y * h;
        const dx = (x === 0) ? 1 : -1, dy = (y === 0) ? 1 : -1;
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.lineTo(cx + dx * len, cy);
        ctx.lineTo(cx, cy + dy * len);
        ctx.closePath();
        ctx.fill();
      }
    }
  }

  if (style === 27) { // Ticket
    const ty = top + h + foot * 0.12;
    ctx.strokeStyle = '#a69c8a';
    ctx.lineWidth = Math.max(1, w * 0.002);
    ctx.setLineDash([w * 0.012, w * 0.012]);
    ctx.beginPath();
    ctx.moveTo(pad, ty);
    ctx.lineTo(pad + w, ty);
    ctx.stroke();
    ctx.setLineDash([]);

    ctx.fillStyle = bg;
    ctx.beginPath();
    ctx.arc(paperX, ty, pad * 0.18, 0, Math.PI * 2);
    ctx.arc(paperX + paperW, ty, pad * 0.18, 0, Math.PI * 2);
    ctx.fill();
  }

  const baseline = top + h + foot * 0.39;
  drawFittedText(ctx, title, pad + w * 0.5, baseline, w, w * 0.028, ink, true, 'center');
  drawFittedText(ctx, settingsStr, pad + w * 0.5, baseline + foot * 0.24, w, w * 0.019, muted, false, 'center');
  drawFittedText(ctx, bottomStr, pad + w * 0.5, baseline + foot * 0.47, w, w * 0.017, muted, false, 'center');

  return out;
}

