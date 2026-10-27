"""Actual HTTP consent/unsubscribe, encrypted identity queue and authorized template tests."""
import json
import re
import os
import subprocess
import time
import urllib.parse
import admin_batch_fixture as fixture

checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1

owner=fixture.Session('owner');manager=fixture.Session();reader=fixture.Session('user');other=fixture.Session('user')
token=reader.token();other_token=other.token();sql=fixture.service
context="require 'app/helpers.php';require 'app/mailer.php';"
_,_,shell=owner.request('/admin?lang=en#admin-create')
check(all('id="'+panel+'"' in shell for panel in ['admin-overview','admin-create','admin-backups']),'Overview, recovery and authoring panels remain present beside campaigns')
status,_,page=owner.submit({'kind':'book','title':'Adjacent authoring fixture','author':'Fixture author','description':'Generated fixture','language':'en','rights_basis':'Fixture permission','source_url':'https://example.invalid/source'})
check(status==200 and sql("echo json_encode((int)$db->query(\"SELECT COUNT(*) FROM books WHERE title='Adjacent authoring fixture'\")->fetchColumn());")==1,'Adjacent book create handler saves and reads back after campaign replacement')
status,body=fixture.api('/me/engagement-preferences',token)
check(status==200 and body['data']['push_campaign_opt_in'] is False,'Push consent defaults off')
status,body=fixture.api('/me/engagement-preferences',token,'PATCH',{'email_campaign_opt_in':True,'locale':'en','quiet_hours':{'start':'22:00','end':'06:00','timezone':'Africa/Cairo'}})
check(status==200 and body['data']['email_campaign_opt_in'] is True and body['data']['quiet_hours']['start']=='22:00','Explicit owned consent and quiet hours persist')
check(fixture.api('/me/engagement-preferences',other_token)[1]['data']['email_campaign_opt_in'] is False,'Another account consent remains isolated')
for invalid in [{'user_id':other.account['id']},{'email_campaign_opt_in':1},{'quiet_hours':{'start':'22:00','end':'22:00','timezone':'Africa/Cairo'}},{'quiet_hours':{'start':'22:00','end':'06:00','timezone':'+02:00'}}]:
    check(fixture.api('/me/engagement-preferences',token,'PATCH',invalid)[0]==422,'Invalid/foreign preference mutation refused')
signed=sql(context+'echo json_encode(email_campaign_unsubscribe_url('+str(reader.account['id'])+'));')
query=urllib.parse.parse_qs(urllib.parse.urlsplit(signed).query);fields={key:value[0] for key,value in query.items()}
path='/email-unsubscribe?'+urllib.parse.urlencode(fields)
status,_,page=reader.request(path)
check(status==200 and 'Manage Athar updates' in page and 'name="e"' in page,'Signed GET renders bilingual confirmation with expiry')
check(fixture.api('/me/engagement-preferences',token)[1]['data']['email_campaign_opt_in'] is True,'GET does not unsubscribe link scanners')
check(reader.request('/email-unsubscribe',fields)[0]==419,'POST requires session CSRF')
status,_,page=reader.request(path)
status,_,page=reader.request('/email-unsubscribe',{'_csrf':fixture.csrf(page),**fields})
check(status==200 and 'You are unsubscribed' in page,'Signed POST confirms unsubscribe')
check(fixture.api('/me/engagement-preferences',token)[1]['data']['email_campaign_opt_in'] is False,'Unsubscribe updates explicit and legacy consent')
for change in [{'v':'7'},{'u':str(other.account['id'])},{'e':'1'},{'t':'0'*64}]:
    check(reader.request('/email-unsubscribe?'+urllib.parse.urlencode({**fields,**change}))[0]==400,'Wrong user/version/expiry/signature refused')
# Real API reset queues an encrypted token message even without marketing consent.
request=fixture.urllib.request.Request(fixture.base+'/api/v1/auth/forgot-password',json.dumps({'email':reader.email}).encode(),{'Content-Type':'application/json'})
try: response=fixture.urllib.request.urlopen(request);status=response.code;response.read()
except fixture.urllib.error.HTTPError as response:status=response.code
check(status==202,'Reset request remains available after campaign opt-out')
queued=sql("echo json_encode($db->query(\"SELECT * FROM email_outbox WHERE category='transactional' ORDER BY id DESC LIMIT 1\")->fetch());")
check(isinstance(queued,dict) and queued['plain_body'].startswith('enc:v1:') and queued['subject']=='Athar message' and reader.email not in json.dumps(queued),'Actual API OTP enqueued with encrypted body and recipient')
settings={'kind':'gmail-settings','gmail_email':'smtp_fixture@example.test','smtp_password':'SyntheticFixturePassword','test_recipient':'authorized_fixture@example.test','test_recipient_authorized':'on','owner_password':'AdminFixture123!'}
check(owner.submit(settings)[0]==200,'Synthetic SMTP settings saved without sending')
base={'kind':'engagement-campaign-save','name':'Email HTTP fixture','channel':'email','audience_type':'all_eligible','title_ar':'عنوان تجريبي','body_ar':'نص تجريبي','title_en':'Email title','body_en':'Email fixture body','ttl_hours':'24','min_frequency_hours':'6','schedule_timezone':'UTC'}
status,url,page=owner.submit(base);cid=int(urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)['campaign_preview'][0])
check(status==200 and 'Queue authorized test' in page,'Email draft exposes authorized Owner test workflow')
test={'kind':'engagement-campaign-test-email','campaign_id':cid,'expected_revision':'1','test_locale':'en','owner_password':'AdminFixture123!'}
check(manager.submit(test)[0]==403,'Content Admin cannot queue authorized recipient email')
status,_,page=owner.submit(test)
check(status==200 and 'data-admin-form-error' not in page,'Owner reauthenticated authorized test queued')
check(sql("echo json_encode((int)$db->query(\"SELECT COUNT(*) FROM email_outbox WHERE category='campaign' AND user_id IS NULL\")->fetchColumn());")==1,'Authorized test has no campaign audience')
status,_,page=owner.submit(test)
check(status==200 and 'authorized test could not be queued' in page and sql("echo json_encode((int)$db->query(\"SELECT COUNT(*) FROM email_outbox WHERE category='campaign' AND user_id IS NULL\")->fetchColumn());")==1,'Persistent test quota blocks repeated sessions/requests')
sql("$db->prepare('UPDATE users SET email_verified_at=NULL WHERE id=?')->execute(["+str(owner.account['id'])+"]);echo json_encode(true);")
check(owner.submit(test)[0]==403,'Unverified Owner cannot bypass campaign test verification gate')
sql("$db->prepare('UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute(["+str(owner.account['id'])+"]);echo json_encode(true);")
key=fixture.root/'storage/secrets/email-outbox.key';held=key.with_suffix('.held');key.rename(held)
try:
    before=sql('echo json_encode((int)$db->query("SELECT COUNT(*) FROM email_outbox")->fetchColumn());')
    failed=sql(context+"try{(new \\Athar\\Engagement\\EmailOutboxService($db))->enqueueTransactional("+str(reader.account['id'])+",'Fixture','Fixture body','missing-key');echo json_encode(false);}catch(Throwable $e){echo json_encode(true);}")
    check(failed and not key.exists() and sql('echo json_encode((int)$db->query("SELECT COUNT(*) FROM email_outbox")->fetchColumn());')==before,'Missing original key never regenerates over retained encrypted queue')
finally:held.rename(key)
status,_,_=owner.request('/storage/secrets/email-outbox.key')
check(status==404,'Outbox key inaccessible under public root')
result=sql(context+"$result=(new \\Athar\\Engagement\\EmailOutboxService($db))->processBatch(static fn(...$args)=>[true,null]);echo json_encode($result);")
check(result['sent']>=1,'Owned fixture transport exercises queue without any SMTP/provider request')
check(sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM email_send_events')->fetchColumn());")==0,'No actual provider send event fabricated by fixture callback')
fixture.api('/me/engagement-preferences',token,'PATCH',{'email_campaign_opt_in':True,'quiet_hours':{'start':None,'end':None,'timezone':None}})
status,_,page=owner.submit({'kind':'engagement-campaign-confirm','campaign_id':cid,'expected_revision':'1','confirmation':'CONFIRM '+str(cid),'owner_password':'AdminFixture123!'})
check(status==200 and 'data-admin-form-error' not in page,'Owner confirms an actual ready Email queue without synchronous sending')
count=sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM engagement_campaign_recipients WHERE campaign_id="+str(cid)+"')->fetchColumn());")
check(count>=1,'Confirmed Email audience snapshotted')
sql(context+"echo json_encode((new \\Athar\\Engagement\\EmailOutboxService($db))->enqueueDueCampaignEmails());")
status,_,page=owner.submit({'kind':'engagement-campaign-cancel','campaign_id':cid,'expected_revision':'2'})
check(status==200 and sql("echo json_encode((int)$db->query(\"SELECT COUNT(*) FROM email_outbox o JOIN engagement_campaign_recipients r ON r.id=o.campaign_delivery_id WHERE r.campaign_id="+str(cid)+" AND o.status='pending'\")->fetchColumn());")==0,'Cancel processing campaign atomically retires pending outbox rows')
status,url,page=owner.submit({**base,'channel':'push_email','name':'Combined blocked fixture'})
combined=int(urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)['campaign_preview'][0])
owner.submit({'kind':'engagement-campaign-confirm','campaign_id':combined,'expected_revision':'1','confirmation':'CONFIRM '+str(combined),'owner_password':'AdminFixture123!'})
check(sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM engagement_campaign_recipients WHERE campaign_id="+str(combined)+"')->fetchColumn());")==0,'Missing Push dispatcher blocks combined launch without partial Email snapshot')
def terminate_owned_worker(after_dispatch):
    label='dispatch' if after_dispatch else 'claim'
    oid=sql(context+"(new \\Athar\\Engagement\\EmailOutboxService($db))->enqueueTransactional("+str(reader.account['id'])+",'Crash fixture','Generated non-sensitive crash body','actual-crash-"+label+"');echo json_encode((int)$db->query('SELECT MAX(id) FROM email_outbox')->fetchColumn());")
    marker=fixture.root/('storage/engineering/mail-crash-'+label+'.marker')
    wait="file_put_contents('storage/engineering/"+marker.name+"','ready');while(true){usleep(100000);}"
    action="$outbox->processBatch(static function(...$args){"+wait+"});" if after_dispatch else "$outbox->claimBatch(1);"+wait
    program=fixture.setup+context+"$outbox=new \\Athar\\Engagement\\EmailOutboxService($db);"+action
    process=subprocess.Popen([fixture.php,'-r',program],cwd=fixture.root,env=fixture.environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        deadline=time.monotonic()+12
        while not marker.exists() and process.poll() is None and time.monotonic()<deadline:time.sleep(.05)
        check(marker.exists() and process.poll() is None,'Owned child reached '+label+' boundary')
        process.kill();process.communicate(timeout=5)
    finally:
        if process.poll() is None:process.kill();process.communicate(timeout=5)
    sql("$db->exec(\"UPDATE email_outbox SET locked_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE) WHERE id="+str(oid)+"\");echo json_encode(true);")
    result=sql(context+"$outbox=new \\Athar\\Engagement\\EmailOutboxService($db);$recovery=$outbox->recover();$calls=0;$result=$outbox->processBatch(static function(...$args)use(&$calls){$calls++;return [true,null];});echo json_encode(['recovery'=>$recovery,'calls'=>$calls]);")
    check(result['calls']==(0 if after_dispatch else 1),'Actual killed worker '+label+' recovery has safe replay behavior')
    if after_dispatch:check(result['recovery']['ambiguous_stopped']==1,'Killed dispatch outcome remains unknown and is never auto-replayed')
terminate_owned_worker(False)
terminate_owned_worker(True)
status_command=subprocess.run([fixture.php,str(fixture.root/'bin/process-email-outbox.php'),'--status'],cwd=fixture.root,env=fixture.environment,capture_output=True,text=True)
check(status_command.returncode==0 and set(json.loads(status_command.stdout))=={'counts'},'Actual CLI status returns aggregate metadata without delivery or secrets')
invalid_command=subprocess.run([fixture.php,str(fixture.root/'bin/process-email-outbox.php'),'invalid-limit'],cwd=fixture.root,env=fixture.environment,capture_output=True,text=True)
check(invalid_command.returncode==2 and 'Usage:' in invalid_command.stderr,'Invalid worker limit refuses before sending')
for locale in ['ar','en']:
    html=sql(context+"echo json_encode(platform_email_html('Fixture Athar message','Generated non-sensitive fixture text','',null,'"+locale+"'));")
    (fixture.root/('storage/engineering/email-preview-'+locale+'.html')).write_text(html,encoding='utf-8')
subprocess.run([os.environ['ATHAR_TEST_NODE'],str(fixture.root/'tests/email_browser_test.cjs'),owner.email,str(cid)],check=True,env=os.environ)
print(f'PASS: {checks} Email HTTP consent/signature/identity/authorization/key-preservation assertions; no actual SMTP send.')
import admin_services_http_test  # One related final mailer/key regression in this same owned fixture.
