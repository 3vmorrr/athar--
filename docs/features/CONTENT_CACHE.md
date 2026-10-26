# Disposable content cache bounds

Status: implemented and focused storage gates tested26 October2026. Full phase2/release acceptance remains open.

The encrypted localized HTTP response cache is disposable. Quran resume, adhkar counters/completion IDs, lesson checkpoints, Night Prayer rows/outbox/cursor and exemption periods are authoritative private state and never enter this eviction policy. Existing Room schema/version1 is retained; only DAO queries/transactions are added. No migration or reset is needed.

Per actor and API-environment database, at most200 Arabic/English response documents and8MiB of encoded encrypted payload are retained. A single document above1MiB is served online without retaining a stale older copy under that key. Metadata inspection avoids decrypting every page during cleanup. Transactional store/prune prevents concurrent writes from exceeding the retained bounds after a completed store. These are application payload bounds, not a guarantee that SQLite file/WAL allocation immediately shrinks.

Maximum fallback age is seven days. Expired/future-timestamp documents are discarded before fallback; offline misses use existing truthful error/retry UI. Freshness is based on absolute timestamps, independent of locale/timezone; a backwards clock can invalidate disposable downloads rather than present them indefinitely. This is insertion-age bounded retention, not LRU. Localized key normalization supports the approved Arabic/English choices and locale tags.

An authoritative404 removes the cached document; authentication/access/validation errors cannot resurrect it. Other eligible network errors may return an unexpired encrypted fallback marked offline. Undecryptable or malformed disposable fallback is removed and the original network failure is returned, without exposing cryptographic text. This differs intentionally from unreadable authoritative private progress, which must be retained for recovery.

Verification gates: deterministic count/byte/age/future/namespace tests; real Room transaction tests proving other actors and private progress survive; encryption/offline/corruption/removal behavior; existing upcoming-cache and Arabic/English UI regression. These bounds do not yet implement book/audio file downloads, private-data retention settings or a storage-management screen.

Initial candidate:42 JVM cases/build/test APK/lint PASS (`content-cache-build.log`,3m36s); API27 Room/content/upcoming-cache cases3/3 PASS (1.431s). The stronger final owner-change assertion is recorded separately below. Returning either a fresh or cached HTTP document must recheck the captured actor, including failures arriving after account switching.

Final owner-change source:42 JVM/build/test APK/lint PASS (`content-cache-owner-build.log`,3m20s). API27 storage/cache/unique-scheduling regression15/15 PASS (17.095s, `content-cache-physical-final.log`), including account switch during a failed HTTP response without returning/deleting the former actor's retained page. Existing encrypted application databases open normally after the non-destructive APK update.Arabic2/2 (22.534s) and English2/2 (21.784s) reader/counter hardware journeys pass.292 paired resources pass; backend/schema unchanged (read-only recheck15 migrations/34 tables and main API HTTP200).
