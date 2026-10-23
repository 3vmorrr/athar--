# Athar

Arabic-first platform for Quran reading, Islamic knowledge, worship tools, and source-attributed content. The Quran reader supports navigation by surah, juz, or page; Arabic verse search; tafsir; selectable reciters; verse-by-verse playback; repeat and skip controls; and a memorization review mode.

## Local setup

Requires PHP 8.0 or newer and MySQL/MariaDB with PDO MySQL, mbstring, fileinfo, OpenSSL, and ZipArchive. For a fresh local database, configure the connection and apply each migration once in filename order:

```powershell
$env:ATHAR_DB_HOST = '127.0.0.1'
$env:ATHAR_DB_PORT = '3306'
$env:ATHAR_DB_NAME = 'athar'
$env:ATHAR_DB_USER = 'root'
$env:ATHAR_DB_PASSWORD = ''
Get-ChildItem database/migrations/*.sql | Sort-Object Name | ForEach-Object {
    Get-Content -Raw $_.FullName | & 'C:\xampp\mysql\bin\mysql.exe' -u root
}
```

The migration ledger is `schema_migrations`. When updating an existing database, apply only migration versions not yet recorded there; do not rerun an `ALTER TABLE` migration.

For deployments, use a dedicated least-privilege database account, configure secrets through the process environment, enable HTTPS, and set the web document root to `public/`. Never use the local XAMPP root account outside development.

Start the local PHP development server:

```powershell
& 'C:\xampp\php\php.exe' -S 127.0.0.1:8000 -t public public/router.php
```

Open <http://127.0.0.1:8000/> and create an account. Promote a content administrator with `& 'C:\xampp\php\php.exe' bin/promote-admin.php admin@example.com`, or the platform owner with `& 'C:\xampp\php\php.exe' bin/promote-owner.php owner@example.com`; both commands require typed confirmation. Visitors can read public content without an account. Accounts support private Quran bookmarks and position, opt-in merge of browser-local Quran progress, a khatmah plan, book/lesson favorites, lesson resume, activity totals, profile name/username updates, JSON export, password changes and OTP recovery. Usernames are unique, lowercase ASCII letters/digits/underscore, 3–24 characters, with reserved names rejected. Daily activity stores aggregate counts only, not specific verses or adhkar.

Administrators can curate scholars, ordered series, series lessons, standalone lessons, books, and reviewed adhkar. A lesson attached to a series inherits or must match the series scholar. Public book PDFs can be uploaded privately or linked through a direct HTTPS download URL; publication/download controls still require a recorded rights basis. Lesson comments are authenticated-only and remain pending until an administrator reviews them. Admin settings include encrypted SMTP and approved Dorar API credentials. Service settings are excluded from database backups, so configure them again after restoring onto a new host.

The owner-only backup feature requires `ATHAR_BACKUP_KEY`, a separate 32-byte key encoded as 64 hexadecimal characters. Generate one with `& 'C:\xampp\php\php.exe' -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`; store it in the machine/service secret store and a separate recovery location, never in Git. The owner dashboard creates and verifies an encrypted archive, then issues a separate one-time download grant. Archives are retained privately for 24 hours, including after download, so an owner can restore from the same host. Restore requires a recent verified archive, a matching schema version, password confirmation, and the exact `RESTORE FULL BACKUP` phrase. A local reset is denied unless `APP_ENV` is exactly `local` or `development`; it also requires a recent verified archive, password confirmation, the typed environment, and `RESET ATHAR DATA`. Reset keeps the schema, migration ledger, owner account, and backup artifacts. Both operations use a MySQL maintenance lock; content and account writes receive a retryable 503 while it is held. Changing a password increments the session version to revoke other devices.

Email verification and password recovery send six-digit OTPs through administrator-configured SMTP. Configure the SMTP host, TLS mode, sender, and credentials in the admin dashboard; send a test message before relying on recovery. Set `ATHAR_SETTINGS_KEY` to a separate randomly generated 32-byte key (64 hex characters) in the host's secret store before saving SMTP credentials or the Dorar token. Generate it with `& 'C:\xampp\php\php.exe' -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`; keep it outside Git and back it up separately. The OTP is database-hashed, expires after 10 minutes, and is limited to five verification attempts. Recovery responses do not reveal whether an email is registered.

Hadith search uses Dorar's official Arabic API through a same-origin server proxy. Dorar manually reviews access requests before issuing a Bearer token. Submit an access request at <https://dorar.net/index.php/api-services/request>, then save the issued token under Admin → Platform Services. Search intentionally remains unavailable until Dorar approves and the token is configured. The interface retains Dorar attribution, result links, and supplied grades; do not scrape or bulk mirror its database.

Apply new database migrations once in filename order. Migration `005` adds email verification/preferences, `006` adds curated book metadata, and `007` adds account usernames/activity aggregates, encrypted service settings, moderated lesson comments, OTP attempt limits, and direct book download URLs.

For SEO, set `ATHAR_BASE_URL` to the actual public HTTPS origin. The app then emits canonical/OG metadata, a dynamic `robots.txt` and `sitemap.xml`; without a verified HTTPS origin it marks pages noindex and omits the sitemap. Private/auth pages and filtered search URLs are excluded from indexing.

## Product and source documentation

Product scope, security expectations, data model, API research, and design decisions are in [`docs/planning/`](docs/planning/). Ordered database migrations are in [`database/migrations/`](database/migrations/). Provider content is fetched live and attributed; search coverage and provider limitations are disclosed in the UI.
