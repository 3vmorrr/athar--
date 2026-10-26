# Engineering change log

##2026-10-26 — Owned persistent synchronization

- Added unique WorkManager jobs by environment/account, verified actor checks, logout cancellation, module-isolated failure handling, bounded retries/time budget and six-hour eventual maintenance. Existing encrypted progress/outbox and foreground behavior are retained; exemption data never enters work requests.
-38 JVM cases,292 paired resources, production/test APK/lint pass; physical Worker/HTTP1 case and unique scheduling/storage12 cases pass. No real account reset or notification/email send. Android network-restoration/reboot acceptance remains open because USB reverse does not satisfy CONNECTED on this disconnected phone. Failed initial harness assumptions remain documented.
- Continue phase2 disposable cache bounds and broader recovery/conflict acceptance.

##2026-10-26 — Retained private-read protection

- Added typed Loading/Ready/Unreadable observer states without exposing exception text or swallowing cancellation. Counter/reading payload validation prevents malformed defaults from becoming valid zero progress.
- Protected Quran, counter, Night Prayer/calendar and exemption UI; failed reads retain ciphertext/revisions/outbox and block affected editing/false-empty analytics. Existing prayer data/metadata must agree before being displayed or replaced. Private form state is owner-scoped.
-29 JVM cases,292 resources, build/lint, final11 physical storage cases, Arabic3/3 +English3/3 error/normal journeys and capture reruns pass. Relevant Arabic/English captures inspected. No real key invalidation or app-data reset occurred.
- Phase2 remains IN_PROGRESS for persistent scheduling, cache bounds, broader recovery and conflict acceptance.

##2026-10-26 — Upcoming context and related reading

- Added additive migration015 and revision-checked localized course/topics/book-page metadata. Hidden/deleted books are suppressed publicly; source page references never bypass rights-aware book access.
- Applied the reviewed schema to the private healthy development and generated fixture targets only after separate verified logical backups;15 versions/34 tables pass read-only checks. Original damaged data is unchanged.
- Native detail supports optional context, related-book navigation and provider thumbnails with compact localized failure feedback.20,000 backend assertions,27 JVM cases,291 resources, build/lint and final Arabic3/3 +English3/3 physical cases pass. Updated captures inspected; initial lazy-header and sleeping-device failures retained.
- Completed phase1's scoped native/API gate; continue phase2 local data recovery. Independent Admin, real broadcast playback, reminders and release gates remain separate work.

##2026-10-26 — Upcoming home and offline-state acceptance

- Added bounded three-row home discovery with authoritative live status, view-all/detail navigation and localized controller identity.
- Clarified removed lessons and unpublished recordings; offline saved-live presentation never offers playback. Explicit401/403/404 clears stale displayed content; cached404 content is removed rather than resurrected offline.
- Added4 controller tests, encrypted event-cache ownership/locale/removal acceptance and cached-state UI tests.26 JVM cases,287 resources, build/lint and physical Arabic3/3 +English3/3 reruns pass; both home captures inspected. Initial English test-context failure remains documented.
- Preserved the verified GitHub checkpoint receipt and excluded generated Kotlin compiler state. Optional extended event metadata and independent Admin/reminder work remain open.

##2026-10-26 — Formal project checkpoint

- Compared all approved requirements with actual native/backend/legacy source and additive migrations; recorded24 remaining work packages and weighted41% engineering estimate.
- Re-executed backend19,974 assertions, native22 JVM cases, build/lint/localization and physical upcoming/API gates; retained initial counter failure and explicit remaining acceptance gaps.
- Added read-only schema verification and secret/history screening; excluded signing/provider-local/generated training artifacts. Preserve original corrupt database and Git history.
- Updated event API, engagement and physical development documentation. Independent Admin and notification delivery remain incomplete. Commit/push receipt follows actual remote verification; no successful backup is inferred from documentation alone.
## 2026-10-25 — Native reading acceptance and audio continuation

- Explicit encrypted Quran resume markers now pass storage/offline/conflict/account-isolation checks and Arabic/English save/exact-scroll/three-recreation journeys. Resume does not infer reading completion.
- Deferred automatic list cursor resets after an observed Compose measure/layout failure; Arabic reading/watch/adhkar shared regression passes.
- Continued native audio with ViewModel-owned selection, supported-edition filtering and separate loaded-media identity. Source changes while paused do not autoplay. Build/unit results and remaining live provider/UI gates are recorded in TEST_REPORT.md.

## 2026-10-25 — Native learning and catalog continuation

- Researched official YouTube IFrame/provider identity requirements and dated current mobile references; implemented original Athar watch metadata/actions, collapsed descriptions/discussion, one-level replies, full comments panel, ordered playlists and dedicated series details.
- Added additive migration013, first-party threaded discussion with pending moderation, stable submission IDs, parent visibility and audited revision-checked moderation APIs. Local migration followed a verified private logical backup.
- Added encrypted native lesson checkpoints, explicit completion, account-bound synchronization and no surprise autoplay. Opening, ending or seeking does not complete a lesson. Official embedded playback is still under device verification.
- Added a dedicated scholar profile with source/portrait and separately paginated series/standalone tabs. Corrected inherited scholarship, published lesson counts and newest-home ordering.
- Continued P4 adhkar with a complete reviewed category index and one-entry sequential counter; unspecified source counts remain unspecified. Native acceptance is in progress.
- See TEST_REPORT.md for executed results, failed harness/provider cases and remaining gates. The legacy public website has not been redesigned in this increment.

## 2026-10-25 — Native UI, provider adapters and local recovery increment

- Restored the local runtime using a validated independent22-table logical import. Preserved all original corrupt files and verified cold copies outside the web root; startup script does not perform implicit repair or migrations.
- Added native Material Symbols with a source/license manifest, original-logo viewport, licensed Noto Sans Arabic typography, complete brand surface roles, responsive content home, scoped navigation, reduced-motion tokens and optimistic marker/favorite feedback.
- Added verified MP3Quran catalog/full-recording/radio/timing adapters and Quranpedia per-ayah tafsir catalog/content. No timing match is invented for an unsupported recording.
- Added a private native monthly Night Prayer calendar with narrow/large-font agenda fallback, zero-offset timezone canonicalization and reusable Quran search regexes.
- Shared web/API OTP issuance, verification, reset and auth rate buckets; enforced registration/reset password confirmation and suspended cookie guards. Preserved guest import and marketing consent.
- Current build/lint passes with12 JVM tests. Backend8 suites pass19876 assertions; actual HTTP cookie/API compatibility adds15. Four current native storage tests pass. The expanded UI journey passes Arabic light phone, Arabic dark/130% font phone, Arabic tablet and English tablet cases; reviewed screenshots are retained. Complete module/release acceptance remains in progress. See TEST_REPORT.md for failures and limits.

## 2026-10-25 — Tested backend and native foundation

- Added explicit shared migrations010-012, versioned JSON API, pending verification gate, keyed OTP/attempt limits, shared auth rate buckets and refresh-family rotation/replay revocation.
- Replaced the invalid109-entry Quran count table with canonical114/6236 metadata, bundled original attributed Tanzil text and added Arabic search/section/range/target services.
- Added owned catalog/progress APIs and private Night Prayer sessions with encrypted notes, optimistic revisions, idempotent replay, cleared deletion markers, sync and derived analytics.
- Added Kotlin/Compose Android project, official logo/colors, onboarding, Arabic/English auth gate, Keystore tokens, Room/outbox, offline Quran, catalog/account/adhkar foundations and native Night Prayer session entry/sync/tools.
- Debug APK,9 JVM tests,2 instrumented emulator tests and lint passed before the subsequent prayer-tools increment. Updated verification records track later build results separately.
- Fixed curated books SQL alias, hidden-ancestor favorite visibility, backup scholar portrait coverage and closed-ZIP cleanup error. Asset validation tests pass; full restore still needs regression testing.
- Preserved original data after observing existing InnoDB corruption; added a dedicated test server runner rather than repairing/deleting original files.

## 2026-10-24 — Audit and initial specifications
- Recorded baseline SHA, dirty worktree and additive development branch.
- Generated source/file/provider inventory and preserved approved requirement documents and original logo.
- Inspected migrations 001–009 and existing UI/backend/provider/model/backup implementation.
- Identified invalid Quran count table, missing backup image assets and authentication/OTP/rate gaps.
- Reviewed official Android skills/design/performance and free-hosting restrictions.
- Created initial product, architecture, design, feature, engineering, testing and deployment documentation.

Implementation and verification outcomes are appended as work completes. No native build or complete platform release is claimed by this entry.
