<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require dirname(__DIR__) . '/app/backup.php';

$pdo = db();
$ownerEmail = 'backup-check-' . bin2hex(random_bytes(5)) . '@example.invalid';
$bookKey = bin2hex(random_bytes(20)) . '.pdf';
$bookStorage = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'storage' . DIRECTORY_SEPARATOR . 'books';
$backup = null;
$plainPath = null;
$tamperedPath = null;
$ownerId = 0;
$bookId = 0;
$encryptedPath = null;

try {
    $existing = (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn()
        + (int) $pdo->query('SELECT COUNT(*) FROM books')->fetchColumn()
        + (int) $pdo->query('SELECT COUNT(*) FROM backup_artifacts')->fetchColumn();
    if ($existing !== 0) throw new RuntimeException('This round-trip test requires an empty local Athar database.');

    $pdo->prepare("INSERT INTO users (email,password_hash,role) VALUES (?,?,'owner')")
        ->execute([$ownerEmail, password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT)]);
    $ownerId = (int) $pdo->lastInsertId();

    if (!is_dir($bookStorage) && !mkdir($bookStorage, 0700, true) && !is_dir($bookStorage)) throw new RuntimeException('Could not create the local book fixture directory.');
    $fixturePdf = "%PDF-1.4\nAthar backup fixture\n%%EOF\n";
    file_put_contents($bookStorage . DIRECTORY_SEPARATOR . $bookKey, $fixturePdf);
    $pdo->prepare('INSERT INTO books (title,author,source_url,rights_basis,file_key,file_size,can_download,is_published) VALUES (?,?,?,?,?,?,1,1)')
        ->execute(['Backup Fixture', 'Athar QA', 'https://example.invalid/book', 'Test-only permission', $bookKey, strlen($fixturePdf)]);
    $bookId = (int) $pdo->lastInsertId();

    putenv('ATHAR_BACKUP_KEY=' . bin2hex(random_bytes(32)));
    $backup = build_backup_archive($ownerId);
    $artifactQuery = $pdo->prepare('SELECT storage_key FROM backup_artifacts WHERE id=?');
    $artifactQuery->execute([$backup['id']]);
    $artifactKey = (string) $artifactQuery->fetchColumn();
    $encryptedPath = backup_directory() . DIRECTORY_SEPARATOR . $artifactKey;

    $plainPath = tempnam(sys_get_temp_dir(), 'athar-verify-');
    unlink($plainPath);
    decrypt_backup_file($encryptedPath, $plainPath, hex2bin((string) getenv('ATHAR_BACKUP_KEY')));
    $zip = new ZipArchive();
    if ($zip->open($plainPath, ZipArchive::CHECKCONS) !== true) throw new RuntimeException('The decrypted backup failed ZIP integrity verification.');
    $manifest = json_decode((string) $zip->getFromName('manifest.json'), true, 512, JSON_THROW_ON_ERROR);
    $books = json_decode((string) $zip->getFromName('database/books.json'), true, 512, JSON_THROW_ON_ERROR);
    $asset = $zip->getFromName('assets/books/' . $bookKey);
    $zip->close();
    if (($manifest['format'] ?? '') !== 'athar-full-backup-v1' || count($books) !== 1 || !is_string($asset)
        || !hash_equals(hash('sha256', $asset), (string) $manifest['book_assets'][0]['sha256'])) {
        throw new RuntimeException('The backup manifest, database snapshot, or PDF asset did not match.');
    }

    $tamperedPath = $encryptedPath . '.tampered';
    copy($encryptedPath, $tamperedPath);
    $handle = fopen($tamperedPath, 'r+b');
    fseek($handle, 30);
    $byte = fread($handle, 1);
    fseek($handle, 30);
    fwrite($handle, chr(ord($byte) ^ 1));
    fclose($handle);
    $badPath = tempnam(sys_get_temp_dir(), 'athar-invalid-');
    unlink($badPath);
    $tamperRejected = false;
    try { decrypt_backup_file($tamperedPath, $badPath, hex2bin((string) getenv('ATHAR_BACKUP_KEY'))); }
    catch (Throwable $exception) { $tamperRejected = true; }
    if (!$tamperRejected || is_file($badPath)) throw new RuntimeException('Tampered ciphertext was not rejected and cleaned up.');
    echo "PASS: encrypted archive, manifest, database snapshot, PDF asset, and tamper rejection.\n";
} finally {
    if (is_string($plainPath) && is_file($plainPath)) unlink($plainPath);
    if (is_string($tamperedPath) && is_file($tamperedPath)) unlink($tamperedPath);
    if ($backup) {
        $pdo->prepare('DELETE FROM backup_download_grants WHERE backup_id=?')->execute([$backup['id']]);
        $pdo->prepare('DELETE FROM backup_artifacts WHERE id=?')->execute([$backup['id']]);
    }
    if ($bookId) $pdo->prepare('DELETE FROM books WHERE id=?')->execute([$bookId]);
    if ($ownerId) {
        $pdo->prepare('DELETE FROM audit_logs WHERE actor_id=?')->execute([$ownerId]);
        $pdo->prepare('DELETE FROM users WHERE id=?')->execute([$ownerId]);
    }
    if (is_file($bookStorage . DIRECTORY_SEPARATOR . $bookKey)) unlink($bookStorage . DIRECTORY_SEPARATOR . $bookKey);
    if (is_string($encryptedPath) && is_file($encryptedPath)) unlink($encryptedPath);
    putenv('ATHAR_BACKUP_KEY');
}
