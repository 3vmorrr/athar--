# Notification delivery policy

Task: 10.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Shared semantics

Local reminder delivery now has the same policy ordering already used by Admin campaign delivery:

1. reject an already-delivered idempotency key;
2. reject an expired notification;
3. respect the scheduled time;
4. apply the minimum frequency interval;
5. apply the user's timezone-aware quiet window;
6. if policy deferral reaches or passes TTL, drop instead of delivering stale content.

Quiet windows support same-day and cross-midnight ranges. The calculation uses \`ZoneId\` / \`ZonedDateTime\`, so DST and timezone offset changes are resolved by the platform rather than fixed-offset arithmetic.

## Local idempotency ledger

Delivered keys and last-delivered timestamps are retained in an encrypted owner-scoped cache:

\`private:notification-policy-ledger:v1\`

The ledger is deliberately small and bounded:

- at most 256 delivered keys;
- delivered-key retention is 30 days;
- category-level last-delivery timestamps are retained for frequency policy.

The ledger contains no notification body, Quran position, Adhkar completion, Khatmah progress, Night Prayer dates, notes, custom reminder text or other private worship payload.

Unreadable encrypted ledger data fails closed. It is not silently replaced with an empty ledger because that could duplicate notifications after corruption.

## Campaign compatibility

The existing server campaign queue already performs timezone quiet deferral, TTL expiry, channel frequency limits and SHA-256 dedupe. This task does not replace that working server path. It establishes equivalent local reminder semantics for the native scheduler used by the following work packages.

## Acceptance still required

The integration lane needs JVM/build validation plus device/timezone tests across DST, midnight, manual clock/timezone changes, restart, duplicate work, corrupted ledger state and long-idle delivery before this task can be marked COMPLETE.
