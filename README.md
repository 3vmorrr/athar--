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

Open <http://127.0.0.1:8000/> and create an account. Promote the first administrator from a local terminal with `& 'C:\xampp\php\php.exe' bin/promote-admin.php admin@example.com`; the command requires typed confirmation. Visitors can read without an account. Accounts support private Quran bookmarks and position, a khatmah plan, book/lesson favorites, approximate lesson resume, JSON export, and account deletion. Administrators can curate scholars, series, lessons, books, and adhkar. Uploaded PDFs are stored outside the public web root and only downloadable when an explicit rights basis is recorded.

## Product and source documentation

Product scope, security expectations, data model, API research, and design decisions are in [`docs/planning/`](docs/planning/). Ordered database migrations are in [`database/migrations/`](database/migrations/). Provider content is fetched live and attributed; search coverage and provider limitations are disclosed in the UI.
