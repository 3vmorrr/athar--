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
state = json.loads((ROOT / "storage/engineering/native-fixture.json").read_text(encoding="utf-8"))
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

environment["ATHAR_FIXTURE_DATA_DIR"] = str(state["data_directory"])
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
$expected=realpath(getenv('ATHAR_FIXTURE_DATA_DIR'));
$actual=realpath((string)$db->query('SELECT @@datadir')->fetchColumn());
if(!$expected||!$actual||strcasecmp(str_replace('\\','/',$expected),str_replace('\\','/',$actual))!==0)exit(3);
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

# Repeated suites share the generated fixture's loopback IP. Reset only its
# known IP bucket at setup, never identity buckets or any development/real DB.
# The helper above revalidates the generated schema and server datadir first.
service("$hash=hash_hmac('sha256',\"admin-login:ip\\0\".'127.0.0.1',\\Athar\\Infrastructure\\SecretKey::authentication());$db->prepare('DELETE FROM auth_rate_buckets WHERE bucket_hash=?')->execute([$hash]);echo json_encode(true);")

admin = service(
    "$email=getenv('ATHAR_ADMIN_EMAIL');"
    "$id=$auth->createAccount(['email'=>$email,'username'=>'admin" + suffix + "','display_name'=>'Admin Fixture','password'=>getenv('ATHAR_ADMIN_PASSWORD'),'password_confirmation'=>getenv('ATHAR_ADMIN_PASSWORD')]);"
    '$db->prepare("UPDATE users SET role=\'admin\',email_verified_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);'
    "echo json_encode(['id'=>$id]);"
)
user = service(
    "$email=getenv('ATHAR_USER_EMAIL');"
    "$id=$auth->createAccount(['email'=>$email,'username'=>'user" + suffix + "','display_name'=>'User Fixture','password'=>getenv('ATHAR_USER_PASSWORD'),'password_confirmation'=>getenv('ATHAR_USER_PASSWORD')]);"
    '$db->prepare("UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?")->execute([$id]);'
    "echo json_encode(['id'=>$id]);"
)

http_port = int(os.environ.get("ATHAR_TEST_HTTP_PORT", "8004"))
assert http_port == 8004, "Use the existing isolated fixture router only"
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
check("Athar Dashboard" in page or "لوحة أثر" in page, "Admin body renders after isolated authentication")
post_login_cookie = cookie("ATHARADMIN")
check(post_login_cookie is not None and post_login_cookie.value != pre_login_cookie, "Admin login rotates session ID")

# The shell must keep its own assets, navigation, language and theme state.
check('/assets/css/admin.css?v=1' in page and '/assets/js/admin.js?v=1' in page, "Independent shell assets are loaded")
check('/assets/js/app.js' not in page and 'class="site-header"' not in page, "Public chrome/scripts are absent from Admin")
check('aria-controls="admin-primary-nav"' in page and 'id="admin-main"' in page, "Drawer and skip-link targets exist")
check('href="#admin-backups"' not in page, "Non-owner shell omits recovery navigation")
url, page = request('/admin?lang=en&theme=dark')
check('lang="en" dir="ltr" data-theme="dark"' in page, "English dark shell renders")
check('Overview &amp; analytics' in page and 'Open administration menu' in page, "English navigation is localized")
url, page = request('/admin?theme=invalid&lang=invalid')
check('lang="en" dir="ltr" data-theme="dark"' in page, "Invalid preference cannot replace saved theme/locale")
url, page = request('/admin?lang=ar&theme=light')
check('lang="ar" dir="rtl" data-theme="light"' in page, "Arabic light shell renders")
url, css = request('/assets/css/admin.css')
check('[dir=rtl] .admin-sidebar' in css and '--on-primary:#10271c' in css, "RTL drawer and dark button contrast styles are served")
url, script = request('/assets/js/admin.js')
check("event.key === 'Escape'" in script and 'sidebar.inert = mobile.matches' in script, "Modal drawer interaction script is served")
url, script = request('/assets/js/admin-campaign-legacy.js')
check("ajax_campaign_batch" in script, "Existing campaign adapter survives independent shell")

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

# Expand the imported acceptance with actual public/Admin isolation and live revocation.
def status(path, data, expected):
    try:
        request(path, data)
    except urllib.error.HTTPError as error:
        check(error.code == expected, f"Expected HTTP {expected} for {path}")
    else:
        raise AssertionError(f"Expected rejection for {path}")


def sign_in():
    _, html = request("/admin/login?lang=en")
    before = csrf(html)
    _, html = request("/admin/login?lang=en", {
        "_csrf": before, "email": environment["ATHAR_ADMIN_EMAIL"],
        "password": environment["ATHAR_ADMIN_PASSWORD"],
    })
    check("Athar Dashboard" in html or "لوحة أثر" in html, "Fresh isolated Admin login succeeds")
    return before, csrf(html)


_, html = request("/admin/login?lang=ar")
check('lang="ar" dir="rtl"' in html, "Arabic login has RTL document semantics")
check('autocomplete="current-password"' in html, "Password manager and paste remain supported")
_, css = request("/assets/css/admin-auth.css")
check(".admin-auth-shell" in css and len(css) > 1000, "Fixture router actually serves login CSS")
_, script = request("/assets/js/admin-auth.js")
check("aria-pressed" in script, "Fixture router actually serves password-control JavaScript")

# A public session alone cannot open the isolated dashboard.
_, html = request("/login")
_, html = request("/login", {"_csrf": csrf(html), "email": environment["ATHAR_USER_EMAIL"], "password": environment["ATHAR_USER_PASSWORD"]})
public_cookie = cookie("PHPSESSID")
check(public_cookie is not None, "Public user has a separate cookie")
public_session_id = public_cookie.value
url, _ = request("/admin")
check(url.endswith("/admin/login"), "Public authentication does not grant Admin access")

old_csrf, admin_csrf = sign_in()
check(old_csrf != admin_csrf, "Admin authentication rotates CSRF")
admin_cookie = cookie("ATHARADMIN")
check(admin_cookie.has_nonstandard_attr("HttpOnly"), "Admin cookie is HttpOnly")
check(admin_cookie.get_nonstandard_attr("SameSite") == "Strict", "Admin cookie is SameSite Strict")
_, public_page = request("/account")
check(environment["ATHAR_USER_EMAIL"] in public_page, "Public user remains distinct during Admin login")
status("/admin/logout", {"_csrf": csrf(public_page)}, 419)
status("/admin/logout", {"_csrf": old_csrf}, 419)
status("/admin/backup-download", {"_csrf": admin_csrf}, 403)
request("/admin/logout", {"_csrf": admin_csrf})
url, public_page = request("/account")
check(url.endswith("/account") and environment["ATHAR_USER_EMAIL"] in public_page, "Admin logout preserves public authentication")
check(cookie("PHPSESSID").value == public_session_id, "Admin logout does not rotate the public session")

sign_in()
service("$db->prepare(\"UPDATE users SET role='user' WHERE id=?\")->execute([" + str(admin["id"]) + "]);echo json_encode(true);")
url, _ = request("/admin")
check(url.endswith("/admin/login"), "Live role demotion revokes Admin access")
service("$db->prepare(\"UPDATE users SET role='admin' WHERE id=?\")->execute([" + str(admin["id"]) + "]);echo json_encode(true);")
sign_in()
service("$db->prepare('UPDATE users SET suspended_at=UTC_TIMESTAMP() WHERE id=?')->execute([" + str(admin["id"]) + "]);echo json_encode(true);")
url, _ = request("/admin")
check(url.endswith("/admin/login"), "Live suspension revokes Admin access")
service("$db->prepare('UPDATE users SET suspended_at=NULL WHERE id=?')->execute([" + str(admin["id"]) + "]);echo json_encode(true);")

for field, age in [("admin_last_activity", 1801), ("admin_authenticated_at", 28801)]:
    sign_in()
    environment["ATHAR_OWN_ADMIN_SESSION"] = cookie("ATHARADMIN").value
    service("session_name('ATHARADMIN');$sid=getenv('ATHAR_OWN_ADMIN_SESSION');if(!preg_match('/^[a-zA-Z0-9,-]{16,128}$/D',$sid))exit(4);session_id($sid);session_start();if((int)($_SESSION['admin_user']['id']??0)!==" + str(admin["id"]) + ")exit(5);$_SESSION['" + field + "']=time()-" + str(age) + ";session_write_close();echo json_encode(true);")
    url, _ = request("/admin")
    check(url.endswith("/admin/login"), f"Expired {field} is rejected")

# Check privileged cookie login never minted unused API bearer credentials.
count = service("$q=$db->prepare('SELECT COUNT(*) FROM api_sessions WHERE user_id IN (?,?)');$q->execute([" + str(admin["id"]) + "," + str(user["id"]) + "]);echo json_encode((int)$q->fetchColumn());")
check(count == 0, "Cookie authentication creates no API sessions")

# Rate limits remain generic for an unknown fixture-only address.
for index in range(9):
    _, html = request("/admin/login?lang=en")
    url, html = request("/admin/login?lang=en", {"_csrf": csrf(html), "email": f"missing_{suffix}@example.test", "password": "InvalidFixture123!"})
    check("Unable to sign in with these credentials." in html, "Failed/rate-limited response remains generic")

(ROOT / "storage/engineering/admin-auth-fixture.json").write_text(json.dumps({
    "fixture_only": True, "database": state["database"],
    "admin_email": environment["ATHAR_ADMIN_EMAIL"], "admin_password": environment["ATHAR_ADMIN_PASSWORD"],
    "public_email": environment["ATHAR_USER_EMAIL"], "public_password": environment["ATHAR_USER_PASSWORD"],
}), encoding="utf-8")
print(f"PASS: {checks} Independent Admin authentication assertions.")
