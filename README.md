# Athar

Arabic-first platform for Quran reading, Islamic knowledge, worship tools, and source-attributed content.

## Local setup

Requires PHP 8.0 or newer and MySQL/MariaDB with PDO MySQL, mbstring, and fileinfo. For a fresh local database, configure the connection and apply each migration once in filename order:

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

Open <http://127.0.0.1:8000/> and create an account. Promote a content administrator with `& 'C:\xampp\php\php.exe' bin/promote-admin.php admin@example.com`, or the platform owner with `& 'C:\xampp\php\php.exe' bin/promote-owner.php owner@example.com`; both commands require typed confirmation. Visitors can read without an account. Accounts support private Quran bookmarks and position, explicit opt-in merge of Quran progress saved in this browser, theme preferences, a khatmah plan, book/lesson favorites, approximate lesson resume, JSON export, password change/recovery, email verification, and account deletion. Administrators can curate scholars, series, lessons, books, and adhkar. Uploaded PDFs are stored outside the public web root and only downloadable when an explicit rights basis is recorded.

The owner-only backup feature requires `ATHAR_BACKUP_KEY`, a separate 32-byte key encoded as 64 hexadecimal characters. Generate one with `& 'C:\xampp\php\php.exe' -r "echo bin2hex(random_bytes(32)), PHP_EOL;"`; store it in the machine/service secret store and a separate recovery location, never in Git. The owner dashboard creates and verifies an encrypted archive, then issues a separate one-time download grant. Archives are retained privately for 24 hours, including after download, so an owner can restore from the same host. Restore requires a recent verified archive, a matching schema version, password confirmation, and the exact `RESTORE FULL BACKUP` phrase. A local reset is denied unless `APP_ENV` is exactly `local` or `development`; it also requires a recent verified archive, password confirmation, the typed environment, and `RESET ATHAR DATA`. Reset keeps the schema, migration ledger, owner account, and backup artifacts. Both operations use a MySQL maintenance lock; content and account writes receive a retryable 503 while it is held. Changing a password increments the session version to revoke other devices.

Email verification and password recovery use one-time database-hashed tokens. Configure `ATHAR_BASE_URL` with the public HTTPS origin and `ATHAR_MAIL_FROM` with a sender accepted by the host's mail transport. Recovery responses do not disclose whether an email is registered. Apply new database migrations once in filename order; migration `005` adds email verification and preferences fields.

## Product and source documentation

Product scope, security expectations, data model, API research, and design decisions are in [`docs/planning/`](docs/planning/). Ordered database migrations are in [`database/migrations/`](database/migrations/). Provider content is fetched live and attributed; search coverage and provider limitations are disclosed in the UI.
