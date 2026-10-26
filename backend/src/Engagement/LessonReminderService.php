<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use DateInterval;
use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class LessonReminderService
{
    public const LEAD_MINUTES = 15;
    public const MAX_ACTIVE = 64;
    private const MAX_ATTEMPTS = 5;

    public function __construct(private PDO $db) {}

    public function list(int $userId): array
    {
        $q=$this->db->prepare("SELECT s.user_id,s.lesson_id,s.revision,s.enabled,s.lead_minutes,s.event_id,
            s.event_revision,s.scheduled_at,s.source_timezone,s.updated_at,
            j.status job_status,j.due_at,j.expires_at,j.last_error_code
            FROM lesson_reminder_subscriptions s
            LEFT JOIN lesson_reminder_jobs j ON j.user_id=s.user_id AND j.lesson_id=s.lesson_id
            WHERE s.user_id=? ORDER BY s.lesson_id");
        $q->execute([$userId]);
        return ['items'=>array_map([$this,'present'],$q->fetchAll())];
    }

    public function get(int $userId,int $lessonId): array
    {
        $q=$this->db->prepare("SELECT s.user_id,s.lesson_id,s.revision,s.enabled,s.lead_minutes,s.event_id,
            s.event_revision,s.scheduled_at,s.source_timezone,s.updated_at,
            j.status job_status,j.due_at,j.expires_at,j.last_error_code
            FROM lesson_reminder_subscriptions s
            LEFT JOIN lesson_reminder_jobs j ON j.user_id=s.user_id AND j.lesson_id=s.lesson_id
            WHERE s.user_id=? AND s.lesson_id=?");
        $q->execute([$userId,$lessonId]);
        $row=$q->fetch();
        return $row?$this->present($row):[
            'lesson_id'=>$lessonId,'revision'=>0,'enabled'=>false,'lead_minutes'=>self::LEAD_MINUTES,
            'event_id'=>null,'event_revision'=>null,'scheduled_at'=>null,'source_timezone'=>null,
            'job'=>null,'updated_at'=>null,
        ];
    }

    public function set(int $userId,int $lessonId,array $input): array
    {
        if($lessonId<1)throw new ApiError('invalid_fields',422,['lesson_id'=>'invalid']);
        if(array_diff(array_keys($input),['expected_revision','enabled']))throw new ApiError('invalid_fields',422,['input'=>'unsupported_field']);
        $expected=$input['expected_revision']??null;$enabled=$input['enabled']??null;
        if(!is_int($expected)||$expected<0)throw new ApiError('invalid_fields',422,['expected_revision'=>'required']);
        if(!is_bool($enabled))throw new ApiError('invalid_fields',422,['enabled'=>'boolean_required']);

        $this->db->beginTransaction();
        try{
            $u=$this->db->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');$u->execute([$userId]);
            if(!$u->fetchColumn())throw new ApiError('not_found',404);
            $q=$this->db->prepare('SELECT * FROM lesson_reminder_subscriptions WHERE user_id=? AND lesson_id=? FOR UPDATE');
            $q->execute([$userId,$lessonId]);$old=$q->fetch()?:null;
            $current=$old===null?0:(int)$old['revision'];
            if($expected!==$current)throw new ApiError('conflict',409,['revision'=>$current]);
            $revision=$current+1;

            if($enabled){
                if(!$this->consentEnabled($userId))throw new ApiError('notification_consent_required',409);
                $event=$this->event($lessonId);
                if(!$this->eligible($event))throw new ApiError('reminder_unavailable',409);
                $now=$this->utcNow();$window=$this->window($event,$now);
                if($window===null)throw new ApiError('reminder_window_passed',409);
                $count=$this->db->prepare('SELECT COUNT(*) FROM lesson_reminder_subscriptions WHERE user_id=? AND enabled=1 AND lesson_id<>?');
                $count->execute([$userId,$lessonId]);
                if((int)$count->fetchColumn()>=self::MAX_ACTIVE)throw new ApiError('reminder_quota_exceeded',409);

                $this->upsertSubscription($userId,$lessonId,$revision,true,$event);
                $this->cancelOutbox($userId,$lessonId,'superseded');
                $this->upsertJob($userId,$lessonId,$revision,$event,$window);
            }else{
                $this->upsertSubscription($userId,$lessonId,$revision,false,null);
                $this->cancelJob($userId,$lessonId,'user_disabled');
                $this->cancelOutbox($userId,$lessonId,'user_disabled');
            }

            $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id)
                VALUES (?,?, 'lesson_reminder', ?)")->execute([
                    $userId,$enabled?'lesson-reminder-enabled':'lesson-reminder-disabled',(string)$lessonId
                ]);
            $this->db->commit();
            return $this->get($userId,$lessonId);
        }catch(Throwable $failure){
            if($this->db->inTransaction())$this->db->rollBack();
            throw $failure;
        }
    }

    public function reconcileUser(int $userId,?DateTimeImmutable $now=null): void
    {
        $now=$now??$this->utcNow();
        if(!$this->consentEnabled($userId)){
            $this->db->prepare("UPDATE lesson_reminder_jobs SET status='cancelled',locked_at=NULL,
                last_error_code='consent_disabled',updated_at=UTC_TIMESTAMP()
                WHERE user_id=? AND status IN ('pending','failed')")->execute([$userId]);
            $this->db->prepare("UPDATE push_outbox SET status='cancelled',locked_at=NULL,
                last_error_code='consent_disabled',updated_at=UTC_TIMESTAMP()
                WHERE user_id=? AND category='lesson_reminder' AND status IN ('queued','processing','failed')")->execute([$userId]);
            return;
        }
        $q=$this->db->prepare('SELECT * FROM lesson_reminder_subscriptions WHERE user_id=? AND enabled=1 ORDER BY lesson_id');
        $q->execute([$userId]);
        foreach($q->fetchAll() as $subscription)$this->reconcileSubscription($subscription,$now);
    }

    public function reconcileLesson(int $lessonId,?DateTimeImmutable $now=null): void
    {
        $now=$now??$this->utcNow();
        $q=$this->db->prepare('SELECT * FROM lesson_reminder_subscriptions WHERE lesson_id=? AND enabled=1 ORDER BY user_id');
        $q->execute([$lessonId]);
        foreach($q->fetchAll() as $subscription)$this->reconcileSubscription($subscription,$now);
    }

    public function processDueBatch(int $limit=20,?DateTimeImmutable $now=null): array
    {
        $limit=max(1,min(100,$limit));$now=$now??$this->utcNow();
        $result=['outboxed'=>0,'cancelled'=>0,'skipped'=>0,'failed'=>0];
        for($i=0;$i<$limit;$i++){
            $jobId=null;
            try{
                $this->db->beginTransaction();
                $q=$this->db->prepare("SELECT id FROM lesson_reminder_jobs
                    WHERE status IN ('pending','failed') AND due_at<=? AND expires_at>?
                    AND (next_attempt_at IS NULL OR next_attempt_at<=?)
                    ORDER BY due_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
                $stamp=$this->sql($now);$q->execute([$stamp,$stamp,$stamp]);$jobId=$q->fetchColumn();
                if($jobId===false){$this->db->commit();break;}

                $q=$this->db->prepare('SELECT * FROM lesson_reminder_jobs WHERE id=?');
                $q->execute([(int)$jobId]);$job=$q->fetch();
                if(!$job){$this->db->commit();continue;}
                $q=$this->db->prepare('SELECT * FROM lesson_reminder_subscriptions WHERE user_id=? AND lesson_id=?');
                $q->execute([(int)$job['user_id'],(int)$job['lesson_id']]);$subscription=$q->fetch()?:null;
                $event=$this->event((int)$job['lesson_id']);

                if(!$subscription||(int)$subscription['enabled']!==1||
                    (int)$subscription['revision']!==(int)$job['subscription_revision']||
                    !$this->consentEnabled((int)$job['user_id'])){
                    $this->cancelJob((int)$job['user_id'],(int)$job['lesson_id'],'consent_or_subscription_changed');
                    $this->cancelOutbox((int)$job['user_id'],(int)$job['lesson_id'],'consent_or_subscription_changed');
                    $result['cancelled']++;$this->db->commit();continue;
                }

                if(!$this->eligible($event)){
                    $this->disableSubscription((int)$job['user_id'],(int)$job['lesson_id'],'event_unavailable');
                    $result['cancelled']++;$this->db->commit();continue;
                }

                $window=$this->window($event,$now,false);
                $same=(int)$event['revision']===(int)$job['event_revision']
                    && $this->sql($this->instant((string)$event['scheduled_at']))===$this->normalizeSql((string)$job['expires_at']);
                if(!$same){
                    $this->reconcileSubscription($subscription,$now);
                    $result['cancelled']++;$this->db->commit();continue;
                }
                if($window===null||!$now<$this->instant((string)$job['expires_at'])){
                    $this->disableSubscription((int)$job['user_id'],(int)$job['lesson_id'],'expired');
                    $result['skipped']++;$this->db->commit();continue;
                }

                $payload=[
                    'kind'=>'lesson_reminder',
                    'lesson_id'=>(int)$job['lesson_id'],
                    'event_id'=>(int)$event['event_id'],
                    'event_revision'=>(int)$event['revision'],
                    'destination'=>'lesson:'.(int)$job['lesson_id'],
                    'lead_minutes'=>self::LEAD_MINUTES,
                ];
                $this->db->prepare("INSERT INTO push_outbox
                    (user_id,category,source_type,source_id,payload_json,dedupe_key,status,expires_at,created_at,updated_at)
                    VALUES (?,'lesson_reminder','lesson_reminder',?,?,?,'queued',?,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                    ON DUPLICATE KEY UPDATE dedupe_key=dedupe_key")
                    ->execute([
                        (int)$job['user_id'],'lesson:'.(int)$job['lesson_id'],
                        json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR),
                        $job['dedupe_key'],$job['expires_at']
                    ]);
                $this->db->prepare("UPDATE lesson_reminder_jobs SET status='outboxed',locked_at=NULL,
                    last_error_code=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([(int)$job['id']]);
                $result['outboxed']++;$this->db->commit();
            }catch(Throwable $failure){
                if($this->db->inTransaction())$this->db->rollBack();
                if($jobId!==null)$this->recordFailure((int)$jobId,$now);
                $result['failed']++;
            }
        }
        return $result;
    }

    private function reconcileSubscription(array $subscription,DateTimeImmutable $now): void
    {
        $user=(int)$subscription['user_id'];$lesson=(int)$subscription['lesson_id'];
        if(!$this->consentEnabled($user)){
            $this->cancelJob($user,$lesson,'consent_disabled');
            $this->cancelOutbox($user,$lesson,'consent_disabled');
            return;
        }
        $event=$this->event($lesson);
        if(!$this->eligible($event)){
            $this->disableSubscription($user,$lesson,'event_unavailable');
            return;
        }
        $window=$this->window($event,$now);
        if($window===null){
            $this->disableSubscription($user,$lesson,'reminder_window_passed');
            return;
        }
        $this->db->prepare("UPDATE lesson_reminder_subscriptions SET event_id=?,event_revision=?,
            scheduled_at=?,source_timezone=?,lead_minutes=?,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND lesson_id=?")->execute([
                (int)$event['event_id'],(int)$event['revision'],$event['scheduled_at'],$event['schedule_timezone'],
                self::LEAD_MINUTES,$user,$lesson
            ]);
        $this->cancelOutbox($user,$lesson,'superseded');
        $this->upsertJob($user,$lesson,(int)$subscription['revision'],$event,$window);
    }

    private function disableSubscription(int $userId,int $lessonId,string $reason): void
    {
        $this->db->prepare("UPDATE lesson_reminder_subscriptions SET enabled=0,revision=revision+1,
            updated_at=UTC_TIMESTAMP() WHERE user_id=? AND lesson_id=? AND enabled=1")->execute([$userId,$lessonId]);
        $this->cancelJob($userId,$lessonId,$reason);$this->cancelOutbox($userId,$lessonId,$reason);
    }

    private function upsertSubscription(int $userId,int $lessonId,int $revision,bool $enabled,?array $event): void
    {
        $this->db->prepare("INSERT INTO lesson_reminder_subscriptions
            (user_id,lesson_id,revision,enabled,lead_minutes,event_id,event_revision,scheduled_at,source_timezone,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE revision=VALUES(revision),enabled=VALUES(enabled),lead_minutes=VALUES(lead_minutes),
            event_id=VALUES(event_id),event_revision=VALUES(event_revision),scheduled_at=VALUES(scheduled_at),
            source_timezone=VALUES(source_timezone),updated_at=UTC_TIMESTAMP()")
            ->execute([
                $userId,$lessonId,$revision,(int)$enabled,self::LEAD_MINUTES,
                $event===null?null:(int)$event['event_id'],$event===null?null:(int)$event['revision'],
                $event['scheduled_at']??null,$event['schedule_timezone']??null
            ]);
    }

    private function upsertJob(int $userId,int $lessonId,int $subscriptionRevision,array $event,array $window): void
    {
        $dedupe=hash('sha256','lesson-reminder:'.$userId.':'.$event['event_id'].':r'.$event['revision'].':'.$event['scheduled_at'].':'.self::LEAD_MINUTES);
        $this->db->prepare("INSERT INTO lesson_reminder_jobs
            (user_id,lesson_id,subscription_revision,event_revision,due_at,expires_at,status,dedupe_key,
             attempt_count,next_attempt_at,locked_at,last_error_code,created_at,updated_at)
            VALUES (?,?,?,?,?,?,'pending',?,0,NULL,NULL,NULL,UTC_TIMESTAMP(),UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE subscription_revision=VALUES(subscription_revision),event_revision=VALUES(event_revision),
            due_at=VALUES(due_at),expires_at=VALUES(expires_at),status='pending',dedupe_key=VALUES(dedupe_key),
            attempt_count=0,next_attempt_at=NULL,locked_at=NULL,last_error_code=NULL,updated_at=UTC_TIMESTAMP()")
            ->execute([
                $userId,$lessonId,$subscriptionRevision,(int)$event['revision'],
                $this->sql($window['due']),$this->sql($window['expires']),$dedupe
            ]);
    }

    private function cancelJob(int $userId,int $lessonId,string $reason): void
    {
        $this->db->prepare("UPDATE lesson_reminder_jobs SET status='cancelled',locked_at=NULL,
            last_error_code=?,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND lesson_id=? AND status<>'cancelled'")->execute([$reason,$userId,$lessonId]);
    }

    private function cancelOutbox(int $userId,int $lessonId,string $reason): void
    {
        $this->db->prepare("UPDATE push_outbox SET status='cancelled',locked_at=NULL,last_error_code=?,
            updated_at=UTC_TIMESTAMP() WHERE user_id=? AND category='lesson_reminder'
            AND source_type='lesson_reminder' AND source_id=? AND status IN ('queued','processing','failed')")
            ->execute([$reason,$userId,'lesson:'.$lessonId]);
    }

    private function recordFailure(int $jobId,DateTimeImmutable $now): void
    {
        try{
            $q=$this->db->prepare('SELECT attempt_count FROM lesson_reminder_jobs WHERE id=?');$q->execute([$jobId]);
            $attempt=(int)($q->fetchColumn()?:0)+1;
            $terminal=$attempt>=self::MAX_ATTEMPTS;
            $delay=min(3600,30*(2**max(0,$attempt-1)));
            $next=$now->add(new DateInterval('PT'.$delay.'S'));
            $this->db->prepare("UPDATE lesson_reminder_jobs SET status=?,attempt_count=?,
                next_attempt_at=?,locked_at=NULL,last_error_code='processor_error',updated_at=UTC_TIMESTAMP()
                WHERE id=?")->execute([$terminal?'skipped':'failed',$attempt,$terminal?null:$this->sql($next),$jobId]);
        }catch(Throwable $ignored){}
    }

    private function event(int $lessonId): ?array
    {
        $q=$this->db->prepare("SELECT e.id event_id,e.lesson_id,e.status,e.scheduled_at,e.schedule_timezone,
            e.reminders_enabled,e.visibility,e.revision,l.scholar_id,l.series_id,
            s.is_published scholar_published,se.is_published series_published,
            se.scholar_id series_scholar_id,ss.is_published series_scholar_published
            FROM lesson_events e JOIN lessons l ON l.id=e.lesson_id
            LEFT JOIN scholars s ON s.id=l.scholar_id
            LEFT JOIN series se ON se.id=l.series_id
            LEFT JOIN scholars ss ON ss.id=se.scholar_id
            WHERE e.lesson_id=?");
        $q->execute([$lessonId]);return $q->fetch()?:null;
    }

    private function eligible(?array $event): bool
    {
        if(!$event||$event['status']!=='scheduled'||$event['visibility']!=='public'||
            !(bool)$event['reminders_enabled']||$event['scheduled_at']===null)return false;
        if($event['scholar_id']!==null&&!(bool)$event['scholar_published'])return false;
        if($event['series_id']!==null){
            if(!(bool)$event['series_published'])return false;
            if($event['series_scholar_id']!==null&&!(bool)$event['series_scholar_published'])return false;
        }
        return true;
    }

    private function consentEnabled(int $userId): bool
    {
        $q=$this->db->prepare('SELECT master_enabled,consent_version,lesson_reminders FROM notification_preferences WHERE user_id=?');
        $q->execute([$userId]);$row=$q->fetch();
        return $row&&(bool)$row['master_enabled']&&(int)$row['consent_version']===NotificationPreferenceService::CONSENT_VERSION&&(bool)$row['lesson_reminders'];
    }

    private function window(array $event,DateTimeImmutable $now,bool $requireFuture=true): ?array
    {
        $expires=$this->instant((string)$event['scheduled_at']);
        $due=$expires->sub(new DateInterval('PT'.self::LEAD_MINUTES.'M'));
        if($requireFuture&&!$due>$now)return null;
        return ['due'=>$due,'expires'=>$expires];
    }

    private function present(array $row): array
    {
        return [
            'lesson_id'=>(int)$row['lesson_id'],'revision'=>(int)$row['revision'],'enabled'=>(bool)$row['enabled'],
            'lead_minutes'=>(int)$row['lead_minutes'],'event_id'=>$row['event_id']===null?null:(int)$row['event_id'],
            'event_revision'=>$row['event_revision']===null?null:(int)$row['event_revision'],
            'scheduled_at'=>$this->iso($row['scheduled_at']),'source_timezone'=>$row['source_timezone'],
            'job'=>$row['job_status']===null?null:[
                'status'=>$row['job_status'],'due_at'=>$this->iso($row['due_at']),'expires_at'=>$this->iso($row['expires_at']),
                'last_error_code'=>$row['last_error_code'],
            ],
            'updated_at'=>$row['updated_at'],
        ];
    }

    private function utcNow(): DateTimeImmutable { return new DateTimeImmutable('now',new DateTimeZone('UTC')); }
    private function instant(string $value): DateTimeImmutable { return new DateTimeImmutable($value,new DateTimeZone('UTC')); }
    private function sql(DateTimeImmutable $value): string { return $value->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.v'); }
    private function normalizeSql(string $value): string { return $this->sql($this->instant($value)); }
    private function iso($value): ?string { return $value===null?null:$this->instant((string)$value)->format('Y-m-d\TH:i:s.v\Z'); }
}
