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
    public function __construct(private PDO $db) {}

    public function createDraft(array $actor,array $input): array
    {
        $this->admin($actor);
        $data=$this->validateDraft($input);
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
        return $this->detail($actor,$id);
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
        return $this->decorate($row)+['delivery_counts'=>$this->deliveryCounts($id)];
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
            $q=$this->db->prepare('SELECT * FROM engagement_campaigns WHERE id=? FOR UPDATE');$q->execute([$id]);$campaign=$q->fetch();
            if(!$campaign)throw new ApiError('not_found',404);
            if($campaign['status']!=='draft'||(int)$campaign['revision']!==$expected)throw new ApiError('conflict',409,['status'=>$campaign['status'],'current_revision'=>(int)$campaign['revision']]);
            $scheduled=$campaign['scheduled_at']?:gmdate('Y-m-d H:i:s');
            if(strtotime($scheduled.' UTC')<time()-60||strtotime($scheduled.' UTC')>time()+366*86400)throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);
            $channels=$this->channels((string)$campaign['channel']);
            $inserted=0;$skipped=0;$dependencyProblems=[];
            foreach($channels as $channel){
                $resolved=$this->resolveAudience($campaign,$channel,(int)$actor['id'],false);
                if(!$resolved['available']){$dependencyProblems[$channel]=$resolved['reason'];continue;}
                foreach($resolved['users'] as $user){
                    $locale=$user['locale']==='en'?'en':'ar';
                    $payload=$this->payload($campaign,$locale);
                    $notBefore=$this->notBefore($scheduled,$campaign,$user);
                    $expires=(new DateTimeImmutable($scheduled,new DateTimeZone('UTC')))->modify('+'.(int)$campaign['ttl_seconds'].' seconds')->format('Y-m-d H:i:s');
                    $recent=$this->recentlySent((int)$user['id'],$channel,(int)$campaign['min_frequency_hours']);
                    $status=$recent?'skipped':'queued';$reason=$recent?'frequency_limit':null;
                    if(strtotime($notBefore.' UTC')>=strtotime($expires.' UTC')){$status='skipped';$reason='quiet_hours_exceeds_ttl';}
                    $dedupe=hash('sha256',$id.':'.$user['id'].':'.$channel.':'.$campaign['revision']);
                    $st=$this->db->prepare('INSERT IGNORE INTO engagement_campaign_recipients (campaign_id,user_id,channel,locale,status,payload_json,dedupe_key,next_attempt_at,expires_at,last_error_code) VALUES (?,?,?,?,?,?,?,?,?,?)');
                    $st->execute([$id,$user['id'],$channel,$locale,$status,json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR),$dedupe,$notBefore,$expires,$reason]);
                    if($st->rowCount()===1){if($status==='queued')$inserted++;else $skipped++;}
                }
            }
            if($dependencyProblems&&$inserted===0)throw new ApiError('conflict',409,['dependencies'=>$dependencyProblems]);
            $nextRevision=(int)$campaign['revision']+1;
            $this->db->prepare("UPDATE engagement_campaigns SET status='scheduled',scheduled_at=?,confirmed_by=?,confirmed_at=UTC_TIMESTAMP(),revision=?,updated_at=UTC_TIMESTAMP() WHERE id=?")
                ->execute([$scheduled,$actor['id'],$nextRevision,$id]);
            $this->event($id,'campaign_confirmed',(int)$actor['id'],['queued'=>$inserted,'skipped'=>$skipped,'dependencies'=>$dependencyProblems]);
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
            $q=$this->db->prepare('SELECT * FROM engagement_campaigns WHERE id=? FOR UPDATE');$q->execute([$id]);$campaign=$q->fetch();
            if(!$campaign)throw new ApiError('not_found',404);
            if(!in_array($campaign['status'],['draft','scheduled'],true)||(int)$campaign['revision']!==$expected)throw new ApiError('conflict',409);
            if(($actor['role']??'')!=='owner'&&(int)$campaign['created_by']!==(int)$actor['id'])throw new ApiError('forbidden',403);
            $this->db->prepare("UPDATE engagement_campaigns SET status='cancelled',cancelled_at=UTC_TIMESTAMP(),revision=revision+1,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='cancelled',last_error_code='campaign_cancelled' WHERE campaign_id=? AND status IN ('queued','failed')")->execute([$id]);
            $this->event($id,'campaign_cancelled',(int)$actor['id'],[]);
            $this->audit($actor,'engagement-campaign-cancelled',$id);
            $this->db->commit();
            return $this->detail($actor,$id);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function recoverQueue(): array
    {
        $expired=$this->db->exec("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='ttl_expired',locked_at=NULL WHERE status IN ('queued','processing','failed') AND expires_at<=UTC_TIMESTAMP()");
        $stale=$this->db->exec("UPDATE engagement_campaign_recipients SET status='queued',last_error_code='stale_lock_recovered',locked_at=NULL,next_attempt_at=UTC_TIMESTAMP() WHERE status='processing' AND locked_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 MINUTE) AND expires_at>UTC_TIMESTAMP()");
        return ['expired'=>(int)$expired,'recovered'=>(int)$stale];
    }

    public function claimPushBatch(int $limit=50): array
    {
        $limit=max(1,min(200,$limit));$this->recoverQueue();$this->db->beginTransaction();
        try{
            $q=$this->db->query("SELECT r.id FROM engagement_campaign_recipients r JOIN engagement_campaigns c ON c.id=r.campaign_id WHERE r.channel='push' AND r.status='queued' AND r.next_attempt_at<=UTC_TIMESTAMP() AND r.expires_at>UTC_TIMESTAMP() AND c.status IN ('scheduled','processing') ORDER BY r.next_attempt_at,r.id LIMIT ".$limit." FOR UPDATE");
            $ids=array_map('intval',$q->fetchAll(PDO::FETCH_COLUMN));
            if($ids){
                $in=implode(',',array_fill(0,count($ids),'?'));
                $u=$this->db->prepare("UPDATE engagement_campaign_recipients SET status='processing',attempt_count=attempt_count+1,locked_at=UTC_TIMESTAMP() WHERE id IN ($in) AND status='queued'");
                $u->execute($ids);
                $q=$this->db->prepare("SELECT * FROM engagement_campaign_recipients WHERE id IN ($in) ORDER BY id");$q->execute($ids);$rows=$q->fetchAll();
            }else $rows=[];
            $this->db->commit();return $rows;
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function completePush(int $deliveryId): void
    {
        $q=$this->db->prepare("UPDATE engagement_campaign_recipients SET status='sent',sent_at=UTC_TIMESTAMP(),locked_at=NULL,last_error_code=NULL WHERE id=? AND channel='push' AND status='processing'");
        $q->execute([$deliveryId]);if($q->rowCount()!==1)throw new ApiError('conflict',409);
        $this->reconcileCampaignForDelivery($deliveryId);
    }

    public function failPush(int $deliveryId,string $code,bool $terminal=false): void
    {
        $code=preg_replace('/[^a-z0-9_.-]/i','_',substr($code,0,80))?:'provider_error';
        $q=$this->db->prepare("SELECT attempt_count,expires_at FROM engagement_campaign_recipients WHERE id=? AND channel='push' AND status='processing'");
        $q->execute([$deliveryId]);$row=$q->fetch();if(!$row)throw new ApiError('conflict',409);
        $terminal=$terminal||(int)$row['attempt_count']>=5||strtotime($row['expires_at'].' UTC')<=time()+60;
        if($terminal)$this->db->prepare("UPDATE engagement_campaign_recipients SET status='failed',last_error_code=?,locked_at=NULL WHERE id=?")->execute([$code,$deliveryId]);
        else{
            $delay=min(3600,60*(2**max(0,(int)$row['attempt_count']-1)));
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='queued',last_error_code=?,locked_at=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? SECOND) WHERE id=?")->execute([$code,$delay,$deliveryId]);
        }
        $this->reconcileCampaignForDelivery($deliveryId);
    }

    private function validateDraft(array $input): array
    {
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
        $zone=(string)($input['schedule_timezone']??'UTC');try{new DateTimeZone($zone);}catch(\Throwable $e){throw new ApiError('invalid_fields',422,['schedule_timezone'=>'invalid']);}
        $scheduled=null;if(isset($input['scheduled_at'])&&$input['scheduled_at']!==null&&$input['scheduled_at']!==''){$scheduled=$this->instant((string)$input['scheduled_at'],$zone);}
        $ttl=$input['ttl_seconds']??86400;if(!is_int($ttl)||$ttl<60||$ttl>604800)throw new ApiError('invalid_fields',422,['ttl_seconds'=>'invalid']);
        $freq=$input['min_frequency_hours']??6;if(!is_int($freq)||$freq<1||$freq>168)throw new ApiError('invalid_fields',422,['min_frequency_hours'=>'invalid']);
        $respect=$input['respect_quiet_hours']??true;if(!is_bool($respect))throw new ApiError('invalid_fields',422,['respect_quiet_hours'=>'invalid']);
        return ['name'=>$name,'channel'=>$channel,'audience_type'=>$audience,'audience_ref_id'=>$ref,'audience_locale'=>$locale,'title_ar'=>$titleAr,'body_ar'=>$bodyAr,'title_en'=>$titleEn,'body_en'=>$bodyEn,'deep_link'=>$deep,'image_url'=>$image,'scheduled_at'=>$scheduled,'schedule_timezone'=>$zone,'ttl_seconds'=>$ttl,'min_frequency_hours'=>$freq,'respect_quiet_hours'=>$respect];
    }

    private function resolveAudience(array $campaign,string $channel,int $actorId,bool $preview): array
    {
        if($channel==='push'&&!$this->tableExists('notification_devices'))return ['available'=>false,'reason'=>'push_device_delivery_not_ready','users'=>[]];
        if($campaign['audience_type']==='test_recipient'&&$channel==='email')return ['available'=>false,'reason'=>'email_test_uses_authorized_smtp_recipient','users'=>[]];
        $where=["u.suspended_at IS NULL","u.email_verified_at IS NOT NULL"];$args=[];
        if($channel==='push')$where[]='p.push_campaign_opt_in=1';
        else{$where[]='p.email_campaign_opt_in=1';$where[]='u.email_marketing_opt_in=1';}
        if($campaign['audience_locale']!==null){$where[]='p.locale=?';$args[]=$campaign['audience_locale'];}
        if($campaign['audience_type']==='test_recipient'){$where[]='u.id=?';$args[]=$actorId;}
        $join='';
        if($campaign['audience_type']==='scholar_followers'){
            if(!$this->tableExists('scholar_follows'))return ['available'=>false,'reason'=>'scholar_follows_not_ready','users'=>[]];
            $join=' JOIN scholar_follows f ON f.user_id=u.id AND f.scholar_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }elseif($campaign['audience_type']==='series_followers'){
            if(!$this->tableExists('series_follows'))return ['available'=>false,'reason'=>'series_follows_not_ready','users'=>[]];
            $join=' JOIN series_follows f ON f.user_id=u.id AND f.series_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }elseif($campaign['audience_type']==='lesson_reminders'){
            if(!$this->tableExists('lesson_reminders'))return ['available'=>false,'reason'=>'lesson_reminders_not_ready','users'=>[]];
            $join=' JOIN lesson_reminders f ON f.user_id=u.id AND f.lesson_id=?';array_unshift($args,(int)$campaign['audience_ref_id']);
        }
        $sql='SELECT DISTINCT u.id,p.locale,p.quiet_start,p.quiet_end,p.timezone FROM users u JOIN engagement_channel_preferences p ON p.user_id=u.id'.$join.' WHERE '.implode(' AND ',$where).' ORDER BY u.id';
        if($preview)$sql.=' LIMIT 100000';
        $q=$this->db->prepare($sql);$q->execute($args);return ['available'=>true,'reason'=>null,'users'=>$q->fetchAll()];
    }

    private function notBefore(string $scheduled,array $campaign,array $user): string
    {
        if(!(bool)$campaign['respect_quiet_hours']||empty($user['quiet_start'])||empty($user['quiet_end'])||empty($user['timezone']))return $scheduled;
        try{$zone=new DateTimeZone((string)$user['timezone']);$utc=new DateTimeImmutable($scheduled,new DateTimeZone('UTC'));$local=$utc->setTimezone($zone);}
        catch(Throwable $e){return $scheduled;}
        $start=(string)$user['quiet_start'];$end=(string)$user['quiet_end'];$day=$local->format('Y-m-d');
        $qs=new DateTimeImmutable($day.' '.$start,$zone);$qe=new DateTimeImmutable($day.' '.$end,$zone);
        if($qe<=$qs){
            if($local<$qe)$qs=$qs->modify('-1 day');else $qe=$qe->modify('+1 day');
        }
        if($local>=$qs&&$local<$qe)return $qe->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
        return $scheduled;
    }

    private function recentlySent(int $user,string $channel,int $hours): bool
    {
        $q=$this->db->prepare("SELECT 1 FROM engagement_campaign_recipients WHERE user_id=? AND channel=? AND status='sent' AND sent_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL ? HOUR) LIMIT 1");
        $q->execute([$user,$channel,$hours]);return (bool)$q->fetchColumn();
    }

    private function reconcileCampaignForDelivery(int $deliveryId): void
    {
        $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([$deliveryId]);$id=(int)$q->fetchColumn();if($id<1)return;
        $counts=$this->deliveryCounts($id);$remaining=$counts['queued']+$counts['processing'];
        if($remaining>0)$status='processing';elseif($counts['failed']>0)$status='partially_failed';else $status='completed';
        $this->db->prepare("UPDATE engagement_campaigns SET status=IF(status='cancelled','cancelled',?),updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$status,$id]);
    }

    private function deliveryCounts(int $id): array
    {
        $result=['queued'=>0,'processing'=>0,'sent'=>0,'failed'=>0,'skipped'=>0,'cancelled'=>0,'total'=>0];
        $q=$this->db->prepare('SELECT status,COUNT(*) c FROM engagement_campaign_recipients WHERE campaign_id=? GROUP BY status');$q->execute([$id]);
        foreach($q->fetchAll() as $row){$result[$row['status']]=(int)$row['c'];$result['total']+=(int)$row['c'];}
        return $result;
    }

    private function decorate(array $row): array
    {
        foreach(['id','audience_ref_id','ttl_seconds','min_frequency_hours','revision','created_by','confirmed_by'] as $field)if(array_key_exists($field,$row))$row[$field]=$row[$field]===null?null:(int)$row[$field];
        $row['respect_quiet_hours']=(bool)$row['respect_quiet_hours'];return $row;
    }
    private function channels(string $channel): array{return $channel==='push_email'?['push','email']:[$channel];}
    private function payload(array $campaign,string $locale): array{return ['title'=>$locale==='en'?$campaign['title_en']:$campaign['title_ar'],'body'=>$locale==='en'?$campaign['body_en']:$campaign['body_ar'],'deep_link'=>$campaign['deep_link'],'image_url'=>$campaign['image_url'],'ttl_seconds'=>(int)$campaign['ttl_seconds'],'campaign_id'=>(int)$campaign['id']];}
    private function tableExists(string $table): bool{$q=$this->db->prepare('SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? LIMIT 1');$q->execute([$table]);return (bool)$q->fetchColumn();}
    private function instant(string $local,string $zone): string{try{$d=new DateTimeImmutable($local,new DateTimeZone($zone));}catch(Throwable $e){throw new ApiError('invalid_fields',422,['scheduled_at'=>'invalid']);}return $d->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');}
    private function nullableUrlLike($value,string $field,int $max,bool $allowDeepLink): ?string{$value=trim((string)$value);if($value==='')return null;if(strlen($value)>$max)throw new ApiError('invalid_fields',422,[$field=>'too_long']);if($allowDeepLink&&preg_match('#^athar://[A-Za-z0-9/_?=&.-]+$#',$value))return $value;if($allowDeepLink&&str_starts_with($value,'/')&&!str_starts_with($value,'//'))return $value;if(filter_var($value,FILTER_VALIDATE_URL)&&strtolower((string)parse_url($value,PHP_URL_SCHEME))==='https')return $value;throw new ApiError('invalid_fields',422,[$field=>'invalid']);}
    private function text($value,string $field,int $max,int $min=0): string{$value=trim((string)$value);$len=mb_strlen($value,'UTF-8');if($len<$min||$len>$max)throw new ApiError('invalid_fields',422,[$field=>'invalid']);return $value;}
    private function event(int $campaign,string $type,?int $actor,array $summary): void{$this->db->prepare('INSERT INTO engagement_campaign_events(campaign_id,event_type,actor_id,summary_json,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())')->execute([$campaign,$type,$actor,$summary?json_encode($summary,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR):null]);}
    private function audit(array $actor,string $action,int $id): void{$this->db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')->execute([$actor['id'],$action,'engagement-campaign',(string)$id]);}
    private function admin(array $actor): void{if(!in_array($actor['role']??'', ['admin','owner'],true)||empty($actor['email_verified_at'])||!empty($actor['suspended_at']))throw new ApiError('forbidden',403);}
    private function owner(array $actor): void{$this->admin($actor);if(($actor['role']??'')!=='owner')throw new ApiError('forbidden',403);}
}
