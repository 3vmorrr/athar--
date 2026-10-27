<?php
declare(strict_types=1);

require __DIR__ . '/integration_support.php';
require dirname(__DIR__) . '/app/backup.php';

$checks = 0;
function recovery_check(bool $condition, string $label): void
{
    global $checks;
    if (!$condition) throw new RuntimeException('FAIL: ' . $label);
    $checks++;
}
function recovery_denied(callable $operation, string $label): void
{
    try { $operation(); } catch (RuntimeException $failure) { recovery_check(true, $label); return; }
    recovery_check(false, $label);
}

$controlName = (string) getenv('ATHAR_DB_NAME');
isolated_database(function (PDO $connection): void {
    $schema = (string) $connection->query('SELECT DATABASE()')->fetchColumn();
    if (!preg_match('/^athar_test_[a-f0-9]{12}$/D', $schema)) throw new RuntimeException('Unsafe recovery schema.');
    putenv('ATHAR_DB_NAME=' . $schema);
    putenv('ATHAR_BACKUP_KEY=' . bin2hex(random_bytes(32)));
    $db = db();
    $artifacts = [];
    $bookKey = bin2hex(random_bytes(20)) . '.pdf';
    $bookPath = dirname(__DIR__) . '/storage/books/' . $bookKey;
    if (!is_dir(dirname($bookPath))) mkdir(dirname($bookPath), 0700, true);
    $pdf = "%PDF-1.4\nRecovery fixture only\n%%EOF\n";
    try {
        $hash = password_hash('RecoveryFixture123!', PASSWORD_DEFAULT);
        $db->prepare("INSERT INTO users(email,password_hash,role,email_verified_at) VALUES ('recovery-owner@example.test',?,'owner',UTC_TIMESTAMP()),('recovery-user@example.test',?,'user',UTC_TIMESTAMP())")->execute([$hash, $hash]);
        $ownerId = (int) $db->query("SELECT id FROM users WHERE role='owner'")->fetchColumn();
        $db->exec("INSERT INTO quran_positions(user_id,surah,ayah,revision) VALUES (2,114,6,1)");
        file_put_contents($bookPath, $pdf);
        $db->prepare("INSERT INTO books(title,author,source_url,rights_basis,file_key,can_download,is_published) VALUES ('Recovery fixture','QA','https://example.invalid/source','Fixture only',?,1,1)")->execute([$bookKey]);
        $artifact = build_backup_archive($ownerId);
        $artifacts[] = $artifact['id'];
        recovery_check($artifact['byte_size'] > 0, 'Real encrypted database/asset backup built');
        $db->exec('UPDATE users SET session_version=83');
        $db->exec('UPDATE quran_positions SET ayah=5,revision=2');
        file_put_contents($bookPath, '%PDF-modified');
        $restored = restore_backup_archive($artifact['id'], $ownerId);
        recovery_check($restored === $ownerId, 'Owner survives normal restoration');
        recovery_check((int) $db->query('SELECT ayah FROM quran_positions')->fetchColumn() === 6, 'Private authoritative reading data restored');
        recovery_check(file_get_contents($bookPath) === $pdf, 'Registered PDF bytes restored');
        recovery_check((int) $db->query('SELECT MIN(session_version) FROM users')->fetchColumn() === 84, 'Every restored user invalidates all historical cookie versions');
        recovery_check((int) $db->query('SELECT @@FOREIGN_KEY_CHECKS')->fetchColumn() === 1, 'Foreign keys reenabled');
        recovery_check(!$db->inTransaction(), 'No transaction remains open');

        $db->prepare('UPDATE backup_artifacts SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?')->execute([$artifact['id']]);
        recovery_denied(fn() => restore_backup_archive($artifact['id'], $ownerId), 'Expired artifact refused despite recent verification');
        $db->prepare('UPDATE backup_artifacts SET expires_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR) WHERE id=?')->execute([$artifact['id']]);
        $query = $db->prepare('SELECT storage_key FROM backup_artifacts WHERE id=?'); $query->execute([$artifact['id']]);
        $encrypted = backup_directory() . '/' . $query->fetchColumn();
        $original = file_get_contents($encrypted);
        file_put_contents($encrypted, $original . 'corruption');
        recovery_denied(fn() => restore_backup_archive($artifact['id'], $ownerId), 'Corrupt encrypted artifact refused before mutation');
        recovery_check((int) $db->query('SELECT MIN(session_version) FROM users')->fetchColumn() === 84, 'Rejected restore leaves identity intact');
        file_put_contents($encrypted, $original);
        $key = getenv('ATHAR_BACKUP_KEY'); putenv('ATHAR_BACKUP_KEY=' . bin2hex(random_bytes(32)));
        recovery_denied(fn() => restore_backup_archive($artifact['id'], $ownerId), 'Wrong decryption key refused');
        putenv('ATHAR_BACKUP_KEY=' . $key);

        // A newly appointed owner absent from the snapshot must retain access under
        // their restored ID, rather than inheriting the actor at their former ID.
        $db->prepare("INSERT INTO users(email,password_hash,role) VALUES ('new-recovery-owner@example.test',?,'owner')")->execute([$hash]);
        $newOwner = (int) $db->lastInsertId();
        $db->prepare("INSERT INTO platform_settings(setting_key,setting_value,updated_by) VALUES ('recovery-fixture','retained-setting',?)")->execute([$newOwner]);
        $restoredNewOwner = restore_backup_archive($artifact['id'], $newOwner);
        $actor = $db->query('SELECT * FROM users WHERE id=' . $restoredNewOwner)->fetch();
        recovery_check($actor['email'] === 'new-recovery-owner@example.test' && $actor['role'] === 'owner', 'Absent owner is recreated and returned by identity');
        recovery_check(password_verify('RecoveryFixture123!', $actor['password_hash']), 'Current owner credentials preserved');
        recovery_check((int) $actor['session_version'] === 85, 'Recreated owner also rotates session version');
        recovery_check((string) $db->query("SELECT setting_value FROM platform_settings WHERE setting_key='recovery-fixture'")->fetchColumn() === 'retained-setting', 'Environment settings retained');
        recovery_check($db->query("SELECT updated_by FROM platform_settings WHERE setting_key='recovery-fixture'")->fetchColumn() === null, 'Dangling retained actor reference cleared');
        recovery_check((int) $db->query("SELECT actor_id FROM audit_logs WHERE action='backup-restored' ORDER BY id DESC LIMIT 1")->fetchColumn() === $restoredNewOwner, 'Restore journal identifies the recreated owner');
        $db->exec('UPDATE users SET session_version=4294967295');
        recovery_denied(fn() => restore_backup_archive($artifact['id'], $restoredNewOwner), 'Session-version overflow refused before deletion');
        recovery_check((int) $db->query('SELECT MAX(session_version) FROM users')->fetchColumn() === 4294967295, 'Overflow refusal preserves users');
        recovery_check(!glob(backup_directory() . '/athar-restore-*'), 'All temporary restore plaintext cleaned up');
    } finally {
        foreach ($artifacts as $id) {
            $q = $db->prepare('SELECT storage_key FROM backup_artifacts WHERE id=?'); $q->execute([$id]);
            $key = $q->fetchColumn();
            if (is_string($key) && preg_match('/^[a-f0-9]{40}\.enc$/D', $key)) @unlink(backup_directory() . '/' . $key);
        }
        if (is_file($bookPath)) unlink($bookPath);
        putenv('ATHAR_BACKUP_KEY');
    }
});
putenv('ATHAR_DB_NAME=' . $controlName);
echo 'PASS: ' . $checks . " isolated recovery restore assertions.\n";
