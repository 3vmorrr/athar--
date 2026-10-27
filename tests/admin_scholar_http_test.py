"""Scholar metadata/portrait requests in the disposable copied schema/storage only."""
import os
import base64
from pathlib import Path
import re
import secrets
import struct
import subprocess
import time
import urllib.error
import urllib.request
import zlib

root = Path(__file__).resolve().parents[1]
assert root == Path(os.environ['ATHAR_RECOVERY_SANDBOX']).resolve()
assert re.fullmatch(r'recovery-http-[a-f0-9]{32}', root.name)
import admin_authorization_http_test as authz
fixture = authz.fixture
session = authz.Session(authz.actor('admin'))
checks = 0


def check(ok, label):
    global checks
    if not ok: raise AssertionError(label)
    checks += 1


def image(size=80):
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
    pixels = zlib.compress((b'\0' + b'\x11\x66\x44' * size) * size)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB', size, size, 8, 2, 0, 0, 0)) + chunk(b'IDAT', pixels) + chunk(b'IEND', b'')


def submit(fields, upload=None):
    _, _, session.page = session.request('/admin?lang=en')
    fields = {'_csrf': fixture.csrf(session.page), **fields}
    boundary = 'athar_' + secrets.token_hex(12)
    body = b''
    for key, value in fields.items():
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
    if upload is not None:
        name, content, mime = upload
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="scholar_image"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode() + content + b'\r\n'
    body += f'--{boundary}--\r\n'.encode()
    request = urllib.request.Request(fixture.base + '/admin', body, {'Content-Type':'multipart/form-data; boundary=' + boundary})
    try: response = session.http.open(request, timeout=20)
    except urllib.error.HTTPError as error: response = error
    return response.code, response.geturl(), response.read().decode()


def portrait(key, private=False, authenticated=True):
    url = fixture.base + ('/admin' if private else '') + '/scholar-image?key=' + key
    client = session.http if authenticated else urllib.request.build_opener()
    try: response = client.open(url, timeout=10)
    except urllib.error.HTTPError as error: response = error
    return response.code, response.headers, response.read()


def row(identifier):
    return fixture.service("$q=$db->prepare('SELECT * FROM scholars WHERE id=?');$q->execute([" + str(identifier) + "]);echo json_encode($q->fetch(PDO::FETCH_ASSOC));")


suffix = secrets.token_hex(5)
name = 'Scholar fixture ' + suffix
fields = {'kind':'scholar','name':name,'biography':'Fixture biography <script>unsafe()</script>','language':'en','source_url':'https://example.invalid/approved','rights_basis':'Fixture permission only','sort_order':'-12'}
status, url, page = submit(fields)
identifier = fixture.service("$q=$db->prepare('SELECT id FROM scholars WHERE name=?');$q->execute(['" + name + "']);echo json_encode((int)$q->fetchColumn());")
check(status == 200 and identifier > 0 and url.endswith('#admin-content'), 'Admin creates optional-portrait draft with content-panel context')
saved = row(identifier)
check(saved['image_key'] is None and saved['language'] == 'en' and int(saved['sort_order']) == -12 and int(saved['is_published']) == 0, 'All metadata and truthful draft status are stored')
check(saved['source_url'] == fields['source_url'] and saved['rights_basis'] == fields['rights_basis'], 'Source/rights metadata is retained')
check('<script>unsafe()</script>' not in page and '&lt;script&gt;unsafe()&lt;/script&gt;' in page, 'Biography is escaped in editorial HTML')
check('Approved source (optional)' in page and 'Optional portrait:' in page, 'Create/edit controls are bilingual')
for change in [{'name':'x'}, {'biography':'b'*6001}, {'rights_basis':'r'*501}, {'language':'invalid'}, {'source_url':'http://example.invalid'}, {'source_url':'https://user:pass@example.invalid'}, {'sort_order':'2.5'}, {'sort_order':'100001'}]:
    _, _, page = submit({**fields, **change, 'kind':'content','content_type':'scholar','content_action':'update','id':identifier})
    check(row(identifier) == saved and 'data-admin-form-error' in page, 'Invalid metadata does not mutate the scholar')

edit = {**fields,'kind':'content','content_type':'scholar','content_action':'update','id':identifier}
for upload in [('bad.svg', b'<svg xmlns="http://www.w3.org/2000/svg"></svg>', 'image/png'), ('small.png', image(10), 'image/png'), ('large.png', image() + bytes(3*1024*1024), 'image/png')]:
    _, _, page = submit(edit, upload)
    check(row(identifier)['image_key'] is None and 'data-admin-form-error' in page, 'Unsupported/small/oversized portrait is refused without a stored asset')
png = image()
_, _, page = submit(edit, ('untrusted.php', png, 'application/octet-stream'))
key = row(identifier)['image_key']
check(bool(re.fullmatch(r'[a-f0-9]{40}\.png', key)), 'Server content inspection determines a private random PNG filename')
check((root/'storage/scholars'/key).read_bytes() == png, 'Uploaded portrait bytes are stored outside public root')
check(portrait(key)[0] == 404, 'Unpublished portrait is unavailable by public key')
status, headers, body = portrait(key, private=True)
check(status == 200 and body == png and headers.get('Content-Type') == 'image/png', 'Authorized Admin previews its draft portrait')
check('private' in headers.get('Cache-Control', '') and headers.get('X-Content-Type-Options') == 'nosniff', 'Preview is private and cannot sniff active content')
check(b'Athar Admin Sign In' in portrait(key, private=True, authenticated=False)[2] or b'admin-auth-form' in portrait(key, private=True, authenticated=False)[2], 'Unauthenticated preview redirects to sign-in')
submit({'kind':'content','content_type':'scholar','content_action':'toggle','id':identifier})
check(portrait(key)[0] == 200, 'Publishing exposes the referenced portrait')
check('no-store' in portrait(key)[1].get('Cache-Control',''), 'Publication revocation is not hidden by a long public cache')
submit({**edit,'publish':'1'}, ('replacement.png', image(81), 'image/png'))
replacement = row(identifier)['image_key']
check(replacement != key and not (root/'storage/scholars'/key).exists() and portrait(key)[0] == 404, 'Replacement removes only its obsolete private portrait')
for encoder, extension, mime in [('imagejpeg','jpg','image/jpeg'), ('imagewebp','webp','image/webp')]:
    encoded = fixture.service('$image=imagecreatetruecolor(80,80);ob_start();' + encoder + '($image);$bytes=ob_get_clean();imagedestroy($image);echo json_encode(base64_encode($bytes));')
    payload = base64.b64decode(encoded)
    submit({**edit,'publish':'1'}, ('portrait.'+extension,payload,mime))
    previous, replacement = replacement, row(identifier)['image_key']
    check(replacement.endswith('.'+extension) and portrait(replacement)[1].get('Content-Type') == mime and portrait(replacement)[2] == payload, 'Actual JPEG/WebP uploads are inspected, stored and served correctly')
    check(not (root/'storage/scholars'/previous).exists(), 'Cross-format replacement cleans the preceding registered file')
series_id = fixture.service("$db->prepare(\"INSERT INTO series(scholar_id,title) VALUES (?,'Fixture dependency')\")->execute([" + str(identifier) + "]);echo json_encode((int)$db->lastInsertId());")
_, _, page = submit({'kind':'content','content_type':'scholar','content_action':'delete','id':identifier})
check('Reassign this scholar' in page and row(identifier) and portrait(replacement)[0] == 200, 'Deleting a referenced scholar is refused without detaching content')
fixture.service("$db->prepare('DELETE FROM series WHERE id=?')->execute([" + str(series_id) + "]);echo json_encode(true);")
submit({'kind':'content','content_type':'scholar','content_action':'delete','id':identifier})
check(row(identifier) is False and not (root/'storage/scholars'/replacement).exists(), 'Unreferenced scholar deletion cleans its registered portrait')
check(portrait(replacement)[0] == 404, 'Deleted portraits cannot be fetched by a remembered URL')
for action in ['update','toggle','delete']:
    _, _, page = submit({**edit,'content_action':action})
    check('This scholar no longer exists.' in page, 'Missing targets cannot produce a false successful mutation')

# Real row lock/deletion race: editor must reread before storing the upload.
submit({**fields,'name':name + ' race'})
race_id = fixture.service("$q=$db->prepare('SELECT id FROM scholars WHERE name=?');$q->execute(['" + name + " race']);echo json_encode((int)$q->fetchColumn());")
program = fixture.setup + '$db->beginTransaction();$db->prepare("DELETE FROM scholars WHERE id=?")->execute([' + str(race_id) + ']);echo "LOCKED\\n";flush();sleep(2);$db->commit();'
process = subprocess.Popen([fixture.php,'-r',program], cwd=root, env=fixture.environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
before_files = set((root/'storage/scholars').glob('*'))
try:
    check(process.stdout.readline().strip() == 'LOCKED', 'Concurrent deletion holds a real scholar row lock')
    started = time.monotonic()
    _, _, page = submit({**edit,'id':race_id}, ('racing.png',png,'image/png'))
    check(time.monotonic()-started >= 1 and 'This scholar no longer exists.' in page, 'Editor waits and rereads the deleted target')
    check(set((root/'storage/scholars').glob('*')) == before_files, 'Losing a deletion race cannot orphan a new upload')
finally: process.communicate(timeout=10)
check(process.returncode == 0, 'Concurrent fixture transaction finishes cleanly')
print(f'PASS: {checks} actual HTTP scholar/portrait assertions (plus120 existing HTTP).')
if os.environ.get('ATHAR_TEST_NODE'):
    subprocess.run([os.environ['ATHAR_TEST_NODE'],str(root/'tests/admin_scholar_browser_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=session.account['email']),check=True,timeout=120)
