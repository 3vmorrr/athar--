<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use PDO;
use Throwable;

final class EmailOutboxService
{
    private EmailPayloadCipher $cipher;
    private array $leases=[];
    public function __construct(private PDO $db,?string $payloadKey=null) {$this->cipher=new EmailPayloadCipher($db,$payloadKey);}

    public function enqueueTransactional(
        int $userId,
        string $subject,
        string $plainBody,
        string $idempotencyKey,
        string $locale='ar',
        int $ttlSeconds=600,
        ?string $htmlBody=null,
        string $htmlFooter='',
        ?int $authTokenId=null,
        ?string $purpose=null
    ): bool {
        if($userId<1||!in_array($locale,['ar','en'],true)||mb_strlen($subject,'UTF-8')<2||mb_strlen($subject,'UTF-8')>180||$plainBody===''||$ttlSeconds<60||$ttlSeconds>86400)throw new ApiError('invalid_fields',422);
        $dedupe=hash('sha256','transactional:'.$userId.':'.$idempotencyKey);
        if($idempotencyKey===''||strlen($idempotencyKey)>200)throw new ApiError('invalid_fields',422);
        $q=$this->db->prepare('SELECT email FROM users WHERE id=?');$q->execute([$userId]);$recipient=$q->fetchColumn();
        if(!is_string($recipient)||!filter_var($recipient,FILTER_VALIDATE_EMAIL))throw new ApiError('invalid_fields',422);
        $payload=['subject'=>$subject,'plain_body'=>$plainBody,'html_body'=>$htmlBody,'html_footer'=>$htmlFooter,'recipient'=>$recipient,'auth_token_id'=>$authTokenId,'purpose'=>$purpose];
        $plainBody=$this->cipher->seal($payload,$dedupe);$subject='Athar message';$htmlBody=null;$htmlFooter='';
        $expiresAt=gmdate('Y-m-d H:i:s',time()+$ttlSeconds);
        $q=$this->db->prepare("INSERT IGNORE INTO email_outbox
            (category,priority,user_id,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
            VALUES ('transactional',10,?,?,?,?,?,?, 'pending',?,0,5,UTC_TIMESTAMP(),?,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        $q->execute([$userId,$locale,$subject,$plainBody,$htmlBody,$htmlFooter,$dedupe,$expiresAt]);
        return $this->acceptedInsert($q->rowCount(),$dedupe,$payload);
    }

    public function enqueueAuthorizedTest(
        string $recipientCipher,
        string $subject,
        string $plainBody,
        string $locale,
        string $idempotencyKey,
        ?string $htmlBody=null
    ): bool {
        if($recipientCipher===''||!in_array($locale,['ar','en'],true)||mb_strlen($subject,'UTF-8')<2||mb_strlen($subject,'UTF-8')>180||trim($plainBody)==='')throw new ApiError('invalid_fields',422);
        $dedupe=hash('sha256','authorized-test:'.$idempotencyKey);
        $footer=$locale==='en'
            ? 'Authorized Athar development preview. This is not a subscription delivery.'
            : 'معاينة تطوير مصرح بها من أثر. هذه ليست رسالة اشتراك.';
        $recipient=\open_platform_secret($recipientCipher);
        if(!filter_var($recipient,FILTER_VALIDATE_EMAIL))throw new ApiError('invalid_fields',422);
        $payload=['subject'=>$subject,'plain_body'=>$plainBody,'html_body'=>$htmlBody,'html_footer'=>$footer,'recipient'=>$recipient];
        $plainBody=$this->cipher->seal($payload,$dedupe);$subject='Athar message';$htmlBody=null;$footer='';$recipientCipher=null;
        $q=$this->db->prepare("INSERT IGNORE INTO email_outbox
            (category,priority,user_id,campaign_delivery_id,recipient_email_cipher,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
            VALUES ('campaign',30,NULL,NULL,?,?,?,?,?,?, 'pending',?,0,3,UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR),UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        $q->execute([$recipientCipher,$locale,$subject,$plainBody,$htmlBody,$footer,$dedupe]);
        return $this->acceptedInsert($q->rowCount(),$dedupe,$payload);
    }

    public function enqueueDueCampaignEmails(int $limit=250): array
    {
        $limit=max(1,min(1000,$limit));
        $this->recover();
        $q=$this->db->query("SELECT r.id,r.user_id,r.locale,r.payload_json,r.dedupe_key,r.expires_at,c.status campaign_status
            FROM engagement_campaign_recipients r
            JOIN engagement_campaigns c ON c.id=r.campaign_id
            WHERE r.channel='email' AND r.status='queued' AND r.next_attempt_at<=UTC_TIMESTAMP() AND r.expires_at>UTC_TIMESTAMP()
              AND c.status IN ('scheduled','processing')
            ORDER BY r.next_attempt_at,r.id LIMIT ".$limit);
        $queued=0;$skipped=0;
        foreach($q->fetchAll() as $delivery){
            $this->db->beginTransaction();
            try{
                $lock=$this->db->prepare("SELECT r.*,c.status campaign_status FROM engagement_campaign_recipients r JOIN engagement_campaigns c ON c.id=r.campaign_id WHERE r.id=? FOR UPDATE");
                $lock->execute([$delivery['id']]);$row=$lock->fetch();
                if(!$row||$row['channel']!=='email'||$row['status']!=='queued'||!in_array($row['campaign_status'],['scheduled','processing'],true)){ $this->db->commit();continue; }
                $user=$this->emailCampaignUser((int)$row['user_id']);
                if(!$user){
                    $this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code='email_consent_unavailable',updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$row['id']]);
                    $skipped++;$this->db->commit();$this->reconcileCampaign((int)$row['campaign_id']);continue;
                }
                $payload=json_decode((string)$row['payload_json'],true,32,JSON_THROW_ON_ERROR);
                $subject=trim((string)($payload['title']??''));$body=trim((string)($payload['body']??''));
                if($subject===''||$body==='')throw new \RuntimeException('Campaign email payload is incomplete.');
                $locale=$row['locale']==='en'?'en':'ar';
                $unsubscribe=\email_campaign_unsubscribe_url((int)$row['user_id']);
                $cta=$this->emailCta((string)($payload['deep_link']??''),$locale);
                $plainFooter=$locale==='en'
                    ? "\n\nYou received this because you opted in to Athar updates. Unsubscribe: ".$unsubscribe
                    : "\n\nوصلتك هذه الرسالة لأنك اشتركت في تحديثات أثر. لإلغاء الاشتراك: ".$unsubscribe;
                $htmlFooter=$locale==='en'
                    ? 'You received this because you opted in to Athar updates. <a href="'.htmlspecialchars($unsubscribe,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'">Unsubscribe</a>'
                    : 'وصلتك هذه الرسالة لأنك اشتركت في تحديثات أثر. <a href="'.htmlspecialchars($unsubscribe,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'">إلغاء الاشتراك</a>';
                $htmlBody='<p style="margin:0;white-space:pre-line">'.nl2br(htmlspecialchars($body,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8'),false).'</p>'.$cta;
                $dedupe=hash('sha256','campaign-email:'.$row['id'].':'.$row['dedupe_key']);
                $payload=['subject'=>$subject,'plain_body'=>$body.$plainFooter,'html_body'=>$htmlBody,'html_footer'=>$htmlFooter,'recipient'=>$user['email']];
                $sealed=$this->cipher->seal($payload,$dedupe);
                $insert=$this->db->prepare("INSERT IGNORE INTO email_outbox
                    (category,priority,user_id,campaign_delivery_id,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
                    VALUES ('campaign',70,?,?,?,?,?,?,?, 'pending',?,0,5,UTC_TIMESTAMP(),?,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
                $insert->execute([(int)$row['user_id'],(int)$row['id'],$locale,'Athar message',$sealed,null,'',$dedupe,$row['expires_at']]);
                $this->db->prepare("UPDATE engagement_campaign_recipients SET status='processing',locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='queued'")->execute([$row['id']]);
                $this->db->prepare("UPDATE engagement_campaigns SET status='processing',updated_at=UTC_TIMESTAMP() WHERE id=? AND status='scheduled'")->execute([$row['campaign_id']]);
                $queued++;$this->db->commit();
            }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
        }
        return ['queued'=>$queued,'skipped'=>$skipped];
    }

    public function claimBatch(int $limit=20): array
    {
        $limit=max(1,min(100,$limit));$this->recover();$this->db->beginTransaction();
        try{
            $q=$this->db->query("SELECT id FROM email_outbox
                WHERE status='pending' AND available_at<=UTC_TIMESTAMP() AND expires_at>UTC_TIMESTAMP()
                ORDER BY priority ASC,available_at ASC,id ASC LIMIT ".$limit." FOR UPDATE");
            $ids=array_map('intval',$q->fetchAll(PDO::FETCH_COLUMN));
            if(!$ids){$this->db->commit();return [];}
            $in=implode(',',array_fill(0,count($ids),'?'));
            $u=$this->db->prepare("UPDATE email_outbox SET status='processing',attempt_count=attempt_count+1,locked_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE id IN ($in) AND status='pending'");
            $u->execute($ids);
            $q=$this->db->prepare("SELECT * FROM email_outbox WHERE id IN ($in) ORDER BY priority,id");$q->execute($ids);$rows=$q->fetchAll();
            foreach($rows as $row)$this->leases[(int)$row['id']]=(int)$row['attempt_count'];
            $this->db->commit();return $rows;
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    /**
     * The transport callback receives:
     * recipient, subject, plainBody, htmlBody, htmlFooter, category, locale
     * and returns [bool $sent, ?string $failureCode].
     */
    public function processBatch(callable $transport,int $limit=20): array
    {
        $limit=max(1,min(100,$limit));
        $lock='athar-email-worker:'.substr(hash('sha256',(string)$this->db->query('SELECT DATABASE()')->fetchColumn()),0,32);
        $q=$this->db->prepare('SELECT GET_LOCK(?,0)');$q->execute([$lock]);
        if((int)$q->fetchColumn()!==1)return ['claimed'=>0,'sent'=>0,'failed'=>0,'retried'=>0,'cancelled'=>0,'busy'=>true];
        try {
        $this->enqueueDueCampaignEmails(max(50,$limit*5));
        $rows=[];$sent=0;$failed=0;$retried=0;$cancelled=0;
        for($index=0;$index<$limit;$index++){
            $claimed=$this->claimBatch(1);if(!$claimed)break;$row=$claimed[0];$rows[]=$row;
            $id=(int)$row['id'];$category=(string)$row['category'];$started=false;
            try{
                $live=$this->db->prepare("SELECT id FROM email_outbox WHERE id=? AND status='processing' AND attempt_count=? AND expires_at>UTC_TIMESTAMP()");$live->execute([$id,(int)$row['attempt_count']]);
                if(!$live->fetchColumn()){$cancelled++;continue;}
                if($category==='campaign'&&$this->campaignDailyQuotaReached()){
                    $this->requeue($id,'campaign_daily_quota',3600);$retried++;continue;
                }
                if($category==='campaign'&&$row['campaign_delivery_id']!==null&&!$this->campaignDeliveryStillEligible((int)$row['campaign_delivery_id'])){
                    $this->cancelOutbox($id,'campaign_or_consent_cancelled');$cancelled++;continue;
                }
                if($category==='campaign'&&$row['campaign_delivery_id']!==null){
                    $policy=$this->db->prepare('SELECT r.user_id,c.min_frequency_hours,c.respect_quiet_hours FROM engagement_campaign_recipients r JOIN engagement_campaigns c ON c.id=r.campaign_id WHERE r.id=?');$policy->execute([$row['campaign_delivery_id']]);$campaign=$policy->fetch();
                    if($campaign){$at=(new CampaignService($this->db))->deliveryNotBefore((int)$campaign['user_id'],'email',$campaign,gmdate('Y-m-d H:i:s'));
                        if($at>=($row['expires_at'])){$this->cancelOutbox($id,'delivery_policy_exceeds_ttl');$cancelled++;continue;}
                        if($at>gmdate('Y-m-d H:i:s')){$this->requeue($id,'delivery_policy_deferred',max(60,strtotime($at.' UTC')-time()));$retried++;continue;}
                    }
                }
                try{$payload=$this->cipher->open((string)$row['plain_body'],(string)$row['idempotency_key']);}
                catch(Throwable $unreadable){$this->requeue($id,'payload_unreadable',300);$retried++;continue;}
                if(!empty($payload['auth_token_id'])){
                    $token=$this->db->prepare('SELECT id FROM auth_tokens WHERE id=? AND user_id=? AND token_type=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP()');$token->execute([$payload['auth_token_id'],$row['user_id'],$payload['purpose']??'']);
                    if(!$token->fetchColumn()){$this->cancelOutbox($id,'obsolete_token');$cancelled++;continue;}
                }
                $recipient=$this->recipient($row);
                if($row['user_id']===null){
                    try{$authorized=\platform_setting('smtp_test_recipient_authorized_at')!==''?\open_platform_secret(\platform_setting('smtp_test_recipient_cipher')):null;}catch(Throwable $e){$authorized=null;}
                    $recipient=is_string($authorized)&&hash_equals($authorized,(string)($payload['recipient']??''))?$authorized:null;
                }
                if($recipient!==null&&!hash_equals($recipient,(string)($payload['recipient']??'')))$recipient=null;
                if($recipient===null){$this->terminalFailure($id,'recipient_unavailable');$failed++;continue;}
                $start=$this->db->prepare("UPDATE email_outbox SET delivery_attempt_count=delivery_attempt_count+1,dispatch_started_at=UTC_TIMESTAMP() WHERE id=? AND status='processing' AND attempt_count=? AND expires_at>UTC_TIMESTAMP()");$start->execute([$id,(int)$row['attempt_count']]);if($start->rowCount()!==1){$cancelled++;continue;}
                $row['delivery_attempt_count']=(int)$row['delivery_attempt_count']+1;
                $started=true;
                $result=$transport($recipient,(string)$payload['subject'],(string)$payload['plain_body'],$payload['html_body']===null?null:(string)$payload['html_body'],(string)$payload['html_footer'],$category,(string)$row['locale']);
                $ok=is_array($result)?(bool)($result[0]??false):(bool)$result;
                $failure=is_array($result)&&isset($result[1])?(string)$result[1]:'smtp_unavailable';
                if($ok){$this->markSent($id);$sent++;continue;}
                if($failure==='delivery_outcome_unknown'){$this->terminalFailure($id,$failure);$failed++;continue;}
                $started=false;
                if((int)$row['delivery_attempt_count']>=(int)$row['max_attempts']||strtotime((string)$row['expires_at'].' UTC')<=time()+60){$this->terminalFailure($id,$failure);$failed++;}
                else{$delay=min(3600,60*(2**max(0,(int)$row['delivery_attempt_count']-1)));$this->requeue($id,$failure,$delay);$retried++;}
            }catch(Throwable $e){
                if($started){$this->terminalFailure($id,'delivery_outcome_unknown');$failed++;continue;}
                if((int)$row['delivery_attempt_count']>=(int)$row['max_attempts']){$this->terminalFailure($id,'worker_exception');$failed++;}
                else{$this->requeue($id,'worker_exception',min(3600,60*(2**max(0,(int)$row['delivery_attempt_count']-1))));$retried++;}
            }
        }
        return ['claimed'=>count($rows),'sent'=>$sent,'failed'=>$failed,'retried'=>$retried,'cancelled'=>$cancelled];
        } finally {$q=$this->db->prepare('SELECT RELEASE_LOCK(?)');$q->execute([$lock]);}
    }

    public function recover(): array
    {
        $affectedCampaigns=[];
        $q=$this->db->query("SELECT DISTINCT r.campaign_id
            FROM email_outbox o
            JOIN engagement_campaign_recipients r ON r.id=o.campaign_delivery_id
            WHERE o.category='campaign' AND o.status IN ('pending','processing')
              AND o.expires_at<=UTC_TIMESTAMP()");
        foreach($q->fetchAll(PDO::FETCH_COLUMN) as $campaignId)$affectedCampaigns[]=(int)$campaignId;

        $expired=$this->db->exec("UPDATE email_outbox SET status='failed',last_error_code=IF(dispatch_started_at IS NULL,'expired','delivery_outcome_unknown'),locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE status IN ('pending','processing') AND expires_at<=UTC_TIMESTAMP()");
        $ambiguous=$this->db->exec("UPDATE email_outbox SET status='failed',last_error_code='delivery_outcome_unknown',locked_at=NULL WHERE status='processing' AND dispatch_started_at IS NOT NULL AND locked_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 MINUTE)");
        $stale=$this->db->exec("UPDATE email_outbox SET status='pending',last_error_code='stale_lock_recovered',locked_at=NULL,available_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE status='processing' AND dispatch_started_at IS NULL AND locked_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 MINUTE) AND expires_at>UTC_TIMESTAMP()");
        $this->db->exec("UPDATE engagement_campaign_recipients r JOIN email_outbox o ON o.campaign_delivery_id=r.id SET r.status='failed',r.last_error_code='email_outbox_expired',r.locked_at=NULL,r.updated_at=UTC_TIMESTAMP() WHERE o.category='campaign' AND o.status='failed' AND o.last_error_code='expired' AND r.status='processing'");

        $repair=$this->db->query("SELECT DISTINCT r.campaign_id FROM engagement_campaign_recipients r JOIN email_outbox o ON o.campaign_delivery_id=r.id WHERE r.status IN ('queued','processing') AND o.status IN ('sent','failed','cancelled')");
        foreach($repair->fetchAll(PDO::FETCH_COLUMN) as $id)$affectedCampaigns[]=(int)$id;
        $this->db->exec("UPDATE engagement_campaign_recipients r JOIN email_outbox o ON o.campaign_delivery_id=r.id SET r.status=CASE o.status WHEN 'sent' THEN 'sent' WHEN 'failed' THEN 'failed' ELSE 'skipped' END,r.last_error_code=o.last_error_code,r.sent_at=o.sent_at,r.locked_at=NULL WHERE r.status IN ('queued','processing') AND o.status IN ('sent','failed','cancelled')");
        foreach(array_values(array_unique($affectedCampaigns)) as $campaignId)if($campaignId>0)$this->reconcileCampaign($campaignId);
        return ['expired'=>(int)$expired,'recovered'=>(int)$stale,'campaigns_reconciled'=>count(array_unique($affectedCampaigns)),'ambiguous_stopped'=>(int)$ambiguous];
    }

    public function cancelCampaignOutbox(int $campaignId): int
    {
        $q=$this->db->prepare("UPDATE email_outbox o JOIN engagement_campaign_recipients r ON r.id=o.campaign_delivery_id SET o.status='cancelled',o.last_error_code='campaign_cancelled',o.locked_at=NULL,o.updated_at=UTC_TIMESTAMP() WHERE r.campaign_id=? AND o.status='pending'");
        $q->execute([$campaignId]);return $q->rowCount();
    }

    private function markSent(int $id): void
    {
        $this->db->beginTransaction();
        try{
            $q=$this->db->prepare("SELECT campaign_delivery_id,category FROM email_outbox WHERE id=? AND status='processing' AND attempt_count=? FOR UPDATE");$q->execute([$id,(int)($this->leases[$id]??-1)]);$row=$q->fetch();
            if(!$row){$this->db->rollBack();return;}
            $this->db->prepare("UPDATE email_outbox SET status='sent',sent_at=UTC_TIMESTAMP(),locked_at=NULL,last_error_code=NULL,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='processing'")->execute([$id]);
            if($row['category']==='campaign'&&$row['campaign_delivery_id']!==null){
                $delivery=(int)$row['campaign_delivery_id'];
                $this->db->prepare("UPDATE engagement_campaign_recipients SET status='sent',sent_at=UTC_TIMESTAMP(),locked_at=NULL,last_error_code=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$delivery]);
                $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([$delivery]);$campaign=(int)$q->fetchColumn();
            }else{$campaign=0;}
            $this->db->commit();if($campaign>0)$this->reconcileCampaign($campaign);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    private function terminalFailure(int $id,string $code): void
    {
        $code=$this->code($code);$q=$this->db->prepare("SELECT campaign_delivery_id FROM email_outbox WHERE id=? AND status='processing' AND attempt_count=".(int)($this->leases[$id]??-1));$q->execute([$id]);$deliveryRow=$q->fetch(PDO::FETCH_NUM);if(!$deliveryRow)return;$delivery=$deliveryRow[0];
        $write=$this->db->prepare("UPDATE email_outbox SET status='failed',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='processing' AND attempt_count=?");$write->execute([$code,$id,(int)($this->leases[$id]??-1)]);if($write->rowCount()!==1)return;
        if($delivery!==false&&$delivery!==null){
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='failed',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,(int)$delivery]);
            $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([(int)$delivery]);$campaign=(int)$q->fetchColumn();if($campaign>0)$this->reconcileCampaign($campaign);
        }
    }
    private function cancelOutbox(int $id,string $code): void
    {
        $code=$this->code($code);$q=$this->db->prepare("SELECT campaign_delivery_id FROM email_outbox WHERE id=? AND status='processing' AND attempt_count=".(int)($this->leases[$id]??-1));$q->execute([$id]);$deliveryRow=$q->fetch(PDO::FETCH_NUM);if(!$deliveryRow)return;$delivery=$deliveryRow[0];
        $write=$this->db->prepare("UPDATE email_outbox SET status='cancelled',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='processing' AND attempt_count=?");$write->execute([$code,$id,(int)($this->leases[$id]??-1)]);if($write->rowCount()!==1)return;
        if($delivery!==false&&$delivery!==null){
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,(int)$delivery]);
            $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([(int)$delivery]);$campaign=(int)$q->fetchColumn();if($campaign>0)$this->reconcileCampaign($campaign);
        }
    }
    private function requeue(int $id,string $code,int $seconds): void{$seconds=max(60,min(3600,$seconds));$at=gmdate('Y-m-d H:i:s',time()+$seconds);$this->db->prepare("UPDATE email_outbox SET status='pending',last_error_code=?,locked_at=NULL,dispatch_started_at=NULL,available_at=?,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='processing' AND attempt_count=".(int)($this->leases[$id]??-1))->execute([$this->code($code),$at,$id]);}

    private function campaignDeliveryStillEligible(int $delivery): bool
    {
        if($delivery<1)return false;
        $q=$this->db->prepare("SELECT r.status,r.user_id,c.status campaign_status,u.email_verified_at,u.suspended_at,u.email_marketing_opt_in,p.email_campaign_opt_in
            FROM engagement_campaign_recipients r JOIN engagement_campaigns c ON c.id=r.campaign_id JOIN users u ON u.id=r.user_id
            LEFT JOIN engagement_channel_preferences p ON p.user_id=u.id WHERE r.id=? AND r.channel='email'");
        $q->execute([$delivery]);$row=$q->fetch();
        return (bool)$row&&$row['status']==='processing'&&in_array($row['campaign_status'],['processing','scheduled'],true)
            &&$row['email_verified_at']!==null&&$row['suspended_at']===null&&(bool)$row['email_marketing_opt_in']
            &&($row['email_campaign_opt_in']===null||(bool)$row['email_campaign_opt_in']);
    }
    private function emailCampaignUser(int $user): ?array
    {
        $q=$this->db->prepare("SELECT u.id,u.email,u.email_verified_at,u.suspended_at,u.email_marketing_opt_in,p.email_campaign_opt_in,
            CASE WHEN p.locale_is_explicit=1 THEN p.locale WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='en' THEN 'en' WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='ar' THEN 'ar' ELSE COALESCE(p.locale,'ar') END locale
            FROM users u LEFT JOIN engagement_channel_preferences p ON p.user_id=u.id WHERE u.id=?");
        $q->execute([$user]);$row=$q->fetch();
        if(!$row||$row['email_verified_at']===null||$row['suspended_at']!==null||!(bool)$row['email_marketing_opt_in']
            ||($row['email_campaign_opt_in']!==null&&!(bool)$row['email_campaign_opt_in'])||!filter_var($row['email'],FILTER_VALIDATE_EMAIL))return null;
        return $row;
    }
    private function recipient(array $row): ?string
    {
        if($row['user_id']!==null){
            $q=$this->db->prepare('SELECT email,suspended_at FROM users WHERE id=?');$q->execute([(int)$row['user_id']]);$user=$q->fetch();
            if(!$user||$user['suspended_at']!==null||!filter_var($user['email'],FILTER_VALIDATE_EMAIL))return null;return (string)$user['email'];
        }
        if(!empty($row['recipient_email_cipher'])){try{$email=\open_platform_secret((string)$row['recipient_email_cipher']);return filter_var($email,FILTER_VALIDATE_EMAIL)?$email:null;}catch(Throwable $e){return null;}}
        return null;
    }
    private function reconcileCampaign(int $campaign): void
    {
        $q=$this->db->prepare("SELECT
            SUM(status='queued') queued,SUM(status='processing') processing,SUM(status='failed') failed
            FROM engagement_campaign_recipients WHERE campaign_id=?");$q->execute([$campaign]);$c=$q->fetch();
        $remaining=(int)($c['queued']??0)+(int)($c['processing']??0);$status=$remaining>0?'processing':((int)($c['failed']??0)>0?'partially_failed':'completed');
        $this->db->prepare("UPDATE engagement_campaigns SET status=IF(status='cancelled','cancelled',?),updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$status,$campaign]);
    }
    private function campaignDailyQuotaReached(): bool{$q=$this->db->query("SELECT COUNT(*) FROM email_send_events WHERE category='campaign' AND sent_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR)");$events=(int)$q->fetchColumn();$outbox=(int)$this->db->query("SELECT COUNT(*) FROM email_outbox WHERE category='campaign' AND status='sent' AND sent_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR)")->fetchColumn();return max($events,$outbox)>=450;}
    private function emailCta(string $link,string $locale): string
    {
        $link=trim($link);if($link==='')return '';
        if(str_starts_with($link,'/'))$link=\platform_email_base_url().$link;
        if(!preg_match('#^(?:https://|athar://)#',$link))return '';
        $label=$locale==='en'?'Open in Athar':'فتح في أثر';
        return '<p style="margin:22px 0 0"><a href="'.htmlspecialchars($link,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'" style="display:inline-block;padding:11px 18px;border-radius:10px;background:#1e6753;color:#fff;text-decoration:none;font-weight:bold">'.htmlspecialchars($label,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'</a></p>';
    }
    private function acceptedInsert(int $inserted,string $dedupe,array $payload): bool
    {
        if($inserted===1)return true;
        $q=$this->db->prepare('SELECT plain_body FROM email_outbox WHERE idempotency_key=?');$q->execute([$dedupe]);$saved=$q->fetchColumn();
        if(!is_string($saved))throw new ApiError('conflict',409);
        $old=$this->cipher->open($saved,$dedupe);
        if($old!==$payload)throw new ApiError('conflict',409,['idempotency_key'=>'different_payload']);
        return true;
    }
    private function code(string $code): string{return in_array($code,['smtp_unavailable','rate_limited','timeout','recipient_unavailable','campaign_or_consent_cancelled','email_opt_out','campaign_cancelled','worker_exception','campaign_daily_quota','payload_unreadable','delivery_outcome_unknown','obsolete_token','delivery_policy_deferred','delivery_policy_exceeds_ttl'],true)?$code:'delivery_failed';}
}
