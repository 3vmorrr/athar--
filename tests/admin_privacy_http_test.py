"""Canary-based Admin privacy and durable operational journal acceptance."""
import re
import secrets
import urllib.parse

import admin_user_management_http_test as users  # 182 existing HTTP checks.

fixture, authz = users.fixture, users.authz
checks = 0


def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1


def audit_section(page):
    match = re.search(r'<section[^>]*id="admin-audit"[\s\S]*?</section>', page)
    if not match:
        raise AssertionError('Expected operational audit panel')
    return match.group(0)


def row_ids(page):
    return re.findall(r'data-audit-id="(\d+)"', audit_section(page))


private = authz.actor('user')
fixture.service("$db->prepare('INSERT INTO quran_positions(user_id,surah,ayah,revision) VALUES (?,114,6,1)')->execute([" + str(private['id']) + "]);$db->prepare(\"INSERT INTO account_daily_stats(user_id,activity_date,quran_sessions,lesson_completions,adhkar_completions,book_downloads) VALUES (?,'1900-01-01',32101,32102,32103,32104)\")->execute([" + str(private['id']) + "]);echo json_encode(true);")
mobile = users.ApiSession(users.api(private))
status, export = mobile.request('/me/export')
check(status == 200, 'Private fixture owner can export their own data')
private_data = export['data']
check(int(private_data['quran_positions'][0]['ayah']) == 6 and int(private_data['account_daily_stats'][0]['quran_sessions']) == 32101, 'Private export retains the canary reading/activity data')
for session in [authz.manager_session, authz.owner_session]:
    _, _, page = session.request('/admin?lang=en&users_q=' + urllib.parse.quote(private['email']))
    check(all(not re.search(r'(?<![0-9A-Za-z])' + re.escape(value) + r'(?![0-9A-Za-z])', page) for value in ['114:6','32101','32102','32103','32104','1900-01-01']), 'Admin and Owner HTML contain no private reading/activity values')
    check('Operational account details' in page and 'private worship history' in page, 'Account detail is explicitly operational only')
    check('Last saved Quran position' not in page and 'Saved lesson progress' not in page, 'Private detail controls are absent, not merely hidden')
    check('Privacy-bounded operational indicators' in page and 'Completed Adhkar' not in page, 'Overview excludes individual and aggregate worship metrics')

suffix = secrets.token_hex(6)
target_id = str(900000000 + secrets.randbelow(90000000))
secret = 'opaque_private_' + suffix
fixture.service("$db->beginTransaction();$q=$db->prepare(\"INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,'admin-user-profile-updated','user',?)\");for($i=0;$i<205;$i++)$q->execute([" + str(authz.owner['id']) + ",'" + target_id + "']);$q=$db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)');$q->execute([" + str(authz.owner['id']) + ",'private-reading-" + suffix + "','user','" + target_id + "']);$q->execute([" + str(authz.owner['id']) + ",'admin-user-profile-updated','quran-position','" + target_id + "']);$q->execute([" + str(authz.owner['id']) + ",'admin-user-profile-updated','user','" + secret + "']);$db->commit();echo json_encode(true);")
pages = []
for number in [1,2,3]:
    _, _, page = authz.manager_session.request('/admin?audit_q=' + target_id + '&audit_page=' + str(number))
    pages.append(page)
ids = [row_ids(page) for page in pages]
check([len(group) for group in ids] == [100,100,5], 'Only205 eligible operations paginate100/100/5')
check(len(set(value for group in ids for value in group)) == 205, 'Operational journal pages do not overlap')
check('audit_q=' + target_id + '&amp;audit_page=2' in pages[0], 'Journal paging retains filtered target context')
check('audit_q=' + target_id + '&amp;audit_page=1&amp;lang=ar' in pages[0], 'Journal locale switching retains query/page')
for page in pages:
    panel = audit_section(page)
    check(authz.owner['email'] not in panel and authz.manager['email'] not in panel, 'Raw actor emails are absent from audit HTML and attributes')
    check('rb***@example.test' in panel and 'Operation details' in panel and 'Record ID' in panel, 'Masked metadata and explicit record detail are present')
    check('private-reading-' + suffix not in panel and 'quran-position' not in panel, 'Unknown/private audit operations are excluded')

# Noncanonical target text must not leak through DOM, search attributes or search counts.
_, _, page = authz.manager_session.request('/admin?audit_q=admin-user-profile-updated')
check(secret not in audit_section(page) and 'data-search=' not in audit_section(page), 'Raw opaque target text never reaches the audit view')
for query in [secret, target_id + '%', target_id + '__', "' OR 1=1 --"]:
    _, _, page = authz.manager_session.request('/admin?audit_q=' + urllib.parse.quote(query))
    check(not row_ids(page), 'Audit search cannot infer opaque targets or treat wildcards/injection as SQL')
    check('No matching operations.' in audit_section(page), 'Empty journal result is visible')

_, _, broad = authz.manager_session.request('/admin')
_, _, filtered = authz.manager_session.request('/admin?audit_q=' + target_id)
overview = lambda page: re.search(r'<section[^>]*id="admin-overview"[\s\S]*?</section>', page).group(0)
check(overview(broad) == overview(filtered), 'Operational overview count does not become the filtered journal count')
active = fixture.service("echo json_encode((int)$db->query('SELECT COUNT(DISTINCT user_id) FROM account_daily_stats WHERE activity_date>=DATE_SUB(CURRENT_DATE,INTERVAL 29 DAY)')->fetchColumn());")
if active < 5:
    check('&lt;5' in overview(broad), 'Small active-user cohort is suppressed')
else:
    check(format(active, ',') in overview(broad), 'Larger active-user cohort shows a genuine count')
status, after = mobile.request('/me/export')
check(status == 200 and after['data']['quran_positions'] == private_data['quran_positions'] and after['data']['account_daily_stats'] == private_data['account_daily_stats'], 'Admin reads never delete or rewrite private authoritative data')
check(fixture.service("$q=$db->prepare('SELECT COUNT(*) FROM audit_logs WHERE target_id=?');$q->execute(['" + target_id + "']);echo json_encode((int)$q->fetchColumn());") == 207, 'Privacy filtering preserves immutable original audit rows')
(fixture.ROOT/'storage/engineering/admin-privacy-fixture.json').write_text(__import__('json').dumps({'fixture_only':True,'audit_target':target_id,'private_owner_email':private['email']}),encoding='utf-8')
print(f'PASS: {checks} Admin private-data/journal canary assertions (plus{fixture.checks+authz.checks+users.checks} existing HTTP).')
