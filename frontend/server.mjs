import http from 'node:http';
import { createReadStream, statSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = __dirname;

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '127.0.0.1';
const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:5000';

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.cube': 'text/plain; charset=utf-8',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.ttf': 'font/ttf',
  '.apk': 'application/vnd.android.package-archive'
};

function proxyToBackend(req, res) {
  const backendTarget = new URL(BACKEND_URL);
  const options = {
    hostname: backendTarget.hostname,
    port: backendTarget.port,
    path: req.url,
    method: req.method,
    headers: {
      ...req.headers,
      host: backendTarget.host,
      'x-forwarded-for': req.headers['x-forwarded-for'] || req.socket.remoteAddress || '',
      'x-forwarded-proto': 'http'
    }
  };

  const proxyReq = http.request(options, proxyRes => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });

  proxyReq.on('error', err => {
    console.error('[Frontend Proxy Error]', err.message);
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ ok: false, error: 'Dịch vụ Backend hiện không khả dụng (502 Bad Gateway)' }));
  });

  req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = url.pathname;

    // Chuyển tiếp các yêu cầu API và Ảnh tải lên sang Backend (Port 5000)
    if (pathname.startsWith('/api/') || pathname === '/api.php' || pathname.startsWith('/api.php') || pathname.startsWith('/uploads/')) {
      return proxyToBackend(req, res);
    }

    let reqPath = decodeURIComponent(pathname);
    if (reqPath === '/' || reqPath === '') reqPath = '/index.html';

    const safePath = path.normalize(path.join(root, reqPath));
    if (!safePath.startsWith(root)) {
      res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('403 Forbidden');
    }

    const basename = path.basename(safePath).toLowerCase();
    const ext = path.extname(safePath).toLowerCase();

    // Security Hardening: Chặn tuyệt đối tải về mã nguồn, cấu hình, test, script, file ẩn
    const isSensitive =
      basename.startsWith('.') ||
      basename === 'server.mjs' ||
      basename === 'api.php' ||
      basename.startsWith('package') ||
      basename.endsWith('.test.mjs') ||
      basename === 'tests.mjs' ||
      basename.endsWith('.sh') ||
      basename.endsWith('.conf') ||
      basename.endsWith('.md') ||
      basename.endsWith('.service') ||
      basename.includes('docker') ||
      basename === 'caddyfile';

    if (isSensitive || !mimeTypes[ext]) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('404 Not Found');
    }

    if (!existsSync(safePath)) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('404 Not Found');
    }

    const stat = statSync(safePath);
    if (!stat.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('404 Not Found');
    }

    const contentType = mimeTypes[ext] || 'application/octet-stream';
    const acceptEncoding = req.headers['accept-encoding'] || '';

    // Modern Security Headers
    const headers = {
      'Content-Type': contentType,
      ...(ext === '.apk' ? { 'Content-Disposition': `attachment; filename="${basename}"` } : {}),
      'Access-Control-Allow-Origin': '*',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: blob: https:; connect-src 'self' https:; object-src 'none'; base-uri 'self'; frame-ancestors 'none';",
      'Vary': 'Accept-Encoding'
    };

    if (['.html', '.json', '.css', '.js', '.mjs'].includes(ext)) {
      headers['Cache-Control'] = 'no-cache';
    } else {
      headers['Cache-Control'] = 'public, max-age=86400';
    }

    if (/\bgzip\b/.test(acceptEncoding) && stat.size > 1024) {
      headers['Content-Encoding'] = 'gzip';
      res.writeHead(200, headers);
      const rawStream = createReadStream(safePath);
      const gzip = zlib.createGzip({ level: 6 });
      rawStream.pipe(gzip).pipe(res);
    } else {
      headers['Content-Length'] = stat.size;
      res.writeHead(200, headers);
      createReadStream(safePath).pipe(res);
    }
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('500 Internal Server Error: ' + err.message);
  }
});

server.listen(PORT, HOST, () => {
  const actualPort = server.address() ? server.address().port : PORT;
  console.log(`Local: http://localhost:${actualPort}`);
  console.log(`\n======================================================`);
  console.log(`  IRO Studio 3.0 Frontend Server (Hardened)`);
  console.log(`  Cổng:    ${actualPort}`);
  console.log(`  Địa chỉ: http://${HOST}:${actualPort}`);
  console.log(`  Proxy API -> ${BACKEND_URL}`);
  console.log(`======================================================\n`);
});
