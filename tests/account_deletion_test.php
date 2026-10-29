<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Identity\DeletionReceipts;
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;
use Athar\Http\ApiKernel;
use Athar\Http\ApiError;
use Athar\Http\Request;

$checks=0;
function deletion_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
function deletion_denied(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $error){deletion_check($error->status===$status,$name);return;}deletion_check(false,$name);}
isolated_database(static function(PDO $db):void{
    $db->exec('DROP TABLE account_deletion_receipts');
    $db->exec("DELETE FROM schema_migrations WHERE version='029_account_deletion_receipts'");
    $q=$db->prepare('INSERT INTO users(email,username,display_name,password_hash,email_verified_at) VALUES(?,?,?,?,UTC_TIMESTAMP())');
    foreach(['one','two','legacy'] as $name)$q->execute([$name.'@example.invalid','deletion_'.$name,'Delete '.$name,password_hash('DeletionFixture123!',PASSWORD_BCRYPT,['cost'=>4])]);
    $auth=new AuthService($db,new OtpService($db,str_repeat('o',32),static fn()=>false));
    $one=$auth->login('one@example.invalid','DeletionFixture123!');$two=$auth->login('two@example.invalid','DeletionFixture123!');
    $db->exec("INSERT INTO quran_bookmarks(user_id,surah,ayah,label) VALUES(1,114,6,'private one'),(2,114,5,'private two')");
    $beforeUsers=$db->query('SELECT * FROM users ORDER BY id')->fetchAll();$beforeTokens=$db->query('SELECT * FROM api_sessions ORDER BY id')->fetchAll();$beforeMarks=$db->query('SELECT * FROM quran_bookmarks ORDER BY id')->fetchAll();
    deletion_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===['029_account_deletion_receipts'],'only additive receipt migration applies to populated pre29 schema');
    deletion_check($beforeUsers===$db->query('SELECT * FROM users ORDER BY id')->fetchAll()&&$beforeTokens===$db->query('SELECT * FROM api_sessions ORDER BY id')->fetchAll()&&$beforeMarks===$db->query('SELECT * FROM quran_bookmarks ORDER BY id')->fetchAll(),'migration preserves accounts credentials sessions and private rows exactly');
    deletion_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===[],'receipt migration replay is a no-op');
    $actor=$auth->authenticate($one['session']['access_token']);$receipts=new DeletionReceipts($db);$id=test_uuid();$secret=bin2hex(random_bytes(32));
    deletion_denied(fn()=>$auth->delete($actor,'wrong',$id,$secret),422,'wrong password cannot delete or claim receipt');
    deletion_check((int)$db->query('SELECT COUNT(*) FROM account_deletion_receipts')->fetchColumn()===0&&(int)$db->query('SELECT COUNT(*) FROM quran_bookmarks')->fetchColumn()===2,'failed reauthentication preserves records and leaves no proof');
    deletion_denied(fn()=>$auth->delete($actor,'DeletionFixture123!',$id,null),422,'incomplete recovery capability rejected');
    deletion_denied(fn()=>$auth->delete($actor,'DeletionFixture123!','not-a-request',$secret),422,'invalid recovery identity rejected');
    try{$receipts->record(1,$id,$secret);deletion_check(false,'receipt cannot exist before deletion transaction');}catch(LogicException $error){deletion_check(true,'receipt creation requires deletion transaction');}
    // A late SQL failure after receipt insertion must roll the receipt and user deletion back together.
    $db->exec('CREATE TABLE deletion_restrict_fixture(user_id BIGINT UNSIGNED NOT NULL,CONSTRAINT fk_deletion_fixture FOREIGN KEY(user_id) REFERENCES users(id)) ENGINE=InnoDB');
    $db->exec('INSERT INTO deletion_restrict_fixture VALUES(1)');
    try{$auth->delete($actor,'DeletionFixture123!',$id,$secret);deletion_check(false,'restricted delete must fail');}catch(PDOException $error){deletion_check(!$db->inTransaction(),'failed delete transaction rolls back');}
    deletion_check((int)$db->query('SELECT COUNT(*) FROM account_deletion_receipts')->fetchColumn()===0&&$auth->profile(1)['id']===1,'rollback cannot produce false deleted proof');
    $db->exec('DROP TABLE deletion_restrict_fixture');
    $otherFingerprint=$db->query('SELECT * FROM users WHERE id=2')->fetchAll();
    $api=new ApiKernel($db,$auth,new RateLimiter($db,str_repeat('r',32)));
    $response=$api->handle(new Request('DELETE','/api/v1/me',['password'=>'DeletionFixture123!','request_id'=>$id,'recovery_secret'=>$secret],['Authorization'=>'Bearer '.$one['session']['access_token']]));
    deletion_check($response->status===200&&$response->body['data']['status']==='deleted','authenticated deletion commits with recovery proof');
    deletion_check((int)$db->query('SELECT COUNT(*) FROM users WHERE id=1')->fetchColumn()===0&&(int)$db->query('SELECT COUNT(*) FROM quran_bookmarks WHERE user_id=1')->fetchColumn()===0,'deleted owner and cascading private records removed');
    deletion_check((int)$db->query('SELECT COUNT(*) FROM api_sessions WHERE user_id=1')->fetchColumn()===0,'deleted access and refresh rows removed');
    deletion_denied(fn()=>$auth->authenticate($one['session']['access_token']),401,'deleted bearer unusable');
    deletion_denied(fn()=>$auth->refresh($one['session']['refresh_token']),401,'deleted refresh unusable');
    deletion_check($otherFingerprint===$db->query('SELECT * FROM users WHERE id=2')->fetchAll()&&(int)$db->query('SELECT COUNT(*) FROM quran_bookmarks WHERE user_id=2')->fetchColumn()===1,'other account and private bookmark preserved');
    $auth->authenticate($two['session']['access_token']);
    $stored=$db->query('SELECT * FROM account_deletion_receipts')->fetch();
    deletion_check($stored['proof_hash']===hash('sha256',$secret)&&!in_array($secret,$stored,true),'recovery secret stored hashed only');
    deletion_check(array_keys($stored)===['request_id','owner_id','proof_hash','created_at'],'minimal receipt retains no account profile or private activity');
    $reopened=Database::connect($db->query('SELECT DATABASE()')->fetchColumn());
    $newAuth=new AuthService($reopened,new OtpService($reopened,str_repeat('o',32),static fn()=>false));
    $newApi=new ApiKernel($reopened,$newAuth,new RateLimiter($reopened,str_repeat('r',32)));
    $probe=fn(string $requestId,string $proof,string $method='POST')=>$newApi->handle(new Request($method,'/api/v1/auth/deletion-receipt',['request_id'=>$requestId,'recovery_secret'=>$proof]));
    $proof=$probe($id,$secret);
    deletion_check($proof->status===200&&$proof->body['data']===['status'=>'deleted','request_id'=>$id,'owner_id'=>1],'fresh process recovers lost deletion acknowledgement without credentials');
    deletion_check($probe($id,str_repeat('0',64))->status===404&&$probe(test_uuid(),$secret)->status===404,'wrong and unknown recovery capabilities reveal no owner');
    deletion_check($probe($id,$secret,'GET')->status===405,'recovery secret never accepted through a GET query');
    deletion_check($probe($id,str_repeat('0',64),'DELETE')->status===200&&$probe($id,$secret)->status===200,'wrong acknowledgement cannot erase proof');
    deletion_denied(fn()=>$auth->delete($actor,'DeletionFixture123!',test_uuid(),bin2hex(random_bytes(32))),401,'retained deleted actor cannot create a new proof');
    deletion_check($probe($id,$secret,'DELETE')->status===200&&$probe($id,$secret)->status===404,'confirmed client cleanup can remove minimal server proof');
    deletion_check($probe($id,$secret,'DELETE')->status===200,'proof acknowledgement remains idempotent');
    $legacy=$auth->login('legacy@example.invalid','DeletionFixture123!');$auth->delete($auth->authenticate($legacy['session']['access_token']),'DeletionFixture123!');
    deletion_check((int)$db->query('SELECT COUNT(*) FROM users WHERE id=3')->fetchColumn()===0&&(int)$db->query('SELECT COUNT(*) FROM account_deletion_receipts')->fetchColumn()===0,'legacy clients retain password-authenticated delete compatibility');
    for($attempt=0;$attempt<6;$attempt++){
        $failed=$api->handle(new Request('DELETE','/api/v1/me',['password'=>'wrong'],['Authorization'=>'Bearer '.$two['session']['access_token']]));
        deletion_check($failed->status===($attempt<5?422:429),'deletion password attempts bounded per owner across sessions');
    }
    deletion_check($otherFingerprint===$db->query('SELECT * FROM users WHERE id=2')->fetchAll(),'rate limited attempts preserve the account');
});
echo 'PASS: '.$checks." deletion/hashed-proof/rollback/ownership/populated-migration/reopen assertions; no live delivery.\n";
