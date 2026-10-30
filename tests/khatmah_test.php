<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Progress\Khatmah;
use Athar\Http\ApiError;
use Athar\Infrastructure\Database;
use Athar\Infrastructure\MigrationRunner;

if(($argv[1]??'')==='--worker'){
    $db=Database::connect($argv[2]);$db->exec('SET innodb_lock_wait_timeout=3');
    echo "READY\n";flush();
    try{(new Khatmah($db))->save(1,json_decode($argv[3],true,32,JSON_THROW_ON_ERROR));echo '200';}
    catch(ApiError $e){echo $e->status;}
    exit;
}
$checks=0;
function kh_check(bool $value,string $label):void {global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$label);}
function kh_reject(callable $call,int $status,string $label):void {
    try{$call();}catch(ApiError $e){kh_check($e->status===$status,$label);return;}
    throw new RuntimeException('FAIL: '.$label.' was accepted');
}
foreach(json_decode(file_get_contents(dirname(__DIR__).'/shared/khatmah-vectors.json'),true,32,JSON_THROW_ON_ERROR) as $vector){
    $actual=Khatmah::decorate(['start_date'=>$vector['start'],'target_date'=>$vector['target'],'completed_pages'=>$vector['completed'],'total_pages'=>604,'status'=>$vector['status'],'revision'=>1],$vector['today']);
    foreach($vector['expected'] as $key=>$value)kh_check($actual[$key]===$value,'shared workload vector '.$key);
    kh_check(Khatmah::fingerprint(1,$actual)===$vector['hash'],'shared baseline fingerprint vector');
}
isolated_database(static function(PDO $db):void {
    $db->exec("INSERT INTO users(email,username,display_name,password_hash,email_verified_at) VALUES('kh1@example.invalid','kh1','Fixture','unused',UTC_TIMESTAMP()),('kh2@example.invalid','kh2','Other','unused',UTC_TIMESTAMP())");
    $service=new Khatmah($db);$today=gmdate('Y-m-d');$tomorrow=gmdate('Y-m-d',time()+86400);
    kh_check($service->get(1)===null,'absent plan is explicit');
    $body=['start_date'=>$today,'target_date'=>$tomorrow,'completed_pages'=>600,'expected_revision'=>0];
    $first=$service->save(1,$body);
    kh_check($first['revision']===1&&$first['daily_pages']===2&&$first['remaining_pages']===4,'inclusive two-day creation');
    kh_reject(fn()=>$service->save(1,array_merge($body,['completed_pages'=>601,'expected_revision'=>1,'expected_plan_hash'=>str_repeat('0',64)])),409,'baseline fingerprint prevents unseen overwrite');
    kh_reject(fn()=>$service->save(1,array_merge($body,['expected_revision'=>1,'expected_plan_hash'=>str_repeat('0',63)])),422,'malformed fingerprint rejected');
    $db->exec('UPDATE khatmah_plans SET completed_pages=599 WHERE user_id=1');
    kh_reject(fn()=>$service->save(1,array_merge($body,['completed_pages'=>601,'expected_revision'=>1,'expected_plan_hash'=>$first['plan_hash']])),409,'same revision with restored different values conflicts');
    kh_check($service->get(1)['completed_pages']===599,'ABA rejection retains restored plan');
    $db->exec('UPDATE khatmah_plans SET completed_pages=600 WHERE user_id=1');
    kh_check($service->get(2)===null,'owner isolation');
    kh_check($service->save(1,$body)['revision']===1,'lost committed response exact replay does not increment');
    kh_reject(fn()=>$service->save(1,array_merge($body,['start_date'=>null])),409,'malformed start cannot be acknowledged as a committed replay');
    kh_reject(fn()=>$service->save(1,array_merge($body,['status'=>null])),409,'malformed status cannot be acknowledged as a committed replay');
    kh_check($service->save(1,array_merge($body,['expected_revision'=>1]))['revision']===1,'same-value current save is a no-op');
    kh_reject(fn()=>$service->save(1,array_merge($body,['completed_pages'=>599])),409,'stale divergent editor conflicts');
    foreach([true,'3',3.5,[],null] as $bad)kh_reject(fn()=>$service->save(1,array_merge($body,['expected_revision'=>$bad])),422,'strict revision scalar');
    foreach([-1,605,'603',603.5,true] as $bad)kh_reject(fn()=>$service->save(1,array_merge($body,['completed_pages'=>$bad,'expected_revision'=>1])),422,'strict bounded pages');
    foreach(['2026-02-30','2026-2-03','2026-10-29T00:00Z','',[],null] as $bad)kh_reject(fn()=>$service->save(1,array_merge($body,['start_date'=>$bad,'expected_revision'=>1])),422,'strict canonical date');
    $plan=$service->save(1,array_merge($body,['completed_pages'=>603,'expected_revision'=>1]));
    kh_check($plan['progress_percent']===99,'603 never displays100 percent');
    kh_reject(fn()=>$service->save(1,array_merge($body,['status'=>'finished','expected_revision'=>2])),422,'generic update cannot bypass explicit lifecycle');
    kh_reject(fn()=>$service->action(1,['action'=>'finish','expected_revision'=>2]),422,'cannot finish before604');
    $plan=$service->save(1,array_merge($body,['completed_pages'=>604,'expected_revision'=>2]));
    kh_check($plan['status']==='active'&&$plan['progress_percent']===100,'604 pages does not silently finish');
    $plan=$service->action(1,['action'=>'finish','expected_revision'=>3]);
    kh_check($plan['status']==='finished'&&$plan['revision']===4,'explicit completed finish');
    kh_check($service->action(1,['action'=>'finish','expected_revision'=>3])['revision']===4,'lost finish acknowledgement does not repeat');
    kh_reject(fn()=>$service->save(1,array_merge($body,['expected_revision'=>4])),422,'finished editor requires explicit restart');
    kh_reject(fn()=>$service->action(1,['action'=>'resume','expected_revision'=>4]),422,'finished resume forbidden');
    $plan=$service->action(1,['action'=>'restart','start_date'=>$today,'target_date'=>$tomorrow,'expected_revision'=>4]);
    kh_check($plan['revision']===5&&$plan['completed_pages']===0,'restart deliberate reset with new revision');
    kh_check($service->action(1,['action'=>'restart','start_date'=>$today,'target_date'=>$tomorrow,'expected_revision'=>4])['revision']===5,'lost restart acknowledgement does not reset again');
    kh_reject(fn()=>$service->action(1,['action'=>'restart','start_date'=>null,'target_date'=>$tomorrow,'expected_revision'=>4]),409,'malformed restart cannot masquerade as a lost successful reply');
    $plan=$service->action(1,['action'=>'pause','expected_revision'=>5]);
    kh_check($plan['daily_pages']===0&&$plan['status']==='paused','paused workload zero');
    $plan=$service->save(1,array_merge($body,['completed_pages'=>1,'expected_revision'=>6]));
    kh_check($plan['status']==='paused','editor preserves paused lifecycle');
    $db->exec("UPDATE khatmah_plans SET start_date=DATE_SUB(UTC_DATE(),INTERVAL 3 DAY),target_date=DATE_SUB(UTC_DATE(),INTERVAL 1 DAY) WHERE user_id=1");
    $old=$service->get(1);$edit=['start_date'=>$old['start_date'],'target_date'=>$old['target_date'],'completed_pages'=>2,'expected_revision'=>7];
    kh_check($service->save(1,$edit)['overdue'],'unchanged overdue dates allow truthful progress correction');
    kh_reject(fn()=>$service->save(1,array_merge($edit,['start_date'=>gmdate('Y-m-d',time()-4*86400),'expected_revision'=>8])),422,'past target exception requires BOTH dates unchanged');
    kh_reject(fn()=>$service->save(2,['start_date'=>gmdate('Y-m-d',time()+86400),'target_date'=>$today,'completed_pages'=>0,'expected_revision'=>0]),422,'end before start rejected');
    $future=$service->save(2,['start_date'=>$tomorrow,'target_date'=>$tomorrow,'completed_pages'=>0,'expected_revision'=>0]);
    kh_check($future['daily_pages']===0&&$future['days_remaining']===1&&$future['starts_in_days']===1,'future plan has no invented today workload');
    kh_check((int)$db->query('SELECT COUNT(*) FROM activity_events')->fetchColumn()===0,'plan edits never count reading activity');
    // Parent holds the same owner fence as save; a competing stale request must not overwrite it.
    $db->beginTransaction();$db->query('SELECT id FROM users WHERE id=1 FOR UPDATE')->fetch();
    $pipes=[];$process=proc_open([PHP_BINARY,__FILE__,'--worker',(string)$db->query('SELECT DATABASE()')->fetchColumn(),json_encode(array_merge($edit,['completed_pages'=>9,'expected_revision'=>8]))],[0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);
    kh_check(is_resource($process),'concurrent native-style writer starts');fclose($pipes[0]);
    kh_check(trim((string)fgets($pipes[1]))==='READY','competing request prepared');
    $db->exec('UPDATE khatmah_plans SET completed_pages=3,revision=revision+1 WHERE user_id=1');$db->commit();
    $result=stream_get_contents($pipes[1]);$errors=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);$exit=proc_close($process);
    kh_check($exit===0&&trim($result)==='409'&&$errors==='','competing stale writer receives409');
    kh_check($service->get(1)['completed_pages']===3,'concurrent committed progress retained');
    $before=$db->query('SELECT id,user_id,start_date,target_date,total_pages,completed_pages,status,created_at,updated_at FROM khatmah_plans ORDER BY id')->fetchAll();
    $db->exec("DELETE FROM schema_migrations WHERE version='031_khatmah_revision'");$db->exec('ALTER TABLE khatmah_plans DROP COLUMN revision');
    MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');
    kh_check($db->query('SELECT id,user_id,start_date,target_date,total_pages,completed_pages,status,created_at,updated_at FROM khatmah_plans ORDER BY id')->fetchAll()===$before,'populated migration preserves every old plan field and timestamp');
    kh_check($service->get(1)['revision']===1,'legacy rows receive revision1');
    $db->exec("DELETE FROM schema_migrations WHERE version='031_khatmah_revision'");
    MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');
    kh_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===[],'interrupted DDL replay and second apply are safe');
    $db->exec("DELETE FROM schema_migrations WHERE version='031_khatmah_revision'");$db->exec('ALTER TABLE khatmah_plans MODIFY revision INT NOT NULL DEFAULT 1');
    try{MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');throw new RuntimeException('wrong type accepted');}catch(PDOException $expected){kh_check((int)$db->query("SELECT COUNT(*) FROM schema_migrations WHERE version='031_khatmah_revision'")->fetchColumn()===0,'wrong schema remains unrecorded');}
    kh_check($db->query('SELECT id,user_id,start_date,target_date,total_pages,completed_pages,status,created_at,updated_at FROM khatmah_plans ORDER BY id')->fetchAll()===$before,'unsupported schema cannot overwrite old private plans');
});
echo "PASS: $checks Khatmah CAS/workload/owner/strict-date/lifecycle/replay/concurrent-writer/populated-migration assertions; no real account or reading activity.\n";
