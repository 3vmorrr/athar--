<?php
declare(strict_types=1);

require_once __DIR__ . '/helpers.php';
require_once __DIR__ . '/backup-assets.php';

function backup_directory(): string
{
    return dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'backups';
}

function backup_key(): string
{
    $encoded = getenv('ATHAR_BACKUP_KEY') ?: '';
    if (!preg_match('/^[a-fA-F0-9]{64}$/', $encoded)) throw new RuntimeException('يجب ضبط ATHAR_BACKUP_KEY كمفتاح مستقل من 64 رقمًا سداسيًا.');
    $key = hex2bin($encoded);
    if ($key === false || strlen($key) !== 32) throw new RuntimeException('مفتاح التشفير غير صالح.');
    return $key;
}

/** All applied versions matter when independent lanes land migrations out of order. */
function backup_schema_version(PDO $db): string
{
    return backup_version_fingerprint($db->query('SELECT version FROM schema_migrations')->fetchAll(PDO::FETCH_COLUMN));
}

function backup_version_fingerprint(array $versions): string
{
    if (!$versions) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    foreach ($versions as $version) if (!is_string($version) || !preg_match('/^[0-9]{3}_[a-zA-Z0-9_]{1,76}$/D', $version)) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    sort($versions, SORT_STRING);
    if (count(array_unique($versions)) !== count($versions)) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    return 'ledger:' . hash('sha256', json_encode($versions, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES));
}

/** Legacy v1 archives also include their authenticated full migration ledger. */
function backup_archive_schema_version(ZipArchive $zip, array $manifest): string
{
    $entry = 'database/schema_migrations.json';
    $bytes = $zip->getFromName($entry); $expected = $manifest['checksums_sha256'][$entry] ?? null;
    if (!is_string($bytes) || !is_string($expected) || !preg_match('/^[a-f0-9]{64}$/D', $expected) || !hash_equals($expected, hash('sha256', $bytes))) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    $rows = json_decode($bytes, true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($rows)) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    $versions = [];
    foreach ($rows as $row) { if (!is_array($row) || !isset($row['version'])) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.'); $versions[] = $row['version']; }
    $fingerprint = backup_version_fingerprint($versions);
    if (str_starts_with((string)($manifest['schema_version'] ?? ''), 'ledger:') && $manifest['schema_version'] !== $fingerprint) throw new RuntimeException('سجل ترحيلات النسخة غير صالح.');
    return $fingerprint;
}

function ensure_backup_directory(): string
{
    $directory = backup_directory();
    if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) throw new RuntimeException('تعذر إعداد مساحة النسخ الاحتياطي الخاصة.');
    @chmod($directory, 0700);
    return $directory;
}

function append_owner_audit(array $event): void
{
    $directory = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'audit';
    if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) throw new RuntimeException('تعذر إنشاء سجل العمليات المستقل.');
    $path = $directory . DIRECTORY_SEPARATOR . 'owner-operations.log';
    $line = json_encode($event + ['recorded_at_utc' => gmdate('c')], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR) . "\n";
    if (file_put_contents($path, $line, FILE_APPEND | LOCK_EX) === false) throw new RuntimeException('تعذر كتابة سجل العمليات المستقل.');
    @chmod($path, 0600);
}

function assert_no_maintenance_lock(): void
{
    $lockOwner = db()->query("SELECT IS_USED_LOCK('athar_maintenance_lock')")->fetchColumn();
    if ($lockOwner !== null && $lockOwner !== false) {
        http_response_code(503);
        header('Retry-After: 30');
        exit('أثر في وضع صيانة مؤقت. حاول بعد انتهاء العملية.');
    }
}

function acquire_maintenance_lock(): void
{
    global $atharRecoveryGuard;
    if ($atharRecoveryGuard instanceof \Athar\Infrastructure\MaintenanceGuard) throw new RuntimeException('هناك عملية صيانة أخرى جارية بالفعل.');
    $guard = \Athar\Infrastructure\MaintenanceGuard::acquire(true);
    if (!$guard) throw new RuntimeException('هناك عملية صيانة أخرى جارية بالفعل.');
    $acquired = db()->query("SELECT GET_LOCK('athar_maintenance_lock',0)")->fetchColumn();
    if ((string) $acquired !== '1') throw new RuntimeException('هناك عملية صيانة أخرى جارية بالفعل.');
    $atharRecoveryGuard = $guard;
}

function release_maintenance_lock(): void
{
    global $atharRecoveryGuard;
    try { db()->query("SELECT RELEASE_LOCK('athar_maintenance_lock')"); }
    finally { $atharRecoveryGuard = null; }
}

function write_encrypted_backup(string $sourcePath, string $targetPath, string $key): string
{
    $source = fopen($sourcePath, 'rb'); $target = fopen($targetPath, 'xb');
    if (!$source || !$target) { if (is_resource($source)) fclose($source); if (is_resource($target)) fclose($target); throw new RuntimeException('تعذر فتح ملف النسخة الاحتياطية.'); }
    @chmod($targetPath, 0600);
    $magic = 'ATHAR-ENC1'; fwrite($target, $magic);
    $index = 0; $chunkSize = 1024 * 1024;
    try {
        while (!feof($source)) {
            $plain = fread($source, $chunkSize);
            if ($plain === false) throw new RuntimeException('تعذر قراءة بيانات النسخة.');
            if ($plain === '' && feof($source)) break;
            $nonce = random_bytes(12); $tag = '';
            $cipher = openssl_encrypt($plain, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'athar-backup-v1:' . $index, 16);
            if ($cipher === false || strlen($tag) !== 16) throw new RuntimeException('تعذر تشفير النسخة الاحتياطية.');
            fwrite($target, pack('N', strlen($cipher)) . $nonce . $tag . $cipher);
            $index++;
        }
        $nonce = random_bytes(12); $tag = '';
        $final = openssl_encrypt('', 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'athar-backup-v1:end:' . $index, 16);
        if ($final === false || $final !== '') throw new RuntimeException('تعذر إنهاء تشفير النسخة.');
        fwrite($target, pack('N', 0) . $nonce . $tag);
    } finally { fclose($source); fclose($target); }
    return hash_file('sha256', $targetPath) ?: '';
}

function read_exact($stream, int $length): string
{
    $data = '';
    while (strlen($data) < $length && !feof($stream)) {
        $part = fread($stream, $length - strlen($data));
        if ($part === false || $part === '') break;
        $data .= $part;
    }
    if (strlen($data) !== $length) throw new RuntimeException('ملف النسخة مشوه أو غير مكتمل.');
    return $data;
}

function decrypt_backup_file(string $sourcePath, string $targetPath, string $key): string
{
    $source = fopen($sourcePath, 'rb'); $target = fopen($targetPath, 'xb');
    if (!$source || !$target) { if (is_resource($source)) fclose($source); if (is_resource($target)) fclose($target); throw new RuntimeException('تعذر فتح ملف النسخة للتحقق.'); }
    @chmod($targetPath, 0600);
    try {
        if (read_exact($source, 10) !== 'ATHAR-ENC1') throw new RuntimeException('إصدار ملف النسخة غير مدعوم.');
        $index = 0; $finished = false;
        while (!feof($source)) {
            $length = unpack('Nlength', read_exact($source, 4))['length'];
            $nonce = read_exact($source, 12); $tag = read_exact($source, 16);
            if ($length === 0) {
                $plain = openssl_decrypt('', 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'athar-backup-v1:end:' . $index);
                if ($plain !== '') throw new RuntimeException('فشل التحقق من نهاية النسخة.');
                $finished = true;
                if (!feof($source) && fread($source, 1) !== '') throw new RuntimeException('توجد بيانات زائدة بعد نهاية النسخة.');
                break;
            }
            if ($length > 1024 * 1024 + 32) throw new RuntimeException('حجم جزء النسخة أكبر من الحد المسموح.');
            $cipher = read_exact($source, $length);
            $plain = openssl_decrypt($cipher, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, 'athar-backup-v1:' . $index);
            if ($plain === false || fwrite($target, $plain) !== strlen($plain)) throw new RuntimeException('فشل التحقق من سلامة النسخة.');
            $index++;
        }
        if (!$finished) throw new RuntimeException('النسخة غير مكتملة.');
    } catch (Throwable $exception) {
        fclose($source); fclose($target); if (is_file($targetPath)) unlink($targetPath); throw $exception;
    }
    fclose($source); fclose($target);
    return hash_file('sha256', $targetPath) ?: '';
}

function build_backup_archive(int $ownerId): array
{
    if (!class_exists(ZipArchive::class)) throw new RuntimeException('امتداد PHP ZIP مطلوب لإنشاء النسخ الاحتياطية.');
    $key = backup_key(); $directory = ensure_backup_directory();
    $plainPath = tempnam($directory, 'athar-plain-');
    if ($plainPath === false) throw new RuntimeException('تعذر إنشاء ملف مؤقت خاص.');
    @chmod($plainPath, 0600);
    $encryptedPath = null; $zip = new ZipArchive();
    try {
        if ($zip->open($plainPath, ZipArchive::OVERWRITE) !== true) throw new RuntimeException('تعذر إنشاء حاوية النسخة.');
        $pdo = db();
        $pdo->exec('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
        $pdo->beginTransaction();
        $database = getenv('ATHAR_DB_NAME') ?: 'athar';
        $list = $pdo->prepare("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME");
        $list->execute([$database]); $tables = $list->fetchAll(PDO::FETCH_COLUMN);
        $excluded = ['email_outbox','auth_tokens','api_sessions','auth_rate_buckets','backup_artifacts','backup_download_grants','platform_settings'];
        $counts = []; $checksums = []; $assetKeys = ['books'=>[],'scholars'=>[]]; $excludedTables = [];
        foreach ($tables as $table) {
            if (!is_string($table) || !preg_match('/^[a-zA-Z0-9_]+$/', $table)) continue;
            $quoted = '`' . $table . '`';
            $schemaRow = $pdo->query('SHOW CREATE TABLE ' . $quoted)->fetch(PDO::FETCH_NUM);
            if (!$schemaRow || !isset($schemaRow[1])) throw new RuntimeException('تعذر حفظ مخطط جدول من قاعدة البيانات.');
            $schemaContent = (string) $schemaRow[1] . ";\n";
            $zip->addFromString('schema/' . $table . '.sql', $schemaContent);
            $checksums['schema/' . $table . '.sql'] = hash('sha256', $schemaContent);
            if (in_array($table, $excluded, true)) { $excludedTables[] = $table; continue; }
            $rows = $pdo->query('SELECT * FROM ' . $quoted)->fetchAll();
            $json = json_encode($rows, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
            $entry = 'database/' . $table . '.json';
            if (!$zip->addFromString($entry, $json)) throw new RuntimeException('تعذر إضافة بيانات جدول إلى النسخة.');
            $counts[$table] = count($rows); $checksums[$entry] = hash('sha256', $json);
            if (isset($assetKeys[$table])) $assetKeys[$table] = backup_asset_keys($table,$rows);
        }
        $assetManifest = backup_add_assets($zip,dirname(__DIR__).DIRECTORY_SEPARATOR.'storage','books',$assetKeys['books']);
        $scholarManifest = backup_add_assets($zip,dirname(__DIR__).DIRECTORY_SEPARATOR.'storage','scholars',$assetKeys['scholars']);
        $migration = backup_schema_version($pdo);
        $manifest = [
            'format' => 'athar-full-backup-v1', 'created_at_utc' => gmdate('c'), 'schema_version' => (string) ($migration ?: 'unknown'),
            'database_name' => $database, 'tables' => $counts, 'checksums_sha256' => $checksums, 'book_assets' => $assetManifest, 'scholar_assets'=>$scholarManifest,
            'excluded_tables' => $excludedTables,
            'excluded' => ['database credentials and environment secrets', 'SMTP/Dorar settings and encrypted secrets', 'auth token rows', 'backup artifact and grant rows', 'logs and temporary files', 'external YouTube media'],
        ];
        $manifestJson = json_encode($manifest, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        $zip->addFromString('manifest.json', $manifestJson);
        $zip->close();
        $pdo->commit();
        $storageKey = bin2hex(random_bytes(20)) . '.enc';
        $encryptedPath = $directory . DIRECTORY_SEPARATOR . $storageKey;
        $checksum = write_encrypted_backup($plainPath, $encryptedPath, $key);
        if ($checksum === '' || !hash_equals($checksum, hash_file('sha256', $encryptedPath) ?: '')) throw new RuntimeException('تعذر التحقق من بصمة الملف المشفر.');
        $verifyPath = tempnam($directory, 'athar-verify-');
        if ($verifyPath === false) throw new RuntimeException('تعذر إنشاء ملف تحقق مؤقت.');
        unlink($verifyPath);
        decrypt_backup_file($encryptedPath, $verifyPath, $key);
        $verifyZip = new ZipArchive();
        if ($verifyZip->open($verifyPath, ZipArchive::CHECKCONS) !== true || $verifyZip->locateName('manifest.json') === false) throw new RuntimeException('فشل فحص محتويات النسخة بعد التشفير.');
        $verifiedManifest = json_decode((string) $verifyZip->getFromName('manifest.json'), true, 512, JSON_THROW_ON_ERROR);
        $verifyZip->close(); unlink($verifyPath);
        if (($verifiedManifest['format'] ?? '') !== 'athar-full-backup-v1' || empty($verifiedManifest['tables'])) throw new RuntimeException('ملف النسخة لا يحتوي على manifest صالح.');
        @unlink($plainPath);
        $insert = $pdo->prepare("INSERT INTO backup_artifacts (created_by,storage_key,sha256,byte_size,schema_version,status,verified_at,expires_at) VALUES (?,?,?,?,?,'ready',UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 24 HOUR))");
        $insert->execute([$ownerId, $storageKey, $checksum, filesize($encryptedPath), (string) $verifiedManifest['schema_version']]);
        $id = (int) $pdo->lastInsertId();
        $pdo->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$ownerId, 'backup-created-and-verified', 'backup', (string) $id]);
        return ['id' => $id, 'schema_version' => (string) $verifiedManifest['schema_version'], 'byte_size' => filesize($encryptedPath), 'expires_at' => date('Y-m-d H:i:s', time() + 86400)];
    } catch (Throwable $exception) {
        if (db()->inTransaction()) db()->rollBack();
        if ($zip instanceof ZipArchive) { try { $zip->close(); } catch (ValueError $ignored) {} }
        if (is_file($plainPath)) unlink($plainPath);
        if (is_string($encryptedPath) && is_file($encryptedPath)) unlink($encryptedPath);
        throw $exception;
    }
}

function expire_backup_artifacts(): void
{
    $directory = backup_directory();
    $expired = db()->query("SELECT id,storage_key FROM backup_artifacts WHERE status IN ('ready','downloaded') AND expires_at<=UTC_TIMESTAMP()")->fetchAll();
    foreach ($expired as $artifact) {
        $key = (string) $artifact['storage_key'];
        if (preg_match('/^[a-f0-9]{40}\.enc$/', $key)) { $path = $directory . DIRECTORY_SEPARATOR . $key; if (is_file($path)) unlink($path); }
        db()->prepare("UPDATE backup_artifacts SET status='expired' WHERE id=?")->execute([$artifact['id']]);
        db()->prepare('INSERT INTO audit_logs (action,target_type,target_id) VALUES (?,?,?)')->execute(['backup-expired-and-deleted', 'backup', (string) $artifact['id']]);
        append_owner_audit(['action' => 'backup-expired-and-deleted', 'backup_id' => (int) $artifact['id']]);
    }
    db()->exec('DELETE FROM backup_download_grants WHERE expires_at<=UTC_TIMESTAMP() OR consumed_at IS NOT NULL');
}

function restore_backup_archive(int $backupId, int $ownerId): int
{
    global $atharRecoveryGuard;
    if ($atharRecoveryGuard instanceof \Athar\Infrastructure\MaintenanceGuard) return restore_backup_archive_locked($backupId, $ownerId);
    acquire_maintenance_lock();
    try { return restore_backup_archive_locked($backupId, $ownerId); }
    finally { release_maintenance_lock(); }
}

function restore_backup_archive_locked(int $backupId, int $ownerId): int
{
    $key = backup_key();
    $query = db()->prepare("SELECT * FROM backup_artifacts WHERE id=? AND status IN ('ready','downloaded') AND expires_at>UTC_TIMESTAMP() AND verified_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR)");
    $query->execute([$backupId]); $artifact = $query->fetch();
    if (!$artifact) throw new RuntimeException('يلزم اختيار نسخة كاملة تم التحقق منها خلال آخر 24 ساعة.');
    $encrypted = backup_directory() . DIRECTORY_SEPARATOR . $artifact['storage_key'];
    if (!preg_match('/^[a-f0-9]{40}\.enc$/', (string) $artifact['storage_key']) || !is_file($encrypted) || !hash_equals((string) $artifact['sha256'], hash_file('sha256', $encrypted) ?: '')) throw new RuntimeException('ملف النسخة مفقود أو بصمته غير مطابقة.');
    $directory = ensure_backup_directory(); $plain = tempnam($directory, 'athar-restore-');
    if ($plain === false) throw new RuntimeException('تعذر إنشاء مساحة استعادة مؤقتة.'); @chmod($plain, 0600);
    // decrypt_backup_file exclusively creates its destination; tempnam already created it.
    if (!unlink($plain)) throw new RuntimeException('تعذر إعداد ملف الاستعادة الخاص.');
    $zip = new ZipArchive(); $staged = [];
    try {
        decrypt_backup_file($encrypted, $plain, $key);
        if ($zip->open($plain, ZipArchive::CHECKCONS) !== true) throw new RuntimeException('حاوية النسخة غير صالحة.');
        $manifest = json_decode((string) $zip->getFromName('manifest.json'), true, 512, JSON_THROW_ON_ERROR);
        if (($manifest['format'] ?? '') !== 'athar-full-backup-v1' || !is_array($manifest['tables'] ?? null) || !is_array($manifest['checksums_sha256'] ?? null)) throw new RuntimeException('بيانات النسخة غير مكتملة.');
        $db = db(); $database = getenv('ATHAR_DB_NAME') ?: 'athar';
        if (($manifest['database_name'] ?? '') !== $database) throw new RuntimeException('اسم قاعدة البيانات في النسخة لا يطابق البيئة الحالية.');
        $currentVersion = backup_schema_version($db);
        if (backup_archive_schema_version($zip, $manifest) !== $currentVersion) throw new RuntimeException('إصدار مخطط النسخة لا يطابق التطبيق الحالي؛ حدّث أو اختر نسخة متوافقة.');
        foreach ($manifest['checksums_sha256'] as $entry => $expected) {
            if (!is_string($entry) || str_contains($entry, '..') || str_starts_with($entry, '/') || !is_string($expected)) throw new RuntimeException('مسار داخل النسخة غير صالح.');
            $contents = $zip->getFromName($entry);
            if ($contents === false || !hash_equals($expected, hash('sha256', $contents))) throw new RuntimeException('فشل التحقق من أحد ملفات النسخة.');
        }
        $excluded = ['email_outbox','auth_tokens','api_sessions','auth_rate_buckets','backup_artifacts','backup_download_grants','platform_settings'];
        $tables = $db->query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=" . $db->quote($database) . " AND TABLE_TYPE='BASE TABLE'")->fetchAll(PDO::FETCH_COLUMN);
        $restoreTables = [];
        foreach ($manifest['tables'] as $table => $count) {
            if (!in_array($table, $tables, true) || in_array($table, $excluded, true) || !is_int($count) || $count < 0) throw new RuntimeException('النسخة تحتوي على جدول غير متوافق.');
            $rows = json_decode((string) $zip->getFromName('database/' . $table . '.json'), true, 512, JSON_THROW_ON_ERROR);
            if (!is_array($rows) || count($rows) !== $count) throw new RuntimeException('عدد السجلات في النسخة لا يطابق manifest.');
            $restoreTables[$table] = $rows;
        }
        if (count($restoreTables) !== count($tables) - count(array_intersect($excluded, $tables))) throw new RuntimeException('النسخة لا تحتوي على كل جداول التطبيق الحالية.');
        foreach (['books'=>'book_assets','scholars'=>'scholar_assets'] as $kind=>$manifestField) {
            $expectedKeys=backup_asset_keys($kind,$restoreTables[$kind]??[]);
            foreach(backup_read_assets($zip,$kind,$manifest[$manifestField]??[],$expectedKeys) as $assetKey=>$bytes){
                $stage = $directory . DIRECTORY_SEPARATOR . 'restore-' . bin2hex(random_bytes(16)) . '.asset';
                if (file_put_contents($stage, $bytes, LOCK_EX) !== strlen($bytes)) throw new RuntimeException('تعذر تجهيز ملف للاستعادة.'); @chmod($stage, 0600); $staged[$kind.'/'.$assetKey] = $stage;
            }
        }
        $zip->close(); $zip = new ZipArchive();
        $oldOwner = $db->prepare("SELECT email,password_hash,email_verified_at FROM users WHERE id=? AND role='owner' AND suspended_at IS NULL"); $oldOwner->execute([$ownerId]); $ownerCredentials = $oldOwner->fetch();
        if (!$ownerCredentials) throw new RuntimeException('تعذر التحقق من حساب المالك الحالي.');
        // Restoring an older version must never make an old public/Admin cookie valid again.
        $versionFloor = (int) $db->query('SELECT COALESCE(MAX(session_version),0) FROM users')->fetchColumn();
        foreach ($restoreTables['users'] ?? [] as $restoredUser) {
            $versionFloor = max($versionFloor, (int) ($restoredUser['session_version'] ?? 0));
        }
        if ($versionFloor >= 4294967295) throw new RuntimeException('تعذر تدوير إصدارات الجلسات بأمان.');
        $retainedActors = $db->query('SELECT id,email FROM users WHERE id IN (SELECT created_by FROM backup_artifacts UNION SELECT updated_by FROM platform_settings)')->fetchAll(PDO::FETCH_KEY_PAIR);
        $installed = [];
        $db->exec('SET FOREIGN_KEY_CHECKS=0');
        try {
            $db->beginTransaction();
            foreach (array_keys($restoreTables) as $table) $db->exec('DELETE FROM `' . $table . '`');
            foreach ($restoreTables as $table => $rows) {
                foreach ($rows as $row) {
                    if (!is_array($row) || array_diff(array_keys($row), array_column($db->query('SHOW COLUMNS FROM `' . $table . '`')->fetchAll(), 'Field'))) throw new RuntimeException('أعمدة جدول في النسخة غير متوافقة.');
                    if (!$row) continue;
                    $columns = array_keys($row); $quoted = array_map(static fn($column) => '`' . $column . '`', $columns);
                    $sql = 'INSERT INTO `' . $table . '` (' . implode(',', $quoted) . ') VALUES (' . implode(',', array_fill(0, count($columns), '?')) . ')';
                    $db->prepare($sql)->execute(array_values($row));
                }
            }
            $preserve = $db->prepare("UPDATE users SET password_hash=?,role='owner',suspended_at=NULL,email_verified_at=? WHERE email=?");
            $preserve->execute([$ownerCredentials['password_hash'], $ownerCredentials['email_verified_at'], $ownerCredentials['email']]);
            $restoredOwner = $db->prepare('SELECT id FROM users WHERE email=?');
            $restoredOwner->execute([$ownerCredentials['email']]);
            $restoredOwnerId = (int) $restoredOwner->fetchColumn();
            if (!$restoredOwnerId) {
                $db->prepare("INSERT INTO users(email,password_hash,role,email_verified_at) VALUES (?,?,'owner',?)")->execute([$ownerCredentials['email'], $ownerCredentials['password_hash'], $ownerCredentials['email_verified_at']]);
                $restoredOwnerId = (int) $db->lastInsertId();
            }
            $db->prepare('UPDATE users SET session_version=?')->execute([$versionFloor + 1]);
            // These tables survive restoration; their old actor may no longer exist.
            $db->exec('UPDATE backup_artifacts a LEFT JOIN users u ON u.id=a.created_by SET a.created_by=NULL WHERE u.id IS NULL');
            $db->exec('UPDATE platform_settings s LEFT JOIN users u ON u.id=s.updated_by SET s.updated_by=NULL WHERE u.id IS NULL');
            $retainedIdentity = $db->prepare('SELECT email FROM users WHERE id=?');
            foreach ($retainedActors as $actorId => $email) {
                $retainedIdentity->execute([$actorId]);
                if ($retainedIdentity->fetchColumn() !== $email) {
                    $db->prepare('UPDATE backup_artifacts SET created_by=NULL WHERE created_by=?')->execute([$actorId]);
                    $db->prepare('UPDATE platform_settings SET updated_by=NULL WHERE updated_by=?')->execute([$actorId]);
                }
            }
            $db->exec('DELETE FROM auth_tokens'); $db->exec('DELETE FROM backup_download_grants');
            if(in_array('email_outbox',$tables,true))$db->exec('DELETE FROM email_outbox');
            if(in_array('engagement_campaigns',$tables,true)){
                $db->exec("UPDATE engagement_campaigns SET status='cancelled',cancelled_at=UTC_TIMESTAMP(),revision=revision+1 WHERE status IN ('scheduled','processing')");
                $db->exec("UPDATE engagement_campaign_recipients SET status='cancelled',locked_at=NULL,last_error_code='restored_requires_reconfirmation' WHERE status IN ('queued','processing')");
            }
            foreach(['api_sessions','auth_rate_buckets'] as $securityTable)if(in_array($securityTable,$tables,true))$db->exec('DELETE FROM `'.$securityTable.'`');
            $installation = backup_install_staged_assets($staged);
            $installed = $installation['files'];
            $db->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$restoredOwnerId, 'backup-restored', 'backup', (string) $backupId]);
            $db->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$restoredOwnerId, 'backup-restore-committed', 'restore-operation', $installation['operation_id']]);
            $db->commit();
        } catch (Throwable $failure) {
            if ($db->inTransaction()) $db->rollBack();
            backup_rollback_installed_assets($installed);
            \Athar\Infrastructure\RecoveryJournal::clear();
            throw $failure;
        }
        finally { $db->exec('SET FOREIGN_KEY_CHECKS=1'); }
        \Athar\Infrastructure\RecoveryJournal::repair($db);
        append_owner_audit(['action' => 'backup-restored', 'actor_id' => $restoredOwnerId, 'backup_id' => $backupId]);
        return $restoredOwnerId;
    } finally {
        if ($zip instanceof ZipArchive) { try { $zip->close(); } catch (ValueError $ignored) {} } if (is_file($plain)) @unlink($plain); foreach ($staged as $stage) if (is_file($stage)) @unlink($stage);
    }
}

/** Refuse destructive reset unless its recovery artifact is still decryptable. */
function verified_reset_backup(): int
{
    $artifact = db()->query("SELECT * FROM backup_artifacts WHERE status IN ('ready','downloaded') AND verified_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR) AND expires_at>UTC_TIMESTAMP() ORDER BY verified_at DESC,id DESC LIMIT 1")->fetch();
    if (!$artifact) throw new RuntimeException('أنشئ نسخة كاملة صالحة تم التحقق منها خلال آخر 24 ساعة قبل إعادة الضبط.');
    $key = (string) $artifact['storage_key'];
    if (!preg_match('/^[a-f0-9]{40}\.enc$/D', $key)) throw new RuntimeException('مسار النسخة الاحتياطية غير صالح.');
    $path = backup_directory() . DIRECTORY_SEPARATOR . $key;
    if (!is_file($path) || is_link($path) || !hash_equals((string) $artifact['sha256'], hash_file('sha256', $path) ?: '')) throw new RuntimeException('ملف النسخة الاحتياطية مفقود أو تالف.');
    $plain = tempnam(ensure_backup_directory(), 'athar-reset-check-');
    if ($plain === false) throw new RuntimeException('تعذر التحقق من نسخة الاسترداد.');
    $zip = new ZipArchive();
    try {
        if (!unlink($plain)) throw new RuntimeException('تعذر إعداد ملف التحقق.');
        decrypt_backup_file($path, $plain, backup_key());
        if ($zip->open($plain, ZipArchive::CHECKCONS) !== true) throw new RuntimeException('حاوية النسخة غير صالحة.');
        $manifest = json_decode((string) $zip->getFromName('manifest.json'), true, 512, JSON_THROW_ON_ERROR);
        $schema = backup_schema_version(db());
        if (($manifest['format'] ?? '') !== 'athar-full-backup-v1' || ($manifest['database_name'] ?? '') !== (getenv('ATHAR_DB_NAME') ?: 'athar') || backup_archive_schema_version($zip, $manifest) !== $schema) throw new RuntimeException('نسخة الاسترداد لا تطابق قاعدة البيانات والمخطط الحاليين.');
        return (int) $artifact['id'];
    } finally {
        try { $zip->close(); } catch (ValueError $ignored) {}
        if (is_file($plain)) unlink($plain);
    }
}

/** Caller holds the maintenance lock and has confirmed the owner password/text. */
function reset_local_data(int $ownerId): void
{
    $environment = strtolower((string) (getenv('APP_ENV') ?: 'production'));
    if (!in_array($environment, ['local','development'], true)) throw new RuntimeException('إعادة الضبط متاحة فقط في بيئة محلية أو تطوير.');
    $db = db();
    $recent = verified_reset_backup();
    $assetKeys = [
        'books' => backup_asset_keys('books', $db->query('SELECT file_key FROM books')->fetchAll()),
        'scholars' => backup_asset_keys('scholars', $db->query('SELECT image_key FROM scholars')->fetchAll()),
    ];
    append_owner_audit(['action'=>'local-reset-started','actor_id'=>$ownerId,'environment'=>$environment,'backup_id'=>$recent]);
    $db->exec('SET FOREIGN_KEY_CHECKS=0');
    try {
        $db->beginTransaction();
        $owner = $db->prepare("SELECT session_version FROM users WHERE id=? AND role='owner' AND suspended_at IS NULL FOR UPDATE");
        $owner->execute([$ownerId]); $version = $owner->fetchColumn();
        if ($version === false || (int) $version >= 4294967295) throw new RuntimeException('تعذر التحقق من حساب المالك وإبطال جلساته بأمان.');
        $database = (string) $db->query('SELECT DATABASE()')->fetchColumn();
        $tables = $db->query('SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=' . $db->quote($database) . " AND TABLE_TYPE='BASE TABLE'")->fetchAll(PDO::FETCH_COLUMN);
        foreach ($tables as $table) {
            if (!preg_match('/^[a-zA-Z0-9_]+$/D', $table)) throw new RuntimeException('اسم جدول غير صالح.');
            if (!in_array($table, ['users','schema_migrations','backup_artifacts'], true)) $db->exec('DELETE FROM `' . $table . '`');
        }
        $db->prepare('UPDATE backup_artifacts SET created_by=NULL WHERE created_by<>?')->execute([$ownerId]);
        $db->prepare('DELETE FROM users WHERE id<>?')->execute([$ownerId]);
        $db->prepare('UPDATE users SET session_version=session_version+1 WHERE id=?')->execute([$ownerId]);
        $db->commit();
    } catch (Throwable $failure) {
        if ($db->inTransaction()) $db->rollBack();
        throw $failure;
    } finally { $db->exec('SET FOREIGN_KEY_CHECKS=1'); }
    // Delete only assets registered in this schema; never glob another environment's files.
    foreach ($assetKeys as $kind => $keys) foreach ($keys as $key) {
        $path = dirname(__DIR__) . '/storage/' . $kind . '/' . $key;
        if (is_file($path) && !unlink($path)) throw new RuntimeException('أُعيد ضبط البيانات لكن تعذر حذف ملف مسجل. راجع سجل الصيانة.');
    }
    append_owner_audit(['action'=>'local-reset-completed','actor_id'=>$ownerId,'environment'=>$environment,'backup_id'=>$recent]);
}
