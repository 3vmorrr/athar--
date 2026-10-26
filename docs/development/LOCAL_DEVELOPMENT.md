# Local development stack

Read-only schema gate: `php scripts/check-development-schema.php` checks the configured isolated loopback3309 migration ledger and tables. It never repairs or applies migrations. Checkpoint26 October observed14 versions/33 tables OK. Keep the corrupt original untouched; `scripts/start-local.ps1 -LoopbackOnly` can restart the healthy local stack after a reboot.

Start the existing stack from the repository root:

```powershell
.\scripts\start-local.ps1 -LoopbackOnly
```

The ignored `storage/local-runtime.json` points to the validated private MariaDB data directory on3309. The original inconsistent XAMPP data files are preserved; startup never repairs, resets or migrates them. Existing listeners are checked before reuse. No unrelated process is stopped.

Stable services:

| Service | Local address | Scope |
|---|---|---|
| Native development API | `http://127.0.0.1:8002/api/v1/` | Existing healthy development database |
| Existing website/admin | `http://127.0.0.1:8081/admin` | Legacy admin; not acceptance of the independent dashboard |
| Native integration fixture | `http://127.0.0.1:8004/api/v1/` | Explicit guarded test router/generated test schema only |
| Independent Admin | Not implemented yet | W01 remains open; do not substitute a legacy page and report it complete |

The fixture router is `tests/native_router.php`; its generated credentials remain in ignored local state. Never expose this test listener outside loopback. Authenticated tests mutate only generated fixture records. Integration tests use fake mail and do not dispatch real campaigns.

Check `/api/v1/health`, database connectivity and actual page responses before reporting a running stack. Inspect process command lines before stopping an owned listener. Keep services available during physical testing. Database schema changes require the documented migration/backup procedure, not automatic startup repair.

See [physical Android workflow](ANDROID_PHYSICAL_DEVICE.md), [runtime safety](../deployment/LOCAL_RUNTIME.md) and [test tracker](../progress/TASK_TRACKER.md).

Migration014 uses `scripts/apply-development-events.php` with explicit process-only migration credentials and a named application/fixture target. It checks the exact known healthy data directory and pending version, writes a logical dump outside the web root, verifies its checksum/footer/table coverage, and checks the three new tables. It never runs from HTTP/startup; full backup restore testing remains separate.
