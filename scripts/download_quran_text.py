"""Download the verbatim official Tanzil XML with its original copyright notice."""
from pathlib import Path
import hashlib,json,urllib.request,urllib.parse,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
url='https://tanzil.net/pub/download/index.php?'+urllib.parse.urlencode({'quranType':'uthmani','outType':'xml','marks':'true','sajdah':'true','alef':'true','tatweel':'true','agree':'true'})
raw=urllib.request.urlopen(url,timeout=30).read()
tree=ET.fromstring(raw)
index=json.loads((root/'shared/quran-index.json').read_text(encoding='utf-8'))
surahs=tree.findall('sura')
assert len(surahs)==114,'Incomplete Quran source'
for node,reference in zip(surahs,index['surahs']):
    assert int(node.attrib['index'])==reference['number']
    assert len(node.findall('aya'))==reference['ayah_count']
assert b'Tanzil' in raw and (b'Creative Commons' in raw or b'License' in raw),'Missing original notice'
folder=root/'shared/quran';folder.mkdir(parents=True,exist_ok=True)
(folder/'tanzil-uthmani.xml').write_bytes(raw)
(folder/'SOURCE.json').write_text(json.dumps({'source':url,'project':'Tanzil Project','source_url':'https://tanzil.net/','terms_url':'https://tanzil.net/docs/text_license','version':'1.1','downloaded':'2026-10-25','sha256':hashlib.sha256(raw).hexdigest(),'surahs':114,'ayahs':6236,'text_policy':'Verbatim original XML, including original notice. Normalization is limited to internal search; display always uses original text.'},indent=2)+'\n',encoding='utf-8')
print('Validated and saved the verbatim official XML: 114 surahs / 6236 ayahs, notice retained.')
