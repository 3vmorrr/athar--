# Recitation Review
Status: native model delivery is implemented / acceptance pending. Local microphone/inference/alignment remain unverified and disabled.

## Purpose
Provide private local recitation feedback with explicit uncertainty.
## Existing implementation and known defects
Worker loads ONNX/IO/corpus via pinned Tilawa SDK; roughly 75 MB; NPL-1.2 restrictions. Native inference is absent. See the audit defect register and migration matrix; no undocumented success is assumed.
## Required behavior
Evaluate ONNX Runtime Android graph/state compatibility, local audio capture and verdict alignment; no uploaded recordings or accuracy promise. Mark native feature unavailable until real inference tests pass.
## Android screens
range selector, model download/management, microphone rationale, live review/retry.
## Administration controls
version/license/download health only; no microphone or private transcript access.
## Backend contract
Proposed v1 suffixes (final routes must match implemented API contract): `/quran/review/model-manifest`.
## Data requirements
versioned model SHA-256 manifest; on-device assets only.
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
Known correct/incorrect/noisy recordings; refusal/low storage/low memory; uncertainty never labeled confirmed correct. Also verify offline/error/Arabic-English/accessibility and authorization behavior. Acceptance requires working UI + domain + transport/data path with named executed checks. Documentation alone is insufficient.
## Extension points
Add providers/editions/settings through the domain/repository boundary; extend APIs compatibly and update source/license decisions, schema and tests. Keep unrelated features and private records isolated.


## Native model delivery status

Task 09.2 now provides an optional Android model-management surface and a strict local delivery path.

At Android build time, Gradle reads the repository's actual ONNX, Quran corpus and IO manifest files and generates a small trusted app asset containing each file's byte length and SHA-256. The large model files themselves are not bundled into the APK.

The runtime resolves each generated relative path only against the current Athar API host's HTTPS origin. Redirects are disabled. Downloads are owner-scoped WorkManager jobs with connected-network, battery-not-low and storage-not-low constraints.

The user must explicitly acknowledge and accept NPL-1.2 for the current manifest version before a download can start. Consent is owner-scoped. The full license text is shipped in the APK from the repository copy.

Each artifact downloads to a private \`.part\` file, is size-bounded, hashed while streaming and renamed only after exact SHA-256 verification. Activation occurs only after all three artifacts verify. Failed/cancelled partials are removed and the previously active verified version remains untouched. The store retains at most the current and previous activated version so a verified rollback remains possible.

The UI exposes package/version/storage/consent/download/cancel/remove/rollback state. It explicitly states that \`runtime_verified=false\`: Task 09.2 does not request microphone permission and does not run inference. Task 09.3 owns native runtime and microphone lifecycle proof.


## Native microphone/runtime status

Task 09.3 now has a real Android microphone lifecycle and a real ONNX Runtime compatibility inference path.

The app requests RECORD_AUDIO only after the user presses the local microphone-test action. Capture is 16 kHz mono PCM16, converted to bounded 100 ms Float32 chunks and never persisted or uploaded. Leaving the foreground stops capture.

The pinned ONNX Runtime Android dependency is checked at build time for both armeabi-v7a and arm64-v8a shared libraries. After the Task 09.2 package is installed, the user may run a native CPU compatibility probe. It constructs every declared current Zipformer input/state tensor within a bounded memory budget and executes the graph for log_probs.

This still does not constitute recitation recognition. The upstream @tilawa/core TypeScript package owns the audio feature extraction, streaming state propagation, CTC decoding and Quran tracking. Until an approved native equivalent is implemented, Athar does not show verse matches or word verdicts from microphone audio.
