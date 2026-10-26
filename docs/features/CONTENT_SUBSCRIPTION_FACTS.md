# New-content subscription facts

Task: 12.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Baseline without historical spam

Migration 024 records the current publication/replay state of existing lessons and series. Existing public catalog items therefore do not become “new” merely because the feature was deployed.

After that baseline, the observer emits a fact only for:

- a series becoming publicly visible;
- a lesson becoming publicly visible;
- replay becoming available for an already-public lesson.

Each target/fact type has one stable dedupe key. Re-observation or repeated state writes cannot create duplicate facts.

## Follow-derived candidate recipients

When a fact is first created, Athar snapshots only explicit follow relationships:

- new series -> followers of its visible scholar;
- lesson/replay -> followers of its effective scholar and followers of its series.

A user following both is still one eligible recipient.

Candidate sources are retained, but final eligibility rechecks the current follow row. Unfollowing after fact creation removes eligibility; following later does not retroactively subscribe the user to old content.

Verified, non-suspended accounts only are returned. Locale comes from the account language preference.

## Hidden/deleted content

Eligibility rechecks current catalog visibility. If a scholar/series/lesson becomes hidden through its own or parent visibility, the fact is cancelled and yields no recipients. Deleted targets produce no new fact.

Upcoming lesson publication/replay changes call the observer transactionally from \`UpcomingLessonService\`. A CLI scanner, \`bin/scan-content-facts.php\`, covers series publication paths and acts as a reconciliation safety net.

## Privacy boundary

Recipient selection queries follow tables, account eligibility and public content visibility only. Quran position, adhkar progress, Khatmah state, Night Prayer records, lesson watch progress and activity statistics are not segmentation inputs.

This task produces facts/candidates only. It does not send a notification. WP13+ consumes reminder foundations and WP15 owns physical push delivery.

Acceptance remains pending for isolated schema/service tests, admin publication-path coverage, scanner deployment wiring, concurrency checks and eventual end-to-end push evidence.
