# Database schema

Subsequent26 October context increment: additive015 creates `lesson_event_context` (one row per event, localized optional course/topics, nullable book FK and paired page references). It contains public editorial content only. Event deletion cascades; book deletion clears its FK, and the serializer suppresses orphan page references. Context writes share the event revision transaction. The healthy development and generated fixture targets now have15 versions/34 tables, checked after separate private logical backups; the corrupt original is unchanged. Earlier14/33 evidence below describes the checkpoint before this increment.

Checkpoint26 October2026: the explicit healthy development runtime has14 matching migration versions and33 tables, all passing read-only CHECK TABLE. This includes existing legacy tables as well as new additions; migration count and table count are different measures. The original corrupt database is unchanged. Full encrypted archive restore remains a separate open acceptance gate.
Baseline migrations 001–009 define users, bookmarks/positions/khatmah, scholars/series/lessons/books/reviewed adhkar, favorites/video progress, audit, OTP, owner archives/grants, preferences/username/activity, encrypted settings/comments, email campaigns/events and scholar image keys. Existing IDs, constraints, password hashes and user records remain.

Implemented additive migrations, tested on disposable schemas; the validated healthy development copy also has010-014 after private backups. The corrupt original is unchanged:

- 010: hashed API sessions/refresh families, verification scope, expiry/session version, shared keyed rate buckets, suspension and OTP hash scheme. Explicit DATETIME expiry prevents MariaDB implicit TIMESTAMP auto-update from expiring codes when attempt counters change.
- 011: versioned Quran positions, series favorites, idempotent activity UUIDs and per-user adhkar progress storage.
- 012: private Night Prayer UUID sessions/context, encrypted notes, revisions, minimal replay records, cleared deletion tombstones, monotonic change cursor and nonsensitive preferences. No server exemption table.

- 013: one-level first-party discussion replies, idempotent submissions and audited revision-checked moderation.
- 014: lesson event status/UTC schedule/revision/provenance, optional lesson translations and durable public schedule-change facts. No private worship audiences or dispatch-on-save. Applied explicitly to the generated fixture and healthy development copy after private logical dump checksum/table-coverage checks; full restore acceptance is not implied.

Original migrations001-009 and existing user data are preserved. New CHECK constraints are not a substitute for service validation. These additions have not been applied to the original corrupt development database.

Store instants as UTC with explicit timezone/context fields where needed. Night date is fixed local Maghrib date, not UTC date. Index ownership/cursor/night and publication/order/association. Avoid redundant totals without invalidation. Migrations run explicitly, recorded in the existing version ledger, never implicitly during HTTP. Test on a disposable schema before changing the existing local database; use forward-recovery notes for nonreversible legacy tables.
