"""Actual cookie/API Khatmah contract on copied disposable fixtures; no real worship data."""
import re
import admin_batch_fixture as fixture

checks = 0
def check(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1

reader = fixture.Session('user')
other = fixture.Session('user')
token, other_token = reader.token(), other.token()
today, tomorrow = fixture.service("echo json_encode([gmdate('Y-m-d'),gmdate('Y-m-d',time()+86400)]);")
_, _, page = reader.request('/login')
code, url, page = reader.request('/login', {'_csrf': fixture.csrf(page), 'email': reader.email, 'password': 'AdminFixture123!'})
check(code == 200 and url.endswith('/account'), 'real cookie login')
check(fixture.api('/me/khatmah', token)[1]['data'] is None, 'no invented plan')
initial_hash = re.search(r'name="expected_plan_hash" value="([a-f0-9]{64})"', page).group(1)
body = {'expected_plan_hash': initial_hash, 'start_date': today, 'target_date': tomorrow, 'completed_pages': 600, 'expected_revision': 0}
check(fixture.api('/me/khatmah', token, 'PUT', {k:v for k,v in body.items() if k != 'expected_revision'})[0] == 422, 'blind legacy mutation safely refused')
code, _, page = reader.request('/account', {'_csrf': fixture.csrf(page), 'action': 'plan', **body})
check(code == 200 and 'تم تحديث خطة الختمة.' in page, 'cookie create uses canonical service')
plan = fixture.api('/me/khatmah', token)[1]['data']
check(plan['revision'] == 1 and plan['daily_pages'] == 2 and plan['completed_pages'] == 600, 'web write visible to native API with inclusive workload')
hash1 = plan['plan_hash']
check(re.fullmatch('[a-f0-9]{64}', hash1) is not None and hash1 != initial_hash, 'owned server fingerprint differs from absent precondition')
check(fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 602, 'expected_revision': 1, 'expected_plan_hash': 'a'*63})[0] == 422, 'malformed fingerprint refused')
check(fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 602, 'expected_revision': 1, 'expected_plan_hash': '0'*64})[0] == 409, 'wrong fingerprint refused without mutation')
fixture.service("$db->exec('UPDATE khatmah_plans SET completed_pages=599 WHERE user_id='.(int)" + str(reader.account['id']) + ");echo json_encode(true);")
check(fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 602, 'expected_revision': 1, 'expected_plan_hash': hash1})[0] == 409, 'same revision restored values cannot be overwritten')
check(fixture.api('/me/khatmah', token)[1]['data']['completed_pages'] == 599, 'restored values retained after stale fingerprint')
fixture.service("$db->exec('UPDATE khatmah_plans SET completed_pages=600 WHERE user_id='.(int)" + str(reader.account['id']) + ");echo json_encode(true);")
body['expected_plan_hash'] = hash1
code, result = fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 603, 'expected_revision': 1})
check(code == 200 and result['data']['progress_percent'] == 99 and result['data']['revision'] == 2, 'API update shares exact revision/rounding contract')
code, _, page = reader.request('/account', {'_csrf': fixture.csrf(page), 'action': 'plan', **body, 'completed_pages': 601, 'expected_revision': 1})
check(code == 200 and 'تغيرت الخطة في جلسة أخرى' in page, 'stale cookie update is recoverable')
check(re.search(r'name="completed_pages"[^>]*value="601"', page) is not None, 'conflict retains typed draft')
check(re.search(r'name="expected_revision" value="1"', page) is not None, 'conflict never silently rebases draft')
check(fixture.api('/me/khatmah', token)[1]['data']['completed_pages'] == 603, 'stale web writer cannot overwrite API result')
check(re.search(r'name="expected_plan_hash" value="'+hash1+'"', page) is not None, 'stale form retains original fingerprint')
body['expected_plan_hash'] = result['data']['plan_hash']
code, _, page = reader.request('/account', {'_csrf': fixture.csrf(page), 'action': 'plan', **body, 'completed_pages': 604, 'expected_revision': 2})
check(code == 200 and fixture.api('/me/khatmah', token)[1]['data']['status'] == 'active', 'record604 does not silently finish')
code, conflict = fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 601, 'expected_revision': 2})
check(code == 409 and conflict['error']['fields']['current']['completed_pages'] == 604, 'native stale response carries owned current version')
code, _, page = reader.request('/account', {'_csrf': fixture.csrf(page), 'action': 'plan-status', 'status': 'finished', 'expected_revision': 3, 'expected_plan_hash': fixture.api('/me/khatmah', token)[1]['data']['plan_hash']})
check(code == 200 and fixture.api('/me/khatmah', token)[1]['data']['status'] == 'finished', 'cookie finish uses explicit action service')
check(fixture.api('/me/khatmah', token, 'PUT', {**body, 'completed_pages': 10, 'expected_revision': 4, 'expected_plan_hash': fixture.api('/me/khatmah', token)[1]['data']['plan_hash']})[0] == 422, 'finished generic update cannot bypass restart')
check(fixture.api('/me/khatmah/action', token, 'POST', {'action': 'resume', 'expected_revision': 4, 'expected_plan_hash': fixture.api('/me/khatmah', token)[1]['data']['plan_hash']})[0] == 422, 'finished resume denied')
restart = {'action': 'restart', 'start_date': today, 'target_date': tomorrow, 'expected_revision': 4, 'expected_plan_hash': fixture.api('/me/khatmah', token)[1]['data']['plan_hash']}
code, result = fixture.api('/me/khatmah/action', token, 'POST', restart)
check(code == 200 and result['data']['revision'] == 5 and result['data']['completed_pages'] == 0, 'native explicit restart')
check(fixture.api('/me/khatmah/action', token, 'POST', restart)[1]['data']['revision'] == 5, 'committed restart replay does not reset again')
check(fixture.api('/me/khatmah', other_token)[1]['data'] is None, 'other actor sees no private plan')
check(fixture.api('/me/khatmah', other_token, 'PUT', {**body, 'user_id': reader.account['id']})[0] == 422, 'foreign owner selector rejected')
check(fixture.api('/me/khatmah', token)[1]['data']['revision'] == 5, 'foreign input cannot mutate owner plan')
code, _, page = reader.request('/account', {'action': 'plan', **body})
check(code == 419 and 'انتهت صلاحية الطلب' in page, 'cookie write requires CSRF')
check(fixture.api('/me/khatmah', token)[1]['data']['revision'] == 5, 'CSRF refusal leaves plan unchanged')
check(fixture.service("echo json_encode((int)$db->query('SELECT COUNT(*) FROM activity_events')->fetchColumn());") == 0, 'HTTP plan operations create no reading activity')
print(f'PASS: {checks} actual cookie/API Khatmah revision/workload/conflict/draft/owner/CSRF/lifecycle assertions; generated private fixtures only.')
