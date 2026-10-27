# Durable Email operations

Apply the complete migration chain in a deployment checkpoint before using queued identity mail. Source019 is retained unchanged; local028 adds a separate network-attempt count, a durable dispatch boundary and explicit campaign-locale provenance. Source016–026 numbering remains reserved. Only disposable schemas were migrated during this integration; no live database or host scheduler was changed.

Configure Gmail through the Owner Services editor and an explicit production HTTPS ATHAR_BASE_URL. Set ATHAR_SETTINGS_KEY and a separate ATHAR_EMAIL_OUTBOX_KEY to independently generated32-byte values represented as64 hexadecimal characters in the host secret store. Do not put real keys, App Passwords or recipients in Git or logs. When no environment outbox key is configured, its private fallback is storage/secrets/email-outbox.key outside the required public/ document root. Existing ciphertext requires its original key; a missing/corrupt key never causes automatic replacement. Preserve it separately while queue ciphertext remains. Auth, private-data, settings, backup and outbox keys have separate purposes.

Inspect aggregate queue metadata without dispatch:

```text
php bin/process-email-outbox.php --status
```

An authorized deployment operator can run a bounded delivery batch:

```text
php bin/process-email-outbox.php 20
```

The numeric bound is1–100; invalid arguments refuse before sending. No scheduler is installed by this review. Deployment orchestration belongs to the release/host acceptance work. Each actual invocation may contact the configured provider and authorized or consented recipients; local acceptance used fixture callbacks only.

The CLI shares the recovery maintenance barrier and serializes workers per database. It claims one next-priority row at a time, so newly queued transactional mail takes precedence over later campaign messages in the same run. Campaign delivery rechecks current consent/account status, frequency, quiet hours and TTL. Policy/quota deferrals do not count as network attempts. Retry delay grows60/120/240… seconds, bounded at3600, with a maximum network-attempt count. Claims carry generation fences. Raw provider errors and message payloads do not enter operational diagnostics.

SMTP acceptance is not inbox receipt. If a worker dies or the transport outcome becomes ambiguous after dispatch begins, automatic replay stops with delivery_outcome_unknown. Preserve the evidence and investigate; do not reset these rows to pending as an automatic recovery policy. An unstarted stale reservation can safely be reclaimed. Exactly-once external SMTP delivery is not promised. Current OTPs are checked before dispatch; superseded, consumed or expired tokens and changed-address snapshots cannot be sent as current identity mail.

Campaign cancellation retires pending queue/outbox rows; a message already in flight may complete. Opt-out never cancels transactional identity mail. Backup archives exclude volatile outbox bodies and tokens. Restore removes the live outbox and cancels restored scheduled/processing campaigns, requiring a new draft and Owner confirmation before any replay. Historical campaign aggregates remain available. The public document root must remain public/.

Task20.5 remains open: only an explicitly authorized real Gmail inbox can prove OTP, reset and campaign-template receipt. No mock transport, HTML capture, queue row or SMTP-ready configuration closes that gate.
