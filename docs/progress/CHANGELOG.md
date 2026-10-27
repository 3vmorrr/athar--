# Engineering change log

## Parallel integration checkpoint — task18.4

Upcoming/Live Admin accepted with strict timezone/DST/precision handling, canonical CAS/transitions/context/audit, preserved actual confirmation provenance, explicit clearing, retained stale drafts, real global totals/paging and verified-email gating.20,791 backend;263 HTTP/260 browser authoring chain;independent149 HTTP/58 browser;privacy218 HTTP PASS. AR/EN light/dark phone/desktop captures reviewed. [Evidence and reconciliation](../testing/ADMIN_EVENTS.md). No migration/native source change or physical playback claim. Exact cursor2f800af4cd7e988c6d9e908e59efb39c3a58b692; next18.5 books/rights. WP18:4/9 (44%); overall51% (51.38 weighted,±10pp);128 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task18.3

Existing lesson authoring baseline accepted with strict provider/field/scope validation, row/parent locks and transactional audit, event-history protection, scoped dependent cleanup and bilingual retained editors.234 actual HTTP,202 browser and20,765 backend assertions PASS; actual native Watch/playlist/visibility/case-preserving identity and parent concurrency checked. Migration027 and complete-ledger legacy-compatible recovery separately tested/pushed. [Evidence and limits](../testing/ADMIN_LESSONS.md). No production migration/native code change or physical playback claim. No new source range consumed; cursor6bbfee5. Next18.4 Upcoming/Live; WP18:3/9 (33%); overall51% (50.88 weighted,±10pp);129 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task18.2

Series metadata/scholar/order/publication and bilingual controls accepted with transactional row/audit/FK boundaries, conflicting lesson-scholar refusal, dependency-safe deletion and SQL error redaction.193 actual HTTP,135 browser and20,752 backend assertions PASS; actual native catalog and concurrent child insertion/delete checks. [Evidence and reconciliation](../testing/ADMIN_SERIES.md). No migration/native change or physical Android claim. Cursor6bbfee50e51899a6a1c100ac0cbbd8c9a54eed6b; next18.3 lesson CRUD. WP18:2/9 (22%); overall50% (50.38 weighted,±10pp);130 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task18.1

Scholar metadata/optional portraits and bilingual create/edit accepted. Added transactional row/audit/file controls, dependency-safe deletion, public publication checks/private Admin preview, strict validation and retained drafts.159 actual HTTP,68 browser and20,752 backend assertions PASS; actual PNG/JPEG/WebP and concurrent deletion without orphan upload. [Evidence and source reconciliation](../testing/ADMIN_SCHOLARS.md). No migration/native change or physical Android claim. Cursor393760bbef96caa95f31253c584cbdda10317edd; next18.2. WP18:1/9 (11%); overall50% (49.88 weighted,±10pp);131 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task17.6

Owner-only bilingual backup/grant/download/restore/reset accepted with current-password/literal/CSRF gates, session isolation, all-user cookie revocation, recovered-owner identity, recoverable-backup checks and transactional/asset compensation. Durable journal repairs actual pre/post-COMMIT process termination; malformed/corrupt/foreign recovery data stays preserved and blocks service.20,752 backend,189 HTTP and48 browser assertions PASS; secret scan clear. [Evidence, source reconciliation and limits](../testing/ADMIN_RECOVERY.md). No migration/native change or physical Android claim. Source cursor8a06c84c1d021ad26b0f86791d78fc1e74adb1f4; next18.1. WP17:6/6 (100%); overall49% (49.38 weighted,±10pp);132 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task17.5

Privacy-bounded Admin overview/account detail and operational journal accepted. Removed private individual/aggregate worship queries; canonical audit allowlist and pre-view projection exclude raw email/opaque private target data while preserving original rows.218 HTTP and20,691 backend assertions PASS, including unchanged private self export and205 records paged100/100/5. Actual AR/EN phone/desktop reviewed. [Evidence, conflicts and resource-run notes](../testing/ADMIN_PRIVACY.md). No migration/native change. Cursor5ad55efbb9d06d5c89395cf663c70da015b90257; next17.6 owner recovery. WP17 closure5/6 (83%); overall49% (48.78 weighted,±10pp);133 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task17.4

User lookup/profile/suspension/restoration/role/deletion accepted with literal global search,50/10 paging, centralized target controls, bilingual tools and focused feedback.182 actual HTTP and20,683 backend assertions PASS, including access/refresh/public/Admin revocation, retained progress and generated-user deletion/dependent cleanup. Actual AR/EN desktop/phone reviewed. [Evidence and retained failed-attempt notes](../testing/ADMIN_USERS.md). No migration/native change. Cursor834af23dbe9f01bcfc8ec1f0eeb943dc3028feed; next17.5 removes historical private Admin analytics/detail data. WP17 closure4/6 (67%); overall48% (48.18 weighted,±10pp);134 open. WP02.8 remains deferred.

## Parallel integration checkpoint — task17.3

Central action/target authorization integrated with fail-closed roles/operations, transactional target locks and durable rollback-denial audit.683 matrix/20,683 backend and120 HTTP assertions PASS, including a real concurrent promotion blocked by row locking and retained-session demotion. Owner/Admin browser controls checked. No migration/native change. [Evidence and limits](../testing/ADMIN_AUTHORIZATION.md). Source cursor81b2996522f869440c6b94c5dec270be8d1bdb48; next17.4. WP17 closure3/6 (50%); overall48% (47.58 weighted,±10pp);135 open. WP02.8 remains PARTIAL/deferred; later mapped parallel work is not accepted merely because code exists.

## Parallel integration checkpoint — task17.2

Independent bilingual Admin shell integrated with corrections;60 HTTP and20,000 disposable backend assertions PASS. Actual1440/1024 desktop,768 tablet,360/320 phone,AR/EN light/dark,keyboard/modal/breakpoint/panel persistence accepted; details in [Admin shell acceptance](../testing/ADMIN_SHELL.md). No migration/API/native change. Source cursor6d74f375ab6634de38f83e78cb081d81d9269e8f; next17.3 action matrix. New source delta through ad345fe7f14c4e4bda6ce0c15e05e895f94f6eee mapped separately and retained locally, not accepted. WP17 closure2/6 (33%); overall47% (46.98 weighted,±10pp);136 open. WP02.8 stays PARTIAL/deferred. Legacy editor localization/privacy/campaign replacement remains with its own later task; shell acceptance does not close those gates.

## Parallel integration checkpoint — task17.1

Independent Admin authentication is integrated with corrections and applicable acceptance. Source cursor is `7fd87ade0a9fb5101962e360fee7d48f3fdb871a`; the full pre-integration map and pending groups live in [PARALLEL_INTEGRATION.json](PARALLEL_INTEGRATION.json). Main base checkpoint was `224a18652ff5cc5abfdbbd25d9f1da657479de8b`. Next task17.2 is the independent bilingual shell. WP02.8 remains PARTIAL and deferred under the user's integration-first instruction. No bulk branch merge has occurred; source implementation is not completion evidence.

Task17.1:49 real HTTP assertions and20,000 disposable backend assertions PASS; PHP/Python/JS syntax and Arabic/English browser authentication/logout/password visibility reviewed. No migration or Android change. Scope/fixes/limitations: [authentication acceptance](../testing/ADMIN_AUTHENTICATION.md). WP17 closure1/6 (17% rounded); overall46% (46.38 weighted, uncertainty±10pp);137 tasks open. Only the accepted authentication slice earns credit.

## Task02.7 — cross-module lost-response/storage reopen acceptance

- Add test-only real HTTP post-commit response loss, actual Room disk close/reopen and repeated retry across reading, counters, lesson checkpoints and ordered Night edits/deletion. Exact exported activity totals and stable revisions/UUIDs prove no duplicate completion or lost pending/newer checkpoint.
- Use fresh verified actors only in the explicitly validated generated fixture schema. Existing storage/conflict/owner/native journeys remain; production logic/UI/backend contracts unchanged.
-56 JVM/build/test APK/lint/316 resources; API27 Arabic19/English19 PASS. Overall46% (weighted45.78), offline90%,138 open tasks. Begin02.8: read-only device connectivity now satisfies CONNECTED/INTERNET/VALIDATED; actual process/reboot/reconnect acceptance remains open.

## Task02.7.2 — retained permanent Night Prayer rejection recovery

- Persist permanent operation rejection separately from409 conflict and temporary delivery failure; isolate the oldest eligible mutation per session and commit healthy acknowledgements in mixed batches.
- Add explicit owner/snapshot-validated unchanged retry and manual untimed correction, retaining original bytes until that deliberate choice. Protect unreadable/stale queues; keep timed/calculated/deletion retry-only to preserve hidden data.
- Register additive Room1→2 migration and retain old schema; actual migration/reopen, real422 native correction, AR/EN/dark200-percent/landscape and regression acceptance pass.56 JVM/build/test APK/lint/316 resources; API27 Arabic10/English29/main API1.
- Overall engineering estimate46% (weighted45.71, uncertainty±10pp), offline phase86%,139 open tasks; next02.7. Parallel17.1–17.3 remain reserved and uncredited until accepted integration.

## Task21.18 — immersive hero specification

- Documented original layered heroes, section-specific data/actions, compact reading/adaptive behavior, bounded scroll/shared-art/sheet motion, reduced motion, accessibility, original asset provenance/fallbacks and performance budgets from the six user inspiration references.
- Added five linked specifications and a nine-ID planned asset specification; reconciled existing brand/premium/motion/research guidance and exact future21.* task order. No reference images, new art resources, native code, runtime/dependency or backend contract is added.
-5 specifications/25 local links/9 planned IDs,149-task graph/render and documentation-only scope PASS. No Android/device rerun is claimed for docs; last native baseline remains53 JVM, Arabic10/English32 and main API1 throughd9edd6f.
- Overall45%, phase2 83%, weighted UI closure6%,140 open tasks. Continue functional02.7.2; real hero implementation/acceptance remains21.19 and screen tasks.

## Task02.7.1 — captured-owner mutation dispatch

- Capture the intended actor before local IO/lock/index waits and at account/reading/counter/Night exemption/bookmark/favorite/discussion call sites; stale actions cannot retarget another account or consume retained pending rows.
- Verified/full SignedIn access is required for private local/API actions, including same-actor transition to Pending. Verify/resend keep their narrow pending-account allowance; logout checks its intended actor; verification UI state is keyed by account. Existing generation/token-persistence protections remain.
- Final53 JVM/build/test APK/lint/306 resources PASS; API27 Arabic10/English32 +main API1 PASS. No real email/profile/password/key reset, backend/schema or visual replacement.
- Overall45%, phase2 83%; new immersive-hero addendum adds four explicit tasks. Next21.18 documents the direction, followed by functional02.7.2;141 tasks open.

## Task02.6 — watch/account local recovery

- Strict retained lesson checkpoint decoding/observation, retry and no-write/no-completion guards; captured-owner save/flush; preserve ready player composition during ordinary metadata switches and own checkpoint/ack updates.
- Account payloads distinguish explicit absence from malformed zero/empty defaults. Validate owned lesson/Night snapshots before opening an export output stream; local-only exemptions stay excluded. Full SAF lifecycle remains later.
- Incrementally reconcile only reviewed watch title/comments preview presentation. No backend/schema/source-provider replacement.50 JVM/build/lint/306 resources PASS; final API27 Arabic5/English15 +main API1 PASS; captures inspected. Initial English4 HTTP failures from lost USB mappings retained; full rerun passed after repair.
- Refined02.7 into owner dispatch, permanent Night rejection recovery and cross-module acceptance tasks with the same phase weight. Overall45%, phase2 80%,138 open tasks; next02.7.1.

## Task02.5 — durable Night Prayer conflict resolution

- Persist owned409 conflicts across recreation, including pending deletions; exclude blocked session IDs before the batch limit so healthy work remains eligible. Other statuses preserve pending/retry classification.
- Fresh account/device review with cancellation, stale confirmation checks, target-only queue replacement, new mutation identity and explicit new-copy behavior for server-deleted sessions. Unreadable retained bytes and account boundaries remain protected. Draft Save is blocked if conflict arrives during editing.
- Reconcile only the history premium surface and section motion, with a scrolling hero and native adaptive review at large text/landscape. No backend/schema/exemption-upload change.
-47 JVM/build/lint/305 resources PASS; English15/15 actual-phone task/regression cases PASS; final Arabic10/10 and main API1/1 PASS, with current evidence recorded in TEST_REPORT. Earlier footer/lint/lazy/localization failures retained. Remaining editor completeness and general lifecycle gates stay separately queued.

## Task21.1 — premium shared UI foundation

- Reconciled only reviewed premium spacing/hero/section/action/status/metric primitives and motion tokens from UI tip0e6f4ba. Added real disabled-action semantics, accessible headings/button role, large-font section stacking and explicit foreground contrast. No data/backend/auth/permissions/dependency changes.
- Controlled remembered page transitions with lifecycle-owned animateTo/snapTo, caller/system reduction, preserved current drafts and keyed target state. No sacred-text looping/celebration/new haptic or external runtime.
-47 JVM/build/lint/292 resources PASS; physical Arabic4 component +3 protected/normal cases, English8 component/protected/normal/watch cases and main API1 case PASS. Scoped normal startup has zero matching critical failures; captures inspected. Single-frame harness failures are retained and final effect/composition budget is explicit.
- Remaining premium screen merges, tablet/TalkBack and release performance gates stay open. Next02.5 is durable Night Prayer conflict resolution with intentional overlapping presentation reconciliation.

## Task02.4 — authentication session ownership

- Fixed old response/refresh success/revocation races during a new login/registration. Authentication intent and durable credential-save boundary share a mutex; successful new authentication also invalidates requests started during its wait. Failed sign-in preserves the prior stored session.
-47 JVM/build/test APK/lint and292 resources PASS; physical Worker/HTTP1/1 and Arabic4/4 +English4/4 auth/counter/reader regression PASS. Failed pre-fix cases and initial wrong test-selector batch remain documented.
- Added strict143-record executable queue, renderer/dependency validation and pinned premium branch reconciliation ledger. Only task02.4 implementation is changed; shared premium integration is the next independent task21.1. Native Android remains primary; independent Admin and other unimplemented features are not credited as complete.

##2026-10-26 — Disposable content-cache bounds

- Bounded encrypted Arabic/English HTTP responses to200 rows/8MiB per actor,1MiB per document and seven-day fallback age; metadata-only transactional pruning leaves private progress/outbox/cursors/exemptions untouched. Existing Room schema1 is preserved.
- Corrupt disposable responses can be downloaded again; stale/invalid timestamps and authoritative404 cannot resurrect unavailable content. Actor checks protect both fresh and offline returns during account switching.
-42 JVM/build/test APK/lint and292 paired resources pass; final hardware cache/storage/unique-work regression15/15 passes. Read-only development schema15/34 and API health pass. Real reboot/network-restoration and complete phase2 conflict/recovery acceptance remain open.

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
