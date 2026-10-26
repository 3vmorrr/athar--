"""Real HTTP acceptance for task 17.1 Independent Admin authentication.

Requires the disposable native fixture and local PHP server used by the existing
HTTP acceptance suite. No production account, email delivery or persistent
database is touched.
"""
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

ROOT = Path(__file__).resolve().parents[1]
state = json.loads((ROOT / "storage/engineering/native-fixture.json").read_text())
assert state.get("fixture_only") is True
assert re.fullmatch(r"athar_test_native_[a-f0-9]{12}", state["database"])
assert state["host"] == "127.0.0.1"

environment = dict(os.environ, ATHAR_LOAD_DOTENV="0", APP_ENV="local")
for key, field in {
    "HOST": "host",
    "PORT": "port",
    "NAME": "database",
    "USER": "user",
    "PASSWORD": "password",
}.items():
    environment["ATHAR_DB_" + key] = str(state[field])

suffix = secrets.token_hex(5)
environment["ATHAR_ADMIN_EMAIL"] = f"admin_{suffix}@example.test"
environment["ATHAR_USER_EMAIL"] = f"user_{suffix}@example.test"
environment["ATHAR_ADMIN_PASSWORD"] = "AdminFixture123!"
environment["ATHAR_USER_PASSWORD"] = "UserFixture123!"

php = os.environ.get("ATHAR_TEST_PHP", r"C:\xampp\php\php.exe")
setup = r'''
require 'backend/bootstrap.php';
if(!preg_match('/^athar_test_native_[a-f0-9]{12}$/',getenv('ATHAR_DB_NAME')))exit(2);
$db=\Athar\Infrastructure\Database::connect();
$otp=new \Athar\Identity\OtpService(
    $db,
    \Athar\Infrastructure\SecretKey::authentication(),
    static fn($email,$subject,$body)=>true
);
$auth=new \Athar\Identity\AuthService($db,$otp);
'''

def service(statement):
    result = subprocess.run(
        [php, "-r", setup + statement],
        cwd=ROOT,
        env=environment,
        capture_output=True,
        text=True,
    )
    if result.returncode:
        raise RuntimeError("Admin fixture service probe failed; inspect locally without publishing credentials.")
    return json.loads(result.stdout)

admin = service(
    "$email=getenv('ATHAR_ADMIN_EMAIL');"
    "$id=$auth->createAccount(['email'=>$email,'username'=>'admin" + suffix + "','display_name'=>'Admin Fixture','password'=>getenv('ATHAR_ADMIN_PASSWORD'),'password_confirmation'=>getenv('ATHAR_ADMIN_PASSWORD')]);"
    "$db->prepare("UPDATE users SET role='admin',email_verified_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);"
    "echo json_encode(['id'=>$id]);"
)
user = service(
    "$email=getenv('ATHAR_USER_EMAIL');"
    "$id=$auth->createAccount(['email'=>$email,'username'=>'user" + suffix + "','display_name'=>'User Fixture','password'=>getenv('ATHAR_USER_PASSWORD'),'password_confirmation'=>getenv('ATHAR_USER_PASSWORD')]);"
    "$db->prepare("UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);"
    "echo json_encode(['id'=>$id]);"
)

http_port = int(os.environ.get("ATHAR_TEST_HTTP_PORT", "8004"))
base = f"http://{state['host']}:{http_port}"
jar = http.cookiejar.CookieJar()
http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
checks = 0

def request(path, data=None):
    payload = urllib.parse.urlencode(data).encode() if data is not None else None
    response = http.open(base + path, payload, timeout=20)
    return response.geturl(), response.read().decode("utf-8")

def csrf(html):
    match = re.search(r'name="_csrf" value="([a-f0-9]+)"', html)
    if not match:
        raise AssertionError("Expected CSRF field")
    return match.group(1)

def cookie(name):
    return next((item for item in jar if item.name == name), None)

def check(condition, label):
    global checks
    if not condition:
        raise AssertionError(label)
    checks += 1

# Independent GET establishes only the Admin cookie, scoped beneath /admin.
url, page = request("/admin/login?lang=en")
check(url.endswith("/admin/login?lang=en"), "Admin login renders independently")
admin_cookie = cookie("ATHARADMIN")
check(admin_cookie is not None and admin_cookie.path == "/admin", "Admin cookie is isolated to /admin")
pre_login_cookie = admin_cookie.value
check("Athar Admin Sign In" in page, "English Admin login is available")

# Missing CSRF is rejected before credential processing.
try:
    request("/admin/login?lang=en", {
        "email": environment["ATHAR_ADMIN_EMAIL"],
        "password": environment["ATHAR_ADMIN_PASSWORD"],
    })
    raise AssertionError("Missing CSRF unexpectedly accepted")
except urllib.error.HTTPError as error:
    check(error.code == 419, "Admin login rejects missing CSRF")

# A normal user cannot obtain an Admin session and receives no role oracle.
url, page = request("/admin/login?lang=en")
url, page = request("/admin/login?lang=en", {
    "_csrf": csrf(page),
    "email": environment["ATHAR_USER_EMAIL"],
    "password": environment["ATHAR_USER_PASSWORD"],
})
check(url.endswith("/admin/login?lang=en"), "Normal user remains on Admin login")
check("Unable to sign in with these credentials." in page, "Role denial is generic")

# Admin login rotates the PHP session ID and reaches the existing Admin body.
url, page = request("/admin/login?lang=en")
url, page = request("/admin/login?lang=en", {
    "_csrf": csrf(page),
    "email": environment["ATHAR_ADMIN_EMAIL"],
    "password": environment["ATHAR_ADMIN_PASSWORD"],
})
check(url.endswith("/admin"), "Admin credentials reach /admin")
check("لوحة أثر" in page, "Admin body renders after isolated authentication")
post_login_cookie = cookie("ATHARADMIN")
check(post_login_cookie is not None and post_login_cookie.value != pre_login_cookie, "Admin login rotates session ID")

# The /admin cookie path means the public site does not receive Admin auth state.
url, public_page = request("/")
check(url.rstrip("/").endswith(f":{http_port}"), "Public root remains accessible")
check("خروج من الإدارة" not in public_page, "Admin auth does not leak into public root")

# Revoking session_version invalidates the retained Admin cookie on the next request.
service(
    "$db->prepare('UPDATE users SET session_version=session_version+1 WHERE id=?')->execute([" + str(admin["id"]) + "]);"
    "echo json_encode(true);"
)
url, page = request("/admin")
check(url.endswith("/admin/login"), "Admin session_version change revokes Admin cookie session")

# Fresh login then explicit Admin logout destroys only the Admin session.
url, page = request("/admin/login")
url, page = request("/admin/login", {
    "_csrf": csrf(page),
    "email": environment["ATHAR_ADMIN_EMAIL"],
    "password": environment["ATHAR_ADMIN_PASSWORD"],
})
check(url.endswith("/admin"), "Admin can sign in after version refresh")
url, page = request("/admin/logout", {"_csrf": csrf(page)})
check(url.endswith("/admin/login"), "Admin logout returns to isolated login")
url, page = request("/admin")
check(url.endswith("/admin/login"), "Logged-out Admin cannot reopen dashboard")

print(f"PASS: {checks} Independent Admin authentication assertions.")
