# Quran functional integration

## 03.1 — canonical navigation

Reviewed source `58d561c5d485aed40d53fd01421cb24d5129f468` against the current integration branch. Applied only the compatible canonical index/data/input/navigation patches. Manually reconciled current lazy reader, bookmark/account routes and navigation shell; retained protected reading-position recovery and captured-owner writes. No Quran asset, schema or server API changes.

Corrections: imported missing `dp` in the source sheet; Range opens the range form directly; exact scrolling includes the retained-data warning header; invalid reference input stays bounded and canonicalized across Western/Arabic/Persian digits. Pure domain tests use the actual bundled XML/index, while the source instrumentation tests retain Android asset/parser coverage.

Automated gate PASS: assembleDebug,68 JVM cases (including3 new navigation/reference cases), assembleDebugAndroidTest, lintDebug (3m47s);334 paired Arabic/English resources pass. Initial compile exposed the missing source import and was corrected. Device acceptance remains queued: no authorized connected ADB transport on28 October2026. Do not mark03.1 COMPLETE until actual Arabic/English/offline/exact-bookmark/recreation/insets/font acceptance is recorded.

Next:03.2 paged highlighted search, keeping the existing reader and canonical display text.

## 03.2 +03.6 — local search and ayah actions

Reviewed source0b579ab/b171260 and cb524e9. Retained the current reader and adopted only independent search domain/rendering and copy/share contracts. Canonical XML/display/export text is unchanged. Search uses20-result pages with exact opening and source-indexed highlights; explicit ayah actions copy text/reference/Tanzil attribution or open the Android chooser, without direct sending.

Corrections: old query results clear before debounce; changing result pages scrolls to the top; highlight foreground is paired with its background. Source's own108:1 test failed on actual Uthmani dagger Alif. Search-only optional-Alif matching accepts both written and omitted forms, including mixed forms, while public normalization/display text remains unchanged. The failing test retains exact canonical-text and highlight-slice assertions; added both orthographic forms and1:4/Musa coverage.

Focused16 JVM cases pass (search5/share3/domain8). Initial broader run had74/75 passing and exposed the dagger-Alif defect; subsequent targeted run passed all16. Debug APK/test APK/lint checkpoint PASS (2m49s).344 paired resources pass. Device copy/chooser/search/offline/RTL/accessibility acceptance remains open; no connected authorized transport.
