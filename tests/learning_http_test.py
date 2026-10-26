import json,urllib.request,urllib.error,re
from pathlib import Path
fixture=json.loads((Path(__file__).resolve().parents[1]/'storage/engineering/native-fixture.json').read_text())
assert fixture.get('fixture_only') is True and re.fullmatch(r'athar_test_native_[a-f0-9]{12}',fixture['database'])
base='http://127.0.0.1:8004/api/v1/';checks=0

def req(path,token=None,body=None,method=None):
 h={'Accept-Language':'ar'}
 if token:h['Authorization']='Bearer '+token
 if body is not None:h['Content-Type']='application/json'
 r=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=h,method=method)
 try:
  with urllib.request.urlopen(r,timeout=15) as response:return response.status,json.load(response)['data']
 except urllib.error.HTTPError as e:return e.code,None

def check(x):
 global checks
 assert x
 checks+=1
_,login=req('auth/login',body={'email':'native.reader@example.test','password':'NativeFixture123!'})
t=login['session']['access_token']
try:
 _,progress=req('me/lesson-progress?lesson_id=2',t)
 status,watch=req('lessons/2/watch',t);check(status==200);check(watch['lesson_number']==2);check(watch['series']['lesson_count']==3);check(watch['progress']==progress);check(watch['discussion']['pagination']['total']>=1)
 status,page=req('lessons/2/discussion?parent_id=1',t);check(status==200 and len(page['items'])==1)
 check(req('lessons/1/discussion?parent_id=1',t)[0]==404)
 check(req('admin/comments',t)[0]==403);check(req('lessons/2/watch')[0]==401)
 status,series=req('series/1/lessons',t);check(status==200 and len(series['items'])==3)
 status,scholar=req('scholars/1/profile',t);check(status==200 and scholar['name']=='Interface test scholar' and 'lessons' not in scholar)
 status,standalone=req('lessons?scholar_id=1&standalone=1',t);check(status==200 and [row['id'] for row in standalone['items']]==[4])
 print(f'PASS: {checks} real HTTP lesson/discussion/series/authorization assertions.')
finally:req('auth/logout',t,body={})
