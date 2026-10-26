<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Learning\DiscussionService;
use Athar\Learning\LessonService;
use Athar\Http\ApiError;
$checks=0;
function learning_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}
function learning_denied(callable $call,int $status,string $label):void{try{$call();}catch(ApiError $e){learning_assert($e->status===$status,$label);return;}learning_assert(false,$label);}
isolated_database(static function(PDO $db):void{
    foreach(['reader','other','moderator'] as $name)$db->prepare("INSERT INTO users(email,username,display_name,password_hash,email_verified_at,role) VALUES (?,?,?,'unused',UTC_TIMESTAMP(),?)")->execute([$name.'@example.test',$name,$name,$name==='moderator'?'admin':'user']);
    $db->exec("INSERT INTO scholars(name,is_published) VALUES ('Fixture',1),('Hidden',0)");$db->exec("INSERT INTO series(scholar_id,title,is_published) VALUES(1,'Fixture series',1)");
    for($i=1;$i<=57;$i++)$db->prepare("INSERT INTO lessons(scholar_id,series_id,title,youtube_id,source_url,sort_order,is_published) VALUES(1,1,?,?,'https://example.invalid',?,1)")->execute(['Lesson '.$i,sprintf('!test%06d',$i),intdiv($i,2)]);
    $service=new LessonService($db);$discussion=new DiscussionService($db);$admin=['id'=>3,'role'=>'admin','email_verified_at'=>'2026-01-01','suspended_at'=>null];$reader=['id'=>1,'role'=>'user','email_verified_at'=>'2026-01-01'];
    learning_assert(array_column((new \Athar\Catalog\CatalogService($db))->home(1)['latest_lessons'],'id')===[57,56,55],'home latest section is distinct from playlist ordering');
    $all=$service->playlist(1,1,['per_page'=>50]);$tail=$service->playlist(1,1,['page'=>2,'per_page'=>50]);
    learning_assert(count($all['items'])===50&&count($tail['items'])===7,'playlist is complete beyond fifty');learning_assert($all['items'][0]['id']===1&&$tail['items'][0]['lesson_number']===51,'tied order uses stable ascending IDs');
    $watch=$service->watch(1,4);learning_assert($watch['lesson_number']===4&&$watch['series']['lesson_count']===57,'current series position is reliable');learning_assert(array_column($watch['playlist_preview'],'id')===[4,5,6],'collapsed preview starts with current then next lessons');learning_assert($watch['progress']===null,'opening does not invent progress');
    $db->exec("INSERT INTO video_progress(user_id,lesson_id,position_seconds,duration_seconds,is_complete) VALUES (1,4,50,100,0),(1,1,100,100,1)");
    learning_assert($service->series(1,1)['resume_lesson_id']===4,'resume finds in-progress lesson');learning_assert($service->series(1,1)['completed_count']===1,'series completion is private and accurate');learning_assert($service->series(2,1)['completed_count']===0,'another user has separate progress');
    $id=test_uuid();$posted=$discussion->add(1,1,['body'=>'Root fixture','client_id'=>$id]);learning_assert($posted['status']==='pending','root starts pending');learning_assert(count($discussion->page(1,1)['items'])===1,'owner sees own pending root');learning_assert($discussion->page(2,1)['items']===[],'pending root not public');
    learning_assert($discussion->add(1,1,['body'=>'Root fixture','client_id'=>$id],static fn()=>false)['replayed'],'retry bypasses only rate counting and creates no duplicate');learning_denied(fn()=>$discussion->add(1,1,['body'=>'Different body','client_id'=>$id]),409,'mutation payload cannot change');
    learning_denied(fn()=>$discussion->moderate($reader,$posted['id'],['status'=>'visible','expected_revision'=>1]),403,'ordinary user cannot moderate');$discussion->moderate($admin,$posted['id'],['status'=>'visible','expected_revision'=>1]);learning_assert(count($discussion->page(2,1)['items'])===1,'approved root public');
    learning_denied(fn()=>$discussion->moderate($admin,$posted['id'],['status'=>'hidden','expected_revision'=>1]),409,'stale moderation rejected');
    $reply=$discussion->add(2,1,['body'=>'Reply fixture','client_id'=>test_uuid(),'parent_id'=>$posted['id']]);learning_assert($discussion->page(2,1)['items'][0]['reply_count']===1,'reply author sees pending reply count');learning_assert($discussion->page(1,1)['items'][0]['reply_count']===0,'other readers do not see pending reply');
    $discussion->moderate($admin,$reply['id'],['status'=>'visible','expected_revision'=>1]);learning_assert(count($discussion->page(1,1,['parent_id'=>$posted['id']])['items'])===1,'approved reply is readable in thread');
    learning_denied(fn()=>$discussion->add(1,2,['body'=>'Wrong lesson','client_id'=>test_uuid(),'parent_id'=>$posted['id']]),404,'cross-lesson reply denied');learning_denied(fn()=>$discussion->add(1,1,['body'=>'Nested reply','client_id'=>test_uuid(),'parent_id'=>$reply['id']]),404,'deeper nesting denied');
    $discussion->moderate($admin,$posted['id'],['status'=>'hidden','expected_revision'=>2]);learning_assert($discussion->page(1,1)['items']===[],'hidden parent suppressed even from author view');learning_denied(fn()=>$discussion->page(2,1,['parent_id'=>$posted['id']]),404,'hidden parent does not expose visible descendants');
    learning_assert(count($discussion->moderation($admin,['status'=>'hidden'])['items'])===1,'moderator can inspect hidden row');learning_assert((int)$db->query("SELECT COUNT(*) FROM audit_logs WHERE target_type='comment'")->fetchColumn()===3,'moderation audited');
    $db->prepare('UPDATE users SET password_hash=?')->execute([password_hash('LearningFixture123!',PASSWORD_DEFAULT)]);
    $key=random_bytes(32);$auth=new \Athar\Identity\AuthService($db,new \Athar\Identity\OtpService($db,$key,static fn()=>false));$kernel=new \Athar\Http\ApiKernel($db,$auth,new \Athar\Identity\RateLimiter($db,$key));
    $headers=['Authorization'=>'Bearer '.$auth->login('reader@example.test','LearningFixture123!')['session']['access_token']];
    $adminHeaders=['Authorization'=>'Bearer '.$auth->login('moderator@example.test','LearningFixture123!')['session']['access_token']];
    learning_assert($kernel->handle(new \Athar\Http\Request('GET','/api/v1/lessons/4/watch',[],$headers))->status===200,'watch route emits authenticated JSON');
    learning_assert($kernel->handle(new \Athar\Http\Request('GET','/api/v1/lessons/4/watch'))->status===401,'watch route rejects unauthenticated access');
    learning_assert($kernel->handle(new \Athar\Http\Request('GET','/api/v1/admin/comments',[],$headers))->status===403,'moderation route rejects ordinary bearer');
    learning_assert($kernel->handle(new \Athar\Http\Request('GET','/api/v1/admin/comments',[],$adminHeaders))->status===200,'moderation route accepts verified admin');
    $apiBody=['body'=>'API pending fixture','client_id'=>test_uuid()];$postedApi=$kernel->handle(new \Athar\Http\Request('POST','/api/v1/lessons/4/discussion',$apiBody,$headers));
    learning_assert($postedApi->status===201&&$postedApi->body['data']['status']==='pending','discussion route cannot bypass moderation');
    learning_assert($kernel->handle(new \Athar\Http\Request('GET','/api/v1/series/1/lessons',[],$headers,['page'=>2,'per_page'=>50]))->body['data']['pagination']['total']===57,'series route keeps complete pagination');
    $db->exec('UPDATE lessons SET scholar_id=2 WHERE id=2');learning_assert($service->series(1,1)['lesson_count']===56,'hidden direct scholar excluded from series totals');
    $db->exec('UPDATE scholars SET is_published=0 WHERE id=1');learning_denied(fn()=>$service->watch(1,1),404,'watch honors unpublished ancestry');learning_denied(fn()=>$discussion->page(1,1),404,'discussion honors unpublished ancestry');
});echo 'PASS: '.$checks." learning/playlist/thread/idempotency/moderation/privacy assertions.\n";
