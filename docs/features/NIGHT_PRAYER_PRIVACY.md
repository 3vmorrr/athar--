# Night Prayer Privacy
Status: approved privacy requirements plus tested encrypted local-read foundation. Task02.5 adds explicit per-session conflict review; full module/release privacy acceptance remains open.

## Purpose
Protect personal worship history and optional exempt-night information.
## Existing implementation and known defects
Baseline lacked this module. Native sessions/outbox and local-only exemptions now use owned encrypted storage with retained-unreadable recovery. Conflict review never uploads exemptions, never logs private notes, and changes only the chosen session. Independent Admin/privacy export/delete/release acceptance remains separately queued; existing legacy aggregates are not independent Admin acceptance.
## Required behavior
Exempt feature disabled by default, generic reason-free selection, local-only encrypted storage, explicit continuity rule and deletion. No upload, admin access, public ranking or inferred sex/identity.
## Android screens
privacy explanation, optional exempt-night editor, deletion controls.
## Administration controls
no exempt or routine private-worship views.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `no exemption endpoint in v1; owned session endpoints only`.
## Data requirements
local exemption table excluded from sync/backup; owned server sessions.
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
Payload/export/log inspection finds no exemption data; admin cannot query another user; exemption never increments prayer totals. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
