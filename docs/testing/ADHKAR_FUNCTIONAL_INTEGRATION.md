# Adhkar functional integration

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-30:** [149-task actual source audit](../progress/ACTUAL_IMPLEMENTATION_AUDIT.md). 113 tasks have implemented source (75 in main, 38 only unmerged); 19 are accepted/closed, 56 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

## Review baseline

Target070997cb7fba5bf726cab84ca5f7e2dca19b059a; parallel7392e93b088990c3478cf32197781f831eb10f83 (refetched before this batch). Preserve current calm counter layout and protected encrypted ownership. Source candidates: d45b818 discovery;078062c backend day contract;023a724/4c6008d/b959edb protected day/pending counters;6086176 stale-day UI;99bf164/c1e66df statistics;e23a956 accessible controls. Reminder-specific3b98360/5d20078/93f574d remain separate until notification dependencies are reviewed.

## Day contract and corrections

UTC daily views project an old acknowledged count as zero while retaining its stored row and revision. Offline prior-day writes retain their original completion date; they cannot be attributed to today's count. CAS ownership and genuine event idempotency remain. Legacy activity retries omitting a date preserve the acknowledged event's original date, fixing the source's midnight rejection. Explicit malformed/future dates fail422. No migration is needed for the backend day projection.

Counter encrypted snapshots gain an optional day. Legacy snapshots derive it from their existing row timestamp. An invalid explicit date remains unreadable; clock rollback retains state, never silently resets it. Pending/conflicted history blocks day rollover. Validated server receipts are checked before storage. A UTC observer tick updates an open completed counter after midnight without requiring a user mutation. Queued genuine completion events survive conflict resolution: deliver/acknowledge them before an explicit server choice changes the day; a concurrent unacknowledged completion prevents replacement.

Immediate backend gate:20 new day/idempotency/ownership assertions +46 catalog/progress +43 identity/API PASS in isolated port3322 database. Immediate Android gate: main Kotlin compile +16 JVM cases (day3/session13) PASS. Batch debug APK/test APK/lint PASS. JVM worker startup initially failed with host paging-file exhaustion (Windows1455); the remaining20 focused JVM cases passed with a task-local128MiB/SerialGC/single-worker init script,8s. No global host setting changed. Physical/device storage acceptance remains open.

## Device attempt

No authorized physical transport. Dedicated Athar_API34_QA attempted without wiping data: initial snapshot feature mismatch fell back to cold boot; a second foreground cold boot reached a boot-completed log but emulator exited1 before exposing an adb transport. No app was installed and no instrumentation PASS is claimed. Private diagnostic logs remain ignored under artifacts/functional-integration. Continue independent implementation; runtime acceptance stays open.

## Discovery, statistics and accessible controls

Current calm counter identity and protected controls remain. Added all-category discovery/search with paging; asynchronous completion advances only if category/query/page/index still match the initiating view. Counts/reset remain disabled while an unresolved day or conflict exists. Reset and navigation touch areas are at least48dp; counting remains64dp with localized progress semantics. Search paging includes all reviewed categories without inventing a fixed count for NULL sources.

Adhkar statistics are derived from owned genuine activity history and current reviewed fixed-count category progress. A changed source target does not reinterpret a stored old-target count as new-target progress. Android validates required dates/counts/category consistency; missing fields never become fabricated zeros. Cached/retained snapshots show their recorded date and cache/retry status. Unreadable protected pending history is reported separately. All category statistics are available through the expandable section; the current counter remains first.

Updated focused backend gate:24 daily/statistics assertions +48 catalog/progress +46 API/identity assertions PASS (118). Pending API scope cannot access statistics; malformed activity dates fail422. New encrypted legacy-day/conflict-history/clock-rollback instrumentation scenarios are added but unexecuted because no usable transport exists.

2026-10-29 follow-up:the API34 read-only QA batch successfully executed all5 CounterStorageTest cases and3 ProtectedStorageTest cases, including the new legacy-day/history/clock-rollback scenarios. Overall20 related cases PASS; see BOOKS_FUNCTIONAL_INTEGRATION.md. Earlier failed emulator attempts are retained above; physical/UI/reboot acceptance remains open.
