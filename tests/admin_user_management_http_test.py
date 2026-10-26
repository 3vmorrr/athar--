"""User administration acceptance on generated local fixture records only."""
from html.parser import HTMLParser
import http.cookiejar
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request

import admin_authorization_http_test as authz  # 120 prior real HTTP checks.

fixture = authz.fixture
checks = 0


def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1


def state(account):
    return fixture.service("$q=$db->prepare('SELECT email,username,display_name,role,suspended_at,session_version,email_verified_at,email_marketing_opt_in FROM users WHERE id=?');$q->execute([" + str(account['id']) + "]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")


def api(account, tokens=None):
    if tokens is None:
        return fixture.service("$result=$auth->login('" + account['email'] + "','AdminFixture123!');echo json_encode($result['session']);")
    return ApiSession(tokens)


class ApiSession:
    def __init__(self, tokens):
        self.tokens = tokens

    def request(self, path='/me', refresh=False):
        body = json.dumps({'refresh_token': self.tokens['refresh_token']}).encode() if refresh else None
        headers = {'Authorization': 'Bearer ' + self.tokens['access_token'], 'Content-Type': 'application/json'}
        request = urllib.request.Request(fixture.base + '/api/v1' + path, body, headers)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        return response.code, json.loads(response.read())


class PublicSession:
    def __init__(self, account):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        _, page = self.request('/login')
        url, _ = self.request('/login', {'_csrf': fixture.csrf(page), 'email': account['email'], 'password': 'AdminFixture123!'})
        check(url.endswith('/account'), 'Generated public actor signs in')

    def request(self, path, data=None):
        response = self.http.open(fixture.base + path, None if data is None else urllib.parse.urlencode(data).encode(), timeout=20)
        return response.geturl(), response.read().decode('utf-8')


manager = authz.manager_session
owner = authz.owner_session
target = authz.actor('user')
tokens = api(target)
mobile = ApiSession(tokens)
public = PublicSession(target)
check(mobile.request()[0] == 200, 'Access token works before suspension')
original = state(target)
fixture.service("$db->prepare('INSERT INTO quran_positions(user_id,surah,ayah,revision) VALUES (?,2,255,1)')->execute([" + str(target['id']) + "]);echo json_encode(true);")


def suspend(session, account, next_state, password='AdminFixture123!'):
    return session.post('user-suspension', user_id=account['id'], state=next_state, password=password)


wrong_status, _, wrong_body = suspend(manager, target, 'suspend', 'wrong-password')
check(wrong_status == 200 and 'Your current Admin password is incorrect.' in wrong_body and 'data-admin-form-error' in wrong_body, 'Wrong reauthentication has a localized focusable error')
check(state(target) == original, 'Wrong password changes no account state')
for protected in [authz.owner, authz.manager, authz.race]:
    check(suspend(manager, protected, 'suspend')[0] == 403, 'Admin cannot suspend owner/self/peer')
check(suspend(owner, authz.owner, 'suspend')[0] == 403, 'Owner cannot suspend self')
check(suspend(manager, target, 'suspend')[0] == 200, 'Admin suspends an ordinary user')
suspended = state(target)
check(suspended['suspended_at'] is not None and int(suspended['session_version']) == int(original['session_version']) + 1, 'Suspension increments revocation version')
check(mobile.request()[0] == 401, 'Suspension revokes actual access token')
check(mobile.request('/auth/refresh', refresh=True)[0] == 401, 'Suspension revokes actual refresh token')
check(public.request('/account')[0].endswith('/login'), 'Suspension revokes public cookie session')
check(suspend(manager, target, 'suspend')[0] == 200 and state(target) == suspended, 'Repeated suspension is a no-op')
check(suspend(manager, target, 'restore')[0] == 200, 'Admin restores the account')
restored = state(target)
check(restored['suspended_at'] is None and int(restored['session_version']) == int(suspended['session_version']) + 1, 'Restore changes state and increments revocation version')
check(mobile.request()[0] == 401 and mobile.request('/auth/refresh', refresh=True)[0] == 401, 'Restore never revives old access/refresh credentials')
check(suspend(manager, target, 'restore')[0] == 200 and state(target) == restored, 'Repeated restore is a no-op')
new_mobile = ApiSession(api(target))
check(new_mobile.request()[0] == 200, 'Fresh login works after restoration')
check(fixture.service("$q=$db->prepare('SELECT COUNT(*) FROM quran_positions WHERE user_id=? AND surah=2 AND ayah=255');$q->execute([" + str(target['id']) + "]);echo json_encode((int)$q->fetchColumn());") == 1, 'Suspend/restore retains private progress')

# Admin suspension must also revoke an independently authenticated Admin cookie.
peer = authz.actor('admin')
peer_session = authz.Session(peer)
check(suspend(owner, peer, 'suspend')[0] == 200, 'Owner can suspend a generated admin')
check(peer_session.request('/admin')[1].endswith('/admin/login'), 'Owner suspension revokes retained Admin cookie')
check(suspend(owner, peer, 'restore')[0] == 200, 'Owner restores the generated admin')
check(peer_session.request('/admin')[1].endswith('/admin/login'), 'Restoration does not revive retained Admin cookie')

# Validation, duplicate username, private email reset and marketing consent.
before = state(target)
for changes in [{'username': 'admin'}, {'username': 'bad user'}, {'email': 'invalid'}, {'display_name': 'x'}, {'username': authz.manager['username']}]:
    fields = {'user_id': target['id'], 'email': target['email'], 'username': target['username'], 'display_name': 'Valid fixture', 'password': 'AdminFixture123!', **changes}
    check(manager.post('user-profile-update', **fields)[0] == 200, 'Invalid/conflicting profile input shows a handled error')
    check(state(target) == before, 'Rejected profile update preserves all account fields')
fixture.service("$db->prepare('UPDATE users SET email_marketing_opt_in=1 WHERE id=?')->execute([" + str(target['id']) + "]);echo json_encode(true);")
new_email = 'changed_' + secrets.token_hex(6) + '@example.test'
check(manager.post('user-profile-update', user_id=target['id'], email=new_email, username=target['username'], display_name='Updated fixture', password='AdminFixture123!')[0] == 200, 'Admin changes a generated email')
changed = state(target)
check(changed['email'] == new_email and changed['email_verified_at'] is None and int(changed['email_marketing_opt_in']) == 0, 'Email change removes verification and marketing consent')
check(new_mobile.request()[0] == 401 and new_mobile.request('/auth/refresh', refresh=True)[0] == 401, 'Email change revokes current access and refresh credentials')

# Deletion is exercised only on a newly generated disposable fixture user.
deleted = authz.actor('user')
fixture.service("$db->prepare('INSERT INTO quran_positions(user_id,surah,ayah,revision) VALUES (?,1,1,1)')->execute([" + str(deleted['id']) + "]);$db->prepare(\"INSERT INTO user_favorites(user_id,target_type,target_id) VALUES (?,'book',1)\")->execute([" + str(deleted['id']) + "]);echo json_encode(true);")
deleted_mobile = ApiSession(api(deleted))
deleted_public = PublicSession(deleted)
check(manager.post('user-delete', user_id=deleted['id'], email_confirmation='wrong@example.test', password='AdminFixture123!')[0] == 200, 'Incorrect deletion confirmation is handled')
check(state(deleted) is not False, 'Incorrect confirmation preserves the account')
check(manager.post('user-delete', user_id=deleted['id'], email_confirmation=deleted['email'], password='AdminFixture123!')[0] == 200, 'Explicit fixture-only deletion succeeds')
check(state(deleted) is False, 'Deleted fixture account is absent')
leftovers = fixture.service("$q=$db->prepare('SELECT (SELECT COUNT(*) FROM quran_positions WHERE user_id=?)+(SELECT COUNT(*) FROM user_favorites WHERE user_id=?)');$q->execute([" + str(deleted['id']) + "," + str(deleted['id']) + "]);echo json_encode((int)$q->fetchColumn());")
check(leftovers == 0, 'Explicit deletion removes dependent fixture progress and favorites')
check(deleted_mobile.request()[0] == 401 and deleted_mobile.request('/auth/refresh', refresh=True)[0] == 401, 'Deleted account credentials cannot authenticate/refresh')
check(deleted_public.request('/account')[0].endswith('/login'), 'Deleted account public session is revoked')


class Cards(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.rows = []
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'article' and 'data-admin-user' in values:
            self.rows.append(values.get('data-search', ''))


# 60 matching rows prove global lookup and search-preserving paging beyond50.
prefix = 'lookup' + secrets.token_hex(4)
fixture.service("$hash=password_hash('SearchFixture123!',PASSWORD_DEFAULT);$q=$db->prepare(\"INSERT INTO users(email,username,display_name,password_hash,role,email_verified_at) VALUES (?,?,?,?, 'user',UTC_TIMESTAMP())\");$db->beginTransaction();for($i=0;$i<60;$i++){$q->execute(['" + prefix + "_'.$i.'@example.test','" + prefix + "_'.$i,'" + prefix + " lookup '.$i,$hash]);}$db->commit();echo json_encode(true);")
_, _, first = manager.request('/admin?users_q=' + prefix)
_, _, second = manager.request('/admin?users_q=' + prefix + '&users_page=2')
first_rows, second_rows = Cards(first).rows, Cards(second).rows
check(len(first_rows) == 50 and len(second_rows) == 10, 'Global filtered lookup paginates50/10')
check(not set(first_rows) & set(second_rows), 'Filtered pages do not overlap')
check('users_q=' + prefix + '&amp;users_page=2' in first, 'Pagination retains the server search')
check('users_q=' + prefix + '&amp;users_page=1&amp;lang=ar' in first, 'Language switch retains filtered lookup context')
for query in [prefix + '%', prefix + '__', "' OR 1=1 --", '<script>alert(1)</script>', 'missing' + prefix]:
    _, _, page = manager.request('/admin?users_q=' + urllib.parse.quote(query))
    check(len(Cards(page).rows) == 0, 'Search treats wildcard/injection/markup as literal text')
    check('data-admin-user-empty hidden' not in page, 'Server empty result is visible')
_, _, page = manager.request('/admin?users_q=' + str(target['id']))
check(any(target['username'] in row for row in Cards(page).rows), 'Account ID is found across all pages')
_, _, page = manager.request('/admin?users_q=' + urllib.parse.quote(new_email))
check(len(Cards(page).rows) == 1 and new_email in Cards(page).rows[0], 'Updated email lookup is exact for the fixture')
audit = fixture.service("$q=$db->prepare(\"SELECT action FROM audit_logs WHERE actor_id=? AND target_type='user' AND target_id=?\");$q->execute([" + str(authz.manager['id']) + ",'" + str(target['id']) + "']);echo json_encode($q->fetchAll(PDO::FETCH_COLUMN));")
check(audit.count('admin-user-suspended') == 1 and audit.count('admin-user-restored') == 1 and 'admin-user-email-updated' in audit, 'Effective transitions have one durable audit event')
(fixture.ROOT/'storage/engineering/admin-users-fixture.json').write_text(json.dumps({'fixture_only':True,'search':prefix,'owner_email':authz.owner['email'],'manager_email':authz.manager['email'],'target_id':target['id'],'target_email':new_email}),encoding='utf-8')
print(f'PASS: {checks} user administration HTTP/state/token/search assertions (plus120 existing HTTP).')
