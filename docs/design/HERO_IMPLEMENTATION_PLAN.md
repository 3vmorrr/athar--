# Immersive hero integration plan

Status: additive task21.18 specification,26 October2026. [The ordered queue](../progress/TASK_QUEUE.md) remains execution source of truth. Complete one coherent task with its tests/docs/review/commit/normal push/report; do not restart or pause the functional roadmap for a whole-app redesign.

## Current baseline and boundary

Codex functionality through `d9edd6f45f7d3f7c3a2532bf95df26b7c0f52b3f` is retained: protected local reads, owned persistent sync, bounded disposable cache, durable Night conflict review, watch/account local recovery and verified captured-owner dispatch. The UI source tip remains `0e6f4ba6e3bb94075ebc38f1187ec28c7db75d32`;21.1 shared primitives,02.5 Night history slice and02.6 watch slice are accepted. This document adds no native UI/resources, backend routes, repository replacement or dependency upgrade.

Implement [hero specification](IMMERSIVE_SECTION_HERO.md), [section language](SECTION_VISUAL_LANGUAGE.md), [motion/scroll](HERO_MOTION_AND_SCROLL_BEHAVIOR.md) and [artwork rules](ARTWORK_LIBRARY.md) together. Preserve the current actor/ProtectedRead/media/provider contracts, tests and source ownership when reconciling the branch.

## Exact integration sequence

| Task | Coherent unit / acceptance boundary |
|---|---|
|21.18 specification|Required design/motion/asset specs and queue mapping only; validate docs, commit/push/report. This is not UI completion|
|02.7.2 next|Return immediately to functional permanent Night rejection recovery; continue the existing roadmap afterward|
|21.19 in visual phase|Original/fallback artwork registry and adaptive shared hero/layered-content/motion primitives; actual component/device/performance gates|
|21.2|Home/navigation hero and real continuation/next event; source-to-section continuity where supported|
|21.4|Quran hub plus compact reader treatment; canonical text/owned recovery preserved|
|21.8|Night atmosphere with validated context and actual session action; history/calendar/tools/privacy preserved|
|21.9|Provider-safe watch media top and layered comments/playlist; keep actual official playback/state|
|21.5|Audio hierarchy and native selection/expansion; preserve service/source/timing state|
|21.13|Fresh authoritative live/next-event hero and honest cached/offline state|
|21.7|Calm category-aware Adhkar hero, real counters and sequential interaction|
|21.20|Books real permitted covers/original art, catalog/download/reader continuity|
|21.21|Knowledge editorial hierarchy and real continuation/learning navigation|
|21.3/21.6/21.10–21.12|Remaining auth/account, Tafsir, scholar, series and discussion refinements, preserving their functionality|
|21.14–21.17,22.*|Screen-aware states, language/theme/large-font/adaptive/TalkBack/reduced-motion closure and measured release quality|

These refine existing work package21 and its current branch integration; high-level work packages1–24 remain unchanged. Earlier feature tasks may apply the approved visual layer only when technically coherent, with the same acceptance gates and a documented queue adjustment. Do not introduce unrelated redesign into an ownership/offline/security fix.

## Proposed component boundaries

Names are proposals for21.19, not existing code claims: an immutable section-hero presentation model, original artwork resolver/fallback, hero surface with one typed action/status, compact-header adapter and layered-content container. Reuse existing AtharHeroSurface/SectionHeader/spacing/icon/motion conventions where possible, preserving accepted callers/tests. The feature supplies data and owner-bound callbacks; shared visuals never open a repository/session directly.

Use existing Compose/Material3/Coil infrastructure. Document and validate any genuinely necessary additional asset/runtime dependency before introducing it. A shared transition uses supported pinned APIs or falls back to normal native navigation; it must never force an upgrade or retain private auth content.

## Required checks per implementation task

1. Inspect current source and both branch histories/diff for every overlap; preserve newer business/safety/privacy/tests, then premium presentation.
2. Build production/test APK, run relevant JVM/instrumented tests and lint, check paired resources. Backend/schema work requires its own contract/migration gates; the visual layer should not require it.
3. Verify actual known/absent/unreadable/offline/error/loading content and real actions. No fake counts, placeholder continuation, fabricated portraits or unsupported controls.
4. On the authorized API27 phone verify Arabic RTL Light first, English LTR, Dark, normal and reduced motion,150–200-percent text, short height/landscape, navigation/back/focus and the feature's real journey. Tablet/TalkBack/appropriate newer-device acceptance remains explicit when unavailable.
5. Check interrupted scroll/reveal/sheet transitions, keyboard/insets, process/configuration restoration and account/auth changes. Preserve player/service identity and pending data.
6. Measure release-like scrolling/expansion/image memory on the target device, record baseline/build/device/results and resolve reproducible regressions. Debug screenshots/tests alone are not release performance evidence.
7. Update design reconciliation, feature docs, test report, tracker and exact queue; review diff/assets/secrets, commit and normal push, verify remote SHA and give the required task checkpoint.

## Acceptance status

Task21.18 may be complete when the specification/links/queue are validated and its documentation commit is pushed. Hero artwork, motion, scroll and screens remain NOT accepted until their actual implementation tasks satisfy all applicable gates above. No static mockup, source-only premium branch review or planned resource is counted as a finished screen.
