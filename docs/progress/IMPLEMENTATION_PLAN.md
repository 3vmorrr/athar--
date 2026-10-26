# Implementation plan

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

## Strict executable queue —26 October2026

The [ordered task queue](TASK_QUEUE.md) and editable [task records](TASK_QUEUE.json) refine the existing24-package roadmap. Exactly one implementation task is active on this track; parallel/admin-foundation independently reserves17.1–17.3 (issues1–3, Draft PR4). Preserve that ownership; no duplicate implementation or source-only completion credit, and no Draft PR merge before actual acceptance and reconciliation. each record defines scope/state/dependencies/modules/tests/device gates/Definition of Done/contribution. Completed02.1–02.7.1 and the accepted21.1 shared premium slice are preserved. Detailed milestones live in [completion receipts](TASK_COMPLETIONS.md) and [the test report](../testing/TEST_REPORT.md). Latest native baseline:56 JVM/build/test APK/lint/316 resources and Arabic19/English19 hardware recovery/native regression PASS for02.7 (previous main API1 and02.7.2 rejection acceptance remain recorded).

Completed21.18 documents the additive [immersive hero direction](../design/IMMERSIVE_SECTION_HERO.md), [motion/scroll](../design/HERO_MOTION_AND_SCROLL_BEHAVIOR.md), [section language](../design/SECTION_VISUAL_LANGUAGE.md), [original artwork](../design/ARTWORK_LIBRARY.md) and [integration plan](../design/HERO_IMPLEMENTATION_PLAN.md). It changes no native UI/resources. Completed functional02.7.2 adds retained permanent Night rejection/explicit retry/manual untimed repair and registered additive Room1→2 migration, with no backend contract change. Completed02.7 adds real HTTP post-commit loss/disk-reopen/repeated-retry acceptance across all four private modules and exact exported activity totals. Current02.8 starts actual OS lifecycle acceptance; the physical phone now has a validated active Internet network. Storage reopen is not credited as an OS process/reboot/reconnect test.02.8 strict installed-OS connected scheduling now passes Arabic1/English1 with direct-worker fallback forbidden; its remaining stable-origin/lifecycle harness is recorded in testing/OS_SYNC_LIFECYCLE.md, and the task stays PARTIAL. Future21.19 primitives and21.20/21.21 Books/Knowledge remain explicit implementation units; visual tasks follow Home/Quran/Night/Watch/Audio/Upcoming/Adhkar/Books/Knowledge before remaining closure. No completed feature is restarted or replaced by a static reference.

Phase2 is90%; its former02.7 was split without changing the10-point budget. Overall engineering estimate46% (45.78 weighted, uncertainty±10pp);138 tasks remain open. Phase21 closure uses contribution budgets (6% rounded), so a documentation task is not credited like a completed screen. Blocked external gates remain open while independently ready work proceeds. Every successful task requires docs/tests/device where applicable, diff/secret review, logical commit, normal push, exact remote-SHA verification, required report and immediate continuation.

## Formal checkpoint roadmap —2026-10-25

Phase2's first stable milestone O01 now passes retained private-read/ciphertext/metadata/cancellation gates, ordinary reader/counter regression and Arabic/English physical UI inspection. Continue persistent owned background sync and remaining cache/conflict hardening; do not mark the entire offline phase complete from O01. See [local recovery scope](../features/LOCAL_DATA_RECOVERY.md).

26 October continuation: phase1's native/API upcoming/live home, optional context, unavailable/replay/offline and related-book navigation functional gates now pass (E01/E01a/E01b). Independent Admin event editing and reminder delivery remain their separate later dependencies; no real broadcast acceptance is inferred. Continue work package2, offline/synchronization hardening, beginning with unreadable retained local-record handling (QF03). The original24-package snapshot remains historical;23 packages now remain open, with release acceptance across all modules still required.

The current source audit and24 remaining dependency-based work packages are recorded in [the formal checkpoint](../audit/CHECKPOINT_2026-10-25.md). These refine existing P4–P8 rather than restarting completed foundations. E01 has migration/API/native list/detail and focused hardware evidence; phase1 closes its remaining extended metadata/home/offline acceptance before the next work package. Stable milestones receive meaningful commits and normal pushes after verification and secret review. Independent Admin remains NOT STARTED; the legacy dashboard is not its acceptance substitute.

Scope follows the approved master plus mandatory Night Prayer addendum. Existing website and local modifications are preserved. No silent paid services, destructive production migrations or feature removal.

| Stage | Deliverables | Dependencies | Exit evidence |
|---|---|---|---|
| P0 Discovery | source/schema/feature/provider/license/brand/toolchain audit | repository | reproducible inventory, defects and blockers |
| P1 Documentation | architecture, feature states, API/data/auth/offline/design/test/deployment contracts | P0 | linked specs and traceability |
| P2 Backend foundation | canonical Quran index; bootstrap/router; scoped auth; rate/OTP services; additive migrations | P1 | isolated domain/API/auth tests |
| P3 Android foundation | Gradle/native design/resources, splash/onboarding, verified auth, Keystore/Room/DataStore/navigation | P2 | unit/build/auth gate tests |
| P4 Core modules | Quran/media/tafsir/search/review, adhkar, knowledge/books/content/comments/account/khatmah | P3 | feature integration and offline tests |
| P5 Independent Admin | separate shell/login, full CRUD/users/moderation/campaign/services/recovery/localization | P2 | authorization/CSRF/CRUD/layout tests |
| P6 Night Prayer and integration | calculators, explicit logging/timers/night context, history/streaks/calendar/pace/local exemptions/sync/admin settings/reminders | P3/P4/P5 | edge/DST/sync/privacy/device tests |
| P7 Quality | performance/security/accessibility/reliability/regression fixes | implemented features | measured release evidence |
| P8 Deployment | APK, signing/build scripts, supported free host verification, deployment package/docs | P7 | reproducible build + hosted mobile journey |

Implementation tasks are tracked with evidence in [TASK_TRACKER](TASK_TRACKER.md). Blocking one external service does not prevent independent domain/backend/UI work; unavailable states must remain truthful. No stage is skipped to produce a superficial demo.

###26 October — phase2 execution

Protected local reads (O01), owned persistent-work implementation (O02) and focused disposable-cache bounds (O03) now have executed gates. See [persistent synchronization](../features/PERSISTENT_SYNC.md) and [cache policy](../features/CONTENT_CACHE.md). Keep actual worker HTTP, durable scheduling and real network-restoration/reboot acceptance distinct. Continue session race/remaining conflict/recovery surfaces, then phase3 Quran tools. Do not evict private pending progress as if it were downloaded catalog content.

## Current P4 execution order — watch requirement incorporated2026-10-25

1. Complete native watch/series/discussion and provider-safe player integration with focused tests.
2. Continue remaining P4 Quran/audio/adhkar/books/scholars/account/search/offline work without public legacy-web redesign. Scholar tab pagination and adhkar sequential counter now have Arabic/English device cases. Durable reading-position resume/conflicts/recreation now passes native storage and Arabic/English UI gates. Audio selection/configuration and actual whole-surah/timing/ayah-clip gates now pass, with physical/background/network acceptance still open. Arabic OTP input also passes native gates. Current account increment is stable-code localized feedback; continue catalog/account/export/khatmah/offline modules afterward. Keep physical fullscreen/back verification and release profiling open rather than treating an emulator playback pass as complete device acceptance.
3. Execute independent P5 Admin, including shared discussion moderation, then complete P6/P7/P8 gates.

Arabic/RTL/light is the fresh-install default regardless of host OS. English is explicit and persisted. Each substantial increment must build and have evidence before moving on. P4 is not complete merely because the watch screen is implemented.

## Engagement and physical-device addenda —2026-10-25

Physical execution is now a mandatory continuing gate: update/install the actual Debug application on the explicitly selected connected phone, use the established API/database via USB reverse, verify actual startup/network/localization and repeat at major milestones. Preserve existing data, the original damaged database and unrelated services. Test implemented modules immediately; missing future campaigns do not block current integration checks.

Integrate engagement A upcoming/live UTC metadata and native UI into P4; B lesson local reminders/deep links plus D central channels/preferences/permission before E personal routines in P6; C explicit content follows; F push/device registration and G private inbox; H/I independent Admin campaigns/email queues and J policy with P5/P6; K physical/security/performance QA with P7. Keep campaigns opt-in and worship data outside Admin audiences. The stages extend dependencies instead of replacing completed work. No feature is complete from a notification screenshot alone.
