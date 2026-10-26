<?php
declare(strict_types=1);

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$publicRoot = realpath(__DIR__);
$requestedFile = realpath(__DIR__ . DIRECTORY_SEPARATOR . ltrim(rawurldecode($path), '/\\'));

if ($path === '/athar-lan-ca.mobileconfig' && $requestedFile !== false && is_file($requestedFile)) {
    header('Content-Type: application/x-apple-aspen-config');
    header('Content-Disposition: attachment; filename="Athar-Local-HTTPS.mobileconfig"');
    readfile($requestedFile);
    exit;
}

if (
    $path !== '/'
    && $publicRoot !== false
    && $requestedFile !== false
    && str_starts_with($requestedFile, $publicRoot . DIRECTORY_SEPARATOR)
    && is_file($requestedFile)
    && in_array(strtolower(pathinfo($requestedFile, PATHINFO_EXTENSION)), ['css', 'js', 'json', 'onnx', 'ttf', 'woff', 'woff2', 'otf', 'png', 'jpg', 'jpeg', 'webp', 'svg', 'ico', 'webmanifest', 'crt', 'mobileconfig', 'mp3', 'wav', 'ogg', 'm4a'], true)
) {
    return false;
}

require __DIR__ . '/index.php';
