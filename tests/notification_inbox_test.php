<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Engagement\NotificationInboxService;
use Athar\Http\ApiError;
use DateTimeImmutable;
use DateTimeZone;

$checks=0;
function ni_assert(bool $value,string $label):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$label);}
function ni_denied(callable $call,int $status,string $label):void{
    try{$call();}catch(ApiError $e){ni_assert($e->status===$status,$label);return;}ni_assert(false,$label);
}

isolated_database(static function(PDO $db):void{
    $password=password_hash('InboxFixture123!',PASSWORD_DEFAULT);
    foreach(['one','two'] as $name)$db->prepare(
        'INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP())'
    )->execute([$name.'@example.test',$name,$name,$password,'user']);

    $service=new NotificationInboxService($db,str_repeat('k',32));
    $future=(new DateTimeImmutable('now',new DateTimeZone('UTC')))->modify('+2 days');
    $ids=[];
    for($i=1;$i<=5;$i++){
        $ids[]=$service->record(
            1,'lesson_reminders','lesson_reminder',(string)$i,
            'Lesson '.$i,'Body '.$i,'lesson:'.$i,hash('sha256','inbox-'.$i),$future
        );
    }
    $other=$service->record(
        2,'lesson_reminders','lesson_reminder','9',
        'Other','Private','lesson:9',hash('sha256','other'),$future
    );

    $page1=$service->page(1,['per_page'=>2]);
    ni_assert(count($page1['items'])===2&&$page1['pagination']['has_more'],'first cursor page bounded');
    ni_assert($page1['unread_count']===5,'unread count owner scoped');
    $page2=$service->page(1,['per_page'=>2,'cursor'=>$page1['pagination']['next_cursor']]);
    ni_assert(count($page2['items'])===2,'second cursor page continues without overlap');
    ni_assert(
        count(array_intersect(array_column($page1['items'],'id'),array_column($page2['items'],'id')))===0,
        'cursor pages do not duplicate ids'
    );

    $read1=$service->markRead(1,$ids[0]);
    $read2=$service->markRead(1,$ids[0]);
    ni_assert($read1['read']&&$read2['read'],'mark read is idempotent');
    ni_assert($service->page(1,['per_page'=>10])['unread_count']===4,'read updates badge count');

    $deleted1=$service->delete(1,$ids[1]);
    $deleted2=$service->delete(1,$ids[1]);
    ni_assert($deleted1['deleted']&&$deleted2['deleted'],'delete is idempotent for owner');
    ni_denied(fn()=>$service->detail(1,$ids[1]),404,'deleted item hidden from detail');

    ni_denied(fn()=>$service->detail(1,$other),404,'other owner item is not visible');
    ni_denied(fn()=>$service->markRead(1,$other),404,'other owner item cannot be marked read');
    ni_denied(fn()=>$service->delete(1,$other),404,'other owner item cannot be deleted');

    $dedupe=$service->record(
        1,'lesson_reminders','lesson_reminder','1',
        'Changed title','Changed body','lesson:1',hash('sha256','inbox-1'),$future
    );
    ni_assert($dedupe===$ids[0],'record dedupe reuses existing owner row');
    ni_assert($service->detail(1,$ids[0])['title']==='Lesson 1','dedupe replay does not rewrite retained inbox content');

    $expired=$service->record(
        1,'custom_routines','custom','x','Short','Expired later','home',
        hash('sha256','expires'),(new DateTimeImmutable('now',new DateTimeZone('UTC')))->modify('+1 hour')
    );
    $db->prepare('UPDATE notification_inbox SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?')->execute([$expired]);
    ni_denied(fn()=>$service->detail(1,$expired),404,'expired detail is unavailable');
    ni_assert(!in_array($expired,array_column($service->page(1,['per_page'=>50])['items'],'id'),true),'expired item absent from page and badge');

    $tampered=$page1['pagination']['next_cursor'];
    $tampered=substr($tampered,0,-1).($tampered[-1]==='A'?'B':'A');
    ni_denied(fn()=>$service->page(1,['cursor'=>$tampered]),422,'tampered cursor rejected');
    ni_denied(fn()=>$service->page(2,['cursor'=>$page1['pagination']['next_cursor']]),422,'cursor cannot cross owners');
});

echo 'PASS: '.$checks." inbox isolation/cursor/read/delete/dedupe/expiry assertions.\n";
