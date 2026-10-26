# Server lesson reminder jobs

Task: 11.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## API and ownership

Authenticated users can reconcile their server fallback state with:

- \`GET /api/v1/me/lesson-reminders\`
- \`PUT /api/v1/me/lesson-reminders/<lesson id>\`

Mutation requires \`expected_revision\` and \`enabled\`. A stale device receives 409 instead of overwriting a newer choice.

Server opt-in requires current versioned notification consent, the master notification switch, the \`lesson_reminders\` category, a public scheduled event, Admin \`reminders_enabled\`, and a future 15-minute reminder window. Each account is limited to 64 active lesson reminders.

## No send-on-save

Enabling a lesson reminder only creates/updates:

- an owner/lesson subscription;
- one pending 15-minute job.

It does not create a push outbox row and cannot send a push.

The CLI worker \`bin/process-lesson-reminders.php\` converts due, still-valid jobs into a structured \`push_outbox\` intent. The outbox contains no provider token and no provider secret. WP15 owns device-token lifecycle, provider transport and the transition from queued intent to physical delivery.

## Revalidation and cancellation

Before outboxing, the worker rechecks:

- owner subscription revision and enabled state;
- current notification consent;
- current public event status;
- Admin reminder flag;
- event revision and canonical start;
- TTL.

Admin event edits call the same reconciliation layer transactionally. Reschedule replaces the pending job and cancels any obsolete queued push intent. Cancel/live/published/private/reminder-disabled transitions invalidate the subscription.

Notification master/category consent changes reconcile server jobs immediately. A temporary master pause cancels delivery work without deleting the user's subscription; re-enabling valid consent rebuilds a future job if its window still exists.

Unexpected processor failures use bounded exponential retry metadata. Dedupe keys are unique across jobs/outbox, and replaying the due worker cannot create a second push intent.

## Privacy

Rows contain only lesson reminder ownership and public event timing facts. They contain no Quran position, adhkar completion, Khatmah workload, Night Prayer dates/notes or campaign segmentation.

Acceptance remains pending for isolated migration/API/worker tests in the integration lane, concurrency/locking tests, deployment cron wiring, physical push proof after WP15, and final device fallback/dedupe evidence.
