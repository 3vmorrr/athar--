# Recovered local development runtime

The existing original XAMPP data directory was preserved. A539-file cold copy was verified by SHA256 and kept outside the web root under the machine user local application-data recovery directory. Diagnostics identified one invalid tablespace header checksum in video_progress, while21 other table files passed the read-only checksum utility.

Recovery was performed exclusively on a working copy. Corrupt replication metadata in that copy was quarantined. A separate trial changed only8 checksum bytes in video_progress page0; no record payload bytes were changed. A later unused-area checksum problem remained in the physical source file, so that physical file is not used as the runtime. The current logical row could be read and a complete22-table SQL export succeeded. Raw snapshots, trial files, SQL exports and private environment backups remain outside Git and the web root.

The export was imported into a freshly initialized, independent MariaDB data directory. All22 table row counts and canonical row-set hashes matched the readable recovery working copy. CHECK TABLE returned OK for all22 restored tables, and21 foreign-key relationships had zero orphan records. Both existing users and the single video-progress record were retained. This proves the validated logical export/import result, not a repair of the original corrupt files or a guarantee about historical transactions before the original crash.

Only after validation, additive migrations010-012 were applied to the independent database. The local ignored `.env` now points to port3309 and a schema-scoped runtime user with SELECT/INSERT/UPDATE/DELETE privileges. Existing secret settings keys were preserved. The previous environment is backed up privately. The original XAMPP data directory remains unchanged. The emergency recovery-mode server was shut down; the active server runs normal mode0 on loopback.

## Start on this machine

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

The ignored `storage/local-runtime.json` selects the verified private MySQL config, executable paths and port. It contains no passwords. The script starts only missing servers and refuses to stop or replace a different application occupying a selected port. No migration, repair, reset or message delivery is implicit.

- Web preview: http://127.0.0.1:8081
- Android emulator API: http://10.0.2.2:8002/api/v1/
- API host on the development computer: http://127.0.0.1:8002/api/v1/health

Web8081 retains LAN preview binding; use `-LoopbackOnly` when starting new servers to bind only locally. Existing listeners are not silently rebound. Database3309 and emulator API8002 remain loopback only. HTTPS is still required for production and microphone access on real mobile browsers. A public compatible HTTPS deployment and signed release are separate, unfinished gates.

Observed HTTP checks after recovery: home, books, Quran and adhkar returned200; API health returned200 JSON; unauthenticated /me returned401 JSON. No real email was sent. This local runtime recovery does not certify every legacy feature or production readiness.

Do not start the original corrupted MySQL directory as the application runtime. Do not delete its files or the independent cold snapshot. Future schema changes require a privileged migration connection and a verified backup; the runtime account intentionally lacks DDL privileges.
