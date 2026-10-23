<?php
declare(strict_types=1);

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $host = getenv('ATHAR_DB_HOST') ?: '127.0.0.1';
    $port = getenv('ATHAR_DB_PORT') ?: '3306';
    $name = getenv('ATHAR_DB_NAME') ?: 'athar';
    $user = getenv('ATHAR_DB_USER') ?: 'root';
    $password = getenv('ATHAR_DB_PASSWORD') ?: '';
    $pdo = new PDO("mysql:host={$host};port={$port};dbname={$name};charset=utf8mb4", $user, $password, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
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
    $statement = db()->prepare('SELECT id,email,role FROM users WHERE id=? LIMIT 1');
    $statement->execute([(int) $sessionUser['id']]);
    $activeUser = $statement->fetch();
    if (!$activeUser) {
        unset($_SESSION['user'], $_SESSION['authenticated_at'], $_SESSION['last_activity']);
        header('Location: /login', true, 303);
        exit;
    }
    $_SESSION['last_activity'] = $now;
    $_SESSION['user'] = $activeUser;
    return $activeUser;
}

function require_admin(): array
{
    $user = require_login();
    if (($user['role'] ?? '') !== 'admin') {
        http_response_code(403);
        exit('غير مصرح بعرض هذه الصفحة.');
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
    if (strlen($url) > 500 || filter_var($url, FILTER_VALIDATE_URL) === false) return false;
    $parts = parse_url($url);
    return is_array($parts) && strtolower((string) ($parts['scheme'] ?? '')) === 'https' && isset($parts['host']);
}

function quran_ayah_count(int $surah): int
{
    static $counts = [7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6];
    return $counts[$surah - 1] ?? 0;
}
