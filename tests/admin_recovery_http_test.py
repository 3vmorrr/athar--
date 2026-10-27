"""Real recovery operations run only inside the dedicated copied storage/schema."""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse

root = Path(__file__).resolve().parents[1]
sandbox = Path(os.environ.get('ATHAR_RECOVERY_SANDBOX', '')).resolve()
assert root == sandbox and re.fullmatch(r'recovery-http-[a-f0-9]{32}', root.name)
assert root.parent.name == 'engineering' and root.parent.parent.name == 'storage'
import admin_authorization_http_test as authz  # 120 existing HTTP checks.

fixture = authz.fixture
checks = 0


def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1


owner = authz.actor('owner')
session = authz.Session(owner)
other_owner_session = authz.Session(owner)
password = 'AdminFixture123!'


def post(kind, **fields):
    _, _, session.page = session.request('/admin?lang=en#admin-backups')
    return session.post(kind, **fields)


def artifacts():
    return fixture.service('echo json_encode($db->query("SELECT id,storage_key,sha256,status FROM backup_artifacts ORDER BY id")->fetchAll(PDO::FETCH_ASSOC));')


def panel(page):
    return re.search(r'<section[^>]*id="admin-backups"[\s\S]*?</section>', page).group(0)


_, _, page = session.request('/admin?lang=en')
check('Create and verify backup' in panel(page) and 'Owner password' in panel(page), 'English recovery controls are localized')
check('No unexpired backup is available' in panel(page), 'Empty restoration has a truthful state')
check('data-confirm="Replace application data and files with this backup?"' in panel(page), 'Restore explicitly confirms destructive effect')
_, _, arabic = session.request('/admin?lang=ar')
check('إنشاء النسخة والتحقق منها' in panel(arabic), 'Arabic recovery controls are available')
check('action="/admin#admin-backups"' in panel(arabic), 'Recovery posts retain their panel fragment')
check(not artifacts(), 'Isolated environment begins with no backup artifacts')
for fields, expected in [
    ({'password': 'wrong', 'manifest_confirmation': 'CREATE FULL BACKUP'}, 'The password is incorrect.'),
    ({'password': password, 'manifest_confirmation': 'incorrect'}, 'Enter CREATE FULL BACKUP exactly as shown.'),
]:
    status, _, page = post('backup-create', **fields)
    check(status == 200 and expected in page and not artifacts(), 'Wrong password/confirmation cannot create an archive')
status, _, _ = session.request('/admin', {'kind': 'backup-create', 'password': password, 'manifest_confirmation': 'CREATE FULL BACKUP'})
check(status == 419 and not artifacts(), 'Recovery creation enforces CSRF')
snapshot_display_name = authz.snapshot(authz.user)['display_name']
status, url, page = post('backup-create', password=password, manifest_confirmation='CREATE FULL BACKUP')
check(status == 200 and url.endswith('#admin-backups'), 'Creation redirects to recovery panel')
check('encrypted backup is created and verified' in page, 'Creation feedback is localized')
backup = artifacts()[-1]
archive = root / 'storage/backups' / backup['storage_key']
check(archive.is_file() and archive.read_bytes().startswith(b'ATHAR-ENC1'), 'HTTP creation emits an encrypted archive only')
check(not list((root/'storage/backups').glob('athar-plain-*')), 'Creation cleans plaintext')
status, _, page = post('backup-grant', password='wrong', backup_id=backup['id'])
check('The password is incorrect.' in page and 'backup-download-form' not in page, 'Download grant requires password reauthentication')
status, _, page = post('backup-grant', password=password, backup_id=backup['id'])
check(status == 200 and 'single-use download grant is ready' in page, 'Owner gets a separate grant')
check('action="/admin/backup-download"' in page, 'Grant stays inside the Admin cookie boundary')
token = fixture.csrf(page)
check(other_owner_session.request('/admin/backup-download', {'_csrf': fixture.csrf(other_owner_session.page)})[0] == 403, 'A grant cannot be borrowed by another session of the same Owner')
fixture.service("$db->prepare('UPDATE backup_download_grants SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE owner_id=?')->execute([" + str(owner['id']) + "]);echo json_encode(true);")
check(session.request('/admin/backup-download', {'_csrf': token})[0] == 403, 'Expired single-use grant is refused by HTTP download')
check(artifacts()[-1]['status'] == 'ready', 'Expired grant does not mark artifact downloaded')
_, _, page = post('backup-grant', password=password, backup_id=backup['id'])
token = fixture.csrf(page)
response = session.http.open(fixture.base + '/admin/backup-download', urllib.parse.urlencode({'_csrf': token}).encode(), timeout=30)
downloaded = response.read()
check(response.code == 200 and hashlib.sha256(downloaded).hexdigest() == backup['sha256'], 'Downloaded bytes match the verified encrypted artifact')
check(response.headers.get('Cache-Control') == 'no-store, private', 'Download cannot be cached')
check(response.headers.get('X-Content-Type-Options') == 'nosniff', 'Encrypted download disables content sniffing')
check('attachment;' in response.headers.get('Content-Disposition', ''), 'Backup downloads as an attachment')
check(artifacts()[-1]['status'] == 'downloaded', 'Successful download updates artifact state')
check(session.request('/admin/backup-download', {'_csrf': token})[0] == 403, 'Consumed download grant cannot be reused')
check(authz.manager_session.request('/admin/backup-download', {'_csrf': fixture.csrf(authz.manager_session.page)})[0] == 403, 'Admin cannot download an Owner artifact')

# Preserve an independent public browser session to prove restore invalidates it.
import http.cookiejar
import urllib.request
public = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
response = public.open(fixture.base + '/login')
csrf = fixture.csrf(response.read().decode())
response = public.open(fixture.base + '/login', urllib.parse.urlencode({'_csrf': csrf, 'email': owner['email'], 'password': password}).encode())
check('/login' not in response.geturl(), 'Owner has an independent public session before recovery')
fixture.service("$db->prepare(\"UPDATE users SET display_name='Changed after snapshot',session_version=session_version+10 WHERE id=?\")->execute([" + str(authz.user['id']) + "]);echo json_encode(true);")
status, _, page = post('backup-restore', password=password, backup_id=backup['id'], restore_confirmation='wrong')
check('Enter RESTORE FULL BACKUP exactly as shown.' in page, 'Restore requires exact typed confirmation')
prior_cookie = next(c.value for c in session.jar if c.name == 'ATHARADMIN')
status, url, page = post('backup-restore', password=password, backup_id=backup['id'], restore_confirmation='RESTORE FULL BACKUP')
check(status == 200 and url.endswith('#admin-backups') and 'Restoration is complete.' in page, 'Actual HTTP restore retains authenticated Owner session')
check(next(c.value for c in session.jar if c.name == 'ATHARADMIN') != prior_cookie, 'Restoration rotates the current Admin session ID')
check(other_owner_session.request('/admin')[1].endswith('/admin/login'), 'Other retained Owner Admin session is revoked')
response = public.open(fixture.base + '/account')
check('/login' in response.geturl(), 'Restoration revokes the old public cookie')
check(authz.snapshot(authz.user)['display_name'] == snapshot_display_name, 'Actual HTTP restore recovers snapshot data')
status, _, page = post('local-reset', password=password, environment_confirmation='production', reset_confirmation='RESET ATHAR DATA')
check('Enter the current environment and RESET ATHAR DATA' in page, 'Reset refuses wrong environment confirmation')
status, _, page = post('local-reset', password='wrong', environment_confirmation='local', reset_confirmation='RESET ATHAR DATA')
check('The password is incorrect.' in page, 'Reset requires owner password')
status, _, page = post('local-reset', password=password, environment_confirmation='local', reset_confirmation='wrong')
check('Enter the current environment and RESET ATHAR DATA' in page, 'Reset requires exact deletion confirmation')
status, url, page = post('local-reset', password=password, environment_confirmation='local', reset_confirmation='RESET ATHAR DATA')
check(status == 200 and url.endswith('#admin-backups') and 'Local reset is complete.' in page, 'Actual HTTP local reset retains Owner session')
check(fixture.service('echo json_encode((int)$db->query("SELECT COUNT(*) FROM users")->fetchColumn());') == 1, 'HTTP reset leaves only the current owner')
check(fixture.service('echo json_encode((int)$db->query("SELECT COUNT(*) FROM lessons")->fetchColumn());') == 0, 'HTTP reset deletes fixture content')
status, _, page = post('backup-restore', password=password, backup_id=backup['id'], restore_confirmation='RESTORE FULL BACKUP')
check(status == 200 and 'Restoration is complete.' in page, 'HTTP restore recovers the reset application')
check(fixture.service('echo json_encode((int)$db->query("SELECT COUNT(*) FROM lessons")->fetchColumn());') > 0, 'Post-reset HTTP restoration recovers content')
check(not list((root/'storage/backups').glob('athar-restore-*')) and not list((root/'storage/backups').glob('athar-reset-check-*')), 'HTTP recovery leaves no plaintext temporary files')
print(f'PASS: {checks} actual HTTP recovery assertions (plus120 existing HTTP).')
if os.environ.get('ATHAR_TEST_NODE'):
    import subprocess
    browser_env = dict(os.environ, ATHAR_TEST_OWNER_EMAIL=owner['email'])
    subprocess.run([os.environ['ATHAR_TEST_NODE'], str(root/'tests/admin_recovery_browser_test.cjs')], env=browser_env, check=True, timeout=120)
