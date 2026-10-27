"""Immediate owner/secrets/key/SMTP authorization gate; no real messages are sent."""
import json
import secrets
import admin_batch_fixture as fixture
checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1
owner=fixture.Session('owner');manager=fixture.Session();sql=fixture.service
suffix=secrets.token_hex(6);password='FixtureSecret'+suffix;recipient='authorized_'+suffix+'@example.test';dorar='FixtureDorar'+suffix
def settings():return sql("echo json_encode($db->query('SELECT setting_key,setting_value FROM platform_settings ORDER BY setting_key')->fetchAll(PDO::FETCH_KEY_PAIR));")
fields={'kind':'gmail-settings','gmail_email':'fixture_'+suffix+'@example.test','smtp_password':password,'test_recipient':recipient,'test_recipient_authorized':'on','owner_password':'AdminFixture123!'}
before=settings();denied,_,_=manager.submit(fields)
check(denied==403 and settings()==before,'Content Admin cannot write owner SMTP settings')
for change in [{'owner_password':'wrong'},{'gmail_email':'invalid'},{'test_recipient':'invalid'},{'smtp_password':'p'*501}]:
    _,_,page=owner.submit({**fields,**change});check('data-admin-form-error' in page and settings()==before,'Invalid settings or wrong owner password cannot change configuration')
unconsented=dict(fields);unconsented.pop('test_recipient_authorized');owner.submit(unconsented)
check(settings()==before,'A test recipient requires explicit authorization before any save')
status,_,page=owner.submit(fields);saved=settings()
check(status==200 and saved['smtp_password_cipher']!=password and saved['smtp_test_recipient_cipher']!=recipient,'SMTP secret and authorized recipient are stored encrypted')
context="require 'app/helpers.php';require 'admin/authorization.php';require 'app/mailer.php';require 'admin/services.php';"
check(sql(context+"echo json_encode(platform_secret('smtp_password')==="+json.dumps(password)+" && platform_secret('smtp_test_recipient')==="+json.dumps(recipient)+");"),'Stored ciphertext decrypts to the exact fixture values')
check(password not in page and recipient not in page and saved['smtp_password_cipher'] not in page,'HTML and diagnostics expose neither plaintext secrets, full recipient nor ciphertext')
check('Stored and readable' in page and 'value="'+password+'"' not in page,'Owner sees actual readability and blank secret inputs')
check(owner.request('/storage/secrets/settings.key')[0]==404,'Settings key remains outside the public document root')
check(saved['smtp_test_recipient_authorized_at']!='' and saved['smtp_host']=='smtp.gmail.com' and saved['smtp_security']=='tls','Authorization is timestamped and secure provider connection stays fixed')
preserve={**fields,'smtp_password':'','test_recipient':''};preserve.pop('test_recipient_authorized')
owner.submit(preserve)
check(settings()['smtp_password_cipher']==saved['smtp_password_cipher'] and settings()['smtp_test_recipient_cipher']==saved['smtp_test_recipient_cipher'],'Blank inputs preserve current same-account ciphertext exactly')
_,_,page=owner.submit({**preserve,'gmail_email':'different_'+suffix+'@example.test'})
check('new App Password' in page and settings()['smtp_username']==fields['gmail_email'],'Changing account cannot silently reuse another account’s App Password')
before=settings();owner.submit({**preserve,'clear_test_recipient':'on','send_test_email':'1'})
check(settings()==before,'Contradictory clear-and-send is rejected before transport or persistence')
owner.submit({'kind':'dorar-settings','dorar_api_token':dorar,'owner_password':'AdminFixture123!'})
check(settings()['dorar_api_token_cipher']!=dorar and dorar not in owner.request('/admin?lang=en')[2],'Provider token is encrypted and never reflected in owner HTML')
before=settings();owner.submit({'kind':'dorar-settings','dorar_api_token':'Replacement fixture','clear_dorar_token':'on','owner_password':'AdminFixture123!'})
check(settings()['dorar_api_token_cipher']=='','Explicit token clear takes priority over a filled new token')
denied,_,page=manager.request('/admin?lang=en')
check('id="admin-services"' not in page and 'Configuration status' not in page,'Non-owner receives no settings or secret diagnostics panel')

# Missing original key must not create a replacement or overwrite old ciphertext.
key=fixture.root/'storage/secrets/settings.key';retained=key.with_suffix('.fixture-retained')
assert key.is_file() and not retained.exists();key.rename(retained)
try:
    before=settings();status,_,page=owner.request('/admin?lang=en')
    check(status==200 and 'Encrypted setting is unreadable' in page and not key.exists(),'Missing original key is diagnosed without creating a replacement')
    owner.submit(preserve)
    check(settings()==before and not key.exists(),'Unreadable secrets are preserved when a blank save cannot decrypt them')
finally:retained.rename(key)
before=settings();sql("$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(['invalid-ciphertext','smtp_password_cipher']);echo json_encode(true);")
status,_,page=owner.request('/admin?lang=en')
check(status==200 and 'Encrypted setting is unreadable' in page and 'invalid-ciphertext' not in page,'Malformed ciphertext remains a distinct redacted state without a whole-page failure')
sql("$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(["+json.dumps(before['smtp_password_cipher'])+",'smtp_password_cipher']);echo json_encode(true);")

# Real handler and rate limiter, with an explicit in-process sender dependency.
# This is not a production HTTP test flag and cannot perform a real delivery.
program=context+"$actor=$db->query('SELECT * FROM users WHERE id="+str(owner.account['id'])+"')->fetch();$input=json_decode("+json.dumps(json.dumps({**preserve,'send_test_email':'1'}))+",true);"
accepted=sql(program+"$correct=false;$result=admin_save_gmail($actor,$input,static function($to)use(&$correct){$correct=$to==="+json.dumps(recipient)+";return true;});echo json_encode(['correct'=>$correct,'accepted'=>$result['test']==='accepted']);")
check(accepted['correct'] and accepted['accepted'],'The real service handler passes only the authorized recipient to its sender dependency')
throttled=sql(program+"$called=false;try{admin_save_gmail($actor,$input,static function($to)use(&$called){$called=true;return true;});$limited=false;}catch(RuntimeException $e){$limited=true;}echo json_encode($limited&&!$called);")
check(throttled,'Persistent owner test quota blocks another send independently of browser session')
sql("$hash=hash_hmac('sha256',\"smtp-admin-test\\0\".'"+str(owner.account['id'])+"',\\Athar\\Infrastructure\\SecretKey::authentication());$db->prepare('DELETE FROM auth_rate_buckets WHERE bucket_hash=?')->execute([$hash]);echo json_encode(true);")
redacted=sql(program+"try{admin_save_gmail($actor,$input,static function($to){throw new RuntimeException('Sensitive fixture transport text');});echo json_encode(false);}catch(RuntimeException $e){echo json_encode(!str_contains($e->getMessage(),'Sensitive fixture'));}")
check(redacted,'Unexpected transport text cannot reach the UI through a test failure')
check(sql(context+"$s=fopen('php://temp','r+');$command='AUTH LOGIN';fwrite($s,str_repeat(' ',strlen($command)+2).\"535 sensitive fixture echo\\r\\n\");rewind($s);try{smtp_command($s,$command,[235]);echo json_encode(false);}catch(RuntimeException $e){echo json_encode($e->getMessage()==='SMTP command rejected (535)');}finally{fclose($s);}"),'SMTP protocol diagnostics retain only the status code, never raw server echoes')
check(sql(context+"putenv('APP_ENV=production');putenv('ATHAR_BASE_URL');$_SERVER['HTTP_HOST']='attacker.example';try{platform_email_base_url();echo json_encode(false);}catch(RuntimeException $e){echo json_encode(true);}"),'Production email links cannot derive their base from an untrusted Host header')
for base in ['http://example.invalid','https://user:pass@example.invalid','https://example.invalid?query=fixture','https://example.invalid#fixture']:
    check(sql(context+"putenv('APP_ENV=production');putenv("+json.dumps('ATHAR_BASE_URL='+base)+");try{platform_email_base_url();echo json_encode(false);}catch(RuntimeException $e){echo json_encode(true);}"),'Production base URL rejects insecure, credential or ambiguous link configuration')
check(sql(context+"putenv('APP_ENV=production');putenv('ATHAR_BASE_URL=https://example.invalid/athar');echo json_encode(platform_email_base_url()==='https://example.invalid/athar');"),'Explicit safe production base retains its deployment path')
owner.submit({**preserve,'test_recipient':'invalid ignored field','clear_test_recipient':'on'})
check(settings()['smtp_test_recipient_cipher']=='' and settings()['smtp_test_recipient_authorized_at']=='','Explicit recipient clear wins over a filled invalid replacement field')
print(f'PASS: {checks} focused service owner/encryption/key/recipient/rate/redaction assertions; no real SMTP/provider message sent.')
