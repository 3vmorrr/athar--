# Khatmah
Status: IMPLEMENTED ON `parallel/admin-foundation`; automated/device acceptance intentionally pending.

## Purpose
Plan Quran completion with realistic date-based workload.
## Implemented contract
- Native `KhatmahScreen` with create/edit workload, pause/resume/restart/finish, explicit confirmations for restart/finish, and Arabic/English states.
- `KhatmahRepository` stores an encrypted owner-partitioned local plan in `cached_documents`; pending state survives process/device restart.
- Server plans use additive revision `020_khatmah_revision.sql`; writes require `expected_revision` and return explicit conflicts instead of silently overwriting another device.
- Conflict review preserves both device and account versions and supports deliberate **Use account** / **Keep device** resolution, including the valid case where the account has no plan.
- Workload is recalculated from 604 canonical pages, inclusive dates, actual completed pages, and current UTC date. It reports remaining pages, daily workload, ahead/behind, future start, and overdue state.
- An overdue plan may still record honest progress when its original target date is unchanged; new/backdated targets remain rejected.
- Night Prayer data is never transferred automatically into Khatmah.
- Private account export includes the retained local Khatmah draft while excluding credentials, keys, and local private exemptions.

Automated Gradle/backend suites and physical-device acceptance were not run in this parallel implementation pass, by request; therefore the progress queue remains PARTIAL until those acceptance gates are executed.
## Required behavior
Native create/edit/pause/resume/restart/finish; explicit units and missed-day recalculation; no automatic Night Prayer count transfer.
## Android screens
plan editor, progress/workload, status/history.
## Administration controls
no routine private-plan editing.
## Backend contract
Implemented: `GET /me/khatmah` and revisioned `PUT /me/khatmah`. A lifecycle compatibility endpoint may exist on the branch, while the native offline-safe client synchronizes lifecycle state through the same revisioned PUT contract.
## Data requirements
khatmah_plans; additional units/history only by additive schema.
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
Inclusive dates, target passed, completed bounds, paused plans and explicit restart. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
