<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use Athar\Http\ApiError;
$checks=0;
function daily_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
function daily_reject(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){daily_check($e->status===$status,$name);return;}daily_check(false,$name);}
isolated_database(static function(PDO $db):void{
    $db->exec("INSERT INTO users(email,username,display_name,password_hash,email_verified_at) VALUES('daily@example.invalid','daily','Daily','unused',UTC_TIMESTAMP()),('other@example.invalid','other','Other','unused',UTC_TIMESTAMP())");
    $db->exec("INSERT INTO adhkar_items(title,formula,source_reference,source_url,repeat_count,is_reviewed,is_published) VALUES('Reviewed','Formula','Ref','https://example.invalid',3,1,1)");
    $service=new ProgressService($db,new CatalogService($db));
    $today=gmdate('Y-m-d');$yesterday=gmdate('Y-m-d',time()-86400);
    $first=$service->saveAdhkar(1,['item_id'=>1,'current_count'=>2,'expected_revision'=>0]);
    daily_check($first['progress_date']===$today&&$first['revision']===1,'legacy undated write uses current UTC day');
    $db->exec("UPDATE adhkar_progress SET updated_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)");
    $view=$service->adhkar(1,['item_id'=>'1'])[0];
    daily_check((int)$view['current_count']===0&&(int)$view['revision']===1&&$view['progress_date']===$today,'previous day projects zero with same CAS revision');
    daily_check((int)$db->query('SELECT current_count FROM adhkar_progress')->fetchColumn()===2,'daily read preserves stored legacy count');
    daily_check($service->adhkar(2,['item_id'=>'1'])===[],'day-aware read remains account scoped');
    $event=test_uuid();$service->activity(1,$event,'adhkar_completion',$yesterday);
    $service->activity(1,$event,'adhkar_completion'); // A legacy lost-response retry after midnight.
    daily_check((int)$db->query('SELECT SUM(adhkar_completions) FROM account_daily_stats')->fetchColumn()===1,'undated retry preserves old event identity across midnight');
    daily_reject(fn()=>$service->activity(1,$event,'adhkar_completion',$today),409,'explicit date cannot rebind an acknowledged event');
    $late=$service->saveAdhkar(1,['item_id'=>1,'current_count'=>3,'expected_revision'=>1,'progress_date'=>$yesterday,'event_id'=>$event]);
    daily_check($late['current_count']===0&&$late['progress_date']===$today&&$late['revision']===2,'offline old-day completion does not become today count');
    daily_check((int)$db->query('SELECT SUM(adhkar_completions) FROM account_daily_stats')->fetchColumn()===1,'activity and counter completion deduplicate together');
    daily_check($db->query('SELECT activity_date FROM activity_events')->fetchColumn()===$yesterday,'late completion retains real activity day');
    daily_reject(fn()=>$service->saveAdhkar(1,['item_id'=>1,'current_count'=>2,'expected_revision'=>1,'progress_date'=>$today]),409,'stale previous-day revision cannot overwrite new revision');
    $service->saveAdhkar(1,['item_id'=>1,'current_count'=>1,'expected_revision'=>2,'progress_date'=>$today]);
    foreach([true,[],null,'',gmdate('Y-m-d',time()+86400),'2026-02-30'] as $bad){
        daily_reject(fn()=>$service->saveAdhkar(1,['item_id'=>1,'current_count'=>2,'expected_revision'=>3,'progress_date'=>$bad]),422,'malformed or future progress day rejected');
    }
    daily_check((int)$service->adhkar(1,['item_id'=>1])[0]['current_count']===1,'rejected dates preserve current count');
    daily_reject(fn()=>$service->adhkar(1,['item_id'=>true]),422,'boolean identity rejected');
    $summary=$service->adhkarStats(1);
    daily_check($summary['total_completions']===1&&$summary['today_completions']===0&&$summary['active_days']===1,'statistics separate genuine prior-day history from today');
    daily_check($summary['categories'][0]['current_count']===1&&$summary['categories'][0]['target_count']===3,'statistics project current owned category progress');
    daily_check($service->adhkarStats(2)['total_completions']===0&&$service->adhkarStats(2)['categories'][0]['current_count']===0,'statistics cannot expose another account progress');
    $db->exec('UPDATE adhkar_items SET repeat_count=2 WHERE id=1');
    daily_check($service->adhkarStats(1)['categories'][0]['current_count']===0,'changed source target never attributes an old counter to new target');
    $db->exec('UPDATE adhkar_items SET repeat_count=3 WHERE id=1');

    daily_reject(fn()=>$service->saveAdhkar(1,['item_id'=>1,'current_count'=>3,'expected_revision'=>3,'event_id'=>'invalid']),422,'invalid completion rolls transaction back');
    daily_check((int)$service->adhkar(1,['item_id'=>1])[0]['revision']===3,'completion rollback retains prior revision');
});
echo 'PASS: '.$checks." daily counter/date/idempotency/ownership assertions.\n";
