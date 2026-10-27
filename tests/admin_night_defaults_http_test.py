"""Public Night Prayer defaults: owner CAS, attribution, and private-data boundary."""
import json
import admin_batch_fixture as fixture
checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1
owner=fixture.Session('owner');manager=fixture.Session();reader=fixture.Session('user');token=reader.token();sql=fixture.service
def config():return fixture.api('/night-prayer/config',token)
def settings():return sql("echo json_encode($db->query('SELECT setting_key,setting_value FROM platform_settings ORDER BY setting_key')->fetchAll(PDO::FETCH_KEY_PAIR));")
status,initial=config()
check(status==200 and initial['data']['revision']==1 and initial['data']['defaults']['calculation_method']=='MUSLIM_WORLD_LEAGUE','Public API supplies a typed canonical default without initializing private state')
check(initial['data']['defaults']['high_latitude_rule']=='MIDDLE_OF_THE_NIGHT' and initial['data']['source']['url']=='https://github.com/batoulapps/adhan-java/','Published identifiers and attribution match the actual native calculator/library')
status,_=fixture.api('/night-prayer/preferences',token,'PUT',{'expected_revision':0,'timezone':'Africa/Cairo','city':'Private fixture location','calculation_method':'EGYPTIAN'})
check(status==200,'A user retains independent personal calculation preferences')
private_before=fixture.api('/night-prayer/preferences',token)[1]['data']
sql("$db->prepare(\"INSERT INTO platform_settings(setting_key,setting_value) VALUES ('private_fixture_canary','Never publish this fixture value') ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)\")->execute();echo json_encode(true);")
fields={'kind':'night-prayer-defaults','expected_revision':'1','enabled':'on','calculation_method':'KARACHI','madhab':'hanafi','high_latitude_rule':'TWILIGHT_ANGLE','target_verses':'300','source_name_ar':'مصدر اختبار عام','source_name_en':'Public fixture source','source_url':'https://example.invalid/source','intro_ar':'نص اختبار عام','intro_en':'Public introduction fixture'}
before=settings();denied,_,_=manager.submit(fields)
check(denied==403 and settings()==before,'Content Admin cannot alter owner-only public defaults')
for change in [{'calculation_method':'InventedMethod'},{'madhab':'invalid'},{'high_latitude_rule':'invalid'},{'target_verses':'0'},{'target_verses':'6237'},{'target_verses':'1.5'},{'source_name_en':'x'},{'source_url':'http://example.invalid'},{'source_url':'https://user:pass@example.invalid'},{'intro_en':'i'*1201},{'private_note':'Must not accept this field'}]:
    _,_,page=owner.submit({**fields,**change});check('data-admin-form-error' in page and settings()==before,'Invalid public metadata, unsupported method or private field cannot mutate settings')
status,url,page=owner.submit(fields)
check(status==200 and url.endswith('#admin-night-prayer') and config()[1]['data']['revision']==2,'Accepted owner save increments the canonical public revision once')
published=config()[1]['data']
check(published['defaults']['calculation_method']=='KARACHI' and published['defaults']['target_verses']==300 and published['intro']['en']==fields['intro_en'],'Actual saved method, target and translations reach the API')
check('Never publish' not in json.dumps(published) and 'Private fixture location' not in json.dumps(published) and fixture.api('/night-prayer/preferences',token)[1]['data']==private_before,'Public publication neither exposes nor alters personal preferences or unrelated settings')
_,_,page=owner.submit({**fields,'intro_en':'Retained stale public draft'})
check('data-night-defaults-conflict' in page and 'Retained stale public draft' in page and config()[1]['data']==published,'A stale public-defaults draft is retained without overwriting the newer revision')
off={**fields,'expected_revision':'2'};off.pop('enabled');owner.submit(off)
check(config()[1]['data']['enabled'] is False and fixture.api('/night-prayer/preferences',token)[1]['data']==private_before,'Disabling suggestions does not disable or rewrite private prayer preferences')
check(fixture.api('/night-prayer/preferences',token,'PUT',{'expected_revision':1,'city':'User explicitly edited city'})[0]==200,'Private self-service preferences remain writable while public suggestions are disabled')
before=settings();sql("$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(['MuslimWorldLeague','night_prayer_default_calculation_method']);$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(['angle_based','night_prayer_default_high_latitude_rule']);echo json_encode(true);")
aliased=settings();data=config()[1]['data']
check(data['defaults']['calculation_method']=='MUSLIM_WORLD_LEAGUE' and data['defaults']['high_latitude_rule']=='TWILIGHT_ANGLE' and settings()==aliased,'Legacy supported aliases normalize for clients without rewriting stored values')
sql("$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(['InvalidStoredMethod','night_prayer_default_calculation_method']);echo json_encode(true);")
status,_=config();admin_status,_,page=owner.request('/admin?lang=en')
check(status==503 and admin_status==200 and 'Public defaults could not be read' in page and 'data-night-defaults-form' not in page,'Corrupt stored configuration is unavailable, never a fabricated default or whole-Admin failure')
check(settings()['night_prayer_default_calculation_method']=='InvalidStoredMethod','Corrupt public metadata is preserved for explicit repair')
sql("$db->prepare('UPDATE platform_settings SET setting_value=? WHERE setting_key=?')->execute(['MUSLIM_WORLD_LEAGUE','night_prayer_default_calculation_method']);echo json_encode(true);")
check('id="admin-night-prayer"' not in manager.request('/admin?lang=en')[2],'Non-owner receives no public-defaults control panel')
print(f'PASS: {checks} focused public-defaults/CAS/private-boundary HTTP assertions; no personal prayer records accessed by Admin.')
