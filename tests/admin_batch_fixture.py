"""Generated-schema HTTP helpers; importing this module runs no acceptance suite."""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request

root=Path(__file__).resolve().parents[1]
assert root == Path(os.environ['ATHAR_RECOVERY_SANDBOX']).resolve()
assert re.fullmatch(r'recovery-http-[a-f0-9]{32}',root.name)
state=json.loads((root/'storage/engineering/native-fixture.json').read_text(encoding='utf-8'))
assert state['fixture_only'] is True and state['host'] == '127.0.0.1'
assert re.fullmatch(r'athar_test_native_[a-f0-9]{12}',state['database'])
environment=dict(os.environ,ATHAR_LOAD_DOTENV='0',APP_ENV='local',ATHAR_FIXTURE_DATA_DIR=state['data_directory'])
for key,field in {'HOST':'host','PORT':'port','NAME':'database','USER':'user','PASSWORD':'password'}.items(): environment['ATHAR_DB_'+key]=str(state[field])
php=os.environ.get('ATHAR_TEST_PHP','C:/xampp/php/php.exe')
setup=r'''
require 'backend/bootstrap.php';
if(!preg_match('/^athar_test_native_[a-f0-9]{12}$/D',getenv('ATHAR_DB_NAME')))exit(2);
$db=\Athar\Infrastructure\Database::connect();
$expected=realpath(getenv('ATHAR_FIXTURE_DATA_DIR'));$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$expected||!$actual||strcasecmp(str_replace('\\','/',$expected),str_replace('\\','/',$actual))!==0)exit(3);
$auth=new \Athar\Identity\AuthService($db,new \Athar\Identity\OtpService($db,\Athar\Infrastructure\SecretKey::authentication(),static fn($email,$subject,$body)=>true));
'''
def service(program, control=False):
    probe_environment=environment if not control else dict(environment,ATHAR_DB_USER=os.environ['ATHAR_DB_USER'],ATHAR_DB_PASSWORD=os.environ.get('ATHAR_DB_PASSWORD',''))
    result=subprocess.run([php,'-r',setup+program],cwd=root,env=probe_environment,capture_output=True,text=True,timeout=25)
    if result.returncode: raise RuntimeError('Generated fixture probe failed ('+next((name for name in ['PDOException','ApiError','RuntimeException','ValueError','TypeError','ParseError'] if name in result.stderr),'unknown')+'); no credentials printed.')
    return json.loads(result.stdout)
def csrf(page):
    found=re.search(r'name="_csrf" value="([a-f0-9]+)"',page)
    assert found, 'Expected CSRF token'
    return found[1]
base='http://127.0.0.1:8004'
class Session:
    def __init__(self,role='admin'):
        assert role in ['admin','owner','user']
        suffix=secrets.token_hex(5);self.email='batch_'+suffix+'@example.test'
        self.account=service("$id=$auth->createAccount(['email'=>'"+self.email+"','username'=>'batch"+suffix+"','display_name'=>'Batch Fixture','password'=>'AdminFixture123!','password_confirmation'=>'AdminFixture123!']);$db->prepare('UPDATE users SET role=?,email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute(['"+role+"',$id]);echo json_encode(['id'=>$id]);")
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        if role == 'user':
            self.page=''
            return
        _,_,page=self.request('/admin/login?lang=en')
        code,url,self.page=self.request('/admin/login?lang=en',{'_csrf':csrf(page),'email':self.email,'password':'AdminFixture123!'})
        assert code == 200 and url.endswith('/admin'), 'Generated privileged login'
    def request(self,path,fields=None):
        body=urllib.parse.urlencode(fields).encode() if fields is not None else None
        try: response=self.http.open(base+path,body,timeout=25)
        except urllib.error.HTTPError as error: response=error
        return response.code,response.geturl(),response.read().decode('utf-8')
    def submit(self,fields):
        _,_,page=self.request('/admin?lang=en')
        return self.request('/admin',{'_csrf':csrf(page),**fields})
    def token(self):
        request=urllib.request.Request(base+'/api/v1/auth/login',json.dumps({'email':self.email,'password':'AdminFixture123!','device_name':'Admin batch fixture'}).encode(),{'Content-Type':'application/json'})
        return json.load(urllib.request.urlopen(request))['data']['session']['access_token']
def api(path,token,method='GET',payload=None):
    request=urllib.request.Request(base+'/api/v1'+path,json.dumps(payload).encode() if payload is not None else None,headers={'Authorization':'Bearer '+token,'Accept-Language':'en','Content-Type':'application/json'},method=method)
    try: response=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as error: response=error
    return response.code,json.load(response)
