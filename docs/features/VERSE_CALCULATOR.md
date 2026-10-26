# Verse Calculator
Status: initial approved specification. Existing behavior below is static-code evidence; native replacement is not yet verified.

## Purpose
Calculate exact inclusive Quran ranges and targets without network.
## Existing implementation and known defects
Missing; existing backend counts invalid and must be replaced first. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Use shared validated global one-based index: end=start+target-1 and count=end-start+1. Presets 10/100/1000; no automatic wrap; offer remaining count on overflow.
## Android screens
target/range tabs, reference/result, save/open/start actions.
## Administration controls
canonical metadata version/health only.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/quran/metadata (bootstrap); local domain calculation`.
## Data requirements
114-surah metadata; optional local planned range.
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
First/last verse, cross-surah, 10/100/1000, invalid/inverted/end overflow; all global indices round-trip. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
