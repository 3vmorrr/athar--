# Original database incident and recovered development runtime

## Current status — 2026-10-25

The development runtime is restored on a separate, normal-mode MariaDB instance on loopback port3309. A verified539-file cold snapshot and all recovery trials remain outside the web root. A full22-table logical export was imported into a fresh data directory; all row counts and canonical row-set hashes matched the readable working copy, all22 tables passed CHECK TABLE, and21 foreign-key relationships had no orphan rows. Both existing accounts and the video-progress row were retained. Additive migrations010-012 were applied only to that independent database. The original corrupt files remain unchanged.

Read [local runtime and recovery evidence](../deployment/LOCAL_RUNTIME.md) for the startup command, validation limits and deployment gates. The initial incident below remains as the audit record; it is no longer a blocker for the local website. This recovery does not certify historical transactions preceding the crash or repair the original tablespaces.

## Initial incident

During the audit, the original XAMPP MariaDB first answered read-only version/ledger queries, then shut down during an integration runner initial SELECT of the users count. This happened before that runner created a test schema or applied any migration. Its mysql/data/mysql_error.log reports InnoDB log sequence numbers in the future and potentially mismatched/copied tablespaces and redo logs.

No original data repair, redo deletion, forced recovery, table deletion, restore or migrations010-012 have been executed on the original data. Recovery-mode work was restricted to independent copies. The website now uses the validated logical import, rather than the original tablespaces.

Tests use separately initialized data directories under ignored storage/engineering and generated athar_test_* schemas. scripts/test-backend.ps1 verifies the connected data directory before setup, binds loopback, drops only its generated control schema and stops only its own process.

Recovery must start with a verified cold copy of the entire original data directory and configuration. Salvage must operate on a copy and validate exports before any deliberate replacement. Never delete redo logs or overwrite original data just to start a demo. Do not commit database contents or raw credentials.
