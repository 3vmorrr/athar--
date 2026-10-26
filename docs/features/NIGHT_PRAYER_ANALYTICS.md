# Night Prayer Analytics
Status: initial approved specification. Existing behavior below is static-code evidence; native replacement is not yet verified.

## Purpose
Describe genuine recorded activity without evaluating spiritual worth.
## Existing implementation and known defects
Missing. Existing aggregate daily stats are separate and must not be treated as prayer records. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Recompute nightly totals on CRUD; milestones 10/100/1000; active/threshold streaks, best/average, monthly/yearly views and measured pace. Explain active-night grace.
## Android screens
overview, night detail, monthly/yearly history, personal insights.
## Administration controls
cohort-safe adoption only, never user log rows.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/night-prayer/analytics; /night-prayer/calendar`.
## Data requirements
derive from owned live session rows; no redundant metric store.
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
Multiple sessions, zero nights, historical edits/deletes, active-night grace, threshold/streak and local exemption cases. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
