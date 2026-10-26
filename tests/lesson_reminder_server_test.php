<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';

use Athar\Engagement\LessonReminderService;
use Athar\Engagement\NotificationPreferenceService;
use Athar\Http\ApiError;
use Athar\Learning\UpcomingLessonService;

$checks=0;
function lr_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}
function lr_denied(callable $call,int $status,string $label):void{
    try{$call();}catch(ApiError $e){lr_assert($e->status===$status,$label);return;}lr_assert(false,$label);
}

isolated_database(static function(PDO $db):void{
    $password=password_hash('ReminderFixture123!',PASSWORD_DEFAULT);
    foreach(['reader','admin'] as $name)$db->prepare('INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP())')
        ->execute([$name.'@example.test',$name,$name,$password,$name==='admin'?'admin':'user']);
    $user=1;$admin=['id'=>2,'role'=>'admin','email_verified_at'=>'2026-01-01','suspended_at'=>null];
    $db->exec("INSERT INTO scholars(name,is_published) VALUES('Reminder scholar',1)");
    $db->exec("INSERT INTO series(scholar_id,title,is_published) VALUES(1,'Reminder series',1)");
    $db->exec("INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,is_published)
        VALUES(1,1,'Reminder lesson','Fixture','Remind00001','https://example.invalid/reminder',0)");

    $events=new UpcomingLessonService($db);
    $events->save($admin,1,[
        'expected_revision'=>0,'status'=>'scheduled','scheduled_at'=>'2030-06-01T20:00:00+03:00',
        'schedule_timezone'=>'Africa/Cairo','reminders_enabled'=>true
    ]);
    $reminders=new LessonReminderService($db);
    $initial=$reminders->get($user,1);
    lr_assert($initial['revision']===0&&!$initial['enabled'],'missing subscription defaults off');
    lr_denied(fn()=>$reminders->set($user,1,['expected_revision'=>0,'enabled'=>true]),409,'server opt-in requires notification consent');

    $preferences=new NotificationPreferenceService($db);
    $preferences->update($user,[
        'expected_revision'=>0,'master_enabled'=>true,'consent_version'=>1,
        'categories'=>['lesson_reminders'=>true],
    ]);
    $enabled=$reminders->set($user,1,['expected_revision'=>0,'enabled'=>true]);
    lr_assert($enabled['revision']===1&&$enabled['enabled'],'explicit user choice persists');
    lr_assert($enabled['lead_minutes']===15&&$enabled['job']['status']==='pending','single 15-minute server job created');
    lr_assert((int)$db->query('SELECT COUNT(*) FROM push_outbox')->fetchColumn()===0,'save never sends or outboxes early');
    lr_denied(fn()=>$reminders->set($user,1,['expected_revision'=>0,'enabled'=>false]),409,'stale device cannot overwrite reminder choice');

    $due=new DateTimeImmutable('2030-06-01T16:46:00Z');
    $processed=$reminders->processDueBatch(10,$due);
    lr_assert($processed['outboxed']===1,'due worker creates canonical push intent');
    lr_assert((int)$db->query("SELECT COUNT(*) FROM push_outbox WHERE status='queued' AND category='lesson_reminder'")->fetchColumn()===1,'outbox is queued not sent');
    lr_assert($reminders->processDueBatch(10,$due)['outboxed']===0,'replay does not duplicate outbox');

    $rescheduled=$events->save($admin,1,['expected_revision'=>1,'scheduled_at'=>'2030-06-01T21:00:00+03:00']);
    lr_assert($rescheduled['revision']===2,'admin reschedule advances event revision');
    $current=$reminders->get($user,1);
    lr_assert($current['enabled']&&$current['event_revision']===2&&$current['job']['status']==='pending','reschedule rebuilds pending server job');
    lr_assert((int)$db->query("SELECT COUNT(*) FROM push_outbox WHERE status='cancelled' AND category='lesson_reminder'")->fetchColumn()===1,'obsolete queued push intent cancelled');

    $preferences->update($user,['expected_revision'=>1,'master_enabled'=>false]);
    lr_assert($reminders->get($user,1)['job']['status']==='cancelled','master pause cancels server delivery work');
    $preferences->update($user,['expected_revision'=>2,'master_enabled'=>true,'consent_version'=>1]);
    lr_assert($reminders->get($user,1)['job']['status']==='pending','re-enabling current consent rebuilds future server job');

    $disabled=$reminders->set($user,1,['expected_revision'=>1,'enabled'=>false]);
    lr_assert($disabled['revision']===2&&!$disabled['enabled']&&$disabled['job']['status']==='cancelled','explicit opt-out cancels job');

    $reminders->set($user,1,['expected_revision'=>2,'enabled'=>true]);
    $cancelled=$events->save($admin,1,['expected_revision'=>2,'status'=>'cancelled']);
    lr_assert($cancelled['status']==='cancelled','event cancellation accepted');
    lr_assert(!$reminders->get($user,1)['enabled'],'authoritative cancellation disables subscription');

    $db->exec("INSERT INTO lessons(scholar_id,series_id,title,description,youtube_id,source_url,is_published)
        VALUES(1,1,'Quota target','Fixture','Quota000001','https://example.invalid/quota',0)");
    $events->save($admin,2,[
        'expected_revision'=>0,'status'=>'scheduled','scheduled_at'=>'2030-06-02T20:00:00+03:00',
        'schedule_timezone'=>'Africa/Cairo','reminders_enabled'=>true
    ]);
    for($i=10;$i<10+LessonReminderService::MAX_ACTIVE;$i++){
        $youtube=sprintf('Q%010d',$i);
        $db->prepare("INSERT INTO lessons(id,scholar_id,series_id,title,description,youtube_id,source_url,is_published)
            VALUES(?,1,1,?,'Fixture',?,'https://example.invalid/quota-item',0)")
            ->execute([$i,'Quota '.$i,$youtube]);
        $db->prepare("INSERT INTO lesson_reminder_subscriptions(user_id,lesson_id,revision,enabled,lead_minutes,created_at,updated_at)
            VALUES (?, ?, 1, 1, 15, UTC_TIMESTAMP(), UTC_TIMESTAMP())")->execute([$user,$i]);
    }
    lr_denied(fn()=>$reminders->set($user,2,['expected_revision'=>0,'enabled'=>true]),409,'active reminder quota enforced');

    lr_assert((int)$db->query("SELECT COUNT(*) FROM push_outbox WHERE status='sent'")->fetchColumn()===0,'task 11.4 never marks physical push sent');
});

echo 'PASS: '.$checks." lesson reminder server consent/job/outbox/reconcile/quota assertions.\n";
