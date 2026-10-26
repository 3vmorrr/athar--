# Notification foundation

Task: 10.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Contract boundary

Notification consent is separate from the existing campaign marketing preferences. A user opting into a lesson or personal reminder does not opt into Admin push campaigns, and campaign consent does not enable personal reminders.

The authenticated contract is:

- \`GET /api/v1/me/notification-preferences\`
- \`PATCH /api/v1/me/notification-preferences\`

All settings default to disabled. The server returns revision \`0\` when no preference row exists, so merely reading settings does not create consent.

## Versioned consent

The current schema and consent policy versions are both \`1\`.

Enabling the master notification switch requires an explicit current consent version. A missing, revoked, unsupported or newer-than-client consent version fails closed. Consent can be explicitly revoked by writing \`consent_version = 0\`, which also disables the master switch.

Updates use \`expected_revision\`; stale writes receive HTTP 409 instead of silently replacing another device's choice.

## Categories

The foundation defines opt-in switches for lesson reminders, following updates, Quran routine, Adhkar routine, Salawat / Friday routine, custom routines, Khatmah, and Night Prayer.

A category is effective only when the master switch is enabled, current consent is present, and that category is enabled.

These are preference facts only. This task does **not** schedule reminders, request Android notification permission, register a push token, infer completion, or send a notification.

## Privacy and local persistence

The server stores only coarse consent/category booleans. It does not store private Night Prayer dates, Khatmah progress, Adhkar completion, Quran position, custom reminder text, or custom schedule times in this foundation.

Android caches the server contract in the existing encrypted, owner-partitioned private cache under \`private:notification-preferences:v1\`. The key is outside the disposable localized HTTP-cache namespace, so normal content trimming cannot delete it.

An absent local record produces the safe all-off default. Corrupted encrypted data becomes \`ProtectedRead.Unreadable\`; it is not silently converted into an opted-out record. Account switching fails closed through the existing verified-owner checks.

Account deletion already clears the owner's \`cached_documents\` rows, so this cache follows the existing owner-cleanup boundary without a new Room schema.

## Acceptance still required

The integration lane still needs to apply migration \`021_notification_preferences.sql\`, run the PHP integration test and Android JVM/build/lint/resource gates, exercise offline/restart/account-switch behavior, and verify the exact pushed SHA before task 10.1 can be marked COMPLETE.
