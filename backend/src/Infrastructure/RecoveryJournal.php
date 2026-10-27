<?php
declare(strict_types=1);
namespace Athar\Infrastructure;

use PDO;
use RuntimeException;

/** Durable compensation plan; only the exclusive maintenance holder may mutate it. */
final class RecoveryJournal
{
    public string $id;
    private array $files;
    private static function storage(): string { return str_replace('\\', '/', dirname(__DIR__, 3)) . '/storage'; }
    public static function path(): string { return self::storage() . '/locks/recovery-pending.json'; }
    public static function exists(): bool { return is_file(self::path()); }

    public static function begin(PDO $db, array $staged): self
    {
        if (self::exists()) throw new RuntimeException('An interrupted recovery must be repaired before another restoration.');
        $journal = new self(); $journal->id = bin2hex(random_bytes(16)); $journal->files = [];
        foreach ($staged as $asset => $stage) {
            if (!self::validAsset($asset) || str_replace('\\', '/', dirname($stage)) !== self::storage() . '/backups') throw new RuntimeException('Invalid staged recovery asset.');
            $name = basename($stage);
            if (!preg_match('/^restore-[a-f0-9]{32}\.asset$/D', $name) || !is_file($stage) || is_link($stage)) throw new RuntimeException('Invalid staged recovery file.');
            $destination = self::storage() . '/' . $asset;
            if (is_link(dirname($destination)) || is_link($destination) || is_dir($destination)) throw new RuntimeException('Restore asset destination is unsafe.');
            $journal->files[$asset] = [
                'stage'=>$name, 'stage_sha256'=>hash_file('sha256', $stage),
                'original'=>is_file($destination) ? 'restore-original-' . bin2hex(random_bytes(16)) . '.asset' : null,
                'original_sha256'=>is_file($destination) ? hash_file('sha256', $destination) : null,
            ];
        }
        $payload = json_encode(['version'=>1,'id'=>$journal->id,'database'=>(string)$db->query('SELECT DATABASE()')->fetchColumn(),'files'=>$journal->files], JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
        $temporary = self::path() . '.' . $journal->id . '.tmp';
        $handle = fopen($temporary, 'xb');
        if (!$handle) throw new RuntimeException('The recovery journal could not be created.');
        @chmod($temporary, 0600);
        try {
            if (fwrite($handle, $payload) !== strlen($payload) || !fflush($handle) || (function_exists('fsync') && !fsync($handle))) throw new RuntimeException('The recovery journal could not be persisted.');
        } finally { fclose($handle); }
        if (!rename($temporary, self::path())) throw new RuntimeException('The recovery journal could not be activated.');
        return $journal;
    }

    public function originalFor(string $asset): ?string
    {
        $name = $this->files[$asset]['original'];
        return $name === null ? null : self::storage() . '/backups/' . $name;
    }

    public static function clear(): void
    {
        if (self::exists() && !unlink(self::path())) throw new RuntimeException('The completed recovery journal could not be removed.');
    }

    private static function validAsset(string $asset): bool
    {
        return (bool) preg_match('#^(?:books/[a-f0-9]{40}\.pdf|scholars/[a-f0-9]{40}\.(?:jpg|png|webp))$#D', $asset);
    }

    /** A committed transaction keeps installed files; an uncommitted one restores originals. */
    public static function repair(PDO $db): void
    {
        if (!self::exists()) return;
        if (is_link(self::path()) || filesize(self::path()) > 10 * 1024 * 1024) throw new RuntimeException('Recovery journal requires operator review.');
        $data = json_decode((string) file_get_contents(self::path()), true, 512, JSON_THROW_ON_ERROR);
        if (($data['version'] ?? null) !== 1 || !preg_match('/^[a-f0-9]{32}$/D', $data['id'] ?? '') || ($data['database'] ?? '') !== $db->query('SELECT DATABASE()')->fetchColumn() || !is_array($data['files'] ?? null)) throw new RuntimeException('Recovery journal does not match this database.');
        // Validate the entire plan before touching any path. Malformed plans are preserved.
        foreach ($data['files'] as $asset => $file) {
            if (!self::validAsset($asset) || !is_array($file) || !preg_match('/^restore-[a-f0-9]{32}\.asset$/D', $file['stage'] ?? '') || !array_key_exists('original', $file) || ($file['original'] !== null && !preg_match('/^restore-original-[a-f0-9]{32}\.asset$/D', $file['original']))) throw new RuntimeException('Invalid recovery journal asset plan.');
            if (!preg_match('/^[a-f0-9]{64}$/D', $file['stage_sha256'] ?? '') || ($file['original'] !== null && !preg_match('/^[a-f0-9]{64}$/D', $file['original_sha256'] ?? ''))) throw new RuntimeException('Invalid recovery journal integrity metadata.');
            foreach ([self::storage() . '/' . $asset, self::storage() . '/backups/' . $file['stage'], $file['original'] === null ? '' : self::storage() . '/backups/' . $file['original']] as $path) {
                if ($path !== '' && (is_link($path) || is_link(dirname($path)) || is_dir($path))) throw new RuntimeException('Unsafe recovery journal path.');
            }
        }
        $query = $db->prepare("SELECT id FROM audit_logs WHERE action='backup-restore-committed' AND target_type='restore-operation' AND target_id=? LIMIT 1");
        $query->execute([$data['id']]); $committed = (bool) $query->fetchColumn();
        // Validate every authoritative file before compensating any one of them.
        foreach ($data['files'] as $asset => $file) {
            $destination = self::storage() . '/' . $asset;
            $original = $file['original'] === null ? null : self::storage() . '/backups/' . $file['original'];
            $checkPath = $committed ? $destination : ($original !== null && is_file($original) ? $original : ($original !== null ? $destination : null));
            $expected = $committed ? $file['stage_sha256'] : $file['original_sha256'];
            if ($checkPath !== null && (!is_file($checkPath) || !hash_equals($expected, hash_file('sha256', $checkPath) ?: ''))) throw new RuntimeException('Recovery file integrity requires operator review; original data is preserved.');
        }
        foreach (array_reverse($data['files'], true) as $asset => $file) {
            $destination = self::storage() . '/' . $asset;
            $stage = self::storage() . '/backups/' . $file['stage'];
            $original = $file['original'] === null ? null : self::storage() . '/backups/' . $file['original'];
            if (!$committed) {
                if ($original !== null && is_file($original)) {
                    if (is_file($destination) && !unlink($destination)) throw new RuntimeException('Recovery asset rollback failed.');
                    if (!rename($original, $destination)) throw new RuntimeException('Recovery original asset could not be restored.');
                } elseif ($original === null && !is_file($stage) && is_file($destination)) {
                    if (!unlink($destination)) throw new RuntimeException('Recovery new asset could not be rolled back.');
                } elseif ($original !== null && !is_file($destination)) throw new RuntimeException('Recovery original asset is missing; operator review required.');
            } elseif (!is_file($destination)) throw new RuntimeException('A committed recovery asset is missing; operator review required.');
            foreach ([$stage, $original] as $temporary) if ($temporary !== null && is_file($temporary) && !unlink($temporary)) throw new RuntimeException('Recovery temporary file cleanup failed.');
        }
        self::clear();
    }
}
