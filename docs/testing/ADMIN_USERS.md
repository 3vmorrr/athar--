# Admin user management acceptance — 17.4

Base:`2d87b57de49981130675d35f5350495514d66c69`. Parallel source:3fbbaaf4463f47b4d1c9aa6e59669c8c22f7f7fc and834af23dbe9f01bcfc8ec1f0eeb943dc3028feed. KEEP WITH FIXES; no textual conflicts. Target authorization locks, rollback auditing and the independent shell from main remain intact.

Source adds global searchable account lookup and transactional suspension/restoration. This integration accepts those flows alongside existing profile/role/deletion controls. No migration or Android/API response contract changes.

## Corrections

- Escaped SQL LIKE wildcard characters with an explicit escape character; `%`/`_` remain literal user input. Bound80-character string input, prepared parameters and numeric ID lookup retained. Removed the client-side current-page filter from the server search form so it cannot hide ID/global results. Empty results are rendered by the server; filtered paging keeps its query.
- User control visibility reuses the centralized target predicate. Source suspension locks/revalidates the target, revokes credentials, retains private progress and makes repeated state changes no-ops. Existing profile/role transaction fixes survive.
- Localized user tools/status/validation/confirmation text and feedback into Arabic/English without translating stored names/identifiers. Marketing consent remains user-controlled. Actual API sign-in is included in the operational last-sign-in field, labeled UTC.
- Locale/theme switching retains filtered account/page context. Error/status feedback is focused after page load and the next frame, preventing fragment restoration from undoing focus. Actual failed reauthentication focuses its visible English error. Content-derived asset versions prevent stale Admin CSS/JS after updates.
- Added safe wrapping,44px disclosure targets and compact role chips for narrow layouts. No public website theme/layout change.

## Evidence

`python tests/admin_user_management_http_test.py` PASS:62 user management checks plus120 prior HTTP authentication/shell/action checks (182 total). All mutable actors/lookup rows are generated inside the existing guarded fixture schema; no real account, email or provider send.

Coverage: wrong-password/self/owner/peer denial; effective suspend/restore with one version/audit change; repeated no-ops; actual access/refresh/public cookie revocation; owner-driven Admin-cookie revocation; old credentials remain dead after restoration; fresh sign-in works; retained Quran position; invalid/reserved/duplicate profile fields; email change clears verification/marketing consent and revokes tokens; explicit generated-user deletion with dependent progress/favorite removal and invalidated credentials. Sixty generated matching accounts prove50/10 global pagination without overlap; ID/email lookup, preserved context, wildcard/injection/markup literals and visible empty results pass.

`scripts/test-backend.ps1`:20,683 assertions PASS; PHP syntax, Python compilation and JS syntax PASS. Ignored logs:`storage/engineering/admin-users-http.log`,`admin-users-backend.log`. One initial backend run encountered an Argon password memory-allocation error under host memory pressure; its log is retained as `admin-users-backend-memory-failure.log`. A fresh ordinary rerun passed with unchanged production password costs. A test initially expected an underscore-only search to be empty despite fixture usernames containing underscores; the assertion was corrected to a literal two-underscore query. An initial localization transformation corrupted PHP field keys; HTTP acceptance caught500, the transformation was corrected using PHP/HTML boundaries, and final runs are clean. These failures are not counted as passes.

Actual browser: Arabic desktop1440 and Arabic dark/English light phone360; exact filtered account retained through language/theme; no horizontal overflow (phone client345/scroll345); English wrong-password error is visible and receives focus after load. Fake credentials only; browser did not submit deletion/suspension/role mutation. Ignored captures:`admin-users-desktop-ar.png`,`admin-users-mobile-ar-dark.png`,`admin-users-mobile-en-error.png`. Temporary viewport reset. No physical Android gate applies to this web-only implementation.

This does not accept privacy-bounded analytics/audit17.5 or owner recovery17.6. Historical private-stat/detail queries remain for the immediately following17.5 reconciliation and must not be treated as final approved Admin privacy behavior. The complete platform is not release-certified by this task.
