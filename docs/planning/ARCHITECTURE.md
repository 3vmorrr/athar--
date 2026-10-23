# Architecture Plan

**Implementation snapshot (2026-10-23):** Athar is a server-rendered PHP application with a small shared helper layer, route handling in `public/index.php`, escaped PHP views, vanilla JavaScript, and a MySQL/MariaDB schema. The current environment is PHP 8.0.30 with MariaDB 10.4.32. This local XAMPP stack is for development only; production versions and host remain unselected.

## Recommended shape

Start with a small, structured, server-rendered PHP MVC-style application. Do not introduce a framework without evidence or a later team need. Keep controllers thin; services hold use cases; repositories own prepared SQL; templates render escaped data; middleware/policies handle authentication, CSRF, and authorization. Separate public, account, admin, integration, and shared domain modules while keeping one deployable app. This minimizes operational complexity and naturally supports RTL HTML. A framework becomes reasonable if team size, testing needs, routing, or integration complexity makes custom infrastructure more expensive; select it before implementation, not implicitly in this plan.

### Current local baseline and production target

- Local PHP 8.0.30 exposes PDO MySQL, mbstring, fileinfo, openssl, and JSON. The implementation is kept compatible with PHP 8.0 for the available XAMPP runtime.
- Local MariaDB 10.4.32 uses InnoDB and `utf8mb4`. This is a development observation, not a recommended production target.
- For production, choose currently supported PHP and MySQL/MariaDB versions and verify extensions, strict SQL mode, UTC timestamps, TLS, and upgrade support before deployment.
- Apache or Nginx with document root set only to `public/`; local Windows Apache/MySQL bundle is acceptable if it meets the version and extension requirements. No framework or package install is prescribed now.
- Schema changes through ordered, tracked migrations with a migration ledger and rollback/forward-recovery notes. Never run an unreviewed migration against production.

The initial schema lives in `database/migrations/001_initial.sql`; `.env.example` lists configuration variable names. No dependency manager is required by the current implementation.

## Module boundaries

| Module | Responsibility |
|---|---|
| Public catalog | Quran navigation, hadith/tafsir display, books, scholars, series, lessons, adhkar |
| Identity and account | registration, login, preferences, private bookmarks/progress/export/deletion |
| Worship tools | Quran/video progress and khatmah calculations |
| Content administration | validation, editorial workflow, publish/visibility, upload and rights metadata |
| Moderation | first-party lesson comments, authenticated posting, pending review and audited publication/hide actions |
| Provider clients | server-side Dorar search; Quran and adhkar providers; official YouTube embeds, with bounded requests and source attribution |
| Operations | migrations, logs, health/metrics, protected backup artifact lifecycle/download, separately confirmed restore, local-development reset gate, audit records |

## Request lifecycle

1. Web server serves static assets or sends request to a single public front controller; secrets, uploads, migrations, and source files remain outside web root.
2. Bootstrap loads environment configuration, secure session, error handler, router, and request ID. Production errors are logged privately; users receive a safe localized message.
3. Middleware validates method, session/CSRF where applicable, authentication, role and resource ownership.
4. Controller validates route/query/body shape and bounds; service applies use-case rules; repository performs parameterized SQL or provider client makes a bounded HTTP call.
5. Provider response is schema-checked and mapped to source-attributed domain DTO; never trust remote shape or render raw HTML.
6. Template escapes text and renders semantic RTL-aware HTML; APIs return bounded, stable response shapes. Logs omit passwords, session IDs, secrets, tokens, private reading history, and full query content.

## Integrations and data ownership

- Quran content: prefer Quranpedia live endpoints if the specific content, search, audio, and usage terms meet feature needs. Use conservative cache TTLs and honor corrections, limits, and attribution/republishing restrictions. Do not mirror by crawling. Quran Foundation is an alternative requiring application credentials and production permission; keep client secret server-side.
- Hadith: use the official Dorar Hadith API through a server-side proxy. A token requires a manually reviewed access request; search stays unavailable until the approved token is configured. Keep the Bearer token encrypted at rest, display supplied grades with every result, preserve Dorar URLs/attribution, and never scrape or bulk cache.
- Video: store admin-entered YouTube ID and embed via official IFrame Player; no Data API key is needed for this proposed embed-only behavior. Do not download media or imply dependable playback resume.
- Adhkar/books/editorial material: use reviewed records with source, rights/license, edition, and review metadata. Do not substitute unknown-provenance feeds.
- Account email: send verification and password-reset OTPs through administrator-configured SMTP. Store only hashes of short-lived codes; store SMTP credentials and the approved Dorar token using AES-256-GCM under the environment-provided `ATHAR_SETTINGS_KEY`.
- Activity reporting: store per-user daily aggregate counters without recording specific verses or adhkar. Users see their own totals only.
- Video discussion: store first-party lesson comments as plain text with pending/visible/hidden states. Only authenticated users can submit; administrator review is required before publication.

## Caching and failures

Use a small cache abstraction with provider, endpoint/parameter key, locale, schema version, and expiry. Do not cache authenticated/private content in shared caches. Cache only as provider terms permit; avoid caching long-lived full text where corrections/rights advise otherwise. Apply request timeout, bounded retries with exponential backoff/jitter only for transient 429/5xx, circuit-breaking or short cooldown after repeated upstream failure, and stale-on-error only for content whose provider terms permit. Never retry invalid 4xx unchanged. Return actionable site-level states, log sanitized provider status/request ID, and keep other site modules working when one provider fails. API fact details are in `API_RESEARCH.md`.

## Backup, restore, and reset service boundary

- The owner-only backup service requires step-up authentication and a CSRF-protected POST to create an encrypted archive containing a consistent database snapshot, schema/migration version metadata, a restore manifest/checksums, and uploaded book PDFs plus Athar-managed assets required to restore content. It excludes credentials/secrets, logs, caches/temp files, and external YouTube media. Backups are integrity-checked before download.
- Store artifacts outside the public web root with restrictive filesystem permissions and encryption at rest. Issue a short-lived, single-use download grant only through a separate owner-authorized, step-up authenticated, CSRF-protected POST; bind it to the owner, artifact, expiry, and one successful use. Never expose a static/predictable public URL. Audit creation/grant issuance/download/expiry/deletion without logging the token.
- Expire artifacts after 24 hours; delete safely after expiry or successful download according to policy. The exact retention window can be shortened by the owner/deployment choice, but must remain short-lived and documented. Keep durable encrypted operational recovery copies separately under the approved retention policy.
- Restore is a separate owner-only workflow with step-up authentication, CSRF, explicit target-environment and destructive confirmation, write lock, pre-restore backup, and integrity/schema checks before replacement.
- Database reset is a local-development admin operation only. Production server logic denies it regardless of UI visibility or feature flags. Reset requires owner-only access, step-up auth, POST+CSRF, typed exact environment and destructive phrase, a verified full backup no older than 24 hours, write lock, explicit deletion/retention summary, and an audit event in an independent log/store before execution. Reset removes app data and Athar-managed uploaded content assets but leaves generated backup artifacts and independent/off-host backups intact until their own expiry/retention cleanup. Recovery uses the verified full backup through the separate restore flow. Neither feature exposes SQL or credentials.

## Performance and maintainability

- Index foreign keys and common lookups; add indexes for published status/order, normalized title/language, owner + content identifiers, and date/status admin queries.
- Pagination and bounded filters for catalogs/search. Avoid unbounded SQL and provider result sets; prevent N+1 reads with eager/batched lookups.
- Lazy-load covers and defer YouTube iframe until intent/viewport where appropriate; reserve aspect-ratio space. Use optimized thumbnails with provider-allowed assets.
- Use HTTP compression/cache headers for static assets, server-side HTML caching only for public pages and with correct invalidation, and query plans for expensive searches.
- Keep search parameter allowlists, min/max lengths, supported sort fields, and server-side result caps. Use full-text indexes only after representative Arabic text/search behavior is evaluated.

## Local setup plan

1. Select supported PHP/MySQL versions and local host (Apache/Nginx or local Windows stack); confirm required extensions.
2. Copy a non-secret `.env.example`; keep real secrets outside version control and web root.
3. Create a least-privilege local database and run reviewed versioned migrations.
4. Create a first admin through a one-time protected setup path or CLI; remove/disable installer after setup.
5. Configure provider credentials, callback/host allowlists, upload storage, and log destination.
6. Configure private encrypted backup storage, expiry/cleanup and independent audit logging; document and verify owner-only backup/download/restore flows. Keep reset available only in local development and prove the server-side production denial before production. This plan phase has not installed software or run migrations.
