"""Actual action/target RBAC requests against the guarded generated fixture only."""
import http.cookiejar
import json
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

import admin_auth_http_test as fixture  # Runs the existing 60 authentication/shell checks.

checks = 0


def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1


def actor(role):
    assert role in {'owner', 'admin', 'user'}
    suffix = secrets.token_hex(6)
    email, username = f'rbac_{suffix}@example.test', f'rbac{suffix}'
    return fixture.service(
        "$id=$auth->createAccount(['email'=>'" + email + "','username'=>'" + username + "','display_name'=>'RBAC Fixture','password'=>'AdminFixture123!','password_confirmation'=>'AdminFixture123!']);"
        "$db->prepare('UPDATE users SET role=?,email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute(['" + role + "',$id]);"
        "echo json_encode(['id'=>$id,'email'=>'" + email + "','username'=>'" + username + "','role'=>'" + role + "']);"
    )


class Session:
    def __init__(self, account):
        self.account = account
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        _, _, page = self.request('/admin/login?lang=en')
        status, url, self.page = self.request('/admin/login?lang=en', {
            '_csrf': fixture.csrf(page), 'email': account['email'], 'password': 'AdminFixture123!',
        })
        check(status == 200 and url.endswith('/admin'), 'Generated privileged actor signs in')

    def request(self, path, data=None):
        body = None if data is None else urllib.parse.urlencode(data).encode()
        try:
            response = self.http.open(fixture.base + path, body, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        return response.code, response.geturl(), response.read().decode('utf-8')

    def post(self, kind, **fields):
        return self.request('/admin', {'_csrf': fixture.csrf(self.page), 'kind': kind, **fields})


owner, manager, peer, user = actor('owner'), actor('admin'), actor('admin'), actor('user')
owner_session, manager_session, peer_session = Session(owner), Session(manager), Session(peer)
check('href="#admin-services"' in owner_session.page and 'href="#admin-backups"' in owner_session.page, 'Owner sees services/recovery navigation')
check('id="admin-services"' not in manager_session.page and 'href="#admin-services"' not in manager_session.page, 'Admin receives no service panel or navigation')
check('name="kind" value="user-role"' not in manager_session.page, 'Admin receives no role-edit form')


def snapshot(account):
    return fixture.service("$q=$db->prepare('SELECT email,username,display_name,role,session_version FROM users WHERE id=?');$q->execute([" + str(account['id']) + "]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")


before = {account['id']: snapshot(account) for account in [owner, manager, peer, user]}
for kind in ['user-role', 'gmail-settings', 'dorar-settings', 'night-prayer-defaults', 'backup-create', 'backup-grant', 'backup-restore', 'local-reset', '', 'unknown', 'private-canary-' + 'x' * 10000]:
    status, _, body = manager_session.post(kind, user_id=user['id'], role='admin')
    check(status == 403, 'Unauthorized/unknown action is forbidden before dispatch')
    check('not authorized' in body and 'private-canary' not in body, 'Denial is localized and does not echo input')

status, _, _ = manager_session.request('/admin/backup-download', {'_csrf': fixture.csrf(manager_session.page)})
check(status == 403, 'Direct owner download route uses the matrix')
check(manager_session.request('/admin/backup-download', {})[0] == 419, 'Direct owner download requires CSRF before dispatch')
status, _, _ = manager_session.request('/admin', {'kind': 'gmail-settings'})
check(status == 419, 'CSRF is enforced before privileged action dispatch')


def profile(session, account, name='Changed fixture'):
    return session.post('user-profile-update', user_id=account['id'], email=account['email'], username=account['username'], display_name=name, password='AdminFixture123!')


for account in [owner, peer, manager]:
    check(profile(manager_session, account)[0] == 403, 'Admin cannot edit owner/peer/self')
    status, _, _ = manager_session.post('user-delete', user_id=account['id'], email_confirmation=account['email'], password='AdminFixture123!')
    check(status == 403, 'Admin cannot delete owner/peer/self')
check(profile(owner_session, owner)[0] == 403, 'Owner cannot use user-management editor on self')
check(owner_session.post('user-role', user_id=owner['id'], role='admin', password='AdminFixture123!')[0] == 403, 'Owner role is immutable')
for account in [owner, manager, peer, user]:
    check(snapshot(account) == before[account['id']], 'Rejected action leaves account data unchanged')

# Positive requests touch only newly generated fixtures and exercise actual dispatch.
check(profile(manager_session, user, 'Managed fixture')[0] == 200, 'Admin can edit a normal user')
check(snapshot(user)['display_name'] == 'Managed fixture', 'Allowed user update is persisted')
check(profile(owner_session, peer, 'Owner-managed fixture')[0] == 200, 'Owner can edit a peer admin')
check(snapshot(peer)['display_name'] == 'Owner-managed fixture', 'Allowed owner update is persisted')
prior = snapshot(peer)
check(owner_session.post('user-role', user_id=peer['id'], role='user', password='AdminFixture123!')[0] == 200, 'Owner can demote a generated peer')
after = snapshot(peer)
check(after['role'] == 'user' and int(after['session_version']) == int(prior['session_version']) + 1, 'Role change increments revocation version once')
check(peer_session.request('/admin')[1].endswith('/admin/login'), 'Demoted retained Admin session is revoked')
check(owner_session.post('user-role', user_id=peer['id'], role='user', password='AdminFixture123!')[0] == 200, 'Repeated canonical role is a no-op')
check(snapshot(peer) == after, 'No-op role request does not increment revocation version')

# Hold a target row while promoting it. The HTTP editor must wait/re-read the
# locked role and refuse the now-privileged target, preserving its original name.
race = actor('user')
program = fixture.setup + "$db->beginTransaction();$db->prepare(\"UPDATE users SET role='admin' WHERE id=?\")->execute([" + str(race['id']) + "]);echo \"LOCKED\\n\";flush();sleep(2);$db->commit();"
process = subprocess.Popen([fixture.php, '-r', program], cwd=fixture.ROOT, env=fixture.environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
try:
    check(process.stdout.readline().strip() == 'LOCKED', 'Concurrent promotion holds a real target row lock')
    started = time.monotonic()
    status, _, _ = profile(manager_session, race, 'Race must not update')
    check(status == 403, 'Concurrent promotion cannot bypass target permission')
    check(time.monotonic() - started >= 1, 'Actual HTTP authorization waited for the target lock')
    check(snapshot(race)['display_name'] == 'RBAC Fixture', 'Concurrent denied edit preserves target profile')
finally:
    process.communicate(timeout=10)
check(process.returncode == 0, 'Concurrent fixture transaction completed cleanly')

audit = fixture.service("$q=$db->prepare(\"SELECT target_type,target_id FROM audit_logs WHERE actor_id=? AND action='admin-action-denied'\");$q->execute([" + str(manager['id']) + "]);echo json_encode($q->fetchAll(PDO::FETCH_ASSOC));")
check(len(audit) >= 18, 'Action and target denials remain audited after transaction rollback')
check(any(row['target_type'] == 'admin-user' and row['target_id'] == str(race['id']) for row in audit), 'Concurrent target denial is durable')
check(all('private-canary' not in str(row) for row in audit), 'Untrusted action text never enters audit storage')

(fixture.ROOT / 'storage/engineering/admin-rbac-fixture.json').write_text(json.dumps({'fixture_only': True, 'owner_email': owner['email'], 'manager_email': manager['email'], 'password': 'AdminFixture123!'}), encoding='utf-8')
print(f'PASS: {checks} actual HTTP Admin action/target/rollback/concurrency assertions (plus{fixture.checks} authentication/shell).')
