# Quran functional integration

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-30:** [149-task actual source audit](../progress/ACTUAL_IMPLEMENTATION_AUDIT.md). 113 tasks have implemented source (77 in main, 36 only unmerged); 19 are accepted/closed, 58 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

## 03.1 — canonical navigation

Reviewed source `58d561c5d485aed40d53fd01421cb24d5129f468` against the current integration branch. Applied only the compatible canonical index/data/input/navigation patches. Manually reconciled current lazy reader, bookmark/account routes and navigation shell; retained protected reading-position recovery and captured-owner writes. No Quran asset, schema or server API changes.

Corrections: imported missing `dp` in the source sheet; Range opens the range form directly; exact scrolling includes the retained-data warning header; invalid reference input stays bounded and canonicalized across Western/Arabic/Persian digits. Pure domain tests use the actual bundled XML/index, while the source instrumentation tests retain Android asset/parser coverage.

Automated gate PASS: assembleDebug,68 JVM cases (including3 new navigation/reference cases), assembleDebugAndroidTest, lintDebug (3m47s);334 paired Arabic/English resources pass. Initial compile exposed the missing source import and was corrected. Device acceptance remains queued: no authorized connected ADB transport on28 October2026. Do not mark03.1 COMPLETE until actual Arabic/English/offline/exact-bookmark/recreation/insets/font acceptance is recorded.

Next:03.2 paged highlighted search, keeping the existing reader and canonical display text.

## 03.2 +03.6 — local search and ayah actions

Reviewed source0b579ab/b171260 and cb524e9. Retained the current reader and adopted only independent search domain/rendering and copy/share contracts. Canonical XML/display/export text is unchanged. Search uses20-result pages with exact opening and source-indexed highlights; explicit ayah actions copy text/reference/Tanzil attribution or open the Android chooser, without direct sending.

Corrections: old query results clear before debounce; changing result pages scrolls to the top; highlight foreground is paired with its background. Source's own108:1 test failed on actual Uthmani dagger Alif. Search-only optional-Alif matching accepts both written and omitted forms, including mixed forms, while public normalization/display text remains unchanged. The failing test retains exact canonical-text and highlight-slice assertions; added both orthographic forms and1:4/Musa coverage.

Focused16 JVM cases pass (search5/share3/domain8). Initial broader run had74/75 passing and exposed the dagger-Alif defect; subsequent targeted run passed all16. Debug APK/test APK/lint checkpoint PASS (2m49s).344 paired resources pass. Device copy/chooser/search/offline/RTL/accessibility acceptance remains open; no connected authorized transport.

## 03.3 +03.7 — Tafsir and daily entry

Source257385a/ad345fe reviewed; current UI header, identity, protected state, font and shared owner-scoped ContentRepository retained. Independent catalog/content state, premium source sheet, saved reference/book/page, correct requested-page retry and stale-result checks integrated. Old catalog cache without `pages` remains readable. API paging is bounded and typed; unpaged legacy callers still receive complete sanitized content. Source missing `Check` icon was adapted to the existing CheckCircle. Empty executable-only blocks are filtered; malformed source metadata fails closed.

PHP syntax, existing8/new26 provider assertions and4 Tafsir JVM contracts pass. No schema change, new cache or real provider send. Quran source text remains unchanged.

Daily entry reuses the existing shared QuranText::daily API. Native date/reference/source/version/text checks select only bundled canonical text; local fallback uses the same unsigned SHA-256/date contract. Direct PHP selections matched four Kotlin fixtures, including leap day. Local fallback and manual refresh are explicit; hub date refresh is lifecycle-bound. The private resume control reads protected state and never writes activity or resets unreadable ciphertext.3 daily JVM cases pass.

WP03 subsystem gate: debug APK, test APK, lint and 28 targeted JVM cases PASS (3m36s);354 resources and13352 Quran PHP assertions PASS. Device acceptance remains open. Reader scope restoration after process death is a known remaining03.7 repair; it is not claimed complete.

03.4/03.5 source audit: retain current exact reader/bookmark/owner guards and identical DataStore font settings. The isolated older bookmarked-ayah screen is superseded and is not imported. Actual bookmark sync/retry and font persistence/accessibility journeys remain pending.

## 03.7 reader view-state restoration repair

Both original branches recreated QuranViewModel at Surah1 after process death. Primitive, canonically validated SavedStateHandle selection now retains Surah/ayah, page/juz/hizb or inclusive range. The existing Nav owner and ViewModel key scope this view state to the account; it is separate from protected authoritative reading position. Unknown/corrupt selection fails visibly without clearing retained progress.

Reader tool state retains the native lazy-list scroll; consumed initial anchors and handled scroll requests prevent repeated jumps during recreation. Explicit daily/resume actions have distinct request tokens. Search query paging also restores without resetting an already-rendered page to its top.

Main Kotlin compilation and8 focused JVM cases pass (selection3/navigation3/protected-read2); final targeted rerun17s. New APK/lint are intentionally queued for the next coherent batch under the addendum cadence. No actual OS process-death/device proof is claimed. Earlier28-case/debug/test-APK/lint evidence above predates this repair.

2026-10-29:API34 read-only QA regression executed3 navigation +5 search +1 reading storage cases successfully, within a20-case cross-module batch. This validates actual Android asset parsing/search/private storage fixtures; copy/share/system chooser and real process-death/phone journeys remain open. See BOOKS_FUNCTIONAL_INTEGRATION.md.
