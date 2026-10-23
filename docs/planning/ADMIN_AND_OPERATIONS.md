# Admin and Operations Plan

## Editorial workflow

1. **Draft:** authorized editor enters metadata, source/edition, language, rights/license, and visibility as draft.
2. **Validate:** server validates lengths, IDs, URLs, upload signatures/size, required fields, source coverage and media IDs. An upload is private and non-executable until approved.
3. **Review:** source-sensitive Quran/tafsir/hadith/adhkar and book rights metadata receive the required qualified editorial/rights review; reviewer and decision are recorded. The platform does not create rulings or silently paraphrase source texts.
4. **Publish:** administrator with content-specific permission publishes; changes to public content retain actor/time and source metadata. Removal/unpublish records reason.
5. **Correct:** preserve source/version and correction history where practical; notify users only if a material correction affects saved/sharable records.

## Roles and admin actions

- Public may browse published content only.
- User may manage only own private data and preferences.
- Moderator scope (if comments/reports are approved) is limited to queue actions; all hide/remove/restore decisions have reason and audit record.
- Admin may CRUD assigned content types, perform scoped account support actions, and inspect aggregate metrics. No password reveal, credential retrieval, arbitrary SQL, or unrestricted private-progress browsing.
- Owner controls role grants, deployment-level backup/restore, and the local-development database reset capability. Admin promotion/removal and account suspension/deletion are high-risk and require step-up authentication, reason, confirmation and audit entry.

Every dashboard route and mutation performs server-side authorization per action and record scope; a hidden button is not a permission check. Audit events include actor, action, target, timestamp, reason, request/correlation ID, outcome and sanitized before/after summary. Do not log secrets, passwords, session IDs, or unnecessary religious reading history.

## Metrics

Show only operational/product aggregates useful to maintain the service: published counts by content type, draft/review backlog, upload/storage utilization, provider availability/latency/error/rate-limit counts, search result counts, and privacy-safe aggregate activity. Avoid user-level worship dashboards, public leaderboards, or personally identifying analytics. Set minimum aggregation thresholds and retention before launch; no third-party analytics without privacy review.

## Moderation

First-party comments are enabled for signed-in users and enter a pending queue. Administrators may publish or hide them; plain text is escaped, bounded to 2,000 characters, and rate-limited. New comments are not public before review. YouTube comments are not imported. Reports, appeals, and moderator staffing remain deployment/product policy work; keep comments disabled if the site cannot review the queue.

## Backups and restore

- Owner is the only role allowed to create and download an admin-area backup by default. Require step-up authentication and CSRF-protected POST to create it; require a separate owner-authorized, step-up authenticated, CSRF-protected POST to issue an owner-bound, single-use download grant expiring within 15 minutes. Audit request, creation, verification, grant issuance, download, expiry, deletion and failures without logging the token.
- Each archive contains a consistent database snapshot, schema/migration metadata, restore manifest/checksums, uploaded book PDFs and other Athar-managed uploaded assets needed to restore content. It excludes credentials/secrets, logs, caches/temp files, and external YouTube media. Show these contents before creation; never bundle encryption keys.
- Encrypt at rest and in transit. Store temporary archives outside the public web root with restrictive owner-only permissions. Verify integrity and manifest/schema readability before making an artifact downloadable. Do not use predictable/static public URLs; the protected download endpoint validates owner, artifact scope, expiry, single-use state and authorization server-side.
- Default temporary artifact expiry is 24 hours; safely delete after expiry or successful download and record deletion. Separately retain encrypted off-host operational recovery copies under the owner-approved retention schedule, with access audit and RPO/RTO. Account deletion propagates as those durable copies expire; disclose the schedule.
- Restore is a separate owner-only action with step-up auth, CSRF, typed target environment and destructive phrase, pre-restore verified backup, write lock, integrity/schema/asset verification, and independent audit. Run restore drills in isolated staging and record date/result/operator.

## Database reset decision and safeguards

Database reset is in the requested admin scope as a local-development feature, not an optional feature. The server denies execution in production regardless of UI visibility. Require owner-only permission, step-up authentication, POST-only, CSRF, typed exact environment (`LOCAL DEVELOPMENT`) and destructive phrase (`RESET ATHAR DATA`), a recent verified full backup no older than 24 hours, and a maintenance lock preventing concurrent writes. Before the final confirmation, list the exact data removed and retained.

Reset removes application data rows (accounts, roles/grants, curated content, source/review records, bookmarks, reading/video progress, favorites, khatmah plans, and database-resident operational data) and Athar-managed uploaded assets (book PDFs/covers) included in the full backup scope. It retains the database/schema, migration ledger, DB server users/grants, deployment secrets/configuration, independent logs/backups, and generated backup artifacts outside application storage until their own expiry/retention cleanup. Write the audit event to an independent append-only/off-host log before reset. Afterward, recreate the owner using the protected setup procedure. Recovery uses the verified full backup via the separate restore workflow. The dashboard never exposes SQL, credentials, encryption keys, or filesystem paths.

The action is disabled by default outside local development, and production has an unconditional server-side denial. Include a recovery notice that reset does not erase independent/off-host backups and that restoring is a separate explicitly confirmed owner operation.

## Routine operations and incident response

- Monitor uptime, application errors, DB capacity/slow queries, upload storage, and provider failures without collecting private text unnecessarily.
- Rotate secrets, update supported PHP/database versions, review API terms, and verify backups on a schedule set by deployment owner.
- Incident runbook: contain credential exposure/abuse, revoke tokens/sessions, preserve sanitized logs, assess personal-data exposure, notify owner and affected users/authorities where required, restore safely, document timeline and corrective action.
- Local development instructions and version assumptions are in `ARCHITECTURE.md`; no local packages, migrations, hosting, or backups were created in this planning phase.
