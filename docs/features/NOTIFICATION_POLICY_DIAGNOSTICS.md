# Notification policy diagnostics

Task: 16.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The Reminders surface now includes a local privacy-safe diagnostics card.

The diagnostic repository reads only the current owner's already-existing encrypted notification preference, schedule, policy-ledger, quiet-hours and inbox-sync state. It does not make a campaign request and does not upload private reminder state.

Exposed diagnostics are deliberately aggregate:

- global master/consent allowance;
- quiet-hours enabled/disabled;
- whether any local delivery-history entry exists, as a boolean only;
- pending reminder count per known category;
- aggregate policy outcomes: ready, deferred, policy-dropped and consent-blocked;
- pending inbox mutation count;
- per-category allowed/blocked state.

The snapshot contains no notification title/body, custom routine label, Quran position, adhkar counter, Khatmah workload, Night Prayer exemption, worship note/date, delivery timestamp or campaign audience.

Outcome calculation reuses the same \`NotificationPolicy.evaluate\` engine as actual local delivery, including idempotency, expiry, minimum interval and global/per-reminder quiet hours. No second policy implementation is introduced.

Unreadable encrypted policy inputs fail closed and suppress the diagnostic rather than showing a misleading partial summary.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and physical Arabic RTL/English LTR policy changes, quiet hours, master pause, restart, large-font, landscape, dark-mode and accessibility evidence.
