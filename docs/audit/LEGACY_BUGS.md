# Legacy defect register
| ID | Evidence | Impact | Priority | Status |
|---|---|---|---|---|
| L01 | `app/helpers.php:quran_ayah_count` has 109 entries | Invalid surah/ayah validation; unsafe range math | P0 | Open |
| L02 | `app/backup.php:build_backup_archive` only collects book assets | Scholar portraits absent after recovery | P1 | Open |
| L03 | `public/index.php:allow_session_rate` | Resetting cookies bypasses rate limits | P0 | Open |
| L04 | verification lookup does not increment `failed_attempts` | OTP attempts tracked inconsistently | P0 | Open |
| L05 | SHA-256 OTP without a server secret | Database disclosure permits cheap offline guessing | P0 | Open |
| L06 | register form/backend have no confirmation field | Mistyped passwords accepted | P1 | Open |
| L07 | static router lacks audio extensions | Static alert audio may fall through to HTML | P1 | Open |
| L08 | `knowledge.js` retains a Quran search implementation while Quran loads `app.js` | Duplicate implementations drift; not evidence of duplicate active requests | P2 | Open |
| L09 | web I18N uses DOM text replacement | Dynamic/long admin content is incompletely localized | P1 | Requires redesign |
| L10 | `account_daily_stats` increments on some repeated events | Totals may count retries rather than unique events | P1 | Open |
| L11 | MariaDB `SHOW COLUMNS auth_tokens`: expires_at has implicit ON UPDATE CURRENT_TIMESTAMP | One wrong OTP update expires the token immediately | P0 | Repair in migration 010; isolated regression test |

No row is closed on the basis of a plan. Append test evidence and changed files when repaired.
