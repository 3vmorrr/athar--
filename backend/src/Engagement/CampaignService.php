<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class CampaignService
{
    public function __construct(private PDO $db, private ?\Closure $readiness = null) {}

    public function createDraft(array $actor,array $input): array
    {
        $this->admin($actor);
        $data=$this->validateDraft($input);
        $this->db->beginTransaction();
        try {
        $this->admin($actor);
        $q=$this->db->prepare(
            "INSERT INTO engagement_campaigns
            (name,channel,status,audience_type,audience_ref_id,audience_locale,title_ar,body_ar,title_en,body_en,deep_link,image_url,scheduled_at,schedule_timezone,ttl_seconds,min_frequency_hours,respect_quiet_hours,revision,created_by,created_at,updated_at)
            VALUES (?,?, 'draft',?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())"
        );
        $q->execute([
            $data['name'],$data['channel'],$data['audience_type'],$data['audience_ref_id'],$data['audience_locale'],
            $data['title_ar'],$data['body_ar'],$data['title_en'],$data['body_en'],$data['deep_link'],$data['image_url'],
            $data['scheduled_at'],$data['schedule_timezone'],$data['ttl_seconds'],$data['min_frequency_hours'],
            (int)$data['respect_quiet_hours'],(int)$actor['id'],
        ]);
        $id=(int)$this->db->lastInsertId();
        $this->event($id,'draft_created',(int)$actor['id'],['channel'=>$data['channel'],'audience_type'=>$data['audience_type']]);
        $this->audit($actor,'engagement-campaign-draft-created',$id);
        $this->db->commit();
        return $this->detail($actor,$id);
        } catch(Throwable $e) {if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function updateDraft(array $actor,int $id,array $input): array
    {
        $this->admin($actor);
        $expected=$input['expected_revision']??null;
        if(!is_int($expected)||$expected<1)throw new ApiError('invalid_fields',422,['expected_revision'=>'required']);
        unset($input['expected_revision']);
        $data=$this->validateDraft($input);
        $this->db->beginTransaction();
        try{
            $this->admin($actor);
            $q=$this->db->prepare('SELECT * FROM engagement_campaigns WHERE id=? FOR UPDATE');$q->execute([$id]);$old=$q->fetch();
            if(!$old)throw new ApiError('not_found',404);
            if($old['status']!=='draft')throw new ApiError('conflict',409,['status'=>$old['status']]);
            if((int)$old['revision']!==$expected)throw new ApiError('conflict',409,['current_revision'=>(int)$old['revision']]);
            if(($actor['role']??'')!=='owner'&&(int)$old['created_by']!==(int)$actor['id'])throw new ApiError('forbidden',403);
            $next=$expected+1;
            $this->db->prepare(
                'UPDATE engagement_campaigns SET name=?,channel=?,audience_type=?,audience_ref_id=?,audience_locale=?,title_ar=?,body_ar=?,title_en=?,body_en=?,deep_link=?,image_url=?,scheduled_at=?,schedule_timezone=?,ttl_seconds=?,min_frequency_hours=?,respect_quiet_hours=?,revision=?,updated_at=UTC_TIMESTAMP() WHERE id=?'
            )->execute([
                $data['name'],$data['channel'],$data['audience_type'],$data['audience_ref_id'],$data['audience_locale'],
                $data['title_ar'],$data['body_ar'],$data['title_en'],$data['body_en'],$data['deep_link'],$data['image_url'],
                $data['scheduled_at'],$data['schedule_timezone'],$data['ttl_seconds'],$data['min_frequency_hours'],
                (int)$data['respect_quiet_hours'],$next,$id,
            ]);
            $this->event($id,'draft_updated',(int)$actor['id'],['revision'=>$next]);
            $this->audit($actor,'engagement-campaign-draft-updated',$id);
            $this->db->commit();
            return $this->detail($actor,$id);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function detail(array $actor,int $id): array
    {
        $this->admin($actor);
        $q=$this->db->prepare('SELECT c.*,u.email creator_email,cf.email confirmer_email FROM engagement_campaigns c LEFT JOIN users u ON u.id=c.created_by LEFT JOIN users cf ON cf.id=c.confirmed_by WHERE c.id=?');
        $q->execute([$id]);$row=$q->fetch();if(!$row)throw new ApiError('not_found',404);
        return $this->decorate($row)+[
            'delivery_counts'=>$this->deliveryCounts($id),
            'delivery_analytics'=>$this->deliveryAnalytics($id),
            'history'=>$this->history($id),
        ];
    }

    public function page(array $actor,int $page=1,int $size=30): array
    {
        $this->admin($actor);$page=max(1,min(10000,$page));$size=max(1,min(100,$size));
        $total=(int)$this->db->query('SELECT COUNT(*) FROM engagement_campaigns')->fetchColumn();
        $q=$this->db->query('SELECT c.*,u.email creator_email,cf.email confirmer_email FROM engagement_campaigns c LEFT JOIN users u ON u.id=c.created_by LEFT JOIN users cf ON cf.id=c.confirmed_by ORDER BY c.id DESC LIMIT '.$size.' OFFSET '.(($page-1)*$size));
        $items=[];foreach($q->fetchAll() as $row)$items[]=$this->decorate($row)+['delivery_counts'=>$this->deliveryCounts((int)$row['id'])];
        return ['items'=>$items,'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total]];
    }

    public function preview(array $actor,int $id): array
    {
        $campaign=$this->detail($actor,$id);
        $channels=$this->channels($campaign['channel']);
        $audience=[];$total=0;$dependencies=[];
        foreach($channels as $channel){
            $resolved=$this->resolveAudience($campaign,$channel,(int)$actor['id'],true);
            $audience[$channel]=['eligible_count'=>count($resolved['users']),'available'=>$resolved['available'],'reason'=>$resolved['reason']];
            $total+=count($resolved['users']);
            if(!$resolved['available'])$dependencies[$channel]=$resolved['reason'];
        }
        return [
            'campaign'=>$campaign,
            'audience'=>$audience,
            'recipient_channel_total'=>$total,
            'dependencies'=>$dependencies,
            'preview'=>[
                'ar'=>['title'=>$campaign['title_ar'],'body'=>$campaign['body_ar'],'deep_link'=>$campaign['deep_link'],'image_url'=>$campaign['image_url']],
                'en'=>['title'=>$campaign['title_en'],'body'=>$campaign['body_en'],'deep_link'=>$campaign['deep_link'],'image_url'=>$campaign['image_url']],
            ],
        ];
    }

    public function confirmAndSchedule(array $actor,int $id,int $expected,string $confirmation): array
    {
        $this->owner($actor);
        if($confirmation!=='CONFIRM '.$id)throw new ApiError('invalid_fields',422,['confirmation'=>'invalid']);
        $this->db->beginTransaction();
        try{
            $this->admin($actor);
            $q=$this->db->prepare('SELECT * FROM engagement_campaigns WHERE id=? FOR UPDATE');$q->execute([$id]);$campaign=$q->fetch();
            if(!$campaign)throw new ApiError('not_found',404);
            if($campaign['status']!=='draft'||(int)$campaign['revision']!==$expected)throw new ApiError('conflict',409,['status'=>$campaign['status'],'current_revision'=>(int)$campaign['revision']]);
            $this->owner($actor);
            $scheduled=$campaign['scheduled_at']?:gmdate('Y-m-d H:i:s');
            if(strtotime($scheduled.' UTC')<time()-60||strtotime($scheduled.' UTC')>time()+366*86400)throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);
            $channels=$this->channels((string)$campaign['channel']);
            $resolvedChannels=[];$dependencyProblems=[];$eligibleTotal=0;
            foreach($channels as $channel){
                $resolvedChannels[$channel]=$this->resolveAudience($campaign,$channel,(int)$actor['id'],false);
                if(!$resolvedChannels[$channel]['available'])$dependencyProblems[$channel]=$resolvedChannels[$channel]['reason'];
                else $eligibleTotal+=count($resolvedChannels[$channel]['users']);
            }
            if($dependencyProblems)throw new ApiError('conflict',409,['dependencies'=>$dependencyProblems]);
            if($eligibleTotal<1)throw new ApiError('invalid_fields',422,['audience'=>'no_eligible_recipients']);
            $inserted=0;$skipped=0;
            foreach($channels as $channel){
                $resolved=$resolvedChannels[$channel];
                foreach($resolved['users'] as $user){
                    $locale=$user['locale']==='en'?'en':'ar';
                    $payload=$this->payload($campaign,$locale);
                    $quietNotBefore=$this->notBefore($scheduled,$campaign,$user);
                    $frequencyNotBefore=$this->frequencyNotBefore((int)$user['id'],$channel,(int)$campaign['min_frequency_hours'],$scheduled);
                    $notBefore=$this->notBefore($frequencyNotBefore,$campaign,$user);
                    $expires=(new DateTimeImmutable($scheduled,new DateTimeZone('UTC')))->modify('+'.(int)$campaign['ttl_seconds'].' seconds')->format('Y-m-d H:i:s');
                    $status='queued';$reason=null;
                    if(strtotime($notBefore.' UTC')>=strtotime($expires.' UTC')){$status='skipped';$reason='delivery_policy_exceeds_ttl';}
                    $dedupe=hash('sha256',$id.':'.$user['id'].':'.$channel.':'.$campaign['revision']);
                    $st=$this->db->prepare('INSERT IGNORE INTO engagement_campaign_recipients (campaign_id,user_id,channel,locale,status,payload_json,dedupe_key,next_attempt_at,expires_at,last_error_code) VALUES (?,?,?,?,?,?,?,?,?,?)');
                    $st->execute([$id,$user['id'],$channel,$locale,$status,json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR),$dedupe,$notBefore,$expires,$reason]);
                    if($st->rowCount()===1){if($status==='queued')$inserted++;else $skipped++;}
                }
            }
            $nextRevision=(int)$campaign['revision']+1;
            $nextStatus=$inserted>0?'scheduled':'completed';
            $this->db->prepare("UPDATE engagement_campaigns SET status=?,scheduled_at=?,confirmed_by=?,confirmed_at=UTC_TIMESTAMP(),revision=?,updated_at=UTC_TIMESTAMP() WHERE id=?")
                ->execute([$nextStatus,$scheduled,$actor['id'],$nextRevision,$id]);
            $this->event($id,'campaign_confirmed',(int)$actor['id'],['queued'=>$inserted,'skipped'=>$skipped]);
            $this->audit($actor,'engagement-campaign-confirmed',$id);
            $this->db->commit();
            return $this->detail($actor,$id);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function cancel(array $actor,int $id,int $expected): array
    {
        $this->admin($actor);
        $this->db->beginTransaction();
        try{
            $this->admin($actor);
            $q=$this->db->prepare('SELECT * FROM engagement_campaigns WHERE id=? FOR UPDATE');$q->execute([$id]);$campaign=$q->fetch();
            if(!$campaign)throw new ApiError('not_found',404);
            if(!in_array($campaign['status'],['draft','scheduled','processing'],true)||(int)$campaign['revision']!==$expected)throw new ApiError('conflict',409);
            if(($actor['role']??'')!=='owner'&&(int)$campaign['created_by']!==(int)$actor['id'])throw new ApiError('forbidden',403);
            $this->db->prepare("UPDATE engagement_campaigns SET status='cancelled',cancelled_at=UTC_TIMESTAMP(),revision=revision+1,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='cancelled',last_error_code='campaign_cancelled' WHERE campaign_id=? AND status IN ('queued','failed')")->execute([$id]);
            if($this->tableExists('email_outbox')){
                $this->db->prepare("UPDATE email_outbox o JOIN engagement_campaign_recipients r ON r.id=o.campaign_delivery_id SET o.status='cancelled',o.last_error_code='campaign_cancelled' WHERE r.campaign_id=? AND o.status='pending'")->execute([$id]);
                $this->db->prepare("UPDATE engagement_campaign_recipients r JOIN email_outbox o ON o.campaign_delivery_id=r.id SET r.status='cancelled',r.last_error_code='campaign_cancelled' WHERE r.campaign_id=? AND o.status='cancelled' AND r.status='processing'")->execute([$id]);
            }
            $this->event($id,'campaign_cancelled',(int)$actor['id'],[]);
            $this->audit($actor,'engagement-campaign-cancelled',$id);
            $this->db->commit();
            return $this->detail($actor,$id);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function recoverQueue(): array
    {
        $affected=[];
        $q=$this->db->query("SELECT DISTINCT campaign_id FROM engagement_campaign_recipients WHERE status IN ('queued','processing','failed') AND expires_at<=UTC_TIMESTAMP()");
        foreach($q->fetchAll(PDO::FETCH_COLUMN) as $campaignId)$affected[]=(int)$campaignId;
        $expired=$this->db->exec("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='ttl_expired',locked_at=NULL WHERE status IN ('queued','processing','failed') AND expires_at<=UTC_TIMESTAMP()");
        $stale=$this->db->exec("UPDATE engagement_campaign_recipients SET status='queued',last_error_code='stale_lock_recovered',locked_at=NULL,next_attempt_at=UTC_TIMESTAMP() WHERE status='processing' AND locked_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 MINUTE) AND expires_at>UTC_TIMESTAMP()");
        foreach(array_values(array_unique($affected)) as $campaignId)if($campaignId>0)$this->reconcileCampaignById($campaignId);
        return ['expired'=>(int)$expired,'recovered'=>(int)$stale,'campaigns_reconciled'=>count(array_unique($affected))];
    }

    public function claimPushBatch(int $limit=50): array
    {
        if(!$this->pushDeliveryReady())return [];
        $limit=max(1,min(200,$limit));$this->recoverQueue();$this->db->beginTransaction();
        try {
            $q=$this->db->query("SELECT r.*,c.min_frequency_hours,c.respect_quiet_hours FROM engagement_campaign_recipients r JOIN engagement_campaigns c ON c.id=r.campaign_id WHERE r.channel='push' AND r.status='queued' AND r.next_attempt_at<=UTC_TIMESTAMP() AND r.expires_at>UTC_TIMESTAMP() AND c.status IN ('scheduled','processing') ORDER BY r.next_attempt_at,r.id LIMIT ".$limit." FOR UPDATE");
            $rows=[];
            foreach($q->fetchAll() as $row){
                $user=$this->db->prepare('SELECT u.id,u.suspended_at,u.email_verified_at,p.push_campaign_opt_in,p.quiet_start,p.quiet_end,p.timezone FROM users u LEFT JOIN engagement_channel_preferences p ON p.user_id=u.id WHERE u.id=? FOR UPDATE');
                $user->execute([$row['user_id']]);$current=$user->fetch();
                if(!$current||$current['suspended_at']!==null||$current['email_verified_at']===null||!(bool)$current['push_campaign_opt_in']){
                    $this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='push_consent_unavailable' WHERE id=?")->execute([$row['id']]);
                    $this->reconcileCampaignById((int)$row['campaign_id']);continue;
                }
                $notBefore=$this->notBefore($this->frequencyNotBefore((int)$row['user_id'],'push',(int)$row['min_frequency_hours'],gmdate('Y-m-d H:i:s')),$row,$current);
                $active=$this->db->prepare("SELECT id FROM engagement_campaign_recipients WHERE user_id=? AND channel='push' AND status='processing' AND id<>? LIMIT 1");$active->execute([$row['user_id'],$row['id']]);
                if($active->fetchColumn())$notBefore=max($notBefore,gmdate('Y-m-d H:i:s',time()+60));
                if($notBefore>gmdate('Y-m-d H:i:s')){
                    $expired=$notBefore>=$row['expires_at'];
                    $this->db->prepare('UPDATE engagement_campaign_recipients SET status=?,next_attempt_at=?,last_error_code=? WHERE id=?')->execute([$expired?'skipped':'queued',$notBefore,$expired?'delivery_policy_exceeds_ttl':'delivery_policy_deferred',$row['id']]);
                    $this->reconcileCampaignById((int)$row['campaign_id']);continue;
                }
                $this->db->prepare("UPDATE engagement_campaign_recipients SET status='processing',attempt_count=attempt_count+1,locked_at=UTC_TIMESTAMP() WHERE id=? AND status='queued'")->execute([$row['id']]);
                $claim=$this->db->prepare('SELECT * FROM engagement_campaign_recipients WHERE id=?');$claim->execute([$row['id']]);$rows[]=$claim->fetch();
                $this->db->prepare("UPDATE engagement_campaigns SET status='processing' WHERE id=? AND status='scheduled'")->execute([$row['campaign_id']]);
            }
            $this->db->commit();return $rows;
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    /** Attempt count fences late results from a reclaimed worker. */
    public function completePush(int $deliveryId,int $attempt): void
    {
        $q=$this->db->prepare("UPDATE engagement_campaign_recipients SET status='sent',sent_at=UTC_TIMESTAMP(),locked_at=NULL,last_error_code=NULL WHERE id=? AND channel='push' AND status='processing' AND attempt_count=? AND expires_at>UTC_TIMESTAMP()");
        $q->execute([$deliveryId,$attempt]);if($q->rowCount()!==1)throw new ApiError('conflict',409);
        $this->reconcileCampaignForDelivery($deliveryId);
    }

    public function failPush(int $deliveryId,int $attempt,string $code,bool $terminal=false): void
    {
        $code=$this->safeReason($code);
        $this->db->beginTransaction();
        try {
            $q=$this->db->prepare("SELECT attempt_count,expires_at FROM engagement_campaign_recipients WHERE id=? AND channel='push' AND status='processing' AND attempt_count=? FOR UPDATE");
            $q->execute([$deliveryId,$attempt]);$row=$q->fetch();if(!$row)throw new ApiError('conflict',409);
            $terminal=$terminal||$attempt>=5||strtotime($row['expires_at'].' UTC')<=time()+60;
            $next=gmdate('Y-m-d H:i:s',time()+min(3600,60*(2**max(0,$attempt-1))));
            $this->db->prepare('UPDATE engagement_campaign_recipients SET status=?,last_error_code=?,locked_at=NULL,next_attempt_at=? WHERE id=?')->execute([$terminal?'failed':'queued',$code,$next,$deliveryId]);
            $this->reconcileCampaignForDelivery($deliveryId);$this->db->commit();
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    private function validateDraft(array $input): array
    {
        $allowed=['name','channel','audience_type','audience_ref_id','audience_locale','title_ar','body_ar','title_en','body_en','deep_link','image_url','scheduled_at','schedule_timezone','schedule_offset','ttl_seconds','min_frequency_hours','respect_quiet_hours'];
        if(array_diff(array_keys($input),$allowed))throw new ApiError('invalid_fields',422,['input'=>'unsupported']);
        foreach($input as $field=>$value)if(!in_array($field,['audience_ref_id','ttl_seconds','min_frequency_hours','respect_quiet_hours'],true)&&$value!==null&&!is_string($value))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        $name=$this->text($input['name']??'','name',160,2);
        $channel=(string)($input['channel']??'push');if(!in_array($channel,['push','email','push_email'],true))throw new ApiError('invalid_fields',422,['channel'=>'invalid']);
        $audience=(string)($input['audience_type']??'all_eligible');$audiences=['all_eligible','locale','scholar_followers','series_followers','lesson_reminders','test_recipient'];if(!in_array($audience,$audiences,true))throw new ApiError('invalid_fields',422,['audience_type'=>'invalid']);
        $ref=$input['audience_ref_id']??null;if($ref!==null&&(!is_int($ref)||$ref<1))throw new ApiError('invalid_fields',422,['audience_ref_id'=>'invalid']);
        if(in_array($audience,['scholar_followers','series_followers','lesson_reminders'],true)&&$ref===null)throw new ApiError('invalid_fields',422,['audience_ref_id'=>'required']);
        if(!in_array($audience,['scholar_followers','series_followers','lesson_reminders'],true))$ref=null;
        $locale=$input['audience_locale']??null;if($locale!==null&&!in_array($locale,['ar','en'],true))throw new ApiError('invalid_fields',422,['audience_locale'=>'invalid']);
        if($audience==='locale'&&$locale===null)throw new ApiError('invalid_fields',422,['audience_locale'=>'required']);
        $titleAr=$this->text($input['title_ar']??'','title_ar',180,2);$bodyAr=$this->text($input['body_ar']??'','body_ar',1000,2);
        $titleEn=$this->text($input['title_en']??'','title_en',180,2);$bodyEn=$this->text($input['body_en']??'','body_en',1000,2);
        $deep=$this->nullableUrlLike($input['deep_link']??null,'deep_link',500,true);$image=$this->nullableUrlLike($input['image_url']??null,'image_url',500,false);
        $zone=(string)($input['schedule_timezone']??'UTC');if(!in_array($zone,DateTimeZone::listIdentifiers(DateTimeZone::ALL_WITH_BC),true))throw new ApiError('invalid_fields',422,['schedule_timezone'=>'invalid']);
        $scheduled=null;if(isset($input['scheduled_at'])&&$input['scheduled_at']!==null&&$input['scheduled_at']!==''){$scheduled=\Athar\Infrastructure\LocalSchedule::utc($input['scheduled_at'],$zone,(string)($input['schedule_offset']??''));}
        $ttl=$input['ttl_seconds']??86400;if(!is_int($ttl)||$ttl<60||$ttl>604800)throw new ApiError('invalid_fields',422,['ttl_seconds'=>'invalid']);
        $freq=$input['min_frequency_hours']??6;if(!is_int($freq)||$freq<1||$freq>168)throw new ApiError('invalid_fields',422,['min_frequency_hours'=>'invalid']);
        $respect=$input['respect_quiet_hours']??true;if(!is_bool($respect))throw new ApiError('invalid_fields',422,['respect_quiet_hours'=>'invalid']);
        return ['name'=>$name,'channel'=>$channel,'audience_type'=>$audience,'audience_ref_id'=>$ref,'audience_locale'=>$locale,'title_ar'=>$titleAr,'body_ar'=>$bodyAr,'title_en'=>$titleEn,'body_en'=>$bodyEn,'deep_link'=>$deep,'image_url'=>$image,'scheduled_at'=>$scheduled,'schedule_timezone'=>$zone,'ttl_seconds'=>$ttl,'min_frequency_hours'=>$freq,'respect_quiet_hours'=>$respect];
    }

    private function resolveAudience(array $campaign,string $channel,int $actorId,bool $preview): array
    {
        if($channel==='push'&&!$this->pushDeliveryReady())return ['available'=>false,'reason'=>'push_delivery_not_ready','users'=>[]];
        if($channel==='email'&&!$this->emailDeliveryReady())return ['available'=>false,'reason'=>'email_transport_not_ready','users'=>[]];
        if($campaign['audience_type']==='test_recipient'&&$channel==='email')return ['available'=>false,'reason'=>'email_test_uses_authorized_smtp_recipient','users'=>[]];
        $where=["u.suspended_at IS NULL","u.email_verified_at IS NOT NULL"];$args=[];
        if($channel==='push')$where[]='COALESCE(p.push_campaign_opt_in,0)=1';
        else{$where[]='u.email_marketing_opt_in=1';$where[]='COALESCE(p.email_campaign_opt_in,u.email_marketing_opt_in)=1';}
        $localeExpression="CASE WHEN p.locale_is_explicit=1 THEN p.locale WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='en' THEN 'en' WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='ar' THEN 'ar' ELSE COALESCE(p.locale,'ar') END";
        if($campaign['audience_locale']!==null){$where[]=$localeExpression.'=?';$args[]=$campaign['audience_locale'];}
        if($campaign['audience_type']==='test_recipient'){$where[]='u.id=?';$args[]=$actorId;}
        $join='';
        if($campaign['audience_type']==='scholar_followers'){
            if(!$this->tableExists('scholar_follows'))return ['available'=>false,'reason'=>'scholar_follows_not_ready','users'=>[]];
            $join=' JOIN scholar_follows f ON f.user_id=u.id AND f.scholar_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }elseif($campaign['audience_type']==='series_followers'){
            if(!$this->tableExists('series_follows'))return ['available'=>false,'reason'=>'series_follows_not_ready','users'=>[]];
            $join=' JOIN series_follows f ON f.user_id=u.id AND f.series_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }elseif($campaign['audience_type']==='lesson_reminders'){
            if(!$this->tableExists('lesson_reminder_subscriptions'))return ['available'=>false,'reason'=>'lesson_reminders_not_ready','users'=>[]];
            $join=' JOIN lesson_reminder_subscriptions f ON f.user_id=u.id AND f.enabled=1 AND f.lesson_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }
        $sql='SELECT DISTINCT u.id,'.$localeExpression.' locale,p.quiet_start,p.quiet_end,p.timezone FROM users u LEFT JOIN engagement_channel_preferences p ON p.user_id=u.id'.$join.' WHERE '.implode(' AND ',$where).' ORDER BY u.id';
        // Preview and confirmation use the same complete consent-safe audience.
        $q=$this->db->prepare($sql);$q->execute($args);return ['available'=>true,'reason'=>null,'users'=>$q->fetchAll()];
    }

    public function deliveryNotBefore(int $user,string $channel,array $campaign,string $scheduled): string
    {
        $q=$this->db->prepare('SELECT quiet_start,quiet_end,timezone FROM engagement_channel_preferences WHERE user_id=?');$q->execute([$user]);
        $preferences=$q->fetch()?:['quiet_start'=>null,'quiet_end'=>null,'timezone'=>null];
        return $this->notBefore($this->frequencyNotBefore($user,$channel,(int)$campaign['min_frequency_hours'],$scheduled),$campaign,$preferences);
    }

    private function notBefore(string $scheduled,array $campaign,array $user): string
    {
        if(!(bool)$campaign['respect_quiet_hours'])return $scheduled;
        if($user['quiet_start']===null&&$user['quiet_end']===null)return $scheduled;
        if(empty($user['quiet_start'])||empty($user['quiet_end'])||empty($user['timezone'])||$user['quiet_start']===$user['quiet_end'])return gmdate('Y-m-d H:i:s',strtotime($scheduled.' UTC')+604801);
        try{$zone=new DateTimeZone((string)$user['timezone']);$utc=new DateTimeImmutable($scheduled,new DateTimeZone('UTC'));$local=$utc->setTimezone($zone);}
        catch(Throwable $e){return gmdate('Y-m-d H:i:s',strtotime($scheduled.' UTC')+604801);}
        $start=(string)$user['quiet_start'];$end=(string)$user['quiet_end'];$day=$local->format('Y-m-d');
        $qs=new DateTimeImmutable($day.' '.$start,$zone);$qe=new DateTimeImmutable($day.' '.$end,$zone);
        if($qe<=$qs){
            if($local<$qe)$qs=$qs->modify('-1 day');else $qe=$qe->modify('+1 day');
        }
        if($local>=$qs&&$local<$qe)return $qe->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
        return $scheduled;
    }

    private function frequencyNotBefore(int $user,string $channel,int $hours,string $scheduled): string
    {
        $q=$this->db->prepare("SELECT MAX(sent_at) FROM engagement_campaign_recipients WHERE user_id=? AND channel=? AND status='sent'");
        $q->execute([$user,$channel]);$last=$q->fetchColumn();
        if(!is_string($last)||$last==='')return $scheduled;
        $allowed=gmdate('Y-m-d H:i:s',strtotime($last.' UTC')+($hours*3600));
        return strtotime($allowed.' UTC')>strtotime($scheduled.' UTC')?$allowed:$scheduled;
    }

    private function reconcileCampaignById(int $id): void
    {
        $counts=$this->deliveryCounts($id);
        $remaining=$counts['queued']+$counts['processing'];
        if($remaining>0)$status='processing';
        elseif($counts['failed']>0)$status='partially_failed';
        else $status='completed';
        $this->db->prepare("UPDATE engagement_campaigns SET status=IF(status='cancelled','cancelled',?),updated_at=UTC_TIMESTAMP() WHERE id=?")
            ->execute([$status,$id]);
    }

    private function reconcileCampaignForDelivery(int $deliveryId): void
    {
        $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([$deliveryId]);$id=(int)$q->fetchColumn();if($id<1)return;
        $this->reconcileCampaignById($id);
    }

    private function deliveryAnalytics(int $id): array
    {
        $channels=[];
        $q=$this->db->prepare('SELECT channel,status,COUNT(*) count FROM engagement_campaign_recipients WHERE campaign_id=? GROUP BY channel,status ORDER BY channel,status');
        $q->execute([$id]);
        foreach($q->fetchAll() as $row)$channels[(string)$row['channel']][(string)$row['status']]=(int)$row['count'];
        $reasons=[];
        $q=$this->db->prepare("SELECT last_error_code,COUNT(*) count FROM engagement_campaign_recipients WHERE campaign_id=? AND last_error_code IS NOT NULL AND last_error_code<>'' GROUP BY last_error_code ORDER BY count DESC,last_error_code LIMIT 20");
        $q->execute([$id]);
        foreach($q->fetchAll() as $row){$code=$this->safeReason((string)$row['last_error_code']);$reasons[$code]=($reasons[$code]??0)+(int)$row['count'];}
        $reasons=array_map(static fn($code,$count)=>['code'=>$code,'count'=>$count],array_keys($reasons),array_values($reasons));
        return ['channels'=>$channels,'reasons'=>$reasons];
    }

    private function history(int $id): array
    {
        $q=$this->db->prepare('SELECT event_type,summary_json,created_at FROM engagement_campaign_events WHERE campaign_id=? ORDER BY id DESC LIMIT 20');
        $q->execute([$id]);$events=[];
        foreach($q->fetchAll() as $row){
            $summary=[];
            if(!empty($row['summary_json'])){try{$decoded=json_decode((string)$row['summary_json'],true,16,JSON_THROW_ON_ERROR);if(is_array($decoded))foreach(['queued','skipped','revision'] as $key)if(isset($decoded[$key])&&is_int($decoded[$key])&&$decoded[$key]>=0)$summary[$key]=$decoded[$key];}catch(Throwable $e){}}
            // Event summaries are aggregate operational facts only; never recipient payloads.
            $event=in_array($row['event_type'],['draft_created','draft_updated','campaign_confirmed','campaign_cancelled'],true)?$row['event_type']:'operational_event';
            $events[]=['event_type'=>$event,'summary'=>$summary,'created_at'=>(string)$row['created_at']];
        }
        return $events;
    }

    private function deliveryCounts(int $id): array
    {
        $result=['queued'=>0,'processing'=>0,'sent'=>0,'failed'=>0,'skipped'=>0,'cancelled'=>0,'total'=>0];
        $q=$this->db->prepare('SELECT status,COUNT(*) c FROM engagement_campaign_recipients WHERE campaign_id=? GROUP BY status');$q->execute([$id]);
        foreach($q->fetchAll() as $row){$result[$row['status']]=(int)$row['c'];$result['total']+=(int)$row['c'];}
        return $result;
    }

    private function safeReason(string $code): string
    {
        return in_array($code,['provider_unavailable','provider_rejected','invalid_token','rate_limited','timeout','transport_error','ttl_expired','stale_lock_recovered','campaign_cancelled','push_consent_unavailable','email_consent_unavailable','email_opt_out','push_opt_out','delivery_policy_exceeds_ttl','delivery_policy_deferred','email_outbox_expired','delivery_outcome_unknown','restored_requires_reconfirmation'],true)?$code:'delivery_failed';
    }

    private function decorate(array $row): array
    {
        foreach(['id','audience_ref_id','ttl_seconds','min_frequency_hours','revision','created_by','confirmed_by'] as $field)if(array_key_exists($field,$row))$row[$field]=$row[$field]===null?null:(int)$row[$field];
        $row['respect_quiet_hours']=(bool)$row['respect_quiet_hours'];return $row;
    }
    private function channels(string $channel): array{return $channel==='push_email'?['push','email']:[$channel];}
    private function payload(array $campaign,string $locale): array{return ['title'=>$locale==='en'?$campaign['title_en']:$campaign['title_ar'],'body'=>$locale==='en'?$campaign['body_en']:$campaign['body_ar'],'deep_link'=>$campaign['deep_link'],'image_url'=>$campaign['image_url'],'ttl_seconds'=>(int)$campaign['ttl_seconds'],'campaign_id'=>(int)$campaign['id']];}
    private function pushDeliveryReady(): bool
    {
        if($this->readiness!==null)return (bool)($this->readiness)('push');
        // WP15 owns push_devices. A registry alone is not a campaign dispatcher.
        return $this->tableExists('push_devices') && class_exists(CampaignPushDispatcher::class)
            && CampaignPushDispatcher::ready($this->db);
    }

    private function emailDeliveryReady(): bool
    {
        if($this->readiness!==null)return (bool)($this->readiness)('email');
        if(!$this->tableExists('email_outbox')||!function_exists('open_platform_secret')||!function_exists('platform_email_base_url'))return false;
        try {
            $base=\platform_email_base_url();
            return $this->setting('smtp_host')==='smtp.gmail.com' && $this->setting('smtp_port')==='587'
                && $this->setting('smtp_security')==='tls'
                && filter_var($this->setting('smtp_username'),FILTER_VALIDATE_EMAIL)!==false
                && $this->setting('smtp_from_email')===$this->setting('smtp_username')
                && \open_platform_secret($this->setting('smtp_password_cipher'))!==''
                && \Athar\Infrastructure\HttpsUrl::valid($base);
        }catch(Throwable $e){return false;}
    }

    private function setting(string $key): string
    {
        $q=$this->db->prepare('SELECT setting_value FROM platform_settings WHERE setting_key=? LIMIT 1');
        $q->execute([$key]);$value=$q->fetchColumn();
        return is_string($value)?trim($value):'';
    }

    private function tableExists(string $table): bool{$q=$this->db->prepare('SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? LIMIT 1');$q->execute([$table]);return (bool)$q->fetchColumn();}
    private function nullableUrlLike($value,string $field,int $max,bool $allowDeepLink): ?string
    {
        if($value===null||$value==='')return null;
        if(!is_string($value))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        $value=trim($value);if($value==='')return null;
        if(strlen($value)>$max||preg_match('/[\\\\\x00-\x20\x7f]/',$value))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        if($allowDeepLink){
            if(preg_match('#^athar://(?:lesson|book|scholar|series)/[1-9][0-9]*$#D',$value))return $value;
            if(preg_match('#^/(?:lessons|books|scholars|series|upcoming|watch)(?:\?[a-z_]+=[1-9][0-9]*)?$#D',$value))return $value;
        }
        if(\Athar\Infrastructure\HttpsUrl::valid($value))return $value;
        throw new ApiError('invalid_fields',422,[$field=>'invalid']);
    }
    private function text($value,string $field,int $max,int $min=0): string
    {
        if(!is_string($value))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        $value=trim($value);$len=mb_strlen($value,'UTF-8');
        if($len<$min||$len>$max||preg_match('/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/',$value))throw new ApiError('invalid_fields',422,[$field=>'invalid']);
        return $value;
    }
    private function event(int $campaign,string $type,?int $actor,array $summary): void{$this->db->prepare('INSERT INTO engagement_campaign_events(campaign_id,event_type,actor_id,summary_json,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())')->execute([$campaign,$type,$actor,$summary?json_encode($summary,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR):null]);}
    private function audit(array $actor,string $action,int $id): void{$this->db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action,'engagement-campaign',(string)$id]);}
    private function admin(array $actor): void
    {
        $q=$this->db->prepare('SELECT role,email_verified_at,suspended_at,session_version FROM users WHERE id=?'.($this->db->inTransaction()?' LOCK IN SHARE MODE':''));
        $q->execute([(int)($actor['id']??0)]);$current=$q->fetch();
        if(!$current||!in_array($current['role'],['admin','owner'],true)||$current['role']!==($actor['role']??'')||empty($current['email_verified_at'])||$current['suspended_at']!==null||((isset($actor['session_version']))&&(int)$actor['session_version']!==(int)$current['session_version']))throw new ApiError('forbidden',403);
    }
    private function owner(array $actor): void{$this->admin($actor);if(($actor['role']??'')!=='owner')throw new ApiError('forbidden',403);}
}
