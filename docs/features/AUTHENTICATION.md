# Authentication
Status: versioned identity services tested; legacy cookie migration remains in progress.

## Cross-channel OTP migration

Web verification and password reset consume the same purpose-bound HMAC OTPs as the API, retaining compatibility with unexpired legacy SHA256 codes. Issuance must not invalidate an older usable code when SMTP rejects the replacement. Web confirmation retains its cookie/CSRF boundary and does not create unused mobile bearer sessions. Password reset revokes sessions through the shared identity service. Shared failed-attempt counts remain transactional in the database. Account creation and cookie login now use shared validation/password hashing without issuing unused API tokens. Explicit marketing consent is preserved; an outer transaction can atomically roll back account creation with guest-progress import. Suspended users must be rejected by cookie guards as well as the API. Eleven actual HTTP fixture assertions cover registration confirmation, cross-channel HMAC verification/reset and suspension. Complete account-management, independent-admin and real SMTP acceptance remain pending.

## Purpose
Require verified authentication for every main Android feature.
## Existing implementation and known defects
Baseline PHP cookie login/register/recovery/OTP used session-only throttles, duplicated verification and no password confirmation/mobile tokens. Shared service and cookie-adapter increments above address those paths; remaining account-management adapters are tracked separately. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Shared identity service, keyed OTP and shared rates, confirmation field, verification-scoped credentials, rotating revocable tokens, honest SMTP failure and secure deletion.
## Android screens
welcome, login, register, verify, forgot/reset.
## Administration controls
account status/suspension/roles with audit; Gmail write-only settings.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/auth/register; /auth/login; /auth/verify-email; /auth/resend; /auth/forgot-password; /auth/reset-password; /auth/refresh; /auth/logout`.
## Data requirements
users; auth_tokens; api_sessions; auth_rate_buckets.
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
Unverified/expired/replayed credentials denied; attempts across cookie changes; password confirmation; no email enumeration. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.

## Native Arabic keyboard OTP input

The native reset and verification fields share OtpInput.normalize. ASCII, Arabic-Indic and Eastern Arabic digits map to six ASCII transport digits; leading zeroes are preserved, paste separators/direction marks are ignored, and the existing six-digit bound remains. This rule applies only to OTP input, never passwords. Server OTP validation, attempts, expiry and rates remain authoritative. Native unit/input-only UI gates are in progress; the UI case does not request mail or submit a password change.

OTP verification outcome:3 unit cases and Arabic/English input-only device cases pass. The reset input correctly accepts both Arabic digit families, leading zeroes and pasted separators. Both production fields share the same normalizer; no complete mail-delivery or reset-password acceptance is inferred.

Native identity/profile feedback now maps transport codes to localized resources rather than rendering the code itself. Known credential/code/rate/session/profile conflicts get their own text. Unknown internal details use a generic localized recovery message. Account profile reuses the same feedback component. The new error-state device checks are pending.

Feedback acceptance: the ViewModel now classifies ApiException.code instead of remote message text. Actual API27 Arabic and English UI cases inject errors through the operation path with distinct untrusted message text and verify localized output/no internal detail. Unit/build/lint gates pass; full external mail delivery remains separate.
