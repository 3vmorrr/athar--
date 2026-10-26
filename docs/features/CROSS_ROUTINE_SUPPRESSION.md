# Cross-routine suppression and user control

Task: 14.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Shared delivery policy

All local reminder families pass through one delivery coordinator. Immediately before posting, it now rechecks:

- authenticated owner;
- current master/category consent;
- encrypted global reminder policy;
- idempotency ledger;
- per-reminder expiry;
- minimum delivery interval;
- reminder-specific and global quiet hours;
- Android notification permission.

Unreadable consent, schedule, policy or ledger state fails closed.

## Cross-category frequency

\`minimumIntervalSeconds\` remains a property of the reminder contract; this task does not invent a new product cadence. When a reminder declares a minimum interval, it is now measured against the most recent delivered local reminder across all categories as well as the category-specific timestamp.

This prevents two different routine categories from bypassing an interval simply because they have different names.

## Global quiet hours

Account → Reminders now exposes optional encrypted local quiet hours. The user chooses start/end and the current IANA timezone. The policy applies to every local reminder at execution time, so an already-scheduled reminder also respects a later quiet-hours change.

Reminder-specific quiet hours and global quiet hours compose. Deferral is allowed only inside the reminder's original TTL. If quiet/frequency policy would move delivery beyond expiry, the reminder is dropped rather than delivered stale.

## Completion and revocation

Feature-specific genuine completion remains authoritative before scheduling: Quran saved-position progress, fixed-repeat adhkar completion, Khatmah plan status/workload and Night Prayer local exemptions each suppress only their own reminder. The shared policy does not infer worship from app opens or from another routine.

Master pause and category revocation do not delete private routine rules; they only block delivery. Resuming master consent does not enable categories the user had disabled.

## Privacy

The global policy stores only quiet-window times and timezone. The delivery ledger stores idempotency keys and delivery timestamps. Neither contains Quran position, adhkar counters, Khatmah workload, Night Prayer exemptions, custom labels or campaign audience facts.

Campaign targeting remains separate from private routine state.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and physical overlapping-rule, quiet-window, expiry, pause/revocation, restart, RTL/LTR, large-font, dark-mode and accessibility evidence.
