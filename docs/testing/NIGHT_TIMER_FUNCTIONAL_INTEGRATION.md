# Measured Night Prayer timer integration —2026-10-30

08.4 adds a foreground measured timer to the existing Tonight screen. Starting, pausing, resuming, finishing and discarding do not record worship. A finished timer requires a separate count/range review and explicit recording. Native HTTP, actual OS lifecycle/process/reboot, physical device and full navigation/accessibility acceptance remain open.

## Reconciliation and ownership

Parallel7392e93 contributes monotonic counting, conservative recovery and premium card hierarchy. Its retained frames lack owner/environment binding, strict reconstruction and captured-generation transactional versions. It can overwrite a retained timer, hides checkpoint failures, and creates a session separately from clearing the timer. Those mechanisms are superseded rather than importing the branch wholesale. Current history/rejection/conflict behavior, Tonight identity and premium primitives remain.

`NightMeasuredTimer` validates sourced Maghrib date, timezone/method, actual boundaries, started/verified endpoint, measured duration and phase/reason combinations. `NightTimerAnchor` is runtime-only and measures monotonic foreground intervals. Paused wall time is excluded. Normal Fajr crossing counts at most the remaining verified interval. A wall-clock jump or uptime reset pauses at the previous verified checkpoint. No uptime value is reconstructed after process/reboot; a retained running checkpoint recovers paused without adding the gap.

Strict bounded manual codecs bind the frame and minimal recording receipts to owner and API namespace. `private:night-timer:v1` retains a versioned empty tombstone after cancellation/recording, so stale callbacks cannot create or discard a replacement. Every write validates the captured SessionLease and deletion fence inside a Room read/version/write transaction, with final checks. Start requires an empty reviewed frame. Unreadable/foreign state cannot become a fresh writable timer or be silently discarded.

One timer UUID is one recording operation. The measured time, actual Maghrib context and chosen count/range/note form a canonical fingerprint. Session, ordered outbox, minimal receipt and timer cleanup commit together or roll back together. A committed retry cannot duplicate or resurrect edited/deleted history and cannot clear a later timer. Different intent for the same UUID is a conflict. Receipts retain no second copy of the note/date/range. Timer operations do not change Quran, Khatmah or independent prayer-reading positions; timer history is not silently linked to a plan.

## Runtime and rendering

The state holder owns the runtime anchor, busy/epoch guards, observations and frozen recording intents. Foreground changes capture the stop instant before waiting for pending writes. Queued background callbacks require the same timer and owned anchor. They cannot pause a later timer after a delayed operation. Stale or uncertain write callbacks only read current state; conservative recovery of a running checkpoint is an explicit retry, avoiding mutation of a newer timer during error handling. Unconfirmed writes stop counting and show the last validated durable duration.

The timer controller lives outside Tonight's lazy item. Scrolling the card away does not background the timer; leaving Tonight or app ON_STOP pauses it. Return does not automatically resume. Source identity/method are required to start; an existing timer keeps its captured historical context when settings change. The plain card accepts immutable state/events and uses the existing premium surface, typography, spacing and native controls. Private drafts and confirmation versions use runtime memory, not plaintext SavedState.

The existing entry form is shared through a private helper. Normal history editing retains its existing interface and date/timezone fields. Measured recording shows its captured date/timezone and verified duration, keeps them noneditable, and asks for the actual count/range. An uncertain retry freezes the original entry. Clock validation preserves that same intent and asks the user to check the device clock. Finishing alone never creates history. History unreadability remains visible without hiding the independent timer controls.

Owned export adds paired optional `device_night_timer` and `device_night_timer_records` sections. Old copies remain accepted; malformed/foreign/duplicate frames or receipts abort export. Existing account cleanup purges these private rows while preserving another actor. No Room migration, dependency, permission, Quran asset or production backend contract changes are introduced.

## Validation provenance

Focused model, Room/Keystore/export and state-holder tests are separate from final consolidated evidence. The first model compile exposed a private helper visibility mistake, corrected locally. The initial new backend test incorrectly expected private measured fields in the deliberately minimal write acknowledgement; it now verifies the acknowledged revision and reads the owned session to check those fields. The existing privacy-preserving API response was not expanded. Failed logs remain ignored local evidence.

The focused backend suite passes39 assertions (the existing32 plus7 measured DTO/cross-midnight/rounding/boundary/replay checks) using its disposable MariaDB fixture. These are in-process API-kernel checks, not authenticated native HTTP acceptance. Earlier reader/planner/Khatmah baselines remain separately pinned, not added to rerun counts. Final consolidated counts, source/APK/log/JUnit fingerprints and remaining gates are recorded at the source checkpoint.


Final consolidated evidence:85/85 JVM cases in11 classes; debug/test APKs PASS; lint0 errors/69 warnings (one additional quantity-string plural candidate, no suppressions);544 paired resources PASS. The actual API34 gate passed30 storage/export cases and15 Arabic RTL+15 English LTR UI cases in dark theme at1.5 font scale:60 executions/45 unique cases,15 newly added instrumentation cases. Backend39 assertions PASS. Exact source/APK/JUnit/log digests are pinned in ACTUAL_IMPLEMENTATION_AUDIT_INPUT.json. Source40820d478164c5214a3e88ab636d9162a7ef213f was normally pushed and exact remote-head verified.08.4 is IMPLEMENTED/INTEGRATED/ACCEPTANCE_PENDING/PARTIAL.

Follow-on08.5 review found that current history renders pace only when nonnull; its null branch has no insufficient-data explanation. The earlier fully implemented/acceptance-only finding was unsupported and is corrected to partial main implementation before proceeding. No dedicated pace UI acceptance is inferred from these timer tests.
