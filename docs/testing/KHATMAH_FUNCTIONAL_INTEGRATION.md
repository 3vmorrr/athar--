# Khatmah functional integration —2026-10-29

## 07.7–07.8 preceding checkpoint

The following dated section records the earlier online editor checkpoint. The07.9 section below supersedes its offline implementation limits.

IMPLEMENTATION:07.7 and07.8 IMPLEMENTED in the current source;07.9 remains PARTIALLY_IMPLEMENTED.
INTEGRATION:07.7/07.8 INTEGRATED through the existing account route and shared web/API writer.07.9 has only the server CAS/replay and in-memory draft foundations integrated.
VERIFICATION:07.7/07.8 ACCEPTANCE_PENDING. Local focused evidence below does not close physical/native HTTP/process gates.
CLOSURE:PARTIAL for all three tasks.
MISSING:07.9 durable encrypted local intent/outbox, owned background sync, restart recovery and explicit server/device conflict resolution. Physical API27/RTL/LTR/accessibility and native actual HTTP journeys remain open; no emulator/physical run was performed for this checkpoint.
EVIDENCE:Khatmah.php;031_khatmah_revision.sql;KhatmahContract/KhatmahViewModel/KhatmahScreen;actual copied-fixture cookie/API HTTP and isolated MariaDB tests. Resulting source SHA is recorded in the next completion receipt, avoiding a self-referential commit hash.

## Source reconciliation

Parallel7392e93 supplies the editor/workload/lifecycle intent. Its repository is not imported wholesale: generic PUT lifecycle collapse, past-target exemption based only on target, rounding mismatch and refresh/ACK draft replacement would contradict the current owner/session/deletion contracts. The current account hierarchy and premium primitives remain; no cosmetic redesign, new dependency or permission was added.

`Athar\Progress\Khatmah` is the one web/API writer. Existing plans receive additive revision1 without changing dates, progress, timestamps or ownership. DDL replay after interruption is safe; a mismatched existing column refuses ledger insertion without rewriting private rows. Mutations serialize on the owner row, including first creation and account deletion, and use expected_revision. Missing revisions fail safely instead of silently overwriting another writer. Reads remain additive; old exported projections retain their deliberate field scope.

Both calendar endpoints are inclusive UTC days. The same shared vectors test leap day, before-start, overdue, paused, completed and603-of604 behavior in PHP and Kotlin. Future/paused/overdue plans do not invent today's workload. Generic saves preserve lifecycle; finished plans need an explicit restart. Restart/finish/pause/resume have separate actions; an exact one-revision committed replay acknowledges without a duplicate reset. Divergent or later intervening versions remain409. Plan operations create no reading activity.

An overdue schedule may retain BOTH dates for a progress correction. Changing either date must satisfy the full current schedule contract. Date spans are bounded to1830 days, with a newly chosen start at most1830 days behind or366 ahead of current UTC date; no inconsistent platform calendar-year arithmetic.

The native ViewModel owns draft/baseline/server state. Refresh does not rebase or replace a draft; explicit discard replaces it only after a successful read and only if no newer typing arrived. A save ACK advances the acknowledged baseline while preserving newer edits with explicit unsaved feedback. Late finish ACKs keep a newer draft visible. Numeric localization converts Arabic/Persian digits without silently truncating6040 or changing1.5 into15. Session-generation leases fence dispatch/results and clear private presentation after actor invalidation. This is retained ViewModel state, not durable process-death/offline intent storage;07.9 remains open.

## Executed focused evidence

-84 isolated Khatmah assertions:shared workload vectors, strict values/dates, owner isolation, CAS/no-op/lost reply, explicit lifecycle/restart, concurrent separate PHP writer, populated/interrupted/replayed/wrong-type migration and no inferred activity. `artifacts/functional-integration/khatmah-replay-strict-final.log`.
-103 existing catalog/progress assertions, with the fixture updated to the revisioned write contract. `khatmah-backend-focused-final.log`.
-33 export +33 deletion +70 recovery assertions PASS on the new schema. `khatmah-backend-compatibility.log`.
-23 actual cookie/API HTTP assertions PASS in one copied disposable schema:both writers share revisions, stale web drafts are retained, API conflict includes owned current state, generic finished edits fail, explicit restart replay is safe, foreign ownership and missing CSRF cannot mutate data. `khatmah-real-http-final.log`.
-8 targeted JVM tests:shared PHP/Kotlin vectors, malformed contract rejection, strict localized numbers, refresh/ACK/newer-draft races, failed deliberate discard, cached-offline no-write, actor invalidation and UTC rollover without draft reset. Debug/test APK and lint are checked in the native checkpoint logs.451 paired AR/EN resources PASS. Latest consolidated gate is recorded in the completion receipt.

The initial export fixture lacked the newly required revision; corrected fixture uses revision0. The first HTTP test expected a generic400/403 CSRF status; source correctly returned the established419. The assertion now checks419 plus retained plan revision. Earlier failed logs remain ignored local evidence. No guard or schema was weakened for a test.

No real account, model/provider, email or worship records were used. No development schema was migrated, device installation/reset performed or physical acceptance inferred. Pre-existing public queued-mail wording and Admin-runner suite additions remain outside the feature commit.


## 07.9 durable synchronization checkpoint

IMPLEMENTATION: durable encrypted Khatmah intents and explicit conflict/rejection recovery are implemented in current main source. INTEGRATION: the existing account screen and owned WorkManager dispatcher use this repository; no parallel branch was merged wholesale. VERIFICATION: focused checks below; full acceptance remains pending. CLOSURE: PARTIAL until required native HTTP/OS/physical journeys are accepted.

The Room v3 owned document table stores a strict, bounded owner/environment journal under `private:khatmah-journal:v3`, outside disposable locale HTTP caches. No new dependency, permission, database migration or plaintext SavedState was introduced. Save commits locally before reporting device success; cloud acknowledgment has separate feedback. Unsubmitted typing remains ViewModel state and is explicitly distinguished from saved durable intents. It is not promised across process death.

The journal persists each prepared server precondition before dispatch. Only an unsent trailing save can coalesce; lifecycle actions remain ordered. A lost committed reply retries the same prepared head. Matching acknowledgments remove only that head, preserve newer intents and do not change the visible edit version. Room read/transform/write operations share a transaction, writable-owner deletion fence and captured session generation. Queued network and token refresh preserve that generation even across same-owner reauthentication. Unreadable or foreign encrypted state remains retained and blocks editing/export instead of becoming an empty plan.

The additive optional `expected_plan_hash` binds the owner and observed plan values/revision. Current web/native writers send it; existing revision-aware clients remain compatible. Both platforms share fingerprint vectors. Same-revision restored values reject stale writes. Cookie conflict forms retain the original fingerprint and revision. Exact committed replays return the current plan without a duplicate mutation; native acknowledgment additionally checks semantic equality.

Conflicts retain the local projection and latest reviewed server candidate, including an absent server plan. Rejected requests retain their encrypted bytes. Explicit cloud/device choices re-read the server and require the reviewed local version. Device rebase refuses a cloud version that changed again, and finished-plan replacement requires the visible restart/progress-reset confirmation. Unsettled transmitted intents cannot be silently canceled through the cloud-choice action. Invalid repairs leave the journal unchanged. Ordinary retry/discard affects only the unsubmitted form and does not erase queued edits.

Account export includes a validated `device_khatmah_state` array and still accepts older copies lacking this additive section. Account-owned deletion purges this document through the existing owned cleanup; other actors and the shared Keystore key remain untouched.

Executed before the emulator gate:66 focused JVM cases (7 classes),96 Khatmah +103 catalog/progress +33 export +33 deletion +70 recovery backend assertions,29 actual disposable-fixture cookie/API HTTP assertions,462 paired AR/EN resources. The debug/test APK and lint results are recorded with the final gate and completion receipt. This is a scoped rerun, not a relabeling of the original167-case baseline.

The initial final JVM gate exposed an acknowledgment-status race; equal edit versions now also require equal projected values before treating an observed result as an acknowledgment. The corrected unchanged regression tests pass. An initial backend selection used a nonexistent recovery suite name after four suites had passed; the supported `recovery_restore_test.php` was then executed separately and passed70 assertions. Earlier failed logs remain private evidence.

Emulator and remaining acceptance results are recorded below after execution. A Room close/reopen is database recovery evidence, not an OS process-kill/reboot test. Deterministic transport fixtures do not prove native production HTTP or OS network-restoration scheduling. API27 physical RTL/LTR/theme/font/landscape/accessibility and real process/reboot acceptance remain pending. No real accounts, mail, worship records or development schemas were changed.


API34 executed result (2026-10-29, reviewed2026-10-30): **6/6 PASS**, consisting of5 new `KhatmahStorageTest` cases plus the extended `AccountExportStorageTest`. Actual Room files and Android Keystore were used. The cases prove prepared-head recovery after close/reopen and lost committed reply, ordered newer-tail delivery, actor-A late-response fencing with actor-B rows unchanged, foreign/unreadable envelope retention, deletion fencing and owned purge, reviewed-server changes refusing overwrite, and export inclusion/failure on unreadable state. Transport is deterministic; native real HTTP, OS process death/reboot, network-restoration scheduling and physical acceptance remain open.

Final native gate:66/66 JVM cases, debug APK, test APK and lint PASS; lint has0 errors/64 warnings (the prior62 plus2 new quantity-string plural candidates). No lint suppressions were added. `khatmah-durable-final-corrected-gate.log`, `khatmah-api34-storage.private.log`, `khatmah-durable-backend-final.log`, `khatmah-durable-recovery-final.log`, and `khatmah-fingerprint-real-http.log` are ignored local evidence; the audit input fingerprints their contents.

At the user's preview request, the current debug app was installed and launched on the isolated API34 emulator and its real sign-in screen captured privately. The existing local stack was restarted via `start-local.ps1 -LoopbackOnly`; API health was available. No migration/reset/login was performed. Automatic approval review rejected a separate visible emulator window ("blocked by policy", no specific reason), so the preview was shown through the actual emulator screenshot. The emulator is no longer connected at the2026-10-30 continuation; no continuously running preview is claimed.
