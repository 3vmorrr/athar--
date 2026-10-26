# Implementation plan

## 2026-10-27 — execution handoff checkpoint

- Source of truth branch: `parallel/admin-foundation`; verified head before this documentation commit: `65c5f551845227a9be55926ee92750031021bb01`.
- Last landed implementation task: **16.1 — Private notification inbox API**.
- Next implementation task: **16.2 — Native notification inbox**.
- Tasks **11.1 through 16.1** listed in `TASK_QUEUE.json > implementation_checkpoint` have landed implementation and are marked **PARTIAL**, not COMPLETE, until their queue-defined build/test/device/integration gates pass.
- **15.3** remains **BLOCKED** on an authorized free FCM project/configuration and approved physical test device. Never fabricate provider/device proof.
- Downstream implementation may continue from landed PARTIAL code dependencies; acceptance closure remains a separate gate.
- The earlier queue pointer to 02.7.2 was stale for this branch and must not cause an agent to restart already-landed engagement/reminder/push/inbox work.

## Strict executable queue —26 October2026

The [ordered task queue](TASK_QUEUE.md) and editable [task records](TASK_QUEUE.json) refine the existing24-package roadmap. Exactly one implementation task is active; each record defines scope/state/dependencies/modules/tests/device gates/Definition of Done/contribution. Completed02.1–02.7.1 and the accepted21.1 shared premium slice are preserved. Detailed milestones live in [completion receipts](TASK_COMPLETIONS.md) and [the test report](../testing/TEST_REPORT.md). Latest native baseline:53 JVM/build/test APK/lint/306 resources and Arabic10/English32 hardware plus main API1 PASS throughd9edd6f.

Completed21.18 documents the additive [immersive hero direction](../design/IMMERSIVE_SECTION_HERO.md), [motion/scroll](../design/HERO_MOTION_AND_SCROLL_BEHAVIOR.md), [section language](../design/SECTION_VISUAL_LANGUAGE.md), [original artwork](../design/ARTWORK_LIBRARY.md) and [integration plan](../design/HERO_IMPLEMENTATION_PLAN.md). It changes no native UI/resources. Current implementation task is 16.2 (Native notification inbox); 11.1–16.1 implementation is landed/acceptance-pending, while 15.3 is externally blocked. Future21.19 primitives and21.20/21.21 Books/Knowledge remain explicit implementation units; visual tasks follow Home/Quran/Night/Watch/Audio/Upcoming/Adhkar/Books/Knowledge before remaining closure. No completed feature is restarted or replaced by a static reference.

Phase2 is83%; its former02.7 was split without changing the10-point budget. Overall engineering estimate45% (45.46 weighted, uncertainty±10pp);140 tasks remain open. Phase21 closure uses contribution budgets (6% rounded), so a documentation task is not credited like a completed screen. Blocked external gates remain open while independently ready work proceeds. Every successful task requires docs/tests/device where applicable, diff/secret review, logical commit, normal push, exact remote-SHA verification, required report and immediate continuation.

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
