# Quran canonical range and section navigation

Task: 03.1
Implementation source: `parallel/admin-foundation` (`58d561c5d485aed40d53fd01421cb24d5129f468`)
Status: **IMPLEMENTED / RUNTIME ACCEPTANCE PENDING**

The premium Mushaf reader now has one canonical navigation flow for exact
Surah/ayah, Juz 1–30, Hizb 1–60, Mushaf page 1–604, and explicit start/end
ranges including cross-Surah ranges. Existing immersive Quran hero and Mushaf
presentation are preserved.

`QuranIndex` validates all 604 page, 30 Juz and 240 quarter-Hizb boundaries as
strictly increasing canonical references. Invalid Surah/ayah, unknown sections,
out-of-range section numbers and reversed ranges fail closed.

`QuranReferenceInput` accepts ASCII, Arabic-Indic and Eastern Arabic/Persian
digits, including mixed input, normalizes them and rejects unrelated characters.

Saved Quran references now open the exact trusted in-app
`quran/<surah>/<ayah>` destination. The existing owner-bound notification
deep-link dispatcher is preserved; bookmark storage/mutation semantics are not
changed by this task.

Focused `QuranNavigationTest` coverage uses the packaged Quran assets for
boundary, Juz/page, cross-Surah, invalid-reference and mixed-digit cases.

Do not mark 03.1 COMPLETE until the integration lane runs native build/unit/
androidTest/lint/resource gates, Arabic RTL and English LTR device journeys,
large-text/landscape navigation-sheet checks, exact bookmark opening, Quran
source-integrity checks, diff/secret review and exact remote-SHA verification.
