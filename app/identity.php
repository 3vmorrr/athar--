<?php
declare(strict_types=1);
function web_identity_otp(): \Athar\Identity\OtpService
{
    static $service;
    return $service ??= new \Athar\Identity\OtpService(db(), \Athar\Infrastructure\SecretKey::authentication(), static function(string $email,string $subject,string $body,int $userId,string $purpose,int $tokenId,string $locale): bool {
        try {
            return (new \Athar\Engagement\EmailOutboxService(db()))->enqueueTransactional(
                $userId,
                $subject,
                $body,
                'otp:' . $purpose . ':' . $tokenId,
                $locale,
                600
            );
        } catch (Throwable $queueError) {
            error_log('[Athar] Account email queue unavailable.');
            return false;
        }
    });
}
function web_identity_auth(): \Athar\Identity\AuthService
{
    static $service;
    return $service ??= new \Athar\Identity\AuthService(db(),web_identity_otp());
}
function web_identity_rate(string $scope,string $identity,int $limit,int $window): bool
{
    $rates = new \Athar\Identity\RateLimiter(db(),\Athar\Infrastructure\SecretKey::authentication());
    $ip = (string)($_SERVER['REMOTE_ADDR'] ?? '127.0.0.1');
    return $rates->consume($scope.':ip',$ip,$limit*5,$window)
        && ($identity === '' || $rates->consume($scope.':identity',$identity,$limit,$window));
}
