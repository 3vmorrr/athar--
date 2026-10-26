# Persistent local reminder scheduler

Task: 10.5  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Durable encrypted reminder state

Pending local reminder specifications are stored under the authenticated owner's encrypted private cache:

\`private:notification-schedules:v1\`

The persisted specification is bounded to 64 pending reminders and validates opaque reminder id, allowlisted personal notification category, allowlisted internal destination, title/body length, scheduled time, expiry, maximum 30-day TTL, bounded frequency interval, and paired quiet-hour start/end/timezone.

The WorkManager database receives only the owner id, environment partition and opaque reminder id. It does not receive reminder text, category, destination, schedule time, quiet hours, Quran position, Adhkar completion, Khatmah progress, Night Prayer dates or notes.

Unreadable encrypted schedule data is retained and fails closed. It is never silently replaced by an empty schedule.

## WorkManager ownership

Every reminder is a unique one-time WorkManager job whose name includes the API environment partition, owner id, and a short SHA-256 digest of the opaque reminder id.

Jobs also carry an owner/environment tag so logout or account switching cancels the previous owner's work in one operation.

The scheduler uses inexact WorkManager initial delay only. It does not request or depend on \`SCHEDULE_EXACT_ALARM\`, \`USE_EXACT_ALARM\`, background location permission, or an always-running service.

## Execution

At execution time the worker validates environment/owner/input, restores the authenticated session if necessary, loads the encrypted reminder and delivery ledger, evaluates scheduled time/TTL/dedupe/frequency/quiet hours, defers with chained unique work when needed, checks current Android notification permission, posts to the stable channel with an owner-bound deep link, records delivery, then removes the one-shot reminder.

The notification id is deterministic from the idempotency key and \`setOnlyAlertOnce(true)\` is used. If the process dies between posting and ledger persistence, replay replaces the same tagged notification rather than creating a second visible notification.

## Account switching

The coordinator watches authenticated owner changes. On sign-out or account switch it cancels the previous owner's WorkManager jobs and all still-active Athar reminder notifications carrying that owner's private tag.

A notification posted immediately before an account switch is cleaned up by that observer; its PendingIntent is owner-bound and refuses to navigate under another account.

## Permission denial

A missing Android notification permission does not delete the encrypted reminder. The worker exits without posting and leaves the reminder available for a later explicit reconciliation after permission is granted. No permission prompt is launched by background work.

## Acceptance still required

The integration lane must run Android build/lint/unit/instrumentation gates and device tests covering process death, WorkManager replacement/defer behavior, logout/account switch cancellation, permission denial then grant/reconcile, corrupted encrypted storage, quiet-hour deferral, deterministic replacement, restart and low-power inexact delivery before task 10.5 can be marked COMPLETE.
