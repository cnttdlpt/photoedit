import {
  defaults, parseCube, processPixels, rotatePixels90,
  buildMonotoneCurveLut, computeHistogramAndStats,
  autoBalanceSettings, CURATED_PRESETS,
  encodeRecipeCode, decodeRecipeCode,
  bakeRecipeToCube, extractDominantPalette, matchColorFromReference,
  promptToRecipeVi, embedRecipeIntoPixels, extractRecipeFromPixels,
  parseFujiRecipe, LIGHTCRAFT_PRESETS, PHOTO_FRAMES, renderFramedImage
} from './engine.mjs';

const canvas = document.querySelector('#canvas');
const ctx = canvas.getContext('2d', { willReadFrequently: true });
const beforeCanvas = document.querySelector('#beforeCanvas');
const beforeCtx = beforeCanvas.getContext('2d');
const canvasWrapper = document.querySelector('#canvasWrapper');
const splitSlider = document.querySelector('#splitSlider');
const histCanvas = document.querySelector('#histogramCanvas');
const histCtx = histCanvas.getContext('2d');
const curveCanvas = document.querySelector('#curveCanvas');
const curveCtx = curveCanvas.getContext('2d');
const status = document.querySelector('#status');
const empty = document.querySelector('#empty');
const quadViewGrid = document.querySelector('#quadViewGrid');
const contactSheetModal = document.querySelector('#contactSheetModal');
const contactSheetGrid = document.querySelector('#contactSheetGrid');
const promptModal = document.querySelector('#promptModal');
const promptTextInput = document.querySelector('#promptTextInput');
const dominantPaletteBar = document.querySelector('#dominantPaletteBar');
const zoomResetBadge = document.querySelector('#zoomResetBadge');
const topActiveFilmLabel = document.querySelector('#topActiveFilmLabel');

// Kho 392 công thức FujiXWeekly & LightCraft Modal & Command Palette
const recipeLibraryModal = document.querySelector('#recipeLibraryModal');
const recipeLibraryGrid = document.querySelector('#recipeLibraryGrid');
const recipeSearchInput = document.querySelector('#recipeSearchInput');
const recipeFilmFilterChips = document.querySelector('#recipeFilmFilterChips');
const lightCraftModal = document.querySelector('#lightCraftModal');
const lightCraftPresetsGrid = document.querySelector('#lightCraftPresetsGrid');
const hslSlidersContainer = document.querySelector('#hslSlidersContainer');
const frameStyleSelect = document.querySelector('#frameStyle');
const frameExifControls = document.querySelector('#frameExifControls');
const frameTitleInput = document.querySelector('#frameTitleInput');
const frameExifInput = document.querySelector('#frameExifInput');
const cmdPaletteModal = document.querySelector('#cmdPaletteModal');
const cmdSearchInput = document.querySelector('#cmdSearchInput');
const cmdQuickResults = document.querySelector('#cmdQuickResults');

const state = {
  source: null,
  lut: null,
  settings: structuredClone(defaults),
  activeRecipeName: 'Gốc',
  history: [],
  historyIndex: -1,
  comparingSplit: false,
  splitRatio: 0.5,
  quadViewActive: false,
  livingFilmActive: false,
  livingFilmTimer: null,
  livingFilmFrame: 0,
  curvePoints: [[0, 0], [1, 1]],
  draggingCurveIdx: -1,
  fullCatalogRecipes: [],
  selectedFilmFilter: 'Tất cả',
  favoriteRecipeIds: new Set(JSON.parse(localStorage.getItem('iro_fav_recipes_v3') || '[]')),
  activeHslMode: 'hue', // 'hue' | 'sat' | 'lum'
  frameStyle: 0,
  stageZoom: 1.0,
  stagePanX: 0,
  stagePanY: 0,
  porcelainTheme: false
};

// 18 thanh trượt hậu kỳ chia làm 2 nhóm: Ánh sáng & Quang học 3.0
const basicSliderSpecs = [
  ['exposure', 'Phơi sáng (EV)', -3, 3, 0.1],
  ['highlight', 'Vùng sáng (Highlight)', -2, 4, 0.5],
  ['shadow', 'Vùng tối (Shadow)', -2, 4, 0.5],
  ['color', 'Độ bão hòa màu (Color)', -4, 4, 0.5],
  ['temperature', 'Nhiệt độ màu (WB Temp)', -100, 100, 1],
  ['red', 'Dịch cân bằng trắng Đỏ (WB R)', -9, 9, 1],
  ['blue', 'Dịch cân bằng trắng Xanh (WB B)', -9, 9, 1],
  ['colorChrome', 'Hiệu ứng Color Chrome', 0, 2, 1],
  ['chromeBlue', 'Color Chrome FX Blue', 0, 2, 1],
  ['lutAmount', 'Cường độ 3D LUT (%)', 0, 100, 1]
];

const opticalSliderSpecs = [
  ['proMist', 'Kính lọc khuếch tán (Black Pro-Mist)', 0, 100, 1],
  ['halation', 'Quầng đỏ vùng sáng (Halation 800T)', 0, 100, 1],
  ['bleachBypass', 'Giữ bạc phòng tối (Bleach Bypass)', 0, 100, 1],
  ['dehaze', 'Khử mù sương quang học (Dehaze)', -100, 100, 1],
  ['texture', 'Chi tiết vi mô / Mịn da (Texture)', -100, 100, 1],
  ['clarity', 'Độ trong khối (Clarity)', -50, 50, 1],
  ['sharpness', 'Độ sắc nét (Sharpness)', -4, 4, 0.5],
  ['nr', 'Khử nhiễu (High ISO NR)', -4, 4, 0.5],
  ['grain', 'Cường độ hạt phim (Film Grain)', 0, 30, 1],
  ['skinProtect', 'Bảo vệ tông màu da (Skin Mask %)', 0, 100, 5],
  ['swirlyBokeh', 'Bokeh xoáy ống kính cổ (Helios 44-2)', 0, 100, 2],
  ['depthFog', 'Sương mù chiều sâu 3D (Depth Fog)', 0, 100, 2],
  ['lightLeak', 'Vệt lọt sáng buồng phim (Light Leak)', 0, 100, 1],
  ['vignette', 'Tối góc ống kính (Vignette)', -50, 50, 1]
];

const allSliderSpecs = [...basicSliderSpecs, ...opticalSliderSpecs];

function saveFavorites() {
  try {
    localStorage.setItem('iro_fav_recipes_v3', JSON.stringify([...state.favoriteRecipeIds]));
  } catch (_) {}
}

function updateTopRecipePill() {
  if (!topActiveFilmLabel) return;
  const film = state.settings.film || 'Original';
  const rec = state.activeRecipeName || 'Tùy chỉnh';
  topActiveFilmLabel.textContent = `${film} · ${rec}`;
}

function createSliders(containerSelector, specs) {
  const root = document.querySelector(containerSelector);
  for (const [key, label, min, max, step] of specs) {
    const wrap = document.createElement('label');
    wrap.className = 'slider';
    wrap.title = 'Nhấp đúp để đặt lại thông số này về mặc định';
    wrap.innerHTML = `<span>${label}</span><output id="out-${key}">${defaults[key]}</output>
      <input id="in-${key}" data-key="${key}" type="range" min="${min}" max="${max}" step="${step}" value="${defaults[key]}" />`;
    wrap.addEventListener('dblclick', () => {
      state.settings[key] = defaults[key];
      syncInputsFromSettings();
      commitHistory(`Đặt lại ${label}`);
      render();
    });
    root.append(wrap);
  }
}

createSliders('#slidersBasic', basicSliderSpecs);
createSliders('#slidersOptical', opticalSliderSpecs);

// =========================================================================
// THANH ĐIỀU HƯỚNG DỌC MATERIAL 3 (LEFT NAVIGATION RAIL) & TAB SWITCHER
// =========================================================================
const segTabBtns = document.querySelectorAll('.m3-seg-btn');
const tabPanes = document.querySelectorAll('.m3-tab-pane');
const railItems = document.querySelectorAll('.rail-item');

function activateInspectorTab(targetId) {
  segTabBtns.forEach(b => b.classList.toggle('active', b.dataset.tabTarget === targetId));
  tabPanes.forEach(pane => {
    pane.hidden = pane.id !== targetId;
  });
  railItems.forEach(item => {
    if (item.dataset.railTarget) {
      item.classList.toggle('active', item.dataset.railTarget === targetId);
    }
  });
}

segTabBtns.forEach(btn => {
  btn.addEventListener('click', () => activateInspectorTab(btn.dataset.tabTarget));
});

railItems.forEach(item => {
  item.addEventListener('click', () => {
    const target = item.dataset.railTarget;
    const action = item.dataset.railAction;
    if (target) {
      activateInspectorTab(target);
    } else if (action === 'lightcraft') {
      if (!state.source) {
        setStatus('Hãy mở ảnh hoặc chọn ảnh mẫu trước khi mở Buồng tối LightCraft.');
        return;
      }
      lightCraftModal.hidden = false;
      drawAllColorWheels();
      renderHslSliders();
    } else if (action === 'library') {
      openRecipeLibraryModal();
    }
  });
});

document.querySelector('#railSampleCycleBtn')?.addEventListener('click', () => {
  const samples = ['portrait', 'street', 'landscape'];
  const next = samples[(Math.floor(Date.now() / 1000) + 1) % samples.length];
  generateSampleImage(next);
});

document.querySelector('#railResetAllBtn')?.addEventListener('click', () => {
  document.querySelector('#resetAll').click();
});

// =========================================================================
// CHẾ ĐỘ SÁNG / TỐI (WARM PORCELAIN & OBSIDIAN DARKROOM)
// =========================================================================
const themeToggleBtn = document.querySelector('#themeToggleBtn');
if (themeToggleBtn) {
  themeToggleBtn.addEventListener('click', () => {
    state.porcelainTheme = !state.porcelainTheme;
    document.body.classList.toggle('theme-porcelain', state.porcelainTheme);
    themeToggleBtn.textContent = state.porcelainTheme ? '🌙' : '☀️';
    drawTickRuler();
    drawCurveEditor();
    setStatus(state.porcelainTheme
      ? 'Đã chuyển sang giao diện Sáng (Warm Porcelain M3 Studio).'
      : 'Đã chuyển sang giao diện Phòng tối (Obsidian Darkroom M3 Studio).');
  });
}

// =========================================================================
// THƯỚC CHIA VẠCH GAUSSIAN M3 (PRECISION TICK RULER DOCK)
// =========================================================================
const tickRulerCanvas = document.querySelector('#m3TickRulerCanvas');
const tickRulerCtx = tickRulerCanvas ? tickRulerCanvas.getContext('2d') : null;
const rulerActiveLabel = document.querySelector('#rulerActiveLabel');
const rulerActiveValue = document.querySelector('#rulerActiveValue');
const rulerResetParamBtn = document.querySelector('#rulerResetParamBtn');
const m3ParamPillStrip = document.querySelector('#m3ParamPillStrip');

let activeRulerSpec = basicSliderSpecs[0];
let rulerDragging = false;
let rulerDragStartX = 0;
let rulerDragStartVal = 0;

function formatParamReadout(key, val) {
  const num = Number(val);
  if (key === 'colorChrome' || key === 'chromeBlue') {
    return num === 0 ? 'Tắt' : (num === 1 ? 'Nhẹ' : 'Mạnh');
  }
  if (key === 'lutAmount' || key === 'skinProtect') {
    return `${Math.round(num)}%`;
  }
  const rounded = Math.round(num * 10) / 10;
  return (rounded > 0 ? '+' : '') + rounded;
}

function initM3RulerDock() {
  if (!m3ParamPillStrip || !tickRulerCanvas) return;
  m3ParamPillStrip.innerHTML = '';
  for (const spec of allSliderSpecs) {
    const [key, label] = spec;
    const shortName = label.split(' (')[0];
    const pill = document.createElement('button');
    pill.type = 'button';
    pill.className = 'm3-param-pill' + (key === activeRulerSpec[0] ? ' active' : '');
    pill.dataset.paramKey = key;
    pill.textContent = shortName;
    pill.addEventListener('click', () => {
      activeRulerSpec = spec;
      syncRulerHeader();
      drawTickRuler();
    });
    m3ParamPillStrip.append(pill);
  }
  syncRulerHeader();
  drawTickRuler();
}

function syncRulerHeader() {
  if (!rulerActiveLabel || !rulerActiveValue) return;
  const [key, label] = activeRulerSpec;
  const val = Number(state.settings[key] ?? defaults[key] ?? 0);
  rulerActiveLabel.textContent = label;
  rulerActiveValue.textContent = formatParamReadout(key, val);
  if (m3ParamPillStrip) {
    m3ParamPillStrip.querySelectorAll('.m3-param-pill').forEach(btn => {
      const k = btn.dataset.paramKey;
      btn.classList.toggle('active', k === key);
      const cur = Number(state.settings[k] ?? defaults[k] ?? 0);
      const def = Number(defaults[k] ?? 0);
      btn.classList.toggle('modified', Math.abs(cur - def) > 0.01);
    });
  }
}

function drawTickRuler() {
  if (!tickRulerCtx || !tickRulerCanvas) return;
  const w = tickRulerCanvas.width;
  const h = tickRulerCanvas.height;
  tickRulerCtx.clearRect(0, 0, w, h);

  const [key, , min, max, step] = activeRulerSpec;
  const val = Number(state.settings[key] ?? defaults[key] ?? 0);
  const defVal = Number(defaults[key] ?? 0);
  const totalSteps = Math.max(1, Math.round((max - min) / step));
  const currentStep = (val - min) / step;
  const zeroStep = (defVal - min) / step;
  const tickSpacing = 12;
  const centerX = w / 2;
  const centerY = h * 0.56;

  // Dải Tonal Delta nối từ vị trí mặc định (0) đến vị trí hiện tại
  const zeroX = centerX + (zeroStep - currentStep) * tickSpacing;
  const deltaLeft = Math.max(12, Math.min(zeroX, centerX));
  const deltaRight = Math.min(w - 12, Math.max(zeroX, centerX));
  if (deltaRight - deltaLeft > 1.5) {
    tickRulerCtx.fillStyle = state.porcelainTheme
      ? 'rgba(217, 119, 6, 0.18)'
      : 'rgba(240, 154, 62, 0.22)';
    tickRulerCtx.beginPath();
    tickRulerCtx.roundRect(deltaLeft, centerY - 12, deltaRight - deltaLeft, 24, 6);
    tickRulerCtx.fill();
  }

  // Các vạch chia với hiệu ứng phóng đại sóng Gaussian quanh kim trung tâm
  const sigma = tickSpacing * 3.4;
  for (let i = 0; i <= totalSteps; i++) {
    const x = centerX + (i - currentStep) * tickSpacing;
    if (x < 10 || x > w - 10) continue;
    const dist = Math.abs(x - centerX);
    const fade = Math.max(0, 1 - dist / (w * 0.48));
    const gaussian = Math.exp(-(dist * dist) / (2 * sigma * sigma)) * (rulerDragging ? 1.0 : 0.45);
    const isMajor = i % 5 === 0;
    const isZero = Math.abs((min + i * step) - defVal) < step * 0.25;

    const baseHalfH = isZero ? 13 : (isMajor ? 10 : 5.5);
    const tickHalfH = baseHalfH * (1 + 0.42 * gaussian);

    tickRulerCtx.strokeStyle = isZero
      ? '#f09a3e'
      : state.porcelainTheme
        ? `rgba(22, 24, 29, ${(fade * (isMajor ? 0.78 : 0.35)).toFixed(2)})`
        : `rgba(243, 244, 248, ${(fade * (isMajor ? 0.85 : 0.38)).toFixed(2)})`;
    tickRulerCtx.lineWidth = isZero ? 2.5 : (isMajor ? 1.8 : 1.15);
    tickRulerCtx.beginPath();
    tickRulerCtx.moveTo(x, centerY - tickHalfH);
    tickRulerCtx.lineTo(x, centerY + tickHalfH);
    tickRulerCtx.stroke();

    if (isZero) {
      tickRulerCtx.fillStyle = '#f09a3e';
      tickRulerCtx.beginPath();
      tickRulerCtx.arc(x, centerY - tickHalfH - 4, 2.6, 0, Math.PI * 2);
      tickRulerCtx.fill();
    }
  }

  // Kim chỉ thị trung tâm chuẩn M3 Expressive (vươn dài khi kéo)
  tickRulerCtx.fillStyle = '#f09a3e';
  const needleTop = rulerDragging ? 4 : 7;
  const needleBot = h - (rulerDragging ? 4 : 7);
  tickRulerCtx.beginPath();
  tickRulerCtx.roundRect(centerX - 1.8, needleTop, 3.6, needleBot - needleTop, 2);
  tickRulerCtx.fill();
}

if (tickRulerCanvas) {
  tickRulerCanvas.addEventListener('pointerdown', e => {
    if (!state.source) return;
    rulerDragging = true;
    rulerDragStartX = e.clientX;
    const [key] = activeRulerSpec;
    rulerDragStartVal = Number(state.settings[key] ?? defaults[key] ?? 0);
    tickRulerCanvas.setPointerCapture(e.pointerId);
    drawTickRuler();
  });

  tickRulerCanvas.addEventListener('pointermove', e => {
    if (!rulerDragging || !state.source) return;
    const [key, , min, max, step] = activeRulerSpec;
    const defVal = Number(defaults[key] ?? 0);
    const dx = e.clientX - rulerDragStartX;
    let raw = rulerDragStartVal - (dx / 12) * step;
    // Nam châm hút nhẹ về giá trị mặc định (Magnetic Zero-Detent Snap)
    if (Math.abs(raw - defVal) < step * 0.35) {
      raw = defVal;
    }
    const snapped = Math.max(min, Math.min(max, Math.round(raw / step) * step));
    const fixed = Number(snapped.toFixed(2));
    if (state.settings[key] !== fixed) {
      state.settings[key] = fixed;
      syncInputsFromSettings();
      render();
    }
  });

  const endRulerDrag = () => {
    if (!rulerDragging) return;
    rulerDragging = false;
    drawTickRuler();
    commitHistory(`Chỉnh ${activeRulerSpec[1]}`);
  };
  tickRulerCanvas.addEventListener('pointerup', endRulerDrag);
  tickRulerCanvas.addEventListener('pointercancel', endRulerDrag);

  tickRulerCanvas.addEventListener('wheel', e => {
    if (!state.source) return;
    e.preventDefault();
    const [key, , min, max, step] = activeRulerSpec;
    const dir = e.deltaY < 0 ? 1 : -1;
    const cur = Number(state.settings[key] ?? defaults[key] ?? 0);
    const next = Math.max(min, Math.min(max, Number((cur + dir * step).toFixed(2))));
    state.settings[key] = next;
    syncInputsFromSettings();
    render();
  }, { passive: false });

  tickRulerCanvas.addEventListener('dblclick', () => {
    if (!state.source) return;
    const [key, label] = activeRulerSpec;
    state.settings[key] = defaults[key];
    syncInputsFromSettings();
    commitHistory(`Đặt lại ${label}`);
    render();
  });
}

if (rulerResetParamBtn) {
  rulerResetParamBtn.addEventListener('click', () => {
    if (!state.source) return;
    const [key, label] = activeRulerSpec;
    state.settings[key] = defaults[key];
    syncInputsFromSettings();
    commitHistory(`Đặt lại ${label}`);
    render();
  });
}

initM3RulerDock();

// =========================================================================
// PHÓNG TO & KÉO TRƯỢT TRÊN SÂN KHẤU TRUNG TÂM (INTERACTIVE STAGE ZOOM & PAN)
// =========================================================================
const stageContainer = document.querySelector('#stageContainer');
let isPanningStage = false;
let panStartClientX = 0;
let panStartClientY = 0;

function applyStageTransform() {
  if (!canvasWrapper) return;
  if (state.stageZoom <= 1.01) {
    state.stageZoom = 1.0;
    state.stagePanX = 0;
    state.stagePanY = 0;
    canvasWrapper.style.transform = '';
    if (zoomResetBadge) zoomResetBadge.hidden = true;
  } else {
    canvasWrapper.style.transform = `translate(${state.stagePanX}px, ${state.stagePanY}px) scale(${state.stageZoom})`;
    if (zoomResetBadge) {
      zoomResetBadge.hidden = false;
      zoomResetBadge.textContent = `🔍 ${Math.round(state.stageZoom * 100)}% · Đặt lại`;
    }
  }
}

stageContainer.addEventListener('wheel', e => {
  if (!state.source || canvasWrapper.hidden) return;
  // Không chặn cuộn nếu đang mở hộp thoại modal
  if (!recipeLibraryModal.hidden || !lightCraftModal.hidden || !contactSheetModal.hidden || !promptModal.hidden || !cmdPaletteModal.hidden) {
    return;
  }
  e.preventDefault();
  const factor = e.deltaY < 0 ? 1.12 : (1 / 1.12);
  state.stageZoom = Math.max(1.0, Math.min(4.0, state.stageZoom * factor));
  applyStageTransform();
}, { passive: false });

canvasWrapper.addEventListener('pointerdown', e => {
  if (e.target === splitSlider || splitSlider.contains(e.target)) return;
  if (state.stageZoom <= 1.01) return;
  isPanningStage = true;
  panStartClientX = e.clientX - state.stagePanX;
  panStartClientY = e.clientY - state.stagePanY;
  canvasWrapper.setPointerCapture(e.pointerId);
});

canvasWrapper.addEventListener('pointermove', e => {
  if (!isPanningStage) return;
  state.stagePanX = e.clientX - panStartClientX;
  state.stagePanY = e.clientY - panStartClientY;
  applyStageTransform();
});

canvasWrapper.addEventListener('pointerup', () => { isPanningStage = false; });
canvasWrapper.addEventListener('dblclick', () => {
  state.stageZoom = state.stageZoom > 1.05 ? 1.0 : 2.0;
  state.stagePanX = 0;
  state.stagePanY = 0;
  applyStageTransform();
});

zoomResetBadge?.addEventListener('click', () => {
  state.stageZoom = 1.0;
  state.stagePanX = 0;
  state.stagePanY = 0;
  applyStageTransform();
});

// =========================================================================
// KHỞI TẠO RIBBON PRESET & NẠP 392 CÔNG THỨC TỪ RECIPES.JSON
// =========================================================================
const presetRibbon = document.querySelector('#presetRibbon');
for (const preset of CURATED_PRESETS) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'preset-pill';
  btn.innerHTML = `<span class="p-name">${preset.name}</span><span class="p-sub">${preset.subtitle}</span>`;
  btn.addEventListener('click', () => {
    document.querySelectorAll('.preset-pill').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const currentDiag = state.settings.diagnosticMode;
    const currentDate = state.settings.dateStamp;
    state.settings = {
      ...structuredClone(defaults),
      ...structuredClone(preset.settings),
      diagnosticMode: currentDiag,
      dateStamp: currentDate
    };
    state.activeRecipeName = preset.name;
    state.curvePoints = structuredClone(preset.settings.curvePoints || [[0, 0], [1, 1]]);
    syncInputsFromSettings();
    drawCurveEditor();
    commitHistory(`Preset: ${preset.name}`);
    render();
  });
  presetRibbon.append(btn);
}

// Khởi tạo 30 mẫu Khung ảnh Triển lãm (PhotoFrame.java Parity)
for (const frame of PHOTO_FRAMES) {
  const opt = document.createElement('option');
  opt.value = String(frame.id);
  opt.textContent = `${frame.id === 0 ? '🚫 ' : '🖼️ #' + frame.id + ' · '}${frame.name}`;
  frameStyleSelect.append(opt);
}

frameStyleSelect.addEventListener('change', e => {
  state.frameStyle = Number(e.target.value) || 0;
  frameExifControls.hidden = state.frameStyle === 0;
  render();
});
frameTitleInput.addEventListener('input', () => render());
frameExifInput.addEventListener('input', () => render());

// Tải kho 392 công thức từ recipes.json
async function load392Catalog() {
  try {
    const res = await fetch('./recipes.json');
    if (!res.ok) return;
    const rawList = await res.json();
    state.fullCatalogRecipes = rawList.map(parseFujiRecipe);
    buildFilmFilterChips();
  } catch (_) {}
}
load392Catalog();

function applyCatalogRecipe(rec) {
  const keepDiag = state.settings.diagnosticMode;
  const keepDate = state.settings.dateStamp;
  state.settings = {
    ...structuredClone(defaults),
    ...structuredClone(rec.settings),
    diagnosticMode: keepDiag,
    dateStamp: keepDate
  };
  state.activeRecipeName = rec.name;
  state.curvePoints = structuredClone(rec.settings.curvePoints || [[0, 0], [1, 1]]);
  syncInputsFromSettings();
  drawCurveEditor();
  commitHistory(`Công thức: ${rec.name}`);
  render();
  setStatus(`🎞️ Đã áp dụng công thức "${rec.name}" (${rec.film} · DR${rec.dr} · WB ${rec.wb}).`);
}

function applySurpriseRandomRecipe() {
  if (!state.fullCatalogRecipes.length) {
    setStatus('Đang tải kho 392 công thức, vui lòng thử lại sau giây lát...');
    return;
  }
  const randomRec = state.fullCatalogRecipes[Math.floor(Math.random() * state.fullCatalogRecipes.length)];
  applyCatalogRecipe(randomRec);
}

document.querySelector('#surpriseRecipeBtn')?.addEventListener('click', applySurpriseRandomRecipe);
document.querySelector('#inlineSurpriseBtn')?.addEventListener('click', applySurpriseRandomRecipe);
document.querySelector('#modalRandomRecipeBtn')?.addEventListener('click', () => {
  applySurpriseRandomRecipe();
  renderRecipeLibraryGrid();
});

function buildFilmFilterChips() {
  const films = [
    'Tất cả', '★ Yêu thích', 'Classic Chrome', 'Classic Neg', 'Velvia',
    'Provia', 'Astia', 'Nostalgic Neg', 'Eterna', 'Acros', 'Monochrome', 'Pro Neg'
  ];
  recipeFilmFilterChips.innerHTML = '';
  for (const f of films) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'film-chip' + (f === state.selectedFilmFilter ? ' active' : '');
    btn.textContent = f === 'Tất cả' ? `Tất cả (${state.fullCatalogRecipes.length})` : f;
    btn.addEventListener('click', () => {
      state.selectedFilmFilter = f;
      recipeFilmFilterChips.querySelectorAll('.film-chip').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderRecipeLibraryGrid();
    });
    recipeFilmFilterChips.append(btn);
  }
}

function renderRecipeLibraryGrid() {
  const q = (recipeSearchInput.value || '').trim().toLowerCase();
  const filmFilter = state.selectedFilmFilter;
  const filtered = state.fullCatalogRecipes.filter(r => {
    const matchFav = filmFilter === '★ Yêu thích' ? state.favoriteRecipeIds.has(r.id) : true;
    const matchFilm = (filmFilter === 'Tất cả' || filmFilter === '★ Yêu thích') ||
      r.film.toLowerCase().includes(filmFilter.toLowerCase()) ||
      r.settings.film.toLowerCase().includes(filmFilter.toLowerCase());
    const matchQuery = !q ||
      r.name.toLowerCase().includes(q) ||
      r.film.toLowerCase().includes(q) ||
      r.wb.toLowerCase().includes(q);
    return matchFav && matchFilm && matchQuery;
  });

  recipeLibraryGrid.innerHTML = '';
  for (const rec of filtered) {
    const isFav = state.favoriteRecipeIds.has(rec.id);
    const isCurrent = state.activeRecipeName === rec.name;
    const card = document.createElement('div');
    card.className = 'recipe-lib-card' + (isCurrent ? ' active' : '');
    card.innerHTML = `
      <div class="r-head">
        <span class="r-title">${rec.name}</span>
        <button type="button" class="fav-star-btn ${isFav ? 'is-fav' : ''}" title="Đánh dấu Yêu thích">${isFav ? '★' : '☆'}</button>
      </div>
      <span class="r-film">🎞️ ${rec.film}</span>
      <div class="r-meta">
        <span>DR${rec.dr}</span>
        <span>H:${rec.settings.highlight > 0 ? '+' : ''}${rec.settings.highlight} S:${rec.settings.shadow > 0 ? '+' : ''}${rec.settings.shadow}</span>
        <span>WB: ${rec.wb}</span>
      </div>
    `;
    const starBtn = card.querySelector('.fav-star-btn');
    starBtn.addEventListener('click', e => {
      e.stopPropagation();
      if (state.favoriteRecipeIds.has(rec.id)) {
        state.favoriteRecipeIds.delete(rec.id);
      } else {
        state.favoriteRecipeIds.add(rec.id);
      }
      saveFavorites();
      renderRecipeLibraryGrid();
    });
    card.addEventListener('click', () => {
      applyCatalogRecipe(rec);
      recipeLibraryModal.hidden = true;
    });
    recipeLibraryGrid.append(card);
  }
}

function openRecipeLibraryModal() {
  recipeLibraryModal.hidden = false;
  renderRecipeLibraryGrid();
  recipeSearchInput.focus();
}

document.querySelector('#recipeLibraryBtn').addEventListener('click', openRecipeLibraryModal);
document.querySelector('#activeRecipeQuickPill')?.addEventListener('click', openRecipeLibraryModal);
document.querySelector('#openFullCatalogInline').addEventListener('click', openRecipeLibraryModal);
document.querySelector('#closeRecipeLibrary').addEventListener('click', () => { recipeLibraryModal.hidden = true; });
recipeSearchInput.addEventListener('input', () => renderRecipeLibraryGrid());

// =========================================================================
// COMMAND PALETTE (TÌM NHANH CÔNG THỨC & LỆNH STUDIO — CTRL+K)
// =========================================================================
function renderCmdPaletteResults() {
  if (!cmdQuickResults) return;
  const q = (cmdSearchInput.value || '').trim().toLowerCase();
  const list = state.fullCatalogRecipes
    .filter(r => !q || r.name.toLowerCase().includes(q) || r.film.toLowerCase().includes(q))
    .slice(0, 12);

  cmdQuickResults.innerHTML = '';
  for (const rec of list) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'preset-pill';
    btn.innerHTML = `<span class="p-name">🎞️ ${rec.name}</span><span class="p-sub">${rec.film} · DR${rec.dr}</span>`;
    btn.addEventListener('click', () => {
      applyCatalogRecipe(rec);
      cmdPaletteModal.hidden = true;
    });
    cmdQuickResults.append(btn);
  }
}

function openCmdPalette() {
  if (!cmdPaletteModal) return;
  cmdPaletteModal.hidden = false;
  renderCmdPaletteResults();
  cmdSearchInput.focus();
}

document.querySelector('#quickCmdBtn')?.addEventListener('click', openCmdPalette);
document.querySelector('#closeCmdPaletteBtn')?.addEventListener('click', () => { cmdPaletteModal.hidden = true; });
cmdSearchInput?.addEventListener('input', renderCmdPaletteResults);

// =========================================================================
// BUỒNG TỐI CHUYÊN NGHIỆP LIGHTCRAFT (3-WAY COLOR WHEELS & 8-BAND HSL)
// =========================================================================
const HSL_BAND_NAMES = [
  ['Đỏ (Red)', '#ef4444'],
  ['Cam (Orange)', '#f97316'],
  ['Vàng (Yellow)', '#eab308'],
  ['Lục (Green)', '#22c55e'],
  ['Lam ngọc (Aqua)', '#06b6d4'],
  ['Xanh dương (Blue)', '#3b82f6'],
  ['Tím (Purple)', '#a855f7'],
  ['Hồng (Magenta)', '#ec4899']
];

function initLightCraftModal() {
  lightCraftPresetsGrid.innerHTML = '';
  for (const p of LIGHTCRAFT_PRESETS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'lc-preset-btn';
    btn.innerHTML = `<strong>${p.name}</strong><small>${p.sub}</small>`;
    btn.addEventListener('click', () => {
      state.settings.colorWheels = structuredClone(defaults.colorWheels);
      state.settings.hslHue = [...defaults.hslHue];
      state.settings.hslSat = [...defaults.hslSat];
      state.settings.hslLum = [...defaults.hslLum];
      if (p.curve) {
        state.curvePoints = structuredClone(p.curve);
        state.settings.curveLut = buildMonotoneCurveLut(state.curvePoints);
      }
      if (p.wheels) {
        for (const k of ['shadows', 'midtones', 'highlights']) {
          if (p.wheels[k]) Object.assign(state.settings.colorWheels[k], p.wheels[k]);
        }
      }
      if (p.hslSat) state.settings.hslSat = [...p.hslSat];
      if (p.hslLum) state.settings.hslLum = [...p.hslLum];
      if (p.clarity !== undefined) state.settings.clarity = p.clarity;
      if (p.vignette !== undefined) state.settings.vignette = p.vignette;
      syncInputsFromSettings();
      drawCurveEditor();
      drawAllColorWheels();
      renderHslSliders();
      commitHistory(`LightCraft: ${p.name}`);
      render();
      setStatus(`🎛️ Đã áp dụng Preset phòng tối LightCraft: "${p.name}".`);
    });
    lightCraftPresetsGrid.append(btn);
  }

  document.querySelectorAll('.hsl-mode-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.hsl-mode-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeHslMode = btn.dataset.hslMode;
      renderHslSliders();
    });
  });

  ['wheelShadows', 'wheelMidtones', 'wheelHighlights'].forEach(id => {
    const wCanvas = document.getElementById(id);
    const wheelKey = wCanvas.dataset.wheel;
    let dragging = false;
    const updateFromMouse = e => {
      const rect = wCanvas.getBoundingClientRect();
      const cx = rect.width / 2;
      const cy = rect.height / 2;
      const dx = e.clientX - rect.left - cx;
      const dy = e.clientY - rect.top - cy;
      const radius = cx - 6;
      const dist = Math.min(1, Math.hypot(dx, dy) / radius);
      let angle = Math.atan2(dy, dx) * (180 / Math.PI);
      if (angle < 0) angle += 360;
      state.settings.colorWheels[wheelKey].hue = Math.round(angle);
      state.settings.colorWheels[wheelKey].sat = Math.round(dist * 100);
      drawColorWheel(wCanvas, state.settings.colorWheels[wheelKey]);
      render();
    };
    wCanvas.addEventListener('pointerdown', e => { dragging = true; wCanvas.setPointerCapture(e.pointerId); updateFromMouse(e); });
    wCanvas.addEventListener('pointermove', e => { if (dragging) updateFromMouse(e); });
    wCanvas.addEventListener('pointerup', () => { dragging = false; commitHistory(`Bàn xoay màu ${wheelKey}`); });
    wCanvas.addEventListener('dblclick', () => {
      state.settings.colorWheels[wheelKey] = { hue: 0, sat: 0, lum: 0 };
      drawColorWheel(wCanvas, state.settings.colorWheels[wheelKey]);
      render();
    });
  });

  [['lumShadows', 'shadows'], ['lumMidtones', 'midtones'], ['lumHighlights', 'highlights']].forEach(([inputId, key]) => {
    const el = document.getElementById(inputId);
    el.addEventListener('input', () => {
      state.settings.colorWheels[key].lum = Number(el.value);
      render();
    });
  });

  renderHslSliders();
  drawAllColorWheels();
}

function drawColorWheel(wCanvas, wheelState) {
  const c = wCanvas.getContext('2d');
  const w = wCanvas.width, h = wCanvas.height;
  const cx = w / 2, cy = h / 2, r = cx - 6;
  c.clearRect(0, 0, w, h);

  for (let angle = 0; angle < 360; angle += 2) {
    const startAngle = (angle - 1.5) * Math.PI / 180;
    const endAngle = (angle + 1.5) * Math.PI / 180;
    const grad = c.createRadialGradient(cx, cy, 0, cx, cy, r);
    grad.addColorStop(0, '#2a2e39');
    grad.addColorStop(1, `hsl(${angle}, 85%, 52%)`);
    c.beginPath();
    c.moveTo(cx, cy);
    c.arc(cx, cy, r, startAngle, endAngle);
    c.closePath();
    c.fillStyle = grad;
    c.fill();
  }

  c.strokeStyle = 'rgba(255,255,255,0.22)';
  c.lineWidth = 1.5;
  c.beginPath();
  c.arc(cx, cy, r, 0, Math.PI * 2);
  c.stroke();

  const rad = (wheelState.hue || 0) * Math.PI / 180;
  const dist = ((wheelState.sat || 0) / 100) * r;
  const px = cx + Math.cos(rad) * dist;
  const py = cy + Math.sin(rad) * dist;

  c.fillStyle = '#fff';
  c.strokeStyle = '#000';
  c.lineWidth = 2;
  c.beginPath();
  c.arc(px, py, 5.5, 0, Math.PI * 2);
  c.fill();
  c.stroke();
}

function drawAllColorWheels() {
  if (!state.settings.colorWheels) state.settings.colorWheels = structuredClone(defaults.colorWheels);
  drawColorWheel(document.getElementById('wheelShadows'), state.settings.colorWheels.shadows);
  drawColorWheel(document.getElementById('wheelMidtones'), state.settings.colorWheels.midtones);
  drawColorWheel(document.getElementById('wheelHighlights'), state.settings.colorWheels.highlights);
  document.getElementById('lumShadows').value = state.settings.colorWheels.shadows.lum || 0;
  document.getElementById('lumMidtones').value = state.settings.colorWheels.midtones.lum || 0;
  document.getElementById('lumHighlights').value = state.settings.colorWheels.highlights.lum || 0;
}

function renderHslSliders() {
  hslSlidersContainer.innerHTML = '';
  const prop = state.activeHslMode === 'hue' ? 'hslHue' : (state.activeHslMode === 'sat' ? 'hslSat' : 'hslLum');
  if (!state.settings[prop]) state.settings[prop] = [0, 0, 0, 0, 0, 0, 0, 0];

  HSL_BAND_NAMES.forEach(([label, hex], idx) => {
    const val = state.settings[prop][idx] || 0;
    const row = document.createElement('div');
    row.className = 'hsl-row';
    row.innerHTML = `
      <span class="hsl-band-label"><i class="hsl-dot" style="background:${hex}"></i>${label.split(' ')[0]}</span>
      <input type="range" min="-100" max="100" step="1" value="${val}" />
      <output style="font-family:monospace;font-size:11px;text-align:right;color:var(--accent)">${val > 0 ? '+' + val : val}</output>
    `;
    const input = row.querySelector('input');
    const out = row.querySelector('output');
    input.addEventListener('input', () => {
      const v = Number(input.value);
      state.settings[prop][idx] = v;
      out.textContent = v > 0 ? '+' + v : String(v);
      render();
    });
    input.addEventListener('dblclick', () => {
      state.settings[prop][idx] = 0;
      input.value = 0;
      out.textContent = '0';
      render();
    });
    hslSlidersContainer.append(row);
  });
}

initLightCraftModal();

document.querySelector('#lightCraftBtn').addEventListener('click', () => {
  lightCraftModal.hidden = !lightCraftModal.hidden;
  if (!lightCraftModal.hidden) {
    drawAllColorWheels();
    renderHslSliders();
  }
});
document.querySelector('#closeLightCraft').addEventListener('click', () => { lightCraftModal.hidden = true; });
document.querySelector('#resetLightCraftBtn').addEventListener('click', () => {
  state.settings.colorWheels = structuredClone(defaults.colorWheels);
  state.settings.hslHue = [0, 0, 0, 0, 0, 0, 0, 0];
  state.settings.hslSat = [0, 0, 0, 0, 0, 0, 0, 0];
  state.settings.hslLum = [0, 0, 0, 0, 0, 0, 0, 0];
  drawAllColorWheels();
  renderHslSliders();
  render();
  setStatus('Đã đặt lại toàn bộ thông số Buồng tối LightCraft về mặc định.');
});

// =========================================================================
// UNDO / REDO HISTORY STACK
// =========================================================================
function commitHistory(actionName = 'Chỉnh sửa') {
  if (!state.source) return;
  const snapshot = {
    settings: structuredClone(state.settings),
    curvePoints: structuredClone(state.curvePoints),
    activeRecipeName: state.activeRecipeName,
    actionName
  };
  state.history = state.history.slice(0, state.historyIndex + 1);
  state.history.push(snapshot);
  if (state.history.length > 30) state.history.shift();
  state.historyIndex = state.history.length - 1;
  updateHistoryButtons();
}

function updateHistoryButtons() {
  document.querySelector('#undoBtn').disabled = state.historyIndex <= 0;
  document.querySelector('#redoBtn').disabled = state.historyIndex >= state.history.length - 1;
}

document.querySelector('#undoBtn').addEventListener('click', () => {
  if (state.historyIndex > 0) {
    state.historyIndex--;
    const snap = state.history[state.historyIndex];
    state.settings = structuredClone(snap.settings);
    state.curvePoints = structuredClone(snap.curvePoints);
    state.activeRecipeName = snap.activeRecipeName || 'Tùy chỉnh';
    syncInputsFromSettings();
    drawCurveEditor();
    updateHistoryButtons();
    render();
    setStatus(`↶ Đã hoàn tác: ${snap.actionName}`);
  }
});

document.querySelector('#redoBtn').addEventListener('click', () => {
  if (state.historyIndex < state.history.length - 1) {
    state.historyIndex++;
    const snap = state.history[state.historyIndex];
    state.settings = structuredClone(snap.settings);
    state.curvePoints = structuredClone(snap.curvePoints);
    state.activeRecipeName = snap.activeRecipeName || 'Tùy chỉnh';
    syncInputsFromSettings();
    drawCurveEditor();
    updateHistoryButtons();
    render();
    setStatus(`↷ Đã làm lại: ${snap.actionName}`);
  }
});

// =========================================================================
// ĐỒNG BỘ THANH TRƯỢT & SELECT
// =========================================================================
function syncInputsFromSettings() {
  for (const [key] of allSliderSpecs) {
    const input = document.querySelector(`#in-${key}`);
    const out = document.querySelector(`#out-${key}`);
    if (input && out) {
      input.value = state.settings[key] ?? defaults[key];
      out.textContent = formatParamReadout(key, state.settings[key] ?? defaults[key]);
    }
  }
  document.querySelector('#film').value = state.settings.film;
  document.querySelector('#dr').value = String(state.settings.dr);
  document.querySelector('#grainSize').value = String(state.settings.grainSize);
  document.querySelector('#chromatic').value = String(state.settings.chromatic || 0);
  document.querySelector('#darkroomProcess').value = String(state.settings.darkroomProcess || 'None');
  document.querySelector('#dateStampBtn').classList.toggle('active', Boolean(state.settings.dateStamp));
  document.querySelectorAll('.diag-pill').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.diag === (state.settings.diagnosticMode || 'Off'));
  });
  updateTopRecipePill();
  syncRulerHeader();
  drawTickRuler();
}

for (const el of document.querySelectorAll('[data-key]')) {
  el.addEventListener('input', e => {
    const key = e.target.dataset.key;
    state.settings[key] = Number(e.target.value);
    document.querySelector(`#out-${key}`).textContent = formatParamReadout(key, state.settings[key]);
    const matchedSpec = allSliderSpecs.find(s => s[0] === key);
    if (matchedSpec) activeRulerSpec = matchedSpec;
    syncRulerHeader();
    drawTickRuler();
    render();
  });
  el.addEventListener('change', e => {
    commitHistory(`Chỉnh ${e.target.dataset.key}`);
  });
}

document.querySelector('#film').addEventListener('change', e => {
  state.settings.film = e.target.value;
  state.activeRecipeName = 'Tùy chỉnh';
  updateTopRecipePill();
  commitHistory(`Đổi Film: ${e.target.value}`);
  render();
});

document.querySelector('#dr').addEventListener('change', e => {
  state.settings.dr = Number(e.target.value);
  commitHistory(`Đổi DR${e.target.value}`);
  render();
});

document.querySelector('#grainSize').addEventListener('change', e => {
  state.settings.grainSize = Number(e.target.value);
  commitHistory('Đổi kích thước hạt');
  render();
});

document.querySelector('#chromatic').addEventListener('change', e => {
  state.settings.chromatic = Number(e.target.value);
  commitHistory('Chỉnh viền tím quang học');
  render();
});

document.querySelector('#darkroomProcess').addEventListener('change', e => {
  state.settings.darkroomProcess = e.target.value;
  commitHistory(`In Phòng tối: ${e.target.value}`);
  render();
});

document.querySelectorAll('.diag-pill').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.diag-pill').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    state.settings.diagnosticMode = btn.dataset.diag;
    render();
  });
});

document.querySelector('#dateStampBtn').addEventListener('click', () => {
  state.settings.dateStamp = !state.settings.dateStamp;
  document.querySelector('#dateStampBtn').classList.toggle('active', state.settings.dateStamp);
  commitHistory(state.settings.dateStamp ? 'Bật dấu ngày tháng LED' : 'Tắt dấu ngày tháng LED');
  render();
});

// =========================================================================
// TƯƠNG TÁC ĐƯỜNG CONG TONE CURVE SPLINE
// =========================================================================
function drawCurveEditor() {
  const w = curveCanvas.width, h = curveCanvas.height;
  curveCtx.clearRect(0, 0, w, h);

  curveCtx.strokeStyle = state.porcelainTheme ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)';
  curveCtx.lineWidth = 1;
  for (let i = 1; i <= 3; i++) {
    curveCtx.beginPath();
    curveCtx.moveTo((w / 4) * i, 0); curveCtx.lineTo((w / 4) * i, h);
    curveCtx.moveTo(0, (h / 4) * i); curveCtx.lineTo(w, (h / 4) * i);
    curveCtx.stroke();
  }

  curveCtx.strokeStyle = state.porcelainTheme ? 'rgba(0,0,0,0.18)' : 'rgba(255,255,255,0.18)';
  curveCtx.setLineDash([4, 4]);
  curveCtx.beginPath();
  curveCtx.moveTo(0, h); curveCtx.lineTo(w, 0);
  curveCtx.stroke();
  curveCtx.setLineDash([]);

  const lut = buildMonotoneCurveLut(state.curvePoints);
  state.settings.curveLut = lut;

  curveCtx.strokeStyle = '#f09a3e';
  curveCtx.lineWidth = 2.4;
  curveCtx.beginPath();
  for (let x = 0; x < 256; x++) {
    const px = (x / 255) * w;
    const py = h - lut[x] * h;
    if (x === 0) curveCtx.moveTo(px, py);
    else curveCtx.lineTo(px, py);
  }
  curveCtx.stroke();

  for (const [nx, ny] of state.curvePoints) {
    const px = nx * w, py = (1 - ny) * h;
    curveCtx.fillStyle = '#161920';
    curveCtx.strokeStyle = '#f09a3e';
    curveCtx.lineWidth = 2.2;
    curveCtx.beginPath();
    curveCtx.arc(px, py, 5, 0, Math.PI * 2);
    curveCtx.fill();
    curveCtx.stroke();
  }
}

curveCanvas.addEventListener('pointerdown', e => {
  const rect = curveCanvas.getBoundingClientRect();
  const nx = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  const ny = Math.max(0, Math.min(1, 1 - (e.clientY - rect.top) / rect.height));
  const hitIdx = state.curvePoints.findIndex(([px, py]) => Math.hypot(px - nx, py - ny) < 0.08);
  if (hitIdx >= 0) {
    state.draggingCurveIdx = hitIdx;
  } else if (state.curvePoints.length < 7) {
    state.curvePoints.push([nx, ny]);
    state.curvePoints.sort((a, b) => a[0] - b[0]);
    state.draggingCurveIdx = state.curvePoints.findIndex(([px, py]) => px === nx && py === ny);
  }
  curveCanvas.setPointerCapture(e.pointerId);
  drawCurveEditor();
  render();
});

curveCanvas.addEventListener('pointermove', e => {
  if (state.draggingCurveIdx < 0) return;
  const rect = curveCanvas.getBoundingClientRect();
  let nx = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  const ny = Math.max(0, Math.min(1, 1 - (e.clientY - rect.top) / rect.height));
  const idx = state.draggingCurveIdx;
  if (idx === 0) nx = 0;
  else if (idx === state.curvePoints.length - 1) nx = 1;
  else {
    nx = Math.max(state.curvePoints[idx - 1][0] + 0.03, Math.min(state.curvePoints[idx + 1][0] - 0.03, nx));
  }
  state.curvePoints[idx] = [nx, ny];
  drawCurveEditor();
  render();
});

curveCanvas.addEventListener('pointerup', () => {
  if (state.draggingCurveIdx >= 0) {
    state.draggingCurveIdx = -1;
    commitHistory('Chỉnh đường cong Tone Curve');
  }
});

curveCanvas.addEventListener('dblclick', () => {
  state.curvePoints = [[0, 0], [1, 1]];
  drawCurveEditor();
  commitHistory('Đặt lại Tone Curve');
  render();
});

document.querySelector('#resetCurve').addEventListener('click', () => {
  state.curvePoints = [[0, 0], [1, 1]];
  drawCurveEditor();
  commitHistory('Đặt lại Tone Curve');
  render();
});

document.querySelectorAll('[data-curve]').forEach(btn => {
  btn.addEventListener('click', () => {
    const type = btn.dataset.curve;
    if (type === 'linear') state.curvePoints = [[0, 0], [1, 1]];
    else if (type === 'scurve') state.curvePoints = [[0, 0], [0.25, 0.18], [0.75, 0.83], [1, 1]];
    else if (type === 'matte') state.curvePoints = [[0, 0.09], [0.25, 0.22], [0.75, 0.78], [1, 0.94]];
    else if (type === 'contrast') state.curvePoints = [[0, 0], [0.2, 0.12], [0.8, 0.89], [1, 1]];
    drawCurveEditor();
    commitHistory(`Tone Curve: ${btn.textContent}`);
    render();
  });
});

// =========================================================================
// BIỂU ĐỒ RGB HISTOGRAM & BẢNG MÀU CHỦ ĐẠO 5 Ô
// =========================================================================
function drawHistogram(pixels) {
  const { rHist, gHist, bHist, maxBin, evShift, highlightClipPct } = computeHistogramAndStats(pixels);
  const w = histCanvas.width, h = histCanvas.height;
  histCtx.clearRect(0, 0, w, h);

  const drawChannel = (hist, color) => {
    histCtx.fillStyle = color;
    histCtx.beginPath();
    histCtx.moveTo(0, h);
    for (let i = 0; i < 64; i++) {
      const x = (i / 63) * w;
      const y = h - Math.pow(hist[i] / maxBin, 0.65) * (h - 8);
      histCtx.lineTo(x, y);
    }
    histCtx.lineTo(w, h);
    histCtx.closePath();
    histCtx.fill();
  };

  histCtx.globalCompositeOperation = 'screen';
  drawChannel(rHist, 'rgba(239, 68, 68, 0.55)');
  drawChannel(gHist, 'rgba(34, 197, 94, 0.55)');
  drawChannel(bHist, 'rgba(59, 130, 246, 0.65)');
  histCtx.globalCompositeOperation = 'source-over';

  document.querySelector('#statEv').textContent = `${evShift >= 0 ? '+' : ''}${evShift.toFixed(1)} EV`;
  document.querySelector('#statDr').textContent = `DR${state.settings.dr}`;
  const kelvin = Math.round(5500 + state.settings.temperature * 25);
  document.querySelector('#statWb').textContent = `${kelvin}K`;
  document.querySelector('#statClip').textContent = `${highlightClipPct.toFixed(1)}%`;

  const palette = extractDominantPalette(pixels, 5);
  dominantPaletteBar.innerHTML = '';
  for (const sw of palette) {
    const div = document.createElement('div');
    div.className = 'palette-swatch';
    div.style.background = sw.hex;
    div.textContent = sw.hex.toUpperCase();
    div.title = `Nhấn để sao chép mã màu ${sw.hex.toUpperCase()} (${sw.pct}%)`;
    div.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(sw.hex.toUpperCase());
        setStatus(`🎨 Đã sao chép mã màu ${sw.hex.toUpperCase()} vào bộ nhớ tạm.`);
      } catch (_) {}
    });
    dominantPaletteBar.append(div);
  }
}

// =========================================================================
// CÁC TÍNH NĂNG ĐỘT PHÁ IRO STUDIO 3.0
// =========================================================================

// 1. AI Auto-Tone & Scene Co-Pilot
document.querySelector('#aiAuto').addEventListener('click', () => {
  if (!state.source) return;
  const { settings: nextSettings, reasonVi } = autoBalanceSettings(state.source.data, state.settings);
  state.settings = nextSettings;
  state.curvePoints = structuredClone(nextSettings.curvePoints || [[0, 0], [1, 1]]);
  syncInputsFromSettings();
  drawCurveEditor();
  commitHistory('AI Auto-Tone');
  render();
  setStatus(reasonVi);
});

// 2. Xoay ảnh 90°
document.querySelector('#rotate90').addEventListener('click', () => {
  if (!state.source) return;
  const rotated = rotatePixels90(state.source.data, state.source.width, state.source.height);
  state.source = new ImageData(rotated.pixels, rotated.width, rotated.height);
  commitHistory('Xoay ảnh 90°');
  render();
  setStatus(`↻ Đã xoay ảnh 90° (${state.source.width}×${state.source.height}px).`);
});

// 3. Thanh gạt chia đôi Trước / Sau (Split-Screen Comparison)
const compareBtn = document.querySelector('#compareBtn');
compareBtn.addEventListener('click', () => {
  if (!state.source) return;
  state.comparingSplit = !state.comparingSplit;
  compareBtn.classList.toggle('active', state.comparingSplit);
  beforeCanvas.hidden = !state.comparingSplit;
  splitSlider.hidden = !state.comparingSplit;
  updateSplitClip();
});

function updateSplitClip() {
  const pct = (state.splitRatio * 100).toFixed(1);
  beforeCanvas.style.clipPath = `inset(0 ${100 - state.splitRatio * 100}% 0 0)`;
  splitSlider.style.left = `${pct}%`;
}

let draggingSplit = false;
splitSlider.addEventListener('pointerdown', e => {
  draggingSplit = true;
  splitSlider.setPointerCapture(e.pointerId);
});
splitSlider.addEventListener('pointermove', e => {
  if (!draggingSplit) return;
  const rect = canvasWrapper.getBoundingClientRect();
  state.splitRatio = Math.max(0.05, Math.min(0.95, (e.clientX - rect.left) / rect.width));
  updateSplitClip();
});
splitSlider.addEventListener('pointerup', () => { draggingSplit = false; });

// 4. Lưới 4 phương án màu cùng lúc (Quad-View Audition)
const quadViewBtn = document.querySelector('#quadViewBtn');
quadViewBtn.addEventListener('click', () => {
  if (!state.source) return;
  state.quadViewActive = !state.quadViewActive;
  quadViewBtn.classList.toggle('active', state.quadViewActive);
  if (state.quadViewActive) {
    canvasWrapper.hidden = true;
    quadViewGrid.hidden = false;
    renderQuadView();
    setStatus('⊞ Chế độ So sánh 4 bản màu (Quad-View) — Nhấn vào bất kỳ phương án nào để chọn làm bản chính.');
  } else {
    quadViewGrid.hidden = true;
    canvasWrapper.hidden = false;
    render();
  }
});

function renderQuadView() {
  if (!state.source || !state.quadViewActive) return;
  quadViewGrid.innerHTML = '';

  const variants = [
    { title: '1. Ảnh gốc (Original)', sub: 'Chưa qua xử lý', settings: { ...defaults, film: 'Original' } },
    { title: `2. Đang chọn (${state.settings.film})`, sub: state.activeRecipeName, settings: structuredClone(state.settings) },
    {
      title: '3. Kodachrome 64 + Pro-Mist',
      sub: 'Classic Chrome ấm cổ điển',
      settings: { ...structuredClone(state.settings), film: 'Classic Chrome', temperature: 16, proMist: 28, colorChrome: 2 }
    },
    {
      title: '4. CineStill 800T + Halation',
      sub: 'Điện ảnh đêm lạnh & quầng đỏ',
      settings: { ...structuredClone(state.settings), film: 'ETERNA', temperature: -22, halation: 45, proMist: 32, bleachBypass: 18 }
    }
  ];

  const scale = Math.min(1, 360 / Math.max(state.source.width, state.source.height));
  const tw = Math.max(1, Math.round(state.source.width * scale));
  const th = Math.max(1, Math.round(state.source.height * scale));
  const tmpSrc = document.createElement('canvas');
  tmpSrc.width = tw; tmpSrc.height = th;
  const tmpCtx = tmpSrc.getContext('2d');
  const fullTmp = document.createElement('canvas');
  fullTmp.width = state.source.width; fullTmp.height = state.source.height;
  fullTmp.getContext('2d').putImageData(state.source, 0, 0);
  tmpCtx.drawImage(fullTmp, 0, 0, tw, th);
  const smallData = tmpCtx.getImageData(0, 0, tw, th);

  for (const v of variants) {
    const card = document.createElement('div');
    card.className = 'quad-card';
    const c = document.createElement('canvas');
    c.width = tw; c.height = th;
    const outPixels = processPixels(smallData.data, tw, th, v.settings, state.lut);
    c.getContext('2d').putImageData(new ImageData(outPixels, tw, th), 0, 0);
    const footer = document.createElement('div');
    footer.className = 'quad-card-footer';
    footer.innerHTML = `<b>${v.title}</b><span>${v.sub}</span>`;
    card.append(c, footer);
    card.addEventListener('click', () => {
      state.settings = structuredClone(v.settings);
      state.quadViewActive = false;
      quadViewBtn.classList.remove('active');
      quadViewGrid.hidden = true;
      canvasWrapper.hidden = false;
      syncInputsFromSettings();
      commitHistory(`Chọn từ Quad-View: ${v.title}`);
      render();
    });
    quadViewGrid.append(card);
  }
}

// 5. Bảng 12 biến thể màu Film (Contact Sheet)
document.querySelector('#contactSheetBtn').addEventListener('click', () => {
  if (!state.source) return;
  contactSheetModal.hidden = false;
  contactSheetGrid.innerHTML = '';

  const scale = Math.min(1, 220 / Math.max(state.source.width, state.source.height));
  const tw = Math.max(1, Math.round(state.source.width * scale));
  const th = Math.max(1, Math.round(state.source.height * scale));

  const tmpCanvas = document.createElement('canvas');
  tmpCanvas.width = tw; tmpCanvas.height = th;
  const tmpCtx = tmpCanvas.getContext('2d');
  const srcCanvas = document.createElement('canvas');
  srcCanvas.width = state.source.width; srcCanvas.height = state.source.height;
  srcCanvas.getContext('2d').putImageData(state.source, 0, 0);
  tmpCtx.drawImage(srcCanvas, 0, 0, tw, th);
  const thumbData = tmpCtx.getImageData(0, 0, tw, th);

  for (const preset of CURATED_PRESETS) {
    const item = document.createElement('div');
    item.className = 'sheet-item';
    const c = document.createElement('canvas');
    c.width = tw; c.height = th;
    const pSettings = { ...structuredClone(defaults), ...structuredClone(preset.settings) };
    pSettings.curveLut = buildMonotoneCurveLut(preset.settings.curvePoints || [[0, 0], [1, 1]]);
    const out = processPixels(thumbData.data, tw, th, pSettings, null);
    c.getContext('2d').putImageData(new ImageData(out, tw, th), 0, 0);
    const title = document.createElement('div');
    title.className = 'sheet-item-title';
    title.textContent = `${preset.name} · ${preset.subtitle}`;
    item.append(c, title);
    item.addEventListener('click', () => {
      state.settings = pSettings;
      state.activeRecipeName = preset.name;
      state.curvePoints = structuredClone(preset.settings.curvePoints || [[0, 0], [1, 1]]);
      syncInputsFromSettings();
      drawCurveEditor();
      contactSheetModal.hidden = true;
      commitHistory(`Contact Sheet: ${preset.name}`);
      render();
      setStatus(`Đã chọn công thức "${preset.name}" từ Bảng 12 biến thể Film.`);
    });
    contactSheetGrid.append(item);
  }
});

document.querySelector('#closeContactSheet').addEventListener('click', () => {
  contactSheetModal.hidden = true;
});

// 6. Hạt phim Sống 24 hình/giây (24fps Living Film Projector)
const livingFilmBtn = document.querySelector('#livingFilmBtn');
livingFilmBtn.addEventListener('click', () => {
  if (!state.source) return;
  state.livingFilmActive = !state.livingFilmActive;
  livingFilmBtn.classList.toggle('active', state.livingFilmActive);
  if (state.livingFilmActive) {
    if (state.settings.grain < 6) {
      state.settings.grain = 12;
      syncInputsFromSettings();
    }
    state.livingFilmTimer = setInterval(() => {
      if (!state.source || !state.livingFilmActive) return;
      state.livingFilmFrame = (state.livingFilmFrame + 1) % 240;
      render(true);
    }, 1000 / 18);
    setStatus('🎞️ Đang chạy máy chiếu Hạt phim Sống 24fps (Living Film) — Hạt bạc 35mm chuyển động hữu cơ theo thời gian thực.');
  } else {
    clearInterval(state.livingFilmTimer);
    state.livingFilmTimer = null;
    state.livingFilmFrame = 0;
    render();
    setStatus('Đã dừng chế độ máy chiếu 24fps Living Film.');
  }
});

// 7. AI Tả Cảnh Pha Màu Tiếng Việt (Prompt-to-Recipe NLP)
document.querySelector('#promptRecipeBtn').addEventListener('click', () => {
  promptModal.hidden = false;
  promptTextInput.focus();
});

document.querySelector('#closePromptModal').addEventListener('click', () => {
  promptModal.hidden = true;
});

function runPromptRecipe(text) {
  const { settings: nextSettings, reasonVi } = promptToRecipeVi(text);
  const keepDiag = state.settings.diagnosticMode;
  state.settings = { ...nextSettings, diagnosticMode: keepDiag };
  state.activeRecipeName = 'AI Tả cảnh';
  state.curvePoints = structuredClone(nextSettings.curvePoints || [[0, 0], [1, 1]]);
  syncInputsFromSettings();
  drawCurveEditor();
  promptModal.hidden = true;
  if (!state.source) generateSampleImage('street');
  else {
    commitHistory(`AI Tả cảnh: ${text.slice(0, 24)}`);
    render();
  }
  setStatus(reasonVi);
}

document.querySelector('#applyPromptBtn').addEventListener('click', () => {
  runPromptRecipe(promptTextInput.value);
});

promptTextInput.addEventListener('keydown', e => {
  if (e.key === 'Enter') runPromptRecipe(promptTextInput.value);
});

document.querySelectorAll('.prompt-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    promptTextInput.value = chip.dataset.prompt;
    runPromptRecipe(chip.dataset.prompt);
  });
});

// 8. Sao chép bảng màu từ Ảnh Mẫu (Reference Image Color Transfer)
const referenceInput = document.querySelector('#referenceInput');
document.querySelector('#matchRefBtn').addEventListener('click', () => {
  if (!state.source) return;
  referenceInput.click();
});

referenceInput.addEventListener('change', async e => {
  const [file] = e.target.files;
  if (!file || !state.source) return;
  try {
    const bmp = await createImageBitmap(file);
    const tmp = document.createElement('canvas');
    tmp.width = Math.min(400, bmp.width);
    tmp.height = Math.min(400, bmp.height);
    const tCtx = tmp.getContext('2d');
    tCtx.drawImage(bmp, 0, 0, tmp.width, tmp.height);
    const refData = tCtx.getImageData(0, 0, tmp.width, tmp.height);
    const { settings: matchedSettings, reasonVi } = matchColorFromReference(state.source.data, refData.data, state.settings);
    state.settings = matchedSettings;
    state.activeRecipeName = `Học mẫu: ${file.name.slice(0, 12)}`;
    syncInputsFromSettings();
    commitHistory(`Học màu từ ${file.name}`);
    render();
    setStatus(reasonVi);
  } catch (err) {
    setStatus('Không thể phân tích ảnh mẫu: ' + err.message, true);
  }
});

// 9. Xuất công thức thành tệp 3D LUT (.cube) chuẩn quốc tế
document.querySelector('#exportCube').addEventListener('click', () => {
  const title = `IRO_${state.settings.film.replace(/\s+/g, '_')}`;
  const cubeContent = bakeRecipeToCube(state.settings, 17, title);
  const blob = new Blob([cubeContent], { type: 'text/plain;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${title.toLowerCase()}.cube`;
  a.click();
  URL.revokeObjectURL(a.href);
  setStatus(`🧊 Đã kết xuất công thức hiện tại thành tệp 3D LUT "${title.toLowerCase()}.cube" (17×17×17).`);
});

// 10. Chia sẻ / Nhập mã công thức IRO1
document.querySelector('#shareCode').addEventListener('click', async () => {
  const currentCode = encodeRecipeCode({ ...state.settings, curvePoints: state.curvePoints });
  const input = window.prompt(
    '🔗 Mã công thức IRO của bạn (Sao chép mã này để chia sẻ, hoặc dán một mã IRO1-... khác vào đây để áp dụng):',
    currentCode
  );
  if (!input || input.trim() === currentCode) {
    if (navigator.clipboard && input) {
      try { await navigator.clipboard.writeText(currentCode); } catch (_) {}
      setStatus('📋 Đã sao chép mã công thức IRO1 vào bộ nhớ tạm!');
    }
    return;
  }
  const decoded = decodeRecipeCode(input);
  if (!decoded) {
    setStatus('Mã công thức IRO1 không hợp lệ.', true);
    return;
  }
  state.settings = { ...structuredClone(defaults), ...decoded };
  state.curvePoints = structuredClone(decoded.curvePoints || [[0, 0], [1, 1]]);
  syncInputsFromSettings();
  drawCurveEditor();
  commitHistory('Nhập mã công thức IRO1');
  render();
  setStatus('✨ Đã áp dụng thành công công thức từ mã chia sẻ IRO1!');
});

// Đặt lại toàn bộ thông số về gốc
document.querySelector('#resetAll').addEventListener('click', () => {
  state.settings = structuredClone(defaults);
  state.activeRecipeName = 'Gốc';
  state.curvePoints = [[0, 0], [1, 1]];
  state.frameStyle = 0;
  frameStyleSelect.value = '0';
  frameExifControls.hidden = true;
  document.querySelectorAll('.preset-pill').forEach(b => b.classList.remove('active'));
  syncInputsFromSettings();
  drawCurveEditor();
  commitHistory('Đặt lại gốc');
  render();
  setStatus('Đã đặt lại toàn bộ thông số về nguyên bản.');
});

// =========================================================================
// PHÍM TẮT STUDIO (SPACEBAR, R, Q, L, X, CTRL+K, CTRL+Z/Y, ESC)
// =========================================================================
window.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    openCmdPalette();
    return;
  }
  if (e.key === 'Escape') {
    recipeLibraryModal.hidden = true;
    lightCraftModal.hidden = true;
    contactSheetModal.hidden = true;
    promptModal.hidden = true;
    cmdPaletteModal.hidden = true;
    return;
  }
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') return;

  if (e.code === 'Space' && state.source && !e.repeat) {
    e.preventDefault();
    ctx.putImageData(state.source, 0, 0);
  } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
    e.preventDefault();
    document.querySelector('#undoBtn').click();
  } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
    e.preventDefault();
    document.querySelector('#redoBtn').click();
  } else if (e.key.toLowerCase() === 'r' && state.source) {
    document.querySelector('#rotate90').click();
  } else if (e.key.toLowerCase() === 'q' && state.source) {
    document.querySelector('#quadViewBtn').click();
  } else if (e.key.toLowerCase() === 'l') {
    openRecipeLibraryModal();
  } else if (e.key.toLowerCase() === 'x') {
    applySurpriseRandomRecipe();
  } else if (e.key === '1') {
    activateInspectorTab('tabFilm');
  } else if (e.key === '2') {
    activateInspectorTab('tabBasic');
  } else if (e.key === '3') {
    activateInspectorTab('tabOptical');
  }
});

window.addEventListener('keyup', e => {
  if (e.code === 'Space' && state.source) {
    render();
  }
});

// =========================================================================
// TẠO ẢNH MẪU STUDIO & MỞ TỆP ẢNH / KÉO THẢ
// =========================================================================
function generateSampleImage(type = 'portrait') {
  const w = 960, h = 640;
  const off = document.createElement('canvas');
  off.width = w; off.height = h;
  const c = off.getContext('2d');

  if (type === 'portrait') {
    const sky = c.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#1e3a8a');
    sky.addColorStop(0.45, '#d97706');
    sky.addColorStop(1, '#3f2e21');
    c.fillStyle = sky;
    c.fillRect(0, 0, w, h);

    const sun = c.createRadialGradient(w * 0.72, h * 0.34, 15, w * 0.72, h * 0.34, 240);
    sun.addColorStop(0, 'rgba(255, 248, 220, 1)');
    sun.addColorStop(0.4, 'rgba(251, 146, 60, 0.65)');
    sun.addColorStop(1, 'rgba(251, 146, 60, 0)');
    c.fillStyle = sun;
    c.fillRect(0, 0, w, h);

    c.fillStyle = '#e6b89c';
    c.beginPath();
    c.arc(w * 0.42, h * 0.45, 92, 0, Math.PI * 2);
    c.fill();

    c.fillStyle = '#b91c1c';
    c.beginPath();
    c.arc(w * 0.42, h * 0.82, 165, Math.PI, 0);
    c.fill();
  } else if (type === 'street') {
    const bg = c.createLinearGradient(0, 0, w, h);
    bg.addColorStop(0, '#090d16');
    bg.addColorStop(0.5, '#111827');
    bg.addColorStop(1, '#1f2937');
    c.fillStyle = bg;
    c.fillRect(0, 0, w, h);

    const neonColors = ['#ef4444', '#06b6d4', '#f59e0b', '#ec4899', '#3b82f6'];
    for (let i = 0; i < 18; i++) {
      const x = (i * 137) % w;
      const y = 120 + ((i * 89) % (h - 220));
      const r = 24 + (i % 4) * 18;
      const g = c.createRadialGradient(x, y, 2, x, y, r);
      const col = neonColors[i % neonColors.length];
      g.addColorStop(0, '#ffffff');
      g.addColorStop(0.3, col);
      g.addColorStop(1, 'rgba(0,0,0,0)');
      c.fillStyle = g;
      c.beginPath();
      c.arc(x, y, r, 0, Math.PI * 2);
      c.fill();
    }
  } else {
    const sky = c.createLinearGradient(0, 0, 0, h * 0.65);
    sky.addColorStop(0, '#0284c7');
    sky.addColorStop(0.6, '#7dd3fc');
    sky.addColorStop(1, '#fef08a');
    c.fillStyle = sky;
    c.fillRect(0, 0, w, h);

    c.fillStyle = '#1e293b';
    c.beginPath();
    c.moveTo(0, h * 0.68);
    c.lineTo(w * 0.28, h * 0.32);
    c.lineTo(w * 0.55, h * 0.62);
    c.lineTo(w * 0.82, h * 0.26);
    c.lineTo(w, h * 0.65);
    c.lineTo(w, h);
    c.lineTo(0, h);
    c.closePath();
    c.fill();

    c.fillStyle = '#15803d';
    c.fillRect(0, h * 0.68, w, h * 0.32);
  }

  loadSourceImageData(c.getImageData(0, 0, w, h), `Ảnh mẫu Studio (${type})`);
}

document.querySelectorAll('[data-sample]').forEach(btn => {
  btn.addEventListener('click', () => generateSampleImage(btn.dataset.sample));
});

function loadSourceImageData(imageData, label) {
  state.source = imageData;
  empty.hidden = true;
  canvasWrapper.hidden = false;

  const enableIds = [
    '#export', '#compareBtn', '#rotate90', '#aiAuto',
    '#contactSheetBtn', '#quadViewBtn', '#dateStampBtn',
    '#shareCode', '#exportCube', '#matchRefBtn', '#livingFilmBtn', '#lightCraftBtn'
  ];
  for (const sel of enableIds) {
    const el = document.querySelector(sel);
    if (el) el.disabled = false;
  }

  const embedded = extractRecipeFromPixels(imageData.data);
  if (embedded) {
    state.settings = { ...structuredClone(defaults), ...embedded };
    state.activeRecipeName = 'Khôi phục LSB';
    state.curvePoints = structuredClone(embedded.curvePoints || [[0, 0], [1, 1]]);
    syncInputsFromSettings();
    drawCurveEditor();
    state.history = [];
    state.historyIndex = -1;
    commitHistory(`Khôi phục công thức ẩn từ ${label}`);
    render();
    setStatus(`🧬 Phát hiện ảnh xuất từ IRO Studio! Đã tự động khôi phục công thức màu ẩn (Film: ${state.settings.film}).`);
    return;
  }

  state.history = [];
  state.historyIndex = -1;
  commitHistory(`Mở ${label}`);
  render();
  setStatus(`Đã nạp ${label} (${imageData.width}×${imageData.height}px) — Sẵn sàng hậu kỳ.`);
}

async function handleImageFile(file) {
  if (!file) return;
  try {
    const bmp = await createImageBitmap(file);
    const maxDim = 1920;
    const scale = Math.min(1, maxDim / Math.max(bmp.width, bmp.height));
    const w = Math.max(1, Math.round(bmp.width * scale));
    const h = Math.max(1, Math.round(bmp.height * scale));
    canvas.width = w; canvas.height = h;
    ctx.drawImage(bmp, 0, 0, w, h);
    loadSourceImageData(ctx.getImageData(0, 0, w, h), file.name);
  } catch (err) {
    setStatus('Không đọc được ảnh: ' + err.message, true);
  }
}

document.querySelector('#photo').addEventListener('change', e => handleImageFile(e.target.files[0]));

stageContainer.addEventListener('dragover', e => {
  e.preventDefault();
  stageContainer.style.borderColor = '#f09a3e';
});
stageContainer.addEventListener('dragleave', () => {
  stageContainer.style.borderColor = '';
});
stageContainer.addEventListener('drop', e => {
  e.preventDefault();
  stageContainer.style.borderColor = '';
  const file = e.dataTransfer?.files?.[0];
  if (file) handleImageFile(file);
});

document.querySelector('#lut').addEventListener('change', async e => {
  const [file] = e.target.files;
  if (!file) return;
  try {
    state.lut = parseCube(await file.text());
    commitHistory(`Nạp LUT ${file.name}`);
    render();
    setStatus(`Đã nạp 3D LUT: ${state.lut.title} (${state.lut.size}³).`);
  } catch (err) {
    setStatus(err.message, true);
  }
});

document.querySelector('#export').addEventListener('click', () => {
  if (!state.source) return;
  render();
  if (state.frameStyle === 0) {
    const currentImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const stegoPixels = embedRecipeIntoPixels(currentImageData.data, {
      ...state.settings,
      curvePoints: state.curvePoints
    });
    ctx.putImageData(new ImageData(stegoPixels, canvas.width, canvas.height), 0, 0);
  }

  canvas.toBlob(blob => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `iro-studio-${state.settings.film.toLowerCase().replace(/\s+/g, '-')}.png`;
    a.click();
    URL.revokeObjectURL(a.href);
    setStatus('💾 Đã xuất ảnh PNG kèm chữ ký công thức ẩn LSB (Invisible Recipe DNA)!');
  }, 'image/png');
});

function render(isLivingFrame = false) {
  if (!state.source) return;
  if (state.quadViewActive && !isLivingFrame) {
    renderQuadView();
    return;
  }

  const out = processPixels(
    state.source.data,
    state.source.width,
    state.source.height,
    state.settings,
    state.lut,
    state.livingFilmActive ? state.livingFilmFrame : 0
  );

  if (state.frameStyle > 0 && !state.comparingSplit) {
    const framedCanvas = renderFramedImage(
      out,
      state.source.width,
      state.source.height,
      state.frameStyle,
      {
        title: frameTitleInput.value,
        exif: frameExifInput.value,
        film: state.settings.film
      }
    );
    canvas.width = framedCanvas.width;
    canvas.height = framedCanvas.height;
    ctx.drawImage(framedCanvas, 0, 0);
  } else {
    canvas.width = state.source.width;
    canvas.height = state.source.height;
    ctx.putImageData(new ImageData(out, state.source.width, state.source.height), 0, 0);
  }

  beforeCanvas.width = state.source.width;
  beforeCanvas.height = state.source.height;
  beforeCtx.putImageData(state.source, 0, 0);
  if (!isLivingFrame) {
    drawHistogram(out);
    syncRulerHeader();
    drawTickRuler();
  }
}

function setStatus(msg, isError = false) {
  status.textContent = msg;
  status.classList.toggle('error', isError);
}

// Khởi tạo trạng thái ban đầu và tự động nạp ảnh mẫu Chân dung Hoàng hôn để hiển thị ngay
drawCurveEditor();
generateSampleImage('portrait');
