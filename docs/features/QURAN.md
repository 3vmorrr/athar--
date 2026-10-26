# Quran
Status: IN_PROGRESS. Canonical text/index/search and native offline storage have executed tests; explicit encrypted reading-position resume/conflict/recreation now has Arabic/English native acceptance; complete audio/tafsir/background-sync/device acceptance remains open.

## Purpose
Read, search, listen to and navigate source-attributed Quran content.
## Existing implementation and known defects
The recorded baseline used quran.php + app.js with AlQuran Cloud/MP3Quran and an invalid109-surah count table. That count table has since been replaced by the canonical114-surah/6236-ayah index. The audit register preserves the original defect; it is not an assertion that the current index remains invalid.

Implemented native scope: bundled original attributed Tanzil text, offline Arabic search, surah/page/juz/hizb navigation, configurable Quran font, bookmarks and separate read/listen/tafsir/radio entry points. Source-backed MP3Quran and Quranpedia adapters exist. The canonical text must not be regenerated or paraphrased. Remaining work includes complete native listening/timing journeys, durable reading-position resume, richer tools and release/accessibility verification. Consult TEST_REPORT.md for actual executed checks rather than treating all source paths as accepted.
## Required behavior
Correct all 114 surah counts; share one-based indexing; validate page/juz/hizb mappings; keep read/listen/tafsir/search separate; support bookmark/position/font preferences.
## Android screens
tools, surah list, reader, tafsir, search, reciter/audio controls, daily ayah.
## Administration controls
provider/source health and edition settings.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/quran/surahs; /quran/position; /quran/bookmarks; /quran/search; /quran/ayahs`.
## Data requirements
quran_positions; quran_bookmarks; canonical metadata asset.
## Input validation
Validate ownership, known enum values, numeric/date/text bounds and referenced published records on the server. Validate locally for fast feedback; client validation never replaces server policy. Preserve source metadata and explicit user intent.
## Loading, empty and error states
Show real loading state with cancellation, an honest no-data state and actionable retry. Distinguish unavailable provider, unauthorized request, invalid input, stale cache and offline mode. Keep user input after failure; do not show a fake completion.
## Offline behavior
Cache only permitted data; account-partition local state and transactional outbox follow the offline/sync contract. Operations needing provider/mail/server authorization show their actual availability. No invented Quran, tafsir, prayer-time or analytics values.
## Security and privacy
Apply verified authentication and resource policy; scope every private query to actor. Secrets remain server-side/Keystore; no microphone upload or worship leaderboard. Destructive mutations require explicit user action and safe server checks.
## Accessibility and localization
Arabic default with proper RTL; complete English resources and LTR. Label controls/status, use 48 dp touch targets, preserve text scaling and Quran shaping, give non-color status descriptions and reduced motion.
## Performance
Use IO dispatchers, bounded payload/list sizes, cancellable requests, indexed queries and lazy stable keys/content types. Record measured performance rather than claiming it. Large model/audio downloads are explicit and limited.
## Test cases and acceptance
All 6236 references round-trip; surah/page boundary and stale-audio request tests. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
