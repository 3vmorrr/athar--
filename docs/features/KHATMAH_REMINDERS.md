# Khatmah workload reminders

Task: 14.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The Khatmah reminder is an explicit, local reminder choice layered on the existing Khatmah plan. It does not create a second plan model.

## Workload source

The reminder body uses the existing \`calculateMetrics\` contract. It is eligible only when the plan:

- exists and is not conflicted;
- is \`active\`;
- has fewer than 604 completed pages;
- has started;
- is not overdue;
- has a positive computed \`dailyPages\` workload and remaining pages.

Paused, finished, completed, conflicted and expired plans suppress pending delivery immediately while retaining the user's reminder rule. Returning a valid plan to active state causes reconciliation to schedule the next eligible occurrence again.

Khatmah's established plan-day boundary is UTC. For each candidate local notification time, the planner converts that instant to its UTC date before calculating workload, so reminder suppression cannot disagree with the plan's own metrics near midnight.

## Local rule and delivery

The encrypted local rule stores enabled state, selected local time, IANA timezone and revision/activation metadata under \`private:khatmah-reminder-rule:v1\`.

Only the next eligible occurrence is retained. Its TTL ends at the next local midnight and its idempotency key is stable per local day, so plan edits can update workload text before delivery without creating a duplicate notification.

KhatmahRepository now has a single AppGraph instance. Every durable local plan write/removal invokes reminder reconciliation before returning; pause, finish, restart, edit, conflict resolution and server refresh therefore share one state transition path.

A sign-in lifecycle hook restores daily maintenance after process restart.

## Consent and privacy

Enabling explicitly opts into only the existing \`khatmah\` notification category and requests Android notification permission only from the user's action. Generic delivery rechecks consent again immediately before posting.

The plan remains subject to its existing account sync contract. The separate reminder time/rule stays encrypted locally and is not campaign segmentation input.

Acceptance remains pending for Android assemble/unit/instrumentation/lint plus physical API27/API33+ edit/pause/finish/offline/restart/date-boundary, RTL/LTR, dark mode, landscape, large-font and accessibility evidence.
