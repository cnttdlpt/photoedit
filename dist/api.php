<?php
/**
 * IRO Studio 3.0 — Realtime Photo & Live Edit Tracking API
 * Database: SQLite3 (data/iro.db) in WAL mode
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Requested-With');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$dbPath = __DIR__ . '/data/iro.db';
$uploadDir = __DIR__ . '/uploads';

if (!is_dir(__DIR__ . '/data')) {
    @mkdir(__DIR__ . '/data', 0777, true);
}
if (!is_dir($uploadDir)) {
    @mkdir($uploadDir, 0777, true);
}

try {
    $db = new SQLite3($dbPath);
    $db->busyTimeout(5000);
    $db->exec('PRAGMA journal_mode = WAL;');
    $db->exec('PRAGMA synchronous = NORMAL;');

    // Khởi tạo bảng edit_sessions
    $db->exec('CREATE TABLE IF NOT EXISTS edit_sessions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT UNIQUE NOT NULL,
        client_ip TEXT,
        user_agent TEXT,
        user_name TEXT,
        filename TEXT,
        file_size INTEGER DEFAULT 0,
        width INTEGER DEFAULT 0,
        height INTEGER DEFAULT 0,
        original_url TEXT,
        preview_url TEXT,
        recipe_name TEXT DEFAULT "Classic Chrome",
        settings_json TEXT DEFAULT "{}",
        edit_count INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );');

    // Khởi tạo bảng edit_logs để lưu lịch sử từng thao tác chỉnh sửa
    $db->exec('CREATE TABLE IF NOT EXISTS edit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        action TEXT NOT NULL,
        detail TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );');
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'Database initialization failed: ' . $e->getMessage()]);
    exit;
}

function getClientIP() {
    if (!empty($_SERVER['HTTP_CF_CONNECTING_IP'])) return $_SERVER['HTTP_CF_CONNECTING_IP'];
    if (!empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $parts = explode(',', $_SERVER['HTTP_X_FORWARDED_FOR']);
        return trim($parts[0]);
    }
    return $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';
}

$action = $_GET['action'] ?? '';

// =============================================================================
// ACTION: init_session (Khởi tạo phiên làm việc khi người dùng tải ảnh lên)
// =============================================================================
if ($action === 'init_session') {
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $sessionId = trim($payload['sessionId'] ?? '');
    if (!$sessionId) {
        $sessionId = 'sess_' . time() . '_' . bin2hex(random_bytes(4));
    }

    $filename = basename($payload['filename'] ?? 'photo.jpg');
    $fileSize = intval($payload['fileSize'] ?? 0);
    $width = intval($payload['width'] ?? 0);
    $height = intval($payload['height'] ?? 0);
    $userName = trim($payload['userName'] ?? 'Khách viếng thăm');
    $recipeName = trim($payload['recipeName'] ?? 'Classic Chrome');
    $clientIp = getClientIP();
    $userAgent = $_SERVER['HTTP_USER_AGENT'] ?? '';

    $stmt = $db->prepare('INSERT INTO edit_sessions (session_id, client_ip, user_agent, user_name, filename, file_size, width, height, recipe_name, updated_at)
        VALUES (:sid, :ip, :ua, :user, :fn, :fs, :w, :h, :rec, CURRENT_TIMESTAMP)
        ON CONFLICT(session_id) DO UPDATE SET
            filename = :fn,
            file_size = :fs,
            width = :w,
            height = :h,
            updated_at = CURRENT_TIMESTAMP');

    $stmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $stmt->bindValue(':ip', $clientIp, SQLITE3_TEXT);
    $stmt->bindValue(':ua', $userAgent, SQLITE3_TEXT);
    $stmt->bindValue(':user', $userName, SQLITE3_TEXT);
    $stmt->bindValue(':fn', $filename, SQLITE3_TEXT);
    $stmt->bindValue(':fs', $fileSize, SQLITE3_INTEGER);
    $stmt->bindValue(':w', $width, SQLITE3_INTEGER);
    $stmt->bindValue(':h', $height, SQLITE3_INTEGER);
    $stmt->bindValue(':rec', $recipeName, SQLITE3_TEXT);
    $stmt->execute();

    // Log khởi tạo
    $logStmt = $db->prepare('INSERT INTO edit_logs (session_id, action, detail) VALUES (:sid, "init", :detail)');
    $logStmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $logStmt->bindValue(':detail', "Tải ảnh: {$filename} ({$width}x{$height}px)", SQLITE3_TEXT);
    $logStmt->execute();

    echo json_encode(['ok' => true, 'sessionId' => $sessionId]);
    exit;
}

// =============================================================================
// ACTION: convert_image (Tự động chuyển đổi định dạng HEIC, RAW, TIFF sang JPEG)
// =============================================================================
if ($action === 'convert_image') {
    $uploadedFile = $_FILES['photo'] ?? null;
    if (!$uploadedFile || $uploadedFile['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'No file uploaded or upload error: ' . ($uploadedFile['error'] ?? 'empty')]);
        exit;
    }
    $tmpSrc = $uploadedFile['tmp_name'];
    $tmpDst = tempnam(sys_get_temp_dir(), 'iro_conv_') . '.jpg';
    $cmd = 'convert ' . escapeshellarg($tmpSrc . '[0]') . ' -auto-orient -quality 92 ' . escapeshellarg($tmpDst);
    exec($cmd, $cmdOut, $retVal);
    if ($retVal === 0 && file_exists($tmpDst) && filesize($tmpDst) > 0) {
        header('Content-Type: image/jpeg');
        header('Content-Length: ' . filesize($tmpDst));
        readfile($tmpDst);
        @unlink($tmpDst);
        exit;
    } else {
        http_response_code(500);
        echo json_encode(['ok' => false, 'error' => 'Image conversion failed']);
        exit;
    }
}

// =============================================================================
// ACTION: upload_original (Lưu bản ảnh gốc lên máy chủ)
// =============================================================================
if ($action === 'upload_original') {
    $sessionId = $_POST['sessionId'] ?? $_GET['sessionId'] ?? '';
    if (!$sessionId) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Missing sessionId']);
        exit;
    }

    $uploadedFile = $_FILES['photo'] ?? null;
    if (!$uploadedFile || $uploadedFile['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Upload error: ' . ($uploadedFile['error'] ?? 'no file')]);
        exit;
    }

    $ext = strtolower(pathinfo($uploadedFile['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, ['jpg', 'jpeg', 'png', 'webp', 'heic', 'avif'])) {
        $ext = 'jpg';
    }

    $targetName = 'orig_' . preg_replace('/[^a-zA-Z0-9_-]/', '', $sessionId) . '.' . $ext;
    $targetPath = $uploadDir . '/' . $targetName;

    if (move_uploaded_file($uploadedFile['tmp_name'], $targetPath)) {
        $origUrl = 'uploads/' . $targetName;
        $stmt = $db->prepare('UPDATE edit_sessions SET original_url = :url, updated_at = CURRENT_TIMESTAMP WHERE session_id = :sid');
        $stmt->bindValue(':url', $origUrl, SQLITE3_TEXT);
        $stmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
        $stmt->execute();

        echo json_encode(['ok' => true, 'originalUrl' => $origUrl]);
    } else {
        http_response_code(500);
        echo json_encode(['ok' => false, 'error' => 'Failed to save uploaded file']);
    }
    exit;
}

// =============================================================================
// ACTION: sync_settings (CẬP NHẬT THỜI GIAN THỰC TỪNG SLIDER & PREVIEW ẢNH)
// =============================================================================
if ($action === 'sync_settings') {
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $sessionId = trim($payload['sessionId'] ?? '');
    if (!$sessionId) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Missing sessionId']);
        exit;
    }

    $recipeName = trim($payload['recipeName'] ?? 'Tự chỉnh');
    $settingsJson = json_encode($payload['settings'] ?? [], JSON_UNESCAPED_UNICODE);
    $actionDesc = trim($payload['actionDesc'] ?? 'Chỉnh sửa thông số');
    $previewJpegData = $payload['previewJpeg'] ?? null;

    $previewUrl = null;
    if ($previewJpegData && str_starts_with($previewJpegData, 'data:image/')) {
        $comma = strpos($previewJpegData, ',');
        if ($comma !== false) {
            $binary = base64_decode(substr($previewJpegData, $comma + 1));
            if ($binary) {
                $prevName = 'prev_' . preg_replace('/[^a-zA-Z0-9_-]/', '', $sessionId) . '.jpg';
                file_put_contents($uploadDir . '/' . $prevName, $binary);
                $previewUrl = 'uploads/' . $prevName . '?v=' . time();
            }
        }
    }

    if ($previewUrl) {
        $stmt = $db->prepare('UPDATE edit_sessions SET 
            recipe_name = :rec,
            settings_json = :set,
            preview_url = :prev,
            edit_count = edit_count + 1,
            updated_at = CURRENT_TIMESTAMP
            WHERE session_id = :sid');
        $stmt->bindValue(':prev', $previewUrl, SQLITE3_TEXT);
    } else {
        $stmt = $db->prepare('UPDATE edit_sessions SET 
            recipe_name = :rec,
            settings_json = :set,
            edit_count = edit_count + 1,
            updated_at = CURRENT_TIMESTAMP
            WHERE session_id = :sid');
    }

    $stmt->bindValue(':rec', $recipeName, SQLITE3_TEXT);
    $stmt->bindValue(':set', $settingsJson, SQLITE3_TEXT);
    $stmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $stmt->execute();

    // Ghi nhận log thao tác
    $logStmt = $db->prepare('INSERT INTO edit_logs (session_id, action, detail) VALUES (:sid, "edit", :detail)');
    $logStmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $logStmt->bindValue(':detail', $actionDesc, SQLITE3_TEXT);
    $logStmt->execute();

    echo json_encode(['ok' => true, 'synced' => true, 'previewUrl' => $previewUrl]);
    exit;
}

// =============================================================================
// ACTION: get_sessions (Lấy danh sách các phiên đang sửa trên máy chủ)
// =============================================================================
if ($action === 'get_sessions') {
    $limit = min(100, max(1, intval($_GET['limit'] ?? 50)));
    $res = $db->query("SELECT * FROM edit_sessions ORDER BY updated_at DESC LIMIT {$limit}");
    
    $sessions = [];
    while ($row = $res->fetchArray(SQLITE3_ASSOC)) {
        $row['settings'] = json_decode($row['settings_json'] ?? '{}', true);
        unset($row['settings_json']);
        $sessions[] = $row;
    }

    // Đếm tổng số
    $totalCount = $db->querySingle('SELECT COUNT(*) FROM edit_sessions') ?: 0;
    $totalEdits = $db->querySingle('SELECT SUM(edit_count) FROM edit_sessions') ?: 0;

    echo json_encode([
        'ok' => true,
        'totalCount' => $totalCount,
        'totalEdits' => $totalEdits,
        'sessions' => $sessions
    ]);
    exit;
}

// =============================================================================
// ACTION: get_logs (Lấy lịch sử chi tiết từng bước chỉnh của 1 ảnh)
// =============================================================================
if ($action === 'get_logs') {
    $sessionId = trim($_GET['sessionId'] ?? '');
    $stmt = $db->prepare('SELECT * FROM edit_logs WHERE session_id = :sid ORDER BY id DESC LIMIT 50');
    $stmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $res = $stmt->execute();

    $logs = [];
    while ($row = $res->fetchArray(SQLITE3_ASSOC)) {
        $logs[] = $row;
    }

    echo json_encode(['ok' => true, 'logs' => $logs]);
    exit;
}

// Mặc định trả về thông tin API
echo json_encode([
    'ok' => true,
    'service' => 'IRO Studio 3.0 Realtime Telemetry API',
    'status' => 'active',
    'database' => 'SQLite3',
    'endpoints' => [
        'init_session' => 'POST /api.php?action=init_session',
        'upload_original' => 'POST /api.php?action=upload_original',
        'sync_settings' => 'POST /api.php?action=sync_settings',
        'get_sessions' => 'GET /api.php?action=get_sessions',
        'get_logs' => 'GET /api.php?action=get_logs&sessionId=...'
    ]
]);
