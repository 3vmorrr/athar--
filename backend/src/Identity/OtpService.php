<?php
declare(strict_types=1);
namespace Athar\Identity;
use PDO;
use Throwable;
use InvalidArgumentException;
final class OtpService
{
    private PDO $db; private string $key; private $mailer;
    public function __construct(PDO $db, string $key, callable $mailer) { $this->db = $db; $this->key = $key; $this->mailer = $mailer; }
    public function issue(int $userId, string $purpose): bool
    {
        if (!in_array($purpose, ['verify_email','password_reset'], true)) throw new InvalidArgumentException('Invalid OTP purpose.');
        $q = $this->db->prepare('SELECT email,preferences_json FROM users WHERE id=?'); $q->execute([$userId]); $user = $q->fetch(PDO::FETCH_ASSOC);
        if (!$user || !is_string($user['email'] ?? null)) return false;
        $email=(string)$user['email'];
        $preferences=json_decode((string)($user['preferences_json']??'{}'),true)?:[];
        $locale=($preferences['language']??'ar')==='en'?'en':'ar';
        $code = (string) random_int(100000, 999999);
        $hash = $this->hash($userId, $purpose, $code);
        $this->db->prepare("INSERT INTO auth_tokens (user_id,token_type,token_hash,hash_scheme,expires_at) VALUES (?,?,?,'hmac-sha256-v1',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 10 MINUTE))")->execute([$userId, $purpose, $hash]);
        $id = (int) $this->db->lastInsertId();
        if($locale==='en'){
            $subject=$purpose==='verify_email'?'Your Athar email verification code':'Your Athar password reset code';
            $body="Your verification code is: {$code}\n\nThis code expires in 10 minutes. Do not share it with anyone.\n\nAthar | أثر";
        }else{
            $subject = $purpose === 'verify_email' ? 'رمز تأكيد البريد الإلكتروني في أثر' : 'رمز إعادة تعيين كلمة مرور أثر';
            $body = "رمز التحقق الخاص بك: {$code}\n\nتنتهي صلاحية الرمز خلال 10 دقائق. لا تشاركه مع أي شخص.\n\nAthar | أثر";
        }
        try { $sent = (bool) ($this->mailer)($email,$subject,$body,$userId,$purpose,$id,$locale); }
        catch (Throwable $e) { error_log('[Athar] OTP queue unavailable.'); $sent = false; }
        if ($sent) $this->db->prepare('UPDATE auth_tokens SET consumed_at=UTC_TIMESTAMP() WHERE user_id=? AND token_type=? AND id<? AND consumed_at IS NULL')->execute([$userId, $purpose, $id]);
        else $this->db->prepare('DELETE FROM auth_tokens WHERE id=?')->execute([$id]);
        return $sent;
    }
    public function consume(int $userId, string $purpose, string $code): bool
    {
        $owns = !$this->db->inTransaction(); if ($owns) $this->db->beginTransaction();
        try {
            $q = $this->db->prepare('SELECT id,token_hash,hash_scheme,failed_attempts FROM auth_tokens WHERE user_id=? AND token_type=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP() ORDER BY id DESC LIMIT 1 FOR UPDATE');
            $q->execute([$userId, $purpose]); $token = $q->fetch(PDO::FETCH_ASSOC);
            $valid = false;
            if ($token && (int) $token['failed_attempts'] < 5) {
                $candidate = $token['hash_scheme'] === 'hmac-sha256-v1' ? $this->hash($userId, $purpose, $code) : hash('sha256', $code);
                $valid = preg_match('/^[0-9]{6}$/', $code) === 1 && hash_equals($token['token_hash'], $candidate);
                if ($valid) $this->db->prepare('UPDATE auth_tokens SET consumed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$token['id']]);
                else $this->db->prepare('UPDATE auth_tokens SET consumed_at=IF(failed_attempts>=4,UTC_TIMESTAMP(),consumed_at),failed_attempts=LEAST(5,failed_attempts+1) WHERE id=?')->execute([$token['id']]);
            }
            if ($owns) $this->db->commit(); return $valid;
        } catch (Throwable $e) { if ($owns && $this->db->inTransaction()) $this->db->rollBack(); throw $e; }
    }
    private function hash(int $userId, string $purpose, string $code): string { return hash_hmac('sha256', $userId . ':' . $purpose . ':' . $code, $this->key); }
}
