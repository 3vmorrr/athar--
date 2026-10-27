"""Immediate editorial migration/CAS/deletion/private-progress gate."""
import json
import admin_batch_fixture as fixture
checks=0
def check(ok,label):
    global checks
    if not ok: raise AssertionError(label)
    checks+=1
session=fixture.Session();token=session.token();user=fixture.Session('user');user_token=user.token();submit=session.submit;sql=fixture.service
def row(identifier):return sql("$q=$db->prepare('SELECT * FROM adhkar_items WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")
def history(identifier):return sql("$q=$db->prepare('SELECT revision,action,snapshot_json,adhkar_id FROM adhkar_item_revisions WHERE source_item_id=? ORDER BY revision');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetchAll());")
fields={'kind':'adhkar','title':'Editorial fixture','formula':'Generated editorial fixture text','source_reference':'Fixture attribution','source_url':'https://example.invalid/source','category':'Fixture category','repeat_count':'','reviewed':'on','publish':'on','sort_order':'-4'}
status,_,page=submit(fields)
identifier=sql("echo json_encode((int)$db->query(\"SELECT id FROM adhkar_items WHERE title='Editorial fixture'\")->fetchColumn());")
check(status==200 and identifier>0 and row(identifier)['repeat_count'] is None and row(identifier)['revision']==1,'Create preserves nullable source count and canonical revision1')
check(history(identifier)[0]['action']=='created' and json.loads(history(identifier)[0]['snapshot_json'])['repeat_count'] is None,'Creation snapshot records NULL without inventing a target')
check(fixture.api('/adhkar/'+str(identifier),user_token)[1]['data']['repeat_count'] is None,'Native catalog preserves optional count')
check(fixture.api('/me/adhkar-progress',user_token,'PUT',{'item_id':identifier,'current_count':1,'expected_revision':0})[0]==422,'Native private writes cannot fabricate a fixed target for NULL source count')
before=row(identifier)
edit=dict(fields,kind='content',content_type='adhkar',content_action='update',id=identifier,expected_revision='1')
for change in [{'title':'x'},{'formula':'x'},{'source_reference':'x'},{'source_url':'https://user:pass@example.invalid/source'},{'repeat_count':'0'},{'repeat_count':'1.5'},{'repeat_count':'1001'},{'sort_order':'1.5'},{'sort_order':'100001'}]:
    _,_,page=submit({**edit,**change});check('data-admin-form-error' in page and row(identifier)==before and len(history(identifier))==1,'Invalid attribution/count/order creates no mutation or history')
no_review={**edit};no_review.pop('reviewed');submit(no_review)
check(row(identifier)==before,'Missing explicit review cannot save an item')
submit({**edit,'repeat_count':'3'});edit['expected_revision']='2';edit['repeat_count']='3'
check(row(identifier)['revision']==2 and len(history(identifier))==2,'One accepted edit appends exactly one snapshot')
status,_=fixture.api('/me/adhkar-progress',user_token,'PUT',{'item_id':identifier,'current_count':2,'expected_revision':0})
check(status==200,'A source-backed fixed target supports actual owned progress')
_,_,page=submit({**edit,'expected_revision':'1','title':'Unsaved competing draft'})
check('data-adhkar-conflict' in page and 'Unsaved competing draft' in page and row(identifier)['revision']==2,'Stale editorial draft is retained without overwriting')
sql("$db->exec(\"CREATE TRIGGER editorial_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture audit failure'\");echo json_encode(true);",control=True)
try:
    submit({**edit,'title':'Must roll back'})
    check(row(identifier)['title']=='Editorial fixture' and row(identifier)['revision']==2 and len(history(identifier))==2,'Audit error rolls back both content and snapshot')
finally:sql("$db->exec('DROP TRIGGER editorial_audit_failure');echo json_encode(true);",control=True)
submit({'kind':'content','content_type':'adhkar','content_action':'delete','id':identifier,'expected_revision':'2'})
check(row(identifier) is False and len(history(identifier))==3 and history(identifier)[2]['action']=='deleted' and history(identifier)[2]['adhkar_id'] is None,'Explicit content deletion leaves a durable unlinked public snapshot')
status,progress=fixture.api('/me/adhkar-progress?item_id='+str(identifier),user_token)
check(status==200 and len(progress['data'])==1 and progress['data'][0]['current_count']==2,'Editorial deletion preserves the exact owned private progress')

def comment(identifier):return sql("$q=$db->prepare('SELECT * FROM lesson_comments WHERE id=?');$q->execute(["+str(identifier)+"]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")
def new_comment(parent=None):
    return sql("$db->prepare(\"INSERT INTO lesson_comments(user_id,lesson_id,parent_id,body,status) VALUES (?,1,?,'Generated moderation fixture','pending')\")->execute(["+str(user.account['id'])+","+('null' if parent is None else str(parent))+"]);echo json_encode((int)$db->lastInsertId());")
root=new_comment();reply=new_comment(root)
decision={'kind':'comment-review','comment_id':root,'expected_revision':'1','decision':'visible','reason':'Reviewed fixture attribution'}
check(fixture.api('/admin/comments',user_token)[0]==403,'Ordinary user cannot access moderation queues')
for change in [{'reason':''},{'reason':'x'},{'reason':'r'*501},{'decision':'invalid'}]:
    _,_,page=submit({**decision,**change});check('data-admin-form-error' in page and comment(root)['revision']==1,'Invalid moderation decision/reason has no effect')
submit(decision)
check(comment(root)['status']=='visible' and comment(root)['revision']==2 and comment(root)['moderation_reason']==decision['reason'],'Publishing stores actual reason and revision atomically')
_,_,page=submit({**decision,'decision':'hidden','reason':'Retained stale reason'})
check('data-comment-conflict' in page and 'Retained stale reason' in page and comment(root)['status']=='visible','Stale moderation preserves its reason and current published state')
_,_,page=submit({**decision,'expected_revision':'2','decision':'delete'})
check('Review and delete replies before' in page and comment(root) is not False and comment(reply) is not False,'Parent deletion cannot cascade over replies implicitly')
sql("$db->exec(\"CREATE TRIGGER moderation_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture audit failure'\");echo json_encode(true);",control=True)
try:
    submit({**decision,'comment_id':reply,'decision':'delete'})
    check(comment(reply) is not False and sql("echo json_encode((int)$db->query('SELECT COUNT(*) FROM lesson_comment_moderation_events WHERE source_comment_id="+str(reply)+"')->fetchColumn());")==0,'Audit failure rolls back deletion and its durable decision')
finally:sql("$db->exec('DROP TRIGGER moderation_audit_failure');echo json_encode(true);",control=True)
submit({**decision,'comment_id':reply,'decision':'delete'});submit({**decision,'expected_revision':'2','decision':'delete'})
check(comment(root) is False and comment(reply) is False,'Explicit reply-first decisions can delete a thread safely')
events=sql("echo json_encode($db->query('SELECT comment_id,source_comment_id,revision,action,reason FROM lesson_comment_moderation_events ORDER BY id')->fetchAll());")
check(len(events)==3 and all(e['comment_id'] is None for e in events) and events[-1]['reason']==decision['reason'],'All three successful moderation decisions survive deletion without bodies/private data')
api_comment=new_comment();status,_=fixture.api('/admin/comments/'+str(api_comment),token,'PUT',{'status':'visible','expected_revision':1,'reason':'Canonical API fixture review'})
check(status==200 and comment(api_comment)['status']=='visible','Admin API and web use the same reason/revision persistence contract')
check(fixture.api('/admin/comments/'+str(api_comment),token,'PUT',{'status':'hidden','expected_revision':2})[0]==422,'Admin API cannot omit the required decision reason')
sql("$db->prepare('UPDATE users SET email_verified_at=NULL WHERE id=?')->execute(["+str(session.account['id'])+"]);echo json_encode(true);")
status,_,page=session.request('/admin?lang=en#admin-comments')
check(status==200 and 'Moderation requires a verified Admin email' in page and 'data-comment-form' not in page,'Unverified existing Admin receives a truthful unavailable moderation panel')
check(submit({**decision,'comment_id':api_comment,'expected_revision':'2'})[0]==403 and comment(api_comment)['revision']==2,'Unverified privileged actor cannot mutate moderation')
print(f'PASS: {checks} focused editorial/private-progress/moderation HTTP assertions; no unrelated regression suite.')
