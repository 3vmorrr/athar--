<?php
declare(strict_types=1);

/**
 * Independent Admin cookie-session boundary.
 *
 * public/index.php selects a dedicated PHP session name/path for /admin requests
 * before session_start(). These helpers then keep authenticated Admin state out
 * of the public website session while reusing the shared identity/password
 * verification service.
 */

function admin_is_request_path(string $path): bool
{
    return $path === '/admin' || str_starts_with($path, '/admin/');
}

function admin_current_user(): ?array
{
    return isset($_SESSION['admin_user']) && is_array($_SESSION['admin_user'])
        ? $_SESSION['admin_user']
        : null;
}

function admin_locale(): string
{
    $requested = strtolower((string) ($_GET['lang'] ?? ''));
    if (in_array($requested, ['ar', 'en'], true)) {
        $_SESSION['admin_locale'] = $requested;
    }
    $saved = strtolower((string) ($_SESSION['admin_locale'] ?? 'ar'));
    return in_array($saved, ['ar', 'en'], true) ? $saved : 'ar';
}

function admin_forget_authenticated_state(): void
{
    unset(
        $_SESSION['admin_user'],
        $_SESSION['admin_authenticated_at'],
        $_SESSION['admin_last_activity'],
        $_SESSION['admin_auth_attempts'],
        $_SESSION['csrf']
    );
}

function admin_start_authenticated_session(int $userId): array
{
    $query = db()->prepare(
        'SELECT id,email,display_name,username,role,session_version,email_verified_at,suspended_at
         FROM users WHERE id=? LIMIT 1'
    );
    $query->execute([$userId]);
    $actor = $query->fetch(PDO::FETCH_ASSOC);

    if (
        !$actor
        || !empty($actor['suspended_at'])
        || !in_array((string) ($actor['role'] ?? ''), ['admin', 'owner'], true)
    ) {
        throw new RuntimeException('Admin access denied.');
    }

    session_regenerate_id(true);
    admin_forget_authenticated_state();

    $now = time();
    $_SESSION['admin_user'] = $actor;
    $_SESSION['admin_authenticated_at'] = $now;
    $_SESSION['admin_last_activity'] = $now;
    csrf_token();

    return $actor;
}

function admin_require_auth(): array
{
    $sessionActor = admin_current_user();
    $now = time();
    $authenticatedAt = (int) ($_SESSION['admin_authenticated_at'] ?? 0);
    $lastActivity = (int) ($_SESSION['admin_last_activity'] ?? 0);

    if (
        !$sessionActor
        || !$authenticatedAt
        || $now - $authenticatedAt > 28800
        || !$lastActivity
        || $now - $lastActivity > 1800
    ) {
        admin_forget_authenticated_state();
        if (session_status() === PHP_SESSION_ACTIVE) session_regenerate_id(true);
        redirect('/admin/login');
    }

    $query = db()->prepare(
        'SELECT id,email,display_name,username,role,session_version,email_verified_at,suspended_at
         FROM users WHERE id=? LIMIT 1'
    );
    $query->execute([(int) $sessionActor['id']]);
    $actor = $query->fetch(PDO::FETCH_ASSOC);

    if (
        !$actor
        || !empty($actor['suspended_at'])
        || !in_array((string) ($actor['role'] ?? ''), ['admin', 'owner'], true)
        || (int) ($sessionActor['session_version'] ?? -1) !== (int) $actor['session_version']
    ) {
        admin_forget_authenticated_state();
        if (session_status() === PHP_SESSION_ACTIVE) session_regenerate_id(true);
        redirect('/admin/login');
    }

    $_SESSION['admin_last_activity'] = $now;
    $_SESSION['admin_user'] = $actor;
    return $actor;
}

function admin_destroy_session(): void
{
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $params = session_get_cookie_params();
        setcookie(
            session_name(),
            '',
            [
                'expires' => time() - 42000,
                'path' => (string) ($params['path'] ?? '/admin'),
                'secure' => (bool) ($params['secure'] ?? false),
                'httponly' => true,
                'samesite' => (string) ($params['samesite'] ?? 'Strict'),
            ]
        );
    }
    if (session_status() === PHP_SESSION_ACTIVE) session_destroy();
}
