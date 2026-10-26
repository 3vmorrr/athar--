<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use PDO;
use Throwable;

final class EmailOutboxService
{
    public function __construct(private PDO $db) {}

    public function enqueueTransactional(
        int $userId,
        string $subject,
        string $plainBody,
        string $idempotencyKey,
        string $locale='ar',
        int $ttlSeconds=600,
        ?string $htmlBody=null,
        string $htmlFooter=''
    ): bool {
        if($userId<1||!in_array($locale,['ar','en'],true)||mb_strlen($subject,'UTF-8')<2||mb_strlen($subject,'UTF-8')>180||$plainBody===''||$ttlSeconds<60||$ttlSeconds>86400)throw new ApiError('invalid_fields',422);
        $dedupe=hash('sha256','transactional:'.$idempotencyKey);
        $q=$this->db->prepare("INSERT IGNORE INTO email_outbox
            (category,priority,user_id,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
            VALUES ('transactional',10,?,?,?,?,?,?, 'pending',?,0,5,UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        $q->execute([$userId,$locale,$subject,$plainBody,$htmlBody,$htmlFooter,$dedupe,$ttlSeconds]);
        return $q->rowCount()===1 || $this->outboxExists($dedupe);
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
        $q=$this->db->prepare("INSERT IGNORE INTO email_outbox
            (category,priority,user_id,campaign_delivery_id,recipient_email_cipher,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
            VALUES ('campaign',30,NULL,NULL,?,?,?,?,?, 'pending',?,0,3,UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR),UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        $q->execute([$recipientCipher,$locale,$subject,$plainBody,$htmlBody,$footer,$dedupe]);
        return $q->rowCount()===1 || $this->outboxExists($dedupe);
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
                $insert=$this->db->prepare("INSERT IGNORE INTO email_outbox
                    (category,priority,user_id,campaign_delivery_id,locale,subject,plain_body,html_body,html_footer,status,idempotency_key,attempt_count,max_attempts,available_at,expires_at,created_at,updated_at)
                    VALUES ('campaign',70,?,?,?,?,?,?,?, 'pending',?,0,5,UTC_TIMESTAMP(),?,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
                $insert->execute([(int)$row['user_id'],(int)$row['id'],$locale,$subject,$body.$plainFooter,$htmlBody,$htmlFooter,$dedupe,$row['expires_at']]);
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
        $this->enqueueDueCampaignEmails(max(50,$limit*5));
        $rows=$this->claimBatch($limit);$sent=0;$failed=0;$retried=0;$cancelled=0;
        foreach($rows as $row){
            $id=(int)$row['id'];$category=(string)$row['category'];
            try{
                if($category==='campaign'&&$this->campaignDailyQuotaReached()){
                    $this->requeue($id,'campaign_daily_quota',3600);$retried++;continue;
                }
                if($category==='campaign'&&$row['campaign_delivery_id']!==null&&!$this->campaignDeliveryStillEligible((int)$row['campaign_delivery_id'])){
                    $this->cancelOutbox($id,'campaign_or_consent_cancelled');$cancelled++;continue;
                }
                $recipient=$this->recipient($row);
                if($recipient===null){$this->terminalFailure($id,'recipient_unavailable');$failed++;continue;}
                $result=$transport($recipient,(string)$row['subject'],(string)$row['plain_body'],$row['html_body']===null?null:(string)$row['html_body'],(string)($row['html_footer']??''),$category,(string)$row['locale']);
                $ok=is_array($result)?(bool)($result[0]??false):(bool)$result;
                $failure=is_array($result)&&isset($result[1])?(string)$result[1]:'smtp_unavailable';
                if($ok){$this->markSent($id);$sent++;continue;}
                if((int)$row['attempt_count']>=(int)$row['max_attempts']||strtotime((string)$row['expires_at'].' UTC')<=time()+60){$this->terminalFailure($id,$failure);$failed++;}
                else{$delay=min(3600,60*(2**max(0,(int)$row['attempt_count']-1)));$this->requeue($id,$failure,$delay);$retried++;}
            }catch(Throwable $e){
                if((int)$row['attempt_count']>=(int)$row['max_attempts']){$this->terminalFailure($id,'worker_exception');$failed++;}
                else{$this->requeue($id,'worker_exception',min(3600,60*(2**max(0,(int)$row['attempt_count']-1))));$retried++;}
            }
        }
        return ['claimed'=>count($rows),'sent'=>$sent,'failed'=>$failed,'retried'=>$retried,'cancelled'=>$cancelled];
    }

    public function recover(): array
    {
        $expired=$this->db->exec("UPDATE email_outbox SET status='failed',last_error_code='expired',locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE status IN ('pending','processing') AND expires_at<=UTC_TIMESTAMP()");
        $stale=$this->db->exec("UPDATE email_outbox SET status='pending',last_error_code='stale_lock_recovered',locked_at=NULL,available_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE status='processing' AND locked_at<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 MINUTE) AND expires_at>UTC_TIMESTAMP()");
        $this->db->exec("UPDATE engagement_campaign_recipients r JOIN email_outbox o ON o.campaign_delivery_id=r.id SET r.status='failed',r.last_error_code='email_outbox_expired',r.updated_at=UTC_TIMESTAMP() WHERE o.category='campaign' AND o.status='failed' AND o.last_error_code='expired' AND r.status='processing'");
        return ['expired'=>(int)$expired,'recovered'=>(int)$stale];
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
            $q=$this->db->prepare('SELECT campaign_delivery_id,category FROM email_outbox WHERE id=? FOR UPDATE');$q->execute([$id]);$row=$q->fetch();
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
        $code=$this->code($code);$q=$this->db->prepare("SELECT campaign_delivery_id FROM email_outbox WHERE id=?");$q->execute([$id]);$delivery=$q->fetchColumn();
        $this->db->prepare("UPDATE email_outbox SET status='failed',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,$id]);
        if($delivery!==false&&$delivery!==null){
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='failed',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,(int)$delivery]);
            $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([(int)$delivery]);$campaign=(int)$q->fetchColumn();if($campaign>0)$this->reconcileCampaign($campaign);
        }
    }
    private function cancelOutbox(int $id,string $code): void
    {
        $code=$this->code($code);$q=$this->db->prepare("SELECT campaign_delivery_id FROM email_outbox WHERE id=?");$q->execute([$id]);$delivery=$q->fetchColumn();
        $this->db->prepare("UPDATE email_outbox SET status='cancelled',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,$id]);
        if($delivery!==false&&$delivery!==null){
            $this->db->prepare("UPDATE engagement_campaign_recipients SET status='skipped',last_error_code=?,locked_at=NULL,updated_at=UTC_TIMESTAMP() WHERE id=?")->execute([$code,(int)$delivery]);
            $q=$this->db->prepare('SELECT campaign_id FROM engagement_campaign_recipients WHERE id=?');$q->execute([(int)$delivery]);$campaign=(int)$q->fetchColumn();if($campaign>0)$this->reconcileCampaign($campaign);
        }
    }
    private function requeue(int $id,string $code,int $seconds): void{$seconds=max(60,min(3600,$seconds));$at=gmdate('Y-m-d H:i:s',time()+$seconds);$this->db->prepare("UPDATE email_outbox SET status='pending',last_error_code=?,locked_at=NULL,available_at=?,updated_at=UTC_TIMESTAMP() WHERE id=? AND status='processing'")->execute([$this->code($code),$at,$id]);}

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
            CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='en' THEN 'en' WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='ar' THEN 'ar' ELSE COALESCE(p.locale,'ar') END locale
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
    private function campaignDailyQuotaReached(): bool{$q=$this->db->query("SELECT COUNT(*) FROM email_send_events WHERE category='campaign' AND sent_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR)");return (int)$q->fetchColumn()>=450;}
    private function emailCta(string $link,string $locale): string
    {
        $link=trim($link);if($link==='')return '';
        if(str_starts_with($link,'/'))$link=\platform_email_base_url().$link;
        if(!preg_match('#^(?:https://|athar://)#',$link))return '';
        $label=$locale==='en'?'Open in Athar':'فتح في أثر';
        return '<p style="margin:22px 0 0"><a href="'.htmlspecialchars($link,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'" style="display:inline-block;padding:11px 18px;border-radius:10px;background:#1e6753;color:#fff;text-decoration:none;font-weight:bold">'.htmlspecialchars($label,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8').'</a></p>';
    }
    private function outboxExists(string $dedupe): bool{$q=$this->db->prepare('SELECT 1 FROM email_outbox WHERE idempotency_key=? LIMIT 1');$q->execute([$dedupe]);return (bool)$q->fetchColumn();}
    private function code(string $code): string{return preg_replace('/[^a-z0-9_.-]/i','_',substr(trim($code),0,80))?:'delivery_failed';}
}
