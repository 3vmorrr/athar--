# Athar maintenance and recovery runbook

## Routine monitoring

### Every release / deployment

1. Record Git SHA, server package SHA-256, Android APK/AAB SHA-256, version code/name and signer certificate fingerprint.
2. Verify HTTPS/API base and run the consent-safe hosted probe.
3. Verify current encrypted backup and managed-asset manifest before schema-affecting changes.
4. Confirm external provider readiness required by the release.
5. Keep the immediately previous server artifact/config reference available outside the public document root.

### Regular checks

- \`GET /api/v1/health\`: status, latency, request-id generation and TLS certificate validity.
- email outbox backlog/failures without exposing recipient identities in general monitoring;
- push/device readiness only through aggregate/provider-safe diagnostics;
- storage and database capacity against the selected host's real quotas;
- backup age, checksum/manifest consistency and restore rehearsal schedule;
- external Quran/audio/Dorar provider failure rates;
- Android release crash/support reports using non-sensitive evidence only.

## Email queue

The durable worker entry point is:

\`\`\`bash
php bin/process-email-outbox.php 20
\`\`\`

Transactional mail has priority over campaign mail. Do not bypass the queue by reintroducing bulk SMTP sends inside Admin HTTP requests.

## Database migrations

- run migrations only against the explicitly selected environment;
- take/verify backup before production schema changes;
- do not invent reverse SQL after an incident;
- when an incompatible migration must be rolled back, restore using the staging-rehearsed encrypted recovery procedure.

## Secret rotation

Keep DB passwords, settings key, backup key, auth HMAC key, SMTP/Dorar secrets, release keystore/passwords and provider credentials outside Git.

Changing encryption keys without an approved migration can make ciphertext unreadable. Treat key rotation as a planned migration, not a normal config edit.

## Android release incident

If a signed release is unhealthy:

1. stop/hold rollout;
2. identify affected versionCode and Git SHA;
3. do not replace/revoke the signing key as rollback;
4. fix from the last trusted source state;
5. assign a higher versionCode;
6. rebuild, verify signatures/canonical payload and rerun affected device journeys.

## Server incident

If health/auth/privacy behavior regresses:

1. stop rollout and new Admin mutations where appropriate;
2. retain logs/request ids without copying secrets or private payloads;
3. restore previous server artifact/config when schema is compatible;
4. otherwise follow encrypted backup recovery;
5. rerun health + authenticated test-user + affected Admin/device journeys.

## Backup recovery

Follow \`docs/deployment/BACKUP_AND_RECOVERY.md\`. Recovery testing belongs on disposable/staging schema/storage roots. Never perform a production reset as a test.

## Evidence hygiene

Release/support evidence may contain: timestamp, version, Git SHA, device model/API, request ids, status codes, aggregate latency, public certificate fingerprints and artifact hashes.

It must not contain: passwords, OTPs, bearer/refresh tokens, Admin cookies, service secrets, release private keys, private worship notes, private notification bodies or arbitrary database dumps.
