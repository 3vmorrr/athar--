<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Engagement\FollowService;
use Athar\Http\ApiError;
use Athar\Http\ApiKernel;
use Athar\Http\Request;
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;

$checks=0;
function follow_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}
function follow_denied(callable $call,int $status,string $label):void{
    try{$call();}catch(ApiError $e){follow_assert($e->status===$status,$label);return;}follow_assert(false,$label);
}

isolated_database(static function(PDO $db):void{
    $password=password_hash('FollowFixture123!',PASSWORD_DEFAULT);
    foreach(['one','two'] as $name)$db->prepare('INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP())')
        ->execute([$name.'@example.test',$name,$name,$password,'user']);
    $db->exec("INSERT INTO scholars(name,is_published,sort_order) VALUES
        ('Visible scholar',1,1),('Hidden scholar',0,2),('Parent hidden scholar',0,3)");
    $db->exec("INSERT INTO series(scholar_id,title,is_published,sort_order) VALUES
        (1,'Visible series',1,1),(1,'Hidden series',0,2),(3,'Hidden parent series',1,3)");

    $service=new FollowService($db);
    $state=$service->state(1,'scholar',1);
    follow_assert(!$state['following']&&$state['target_id']===1,'visible target begins unfollowed');

    $first=$service->follow(1,'scholar',1);
    $second=$service->follow(1,'scholar',1);
    follow_assert($first['following']&&$second['following'],'repeated scholar follow is idempotent');
    follow_assert((int)$db->query('SELECT COUNT(*) FROM scholar_follows WHERE user_id=1 AND scholar_id=1')->fetchColumn()===1,'unique scholar ownership row');
    follow_assert(!$service->state(2,'scholar',1)['following'],'follow state is owner isolated');

    $series=$service->follow(1,'series',1);
    follow_assert($series['following'],'visible series can be followed');
    follow_assert((int)$db->query('SELECT COUNT(*) FROM series_follows WHERE user_id=1 AND series_id=1')->fetchColumn()===1,'unique series ownership row');

    follow_denied(fn()=>$service->follow(1,'scholar',2),404,'hidden scholar cannot be followed');
    follow_denied(fn()=>$service->follow(1,'series',2),404,'hidden series cannot be followed');
    follow_denied(fn()=>$service->follow(1,'series',3),404,'series under hidden scholar cannot be followed');

    $db->exec('INSERT INTO scholar_follows(user_id,scholar_id) VALUES (1,2)');
    $visible=$service->list(1,['per_page'=>50]);
    follow_assert(!in_array(2,array_column(array_filter($visible['items'],fn($x)=>$x['target_type']==='scholar'),'target_id'),true),'list suppresses retained hidden scholar follow');
    $removedHidden=$service->unfollow(1,'scholar',2);
    follow_assert(!$removedHidden['following']&&(int)$db->query('SELECT COUNT(*) FROM scholar_follows WHERE user_id=1 AND scholar_id=2')->fetchColumn()===0,'hidden target can still be unfollowed idempotently');

    $list=$service->list(1,['per_page'=>50]);
    follow_assert(count($list['items'])===2,'mixed follow list returns visible scholar and series only');
    follow_assert($service->list(2,['per_page'=>50])['items']===[],'other owner list stays empty');

    $service->unfollow(1,'series',1);
    $service->unfollow(1,'series',1);
    follow_assert((int)$db->query('SELECT COUNT(*) FROM series_follows WHERE user_id=1 AND series_id=1')->fetchColumn()===0,'repeated unfollow is idempotent');

    $service->follow(1,'series',1);
    $db->exec('DELETE FROM series WHERE id=1');
    follow_assert((int)$db->query('SELECT COUNT(*) FROM series_follows WHERE user_id=1')->fetchColumn()===0,'deleted series cascades follow removal');

    $key=random_bytes(32);
    $auth=new AuthService($db,new OtpService($db,$key,static fn()=>false));
    $kernel=new ApiKernel($db,$auth,new RateLimiter($db,$key));
    $token=$auth->login('one@example.test','FollowFixture123!')['session']['access_token'];
    $headers=['Authorization'=>'Bearer '.$token];
    $put=$kernel->handle(new Request('PUT','/api/v1/me/follows/scholar/1',[],$headers));
    follow_assert($put->status===200&&$put->body['data']['following']===true,'authenticated follow API works');
    $get=$kernel->handle(new Request('GET','/api/v1/me/follows',[],$headers,['target_type'=>'scholar']));
    follow_assert($get->status===200&&count($get->body['data']['items'])===1,'owner follow list API works');
    $delete=$kernel->handle(new Request('DELETE','/api/v1/me/follows/scholar/1',[],$headers));
    follow_assert($delete->status===200&&$delete->body['data']['following']===false,'authenticated unfollow API works');
});

echo 'PASS: '.$checks." follow/idempotency/visibility/deletion/ownership/API assertions.\n";
