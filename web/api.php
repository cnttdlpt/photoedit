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
        user_id INTEGER DEFAULT NULL,
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

    // Tự động bổ sung cột user_id nếu database cũ chưa có
    $colCheck = $db->query("PRAGMA table_info(edit_sessions)");
    $cols = [];
    while ($c = $colCheck->fetchArray(SQLITE3_ASSOC)) {
        $cols[] = $c['name'];
    }
    if (!in_array('user_id', $cols)) {
        @$db->exec('ALTER TABLE edit_sessions ADD COLUMN user_id INTEGER DEFAULT NULL');
    }

    // Khởi tạo bảng edit_logs để lưu lịch sử từng thao tác chỉnh sửa
    $db->exec('CREATE TABLE IF NOT EXISTS edit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        action TEXT NOT NULL,
        detail TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );');

    // Khởi tạo bảng users quản lý tài khoản người dùng và quản trị viên
    $db->exec('CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT DEFAULT "user",
        full_name TEXT,
        avatar_url TEXT,
        status TEXT DEFAULT "active",
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        last_login_at DATETIME
    );');

    // Khởi tạo bảng user_tokens xác thực phiên đăng nhập
    $db->exec('CREATE TABLE IF NOT EXISTS user_tokens (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        token TEXT UNIQUE NOT NULL,
        expires_at DATETIME NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );');

    // Khởi tạo tài khoản Quản trị viên (Admin) mặc định nếu chưa có
    $adminCount = $db->querySingle('SELECT COUNT(*) FROM users WHERE role = "admin"');
    if (!$adminCount) {
        $defaultAdminPass = password_hash('admin123@', PASSWORD_DEFAULT);
        $initAdmin = $db->prepare('INSERT INTO users (username, email, password_hash, role, full_name, status)
            VALUES ("admin", "admin@irostudio.local", :pwd, "admin", "Hệ Thống Quản Trị Viên", "active")');
        $initAdmin->bindValue(':pwd', $defaultAdminPass, SQLITE3_TEXT);
        $initAdmin->execute();
    }
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

function getBearerToken() {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if (!$header && function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        $header = $headers['Authorization'] ?? $headers['authorization'] ?? $headers['X-Auth-Token'] ?? '';
    }
    if (!$header) {
        $header = $_SERVER['HTTP_X_AUTH_TOKEN'] ?? '';
    }
    if ($header && preg_match('/Bearer\s+(\S+)/i', $header, $matches)) {
        return $matches[1];
    }
    if ($header && !str_contains($header, ' ')) {
        return trim($header);
    }
    return $_COOKIE['iro_token'] ?? $_GET['token'] ?? $_POST['token'] ?? null;
}

function getSafeUser($user) {
    if (!$user) return null;
    return [
        'id' => intval($user['id']),
        'username' => $user['username'],
        'email' => $user['email'],
        'role' => $user['role'] ?? 'user',
        'full_name' => $user['full_name'] ?: $user['username'],
        'avatar_url' => $user['avatar_url'] ?? null,
        'status' => $user['status'] ?? 'active',
        'created_at' => $user['created_at'] ?? null,
        'last_login_at' => $user['last_login_at'] ?? null
    ];
}

function getCurrentUser($db) {
    $token = getBearerToken();
    if (!$token) return null;
    $stmt = $db->prepare('SELECT u.* FROM users u
        INNER JOIN user_tokens t ON t.user_id = u.id
        WHERE t.token = :tok AND t.expires_at > CURRENT_TIMESTAMP');
    $stmt->bindValue(':tok', $token, SQLITE3_TEXT);
    $res = $stmt->execute();
    $user = $res->fetchArray(SQLITE3_ASSOC);
    if ($user && $user['status'] === 'active') {
        return $user;
    }
    return null;
}

function requireAuth($db) {
    $user = getCurrentUser($db);
    if (!$user) {
        http_response_code(401);
        echo json_encode(['ok' => false, 'error' => 'Vui lòng đăng nhập để thực hiện']);
        exit;
    }
    return $user;
}

function requireAdmin($db) {
    $user = requireAuth($db);
    if ($user['role'] !== 'admin') {
        http_response_code(403);
        echo json_encode(['ok' => false, 'error' => 'Bạn không có quyền quản trị viên']);
        exit;
    }
    return $user;
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

    // Tự động gán tài khoản người dùng nếu đã đăng nhập
    $currUser = getCurrentUser($db);
    $userId = $currUser ? intval($currUser['id']) : null;
    $userName = $currUser ? ($currUser['full_name'] ?: $currUser['username']) : trim($payload['userName'] ?? 'Khách vãng lai');

    $recipeName = trim($payload['recipeName'] ?? 'Classic Chrome');
    $clientIp = getClientIP();
    $userAgent = $_SERVER['HTTP_USER_AGENT'] ?? '';

    $stmt = $db->prepare('INSERT INTO edit_sessions (session_id, user_id, client_ip, user_agent, user_name, filename, file_size, width, height, recipe_name, updated_at)
        VALUES (:sid, :uid, :ip, :ua, :user, :fn, :fs, :w, :h, :rec, CURRENT_TIMESTAMP)
        ON CONFLICT(session_id) DO UPDATE SET
            user_id = coalesce(:uid, user_id),
            filename = :fn,
            file_size = :fs,
            width = :w,
            height = :h,
            updated_at = CURRENT_TIMESTAMP');

    $stmt->bindValue(':sid', $sessionId, SQLITE3_TEXT);
    $stmt->bindValue(':uid', $userId, $userId ? SQLITE3_INTEGER : SQLITE3_NULL);
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
    $logStmt->bindValue(':detail', "Tải ảnh: {$filename} ({$width}x{$height}px) bởi {$userName}", SQLITE3_TEXT);
    $logStmt->execute();

    echo json_encode(['ok' => true, 'sessionId' => $sessionId, 'userId' => $userId, 'userName' => $userName]);
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

// =============================================================================
// ACTION: auth_register (Đăng ký tài khoản người dùng mới)
// =============================================================================
if ($action === 'auth_register') {
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $username = strtolower(trim($payload['username'] ?? ''));
    $email = strtolower(trim($payload['email'] ?? ''));
    $password = $payload['password'] ?? '';
    $fullName = trim($payload['fullName'] ?? $username);

    if (!preg_match('/^[a-z0-9_]{3,24}$/', $username)) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Tên đăng nhập phải từ 3-24 ký tự (chữ thường, số, dấu gạch dưới).']);
        exit;
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Địa chỉ email không đúng định dạng.']);
        exit;
    }

    if (strlen($password) < 6) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Mật khẩu phải có tối thiểu 6 ký tự.']);
        exit;
    }

    // Kiểm tra trùng username hoặc email
    $checkUser = $db->prepare('SELECT id FROM users WHERE username = :u OR email = :e LIMIT 1');
    $checkUser->bindValue(':u', $username, SQLITE3_TEXT);
    $checkUser->bindValue(':e', $email, SQLITE3_TEXT);
    $exists = $checkUser->execute()->fetchArray(SQLITE3_ASSOC);
    if ($exists) {
        http_response_code(409);
        echo json_encode(['ok' => false, 'error' => 'Tên đăng nhập hoặc email này đã được sử dụng.']);
        exit;
    }

    $hash = password_hash($password, PASSWORD_DEFAULT);
    $ins = $db->prepare('INSERT INTO users (username, email, password_hash, full_name, role, status, last_login_at)
        VALUES (:u, :e, :h, :fn, "user", "active", CURRENT_TIMESTAMP)');
    $ins->bindValue(':u', $username, SQLITE3_TEXT);
    $ins->bindValue(':e', $email, SQLITE3_TEXT);
    $ins->bindValue(':h', $hash, SQLITE3_TEXT);
    $ins->bindValue(':fn', $fullName, SQLITE3_TEXT);
    $ins->execute();

    $newUserId = $db->lastInsertRowID();
    $token = bin2hex(random_bytes(32));
    $exp = date('Y-m-d H:i:s', time() + 30 * 86400);

    $tokStmt = $db->prepare('INSERT INTO user_tokens (user_id, token, expires_at) VALUES (:uid, :tok, :exp)');
    $tokStmt->bindValue(':uid', $newUserId, SQLITE3_INTEGER);
    $tokStmt->bindValue(':tok', $token, SQLITE3_TEXT);
    $tokStmt->bindValue(':exp', $exp, SQLITE3_TEXT);
    $tokStmt->execute();

    $fetchUser = $db->query("SELECT * FROM users WHERE id = {$newUserId}")->fetchArray(SQLITE3_ASSOC);

    echo json_encode([
        'ok' => true,
        'token' => $token,
        'user' => getSafeUser($fetchUser),
        'message' => 'Đăng ký tài khoản thành công!'
    ]);
    exit;
}

// =============================================================================
// ACTION: auth_login (Đăng nhập bằng Tên đăng nhập hoặc Email)
// =============================================================================
if ($action === 'auth_login') {
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $login = strtolower(trim($payload['account'] ?? $payload['login'] ?? $payload['username'] ?? $payload['email'] ?? ''));
    $password = $payload['password'] ?? '';

    if (!$login || !$password) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Vui lòng nhập tên đăng nhập/email và mật khẩu.']);
        exit;
    }

    $stmt = $db->prepare('SELECT * FROM users WHERE username = :l OR email = :l LIMIT 1');
    $stmt->bindValue(':l', $login, SQLITE3_TEXT);
    $user = $stmt->execute()->fetchArray(SQLITE3_ASSOC);

    if (!$user || !password_verify($password, $user['password_hash'])) {
        http_response_code(401);
        echo json_encode(['ok' => false, 'error' => 'Tên đăng nhập hoặc mật khẩu không chính xác.']);
        exit;
    }

    if ($user['status'] === 'banned') {
        http_response_code(403);
        echo json_encode(['ok' => false, 'error' => 'Tài khoản của bạn đã bị quản trị viên tạm khóa.']);
        exit;
    }

    // Cập nhật thời điểm đăng nhập
    $db->exec("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = {$user['id']}");

    // Tạo mã token phiên
    $token = bin2hex(random_bytes(32));
    $exp = date('Y-m-d H:i:s', time() + 30 * 86400);

    $tokStmt = $db->prepare('INSERT INTO user_tokens (user_id, token, expires_at) VALUES (:uid, :tok, :exp)');
    $tokStmt->bindValue(':uid', $user['id'], SQLITE3_INTEGER);
    $tokStmt->bindValue(':tok', $token, SQLITE3_TEXT);
    $tokStmt->bindValue(':exp', $exp, SQLITE3_TEXT);
    $tokStmt->execute();

    echo json_encode([
        'ok' => true,
        'token' => $token,
        'user' => getSafeUser($user),
        'message' => 'Đăng nhập thành công!'
    ]);
    exit;
}

// =============================================================================
// ACTION: auth_me (Lấy thông tin tài khoản đang đăng nhập)
// =============================================================================
if ($action === 'auth_me') {
    $user = getCurrentUser($db);
    echo json_encode([
        'ok' => true,
        'authenticated' => (bool)$user,
        'user' => getSafeUser($user)
    ]);
    exit;
}

// =============================================================================
// ACTION: auth_logout (Đăng xuất khỏi phiên)
// =============================================================================
if ($action === 'auth_logout') {
    $token = getBearerToken();
    if ($token) {
        $stmt = $db->prepare('DELETE FROM user_tokens WHERE token = :tok');
        $stmt->bindValue(':tok', $token, SQLITE3_TEXT);
        $stmt->execute();
    }
    echo json_encode(['ok' => true, 'message' => 'Đã đăng xuất']);
    exit;
}

// =============================================================================
// ACTION: get_my_photos (Lấy danh sách các ảnh đã sửa của user đang đăng nhập)
// =============================================================================
if ($action === 'get_my_photos') {
    $user = requireAuth($db);
    $stmt = $db->prepare('SELECT * FROM edit_sessions WHERE user_id = :uid ORDER BY updated_at DESC LIMIT 60');
    $stmt->bindValue(':uid', $user['id'], SQLITE3_INTEGER);
    $res = $stmt->execute();

    $sessions = [];
    while ($row = $res->fetchArray(SQLITE3_ASSOC)) {
        $row['settings'] = json_decode($row['settings_json'] ?? '{}', true);
        unset($row['settings_json']);
        $sessions[] = $row;
    }

    echo json_encode(['ok' => true, 'sessions' => $sessions]);
    exit;
}

// =============================================================================
// ACTION: admin_get_users (Danh sách tài khoản dành cho Quản trị viên)
// =============================================================================
if ($action === 'admin_get_users') {
    requireAdmin($db);
    $query = "SELECT u.id, u.username, u.email, u.full_name, u.role, u.status, u.created_at, u.last_login_at,
                     COUNT(s.id) AS photo_count,
                     COALESCE(SUM(s.edit_count), 0) AS total_edits
              FROM users u
              LEFT JOIN edit_sessions s ON s.user_id = u.id
              GROUP BY u.id
              ORDER BY u.created_at DESC";
    $res = $db->query($query);
    $userList = [];
    while ($row = $res->fetchArray(SQLITE3_ASSOC)) {
        $userList[] = $row;
    }

    echo json_encode(['ok' => true, 'users' => $userList]);
    exit;
}

// =============================================================================
// ACTION: admin_update_user (Quản trị viên cập nhật quyền / khóa tài khoản)
// =============================================================================
if ($action === 'admin_update_user') {
    $admin = requireAdmin($db);
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $targetUserId = intval($payload['userId'] ?? 0);
    if (!$targetUserId) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Thiếu userId']);
        exit;
    }

    $updates = [];
    if (isset($payload['role']) && in_array($payload['role'], ['admin', 'user'])) {
        if ($targetUserId === intval($admin['id']) && $payload['role'] !== 'admin') {
            http_response_code(400);
            echo json_encode(['ok' => false, 'error' => 'Không thể tự giáng cấp tài khoản quản trị của chính bạn']);
            exit;
        }
        $updates[] = "role = '" . SQLite3::escapeString($payload['role']) . "'";
    }

    if (isset($payload['status']) && in_array($payload['status'], ['active', 'banned'])) {
        if ($targetUserId === intval($admin['id'])) {
            http_response_code(400);
            echo json_encode(['ok' => false, 'error' => 'Không thể tự khóa tài khoản của chính bạn']);
            exit;
        }
        $updates[] = "status = '" . SQLite3::escapeString($payload['status']) . "'";
    }

    if (!empty($payload['newPassword']) && strlen($payload['newPassword']) >= 6) {
        $newHash = password_hash($payload['newPassword'], PASSWORD_DEFAULT);
        $updates[] = "password_hash = '" . SQLite3::escapeString($newHash) . "'";
    }

    if (!empty($updates)) {
        $sql = "UPDATE users SET " . implode(', ', $updates) . " WHERE id = {$targetUserId}";
        $db->exec($sql);
    }

    echo json_encode(['ok' => true, 'message' => 'Đã cập nhật thông tin người dùng']);
    exit;
}

// =============================================================================
// ACTION: admin_delete_user (Quản trị viên xóa người dùng)
// =============================================================================
if ($action === 'admin_delete_user') {
    $admin = requireAdmin($db);
    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true) ?: [];

    $targetUserId = intval($payload['userId'] ?? 0);
    if ($targetUserId === intval($admin['id'])) {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Không thể tự xóa tài khoản của chính bạn']);
        exit;
    }

    $db->exec("DELETE FROM user_tokens WHERE user_id = {$targetUserId}");
    $db->exec("DELETE FROM users WHERE id = {$targetUserId}");

    echo json_encode(['ok' => true, 'message' => 'Đã xóa người dùng thành công']);
    exit;
}

// Mặc định trả về thông tin API
echo json_encode([
    'ok' => true,
    'service' => 'IRO Studio 3.0 Realtime Telemetry & Auth API',
    'status' => 'active',
    'database' => 'SQLite3',
    'endpoints' => [
        'auth_register' => 'POST /api.php?action=auth_register',
        'auth_login' => 'POST /api.php?action=auth_login',
        'auth_me' => 'GET /api.php?action=auth_me',
        'auth_logout' => 'POST /api.php?action=auth_logout',
        'admin_get_users' => 'GET /api.php?action=admin_get_users',
        'init_session' => 'POST /api.php?action=init_session',
        'upload_original' => 'POST /api.php?action=upload_original',
        'sync_settings' => 'POST /api.php?action=sync_settings',
        'get_sessions' => 'GET /api.php?action=get_sessions',
        'get_logs' => 'GET /api.php?action=get_logs&sessionId=...'
    ]
]);

