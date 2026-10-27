"""Series association/publication/dependency acceptance in generated schema/storage."""
import json
import os
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request

import admin_scholar_http_test as previous  # Actual scholar and adjacent lesson regressions.
fixture, submit, session, root = previous.fixture, previous.submit, previous.session, previous.root
checks = 0


def check(ok, label):
    global checks
    if not ok: raise AssertionError(label)
    checks += 1


def row(identifier):
    return fixture.service("$q=$db->prepare('SELECT * FROM series WHERE id=?');$q->execute([" + str(identifier) + "]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")


suffix = secrets.token_hex(5)
scholars = []
for index in [1, 2]:
    name = 'Series scholar ' + suffix + str(index)
    submit({'kind':'scholar','name':name,'publish':'1'})
    scholars.append(fixture.service("$q=$db->prepare('SELECT id FROM scholars WHERE name=?');$q->execute(['" + name + "']);echo json_encode((int)$q->fetchColumn());"))
title = 'Series fixture ' + suffix
fields = {'kind':'series','title':title,'description':'Series description <script>test()</script>','source_url':'https://example.invalid/series','scholar_id':scholars[0],'sort_order':'-91'}
status, url, page = submit(fields)
identifier = fixture.service("$q=$db->prepare('SELECT id FROM series WHERE title=?');$q->execute(['" + title + "']);echo json_encode((int)$q->fetchColumn());")
check(status == 200 and url.endswith('#admin-content') and identifier > 0, 'Admin creates series and returns to content inventory')
saved = row(identifier)
check(int(saved['scholar_id']) == scholars[0] and int(saved['sort_order']) == -91 and int(saved['is_published']) == 0, 'Series association/order/draft state are persisted')
check('Add a series' in page and '&lt;script&gt;test()&lt;/script&gt;' in page and '<script>test()</script>' not in page, 'Series controls are localized and descriptions escaped')
edit = {**fields,'kind':'content','content_type':'series','content_action':'update','id':identifier}
for change in [{'title':'x'},{'title':'t'*221},{'description':'d'*4001},{'source_url':'http://example.invalid'},{'source_url':'https://user:pass@example.invalid'},{'sort_order':'1.5'},{'sort_order':'100001'},{'scholar_id':'-1'},{'scholar_id':'999999999'}]:
    _, _, page = submit({**edit,**change})
    check('data-admin-form-error' in page and row(identifier) == saved, 'Invalid series metadata/association cannot mutate the row')

request = urllib.request.Request(fixture.base+'/api/v1/auth/login',json.dumps({'email':session.account['email'],'password':'AdminFixture123!'}).encode(),{'Content-Type':'application/json'})
token = json.load(urllib.request.urlopen(request))['data']['session']['access_token']


def api(path):
    request = urllib.request.Request(fixture.base+'/api/v1'+path,headers={'Authorization':'Bearer '+token,'Accept-Language':'en'})
    try: response = urllib.request.urlopen(request,timeout=10)
    except urllib.error.HTTPError as error: response = error
    return response.code,json.load(response)


check(api('/series/'+str(identifier))[0] == 404, 'Canonical native catalog excludes a draft series')
submit({'kind':'content','content_type':'series','content_action':'toggle','id':identifier})
status, detail = api('/series/'+str(identifier))
check(status == 200 and int(detail['data']['scholar_id']) == scholars[0], 'Published series exposes its canonical scholar contract')
status, listing = api('/series?scholar_id='+str(scholars[0]))
check(status == 200 and int(listing['data']['items'][0]['id']) == identifier, 'Native catalog uses genuine edited series ordering')
fixture.service("$db->prepare('UPDATE scholars SET is_published=0 WHERE id=?')->execute([" + str(scholars[0]) + "]);echo json_encode(true);")
check(api('/series/'+str(identifier))[0] == 404, 'Unpublished parent hides its series in the canonical catalog')
fixture.service("$db->prepare('UPDATE scholars SET is_published=1 WHERE id=?')->execute([" + str(scholars[0]) + "]);echo json_encode(true);")

video = 'S'+suffix
lesson = fixture.service("$db->prepare(\"INSERT INTO lessons(scholar_id,series_id,title,youtube_id,source_url) VALUES (?,?,'Series dependency fixture',?,'https://example.invalid/fixture')\")->execute([" + str(scholars[0]) + "," + str(identifier) + ",'" + video + "']);echo json_encode((int)$db->lastInsertId());")
_, _, page = submit({**edit,'scholar_id':scholars[1],'publish':'1'})
check('Reassign lessons linked to another scholar' in page and int(row(identifier)['scholar_id']) == scholars[0], 'Series cannot contradict an explicit lesson scholar')
_, _, page = submit({'kind':'content','content_type':'series','content_action':'delete','id':identifier})
check('Move or remove the series lessons' in page and row(identifier), 'Referenced series deletion is refused')
edit_form = next(form for form in re.findall(r'<form[^>]*data-series-form>[\s\S]*?</form>', page) if 'name="id" value="'+str(identifier)+'"' in form)
check('name="publish" checked' in edit_form, 'A failed delete cannot silently uncheck the saved publication field in the editor')
check(fixture.service("$q=$db->prepare('SELECT series_id FROM lessons WHERE id=?');$q->execute([" + str(lesson) + "]);echo json_encode((int)$q->fetchColumn());") == identifier, 'Rejected deletion preserves the lesson association')
fixture.service("$db->prepare('DELETE FROM lessons WHERE id=?')->execute([" + str(lesson) + "]);echo json_encode(true);")
submit({**edit,'scholar_id':scholars[1],'source_url':'','publish':'1'})
check(int(row(identifier)['scholar_id']) == scholars[1] and row(identifier)['source_url'] is None, 'Unreferenced series can change scholar and clear an optional source')
submit({**edit,'scholar_id':'0','publish':'1'})
check(row(identifier)['scholar_id'] is None, 'Series can explicitly remove its optional scholar')
submit({'kind':'content','content_type':'series','content_action':'delete','id':identifier})
check(row(identifier) is False and api('/series/'+str(identifier))[0] == 404, 'Deleting an unreferenced series removes its public contract')
for action in ['update','toggle','delete']:
    _, _, page = submit({**edit,'content_action':action})
    check('This series no longer exists.' in page, 'Missing series cannot report a false mutation success')

# Child FK lock must serialize a delete against a concurrent lesson insertion.
race_title = title + ' race'; submit({**fields,'title':race_title})
race_id = fixture.service("$q=$db->prepare('SELECT id FROM series WHERE title=?');$q->execute(['" + race_title + "']);echo json_encode((int)$q->fetchColumn());")
program = fixture.setup + "$db->beginTransaction();$db->prepare(\"INSERT INTO lessons(scholar_id,series_id,title,youtube_id,source_url) VALUES (?,?,'Concurrent series child',?,'https://example.invalid/race')\")->execute(["+str(scholars[0])+","+str(race_id)+",'R"+suffix+"']);echo \"LOCKED\\n\";flush();sleep(2);$db->commit();"
process = subprocess.Popen([fixture.php,'-r',program],cwd=root,env=fixture.environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
    check(process.stdout.readline().strip() == 'LOCKED', 'Concurrent insertion holds the real parent FK lock')
    started=time.monotonic(); _, _, page=submit({'kind':'content','content_type':'series','content_action':'delete','id':race_id})
    check(time.monotonic()-started >= 1 and 'Move or remove the series lessons' in page, 'Deletion waits and sees the committed new dependency')
    check(row(race_id) is not False, 'Concurrent lesson insertion cannot be orphaned by deletion')
finally: process.communicate(timeout=10)
check(process.returncode == 0, 'Concurrent child insertion completes cleanly')
_, _, failed_save = submit({**previous.lesson_fields,'source_url':'https://www.youtube.com/watch?v=M7lc1UVf-VE','title':'Must not overwrite on constraint failure'})
after = fixture.service("$q=$db->prepare('SELECT title,youtube_id FROM lessons WHERE id=?');$q->execute([" + str(previous.lesson_id) + "]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")
check(('The content could not be saved.' in failed_save or 'already registered in another lesson' in failed_save) and 'SQLSTATE' not in failed_save and after['title'] == previous.lesson_fields['title'] and after['youtube_id'] == previous.lesson_video, 'Real constraint failure is localized without exposing SQL details or partially updating a lesson')
print(f'PASS: {checks} actual series/association/native-catalog assertions (plus162 prior HTTP and68 prior browser).')
if os.environ.get('ATHAR_TEST_NODE'):
    subprocess.run([os.environ['ATHAR_TEST_NODE'],str(root/'tests/admin_series_browser_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=session.account['email']),check=True,timeout=120)
