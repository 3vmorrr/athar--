"""Verify first-class Arabic/English strings, including numbered format arguments."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "android/app/src/main/res"


def strings(locale: str) -> dict[str, ET.Element]:
    elements = list(ET.parse(root / locale / "strings.xml").getroot())
    names = Counter(item.attrib["name"] for item in elements)
    duplicates = [name for name, count in names.items() if count > 1]
    assert not duplicates, f"{locale}: duplicate resource names: {duplicates}"
    return {item.attrib["name"]: item for item in elements}


arabic, english = strings("values"), strings("values-en")
assert arabic.keys() == english.keys(), f"Unpaired resources: {arabic.keys() ^ english.keys()}"
pattern = re.compile(r"%(?:\d+\$)?[-#+0,(<]*\d*(?:\.\d+)?[sdf]")
for key in arabic:
    assert arabic[key].tag == english[key].tag, f"Resource kind differs: {key}"
    if arabic[key].tag == "plurals":
        argument_sets = []
        for language, element in [("ar", arabic[key]), ("en", english[key])]:
            quantities = [item.attrib["quantity"] for item in element]
            assert len(quantities) == len(set(quantities)), f"Duplicate plural quantity: {key}"
            required = {"zero", "one", "two", "few", "many", "other"} if language == "ar" else {"one", "other"}
            assert required <= set(quantities), f"Missing {language} plural forms: {key}"
            # Arabic singular/dual may omit the explicit number; compare the argument contract across forms.
            argument_sets.append({match.group(0) for item in element for match in pattern.finditer("".join(item.itertext()))})
        assert argument_sets[0] == argument_sets[1], f"Inconsistent plural arguments: {key}"
        continue
    ar = Counter(match.group(0) for match in pattern.finditer("".join(arabic[key].itertext())))
    en = Counter(match.group(0) for match in pattern.finditer("".join(english[key].itertext())))
    assert ar == en, f"Inconsistent format arguments for {key}: {ar} versus {en}"
print(f"PASS: {len(arabic)} unique paired Arabic/English resources and matching format arguments.")
