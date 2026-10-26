# Adhkar
Status: IN_PROGRESS. Native sequential counter and complete category index implemented; current build/device acceptance recorded separately in TEST_REPORT.md.

## Purpose
Source-attributed daily adhkar with an enduring counter.
## Existing implementation and known defects
knowledge.js reads rn0x JSON, persists counters locally and posts aggregate completion. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Native category/search/detail/counter/reset; persist account-specific progress; use source counts only; make completion event retries idempotent.
## Android screens
categories, search, one-entry counter, progress.
## Administration controls
reviewed entries/categories/source/count publication CRUD.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/adhkar; /me/adhkar-progress`.

Implemented increment: GET `/adhkar/categories` returns every published, reviewed category and its item count independently of content pagination. GET `/adhkar?category=...&page=...&per_page=20` supplies one category at a time. Native UI displays one entry, persists each tap locally, and advances after the final actual tap. Next/previous controls cross page boundaries. A source with no repeat count is displayed without a fabricated target or completion event. Reaching the end of a section is distinguished from reading every entry, since users can skip manually. Reset and conflict resolution remain explicit. Counter I/O errors show feedback without losing already persisted progress.
## Data requirements
adhkar_items; new keyed completion/progress state.
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
Process restart preserves count; reset/next/category/account isolation; sourced-count validation. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
