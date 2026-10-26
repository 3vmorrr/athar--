# Completion-aware adhkar reminders

Task: 13.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Genuine completion

A category reminder is suppressed only when every currently snapshotted published dhikr with a real fixed \`repeat_count\` has a current-day counter whose count reached that target.

Opening the adhkar screen, browsing a category, skipping an item, partial counting, an old-day count or a conflicted counter never counts as completion.

Items whose curated \`repeat_count\` is NULL are not fabricated into measurable requirements. A category with no fixed-repeat requirements cannot enable completion-aware reminders.

The completion date deliberately follows the existing CounterRepository/backend UTC-day contract instead of inventing a second local-day definition for the same progress records.

## Rules and privacy

Each category rule stores locally encrypted:

- category name;
- reminder time;
- selected ISO weekdays;
- IANA timezone;
- the public item-id -> fixed-repeat requirement snapshot;
- revision/activation time.

Up to 24 category rules are retained under \`private:adhkar-reminder-rules:v1\`. Rule/counter state is never a campaign segmentation input.

When the category screen is revisited online, the enabled rule refreshes its public fixed-repeat requirement snapshot so added/removed curated items can be reconciled.

## Scheduling and suppression

Each enabled category creates up to 14 days of one-shot owner-scoped reminders through the shared WP10 scheduler. Category names are hashed for WorkManager-safe opaque ids; the encrypted reminder body may still show the category locally.

CounterRepository invokes the reminder reconciler after each durable local counter write. When the final fixed requirement genuinely reaches its repeat target, today's pending category reminder is removed. Resetting a completed counter makes the category incomplete again and reconciliation can restore today's reminder while its TTL remains valid.

Generic delivery still rechecks master/category notification consent immediately before posting.

## Consent

Enabling explicitly opts into only \`adhkar_routine\`, then requests Android notification permission from that user action when required. Rule disable removes pending reminders for that category.

Acceptance remains pending for Android build/unit/instrumentation, offline pending counters, sync/conflict resolution, UTC boundary tests on physical devices, permission flows, account switching, process death, RTL/LTR, large font, landscape and accessibility evidence.
