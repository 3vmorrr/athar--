<?php
declare(strict_types=1);

require_once __DIR__ . '/helpers.php';

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
    $acquired = db()->query("SELECT GET_LOCK('athar_maintenance_lock',0)")->fetchColumn();
    if ((string) $acquired !== '1') throw new RuntimeException('هناك عملية صيانة أخرى جارية بالفعل.');
}

function release_maintenance_lock(): void
{
    db()->query("SELECT RELEASE_LOCK('athar_maintenance_lock')");
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
        $excluded = ['auth_tokens','backup_artifacts','backup_download_grants'];
        $counts = []; $checksums = []; $bookFiles = []; $excludedTables = [];
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
            if ($table === 'books') foreach ($rows as $book) if (!empty($book['file_key'])) $bookFiles[] = (string) $book['file_key'];
        }
        $assetManifest = [];
        foreach (array_unique($bookFiles) as $fileKey) {
            if (!preg_match('/^[a-f0-9]{40}\.pdf$/', $fileKey)) throw new RuntimeException('مفتاح ملف كتاب غير صالح؛ أوقفنا إنشاء النسخة.');
            $sourceFile = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books' . DIRECTORY_SEPARATOR . $fileKey;
            if (!is_file($sourceFile) || !$zip->addFile($sourceFile, 'assets/books/' . $fileKey)) throw new RuntimeException('تعذر تضمين أحد ملفات الكتب المسجلة.');
            $assetManifest[] = ['key' => $fileKey, 'sha256' => hash_file('sha256', $sourceFile), 'bytes' => filesize($sourceFile)];
        }
        $migration = $pdo->query('SELECT MAX(version) FROM schema_migrations')->fetchColumn();
        $manifest = [
            'format' => 'athar-full-backup-v1', 'created_at_utc' => gmdate('c'), 'schema_version' => (string) ($migration ?: 'unknown'),
            'database_name' => $database, 'tables' => $counts, 'checksums_sha256' => $checksums, 'book_assets' => $assetManifest,
            'excluded_tables' => $excludedTables,
            'excluded' => ['database credentials and environment secrets', 'auth token rows', 'backup artifact and grant rows', 'logs and temporary files', 'external YouTube media'],
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
        if ($zip instanceof ZipArchive) @$zip->close();
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

function restore_backup_archive(int $backupId, int $ownerId): void
{
    $key = backup_key();
    $query = db()->prepare("SELECT * FROM backup_artifacts WHERE id=? AND status IN ('ready','downloaded') AND verified_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR)");
    $query->execute([$backupId]); $artifact = $query->fetch();
    if (!$artifact) throw new RuntimeException('يلزم اختيار نسخة كاملة تم التحقق منها خلال آخر 24 ساعة.');
    $encrypted = backup_directory() . DIRECTORY_SEPARATOR . $artifact['storage_key'];
    if (!preg_match('/^[a-f0-9]{40}\.enc$/', (string) $artifact['storage_key']) || !is_file($encrypted) || !hash_equals((string) $artifact['sha256'], hash_file('sha256', $encrypted) ?: '')) throw new RuntimeException('ملف النسخة مفقود أو بصمته غير مطابقة.');
    $directory = ensure_backup_directory(); $plain = tempnam($directory, 'athar-restore-');
    if ($plain === false) throw new RuntimeException('تعذر إنشاء مساحة استعادة مؤقتة.'); @chmod($plain, 0600);
    $zip = new ZipArchive(); $staged = [];
    try {
        decrypt_backup_file($encrypted, $plain, $key);
        if ($zip->open($plain, ZipArchive::CHECKCONS) !== true) throw new RuntimeException('حاوية النسخة غير صالحة.');
        $manifest = json_decode((string) $zip->getFromName('manifest.json'), true, 512, JSON_THROW_ON_ERROR);
        if (($manifest['format'] ?? '') !== 'athar-full-backup-v1' || !is_array($manifest['tables'] ?? null) || !is_array($manifest['checksums_sha256'] ?? null)) throw new RuntimeException('بيانات النسخة غير مكتملة.');
        $db = db(); $database = getenv('ATHAR_DB_NAME') ?: 'athar';
        if (($manifest['database_name'] ?? '') !== $database) throw new RuntimeException('اسم قاعدة البيانات في النسخة لا يطابق البيئة الحالية.');
        $currentVersion = (string) ($db->query('SELECT MAX(version) FROM schema_migrations')->fetchColumn() ?: 'unknown');
        if (($manifest['schema_version'] ?? '') !== $currentVersion) throw new RuntimeException('إصدار مخطط النسخة لا يطابق التطبيق الحالي؛ حدّث أو اختر نسخة متوافقة.');
        foreach ($manifest['checksums_sha256'] as $entry => $expected) {
            if (!is_string($entry) || str_contains($entry, '..') || str_starts_with($entry, '/') || !is_string($expected)) throw new RuntimeException('مسار داخل النسخة غير صالح.');
            $contents = $zip->getFromName($entry);
            if ($contents === false || !hash_equals($expected, hash('sha256', $contents))) throw new RuntimeException('فشل التحقق من أحد ملفات النسخة.');
        }
        $excluded = ['auth_tokens','backup_artifacts','backup_download_grants'];
        $tables = $db->query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=" . $db->quote($database) . " AND TABLE_TYPE='BASE TABLE'")->fetchAll(PDO::FETCH_COLUMN);
        $restoreTables = [];
        foreach ($manifest['tables'] as $table => $count) {
            if (!in_array($table, $tables, true) || in_array($table, $excluded, true) || !is_int($count) || $count < 0) throw new RuntimeException('النسخة تحتوي على جدول غير متوافق.');
            $rows = json_decode((string) $zip->getFromName('database/' . $table . '.json'), true, 512, JSON_THROW_ON_ERROR);
            if (!is_array($rows) || count($rows) !== $count) throw new RuntimeException('عدد السجلات في النسخة لا يطابق manifest.');
            $restoreTables[$table] = $rows;
        }
        if (count($restoreTables) !== count($tables) - count(array_intersect($excluded, $tables))) throw new RuntimeException('النسخة لا تحتوي على كل جداول التطبيق الحالية.');
        foreach (($manifest['book_assets'] ?? []) as $asset) {
            $assetKey = (string) ($asset['key'] ?? '');
            if (!preg_match('/^[a-f0-9]{40}\.pdf$/', $assetKey)) throw new RuntimeException('اسم ملف كتاب داخل النسخة غير صالح.');
            $bytes = $zip->getFromName('assets/books/' . $assetKey);
            if ($bytes === false || !hash_equals((string) $asset['sha256'], hash('sha256', $bytes)) || strlen($bytes) !== (int) $asset['bytes']) throw new RuntimeException('تعذر التحقق من ملف كتاب ضمن النسخة.');
            $stage = $directory . DIRECTORY_SEPARATOR . 'restore-' . bin2hex(random_bytes(16)) . '.pdf';
            if (file_put_contents($stage, $bytes, LOCK_EX) !== strlen($bytes)) throw new RuntimeException('تعذر تجهيز ملف كتاب للاستعادة.'); @chmod($stage, 0600); $staged[$assetKey] = $stage;
        }
        $zip->close(); $zip = new ZipArchive();
        $oldOwner = $db->prepare("SELECT email,password_hash FROM users WHERE id=? AND role='owner'"); $oldOwner->execute([$ownerId]); $ownerCredentials = $oldOwner->fetch();
        if (!$ownerCredentials) throw new RuntimeException('تعذر التحقق من حساب المالك الحالي.');
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
            $preserve = $db->prepare("UPDATE users SET password_hash=?,role='owner',session_version=session_version+1 WHERE email=?");
            $preserve->execute([$ownerCredentials['password_hash'], $ownerCredentials['email']]);
            if (!$preserve->rowCount()) $db->prepare("INSERT INTO users(email,password_hash,role,session_version) VALUES (?,?,'owner',2)")->execute([$ownerCredentials['email'], $ownerCredentials['password_hash']]);
            $db->exec('DELETE FROM auth_tokens'); $db->exec('DELETE FROM backup_download_grants');
            $db->commit();
        } catch (Throwable $failure) { if ($db->inTransaction()) $db->rollBack(); throw $failure; }
        finally { $db->exec('SET FOREIGN_KEY_CHECKS=1'); }
        $assetsDir = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
        if (!is_dir($assetsDir)) mkdir($assetsDir, 0700, true);
        foreach (glob($assetsDir . DIRECTORY_SEPARATOR . '*.pdf') ?: [] as $oldFile) if (preg_match('/^[a-f0-9]{40}\.pdf$/', basename($oldFile))) @unlink($oldFile);
        foreach ($staged as $assetKey => $stage) { if (!rename($stage, $assetsDir . DIRECTORY_SEPARATOR . $assetKey)) throw new RuntimeException('استعيدت البيانات لكن تعذر نقل ملف كتاب؛ أعد الاستعادة من النسخة.'); unset($staged[$assetKey]); }
        db()->prepare('INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$ownerId, 'backup-restored', 'backup', (string) $backupId]);
        append_owner_audit(['action' => 'backup-restored', 'actor_id' => $ownerId, 'backup_id' => $backupId]);
    } finally {
        if ($zip instanceof ZipArchive) @$zip->close(); if (is_file($plain)) @unlink($plain); foreach ($staged as $stage) if (is_file($stage)) @unlink($stage);
    }
}
