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
    $sharedOne = \Athar\Infrastructure\MaintenanceGuard::acquire();
    $sharedTwo = \Athar\Infrastructure\MaintenanceGuard::acquire();
    recovery_check($sharedOne !== null && $sharedTwo !== null, 'Normal requests can share the recovery barrier concurrently');
    recovery_denied(fn() => acquire_maintenance_lock(), 'Recovery cannot overtake an in-flight shared request');
    $sharedOne = null; $sharedTwo = null;
    acquire_maintenance_lock();
    try {
        $otp = new \Athar\Identity\OtpService($db, random_bytes(32), static fn() => true);
        $kernel = new \Athar\Http\ApiKernel($db, new \Athar\Identity\AuthService($db, $otp), new \Athar\Identity\RateLimiter($db, random_bytes(32)));
        $response = $kernel->handle(new \Athar\Http\Request('GET', '/api/v1/health', [], ['Accept-Language'=>'en']));
        recovery_check($response->status === 503 && $response->body['error']['code'] === 'maintenance', 'Canonical API refuses recovery overlap before dispatch');
        recovery_check(str_contains($response->body['error']['message'], 'maintenance'), 'Maintenance error follows API language contract');
    } finally { release_maintenance_lock(); }
    recovery_check($kernel->handle(new \Athar\Http\Request('GET', '/api/v1/health'))->status === 200, 'API guard releases after maintenance');
    $artifacts = [];
    $bookKey = bin2hex(random_bytes(20)) . '.pdf';
    $bookPath = dirname(__DIR__) . '/storage/books/' . $bookKey;
    $initialPhotoKey = bin2hex(random_bytes(20)) . '.png';
    $initialPhotoPath = dirname(__DIR__) . '/storage/scholars/' . $initialPhotoKey;
    if (!is_dir(dirname($bookPath))) mkdir(dirname($bookPath), 0700, true);
    if (!is_dir(dirname($initialPhotoPath))) mkdir(dirname($initialPhotoPath), 0700, true);
    $pdf = "%PDF-1.4\nRecovery fixture only\n%%EOF\n";
    try {
        $hash = password_hash('RecoveryFixture123!', PASSWORD_DEFAULT);
        $db->prepare("INSERT INTO users(email,password_hash,role,email_verified_at) VALUES ('recovery-owner@example.test',?,'owner',UTC_TIMESTAMP()),('recovery-user@example.test',?,'user',UTC_TIMESTAMP())")->execute([$hash, $hash]);
        $ownerId = (int) $db->query("SELECT id FROM users WHERE role='owner'")->fetchColumn();
        $db->exec("INSERT INTO quran_positions(user_id,surah,ayah,revision) VALUES (2,114,6,1)");
        file_put_contents($bookPath, $pdf);
        file_put_contents($initialPhotoPath, "\x89PNG\r\n\x1a\ninitial fixture");
        $db->prepare("INSERT INTO scholars(name,image_key) VALUES ('Initial recovery portrait',?)")->execute([$initialPhotoKey]);
        $db->prepare("INSERT INTO books(title,author,source_url,rights_basis,file_key,can_download,is_published) VALUES ('Recovery fixture','QA','https://example.invalid/source','Fixture only',?,1,1)")->execute([$bookKey]);
        $artifact = build_backup_archive($ownerId);
        $artifacts[] = $artifact['id'];
        recovery_check($artifact['byte_size'] > 0, 'Real encrypted database/asset backup built');
        $db->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([password_hash('NewerOwnerPassword123!', PASSWORD_DEFAULT), $ownerId]);
        $db->exec('UPDATE users SET session_version=83');
        $db->exec('UPDATE quran_positions SET ayah=5,revision=2');
        file_put_contents($bookPath, '%PDF-modified');
        $restored = restore_backup_archive($artifact['id'], $ownerId);
        recovery_check($restored === $ownerId, 'Owner survives normal restoration');
        recovery_check(password_verify('NewerOwnerPassword123!', (string)$db->query('SELECT password_hash FROM users WHERE id=' . $ownerId)->fetchColumn()), 'Snapshot cannot roll back the current Owner password');
        recovery_check((int) $db->query('SELECT ayah FROM quran_positions')->fetchColumn() === 6, 'Private authoritative reading data restored');
        recovery_check(file_get_contents($bookPath) === $pdf, 'Registered PDF bytes restored');
        recovery_check((int) $db->query('SELECT MIN(session_version) FROM users')->fetchColumn() === 84, 'Every restored user invalidates all historical cookie versions');
        recovery_check((int) $db->query('SELECT @@FOREIGN_KEY_CHECKS')->fetchColumn() === 1, 'Foreign keys reenabled');
        recovery_check(!$db->inTransaction(), 'No transaction remains open');

        $currentPdf = '%PDF-current-before-failed-restore';
        file_put_contents($bookPath, $currentPdf);
        $db->exec('UPDATE quran_positions SET ayah=5,revision=2');
        unlink($initialPhotoPath); mkdir($initialPhotoPath);
        try {
            recovery_denied(fn() => restore_backup_archive($artifact['id'], $ownerId), 'Unsafe second asset destination fails restoration');
            recovery_check(file_get_contents($bookPath) === $currentPdf, 'Second asset failure restores the first original file');
            recovery_check((int) $db->query('SELECT ayah FROM quran_positions')->fetchColumn() === 5, 'Asset install failure rolls back all restored database rows');
            recovery_check((int) $db->query('SELECT MIN(session_version) FROM users')->fetchColumn() === 84, 'Asset failure retains current credential versions');
            recovery_check(!glob(backup_directory() . '/restore-original-*.asset'), 'Successful compensation cleans original-file rollback copies');
        } finally { rmdir($initialPhotoPath); file_put_contents($initialPhotoPath, "\x89PNG\r\n\x1a\ninitial fixture"); }
        $db->exec("CREATE TRIGGER recovery_restore_audit_abort BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='backup-restored' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure after asset installation'; END IF; END");
        try {
            recovery_denied(fn() => restore_backup_archive($artifact['id'], $ownerId), 'Post-install transactional failure is surfaced');
            recovery_check(file_get_contents($bookPath) === $currentPdf && (int)$db->query('SELECT ayah FROM quran_positions')->fetchColumn() === 5, 'Post-install SQL failure compensates files and restores prior database state');
            recovery_check(!\Athar\Infrastructure\RecoveryJournal::exists() && !glob(backup_directory() . '/restore-original-*.asset'), 'Compensated exception removes its journal and retained originals');
        } finally { $db->exec('DROP TRIGGER recovery_restore_audit_abort'); }

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
        $db->exec("UPDATE users SET email='changed-identity@example.test' WHERE id=2");
        $db->exec("INSERT INTO platform_settings(setting_key,setting_value,updated_by) VALUES ('reused-identity','retained-setting',2)");
        $restoredNewOwner = restore_backup_archive($artifact['id'], $newOwner);
        $actor = $db->query('SELECT * FROM users WHERE id=' . $restoredNewOwner)->fetch();
        recovery_check($actor['email'] === 'new-recovery-owner@example.test' && $actor['role'] === 'owner', 'Absent owner is recreated and returned by identity');
        recovery_check(password_verify('RecoveryFixture123!', $actor['password_hash']), 'Current owner credentials preserved');
        recovery_check((int) $actor['session_version'] === 85, 'Recreated owner also rotates session version');
        recovery_check((string) $db->query("SELECT setting_value FROM platform_settings WHERE setting_key='recovery-fixture'")->fetchColumn() === 'retained-setting', 'Environment settings retained');
        recovery_check($db->query("SELECT updated_by FROM platform_settings WHERE setting_key='recovery-fixture'")->fetchColumn() === null, 'Dangling retained actor reference cleared');
        recovery_check($db->query("SELECT updated_by FROM platform_settings WHERE setting_key='reused-identity'")->fetchColumn() === null, 'Reused account ID cannot inherit another retained settings attribution');
        recovery_check((int) $db->query("SELECT actor_id FROM audit_logs WHERE action='backup-restored' ORDER BY id DESC LIMIT 1")->fetchColumn() === $restoredNewOwner, 'Restore journal identifies the recreated owner');
        $db->exec('UPDATE users SET session_version=4294967295');
        recovery_denied(fn() => restore_backup_archive($artifact['id'], $restoredNewOwner), 'Session-version overflow refused before deletion');
        recovery_check((int) $db->query('SELECT MAX(session_version) FROM users')->fetchColumn() === 4294967295, 'Overflow refusal preserves users');
        recovery_check(!glob(backup_directory() . '/athar-restore-*'), 'All temporary restore plaintext cleaned up');

        $db->exec('UPDATE users SET session_version=86');
        $previousEnv = getenv('APP_ENV'); putenv('APP_ENV=production');
        recovery_denied(fn() => reset_local_data($restoredNewOwner), 'Production reset refused inside service boundary');
        putenv('APP_ENV=local');
        $db->exec("UPDATE backup_artifacts SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND)");
        recovery_denied(fn() => reset_local_data($restoredNewOwner), 'Reset refuses expired recovery backup');
        $db->exec("UPDATE backup_artifacts SET expires_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR)");
        rename($encrypted, $encrypted . '.fixture-held');
        try { recovery_denied(fn() => reset_local_data($restoredNewOwner), 'Reset refuses missing recovery file'); }
        finally { rename($encrypted . '.fixture-held', $encrypted); }
        $key = getenv('ATHAR_BACKUP_KEY'); putenv('ATHAR_BACKUP_KEY=' . bin2hex(random_bytes(32)));
        recovery_denied(fn() => reset_local_data($restoredNewOwner), 'Reset refuses an undecryptable backup');
        putenv('ATHAR_BACKUP_KEY=' . $key);
        recovery_check((int) $db->query('SELECT COUNT(*) FROM users')->fetchColumn() === 3, 'Rejected resets preserve all accounts');
        $photoKey = bin2hex(random_bytes(20)) . '.png';
        $photoPath = dirname(__DIR__) . '/storage/scholars/' . $photoKey;
        if (!is_dir(dirname($photoPath))) mkdir(dirname($photoPath), 0700, true);
        $unregisteredPath = dirname($bookPath) . '/' . bin2hex(random_bytes(20)) . '.pdf';
        try {
            file_put_contents($photoPath, "\x89PNG\r\n\x1a\nfixture");
            file_put_contents($unregisteredPath, '%PDF-unregistered-fixture');
            $db->prepare("INSERT INTO scholars(name,image_key) VALUES ('Reset fixture',?)")->execute([$photoKey]);
            $latest = build_backup_archive($restoredNewOwner); $artifacts[] = $latest['id'];
            $migrationCount = (int) $db->query('SELECT COUNT(*) FROM schema_migrations')->fetchColumn();
            $db->exec("CREATE TRIGGER recovery_fixture_abort BEFORE DELETE ON books FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture blocked deletion'");
            try {
                recovery_denied(fn() => reset_local_data($restoredNewOwner), 'Mid-reset SQL failure is surfaced');
                recovery_check((int) $db->query('SELECT COUNT(*) FROM users')->fetchColumn() === 3 && (int) $db->query('SELECT COUNT(*) FROM quran_positions')->fetchColumn() === 1, 'Mid-reset failure rolls back earlier deletes');
                recovery_check(is_file($bookPath) && is_file($photoPath), 'Rolled-back reset leaves registered assets untouched');
                recovery_check((int) $db->query('SELECT @@FOREIGN_KEY_CHECKS')->fetchColumn() === 1 && !$db->inTransaction(), 'Failed reset restores connection state');
            } finally { $db->exec('DROP TRIGGER recovery_fixture_abort'); }
            acquire_maintenance_lock();
            try { reset_local_data($restoredNewOwner); } finally { release_maintenance_lock(); }
            recovery_check((int) $db->query('SELECT COUNT(*) FROM users')->fetchColumn() === 1, 'Local reset retains only active owner');
            recovery_check((int) $db->query('SELECT id FROM users')->fetchColumn() === $restoredNewOwner, 'Local reset preserves owner identity');
            recovery_check((int) $db->query('SELECT session_version FROM users')->fetchColumn() === 87, 'Local reset revokes old owner sessions');
            recovery_check((int) $db->query('SELECT COUNT(*) FROM quran_positions')->fetchColumn() === 0 && (int) $db->query('SELECT COUNT(*) FROM books')->fetchColumn() === 0, 'Reset clears private and content records');
            recovery_check((int) $db->query('SELECT COUNT(*) FROM schema_migrations')->fetchColumn() === $migrationCount, 'Reset preserves migration ledger');
            recovery_check((int) $db->query('SELECT COUNT(*) FROM backup_artifacts')->fetchColumn() === 2, 'Reset preserves recovery artifacts');
            recovery_check(!is_file($bookPath) && !is_file($photoPath), 'Reset removes registered book and scholar assets');
            recovery_check(is_file($unregisteredPath), 'Reset preserves files outside registered asset set');
            recovery_check((int) $db->query('SELECT @@FOREIGN_KEY_CHECKS')->fetchColumn() === 1 && !$db->inTransaction(), 'Reset closes transaction and reenables FK checks');
            recovery_check((string) $db->query("SELECT IS_FREE_LOCK('athar_maintenance_lock')")->fetchColumn() === '1', 'Maintenance lock released');
            $recovered = restore_backup_archive($latest['id'], $restoredNewOwner);
            recovery_check($recovered === $restoredNewOwner && (int) $db->query('SELECT COUNT(*) FROM users')->fetchColumn() === 3, 'Actual reset can be recovered from the retained encrypted backup');
            recovery_check(file_get_contents($bookPath) === $pdf && is_file($photoPath), 'Post-reset recovery restores both asset kinds');
        } finally {
            if (is_file($photoPath)) unlink($photoPath);
            if (is_file($unregisteredPath)) unlink($unregisteredPath);
            if ($previousEnv === false) putenv('APP_ENV'); else putenv('APP_ENV=' . $previousEnv);
        }
        $journalPath = \Athar\Infrastructure\RecoveryJournal::path();
        recovery_check(!is_file($journalPath), 'No recovery journal remains before preservation tests');
        $malformed = '{"version":';
        file_put_contents($journalPath, $malformed);
        try {
            recovery_check(\Athar\Infrastructure\MaintenanceGuard::acquire() === null, 'Malformed journal refuses ordinary requests');
            recovery_check(file_get_contents($journalPath) === $malformed, 'Malformed journal is preserved rather than silently reset');
        } finally { unlink($journalPath); }
        $foreign = json_encode(['version'=>1,'id'=>str_repeat('a',32),'database'=>'different_environment','files'=>[]]);
        file_put_contents($journalPath, $foreign);
        try {
            recovery_check(\Athar\Infrastructure\MaintenanceGuard::acquire() === null, 'Recovery journal cannot cross database environments');
            recovery_check(file_get_contents($journalPath) === $foreign, 'Foreign-environment journal remains intact');
        } finally { unlink($journalPath); }
    } finally {
        foreach ($artifacts as $id) {
            $q = $db->prepare('SELECT storage_key FROM backup_artifacts WHERE id=?'); $q->execute([$id]);
            $key = $q->fetchColumn();
            if (is_string($key) && preg_match('/^[a-f0-9]{40}\.enc$/D', $key)) @unlink(backup_directory() . '/' . $key);
        }
        if (is_file($bookPath)) unlink($bookPath);
        if (is_file($initialPhotoPath)) unlink($initialPhotoPath);
        putenv('ATHAR_BACKUP_KEY');
    }
});
putenv('ATHAR_DB_NAME=' . $controlName);
echo 'PASS: ' . $checks . " isolated recovery restore assertions.\n";
