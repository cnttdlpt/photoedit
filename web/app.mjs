import {
  defaults, films, parseCube, processPixels, rotatePixels90,
  autoBalanceSettings, promptToRecipeVi, encodeRecipeCode, decodeRecipeCode,
  embedRecipeIntoPixels, extractRecipeFromPixels,
  bakeRecipeToCube, extractDominantPalette, matchColorFromReference,
  PHOTO_FRAMES, defaultPhotoInfo, parseFujiRecipe, renderPhotoFrame, buildMonotoneCurveLut
} from './engine.mjs';

const $ = id => document.getElementById(id);
const canvas = $('canvas'), ctx = canvas.getContext('2d');
const histCanvas = $('histogram'), histCtx = histCanvas ? histCanvas.getContext('2d') : null;

let settings = { ...defaults }, source, preview, lastRenderedImageData;
let luts = [], selected = '', compare = false, quadView = false, livingTimer = null, livingFrame = 0, splitRatio = 0;
let serial = 0, timer, exporting = false, currentName = 'iro-demo';

const worker = new Worker('./worker.mjs', { type: 'module' });
const pending = new Map();

worker.onmessage = ({ data }) => {
  const p = pending.get(data.id);
  if (!p) return;
  pending.delete(data.id);
  data.error ? p.reject(Error(data.error)) : p.resolve(data);
};

worker.onerror = () => {
  for (const p of pending.values()) {
    p.reject(Error('Bộ xử lý ảnh gặp lỗi. Hãy tải lại trang.'));
  }
  pending.clear();
};

const toast = message => {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(timer);
  timer = setTimeout(() => ($('toast').hidden = true), 5000);
};

// Vẽ dấu ngày tháng LED cam kiểu máy phim thập niên 90 (Quartz Date Stamp)
function drawQuartzDateStamp(targetCtx, w, h, enabled) {
  if (!enabled) return;
  const now = new Date();
  const yy = String(now.getFullYear()).slice(-2);
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const stamp = `'${yy} ${mm} ${dd}`;
  const fontSize = Math.max(14, Math.round(Math.min(w, h) * 0.032));
  targetCtx.save();
  targetCtx.font = `700 ${fontSize}px "Courier New", monospace`;
  targetCtx.shadowColor = 'rgba(255, 68, 0, 0.85)';
  targetCtx.shadowBlur = Math.round(fontSize * 0.45);
  targetCtx.fillStyle = '#ff7b1a';
  targetCtx.fillText(stamp, w - fontSize * 6.4, h - Math.round(fontSize * 1.1));
  targetCtx.restore();
}

const LUT_DB_NAME = 'iro-studio-db', LUT_STORE = 'luts';
function openLutDb() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) return resolve(null);
    const req = indexedDB.open(LUT_DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(LUT_STORE, { keyPath: 'id' });
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function loadStoredLuts() {
  try {
    const db = await openLutDb();
    if (!db) return;
    const tx = db.transaction(LUT_STORE, 'readonly');
    const req = tx.objectStore(LUT_STORE).getAll();
    req.onsuccess = () => {
      if (Array.isArray(req.result)) {
        luts = req.result.map(item => ({ ...item, data: new Float32Array(item.data) }));
        updateLutList();
      }
    };
  } catch {}
}
async function saveLutToDb(lut) {
  try {
    const db = await openLutDb();
    if (!db) return;
    const tx = db.transaction(LUT_STORE, 'readwrite');
    tx.objectStore(LUT_STORE).put(lut);
  } catch {}
}
async function deleteLutFromDb(id) {
  try {
    const db = await openLutDb();
    if (!db) return;
    const tx = db.transaction(LUT_STORE, 'readwrite');
    tx.objectStore(LUT_STORE).delete(id);
  } catch {}
}

const groups = [
  ['ÁNH SÁNG, KHỬ SƯƠNG & ĐỚI SÁNG', [
    ['exposure', 'Bù sáng · EV', -3, 3, 1 / 3],
    ['dr', 'Dynamic Range', [100, 200, 400]],
    ['highlight', 'Highlight', -2, 4, 1],
    ['shadow', 'Shadow', -2, 4, 1],
    ['clarity', 'Clarity (Tương phản vi mô)', -100, 100, 5],
    ['texture', 'Texture (Chi tiết bề mặt/Da)', -100, 100, 5],
    ['dehaze', 'Dehaze (Khử mù sương)', -100, 100, 5],
    ['diagnosticMode', 'Chẩn đoán Đới sáng / Zebra', ['Off', 'ZoneMap', 'Zebra']]
  ]],
  ['MÀU SẮC, CHROME & BẢO VỆ DA', [
    ['amount', 'Cường độ màu phim', 0, 100, 1],
    ['skinProtect', 'Bảo vệ màu da (Skin Protect)', 0, 100, 5],
    ['wb', 'Cân bằng trắng', ['Auto', 'Daylight', 'Cloudy', 'Tungsten']],
    ['temperature', 'Nhiệt độ', -100, 100, 1],
    ['red', 'WB Shift · Red', -9, 9, 1],
    ['blue', 'WB Shift · Blue', -9, 9, 1],
    ['color', 'Color', -4, 4, 1],
    ['chrome', 'Color Chrome Effect', [0, 1, 2]],
    ['chromeBlue', 'Color Chrome FX Blue', [0, 1, 2]]
  ]],
  ['QUANG HỌC 3D & HÓA CHẤT PHÒNG TỐI', [
    ['darkroomProcess', 'Giấy ảnh Phòng tối Cổ điển', ['None', 'SplitGrade', 'Cyanotype', 'Platinum']],
    ['proMist', 'Kính lọc Black Pro-Mist', 0, 100, 5],
    ['swirlyBokeh', 'Bokeh xoáy Helios 44-2', 0, 100, 5],
    ['depthFog', 'Sương mù chiều sâu 3D', 0, 100, 5],
    ['halation', 'Halation (Quầng đỏ phim)', 0, 100, 5],
    ['bleachBypass', 'Bleach Bypass (Giữ bạc)', 0, 100, 5],
    ['lightLeak', 'Light Leak (Lọt sáng 35mm)', 0, 100, 5],
    ['chromatic', 'Viền tím quang học (CA)', 0, 4, 1],
    ['grain', 'Grain Effect', [0, 8, 18]],
    ['grainSize', 'Kích thước hạt (Grain Size)', [1, 2]],
    ['sharpness', 'Sharpness', -4, 4, 1],
    ['nr', 'Noise Reduction', -4, 4, 1]
  ]]
];

const labels = {
  dr: { 100: 'DR100', 200: 'DR200', 400: 'DR400' },
  grain: { 0: 'Off', 8: 'Weak', 18: 'Strong' },
  grainSize: { 1: 'Small (Mịn)', 2: 'Large (Thô)' },
  chrome: { 0: 'Off', 1: 'Weak', 2: 'Strong' },
  chromeBlue: { 0: 'Off', 1: 'Weak', 2: 'Strong' },
  wb: { Auto: 'Auto (giữ WB ảnh)', Daylight: 'Daylight', Cloudy: 'Cloudy', Tungsten: 'Tungsten' },
  diagnosticMode: { Off: 'Tắt (Xem ảnh thật)', ZoneMap: '11-Zone Ansel Adams', Zebra: 'Zebra Cảnh báo Cháy/Bệt' },
  darkroomProcess: {
    None: 'Tắt (Màu phim chuẩn)',
    SplitGrade: 'Split-Grade (Selenium + Sepia)',
    Cyanotype: 'Cyanotype (In Xanh Phổ 1842)',
    Platinum: 'Platinum / Palladium Cổ điển'
  }
};

for (const [title, controls] of groups) {
  const group = document.createElement('section');
  group.className = 'control-group';
  const h = document.createElement('div');
  h.className = 'group-title';
  h.textContent = title;
  group.append(h);
  for (const [key, label, min, max, step] of controls) {
    const d = document.createElement('div');
    d.className = 'control';
    const l = document.createElement('label');
    l.htmlFor = key;
    l.textContent = label;
    const input = document.createElement(Array.isArray(min) ? 'select' : 'input');
    input.id = key;
    if (Array.isArray(min)) {
      const map = labels[key] || {};
      for (const v of min) {
        const o = new Option(map[v] || v, v);
        input.add(o);
      }
      d.append(l, input);
    } else {
      input.type = 'range';
      input.min = min;
      input.max = max;
      input.step = step;
      const output = document.createElement('output');
      output.id = key + 'Value';
      l.append(output);
      d.append(l, input);
    }
    input.oninput = () => {
      const strKeys = new Set(['wb', 'diagnosticMode', 'darkroomProcess']);
      settings[key] = strKeys.has(key) ? input.value : Number(input.value);
      quadView = false;
      sync();
      render();
    };
    group.append(d);
  }
  $('controls').append(group);
}

Object.keys(films).forEach((name, i) => {
  const b = document.createElement('button');
  b.className = 'film';
  b.dataset.film = name;
  const sw = document.createElement('span');
  sw.className = 'swatch';
  sw.style.filter = `saturate(${films[name][0]}) contrast(${films[name][1]}) hue-rotate(${i * 5}deg)`;
  b.append(sw, document.createTextNode(name));
  const small = document.createElement('small');
  small.textContent = String(i + 1).padStart(2, '0');
  b.append(small);
  b.onclick = () => {
    settings.film = name;
    quadView = false;
    sync();
    render();
  };
  $('films').append(b);
});

const pctKeys = new Set(['amount', 'halation', 'proMist', 'bleachBypass', 'lightLeak', 'skinProtect', 'swirlyBokeh', 'depthFog']);

function sync() {
  for (const [, list] of groups) {
    for (const [key] of list) {
      if ($(key)) $(key).value = settings[key];
      if ($(key + 'Value')) {
        $(key + 'Value').textContent =
          pctKeys.has(key)
            ? settings[key] + '%'
            : key === 'exposure'
            ? (settings[key] > 0 ? '+' : '') + Number(settings[key]).toFixed(2)
            : (settings[key] > 0 ? '+' : '') + settings[key];
      }
    }
  }
  document.querySelectorAll('.film').forEach(b =>
    b.classList.toggle('active', b.dataset.film === settings.film)
  );
  const filmName = settings.film || 'Original';
  $('lookTitle').textContent = quadView ? 'So sánh 4 Màu Phim (Quad-View)' : filmName;
  $('imageTag').textContent = 'IRO / ' + (compare ? 'ORIGINAL' : livingTimer ? 'LIVING FILM 24FPS' : quadView ? 'QUAD-VIEW' : filmName.toUpperCase());
  $('lutAmount').value = settings.lutAmount;
  $('lutValue').textContent = settings.lutAmount + '%';
  if ($('frameSelect')) $('frameSelect').value = settings.frame || 'None';
}

function job(data) {
  return new Promise((resolve, reject) => {
    const id = ++serial;
    pending.set(id, { resolve, reject });
    worker.postMessage({ ...data, id });
  });
}

function drawHistogram(hist, pixels) {
  if (!histCtx || !hist) return;
  const w = histCanvas.width, h = histCanvas.height;
  histCtx.clearRect(0, 0, w, h);
  let maxVal = 1;
  for (let i = 4; i < 252; i++) {
    maxVal = Math.max(maxVal, hist.rHist[i], hist.gHist[i], hist.bHist[i]);
  }
  histCtx.globalCompositeOperation = 'screen';
  const channels = [
    [hist.rHist, 'rgba(255, 75, 75, 0.75)'],
    [hist.gHist, 'rgba(75, 230, 115, 0.75)'],
    [hist.bHist, 'rgba(80, 155, 255, 0.75)']
  ];
  for (const [arr, color] of channels) {
    histCtx.fillStyle = color;
    histCtx.beginPath();
    histCtx.moveTo(0, h);
    for (let i = 0; i < 256; i++) {
      const barH = Math.min(h, (arr[i] / maxVal) * (h - 6));
      histCtx.lineTo(i, h - barH);
    }
    histCtx.lineTo(255, h);
    histCtx.closePath();
    histCtx.fill();
  }
  histCtx.globalCompositeOperation = 'source-over';

  const swatchBox = $('paletteSwatches');
  if (swatchBox && pixels) {
    const pal = extractDominantPalette(pixels, 5);
    swatchBox.innerHTML = '';
    for (const c of pal) {
      const dot = document.createElement('div');
      dot.style.cssText = `flex:1;border-radius:4px;background:${c.hex};border:1px solid rgba(255,255,255,0.2)`;
      dot.title = c.hex.toUpperCase();
      swatchBox.append(dot);
    }
  }
}

function renderQuadViewCanvas() {
  if (!preview) return;
  const rot = settings.rotation
    ? rotatePixels90(preview.data, preview.width, preview.height, settings.rotation)
    : { pixels: preview.data, width: preview.width, height: preview.height };
  const w = rot.width, h = rot.height;
  canvas.width = w; canvas.height = h;
  const halfW = Math.floor(w / 2), halfH = Math.floor(h / 2);
  const scaleCanvas = document.createElement('canvas');
  scaleCanvas.width = halfW; scaleCanvas.height = halfH;
  const sCtx = scaleCanvas.getContext('2d');
  const tmpFull = document.createElement('canvas');
  tmpFull.width = w; tmpFull.height = h;
  tmpFull.getContext('2d').putImageData(new ImageData(rot.pixels, w, h), 0, 0);
  sCtx.drawImage(tmpFull, 0, 0, halfW, halfH);
  const subData = sCtx.getImageData(0, 0, halfW, halfH);

  const quadFilms = ['Classic Chrome', 'Classic Neg.', 'Nostalgic Neg.', 'ACROS'];
  const activeLut = luts.find(l => l.id === selected);
  quadFilms.forEach((fName, idx) => {
    const qx = (idx % 2) * halfW, qy = Math.floor(idx / 2) * halfH;
    const outPx = processPixels(subData.data, halfW, halfH, { ...settings, film: fName, diagnosticMode: 'Off' }, activeLut);
    ctx.putImageData(new ImageData(outPx, halfW, halfH), qx, qy);
    ctx.fillStyle = 'rgba(0,0,0,0.65)';
    ctx.fillRect(qx + 10, qy + 10, 145, 26);
    ctx.fillStyle = '#fff';
    ctx.font = '600 12px system-ui, sans-serif';
    ctx.fillText(fName.toUpperCase(), qx + 18, qy + 27);
  });
}

let renderTimer, version = 0;
function render() {
  const v = ++version;
  clearTimeout(renderTimer);
  if (!preview) return;
  if (quadView) {
    renderQuadViewCanvas();
    $('busy').hidden = true;
    return;
  }
  if (compare) {
    const rotOrig = settings.rotation
      ? rotatePixels90(preview.data, preview.width, preview.height, settings.rotation)
      : { pixels: preview.data, width: preview.width, height: preview.height };
    paint(new ImageData(rotOrig.pixels, rotOrig.width, rotOrig.height));
    $('busy').hidden = true;
    return;
  }
  renderTimer = setTimeout(async () => {
    $('busy').hidden = false;
    try {
      const result = await job({
        pixels: preview.data,
        width: preview.width,
        height: preview.height,
        settings: { ...settings },
        lut: luts.find(l => l.id === selected),
        withHistogram: true
      });
      if (v === version && !compare && !quadView) {
        lastRenderedImageData = new ImageData(result.pixels, result.width, result.height);
        paint(lastRenderedImageData);
        drawHistogram(result.hist, result.pixels);
      }
    } catch (e) {
      toast(e.message);
    } finally {
      if (v === version) $('busy').hidden = true;
    }
  }, 45);
}

function paint(data) {
  canvas.width = data.width;
  canvas.height = data.height;
  ctx.putImageData(data, 0, 0);
  drawQuartzDateStamp(ctx, data.width, data.height, settings.dateStamp);
  if (splitRatio > 0 && !compare && !quadView && preview) {
    const rotOrig = settings.rotation
      ? rotatePixels90(preview.data, preview.width, preview.height, settings.rotation)
      : { pixels: preview.data, width: preview.width, height: preview.height };
    const splitX = Math.round((splitRatio / 100) * data.width);
    ctx.putImageData(new ImageData(rotOrig.pixels, rotOrig.width, rotOrig.height), 0, 0, 0, 0, splitX, data.height);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(splitX - 1, 0, 2, data.height);
  }
}

// Bật/tắt chế độ Living Film 24fps (Hạt muối bạc và ánh sáng phim chuyển động 24fps)
if ($('livingFilmBtn')) {
  $('livingFilmBtn').onclick = () => {
    if (livingTimer) {
      clearInterval(livingTimer);
      livingTimer = null;
      sync();
      render();
      toast('Đã dừng chế độ Living Film 24fps.');
      return;
    }
    if (!preview) return;
    if (settings.grain === 0) settings.grain = 8;
    quadView = false;
    const rot = settings.rotation
      ? rotatePixels90(preview.data, preview.width, preview.height, settings.rotation)
      : { pixels: preview.data, width: preview.width, height: preview.height };
    const activeLut = luts.find(l => l.id === selected);
    livingTimer = setInterval(() => {
      livingFrame = (livingFrame + 1) % 240;
      const px = processPixels(rot.pixels, rot.width, rot.height, settings, activeLut, livingFrame);
      paint(new ImageData(px, rot.width, rot.height));
    }, 1000 / 24);
    sync();
    toast('Đang phát Living Film 24fps (hạt muối bạc & quầng sáng chuyển động trực tiếp)!');
  };
}

// Bật/tắt dấu ngày tháng LED cam Quartz Date ('26 10 14)
if ($('dateStampBtn')) {
  $('dateStampBtn').onclick = () => {
    settings.dateStamp = !settings.dateStamp;
    if (lastRenderedImageData) paint(lastRenderedImageData);
    toast(settings.dateStamp ? 'Đã bật dấu ngày tháng máy phim Quartz Date.' : 'Đã tắt dấu ngày tháng.');
  };
}

function setSource(c, name, demo = false) {
  source = c;
  currentName = name;
  const fullData = c.getContext('2d').getImageData(0, 0, Math.min(c.width, 600), Math.min(c.height, 4)).data;
  const embedded = extractRecipeFromPixels(fullData);
  if (embedded && !demo) {
    settings = { ...settings, ...embedded };
    toast(`Đã tự động khôi phục công thức màu (${embedded.film}) nhúng ẩn trong ảnh!`);
  }

  const p = document.createElement('canvas');
  const scale = Math.min(1, 1400 / Math.max(c.width, c.height));
  p.width = Math.round(c.width * scale);
  p.height = Math.round(c.height * scale);
  const x = p.getContext('2d');
  x.drawImage(c, 0, 0, p.width, p.height);
  preview = x.getImageData(0, 0, p.width, p.height);
  $('filename').textContent = name;
  $('imageSize').textContent = demo ? 'ẢNH MẪU MINH HỌA' : `${c.width} x ${c.height}`;
  $('status').textContent = embedded
    ? `Đã khôi phục công thức ẩn từ ảnh · Phim ${settings.film}`
    : demo
    ? 'Thử màu trên ảnh mẫu hoặc mở ảnh của bạn'
    : 'Đã mở ảnh · mọi thay đổi đều có thể đặt lại';
  sync();
  render();
}

function demo() {
  const c = document.createElement('canvas');
  c.width = 1200; c.height = 900;
  const x = c.getContext('2d');
  const sky = x.createLinearGradient(0, 0, 0, 900);
  sky.addColorStop(0, '#5c8e94');
  sky.addColorStop(.57, '#d0ccb0');
  sky.addColorStop(1, '#b49a76');
  x.fillStyle = sky;
  x.fillRect(0, 0, 1200, 900);
  x.fillStyle = '#e9d4a1';
  x.beginPath();
  x.arc(850, 215, 65, 0, 7);
  x.fill();
  for (let n = 0; n < 4; n++) {
    x.fillStyle = ['#718781', '#526d65', '#3d5a4d', '#2c4437'][n];
    x.beginPath();
    x.moveTo(0, 440 + n * 85);
    for (let a = 0; a <= 1200; a += 20)
      x.lineTo(a, 410 + n * 85 + Math.sin(a / 180 + n * 2) * 55 + Math.cos(a / 83) * 15);
    x.lineTo(1200, 900);
    x.lineTo(0, 900);
    x.fill();
  }
  x.fillStyle = '#cab98f';
  x.beginPath();
  x.moveTo(60, 580);
  x.lineTo(240, 470);
  x.lineTo(410, 580);
  x.fill();
  setSource(c, 'Fujifilm Classic Chrome Demo', true);
}

function updateLutList() {
  const s = $('lutSelect');
  s.innerHTML = '<option value="">Không dùng LUT 3D</option>';
  for (const l of luts) {
    const o = new Option(`${l.name} (${l.size}x${l.size}x${l.size})`, l.id);
    o.selected = l.id === selected;
    s.add(o);
  }
  $('removeLut').hidden = !selected;
}

$('open').onclick = () => $('photo').click();
$('drop').onclick = () => $('photo').click();
$('photo').onchange = e => {
  const f = e.target.files[0];
  if (!f) return;
  const img = new Image(), url = URL.createObjectURL(f);
  img.onload = () => {
    URL.revokeObjectURL(url);
    const c = document.createElement('canvas');
    c.width = img.naturalWidth;
    c.height = img.naturalHeight;
    c.getContext('2d').drawImage(img, 0, 0);
    settings.rotation = 0;
    setSource(c, f.name);
    $('photo').value = '';
  };
  img.onerror = () => toast('Không thể đọc tệp ảnh này.');
  img.src = url;
};

if ($('matchRefBtn') && $('refPhoto')) {
  $('matchRefBtn').onclick = () => $('refPhoto').click();
  $('refPhoto').onchange = e => {
    const f = e.target.files[0];
    if (!f || !preview) return;
    const img = new Image(), url = URL.createObjectURL(f);
    img.onload = () => {
      URL.revokeObjectURL(url);
      const c = document.createElement('canvas');
      c.width = 256; c.height = 256;
      c.getContext('2d').drawImage(img, 0, 0, 256, 256);
      const refData = c.getContext('2d').getImageData(0, 0, 256, 256).data;
      const matched = matchColorFromReference(preview.data, refData, settings);
      settings = matched.settings;
      quadView = false;
      sync();
      render();
      $('status').textContent = matched.reasonVi;
      toast(matched.reasonVi);
      $('refPhoto').value = '';
    };
    img.src = url;
  };
}

if ($('promptRecipeBtn')) {
  $('promptRecipeBtn').onclick = () => {
    const text = prompt(
      'Nhập cảm xúc hoặc quy trình phòng tối bạn muốn (ví dụ: "Chiều mưa Đà Lạt sương mù", "Nắng cổ điển Hà Nội Helios", "In xanh phổ Cyanotype 1842", "Đen trắng phóng sự"):',
      'Nắng cổ điển Hà Nội Helios'
    );
    if (!text) return;
    const res = promptToRecipeVi(text, settings);
    settings = res.settings;
    quadView = false;
    sync();
    render();
    $('status').textContent = res.reasonVi;
    toast(res.reasonVi);
  };
}

if ($('quadViewBtn')) {
  $('quadViewBtn').onclick = () => {
    quadView = !quadView;
    sync();
    render();
    toast(quadView ? 'Đang bật chế độ So sánh 4 Màu Phim (Quad-View).' : 'Đã quay lại chế độ xem đơn.');
  };
}

if ($('contactSheetBtn')) {
  $('contactSheetBtn').onclick = () => {
    if (!preview) return;
    const thumbW = 360, thumbH = Math.round((preview.height / preview.width) * thumbW);
    const pad = 28, headerH = 70, labelH = 34;
    const sheet = document.createElement('canvas');
    sheet.width = pad * 4 + thumbW * 3;
    sheet.height = headerH + pad * 4 + (thumbH + labelH) * 3;
    const g = sheet.getContext('2d');
    g.fillStyle = '#0d0e11';
    g.fillRect(0, 0, sheet.width, sheet.height);

    g.fillStyle = '#f59e0b';
    g.font = '700 20px monospace';
    g.fillText(`IRO DARKROOM CONTACT SHEET · ${currentName.toUpperCase()}`, pad, 44);

    const tmpSrc = document.createElement('canvas');
    tmpSrc.width = preview.width; tmpSrc.height = preview.height;
    tmpSrc.getContext('2d').putImageData(preview, 0, 0);
    const smallCanvas = document.createElement('canvas');
    smallCanvas.width = thumbW; smallCanvas.height = thumbH;
    smallCanvas.getContext('2d').drawImage(tmpSrc, 0, 0, thumbW, thumbH);
    const smallData = smallCanvas.getContext('2d').getImageData(0, 0, thumbW, thumbH);

    Object.keys(films).forEach((filmName, idx) => {
      const col = idx % 3, row = Math.floor(idx / 3);
      const x = pad + col * (thumbW + pad);
      const y = headerH + pad + row * (thumbH + labelH + pad);
      const rendered = processPixels(smallData.data, thumbW, thumbH, { ...settings, film: filmName, diagnosticMode: 'Off' });
      const cellCanvas = document.createElement('canvas');
      cellCanvas.width = thumbW; cellCanvas.height = thumbH;
      cellCanvas.getContext('2d').putImageData(new ImageData(rendered, thumbW, thumbH), 0, 0);
      g.drawImage(cellCanvas, x, y);

      g.fillStyle = '#f59e0b';
      g.font = '600 13px monospace';
      g.fillText(`${String(idx + 1).padStart(2, '0')}A · ${filmName.toUpperCase()}`, x, y + thumbH + 22);
    });

    sheet.toBlob(blob => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `${currentName.replace(/\.[^/.]+$/, '')}-IRO-ContactSheet.jpg`;
      a.click();
      URL.revokeObjectURL(a.href);
      toast('Đã xuất bản in tiếp xúc Contact Sheet 9 màu phim!');
    }, 'image/jpeg', 0.94);
  };
}

if ($('exportCube')) {
  $('exportCube').onclick = () => {
    try {
      const cubeContent = bakeRecipeToCube(settings, 17, `IRO ${settings.film}`);
      const blob = new Blob([cubeContent], { type: 'text/plain;charset=utf-8' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `IRO-${settings.film.toLowerCase().replace(/\s+/g, '-')}.cube`;
      a.click();
      URL.revokeObjectURL(a.href);
      toast('Đã xuất công thức thành tệp 3D LUT (.cube) cho Video/Premiere/CapCut!');
    } catch (err) {
      toast(err.message);
    }
  };
}

const drop = $('drop');
['dragenter', 'dragover'].forEach(e =>
  drop.addEventListener(e, ev => { ev.preventDefault(); drop.classList.add('drag'); })
);
['dragleave', 'drop'].forEach(e =>
  drop.addEventListener(e, ev => { ev.preventDefault(); drop.classList.remove('drag'); })
);
drop.addEventListener('drop', e => {
  const f = e.dataTransfer.files[0];
  if (f && f.type.startsWith('image/')) {
    $('photo').files = e.dataTransfer.files;
    $('photo').dispatchEvent(new Event('change'));
  }
});

$('importLut').onclick = () => $('lutfile').click();
$('lutfile').onchange = async e => {
  const f = e.target.files[0];
  if (!f) return;
  try {
    const text = await f.text();
    const parsed = parseCube(text);
    const lut = {
      ...parsed,
      id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
      name: parsed.title && parsed.title !== 'Custom LUT' ? parsed.title : f.name.replace(/\.cube$/i, '')
    };
    luts.push(lut);
    selected = lut.id;
    await saveLutToDb(lut);
    updateLutList();
    sync();
    render();
    toast(`Đã lưu LUT vào IndexedDB: ${lut.name}`);
  } catch (err) {
    toast(err.message);
  }
  $('lutfile').value = '';
};

$('lutSelect').onchange = e => { selected = e.target.value; updateLutList(); sync(); render(); };
$('lutAmount').oninput = e => { settings.lutAmount = +e.target.value; sync(); render(); };
$('removeLut').onclick = async () => {
  if (!selected) return;
  const removedId = selected;
  luts = luts.filter(l => l.id !== removedId);
  selected = '';
  await deleteLutFromDb(removedId);
  updateLutList();
  sync();
  render();
  toast('Đã gỡ bỏ LUT 3D.');
};

if ($('rotate90')) {
  $('rotate90').onclick = () => {
    settings.rotation = ((settings.rotation || 0) + 90) % 360;
    render();
    toast(`Đã xoay ảnh: ${settings.rotation}°`);
  };
}

if ($('splitCompare')) {
  $('splitCompare').oninput = e => {
    splitRatio = Number(e.target.value);
    if (lastRenderedImageData) paint(lastRenderedImageData);
  };
}

if ($('aiAuto')) {
  $('aiAuto').onclick = async () => {
    if (!preview) return;
    const result = autoBalanceSettings(preview.data, settings);
    settings = result.settings;
    quadView = false;
    sync();
    render();
    $('status').textContent = result.reasonVi;
    toast(result.reasonVi);
  };
}

if ($('shareCode')) {
  $('shareCode').onclick = () => {
    const code = encodeRecipeCode(settings);
    const input = prompt('Sao chép mã công thức IRO dưới đây hoặc dán mã IRO1-... khác để nạp:', code);
    if (input && input.trim() !== code) {
      try {
        settings = { ...settings, ...decodeRecipeCode(input.trim()) };
        sync();
        render();
        toast('Đã nạp mã công thức IRO thành công!');
      } catch (err) {
        toast(err.message);
      }
    }
  };
}

if ($('frameSelect')) {
  $('frameSelect').onchange = e => {
    settings.frameStyle = Number(e.target.value);
    settings.frame = e.target.options[e.target.selectedIndex].text;
    toast(`Khung ảnh khi xuất: ${settings.frame}`);
  };
}

$('reset').onclick = () => { settings = { ...defaults }; quadView = false; sync(); render(); toast('Đã đặt lại toàn bộ thông số.'); };
$('compare').onmousedown = () => { compare = true; sync(); render(); };
$('compare').onmouseup = () => { compare = false; sync(); render(); };
$('compare').ontouchstart = e => { e.preventDefault(); compare = true; sync(); render(); };
$('compare').ontouchend = () => { compare = false; sync(); render(); };

$('sampleRecipe').onclick = () => {
  settings = {
    ...defaults,
    film: 'Classic Chrome', dr: 200, grain: 8, grainSize: 2,
    chrome: 1, chromeBlue: 1, halation: 20, proMist: 20, clarity: 20, skinProtect: 50,
    red: 1, blue: -1, highlight: -1, shadow: 1, color: 2, sharpness: 2, nr: -2, exposure: 2 / 3
  };
  quadView = false;
  sync();
  render();
  toast('Đã áp dụng công thức Fujifilm Street Classic Pro 3.0');
};

$('saveRecipe').onclick = () => {
  const blob = new Blob([JSON.stringify({ app: 'IRO', version: 1, code: encodeRecipeCode(settings), settings }, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${settings.film.toLowerCase().replace(/\s+/g, '-')}-recipe.json`;
  a.click();
  URL.revokeObjectURL(a.href);
};

$('loadRecipe').onclick = () => $('recipefile').click();
$('recipefile').onchange = async e => {
  const f = e.target.files[0];
  if (!f) return;
  try {
    const r = JSON.parse(await f.text());
    if (r.app !== 'IRO' || r.version !== 1 || !r.settings) throw Error('Recipe không đúng định dạng IRO.');
    if (r.settings.lutAmount !== undefined) {
      const amount = r.settings.lutAmount;
      if (typeof amount !== 'number' || !Number.isFinite(amount) || amount < 0 || amount > 100) {
        throw Error('Cường độ LUT không hợp lệ.');
      }
    }
    for (const [key, , min, max] of groups.flatMap(g => g[1])) {
      const val = r.settings[key];
      if (val === undefined) continue;
      if (Array.isArray(min)) {
        if (!min.includes(val)) throw Error('Thông số không hợp lệ: ' + key);
      } else if (typeof val !== 'number' || !Number.isFinite(val) || val < min || val > max) {
        throw Error('Thông số nằm ngoài khoảng cho phép: ' + key);
      }
    }
    settings = { ...defaults, ...r.settings };
    sync();
    render();
    toast('Đã tải công thức màu thành công.');
  } catch (err) {
    toast(err.message);
  }
  $('recipefile').value = '';
};

let currentPhotoInfo = { ...defaultPhotoInfo };

function renderFramedCanvas(rawCanvas, rawPixels, s) {
  drawQuartzDateStamp(rawCanvas.getContext('2d'), rawCanvas.width, rawCanvas.height, s.dateStamp);
  const styleIdx = s.frameStyle !== undefined ? Number(s.frameStyle) : (typeof s.frame === 'number' ? s.frame : 0);
  if (!styleIdx || styleIdx === 0) return rawCanvas;
  return renderPhotoFrame(rawCanvas, rawPixels, s, currentPhotoInfo, styleIdx);
}

$('export').onclick = async () => {
  if (!source || exporting) return;
  exporting = true;
  $('busy').hidden = false;
  try {
    const fmt = $('format').value;
    const ext = fmt === 'image/jpeg' ? '.jpg' : fmt === 'image/webp' ? '.webp' : '.png';
    const scaleFactor = Number($('exportScale')?.value || 1);
    let srcCanvas = source;
    if (scaleFactor > 1) {
      const scaled = document.createElement('canvas');
      scaled.width = source.width * scaleFactor;
      scaled.height = source.height * scaleFactor;
      const sctx = scaled.getContext('2d');
      sctx.imageSmoothingQuality = 'high';
      sctx.drawImage(source, 0, 0, scaled.width, scaled.height);
      srcCanvas = scaled;
    }

    const result = await job({
      pixels: srcCanvas.getContext('2d').getImageData(0, 0, srcCanvas.width, srcCanvas.height).data,
      width: srcCanvas.width,
      height: srcCanvas.height,
      settings: { ...settings, diagnosticMode: 'Off' },
      lut: luts.find(l => l.id === selected)
    });
    const outPixels = fmt === 'image/png'
      ? embedRecipeIntoPixels(result.pixels, settings)
      : result.pixels;
    const rawOut = document.createElement('canvas');
    rawOut.width = result.width;
    rawOut.height = result.height;
    rawOut.getContext('2d').putImageData(new ImageData(outPixels, result.width, result.height), 0, 0);
    const finalCanvas = renderFramedCanvas(rawOut, outPixels, settings);

    finalCanvas.toBlob(blob => {
      const a = document.createElement('a');
      const base = currentName.replace(/\.[^/.]+$/, '');
      a.href = URL.createObjectURL(blob);
      a.download = `${base}-IRO${scaleFactor > 1 ? '-2X' : ''}${ext}`;
      a.click();
      URL.revokeObjectURL(a.href);
      exporting = false;
      $('busy').hidden = true;
      toast(`Đã xuất ảnh thành công (${finalCanvas.width}x${finalCanvas.height})!`);
    }, fmt, fmt === 'image/png' ? undefined : 0.94);
  } catch (e) {
    exporting = false;
    $('busy').hidden = true;
    toast(e.message);
  }
};

loadStoredLuts();
sync();
demo();


// === MATERIAL DESIGN 3 EXPRESSIVE UX & ANIMATION ENGINE (RIPPLE, THEME, TABS & TICK RULER) ===

function attachMd3Ripples() {
  document.querySelectorAll('button').forEach(btn => {
    if (btn.dataset.md3RippleAttached) return;
    btn.dataset.md3RippleAttached = '1';
    btn.addEventListener('pointerdown', ev => {
      const rect = btn.getBoundingClientRect();
      const ripple = document.createElement('span');
      const size = Math.max(rect.width, rect.height);
      ripple.className = 'md3-ripple';
      ripple.style.width = ripple.style.height = `${size}px`;
      ripple.style.left = `${ev.clientX - rect.left - size / 2}px`;
      ripple.style.top = `${ev.clientY - rect.top - size / 2}px`;
      btn.appendChild(ripple);
      setTimeout(() => ripple.remove(), 540);
    });
  });
}

function triggerCanvasSpringPop() {
  canvas.classList.remove('canvas-pop');
  void canvas.offsetWidth;
  canvas.classList.add('canvas-pop');
  setTimeout(() => canvas.classList.remove('canvas-pop'), 240);
}

// Chuyển đổi giao diện M3 Dark Darkroom <-> M3 Warm Porcelain (đồng bộ StudioStyle.java)
if ($('themeToggleBtn')) {
  $('themeToggleBtn').onclick = () => {
    document.body.classList.toggle('theme-porcelain');
    const isLight = document.body.classList.contains('theme-porcelain');
    drawTickRuler();
    toast(isLight ? 'Đã chuyển sang giao diện M3 Warm Porcelain (Sáng).' : 'Đã chuyển sang giao diện M3 Darkroom (Tối).');
  };
}

// M3 Segmented Category Tabs kèm hiệu ứng Staggered Cascade
if ($('categoryTabs')) {
  const allGroups = [...document.querySelectorAll('.control-group')];
  $('categoryTabs').querySelectorAll('.md3-tab').forEach(tab => {
    tab.onclick = () => {
      $('categoryTabs').querySelectorAll('.md3-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const cat = tab.dataset.cat;
      allGroups.forEach((grp, idx) => {
        const show = cat === 'all' || Number(cat) === idx;
        grp.style.display = show ? 'flex' : 'none';
        if (show) {
          grp.style.animation = 'none';
          void grp.offsetWidth;
          grp.style.animation = `md3CascadeIn 320ms cubic-bezier(0.05, 0.7, 0.1, 1) ${idx * 45}ms both`;
        }
      });
    };
  });
}

// Thanh trượt thước chia vạch độ chính xác cao chuẩn M3 Expressive (Web TickRulerView)
const rulerCanvas = $('tickRulerCanvas');
const rulerCtx = rulerCanvas ? rulerCanvas.getContext('2d') : null;
let activeRulerControl = ['exposure', 'Bù sáng · EV', -3, 3, 1 / 3];
let rulerDragging = false, rulerStartX = 0, rulerStartVal = 0;

function bindControlToTickRuler(ctrl) {
  activeRulerControl = ctrl;
  document.querySelectorAll('.control').forEach(c => c.classList.remove('focused-control'));
  const inputEl = $(ctrl[0]);
  if (inputEl && inputEl.closest('.control')) {
    inputEl.closest('.control').classList.add('focused-control');
  }
  drawTickRuler();
}

for (const [, list] of groups) {
  for (const ctrl of list) {
    if (!Array.isArray(ctrl[2])) {
      const el = $(ctrl[0]);
      if (el) {
        el.addEventListener('focus', () => bindControlToTickRuler(ctrl));
        el.addEventListener('pointerdown', () => bindControlToTickRuler(ctrl));
        el.addEventListener('input', () => {
          const outBadge = $(ctrl[0] + 'Value');
          if (outBadge) {
            outBadge.classList.add('pulse');
            setTimeout(() => outBadge.classList.remove('pulse'), 180);
          }
          drawTickRuler();
        });
      }
    }
  }
}

function drawTickRuler() {
  if (!rulerCtx || !activeRulerControl) return;
  const [key, label, min, max, step] = activeRulerControl;
  const val = Number(settings[key] || 0);
  if ($('rulerLabel')) $('rulerLabel').textContent = `M3 TICK RULER · ${label.toUpperCase()}`;
  if ($('rulerValue')) {
    $('rulerValue').textContent = key === 'exposure'
      ? (val >= 0 ? '+' : '') + val.toFixed(2) + ' EV'
      : (val >= 0 ? '+' : '') + Math.round(val * 100) / 100;
  }

  const w = rulerCanvas.width, h = rulerCanvas.height;
  rulerCtx.clearRect(0, 0, w, h);
  const centerX = w * 0.5, centerY = h * 0.54;
  const tickSpacing = 12;
  const totalSteps = Math.round((max - min) / step);
  const currentStepFloat = (val - min) / step;
  const zeroStepFloat = (0 - min) / step;
  const isLight = document.body.classList.contains('theme-porcelain');
  const accent = isLight ? '#e07a3b' : '#f59e0b';

  // Thanh Tonal Delta nối từ 0 đến giá trị hiện tại
  if (min < 0 && max > 0) {
    const zeroX = centerX + (zeroStepFloat - currentStepFloat) * tickSpacing;
    const left = Math.max(10, Math.min(zeroX, centerX));
    const right = Math.min(w - 10, Math.max(zeroX, centerX));
    rulerCtx.fillStyle = isLight ? 'rgba(224, 122, 59, 0.18)' : 'rgba(245, 158, 11, 0.18)';
    rulerCtx.fillRect(left, centerY - 11, right - left, 22);
  }

  const sigma = tickSpacing * 3.4;
  for (let i = 0; i <= totalSteps; i++) {
    const x = centerX + (i - currentStepFloat) * tickSpacing;
    if (x < 8 || x > w - 8) continue;
    const dist = Math.abs(x - centerX);
    const edgeFade = Math.max(0.1, 1 - dist / (w * 0.48));
    const gauss = Math.exp(-(dist * dist) / (2 * sigma * sigma));
    const tickVal = min + i * step;
    const isZero = Math.abs(tickVal) < step * 0.25;
    const isMajor = (i % 5 === 0) || isZero;
    const halfH = (isMajor ? 9.5 : 5) * (1 + 0.45 * gauss);

    rulerCtx.strokeStyle = isZero ? accent : isLight ? `rgba(24,26,31,${edgeFade * 0.75})` : `rgba(238,240,245,${edgeFade * 0.75})`;
    rulerCtx.lineWidth = isZero ? 2.4 : isMajor ? 1.7 : 1.1;
    rulerCtx.beginPath();
    rulerCtx.moveTo(x, centerY - halfH);
    rulerCtx.lineTo(x, centerY + halfH);
    rulerCtx.stroke();
  }

  // Kim chỉ thị trung tâm M3 Expressive
  rulerCtx.fillStyle = accent;
  rulerCtx.fillRect(centerX - 1.5, 4, 3, h - 8);
}

if (rulerCanvas) {
  rulerCanvas.addEventListener('pointerdown', ev => {
    rulerDragging = true;
    rulerStartX = ev.clientX;
    rulerStartVal = Number(settings[activeRulerControl[0]] || 0);
    rulerCanvas.setPointerCapture(ev.pointerId);
  });
  rulerCanvas.addEventListener('pointermove', ev => {
    if (!rulerDragging) return;
    const [key, , min, max, step] = activeRulerControl;
    const dx = ev.clientX - rulerStartX;
    let raw = rulerStartVal - (dx / 12) * step;
    if (min < 0 && max > 0 && Math.abs(raw) < step * 0.35) raw = 0;
    const snapped = Math.max(min, Math.min(max, Math.round(raw / step) * step));
    if (snapped !== settings[key]) {
      settings[key] = Number(snapped.toFixed(4));
      sync();
      drawTickRuler();
      render();
    }
  });
  const endDrag = () => { rulerDragging = false; };
  rulerCanvas.addEventListener('pointerup', endDrag);
  rulerCanvas.addEventListener('pointercancel', endDrag);
}

attachMd3Ripples();
drawTickRuler();

// =========================================================================
// THƯ VIỆN 392 CÔNG THỨC MÀU PHIM FUJI X WEEKLY (1-CLICK LOAD)
// =========================================================================
let allRecipes = [];
let currentSensor = 'all';

async function initRecipeLibrary() {
  try {
    const res = await fetch('./recipes.json');
    if (!res.ok) return;
    const data = await res.json();
    allRecipes = Array.isArray(data.recipes) ? data.recipes : [];
    if ($('totalRecipeCount')) $('totalRecipeCount').textContent = allRecipes.length;
    renderRecipeGrid();
  } catch (err) {
    console.warn('recipes.json chưa sẵn sàng:', err);
  }
}

function renderRecipeGrid() {
  const grid = $('recipeGrid');
  if (!grid) return;
  const search = ($('recipeSearchInput')?.value || '').toLowerCase().trim();
  const filtered = allRecipes.filter(r => {
    const matchSensor = currentSensor === 'all' || (r.sensors && r.sensors.some(s => s.toLowerCase().includes(currentSensor.toLowerCase())));
    if (!matchSensor) return false;
    if (!search) return true;
    const nameMatch = (r.name || '').toLowerCase().includes(search);
    const filmMatch = (r.film || '').toLowerCase().includes(search);
    return nameMatch || filmMatch;
  });

  grid.innerHTML = '';
  const toRender = filtered.slice(0, 80);
  for (const r of toRender) {
    const card = document.createElement('div');
    card.className = 'recipe-card';
    const thumbUrl = r.sampleImageUrl || (r.sampleImages && r.sampleImages[0]) || '';
    const filmName = r.film || 'Classic Chrome';
    const sensorsStr = (r.sensors || []).join(' / ');
    const raw = r.rawSettings || {};
    const hl = raw.Highlight || '0';
    const sh = raw.Shadow || '0';
    const wb = raw['White Balance'] ? raw['White Balance'].split(',')[0] : 'Auto';

    card.innerHTML = `
      <div class="recipe-card-thumb">
        ${thumbUrl ? `<img src="${thumbUrl}" loading="lazy" alt="${r.name}" onerror="this.style.display='none';this.nextElementSibling.style.display='block'">` : ''}
        <div class="fallback-art" style="${thumbUrl ? 'display:none;' : ''}">🎞️</div>
        <span class="recipe-card-badge">${filmName}</span>
      </div>
      <div class="recipe-card-content">
        <h4 class="recipe-card-title">${r.name}</h4>
        <div class="recipe-card-sensor">${sensorsStr}</div>
        <div class="recipe-card-specs">
          <span class="spec-badge">HL: ${hl}</span>
          <span class="spec-badge">SH: ${sh}</span>
          <span class="spec-badge">WB: ${wb}</span>
        </div>
        <button class="recipe-card-btn">Áp dụng công thức</button>
      </div>
    `;

    card.querySelector('.recipe-card-btn').onclick = (e) => {
      e.stopPropagation();
      applyFujiRecipe(r);
    };
    card.onclick = () => applyFujiRecipe(r);
    grid.appendChild(card);
  }
}

function applyFujiRecipe(recipe) {
  const parsed = parseFujiRecipe(recipe, defaults);
  settings = { ...settings, ...parsed.settings };
  quadView = false;
  sync();
  render();
  if ($('recipeModal')) $('recipeModal').hidden = true;
  toast(`Đã nạp công thức "${recipe.name}" (${parsed.film})!`);
}

if ($('recipeLibraryBtn')) {
  $('recipeLibraryBtn').onclick = () => {
    if ($('recipeModal')) $('recipeModal').hidden = false;
    if (allRecipes.length === 0) initRecipeLibrary();
  };
}

if ($('closeRecipeModal')) {
  $('closeRecipeModal').onclick = () => {
    if ($('recipeModal')) $('recipeModal').hidden = true;
  };
}
if ($('recipeModal')) {
  $('recipeModal').onclick = (e) => {
    if (e.target === $('recipeModal')) $('recipeModal').hidden = true;
  };
}

if ($('recipeSearchInput')) {
  $('recipeSearchInput').oninput = () => renderRecipeGrid();
}

if ($('recipeSensorChips')) {
  $('recipeSensorChips').querySelectorAll('.recipe-chip').forEach(chip => {
    chip.onclick = () => {
      $('recipeSensorChips').querySelectorAll('.recipe-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      currentSensor = chip.dataset.sensor;
      renderRecipeGrid();
    };
  });
}

// =========================================================================
// LIGHTCRAFT SUITE (TONE CURVE & 3-WAY COLOR WHEELS INTERACTIVE WIDGETS)
// =========================================================================
let redrawToneCurve = null;
let redrawColorWheels = null;

function initToneCurveWidget() {
  const c = $('toneCurveCanvas');
  if (!c) return;
  const tctx = c.getContext('2d');
  let draggingIdx = -1;

  function drawCurve() {
    const w = c.width, h = c.height;
    tctx.clearRect(0, 0, w, h);

    tctx.fillStyle = '#111318';
    tctx.fillRect(0, 0, w, h);

    // Grid 4x4
    tctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    tctx.lineWidth = 1;
    for (let i = 1; i < 4; i++) {
      const p = (i / 4) * w;
      tctx.beginPath(); tctx.moveTo(p, 0); tctx.lineTo(p, h); tctx.stroke();
      tctx.beginPath(); tctx.moveTo(0, p); tctx.lineTo(w, p); tctx.stroke();
    }

    // Diagonal
    tctx.strokeStyle = 'rgba(255, 255, 255, 0.16)';
    tctx.setLineDash([4, 4]);
    tctx.beginPath(); tctx.moveTo(0, h); tctx.lineTo(w, 0); tctx.stroke();
    tctx.setLineDash([]);

    // Curve
    const lut = buildMonotoneCurveLut(settings.curvePoints);
    tctx.strokeStyle = '#f59e0b';
    tctx.lineWidth = 2.5;
    tctx.beginPath();
    for (let x = 0; x < w; x++) {
      const inVal = Math.round((x / w) * 255);
      const outVal = lut[inVal];
      const y = h - (outVal / 255) * h;
      if (x === 0) tctx.moveTo(x, y); else tctx.lineTo(x, y);
    }
    tctx.stroke();

    // Control points
    settings.curvePoints.forEach(([px, py], i) => {
      const cx = px * w, cy = h - py * h;
      tctx.beginPath();
      tctx.arc(cx, cy, 6.5, 0, Math.PI * 2);
      tctx.fillStyle = (i === draggingIdx) ? '#ffea00' : '#ffffff';
      tctx.fill();
      tctx.strokeStyle = '#f59e0b';
      tctx.lineWidth = 2;
      tctx.stroke();
    });
  }

  function getCoords(e) {
    const rect = c.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    const x = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    const y = Math.max(0, Math.min(1, 1 - (clientY - rect.top) / rect.height));
    return { x, y };
  }

  const onDown = e => {
    const { x, y } = getCoords(e);
    let bestIdx = -1, bestDist = 0.09;
    settings.curvePoints.forEach(([px, py], i) => {
      const d = Math.hypot(px - x, py - y);
      if (d < bestDist) { bestDist = d; bestIdx = i; }
    });
    draggingIdx = bestIdx;
    if (draggingIdx >= 0) e.preventDefault();
  };

  const onMove = e => {
    if (draggingIdx < 0) return;
    e.preventDefault();
    const { x, y } = getCoords(e);
    if (draggingIdx === 0) settings.curvePoints[0] = [0, y];
    else if (draggingIdx === settings.curvePoints.length - 1) settings.curvePoints[draggingIdx] = [1, y];
    else {
      const prevX = settings.curvePoints[draggingIdx - 1][0] + 0.02;
      const nextX = settings.curvePoints[draggingIdx + 1][0] - 0.02;
      settings.curvePoints[draggingIdx] = [Math.max(prevX, Math.min(nextX, x)), y];
    }
    drawCurve();
    render();
  };

  const onUp = () => { draggingIdx = -1; };

  c.onmousedown = onDown;
  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);
  c.ontouchstart = onDown;
  window.addEventListener('touchmove', onMove, { passive: false });
  window.addEventListener('touchend', onUp);

  c.ondblclick = () => {
    settings.curvePoints = [[0, 0], [0.25, 0.25], [0.5, 0.5], [0.75, 0.75], [1, 1]];
    drawCurve();
    render();
    toast('Đã đặt lại đường cong Tone Curve về mặc định!');
  };

  if ($('resetCurveBtn')) {
    $('resetCurveBtn').onclick = () => {
      settings.curvePoints = [[0, 0], [0.25, 0.25], [0.5, 0.5], [0.75, 0.75], [1, 1]];
      drawCurve();
      render();
      toast('Đã đặt lại đường cong Tone Curve.');
    };
  }

  drawCurve();
  redrawToneCurve = drawCurve;
}

function initColorWheelsWidget() {
  const c = $('colorWheelsCanvas');
  if (!c) return;
  const wctx = c.getContext('2d');
  const wheelLabels = ['SHADOWS', 'MIDTONES', 'HIGHLIGHTS'];
  let activeWheel = -1;

  function drawWheels() {
    const w = c.width, h = c.height;
    wctx.clearRect(0, 0, w, h);
    const radius = Math.min(w / 3.4, h * 0.38);
    const centerY = h * 0.54;

    for (let i = 0; i < 3; i++) {
      const cx = (w / 3) * (i + 0.5);

      for (let angle = 0; angle < 360; angle += 4) {
        const rad1 = (angle * Math.PI) / 180;
        const rad2 = ((angle + 4.5) * Math.PI) / 180;
        wctx.beginPath();
        wctx.moveTo(cx, centerY);
        wctx.arc(cx, centerY, radius, rad1, rad2);
        wctx.closePath();
        wctx.fillStyle = `hsl(${angle}, 85%, 55%)`;
        wctx.fill();
      }

      const radial = wctx.createRadialGradient(cx, centerY, 0, cx, centerY, radius);
      radial.addColorStop(0, '#ffffff');
      radial.addColorStop(0.35, 'rgba(255, 255, 255, 0.85)');
      radial.addColorStop(1, 'rgba(255, 255, 255, 0)');
      wctx.fillStyle = radial;
      wctx.beginPath();
      wctx.arc(cx, centerY, radius, 0, Math.PI * 2);
      wctx.fill();

      wctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
      wctx.lineWidth = 1.5;
      wctx.stroke();

      wctx.fillStyle = '#9ca3b0';
      wctx.font = '700 10px system-ui, sans-serif';
      wctx.textAlign = 'center';
      wctx.fillText(wheelLabels[i], cx, centerY - radius - 8);

      const hue = i === 0 ? settings.shadowsHue : i === 1 ? settings.midtonesHue : settings.highlightsHue;
      const sat = i === 0 ? settings.shadowsSat : i === 1 ? settings.midtonesSat : settings.highlightsSat;
      const rPuck = (sat / 100) * radius;
      const angRad = (hue * Math.PI) / 180;
      const px = cx + Math.cos(angRad) * rPuck;
      const py = centerY + Math.sin(angRad) * rPuck;

      wctx.beginPath();
      wctx.arc(px, py, 5.5, 0, Math.PI * 2);
      wctx.fillStyle = sat > 0 ? `hsl(${hue}, 90%, 55%)` : '#333333';
      wctx.fill();
      wctx.strokeStyle = '#ffffff';
      wctx.lineWidth = 2;
      wctx.stroke();
    }
  }

  function handlePointer(e) {
    const rect = c.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    const x = (clientX - rect.left) * (c.width / rect.width);
    const y = (clientY - rect.top) * (c.height / rect.height);
    const radius = Math.min(c.width / 3.4, c.height * 0.38);
    const centerY = c.height * 0.54;

    if (activeWheel < 0) {
      for (let i = 0; i < 3; i++) {
        const cx = (c.width / 3) * (i + 0.5);
        if (Math.hypot(x - cx, y - centerY) <= radius * 1.15) {
          activeWheel = i;
          break;
        }
      }
    }

    if (activeWheel >= 0) {
      const cx = (c.width / 3) * (activeWheel + 0.5);
      const dx = x - cx, dy = y - centerY;
      const dist = Math.min(radius, Math.hypot(dx, dy));
      const sat = Math.round((dist / radius) * 100);
      let angle = Math.round((Math.atan2(dy, dx) * 180) / Math.PI);
      if (angle < 0) angle += 360;

      if (activeWheel === 0) { settings.shadowsHue = angle; settings.shadowsSat = sat; }
      else if (activeWheel === 1) { settings.midtonesHue = angle; settings.midtonesSat = sat; }
      else { settings.highlightsHue = angle; settings.highlightsSat = sat; }

      drawWheels();
      render();
    }
  }

  c.onmousedown = handlePointer;
  window.addEventListener('mousemove', e => { if (activeWheel >= 0) { e.preventDefault(); handlePointer(e); } });
  window.addEventListener('mouseup', () => { activeWheel = -1; });
  c.ontouchstart = handlePointer;
  window.addEventListener('touchmove', e => { if (activeWheel >= 0) { e.preventDefault(); handlePointer(e); } }, { passive: false });
  window.addEventListener('touchend', () => { activeWheel = -1; });

  c.ondblclick = e => {
    const rect = c.getBoundingClientRect();
    const x = (e.clientX - rect.left) * (c.width / rect.width);
    for (let i = 0; i < 3; i++) {
      const cx = (c.width / 3) * (i + 0.5);
      if (Math.abs(x - cx) < c.width / 6) {
        if (i === 0) { settings.shadowsHue = 0; settings.shadowsSat = 0; }
        else if (i === 1) { settings.midtonesHue = 0; settings.midtonesSat = 0; }
        else { settings.highlightsHue = 0; settings.highlightsSat = 0; }
        drawWheels();
        render();
        toast(`Đã đặt lại bánh xe màu ${wheelLabels[i]}.`);
        break;
      }
    }
  };

  if ($('resetWheelsBtn')) {
    $('resetWheelsBtn').onclick = () => {
      settings.shadowsHue = 0; settings.shadowsSat = 0;
      settings.midtonesHue = 0; settings.midtonesSat = 0;
      settings.highlightsHue = 0; settings.highlightsSat = 0;
      drawWheels();
      render();
      toast('Đã đặt lại bộ 3 bánh xe màu về trung tính.');
    };
  }

  drawWheels();
  redrawColorWheels = drawWheels;
}

if ($('lightCraftBtn')) {
  $('lightCraftBtn').onclick = () => {
    if ($('lightCraftModal')) $('lightCraftModal').hidden = false;
    if (redrawToneCurve) redrawToneCurve();
    if (redrawColorWheels) redrawColorWheels();
  };
}
if ($('closeLightCraftModal')) {
  $('closeLightCraftModal').onclick = () => {
    if ($('lightCraftModal')) $('lightCraftModal').hidden = true;
  };
}
if ($('lightCraftModal')) {
  $('lightCraftModal').onclick = (e) => {
    if (e.target === $('lightCraftModal')) $('lightCraftModal').hidden = true;
  };
}

// EXIF modal
if ($('editExifBtn')) {
  $('editExifBtn').onclick = () => {
    if ($('exifModal')) {
      $('exifCamera').value = currentPhotoInfo.camera || '';
      $('exifLens').value = currentPhotoInfo.lens || '';
      $('exifFocal').value = currentPhotoInfo.focal || '';
      $('exifAperture').value = currentPhotoInfo.aperture || '';
      $('exifShutter').value = currentPhotoInfo.shutter || '';
      $('exifIso').value = currentPhotoInfo.iso || '';
      $('exifDate').value = currentPhotoInfo.date || '';
      $('exifAuthor').value = currentPhotoInfo.author || '';
      $('exifModal').hidden = false;
    }
  };
}
if ($('closeExifModal')) {
  $('closeExifModal').onclick = () => {
    if ($('exifModal')) $('exifModal').hidden = true;
  };
}
if ($('saveExifBtn')) {
  $('saveExifBtn').onclick = () => {
    currentPhotoInfo = {
      camera: $('exifCamera').value.trim() || 'FUJIFILM X-T5',
      lens: $('exifLens').value.trim() || 'XF 35mm F1.4 R',
      focal: $('exifFocal').value.trim() || '35mm',
      aperture: $('exifAperture').value.trim() || 'f/1.4',
      shutter: $('exifShutter').value.trim() || '1/250s',
      iso: $('exifIso').value.trim() || 'ISO 400',
      date: $('exifDate').value.trim() || '2026.10.14',
      author: $('exifAuthor').value.trim() || 'IRO Studio'
    };
    if ($('exifModal')) $('exifModal').hidden = true;
    toast('Đã lưu thông số máy ảnh & EXIF cho khung ảnh!');
  };
}

initRecipeLibrary();
initToneCurveWidget();
initColorWheelsWidget();

