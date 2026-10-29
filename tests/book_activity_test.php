<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Infrastructure\MigrationRunner;
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use Athar\Progress\AccountExportService;
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Http\ApiKernel;
use Athar\Http\Request;
$checks=0;
function book_activity_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
isolated_database(static function(PDO $db):void{
    $auth=new AuthService($db,new OtpService($db,str_repeat('b',32),static fn()=>false));
    $q=$db->prepare('INSERT INTO users(email,username,display_name,password_hash,email_verified_at) VALUES(?,?,?,?,UTC_TIMESTAMP())');
    foreach(['one','two'] as $name)$q->execute([$name.'@example.invalid','book_'.$name,$name,password_hash('BookFixture123!',PASSWORD_BCRYPT,['cost'=>4])]);
    $one=$auth->login('one@example.invalid','BookFixture123!');$two=$auth->login('two@example.invalid','BookFixture123!');
    $progress=new ProgressService($db,new CatalogService($db));$today=gmdate('Y-m-d');$yesterday=gmdate('Y-m-d',time()-86400);
    $progress->addBookmark(1,['surah'=>114,'ayah'=>6,'label'=>'private 😀']);
    $progress->activity(1,test_uuid(),'book_download',$yesterday);$progress->activity(2,test_uuid(),'quran_session',$today);
    book_activity_check((int)$db->query('SELECT SUM(book_completions) FROM account_daily_stats')->fetchColumn()===0,'fresh schema does not backfill legacy completion counts');
    $before=[];
    foreach(['users','api_sessions','quran_bookmarks','activity_events'] as $table)$before[$table]=$db->query('SELECT * FROM '.$table.' ORDER BY 1,2')->fetchAll();
    $oldColumns='user_id,activity_date,quran_sessions,lesson_completions,adhkar_completions,book_downloads';
    $oldStats=$db->query('SELECT '.$oldColumns.' FROM account_daily_stats ORDER BY user_id,activity_date')->fetchAll();
    $db->exec('ALTER TABLE account_daily_stats DROP COLUMN book_completions');
    $db->exec("ALTER TABLE activity_events MODIFY activity_type ENUM('quran_session','lesson_completion','adhkar_completion','book_download') NOT NULL");
    $db->exec("DELETE FROM schema_migrations WHERE version='030_book_download_completions'");
    book_activity_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===['030_book_download_completions'],'only030 applies to populated pre-completion schema');
    foreach($before as $table=>$rows)book_activity_check($rows===$db->query('SELECT * FROM '.$table.' ORDER BY 1,2')->fetchAll(),'migration preserves exact existing '.$table.' data');
    book_activity_check($oldStats===$db->query('SELECT '.$oldColumns.' FROM account_daily_stats ORDER BY user_id,activity_date')->fetchAll(),'migration preserves all mixed historical daily counters');
    book_activity_check((int)$db->query('SELECT SUM(book_completions) FROM account_daily_stats')->fetchColumn()===0,'new owned counters start at zero without fabricated history');
    book_activity_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===[],'recorded migration replay is a no-op');
    // Both DDL statements committed but a process stopped before the ledger insert.
    $db->exec("DELETE FROM schema_migrations WHERE version='030_book_download_completions'");
    book_activity_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===['030_book_download_completions'],'interrupted DDL can resume with the column already present');
    book_activity_check($oldStats===$db->query('SELECT '.$oldColumns.' FROM account_daily_stats ORDER BY user_id,activity_date')->fetchAll(),'interrupted migration replay cannot rewrite original counters');
    $db->exec('ALTER TABLE account_daily_stats MODIFY book_completions INT UNSIGNED NOT NULL DEFAULT 0');
    $db->exec("DELETE FROM schema_migrations WHERE version='030_book_download_completions'");
    $failed=false;try{MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations');}catch(PDOException $error){$failed=true;}
    book_activity_check($failed&&(int)$db->query("SELECT COUNT(*) FROM schema_migrations WHERE version='030_book_download_completions'")->fetchColumn()===0,'incompatible existing column blocks readiness without silently declaring migration applied');
    book_activity_check($oldStats===$db->query('SELECT '.$oldColumns.' FROM account_daily_stats ORDER BY user_id,activity_date')->fetchAll(),'incompatible schema preserves private historical rows for repair');
    $db->exec('ALTER TABLE account_daily_stats MODIFY book_completions SMALLINT UNSIGNED NOT NULL DEFAULT 0');
    book_activity_check(MigrationRunner::apply($db,dirname(__DIR__).'/database/migrations')===['030_book_download_completions'],'explicit compatible schema repair allows a safe retry');
    $api=new ApiKernel($db,$auth,new RateLimiter($db,str_repeat('r',32)));$id=test_uuid();
    $headers=['Authorization'=>'Bearer '.$one['session']['access_token']];
    $body=['event_id'=>$id,'activity_type'=>'book_download_completed','activity_date'=>$yesterday];
    for($attempt=0;$attempt<2;$attempt++){
        $response=$api->handle(new Request('POST','/api/v1/me/activity',$body,$headers));
        book_activity_check($response->status===200&&$response->body['data']['recorded']===true,'lost reply retry returns a strict idempotent acknowledgement');
    }
    $summary=$progress->activitySummary(1);
    book_activity_check($summary['recorded']['completed_book_downloads']===1&&$summary['recorded']['legacy_book_download_events']===1,'only the explicit new event changes the completion metric');
    book_activity_check($summary['recent'][0]['date']===$yesterday&&$summary['recent'][0]['completed_book_downloads']===1,'original UTC date survives delayed delivery and retry');
    $changed=$body;$changed['activity_date']=$today;
    book_activity_check($api->handle(new Request('POST','/api/v1/me/activity',$changed,$headers))->status===409,'retry cannot relabel an original completion as today');
    $changed=$body;$changed['activity_type']='book_download';
    book_activity_check($api->handle(new Request('POST','/api/v1/me/activity',$changed,$headers))->status===409,'legacy request kind cannot overwrite new completion');
    $otherHeaders=['Authorization'=>'Bearer '.$two['session']['access_token']];
    book_activity_check($api->handle(new Request('POST','/api/v1/me/activity',$body,$otherHeaders))->status===200,'same UUID is independently owned by the other account');
    book_activity_check($progress->activitySummary(1)['recorded']['completed_book_downloads']===1&&$progress->activitySummary(2)['recorded']['completed_book_downloads']===1,'another owner cannot inflate or acknowledge the first owner counter');
    $home=(new CatalogService($db))->home(1)['activity'];
    book_activity_check($home['quran_sessions']===null&&$home['availability']['reading_sessions']===false,'home cannot present legacy reading counters as tracked sessions');
    book_activity_check($home['book_downloads']===1&&$home['active_days']===1,'home uses genuine completed downloads and completion days without mixed legacy counters');
    $copy=(new AccountExportService($db,$auth))->read(1);
    book_activity_check((int)$copy['account_daily_stats'][0]['book_completions']===1&&count(array_filter($copy['activity_events'],static fn(array $row):bool=>$row['activity_type']==='book_download_completed'))===1,'fixed export projections include new count and original completion event');
    $auth->delete($auth->authenticate($one['session']['access_token']),'BookFixture123!');
    book_activity_check((int)$db->query('SELECT COUNT(*) FROM activity_events WHERE user_id=1')->fetchColumn()===0&&(int)$db->query('SELECT COUNT(*) FROM account_daily_stats WHERE user_id=1')->fetchColumn()===0,'account deletion cascades all owned new completion rows');
    book_activity_check($progress->activitySummary(2)['recorded']['completed_book_downloads']===1,'deleting one account preserves the other account completion history');
});
echo 'PASS: '.$checks." completed-download/migration/retry/date/owner/export/deletion assertions; no live provider.\n";
