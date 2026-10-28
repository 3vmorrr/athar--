<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Infrastructure\Database;
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Http\ApiKernel;
use Athar\Http\ApiError;
use Athar\Http\Request;

function security_auth(PDO $db):AuthService{return new AuthService($db,new OtpService($db,str_repeat('k',32),static fn()=>false));}
if(($argv[1]??'')==='child'){
    $input=json_decode(stream_get_contents(STDIN),true,512,JSON_THROW_ON_ERROR);
    if(!preg_match('/^athar_test_[a-f0-9]{12}$/',$input['schema']??''))throw new RuntimeException('Unsafe child schema.');
    $db=Database::connect($input['schema']);$db->exec('SET innodb_lock_wait_timeout=15');$auth=security_auth($db);
    echo json_encode(['connection'=>(int)$db->query('SELECT CONNECTION_ID()')->fetchColumn()])."\n";flush();
    try{
        switch($input['operation']){
            case 'login':$result=$auth->login($input['email'],$input['password']);break;
            case 'refresh':$result=$auth->refresh($input['refresh']);break;
            case 'revoke':$result=$auth->revokeOtherSessions($input['actor']);break;
            case 'password':$result=$auth->changePassword($input['actor'],$input['password'],'Replacement123!','Replacement123!');break;
            case 'delete':$auth->delete($input['actor'],$input['password']);$result=null;break;
            default:throw new RuntimeException('Unknown child operation.');
        }
        echo json_encode(['status'=>200,'result'=>$result],JSON_THROW_ON_ERROR)."\n";
    }catch(ApiError $error){echo json_encode(['status'=>$error->status,'code'=>$error->getMessage()])."\n";}
    exit;
}
$checks=0;
function security_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
function security_denied(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){security_check($e->status===$status,$name);return;}security_check(false,$name);}
function start_child(PDO $db,array $input):array{
    $input['schema']=$db->query('SELECT DATABASE()')->fetchColumn();
    $process=proc_open([PHP_BINARY,__FILE__,'child'],[['pipe','r'],['pipe','w'],['pipe','w']],$pipes);
    if(!is_resource($process))throw new RuntimeException('Child did not start.');
    fwrite($pipes[0],json_encode($input,JSON_THROW_ON_ERROR));fclose($pipes[0]);
    $ready=json_decode((string)fgets($pipes[1]),true,512,JSON_THROW_ON_ERROR);
    return ['process'=>$process,'pipes'=>$pipes,'connection'=>$ready['connection']];
}
function wait_locked(PDO $db,array $child):void{
    $q=$db->prepare('SELECT INFO FROM INFORMATION_SCHEMA.PROCESSLIST WHERE ID=?');$deadline=microtime(true)+10;
    do{$q->execute([$child['connection']]);$info=$q->fetchColumn();if(is_string($info)&&stripos($info,'FOR UPDATE')!==false){security_check(true,'independent child reached guarded row lock');return;}usleep(20000);}while(microtime(true)<$deadline);
    throw new RuntimeException('Child did not reach guarded row lock.');
}
function finish_child(array $child):array{
    $deadline=microtime(true)+20;
    do{$status=proc_get_status($child['process']);if(!$status['running'])break;usleep(20000);}while(microtime(true)<$deadline);
    if($status['running']){proc_terminate($child['process']);throw new RuntimeException('Child timed out.');}
    $text=stream_get_contents($child['pipes'][1]);$errors=stream_get_contents($child['pipes'][2]);fclose($child['pipes'][1]);fclose($child['pipes'][2]);proc_close($child['process']);
    if($status['exitcode']!==0||$errors!=='')throw new RuntimeException('Security child failed (no credential output logged).');
    return json_decode(trim($text),true,512,JSON_THROW_ON_ERROR);
}
isolated_database(static function(PDO $db):void{
    $auth=security_auth($db);$password='OriginalPassword123!';
    $q=$db->prepare('INSERT INTO users(email,username,display_name,password_hash,email_verified_at) VALUES(?,?,?,?,UTC_TIMESTAMP())');
    foreach(['one','two'] as $name)$q->execute([$name.'@example.invalid','security_'.$name,'Security '.$name,password_hash($password,PASSWORD_BCRYPT,['cost'=>4])]);
    $a=$auth->login('one@example.invalid',$password);$b=$auth->login('one@example.invalid',$password);$c=$auth->login('two@example.invalid',$password);
    $actor=$auth->authenticate($a['session']['access_token']);
    $profile=$auth->updateProfile((int)$actor['id'],'😀😀','security_edited');
    security_check($profile['display_name']==='😀😀'&&$profile['username']==='security_edited','Unicode profile edits round trip without lossy conversion');
    security_denied(fn()=>$auth->updateProfile((int)$actor['id'],'Valid Name','security_two'),409,'duplicate username leaves owner unchanged');
    foreach(['admin','ab','اسم','mixed_١٢','a'.str_repeat('b',24)] as $invalid)security_denied(fn()=>$auth->updateProfile((int)$actor['id'],'Valid Name',$invalid),422,'reserved or malformed username rejected');
    security_denied(fn()=>$auth->updateProfile((int)$actor['id'],str_repeat('😀',81),'security_edited'),422,'display name limit counts Unicode characters');
    security_check($auth->profile((int)$actor['id'])['display_name']==='😀😀'&&$auth->profile(2)['username']==='security_two','rejected profile changes preserve both owners');
    security_check($auth->revokeOtherSessions($actor)===1,'only other owner family revoked');
    security_check($auth->authenticate($a['session']['access_token'])['id']===$actor['id'],'current family retained');
    security_denied(fn()=>$auth->authenticate($b['session']['access_token']),401,'other family access revoked');
    security_denied(fn()=>$auth->refresh($b['session']['refresh_token']),401,'other family refresh revoked');
    security_check($auth->authenticate($c['session']['access_token'])['id']!==$actor['id'],'other account unaffected');
    security_check($auth->revokeOtherSessions($actor)===0,'repeat revocation idempotent');
    $next=$auth->refresh($a['session']['refresh_token']);
    $newOther=$auth->login('one@example.invalid',$password);
    security_denied(fn()=>$auth->revokeOtherSessions($actor),401,'rotated caller snapshot cannot revoke new family');
    $auth->authenticate($newOther['session']['access_token']);
    $current=$auth->authenticate($next['session']['access_token']);
    $api=new ApiKernel($db,$auth,new RateLimiter($db,str_repeat('r',32)));
    $response=$api->handle(new Request('POST','/api/v1/me/sessions/revoke-others',[],['Authorization'=>'Bearer '.$next['session']['access_token']]));
    security_check($response->status===200&&$response->body['data']['revoked_count']===1,'owned API revocation contract');
    security_check($api->handle(new Request('GET','/api/v1/me/sessions/revoke-others',[],['Authorization'=>'Bearer '.$next['session']['access_token']]))->status===405,'revocation requires POST');
    $db->exec("INSERT INTO users(email,username,display_name,password_hash) VALUES('pending@example.invalid','security_pending','Pending','unused')");
    // Registration produces only a verification-scoped credential, without sending any mail.
    $pending=$auth->register(['email'=>'pending2@example.invalid','username'=>'security_pending2','display_name'=>'Pending Two','password'=>$password,'password_confirmation'=>$password]);
    security_check($api->handle(new Request('POST','/api/v1/me/sessions/revoke-others',[],['Authorization'=>'Bearer '.$pending['session']['access_token']]))->status===403,'pending account cannot revoke sessions');

    $schema=$db->query('SELECT DATABASE()')->fetchColumn();$guard=Database::connect($schema);
    // Bcrypt at low cost forces the old rehash path; an uncommitted replacement remains
    // invisible to the child's first consistent read, but visible under its row lock.
    $db->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([password_hash($password,PASSWORD_BCRYPT,['cost'=>4]),$actor['id']]);
    $guard->beginTransaction();$guard->prepare('UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?')->execute([password_hash('NewPassword123!',PASSWORD_BCRYPT),$actor['id']]);
    $child=null;
    try{
        $child=start_child($db,['operation'=>'login','email'=>'one@example.invalid','password'=>$password]);wait_locked($db,$child);$guard->commit();$reply=finish_child($child);$child=null;
        security_check($reply['status']===401,'old password cannot acquire new revocation version after concurrent reset');
        $hash=$db->query('SELECT password_hash FROM users WHERE id='.(int)$actor['id'])->fetchColumn();
        security_check(password_verify('NewPassword123!',$hash)&&!password_verify($password,$hash),'stale rehash cannot overwrite concurrent password replacement');
    }finally{if($guard->inTransaction())$guard->rollBack();if($child)proc_terminate($child['process']);}
    $a=$auth->login('one@example.invalid','NewPassword123!');$b=$auth->login('one@example.invalid','NewPassword123!');$actor=$auth->authenticate($a['session']['access_token']);
    $guard->beginTransaction();$guard->query('SELECT id FROM users WHERE id='.(int)$actor['id'].' FOR UPDATE');$children=[];
    try{
        $children[]=start_child($db,['operation'=>'refresh','refresh'=>$b['session']['refresh_token']]);wait_locked($db,$children[0]);
        $children[]=start_child($db,['operation'=>'revoke','actor'=>$actor]);wait_locked($db,$children[1]);$guard->commit();
        $refresh=finish_child($children[0]);$revoke=finish_child($children[1]);$children=[];
        security_check($revoke['status']===200&&in_array($refresh['status'],[200,401],true),'concurrent refresh and revocation finish without deadlock');
        if($refresh['status']===200)security_denied(fn()=>$auth->authenticate($refresh['result']['session']['access_token']),401,'racing refreshed credential is revoked');
        security_denied(fn()=>$auth->refresh($b['session']['refresh_token']),401,'revoked family cannot refresh after race');
        security_check($auth->authenticate($a['session']['access_token'])['id']===$actor['id'],'racing revocation retains caller family');
    }finally{if($guard->inTransaction())$guard->rollBack();foreach($children as $child)proc_terminate($child['process']);}
    // A retained actor must never authorize sensitive operations after rotation/revocation.
    $auth->changePassword($actor,'NewPassword123!','FinalPassword123!','FinalPassword123!');
    security_denied(fn()=>$auth->changePassword($actor,'NewPassword123!','StalePassword123!','StalePassword123!'),401,'stale actor cannot replace newer password');
    security_denied(fn()=>$auth->delete($actor,'NewPassword123!'),401,'stale actor cannot delete account after password change');
    $final=$auth->login('one@example.invalid','FinalPassword123!');$finalActor=$auth->authenticate($final['session']['access_token']);
    security_denied(fn()=>$auth->delete($finalActor,'wrong'),422,'fresh actor still requires current password');
    security_check($auth->profile((int)$actor['id'])['email']==='one@example.invalid','rejected deletion preserves account');
    $cookie=$auth->login('one@example.invalid','FinalPassword123!',false);
    security_check($cookie['session']===null,'cookie login preserves no-bearer contract');
    security_check((int)$db->query("SELECT COUNT(*) FROM audit_logs WHERE action='api-other-sessions-revoked'")->fetchColumn()>=3,'successful revocations audited');
});
echo 'PASS: '.$checks." isolated session/credential/concurrency assertions; no live mail.\n";
