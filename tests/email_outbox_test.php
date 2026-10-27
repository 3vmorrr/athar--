<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
require dirname(__DIR__).'/app/mailer.php';
use Athar\Engagement\EmailOutboxService;
use Athar\Engagement\EmailPayloadCipher;
use Athar\Engagement\CampaignService;
use Athar\Engagement\PreferenceService;
use Athar\Identity\OtpService;
use Athar\Http\ApiError;
function db(): PDO{return $GLOBALS['email_fixture_db'];}
$checks=0;
function mail_check(bool $ok,string $label): void {global $checks;$checks++;if(!$ok)throw new RuntimeException('FAIL: '.$label);}
function mail_reject(callable $call,int $status,string $label): void {try{$call();}catch(ApiError $e){mail_check($e->status===$status,$label);return;}mail_check(false,$label);}
$priorBase=getenv('ATHAR_BASE_URL');$priorSettings=getenv('ATHAR_SETTINGS_KEY');
putenv('ATHAR_BASE_URL=https://example.invalid/athar');putenv('ATHAR_SETTINGS_KEY='.str_repeat('a',64));
try{isolated_database(static function(PDO $db): void {
 $GLOBALS['email_fixture_db']=$db;
 foreach(['owner','user','user'] as $i=>$role)$db->prepare("INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at,email_marketing_opt_in,preferences_json) VALUES (?,?,?,'unused',?,UTC_TIMESTAMP(),1,'{\"language\":\"en\"}')")->execute(['mail'.$i.'@example.invalid','mail'.$i,'Fixture',$role]);
 $key=str_repeat('k',32);$outbox=new EmailOutboxService($db,$key);$cipher=new EmailPayloadCipher($db,$key);
 $sent=[];$transport=static function(...$message)use(&$sent){$sent[]=$message;return [true,null];};
 mail_check($outbox->enqueueTransactional(2,'Fixture subject','Secret fixture 654321','fixture-one','en'),'transactional enqueue succeeds');
 mail_check($outbox->enqueueTransactional(2,'Fixture subject','Secret fixture 654321','fixture-one','en'),'same idempotency key accepted');
 mail_reject(fn()=>$outbox->enqueueTransactional(2,'Changed subject','Changed body','fixture-one','en'),409,'same request identity cannot substitute a different message');
 $rows=$db->query('SELECT * FROM email_outbox')->fetchAll();
 mail_check(count($rows)===1&&!str_contains(json_encode($rows),'654321')&&!str_contains(json_encode($rows),'mail1@example.invalid'),'OTP body and recipient snapshot encrypted at rest');
 mail_check($cipher->open($rows[0]['plain_body'],$rows[0]['idempotency_key'])['plain_body']==='Secret fixture 654321','authenticated payload readback');
 try{$cipher->open($rows[0]['plain_body'],str_repeat('f',64));mail_check(false,'AAD must fail');}catch(RuntimeException $e){mail_check(true,'ciphertext bound to delivery identity');}
 try{(new EmailPayloadCipher($db,str_repeat('x',32)))->open($rows[0]['plain_body'],$rows[0]['idempotency_key']);mail_check(false,'wrong key must fail');}catch(RuntimeException $e){mail_check(true,'wrong key refuses without replacement');}
 $result=$outbox->processBatch($transport);
 mail_check($result['sent']===1&&$sent[0][0]==='mail1@example.invalid'&&$sent[0][2]==='Secret fixture 654321'&&$sent[0][6]==='en','transport receives exact snapshot only in worker memory');
 mail_check($outbox->processBatch($transport)['claimed']===0,'accepted delivery not replayed');
 $outbox->enqueueTransactional(2,'Changed account','Private old address','address-change','en');$db->exec("UPDATE users SET email='new@example.invalid' WHERE id=2");
 mail_check($outbox->processBatch($transport)['failed']===1&&count($sent)===1,'old queued message never follows changed email');
 $outbox->enqueueTransactional(2,'Retry fixture','Retry body','retry-fixture','en');
 $result=$outbox->processBatch(static fn(...$args)=>[false,'secret echoed by provider']);
 $retry=$db->query("SELECT * FROM email_outbox WHERE status='pending'")->fetch();
 mail_check($result['retried']===1&&$retry['last_error_code']==='delivery_failed'&&(int)$retry['delivery_attempt_count']===1,'retry diagnostics static and network attempts counted');
 mail_check(strtotime($retry['available_at'].' UTC')>=time()+55,'first retry waits sixty seconds');
 $db->exec("UPDATE email_outbox SET status='cancelled' WHERE status='pending'");
 $outbox->enqueueTransactional(2,'Expired fixture','Expired body','expired-fixture','en');$db->exec("UPDATE email_outbox SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE status='pending'");
 mail_check($outbox->processBatch($transport)['claimed']===0,'expired mail cannot reach transport');
 $outbox->enqueueTransactional(2,'Stale fixture','Stale body','stale-fixture','en');$claimed=$outbox->claimBatch();
 $db->exec("UPDATE email_outbox SET locked_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE) WHERE status='processing'");
 mail_check($outbox->recover()['recovered']===1,'stale lock recovered durably');
 $outbox->processBatch($transport);mail_check(count($sent)===2,'recovered reservation sends once without counting lost claim as network attempt');
 $owner=['id'=>1,'role'=>'owner'];$campaigns=new CampaignService($db,static fn($channel)=>true);
 $campaign=$campaigns->createDraft($owner,['name'=>'Email fixture','channel'=>'email','title_ar'=>'عنوان تجريبي','body_ar'=>'نص اختباري','title_en'=>'Campaign fixture','body_en'=>'Campaign body']);
 $campaigns->confirmAndSchedule($owner,$campaign['id'],1,'CONFIRM '.$campaign['id']);
 $outbox->enqueueTransactional(2,'Priority fixture','Priority body','priority-fixture','en');
 $outbox->enqueueDueCampaignEmails();$claims=$outbox->claimBatch(10);
 mail_check($claims[0]['category']==='transactional'&&count($claims)===4,'transactional priority precedes campaign snapshots');
 $db->exec("UPDATE email_outbox SET locked_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE) WHERE status='processing'");$outbox->recover();
 $prefs=new PreferenceService($db);$prefs->update(3,['email_campaign_opt_in'=>false]);
 $before=count($sent);$result=$outbox->processBatch($transport,10);
 mail_check(count($sent)===$before+3,'opted-out recipient suppressed while transactional and eligible channels continue');
 mail_check((int)$db->query('SELECT email_marketing_opt_in FROM users WHERE id=3')->fetchColumn()===0,'explicit consent synchronizes legacy flag');
 mail_reject(fn()=>$prefs->update(2,['email_campaign_opt_in'=>1]),422,'consent strict boolean');
 mail_reject(fn()=>$prefs->update(2,['quiet_hours'=>['start'=>'22:00','end'=>'06:00','timezone'=>'+02:00']]),422,'quiet timezone must be named IANA');
 $outbox->enqueueTransactional(2,'Ambiguous fixture','Ambiguous body','ambiguous-fixture','en');
 $before=count($sent);$result=$outbox->processBatch(static function(...$args){throw new RuntimeException('Synthetic uncertain transport outcome');});
 mail_check($result['failed']===1&&$outbox->processBatch($transport)['claimed']===0,'ambiguous transport outcome stops automatic replay');
 $outbox->enqueueTransactional(2,'Crash fixture','Crash body','crash-fixture','en');$outbox->claimBatch();
 $db->exec("UPDATE email_outbox SET dispatch_started_at=UTC_TIMESTAMP(),locked_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE) WHERE status='processing'");
 mail_check($outbox->recover()['ambiguous_stopped']===1&&$outbox->processBatch($transport)['claimed']===0,'crash after dispatch boundary cannot be silently retried');
 $otp=new OtpService($db,str_repeat('a',32),static function($email,$subject,$body,$user,$purpose,$token,$locale)use($outbox){return $outbox->enqueueTransactional($user,$subject,$body,'otp:'.$token,$locale,600,authTokenId:$token,purpose:$purpose);});
 mail_check($otp->issue(2,'password_reset')&&$otp->issue(2,'password_reset'),'OTP issue and durable encrypted enqueue succeed');
 $before=count($sent);$result=$outbox->processBatch($transport);
 mail_check($result['cancelled']===1&&count($sent)===$before+1,'superseded OTP suppressed while current token delivers once');
 $otp->issue(2,'verify_email');$db->exec("UPDATE auth_tokens SET consumed_at=UTC_TIMESTAMP() WHERE user_id=2 AND token_type='verify_email'");
 $before=count($sent);$result=$outbox->processBatch($transport);
 mail_check($result['cancelled']===1&&count($sent)===$before,'consumed token never enters transport');
 $outbox->enqueueTransactional(2,'Corrupt fixture','Corrupt body','corrupt-fixture','en');
 $db->exec("UPDATE email_outbox SET plain_body=CONCAT(plain_body,'corrupt') WHERE status='pending'");
 $saved=$db->query("SELECT plain_body FROM email_outbox WHERE status='pending'")->fetchColumn();$before=count($sent);$outbox->processBatch($transport);
 mail_check(count($sent)===$before&&$db->query("SELECT plain_body FROM email_outbox WHERE status='pending'")->fetchColumn()===$saved,'unreadable payload preserved without send or replacement');
 $db->exec("UPDATE email_outbox SET status='cancelled' WHERE status='pending'");
 $lock='athar-email-worker:'.substr(hash('sha256',(string)$db->query('SELECT DATABASE()')->fetchColumn()),0,32);
 $other=\Athar\Infrastructure\Database::connect((string)$db->query('SELECT DATABASE()')->fetchColumn());$q=$other->prepare('SELECT GET_LOCK(?,0)');$q->execute([$lock]);
 mail_check($outbox->processBatch($transport)['busy']===true,'second worker connection cannot overlap active batch');$q=$other->prepare('SELECT RELEASE_LOCK(?)');$q->execute([$lock]);
 $prefs->update(2,['locale'=>'ar']);
 mail_check($prefs->get(2)['locale']==='ar','Explicit campaign locale overrides profile fallback without rewriting profile');
 $localized=$campaigns->createDraft($owner,['name'=>'Locale fixture','channel'=>'email','audience_type'=>'locale','audience_locale'=>'ar','title_ar'=>'Arabic fixture','body_ar'=>'Arabic fixture text','title_en'=>'English fixture','body_en'=>'English fixture text']);
 mail_check($campaigns->preview($owner,$localized['id'])['audience']['email']['eligible_count']===1,'Audience locale resolution matches explicit consent preference');
 $url=email_campaign_unsubscribe_url(2);parse_str((string)parse_url($url,PHP_URL_QUERY),$query);
 mail_check(email_campaign_unsubscribe_valid(2,$query['t'],(int)$query['e'],2),'signed expiring unsubscribe validates');
 mail_check(!email_campaign_unsubscribe_valid(3,$query['t'],(int)$query['e'],2)&&!email_campaign_unsubscribe_valid(2,$query['t'],time()-1,2)&&!email_campaign_unsubscribe_valid(2,$query['t'],(int)$query['e'],7),'wrong user expired or unknown version fails');
 foreach(['en'=>'ltr','ar'=>'rtl'] as $locale=>$direction){$html=platform_email_html('Fixture <title>','Safe <body>','',null,$locale);mail_check(str_contains($html,'lang="'.$locale.'" dir="'.$direction.'"')&&str_contains($html,'&lt;body&gt;')&&str_contains($html,'athar-logo-cropped.png'),'branded escaped locale rendering');
   $mime=platform_email_mime('to@example.invalid','from@example.invalid','Athar','Fixture subject',".Body first line\nSecond line",locale:$locale);
   mail_check(str_contains($mime,'multipart/alternative;')&&substr_count($mime,'Content-Transfer-Encoding: base64')===2&&str_contains($mime,base64_encode(".Body first line\r\nSecond line")),'MIME has both UTF-8 base64 parts without corrupting leading dot');
 }
});}finally{putenv('ATHAR_BASE_URL'.($priorBase===false?'':'='.$priorBase));putenv('ATHAR_SETTINGS_KEY'.($priorSettings===false?'':'='.$priorSettings));}
echo "PASS: {$checks} encrypted Email outbox/priority/retry/consent/locale/signature assertions; no real SMTP send.\n";
