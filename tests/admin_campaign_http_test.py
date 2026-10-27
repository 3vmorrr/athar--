"""Real campaign authoring, protected Admin sessions, unavailable provider gates."""
import json
import os
from pathlib import Path
import subprocess
import admin_batch_fixture as fixture

checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1

owner=fixture.Session('owner');manager=fixture.Session();reader=fixture.Session('user')
sql=fixture.service
base={'kind':'engagement-campaign-save','name':'HTTP campaign fixture','channel':'push','audience_type':'all_eligible','title_ar':'عنوان تجريبي','body_ar':'نص تجريبي غير ديني','title_en':'English fixture','body_en':'Safe fixture message','ttl_hours':'24','min_frequency_hours':'6','schedule_timezone':'UTC','respect_quiet_hours':'1'}
status,url,html=manager.submit(base)
check(status==200 and 'campaign_preview=' in url,'Draft saves and opens saved preview')
campaign=sql("echo json_encode($db->query('SELECT * FROM engagement_campaigns ORDER BY id DESC LIMIT 1')->fetch());")
cid=campaign['id']
check(campaign['status']=='draft' and campaign['created_by']==manager.account['id'],'Actual creator and draft state')
check('English fixture' in html and 'عنوان تجريبي' in html and 'push_delivery_not_ready' in html,'Both localized previews and truthful delivery gate')
check('campaign-batch' not in html and 'data-campaign-batch' not in html,'No old bulk-send control')
check(sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM engagement_campaign_recipients')->fetchColumn());")==0,'Draft/preview causes no delivery snapshot')
status,_,html=manager.submit({**base,'campaign_id':cid,'expected_revision':'1','name':'Updated actual campaign'})
check(status==200 and 'Updated actual campaign' in html,'Creator edit saved and read back')
status,_,html=manager.submit({**base,'campaign_id':cid,'expected_revision':'1','name':'Retained stale draft'})
check(status==200 and 'Retained stale draft' in html and 'This version is stale' in html and '<fieldset disabled>' in html,'Stale draft retained and blocked')
status,_,html=owner.submit({'kind':'engagement-campaign-confirm','campaign_id':cid,'expected_revision':'2','confirmation':'CONFIRM '+str(cid),'owner_password':'wrong'})
check(status==200 and 'current Owner password' in html,'Owner current password required')
status,_,html=owner.submit({'kind':'engagement-campaign-confirm','campaign_id':cid,'expected_revision':'2','confirmation':'CONFIRM '+str(cid),'owner_password':'AdminFixture123!'})
check(status==200 and 'delivery dependency is unavailable' in html,'Provider-unavailable launch refused truthfully')
check(sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM engagement_campaign_recipients')->fetchColumn());")==0,'Unavailable launch remains atomic')
status,_,_=manager.submit({'kind':'engagement-campaign-confirm','campaign_id':cid,'expected_revision':'2','confirmation':'CONFIRM '+str(cid),'owner_password':'AdminFixture123!'})
check(status==403,'Admin cannot launch a campaign')
status,_,html=manager.submit({'kind':'engagement-campaign-cancel','campaign_id':cid,'expected_revision':'2'})
check(status==200 and sql("echo json_encode($db->query('SELECT status FROM engagement_campaigns WHERE id="+str(cid)+"')->fetchColumn());")=='cancelled','Creator cancellation commits')
for bad in ({'image_url':'https://'+'user:password'+'@example.invalid/image'},{'scheduled_at':'2026-03-08T02:30','schedule_timezone':'America/New_York'},{'ttl_hours':'0'}):
    status,_,html=manager.submit({**base,**bad})
    check(status==200 and 'Check the campaign fields' in html,'Invalid draft rejected with controlled localized feedback')
status,_,html=owner.submit({'kind':'campaign-batch','campaign_id':cid})
check(status==410 and 'bulk-send action is retired' in html,'Old synchronous route retired without provider request')
sql("$db->prepare('UPDATE users SET email_verified_at=NULL WHERE id=?')->execute(["+str(manager.account['id'])+"]);echo json_encode(true);")
status,_,html=manager.request('/admin?lang=en#admin-campaigns')
check(status==200 and 'Campaigns are currently unavailable' in html and 'data-campaign-editor' not in html,'Unverified Admin retains dashboard without campaign editing')
sql("$db->prepare('UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute(["+str(manager.account['id'])+"]);echo json_encode(true);")
print(f'PASS: {checks} campaign authoring/confirmation/readiness/retention/retired-route HTTP assertions; no provider send.')
subprocess.run([os.environ['ATHAR_TEST_NODE'],str(fixture.root/'tests/admin_campaign_browser_test.cjs'),owner.email],check=True,env=os.environ)
