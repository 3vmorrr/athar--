<?php
declare(strict_types=1);

spl_autoload_register(static function (string $class): void {
    $prefix = 'Athar\\';
    if (str_starts_with($class, $prefix)) {
        $relative = substr($class, strlen($prefix));
        if (!preg_match('/^[A-Za-z0-9_\\\\]+$/', $relative)) return;
        $path = __DIR__ . '/src/' . str_replace('\\', '/', $relative) . '.php';
        if (is_file($path)) require_once $path;
    }
});

// Local configuration is data only; process environment always takes precedence.
$environmentPath = dirname(__DIR__) . '/.env';
if (getenv('ATHAR_LOAD_DOTENV') !== '0' && is_file($environmentPath)) {
    foreach (file($environmentPath, FILE_IGNORE_NEW_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || $line[0] === '#' || !str_contains($line, '=')) continue;
        [$name, $value] = array_map('trim', explode('=', $line, 2));
        if (!preg_match('/^(?:ATHAR_[A-Z0-9_]+|APP_ENV)$/', $name) || getenv($name) !== false) continue;
        if (strlen($value) >= 2 && (($value[0] === '"' && substr($value, -1) === '"') || ($value[0] === "'" && substr($value, -1) === "'"))) $value = substr($value, 1, -1);
        putenv($name . '=' . $value);
    }
}
