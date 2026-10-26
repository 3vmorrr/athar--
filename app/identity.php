<?php
declare(strict_types=1);
function web_identity_otp(): \Athar\Identity\OtpService
{
    static $service;
    return $service ??= new \Athar\Identity\OtpService(db(), \Athar\Infrastructure\SecretKey::authentication(), static function(string $email,string $subject,string $body): bool {
        $failure = null;
        $sent = send_platform_email($email,$subject,$body,$failure);
        if (!$sent) error_log('[Athar] Account email delivery unavailable.');
        return $sent;
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
