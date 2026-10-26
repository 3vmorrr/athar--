"""Read-only checkpoint screening. Findings contain locations, never secret values.

Heuristic screening is a review aid, not a guarantee that credentials are absent.
Scans all reachable Git blobs and nonignored working files, including untracked files.
"""
from pathlib import Path
import json
import re
import subprocess
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

patterns = {
    'private-key': rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----',
    'provider-token': rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,}|hf_[A-Za-z0-9]{30,}|AIza[0-9A-Za-z_-]{35})\b',
    'aws-access': rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
    'firebase-private': rb'"type"\s*:\s*"service_account"',
    'credential-url': rb'https?://[^\s/:@]+:[^\s/@]+@',
}
known = []
env = ROOT / '.env'
if env.exists():
    for line in env.read_text(encoding='utf-8-sig').splitlines():
        if '=' not in line or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        value = value.strip().strip('\"\'')
        if re.search(r'PASSWORD|SECRET|TOKEN|SETTINGS_KEY', key) and len(value) >= 12:
            known.append(value.encode())

def inspect(raw):
    # Deliberately invalid URL credentials used by media-origin rejection tests.
    raw = raw.replace(b'https://user:pass@', b'https://fixture-invalid@')
    found = [name for name, pattern in patterns.items() if re.search(pattern, raw)]
    if any(value in raw for value in known):
        found.append('matches-local-secret')
    return found

paths = git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')
working, large = [], []
for name in filter(None, paths):
    file = ROOT / name
    if not file.is_file():
        continue
    size = file.stat().st_size
    if size > 10_000_000:
        large.append({'path': name, 'bytes': size})
    if size <= 150_000_000:
        for kind in inspect(file.read_bytes()):
            working.append({'path': name, 'kind': kind})

objects = git('rev-list', '--objects', '--all').decode().splitlines()
process = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
history = []
count = 0
for item in objects:
    oid, _, path = item.partition(' ')
    process.stdin.write((oid + '\n').encode()); process.stdin.flush()
    header = process.stdout.readline().decode().split()
    if len(header) != 3:
        raise RuntimeError('Unexpected Git object header')
    raw = process.stdout.read(int(header[2])); process.stdout.read(1)
    if header[1] == 'blob':
        count += 1
        for kind in inspect(raw):
            history.append({'object': oid, 'path': path, 'kind': kind})
process.stdin.close(); process.wait()
remotes = []
for remote in git('remote').decode().splitlines():
    url = git('remote', 'get-url', remote).decode().strip()
    if '://' in url:
        parsed = urlsplit(url)
        url = urlunsplit((parsed.scheme, parsed.hostname or '', parsed.path, '', ''))
    remotes.append({'name': remote, 'url': url})
report = {'history_blobs': count, 'working_files': len(list(filter(None, paths))),
          'working_findings': working, 'history_findings': history, 'large_files': large, 'remotes': remotes}
output = ROOT / 'storage/engineering/checkpoint-security.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
raise SystemExit(1 if working or history else 0)
