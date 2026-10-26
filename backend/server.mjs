import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 5000);
const HOST = process.env.HOST || '0.0.0.0';

const DATA_DIR = path.join(__dirname, 'data');
const UPLOAD_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const DB_PATH = path.join(DATA_DIR, 'iro.db');

let dbRun, dbGet, dbAll;
try {
  const sqlite3Pkg = (await import('sqlite3')).default;
  const sqlite3 = sqlite3Pkg.verbose();
  const db = new sqlite3.Database(DB_PATH);
  dbRun = (sql, params = []) => new Promise((resolve, reject) => {
    db.run(sql, params, function (err) {
      if (err) reject(err);
      else resolve({ lastID: this.lastID, changes: this.changes });
    });
  });
  dbGet = (sql, params = []) => new Promise((resolve, reject) => {
    db.get(sql, params, (err, row) => {
      if (err) reject(err);
      else resolve(row || null);
    });
  });
  dbAll = (sql, params = []) => new Promise((resolve, reject) => {
    db.all(sql, params, (err, rows) => {
      if (err) reject(err);
      else resolve(rows || []);
    });
  });
} catch (_) {
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(DB_PATH);
  dbRun = async (sql, params = []) => {
    const stmt = db.prepare(sql);
    const result = stmt.run(...params);
    return { lastID: Number(result.lastInsertRowid || 0), changes: result.changes || 0 };
  };
  dbGet = async (sql, params = []) => {
    const stmt = db.prepare(sql);
    return stmt.get(...params) || null;
  };
  dbAll = async (sql, params = []) => {
    const stmt = db.prepare(sql);
    return stmt.all(...params) || [];
  };
}

// Initialize Database Schema
async function initDatabase() {
  await dbRun('PRAGMA journal_mode = WAL');
  await dbRun('PRAGMA busy_timeout = 5000');

  // Users table
  await dbRun(`CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt TEXT,
    role TEXT NOT NULL DEFAULT 'user',
    full_name TEXT,
    avatar_url TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME
  )`);

  // User Tokens table (30-day bearer token)
  await dbRun(`CREATE TABLE IF NOT EXISTS user_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    token TEXT UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
  )`);

  // Edit Sessions table
  await dbRun(`CREATE TABLE IF NOT EXISTS edit_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT UNIQUE NOT NULL,
    user_id INTEGER,
    client_ip TEXT,
    user_agent TEXT,
    user_name TEXT,
    filename TEXT,
    file_size INTEGER,
    width INTEGER,
    height INTEGER,
    original_url TEXT,
    preview_url TEXT,
    recipe_name TEXT,
    settings_json TEXT,
    edit_count INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
  )`);

  // Edit Logs table
  await dbRun(`CREATE TABLE IF NOT EXISTS edit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    action TEXT NOT NULL,
    detail TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  )`);

  // Seed default admin account if not exists
  const existingAdmin = await dbGet('SELECT id FROM users WHERE username = ? OR email = ?', ['admin', 'admin@irostudio.local']);
  if (!existingAdmin) {
    const salt = crypto.randomBytes(16).toString('hex');
    const genPass = 'Iro#' + crypto.randomBytes(6).toString('hex') + '!2026';
    const hash = hashPassword(genPass, salt);
    await dbRun(
      `INSERT INTO users (username, email, password_hash, salt, role, full_name, status, created_at)
       VALUES (?, ?, ?, ?, 'admin', 'Hệ Thống Quản Trị Viên', 'active', CURRENT_TIMESTAMP)`,
      ['admin', 'admin@irostudio.local', hash, salt]
    );
    console.log('[Database] Default admin account seeded with random password.');
  }
}

// Password hashing and verification
function hashPassword(password, salt) {
  const derived = crypto.scryptSync(password, salt, 64);
  return `scrypt$${salt}$${derived.toString('hex')}`;
}

function verifyPassword(password, storedHash, storedSalt) {
  if (!storedHash || typeof storedHash !== 'string') return false;
  try {
    if (storedHash.startsWith('scrypt$')) {
      const parts = storedHash.split('$');
      if (parts.length < 3) return false;
      const salt = parts[1] || storedSalt;
      const key = parts[2];
      if (!salt || !key) return false;
      const bufDerived = crypto.scryptSync(password, salt, 64);
      const bufKey = Buffer.from(key, 'hex');
      if (bufDerived.length !== bufKey.length) return false;
      return crypto.timingSafeEqual(bufDerived, bufKey);
    }
  } catch {
    return false;
  }
  return false;
}

function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

function sanitizeUser(u) {
  if (!u) return null;
  return {
    id: u.id,
    username: u.username,
    email: u.email,
    role: u.role,
    full_name: u.full_name || u.username,
    avatar_url: u.avatar_url,
    status: u.status,
    created_at: u.created_at,
    last_login_at: u.last_login_at
  };
}

async function getAuthUser(req) {
  const authHeader = req.headers['authorization'] || '';
  let token = '';
  if (authHeader.startsWith('Bearer ')) {
    token = authHeader.slice(7).trim();
  } else {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    token = url.searchParams.get('token') || '';
  }
  if (!token) return null;

  const row = await dbGet(
    `SELECT u.* FROM users u 
     JOIN user_tokens t ON t.user_id = u.id 
     WHERE t.token = ? AND datetime(t.expires_at) > datetime('now')
     LIMIT 1`,
    [token]
  );
  if (!row || row.status === 'banned') return null;
  return row;
}

const ALLOWED_ORIGINS = new Set([
  'https://npccc.io.vn',
  'http://npccc.io.vn',
  'http://192.168.88.230',
  'http://localhost:3000',
  'http://127.0.0.1:3000'
]);

function getSafeCorsOrigin(req) {
  if (!req || !req.headers) return 'https://npccc.io.vn';
  const origin = req.headers['origin'];
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    return origin;
  }
  return 'https://npccc.io.vn';
}

// Simple JSON response helper with security headers & strict CORS
function sendJson(res, statusCode, data, req = null) {
  const origin = req ? getSafeCorsOrigin(req) : 'https://npccc.io.vn';
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'strict-origin-when-cross-origin'
  });
  res.end(JSON.stringify(data));
}

// Periodic purge of expired tokens (every 1 hour)
setInterval(async () => {
  try {
    await dbRun("DELETE FROM user_tokens WHERE datetime(expires_at) <= datetime('now')");
  } catch (err) {
    console.warn('[Token Cleanup Error]', err.message);
  }
}, 3600000);

// In-memory rate limiting store
const rateLimitMap = new Map();
function checkRateLimit(ip, action, maxAttempts, windowMs) {
  const key = `${action}:${ip}`;
  const now = Date.now();
  const entry = rateLimitMap.get(key) || { count: 0, resetAt: now + windowMs };
  if (now > entry.resetAt) {
    entry.count = 1;
    entry.resetAt = now + windowMs;
    rateLimitMap.set(key, entry);
    return true;
  }
  entry.count++;
  rateLimitMap.set(key, entry);
  return entry.count <= maxAttempts;
}
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of rateLimitMap.entries()) {
    if (now > v.resetAt) rateLimitMap.delete(k);
  }
}, 300000);

// Validate image signature (Magic Bytes)
function isValidImage(buffer, ext) {
  const cleanExt = (ext || '').toLowerCase();
  if (!['.jpg', '.jpeg', '.png', '.webp'].includes(cleanExt)) return false;
  if (!buffer || buffer.length < 12) return false;
  if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) return true;
  if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) return true;
  if (buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
      buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50) return true;
  return false;
}

// Request Body Reader with size limit
function readBody(req, maxBytes = 2 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', c => {
      size += c.length;
      if (size > maxBytes) {
        req.destroy();
        const err = new Error('PAYLOAD_TOO_LARGE');
        err.statusCode = 413;
        return reject(err);
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

// Parse Multipart FormData safely for photo uploads
function parseMultipart(buffer, boundary) {
  const result = { fields: {}, files: {} };
  const boundaryBuffer = Buffer.from('--' + boundary);
  let start = 0;

  while (start < buffer.length) {
    const boundaryIndex = buffer.indexOf(boundaryBuffer, start);
    if (boundaryIndex === -1) break;

    const nextBoundary = buffer.indexOf(boundaryBuffer, boundaryIndex + boundaryBuffer.length);
    if (nextBoundary === -1) break;

    const part = buffer.subarray(boundaryIndex + boundaryBuffer.length, nextBoundary);
    const headerEndIndex = part.indexOf(Buffer.from('\r\n\r\n'));
    if (headerEndIndex !== -1) {
      const headerStr = part.subarray(0, headerEndIndex).toString('utf8');
      const body = part.subarray(headerEndIndex + 4, part.length - 2); // remove trailing \r\n

      const nameMatch = headerStr.match(/name="([^"]+)"/);
      const filenameMatch = headerStr.match(/filename="([^"]+)"/);
      if (nameMatch) {
        const fieldName = nameMatch[1];
        if (filenameMatch) {
          result.files[fieldName] = {
            filename: filenameMatch[1],
            data: body
          };
        } else {
          result.fields[fieldName] = body.toString('utf8');
        }
      }
    }
    start = nextBoundary;
  }
  return result;
}


// =============================================================================
// AI FILM STYLIST & SMART RECIPE MATCHER (Ollama Local AI & 421 Film Library)
// =============================================================================

let recipesCatalog = [];
function reloadRecipesCatalog() {
  const possiblePaths = [
    path.join(__dirname, '../frontend/recipes.json'),
    path.join(__dirname, '../web/recipes.json'),
    path.join(__dirname, 'recipes.json')
  ];
  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      try {
        const raw = fs.readFileSync(p, 'utf8');
        const data = JSON.parse(raw);
        recipesCatalog = Array.isArray(data) ? data : (data.recipes || []);
        console.log(`[AI Stylist] Đã tải ${recipesCatalog.length} công thức từ ${p}`);
        return;
      } catch (e) {
        console.warn(`[AI Stylist] Lỗi đọc ${p}:`, e.message);
      }
    }
  }
}
reloadRecipesCatalog();

const aiStylistCache = new Map();
const MAX_AI_CACHE = 200;
let isOllamaRunningTask = false;

const AI_SCENARIOS = [
  {
    keys: ['cafe', 'cà phê', 'quán', 'vintage', 'hoài cổ', '90s', 'ấm', 'nâu', 'retro', 'gỗ'],
    film: 'Classic Chrome',
    tone: 'Ấm áp hoài cổ & Vintage',
    advice: 'Tone Classic Chrome hạ nhẹ độ bão hòa, kết hợp cân bằng trắng ấm tạo màu gỗ trầm ấm cúng, tái hiện chất phim vintage thập niên 90 hoàn hảo cho không gian quán cà phê.'
  },
  {
    keys: ['biển', 'beach', 'hè', 'nắng vàng', 'mùa hè', 'hoàng hôn biển', 'summer', 'đại dương', 'sóng'],
    film: 'Velvia',
    tone: 'Rực rỡ sắc màu & Nắng hè',
    advice: 'Giả lập Velvia kết hợp Color Chrome FX đẩy sắc xanh ngắt của nước biển và ánh nắng vàng mật ong, mang lại năng lượng tràn đầy rực rỡ của kỳ nghỉ nhiệt đới.'
  },
  {
    keys: ['đà lạt', 'mưa', 'buồn', 'lạnh', 'sương', 'mờ', 'điện ảnh', 'cinematic', 'trầm'],
    film: 'Eterna',
    tone: 'Điện ảnh trầm lạnh (Moody Cinematic)',
    advice: 'Giả lập điện ảnh Eterna kết hợp dải tương phản mở rộng DR400 và Pro-Mist nhẹ tạo hiệu ứng sương mờ bảng lảng, mang cảm giác u buồn và chiều sâu như thước phim chiếu rạp.'
  },
  {
    keys: ['nàng thơ', 'chân dung', 'trong trẻo', 'pastel', 'da mịn', 'dịu dàng', 'bạn gái', 'ngọt ngào', 'trắng hồng'],
    film: 'Astia',
    tone: 'Trong trẻo tự nhiên (Pastel & Soft Skin)',
    advice: 'Giả lập Astia làm dịu vùng sáng (Highlight -1), tái hiện sắc tố da người trắng hồng tự nhiên, mềm mại và trong trẻo như ánh sáng ban mai.'
  },
  {
    keys: ['đêm', 'phố đêm', 'neon', 'cyberpunk', 'cinestill', 'ánh đèn', 'tokyo', 'sài gòn đêm', 'đèn đường'],
    film: 'Classic Neg.',
    tone: 'Ánh đèn Neon CineStill (Night Street)',
    advice: 'Giả lập Classic Neg kết hợp quầng đỏ Halation và cân bằng trắng Tungsten đẩy độ tương phản giữa vùng bóng tối sâu thẳm và ánh đèn neon rực rỡ sắc màu.'
  },
  {
    keys: ['hà nội', 'thu', 'nắng thu', 'cổ kính', 'phố cổ', 'nắng sớm', 'mùa thu', 'hoài niệm'],
    film: 'Nostalgic Neg.',
    tone: 'Hoài niệm nắng thu (Amber & Warm Glow)',
    advice: 'Nostalgic Neg mang sắc hổ phách vàng ấm đặc trưng với vùng chuyển tông mềm mại, ghi lại trọn vẹn nét cổ kính rêu phong và ánh nắng thu len lỏi qua từng góc phố.'
  },
  {
    keys: ['đen trắng', 'bw', 'b&w', 'phóng sự', 'street', 'đường phố', 'tương phản gắt', 'monochrome'],
    film: 'Acros',
    tone: 'Đen trắng phóng sự (Acros High Contrast)',
    advice: 'Giả lập phim đen trắng huyền thoại Acros với hạt film mô phỏng mịn màng ở vùng sáng và rõ nét ở vùng tối, mang lại độ sắc sảo đầy biểu cảm cho ảnh đường phố.'
  },
  {
    keys: ['portra', 'kodak', 'màu film', 'film look', 'analog', 'film stock'],
    film: 'Classic Chrome',
    tone: 'Kodak Portra Film Emulation',
    advice: 'Mô phỏng chất phim âm bản Kodak Portra trứ danh với tông màu vàng kem mịn màng, độ tương phản dịu và khả năng tôn vinh màu da tự nhiên vượt trội.'
  },
  {
    keys: ['nhật bản', 'japan', 'trong sáng', 'tối giản', 'minimalist', 'fuji'],
    film: 'Provia',
    tone: 'Nhật Bản trong trẻo (Clean Japanese Tone)',
    advice: 'Giả lập Provia với cân bằng trắng thiên xanh nhẹ và tương phản tự nhiên, tạo cảm giác nhẹ nhàng, tĩnh lặng và thanh thoát đậm phong cách nhiếp ảnh Nhật Bản.'
  }
];

function analyzeFilmStylist(prompt) {
  const clean = (prompt || '').trim();
  const q = clean.toLowerCase();
  const cacheKey = q;

  if (aiStylistCache.has(cacheKey)) {
    return { ...aiStylistCache.get(cacheKey), cached: true };
  }

  const tokens = q.split(/\s+/).filter(t => t.length > 1);

  let bestScenario = null;
  let maxScenarioScore = 0;

  for (const s of AI_SCENARIOS) {
    let score = 0;
    for (const k of s.keys) {
      if (q.includes(k)) score += 15;
    }
    if (score > maxScenarioScore) {
      maxScenarioScore = score;
      bestScenario = s;
    }
  }

  const targetFilm = bestScenario ? bestScenario.film : 'Classic Chrome';
  const targetTone = bestScenario ? bestScenario.tone : 'Fujifilm Màu Phim Đa Dụng';
  const advice = bestScenario ? bestScenario.advice : 'Công thức cân bằng sắc thái màu sắc và độ tương phản tự nhiên, hạt film mịn nhẹ nhàng phù hợp cho nhiều điều kiện ánh sáng đời thường.';

  const scored = recipesCatalog.map(r => {
    let score = 0;
    const name = (r.name || '').toLowerCase();
    const film = (r.film || '').toLowerCase();
    const sourceUrl = (r.sourceUrl || '').toLowerCase();

    if (film === targetFilm.toLowerCase()) score += 40;
    else if (film.includes(targetFilm.toLowerCase())) score += 30;

    for (const t of tokens) {
      if (name.includes(t)) score += 25;
      if (film.includes(t)) score += 15;
      if (sourceUrl.includes(t)) score += 10;
    }

    if (bestScenario) {
      for (const k of bestScenario.keys) {
        if (name.includes(k)) score += 20;
      }
    }

    if (r.sampleImageUrl) score += 15;

    if (name.includes('portra') || name.includes('summer') || name.includes('gold') || name.includes('tokyo') || name.includes('cinestill') || name.includes('vintage') || name.includes('kodachrome')) {
      score += 10;
    }

    return { recipe: r, score };
  });

  scored.sort((a, b) => b.score - a.score);

  const top3 = scored.slice(0, 3).map(s => {
    const r = s.recipe;
    const matchPct = Math.min(99, Math.max(72, Math.round(s.score * 0.8 + 30)));
    let reason = `Đạt độ tương thích ${matchPct}% với bối cảnh "${clean}".`;
    if (r.rawSettings) {
      const wb = r.rawSettings['White Balance'] || r.rawSettings['wb'] || '';
      const grain = r.rawSettings['Grain Effect'] || r.rawSettings['Grain'] || '';
      if (wb) reason += ` WB: ${wb}.`;
      if (grain) reason += ` Grain: ${grain}.`;
    }
    return {
      id: r.id,
      name: r.name,
      film: r.film,
      source: r.source || 'Fujifilm Community',
      sourceUrl: r.sourceUrl,
      sampleImageUrl: r.sampleImageUrl,
      rawSettings: r.rawSettings,
      score: s.score,
      matchReason: reason
    };
  });

  const responseObj = {
    ok: true,
    prompt: clean,
    film: targetFilm,
    tone: targetTone,
    advice: advice,
    recipes: top3,
    totalRecipesInCatalog: recipesCatalog.length,
    engine: 'hybrid-semantic-ollama'
  };

  if (aiStylistCache.size >= MAX_AI_CACHE) {
    const firstKey = aiStylistCache.keys().next().value;
    aiStylistCache.delete(firstKey);
  }
  aiStylistCache.set(cacheKey, responseObj);

  queryOllamaBackground(clean, targetFilm, targetTone).catch(() => {});

  return responseObj;
}

async function queryOllamaBackground(prompt, targetFilm, targetTone) {
  if (isOllamaRunningTask) return;
  isOllamaRunningTask = true;
  try {
    const postData = JSON.stringify({
      model: 'qwen2.5:0.5b',
      prompt: `Bạn là chuyên gia màu phim Fujifilm. Người dùng mô tả ảnh: "${prompt}". Gợi ý 1 giả lập phim (${targetFilm}) và 1 câu nhận xét thẩm mỹ ngắn gọn bằng tiếng Việt. Trả về đúng JSON: {"film": "${targetFilm}", "tone": "${targetTone}", "advice": "..."}`,
      stream: false,
      options: { temperature: 0.2, num_ctx: 256, num_predict: 40, num_thread: 4 }
    });

    const req = http.request({
      hostname: '127.0.0.1',
      port: 11434,
      path: '/api/generate',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(postData) },
      timeout: 35000
    }, (res) => {
      let data = '';
      res.on('data', c => { data += c; });
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          const raw = parsed.response || '';
          const m = raw.match(/\{[\s\S]*\}/);
          if (m) {
            const obj = JSON.parse(m[0]);
            if (obj && obj.advice && obj.advice.length > 10) {
              const cached = aiStylistCache.get(prompt.toLowerCase().trim());
              if (cached) {
                cached.advice = obj.advice;
                cached.engine = 'ollama-qwen2.5:0.5b';
              }
            }
          }
        } catch (_) {}
        isOllamaRunningTask = false;
      });
    });

    req.on('timeout', () => { req.destroy(); isOllamaRunningTask = false; });
    req.on('error', () => { isOllamaRunningTask = false; });
    req.write(postData);
    req.end();
  } catch (_) {
    isOllamaRunningTask = false;
  }
}

// Main HTTP Request Handler
const server = http.createServer(async (req, res) => {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    const origin = getSafeCorsOrigin(req);
    res.writeHead(204, {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY'
    });
    return res.end();
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  const action = url.searchParams.get('action') || '';

  try {
    // -------------------------------------------------------------------------
    // HEALTH CHECK
    // -------------------------------------------------------------------------
    
    // -------------------------------------------------------------------------
    // API: CONVERT IMAGE (HEIC, RAW, PNG, TIFF -> JPEG)
    // -------------------------------------------------------------------------
    if (pathname === '/api/image/convert' || action === 'convert_image') {
      if (req.method !== 'POST') {
        res.writeHead(405, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify({ ok: false, error: 'Method Not Allowed' }));
      }
      const contentType = req.headers['content-type'] || '';
      const boundaryMatch = contentType.match(/boundary=(?:"([^"]+)"|([^;]+))/i);
      if (!boundaryMatch) {
        return sendJson(res, 400, { ok: false, error: 'Missing multipart boundary' }, req);
      }
      let raw;
      try {
        raw = await readBody(req, 30 * 1024 * 1024);
      } catch (err) {
        return sendJson(res, err.statusCode || 400, { ok: false, error: err.message }, req);
      }
      const parsed = parseMultipart(raw, boundaryMatch[1] || boundaryMatch[2]);
      const photo = parsed.files.photo;
      if (!photo || !photo.data || photo.data.length === 0) {
        return sendJson(res, 400, { ok: false, error: 'Missing photo file' }, req);
      }
      const ext = path.extname(photo.filename || '').toLowerCase().replace('.', '');
      const supported = ['png', 'heic', 'heif', 'jpg', 'jpeg', 'webp', 'tiff', 'tif', 'bmp', 'raw', 'dng', 'raf'];
      if (!supported.includes(ext)) {
        res.writeHead(415, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify({ ok: false, error: 'Unsupported media type' }));
      }
      if (photo.data.length < 8 || (photo.data[0] === 0xff && photo.data[1] === 0xd8 && photo.data[2] === 0xff && photo.data[3] === 0x00)) {
        res.writeHead(422, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify({ ok: false, error: 'Unprocessable entity: Corrupted image file' }));
      }

      // Convert using ImageMagick if available
      const tmpPrefix = `conv_${Date.now()}_${Math.random().toString(36).slice(2)}`;
      const tmpIn = path.join(DATA_DIR, `${tmpPrefix}.${ext}`);
      const tmpOut = path.join(DATA_DIR, `${tmpPrefix}.jpg`);
      try {
        await fs.promises.writeFile(tmpIn, photo.data);
        const { spawnSync } = await import('node:child_process');
        let converted = false;

        // Ưu tiên magick / convert giữ nguyên độ phân giải gốc cao nhất (quality 95)
        const attempts = [
          { bin: 'magick', args: [tmpIn, '-auto-orient', '-quality', '95', tmpOut] },
          { bin: 'convert', args: [tmpIn, '-auto-orient', '-quality', '95', tmpOut] },
          { bin: 'magick', args: [`${tmpIn}[0]`, '-auto-orient', '-quality', '95', tmpOut] },
          { bin: 'convert', args: [`${tmpIn}[0]`, '-auto-orient', '-quality', '95', tmpOut] }
        ];

        for (const att of attempts) {
          try {
            spawnSync(att.bin, att.args, { stdio: 'ignore', timeout: 25000, windowsHide: true });
            if (fs.existsSync(tmpOut) && fs.statSync(tmpOut).size > 0) {
              converted = true;
              break;
            }
          } catch (_) {}

          // Kiểm tra nếu ImageMagick tách thành nhiều frame/layer (-0.jpg, -1.jpg), chọn file có dung lượng lớn nhất (ảnh gốc, bỏ qua thumbnail)
          try {
            const files = await fs.promises.readdir(DATA_DIR);
            const matches = files.filter(f => f.startsWith(tmpPrefix) && f.endsWith('.jpg') && f !== path.basename(tmpOut));
            if (matches.length > 0) {
              let largestFile = null;
              let largestSize = 0;
              for (const m of matches) {
                const sz = fs.statSync(path.join(DATA_DIR, m)).size;
                if (sz > largestSize) {
                  largestSize = sz;
                  largestFile = m;
                }
              }
              if (largestFile && largestSize > 0) {
                await fs.promises.copyFile(path.join(DATA_DIR, largestFile), tmpOut);
                converted = true;
                break;
              }
            }
          } catch (_) {}
        }

        if (!converted || !fs.existsSync(tmpOut) || fs.statSync(tmpOut).size === 0) {
          res.writeHead(422, { 'Content-Type': 'application/json; charset=utf-8' });
          return res.end(JSON.stringify({ ok: false, error: 'Không thể chuyển đổi định dạng tệp ảnh này sang JPEG.' }));
        }

        const outData = await fs.promises.readFile(tmpOut);
        res.writeHead(200, {
          'Content-Type': 'image/jpeg',
          'Content-Length': outData.length,
          'X-Content-Type-Options': 'nosniff'
        });
        return res.end(outData);
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify({ ok: false, error: 'Conversion failed: ' + err.message }));
      } finally {
        try {
          const files = await fs.promises.readdir(DATA_DIR);
          for (const f of files) {
            if (f.startsWith(tmpPrefix)) {
              try { await fs.promises.unlink(path.join(DATA_DIR, f)); } catch (_) {}
            }
          }
        } catch (_) {}
      }
    }

    if (pathname === '/health' || pathname === '/api/health') {
      return sendJson(res, 200, {
        ok: true,
        service: 'iro-backend',
        version: '3.0.0',
        uptime: process.uptime(),
        database: 'SQLite3',
        timestamp: new Date().toISOString()
      });
    }

    // -------------------------------------------------------------------------
    // STATIC UPLOADS SERVING: /uploads/...
    // -------------------------------------------------------------------------
    if (pathname.startsWith('/uploads/')) {
      const rawRel = pathname.slice('/uploads/'.length);
      const safePath = path.resolve(UPLOAD_DIR, rawRel);
      const resolvedUploadDir = path.resolve(UPLOAD_DIR);
      if (!safePath.startsWith(resolvedUploadDir + path.sep)) {
        return sendJson(res, 403, { ok: false, error: 'Access denied' });
      }
      if (fs.existsSync(safePath) && fs.statSync(safePath).isFile()) {
        const ext = path.extname(safePath).toLowerCase();
        const mimeMap = {
          '.png': 'image/png',
          '.webp': 'image/webp',
          '.jpg': 'image/jpeg',
          '.jpeg': 'image/jpeg',
          '.gif': 'image/gif'
        };
        const mime = mimeMap[ext] || 'application/octet-stream';
        res.writeHead(200, {
          'Content-Type': mime,
          'Content-Security-Policy': "default-src 'none'",
          'X-Content-Type-Options': 'nosniff',
          'Cache-Control': 'public, max-age=86400',
          'Access-Control-Allow-Origin': '*'
        });
        return fs.createReadStream(safePath).pipe(res);
      }
      return sendJson(res, 404, { ok: false, error: 'File not found' });
    }

    // -------------------------------------------------------------------------
    // API: AUTH REGISTER
    // -------------------------------------------------------------------------
    if (pathname === '/api/auth/register' || action === 'auth_register') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
      if (!checkRateLimit(clientIp, 'register', 5, 600000)) {
        return sendJson(res, 429, { ok: false, error: 'Quá nhiều yêu cầu đăng ký. Vui lòng thử lại sau.' });
      }
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const username = (payload.username || '').trim().toLowerCase();
      const email = (payload.email || '').trim().toLowerCase();
      const password = payload.password || '';
      const fullName = (payload.full_name || payload.fullName || username).trim();

      if (username.length < 3 || username.length > 24 || !/^[a-z0-9_.-]+$/.test(username)) {
        return sendJson(res, 400, { ok: false, error: 'Tên đăng nhập phải từ 3-24 ký tự (chữ thường, số, dấu gạch).' });
      }
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email) || email.length > 100) {
        return sendJson(res, 400, { ok: false, error: 'Địa chỉ email không đúng định dạng hoặc vượt quá độ dài.' });
      }
      if (password.length < 8) {
        return sendJson(res, 400, { ok: false, error: 'Mật khẩu phải có tối thiểu 8 ký tự để đảm bảo an toàn.' });
      }

      const dup = await dbGet('SELECT id FROM users WHERE username = ? OR email = ?', [username, email]);
      if (dup) {
        return sendJson(res, 409, { ok: false, error: 'Tên đăng nhập hoặc Email đã tồn tại trên hệ thống.' });
      }

      const salt = crypto.randomBytes(16).toString('hex');
      const hash = hashPassword(password, salt);
      const ins = await dbRun(
        `INSERT INTO users (username, email, password_hash, salt, role, full_name, status, created_at, last_login_at)
         VALUES (?, ?, ?, ?, 'user', ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`,
        [username, email, hash, salt, fullName]
      );
      const newUserId = ins.lastID;

      // Create token
      const token = generateToken();
      const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
      await dbRun('INSERT INTO user_tokens (user_id, token, expires_at) VALUES (?, ?, ?)', [newUserId, token, expiresAt]);

      const newUser = await dbGet('SELECT * FROM users WHERE id = ?', [newUserId]);
      return sendJson(res, 201, {
        ok: true,
        token,
        user: sanitizeUser(newUser),
        message: 'Đăng ký tài khoản thành công!'
      });
    }

    // -------------------------------------------------------------------------
    // API: AUTH LOGIN
    // -------------------------------------------------------------------------
    if (pathname === '/api/auth/login' || action === 'auth_login') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
      if (!checkRateLimit(clientIp, 'login', 15, 60000)) {
        return sendJson(res, 429, { ok: false, error: 'Quá nhiều lần đăng nhập không thành công. Vui lòng chờ 1 phút.' });
      }
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const login = (payload.account || payload.login || payload.username || payload.email || '').trim().toLowerCase();
      const password = payload.password || '';

      if (!login || !password) {
        return sendJson(res, 400, { ok: false, error: 'Vui lòng nhập tên đăng nhập/email và mật khẩu.' });
      }

      const user = await dbGet('SELECT * FROM users WHERE username = ? OR email = ? LIMIT 1', [login, login]);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Tài khoản không tồn tại trên hệ thống.' });
      }

      if (user.status === 'banned') {
        return sendJson(res, 403, { ok: false, error: 'Tài khoản của bạn đã bị khóa. Vui lòng liên hệ Admin.' });
      }

      const valid = verifyPassword(password, user.password_hash, user.salt);
      if (!valid) {
        return sendJson(res, 401, { ok: false, error: 'Mật khẩu không chính xác.' });
      }

      // Upgrade bcrypt to scrypt if needed
      if (!user.password_hash.startsWith('scrypt$')) {
        const newSalt = crypto.randomBytes(16).toString('hex');
        const newHash = hashPassword(password, newSalt);
        await dbRun('UPDATE users SET password_hash = ?, salt = ? WHERE id = ?', [newHash, newSalt, user.id]);
      }

      const token = generateToken();
      const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
      await dbRun('INSERT INTO user_tokens (user_id, token, expires_at) VALUES (?, ?, ?)', [user.id, token, expiresAt]);
      await dbRun('UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?', [user.id]);

      return sendJson(res, 200, {
        ok: true,
        token,
        user: sanitizeUser(user),
        message: 'Đăng nhập thành công!'
      });
    }

    // -------------------------------------------------------------------------
    // API: AUTH ME
    // -------------------------------------------------------------------------
    if (pathname === '/api/auth/me' || action === 'auth_me') {
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 200, { ok: true, authenticated: false, user: null });
      }
      return sendJson(res, 200, { ok: true, authenticated: true, user: sanitizeUser(user) });
    }

    // -------------------------------------------------------------------------
    // API: AUTH LOGOUT
    // -------------------------------------------------------------------------
    if (pathname === '/api/auth/logout' || action === 'auth_logout') {
      const authHeader = req.headers['authorization'] || '';
      if (authHeader.startsWith('Bearer ')) {
        const token = authHeader.slice(7).trim();
        await dbRun('DELETE FROM user_tokens WHERE token = ?', [token]);
      }
      return sendJson(res, 200, { ok: true, message: 'Đã đăng xuất tài khoản.' });
    }

    // -------------------------------------------------------------------------
    // API: INIT SESSION (Requires Auth)
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/init' || action === 'init_session') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Vui lòng đăng nhập để bắt đầu chỉnh sửa ảnh.' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const sessionId = (payload.sessionId || '').trim();
      if (!sessionId || !/^[a-zA-Z0-9_-]{6,64}$/.test(sessionId)) {
        return sendJson(res, 400, { ok: false, error: 'Mã sessionId không hợp lệ (từ 6-64 ký tự).' });
      }

      const existingSession = await dbGet('SELECT user_id FROM edit_sessions WHERE session_id = ?', [sessionId]);
      if (existingSession && existingSession.user_id && existingSession.user_id !== user.id && user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền can thiệp vào phiên của người khác.' });
      }

      const filename = path.basename(payload.filename || 'photo.jpg');
      const fileSize = Number(payload.fileSize || 0);
      const width = Number(payload.width || 0);
      const height = Number(payload.height || 0);
      const recipeName = (payload.recipeName || 'Classic Chrome').trim();

      const userId = user.id;
      const userName = (user.full_name || user.username).trim();

      const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
      const userAgent = req.headers['user-agent'] || '';

      await dbRun(
        `INSERT INTO edit_sessions (session_id, user_id, client_ip, user_agent, user_name, filename, file_size, width, height, recipe_name, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
         ON CONFLICT(session_id) DO UPDATE SET
           user_id = coalesce(?, user_id),
           filename = excluded.filename,
           file_size = excluded.file_size,
           width = excluded.width,
           height = excluded.height,
           updated_at = CURRENT_TIMESTAMP`,
        [sessionId, userId, clientIp, userAgent, userName, filename, fileSize, width, height, recipeName, userId]
      );

      await dbRun(
        'INSERT INTO edit_logs (session_id, action, detail) VALUES (?, "init", ?)',
        [sessionId, `Tải ảnh: ${filename} (${width}x${height}px) bởi ${userName}`]
      );

      return sendJson(res, 200, { ok: true, sessionId, userId, userName });
    }

    // -------------------------------------------------------------------------
    // API: SYNC SETTINGS & PREVIEW (Requires Auth)
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/sync' || action === 'sync_settings') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Vui lòng đăng nhập để đồng bộ thông số chỉnh sửa.' });
      }
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const sessionId = (payload.sessionId || '').trim();
      if (!sessionId || !/^[a-zA-Z0-9_-]{6,64}$/.test(sessionId)) {
        return sendJson(res, 400, { ok: false, error: 'Mã sessionId không hợp lệ.' });
      }

      const existingSession = await dbGet('SELECT user_id FROM edit_sessions WHERE session_id = ?', [sessionId]);
      if (existingSession && existingSession.user_id && existingSession.user_id !== user.id && user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền đồng bộ phiên của người khác.' });
      }

      const recipeName = (payload.recipeName || 'Tự chỉnh').trim();
      const settingsJson = JSON.stringify(payload.settings || {});
      const actionDesc = (payload.actionDesc || 'Chỉnh sửa thông số').trim();
      const previewJpeg = payload.previewJpeg;

      let previewUrl = null;
      if (previewJpeg && previewJpeg.startsWith('data:image/')) {
        const commaIdx = previewJpeg.indexOf(',');
        if (commaIdx !== -1) {
          const bin = Buffer.from(previewJpeg.slice(commaIdx + 1), 'base64');
          const cleanSid = sessionId.replace(/[^a-zA-Z0-9_-]/g, '');
          const prevName = `prev_${cleanSid}.jpg`;
          fs.writeFileSync(path.join(UPLOAD_DIR, prevName), bin);
          previewUrl = `uploads/${prevName}?v=${Date.now()}`;
        }
      }

      if (previewUrl) {
        await dbRun(
          `UPDATE edit_sessions SET 
             recipe_name = ?, settings_json = ?, preview_url = ?, edit_count = edit_count + 1, updated_at = CURRENT_TIMESTAMP
           WHERE session_id = ?`,
          [recipeName, settingsJson, previewUrl, sessionId]
        );
      } else {
        await dbRun(
          `UPDATE edit_sessions SET 
             recipe_name = ?, settings_json = ?, edit_count = edit_count + 1, updated_at = CURRENT_TIMESTAMP
           WHERE session_id = ?`,
          [recipeName, settingsJson, sessionId]
        );
      }

      await dbRun(
        'INSERT INTO edit_logs (session_id, action, detail) VALUES (?, "edit", ?)',
        [sessionId, `${actionDesc} -> Film: ${recipeName}`]
      );

      return sendJson(res, 200, { ok: true, sessionId, previewUrl });
    }

    // -------------------------------------------------------------------------
    // API: UPLOAD ORIGINAL PHOTO (Multipart - Requires Auth)
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/upload' || action === 'upload_original') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Vui lòng đăng nhập để tải ảnh gốc lên máy chủ.' });
      }
      const contentType = req.headers['content-type'] || '';
      const boundaryMatch = contentType.match(/boundary=(?:"([^"]+)"|([^;]+))/i);
      if (!boundaryMatch) return sendJson(res, 400, { ok: false, error: 'Missing multipart boundary' });

      const boundary = boundaryMatch[1] || boundaryMatch[2];
      const raw = await readBody(req, 25 * 1024 * 1024);
      const parsed = parseMultipart(raw, boundary);

      const sessionId = (parsed.fields.sessionId || '').trim();
      const photoFile = parsed.files.photo;

      if (!sessionId || !photoFile) {
        return sendJson(res, 400, { ok: false, error: 'Missing sessionId or photo' });
      }
      if (!/^[a-zA-Z0-9_-]{6,64}$/.test(sessionId)) {
        return sendJson(res, 400, { ok: false, error: 'Mã sessionId không hợp lệ.' });
      }

      const existingSession = await dbGet('SELECT user_id FROM edit_sessions WHERE session_id = ?', [sessionId]);
      if (existingSession && existingSession.user_id && existingSession.user_id !== user.id && user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền tải ảnh vào phiên của người khác.' });
      }

      let origExt = path.extname(photoFile.filename || '').toLowerCase();
      if (!['.jpg', '.jpeg', '.png', '.webp'].includes(origExt)) {
        return sendJson(res, 400, { ok: false, error: 'Chỉ chấp nhận định dạng ảnh JPG, PNG hoặc WebP' });
      }
      if (!isValidImage(photoFile.data, origExt)) {
        return sendJson(res, 400, { ok: false, error: 'Tập tin tải lên không phải là ảnh hợp lệ' });
      }
      if (origExt === '.jpeg') origExt = '.jpg';

      const cleanSid = sessionId.replace(/[^a-zA-Z0-9_-]/g, '');
      const safeFilename = `orig_${cleanSid}${origExt}`;
      const savePath = path.join(UPLOAD_DIR, safeFilename);

      fs.writeFileSync(savePath, photoFile.data);
      const origUrl = `uploads/${safeFilename}`;

      await dbRun(
        'UPDATE edit_sessions SET original_url = ?, updated_at = CURRENT_TIMESTAMP WHERE session_id = ?',
        [origUrl, sessionId]
      );

      await dbRun(
        'INSERT INTO edit_logs (session_id, action, detail) VALUES (?, "upload_orig", ?)',
        [sessionId, `Lưu ảnh gốc: ${safeFilename} (${(photoFile.data.length / 1024).toFixed(1)} KB)`]
      );

      return sendJson(res, 200, { ok: true, originalUrl: origUrl });
    }

    // -------------------------------------------------------------------------
    // API: MOBILE CLOUD SYNC & STORAGE (Android & iOS)
    // -------------------------------------------------------------------------
    if (pathname === '/api/mobile/sync-edit' || action === 'mobile_sync_edit') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });

      let platform = 'Mobile';
      let deviceId = '';
      let filename = 'mobile_photo.jpg';
      let recipeName = 'Classic Chrome';
      let width = 0;
      let height = 0;
      let settingsJson = '{}';
      let photoBuffer = null;
      let photoExt = '.jpg';

      const contentType = req.headers['content-type'] || '';

      if (contentType.includes('multipart/form-data')) {
        const boundaryMatch = contentType.match(/boundary=([^;]+)/i);
        if (!boundaryMatch) {
          return sendJson(res, 400, { ok: false, error: 'Thiếu boundary trong multipart request' });
        }
        const boundary = boundaryMatch[1].replace(/^"(.*)"$/, '$1').trim();
        const raw = await readBody(req, 30 * 1024 * 1024);
        const parsed = parseMultipart(raw, boundary);

        platform = (parsed.fields.platform || 'Mobile').trim();
        deviceId = (parsed.fields.deviceId || '').trim();
        filename = path.basename(parsed.fields.filename || 'mobile_photo.jpg');
        recipeName = (parsed.fields.recipeName || 'Classic Chrome').trim();
        width = parseInt(parsed.fields.width, 10) || 0;
        height = parseInt(parsed.fields.height, 10) || 0;
        settingsJson = parsed.fields.settings || '{}';

        const photoFile = parsed.files.photo;
        if (!photoFile || !photoFile.data) {
          return sendJson(res, 400, { ok: false, error: 'Thiếu tệp ảnh tải lên (photo)' });
        }
        photoBuffer = photoFile.data;
        photoExt = path.extname(photoFile.filename || filename || '.jpg').toLowerCase();
      } else {
        const raw = await readBody(req, 30 * 1024 * 1024);
        const payload = JSON.parse(raw.toString('utf8') || '{}');

        platform = (payload.platform || 'Mobile').trim();
        deviceId = (payload.deviceId || '').trim();
        filename = path.basename(payload.filename || 'mobile_photo.jpg');
        recipeName = (payload.recipeName || 'Classic Chrome').trim();
        width = parseInt(payload.width, 10) || 0;
        height = parseInt(payload.height, 10) || 0;
        settingsJson = typeof payload.settings === 'object' ? JSON.stringify(payload.settings) : (payload.settings || '{}');

        const b64Data = payload.photo || payload.photoBase64 || payload.previewJpeg;
        if (!b64Data) {
          return sendJson(res, 400, { ok: false, error: 'Thiếu dữ liệu ảnh (photo / photoBase64)' });
        }

        const commaIdx = b64Data.indexOf(',');
        const pureB64 = commaIdx !== -1 ? b64Data.slice(commaIdx + 1) : b64Data;
        photoBuffer = Buffer.from(pureB64, 'base64');
        photoExt = path.extname(filename || '.jpg').toLowerCase();
      }

      if (!['.jpg', '.jpeg', '.png', '.webp'].includes(photoExt)) {
        photoExt = '.jpg';
      }
      if (photoExt === '.jpeg') photoExt = '.jpg';

      if (!isValidImage(photoBuffer, photoExt)) {
        return sendJson(res, 400, { ok: false, error: 'Dữ liệu tải lên không phải định dạng ảnh hợp lệ (JPG, PNG, WebP)' });
      }

      const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
      const userAgent = req.headers['user-agent'] || `${platform}-App`;
      const cleanPlatform = platform.replace(/[^a-zA-Z0-9_-]/g, '') || 'Mobile';
      const cleanDeviceId = deviceId.slice(0, 60).replace(/[^a-zA-Z0-9_\-\. ()]/g, '') || 'Device';
      const userName = `[${cleanPlatform}] ${cleanDeviceId}`;

      const sessionId = `mob_${cleanPlatform.toLowerCase()}_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
      const savedFilename = `${sessionId}${photoExt}`;
      const savePath = path.join(UPLOAD_DIR, savedFilename);

      fs.writeFileSync(savePath, photoBuffer);
      const photoUrl = `uploads/${savedFilename}`;
      const fileSize = photoBuffer.length;

      await dbRun(
        `INSERT INTO edit_sessions (
           session_id, user_id, client_ip, user_agent, user_name, filename,
           file_size, width, height, original_url, preview_url, recipe_name,
           settings_json, edit_count, created_at, updated_at
         ) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`,
        [sessionId, clientIp, userAgent, userName, filename, fileSize, width, height, photoUrl, photoUrl, recipeName, settingsJson]
      );

      const logDetail = `[${cleanPlatform}] Đã lưu ảnh xuất: ${filename} (${width > 0 ? width + 'x' + height + 'px, ' : ''}${(fileSize / 1024).toFixed(1)} KB) - Công thức: ${recipeName}`;
      await dbRun(
        'INSERT INTO edit_logs (session_id, action, detail, created_at) VALUES (?, "mobile_export", ?, CURRENT_TIMESTAMP)',
        [sessionId, logDetail]
      );

      return sendJson(res, 200, {
        ok: true,
        sessionId,
        url: photoUrl,
        fullUrl: `https://${req.headers.host || 'npccc.io.vn'}/${photoUrl}`,
        filename: savedFilename,
        fileSize,
        message: 'Đã lưu trữ và ghi nhận ảnh thành công lên máy chủ'
      });
    }

    // -------------------------------------------------------------------------
    // API: MOBILE LIST PHOTOS
    // -------------------------------------------------------------------------
    if (pathname === '/api/mobile/photos' || action === 'mobile_get_photos') {
      const deviceId = (url.searchParams.get('deviceId') || '').trim();
      if (!deviceId || deviceId.length < 3) {
        return sendJson(res, 400, { ok: false, error: 'Yêu cầu mã deviceId hợp lệ' });
      }
      const cleanDeviceId = deviceId.slice(0, 60).replace(/[^a-zA-Z0-9_\-\.\ \(\)]/g, '');
      const rows = await dbAll(
        `SELECT id, session_id, user_name, filename, file_size, width, height,
                original_url, preview_url, recipe_name, settings_json, edit_count, created_at, updated_at
         FROM edit_sessions
         WHERE user_name LIKE ?
         ORDER BY updated_at DESC LIMIT 50`,
        [`%${cleanDeviceId}%`]
      );
      return sendJson(res, 200, {
        ok: true,
        photos: rows.map(r => ({ 
          ...r,
          settings_json: r.settings_json ? JSON.parse(r.settings_json) : {}
        }))
      });
    }

    // -------------------------------------------------------------------------
    // API: MOBILE OTA (Over-The-Air Update System)
    // -------------------------------------------------------------------------
    if (pathname === '/api/mobile/ota/check' || action === 'ota_check') {
      const platform = (url.searchParams.get('platform') || 'android').toLowerCase();
      const currentVersionCode = parseInt(url.searchParams.get('versionCode') || '0', 10);

      const manifestPath = path.join(DATA_DIR, 'ota_manifest.json');
      let manifest = {
        latestVersionCode: 3,
        latestVersionName: '0.3.0',
        apkUrl: 'https://npccc.io.vn/downloads/iro-studio-latest.apk',
        fileSize: 0,
        sha256: '',
        releaseNotes: '- Tự động đồng bộ và sao lưu ảnh lên đám mây IRO Cloud\n- Bổ sung 421 công thức màu film (Fuji X Weekly & FujiStyle)\n- Cập nhật Over-The-Air (OTA) tức thì không cần cắm cáp\n- Nâng cấp độ mượt và tối ưu hóa xử lý ảnh',
        publishedAt: new Date().toISOString()
      };

      if (fs.existsSync(manifestPath)) {
        try {
          manifest = { ...manifest, ...JSON.parse(fs.readFileSync(manifestPath, 'utf8')) };
        } catch (e) {
          console.warn('[OTA] Error reading manifest:', e.message);
        }
      } else {
        fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
      }

      const downloadsDir = path.join(__dirname, '../frontend/downloads');
      const apkPath = path.join(downloadsDir, 'iro-studio-latest.apk');
      if (fs.existsSync(apkPath)) {
        manifest.fileSize = fs.statSync(apkPath).size;
      }

      const hasUpdate = manifest.latestVersionCode > currentVersionCode;

      return sendJson(res, 200, {
        ok: true,
        platform,
        hasUpdate,
        ...manifest
      });
    }

    // API: ADMIN PUBLISH NEW OTA RELEASE
    if (pathname === '/api/admin/ota/publish' || action === 'admin_ota_publish') {
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Yêu cầu quyền quản trị viên (Admin)' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString('utf8') || '{}');
      const manifestPath = path.join(DATA_DIR, 'ota_manifest.json');

      const manifest = {
        latestVersionCode: parseInt(payload.versionCode, 10) || 3,
        latestVersionName: (payload.versionName || '0.3.0').trim(),
        apkUrl: (payload.apkUrl || 'https://npccc.io.vn/downloads/iro-studio-latest.apk').trim(),
        fileSize: parseInt(payload.fileSize, 10) || 0,
        sha256: (payload.sha256 || '').trim(),
        releaseNotes: (payload.releaseNotes || 'Cập nhật phiên bản mới').trim(),
        publishedAt: new Date().toISOString()
      };

      fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');

      await dbRun(
        'INSERT INTO edit_logs (session_id, action, detail, created_at) VALUES ("system", "ota_publish", ?, CURRENT_TIMESTAMP)',
        [`Phát hành bản cập nhật OTA v${manifest.latestVersionName} (Build ${manifest.latestVersionCode})`]
      );

      return sendJson(res, 200, { ok: true, manifest });
    }

    // -------------------------------------------------------------------------
    // API: GET MY PHOTOS
    // -------------------------------------------------------------------------
    if (pathname === '/api/photos' || action === 'get_my_photos') {
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Vui lòng đăng nhập để thực hiện' });
      }

      const rows = await dbAll(
        `SELECT * FROM edit_sessions 
         WHERE user_id = ? 
         ORDER BY updated_at DESC LIMIT 60`,
        [user.id]
      );

      return sendJson(res, 200, {
        ok: true,
        sessions: rows.map(r => ({
          ...r,
          settings_json: r.settings_json ? JSON.parse(r.settings_json) : {}
        }))
      });
    }

    // -------------------------------------------------------------------------
    // API: ADMIN GET USERS
    // -------------------------------------------------------------------------
    if (pathname === '/api/admin/users' || action === 'admin_get_users') {
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền quản trị viên' });
      }

      const rows = await dbAll(`
        SELECT u.id, u.username, u.email, u.full_name, u.role, u.status, u.created_at, u.last_login_at,
               count(distinct s.id) as photo_count,
               coalesce(sum(s.edit_count), 0) as total_edits
        FROM users u
        LEFT JOIN edit_sessions s ON s.user_id = u.id
        GROUP BY u.id
        ORDER BY u.id DESC
      `);

      return sendJson(res, 200, { ok: true, users: rows });
    }

    // -------------------------------------------------------------------------
    // API: ADMIN UPDATE USER (Role & Status)
    // -------------------------------------------------------------------------
    if (pathname === '/api/admin/user/update' || action === 'admin_update_user') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền quản trị viên' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');
      const targetUserId = Number(payload.userId);
      if (!targetUserId) return sendJson(res, 400, { ok: false, error: 'Missing userId' });

      if (payload.role !== undefined && ['admin', 'user'].includes(payload.role)) {
        await dbRun('UPDATE users SET role = ? WHERE id = ?', [payload.role, targetUserId]);
      }
      if (payload.status !== undefined && ['active', 'banned'].includes(payload.status)) {
        await dbRun('UPDATE users SET status = ? WHERE id = ?', [payload.status, targetUserId]);
        if (payload.status === 'banned') {
          await dbRun('DELETE FROM user_tokens WHERE user_id = ?', [targetUserId]);
        }
      }

      return sendJson(res, 200, { ok: true, message: 'Đã cập nhật thông tin người dùng' });
    }

    // -------------------------------------------------------------------------
    // API: ADMIN DELETE USER
    // -------------------------------------------------------------------------
    if (pathname === '/api/admin/user/delete' || action === 'admin_delete_user') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền quản trị viên' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');
      const targetUserId = Number(payload.userId);

      if (targetUserId === user.id) {
        return sendJson(res, 400, { ok: false, error: 'Không thể tự xóa tài khoản của chính mình' });
      }

      await dbRun('DELETE FROM users WHERE id = ?', [targetUserId]);
      return sendJson(res, 200, { ok: true, message: 'Đã xóa vĩnh viễn tài khoản' });
    }

    // -------------------------------------------------------------------------
    // API: ADMIN DELETE SESSION / PHOTO
    // -------------------------------------------------------------------------
    if (pathname === '/api/admin/photo/delete' || action === 'admin_delete_photo' || action === 'admin_delete_session') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền quản trị viên' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');
      const sessionId = payload.sessionId;
      if (!sessionId) return sendJson(res, 400, { ok: false, error: 'Missing sessionId' });

      const session = await dbGet('SELECT * FROM edit_sessions WHERE session_id = ?', [sessionId]);
      if (!session) {
        return sendJson(res, 404, { ok: false, error: 'Không tìm thấy phiên làm việc / ảnh' });
      }

      // Xóa các file ảnh vật lý trên đĩa cứng
      const filesToDelete = [];
      if (session.original_url) filesToDelete.push(path.basename(session.original_url.split('?')[0]));
      if (session.preview_url) filesToDelete.push(path.basename(session.preview_url.split('?')[0]));

      for (const fn of filesToDelete) {
        if (!fn) continue;
        const p = path.join(UPLOAD_DIR, fn);
        try {
          if (fs.existsSync(p)) fs.unlinkSync(p);
        } catch (e) {
          console.warn('Lỗi khi xóa file ảnh:', fn, e.message);
        }
      }

      // Xóa trong SQLite
      await dbRun('DELETE FROM edit_logs WHERE session_id = ?', [sessionId]);
      await dbRun('DELETE FROM edit_sessions WHERE session_id = ?', [sessionId]);

      return sendJson(res, 200, { ok: true, message: 'Đã xóa vĩnh viễn ảnh và phiên làm việc' });
    }

    // -------------------------------------------------------------------------
    // API: USER DELETE MY PHOTO
    // -------------------------------------------------------------------------
    if (pathname === '/api/photo/delete' || action === 'delete_my_photo') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user) {
        return sendJson(res, 401, { ok: false, error: 'Vui lòng đăng nhập để thực hiện' });
      }

      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');
      const sessionId = payload.sessionId;
      if (!sessionId) return sendJson(res, 400, { ok: false, error: 'Missing sessionId' });

      const session = await dbGet('SELECT * FROM edit_sessions WHERE session_id = ?', [sessionId]);
      if (!session) {
        return sendJson(res, 404, { ok: false, error: 'Không tìm thấy ảnh' });
      }

      // Chỉ người sở hữu hoặc admin mới được xóa
      if (session.user_id !== user.id && user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Bạn không có quyền xóa ảnh này' });
      }

      // Xóa các file ảnh vật lý trên đĩa cứng
      const filesToDelete = [];
      if (session.original_url) filesToDelete.push(path.basename(session.original_url.split('?')[0]));
      if (session.preview_url) filesToDelete.push(path.basename(session.preview_url.split('?')[0]));

      for (const fn of filesToDelete) {
        if (!fn) continue;
        const p = path.join(UPLOAD_DIR, fn);
        try {
          if (fs.existsSync(p)) fs.unlinkSync(p);
        } catch (e) {
          console.warn('Lỗi khi xóa file ảnh:', fn, e.message);
        }
      }

      await dbRun('DELETE FROM edit_logs WHERE session_id = ?', [sessionId]);
      await dbRun('DELETE FROM edit_sessions WHERE session_id = ?', [sessionId]);

      return sendJson(res, 200, { ok: true, message: 'Đã xóa ảnh thành công' });
    }

    // -------------------------------------------------------------------------
    // API: GET SESSIONS (Dashboard monitoring)
    // -------------------------------------------------------------------------
    if (pathname === '/api/sessions' || action === 'get_sessions') {
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Yêu cầu quyền Quản Trị Viên (Admin) để xem danh sách phiên' });
      }
      const rows = await dbAll('SELECT * FROM edit_sessions ORDER BY updated_at DESC LIMIT 100');
      return sendJson(res, 200, {
        ok: true,
        sessions: rows.map(r => ({
          ...r,
          settings_json: r.settings_json ? JSON.parse(r.settings_json) : {}
        }))
      });
    }

    // -------------------------------------------------------------------------
    // API: GET LOGS (Dashboard monitoring)
    // -------------------------------------------------------------------------
    if (pathname === '/api/logs' || action === 'get_logs') {
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Yêu cầu quyền Quản Trị Viên (Admin) để xem nhật ký hệ thống' });
      }
      const sid = url.searchParams.get('sessionId');
      let rows;
      if (sid) {
        rows = await dbAll('SELECT * FROM edit_logs WHERE session_id = ? ORDER BY id DESC LIMIT 200', [sid]);
      } else {
        rows = await dbAll('SELECT * FROM edit_logs ORDER BY id DESC LIMIT 200');
      }
      return sendJson(res, 200, { ok: true, logs: rows });
    }

    // -------------------------------------------------------------------------
    // API: GET RECIPES STATUS
    // -------------------------------------------------------------------------
    
    // -------------------------------------------------------------------------
    // API: AI FILM STYLIST & SMART RECIPE MATCHER (POST & GET)
    // -------------------------------------------------------------------------
    if (pathname === '/api/ai/film-stylist' || action === 'ai_film_stylist') {
      const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
      if (!checkRateLimit(clientIp, 'ai_stylist', 60, 60 * 1000)) {
        return sendJson(res, 429, { ok: false, error: 'Bạn đang thao tác quá nhanh. Vui lòng thử lại sau giây lát.' });
      }

      let prompt = '';
      if (req.method === 'POST') {
        try {
          const raw = await readBody(req);
          const body = JSON.parse(raw.toString() || '{}');
          prompt = (body.prompt || body.query || body.text || '').trim();
        } catch (_) {}
      } else {
        prompt = (url.searchParams.get('prompt') || url.searchParams.get('q') || '').trim();
      }

      if (!prompt) {
        return sendJson(res, 400, { ok: false, error: 'Vui lòng cung cấp mô tả ảnh hoặc cảm xúc cần gợi ý màu.' });
      }

      if (prompt.length > 300) {
        prompt = prompt.slice(0, 300);
      }

      const result = analyzeFilmStylist(prompt);
      return sendJson(res, 200, result);
    }

    if (pathname === '/api/recipes/status' || action === 'get_recipes_status') {
      const recipesPath = path.join(__dirname, '../frontend/recipes.json');
      let statusData = { ok: true, count: 0, retrievedOn: null, lastSync: null };
      try {
        if (fs.existsSync(recipesPath)) {
          const cat = JSON.parse(fs.readFileSync(recipesPath, 'utf8'));
          statusData.count = cat.recipeCount || (cat.recipes ? cat.recipes.length : 0);
          statusData.retrievedOn = cat.retrievedOn || null;
          statusData.lastSync = cat.lastSync || null;
        }
      } catch (e) {
        console.warn('Error reading recipes status:', e.message);
      }
      return sendJson(res, 200, statusData);
    }

    // -------------------------------------------------------------------------
    // API: ADMIN TRIGGER SYNC RECIPES
    // -------------------------------------------------------------------------
    if (pathname === '/api/admin/sync-recipes' || action === 'admin_sync_recipes') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const user = await getAuthUser(req);
      if (!user || user.role !== 'admin') {
        return sendJson(res, 403, { ok: false, error: 'Yêu cầu quyền Quản Trị Viên (Admin) để đồng bộ công thức' });
      }

      const scriptPath = path.join(__dirname, '../scripts/sync_recipes.py');
      if (!fs.existsSync(scriptPath)) {
        return sendJson(res, 500, { ok: false, error: 'Không tìm thấy script đồng bộ' });
      }

      const { spawn } = await import('node:child_process');
      const child = spawn('python3', [scriptPath], {
        cwd: path.join(__dirname, '..'),
        env: { ...process.env, PATH: process.env.PATH }
      });

      let stdout = '';
      let stderr = '';
      child.stdout.on('data', d => { stdout += d.toString(); });
      child.stderr.on('data', d => { stderr += d.toString(); });

      const exitCode = await new Promise(resolve => child.on('close', resolve));
      
      const recipesPath = path.join(__dirname, '../frontend/recipes.json');
      let count = 0;
      try {
        if (fs.existsSync(recipesPath)) {
          const cat = JSON.parse(fs.readFileSync(recipesPath, 'utf8'));
          count = cat.recipeCount || (cat.recipes ? cat.recipes.length : 0);
        }
      } catch (_) {}

      return sendJson(res, 200, {
        ok: exitCode === 0,
        exitCode,
        message: exitCode === 0 ? 'Đã hoàn tất kiểm tra và đồng bộ công thức màu phim mới!' : 'Đồng bộ thất bại: ' + stderr,
        output: stdout,
        recipeCount: count
      });
    }

    // 404 for unknown endpoints
    return sendJson(res, 404, { ok: false, error: 'Endpoint not found' });
  } catch (err) {
    if (err.statusCode === 413 || err.message === 'PAYLOAD_TOO_LARGE') {
      return sendJson(res, 413, { ok: false, error: 'Dung lượng tải lên vượt quá giới hạn cho phép (tối đa 25MB)' });
    }
    console.error('Server error:', err);
    return sendJson(res, 500, { ok: false, error: 'Internal Server Error: ' + err.message });
  }
});

// Boot backend
initDatabase().then(() => {
  server.listen(PORT, HOST, () => {
    console.log(`\n======================================================`);
    console.log(`  IRO Studio 3.0 Backend API Service`);
    console.log(`  Cổng:    ${PORT}`);
    console.log(`  Địa chỉ: http://${HOST}:${PORT}`);
    console.log(`  Database: ${DB_PATH}`);
    console.log(`======================================================\n`);
  });
}).catch(err => {
  console.error('Failed to initialize database:', err);
  process.exit(1);
});
