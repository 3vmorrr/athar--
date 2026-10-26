# Reading Pace
Status: initial approved specification. Existing behavior below is static-code evidence; native replacement is not yet verified.

## Purpose
Estimate recitation duration from the user's actual measured sessions.
## Existing implementation and known defects
Missing; no genuine pace data exists. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Pauseable timer persists accumulated monotonic duration across lifecycle; save measured duration with verse count; weighted seconds/verse; exclude zero/unmeasured entries and show approximate estimates.
## Android screens
active timer, save session, pace insight.
## Administration controls
no individual pace access.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `session duration field; derived /night-prayer/analytics`.
## Data requirements
measured_seconds/measured_verse_count and timer origin on device.
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
Pause/resume, process recreation, clock change, zero count and weighted totals; no fabricated default pace. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
