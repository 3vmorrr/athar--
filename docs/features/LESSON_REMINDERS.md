# Lesson reminder local scheduling

Task: 11.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

A selected lesson now creates one real WorkManager reminder through the WP10 owner-scoped scheduler. Canonical server \`scheduled_at\` is used as an Instant; \`schedule_timezone\` is validated and retained as source metadata without reinterpreting the UTC instant.

The reminder is due 15 minutes before start and expires at start. If the 15-minute point has passed, Athar refuses to invent an immediate/late replacement.

Unique work identity is stable per lesson. Idempotency includes event identity, event revision, canonical start and lead. An encrypted binding under \`private:lesson-reminder-binding:<lesson id>\` retains event id, revision, canonical start, source timezone and opaque work id.

Choice persistence happens before schedule creation so a crash cannot leave a hidden reminder with no visible opt-out. Scheduling failures roll back the choice. Opt-out removes work before the visible choice.

Task 11.3 owns authoritative change/cancellation reconciliation. Acceptance remains pending for build/unit/device timing, process death, near-start, permission, account-switch, timezone and reboot journeys.
