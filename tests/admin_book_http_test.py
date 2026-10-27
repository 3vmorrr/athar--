"""Immediate rights/download/deletion/CAS gate; no unchanged auth suite is imported."""
import json
import re
import admin_batch_fixture as fixture
checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1
session=fixture.Session();token=session.token();submit=session.submit;sql=fixture.service
def row(identifier): return sql("$q=$db->prepare('SELECT * FROM books WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")
def version(identifier):
    return sql("require 'admin/books.php';$q=$db->prepare('SELECT * FROM books WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode(admin_book_version($q->fetch()));")
def edit(identifier,**changes): return dict(fields,kind='content',content_type='book',content_action='update',id=identifier,expected_state=version(identifier),**changes)
fields={'kind':'book','title':'Catalog only fixture','author':'Fixture author','description':'Retained book description','source_url':'https://example.invalid/source','rights_basis':'Authorized fixture rights','language':'ar','publication_year':'2020','download_url':'','publish':'on'}
status,url,page=submit(fields)
identifier=sql("echo json_encode((int)$db->query(\"SELECT id FROM books WHERE title='Catalog only fixture'\")->fetchColumn());")
check(status == 200 and identifier>0 and row(identifier)['download_url'] is None and row(identifier)['can_download'] == 0, 'Catalog-only creation does not require or invent a download')
status,detail=fixture.api('/books/'+str(identifier),token)
check(status == 200 and detail['data']['can_download'] is False and detail['data']['download_endpoint'] is None, 'Native book detail truthfully has no download action')
check(fixture.api('/books/'+str(identifier)+'/download',token)[0] == 404, 'Native download refuses catalog-only book')
before=row(identifier)
for changes in [{'title':'x'},{'author':'x'},{'description':'d'*12001},{'rights_basis':''},{'source_url':'http://example.invalid/source'},{'source_url':'https://user:pass@example.invalid/source'},{'download_url':'https://user:pass@example.invalid/book.pdf'},{'download_url':'javascript:fixture()'},{'language':'invalid'},{'publication_year':'2020.5'}]:
    _,_,page=submit(edit(identifier,**changes));check('data-admin-form-error' in page and row(identifier)==before,'Invalid book data/rights/credential URL cannot mutate the row')
status,_,_=submit(edit(identifier,download_url='https://example.invalid/book.pdf'))
check(status == 200 and row(identifier)['can_download']==1, 'Optional valid HTTPS download enables canonical availability')
status,payload=fixture.api('/books/'+str(identifier)+'/download',token)
check(status == 200 and payload['data']['url']=='https://example.invalid/book.pdf' and payload['data']['mode']=='direct','Native endpoint retains its direct-download contract')
old=edit(identifier,title='Unsaved stale metadata');submit(edit(identifier,title='Newer saved metadata'))
_,_,page=submit(old)
check('data-book-conflict' in page and 'Unsaved stale metadata' in page and row(identifier)['title']=='Newer saved metadata','Stale metadata retains draft without overwriting a newer book')
_,_,page=submit(dict(old,content_action='delete'))
check('Another editor changed' in page and row(identifier) is not False,'Stale explicit deletion is rejected')
key='b'*40+'.pdf';asset=fixture.root/'storage/books'/key;asset.parent.mkdir(parents=True,exist_ok=True);asset.write_bytes(b'%PDF-1.4\nowned fixture bytes')
sql("$db->prepare('UPDATE books SET file_key=?,file_size=? WHERE id=?')->execute(['"+key+"',"+str(asset.stat().st_size)+","+str(identifier)+"]);echo json_encode(true);")
submit(edit(identifier,title='Preserve local asset',download_url=''))
check(asset.is_file() and row(identifier)['file_key']==key and row(identifier)['can_download']==0,'Ordinary metadata/catalog-only edits retain the registered local PDF')
# Force a real database error after the mutation; both row and owned file must survive.
before=row(identifier);sql("$db->exec(\"CREATE TRIGGER book_test_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture audit failure'\");echo json_encode(true);", control=True)
try:
    _,_,page=submit({'kind':'content','content_type':'book','content_action':'delete','id':identifier,'expected_state':version(identifier)})
    check(row(identifier)==before and asset.is_file() and 'data-admin-form-error' in page,'Audit failure rolls back deletion and preserves its local asset')
finally:sql("$db->exec('DROP TRIGGER book_test_audit_failure');echo json_encode(true);",control=True)
sql("$db->prepare(\"INSERT INTO user_favorites(user_id,target_type,target_id) VALUES (?,'book',?)\")->execute(["+str(session.account['id'])+","+str(identifier)+"]);echo json_encode(true);")
submit({'kind':'content','content_type':'book','content_action':'delete','id':identifier,'expected_state':version(identifier)})
check(row(identifier) is False and not asset.exists(),'Explicit committed deletion retires only its owned local PDF')
check(sql("echo json_encode((int)$db->query(\"SELECT COUNT(*) FROM user_favorites WHERE target_type='book' AND target_id="+str(identifier)+"\")->fetchColumn());")==0,'Explicit deletion cleans only the deleted public-content favorites')
for changes in [{'rights_basis':''},{'source_url':'https://user:pass@example.invalid/source'},{'download_url':'https://user:pass@example.invalid/file.pdf'}]:
    sql("$db->prepare('UPDATE books SET source_url=?,rights_basis=?,download_url=?,can_download=1 WHERE id=1')->execute(['https://example.invalid/source','Fixture rights','https://example.invalid/book.pdf']);$db->prepare('UPDATE books SET "+next(iter(changes))+"=? WHERE id=1')->execute(["+json.dumps(next(iter(changes.values())))+"]);echo json_encode(true);")
    check(fixture.api('/books/1/download',token)[0]==404 and fixture.api('/books/1',token)[1]['data']['can_download'] is False,'Malformed legacy rights/credential URLs cannot bypass native download checks')
sql("$db->prepare('UPDATE books SET source_url=? WHERE id=1')->execute(['https://user:pass@example.invalid/source']);echo json_encode(true);")
check('user:pass' not in json.dumps(fixture.api('/books/1',token)[1]),'Malformed legacy credential sources are redacted from public metadata')
check('user:pass' not in session.request('/admin?lang=en')[2],'Malformed credential links are not exposed in Admin input values either')
submit({**fields,'title':'Cleanup warning fixture'})
cleanup_id=sql("echo json_encode((int)$db->query(\"SELECT id FROM books WHERE title='Cleanup warning fixture'\")->fetchColumn());")
canary=fixture.root/'storage/do-not-delete.pdf';canary.write_bytes(b'Unregistered fixture canary')
sql("$db->prepare('UPDATE books SET file_key=? WHERE id=?')->execute(['../do-not-delete.pdf',"+str(cleanup_id)+"]);echo json_encode(true);")
_,_,page=submit({'kind':'content','content_type':'book','content_action':'delete','id':cleanup_id,'expected_state':version(cleanup_id)})
check(row(cleanup_id) is False and canary.read_bytes()==b'Unregistered fixture canary' and 'local copy could not be cleaned up' in page,'Unsafe registered key never reaches a file operation and reports incomplete cleanup truthfully')
submit({**fields,'title':'Linked book fixture'})
linked_id=sql("echo json_encode((int)$db->query(\"SELECT id FROM books WHERE title='Linked book fixture'\")->fetchColumn());")
sql("$db->exec(\"INSERT IGNORE INTO lesson_events(lesson_id,status,revision,created_at,updated_at) VALUES (1,'draft',1,UTC_TIMESTAMP(),UTC_TIMESTAMP())\");$event=$db->query('SELECT id FROM lesson_events WHERE lesson_id=1')->fetchColumn();$db->prepare('INSERT INTO lesson_event_context(event_id,related_book_id,topics_ar_json,topics_en_json) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE related_book_id=VALUES(related_book_id)')->execute([$event,"+str(linked_id)+",\'[]\',\'[]\']);echo json_encode(true);")
_,_,page=submit({'kind':'content','content_type':'book','content_action':'delete','id':linked_id,'expected_state':version(linked_id)})
check(row(linked_id) is not False and 'event references before deleting' in page,'Explicit deletion cannot silently erase an event’s book reference')
print(f'PASS: {checks} focused book rights/download/CAS/asset-transaction HTTP assertions; no unrelated regression suite.')
