# Night Prayer
Status: backend service and native encrypted offline foundation tested; complete feature acceptance remains in progress. Requirements below remain mandatory unless marked implemented with evidence.

## Conflict resolution increment

Task02.5 provides durable explicit device/account review, safe stale/deletion/copy handling and retained-ciphertext/owner protection. Arabic10/English15 physical task/regression cases, main API1,47 JVM/build/lint/305 resources pass. See [conflict scope and limits](NIGHT_PRAYER_CONFLICTS.md). This does not complete the timer/editor/insights/notification roadmap.

## Calendar implementation increment

The native calendar derives its daily and monthly totals from the signed-in user's locally stored sessions, including pending edits, without creating synthetic sessions or estimating missing worship. Users can move by month, select a day and inspect its recorded sessions. A seven-column calendar is used only when48dp day targets fit; narrow or large-text layouts use a dated agenda. Empty dates are described neutrally. Local-only exemptions may be shown on-device and must never enter synchronization bodies. Calendar display is distinct from astronomical night-boundary calculation and must not claim that a blank date is a missed obligation. Leap-year month length and neutral daily aggregation are covered by JVM tests. Focused real-HTTP UI cases cover opening/returning from the calendar in Arabic/English and large-font agenda layout. Complete historical/yearly analytics, reminders and physical-device acceptance remain open.

## Purpose
Record explicit self-reported Night Prayer sessions and private continuity.
## Existing implementation and known defects
Missing in baseline; no sessions, native screens or sync. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Implement target/range calculator, session CRUD/timer/night context, monthly/yearly history, pace/streaks, next-position suggestion, local-only exemptions, optional reminders and authenticated offline sync.
## Android screens
overview, calculator, active/save session, night/history/calendar/insights/settings.
## Administration controls
module/default/educational/reference/announcement controls and safe aggregates only.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/night-prayer/overview; /night-prayer/sessions; /night-prayer/analytics; /night-prayer/calendar; /night-prayer/preferences; /night-prayer/sync`.
## Data requirements
night_prayer_sessions; night_prayer_preferences; stable UUID/revision/tombstone.
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
Inclusive math, cross-surah, edit/delete totals, duplicate retries, DST/travel/night identity and privacy gate tests. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.
