"""Event lifecycle acceptance against the generated copied-tree fixture only."""
import os
import re
import subprocess
import admin_authorization_http_test as previous

fixture = previous.fixture
session = previous.Session(previous.actor('admin'))
root = fixture.ROOT
def submit(fields):
    _,_,session.page = session.request('/admin?lang=en')
    return session.request('/admin', {'_csrf':fixture.csrf(session.page),**fields})
def lesson_row(identifier):
    return fixture.service("$q=$db->prepare('SELECT * FROM lessons WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")

checks = 0
def check(ok, label):
    global checks
    if not ok: raise AssertionError(label)
    checks += 1

def sql(program): return fixture.service(program)
def record(identifier):
    return sql("$q=$db->prepare('SELECT * FROM lesson_events WHERE lesson_id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")
def create_lesson(index):
    return sql("$db->prepare('INSERT INTO lessons(title,description,youtube_id,source_url) VALUES (?,?,?,?)')->execute(['Event fixture "+str(index)+"','Retained source description','E"+format(index,'010d')+"','https://youtu.be/E"+format(index,'010d')+"']);echo json_encode((int)$db->lastInsertId());")

identifier = create_lesson(1)
fields = {'kind':'lesson-event','lesson_id':identifier,'expected_revision':'0','status':'scheduled','visibility':'public','schedule_timezone':'UTC','scheduled_local':'2027-02-20T14:30:59.123','title_ar':'','description_ar':'','is_live_event':'on','title_en':'English event','description_en':'English description','topics_ar':'موضوع أول،موضوع ثان','topics_en':'First topic\nSecond topic'}
status,url,page = submit(fields)
saved = record(identifier)
check(status == 200 and saved is not False and int(saved['revision']) == 1 and 'event_edit='+str(identifier) in url, 'Real Admin event creation returns its saved editor')
check(saved['scheduled_at'] == '2027-02-20 14:30:59.123', 'Milliseconds survive the real HTTP/database write')
check(lesson_row(identifier)['title'] == 'Event fixture 1' and lesson_row(identifier)['description'] == 'Retained source description', 'Blank creation fields preserve real lesson metadata')
check('2027-02-20T14:30:59.123' in page and 'value="+00:00"' in page, 'Reopened form preserves full local precision and offset')
check('English event' in page and 'موضوع ثان' in page, 'Canonical translations and Arabic comma topics reopen')
fields.update(expected_revision='1',title_ar='Event fixture 1',description_ar='Retained source description')
for change in [{'scheduled_local':'2027-02-30T12:30'}, {'schedule_timezone':'America/New_York','scheduled_local':'2027-03-14T02:30'}, {'schedule_timezone':'America/New_York','scheduled_local':'2027-11-07T01:30'}, {'topics_en':','.join('Topic '+str(i) for i in range(13))}, {'estimated_duration_minutes':'1.5'}, {'related_page_start':'2'}, {'status':'live'}, {'title_ar':'x'}]:
    _,_,error = submit({**fields,**change})
    check('data-admin-form-error' in error and record(identifier) == saved, 'Invalid time/topics/context/live confirmation cannot mutate the saved event')
status,url,page = submit({**fields,'clear_english_translation':'on'})
check(record(identifier)['revision'] == 2 and 'value="English event"' not in page, 'Explicit English clear wins even with prefilled title/description')
fields['expected_revision']='2'
submit({**fields,'schedule_timezone':'America/New_York','scheduled_local':'2027-11-07T01:30:59.123','schedule_offset':'-05:00','clear_arabic_description':'on'})
saved = record(identifier)
check(saved['scheduled_at'] == '2027-11-07 06:30:59.123' and lesson_row(identifier)['description'] is None, 'Explicit fold offset selects exact instant and explicit description clear works')
_,_,conflict = submit({**fields,'title_ar':'My unsaved competing draft'})
check('data-event-conflict' in conflict and 'My unsaved competing draft' in conflict and 'name="expected_revision" value="2"' in conflict, 'Stale submission retains its draft and original revision')
check(record(identifier) == saved and re.search(r'<button[^>]*disabled[^>]*>Save event',conflict), 'Conflict cannot overwrite saved data and disables resubmission')
changes = sql("$q=$db->prepare('SELECT COUNT(*) FROM lesson_event_changes WHERE event_id=?');$q->execute(["+str(saved['id'])+"]);echo json_encode((int)$q->fetchColumn());")
check(changes == 3, 'Only three successful edits created durable changes')
check(sql("$q=$db->prepare('SELECT change_type FROM lesson_event_changes WHERE event_id=? AND revision=2');$q->execute(["+str(saved['id'])+"]);echo json_encode($q->fetchColumn());") == 'metadata_changed', 'Unchanged full-precision time does not emit a false reschedule')
check(sql("$q=$db->prepare(\"SELECT COUNT(*) FROM audit_logs WHERE action='lesson-event-scheduled' AND target_type='lesson_event' AND target_id=?\");$q->execute(["+str(saved['id'])+"]);echo json_encode((int)$q->fetchColumn());") == 3, 'Every accepted event edit writes one operational audit row')
fields.update(expected_revision='3',scheduled_local='2027-11-07T06:30:59.123',status='live',confirm_live='on')
submit(fields); live = record(identifier)
check(live['status'] == 'live' and live['live_source'] == 'admin' and live['live_confirmed_at'] is not None and lesson_row(identifier)['is_published'] == 1, 'Explicit confirmation records real Admin provenance and canonical publication')
_,_,error = submit({**fields,'expected_revision':'4','confirm_live':''})
check('Confirm that you have checked' in error and record(identifier) == live, 'Empty confirmation cannot claim an affirmative check')
# A checkbox omitted by a real form is the authoritative false value.
omitted = dict(fields,expected_revision='5' if record(identifier)['revision'] == 5 else '4'); omitted.pop('confirm_live')
before = record(identifier); _,_,error = submit(omitted)
check('Confirm that you have checked' in error and record(identifier) == before, 'Live web edits require a new explicit confirmation')

# Pagination must use all events, not an arbitrary first fifty.
for index in range(2,57):
    lesson = create_lesson(index)
    sql("$db->prepare(\"INSERT INTO lesson_events(lesson_id,status,schedule_timezone,visibility,revision,created_at,updated_at) VALUES (?,'draft','UTC','private',1,UTC_TIMESTAMP(),UTC_TIMESTAMP())\")->execute(["+str(lesson)+"]);echo json_encode(true);")
total = sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM lesson_events')->fetchColumn());")
_,_,page = session.request('/admin?lang=en&events_page=2&event_edit='+str(identifier))
check(str(total)+' events' in page and 'Page 2 of '+str((total+19)//20) in page, 'Real total and page count cover records beyond fifty')
check(page.count('data-event-record=') >= 20 and 'events_page=2' in page and 'event_edit='+str(identifier) in page, 'Focused record outside the page and preference context remain available')
creation = page.split('admin-event-create',1)[1].split('</details>',1)[0]
check('Event fixture 56</option>' not in creation and 'Event fixture 1</option>' not in creation, 'Create selector excludes existing events across every page')
sql("$db->prepare('UPDATE lesson_events SET schedule_timezone=? WHERE lesson_id=?')->execute(['Invalid/Zone',"+str(identifier)+"]);echo json_encode(true);")
_,_,page = session.request('/admin?lang=en&event_edit='+str(identifier))
check('time data could not be read' in page and record(identifier)['schedule_timezone'] == 'Invalid/Zone', 'Malformed stored time is surfaced without silently rewriting it')
sql("$db->prepare('UPDATE lesson_events SET schedule_timezone=? WHERE lesson_id=?')->execute(['UTC',"+str(identifier)+"]);$db->prepare('UPDATE users SET email_verified_at=NULL WHERE id=?')->execute(["+str(session.account['id'])+"]);echo json_encode(true);")
status,_,page = session.request('/admin?lang=en')
check(status == 200 and 'requires a verified Admin email' in page and 'data-event-form' not in page, 'Unverified existing Admin can open the dashboard without event edits or a whole-page failure')
before = record(identifier); denied,_,_ = submit(dict(fields,expected_revision=before['revision']))
check(denied == 403 and record(identifier) == before, 'Unverified Admin event mutation is forbidden')
sql("$db->prepare('UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute(["+str(session.account['id'])+"]);echo json_encode(true);")
print(f'PASS: {checks} real event HTTP assertions plus120 prior authentication/authorization HTTP checks.')
if os.environ.get('ATHAR_TEST_NODE'):
    for index in range(60,68): create_lesson(index)
    subprocess.run([os.environ['ATHAR_TEST_NODE'],str(root/'tests/admin_event_browser_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=session.account['email']),check=True,timeout=180)
