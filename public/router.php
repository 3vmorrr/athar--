<?php
declare(strict_types=1);

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$publicRoot = realpath(__DIR__);
$requestedFile = realpath(__DIR__ . DIRECTORY_SEPARATOR . ltrim(rawurldecode($path), '/\\'));

if (
    $path !== '/'
    && $publicRoot !== false
    && $requestedFile !== false
    && str_starts_with($requestedFile, $publicRoot . DIRECTORY_SEPARATOR)
    && is_file($requestedFile)
    && in_array(strtolower(pathinfo($requestedFile, PATHINFO_EXTENSION)), ['css', 'js', 'png', 'jpg', 'jpeg', 'webp', 'svg', 'ico', 'webmanifest'], true)
) {
    return false;
}

require __DIR__ . '/index.php';
