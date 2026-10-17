import {
  defaults, parseCube, processPixels, rotatePixels90,
  buildMonotoneCurveLut, computeHistogramAndStats,
  autoBalanceSettings, CURATED_PRESETS,
  encodeRecipeCode, decodeRecipeCode,
  bakeRecipeToCube, extractDominantPalette, matchColorFromReference,
  promptToRecipeVi, embedRecipeIntoPixels, extractRecipeFromPixels,
  parseFujiRecipe, LIGHTCRAFT_PRESETS, PHOTO_FRAMES, renderPhotoFrame
} from './engine.mjs?v=20261016-fix2';

// Polyfill roundRect cho các trình duyệt hoặc WebKit cũ
if (typeof CanvasRenderingContext2D !== 'undefined' && !CanvasRenderingContext2D.prototype.roundRect) {
  CanvasRenderingContext2D.prototype.roundRect = function(x, y, w, h, radii = 0) {
    const r = typeof radii === 'number' ? radii : (Array.isArray(radii) ? radii[0] : 0);
    this.moveTo(x + r, y);
    this.arcTo(x + w, y, x + w, y + h, r);
    this.arcTo(x + w, y + h, x, y + h, r);
    this.arcTo(x, y + h, x, y, r);
    this.arcTo(x, y, x + w, y, r);
    this.closePath();
    return this;
  };
}

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
const dragDropOverlay = document.querySelector('#dragDropOverlay');
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
  favoriteRecipeIds: readFavoriteRecipeIds(),
  activeHslMode: 'hue', // 'hue' | 'sat' | 'lum'
  frameStyle: 0,
  stageZoom: 1.0,
  stagePanX: 0,
  stagePanY: 0,
  porcelainTheme: true,
  previewSource: null,
  currentUser: null,
  authToken: (() => {
    try {
      return (typeof localStorage !== 'undefined' && typeof localStorage.getItem === 'function')
        ? localStorage.getItem('iro_token') || ''
        : '';
    } catch (_) {
      return '';
    }
  })()
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
  ['chrome', 'Hiệu ứng Color Chrome', 0, 2, 1],
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

function readFavoriteRecipeIds() {
  try {
    const saved = JSON.parse(localStorage.getItem('iro_fav_recipes_v3') || '[]');
    return new Set(Array.isArray(saved) ? saved.filter(id => typeof id === 'string') : []);
  } catch (_) { return new Set(); }
}

function wheelValues(key) {
  return { hue: state.settings[`${key}Hue`] || 0, sat: state.settings[`${key}Sat`] || 0, lum: state.settings[`${key}Lum`] || 0 };
}

function setWheelValues(key, values) {
  for (const [name, suffix] of [['hue', 'Hue'], ['sat', 'Sat'], ['lum', 'Lum']]) {
    if (values[name] !== undefined) state.settings[`${key}${suffix}`] = Number(values[name]);
  }
}

function resetColorWheels() {
  for (const key of ['shadows', 'midtones', 'highlights']) setWheelValues(key, { hue: 0, sat: 0, lum: 0 });
}

function escapeHTML(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
}

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
  document.querySelectorAll('#m3BottomNav .bottom-nav-item').forEach(item => {
    if (item.dataset.railTarget) {
      item.classList.toggle('active', item.dataset.railTarget === targetId);
    }
  });
}

segTabBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    if (btn.dataset.tabTarget) activateInspectorTab(btn.dataset.tabTarget);
  });
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
// MÀN HÌNH CHÍNH & BUỒNG TỐI BIÊN TẬP DẠNG THẺ (CARD-DECK & BACK NAVIGATION)
// =========================================================================
function showScreen(screenId, pushHistory = true) {
  const isEditor = screenId === 'editor';
  const screenLanding = document.querySelector('#screenLanding');
  const screenEditor = document.querySelector('#screenEditor');
  const editorBackBtn = document.querySelector('#editorBackBtn');
  const resumeEditorBanner = document.querySelector('#resumeEditorBanner');
  const resumePhotoTitle = document.querySelector('#resumePhotoTitle');
  const brandIdentity = document.querySelector('#brandIdentity');

  if (screenLanding) {
    screenLanding.classList.toggle('active', !isEditor);
  }
  if (screenEditor) {
    screenEditor.classList.toggle('active', isEditor);
  }
  if (editorBackBtn) {
    editorBackBtn.hidden = !isEditor;
  }
  if (brandIdentity) {
    brandIdentity.classList.toggle('in-editor', isEditor);
  }
  if (resumeEditorBanner) {
    if (!isEditor && state.source) {
      resumeEditorBanner.hidden = false;
      if (resumePhotoTitle) {
        resumePhotoTitle.textContent = state.activeRecipeName || 'Bức ảnh đang mở';
      }
    } else {
      resumeEditorBanner.hidden = true;
    }
  }

  if (isEditor) {
    requestAnimationFrame(() => {
      drawTickRuler();
      if (typeof drawCurveEditor === 'function') drawCurveEditor();
      if (typeof drawAllColorWheels === 'function') drawAllColorWheels();
    });
  }

  if (pushHistory && typeof window !== 'undefined' && window.history && typeof window.history.pushState === 'function') {
    try {
      window.history.pushState({ screen: screenId }, '', screenId === 'landing' ? '#/' : '#/editor');
    } catch (_) {}
  }
}

document.querySelector('#editorBackBtn')?.addEventListener('click', () => {
  showScreen('landing');
});
document.querySelector('#resumeEditorBtn')?.addEventListener('click', () => {
  showScreen('editor');
});
if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('popstate', (e) => {
    const targetScreen = (e.state && e.state.screen) || (location.hash === '#/editor' ? 'editor' : 'landing');
    showScreen(targetScreen, false);
  });
  if (location.hash === '#/editor') {
    showScreen('editor', false);
  }
}

function selectDeckTab(tabId) {
  document.querySelectorAll('.deck-tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.deckTab === tabId);
  });
  document.querySelectorAll('.deck-pane').forEach(pane => {
    const isTarget = pane.id === tabId;
    pane.classList.toggle('active', isTarget);
    pane.hidden = !isTarget;
  });
  if (tabId === 'tabAdjust') {
    requestAnimationFrame(() => {
      drawTickRuler();
    });
  } else if (tabId === 'tabLightCraft') {
    requestAnimationFrame(() => {
      drawCurveEditor();
      drawAllColorWheels();
      if (typeof renderHslSliders === 'function') renderHslSliders();
    });
  }
}

document.querySelectorAll('.deck-tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    selectDeckTab(btn.dataset.deckTab);
  });
});

function updateStageInfoPill() {
  const pill = document.querySelector('#stageInfoPill');
  if (!state.source) {
    if (pill) pill.hidden = true;
    return;
  }
  if (pill) pill.hidden = false;
  const dimEl = document.querySelector('#stageDimensionText');
  const filmEl = document.querySelector('#stageFilmText');
  const frameEl = document.querySelector('#stageFrameText');
  if (canvas && dimEl) {
    dimEl.textContent = `${canvas.width} × ${canvas.height}`;
  }
  if (filmEl) {
    filmEl.textContent = state.activeRecipeName || 'Gốc';
  }
  if (frameEl) {
    const frameName = PHOTO_FRAMES[state.frameStyle]?.name || 'Không khung';
    frameEl.textContent = frameName;
  }
  const topActiveFilmLabel = document.querySelector('#topActiveFilmLabel');
  if (topActiveFilmLabel) {
    topActiveFilmLabel.textContent = `${state.settings.film || 'Classic Chrome'} · ${state.activeRecipeName || 'Gốc'}`;
  }
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
  if (key === 'chrome' || key === 'chromeBlue') {
    return num === 0 ? 'Tắt' : (num === 1 ? 'Nhẹ' : 'Mạnh');
  }
  if (key === 'lutAmount' || key === 'skinProtect') {
    return `${Math.round(num)}%`;
  }
  const rounded = Math.round(num * 10) / 10;
  return (rounded > 0 ? '+' : '') + rounded;
}

function initM3RulerDock() {
  if (!tickRulerCanvas) return;
  if (m3ParamPillStrip) {
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
  }

  // Gắn sự kiện cho Dải công cụ tròn nằm ngang (Circular Tools Strip)
  document.querySelectorAll('.circular-tool-item').forEach(item => {
    const k = item.dataset.paramKey;
    if (!k) return;
    item.addEventListener('click', () => {
      const spec = allSliderSpecs.find(s => s[0] === k);
      if (spec) {
        activeRulerSpec = spec;
        syncRulerHeader();
        drawTickRuler();
      }
    });
  });

  document.querySelector('#toolBtnAuto')?.addEventListener('click', () => {
    document.querySelector('#aiAuto')?.click();
  });

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
  // Đồng bộ trạng thái active của dải nút tròn (Circular Tools)
  document.querySelectorAll('.circular-tool-item').forEach(btn => {
    const k = btn.dataset.paramKey;
    if (k) {
      btn.classList.toggle('active', k === key);
      const cur = Number(state.settings[k] ?? defaults[k] ?? 0);
      const def = Number(defaults[k] ?? 0);
      btn.classList.toggle('modified', Math.abs(cur - def) > 0.01);
    }
  });
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

// Cảm ứng đa điểm Native trên điện thoại & máy tính bảng (2-finger Pinch-to-Zoom & Pan)
let initialPinchDist = 0;
let initialPinchZoom = 1.0;
let initialPinchCenter = { x: 0, y: 0 };
let initialPan = { x: 0, y: 0 };

stageContainer.addEventListener('touchstart', e => {
  if (e.touches.length === 2) {
    const t1 = e.touches[0];
    const t2 = e.touches[1];
    initialPinchDist = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
    initialPinchZoom = state.stageZoom;
    initialPinchCenter = {
      x: (t1.clientX + t2.clientX) / 2,
      y: (t1.clientY + t2.clientY) / 2
    };
    initialPan = { x: state.stagePanX, y: state.stagePanY };
  }
}, { passive: true });

stageContainer.addEventListener('touchmove', e => {
  if (e.touches.length === 2 && initialPinchDist > 0) {
    e.preventDefault();
    const t1 = e.touches[0];
    const t2 = e.touches[1];
    const currentDist = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
    const scale = currentDist / initialPinchDist;
    state.stageZoom = Math.max(1.0, Math.min(4.0, initialPinchZoom * scale));

    const currentCenter = {
      x: (t1.clientX + t2.clientX) / 2,
      y: (t1.clientY + t2.clientY) / 2
    };
    state.stagePanX = initialPan.x + (currentCenter.x - initialPinchCenter.x);
    state.stagePanY = initialPan.y + (currentCenter.y - initialPinchCenter.y);
    applyStageTransform();
  }
}, { passive: false });

stageContainer.addEventListener('touchend', e => {
  if (e.touches.length < 2) {
    initialPinchDist = 0;
  }
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
  commitHistory('Đổi khung ảnh');
  render();
});
frameTitleInput.addEventListener('input', () => render());
frameExifInput.addEventListener('input', () => render());
frameTitleInput.addEventListener('change', () => commitHistory('Đổi tiêu đề khung'));
frameExifInput.addEventListener('change', () => commitHistory('Đổi thông tin khung'));

// Tải kho 392 công thức từ recipes.json
async function load392Catalog() {
  try {
    const res = await fetch('./recipes.json');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const catalog = await res.json();
    const rawList = Array.isArray(catalog) ? catalog : catalog.recipes;
    if (!Array.isArray(rawList)) throw new Error('Kho công thức không đúng định dạng.');
    state.fullCatalogRecipes = rawList.map((record, index) => {
      const parsed = parseFujiRecipe(record, structuredClone(defaults));
      return {
        ...parsed,
        id: String(record.id || record.sourceUrl || `recipe-${index}`),
        wb: String(parsed.settings.wb || 'Auto'),
        dr: parsed.settings.dr || 100
      };
    });
    buildFilmFilterChips();
    if (!recipeLibraryModal.hidden) renderRecipeLibraryGrid();
    if (!cmdPaletteModal.hidden) renderCmdPaletteResults();
    renderLandingRecipeGrid();
  } catch (error) {
    setStatus(`Không tải được kho công thức: ${error.message}. Bạn vẫn có thể dùng preset và chỉnh tay.`, true);
  }
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
        <span class="r-title">${escapeHTML(rec.name)}</span>
        <button type="button" class="fav-star-btn ${isFav ? 'is-fav' : ''}" title="Đánh dấu Yêu thích">${isFav ? '★' : '☆'}</button>
      </div>
      <span class="r-film">🎞️ ${escapeHTML(rec.film)}</span>
      <div class="r-meta">
        <span>DR${rec.dr}</span>
        <span>H:${rec.settings.highlight > 0 ? '+' : ''}${rec.settings.highlight} S:${rec.settings.shadow > 0 ? '+' : ''}${rec.settings.shadow}</span>
        <span>WB: ${escapeHTML(rec.wb)}</span>
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

// =========================================================================
// LƯỚI 392 CÔNG THỨC TRÊN MÀN HÌNH LANDING (BENTO SENSOR CARD)
// =========================================================================
let landingSensorFilter = 'Tất cả';

function renderLandingRecipeGrid() {
  const grid = document.querySelector('#landingRecipeGrid');
  if (!grid) return;
  const q = (document.querySelector('#landingSearchInput')?.value || '').trim().toLowerCase();

  const filtered = state.fullCatalogRecipes.filter(r => {
    const matchSensor = landingSensorFilter === 'Tất cả' ||
      (r.sensor && r.sensor.toLowerCase().includes(landingSensorFilter.toLowerCase())) ||
      (landingSensorFilter === 'Bayer' && (r.sensor?.toLowerCase().includes('bayer') || r.sensor?.toLowerCase().includes('gfx')));
    const matchQuery = !q ||
      r.name.toLowerCase().includes(q) ||
      r.film.toLowerCase().includes(q) ||
      r.wb.toLowerCase().includes(q);
    return matchSensor && matchQuery;
  }).slice(0, 36);

  grid.innerHTML = '';
  if (filtered.length === 0) {
    grid.innerHTML = '<div class="landing-empty-recipes" style="grid-column: 1 / -1; text-align: center; padding: 24px; color: var(--text-sub);">Không tìm thấy công thức phù hợp. Thử tìm từ khóa khác nhé!</div>';
    return;
  }

  for (const rec of filtered) {
    const isCurrent = state.activeRecipeName === rec.name;
    const card = document.createElement('div');
    card.className = 'landing-recipe-card' + (isCurrent ? ' active' : '');
    card.innerHTML = `
      <div class="l-card-head">
        <span class="l-card-title">${escapeHTML(rec.name)}</span>
        <span class="l-card-sensor">${escapeHTML(rec.sensor || 'X-Trans')}</span>
      </div>
      <div class="l-card-film">🎞️ ${escapeHTML(rec.film)}</div>
      <div class="l-card-meta">
        <span>DR${rec.dr}</span>
        <span>WB ${escapeHTML(rec.wb)}</span>
      </div>
    `;
    card.addEventListener('click', () => {
      applyCatalogRecipe(rec);
      if (!state.source) {
        showToast(`Đã chọn công thức "${rec.name}". Vui lòng chọn ảnh để áp dụng!`, 'info', '📷');
        document.querySelector('#photo')?.click();
      } else {
        showScreen('editor');
      }
    });
    grid.append(card);
  }
}

document.querySelectorAll('.sensor-bento-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('.sensor-bento-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    landingSensorFilter = pill.dataset.sensor;
    renderLandingRecipeGrid();
  });
});
document.querySelector('#landingSearchInput')?.addEventListener('input', () => {
  renderLandingRecipeGrid();
});
document.querySelector('#bentoOpenFullLibBtn')?.addEventListener('click', openRecipeLibraryModal);

document.querySelectorAll('.frame-preview-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    const frameId = Number(pill.dataset.frameId || 0);
    state.frameStyle = frameId;
    if (frameStyleSelect) {
      frameStyleSelect.value = String(frameId);
      frameStyleSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (!state.source) {
      showToast('Đã chọn khung. Vui lòng mở ảnh từ máy để áp dụng!', 'info', '🖼️');
      document.querySelector('#photo')?.click();
    } else {
      render();
      selectDeckTab('tabFrames');
      showScreen('editor');
    }
  });
});

document.querySelector('#heroOpenBtn')?.addEventListener('click', () => {
  document.querySelector('#photo')?.click();
});
document.querySelector('#heroPromptBtn')?.addEventListener('click', () => {
  promptModal.hidden = false;
  promptTextInput?.focus();
});
document.querySelector('#heroRandomBtn')?.addEventListener('click', () => {
  applySurpriseRandomRecipe();
  if (!state.source) {
    showToast(`Đã chọn ngẫu nhiên "${state.activeRecipeName}". Vui lòng mở ảnh để trải nghiệm!`, 'info', '🎲');
    document.querySelector('#photo')?.click();
  } else {
    showScreen('editor');
  }
});

function openRecipeLibraryModal() {
  recipeLibraryModal.hidden = false;
  renderRecipeLibraryGrid();
  recipeSearchInput.focus();
}

document.querySelector('#recipeLibraryBtn').addEventListener('click', openRecipeLibraryModal);
document.querySelector('#segRecipeLibBtn')?.addEventListener('click', openRecipeLibraryModal);
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
    btn.innerHTML = `<span class="p-name">🎞️ ${escapeHTML(rec.name)}</span><span class="p-sub">${escapeHTML(rec.film)} · DR${rec.dr}</span>`;
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
      resetColorWheels();
      state.settings.hslHue = [...defaults.hslHue];
      state.settings.hslSat = [...defaults.hslSat];
      state.settings.hslLum = [...defaults.hslLum];
      state.curvePoints = [[0, 0], [1, 1]];
      state.settings.clarity = defaults.clarity;
      state.settings.vignette = defaults.vignette;
      if (p.curve) {
        state.curvePoints = structuredClone(p.curve);
        state.settings.curveLut = buildMonotoneCurveLut(state.curvePoints);
      }
      if (p.wheels) {
        for (const k of ['shadows', 'midtones', 'highlights']) {
          if (p.wheels[k]) setWheelValues(k, p.wheels[k]);
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
      state.settings[`${wheelKey}Hue`] = Math.round(angle);
      state.settings[`${wheelKey}Sat`] = Math.round(dist * 100);
      drawColorWheel(wCanvas, wheelValues(wheelKey));
      requestInteractiveRender();
    };
    wCanvas.addEventListener('pointerdown', e => { dragging = true; wCanvas.setPointerCapture(e.pointerId); updateFromMouse(e); });
    wCanvas.addEventListener('pointermove', e => { if (dragging) updateFromMouse(e); });
    wCanvas.addEventListener('pointerup', () => { dragging = false; commitHistory(`Bàn xoay màu ${wheelKey}`); render(false, false, false); });
    wCanvas.addEventListener('dblclick', () => {
      setWheelValues(wheelKey, { hue: 0, sat: 0, lum: 0 });
      drawAllColorWheels();
      commitHistory(`Đặt lại màu ${wheelKey}`);
      render();
    });
  });

  [['lumShadows', 'shadows'], ['lumMidtones', 'midtones'], ['lumHighlights', 'highlights']].forEach(([inputId, key]) => {
    const el = document.getElementById(inputId);
    el.addEventListener('input', () => {
      state.settings[`${key}Lum`] = Number(el.value);
      requestInteractiveRender();
    });
    el.addEventListener('change', () => {
      commitHistory(`Độ sáng ${key}`);
      render(false, false, false);
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
  drawColorWheel(document.getElementById('wheelShadows'), wheelValues('shadows'));
  drawColorWheel(document.getElementById('wheelMidtones'), wheelValues('midtones'));
  drawColorWheel(document.getElementById('wheelHighlights'), wheelValues('highlights'));
  document.getElementById('lumShadows').value = wheelValues('shadows').lum;
  document.getElementById('lumMidtones').value = wheelValues('midtones').lum;
  document.getElementById('lumHighlights').value = wheelValues('highlights').lum;
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
      requestInteractiveRender();
    });
    input.addEventListener('change', () => {
      commitHistory(`HSL ${label}`);
      render(false, false, false);
    });
    input.addEventListener('dblclick', () => {
      state.settings[prop][idx] = 0;
      input.value = 0;
      out.textContent = '0';
      commitHistory(`Đặt lại HSL ${label}`);
      render();
    });
    hslSlidersContainer.append(row);
  });
}

initLightCraftModal();

const openLightCraft = () => {
  if (!state.source) {
    setStatus('Hãy mở ảnh hoặc chọn ảnh mẫu trước khi mở Buồng tối LightCraft.');
    return;
  }
  lightCraftModal.hidden = !lightCraftModal.hidden;
  if (!lightCraftModal.hidden) {
    drawAllColorWheels();
    renderHslSliders();
  }
};
document.querySelector('#lightCraftBtn')?.addEventListener('click', openLightCraft);
document.querySelector('#segLightCraftBtn')?.addEventListener('click', openLightCraft);
document.querySelector('#closeLightCraft').addEventListener('click', () => { lightCraftModal.hidden = true; });
document.querySelector('#resetLightCraftBtn').addEventListener('click', () => {
  resetColorWheels();
  state.settings.hslHue = [0, 0, 0, 0, 0, 0, 0, 0];
  state.settings.hslSat = [0, 0, 0, 0, 0, 0, 0, 0];
  state.settings.hslLum = [0, 0, 0, 0, 0, 0, 0, 0];
  state.settings.clarity = defaults.clarity;
  state.settings.vignette = defaults.vignette;
  state.curvePoints = [[0, 0], [1, 1]];
  drawCurveEditor();
  syncInputsFromSettings();
  commitHistory('Đặt lại LightCraft');
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
    source: state.source,
    lut: state.lut,
    frameStyle: state.frameStyle,
    frameTitle: frameTitleInput.value,
    frameExif: frameExifInput.value,
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
    state.source = snap.source;
    state.lut = snap.lut;
    state.frameStyle = snap.frameStyle;
    frameTitleInput.value = snap.frameTitle;
    frameExifInput.value = snap.frameExif;
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
    state.source = snap.source;
    state.lut = snap.lut;
    state.frameStyle = snap.frameStyle;
    frameTitleInput.value = snap.frameTitle;
    frameExifInput.value = snap.frameExif;
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
  const filmSelect = document.querySelector('#film');
  let selectedFilm = [...filmSelect.options].find(option => option.value.toLowerCase() === String(state.settings.film).toLowerCase());
  if (!selectedFilm) {
    selectedFilm = document.createElement('option');
    selectedFilm.value = state.settings.film;
    selectedFilm.textContent = state.settings.film;
    filmSelect.append(selectedFilm);
  }
  filmSelect.value = selectedFilm.value;
  frameStyleSelect.value = String(state.frameStyle);
  frameExifControls.hidden = state.frameStyle === 0;
  if (!lightCraftModal.hidden) {
    drawAllColorWheels();
    renderHslSliders();
  }
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
    requestInteractiveRender();
  });
  el.addEventListener('change', e => {
    commitHistory(`Chỉnh ${e.target.dataset.key}`);
    render(false, false, false);
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
  state.settings.curvePoints = structuredClone(state.curvePoints);
  state.settings.curveLut = lut;

  curveCtx.strokeStyle = '#f09a3e';
  curveCtx.lineWidth = 2.4;
  curveCtx.beginPath();
  for (let x = 0; x < 256; x++) {
    const px = (x / 255) * w;
    const py = h - (lut[x] / 255) * h;
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
  requestInteractiveRender();
});

curveCanvas.addEventListener('pointerup', () => {
  if (state.draggingCurveIdx >= 0) {
    state.draggingCurveIdx = -1;
    commitHistory('Chỉnh đường cong Tone Curve');
    render(false, false, false);
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
  const { rHist, gHist, bHist } = computeHistogramAndStats(pixels);
  const maxBin = Math.max(1, ...rHist, ...gHist, ...bHist);
  const evShift = Number(state.settings.exposure) || 0;
  let clipped = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] > 0 && Math.max(pixels[i], pixels[i + 1], pixels[i + 2]) >= 254) clipped++;
  }
  const highlightClipPct = clipped / Math.max(1, pixels.length / 4) * 100;
  const w = histCanvas.width, h = histCanvas.height;
  histCtx.clearRect(0, 0, w, h);

  const drawChannel = (hist, color) => {
    histCtx.fillStyle = color;
    histCtx.beginPath();
    histCtx.moveTo(0, h);
    for (let i = 0; i < hist.length; i++) {
      const x = (i / (hist.length - 1)) * w;
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
    div.title = `Nhấn để sao chép mã màu ${sw.hex.toUpperCase()}`;
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
  const rotated = rotatePixels90(state.source.data, state.source.width, state.source.height, 90);
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
  render();
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
    { title: '1. Ảnh gốc (Original)', sub: 'Chưa qua xử lý', settings: { ...structuredClone(defaults), film: 'Original' }, lut: null },
    { title: `2. Đang chọn (${state.settings.film})`, sub: state.activeRecipeName, settings: structuredClone(state.settings) },
    {
      title: '3. Kodachrome 64 + Pro-Mist',
      sub: 'Classic Chrome ấm cổ điển',
      settings: { ...structuredClone(state.settings), film: 'Classic Chrome', temperature: 16, proMist: 28, chrome: 2 }
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
    const outPixels = processPixels(smallData.data, tw, th, v.settings, v.lut === null ? null : state.lut);
    c.getContext('2d').putImageData(new ImageData(outPixels, tw, th), 0, 0);
    const footer = document.createElement('div');
    footer.className = 'quad-card-footer';
    footer.innerHTML = `<b>${escapeHTML(v.title)}</b><span>${escapeHTML(v.sub)}</span>`;
    card.append(c, footer);
    card.addEventListener('click', () => {
      state.settings = structuredClone(v.settings);
      state.curvePoints = structuredClone(v.settings.curvePoints || [[0, 0], [1, 1]]);
      if (v.lut === null) state.lut = null;
      state.activeRecipeName = v.title;
      drawCurveEditor();
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
    pSettings.curvePoints = structuredClone(preset.settings.curvePoints || [[0, 0], [1, 1]]);
    const out = processPixels(thumbData.data, tw, th, pSettings, null);
    c.getContext('2d').putImageData(new ImageData(out, tw, th), 0, 0);
    const title = document.createElement('div');
    title.className = 'sheet-item-title';
    title.textContent = `${preset.name} · ${preset.subtitle}`;
    item.append(c, title);
    item.addEventListener('click', () => {
      state.settings = structuredClone(pSettings);
      state.lut = null;
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
  state.settings = { ...structuredClone(nextSettings), diagnosticMode: keepDiag };
  state.activeRecipeName = 'AI Tả cảnh';
  state.curvePoints = structuredClone(nextSettings.curvePoints || [[0, 0], [1, 1]]);
  syncInputsFromSettings();
  drawCurveEditor();
  promptModal.hidden = true;
  if (!state.source) {
    showToast('Đã tạo công thức AI! Vui lòng chọn ảnh để áp dụng.', 'info', '🪄');
    document.querySelector('#photo')?.click();
  } else {
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
    bmp.close();
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
  const cubeContent = bakeRecipeToCube(state.settings, 17, title, state.lut);
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
  if (!input) return;
  if (input.trim() === currentCode) {
    try {
      if (!navigator.clipboard) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(currentCode);
      setStatus('📋 Đã sao chép mã công thức IRO1 vào bộ nhớ tạm!');
    } catch (_) {
      setStatus('Trình duyệt không cho sao chép tự động. Mở lại mã để sao chép thủ công.', true);
    }
    return;
  }
  let decoded;
  try {
    decoded = decodeRecipeCode(input.trim());
  } catch (_) {
    setStatus('Mã công thức IRO1 không hợp lệ.', true);
    return;
  }
  state.activeRecipeName = 'Mã chia sẻ IRO1';
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
  state.lut = null;
  document.querySelector('#lut').value = '';
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
    if (document.body.classList.contains('auth-locked')) {
      return;
    }
    recipeLibraryModal.hidden = true;
    lightCraftModal.hidden = true;
    contactSheetModal.hidden = true;
    promptModal.hidden = true;
    cmdPaletteModal.hidden = true;
    const authModal = document.getElementById('authModal');
    if (authModal) authModal.hidden = true;
    const adminUserModal = document.getElementById('adminUserModal');
    if (adminUserModal) adminUserModal.hidden = true;
    const myPhotosModal = document.getElementById('myPhotosModal');
    if (myPhotosModal) myPhotosModal.hidden = true;
    const moreToolsDropdown = document.getElementById('moreToolsDropdown');
    if (moreToolsDropdown) moreToolsDropdown.hidden = true;
    const userAccountDropdown = document.getElementById('userAccountDropdown');
    if (userAccountDropdown) userAccountDropdown.hidden = true;
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

if (typeof window !== 'undefined') {
  window.generateSampleImage = generateSampleImage;
}

document.querySelectorAll('[data-sample]').forEach(btn => {
  btn.addEventListener('click', () => generateSampleImage(btn.dataset.sample));
});

document.querySelector('#emptyOpenBtn')?.addEventListener('click', (e) => {
  e.stopPropagation();
  document.querySelector('#photo')?.click();
});
document.querySelector('#empty')?.addEventListener('click', (e) => {
  if (e.target && e.target.closest('#emptyOpenBtn')) return;
  document.querySelector('#photo')?.click();
});

function createDownsampledImageData(srcData, maxDim = 1200) {
  if (!srcData) return null;
  const w0 = srcData.width;
  const h0 = srcData.height;
  if (Math.max(w0, h0) <= maxDim) return srcData;
  const scale = maxDim / Math.max(w0, h0);
  const w = Math.max(1, Math.round(w0 * scale));
  const h = Math.max(1, Math.round(h0 * scale));

  const c1 = document.createElement('canvas');
  c1.width = w0;
  c1.height = h0;
  c1.getContext('2d').putImageData(srcData, 0, 0);

  const c2 = document.createElement('canvas');
  c2.width = w;
  c2.height = h;
  const ctx2 = c2.getContext('2d');
  ctx2.drawImage(c1, 0, 0, w, h);
  return ctx2.getImageData(0, 0, w, h);
}

function loadSourceImageData(imageData, label, rawFile = null) {
  state.source = imageData;
  // Tạo bản preview nén nhẹ để tương tác slider siêu tốc 60fps
  state.previewSource = createDownsampledImageData(imageData, 1200);

  state.stageZoom = 1.0;
  state.stagePanX = 0;
  state.stagePanY = 0;
  applyStageTransform();

  empty.hidden = true;
  empty.style.display = 'none';
  canvasWrapper.hidden = false;
  canvasWrapper.style.display = 'inline-block';
  if (typeof window !== 'undefined' && window.location && window.location.hash === '#/') {
    showScreen('landing', false);
  } else {
    showScreen('editor', false);
  }
  updateStageInfoPill();
  const quickPeekBadge = document.querySelector('#quickPeekBadge');
  if (quickPeekBadge) quickPeekBadge.hidden = false;

  const enableIds = [
    '#export', '#exportRecipeCard', '#compareBtn', '#rotate90', '#aiAuto',
    '#contactSheetBtn', '#quadViewBtn', '#dateStampBtn',
    '#shareCode', '#exportCube', '#matchRefBtn', '#livingFilmBtn', '#lightCraftBtn'
  ];
  for (const sel of enableIds) {
    const el = document.querySelector(sel);
    if (el) el.disabled = false;
  }

  // Khởi tạo phiên làm việc trên máy chủ và đồng bộ ảnh gốc
  initSessionOnServer(label, rawFile ? rawFile.size : 0, imageData.width, imageData.height, rawFile);

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

async function readImageFile(file) {
  // 1. createImageBitmap (Tự động nhận diện EXIF orientation, hỗ trợ cực tốt trên Safari 15+, Chrome, iPad)
  if (typeof createImageBitmap === 'function') {
    try {
      const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' }).catch(() => createImageBitmap(file));
      if (bmp && bmp.width > 0 && bmp.height > 0) {
        return bmp;
      }
    } catch (_) {}
  }

  // 2. HTMLImageElement + ObjectURL (Không dùng decoding="async" để tránh Safari giải phóng blob sớm)
  try {
    const img = await new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const el = new Image();
      el.onload = async () => {
        try {
          if (typeof el.decode === 'function') await el.decode();
        } catch (_) {}
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        resolve(el);
      };
      el.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error('Object URL decode failed'));
      };
      el.src = url;
    });
    if (img && (img.naturalWidth || img.width) > 0) return img;
  } catch (_) {}

  // 3. FileReader DataURL
  try {
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(new Error('FileReader failed'));
      reader.readAsDataURL(file);
    });

    const img2 = await new Promise((resolve, reject) => {
      const el = new Image();
      el.onload = async () => {
        try {
          if (typeof el.decode === 'function') await el.decode();
        } catch (_) {}
        resolve(el);
      };
      el.onerror = () => reject(new Error('DataURL decode failed'));
      el.src = dataUrl;
    });
    if (img2 && (img2.naturalWidth || img2.width) > 0) return img2;
  } catch (_) {}

  // 4. Chuyển đổi định dạng nâng cao qua máy chủ (Hỗ trợ định dạng HEIC/HEIF/RAW từ iPhone/iPad trên Chrome)
  try {
    setStatus(`Đang giải mã định dạng ảnh nâng cao qua máy chủ...`);
    const formData = new FormData();
    formData.append('photo', file);
    const convRes = await fetch('api.php?action=convert_image', {
      method: 'POST',
      body: formData
    });
    if (convRes.ok) {
      const convBlob = await convRes.blob();
      if (typeof createImageBitmap === 'function') {
        try {
          return await createImageBitmap(convBlob);
        } catch (_) {}
      }
      return await new Promise((resolve, reject) => {
        const url = URL.createObjectURL(convBlob);
        const el = new Image();
        el.onload = () => {
          setTimeout(() => URL.revokeObjectURL(url), 10000);
          resolve(el);
        };
        el.onerror = () => {
          URL.revokeObjectURL(url);
          reject(new Error('Converted image load failed'));
        };
        el.src = url;
      });
    }
  } catch (err) {
    console.warn('Server image conversion error:', err);
  }

  throw new Error('Trình duyệt không thể giải mã định dạng ảnh này. Hãy thử chọn ảnh JPG, PNG hoặc WebP.');
}

async function handleImageFile(file) {
  if (!file) return;
  const fileName = file.name || 'Ảnh tải lên';
  setStatus(`Đang nạp tệp ${fileName}...`);
  try {
    const img = await readImageFile(file);
    const maxDim = 2560; // Hỗ trợ độ phân giải lên tới 2.5K sắc nét
    const w0 = img.naturalWidth || img.width;
    const h0 = img.naturalHeight || img.height;
    if (!w0 || !h0) throw new Error('Không đọc được kích thước ảnh');

    const scale = Math.min(1, maxDim / Math.max(w0, h0));
    const w = Math.max(1, Math.round(w0 * scale));
    const h = Math.max(1, Math.round(h0 * scale));

    const tempCanvas = document.createElement('canvas');
    tempCanvas.width = w;
    tempCanvas.height = h;
    const tempCtx = tempCanvas.getContext('2d', { willReadFrequently: true });
    tempCtx.drawImage(img, 0, 0, w, h);
    if (typeof img.close === 'function') img.close();

    const imgData = tempCtx.getImageData(0, 0, w, h);
    loadSourceImageData(imgData, fileName, file);
  } catch (err) {
    console.error('Lỗi khi mở ảnh:', err);
    alert('Không thể mở tệp ảnh: ' + (err.message || 'Định dạng không được hỗ trợ'));
    setStatus('Không đọc được ảnh: ' + err.message, true);
  }
}

const photoInput = document.querySelector('#photo');
const openBtn = document.querySelector('#openBtn');
if (openBtn) {
  openBtn.addEventListener('click', (e) => {
    e.preventDefault();
    photoInput.value = '';
    photoInput.click();
  });
}

const lutInput = document.querySelector('#lut');
const importLutBtn = document.querySelector('#importLutBtn');
if (importLutBtn) {
  importLutBtn.addEventListener('click', (e) => {
    e.preventDefault();
    lutInput.value = '';
    lutInput.click();
  });
}

photoInput.addEventListener('change', async e => {
  const file = e.target.files?.[0];
  if (file) {
    await handleImageFile(file);
  }
  // Chỉ xóa value sau khi đọc xong tránh Safari giải phóng file handle
  photoInput.value = '';
});

// Cho phép nhấp trực tiếp vào vùng trống sân khấu để chọn ảnh
empty.addEventListener('click', e => {
  if (e.target.closest('button')) return; // Không can thiệp nút chọn ảnh mẫu
  photoInput.value = '';
  photoInput.click();
});
empty.style.cursor = 'pointer';

// =========================================================================
// KÉO THẢ ẢNH TRỰC QUAN TOÀN MÀN HÌNH (CHUẨN CHROME & SAFARI / MACOS / IPAD)
// =========================================================================
let dragCounter = 0;

function setDragOverlayVisible(visible) {
  if (dragDropOverlay) {
    dragDropOverlay.hidden = !visible;
  }
  if (stageContainer) {
    stageContainer.style.borderColor = visible ? 'var(--accent, #e28743)' : '';
  }
}

// Ngăn chặn hành vi mặc định của trình duyệt (mở ảnh thành trang mới) trên toàn document
['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
  document.addEventListener(eventName, e => {
    e.preventDefault();
    e.stopPropagation();
  }, false);
  window.addEventListener(eventName, e => {
    e.preventDefault();
    e.stopPropagation();
  }, false);
});

window.addEventListener('dragenter', e => {
  e.preventDefault();
  e.stopPropagation();
  if (document.body.classList.contains('auth-locked')) return;
  dragCounter++;
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy';
  }
  setDragOverlayVisible(true);
});

window.addEventListener('dragover', e => {
  e.preventDefault();
  e.stopPropagation();
  if (document.body.classList.contains('auth-locked')) return;
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy';
  }
});

window.addEventListener('dragleave', e => {
  e.preventDefault();
  e.stopPropagation();
  dragCounter--;
  if (dragCounter <= 0) {
    dragCounter = 0;
    setDragOverlayVisible(false);
  }
});

window.addEventListener('drop', async e => {
  e.preventDefault();
  e.stopPropagation();
  dragCounter = 0;
  setDragOverlayVisible(false);
  if (document.body.classList.contains('auth-locked')) {
    showToast('Vui lòng đăng nhập trước khi tải ảnh vào buồng tối!', 'warning', '🔒');
    return;
  }

  // 1. Lấy tệp từ files
  let file = e.dataTransfer?.files?.[0];

  // 2. Nếu files rỗng (kéo từ Safari hoặc một số app macOS), tìm trong items
  if (!file && e.dataTransfer?.items) {
    for (let i = 0; i < e.dataTransfer.items.length; i++) {
      const item = e.dataTransfer.items[i];
      if (item.kind === 'file') {
        const f = item.getAsFile();
        if (f) {
          file = f;
          break;
        }
      }
    }
  }

  if (!file) {
    setStatus('Không phát hiện tệp ảnh nào được thả vào.', true);
    return;
  }

  // Tự động nhận diện nếu kéo thả tệp 3D LUT (.cube)
  if (file.name && file.name.toLowerCase().endsWith('.cube')) {
    try {
      state.lut = parseCube(await file.text());
      commitHistory(`Nạp LUT ${file.name}`);
      render();
      setStatus(`Đã nạp 3D LUT: ${state.lut.title} (${state.lut.size}³).`);
    } catch (err) {
      setStatus('Lỗi nạp LUT: ' + err.message, true);
    }
    return;
  }

  // Nạp ảnh
  await handleImageFile(file);
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
  render(false, true);
  const output = document.createElement('canvas');
  output.width = canvas.width;
  output.height = canvas.height;
  const outputCtx = output.getContext('2d');
  outputCtx.drawImage(canvas, 0, 0);
  let hasRecipe = false;
  if (state.frameStyle === 0) {
    const currentImageData = outputCtx.getImageData(0, 0, output.width, output.height);
    const stegoPixels = embedRecipeIntoPixels(currentImageData.data, {
      ...state.settings,
      curvePoints: state.curvePoints
    });
    hasRecipe = Boolean(extractRecipeFromPixels(stegoPixels));
    outputCtx.putImageData(new ImageData(stegoPixels, output.width, output.height), 0, 0);
  }
  const film = state.settings.film;
  render();
  output.toBlob(blob => {
    if (!blob) {
      setStatus('Trình duyệt không thể tạo ảnh PNG. Hãy thử lại.', true);
      return;
    }
    const a = document.createElement('a');
    const url = URL.createObjectURL(blob);
    a.href = url;
    a.download = `iro-studio-${film.toLowerCase().replace(/\s+/g, '-')}.png`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    scheduleServerSync(`Xuất ảnh PNG (${film})`);
    setStatus(hasRecipe
      ? '💾 Đã xuất ảnh PNG kèm chữ ký công thức ẩn LSB (Invisible Recipe DNA)!'
      : '💾 Đã xuất ảnh PNG.');
  }, 'image/png');
});

// =========================================================================
// REALTIME TELEMETRY & LIVE DATABASE SYNC (Máy chủ IRO Studio 3.0)
// =========================================================================
let currentSessionId = 'sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
let syncDebounceTimer = null;
let isSyncing = false;
let pendingSync = false;
const cloudSyncStatus = document.getElementById('cloudSyncStatus');
const syncText = cloudSyncStatus ? cloudSyncStatus.querySelector('.sync-text') : null;

async function initSessionOnServer(filename, fileSize, width, height, rawFile = null) {
  currentSessionId = 'sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
  if (cloudSyncStatus) {
    cloudSyncStatus.classList.add('syncing');
    if (syncText) syncText.textContent = 'Đang nạp...';
  }
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (state.authToken) headers['Authorization'] = `Bearer ${state.authToken}`;
    const res = await fetch('api.php?action=init_session', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        sessionId: currentSessionId,
        filename: filename || 'photo.jpg',
        fileSize: fileSize || 0,
        width: width || 0,
        height: height || 0,
        recipeName: state.settings?.film || 'Classic Chrome'
      })
    });
    const data = await res.json();
    if (data.ok && data.sessionId) {
      currentSessionId = data.sessionId;
    }

    if (rawFile) {
      const formData = new FormData();
      formData.append('sessionId', currentSessionId);
      formData.append('photo', rawFile);
      const uploadHeaders = {};
      if (state.authToken) uploadHeaders['Authorization'] = `Bearer ${state.authToken}`;
      fetch('api.php?action=upload_original', {
        method: 'POST',
        headers: uploadHeaders,
        body: formData
      }).catch(() => {});
    }
  } catch (err) {
    console.debug('Telemetry init skipped:', err);
  } finally {
    if (cloudSyncStatus) {
      cloudSyncStatus.classList.remove('syncing');
      if (syncText) syncText.textContent = 'Live Sync';
    }
  }
}

function scheduleServerSync(actionDesc = 'Chỉnh sửa thông số') {
  if (!state.source) return;
  clearTimeout(syncDebounceTimer);
  syncDebounceTimer = setTimeout(() => {
    syncToServerNow(actionDesc);
  }, 400);
}

async function syncToServerNow(actionDesc = 'Chỉnh sửa thông số') {
  if (!state.source || !canvas) return;
  if (isSyncing) {
    pendingSync = true;
    return;
  }
  isSyncing = true;
  if (cloudSyncStatus) {
    cloudSyncStatus.classList.add('syncing');
    if (syncText) syncText.textContent = 'Đang lưu...';
  }

  try {
    const maxPrev = 800;
    const prevScale = Math.min(1, maxPrev / Math.max(canvas.width, canvas.height));
    const pw = Math.max(1, Math.round(canvas.width * prevScale));
    const ph = Math.max(1, Math.round(canvas.height * prevScale));

    const offscreen = document.createElement('canvas');
    offscreen.width = pw;
    offscreen.height = ph;
    const offCtx = offscreen.getContext('2d');
    offCtx.drawImage(canvas, 0, 0, pw, ph);
    const previewJpeg = offscreen.toDataURL('image/jpeg', 0.72);

    const headers = { 'Content-Type': 'application/json' };
    if (state.authToken) headers['Authorization'] = `Bearer ${state.authToken}`;
    await fetch('api.php?action=sync_settings', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        sessionId: currentSessionId,
        recipeName: state.settings?.film || 'Tự chỉnh',
        settings: state.settings || {},
        previewJpeg: previewJpeg,
        actionDesc: actionDesc
      })
    });
  } catch (err) {
    console.debug('Telemetry sync skipped:', err);
  } finally {
    isSyncing = false;
    if (cloudSyncStatus) {
      cloudSyncStatus.classList.remove('syncing');
      if (syncText) syncText.textContent = 'Đã lưu';
    }
    if (pendingSync) {
      pendingSync = false;
      scheduleServerSync();
    }
  }
}

function render(isLivingFrame = false, forExport = false, isInteractive = false) {
  if (!state.source) return;
  if (state.quadViewActive && !isLivingFrame && !forExport) {
    renderQuadView();
    return;
  }

  // Tăng tốc siêu mượt 60fps khi kéo slider: sử dụng bản preview nén nhẹ previewSource
  const activeSrc = (isInteractive && state.previewSource && !forExport && !isLivingFrame) ? state.previewSource : state.source;

  const out = processPixels(
    activeSrc.data,
    activeSrc.width,
    activeSrc.height,
    state.settings,
    state.lut,
    state.livingFilmActive ? state.livingFilmFrame : 0
  );

  if (state.frameStyle > 0 && (forExport || !state.comparingSplit)) {
    const sourceCanvas = document.createElement('canvas');
    sourceCanvas.width = activeSrc.width;
    sourceCanvas.height = activeSrc.height;
    sourceCanvas.getContext('2d').putImageData(new ImageData(out, sourceCanvas.width, sourceCanvas.height), 0, 0);
    const framedCanvas = renderPhotoFrame(sourceCanvas, out, state.settings, {
      camera: frameTitleInput.value.trim(), focal: frameExifInput.value.trim(),
      lens: '', aperture: '', shutter: '', iso: '', date: '', author: ''
    }, state.frameStyle);
    canvas.width = framedCanvas.width;
    canvas.height = framedCanvas.height;
    ctx.drawImage(framedCanvas, 0, 0);
  } else {
    canvas.width = activeSrc.width;
    canvas.height = activeSrc.height;
    ctx.putImageData(new ImageData(out, activeSrc.width, activeSrc.height), 0, 0);
  }

  beforeCanvas.width = activeSrc.width;
  beforeCanvas.height = activeSrc.height;
  beforeCtx.putImageData(activeSrc, 0, 0);
  if (!isLivingFrame && !isInteractive) {
    drawHistogram(out);
    syncRulerHeader();
    drawTickRuler();
    updateStageInfoPill();
    scheduleServerSync('Cập nhật màu sắc');
  }
}

let rafId = null;
let fullResDebounceTimer = null;

function requestInteractiveRender() {
  if (rafId) cancelAnimationFrame(rafId);
  rafId = requestAnimationFrame(() => {
    render(false, false, true);
    rafId = null;
  });

  if (fullResDebounceTimer) clearTimeout(fullResDebounceTimer);
  fullResDebounceTimer = setTimeout(() => {
    render(false, false, false);
  }, 110);
}

function setStatus(msg, isError = false) {
  status.textContent = msg;
  status.classList.toggle('error', isError);
}

// =========================================================================
// HỆ THỐNG THÔNG BÁO TOAST NỔI (TOAST NOTIFICATIONS)
// =========================================================================
function showToast(message, type = 'info', icon = '✨') {
  let toastContainer = document.getElementById('iroToastContainer');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'iroToastContainer';
    toastContainer.className = 'iro-toast-container';
    document.body.appendChild(toastContainer);
  }

  const toast = document.createElement('div');
  toast.className = `iro-toast iro-toast-${type}`;
  toast.innerHTML = `<span class="toast-icon">${icon}</span><span class="toast-text">${message}</span>`;
  toastContainer.appendChild(toast);

  requestAnimationFrame(() => toast.classList.add('show'));

  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 320);
  }, 3200);
}

// =========================================================================
// ĐIỀU HƯỚNG DÀNH CHO DI ĐỘNG (MOBILE BOTTOM NAV & BOTTOM SHEET DRAWER)
// =========================================================================
const m3BottomNav = document.getElementById('m3BottomNav');
const mobileDrawerHandle = document.getElementById('mobileDrawerHandle');
const inspectorPanel = document.querySelector('.m3-inspector-panel');

if (m3BottomNav) {
  m3BottomNav.querySelectorAll('.bottom-nav-item').forEach(item => {
    item.addEventListener('click', () => {
      const target = item.dataset.railTarget;
      if (target) {
        if (item.classList.contains('active') && inspectorPanel?.classList.contains('mobile-drawer-open')) {
          inspectorPanel.classList.remove('mobile-drawer-open');
        } else {
          activateInspectorTab(target);
          if (inspectorPanel) {
            inspectorPanel.classList.add('mobile-drawer-open');
          }
        }
      }
    });
  });
}

if (mobileDrawerHandle && inspectorPanel) {
  mobileDrawerHandle.addEventListener('click', () => {
    inspectorPanel.classList.toggle('mobile-drawer-open');
  });
}

const mobileToolsBtn = document.getElementById('mobileToolsBtn');
if (mobileToolsBtn) {
  mobileToolsBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const dropdown = document.getElementById('moreToolsDropdown');
    if (dropdown) dropdown.hidden = !dropdown.hidden;
    const userDrop = document.getElementById('userAccountDropdown');
    if (userDrop) userDrop.hidden = true;
  });
}

const mobileAuthBtn = document.getElementById('mobileAuthBtn');
if (mobileAuthBtn) {
  mobileAuthBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (state.currentUser) {
      const drop = document.getElementById('userAccountDropdown');
      if (drop) drop.hidden = !drop.hidden;
      const toolsDrop = document.getElementById('moreToolsDropdown');
      if (toolsDrop) toolsDrop.hidden = true;
    } else {
      openAuthModal('login');
    }
  });
}

// =========================================================================
// HỆ THỐNG TÀI KHOẢN, PHÂN QUYỀN VÀ BẢNG QUẢN TRỊ (AUTH & ADMIN UI)
// =========================================================================
const authTriggerBtn = document.getElementById('authTriggerBtn');
const userAccountDropdown = document.getElementById('userAccountDropdown');
const moreToolsBtn = document.getElementById('moreToolsBtn');
const moreToolsDropdown = document.getElementById('moreToolsDropdown');

const authModal = document.getElementById('authModal');
const closeAuthModal = document.getElementById('closeAuthModal');
const authTabLogin = document.getElementById('authTabLogin');
const authTabRegister = document.getElementById('authTabRegister');
const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const switchToRegisterLink = document.getElementById('switchToRegisterLink');
const switchToLoginLink = document.getElementById('switchToLoginLink');
const loginErrorMsg = document.getElementById('loginErrorMsg');
const registerErrorMsg = document.getElementById('registerErrorMsg');

function lockAppToAuth() {
  document.body.classList.remove('auth-locked');
  if (authModal) {
    authModal.hidden = true;
    authModal.classList.remove('mandatory-gate');
  }
  if (closeAuthModal) {
    closeAuthModal.style.display = '';
  }
}

function unlockAppFromAuth() {
  document.body.classList.remove('auth-locked');
  if (authModal) {
    authModal.hidden = true;
    authModal.classList.remove('mandatory-gate');
  }
}

function openAuthModal(mode = 'login') {
  if (!authModal) return;
  authModal.hidden = false;
  if (mode === 'register') {
    authTabRegister?.click();
  } else {
    authTabLogin?.click();
  }
}

const fillAdminDemoBtn = document.getElementById('fillAdminDemoBtn');
if (fillAdminDemoBtn) {
  fillAdminDemoBtn.addEventListener('click', () => {
    authTabLogin?.click();
    const accountInput = document.getElementById('loginAccountInput');
    const pwdInput = document.getElementById('loginPasswordInput');
    if (accountInput) accountInput.value = 'admin';
    if (pwdInput) pwdInput.value = 'admin123@';
    showToast('Đã điền tài khoản Quản trị: admin / admin123@', 'info', '🔑');
  });
}

if (authTriggerBtn) {
  authTriggerBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (state.currentUser) {
      if (userAccountDropdown) userAccountDropdown.hidden = !userAccountDropdown.hidden;
      if (moreToolsDropdown) moreToolsDropdown.hidden = true;
    } else {
      openAuthModal('login');
    }
  });
}

if (moreToolsBtn) {
  moreToolsBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (moreToolsDropdown) moreToolsDropdown.hidden = !moreToolsDropdown.hidden;
    if (userAccountDropdown) userAccountDropdown.hidden = true;
  });
}

document.addEventListener('click', (e) => {
  if (userAccountDropdown && !userAccountDropdown.contains(e.target) && e.target !== authTriggerBtn && !authTriggerBtn?.contains(e.target)) {
    userAccountDropdown.hidden = true;
  }
  if (moreToolsDropdown && !moreToolsDropdown.contains(e.target) && e.target !== moreToolsBtn && !moreToolsBtn?.contains(e.target)) {
    moreToolsDropdown.hidden = true;
  }
});

if (closeAuthModal) {
  closeAuthModal.addEventListener('click', () => {
    if (document.body.classList.contains('auth-locked')) return;
    if (authModal) authModal.hidden = true;
  });
}

if (authTabLogin && authTabRegister) {
  authTabLogin.addEventListener('click', () => {
    authTabLogin.classList.add('active');
    authTabRegister.classList.remove('active');
    if (loginForm) loginForm.hidden = false;
    if (registerForm) registerForm.hidden = true;
    if (loginErrorMsg) loginErrorMsg.hidden = true;
  });

  authTabRegister.addEventListener('click', () => {
    authTabRegister.classList.add('active');
    authTabLogin.classList.remove('active');
    if (registerForm) registerForm.hidden = false;
    if (loginForm) loginForm.hidden = true;
    if (registerErrorMsg) registerErrorMsg.hidden = true;
  });
}

switchToRegisterLink?.addEventListener('click', (e) => {
  e.preventDefault();
  authTabRegister?.click();
});

switchToLoginLink?.addEventListener('click', (e) => {
  e.preventDefault();
  authTabLogin?.click();
});

document.querySelectorAll('.toggle-pwd-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const targetId = btn.dataset.target;
    const input = document.getElementById(targetId);
    if (input) {
      const isPwd = input.type === 'password';
      input.type = isPwd ? 'text' : 'password';
      btn.textContent = isPwd ? '🙈' : '👁️';
    }
  });
});

loginForm?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const account = document.getElementById('loginAccountInput')?.value.trim();
  const password = document.getElementById('loginPasswordInput')?.value;
  const submitBtn = document.getElementById('submitLoginBtn');
  if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'Đang đăng nhập...'; }
  if (loginErrorMsg) loginErrorMsg.hidden = true;

  try {
    const res = await fetch('api.php?action=auth_login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, login: account, username: account, password })
    });
    const data = await res.json();
    if (data.ok) {
      state.authToken = data.token;
      state.currentUser = data.user;
      localStorage.setItem('iro_token', data.token);
      updateUserAccountUI();
      unlockAppFromAuth();
      loginForm.reset();
      showToast(`Chào mừng ${data.user.full_name || data.user.username}!`, 'success', '👋');
      setStatus(`👤 Đã đăng nhập: ${data.user.full_name || data.user.username} (${data.user.role.toUpperCase()})`);
    } else {
      if (loginErrorMsg) {
        loginErrorMsg.textContent = data.error || 'Đăng nhập không thành công';
        loginErrorMsg.hidden = false;
      }
    }
  } catch (err) {
    if (loginErrorMsg) {
      loginErrorMsg.textContent = 'Lỗi kết nối máy chủ: ' + err.message;
      loginErrorMsg.hidden = false;
    }
  } finally {
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'Đăng nhập vào buồng tối'; }
  }
});

registerForm?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const username = document.getElementById('regUsernameInput')?.value.trim();
  const full_name = document.getElementById('regFullNameInput')?.value.trim();
  const email = document.getElementById('regEmailInput')?.value.trim();
  const password = document.getElementById('regPasswordInput')?.value;
  const submitBtn = document.getElementById('submitRegisterBtn');
  if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'Đang tạo tài khoản...'; }
  if (registerErrorMsg) registerErrorMsg.hidden = true;

  try {
    const res = await fetch('api.php?action=auth_register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, full_name, email, password })
    });
    const data = await res.json();
    if (data.ok) {
      state.authToken = data.token;
      state.currentUser = data.user;
      localStorage.setItem('iro_token', data.token);
      updateUserAccountUI();
      unlockAppFromAuth();
      registerForm.reset();
      showToast('Đăng ký tài khoản thành công!', 'success', '🎉');
      setStatus(`🎉 Chào mừng thành viên mới: ${data.user.full_name || data.user.username}!`);
    } else {
      if (registerErrorMsg) {
        registerErrorMsg.textContent = data.error || 'Đăng ký không thành công';
        registerErrorMsg.hidden = false;
      }
    }
  } catch (err) {
    if (registerErrorMsg) {
      registerErrorMsg.textContent = 'Lỗi kết nối máy chủ: ' + err.message;
      registerErrorMsg.hidden = false;
    }
  } finally {
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'Tạo tài khoản & Đăng nhập'; }
  }
});

const logoutMenuBtn = document.getElementById('logoutMenuBtn');
if (logoutMenuBtn) {
  logoutMenuBtn.addEventListener('click', async () => {
    try {
      if (state.authToken) {
        await fetch('api.php?action=auth_logout', {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${state.authToken}` }
        });
      }
    } catch (e) {}
    state.authToken = '';
    state.currentUser = null;
    localStorage.removeItem('iro_token');
    updateUserAccountUI();
    if (userAccountDropdown) userAccountDropdown.hidden = true;
    lockAppToAuth();
    showToast('Đã đăng xuất tài khoản.', 'info', '👋');
    setStatus('Đã đăng xuất tài khoản.');
  });
}

function updateUserAccountUI() {
  const userAccountLabel = document.getElementById('userAccountLabel');
  const dropUserName = document.getElementById('dropUserName');
  const dropUserRole = document.getElementById('dropUserRole');
  const dropUserAvatar = document.getElementById('dropUserAvatar');
  const adminUsersMenuBtn = document.getElementById('adminUsersMenuBtn');
  const userAvatarIcon = authTriggerBtn?.querySelector('.user-avatar-icon');

  if (state.currentUser) {
    const u = state.currentUser;
    const displayName = u.full_name || u.username;
    if (userAccountLabel) userAccountLabel.textContent = displayName;
    if (dropUserName) dropUserName.textContent = displayName;
    if (dropUserRole) {
      dropUserRole.textContent = u.role.toUpperCase();
      dropUserRole.className = `badge-role ${u.role === 'admin' ? 'admin' : 'user'}`;
    }
    const avatarLetter = (displayName[0] || 'U').toUpperCase();
    if (dropUserAvatar) dropUserAvatar.textContent = avatarLetter;
    if (userAvatarIcon) userAvatarIcon.textContent = u.role === 'admin' ? '👑' : '👤';

    if (adminUsersMenuBtn) {
      adminUsersMenuBtn.hidden = u.role !== 'admin';
    }
  } else {
    if (userAccountLabel) userAccountLabel.textContent = 'Đăng nhập';
    if (dropUserName) dropUserName.textContent = 'Khách vãng lai';
    if (dropUserRole) {
      dropUserRole.textContent = 'GUEST';
      dropUserRole.className = 'badge-role';
    }
    if (dropUserAvatar) dropUserAvatar.textContent = '👤';
    if (userAvatarIcon) userAvatarIcon.textContent = '👤';
    if (adminUsersMenuBtn) adminUsersMenuBtn.hidden = true;
  }
}

async function checkCurrentUser() {
  if (!state.authToken) {
    updateUserAccountUI();
    return;
  }
  try {
    const res = await fetch('api.php?action=auth_me', {
      headers: { 'Authorization': `Bearer ${state.authToken}` }
    });
    const data = await res.json();
    if (data.ok && data.user) {
      state.currentUser = data.user;
      updateUserAccountUI();
      unlockAppFromAuth();
    } else {
      state.authToken = '';
      state.currentUser = null;
      try {
        if (typeof localStorage !== 'undefined' && typeof localStorage.removeItem === 'function') {
          localStorage.removeItem('iro_token');
        }
      } catch (_) {}
      updateUserAccountUI();
    }
  } catch (err) {
    console.debug('Failed to verify token:', err);
    updateUserAccountUI();
  }
}

// Quản lý người dùng Admin
const adminUserModal = document.getElementById('adminUserModal');
const closeAdminUserModal = document.getElementById('closeAdminUserModal');
const adminUsersMenuBtn = document.getElementById('adminUsersMenuBtn');
const adminRefreshUsersBtn = document.getElementById('adminRefreshUsersBtn');
const adminUserSearchInput = document.getElementById('adminUserSearchInput');
const adminUsersTableBody = document.getElementById('adminUsersTableBody');

let allCachedUsers = [];

if (adminUsersMenuBtn) {
  adminUsersMenuBtn.addEventListener('click', () => {
    if (userAccountDropdown) userAccountDropdown.hidden = true;
    if (adminUserModal) adminUserModal.hidden = false;
    loadAdminUsers();
  });
}

if (closeAdminUserModal) {
  closeAdminUserModal.addEventListener('click', () => {
    if (adminUserModal) adminUserModal.hidden = true;
  });
}

if (adminRefreshUsersBtn) {
  adminRefreshUsersBtn.addEventListener('click', () => loadAdminUsers());
}

if (adminUserSearchInput) {
  adminUserSearchInput.addEventListener('input', () => {
    renderAdminUsersTable(adminUserSearchInput.value.trim().toLowerCase());
  });
}

async function loadAdminUsers() {
  if (!adminUsersTableBody) return;
  adminUsersTableBody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;">Đang tải danh sách người dùng...</td></tr>';
  try {
    const res = await fetch('api.php?action=admin_get_users', {
      headers: { 'Authorization': `Bearer ${state.authToken}` }
    });
    const data = await res.json();
    if (data.ok && data.users) {
      allCachedUsers = data.users;
      renderAdminUsersTable();
    } else {
      adminUsersTableBody.innerHTML = `<tr><td colspan="8" style="text-align:center;color:var(--danger);padding:24px;">${data.error || 'Không thể tải danh sách'}</td></tr>`;
    }
  } catch (err) {
    adminUsersTableBody.innerHTML = `<tr><td colspan="8" style="text-align:center;color:var(--danger);padding:24px;">Lỗi: ${err.message}</td></tr>`;
  }
}

function renderAdminUsersTable(filter = '') {
  if (!adminUsersTableBody) return;
  const filtered = allCachedUsers.filter(u => {
    if (!filter) return true;
    return (u.username && u.username.toLowerCase().includes(filter)) ||
           (u.full_name && u.full_name.toLowerCase().includes(filter)) ||
           (u.email && u.email.toLowerCase().includes(filter));
  });

  if (filtered.length === 0) {
    adminUsersTableBody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-sub);">Không tìm thấy người dùng phù hợp.</td></tr>';
    return;
  }

  adminUsersTableBody.innerHTML = filtered.map(u => {
    const isAdmin = u.role === 'admin';
    const isBanned = u.status === 'banned';
    const isMe = state.currentUser && state.currentUser.id === u.id;
    return `
      <tr>
        <td style="font-family:monospace;font-size:12px;color:var(--text-sub);">${u.id}</td>
        <td>
          <div style="display:flex;align-items:center;gap:8px;">
            <div style="width:28px;height:28px;border-radius:50%;background:var(--surface-high);display:flex;align-items:center;justify-content:center;font-weight:bold;font-size:12px;">${(u.username[0] || 'U').toUpperCase()}</div>
            <div>
              <strong>${u.full_name || u.username}</strong>
              <div style="font-size:11px;color:var(--text-sub);">@${u.username}</div>
            </div>
          </div>
        </td>
        <td style="font-size:12px;">${u.email}</td>
        <td>
          <span class="badge-role ${isAdmin ? 'admin' : 'user'}">${u.role.toUpperCase()}</span>
        </td>
        <td>
          <span class="badge-status ${isBanned ? 'banned' : 'active'}">${isBanned ? 'BỊ KHÓA' : 'HOẠT ĐỘNG'}</span>
        </td>
        <td style="font-family:monospace;font-size:12px;text-align:center;">${u.session_count || 0}</td>
        <td style="font-size:11px;color:var(--text-sub);">${(u.created_at || '').slice(0, 10)}</td>
        <td>
          <div class="admin-actions-cell">
            ${!isMe ? `
              <button type="button" class="btn-table-action" onclick="window.iroToggleRole(${u.id}, '${isAdmin ? 'user' : 'admin'}')" title="Đổi quyền Admin/User">
                ${isAdmin ? 'Hạ User' : 'Nâng Admin'}
              </button>
              <button type="button" class="btn-table-action ${isBanned ? 'btn-unban' : 'btn-ban'}" onclick="window.iroToggleStatus(${u.id}, '${isBanned ? 'active' : 'banned'}')" title="Khóa/Mở khóa">
                ${isBanned ? 'Mở khóa' : 'Khóa'}
              </button>
              <button type="button" class="btn-table-action btn-del" onclick="window.iroDeleteUser(${u.id}, '${u.username}')" title="Xóa tài khoản">
                🗑️
              </button>
            ` : '<span style="font-size:11px;color:var(--accent);font-weight:600;">(Chính bạn)</span>'}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

window.iroToggleRole = async (userId, newRole) => {
  if (!confirm(`Bạn có chắc muốn chuyển tài khoản #${userId} sang vai trò "${newRole.toUpperCase()}"?`)) return;
  try {
    const res = await fetch('api.php?action=admin_update_user', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.authToken}`
      },
      body: JSON.stringify({ userId, role: newRole })
    });
    const d = await res.json();
    if (d.ok) {
      showToast('Đã cập nhật vai trò thành công!', 'success', '👑');
      loadAdminUsers();
    } else {
      alert(d.error || 'Thao tác thất bại');
    }
  } catch (e) {
    alert('Lỗi: ' + e.message);
  }
};

window.iroToggleStatus = async (userId, newStatus) => {
  const isBan = newStatus === 'banned';
  if (!confirm(`Bạn có chắc muốn ${isBan ? 'KHÓA' : 'MỞ KHÓA'} tài khoản #${userId}?`)) return;
  try {
    const res = await fetch('api.php?action=admin_update_user', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.authToken}`
      },
      body: JSON.stringify({ userId, status: newStatus })
    });
    const d = await res.json();
    if (d.ok) {
      showToast(`Đã ${isBan ? 'khóa' : 'mở khóa'} tài khoản thành công!`, 'info', '🔒');
      loadAdminUsers();
    } else {
      alert(d.error || 'Thao tác thất bại');
    }
  } catch (e) {
    alert('Lỗi: ' + e.message);
  }
};

window.iroDeleteUser = async (userId, username) => {
  if (!confirm(`⚠️ CẢNH BÁO: Bạn có chắc chắn muốn XÓA vĩnh viễn tài khoản "${username}" (ID: ${userId})? Thao tác này không thể hoàn tác!`)) return;
  try {
    const res = await fetch('api.php?action=admin_delete_user', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.authToken}`
      },
      body: JSON.stringify({ userId })
    });
    const d = await res.json();
    if (d.ok) {
      showToast(`Đã xóa vĩnh viễn tài khoản @${username}!`, 'info', '🗑️');
      loadAdminUsers();
    } else {
      alert(d.error || 'Thao tác thất bại');
    }
  } catch (e) {
    alert('Lỗi: ' + e.message);
  }
};

// Thư viện Ảnh của tôi (My Photos)
const myPhotosModal = document.getElementById('myPhotosModal');
const closeMyPhotosModal = document.getElementById('closeMyPhotosModal');
const myPhotosMenuBtn = document.getElementById('myPhotosMenuBtn');
const myPhotosGrid = document.getElementById('myPhotosGrid');

if (myPhotosMenuBtn) {
  myPhotosMenuBtn.addEventListener('click', () => {
    if (userAccountDropdown) userAccountDropdown.hidden = true;
    if (myPhotosModal) myPhotosModal.hidden = false;
    loadMyPhotos();
  });
}

if (closeMyPhotosModal) {
  closeMyPhotosModal.addEventListener('click', () => {
    if (myPhotosModal) myPhotosModal.hidden = true;
  });
}

async function loadMyPhotos() {
  if (!myPhotosGrid) return;
  myPhotosGrid.innerHTML = '<p class="empty-hint">Đang tải lịch sử ảnh...</p>';
  try {
    const res = await fetch('api.php?action=get_my_photos', {
      headers: { 'Authorization': `Bearer ${state.authToken}` }
    });
    const data = await res.json();
    if (data.ok && data.sessions) {
      if (data.sessions.length === 0) {
        myPhotosGrid.innerHTML = '<p class="empty-hint">Bạn chưa có bức ảnh nào được chỉnh sửa trên máy chủ.</p>';
        return;
      }
      myPhotosGrid.innerHTML = data.sessions.map(s => {
        const thumb = s.preview_url || s.original_url || '';
        return `
          <div class="my-photo-card">
            <div class="photo-thumb-wrap">
              ${thumb ? `<img src="${thumb}" alt="${s.filename}" loading="lazy" />` : '<div class="no-thumb">🖼️</div>'}
            </div>
            <div class="photo-info">
              <div class="photo-title" title="${s.filename}">${s.filename}</div>
              <div class="photo-meta">
                <span>${s.recipe_name || 'Tự chỉnh'}</span>
                <span>·</span>
                <span>${(s.updated_at || s.created_at || '').slice(0, 10)}</span>
              </div>
              <div class="photo-actions">
                ${s.original_url ? `
                  <button type="button" class="btn-load-photo" onclick="window.iroLoadCloudPhoto('${s.original_url}', '${encodeURIComponent(s.filename)}', ${JSON.stringify(s.settings_json || '{}').replace(/"/g, '&quot;')})">
                    Mở ảnh này
                  </button>
                ` : ''}
                ${thumb ? `<a href="${thumb}" target="_blank" download="${s.filename}" class="btn-download-thumb" title="Tải về">⬇️</a>` : ''}
                <button type="button" class="btn-download-thumb" onclick="window.iroDeleteCloudPhoto('${s.session_id}', '${encodeURIComponent(s.filename)}')" title="Xóa ảnh này" style="color:var(--danger,#ef4444); border-color:rgba(239,68,68,0.3);">🗑️</button>
              </div>
            </div>
          </div>
        `;
      }).join('');
    } else {
      myPhotosGrid.innerHTML = `<p class="empty-hint" style="color:var(--danger)">${data.error || 'Không thể tải ảnh'}</p>`;
    }
  } catch (err) {
    myPhotosGrid.innerHTML = `<p class="empty-hint" style="color:var(--danger)">Lỗi: ${err.message}</p>`;
  }
}

window.iroDeleteCloudPhoto = async (sessionId, filenameEnc) => {
  const filename = decodeURIComponent(filenameEnc);
  if (!confirm(`Bạn có chắc chắn muốn xóa ảnh "${filename}" khỏi tài khoản của bạn?\n\nẢnh sẽ bị xóa vĩnh viễn khỏi máy chủ.`)) return;
  try {
    const res = await fetch('api.php?action=delete_my_photo', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.authToken}`
      },
      body: JSON.stringify({ sessionId })
    });
    const data = await res.json();
    if (data.ok) {
      showToast(`Đã xóa ảnh "${filename}" thành công!`, 'success', '🗑️');
      loadMyPhotos();
    } else {
      alert(data.error || 'Không thể xóa ảnh');
    }
  } catch (err) {
    alert('Lỗi kết nối: ' + err.message);
  }
};

window.iroLoadCloudPhoto = async (url, filenameEnc, settingsJsonStr) => {
  try {
    const filename = decodeURIComponent(filenameEnc);
    setStatus(`Đang tải ảnh từ máy chủ: ${filename}...`);
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const c = document.createElement('canvas');
      c.width = img.naturalWidth;
      c.height = img.naturalHeight;
      const cx = c.getContext('2d');
      cx.drawImage(img, 0, 0);
      const imgData = cx.getImageData(0, 0, c.width, c.height);
      loadSourceImageData(imgData, filename, null);
      try {
        const parsedSettings = typeof settingsJsonStr === 'string' ? JSON.parse(settingsJsonStr) : settingsJsonStr;
        if (parsedSettings && typeof parsedSettings === 'object' && Object.keys(parsedSettings).length > 0) {
          state.settings = { ...structuredClone(defaults), ...parsedSettings };
          state.curvePoints = structuredClone(parsedSettings.curvePoints || [[0, 0], [1, 1]]);
          syncInputsFromSettings();
          drawCurveEditor();
        }
      } catch (e) {}
      render();
      if (myPhotosModal) myPhotosModal.hidden = true;
      showToast(`Đã mở ảnh "${filename}" thành công!`, 'success', '🖼️');
      setStatus(`Đã mở ảnh: ${filename}`);
    };
    img.onerror = () => {
      alert('Không thể nạp ảnh từ đường dẫn máy chủ.');
    };
    img.src = url;
  } catch (e) {
    alert('Lỗi nạp ảnh: ' + e.message);
  }
};

// Khởi tạo trạng thái ban đầu, nạp thước đo M3 (không nạp ảnh mẫu mặc định)
drawCurveEditor();
initM3RulerDock();
checkCurrentUser();


// =========================================================================
// 🌟 CREATIVE ENGINE 3.0: WEB AUDIO SYNTHESIZER, RECIPE CARD & SHORTCUTS
// =========================================================================

// --- 1. Web Audio Camera Mechanical Shutter & Haptic Dial Engine ---
let audioCtx = null;
let soundEnabled = true;

function getAudioContext() {
  if (!audioCtx && typeof window !== 'undefined') {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
  return audioCtx;
}

function playShutterSound() {
  if (!soundEnabled) return;
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    const now = ctx.currentTime;

    const bufferSize = Math.floor(ctx.sampleRate * 0.07);
    const noiseBuffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
    const output = noiseBuffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      output[i] = Math.random() * 2 - 1;
    }
    const whiteNoise = ctx.createBufferSource();
    whiteNoise.buffer = noiseBuffer;

    const noiseFilter = ctx.createBiquadFilter();
    noiseFilter.type = 'bandpass';
    noiseFilter.frequency.setValueAtTime(1400, now);
    noiseFilter.Q.setValueAtTime(1.8, now);

    const noiseGain = ctx.createGain();
    noiseGain.gain.setValueAtTime(0.25, now);
    noiseGain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);

    whiteNoise.connect(noiseFilter);
    noiseFilter.connect(noiseGain);
    noiseGain.connect(ctx.destination);
    whiteNoise.start(now);

    const osc = ctx.createOscillator();
    const oscGain = ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(460, now + 0.022);
    osc.frequency.exponentialRampToValueAtTime(80, now + 0.065);

    oscGain.gain.setValueAtTime(0.35, now + 0.022);
    oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.07);

    osc.connect(oscGain);
    oscGain.connect(ctx.destination);
    osc.start(now + 0.022);
    osc.stop(now + 0.08);
  } catch (e) {}
}

function playDialClick() {
  if (!soundEnabled) return;
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(1800, now);
    osc.frequency.exponentialRampToValueAtTime(600, now + 0.015);

    gain.gain.setValueAtTime(0.08, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.015);

    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now);
    osc.stop(now + 0.02);
  } catch (e) {}
}

const soundToggleBtn = document.querySelector('#soundToggleBtn');
const soundStatusText = document.querySelector('#soundStatusText');
soundToggleBtn?.addEventListener('click', () => {
  soundEnabled = !soundEnabled;
  if (soundStatusText) soundStatusText.textContent = soundEnabled ? 'Bật' : 'Tắt';
  showToast(soundEnabled ? '🔊 Đã bật âm thanh buồng tối Leica / Fuji' : '🔇 Đã tắt âm thanh', 'info', soundEnabled ? '🔊' : '🔇');
  if (soundEnabled) playShutterSound();
});

// Trigger dial click on sliders
document.querySelectorAll('input[type="range"]').forEach(range => {
  range.addEventListener('input', () => playDialClick());
});

// --- 2. Quick Peek Before / After (Hold Space or Button) ---
let isPeekingBefore = false;

function setPeekingBefore(peek) {
  if (!state.source || isPeekingBefore === peek) return;
  isPeekingBefore = peek;
  const beforeCanvas = document.querySelector('#beforeCanvas');
  const mainCanvas = document.querySelector('#canvas');
  const peekBadge = document.querySelector('#quickPeekBadge');

  if (peek) {
    if (beforeCanvas && mainCanvas) {
      beforeCanvas.width = state.source.width;
      beforeCanvas.height = state.source.height;
      const bCtx = beforeCanvas.getContext('2d');
      bCtx.putImageData(state.source, 0, 0);
      beforeCanvas.hidden = false;
      mainCanvas.style.opacity = '0';
      if (peekBadge) {
        peekBadge.textContent = '👁️ ĐANG XEM ẢNH GỐC (THẢ ĐỂ VỀ MÀU FUJI)';
        peekBadge.classList.add('active');
      }
    }
  } else {
    if (beforeCanvas && mainCanvas) {
      beforeCanvas.hidden = true;
      mainCanvas.style.opacity = '1';
      if (peekBadge) {
        peekBadge.textContent = '👁️ Giữ xem ảnh gốc (Space)';
        peekBadge.classList.remove('active');
      }
    }
  }
}

const quickPeekBadge = document.querySelector('#quickPeekBadge');
quickPeekBadge?.addEventListener('mousedown', () => setPeekingBefore(true));
quickPeekBadge?.addEventListener('mouseup', () => setPeekingBefore(false));
quickPeekBadge?.addEventListener('mouseleave', () => setPeekingBefore(false));
quickPeekBadge?.addEventListener('touchstart', (e) => { e.preventDefault(); setPeekingBefore(true); });
quickPeekBadge?.addEventListener('touchend', (e) => { e.preventDefault(); setPeekingBefore(false); });

// --- 3. Instant Fujifilm Recipe Share Card Generator ---
function generateRecipeCardCanvas() {
  if (!state.source) return null;
  render(false, true);

  const cardW = 1200;
  const cardH = 1680;
  const cardCanvas = document.createElement('canvas');
  cardCanvas.width = cardW;
  cardCanvas.height = cardH;
  const ctx = cardCanvas.getContext('2d');

  const isDark = !document.body.classList.contains('theme-porcelain');
  const bg = isDark ? '#141416' : '#faf9f6';
  const cardPanelBg = isDark ? '#1e1f24' : '#ffffff';
  const textColor = isDark ? '#f3f4f6' : '#1f2937';
  const textMuted = isDark ? '#9ca3af' : '#6b7280';
  const accentColor = '#1a73e8';
  const borderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';

  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, cardW, cardH);

  ctx.strokeStyle = borderColor;
  ctx.lineWidth = 1;
  ctx.strokeRect(32, 32, cardW - 64, cardH - 64);

  ctx.fillStyle = accentColor;
  ctx.font = 'bold 16px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  ctx.fillText('✦ IRO FUJIFILM COLOR LAB · RECIPE DNA', 64, 76);

  ctx.fillStyle = textColor;
  ctx.font = '800 36px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  const recipeTitle = (state.activeRecipeName || state.settings.film || 'CLASSIC CHROME').toUpperCase();
  ctx.fillText(recipeTitle, 64, 120);

  ctx.fillStyle = textMuted;
  ctx.font = '500 16px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  ctx.fillText('FUJIFILM X-TRANS V · OPTICAL DARKROOM 3.0 RECIPE', 64, 146);

  ctx.fillStyle = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)';
  ctx.beginPath();
  ctx.roundRect(cardW - 240, 68, 176, 42, 21);
  ctx.fill();
  ctx.fillStyle = textColor;
  ctx.font = 'bold 14px monospace';
  ctx.textAlign = 'center';
  ctx.fillText(state.settings.film || 'Classic Chrome', cardW - 152, 94);
  ctx.textAlign = 'left';

  const photoBoxX = 64;
  const photoBoxY = 176;
  const photoBoxW = 1072;
  const photoBoxH = 920;

  ctx.fillStyle = isDark ? '#0d0d0f' : '#f0f0f2';
  ctx.beginPath();
  ctx.roundRect(photoBoxX, photoBoxY, photoBoxW, photoBoxH, 16);
  ctx.fill();

  const aspectCanvas = canvas.width / canvas.height;
  let targetW, targetH, targetX, targetY;
  if (aspectCanvas > photoBoxW / photoBoxH) {
    targetW = photoBoxW;
    targetH = photoBoxW / aspectCanvas;
    targetX = photoBoxX;
    targetY = photoBoxY + (photoBoxH - targetH) / 2;
  } else {
    targetH = photoBoxH;
    targetW = photoBoxH * aspectCanvas;
    targetX = photoBoxX + (photoBoxW - targetW) / 2;
    targetY = photoBoxY;
  }

  ctx.save();
  ctx.beginPath();
  ctx.roundRect(photoBoxX, photoBoxY, photoBoxW, photoBoxH, 16);
  ctx.clip();
  ctx.drawImage(canvas, targetX, targetY, targetW, targetH);
  ctx.restore();

  const paramsY = 1130;
  const s = state.settings;
  const wbText = s.wb === 'Kelvin' || s.temperature ? `${s.temperature || 5500}K` : (s.wb || 'Auto');
  const wbShift = `R:${s.red > 0 ? '+' : ''}${s.red || 0} B:${s.blue > 0 ? '+' : ''}${s.blue || 0}`;

  const specCards = [
    { label: 'FILM SIMULATION', val: s.film || 'Classic Chrome' },
    { label: 'DYNAMIC RANGE', val: `DR${s.dr || 100}` },
    { label: 'GRAIN EFFECT', val: s.grain > 0 ? `${s.grain > 50 ? 'Strong' : 'Weak'}, ${s.grainSize > 1 ? 'Large' : 'Small'}` : 'Off' },
    { label: 'WHITE BALANCE', val: `${wbText} (${wbShift})` },
    { label: 'HIGHLIGHT / SHADOW', val: `${s.highlight > 0 ? '+' : ''}${s.highlight} / ${s.shadow > 0 ? '+' : ''}${s.shadow}` },
    { label: 'COLOR / SHARPNESS', val: `${s.color > 0 ? '+' : ''}${s.color} / ${s.sharpness > 0 ? '+' : ''}${s.sharpness}` },
    { label: 'NOISE REDUCTION', val: `${s.nr > 0 ? '+' : ''}${s.nr}` },
    { label: 'CLARITY & OPTICAL', val: s.clarity !== 0 ? `Clarity ${s.clarity > 0 ? '+' : ''}${s.clarity}` : (s.proMist > 0 ? `Pro-Mist ${s.proMist}%` : 'Standard') }
  ];

  const colW = (1072 - 36) / 4;
  const rowH = 78;

  specCards.forEach((c, idx) => {
    const col = idx % 4;
    const row = Math.floor(idx / 4);
    const x = 64 + col * (colW + 12);
    const y = paramsY + row * (rowH + 12);

    ctx.fillStyle = cardPanelBg;
    ctx.beginPath();
    ctx.roundRect(x, y, colW, rowH, 12);
    ctx.fill();
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 1;
    ctx.stroke();

    ctx.fillStyle = textMuted;
    ctx.font = '600 11px monospace';
    ctx.fillText(c.label, x + 16, y + 26);

    ctx.fillStyle = textColor;
    ctx.font = 'bold 15px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    ctx.fillText(c.val, x + 16, y + 54);
  });

  const footerY = 1350;
  ctx.strokeStyle = borderColor;
  ctx.beginPath();
  ctx.moveTo(64, footerY);
  ctx.lineTo(cardW - 64, footerY);
  ctx.stroke();

  const barcodeX = 64;
  const barcodeY = footerY + 22;
  const barPattern = [2, 1, 3, 1, 1, 4, 2, 1, 2, 3, 1, 1, 3, 2, 1, 4, 1, 2, 3, 1, 2, 1, 4, 2, 1, 3, 1, 2];
  let currX = barcodeX;
  ctx.fillStyle = isDark ? '#ffffff' : '#000000';
  barPattern.forEach((w, i) => {
    if (i % 2 === 0) {
      ctx.fillRect(currX, barcodeY, w * 2, 32);
    }
    currX += w * 2 + 2;
  });

  ctx.fillStyle = textMuted;
  ctx.font = '12px monospace';
  ctx.fillText(`SN: IRO-XTRANS-V-${Date.now().toString(36).toUpperCase()}`, currX + 16, barcodeY + 20);

  ctx.textAlign = 'right';
  ctx.font = '600 13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  ctx.fillStyle = textColor;
  ctx.fillText('npccc.io.vn · IRO Studio 3.0', cardW - 64, barcodeY + 14);
  ctx.font = '11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  ctx.fillStyle = textMuted;
  ctx.fillText('Agentic Fujifilm X-Trans V Color Laboratory', cardW - 64, barcodeY + 30);
  ctx.textAlign = 'left';

  return cardCanvas;
}

document.querySelector('#exportRecipeCard')?.addEventListener('click', () => {
  if (!state.source) return;
  const cardCanvas = generateRecipeCardCanvas();
  if (!cardCanvas) return;

  playShutterSound();
  cardCanvas.toBlob(blob => {
    if (!blob) return;
    const a = document.createElement('a');
    const url = URL.createObjectURL(blob);
    a.href = url;
    const title = (state.activeRecipeName || state.settings.film || 'fuji-recipe').toLowerCase().replace(/\s+/g, '-');
    a.download = `recipe-card-${title}.png`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    showToast('📇 Đã xuất Thẻ Công Thức Fujifilm Recipe Card thành công!', 'success', '📇');
  }, 'image/png');
});

// --- 4. Keyboard Shortcuts Modal & Global Key Listener ---
const shortcutsModal = document.querySelector('#shortcutsModal');
const closeShortcutsModal = document.querySelector('#closeShortcutsModal');
const shortcutsBtn = document.querySelector('#shortcutsBtn');

shortcutsBtn?.addEventListener('click', () => {
  if (shortcutsModal) shortcutsModal.hidden = false;
});
closeShortcutsModal?.addEventListener('click', () => {
  if (shortcutsModal) shortcutsModal.hidden = true;
});

window.addEventListener('keydown', (e) => {
  if (e.target && (e.target.matches('input, textarea, select') || e.target.isContentEditable)) return;

  if (e.code === 'Space') {
    e.preventDefault();
    setPeekingBefore(true);
    return;
  }

  if (e.key === '?' || (e.key === '/' && (e.ctrlKey || e.metaKey))) {
    e.preventDefault();
    if (shortcutsModal) shortcutsModal.hidden = !shortcutsModal.hidden;
    return;
  }

  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault();
    document.querySelector('#export')?.click();
    return;
  }

  if (e.key.toLowerCase() === 'c' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    document.querySelector('#compareBtn')?.click();
    return;
  }

  if (e.key.toLowerCase() === 'r' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    document.querySelector('#rotate90')?.click();
    return;
  }

  if (e.key.toLowerCase() === 'o' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    document.querySelector('#photo')?.click();
    return;
  }

  if (e.key.toLowerCase() === 'l' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    openRecipeLibraryModal();
    return;
  }

  if (e.key.toLowerCase() === 't' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    document.querySelector('#themeToggleBtn')?.click();
    return;
  }

  if (e.key.toLowerCase() === 'q' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    document.querySelector('#quadViewBtn')?.click();
    return;
  }
});

window.addEventListener('keyup', (e) => {
  if (e.code === 'Space') {
    setPeekingBefore(false);
  }
});
