# Android push-device lifecycle

Task: 15.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Android owns a random 128-bit installation id per API environment partition. The id is not a device hardware identifier and contains no account information.

Provider tokens are retained only in the existing encrypted owner-scoped Room cache. WorkManager inputs contain owner id and environment only; raw FCM tokens are never put in WorkManager data, logs, deep links or UI.

The lifecycle manager supports sign-in reconciliation from an injected provider token source, provider token rotation, network-constrained registration retry with bounded exponential backoff, environment-bound registration, and pre-logout installation revoke.

Logout always cancels token-sync work and deletes the local encrypted provider token even when the network revoke fails. Local sign-out is never blocked by device cleanup.

The token-source interface is intentionally provider-neutral. Task 15.3 will supply the authorized Firebase implementation; until then AppGraph uses a no-op source and no device registers accidentally.

SessionRepository exposes a narrow lifecycle hook. The server independently ties every registration to the authenticated session family and excludes revoked/version-stale families from delivery, so an offline local logout cannot make a signed-out app eligible for trusted delivery.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and authorized Firebase token/rotation/logout evidence in Task 15.3.
