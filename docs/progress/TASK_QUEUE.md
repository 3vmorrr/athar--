# Ordered execution queue

Parallel/admin-foundation leads implementation; codex/android-ecosystem integrates one logical task at a time from the recorded exact source cursor. Inspect both local worktrees, preserve newer security/offline logic and premium UI, integrate, fix, test, document, commit, push and verify the remote SHA before the next task. Never bulk merge or count source-only implementation as accepted. Keep the parallel worktree and classify every interleaved commit. WP02.8 remains PARTIAL and deferred until all available parallel work is integrated and stabilized, then complete it and audit WP1–WP24. External gates stay open while independent integration proceeds. No per-task permission is required.

Snapshot: 2026-10-27. Current task: **18.4**. Open tasks: **129**. Overall estimate: **51%** (engineering estimate, not test coverage).

The JSON beside this file is the editable source. Run `python scripts/render-task-queue.py` after an update. COMPLETE requires the recorded applicable gates, docs, reviewed commit and verified normal push. Commit receipts live in TASK_COMPLETIONS.md to avoid self-referential hashes. Dependencies reference queue IDs; blocked external gates may be skipped only for an independently ready task, with the skip reported. The parallel branch leads implementation; this track accepts one integration task at a time under PARALLEL_INTEGRATION.json.

Contribution values are approximate remaining scope budgets in percentage points, not automatic earned credit. Overall estimates must be reviewed against actual source and verification after each completion. Previously completed rows have zero new contribution because their work is already included in the baseline. Phase2 task weights are recorded separately in JSON; shared visual infrastructure belongs to phase21, even when ordered early as a dependency.

## 02.1 — Protected authoritative local reads

- Parent: Work package 2; state: **COMPLETE**; dependencies: existing approved foundation.
- Exact scope: Retain unreadable progress/ciphertext and block unsafe replacement; explicit retry, owner isolation and truthful empty/loading/error UI.
- Files/modules: android/app/src/main/java/org/athar/app/core/data/; android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/adhkar/; android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: ProtectedRead JVM; protected Room/ciphertext tests; Arabic/English retained-read journeys. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Retain unreadable progress/ciphertext and block unsafe replacement; explicit retry, owner isolation and truthful empty/loading/error UI. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 02.2 — Owned persistent-work foundation

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.1.
- Exact scope: Unique account/environment work, bounded retries, module isolation, logout cancellation and local exemption exclusion; lifecycle delivery gate remains02.8.
- Files/modules: android/app/src/main/java/org/athar/app/core/sync/.
- Automated gates: OwnedSyncRunner cases; actual Worker/HTTP and persistent unique-name/cancellation cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Unique account/environment work, bounded retries, module isolation, logout cancellation and local exemption exclusion; lifecycle delivery gate remains02.8. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 02.3 — Disposable response-cache limits

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.1, 02.2.
- Exact scope: Bound only localized HTTP documents by count/encoded bytes/age; preserve authoritative private rows and enforce offline actor boundaries.
- Files/modules: android/app/src/main/java/org/athar/app/core/data/.
- Automated gates: ContentCachePolicy; Room eviction/isolation/corruption/404; existing reader/counter/cache journeys. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Bound only localized HTTP documents by count/encoded bytes/age; preserve authoritative private rows and enforce offline actor boundaries. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 02.4 — Authentication request ownership races

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.2, 02.3.
- Exact scope: Invalidate former and during-sign-in responses; prevent late refresh success/revocation replacing a new account; serialize slow credential persistence.
- Files/modules: android/app/src/main/java/org/athar/app/auth/SessionRepository.kt; android/app/src/test/java/org/athar/app/SessionRepositoryTest.kt.
- Automated gates: SessionRepository deterministic coroutine races; full JVM/build/lint; actual Worker/HTTP and AR/EN auth/reader/counter regression. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Invalidate former and during-sign-in responses; prevent late refresh success/revocation replacing a new account; serialize slow credential persistence. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 21.1 — Premium shared primitives and motion reconciliation

- Parent: Work package21: UX/accessibility; state: **COMPLETE**; dependencies: 02.4.
- Exact scope: Review/import only shared premium surfaces, motion and required design docs from pinned UI branch; preserve immediate auth boundaries and reduced motion.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/PremiumComponents.kt; android/app/src/main/java/org/athar/app/core/ui/Motion.kt; docs/design/.
- Automated gates: Compile shared component call sites; Compose semantics/reduced-motion tests; no data/backend diff. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Review/import only shared premium surfaces, motion and required design docs from pinned UI branch; preserve immediate auth boundaries and reduced motion. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 02.5 — Night Prayer conflict resolution

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.1, 02.4, 21.1.
- Exact scope: Expose durable per-record conflict state and intentional server/local choice including deletion; retain pending edits until successful resolution. Reconcile the premium Night Prayer surface only where touched.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/; android/app/src/main/java/org/athar/app/core/data/; backend/src/NightPrayer/.
- Automated gates: CAS/lost-ack/retry/tombstone/owner tests; native conflict choice/error/offline UI cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Expose durable per-record conflict state and intentional server/local choice including deletion; retain pending edits until successful resolution. Reconcile the premium Night Prayer surface only where touched. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 02.6 — Watch and account local recovery surfaces

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.4, 21.1.
- Exact scope: Apply retained-unreadable behavior to lesson checkpoint and account cache presentation; never infer zero/completion or silently overwrite retained progress.
- Files/modules: android/app/src/main/java/org/athar/app/learning/WatchScreen.kt; android/app/src/main/java/org/athar/app/learning/LessonRepository.kt; android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt.
- Automated gates: Malformed/tag/domain payload cases; watch checkpoint recovery and account retry/owner UI cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Apply retained-unreadable behavior to lesson checkpoint and account cache presentation; never infer zero/completion or silently overwrite retained progress. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 02.7.1 — Captured-owner mutation dispatch guards

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.5, 02.6.
- Exact scope: Capture the intended actor before asynchronous private/local or account-action dispatch; reject writes after account switching, including reading, counters, local exemptions and account tools. Preserve existing pending rows and verified-access gating.
- Files/modules: android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/core/data/CounterRepository.kt; android/app/src/main/java/org/athar/app/nightprayer/; android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt; android/app/src/androidTest/; android/app/src/main/java/org/athar/app/auth/SessionRepository.kt; android/app/src/main/java/org/athar/app/learning/DiscussionViewModel.kt; android/app/src/main/java/org/athar/app/core/ui/MainShell.kt.
- Automated gates: Deterministic actor switch before IO dispatch and during suspended mutation; rejected original-owner write and no new-owner row/API mutation; existing recovery/regression/build/lint/resources.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Capture the intended actor before asynchronous private/local or account-action dispatch; reject writes after account switching, including reading, counters, local exemptions and account tools. Preserve existing pending rows and verified-access gating. Applicable tests, physical Arabic/English owner-safe journey, docs, reviewed commit and verified normal push pass.
- Estimated contribution: 0.18 overall percentage points.

## 21.18 — Document immersive section heroes and original artwork

- Parent: Work package 21: UI/UX closure; state: **COMPLETE**; dependencies: 02.7.1, 21.1.
- Exact scope: Define the additive Athar Immersive Section Hero system, section visual language, hero motion/scroll, state/ownership rules and original artwork/fallback specification from six user-supplied inspiration images. Reconcile the premium branch and route future implementation without pausing feature work or claiming static mocks as completed UI.
- Files/modules: docs/design/IMMERSIVE_SECTION_HERO.md; docs/design/SECTION_VISUAL_LANGUAGE.md; docs/design/HERO_MOTION_AND_SCROLL_BEHAVIOR.md; docs/design/ARTWORK_LIBRARY.md; docs/design/UI_UX_PREMIUM_V2_CHANGELOG.md; docs/design/HERO_IMPLEMENTATION_PLAN.md; docs/design/HERO_ASSET_SPEC.json; docs/progress/.
- Automated gates: Check internal Markdown links, required sections/asset specifications, queue dependencies/render and documentation-only diff; verify cited Android/provider guidance. No new Android/runtime/backend gate is claimed for documentation only.
- Physical-device gate: Not applicable to this documentation-only task. Every implementing screen/component task still requires actual-device Arabic/English, state/motion/performance acceptance.
- Definition of Done: All required design/asset/scroll documents cover purpose, affected screens, visual/behavior/motion do/donot, performance/accessibility/reduced-motion, truthful data/CTA and existing branch integration. Exact subsequent tasks are recorded; links/queue/diff reviewed; logical documentation commit normally pushed and remote SHA verified. This is not native screen completion.
- Estimated contribution: 0.03 overall percentage points.

## 02.7.2 — Actionable permanent Night Prayer rejection recovery

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.5, 02.7.1.
- Exact scope: Keep rejected outbox bytes, distinguish permanent non409 operation rejection from revision conflict and temporary connectivity, and provide explicit validated repair/retry without silently discarding data or starving healthy sessions.
- Files/modules: android/app/src/main/java/org/athar/app/core/data/; android/app/src/main/java/org/athar/app/nightprayer/; android/app/src/androidTest/.
- Automated gates: 422/404/429/503 operation outcomes; retained rejection after restart; explicit retry/repair/stale review; unreadable and owner isolation; healthy queue progress; native Arabic/English UI/build/lint/resources.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Keep rejected outbox bytes, distinguish permanent non409 operation rejection from revision conflict and temporary connectivity, and provide explicit validated repair/retry without silently discarding data or starving healthy sessions. Applicable durable-state/API/error/localization/physical gates, safe migration if required, docs, reviewed commit and verified normal push pass.
- Estimated contribution: 0.18 overall percentage points.

## 02.7 — Cross-module lost-response and restart recovery

- Parent: Work package 2; state: **COMPLETE**; dependencies: 02.7.1, 02.7.2.
- Exact scope: Verify lost-response/repeated-retry/process recreation/order/conflict behavior across reading, counters, lesson checkpoints and Night Prayer without duplicate completion/activity or lost pending data. Any newly exposed defect must be fixed and retested in a focused logical increment.
- Files/modules: android/app/src/main/java/org/athar/app/core/data/; android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/nightprayer/; tests/.
- Automated gates: Dropped acknowledgement/repeated retry/restart/order/conflict tests across four modules; no duplicate activity. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify lost-response/repeated-retry/process recreation/order/conflict behavior across reading, counters, lesson checkpoints and Night Prayer without duplicate completion/activity or lost pending data. Any newly exposed defect must be fixed and retested in a focused logical increment. Required cross-module storage/API/device regression, documentation, reviewed logical commit and verified normal push pass.
- Estimated contribution: 0.18 overall percentage points.

## 02.8 — OS reconnect and process/reboot acceptance

- Parent: Work package 2; state: **PARTIAL**; dependencies: 02.7.
- Exact scope: Verify real constrained dispatch after reconnect, process recreation and reboot without clearing app data or replacing direct Worker tests with scheduling claims.
- Files/modules: android/app/src/main/java/org/athar/app/core/sync/; android/app/src/androidTest/; docs/testing/.
- Automated gates: Persistent work state/retained actor/pending checks and scoped startup logs. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Connected phone needs an active Internet network; USB reverse alone is not CONNECTED. Then run actual reconnect/process/reboot journeys with retained data.
- Definition of Done: Verify real constrained dispatch after reconnect, process recreation and reboot without clearing app data or replacing direct Worker tests with scheduling claims. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 03.1 — Range and section navigation

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 02.7.
- Exact scope: Complete surah/ayah/juz/hizb/page/range entry and reference validation; reconcile Quran hub/control sheets.
- Files/modules: android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Canonical boundary/invalid/mixed Arabic-digit tests; exact reader navigation. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete surah/ayah/juz/hizb/page/range entry and reference validation; reconcile Quran hub/control sheets. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.2 — Quran search journey

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.1.
- Exact scope: Complete Arabic normalization, result paging/highlight and exact result opening while preserving canonical text.
- Files/modules: android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Search normalization/boundary/result-navigation/offline cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete Arabic normalization, result paging/highlight and exact result opening while preserving canonical text. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.3 — Tafsir selection and source states

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.2.
- Exact scope: Complete book/source/verse paging, unavailable/offline states and premium source/book sheet.
- Files/modules: android/app/src/main/java/org/athar/app/quran/; backend/src/Providers/.
- Automated gates: Provider contract/source/paging and native book-switch/error tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete book/source/verse paging, unavailable/offline states and premium source/book sheet. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.4 — Durable Quran bookmarks

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.3.
- Exact scope: Implement remaining bookmark add/remove/list/open/sync behavior without counting a bookmark as reading.
- Files/modules: android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt; backend/src/Progress/.
- Automated gates: Ownership/idempotency/offline/duplicate and exact reference tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement remaining bookmark add/remove/list/open/sync behavior without counting a bookmark as reading. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.5 — Quran typography preferences

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.4.
- Exact scope: Complete persisted accessible font/layout preferences using canonical Quran font; no animated sacred text.
- Files/modules: android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/core/data/SettingsStore.kt.
- Automated gates: Preference persistence, large text, clipping and reduced-motion cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete persisted accessible font/layout preferences using canonical Quran font; no animated sacred text. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.6 — Verse copy and share

- Parent: Work package 3: Quran reader/tools; state: **NOT STARTED**; dependencies: 03.5.
- Exact scope: Add bounded canonical text/reference/source copy/share with mixed-direction-safe presentation.
- Files/modules: android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Reference/text fidelity and Android chooser/copy semantics tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add bounded canonical text/reference/source copy/share with mixed-direction-safe presentation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.7 — Daily verse and Quran entry restoration

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.6.
- Exact scope: Provide source-attributed daily verse and explicit resume/navigation restoration; no invented completion.
- Files/modules: android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/core/ui/HomeScreen.kt.
- Automated gates: Deterministic day/reference/restore/offline cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Provide source-attributed daily verse and explicit resume/navigation restoration; no invented completion. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.8 — Approved Dorar provider contract

- Parent: Work package 3: Quran reader/tools; state: **BLOCKED**; dependencies: 03.7.
- Exact scope: Implement/verify only approved Arabic hadith search API, response attribution and safe provider errors; no unapproved scrape.
- Files/modules: backend/src/Providers/; docs/features/DORAR_SEARCH.md.
- Automated gates: Fake provider timeout/auth/schema/rate/source tests and authorized access probe. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement/verify only approved Arabic hadith search API, response attribution and safe provider errors; no unapproved scrape. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 03.9 — Native hadith and knowledge search

- Parent: Work package 3: Quran reader/tools; state: **PARTIAL**; dependencies: 03.8.
- Exact scope: Complete Arabic-first search/results/source/detail/error/offline navigation against approved contract.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; backend/src/Providers/.
- Automated gates: Search/filter/paging/source/error native tests and provider integration. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Arabic/English hadith search with an approved provider; provider approval is external. Fake transport UI can proceed independently.
- Definition of Done: Complete Arabic-first search/results/source/detail/error/offline navigation against approved contract. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 04.1 — Audio background and lockscreen

- Parent: Work package 4: Recitation/radio; state: **PARTIAL**; dependencies: 02.7.
- Exact scope: Verify/fix Media3 foreground service, background playback, safe lockscreen controls and retained paused state.
- Files/modules: android/app/src/main/java/org/athar/app/media/.
- Automated gates: Service/controller/lifecycle/source identity tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify/fix Media3 foreground service, background playback, safe lockscreen controls and retained paused state. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 04.2 — Audio focus and interruption

- Parent: Work package 4: Recitation/radio; state: **PARTIAL**; dependencies: 04.1.
- Exact scope: Handle focus loss, call/interruption, headphones and resume without surprise autoplay.
- Files/modules: android/app/src/main/java/org/athar/app/media/.
- Automated gates: Focus/interruption route-change controller tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Handle focus loss, call/interruption, headphones and resume without surprise autoplay. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 04.3 — Audio network and recovery

- Parent: Work package 4: Recitation/radio; state: **PARTIAL**; dependencies: 04.2.
- Exact scope: Handle buffering/network loss/retry/cancel and source changes without stale requests.
- Files/modules: android/app/src/main/java/org/athar/app/media/.
- Automated gates: Offline/source-race/buffering/error recovery tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Handle buffering/network loss/retry/cancel and source changes without stale requests. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 04.4 — Follow-along timing and ayah clipping

- Parent: Work package 4: Recitation/radio; state: **PARTIAL**; dependencies: 04.3.
- Exact scope: Close physical timing/seek/clip/reciter-switch accuracy; unsupported timing remains explicit.
- Files/modules: android/app/src/main/java/org/athar/app/media/.
- Automated gates: Timing/source matching/endpoints/seek races and real provider cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close physical timing/seek/clip/reciter-switch accuracy; unsupported timing remains explicit. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 04.5 — Radio catalog and playback acceptance

- Parent: Work package 4: Recitation/radio; state: **PARTIAL**; dependencies: 04.4.
- Exact scope: Complete searchable radio selection, source/error identity and premium audio selector reconciliation.
- Files/modules: android/app/src/main/java/org/athar/app/media/.
- Automated gates: Catalog/source disappearance/radio-switch/offline tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete searchable radio selection, source/error identity and premium audio selector reconciliation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 05.1 — Complete adhkar and dua discovery

- Parent: Work package 5: Adhkar/dua; state: **PARTIAL**; dependencies: 04.5.
- Exact scope: Finish all category/source paging and Arabic search without missing NULL-repeat items; reconcile category presentation.
- Files/modules: android/app/src/main/java/org/athar/app/adhkar/; backend/src/Catalog/.
- Automated gates: Full source coverage, pagination/search and unspecified-repeat cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Finish all category/source paging and Arabic search without missing NULL-repeat items; reconcile category presentation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 05.2 — Adhkar daily counting and reset policy

- Parent: Work package 5: Adhkar/dua; state: **PARTIAL**; dependencies: 05.1.
- Exact scope: Finish explicit reset/restart/day-boundary semantics without granting completion from navigation/skipping.
- Files/modules: android/app/src/main/java/org/athar/app/adhkar/; android/app/src/main/java/org/athar/app/core/data/CounterRepository.kt; backend/src/Progress/.
- Automated gates: Date/count/repeat/NULL/reset/advance/idempotency cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Finish explicit reset/restart/day-boundary semantics without granting completion from navigation/skipping. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 05.3 — Adhkar personal statistics

- Parent: Work package 5: Adhkar/dua; state: **PARTIAL**; dependencies: 05.2.
- Exact scope: Expose only genuine completion events and daily category progress with offline/retry truthfulness.
- Files/modules: android/app/src/main/java/org/athar/app/adhkar/; android/app/src/main/java/org/athar/app/core/ui/; backend/src/Progress/.
- Automated gates: Repeated/skipped/offline/lost-ack/date-boundary statistics tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Expose only genuine completion events and daily category progress with offline/retry truthfulness. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 05.4 — Adhkar one-handed accessibility

- Parent: Work package 5: Adhkar/dua; state: **PARTIAL**; dependencies: 05.3.
- Exact scope: Reconcile premium active-dhikr surface, readable source/counter controls and Arabic numeric entry where applicable.
- Files/modules: android/app/src/main/java/org/athar/app/adhkar/.
- Automated gates: Rapid taps, font/reduced-motion/RTL/LTR and protected-unreadable regressions. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Reconcile premium active-dhikr surface, readable source/counter controls and Arabic numeric entry where applicable. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 06.1 — Book rights and metadata journey

- Parent: Work package 6: Books; state: **PARTIAL**; dependencies: 05.4.
- Exact scope: Complete source/rights/availability/detail/favorite handling, including direct-link metadata without forced uploads.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; backend/src/Catalog/.
- Automated gates: Hidden/rights/source/link/favorite owner cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete source/rights/availability/detail/favorite handling, including direct-link metadata without forced uploads. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 06.2 — Safe durable book download

- Parent: Work package 6: Books; state: **NOT STARTED**; dependencies: 06.1.
- Exact scope: Implement validated HTTPS download, bounded storage, progress and durable identity without bypassing rights.
- Files/modules: android/app/src/main/java/org/athar/app/books/ (new); backend/src/Catalog/.
- Automated gates: Redirect/SSRF/MIME/size/failure/resume eligibility tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement validated HTTPS download, bounded storage, progress and durable identity without bypassing rights. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 06.3 — Book download cancellation and management

- Parent: Work package 6: Books; state: **NOT STARTED**; dependencies: 06.2.
- Exact scope: Persist downloaded-file inventory; cancel/retry/remove only owned downloaded files safely.
- Files/modules: android/app/src/main/java/org/athar/app/books/ (new).
- Automated gates: Interrupted download/process restart/space/path ownership tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Persist downloaded-file inventory; cancel/retry/remove only owned downloaded files safely. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 06.4 — Native PDF open and reading state

- Parent: Work package 6: Books; state: **NOT STARTED**; dependencies: 06.3.
- Exact scope: Open verified local PDFs with accessible native/file-provider flow and explicit retained reading position when supported.
- Files/modules: android/app/src/main/java/org/athar/app/books/ (new); android/app/src/main/AndroidManifest.xml.
- Automated gates: Malformed PDF/no viewer/provider grant/position tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Open verified local PDFs with accessible native/file-provider flow and explicit retained reading position when supported. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 06.5 — Book offline integrity acceptance

- Parent: Work package 6: Books; state: **NOT STARTED**; dependencies: 06.4.
- Exact scope: Verify checksums/stale metadata/rights changes, corruption and file cleanup without deleting private progress.
- Files/modules: android/app/src/main/java/org/athar/app/books/ (new); android/app/src/main/java/org/athar/app/core/data/.
- Automated gates: Corrupt/truncated/missing/expired file and offline catalog cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify checksums/stale metadata/rights changes, corruption and file cleanup without deleting private progress. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 07.1 — Account profile editing

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 06.5.
- Exact scope: Complete display-name/username editing and validation/error recovery; reconcile premium profile/auth presentation where touched.
- Files/modules: android/app/src/main/java/org/athar/app/auth/; android/app/src/main/java/org/athar/app/core/ui/.
- Automated gates: Username bounds/duplicate/owner/AR numeric/mixed-content UI/API tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete display-name/username editing and validation/error recovery; reconcile premium profile/auth presentation where touched. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.2 — Account security journeys

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.1.
- Exact scope: Complete registration/verification/password reset/change/revocation UI; verify Gmail OTP using isolated/fake transport until explicit recipient gate.
- Files/modules: android/app/src/main/java/org/athar/app/auth/; backend/src/Identity/.
- Automated gates: OTP Arabic digits/expiry/rate/session/password/failed submit tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete registration/verification/password reset/change/revocation UI; verify Gmail OTP using isolated/fake transport until explicit recipient gate. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.3 — Private account export

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.2.
- Exact scope: Complete deliberate export/chooser, data scope/privacy and offline/error behavior.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt; backend/src/Progress/.
- Automated gates: Ownership/escaping/exemption exclusion/export cancellation tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete deliberate export/chooser, data scope/privacy and offline/error behavior. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.4 — Account deletion and local ownership cleanup

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.3.
- Exact scope: Complete confirmation/authentication/revocation/deletion and safe owned cleanup without affecting another actor.
- Files/modules: android/app/src/main/java/org/athar/app/auth/; android/app/src/main/java/org/athar/app/core/data/; backend/src/Identity/.
- Automated gates: Delete/reauth/tokens/local rows/worker cancellation/failure recovery tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete confirmation/authentication/revocation/deletion and safe owned cleanup without affecting another actor. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.5 — Saved content collections

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.4.
- Exact scope: Complete favorites/bookmarks list paging, removal and exact destination navigation.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt; backend/src/Progress/.
- Automated gates: Owner/paging/deleted target/optimistic rollback/offline cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete favorites/bookmarks list paging, removal and exact destination navigation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.6 — Personal activity dashboard

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.5.
- Exact scope: Show genuine read/listen/adhkar/lesson progress only; define unavailable totals and loading/error states.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; backend/src/Progress/.
- Automated gates: No inferred opening-completion; dedup/date/source/owner tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Show genuine read/listen/adhkar/lesson progress only; define unavailable totals and loading/error states. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.7 — Khatmah workload and plan editor

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.6.
- Exact scope: Complete canonical daily workload/start/end/target validation and premium editor hierarchy.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt; backend/src/Progress/.
- Automated gates: Cross-surah/day/invalid target/date/workload domain and UI tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete canonical daily workload/start/end/target validation and premium editor hierarchy. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.8 — Khatmah lifecycle actions

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.7.
- Exact scope: Implement pause/resume/edit/restart/finish with deliberate progress updates and confirmation.
- Files/modules: android/app/src/main/java/org/athar/app/khatmah/ (new or extracted); backend/src/Progress/.
- Automated gates: State transitions/idempotency/date/actual progress cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement pause/resume/edit/restart/finish with deliberate progress updates and confirmation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 07.9 — Khatmah offline and conflicts

- Parent: Work package 7: Account/Khatmah; state: **PARTIAL**; dependencies: 07.8.
- Exact scope: Persist plan edits/progress safely across restart/account changes and reconcile conflicts explicitly.
- Files/modules: android/app/src/main/java/org/athar/app/khatmah/ (new or extracted); android/app/src/main/java/org/athar/app/core/data/.
- Automated gates: Offline restart/lost response/conflict/ownership plan tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Persist plan edits/progress safely across restart/account changes and reconcile conflicts explicitly. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.40 overall percentage points.

## 08.1 — Tonight dashboard and Islamic night identity

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 07.9.
- Exact scope: Implement personalized tonight summary/milestones/recent log using fixed local Maghrib date and existing rules.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Maghrib/midnight/UTC/DST/no inferred prayer cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement personalized tonight summary/milestones/recent log using fixed local Maghrib date and existing rules. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.2 — Verse target and range workflow

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.1.
- Exact scope: Finish target/range planning, copy reference, save plan and explicit record-completed actions.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/; android/app/src/main/java/org/athar/app/quran/.
- Automated gates: 10/100/1000/exact inclusive/cross-surah/range UX tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Finish target/range planning, copy reference, save plan and explicit record-completed actions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.3 — Exact prayer reading continuation

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.2.
- Exact scope: Link planned/session Quran range with separate Quran/Khatmah positions; resume without conflating progress.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/; android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Exact boundaries/cross-surah/recreation/owner tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Link planned/session Quran range with separate Quran/Khatmah positions; resume without conflating progress. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.4 — Measured prayer session timer

- Parent: Work package 8: Night Prayer; state: **NOT STARTED**; dependencies: 08.3.
- Exact scope: Implement pause/resume/finish/cancel and durable measured duration without counting background wall time as recitation.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Monotonic/wall clock/process/interruption/save-once cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement pause/resume/finish/cancel and durable measured duration without counting background wall time as recitation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.5 — Measured reading pace insights

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.4.
- Exact scope: Use valid measured samples only for pace and explain insufficient data; no speed pressure.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Weighted pace/outlier/zero/edited/deleted sample tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Use valid measured samples only for pace and explain insufficient data; no speed pressure. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.6 — Saved city and prayer method

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.5.
- Exact scope: Persist chosen city/timezone/method and expose source/manual/polar unavailable behavior without silent GPS permission.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Method/location/DST/polar/old tzdb parity tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Persist chosen city/timezone/method and expose source/manual/polar unavailable behavior without silent GPS permission. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.7 — Yearly prayer visualization

- Parent: Work package 8: Night Prayer; state: **NOT STARTED**; dependencies: 08.6.
- Exact scope: Add accessible yearly activity/legend/year navigation without revealing private dates to Admin.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Leap year/date bounds/empty/deleted/exempt semantics tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add accessible yearly activity/legend/year navigation without revealing private dates to Admin. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.8 — Category streak and consistency views

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.7.
- Exact scope: Finish current/longest/category streaks using explicitly logged nights and local exemptions.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Gaps/exemptions/travel/year boundary/edit/delete tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Finish current/longest/category streaks using explicitly logged nights and local exemptions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.9 — Personal records and milestone insights

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.8.
- Exact scope: Display genuine verse/duration/classification records and next milestone without gamification.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Ties/edits/deletion/zero/duplicate/sacred-text motion tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Display genuine verse/duration/classification records and next milestone without gamification. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.10 — Prayer history edit and delete journeys

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.9.
- Exact scope: Complete ordered pagination/detail/edit/delete/restore-conflict feedback with premium hierarchy. Preserve measured duration and prayer context when editing notes; the existing form currently reconstructs only a subset of fields.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/; backend/src/NightPrayer/.
- Automated gates: Validation/tombstone/CAS/offline/error/owner native tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable. Include note-only edits preserving measured_seconds and boundary/timing context.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete ordered pagination/detail/edit/delete/restore-conflict feedback with premium hierarchy. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.11 — Private exemption and export boundaries

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.10.
- Exact scope: Complete optional exemption-period editing/calendar behavior and deliberate export exclusions.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Overlap/date bounds/privacy/no uploads/no Admin audience tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Complete optional exemption-period editing/calendar behavior and deliberate export exclusions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 08.12 — Night Prayer travel and edge acceptance

- Parent: Work package 8: Night Prayer; state: **PARTIAL**; dependencies: 08.11.
- Exact scope: Close timezone/DST/polar/midnight/clock changes across calculator/calendar/session/insights on hardware.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/; shared/; docs/testing/.
- Automated gates: Deterministic shared vectors plus device date-source discrepancy evidence. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close timezone/DST/polar/midnight/clock changes across calculator/calendar/session/insights on hardware. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 09.1 — Native model compatibility and license

- Parent: Work package 9: Native review engine; state: **PARTIAL**; dependencies: 08.12.
- Exact scope: Evaluate existing approved old model/graph/tokenization/runtime against ARM32 and noncommercial terms; do not revive rejected training exports.
- Files/modules: android/app/src/main/java/org/athar/app/review/ (new); training/; docs/features/RECITATION_REVIEW.md.
- Automated gates: Artifact graph/token compatibility/known audio smoke and recorded license decision. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Evaluate existing approved old model/graph/tokenization/runtime against ARM32 and noncommercial terms; do not revive rejected training exports. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 09.2 — Verified optional model delivery

- Parent: Work package 9: Native review engine; state: **NOT STARTED**; dependencies: 09.1.
- Exact scope: Implement explicit consent/download/hash/version/storage/cancel behavior for a compatible licensed model.
- Files/modules: android/app/src/main/java/org/athar/app/review/ (new).
- Automated gates: Integrity/partial download/disk/restart/rollback tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement explicit consent/download/hash/version/storage/cancel behavior for a compatible licensed model. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 09.3 — Microphone lifecycle and local inference

- Parent: Work package 9: Native review engine; state: **NOT STARTED**; dependencies: 09.2.
- Exact scope: Implement contextual microphone permission/audio capture/local runtime with bounded memory and no audio upload.
- Files/modules: android/app/src/main/java/org/athar/app/review/ (new); android/app/src/main/AndroidManifest.xml.
- Automated gates: Denied/revoked/interruption/ARM32/memory/capture format tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement contextual microphone permission/audio capture/local runtime with bounded memory and no audio upload. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 09.4 — Recitation alignment and uncertainty feedback

- Parent: Work package 9: Native review engine; state: **NOT STARTED**; dependencies: 09.3.
- Exact scope: Implement empty verse slots/fill/letter-phoneme alignment and truthful uncertain/error correction with optional supported sound/haptics.
- Files/modules: android/app/src/main/java/org/athar/app/review/ (new); android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Insertion/deletion/substitution/repeat/disjoint-letter/noisy-audio tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement empty verse slots/fill/letter-phoneme alignment and truthful uncertain/error correction with optional supported sound/haptics. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 09.5 — Measured review accuracy and latency

- Parent: Work package 9: Native review engine; state: **NOT STARTED**; dependencies: 09.4.
- Exact scope: Evaluate representative reciters/learner/errors and report measured false acceptance/rejection and latency; never claim99% without evidence.
- Files/modules: android/app/src/main/java/org/athar/app/review/ (new); docs/testing/.
- Automated gates: Frozen corpus/versioned metrics/device memory/latency reproducibility. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Evaluate representative reciters/learner/errors and report measured false acceptance/rejection and latency; never claim99% without evidence. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.54 overall percentage points.

## 10.1 — Notification preference contract

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 07.9, 08.6.
- Exact scope: Implement opt-in settings schema/API/local persistence with safe defaults and versioned consent.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); backend/src/Engagement/ (new); database/migrations/.
- Automated gates: Ownership/default-deny/version/retry/withdrawal tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement opt-in settings schema/API/local persistence with safe defaults and versioned consent. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 10.2 — Channels and contextual permission

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 10.1.
- Exact scope: Implement localized Android channels and contextual API33 permission/denial/revocation flows.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/AndroidManifest.xml.
- Automated gates: Manifest/permission/channel and no-permission behavior tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement localized Android channels and contextual API33 permission/denial/revocation flows. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 10.3 — Stable notification deep links

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 10.2.
- Exact scope: Implement authenticated safe destination restoration for lesson/series/Quran/adhkar/night/inbox.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/core/ui/.
- Automated gates: Malformed/untrusted/removed target/verified access/back-stack tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement authenticated safe destination restoration for lesson/series/Quran/adhkar/night/inbox. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 10.4 — Quiet hours expiry and dedup policy

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 10.3.
- Exact scope: Implement shared timezone-aware quiet windows/TTL/frequency/idempotent local policy; private data stays local.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); backend/src/Engagement/ (new).
- Automated gates: DST/overnight/late/duplicate/rate/withdrawal policy vectors. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement shared timezone-aware quiet windows/TTL/frequency/idempotent local policy; private data stays local. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 10.5 — Persistent local reminder scheduler

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 10.4.
- Exact scope: Implement bounded owned local work/reconciliation and cancellation without unjustified exact-alarm privileges.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Owner/retry/constraints/permission/duplicate schedule tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement bounded owned local work/reconciliation and cancellation without unjustified exact-alarm privileges. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 10.6 — Boot and time-change reconciliation

- Parent: Work package 10: Notification foundation; state: **NOT STARTED**; dependencies: 10.5.
- Exact scope: Safely restore reminders after boot/time/timezone change with stale/permission/privacy checks.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/AndroidManifest.xml.
- Automated gates: Receiver permission/input validation/reboot/timezone/TTL tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Safely restore reminders after boot/time/timezone change with stale/permission/privacy checks. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 11.1 — Lesson reminder consent and controls

- Parent: Work package 11: Lesson reminders; state: **NOT STARTED**; dependencies: 10.6.
- Exact scope: Add explicit lesson reminder choice/default single15-minute preference and visible opt-out.
- Files/modules: android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Consent/no implicit subscription/UI persistence tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add explicit lesson reminder choice/default single15-minute preference and visible opt-out. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 11.2 — Local lesson reminder schedule

- Parent: Work package 11: Lesson reminders; state: **NOT STARTED**; dependencies: 11.1.
- Exact scope: Schedule actual lesson reminder from UTC/source timezone and reconcile per event revision.
- Files/modules: android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: UTC/DST/past/duplicate/offline actual delivery tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Schedule actual lesson reminder from UTC/source timezone and reconcile per event revision. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 11.3 — Lesson change and cancellation reconciliation

- Parent: Work package 11: Lesson reminders; state: **NOT STARTED**; dependencies: 11.2.
- Exact scope: Cancel/reschedule reminders for event changes/unavailability/replay and display clear change feedback.
- Files/modules: android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/notifications/ (new); backend/src/Learning/.
- Automated gates: Revision/cancel/live/published/reschedule/TTL tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Cancel/reschedule reminders for event changes/unavailability/replay and display clear change feedback. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 11.4 — Server lesson reminder jobs

- Parent: Work package 11: Lesson reminders; state: **NOT STARTED**; dependencies: 11.3.
- Exact scope: Persist consent-aware server jobs/device reconciliation with quotas/cancel/retries and no send-on-save.
- Files/modules: backend/src/Engagement/ (new); database/migrations/.
- Automated gates: Audience consent/dedup/retry/cancel/privacy tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Persist consent-aware server jobs/device reconciliation with quotas/cancel/retries and no send-on-save. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 12.1 — Follow persistence and API

- Parent: Work package 12: Following; state: **NOT STARTED**; dependencies: 11.4.
- Exact scope: Implement explicit scholar/series follows/unfollows with unique ownership and content visibility.
- Files/modules: backend/src/Engagement/ (new); database/migrations/.
- Automated gates: Follow/unfollow/idempotency/hidden/deleted/owner tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement explicit scholar/series follows/unfollows with unique ownership and content visibility. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.22 overall percentage points.

## 12.2 — Scholar follow journey

- Parent: Work package 12: Following; state: **NOT STARTED**; dependencies: 12.1.
- Exact scope: Add native follow state/error/offline/rollback and reconcile scholar premium identity surface.
- Files/modules: android/app/src/main/java/org/athar/app/learning/ScholarScreen.kt.
- Automated gates: Rapid tap/retry/owner/pagination/native follow tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add native follow state/error/offline/rollback and reconcile scholar premium identity surface. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.22 overall percentage points.

## 12.3 — Series follow journey

- Parent: Work package 12: Following; state: **NOT STARTED**; dependencies: 12.2.
- Exact scope: Add series follow controls distinct from favorite/save and reconcile premium series surface.
- Files/modules: android/app/src/main/java/org/athar/app/learning/SeriesScreen.kt.
- Automated gates: Consent/distinct favorite/unfollow/owner/native tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add series follow controls distinct from favorite/save and reconcile premium series surface. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.22 overall percentage points.

## 12.4 — New content subscription facts

- Parent: Work package 12: Following; state: **NOT STARTED**; dependencies: 12.3.
- Exact scope: Produce safe new-lesson/series facts and opt-in recipients without worship-data segmentation.
- Files/modules: backend/src/Engagement/ (new); backend/src/Learning/.
- Automated gates: Publication/replay/dedup/unfollow/hidden content audience tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Produce safe new-lesson/series facts and opt-in recipients without worship-data segmentation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.22 overall percentage points.

## 13.1 — Quran wird reminders

- Parent: Work package 13: Personal routines; state: **NOT STARTED**; dependencies: 12.4.
- Exact scope: Implement optional personal reading target/time/day reminders from genuine progress only.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Opening vs completion/day/DST/quiet/withdrawal tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement optional personal reading target/time/day reminders from genuine progress only. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 13.2 — Completion-aware adhkar reminders

- Parent: Work package 13: Personal routines; state: **NOT STARTED**; dependencies: 13.1.
- Exact scope: Implement optional category/day reminders suppressed only after genuine completed repeat requirements.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/adhkar/.
- Automated gates: Skipped/repeated/NULL/offline completion/reset/date tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement optional category/day reminders suppressed only after genuine completed repeat requirements. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 13.3 — Salawat and Friday reminders

- Parent: Work package 13: Personal routines; state: **NOT STARTED**; dependencies: 13.2.
- Exact scope: Implement optional localized salawat/Friday routines and timezone/day boundaries without guilt or inferred worship.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Day/timezone/quiet/TTL/dedup/consent tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement optional localized salawat/Friday routines and timezone/day boundaries without guilt or inferred worship. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 13.4 — Custom routine reminders

- Parent: Work package 13: Personal routines; state: **NOT STARTED**; dependencies: 13.3.
- Exact scope: Add bounded user-controlled custom days/times/edit/pause/delete with local privacy.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Validation/multiple schedules/offline/restart/delete tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Add bounded user-controlled custom days/times/edit/pause/delete with local privacy. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 13.5 — Unified reminder center

- Parent: Work package 13: Personal routines; state: **NOT STARTED**; dependencies: 13.4.
- Exact scope: Expose all active reminder types, permission health, pause and per-rule controls accessibly.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/core/ui/.
- Automated gates: Cross-type preference consistency/deep-link/error/empty tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Expose all active reminder types, permission health, pause and per-rule controls accessibly. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 14.1 — Khatmah workload reminders

- Parent: Work package 14: Khatmah/Night reminders; state: **NOT STARTED**; dependencies: 13.5.
- Exact scope: Schedule user-consented remaining daily workload with paused/finished-plan suppression.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/khatmah/ (new or extracted).
- Automated gates: Plan edit/pause/finish/date/offline/dedup tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Schedule user-consented remaining daily workload with paused/finished-plan suppression. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.30 overall percentage points.

## 14.2 — Night Prayer and last-third reminders

- Parent: Work package 14: Khatmah/Night reminders; state: **NOT STARTED**; dependencies: 14.1.
- Exact scope: Implement optional sourced last-third/selected-time routine with city/method/polar unavailable states.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: DST/exempt/local privacy/permission/actual delivery tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement optional sourced last-third/selected-time routine with city/method/polar unavailable states. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.30 overall percentage points.

## 14.3 — Cross-routine suppression and user control

- Parent: Work package 14: Khatmah/Night reminders; state: **NOT STARTED**; dependencies: 14.2.
- Exact scope: Verify quiet hours/frequency/expiry/completed state across overlapping routines without exposing private activity to campaigns.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Cross-rule dedup/privacy/pause/revocation/boundary tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify quiet hours/frequency/expiry/completed state across overlapping routines without exposing private activity to campaigns. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.30 overall percentage points.

## 15.1 — Free push provider abstraction

- Parent: Work package 15: Free push; state: **NOT STARTED**; dependencies: 14.3.
- Exact scope: Implement provider interface/configuration boundary and test transport with no paid dependency or secret in APK.
- Files/modules: backend/src/Engagement/ (new); android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: Fake send/config/credential isolation/provider retry tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement provider interface/configuration boundary and test transport with no paid dependency or secret in APK. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 15.2 — Device token lifecycle

- Parent: Work package 15: Free push; state: **NOT STARTED**; dependencies: 15.1.
- Exact scope: Implement multi-device token register/rotate/logout/revoke and owner/environment separation.
- Files/modules: backend/src/Engagement/ (new); android/app/src/main/java/org/athar/app/notifications/ (new); database/migrations/.
- Automated gates: Token uniqueness/rotation/invalid/owner/logout/retry tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement multi-device token register/rotate/logout/revoke and owner/environment separation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 15.3 — Authorized physical push delivery

- Parent: Work package 15: Free push; state: **BLOCKED**; dependencies: 15.2.
- Exact scope: Configure an authorized free FCM project and prove one test-device delivery with no broadcast.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); docs/deployment/.
- Automated gates: Actual token/foreground/background/channel/deep-link and TTL evidence. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Requires authorized free FCM project/config and one approved test device; actual foreground/background delivery and token lifecycle. No broadcast.
- Definition of Done: Configure an authorized free FCM project and prove one test-device delivery with no broadcast. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 16.1 — Private notification inbox API

- Parent: Work package 16: Inbox/policy; state: **NOT STARTED**; dependencies: 15.2.
- Exact scope: Implement paginated owner-scoped inbox/read/delete/expiry contract and additive schema.
- Files/modules: backend/src/Engagement/ (new); database/migrations/.
- Automated gates: Isolation/cursor/read/idempotency/expiry tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement paginated owner-scoped inbox/read/delete/expiry contract and additive schema. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 16.2 — Native notification inbox

- Parent: Work package 16: Inbox/policy; state: **NOT STARTED**; dependencies: 16.1.
- Exact scope: Implement accessible inbox list/detail/read navigation with loading/empty/error states.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/core/ui/.
- Automated gates: Paging/read/deleted target/AR/EN UI cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Implement accessible inbox list/detail/read navigation with loading/empty/error states. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 16.3 — Inbox offline badge and synchronization

- Parent: Work package 16: Inbox/policy; state: **NOT STARTED**; dependencies: 16.2.
- Exact scope: Persist private inbox/read state and badge without duplicates across restart/account changes.
- Files/modules: android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/core/data/.
- Automated gates: Offline read merge/restart/dedup/actor tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Persist private inbox/read state and badge without duplicates across restart/account changes. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 16.4 — Notification policy diagnostics

- Parent: Work package 16: Inbox/policy; state: **NOT STARTED**; dependencies: 16.3.
- Exact scope: Expose privacy-safe policy outcomes/preferences and delivery status without worship notes/dates/audiences.
- Files/modules: backend/src/Engagement/ (new); android/app/src/main/java/org/athar/app/notifications/ (new).
- Automated gates: TTL/frequency/dedup/redaction/ownership tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Expose privacy-safe policy outcomes/preferences and delivery status without worship notes/dates/audiences. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 17.1 — Independent Admin authentication

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 02.4.
- Exact scope: Create separate Admin entry/session/login/logout using shared identity with CSRF/fixation/role protections.
- Files/modules: admin/auth.php; admin/Views/login.php; public/index.php; public/assets/css/admin-auth.css; public/assets/js/admin-auth.js.
- Automated gates: HTTP session/CSRF/fixation/denial/logout tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Create separate Admin entry/session/login/logout using shared identity with CSRF/fixation/role protections. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 17.2 — Independent Admin bilingual shell

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 17.1.
- Exact scope: Create responsive sidebar/navigation/localization/theme/loading/error shell without public-web redesign.
- Files/modules: admin/ (new).
- Automated gates: Routes/RTL/LTR/keyboard/focus/phone/tablet shell tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Create responsive sidebar/navigation/localization/theme/loading/error shell without public-web redesign. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 17.3 — Admin authorization matrix

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 17.2.
- Exact scope: Enforce action-level roles/owner boundaries server-side and reflect permitted navigation.
- Files/modules: admin/ (new); backend/src/.
- Automated gates: Every role/action, bypass, owner/self-lockout and CSRF matrix. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Enforce action-level roles/owner boundaries server-side and reflect permitted navigation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 17.4 — Admin user management

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 17.3.
- Exact scope: Implement full user lookup/detail/edit/suspension/role/deletion safe flows and session revocation.
- Files/modules: admin/ (new); backend/src/Identity/.
- Automated gates: Validation/owner/self protection/audit/session revoke tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement full user lookup/detail/edit/suspension/role/deletion safe flows and session revocation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 17.5 — Admin audit and analysis

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 17.4.
- Exact scope: Implement paginated redacted logs/filter/detail and permitted aggregate metrics without private prayer data.
- Files/modules: admin/ (new); backend/src/.
- Automated gates: RBAC/paging/redaction/filter/no private payload tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement paginated redacted logs/filter/detail and permitted aggregate metrics without private prayer data. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.60 overall percentage points.

## 17.6 — Owner recovery operations

- Parent: Work package 17: Independent Admin foundation; state: **COMPLETE**; dependencies: 17.5.
- Exact scope: Expose owner-only backup/restore operation review/confirmation/status without executing destructive development restore.
- Files/modules: admin/ (new); app/backup.php; app/backup-assets.php.
- Automated gates: COMPLETE:20,752 backend (61 isolated recovery),189 HTTP,48 actual browser assertions; actual pre/post-COMMIT worker termination, corrupt journal/original preservation, owner/password/version/actor isolation, TTL/single-use/CSRF, reset/recovery and rollback. Evidence:docs/testing/ADMIN_RECOVERY.md.
- Physical-device gate: Actual Admin browser AR/EN light/dark desktop1440/phone360; root-font enlargement/reduced motion/error focus/cancelled confirmation. No native code/UI change; no physical Android PASS claimed.
- Definition of Done: Expose owner-only backup/restore operation review/confirmation/status without executing destructive development restore. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 18.1 — Admin scholar CRUD and portraits

- Parent: Work package 18: Admin content/services; state: **COMPLETE**; dependencies: 17.6.
- Exact scope: Manage scholar name/image/optional approved biography/source using existing model validation.
- Files/modules: admin/ (new); backend/src/Catalog/.
- Automated gates: COMPLETE:20,752 backend;162 HTTP (42 scholar/portrait/adjacent lesson +120 auth/RBAC/concurrency);68 actual browser. Actual PNG/JPEG/WebP, private draft/public published boundary, validators, referenced deletion and real concurrent deletion/no orphan upload. Evidence:docs/testing/ADMIN_SCHOLARS.md.
- Physical-device gate: Actual Admin AR/EN light/dark browser at1440/360; create/edit values, numeric RTL direction, touch spacing, preserved draft/error focus and captures reviewed. No native code/schema change or physical Android PASS claimed.
- Definition of Done: Manage scholar name/image/optional approved biography/source using existing model validation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 18.2 — Admin series CRUD and ordering

- Parent: Work package 18: Admin content/services; state: **COMPLETE**; dependencies: 18.1.
- Exact scope: Manage series metadata/scholar/order/publication and safe delete dependencies.
- Files/modules: admin/ (new); backend/src/Learning/.
- Automated gates: COMPLETE:20,752 backend;193 actual HTTP (31 series/native catalog +162 auth/scholar/adjacent lesson);135 actual browser (67 series +68 scholar). Includes real child insertion/FK/delete race, associations/visibility/order, SQL rollback/redaction. Evidence:docs/testing/ADMIN_SERIES.md.
- Physical-device gate: Actual Admin AR/EN light/dark browser at1440/360; retained form/editor values, numeric RTL direction, truthful statuses, error focus and captures inspected. Canonical native catalog checked via actual HTTP. No native code/schema change or physical Android PASS claimed.
- Definition of Done: Manage series metadata/scholar/order/publication and safe delete dependencies. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 18.3 — Admin lesson CRUD

- Parent: Work package 18: Admin content/services; state: **COMPLETE**; dependencies: 18.2.
- Exact scope: Manage series and standalone lessons/provider IDs/descriptions/order/publication without media extraction.
- Files/modules: admin/ (new); backend/src/Learning/.
- Automated gates: COMPLETE:20,765 backend;234 actual HTTP (41 lesson/native Watch +193 prior);202 actual browser (67 lesson +135 prior). Provider/scope/publication/order, populated migration027, exact-case IDs, event history and scoped cleanup, actual concurrent parent change. Evidence:docs/testing/ADMIN_LESSONS.md.
- Physical-device gate: Actual AR/EN light/dark Admin browser1440/360; canonical editor state, RTL numbers, retained draft/error focus and captures inspected. Native Watch/playlist contracts checked via real HTTP; no real provider playback or physical Android PASS claimed. No native code change.
- Definition of Done: Manage series and standalone lessons/provider IDs/descriptions/order/publication without media extraction. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.00 overall percentage points.

## 18.4 — Admin upcoming and live editor

- Parent: Work package 18: Admin content/services; state: **PARTIAL**; dependencies: 18.3.
- Exact scope: Edit localized course/topics/book pages/UTC schedules/status/replay with CAS and explicit audited transitions.
- Files/modules: admin/ (new); backend/src/Learning/.
- Automated gates: Event CAS/state/timezone/book visibility/native changes tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Edit localized course/topics/book pages/UTC schedules/status/replay with CAS and explicit audited transitions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 18.5 — Admin book CRUD and rights

- Parent: Work package 18: Admin content/services; state: **PARTIAL**; dependencies: 18.4.
- Exact scope: Manage metadata/source/rights/direct-download links and publication without forcing book uploads.
- Files/modules: admin/ (new); backend/src/Catalog/.
- Automated gates: URL/rights/publish/delete/native catalog/RBAC tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Manage metadata/source/rights/direct-download links and publication without forcing book uploads. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 18.6 — Admin adhkar CRUD and source control

- Parent: Work package 18: Admin content/services; state: **PARTIAL**; dependencies: 18.5.
- Exact scope: Manage approved source/category/repeat/NULL count content with attribution and versioning.
- Files/modules: admin/ (new); backend/src/Catalog/.
- Automated gates: Source/repeat/validation/RBAC/native category tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Manage approved source/category/repeat/NULL count content with attribution and versioning. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 18.7 — Admin discussion moderation

- Parent: Work package 18: Admin content/services; state: **PARTIAL**; dependencies: 18.6.
- Exact scope: Implement first-party top-level/reply queues, publish/hide/delete with reason/revision and audit.
- Files/modules: admin/ (new); backend/src/Learning/.
- Automated gates: Parent hiding/CAS/role/audit/paging/native pending states tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement first-party top-level/reply queues, publish/hide/delete with reason/revision and audit. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 18.8 — Admin secure service settings

- Parent: Work package 18: Admin content/services; state: **PARTIAL**; dependencies: 18.7.
- Exact scope: Provide Gmail App Password/encrypted provider settings, permission-safe diagnostics and test-recipient controls.
- Files/modules: admin/ (new); app/mailer.php; backend/src/.
- Automated gates: Encryption/no echo/RBAC/CSRF/fake SMTP/invalid config tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Provide Gmail App Password/encrypted provider settings, permission-safe diagnostics and test-recipient controls. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 18.9 — Admin public Night Prayer defaults

- Parent: Work package 18: Admin content/services; state: **NOT STARTED**; dependencies: 18.8.
- Exact scope: Manage public calculation/source/text defaults only; prohibit access to private sessions/notes/exemptions.
- Files/modules: admin/ (new); backend/src/NightPrayer/.
- Automated gates: Role/validation/explicit private-data exclusion tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Manage public calculation/source/text defaults only; prohibit access to private sessions/notes/exemptions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.50 overall percentage points.

## 19.1 — Push campaign composer and preview

- Parent: Work package 19: Push campaigns; state: **NOT STARTED**; dependencies: 18.9.
- Exact scope: Build localized draft/preview/test-recipient workflow with explicit final-send confirmation.
- Files/modules: admin/ (new); backend/src/Engagement/ (new).
- Automated gates: Draft/escaping/payload/role/confirmation/no implicit send tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Build localized draft/preview/test-recipient workflow with explicit final-send confirmation. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 19.2 — Consent-safe campaign audiences

- Parent: Work package 19: Push campaigns; state: **NOT STARTED**; dependencies: 19.1.
- Exact scope: Resolve only approved content follows/engagement consent; exclude private worship signals and revoked devices.
- Files/modules: backend/src/Engagement/ (new); admin/ (new).
- Automated gates: Audience opt-in/unfollow/withdrawal/privacy tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Resolve only approved content follows/engagement consent; exclude private worship signals and revoked devices. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 19.3 — Push campaign queue and cancellation

- Parent: Work package 19: Push campaigns; state: **NOT STARTED**; dependencies: 19.2.
- Exact scope: Implement scheduled batches/idempotency/retries/cancel/TTL/quiet/frequency limits with no duplicate send.
- Files/modules: backend/src/Engagement/ (new); bin/; database/migrations/.
- Automated gates: Fake provider races/retry/cancel/quotas/dedup tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement scheduled batches/idempotency/retries/cancel/TTL/quiet/frequency limits with no duplicate send. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 19.4 — Campaign history and delivery analytics

- Parent: Work package 19: Push campaigns; state: **NOT STARTED**; dependencies: 19.3.
- Exact scope: Expose permitted counts/status/reasons and audit without sensitive recipient payloads.
- Files/modules: admin/ (new); backend/src/Engagement/ (new).
- Automated gates: RBAC/redaction/paging/reconciliation/retry counts tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Expose permitted counts/status/reasons and audit without sensitive recipient payloads. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.45 overall percentage points.

## 20.1 — Transactional email branding and reliability

- Parent: Work package 20: Email campaigns; state: **PARTIAL**; dependencies: 18.8.
- Exact scope: Verify existing logo/AR/EN templates/OTP/reset MIME and transport failure handling without live send.
- Files/modules: app/mailer.php; backend/src/Identity/.
- Automated gates: HTML/plaintext/escaping/OTP no-account-before-verification/fake SMTP tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Verify existing logo/AR/EN templates/OTP/reset MIME and transport failure handling without live send. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 20.2 — Email consent and unsubscribe

- Parent: Work package 20: Email campaigns; state: **PARTIAL**; dependencies: 20.1.
- Exact scope: Implement engagement consent/preferences/signed unsubscribe independent of transactional identity messages.
- Files/modules: backend/src/Engagement/ (new); database/migrations/.
- Automated gates: Tamper/expiry/idempotency/withdrawal/no auth bypass tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement engagement consent/preferences/signed unsubscribe independent of transactional identity messages. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 20.3 — Prioritized email queue

- Parent: Work package 20: Email campaigns; state: **PARTIAL**; dependencies: 20.2.
- Exact scope: Implement durable transactional-priority batches/retries/quotas/idempotency and safe worker operations.
- Files/modules: backend/src/Engagement/ (new); bin/; database/migrations/.
- Automated gates: Fake SMTP priority/duplicate/concurrency/cancel/retry/limits tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement durable transactional-priority batches/retries/quotas/idempotency and safe worker operations. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 20.4 — Admin email and combined campaigns

- Parent: Work package 20: Email campaigns; state: **PARTIAL**; dependencies: 20.3.
- Exact scope: Implement localized preview/test/confirmed scheduled email or push+email campaigns with independent channel consent.
- Files/modules: admin/ (new); backend/src/Engagement/ (new).
- Automated gates: Role/audience/unsubscribe/preview/cancel/queue integration tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Implement localized preview/test/confirmed scheduled email or push+email campaigns with independent channel consent. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 20.5 — Authorized Gmail inbox proof

- Parent: Work package 20: Email campaigns; state: **BLOCKED**; dependencies: 20.4.
- Exact scope: Use configured Gmail App Password and explicitly authorized test recipient to prove OTP/reset/campaign-template delivery.
- Files/modules: app/mailer.php; docs/testing/.
- Automated gates: Actual received MIME/logo/AR/EN/expiry evidence; never bulk-send in development. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Requires configured Gmail and explicitly authorized test recipient; received inbox evidence, never bulk send.
- Definition of Done: Use configured Gmail App Password and explicitly authorized test recipient to prove OTP/reset/campaign-template delivery. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 21.19 — Implement immersive hero and layered-content primitives

- Parent: Work package21: UX/accessibility; state: **NOT STARTED**; dependencies: 21.1, 21.18.
- Exact scope: Evolve accepted shared Compose primitives with original branded lightweight artwork/fallback registry, image scrim/contrast treatment, adaptive hero/compact-header and layered-content structure, truthful accessible CTA/state handling and reduced-motion-aware lifecycle/scroll behavior. Preserve auth isolation and player/data ownership; no arbitrary backend/runtime change.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; android/app/src/main/res/drawable/; docs/design/ARTWORK_LIBRARY.md; android/app/src/androidTest/.
- Automated gates: Component state/contrast/semantics/RTL/LTR/large-font and reduced-motion/scroll/draft retention tests; asset provenance/dimensions/budget checks; build/JVM/test APK/lint/resources; no backend/data regression.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Functional native primitives and original/fallback artwork meet the new documented visual system; no fake handles/actions, no sacred-text animation or provider overlay. Normal/reduced-motion, light/dark, Arabic/English/200-percent/landscape and physical memory/scroll gates are recorded. Docs, reviewed logical commit and verified normal push pass. Screen implementations remain separate.
- Estimated contribution: 0.20 overall percentage points.

## 21.2 — Home and navigation premium reconciliation

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.19, 20.4.
- Exact scope: Integrate pinned home/nav hierarchy preserving owned data, upcoming limits and routes; fix account clipping/landscape density. Immersive Home hero and layered content, actual continuation/next event and original artwork; shared source-to-section continuity only where supported. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/HomeScreen.kt; android/app/src/main/java/org/athar/app/core/ui/MainShell.kt.
- Automated gates: Home/route/owner/unavailable states and semantics regression. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Integrate pinned home/nav hierarchy preserving owned data, upcoming limits and routes; fix account clipping/landscape density. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.4 — Quran premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.2.
- Exact scope: Close hub/reader/control sheet visual reconciliation after functional tasks without losing protected resume. Immersive Quran hub with protected real reading CTA and four actual tools; keep the reader compact and canonical text/Amiri shaping untouched. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/quran/QuranScreen.kt.
- Automated gates: Sheet/reference/font/search/unreadable/reduced-motion tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close hub/reader/control sheet visual reconciliation after functional tasks without losing protected resume. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.8 — Night Prayer premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.4.
- Exact scope: Close history/calendar/tools/tonight hierarchy with protected private states and no fixed overhead starving content. Original deep-night hero, only validated night context/countdown and implemented session CTA; retain explicit history/calendar/tools and local-only exemptions. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/nightprayer/.
- Automated gates: Calendar/history/tools/empty/error/unreadable/large-font cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close history/calendar/tools/tonight hierarchy with protected private states and no fixed overhead starving content. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.9 — Watch premium reconciliation and provider hardware

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.8.
- Exact scope: Reconcile watch hierarchy and close real official provider fullscreen/back/configuration loss/error transitions. Media hero around the official16:9 player with real metadata; no custom YouTube overlays/extraction/ad bypass or player recreation on collapse. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/learning/WatchScreen.kt; android/app/src/main/java/org/athar/app/learning/YouTubePlayer.kt.
- Automated gates: Actual IFrame playback/rotation/checkpoint/no inferred completion/native hierarchy tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Reconcile watch hierarchy and close real official provider fullscreen/back/configuration loss/error transitions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.5 — Audio premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.9.
- Exact scope: Review audio/radio hero and bottom sheets preserving player identity/timing/focus behavior. Premium native audio/reciter hierarchy with real source/progress, accessible expansion and selection sheets; preserve service/player/source timing state; no invented portrait/favorite. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/media/AudioScreen.kt.
- Automated gates: Selector/source/paused state/rotation/reduced-motion tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Review audio/radio hero and bottom sheets preserving player identity/timing/focus behavior. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.13 — Upcoming premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.5.
- Exact scope: Verify home/list/detail/live/cancelled/replay/offline hierarchy without stale-live play actions. Live/next-event hero from authoritative state; cached status stays labelled saved/offline; reminder/watch only when implemented. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/learning/UpcomingHomeSection.kt; android/app/src/main/java/org/athar/app/learning/UpcomingScreen.kt.
- Automated gates: Real fixture event transitions/context/book/unavailable/cached live tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify home/list/detail/live/cancelled/replay/offline hierarchy without stale-live play actions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.7 — Adhkar premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.13.
- Exact scope: Close current-dhikr/category/counter hierarchy and source readability without changing daily completion rules. Calm dawn/category hero with protected actual counter state and subtle original art; no worship gamification. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/adhkar/AdhkarScreen.kt.
- Automated gates: Rapid counter/advance/NULL/unreadable/reduced-motion tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close current-dhikr/category/counter hierarchy and source readability without changing daily completion rules. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.20 — Books immersive hero and editorial hierarchy

- Parent: Work package21: UX/accessibility; state: **NOT STARTED**; dependencies: 21.7, 06.5.
- Exact scope: Apply the shared hero/layered-content system using actual permitted book covers or original Athar books artwork. Preserve metadata/rights/download/reading/offline ownership and accessible catalog actions; no random stock, fake cover/title or new backend contracts.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; android/app/src/main/java/org/athar/app/books/; docs/design/.
- Automated gates: Compile shared component call sites; Compose semantics/reduced-motion tests; no data/backend diff. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Actual books screen hero, native scroll/motion/reduction, RTL/LTR/theme/accessibility/performance and existing download/offline journeys pass on device; docs, reviewed commit and verified normal push pass.
- Estimated contribution: 0.10 overall percentage points.

## 21.21 — Knowledge immersive hero and continuation hierarchy

- Parent: Work package21: UX/accessibility; state: **NOT STARTED**; dependencies: 21.20, 12.4.
- Exact scope: Refine the existing learning destination with a scholarly original hero, real last-lesson/series context when available, real lesson/scholar/series/book navigation and editorial hierarchy. Preserve current API/pagination/progress/moderation; fallback to exploration instead of invented continuation.
- Files/modules: android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/core/ui/; docs/design/.
- Automated gates: Compile shared component call sites; Compose semantics/reduced-motion tests; no data/backend diff. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Native learning destination uses actual content/state and functional navigation with approved original artwork, scroll/motion/reduction, Arabic/English/theme/large-text/performance/device gates; docs, reviewed commit and verified normal push pass.
- Estimated contribution: 0.10 overall percentage points.

## 21.3 — Auth and account premium reconciliation

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.21.
- Exact scope: Integrate reviewed hero/tool presentation preserving OTP/session/gates and account operations.
- Files/modules: android/app/src/main/java/org/athar/app/auth/AuthScreens.kt; android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt.
- Automated gates: Auth error/loading/verification/security and tool switching tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Integrate reviewed hero/tool presentation preserving OTP/session/gates and account operations. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.6 — Tafsir premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.3.
- Exact scope: Review source hierarchy/book sheet while retaining provider/paging/offline contracts. Inherit Quran compact reading/header language with sacred text static and real tafsir data. Apply IMMERSIVE_SECTION_HERO/SECTION_VISUAL_LANGUAGE/HERO_MOTION_AND_SCROLL_BEHAVIOR and ARTWORK_LIBRARY; preserve newer logic/security/tests and verify actual screen/hardware acceptance.
- Files/modules: android/app/src/main/java/org/athar/app/quran/TafsirScreen.kt.
- Automated gates: Selection/paging/source/error/large-font cases. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Review source hierarchy/book sheet while retaining provider/paging/offline contracts. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.10 — Scholar premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.6.
- Exact scope: Verify portrait/source/biography/series/standalone hierarchy and paginated profile interactions.
- Files/modules: android/app/src/main/java/org/athar/app/learning/ScholarScreen.kt.
- Automated gates: Scholar pagination/hidden counts/source/image failure/RTL tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify portrait/source/biography/series/standalone hierarchy and paginated profile interactions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.11 — Series premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.10.
- Exact scope: Verify current position/progress/continue/favorite/list hierarchy without unexpected autoplay.
- Files/modules: android/app/src/main/java/org/athar/app/learning/SeriesScreen.kt.
- Automated gates: Series resume/favorite/playlist ordering/current lesson tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify current position/progress/continue/favorite/list hierarchy without unexpected autoplay. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.12 — Discussion premium reconciliation acceptance

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.11.
- Exact scope: Integrate comments/replies/pending moderation surfaces and fix tablet composer width without video-state regressions.
- Files/modules: android/app/src/main/java/org/athar/app/learning/DiscussionPanel.kt; android/app/src/main/java/org/athar/app/learning/WatchScreen.kt.
- Automated gates: Sort/paging/replies/composer/IME/pending/cancel/player retention tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Integrate comments/replies/pending moderation surfaces and fix tablet composer width without video-state regressions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.14 — Screen-aware loading states

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.12.
- Exact scope: Integrate list/grid/detail skeleton families against actual loading/error/offline machine; disable pulse for reduced motion.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/LoadingSkeleton.kt; android/app/src/main/java/org/athar/app/core/ui/DataScreen.kt.
- Automated gates: Skeleton semantics/size/state transitions/no hidden private content tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Integrate list/grid/detail skeleton families against actual loading/error/offline machine; disable pulse for reduced motion. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.15 — Arabic English and theme acceptance matrix

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.14.
- Exact scope: Close mixed content/numbers/dates/icon mirroring/light default/dark contrast across all implemented screens.
- Files/modules: android/app/src/main/res/; android/app/src/main/java/org/athar/app/core/ui/; docs/testing/.
- Automated gates: 292+ resource format checks; fresh-default/persisted language/theme and screenshot matrix. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Close mixed content/numbers/dates/icon mirroring/light default/dark contrast across all implemented screens. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.16 — Large font tablet and landscape closure

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.15.
- Exact scope: Fix remaining focus/composer/toolbar/clipping issues using adaptive constraints and retained state.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; android/app/src/main/java/org/athar/app/quran/; android/app/src/main/java/org/athar/app/learning/; docs/testing/.
- Automated gates: 130-200 percent font and phone/landscape/tablet layout matrix. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Fix remaining focus/composer/toolbar/clipping issues using adaptive constraints and retained state. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 21.17 — TalkBack and reduced-motion closure

- Parent: Work package 21: UX/accessibility; state: **PARTIAL**; dependencies: 21.16.
- Exact scope: Verify focus order/actions/labels/content announcements and all motion disabled without changing global user preferences.
- Files/modules: android/app/src/main/java/org/athar/app/core/ui/; docs/testing/.
- Automated gates: Accessibility semantics/action tests and actual TalkBack/reduced-motion evidence. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify focus order/actions/labels/content announcements and all motion disabled without changing global user preferences. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.16 overall percentage points.

## 22.1 — Release startup baseline

- Parent: Work package 22: Performance/reliability; state: **NOT STARTED**; dependencies: 21.17.
- Exact scope: Create reproducible profileable/release startup measurements on representative hardware, then fix measured regressions only.
- Files/modules: android/; docs/testing/.
- Automated gates: Cold/warm startup traces/versioned device/build evidence. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Create reproducible profileable/release startup measurements on representative hardware, then fix measured regressions only. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 22.2 — Release watch and scrolling jank

- Parent: Work package 22: Performance/reliability; state: **PARTIAL**; dependencies: 22.1.
- Exact scope: Profile watch/comments/playlist/home/Quran lists and optimize measured recomposition/layout bottlenecks.
- Files/modules: android/app/src/main/java/org/athar/app/learning/; android/app/src/main/java/org/athar/app/core/ui/; android/app/src/main/java/org/athar/app/quran/.
- Automated gates: Frame-time/jank traces before/after; player identity regression. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Profile watch/comments/playlist/home/Quran lists and optimize measured recomposition/layout bottlenecks. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 22.3 — Low-memory and cache reliability

- Parent: Work package 22: Performance/reliability; state: **PARTIAL**; dependencies: 22.2.
- Exact scope: Measure memory/process death/file/cache pressure and fix leaks/oversized allocations without deleting pending progress.
- Files/modules: android/app/src/main/java/org/athar/app/core/data/; android/app/src/main/java/org/athar/app/media/; android/app/src/main/java/org/athar/app/learning/.
- Automated gates: Low-memory/background/recreation/file-pressure tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Measure memory/process death/file/cache pressure and fix leaks/oversized allocations without deleting pending progress. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 22.4 — Background battery and network budgets

- Parent: Work package 22: Performance/reliability; state: **NOT STARTED**; dependencies: 22.3.
- Exact scope: Measure work frequency/media/routine battery/network behavior, quotas and cancellation under idle.
- Files/modules: android/app/src/main/java/org/athar/app/core/sync/; android/app/src/main/java/org/athar/app/notifications/ (new); android/app/src/main/java/org/athar/app/media/.
- Automated gates: Work/provider call counts/idle/background traces. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Measure work frequency/media/routine battery/network behavior, quotas and cancellation under idle. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 22.5 — Backend query and rate budgets

- Parent: Work package 22: Performance/reliability; state: **PARTIAL**; dependencies: 22.4.
- Exact scope: Profile catalog/discussion/sync/Admin indexes/pagination/queries and fix demonstrated hot paths.
- Files/modules: backend/src/; database/migrations/.
- Automated gates: Query count/EXPLAIN/rate/concurrency regression tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Profile catalog/discussion/sync/Admin indexes/pagination/queries and fix demonstrated hot paths. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 23.1 — Android permissions and IPC security gate

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 22.5.
- Exact scope: Audit final manifest/services/receivers/providers/exported flags/grants and runtime permissions across supported APIs.
- Files/modules: android/app/src/main/AndroidManifest.xml; android/app/src/androidTest/.
- Automated gates: Permission/UID/exported/deep-link/file-provider hostile caller tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Audit final manifest/services/receivers/providers/exported flags/grants and runtime permissions across supported APIs. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 23.2 — Backend and Admin security gate

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 23.1.
- Exact scope: Verify auth/CSRF/rates/SSRF/uploads/output encoding/owner/private-data boundaries for final routes.
- Files/modules: backend/; admin/ (new); tests/.
- Automated gates: Adversarial HTTP/role/ownership/upload/provider tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Verify auth/CSRF/rates/SSRF/uploads/output encoding/owner/private-data boundaries for final routes. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 23.3 — Dependency content and model rights gate

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 23.2.
- Exact scope: Pin/review dependencies/licenses/source attribution/download/book/model rights and document actual zero-cost restrictions.
- Files/modules: android/; composer.lock; docs/security/; docs/legal/.
- Automated gates: Lock/build/license/attribution artifact checks. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Pin/review dependencies/licenses/source attribution/download/book/model rights and document actual zero-cost restrictions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 23.4 — Full encrypted backup restore rehearsal

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 23.3.
- Exact scope: Create and restore disposable encrypted complete backup/assets to isolated schema/path; compare integrity/ownership without touching corrupt original.
- Files/modules: app/backup.php; app/backup-assets.php; tests/; docs/testing/.
- Automated gates: Archive tamper/wrong key/assets/foreign keys/count/hash/restore tests. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Create and restore disposable encrypted complete backup/assets to isolated schema/path; compare integrity/ownership without touching corrupt original. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 23.5 — Final native API Admin end-to-end regression

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 23.4.
- Exact scope: Exercise complete approved journeys from account through content/private state/Admin moderation/notifications with isolated fixtures.
- Files/modules: android/app/src/androidTest/; tests/; admin/ (new).
- Automated gates: Full cross-stack matrix plus documented external delivery evidence. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Exercise complete approved journeys from account through content/private state/Admin moderation/notifications with isolated fixtures. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 23.6 — Final secret and recovery checkpoint

- Parent: Work package 23: Security/restore/regression; state: **PARTIAL**; dependencies: 23.5.
- Exact scope: Scan candidate/history/config/artifacts; verify clean recoverable branch and release rollback instructions.
- Files/modules: scripts/checkpoint-security.py; docs/deployment/.
- Automated gates: Reachable-history/candidate scan, Git integrity, artifact no-secret assertions. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Independent Admin browser: Arabic RTL/English LTR, narrow/desktop keyboard/touch; phone→API journey when public/native behavior changes. No real campaign send.
- Definition of Done: Scan candidate/history/config/artifacts; verify clean recoverable branch and release rollback instructions. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.37 overall percentage points.

## 24.1 — Free-host capability proof

- Parent: Work package 24: Deployment/release; state: **BLOCKED**; dependencies: 23.6.
- Exact scope: Select/prove a genuinely free compatible host with HTTPS/API/outbound providers/SMTP/cron/storage and honest capacity limits.
- Files/modules: docs/deployment/; backend/.
- Automated gates: Hosted health/provider/queue/storage/HTTPS probes with no paid signup. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Requires compatible free hosting account/capability proof; no paid plan or production overwrite.
- Definition of Done: Select/prove a genuinely free compatible host with HTTPS/API/outbound providers/SMTP/cron/storage and honest capacity limits. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 24.2 — Production configuration and operations

- Parent: Work package 24: Deployment/release; state: **PARTIAL**; dependencies: 23.6.
- Exact scope: Create validated environment/secrets/migrations/queue/health/backup/rollback deployment procedures.
- Files/modules: backend/; bin/; docs/deployment/.
- Automated gates: Missing/invalid config, safe migration and health/rollback rehearsal. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Create validated environment/secrets/migrations/queue/health/backup/rollback deployment procedures. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 24.3 — Signed reproducible Android release

- Parent: Work package 24: Deployment/release; state: **NOT STARTED**; dependencies: 24.2.
- Exact scope: Configure approved signing/versioning/proguard/AAB/APK without committing keys or local settings.
- Files/modules: android/; scripts/; docs/deployment/.
- Automated gates: Release build/signature/API URL/no debug flags/reproducibility checks. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Configure approved signing/versioning/proguard/AAB/APK without committing keys or local settings. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 24.4 — Hosted device and monitoring acceptance

- Parent: Work package 24: Deployment/release; state: **NOT STARTED**; dependencies: 24.3.
- Exact scope: Verify signed app against hosted API/Admin with consent-safe diagnostics and recoverable rollout.
- Files/modules: docs/deployment/; docs/testing/.
- Automated gates: Actual device/auth/content/media/notification/restore hosted matrix. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Verify signed app against hosted API/Admin with consent-safe diagnostics and recoverable rollout. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.

## 24.5 — Release artifacts and user operations handoff

- Parent: Work package 24: Deployment/release; state: **NOT STARTED**; dependencies: 24.4.
- Exact scope: Deliver verified APK/AAB/Admin/backend package, admin/user guides, known limits and maintenance/recovery runbook.
- Files/modules: docs/; scripts/.
- Automated gates: Artifact checksums/install/config/document links/final acceptance checklist. Native changes: assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, lintDebug and paired resources; backend changes: isolated relevant suites/schema checks; Admin changes: build plus HTTP/RBAC/CSRF/browser tests, as applicable.
- Physical-device gate: Authorized API27 phone: relevant real journey, Arabic RTL light and English LTR, offline/error/retry and retained data. UI-sensitive work also checks dark/reduced motion/large font/landscape; API33+ or tablet when required. No uninstall/reset/global-settings changes.
- Definition of Done: Deliver verified APK/AAB/Admin/backend package, admin/user guides, known limits and maintenance/recovery runbook. Applicable logic/API/persistence/offline/error/retry/privacy/localization/accessibility gates above pass; no placeholder or stale-branch logic; relevant device evidence recorded; docs updated; diff/security reviewed; logical commit and verified normal push.
- Estimated contribution: 0.36 overall percentage points.
