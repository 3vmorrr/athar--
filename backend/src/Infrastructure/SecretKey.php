<?php
declare(strict_types=1);
namespace Athar\Infrastructure;
use RuntimeException;

final class SecretKey
{
    public static function authentication(): string
    {
        return self::load('ATHAR_AUTH_KEY', 'auth.key');
    }
    public static function privateData(): string
    {
        return self::load('ATHAR_DATA_KEY', 'data.key');
    }
    private static function load(string $variable, string $filename): string
    {
        $key = trim((string) (getenv($variable) ?: ''));
        if ($key === '') {
            $directory = dirname(__DIR__, 3) . '/storage/secrets';
            if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) throw new RuntimeException('Secret storage is unavailable.');
            $path = $directory . '/' . $filename;
            if (!is_file($path)) {
                $handle = @fopen($path, 'x');
                if ($handle) {
                    $generated = bin2hex(random_bytes(32));
                    $written = fwrite($handle, $generated); fflush($handle); fclose($handle); @chmod($path, 0600);
                    if ($written !== 64) throw new RuntimeException('Secret could not be saved.');
                }
            }
            $key = trim((string) file_get_contents($path));
        }
        if (!preg_match('/^[a-f0-9]{64}$/i', $key)) throw new RuntimeException($variable . ' must contain 64 hexadecimal characters.');
        return (string) hex2bin($key);
    }
}
