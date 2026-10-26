"""Build the shared factual Quran reference index from reviewed provider metadata."""
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
cache = ROOT / 'storage/engineering/quran-meta.json'
if not cache.exists():
    cache.parent.mkdir(parents=True, exist_ok=True)
    with urlopen(Request('https://api.alquran.cloud/v1/meta', headers={'User-Agent': 'AtharReferenceBuilder/1.0'}), timeout=30) as response:
        candidate = response.read(2_000_000)
    parsed = json.loads(candidate)['data']['surahs']['references']
    if len(parsed) != 114 or sum(item['numberOfAyahs'] for item in parsed) != 6236:
        raise ValueError('Provider metadata failed canonical Quran validation.')
    cache.write_bytes(candidate)
raw = cache.read_bytes()
data = json.loads(raw)['data']
surahs = data['surahs']['references']
assert len(surahs) == 114 and sum(x['numberOfAyahs'] for x in surahs) == 6236
offset = 0
items = []
for item in surahs:
    assert item['number'] == len(items) + 1
    items.append({'number': item['number'], 'name': item['name'], 'english_name': item['englishName'], 'ayah_count': item['numberOfAyahs'], 'start_index': offset + 1})
    offset += item['numberOfAyahs']
sections = {}
for name, provider_key in [('page', 'pages'), ('juz', 'juzs'), ('hizb_quarter', 'hizbQuarters')]:
    sections[name] = data[provider_key]['references']
result = {'version': 1, 'source': 'https://api.alquran.cloud/v1/meta', 'source_sha256': hashlib.sha256(raw).hexdigest(), 'reviewed_date': '2026-10-25', 'total_ayahs': offset, 'surahs': items, 'sections': sections}
target = ROOT / 'shared/quran-index.json'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Wrote 114 surahs / 6236 ayahs with source checksum and section boundaries.')
