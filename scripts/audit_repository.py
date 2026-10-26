"""Create a reproducible source inventory without reading credentials or user data."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'audit'
SKIP = {'.git', 'storage', 'artifacts', 'node_modules', 'vendor', '.gradle', 'build', '.idea', '__pycache__'}
SECRET = {'.env', 'local.php', 'local.properties', 'settings.key'}
TEXT = {'.php', '.js', '.sql', '.md', '.json', '.py', '.ps1', '.kt', '.kts', '.xml', '.toml', '.yml', '.yaml', '.example'}

def files(directory):
    for path in sorted(directory.iterdir()):
        if path.name in SKIP or path.name in SECRET:
            continue
        if path.is_dir():
            yield from files(path)
        elif path.is_file():
            yield path

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    inventory, outlines, urls = [], [], {}
    for path in files(ROOT):
        relative = path.relative_to(ROOT).as_posix()
        if relative.startswith('docs/audit/FILE_INVENTORY') or relative.startswith('docs/audit/SOURCE_OUTLINE'):
            continue
        size = path.stat().st_size
        entry = {'path': relative, 'bytes': size}
        if path.suffix in TEXT and size < 2_000_000:
            raw = path.read_bytes()
            content = raw.decode('utf-8', errors='replace')
            entry.update(lines=len(content.splitlines()), sha256=hashlib.sha256(raw).hexdigest())
            if path.suffix in {'.php', '.js', '.sql', '.kt'}:
                matches = []
                for number, line in enumerate(content.splitlines(), 1):
                    if re.search(r'^(?:async )?function |^\s*(?:export )?(?:async )?function |^CREATE TABLE|^ALTER TABLE|^if \(\$path|^\s*(?:val|fun|class|data class|interface) ', line):
                        matches.append(f'{number}: {line[:220].rstrip()}')
                    for url in re.findall(r'https://[a-zA-Z0-9./@_?=+:%{}-]+', line):
                        urls.setdefault(url, []).append(f'{relative}:{number}')
                if matches:
                    outlines.append('## ' + relative + '\n\n```text\n' + '\n'.join(matches) + '\n```\n')
        elif relative.startswith('public/assets/models/'):
            entry['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        inventory.append(entry)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    (OUT / 'FILE_INVENTORY.json').write_text(json.dumps({'base_commit': head, 'files': inventory, 'provider_urls': urls}, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    rows = ['# Repository file inventory', '', f'Base commit: `{head}`. Generated with `python scripts/audit_repository.py`.', '', 'Credentials, local configuration, private storage, build products and dependency caches are excluded. Training source is inventoried; generated model exports are recorded by size only.', '', '| File | Bytes | Lines |', '|---|---:|---:|']
    rows += [f"| `{x['path']}` | {x['bytes']} | {x.get('lines', 'binary')} |" for x in inventory]
    (OUT / 'FILE_INVENTORY.md').write_text('\n'.join(rows) + '\n', encoding='utf-8')
    (OUT / 'SOURCE_OUTLINE.md').write_text('# Source declarations and route evidence\n\nGenerated static evidence, not a runtime verification report.\n\n' + '\n'.join(outlines), encoding='utf-8')
    print(f'Inventoried {len(inventory)} files; {len(urls)} provider/source URLs. No private data was read.')

if __name__ == '__main__':
    main()
