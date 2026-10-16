import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync, execFile } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import sqlite3Pkg from 'sqlite3';

const sqlite3 = sqlite3Pkg.verbose();
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 5000);
const HOST = process.env.HOST || '0.0.0.0';

const DATA_DIR = path.join(__dirname, 'data');
const UPLOAD_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const DB_PATH = path.join(DATA_DIR, 'iro.db');
const db = new sqlite3.Database(DB_PATH);

// Promise helpers for sqlite3
const dbRun = (sql, params = []) => new Promise((resolve, reject) => {
  db.run(sql, params, function (err) {
    if (err) reject(err);
    else resolve({ lastID: this.lastID, changes: this.changes });
  });
});

const dbGet = (sql, params = []) => new Promise((resolve, reject) => {
  db.get(sql, params, (err, row) => {
    if (err) reject(err);
    else resolve(row || null);
  });
});

const dbAll = (sql, params = []) => new Promise((resolve, reject) => {
  db.all(sql, params, (err, rows) => {
    if (err) reject(err);
    else resolve(rows || []);
  });
});

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
  const existingAdmin = await dbGet('SELECT id FROM users WHERE username = "admin" OR email = "admin@irostudio.local"');
  if (!existingAdmin) {
    const salt = crypto.randomBytes(16).toString('hex');
    const hash = hashPassword('admin123@', salt);
    await dbRun(
      `INSERT INTO users (username, email, password_hash, salt, role, full_name, status, created_at)
       VALUES (?, ?, ?, ?, 'admin', 'Hệ Thống Quản Trị Viên', 'active', CURRENT_TIMESTAMP)`,
      ['admin', 'admin@irostudio.local', hash, salt]
    );
    console.log('[Database] Default admin account seeded: admin / admin123@');
  }
}

// Password hashing and verification
function hashPassword(password, salt) {
  const derived = crypto.scryptSync(password, salt, 64);
  return `scrypt$${salt}$${derived.toString('hex')}`;
}

function verifyPassword(password, storedHash, storedSalt) {
  if (!storedHash) return false;
  if (storedHash.startsWith('scrypt$')) {
    const parts = storedHash.split('$');
    const salt = parts[1] || storedSalt;
    const key = parts[2];
    const derived = crypto.scryptSync(password, salt, 64).toString('hex');
    return crypto.timingSafeEqual(Buffer.from(derived, 'hex'), Buffer.from(key, 'hex'));
  }
  // Fallback for bcrypt hash from PHP ($2y$ or $2a$)
  if (storedHash.startsWith('$2y$') || storedHash.startsWith('$2a$')) {
    try {
      execFileSync('php', ['-r', 'exit(password_verify($argv[1], $argv[2]) ? 0 : 1);', password, storedHash]);
      return true;
    } catch {
      return false;
    }
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

// Simple JSON response helper
function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
  });
  res.end(JSON.stringify(data));
}

// Request Body Reader
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', c => chunks.push(c));
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

// Main HTTP Request Handler
const server = http.createServer(async (req, res) => {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
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
      const filename = path.basename(pathname);
      const filePath = path.join(UPLOAD_DIR, filename);
      if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filename).toLowerCase();
        const mime = ext === '.png' ? 'image/png' : (ext === '.webp' ? 'image/webp' : 'image/jpeg');
        res.writeHead(200, {
          'Content-Type': mime,
          'Cache-Control': 'public, max-age=86400',
          'Access-Control-Allow-Origin': '*'
        });
        return fs.createReadStream(filePath).pipe(res);
      }
      return sendJson(res, 404, { ok: false, error: 'File not found' });
    }

    // -------------------------------------------------------------------------
    // API: AUTH REGISTER
    // -------------------------------------------------------------------------
    if (pathname === '/api/auth/register' || action === 'auth_register') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const username = (payload.username || '').trim().toLowerCase();
      const email = (payload.email || '').trim().toLowerCase();
      const password = payload.password || '';
      const fullName = (payload.full_name || payload.fullName || username).trim();

      if (username.length < 3 || username.length > 24 || !/^[a-z0-9_.-]+$/.test(username)) {
        return sendJson(res, 400, { ok: false, error: 'Tên đăng nhập phải từ 3-24 ký tự (chữ thường, số, dấu gạch).' });
      }
      if (!email.includes('@') || !email.includes('.')) {
        return sendJson(res, 400, { ok: false, error: 'Địa chỉ email không đúng định dạng.' });
      }
      if (password.length < 6) {
        return sendJson(res, 400, { ok: false, error: 'Mật khẩu phải có tối thiểu 6 ký tự.' });
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
    // API: INIT SESSION
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/init' || action === 'init_session') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const sessionId = (payload.sessionId || '').trim();
      if (!sessionId) return sendJson(res, 400, { ok: false, error: 'Missing sessionId' });

      const filename = path.basename(payload.filename || 'photo.jpg');
      const fileSize = Number(payload.fileSize || 0);
      const width = Number(payload.width || 0);
      const height = Number(payload.height || 0);
      const recipeName = (payload.recipeName || 'Classic Chrome').trim();

      const user = await getAuthUser(req);
      const userId = user ? user.id : null;
      const userName = user ? (user.full_name || user.username) : (payload.userName || 'Khách vãng lai').trim();

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
    // API: SYNC SETTINGS & PREVIEW
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/sync' || action === 'sync_settings') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const raw = await readBody(req);
      const payload = JSON.parse(raw.toString() || '{}');

      const sessionId = (payload.sessionId || '').trim();
      if (!sessionId) return sendJson(res, 400, { ok: false, error: 'Missing sessionId' });

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
    // API: UPLOAD ORIGINAL PHOTO (Multipart)
    // -------------------------------------------------------------------------
    if (pathname === '/api/session/upload' || action === 'upload_original') {
      if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
      const contentType = req.headers['content-type'] || '';
      const boundaryMatch = contentType.match(/boundary=(?:"([^"]+)"|([^;]+))/i);
      if (!boundaryMatch) return sendJson(res, 400, { ok: false, error: 'Missing multipart boundary' });

      const boundary = boundaryMatch[1] || boundaryMatch[2];
      const raw = await readBody(req);
      const parsed = parseMultipart(raw, boundary);

      const sessionId = (parsed.fields.sessionId || '').trim();
      const photoFile = parsed.files.photo;

      if (!sessionId || !photoFile) {
        return sendJson(res, 400, { ok: false, error: 'Missing sessionId or photo' });
      }

      const cleanSid = sessionId.replace(/[^a-zA-Z0-9_-]/g, '');
      const origExt = path.extname(photoFile.filename || '.jpg').toLowerCase() || '.jpg';
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
    // API: GET SESSIONS (Dashboard monitoring)
    // -------------------------------------------------------------------------
    if (pathname === '/api/sessions' || action === 'get_sessions') {
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
      const sid = url.searchParams.get('sessionId');
      let rows;
      if (sid) {
        rows = await dbAll('SELECT * FROM edit_logs WHERE session_id = ? ORDER BY id DESC LIMIT 200', [sid]);
      } else {
        rows = await dbAll('SELECT * FROM edit_logs ORDER BY id DESC LIMIT 200');
      }
      return sendJson(res, 200, { ok: true, logs: rows });
    }

    // 404 for unknown endpoints
    return sendJson(res, 404, { ok: false, error: 'Endpoint not found' });
  } catch (err) {
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
