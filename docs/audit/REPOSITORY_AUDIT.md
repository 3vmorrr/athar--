# Repository audit — 2026-10-24
## Scope and evidence
Inspected the Git baseline, all nine migration files, PHP helpers/mailer/backup/front controller/router, PHP page templates, browser application/search/translation/recitation workers, model notices and manifests, planning documentation and the existing backup test. [File inventory](FILE_INVENTORY.md) and [source outline](SOURCE_OUTLINE.md) provide reproducible declaration/route evidence. Private storage and credentials are excluded. Binary model inference was not runtime-verified during discovery.

## Existing architecture
The website is server-rendered PHP with vanilla JavaScript and CSS. `public/index.php` owns routing, authentication, account mutations, provider proxies, administration and SQL. `app/helpers.php` owns connection/session/CSRF/upload/validation helpers. `app/mailer.php` supplies encrypted settings and Gmail SMTP. `app/backup.php` supplies owner-only encrypted archives and restoration. There is no native Android application, mobile bearer authentication, versioned API, structured offline mobile database or standalone administration deployment at baseline.

## Runtime observations
PHP CLI is 8.0.30; MariaDB is 10.4.32. MySQL was stopped at first inspection and was started locally without modifying application data. The nine migrations are present in the database ledger. Android Studio/JBR, Android SDK platforms through 36, build-tools 36.0.0 and Gradle 9.1/9.2 are installed. No ADB device was connected at discovery; API 26 and API 36 virtual-device definitions exist. A successful APK build or device journey has not yet been established.

## Critical findings
1. Backend `quran_ayah_count()` contains only 109 entries, totaling 6213. Provider metadata reports 114 and 6236. Index-dependent validation, bookmarks and future Night Prayer calculations require correction before migration.
2. The full-backup builder includes legacy book files but omits `scholars.image_key` binaries. An apparently successful backup cannot currently restore every managed image.
3. Authentication throttles are predominantly PHP-session counters, allowing cookie reset to bypass them. A shared database-backed rate limiter is needed for mobile and web identity actions.
4. Verification OTPs are plain SHA-256 hashes over a six-digit space. A server-keyed hash and transactional attempt accounting are needed. The email verification handler does not increment token `failed_attempts` on an incorrect code.
5. Account registration lacks password confirmation. Unverified web users have a restricted account screen, but privileged-role verification exceptions and stale sessions require an explicit migration policy.
6. Public router static allowlist omits audio formats. Browser sound/media assets need route and MIME verification.
7. Local PHP/MariaDB versions are historical development dependencies, not supported production targets. No host has been validated end to end.

## Migration strategy
Retain legacy routes and assets. Extract shared identity/domain/provider services behind an additive API. Introduce independent mobile tokens without weakening cookie CSRF. Introduce additive Night Prayer tables and a separate administration shell. Validate on a disposable database before applying migrations to the existing local database. Preserve any pre-existing work and do not remove legacy functionality until its replacement is verified.
