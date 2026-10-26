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
    $mailer=static function(string $to,string $subject,string $body):bool { $failure=null;$sent=send_platform_email($to,$subject,$body,$failure);if(!$sent)error_log('[Athar] OTP email unavailable: '.($failure??'unknown failure'));return $sent; };
    $kernel=new ApiKernel($pdo,new AuthService($pdo,new OtpService($pdo,$key,$mailer)),new RateLimiter($pdo,$key),new \Athar\NightPrayer\NightPrayerService($pdo,SecretKey::privateData()));
    try {$request=Request::fromGlobals();$response=$kernel->handle($request);}
    catch(ApiError $e){$request=new Request($_SERVER['REQUEST_METHOD']??'GET','/api/v1',[],['Accept-Language'=>$_SERVER['HTTP_ACCEPT_LANGUAGE']??'ar']);$response=$kernel->error($request,$e->getMessage(),$e->status,$e->fields);}
    $response->emit();
}catch(Throwable $e){error_log('[Athar API] Bootstrap unavailable.');(new Response(['error'=>['code'=>'service_unavailable','message'=>'الخدمة غير متاحة مؤقتًا.','fields'=>new stdClass()],'meta'=>['request_id'=>bin2hex(random_bytes(8))]],503))->emit();}
exit;
