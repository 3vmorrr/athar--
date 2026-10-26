# Lesson reminder reconciliation

Task: 11.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Selected lesson reminders are reconciled against the existing authoritative lesson-detail endpoint. No new endpoint exposes hidden event inventory.

The app keeps an immediate and 15-minute network-constrained WorkManager reconciliation job for the currently verified owner. Account/environment tags cancel old-owner work on logout or switch.

For each encrypted local lesson choice the worker fetches \`GET /upcoming-lessons/<lesson id>\`:

- a newer scheduled public event with reminders enabled is rescheduled from its new canonical instant/revision/timezone;
- live, published, cancelled or reminder-disabled events remove pending reminder work and the no-longer-fulfillable local choice;
- HTTP 404 also removes the known local choice because the formerly known event is no longer publicly available;
- authentication failures stop owner work;
- transient/network failures never erase local choices and use bounded WorkManager retry.

The local detail screen also re-runs \`ensureScheduled\` whenever revision/start/timezone/reminder eligibility changes, providing faster reconciliation while the user is viewing the event.

No polling occurs without a selected local reminder. The worker is network-constrained and does not block offline local delivery merely because the network is unavailable.

Acceptance still requires device testing around cancellation/reschedule races, offline-at-delivery, 404/private transitions, account switching, process death, periodic WorkManager timing and near-start changes.
