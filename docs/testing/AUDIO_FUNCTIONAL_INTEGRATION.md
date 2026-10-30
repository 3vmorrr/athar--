# Audio functional integration

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-30:** [149-task actual source audit](../progress/ACTUAL_IMPLEMENTATION_AUDIT.md). 114 tasks have implemented source (75 in main, 39 only unmerged); 19 are accepted/closed, 56 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

## 04.1 ownership and session activity increment

Reviewed sourcec61e58c and the current persistent premium player. Retained Media3 focus/noisy handling and current mini/expanded controls. Adopted the source immutable session-activity PendingIntent. Both branches only cleared queues on unsigned/pending states, so direct SignedInA→SignedInB could retain former media. The service now rechecks verified current ownership before connections/commands/media replacement and clears the queue on owner changes. The application controller carries its captured owner as an additional fence and rechecks it before/after connection and controls.

Media3's verified ControllerInfo UID/trust authorizes callers; owner hints alone never authorize a UID. Trusted system controls remain available; only the app UID may replace media. A complete invalid media request is rejected so filtering cannot change its selected index. Existing URI allowlisting remains. No schema, backend API, dependency or manifest permission changes.

Installed Media3 1.8.0 AAR signatures were inspected for callbacks, commands and connection hints. [Official service reference](https://developer.android.com/reference/androidx/media3/session/MediaSessionService#onGetSession(androidx.media3.session.MediaSession.ControllerInfo)) documents main-thread callbacks and placeholder media-button callers; UID checks therefore belong to real connection/command callbacks, while onGetSession checks current authentication. Framework notification/system callers are not forced to present app-specific hints.

Immediate gate: main Kotlin compilation +17 JVM cases (ownership4/session13) PASS. Extended media replacement policy and its actual service wiring then passed5 ownership cases. No physical/IPC end-to-end/background/lockscreen proof is inferred. Next related increments hydrate retained session metadata, expose focus state and recover bounded source requests; then run the subsystem APK/test APK/lint gate.


## 04.1–04.5 retained session, recovery, timing and radio batch

Source commits reviewed: c61e58c /435d8a1 /9747b58 (session and hydration), db9b7aa /721a747 (focus), 08bbee6 /1d6e2bd (retry), 87a1018 /cee3b3d (timing/origin), 8d19740 (radio catalog). Changes are manually reconciled around the current premium mini/expanded player; the older source player fields and follow-along motion do not replace current controls or reduced-motion support.

The player derives actual queue metadata, buffering, suppression, duration and controls from Media3. A retained clipped item restores its public clip origin; timing recovery is read-only, fenced by account/current source/request generation, and never starts playback. Errors from a requested source survive unrelated player events. Retry repeats the last explicit request; a cold retained Quran session resolves its canonical source tuple. Changing selection invalidates stale Quran responses even if cancellation is ignored, while browsing Quran does not cancel an explicit radio request. Whole-surah audio starts before optional timing lookup; a single-ayah clip requires validated exact timing. Duplicate, overlapping, reversed, oversized or out-of-surah intervals fail closed; gaps do not invent highlights.

Radio search is applied by the backend before pagination (the source UI sent q but both previous backends ignored it). The source catalog search/pager is integrated separately. Provider identifiers, safe HTTPS sources, malformed search/page input and ambiguous duplicate rows are checked. Canonical Quran asset load failure is visible/retryable without breaking the independent radio route. No new migrations, permissions or dependencies.

Focused PHP fixture gate:31 combined media assertions PASS. A live public radio catalog read failed with URLError; no actual provider response or playback acceptance is claimed. Device inventory is empty. Physical background/lockscreen/focus/headphones/network retry/account-switch and RTL/LTR/accessibility journeys remain open. Android subsystem gate PASS: assembleDebug, assembleDebugAndroidTest, lintDebug and29 JVM cases (request3/selection2/timing3/ownership5/SessionRepository13/Quran restoration3),4m30s;356 paired resources. Initial compile failed on a newly referenced missing localized resource; added both translations and reran the complete batch gate successfully.
