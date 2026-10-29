<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Identity\AuthService;
use Athar\Identity\OtpService;
use Athar\Identity\RateLimiter;
use Athar\Http\ApiKernel;
use Athar\Http\Request;
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use Athar\Progress\AccountExportService;
use Athar\NightPrayer\NightPrayerService;

$checks=0;
function export_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
isolated_database(static function(PDO $db):void{
    $auth=new AuthService($db,new OtpService($db,str_repeat('a',32),static fn()=>false));
    $night=new NightPrayerService($db,str_repeat('n',32));
    $progress=new ProgressService($db,new CatalogService($db));
    $service=new AccountExportService($db,$auth,$night);
    $q=$db->prepare('INSERT INTO users(email,username,display_name,password_hash,email_verified_at,preferences_json) VALUES(?,?,?,?,UTC_TIMESTAMP(),?)');
    foreach(['one','two'] as $name)$q->execute([$name.'@example.invalid','export_'.$name,'خاص "'.$name.'" 😀',password_hash('ExportFixture123!',PASSWORD_BCRYPT,['cost'=>4]),json_encode(['theme'=>'dark','language'=>'ar','quran_font_size'=>32,'private_exemptions'=>['private exemption canary'],'api_key'=>'credential canary'])]);
    $db->exec("INSERT INTO scholars(name,is_published) VALUES('Scholar',1)");
    $db->exec("INSERT INTO lessons(scholar_id,title,youtube_id,source_url,is_published) VALUES(1,'Lesson','abcdefghijk','https://youtu.be/abcdefghijk',1)");
    foreach([1,2] as $owner){
        $progress->addBookmark($owner,['surah'=>114,'ayah'=>6,'label'=>'خاص "'.$owner.'" 😀']);
        $progress->savePosition($owner,['surah'=>114,'ayah'=>6,'expected_revision'=>0]);
        $progress->favorite($owner,'lesson',1,true);
        $progress->saveLessonProgress($owner,['lesson_id'=>1,'position_seconds'=>10,'duration_seconds'=>20,'is_complete'=>false]);
        $progress->saveKhatmah($owner,['target_date'=>gmdate('Y-m-d',time()+86400),'completed_pages'=>10,'status'=>'active','expected_revision'=>0]);
        $progress->activity($owner,test_uuid(),'book_download');
        $db->prepare("INSERT INTO adhkar_progress(user_id,item_key,current_count,repeat_count,revision,updated_at) VALUES(?,'curated:1',1,3,1,UTC_TIMESTAMP())")->execute([$owner]);
        $night->preferences($owner,['expected_revision'=>0,'timezone'=>'Africa/Cairo']);
        for($i=0;$i<($owner===1?55:1);$i++)$night->write($owner,test_uuid(),['mutation_id'=>test_uuid(),'expected_revision'=>0,'night_date'=>'2026-01-01','timezone'=>'Africa/Cairo','entry_type'=>'manual','verse_count'=>10,'note'=>'private note owner '.$owner]);
    }
    $first=$service->read(1);$other=$service->read(2);
    export_check($first['profile']['id']===1&&$other['profile']['id']===2,'profile bound to each owner');
    foreach(['quran_bookmarks','quran_positions','khatmah_plans','user_favorites','video_progress','account_daily_stats','adhkar_progress','activity_events'] as $table){
        export_check(count($first[$table])===1&&count($other[$table])===1,'complete explicit '.$table.' section');
        export_check((int)$first[$table][0]['user_id']===1&&(int)$other[$table][0]['user_id']===2,'owned '.$table.' rows only');
    }
    export_check(count($first['night_prayer_sessions'])===55&&count($other['night_prayer_sessions'])===1,'night export traverses all pages without another owner');
    export_check($first['night_prayer_sessions'][0]['note']==='private note owner 1','owned encrypted note exported as approved plaintext');
    export_check($first['night_prayer_preferences']['values']->timezone==='Africa/Cairo','owned night preferences retained');
    $db->exec("UPDATE night_prayer_preferences SET preferences_json='{\"timezone\":\"Africa/Cairo\",\"private_exemptions\":[\"legacy private canary\"]}' WHERE user_id=1");
    export_check(!str_contains(json_encode($service->read(1),JSON_THROW_ON_ERROR),'legacy private canary'),'legacy unsupported night fields cannot enter export');
    $encoded=json_encode($first,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
    export_check(!str_contains($encoded,'private exemption canary')&&!str_contains($encoded,'credential canary')&&!str_contains($encoded,'password_hash')&&!str_contains($encoded,'note_cipher'),'credentials/private exemptions/ciphertexts excluded');
    export_check($first['profile']['preferences']->theme==='dark'&&$first['profile']['preferences']->language==='ar'&&$first['profile']['preferences']->quran_font_size===32,'all supported public preference values retained');
    export_check(json_decode($encoded,true,512,JSON_THROW_ON_ERROR)['quran_bookmarks'][0]['label']==='خاص "1" 😀','Unicode and escaping survive round trip');
    $db->exec("ALTER TABLE quran_positions ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT 'future secret column'");
    export_check(!str_contains(json_encode($service->read(1),JSON_THROW_ON_ERROR),'future secret column'),'future private columns cannot enter fixed projections');
    $login=$auth->login('one@example.invalid','ExportFixture123!');
    $api=new ApiKernel($db,$auth,new RateLimiter($db,str_repeat('r',32)),$night);
    $headers=['Authorization'=>'Bearer '.$login['session']['access_token']];
    $response=$api->handle(new Request('GET','/api/v1/me/export',[],$headers));
    export_check($response->status===200&&count($response->body['data']['night_prayer_sessions'])===55,'API preserves export shape and all pages');
    export_check(!str_contains(json_encode($response->body,JSON_THROW_ON_ERROR),$login['session']['access_token']),'API token never included in personal copy');
    export_check($api->handle(new Request('GET','/api/v1/me/export'))->status===401,'anonymous export denied');
    export_check($api->handle(new Request('POST','/api/v1/me/export',[],$headers))->status===405,'export route is read only');
    $db->beginTransaction();$service->read(1);export_check($db->inTransaction(),'caller-owned snapshot transaction is retained');$db->rollBack();
    $db->exec("UPDATE users SET preferences_json='{\"theme\":{\"secret\":\"hidden\"}}' WHERE id=1");
    try{$service->read(1);export_check(false,'unreadable known preference must abort');}catch(UnexpectedValueException $error){export_check(!$db->inTransaction(),'malformed preference aborts and rolls back export');}
    $db->exec("UPDATE users SET preferences_json='0' WHERE id=1");
    try{$service->read(1);export_check(false,'scalar preferences must not become an empty copy');}catch(UnexpectedValueException $error){export_check(!$db->inTransaction(),'unreadable preferences abort snapshot without a partial copy');}
    export_check($db->query('SELECT preferences_json FROM users WHERE id=1')->fetchColumn()==='0','unreadable source is preserved');
});
echo 'PASS: '.$checks." private export/ownership/projection/paging/escaping assertions; no live delivery.\n";
