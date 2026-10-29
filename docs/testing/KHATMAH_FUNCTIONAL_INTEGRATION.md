# Khatmah functional integration —2026-10-29

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
