# Athar

Native Android/shared API migration is in progress. See [implementation tracker](docs/progress/TASK_TRACKER.md), [Android build instructions](android/README.md), [API contract](docs/architecture/API_CONTRACT.md), and [executed tests](docs/testing/TEST_REPORT.md). This is not a completed production release. The [recovered local runtime](docs/deployment/LOCAL_RUNTIME.md) uses a validated logical import on an independent MariaDB instance; the original corrupt data files remain preserved.

Arabic-first platform for Quran reading, Islamic knowledge, worship tools, and source-attributed content. The interface can be switched between Arabic and English, with a responsive header and detailed site footer. Password fields include accessible show/hide controls; new-password forms also provide a live strength hint. Quran tools include Arabic search, daily verse, reading by surah/juz/hizb/page, selected-verse or full-surah audio, tafsir, and memorization review.

## Local setup

For the configured recovered runtime on this development machine, run `powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1`. This starts missing local services without repairing data, applying migrations or replacing other processes. The following fresh-database instructions apply to a new installation.

Requires PHP 8.0 or newer and MySQL/MariaDB with PDO MySQL, mbstring, fileinfo, OpenSSL, and ZipArchive. For a fresh local database, configure the connection and apply each migration once in filename order:

```powershell
$env:ATHAR_DB_HOST = '127.0.0.1'
$env:ATHAR_DB_PORT = '3306'
$env:ATHAR_DB_NAME = 'athar'
$env:ATHAR_DB_USER = 'root'
$env:ATHAR_DB_PASSWORD = ''
& 'C:\xampp\php\php.exe' scripts/migrate.php --database=athar
```

Create the selected database first. The migration runner strips legacy database-selection statements, uses the explicit selected schema and skips versions already in `schema_migrations`. It never runs automatically during an HTTP request. Back up and verify existing data before applying additions. Do not rerun raw ALTER migrations.

Run backend tests without connecting to the existing database: `powershell -ExecutionPolicy Bypass -File scripts/test-backend.ps1`. This starts a separate loopback MariaDB instance, checks its data directory, creates disposable schemas, uses fake email delivery and stops its own instance afterward.

For deployments, use a dedicated least-privilege database account, configure secrets through the process environment, enable HTTPS, and set the web document root to `public/`. Never use the local XAMPP root account outside development.

Start the local PHP development server:

```powershell
& 'C:\xampp\php\php.exe' -S 127.0.0.1:8000 -t public public/router.php
```

Open <http://127.0.0.1:8000/> and create an account. Promote a content administrator with `& 'C:\xampp\php\php.exe' bin/promote-admin.php admin@example.com`, or the platform owner with `& 'C:\xampp\php\php.exe' bin/promote-owner.php owner@example.com`; both commands require typed confirmation. Visitors can read public content without an account. Accounts support private Quran bookmarks and position, opt-in merge of browser-local Quran progress, a khatmah plan, book/lesson favorites, lesson resume, activity totals, profile name/username updates, JSON export, password changes and OTP recovery. Usernames are unique, lowercase ASCII letters/digits/underscore, 3–24 characters, with reserved names rejected. Daily activity stores aggregate counts only, not specific verses or adhkar.

Administrators can curate, edit, publish, unpublish, and delete scholars, ordered series, series lessons, standalone lessons, books, reviewed adhkar, and lesson comments. Scholar profiles use a name and optional uploaded portrait image. Books use direct HTTPS download links; PDF files are not uploaded to the site. Publication/download controls still require a recorded rights basis. Admin settings include encrypted Gmail SMTP and approved Dorar API credentials. Branded HTML emails use the public logo URL derived from `ATHAR_BASE_URL` (set the real HTTPS site URL in production), with a plain-text fallback. Email campaigns target only verified accounts that explicitly opted in, provide an unsubscribe link, send in batches of three, and pause after 450 messages within 24 hours to preserve Gmail account capacity. Users manage their own mailing preference in their account, and administrators can update account profile fields and email; changing the address clears verification and opt-in and invalidates active sessions. Service settings are excluded from database backups, so configure them again after restoring onto a new host.

The owner-only backup feature requires `ATHAR_BACKUP_KEY`, a separate 32-byte key encoded as 64 hexadecimal characters. Generate one with `& 'C:\xampp\php\php.exe' -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`; store it in the machine/service secret store and a separate recovery location, never in Git. The owner dashboard creates and verifies an encrypted archive, then issues a separate one-time download grant. Archives are retained privately for 24 hours, including after download, so an owner can restore from the same host. Restore requires a recent verified archive, a matching schema version, password confirmation, and the exact `RESTORE FULL BACKUP` phrase. A local reset is denied unless `APP_ENV` is exactly `local` or `development`; it also requires a recent verified archive, password confirmation, the typed environment, and `RESET ATHAR DATA`. Reset keeps the schema, migration ledger, owner account, and backup artifacts. Both operations use a MySQL maintenance lock; content and account writes receive a retryable 503 while it is held. Changing a password increments the session version to revoke other devices.

Email verification and password recovery send six-digit OTPs through administrator-configured SMTP. Configure the SMTP host, TLS mode, sender, and credentials in the admin dashboard; send a test message before relying on recovery. Set `ATHAR_SETTINGS_KEY` to a separate randomly generated 32-byte key (64 hex characters) in the host's secret store before saving SMTP credentials or the Dorar token. Generate it with `& 'C:\xampp\php\php.exe' -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`; keep it outside Git and back it up separately. The OTP is database-hashed, expires after 10 minutes, and is limited to five verification attempts. Recovery responses do not reveal whether an email is registered.

The unified Dorar search page is wired to the official Arabic APIs for hadith, tafsir, creed, jurisprudence, ethics, etiquette, and history through a same-origin server proxy. Each service requires a manually reviewed access request. Apply at <https://dorar.net/api-services/request>, then save the approved token under Admin → Platform Services. Search remains unavailable until approval and token configuration. The interface retains result attribution and links, and requires the supplied grade for displayed hadith; do not scrape, bulk mirror, or re-API Dorar data.

Apply new database migrations once in filename order. Migration `005` adds email verification/preferences, `006` adds curated book metadata, `007` adds account usernames/activity aggregates, encrypted service settings, moderated lesson comments, OTP attempt limits, and direct book download URLs, `008` adds email opt-in and campaign delivery records, and `009` adds scholar portrait images.

For SEO, set `ATHAR_BASE_URL` to the actual public HTTPS origin. The app then emits canonical/OG metadata, a dynamic `robots.txt` and `sitemap.xml`; without a verified HTTPS origin it marks pages noindex and omits the sitemap. Private/auth pages and filtered search URLs are excluded from indexing.

## Product and source documentation

Product scope, security expectations, data model, API research, and design decisions are in [`docs/planning/`](docs/planning/). Ordered database migrations are in [`database/migrations/`](database/migrations/). Provider content is fetched live and attributed; search coverage and provider limitations are disclosed in the UI.
