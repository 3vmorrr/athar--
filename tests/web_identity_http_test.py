"""Opt-in real HTTP cookie/API OTP compatibility on disposable native fixtures only."""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
state = json.loads((ROOT / "storage/engineering/native-fixture.json").read_text())
assert state.get("fixture_only") is True and re.fullmatch(r"athar_test_native_[a-f0-9]{12}", state["database"])
assert state["host"] == "127.0.0.1"
environment = dict(os.environ, ATHAR_LOAD_DOTENV="0", APP_ENV="local")
for key, field in {"HOST": "host", "PORT": "port", "NAME": "database", "USER": "user", "PASSWORD": "password"}.items():
    environment["ATHAR_DB_" + key] = str(state[field])
environment["ATHAR_FIXTURE_EMAIL"] = "adapter_" + secrets.token_hex(5) + "@example.test"
php = os.environ.get("ATHAR_TEST_PHP", r"C:\xampp\php\php.exe")
setup = r'''
require 'backend/bootstrap.php';
if(!preg_match('/^athar_test_native_[a-f0-9]{12}$/',getenv('ATHAR_DB_NAME')))exit(2);
$db=\Athar\Infrastructure\Database::connect();$code='';
$otp=new \Athar\Identity\OtpService($db,\Athar\Infrastructure\SecretKey::authentication(),static function($email,$subject,$body)use(&$code){preg_match('/[0-9]{6}/',$body,$m);$code=$m[0];return true;});
$auth=new \Athar\Identity\AuthService($db,$otp);$email=getenv('ATHAR_FIXTURE_EMAIL');
'''

def service(statement):
    result = subprocess.run([php, "-r", setup + statement], cwd=ROOT, env=environment, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Fixture service probe failed; inspect locally without publishing credentials.")
    return json.loads(result.stdout)

created = service("$r=$auth->register(['email'=>$email,'username'=>explode('@',$email)[0],'display_name'=>'Interface adapter fixture','password'=>'AdapterFixture123!','password_confirmation'=>'AdapterFixture123!']); echo json_encode(['id'=>$r['user']['id'],'code'=>$code]);")
jar = http.cookiejar.CookieJar()
http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
checks = 0

def request(path, data=None):
    response = http.open("http://127.0.0.1:8004" + path, urllib.parse.urlencode(data).encode() if data is not None else None, timeout=20)
    return response.geturl(), response.read().decode("utf-8")

def csrf(html):
    return re.search(r'name="_csrf" value="([a-f0-9]+)"', html).group(1)

def check(condition, label):
    global checks
    if not condition:
        raise AssertionError(label)
    checks += 1

before_users = service("echo json_encode((int)$db->query('SELECT COUNT(*) FROM users')->fetchColumn());")
_, page = request("/register")
_, page = request("/register", {"_csrf": csrf(page), "email": "mismatch@example.test", "display_name": "Interface fixture", "username": "mismatch_fixture", "password": "AdapterFixture123!", "password_confirmation": "different"})
check("كلمتا المرور غير متطابقتين." in page, "Website registration requires matching confirmation")
check(before_users == service("echo json_encode((int)$db->query('SELECT COUNT(*) FROM users')->fetchColumn());"), "Rejected registration creates no account")
_, page = request("/login")
url, page = request("/login", {"_csrf": csrf(page), "email": environment["ATHAR_FIXTURE_EMAIL"], "password": "AdapterFixture123!"})
check(url.endswith("/account"), "Cookie login reaches pending account")
before = service("echo json_encode((int)$db->query('SELECT COUNT(*) FROM api_sessions')->fetchColumn());")
url, page = request("/account", {"_csrf": csrf(page), "action": "verify-email-code", "code": created["code"]})
check("تم تأكيد البريد الإلكتروني." in page, "Website accepts API-generated HMAC OTP")
after = service("echo json_encode(['sessions'=>(int)$db->query('SELECT COUNT(*) FROM api_sessions')->fetchColumn(),'verified'=>$auth->profile(" + str(created["id"]) + ")[ 'email_verified']]);")
check(after["verified"] and before == after["sessions"], "Cookie confirmation verifies without creating mobile bearer credentials")
code = service("$otp->issue(" + str(created["id"]) + ",'password_reset');echo json_encode($code);")
_, page = request("/reset-password")
payload = {"_csrf": csrf(page), "email": environment["ATHAR_FIXTURE_EMAIL"], "code": code, "password": "AdapterReset123!", "password_confirmation": "different"}
_, page = request("/reset-password", payload)
check("راجع كلمة المرور وتأكيدها." in page, "Reset confirmation mismatch rejected")
payload["password_confirmation"] = payload["password"]
payload["_csrf"] = csrf(page)
url, page = request("/reset-password", payload)
check(url.endswith("/login"), "Shared reset returns to login")
check(service("echo json_encode($auth->login($email,'AdapterReset123!')['user']['email_verified']);"), "API login accepts web-reset password")
_, page = request("/login")
url, page = request("/login", {"_csrf": csrf(page), "email": environment["ATHAR_FIXTURE_EMAIL"], "password": "AdapterReset123!"})
check(url.endswith("/account"), "Cookie login accepts shared password reset")
old = service("$r=$auth->login($email,'AdapterReset123!');echo json_encode(['token'=>$r['session']['access_token'],'count'=>(int)$db->query('SELECT COUNT(*) FROM api_sessions')->fetchColumn()]);")
environment['ATHAR_FIXTURE_OLD_TOKEN'] = old['token']
_, page = request("/account", {"_csrf": csrf(page), "action": "change-password", "current_password": "AdapterReset123!", "new_password": "AdapterChanged123!", "new_password_confirmation": "different"})
check("راجع كلمة المرور وتأكيدها." in page, "Cookie password change requires confirmation")
url, page = request("/account", {"_csrf": csrf(page), "action": "change-password", "current_password": "AdapterReset123!", "new_password": "AdapterChanged123!", "new_password_confirmation": "AdapterChanged123!"})
check(url.endswith("/account") and "تم تغيير كلمة المرور وتسجيل الخروج من الجلسات الأخرى." in page, "Password change keeps the initiating cookie session")
check(service("try{$auth->authenticate(getenv('ATHAR_FIXTURE_OLD_TOKEN'));echo 'false';}catch(\\Athar\\Http\\ApiError $e){echo json_encode($e->status===401);}"), "Cookie password change revokes the previous API session")
check(old['count'] == service("echo json_encode((int)$db->query('SELECT COUNT(*) FROM api_sessions')->fetchColumn());"), "Cookie password change creates no unused bearer credentials")
service("$q=$db->prepare('UPDATE users SET suspended_at=UTC_TIMESTAMP() WHERE email=?');$q->execute([$email]);echo 'true';")
url, page = request("/account")
check(url.endswith("/login"), "Suspension invalidates an existing cookie session")
url, page = request("/login", {"_csrf": csrf(page), "email": environment["ATHAR_FIXTURE_EMAIL"], "password": "AdapterChanged123!"})
check(url.endswith("/login") and "تعذر تسجيل الدخول بهذه البيانات." in page, "Suspended account cannot start another cookie session")
print(f"PASS: {checks} real HTTP cookie/API OTP/reset compatibility assertions; fake provider only.")
