<?php
declare(strict_types=1);
namespace Athar\Identity;
use PDO;
final class RateLimiter
{
    private PDO $db; private string $key;
    public function __construct(PDO $db, string $key) { $this->db = $db; $this->key = $key; }
    public function consume(string $scope, string $identity, int $limit, int $window, ?int $now = null): bool
    {
        $now ??= time(); $start = intdiv($now, $window) * $window;
        $hash = hash_hmac('sha256', $scope . "\0" . $identity, $this->key);
        $this->db->prepare('INSERT INTO auth_rate_buckets (bucket_hash,window_start,attempts) VALUES (?,?,1) ON DUPLICATE KEY UPDATE attempts=LEAST(attempts+1,1000000)')->execute([$hash, $start]);
        $query = $this->db->prepare('SELECT attempts FROM auth_rate_buckets WHERE bucket_hash=? AND window_start=?'); $query->execute([$hash, $start]);
        return (int) $query->fetchColumn() <= $limit;
    }
}
