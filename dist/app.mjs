import {
  defaults, films, parseCube, processPixels, rotatePixels90,
  autoBalanceSettings, promptToRecipeVi, encodeRecipeCode, decodeRecipeCode,
  embedRecipeIntoPixels, extractRecipeFromPixels,
  bakeRecipeToCube, extractDominantPalette, matchColorFromReference
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
    settings.frame = e.target.value;
    toast(`Khung ảnh khi xuất: ${e.target.options[e.target.selectedIndex].text}`);
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

function renderFramedCanvas(rawCanvas, rawPixels, s) {
  drawQuartzDateStamp(rawCanvas.getContext('2d'), rawCanvas.width, rawCanvas.height, s.dateStamp);
  if (!s.frame || s.frame === 'None') return rawCanvas;
  const pad = Math.round(Math.max(rawCanvas.width, rawCanvas.height) * 0.048);

  if (s.frame === 'Film35mm') {
    const topBar = Math.round(pad * 1.45);
    const out = document.createElement('canvas');
    out.width = rawCanvas.width + pad;
    out.height = rawCanvas.height + topBar * 2;
    const g = out.getContext('2d');
    g.fillStyle = '#0c0d0f';
    g.fillRect(0, 0, out.width, out.height);
    g.drawImage(rawCanvas, Math.round(pad * 0.5), topBar);

    g.fillStyle = '#e5e7eb';
    const holeW = Math.max(12, Math.round(topBar * 0.42));
    const holeH = Math.max(16, Math.round(topBar * 0.54));
    const step = Math.round(holeW * 2.1);
    for (let x = pad; x < out.width - pad; x += step) {
      g.fillRect(x, Math.round((topBar - holeH) * 0.5), holeW, holeH);
      g.fillRect(x, out.height - topBar + Math.round((topBar - holeH) * 0.5), holeW, holeH);
    }
    g.fillStyle = '#f59e0b';
    g.font = `700 ${Math.max(11, Math.round(topBar * 0.24))}px monospace`;
    g.fillText(`FUJIFILM ${s.film.toUpperCase()} 400 · IRO SAFETY FILM`, pad, topBar - 4);
    g.fillText(`► 24A   DR${s.dr}   CHROME ${s.chrome}`, pad, out.height - 5);
    return out;
  }

  if (s.frame === 'HasselbladV') {
    const out = document.createElement('canvas');
    out.width = rawCanvas.width + pad * 2;
    out.height = rawCanvas.height + pad * 2;
    const g = out.getContext('2d');
    g.fillStyle = '#0e0f12';
    g.fillRect(0, 0, out.width, out.height);
    g.strokeStyle = '#ffffff';
    g.lineWidth = Math.max(2, Math.round(pad * 0.08));
    g.strokeRect(pad - 2, pad - 2, rawCanvas.width + 4, rawCanvas.height + 4);
    g.drawImage(rawCanvas, pad, pad);
    const vSize = Math.max(10, Math.round(pad * 0.28));
    const vy1 = pad + Math.round(rawCanvas.height * 0.28);
    const vy2 = pad + Math.round(rawCanvas.height * 0.72);
    g.fillStyle = '#0e0f12';
    for (const vy of [vy1, vy2]) {
      g.beginPath();
      g.moveTo(pad + rawCanvas.width + 4, vy - vSize);
      g.lineTo(pad + rawCanvas.width - vSize, vy);
      g.lineTo(pad + rawCanvas.width + 4, vy + vSize);
      g.closePath();
      g.fill();
    }
    return out;
  }

  const bottomPad = Math.round(pad * 2.5);
  const out = document.createElement('canvas');
  out.width = rawCanvas.width + pad * 2;
  out.height = rawCanvas.height + pad + bottomPad;
  const g = out.getContext('2d');
  const dark = s.frame === 'DarkroomBlack';
  g.fillStyle = dark ? '#101214' : s.frame === 'PostcardGPS' ? '#f6f1e7' : '#faf8f5';
  g.fillRect(0, 0, out.width, out.height);
  g.drawImage(rawCanvas, pad, pad);

  g.fillStyle = dark ? '#e5e7eb' : '#1f2937';
  const fontSize = Math.max(14, Math.round(pad * 0.42));
  g.font = `600 ${fontSize}px system-ui, -apple-system, sans-serif`;
  const titleText = s.frame === 'PostcardGPS'
    ? `POSTCARD ARCHIVE · ${s.film.toUpperCase()} · VIETNAM`
    : `FUJIFILM SIMULATION · ${s.film.toUpperCase()}`;
  g.fillText(titleText, pad, rawCanvas.height + pad + Math.round(bottomPad * 0.48));

  g.fillStyle = dark ? '#9ca3af' : '#6b7280';
  g.font = `400 ${Math.round(fontSize * 0.78)}px monospace`;
  const metaText = `DR${s.dr} · WB ${s.wb} · Clarity ${s.clarity} · ProMist ${s.proMist}% · ${encodeRecipeCode(s).slice(0, 18)}...`;
  g.fillText(metaText, pad, rawCanvas.height + pad + Math.round(bottomPad * 0.78));

  if (rawPixels) {
    const palette = extractDominantPalette(rawPixels, 5);
    const radius = Math.max(8, Math.round(fontSize * 0.55));
    const startX = out.width - pad - (palette.length * radius * 2.6) + radius;
    const centerY = rawCanvas.height + pad + Math.round(bottomPad * 0.58);
    palette.forEach((sw, i) => {
      g.beginPath();
      g.arc(startX + i * radius * 2.6, centerY, radius, 0, Math.PI * 2);
      g.fillStyle = sw.hex;
      g.fill();
      g.lineWidth = 1.5;
      g.strokeStyle = dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.15)';
      g.stroke();
    });
  }
  return out;
}

$('export').onclick = async () => {
  if (!source || exporting) return;
  exporting = true;
  $('busy').hidden = false;
  try {
    const fmt = $('format').value;
    const ext = fmt === 'image/jpeg' ? '.jpg' : fmt === 'image/webp' ? '.webp' : '.png';
    const result = await job({
      pixels: source.getContext('2d').getImageData(0, 0, source.width, source.height).data,
      width: source.width,
      height: source.height,
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
      a.download = `${base}-IRO${ext}`;
      a.click();
      URL.revokeObjectURL(a.href);
      exporting = false;
      $('busy').hidden = true;
      toast(fmt === 'image/png' ? 'Đã xuất PNG (kèm công thức nhúng ẩn trong ảnh)!' : 'Xuất ảnh thành công!');
    }, fmt, fmt === 'image/png' ? undefined : 0.92);
  } catch (e) {
    exporting = false;
    $('busy').hidden = true;
    toast(e.message);
  }
};

loadStoredLuts();
sync();
demo();