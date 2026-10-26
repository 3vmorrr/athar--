<?php
declare(strict_types=1);
// Read-only verification of the explicitly configured development database.
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require dirname(__DIR__) . '/backend/bootstrap.php';
$runtime = json_decode((string) file_get_contents(dirname(__DIR__) . '/storage/local-runtime.json'), true, 512, JSON_THROW_ON_ERROR);
if ((getenv('ATHAR_DB_HOST') ?: '') !== '127.0.0.1' || (int) getenv('ATHAR_DB_PORT') !== (int) ($runtime['mysqlPort'] ?? 0) || (int) getenv('ATHAR_DB_PORT') !== 3309) {
    throw new RuntimeException('Expected the configured isolated loopback development database.');
}
$db = \Athar\Infrastructure\Database::connect();
$versions = $db->query('SELECT version FROM schema_migrations ORDER BY version')->fetchAll(PDO::FETCH_COLUMN);
$files = array_map(static fn(string $path): string => pathinfo($path, PATHINFO_FILENAME), glob(dirname(__DIR__) . '/database/migrations/*.sql'));
sort($files);
if ($versions !== $files) throw new RuntimeException('Migration ledger differs from repository migrations.');
$tables = $db->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
foreach ($tables as $table) {
    if (!preg_match('/^[a-zA-Z0-9_]+$/', $table)) throw new RuntimeException('Invalid table name.');
    $rows = $db->query('CHECK TABLE `' . $table . '`')->fetchAll(PDO::FETCH_ASSOC);
    if (!$rows || end($rows)['Msg_text'] !== 'OK') throw new RuntimeException('Development table validation failed.');
}
echo 'PASS: ' . count($versions) . ' matching migrations; ' . count($tables) . " healthy development tables checked. No data changed.\n";
