<?php
declare(strict_types=1);
use Athar\Http\ApiKernel;
use Athar\Http\ApiError;
use Athar\Http\Request;
use Athar\Http\Response;
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Infrastructure\Database;
use Athar\Infrastructure\SecretKey;
require_once __DIR__.'/bootstrap.php';
try {
    $pdo=Database::connect();$key=SecretKey::authentication();
    $mailer=static function(string $to,string $subject,string $body,int $userId,string $purpose,int $tokenId,string $locale) use ($pdo):bool {
        try {
            return (new \Athar\Engagement\EmailOutboxService($pdo))->enqueueTransactional(
                $userId,
                $subject,
                $body,
                'otp:' . $purpose . ':' . $tokenId,
                $locale,
                600,
                authTokenId: $tokenId,
                purpose: $purpose
            );
        } catch (Throwable $queueError) {
            error_log('[Athar API] OTP email queue unavailable.');
            return false;
        }
    };
    $kernel=new ApiKernel($pdo,new AuthService($pdo,new OtpService($pdo,$key,$mailer)),new RateLimiter($pdo,$key),new \Athar\NightPrayer\NightPrayerService($pdo,SecretKey::privateData()));
    try {$request=Request::fromGlobals();$response=$kernel->handle($request);}
    catch(ApiError $e){$request=new Request($_SERVER['REQUEST_METHOD']??'GET','/api/v1',[],['Accept-Language'=>$_SERVER['HTTP_ACCEPT_LANGUAGE']??'ar']);$response=$kernel->error($request,$e->getMessage(),$e->status,$e->fields);}
    $response->emit();
}catch(Throwable $e){error_log('[Athar API] Bootstrap unavailable.');(new Response(['error'=>['code'=>'service_unavailable','message'=>'الخدمة غير متاحة مؤقتًا.','fields'=>new stdClass()],'meta'=>['request_id'=>bin2hex(random_bytes(8))]],503))->emit();}
exit;
