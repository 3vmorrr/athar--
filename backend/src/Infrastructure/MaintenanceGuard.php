<?php
declare(strict_types=1);
namespace Athar\Infrastructure;

use RuntimeException;

/** Request-lifetime reader/writer barrier for database and registered asset recovery. */
final class MaintenanceGuard
{
    private $handle;

    private function __construct($handle) { $this->handle = $handle; }

    public static function acquire(bool $exclusive = false): ?self
    {
        $directory = dirname(__DIR__, 3) . '/storage/locks';
        if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) throw new RuntimeException('Maintenance lock storage is unavailable.');
        $path = $directory . '/recovery.lock';
        if (is_link($path)) throw new RuntimeException('Maintenance lock path is invalid.');
        $handle = @fopen($path, 'c+b');
        if (!$handle) throw new RuntimeException('Maintenance lock storage is unavailable.');
        @chmod($path, 0600);
        if (!flock($handle, ($exclusive ? LOCK_EX : LOCK_SH) | LOCK_NB)) { fclose($handle); return null; }
        if (RecoveryJournal::exists()) {
            if (!$exclusive && !flock($handle, LOCK_EX | LOCK_NB)) { fclose($handle); return null; }
            try {
                $db = Database::connect();
                if ((string) $db->query("SELECT GET_LOCK('athar_maintenance_lock',0)")->fetchColumn() !== '1') { fclose($handle); return null; }
                try { RecoveryJournal::repair($db); }
                finally { $db->query("SELECT RELEASE_LOCK('athar_maintenance_lock')"); }
            } catch (\Throwable $failure) {
                error_log('[Athar recovery] Pending journal preserved; recovery requires retry or operator review.');
                fclose($handle); return null;
            }
            if (!$exclusive && !flock($handle, LOCK_SH | LOCK_NB)) { fclose($handle); return null; }
        }
        return new self($handle);
    }

    public function __destruct()
    {
        if (is_resource($this->handle)) { flock($this->handle, LOCK_UN); fclose($this->handle); }
    }
}
