"""Compile checked-in Google Material Symbols SVG paths into native Compose vectors.

No network is needed for a clean build. Run after deliberately updating reviewed source SVGs.
"""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NAMES = {
    'Pause': 'pause', 'Stop': 'stop', 'Headphones': 'headphones', 'Repeat': 'repeat', 'Speed': 'speed',
    'Share': 'share', 'PlayArrow': 'play_arrow', 'CheckCircle': 'check_circle', 'Fullscreen': 'fullscreen', 'Close': 'close', 'ChatBubble': 'chat_bubble', 'ExpandMore': 'expand_more',
    'Home': 'home', 'MenuBook': 'menu_book', 'School': 'school', 'Spa': 'spa',
    'Person': 'person', 'ArrowBack': 'arrow_back', 'ArrowForward': 'arrow_forward',
    'Search': 'search', 'Visibility': 'visibility', 'VisibilityOff': 'visibility_off',
    'CalendarMonth': 'calendar_month', 'NightsStay': 'dark_mode', 'Lock': 'lock',
    'BookmarkAdd': 'bookmark_add', 'Bookmark': 'bookmark', 'BookmarkFilled': 'bookmark_fill1',
    'Favorite': 'favorite', 'FavoriteFilled': 'favorite_fill1',
}
header = '''// Generated from reviewed Google Material Symbols sources; Apache-2.0.
// Run: python scripts/prepare_material_symbols.py
package org.athar.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object AtharIcons {
'''
parts = [header]
manifest = []
for name, filename in NAMES.items():
    source = ROOT / 'android/visual-sources/material-symbols' / f'{name}.svg'
    raw = source.read_bytes()
    element = ET.fromstring(raw)
    x, y, width, height = map(float, element.attrib['viewBox'].split())
    mirrored = name in ('ArrowBack', 'ArrowForward', 'MenuBook')
    parts.append(f'    val {name}: ImageVector by lazy {{\n        ImageVector.Builder(name = "{name}", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = {width}f, viewportHeight = {height}f, autoMirror = {str(mirrored).lower()})\n            .addGroup(name = "sourceViewport", translationX = {-x}f, translationY = {-y}f)\n')
    for child in element:
        if child.tag.rsplit('}', 1)[-1] != 'path' or 'transform' in child.attrib:
            raise ValueError(f'Unsupported SVG element in {name}')
        path = json.dumps(child.attrib['d'])
        parts.append(f'            .addPath(pathData = PathParser().parsePathString({path}).toNodes(), fill = SolidColor(Color.Black))\n')
    parts.append('            .clearGroup().build()\n    }\n')
    folder = filename.removesuffix('_fill1')
    manifest.append({'name': name, 'author': 'Google', 'license': 'Apache-2.0', 'style': 'Material Symbols Outlined', 'optical_size': 24, 'weight': 400, 'fill': 1 if filename.endswith('_fill1') else 0, 'source_url': f'https://github.com/google/material-design-icons/blob/master/symbols/web/{folder}/materialsymbolsoutlined/{filename}_24px.svg', 'sha256': hashlib.sha256(raw).hexdigest()})
parts.append('}\n')
(ROOT / 'android/app/src/main/java/org/athar/app/core/ui/AtharIcons.kt').write_text(''.join(parts), encoding='utf-8')
(ROOT / 'android/visual-sources/material-symbols/SOURCES.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'Generated {len(manifest)} native Material Symbols vectors from checked-in SVGs.')
