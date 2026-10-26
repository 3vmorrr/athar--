# Unified reminder center

Task: 13.5  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The Account → Reminders surface is now the single control plane for the reminder families implemented through Work Package 13.

## Health and global pause

The overview shows Android notification-permission health and exposes an explicit system-permission action only from a user tap.

It also exposes the server-backed notification master switch. Pausing the master switch prevents every category from delivering while retaining all encrypted local rules and per-category choices. Resuming restores delivery only for categories the user previously enabled; it does not silently turn disabled categories on.

The generic worker still performs a final master/category/consent recheck immediately before posting.

## Cross-type inventory

The center counts active:

- lesson reminders;
- Quran wird rule;
- enabled adhkar category rules;
- Salawat and Friday rules;
- custom rules.

Unreadable encrypted sources fail closed and produce a visible partial-data warning instead of being overwritten or treated as empty.

Lesson choices now have a live Room collection observer, so enabling/disabling a lesson updates the center immediately without a schema migration.

## Controls

Lesson reminders can be opened or disabled directly from the center.

Quran and adhkar summaries link to their specialized screens, where their progress-aware controls remain located. Salawat, Friday and custom routines retain their full per-rule controls directly below the overview.

Custom reminders retain create/edit/pause/resume/delete. The global pause never deletes any of those rules.

## Privacy

The center composes local encrypted state in memory. It does not create a new analytics record, upload routine names/schedules, or expose Quran/adhkar completion as campaign segmentation.

Acceptance remains pending for Android assemble/unit/instrumentation/lint gates plus API27/API33+ physical permission health, pause/resume, account switching, offline retained state, RTL/LTR, large font, dark mode, landscape and accessibility evidence.
