# Free push provider abstraction

Task: 15.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Provider boundary

The backend now owns a provider-neutral \`PushProvider\` contract and a concrete Firebase Cloud Messaging HTTP v1 adapter. Sending code receives a validated \`PushMessage\` and returns one of four outcomes:

- sent;
- transient provider/transport failure;
- invalid device token;
- permanent provider/configuration failure.

FCM 408/425/429/5xx responses are retryable. \`UNREGISTERED\` and \`SENDER_ID_MISMATCH\` are classified as token failures for Task 15.2 revocation. Provider authentication failures remain distinct.

The provider emits data-only messages so Android can validate owner, environment, expiry and allowlisted destination before posting/navigation.

## Credentials

No provider credential belongs in Android or in the repository.

Backend configuration uses:

- \`ATHAR_PUSH_PROVIDER=disabled|fcm_v1\`;
- \`ATHAR_FCM_PROJECT_ID\`;
- \`ATHAR_FCM_SERVICE_ACCOUNT_FILE\`.

Inline \`ATHAR_FCM_PRIVATE_KEY\` is explicitly rejected. The service-account path must be absolute, readable and outside the repository tree.

The service-account token source signs a short-lived RS256 OAuth assertion for the Firebase Messaging scope and pins the OAuth token endpoint to Google. The production HTTP transport allows HTTPS only and does not follow redirects.

The default example configuration keeps push disabled, so a checkout cannot accidentally send.

## Android boundary

Android currently contains only the strict provider-neutral \`RemotePushEnvelope\` parser. It accepts version, owner id, 12-hex environment partition, known notification category, allowlisted destination, bounded title/body, dedupe key and expiry.

Unknown fields, arbitrary URLs/routes, invalid owners/environments and malformed expiry fail closed. No service-account key, OAuth token or server credential is present in this contract.

Task 15.2 owns device-token registration/rotation/revocation. Task 15.3 remains blocked until an authorized FCM project and approved physical test device are available.

Acceptance remains pending for executable backend tests in the integration lane and Android build/unit/lint gates.
