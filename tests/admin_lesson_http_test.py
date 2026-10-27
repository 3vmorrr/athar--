"""Full lesson authoring/provider/hierarchy acceptance in the isolated copy only."""
import os
import secrets
import subprocess
import time
import admin_series_http_test as previous

fixture, submit, session, root, api = previous.fixture, previous.submit, previous.session, previous.root, previous.api
checks = 0


def check(ok, label):
    global checks
    if not ok: raise AssertionError(label)
    checks += 1


def row(identifier):
    return fixture.service("$q=$db->prepare('SELECT * FROM lessons WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")


def by_video(video):
    return fixture.service("$q=$db->prepare('SELECT id FROM lessons WHERE youtube_id=?');$q->execute(['"+video+"']);echo json_encode((int)$q->fetchColumn());")


suffix = secrets.token_hex(5)
title = 'Lesson fixture '+suffix
video = 'L'+suffix
fields = {'kind':'lesson','title':title,'description':'Lesson fixture <script>lesson_test()</script>','source_url':'https://www.youtube.com/watch?v='+video,'scholar_id':'0','series_id':'0','sort_order':'-11'}
status,url,page = submit(fields); identifier = by_video(video)
check(status == 200 and identifier > 0 and url.endswith('#admin-content'), 'Admin creates a standalone provider lesson')
saved = row(identifier)
check(saved['scholar_id'] is None and saved['series_id'] is None and int(saved['is_published']) == 0 and int(saved['sort_order']) == -11, 'Standalone draft/order and optional associations are genuine')
check('Add a YouTube lesson' in page and '&lt;script&gt;lesson_test()&lt;/script&gt;' in page and '<script>lesson_test()</script>' not in page, 'Lesson metadata is escaped and editorial controls localized')
edit = {**fields,'kind':'content','content_type':'lesson','content_action':'update','id':identifier}
for change in [{'title':'x'},{'title':'t'*221},{'description':'d'*4001},{'source_url':'http://www.youtube.com/watch?v='+video},{'source_url':'https://youtube.com.example.invalid/watch?v='+video},{'source_url':'https://user:pass@www.youtube.com/watch?v='+video},{'source_url':'https://www.youtube.com/watch?v=short'},{'sort_order':'1.5'},{'sort_order':'100001'},{'scholar_id':'-1'},{'series_id':'-1'},{'scholar_id':'999999999'},{'series_id':'999999999'}]:
    _,_,page = submit({**edit,**change})
    check('data-admin-form-error' in page and row(identifier) == saved, 'Invalid lesson metadata/provider/association cannot mutate the row')
submit({**fields,'title':'Must not duplicate'})
check(by_video(video) == identifier and 'already registered in another lesson' in submit({**fields,'title':'Still must not duplicate'})[2], 'Duplicate provider identity gives a safe actionable error')
check(api('/lessons/'+str(identifier)+'/watch')[0] == 404, 'Native Watch cannot open a draft lesson')
submit({'kind':'content','content_type':'lesson','content_action':'toggle','id':identifier})
status,watch = api('/lessons/'+str(identifier)+'/watch')
check(status == 200 and watch['data']['youtube_id'] == video and watch['data']['series'] is None, 'Published standalone Watch contract retains exact provider ID and genuine hierarchy')

series_title = 'Lesson series '+suffix
submit({'kind':'series','title':series_title,'scholar_id':previous.scholars[0],'sort_order':'-10','publish':'1'})
series = fixture.service("$q=$db->prepare('SELECT id FROM series WHERE title=?');$q->execute(['"+series_title+"']);echo json_encode((int)$q->fetchColumn());")
submit({**edit,'series_id':series,'scholar_id':'0','publish':'1'})
check(int(row(identifier)['series_id']) == series and int(row(identifier)['scholar_id']) == previous.scholars[0], 'Series selection derives its canonical scholar')
linked = row(identifier)
_,_,page = submit({**edit,'series_id':series,'scholar_id':previous.scholars[1],'publish':'1'})
check('does not match the series scholar' in page and row(identifier) == linked, 'Conflicting explicit scholar/series cannot be saved')
status,watch = api('/lessons/'+str(identifier)+'/watch')
check(status == 200 and int(watch['data']['series']['id']) == series and int(watch['data']['playlist_preview'][0]['id']) == identifier, 'Canonical native Watch retains series/playlist hierarchy')
fixture.service("$db->prepare('UPDATE series SET is_published=0 WHERE id=?')->execute(["+str(series)+"]);echo json_encode(true);")
check(api('/lessons/'+str(identifier)+'/watch')[0] == 404, 'Unpublished series cannot leak a Watch lesson')
fixture.service("$db->prepare('UPDATE series SET is_published=1 WHERE id=?')->execute(["+str(series)+"]);echo json_encode(true);")
fixture.service("$db->prepare('UPDATE scholars SET is_published=0 WHERE id=?')->execute(["+str(previous.scholars[0])+"]);echo json_encode(true);")
check(api('/lessons/'+str(identifier)+'/watch')[0] == 404, 'Unpublished scholar cannot leak a Watch lesson')
fixture.service("$db->prepare('UPDATE scholars SET is_published=1 WHERE id=?')->execute(["+str(previous.scholars[0])+"]);echo json_encode(true);")
submit({**edit,'publish':'1'})
check(row(identifier)['series_id'] is None and row(identifier)['scholar_id'] is None, 'Lesson can return to explicit standalone state')
for url in ['https://youtu.be/'+video,'https://www.youtube-nocookie.com/embed/'+video]:
    submit({**edit,'source_url':url,'publish':'1'})
    check(row(identifier)['youtube_id'] == video and row(identifier)['source_url'] == url, 'Approved URL variants preserve the canonical provider ID')
changed = 'N'+suffix
submit({**edit,'source_url':'https://youtu.be/'+changed,'publish':'1'})
check(row(identifier)['youtube_id'] == changed, 'Authorized standalone provider correction actually persists')
case_video = 'n'+suffix
submit({**fields,'title':'Case-distinct provider fixture','source_url':'https://youtu.be/'+case_video,'publish':'1'})
case_id = by_video(case_video)
check(case_id > 0 and case_id != identifier and api('/lessons/'+str(case_id)+'/watch')[1]['data']['youtube_id'] == case_video, 'Case-distinct provider IDs remain separate in actual native Watch HTTP')

event = fixture.service("$db->prepare(\"INSERT INTO lesson_events(lesson_id,status,created_at,updated_at) VALUES (?,'draft',UTC_TIMESTAMP(),UTC_TIMESTAMP())\")->execute(["+str(identifier)+"]);$id=(int)$db->lastInsertId();$db->prepare(\"INSERT INTO lesson_event_changes(event_id,revision,change_type,payload_json,created_at) VALUES (?,1,'created','{}',UTC_TIMESTAMP())\")->execute([$id]);echo json_encode($id);")
_,_,page = submit({'kind':'content','content_type':'lesson','content_action':'delete','id':identifier})
check('linked event history and cannot be deleted' in page and row(identifier) is not False, 'Lesson deletion cannot erase linked event history')
check(fixture.service("$q=$db->prepare('SELECT COUNT(*) FROM lesson_event_changes WHERE event_id=?');$q->execute(["+str(event)+"]);echo json_encode((int)$q->fetchColumn());") == 1, 'Rejected deletion retains the durable event change record')
_,_,page = submit({**edit,'source_url':'https://youtu.be/Z'+suffix,'publish':'1'})
check('Create a separate lesson for a new video' in page and row(identifier)['youtube_id'] == changed, 'Event-backed video identity cannot change underneath its lifecycle record')
submit({**edit,'title':'Updated event-backed metadata','source_url':'https://youtu.be/'+changed,'publish':'1'})
check(row(identifier)['title'] == 'Updated event-backed metadata', 'Event-backed lesson metadata remains editable without replacing video identity')

# Explicit content deletion cleans its own dependents, preserving another lesson.
legacy = previous.previous.lesson_id
uid = session.account['id']
fixture.service("$db->prepare(\"INSERT INTO user_favorites(user_id,target_type,target_id) VALUES (?,'lesson',?)\")->execute(["+str(uid)+","+str(case_id)+"]);$q=$db->prepare('INSERT INTO video_progress(user_id,lesson_id,position_seconds,duration_seconds) VALUES (?,?,10,100) ON DUPLICATE KEY UPDATE position_seconds=10');$q->execute(["+str(uid)+","+str(case_id)+"]);$q->execute(["+str(uid)+","+str(legacy)+"]);$db->prepare(\"INSERT INTO lesson_comments(lesson_id,user_id,body,status) VALUES (?,?,'Owned cleanup fixture','pending')\")->execute(["+str(case_id)+","+str(uid)+"]);echo json_encode(true);")
submit({'kind':'content','content_type':'lesson','content_action':'delete','id':case_id})
counts = fixture.service("$id="+str(case_id)+";$q=$db->prepare(\"SELECT (SELECT COUNT(*) FROM user_favorites WHERE target_type='lesson' AND target_id=?)+(SELECT COUNT(*) FROM video_progress WHERE lesson_id=?)+(SELECT COUNT(*) FROM lesson_comments WHERE lesson_id=?)\");$q->execute([$id,$id,$id]);echo json_encode((int)$q->fetchColumn());")
check(row(case_id) is False and counts == 0, 'Explicit unreferenced lesson deletion cleans only its favorites/progress/comments')
check(fixture.service("$q=$db->prepare('SELECT position_seconds FROM video_progress WHERE user_id=? AND lesson_id=?');$q->execute(["+str(uid)+","+str(legacy)+"]);echo json_encode((int)$q->fetchColumn());") == 10, 'Unrelated owned playback data survives lesson deletion')
for action in ['update','toggle','delete']:
    _,_,page = submit({**edit,'id':case_id,'content_action':action})
    check('This lesson no longer exists.' in page, 'Missing lesson actions cannot claim success')

# A concurrent valid empty-series reparent must be reread by lesson creation.
race_title = 'Lesson parent race '+suffix
submit({'kind':'series','title':race_title,'scholar_id':previous.scholars[0]})
race_series = fixture.service("$q=$db->prepare('SELECT id FROM series WHERE title=?');$q->execute(['"+race_title+"']);echo json_encode((int)$q->fetchColumn());")
program = fixture.setup+"$db->beginTransaction();$db->prepare('UPDATE series SET scholar_id=? WHERE id=?')->execute(["+str(previous.scholars[1])+","+str(race_series)+"]);echo \"LOCKED\\n\";flush();sleep(2);$db->commit();"
process = subprocess.Popen([fixture.php,'-r',program],cwd=root,env=fixture.environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
    check(process.stdout.readline().strip() == 'LOCKED', 'Concurrent parent reparent holds a real row lock')
    started = time.monotonic(); _,_,page = submit({**fields,'series_id':race_series,'scholar_id':previous.scholars[0],'source_url':'https://youtu.be/T'+suffix})
    check(time.monotonic()-started >= 1 and 'does not match the series scholar' in page and by_video('T'+suffix) == 0, 'Lesson creation waits and refuses a newly conflicting parent')
finally: process.communicate(timeout=10)
check(process.returncode == 0, 'Concurrent parent transaction completes cleanly')
print(f'PASS: {checks} actual lesson/provider/native-Watch assertions (plus193 prior HTTP and135 prior browser).')
if os.environ.get('ATHAR_TEST_NODE'):
    subprocess.run([os.environ['ATHAR_TEST_NODE'],str(root/'tests/admin_lesson_browser_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=session.account['email']),check=True,timeout=120)
