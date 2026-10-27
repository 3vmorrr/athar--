<?php
declare(strict_types=1);
require __DIR__ . '/integration_support.php';
require dirname(__DIR__) . '/app/backup.php';
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;

$checks = 0;
function identity_check(bool $ok, string $label): void { global $checks; if (!$ok) throw new RuntimeException($label); $checks++; }
if (!preg_match('/^athar_test_control_[a-f0-9]{12}$/D', (string)getenv('ATHAR_DB_NAME'))) throw new RuntimeException('Dedicated control schema required.');
$control = Database::connect();
$expected = realpath((string)getenv('ATHAR_TEST_DATA_DIR'));
$actual = realpath((string)$control->query('SELECT @@datadir')->fetchColumn());
if (!$expected || !$actual || strcasecmp($expected,$actual) !== 0) throw new RuntimeException('Unexpected test server.');
$schema = 'athar_test_' . bin2hex(random_bytes(6));
$legacy = dirname(__DIR__) . '/storage/engineering/provider-migration-' . bin2hex(random_bytes(8));
mkdir($legacy,0700,true);
$control->exec('CREATE DATABASE `' . $schema . '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
try {
    foreach (glob(dirname(__DIR__) . '/database/migrations/*.sql') as $file) if (basename($file) !== '027_lesson_provider_identity.sql') copy($file,$legacy . '/' . basename($file));
    $db = Database::connect($schema); MigrationRunner::apply($db,$legacy);
    $db->exec("INSERT INTO users(email,password_hash) VALUES ('migration-owner@example.test','fixture-only-not-a-login-hash')");
    $db->exec("INSERT INTO lessons(title,youtube_id,source_url,is_published) VALUES ('Existing migration fixture','AbCdEf12345','https://www.youtube.com/watch?v=AbCdEf12345',1)");
    $lesson = (int)$db->lastInsertId();
    $db->prepare('INSERT INTO video_progress(user_id,lesson_id,position_seconds,duration_seconds) VALUES (1,?,14,180)')->execute([$lesson]);
    $db->prepare("INSERT INTO user_favorites(user_id,target_type,target_id) VALUES (1,'lesson',?)")->execute([$lesson]);
    $before = $db->query('SELECT id,email,password_hash,session_version FROM users')->fetchAll();
    $oldRejected = false;
    try { $db->exec("INSERT INTO lessons(title,youtube_id,source_url) VALUES ('Case fixture','aBcDef12345','https://www.youtube.com/watch?v=aBcDef12345')"); }
    catch (PDOException $failure) { if ((int)($failure->errorInfo[1] ?? 0) !== 1062) throw $failure; $oldRejected = true; }
    identity_check($oldRejected,'Legacy uniqueness demonstrably folds distinct provider ID spellings');
    $applied = MigrationRunner::apply($db,dirname(__DIR__) . '/database/migrations');
    identity_check($applied === ['027_lesson_provider_identity'],'Only the new noncolliding migration applies to the populated baseline');
    $column = $db->prepare("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='lessons' AND COLUMN_NAME='youtube_id'"); $column->execute([$schema]);
    identity_check($column->fetchColumn() === 'utf8mb4_bin','Opaque provider identity uses binary collation');
    $db->exec("INSERT INTO lessons(title,youtube_id,source_url) VALUES ('Case fixture','aBcDef12345','https://www.youtube.com/watch?v=aBcDef12345')");
    identity_check($db->query('SELECT youtube_id FROM lessons ORDER BY id')->fetchAll(PDO::FETCH_COLUMN) === ['AbCdEf12345','aBcDef12345'],'Case-distinct provider identities remain distinct and exact');
    identity_check($before === $db->query('SELECT id,email,password_hash,session_version FROM users')->fetchAll(),'Migration preserves account/credential ownership');
    identity_check((int)$db->query('SELECT position_seconds FROM video_progress')->fetchColumn() === 14,'Existing private playback progress survives unchanged');
    identity_check((int)$db->query('SELECT target_id FROM user_favorites')->fetchColumn() === $lesson,'Existing favorite remains bound to the original lesson ID');
    identity_check(MigrationRunner::apply($db,dirname(__DIR__) . '/database/migrations') === [],'Migration replay is a no-op');
    $version = backup_schema_version($db); $maximum = $db->query('SELECT MAX(version) FROM schema_migrations')->fetchColumn();
    $db->exec("INSERT INTO schema_migrations(version) VALUES ('016_fixture_additive_migration')");
    identity_check($db->query('SELECT MAX(version) FROM schema_migrations')->fetchColumn() === $maximum && backup_schema_version($db) !== $version,'Full ledger detects subsequently integrated lower-numbered migrations');
    echo 'PASS: ' . $checks . " provider-identity populated-schema migration assertions.\n";
} finally {
    if (!preg_match('/^athar_test_[a-f0-9]{12}$/D',$schema)) throw new RuntimeException('Unsafe cleanup target.');
    $control->exec('DROP DATABASE `' . $schema . '`');
    foreach (glob($legacy . '/*.sql') as $file) unlink($file);
    rmdir($legacy);
}
