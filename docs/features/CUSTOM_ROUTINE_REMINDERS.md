# Custom routine reminders

Task: 13.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **ENGINE IMPLEMENTED / UI ACCEPTANCE PENDING**

Custom reminders are local-only, owner-scoped and encrypted under \`private:custom-routine-reminders:v1\`. A rule contains a bounded user label, selected weekdays, local time, IANA timezone, enabled state and revision/activation metadata.

Rules have stable opaque 16-hex ids across edits. At most 12 custom rules may exist. Each enabled rule keeps only its next one-shot reminder, expires at the next local midnight and uses a stable per-rule/per-day idempotency key.

Create, edit, pause/resume and delete are offline-local operations. Deleting a rule removes its pending reminder. Restart persistence is guarded by a strict versioned codec. WorkManager receives only owner/environment/reminder identifiers; user-entered labels stay inside encrypted local storage/reminder specs and the local notification body.

The category is the pre-existing \`custom_routines\` consent category and generic delivery rechecks consent before posting.

Acceptance remains pending for the companion UI controls plus Android build/unit/instrumentation and physical restart/offline/edit/pause/delete evidence.
