# Proposed Data Model

Names are conceptual and may change during schema design. Store IDs as stable internal identifiers; provider identifiers and source snapshots remain separate. Use InnoDB, `utf8mb4`, UTC timestamps, foreign keys, bounded columns, and explicit indexes. Avoid duplicating provider religious text unless the provider license and use case explicitly permit it.

## Core entities

| Entity | Main fields / constraints |
|---|---|
| `users` | `id`, normalized unique email, password hash, status, locale/theme preferences, created/updated/deleted timestamps; no public progress fields. Unique normalized email; password hash only. |
| `roles`, `user_roles` | role key (`user`, `moderator`, `admin`, owner grant separately protected); unique user/role pair. Permissions stay server-defined, not user-editable. |
| `content_sources` | provider/name, canonical source URL, license/terms URL, attribution text, access/rights notes, verification date, active status. |
| `source_records` | content kind, source ID/reference, edition/version/language, metadata JSON only for bounded supplemental fields, verified date; unique source + kind + external ID + edition. |
| `scholars` | name, image key/URL, short biography, language, source/rights metadata, visibility, timestamps. |
| `series` | scholar FK, title, description, language, visibility, sort/order, timestamps. |
| `lessons` | nullable series FK for standalone lessons, optional scholar FK, title/description, unique validated YouTube video ID, duration/thumbnail metadata, order, visibility, source/created/updated timestamps. Series lesson order unique by series/order where supported. |
| `books` | title, author, description, keywords, language, cover key, publication metadata, rights holder/basis, source FK, upload key, detected MIME/size/checksum, allowed read/download flags, visibility, uploader and timestamps. Do not use original filename as storage key. |
| `adhkar_items`, `adhkar_categories` | text, language, optional count, category/occasion, source FK/reference, license/reviewer/review status, sort and publication state. Publish only reviewed entries. |
| `editorial_reviews` | content kind/ID, reviewer FK, decision, notes, reviewed timestamp; restrict publication to review state required by policy. |
| `audit_events` | actor ID, action, target type/ID, timestamp, request/correlation ID, reason, sanitized before/after summary. No passwords, secrets, full private content or tokens. Append-only to app roles; export separately for destructive reset protection. |
| `backup_jobs` | owner actor, state, created/expiry timestamps, artifact key, encrypted size, archive checksum, manifest/schema version, verification result, download/expiry/deletion timestamps. No artifact bytes or secrets in SQL. Artifact and metadata are stored privately outside the public web root; audit log records lifecycle events. |
| `backup_download_grants` | hash of random one-time grant, owner FK, backup job FK, issued/expiry/consumed timestamps, narrowly scoped purpose. Never persist the bearer token; short expiry and single successful use. |
| `reset_operations` (optional DB record only for non-sensitive status) | operation ID, actor, target environment, start/result timestamps and backup job ID. The authoritative reset audit event must also be written to an independent append-only log/store before reset, because the reset database may be unavailable or cleared. |

## Religious content and source boundaries

Quran ayah, translation, tafsir, hadith result, and audio metadata can be provider-backed DTOs rather than replicated tables. A short cache entry may store provider key, locale/edition, fetched/expiry time, and response only if provider terms allow. Keep provider/collection/book/chapter/hadith number, grade-if-supplied, language, edition, and attribution with the rendered record. Never synthesize a grade or source reference. If an editorial approved corpus is stored, add explicit license and edition/version fields and an audit trail for correction.

Search metadata records provider and coverage boundary, query limits, and checked date; it does not imply comprehensive corpus coverage. Search caches must have bounded TTL and normalized, non-sensitive keys.

## Private user data

| Entity | Relationship and behavior |
|---|---|
| `quran_bookmarks` | user FK + provider/edition + surah + ayah + optional label; unique `(user, provider, edition, surah, ayah)`. |
| `quran_positions` | one latest position per user and reading edition (or explicit history table if history is later approved); validated surah/ayah/page; updated timestamp. |
| `video_progress` | user FK + lesson FK, last position seconds, known duration if supplied, completion flag, updated timestamp; unique user/lesson; enforce range and throttle writes. |
| `user_favorites` | user + typed target (prefer separate constrained relations or validated polymorphic type/key); unique user/type/target. |
| `khatmah_plans` | owner FK, start date, target date, unit type (`pages`/`juz`/approved alternative), total and completed units, status (`active`/`paused`/`finished`), updated/calculation timestamps; numeric checks and date bounds. |
| `user_exports` (optional job record) | owner, state, created/expiry, file key; short TTL and private storage. Delete export after expiry/download policy. |

Every read/write of user data scopes by authenticated owner in the query/policy; admin access is exceptional and audited. No public profile, social ranking, or worship activity feed is proposed.

## Comments, reports, and moderation

`lesson_comments`: lesson/user foreign keys, plain-text body up to 2,000 characters, pending/visible/hidden state, timestamps, and optional moderator. Authenticated-only posting with session rate limiting; pending comments are visible only to their author and administrators. Every moderation decision is recorded in `audit_logs`. No YouTube comments integration is implied.

`users` also carries an optional public display name and a unique lowercase username (3–24 ASCII letters, digits, underscores; reserved names rejected). `platform_settings` stores non-secret SMTP fields and authenticated settings; secret values are AES-256-GCM ciphertext, while the key remains outside the database in `ATHAR_SETTINGS_KEY`. `account_daily_stats` stores aggregate Quran sessions, completed lessons, completed adhkar counters, and book downloads by user/date, without item-level reading history.

## Operational constraints and indexes

- Foreign-key indexes for all relations; unique normalized email; indexes on public `(visibility, updated_at)` and series/order; book language/title; scholar visibility/name; adhkar category/occasion/order; audit `(created_at, actor_id, target_type, target_id)`; private `(user_id, updated_at)` and unique ownership/content keys.
- Use database constraints for positive positions/counts, known status values, and valid dates, in addition to server validation.
- Avoid storing raw session tokens; session state should use secure server-side session storage. Avoid IP retention unless needed for abuse response and then apply documented short retention/aggregation.
- A downloadable backup archive contains a consistent database snapshot, migration/schema metadata, restore manifest/checksums, uploaded book PDF binaries, and Athar-managed assets required to restore content. It excludes secrets, logs, temporary/cache files and external YouTube media. Keep encrypted artifacts outside the public web root with restrictive permissions; default artifact expiry is 24 hours, with safe deletion after expiry or successful download. Retain separate encrypted durable recovery copies only under an owner-approved retention policy.
- Restore is separate from backup creation/download and requires owner authorization, step-up authentication, explicit target and destructive confirmation, integrity/schema checks, and a write lock. Local-development reset removes application data and uploaded assets only after a verified full backup; schema/migration ledger and deployment configuration/secrets remain. Reset is denied in production and its audit record is outside the reset database.
- Migration ledger stores version, checksum, applied timestamp, and deploy identity; no dashboard SQL interface.
- Account deletion removes or irreversibly anonymizes user-owned progress/favorites; document treatment of authored content and narrowly retained security/audit records in the privacy policy.
