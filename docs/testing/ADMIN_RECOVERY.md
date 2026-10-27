# Owner recovery integration — task 17.6

Base: `65e8296e5b890222b1c448f18a925abfd2bf880f`. Source commits: `7f23e72f15c152f6c8b6beff1c6253f1cb00eff7`, `8a06c84c1d021ad26b0f86791d78fc1e74adb1f4`. Classification: KEEP WITH FIXES / PARTIAL. The isolated session, download route and authenticated-session refresh were already incorporated as compatibility fixes in 17.1; preserve the newer centralized denial audit and session regeneration rather than copying the weaker source refresh.

Fetched source head: `7392e93b088990c3478cf32197781f831eb10f83`; merge base `5e46b37cfdba6ed64c27273f0aaafa1ce3eaaa4e`. Existing clean review worktree `C:/xampp/athar-parallel` updated to that detached head. Latest source `public/index.php` fails PHP syntax at line63 (unexpected `style`); source task17.6 boundary `8a06c84:public/index.php` passes PHP syntax. Latest source backup helper passes syntax. No full-branch merge performed. Later source work remains review pending.

## 17.6.1 — restore correctness and identity revocation

Fixed an exclusive-create destination error: `tempnam` creates a file, but the decryptor requires a nonexistent destination. Restoration now removes that reserved file before exclusive creation, and always cleans its plaintext afterward. Reject expired artifacts even when their verification timestamp is recent.

All restored account session versions advance beyond both current and snapshot maxima, preventing old public/Admin cookies from becoming valid when a snapshot rolls versions backward. Unsigned version overflow is rejected before mutation. Restore returns the actual recovered owner ID and the route establishes its independent Admin session using that ID, including when the current owner was absent from the snapshot. Current owner credentials survive; an obsolete snapshot suspension is cleared. Retained backup/settings actor references without a surviving account are cleared. Audits use the restored owner identity.

Validation: `scripts/test-backend.ps1` PASS:20,711 assertions, including20 new isolated recovery assertions. Real migrations, encrypted archive creation/decryption, private reading-data restoration, registered PDF byte restoration, all-user version rotation, missing-owner recreation, credentials, settings retention, expired/corrupted/wrong-key rejection, overflow refusal, transaction/FK cleanup and plaintext cleanup. Dedicated owned MariaDB runner only; generated disposable schema; unique test PDF/encrypted files removed. No real restore/reset, live account changes or outbound mail. Initial test referenced the wrong settings column; corrected to the actual `setting_value` contract before the passing run. PHP syntax and diff checks pass. No migration or Android contract change; physical Android gate does not apply to this increment.

## Remaining acceptance

17.6 remains PARTIAL. Local reset safety, HTTP create/grant/single-use download/restore/reset journeys, bilingual recovery controls and actual browser acceptance remain open. Asset installation after DB commit still needs failure/retry review. The source cursor stays `5ad55efbb9d06d5c89395cf663c70da015b90257` until those gates are resolved or explicitly recorded as pending. WP17 remains5/6 accepted; overall estimate remains49% (48.78 weighted, ±10pp). WP02.8 retains its deferred checkpoint.

## 17.6.2 — local reset transaction and recoverable-backup gate

Base:`bf6b0b808e88cb05ffee7c463d5859d9859a8e81`. Reset now rechecks local/development environment inside its service, a current unexpired artifact, encrypted file integrity, the active decryption key and matching database/schema before deleting data. Deletes run in one transaction, preserve the active owner/migration ledger/artifacts, invalidate the owner's old cookies and clear dangling artifact-creator references. Cleanup is limited to registered book/scholar asset keys; unrelated files are preserved. Maintenance locking and password/literal confirmation remain at the HTTP boundary.

Validation:`scripts/test-backend.ps1` PASS:20,732 assertions, including41 isolated recovery assertions. New evidence includes production/expired/missing/wrong-key reset denials, retained accounts, injected BEFORE DELETE trigger failure after earlier deletes (full rollback/FK/transaction/asset preservation), actual successful reset, owner/version/schema/artifact retention, registered-vs-unregistered file isolation, lock release and actual post-reset restoration of private records/PDF/portrait bytes. No production schema or existing development files were reset. Initial expanded37-check run also passed; final41-check run adds transactional failure evidence. Logs:`storage/engineering/admin-recovery-reset-backend.log`. No migration/native changes.

Task17.6 remains PARTIAL pending HTTP/bilingual/browser and asset-install failure acceptance. Cursor unchanged. Restore increment17.6.1 pushed and remote SHA verified as`bf6b0b808e88cb05ffee7c463d5859d9859a8e81`.
