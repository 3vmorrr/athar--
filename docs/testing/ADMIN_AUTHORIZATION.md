# Admin action and target authorization — 17.3

Integration base:`9f72ec3f7247edb3ecd271c8e1b86247c503d6b7`. Parallel source:8eef7909b6bc35b01c006e66e3b49702e9837bf5,3443e80076be3bb18c1c9a9c0f5d8332129a531b,81b2996522f869440c6b94c5dec270be8d1bdb48. KEEP WITH FIXES; no textual conflicts. Source supplied the centralized capability/action matrix and target checks plus capability-aware services/recovery navigation.

## Accepted boundaries

Owners manage ordinary users/admins; admins manage ordinary users only. User-management flows cannot edit/delete themselves or owner accounts. Only owners change user/admin roles; owner role cannot be assigned or modified through this flow. Unknown actor roles, target roles, operations, capabilities and action kinds fail closed. Services, recovery and public Night Prayer defaults are owner capabilities. Direct POST authorization is mandatory regardless of hidden navigation.

The owner download endpoint now shares the recovery matrix with CSRF checked before dispatch. Unknown posted action text is never stored verbatim in audit logs. Target denials explicitly roll back row transactions before durable audit insertion, return localized403 and preserve user data. No secrets/private payloads are added to audit metadata.

Profile and role updates now lock the target row in a transaction through authorization/write/audit; this closes the concurrent user-to-admin promotion gap. Role changes increment session_version once; a repeated canonical role is a no-op. Live session checks reject the demoted retained Admin session. Existing password reauthentication remains.

## Evidence

- `php tests/admin_authorization_test.php`:683 capability/target assertions PASS. Explicit contract tables exercise roles, owner/self/unknown targets, invalid IDs, operations and action mappings. Included in the normal disposable backend runner.
- `scripts/test-backend.ps1`:20,683 assertions PASS (prior20,000 plus683 authorization). No migration or schema/API contract change; no native changes.
- `python tests/admin_authorization_http_test.py`:60 real HTTP action/target/rollback/concurrency assertions plus60 existing authentication/shell checks PASS. Newly generated fixture actors only; schema/datadir guarded by the shared fixture setup.
- Direct admin requests to services/defaults/roles/recovery and unknown/10,000-character action are403; missing CSRF419. Owner/admin/self target edits/deletions are forbidden and snapshots unchanged. Positive generated-user profile update and owner-managed peer update/demotion succeed; no-op role/revocation verified.
- A second PHP process holds a real row lock, promotes a generated user and commits after two seconds. The concurrent HTTP profile request waits at least one second, re-reads the new admin role, returns403 and preserves the original profile. Its denial audit remains after rollback. No permanent user deletion or real owner recovery/service operation ran.
- Actual browser: English Admin receives no services panel/recovery navigation; Arabic Owner receives services/recovery and permitted role controls. Ignored screenshot:`storage/engineering/admin-authorization-owner-ar.png`. The17.2 responsive/accessibility shell is preserved. No physical Android gate applies.
- PHP syntax, Python compilation and diff/security screening PASS. Ignored logs:`admin-authorization-http.log`,`admin-authorization-backend.log`.

One repeat initially failed because accumulated loopback Admin login attempts reached the real shared IP bucket. Test setup now resets only that exact IP bucket inside the validated generated fixture before the suite; identity rate-limit checks and production behavior remain intact. The final suite still proves generic rate-limited responses. An existing missing account theme preference emitted a PHP warning during auth regression; its fallback now reads a defined value and the same HTTP journey passes.

Scope limits: user suspension/tools17.4, privacy-bounded overview/audit17.5, owner recovery17.6 and later campaign/content functions remain separate acceptance tasks. Matrix entries for future actions establish permissions and do not certify those implementations. Database row transactions do not imply every future user-management workflow is already accepted.
