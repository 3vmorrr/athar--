# Independent Admin authentication acceptance — 17.1

Main integration base: `224a18652ff5cc5abfdbbd25d9f1da657479de8b`.
Parallel inspected head: `29028f5d7cde176b92f23f0aee27153628942d23`, retained at `C:/xampp/athar-parallel`.
Source commits: c599fd4, 828cbc9, c4ff348, 0173b00, e2c2d43, f5c7bf2, 7fd87ad. Classification: KEEP WITH FIXES. The intervening eb24c1f documentation is superseded by the canonical integration map. No merge conflicts; compatibility fixes were required in existing owner actions.

## Accepted behavior

`ATHARADMIN` uses `/admin`, HttpOnly and SameSite Strict, with Secure conditional on HTTPS. Shared identity/password checks grant only live admin/owner roles; no unused API bearer session is created. Session fixation/CSRF rotation, idle30-minute and absolute8-hour expiry, live suspension/demotion/session-version invalidation and isolated logout are enforced. Public PHPSESSID and its signed-in actor survive Admin login/logout.

Owner downloads now remain within `/admin/backup-download`; ordinary admins are denied. Restore/reset refresh the Admin actor rather than creating public-session identity. Expiry clears retained backup grants. This adopts only compatibility from later source7f23e72; recovery17.6 is still pending and no restore/reset/download was executed.

The standalone bilingual emerald/warm login keeps password-manager autocomplete, paste, accessible localized password visibility and reduced-motion handling. Input borders were strengthened and English headings balanced. No public layout is loaded by login. The legacy authenticated dashboard remains until17.2; action-level matrix/redaction are17.3/17.5, not accepted here.

## Verification

- `python tests/admin_auth_http_test.py`:49 actual HTTP assertions PASS, generated fixture accounts only, validated disposable database name and actual server datadir. Covers login denial, CSRF, cookie properties, fixation, actor separation, logout, expiry/revocation, rate limiting, owner-route denial, locale/markup and real CSS/JS content.
- `scripts/test-backend.ps1`:20,000 isolated assertions PASS (6382 domain,13352 Quran,9 backup assets,15 audio,8 tafsir,43 identity,44 catalog,35 learning,80 upcoming,32 Night). Existing15 migrations applied on disposable schema; no new migration.
- Changed PHP syntax checks, Python compilation and `node --check public/assets/js/admin-auth.js` PASS.
- Actual browser: generated Admin login/dashboard/logout, Arabic RTL/English LTR login and password visibility. Clean mobile-width captures: ignored `storage/engineering/admin-auth-login-ar.png` and `admin-auth-login-en.png`. Wider dashboard/keyboard/theme acceptance belongs to shell17.2. HTTPS Secure was reviewed in source; no live TLS environment was tested.
- Fixed the source HTTP test's invalid SQL quoting. Fixed test-router static-file passthrough: the nested router's `false` result must be returned, otherwise CSS/JS falsely answer200 with empty bodies. Asset-body assertions prevent that false acceptance.

Logs: ignored `storage/engineering/admin-auth-expanded-http.log` and `admin-auth-backend.log`. No physical Android/build gate applies: no Android code/resources/contracts changed. No email/campaign or destructive owner action ran. Source-only later work receives no acceptance credit.

The durable task map is [PARALLEL_INTEGRATION.json](../progress/PARALLEL_INTEGRATION.json). Advance its cursor only across accepted or explicitly superseded source commits, retaining later interleaved corrections.
