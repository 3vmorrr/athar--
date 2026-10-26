<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require dirname(__DIR__) . '/backend/bootstrap.php';
$options = getopt('', ['database:']);
if (empty($options['database'])) { fwrite(STDERR, "Usage: php scripts/migrate.php --database=EXPLICIT_DATABASE\n"); exit(2); }
try {
    $db = \Athar\Infrastructure\Database::connect((string) $options['database']);
    foreach (\Athar\Infrastructure\MigrationRunner::apply($db, dirname(__DIR__) . '/database/migrations') as $version) echo 'Applied ' . $version . PHP_EOL;
    echo "Migration ledger is current.\n";
} catch (Throwable $e) { fwrite(STDERR, 'Migration failed: ' . $e->getMessage() . PHP_EOL); exit(1); }
