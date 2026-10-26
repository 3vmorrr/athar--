<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Engagement\FollowService;
use Athar\Engagement\NewContentFactService;

$checks=0;
function fact_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}

isolated_database(static function(PDO $db):void{
    $password=password_hash('ContentFact123!',PASSWORD_DEFAULT);
    foreach(['scholar_follower','series_follower','both_follower','outsider'] as $name)$db->prepare(
        'INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at,preferences_json)
         VALUES (?,?,?,?,?,UTC_TIMESTAMP(),?)'
    )->execute([$name.'@example.test',$name,$name,$password,'user',json_encode(['language'=>$name==='series_follower'?'en':'ar'])]);

    $db->exec("INSERT INTO scholars(name,is_published) VALUES('Fact scholar',1)");
    $db->exec("INSERT INTO series(scholar_id,title,is_published) VALUES(1,'Fact series',0)");
    $db->exec("INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,is_published)
        VALUES(NULL,1,'Fact lesson','Fixture','FactVid0001','https://example.invalid/fact',0)");

    $follows=new FollowService($db);
    $follows->follow(1,'scholar',1);
    $follows->follow(3,'scholar',1);

    $facts=new NewContentFactService($db);
    $initial=$facts->observeSeries(1);
    fact_assert($initial['created']===[]&&!$initial['public'],'unpublished series produces no fact');

    $db->exec('UPDATE series SET is_published=1 WHERE id=1');
    $publishedSeries=$facts->observeSeries(1);
    fact_assert(count($publishedSeries['created'])===1,'series publication produces one fact');
    $seriesFact=$publishedSeries['created'][0];
    fact_assert(array_column($facts->eligibleRecipients($seriesFact),'user_id')===[1,3],'new series targets only current scholar followers');

    $follows->follow(2,'series',1);
    $follows->follow(3,'series',1);
    $facts->observeSeries(1);
    fact_assert((int)$db->query("SELECT COUNT(*) FROM engagement_content_facts WHERE fact_type='series_published' AND target_id=1")->fetchColumn()===1,'series publication fact dedupes');

    $facts->observeLesson(1);
    $db->exec('UPDATE lessons SET is_published=1 WHERE id=1');
    $publishedLesson=$facts->observeLesson(1);
    fact_assert(count($publishedLesson['created'])===1,'lesson publication produces one fact');
    $lessonFact=$publishedLesson['created'][0];
    fact_assert(array_column($facts->eligibleRecipients($lessonFact),'user_id')===[1,2,3],'lesson recipients union scholar and series follows without duplicates');

    $follows->unfollow(2,'series',1);
    fact_assert(array_column($facts->eligibleRecipients($lessonFact),'user_id')===[1,3],'unfollow removes eligibility for an existing candidate fact');

    $db->exec("INSERT INTO lesson_events(lesson_id,status,scheduled_at,schedule_timezone,is_live_event,reminders_enabled,replay_available,visibility,revision,created_at,updated_at)
        VALUES(1,'published','2030-01-01 10:00:00','UTC',1,0,0,'public',1,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
    $facts->observeLesson(1);
    $db->exec("UPDATE lesson_events SET replay_available=1,revision=2 WHERE lesson_id=1");
    $replay=$facts->observeLesson(1);
    fact_assert(count($replay['created'])===1,'replay availability transition produces one fact');
    $replayFact=$replay['created'][0];
    fact_assert((int)$db->query("SELECT COUNT(*) FROM engagement_content_facts WHERE fact_type='lesson_replay_available' AND target_id=1")->fetchColumn()===1,'replay fact dedupes');
    $facts->observeLesson(1);
    fact_assert((int)$db->query("SELECT COUNT(*) FROM engagement_content_facts WHERE fact_type='lesson_replay_available' AND target_id=1")->fetchColumn()===1,'replay re-observation does not duplicate');

    $db->exec('UPDATE scholars SET is_published=0 WHERE id=1');
    fact_assert($facts->eligibleRecipients($lessonFact)===[],'hidden parent suppresses recipient eligibility');
    fact_assert((string)$db->query('SELECT status FROM engagement_content_facts WHERE id='.$lessonFact)->fetchColumn()==='cancelled','hidden content cancels active fact');

    $db->exec("INSERT INTO account_daily_stats(user_id,activity_date,quran_sessions,lesson_completions,adhkar_completions,book_downloads)
        VALUES(1,UTC_DATE(),99,99,99,99),(4,UTC_DATE(),0,0,0,0)");
    fact_assert($facts->eligibleRecipients($replayFact)===[],'worship/activity history never restores eligibility after content becomes hidden');

    $db->exec('DELETE FROM series WHERE id=1');
    $missing=$facts->observeSeries(1);
    fact_assert(!$missing['public']&&$missing['created']===[],'deleted target produces no new fact');
});

echo 'PASS: '.$checks." publication/replay/dedupe/unfollow/hidden/audience-privacy assertions.\n";
