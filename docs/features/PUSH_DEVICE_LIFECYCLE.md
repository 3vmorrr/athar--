# Push device lifecycle

Task: 15.2 backend foundation  
Implementation branch: \`parallel/admin-foundation\`

Push device registrations are owner-, environment- and session-family scoped.

The server stores one row per environment/installation and at most one active owner for a provider token. Re-registering an installation rotates its token. Reusing a token on another account or installation revokes the previous mapping before activating the new one.

Raw provider tokens are AES-256-GCM encrypted at rest with a per-owner/environment/installation key derived from \`ATHAR_DATA_KEY\`. Revoked/invalid rows have both token ciphertext and token hash cleared. Device-list responses and audit rows never contain token material or token hashes.

Registration requires the Android 12-hex environment partition to match the backend environment. \`ATHAR_PUSH_ENVIRONMENT\` may set this explicitly; otherwise the backend derives it from \`ATHAR_BASE_URL + /api/v1/\`.

API:
- \`GET /api/v1/me/push-devices\`
- \`PUT /api/v1/me/push-devices/<32-hex installation id>\`
- \`DELETE /api/v1/me/push-devices/<installation id>\`

Logout revokes active devices tied to the current session family. Revoke-other-sessions also revokes device rows from other families.

Delivery eligibility independently verifies that the registration's session family still has a live, version-current API session. Password reset/change or replay revocation therefore stops pushes even if explicit device-row cleanup has not run yet.

The Android registration/retry layer is implemented in the companion 15.2 commit. Physical FCM registration remains Task 15.3 and stays blocked without authorized project configuration.
