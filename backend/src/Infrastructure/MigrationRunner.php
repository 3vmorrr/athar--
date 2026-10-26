<?php
declare(strict_types=1);
namespace Athar\Infrastructure;
use PDO;
use RuntimeException;
final class MigrationRunner
{
    public static function apply(PDO $db, string $directory): array
    {
        $applied = [];
        $exists = $db->query("SHOW TABLES LIKE 'schema_migrations'")->fetchColumn();
        $versions = $exists ? $db->query('SELECT version FROM schema_migrations')->fetchAll(PDO::FETCH_COLUMN) : [];
        $files = glob(rtrim($directory, '/\\') . '/*.sql') ?: []; sort($files);
        foreach ($files as $file) {
            $version = basename($file, '.sql');
            if (in_array($version, $versions, true)) continue;
            $sql = file_get_contents($file);
            if (!is_string($sql)) throw new RuntimeException('Migration could not be read.');
            // Historical migrations name the original schema. Always keep the caller's selected schema.
            $sql = preg_replace('/^\s*(?:CREATE DATABASE[^;]*|USE\s+[a-zA-Z0-9_`]+)\s*;\s*$/mi', '', $sql);
            foreach (preg_split('/;\s*(?:\r?\n|$)/', (string) $sql) ?: [] as $statement) {
                if (trim($statement) !== '') $db->exec($statement);
            }
            $check = $db->prepare('SELECT version FROM schema_migrations WHERE version=?'); $check->execute([$version]);
            if (!$check->fetchColumn()) throw new RuntimeException('Migration did not record its version: ' . $version);
            $applied[] = $version;
        }
        return $applied;
    }
}
