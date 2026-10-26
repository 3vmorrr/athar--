<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Http\ApiKernel;
use Athar\Http\Request;

$checks=0;
function assert_notification(bool $condition,string $name):void
{
    global $checks;
    $checks++;
    if(!$condition)throw new RuntimeException('FAIL: '.$name);
}

isolated_database(static function(PDO $db):void {
    $key=random_bytes(32);
    $codes=[];
    $otp=new OtpService($db,$key,static function($email,$subject,$body)use(&$codes):bool{
        preg_match('/[0-9]{6}/',$body,$m);
        $codes[$email]=$m[0];
        return true;
    });
    $auth=new AuthService($db,$otp);
    $api=new ApiKernel($db,$auth,new RateLimiter($db,$key));

    $tokens=[];
    foreach(['notify_one','notify_two'] as $name){
        $email=$name.'@example.invalid';
        $registered=$auth->register([
            'email'=>$email,
            'display_name'=>'Notification Fixture',
            'username'=>$name,
            'password'=>'SafePassword123!',
            'password_confirmation'=>'SafePassword123!',
        ]);
        $tokens[]=$auth->verify($registered['user']['id'],$codes[$email])['session']['access_token'];
    }
    $call=static fn(string $method,string $path,array $body=[],int $actor=0)=>
        $api->handle(new Request($method,'/api/v1'.$path,$body,['Authorization'=>'Bearer '.$tokens[$actor]],[]));

    $initial=$call('GET','/me/notification-preferences')->body['data'];
    assert_notification($initial['revision']===0,'missing row exposes revision zero');
    assert_notification($initial['master_enabled']===false,'notifications default off');
    assert_notification($initial['consent']['current']===false,'consent defaults absent');
    assert_notification(!in_array(true,$initial['effective_categories'],true),'all effective categories default off');

    $bad=$call('PATCH','/me/notification-preferences',[
        'expected_revision'=>0,
        'master_enabled'=>true,
        'consent_version'=>0,
        'categories'=>['lesson_reminders'=>true],
    ]);
    assert_notification($bad->status===422,'enabling requires current consent');

    $enabled=$call('PATCH','/me/notification-preferences',[
        'expected_revision'=>0,
        'master_enabled'=>true,
        'consent_version'=>1,
        'categories'=>['lesson_reminders'=>true,'night_prayer'=>true],
    ]);
    assert_notification($enabled->status===200,'explicit current consent accepted');
    $data=$enabled->body['data'];
    assert_notification($data['revision']===1&&$data['consent']['current']===true,'revision and consent recorded');
    assert_notification($data['effective_categories']['lesson_reminders']===true,'opted-in category becomes effective');

    $stale=$call('PATCH','/me/notification-preferences',[
        'expected_revision'=>0,
        'categories'=>['following_updates'=>true],
    ]);
    assert_notification($stale->status===409,'stale preference update rejected');

    $other=$call('GET','/me/notification-preferences',[],1)->body['data'];
    assert_notification($other['revision']===0&&!in_array(true,$other['categories'],true),'preferences are owner scoped');

    $paused=$call('PATCH','/me/notification-preferences',[
        'expected_revision'=>1,
        'master_enabled'=>false,
    ])->body['data'];
    assert_notification($paused['categories']['night_prayer']===true,'master pause retains explicit category choice');
    assert_notification($paused['effective_categories']['night_prayer']===false,'master pause disables delivery');

    $revoked=$call('PATCH','/me/notification-preferences',[
        'expected_revision'=>2,
        'consent_version'=>0,
    ])->body['data'];
    assert_notification($revoked['master_enabled']===false&&$revoked['consent']['current']===false,'consent revocation fails closed');
});

echo 'PASS: '.$checks." notification preference consent/ownership/CAS assertions.\n";
