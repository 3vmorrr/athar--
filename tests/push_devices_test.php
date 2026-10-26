<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Engagement\PushDeviceService;
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;

$checks=0;
function pd_assert(bool $value,string $label):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$label);}

isolated_database(static function(PDO $db):void{
    $password=password_hash('PushDeviceFixture123!',PASSWORD_DEFAULT);
    foreach(['one','two'] as $name)$db->prepare(
        'INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP())'
    )->execute([$name.'@example.test',$name,$name,$password,'user']);

    $key=random_bytes(32);$environment='012345abcdef';
    $auth=new AuthService($db,new OtpService($db,$key,static fn()=>false));
    $one=$auth->login('one@example.test','PushDeviceFixture123!')['session'];
    $two=$auth->login('two@example.test','PushDeviceFixture123!')['session'];
    $actorOne=$auth->authenticate($one['access_token']);
    $actorTwo=$auth->authenticate($two['access_token']);
    $service=new PushDeviceService($db,$key,$environment);

    $installA='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
    $installB='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';
    $tokenA='token_A_12345678901234567890';
    $tokenB='token_B_12345678901234567890';
    $tokenC='token_C_12345678901234567890';

    $first=$service->register(1,$actorOne['family_id'],$installA,[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenA,
    ]);
    pd_assert($first['status']==='active','first registration active');
    pd_assert(!array_key_exists('token',$first),'registration response never returns raw token');

    $service->register(1,$actorOne['family_id'],$installA,[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenB,
    ]);
    $active=$service->activeForUser(1);
    pd_assert(count($active)===1&&$active[0]['token']===$tokenB,'rotation replaces token for same installation');

    $service->register(1,$actorOne['family_id'],$installB,[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenC,
    ]);
    pd_assert(count($service->activeForUser(1))===2,'same owner supports multiple devices');

    $service->register(2,$actorTwo['family_id'],'cccccccccccccccccccccccccccccccc',[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenB,
    ]);
    pd_assert(count($service->activeForUser(1))===1,'same provider token cannot remain active for old owner');
    pd_assert(count($service->activeForUser(2))===1,'reassigned token belongs only to new owner');

    $list=$service->list(2);
    $encoded=json_encode($list,JSON_THROW_ON_ERROR);
    pd_assert(!str_contains($encoded,'token_B_'),'device list never exposes token material');
    pd_assert(!str_contains($encoded,hash('sha256',$tokenB)),'device list never exposes token hash');

    $service->revokeInstallation(1,$installB);
    pd_assert($service->activeForUser(1)===[],'explicit installation revoke removes delivery eligibility');

    $service->register(2,$actorTwo['family_id'],'dddddddddddddddddddddddddddddddd',[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenA,
    ]);
    pd_assert(count($service->activeForUser(2))===2,'second owner has two active installations');
    $revoked=$service->revokeSessionFamily(2,$actorTwo['family_id']);
    pd_assert($revoked===2&&$service->activeForUser(2)===[],'logout family revokes all devices registered by that session family');

    $mismatch=false;
    try{$service->register(1,$actorOne['family_id'],$installA,[
        'provider'=>'fcm_v1','environment'=>'fedcba654321','token'=>$tokenA,
    ]);}catch(\Athar\Http\ApiError $e){$mismatch=$e->status===409;}
    pd_assert($mismatch,'environment mismatch is rejected');

    $service->register(1,$actorOne['family_id'],$installA,[
        'provider'=>'fcm_v1','environment'=>$environment,'token'=>$tokenA,
    ]);
    $db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='fixture' WHERE family_id=?")
        ->execute([$actorOne['family_id']]);
    pd_assert($service->activeForUser(1)===[],'revoked session family is ineligible even before device-row cleanup');
});

echo 'PASS: '.$checks." push device uniqueness/rotation/owner/environment/logout/session assertions.\n";
