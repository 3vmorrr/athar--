# Security and Privacy Plan

Security requirements apply to every route, admin operation, integration, and deployment. This is a planning baseline, not a completed security review.

## Authentication and account lifecycle

- Browsing, reading, and listening to public content does not require an account. Require login only for save/continuity actions.
- Hash passwords with PHP `password_hash()` using `PASSWORD_DEFAULT` (or a supported stronger Argon2id choice after host verification); verify with `password_verify()` and upgrade with `password_needs_rehash()`. Never log/store plaintext passwords or send them in email.
- Use generic login/reset responses to reduce account enumeration; throttle login, signup, reset, and verification by account and source/IP using bounded progressive delays. Protect against abuse without permanent lockout.
- Password reset tokens are random, single-use, hashed at rest, short-lived, and invalidate sessions or require reauthentication. Email verification and recovery contact changes are confirmed.
- On login, rotate session ID; regenerate on privilege change; revoke sessions on logout, password reset/change, deletion, and suspension. Do not place session IDs in URLs or logs.
- Current implementation uses strict server-side PHP sessions, a 30-minute idle limit and a 24-hour absolute limit on protected routes; protected requests re-read the account role from the database. Password recovery/change and cross-device session revocation still need an operational email/token flow.
- Deletion: require reauthentication/step-up, clear sessions/tokens, delete or anonymize personal progress/favorites, stop optional jobs, and disclose any legally required security/audit retention. Provide a user export before deletion and explain export expiry.

## Roles and authorization

Public: browse published content and permitted API search.
Authenticated user: only own bookmarks, positions, favorites, plans, settings, export, and deletion.
Moderator: only explicitly assigned reports/comments and moderation actions if enabled; no role management, credentials, reset, or unrestricted user data.
Administrator: curated-content CRUD, user support actions within scope, metrics, and operational tasks explicitly assigned.
Owner: assign privileged admins and perform separately protected, documented destructive operations.

Every protected request checks permission and object ownership on the server, including AJAX/API requests. Hide controls for convenience but never treat UI hiding as access control. Deny by default, validate role assignment, avoid direct-object-reference leaks, and audit sensitive actions with actor, target, reason, timestamp, and request ID.

## Sessions, requests, and output

- HTTPS in production; session cookies `Secure`, `HttpOnly`, `SameSite=Lax` (or stricter compatible setting), scoped to host/path, with idle and absolute lifetime limits.
- CSRF token for every state-changing browser request; POST/appropriate method only. SameSite is defense-in-depth, not a replacement.
- Use prepared statements; output-escape by context; treat rich content as plain text unless a vetted sanitizer and restricted markup policy are explicitly adopted.
- Validate route/query IDs as bounded integers/allowlisted IDs; allowlist sort/filter names; bound query length, page size, dates, text lengths, URL schemes/hosts, and JSON structure. Reject malformed Unicode/encoding safely.
- Add security headers (CSP compatible with provider iframe, frame/referrer policy, content type nosniff, permissions policy) and avoid inline unsafe script allowances where practical. Error pages never show stack traces, SQL, filesystem paths, secrets, or provider raw bodies.

## Uploads and downloads

- Only privileged content editors can upload. Enforce file size before processing; allow only reviewed PDF format for book documents; verify extension, server-detected MIME, and file signature; reject polyglots/encrypted/invalid PDFs per policy; malware scan when available.
- Generate random storage key; never trust browser MIME or filename. Store outside public executable web root/private object storage; prevent script execution; scan access path and backups.
- Publish only with rights-holder/license/permission source, edition, and allowed actions recorded. Authorization checks happen at download time.
- Set correct fixed content type, attachment disposition with a safely encoded display filename, `X-Content-Type-Options: nosniff`, and cache policy appropriate to public/private rights. Prevent path traversal and open redirects.

## Abuse, provider, and application limits

- Rate limit auth, search, write, comment/report if later enabled, upload, and expensive exports. Enforce limits server-side; return retry guidance without exposing internals.
- External HTTP calls use TLS verification, fixed provider host allowlists, strict timeouts, response size limits, schema validation, safe retry policy, and no user-supplied arbitrary URL fetch (SSRF prevention). Never scrape or ignore provider limits.
- Quranpedia documented limit: 120 requests/minute and 10,000/day per IP; server-side caching should reduce calls and comply with its no bulk scraping/republishing policy. Sunnah key is not guaranteed. Quran Foundation credentials are backend-only and production permissions are requested. See `API_RESEARCH.md`.
- Secrets are environment/configuration-managed outside web root and source control; distinct per environment, least privilege, rotated on exposure, and redacted from logs. Use least-privilege DB user; TLS to remote DB; no credentials in dashboard.

## Privacy and retention

- Collect only email/password and data necessary for requested account functionality. Quran/video progress and khatmah plans are private by default and are never public or used for ranking.
- Tell users which provider receives data when they initiate external playback or API-backed content; embed YouTube only when requested/visible and follow current Google requirements and applicable consent/privacy law.
- Define retention before launch for inactive accounts, logs, rate-limit keys, exports, reports, backups, and deleted accounts. Proposed defaults: short-lived logs with redaction, expiring exports, and backups retained only for an owner-approved recovery window. These durations need owner/deployment/legal review before publication.
- Provide account export and deletion routes, explain treatment of backups (deletion propagates as backups expire), and minimize IP/device fingerprint retention. Do not add third-party analytics until purpose, consent, and retention are approved.

## Downloadable backup, restore, and reset

### Owner-only backup create/download

- The admin area supports owner-only backup creation and download. Require recent step-up authentication and a CSRF-protected POST to create an artifact. Issue the one-time download grant only through a second owner-authorized, step-up authenticated, CSRF-protected POST; bind a random grant to the owner and one backup artifact, expire it within 15 minutes, and consume it after one successful download. Do not expose static/predictable public URLs, reusable bearer links, or artifact directory paths.
- Create a consistent database snapshot and encrypted archive containing the database, schema/migration metadata, restore manifest/checksums, uploaded book PDFs, and other Athar-managed uploaded assets necessary for content recovery. Exclude environment secrets, DB credentials, logs, caches/temp data, and externally hosted YouTube media. State this manifest to the owner before creation.
- Write artifacts outside the public web root with restrictive owner-only filesystem permissions; use authenticated encryption at rest with keys stored separately from artifacts. Do not put encryption keys in the DB dump or archive. Keep TLS in transit and do not log download tokens or backup contents.
- Verify archive integrity/checksum and manifest/schema readability before enabling download. Audit request, creation, verification, download, expiry, deletion and failure with actor/time/artifact ID/result; never log the bearer token or secrets.
- Default download-artifact expiry is 24 hours. Delete expired or successfully downloaded temporary artifacts safely and record deletion; clean up abandoned partial artifacts. Durable operational recovery copies are separately encrypted/off-host and retained only for an owner-approved schedule. Account deletion propagates to backups as those copies expire; disclose this retention.

### Restore

Restore is a separate owner-only action, not a side effect of download. Require step-up authentication, CSRF, typed target environment and explicit destructive phrase, a write/maintenance lock, and pre-restore verified backup. Verify archive integrity, schema/migration compatibility, and asset manifest before replacing data. Record the action outside the target database and verify restore in isolation before switching production traffic.

### Local-development database reset

Database reset remains in scope as an admin feature for local development. The server must deny reset in production regardless of whether the control is visible or a feature flag is misconfigured. Require owner-only permission, step-up authentication, POST only, CSRF, typed exact environment (`LOCAL DEVELOPMENT`) plus the destructive phrase (`RESET ATHAR DATA`), a recent verified full backup no older than 24 hours, and a maintenance lock that blocks concurrent writes. Show the precise removal/retention list and require a final confirmation.

Reset removes all application data rows (accounts, roles/grants, curated content, source/review records, bookmarks, Quran/video progress, favorites, khatmah plans, and database-resident operational records) and all Athar-managed uploaded files (including book PDFs/covers) covered by the reset scope. It retains the database/schema itself, schema and migration ledger, database server users/grants, deployment configuration/secrets, and independent logs/backups outside the application database. Generated backup artifacts outside application storage are not deleted by reset; temporary artifacts remain until their own expiry or successful-download cleanup, and durable copies follow their separate retention schedule. A verified full backup archive includes the files being removed. Recreate the initial owner through the protected setup procedure after reset; do not retain a hidden admin user or user progress.

Before executing, write an audit event with actor, environment, backup ID, scope, confirmation result and timestamp to an independent append-only/off-host log. Never rely only on the database being reset. Recovery is a separately confirmed owner restore from the verified full backup; reset does not erase independent/off-host backup copies. Never expose SQL execution, DB credentials, or raw backup paths through the dashboard.
