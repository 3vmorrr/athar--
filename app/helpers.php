<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/backend/bootstrap.php';
require_once __DIR__.'/identity.php';

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $pdo = \Athar\Infrastructure\Database::connect();
    return $pdo;
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(32));
    return (string) $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="_csrf" value="' . e(csrf_token()) . '">';
}

function require_csrf(): void
{
    $provided = $_POST['_csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? '');
    if (!is_string($provided) || !hash_equals(csrf_token(), $provided)) {
        http_response_code(419);
        exit('انتهت صلاحية الطلب. ارجع وحاول مرة أخرى.');
    }
}

function current_user(): ?array
{
    return isset($_SESSION['user']) && is_array($_SESSION['user']) ? $_SESSION['user'] : null;
}

function require_login(): array
{
    $sessionUser = current_user();
    $now = time();
    $authenticatedAt = (int) ($_SESSION['authenticated_at'] ?? 0);
    $lastActivity = (int) ($_SESSION['last_activity'] ?? 0);
    if (!$sessionUser || !$authenticatedAt || $now - $authenticatedAt > 86400 || $now - $lastActivity > 1800) {
        unset($_SESSION['user'], $_SESSION['authenticated_at'], $_SESSION['last_activity']);
        header('Location: /login?next=' . rawurlencode($_SERVER['REQUEST_URI'] ?? '/account'), true, 303);
        exit;
    }
    $statement = db()->prepare('SELECT id,email,display_name,username,role,session_version,email_verified_at,email_marketing_opt_in,preferences_json,suspended_at FROM users WHERE id=? LIMIT 1');
    $statement->execute([(int) $sessionUser['id']]);
    $activeUser = $statement->fetch();
    if (!$activeUser || !empty($activeUser['suspended_at']) || (int) ($sessionUser['session_version'] ?? 0) !== (int) $activeUser['session_version']) {
        unset($_SESSION['user'], $_SESSION['authenticated_at'], $_SESSION['last_activity']);
        header('Location: /login', true, 303);
        exit;
    }
    if (empty($activeUser['email_verified_at']) && !in_array(($activeUser['role'] ?? 'user'), ['admin','owner'], true)) {
        $requestPath = parse_url((string) ($_SERVER['REQUEST_URI'] ?? '/'), PHP_URL_PATH) ?: '/';
        if ($requestPath !== '/account' && $requestPath !== '/logout') {
            if (str_starts_with($requestPath, '/api/')) {
                http_response_code(403);
                header('Content-Type: application/json; charset=utf-8');
                echo json_encode(['ok' => false, 'error' => 'أكد بريدك الإلكتروني أولًا.'], JSON_UNESCAPED_UNICODE);
                exit;
            }
            header('Location: /account#account-verification', true, 303);
            exit;
        }
    }
    $_SESSION['last_activity'] = $now;
    $_SESSION['user'] = $activeUser;
    return $activeUser;
}

function require_admin(): array
{
    $user = require_login();
    if (!in_array(($user['role'] ?? ''), ['admin', 'owner'], true)) {
        http_response_code(403);
        exit('غير مصرح بعرض هذه الصفحة.');
    }
    return $user;
}

function require_owner(): array
{
    $user = require_login();
    if (($user['role'] ?? '') !== 'owner') {
        http_response_code(403);
        exit('هذه العملية متاحة لمالك المنصة فقط.');
    }
    return $user;
}

function flash(string $message, string $type = 'success'): void
{
    $_SESSION['flash'] = ['message' => $message, 'type' => $type];
}

function take_flash(): ?array
{
    $message = $_SESSION['flash'] ?? null;
    unset($_SESSION['flash']);
    return is_array($message) ? $message : null;
}

function redirect(string $path): void
{
    header('Location: ' . $path, true, 303);
    exit;
}

function is_https_url(string $url): bool
{
    return \Athar\Infrastructure\HttpsUrl::valid($url);
}

function quran_ayah_count(int $surah): int
{
    require_once dirname(__DIR__) . '/backend/src/Domain/QuranIndex.php';
    return \Athar\Domain\QuranIndex::standard()->ayahCount($surah);
}

function youtube_video_id(string $url): ?string
{
    if (!is_https_url($url)) return null;
    $parts = parse_url($url);
    $host = strtolower((string) ($parts['host'] ?? ''));
    $path = (string) ($parts['path'] ?? '');
    $id = '';
    if (in_array($host, ['www.youtube.com','youtube.com','m.youtube.com'], true) && $path === '/watch') {
        parse_str((string) ($parts['query'] ?? ''), $query);
        $id = (string) ($query['v'] ?? '');
    } elseif ($host === 'youtu.be') {
        $id = trim($path, '/');
    } elseif (in_array($host, ['www.youtube-nocookie.com','youtube-nocookie.com'], true) && preg_match('#^/embed/([A-Za-z0-9_-]{11})$#', $path, $match)) {
        $id = $match[1];
    }
    return preg_match('/^[A-Za-z0-9_-]{11}$/', $id) ? $id : null;
}

function validated_lesson_scope(int $scholarId, int $seriesId): array
{
    $scholarId = max(0, $scholarId);
    $seriesId = max(0, $seriesId);
    if ($seriesId > 0) {
        $statement = db()->prepare('SELECT scholar_id FROM series WHERE id=? LIMIT 1');
        $statement->execute([$seriesId]);
        $seriesScholarId = $statement->fetchColumn();
        if ($seriesScholarId === false) throw new RuntimeException('السلسلة المحددة غير موجودة.');
        $seriesScholarId = (int) ($seriesScholarId ?: 0);
        if ($scholarId > 0 && $seriesScholarId > 0 && $scholarId !== $seriesScholarId) throw new RuntimeException('العالم المحدد لا يطابق عالم هذه السلسلة.');
        if ($scholarId === 0) $scholarId = $seriesScholarId;
    }
    if ($scholarId > 0) {
        $statement = db()->prepare('SELECT id FROM scholars WHERE id=? LIMIT 1');
        $statement->execute([$scholarId]);
        if (!$statement->fetchColumn()) throw new RuntimeException('العالم المحدد غير موجود.');
    }
    return [$scholarId, $seriesId];
}

function store_scholar_image(array $file, string $storage): string
{
    if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || (int) ($file['size'] ?? 0) > 3 * 1024 * 1024 || empty($file['tmp_name']) || !is_uploaded_file($file['tmp_name'])) {
        throw new RuntimeException('ارفع صورة صالحة لا يتجاوز حجمها 3 ميجابايت.');
    }
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
    $extensions = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'];
    $dimensions = @getimagesize($file['tmp_name']);
    if (!isset($extensions[$mime]) || !$dimensions || $dimensions[0] < 80 || $dimensions[1] < 80 || $dimensions[0] > 6000 || $dimensions[1] > 6000) {
        throw new RuntimeException('صيغة الصورة غير مدعومة. استخدم JPG أو PNG أو WebP بأبعاد مناسبة.');
    }
    if (!is_dir($storage) && !mkdir($storage, 0700, true) && !is_dir($storage)) throw new RuntimeException('تعذر إعداد تخزين الصور.');
    $key = bin2hex(random_bytes(20)) . '.' . $extensions[$mime];
    if (!move_uploaded_file($file['tmp_name'], $storage . DIRECTORY_SEPARATOR . $key)) throw new RuntimeException('تعذر حفظ صورة العالم.');
    return $key;
}

function delete_scholar_image(?string $key, string $storage): void
{
    if (!is_string($key) || !preg_match('/^[a-f0-9]{40}\.(?:jpg|png|webp)$/', $key)) return;
    $path = $storage . DIRECTORY_SEPARATOR . $key;
    if (is_file($path)) unlink($path);
}
