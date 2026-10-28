# Quran functional integration

## 03.1 — canonical navigation

Reviewed source `58d561c5d485aed40d53fd01421cb24d5129f468` against the current integration branch. Applied only the compatible canonical index/data/input/navigation patches. Manually reconciled current lazy reader, bookmark/account routes and navigation shell; retained protected reading-position recovery and captured-owner writes. No Quran asset, schema or server API changes.

Corrections: imported missing `dp` in the source sheet; Range opens the range form directly; exact scrolling includes the retained-data warning header; invalid reference input stays bounded and canonicalized across Western/Arabic/Persian digits. Pure domain tests use the actual bundled XML/index, while the source instrumentation tests retain Android asset/parser coverage.

Automated gate PASS: assembleDebug,68 JVM cases (including3 new navigation/reference cases), assembleDebugAndroidTest, lintDebug (3m47s);334 paired Arabic/English resources pass. Initial compile exposed the missing source import and was corrected. Device acceptance remains queued: no authorized connected ADB transport on28 October2026. Do not mark03.1 COMPLETE until actual Arabic/English/offline/exact-bookmark/recreation/insets/font acceptance is recorded.

Next:03.2 paged highlighted search, keeping the existing reader and canonical display text.
