# Parallel Admin track

Date: 2026-10-26  
Implementation branch: `parallel/admin-foundation`  
Integration target: `codex/android-ecosystem`

This track exists to execute Work Package 17 in parallel while the main Codex
track continues Work Package 2 and subsequent Android/offline work. The files
and dependencies were selected specifically to avoid overlapping the current
Android synchronization implementation.

## Reserved tasks

- Issue #1 — 17.1 Independent Admin authentication
- Issue #2 — 17.2 Independent Admin bilingual shell
- Issue #3 — 17.3 Admin authorization matrix

Do not work ahead of dependency order. 17.2 starts only after 17.1 has passed
its runtime/security acceptance. 17.3 starts only after 17.2 is accepted.

## Conflict guardrails

The parallel track must not modify Android offline/sync repositories, protected
local reads, WorkManager scheduling, Quran/counter ownership dispatch, or Night
Prayer outbox/conflict work unless an explicit merge reconciliation is required.

The main Codex track should avoid the files reserved by the active Admin task
until the task is merged:

- `admin/`
- Admin-only routing/session changes in `public/index.php`
- Admin view/login assets
- Admin-specific HTTP acceptance tests

Shared progress queue files remain owned by the main Codex track. This parallel
track records progress here and in GitHub issues to avoid queue merge conflicts.

## 17.1 — Independent Admin authentication

Status: **PARTIAL — implementation is present; executable HTTP fixture gate is pending.**

Implemented:

- dedicated PHP session cookie `ATHARADMIN`
- cookie scope restricted to `/admin`
- SameSite=Strict, HttpOnly and existing HTTPS-aware Secure behavior
- independent `/admin/login`
- independent `/admin/logout`
- shared identity/password verification without issuing Android/API bearer credentials
- generic role-denial response for non-Admin accounts
- explicit Admin/Owner role gate
- session ID rotation on successful authentication
- 8-hour absolute and 30-minute idle Admin session limits
- live role/suspension/`session_version` revalidation on each protected Admin request
- CSRF token rotation after successful login
- bilingual Arabic/English login view
- explicit Admin logout control on the existing dashboard body
- no public website account session is reused as Admin authorization

### Acceptance test added

`tests/admin_auth_http_test.py` covers:

1. isolated Admin cookie name/path,
2. English login rendering,
3. missing-CSRF rejection,
4. regular-user role denial with generic feedback,
5. Admin login success,
6. session ID rotation,
7. no Admin-auth leakage to the public root,
8. `session_version` revocation,
9. fresh reauthentication,
10. isolated Admin logout,
11. denial after logout.

The test uses only the disposable native fixture database and fake accounts.

### Local execution blocker in this environment

The current execution container cannot resolve `github.com`, so the branch
cannot be cloned into the local PHP/fixture runtime here. No runtime PASS is
claimed.

Before closing issue #1, execute the normal fixture gate on the development
workstation:

```powershell
python tests/admin_auth_http_test.py
```

and PHP syntax checks for the changed PHP files. Then verify manually in a
browser:

- Arabic RTL login
- English LTR login
- narrow mobile width
- desktop width
- keyboard-only focus
- invalid credentials
- normal-user denial
- Admin/Owner login
- logout
- session-version invalidation
- suspended Admin invalidation

Only after those gates pass should task 17.1 be marked COMPLETE and task 17.2
begin.

## Integration policy

Before each push/review, compare this branch with `codex/android-ecosystem`.
If the main branch has advanced, preserve its latest logic and reconcile rather
than overwriting files. Merge/cherry-pick only after the task-specific
acceptance is recorded.
