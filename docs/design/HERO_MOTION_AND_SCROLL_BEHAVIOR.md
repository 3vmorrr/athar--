# Hero motion and scroll behavior

Status: design contract, task21.18. Values below are implementation budgets, not benchmark results. Extend the tested [motion system](MOTION_SYSTEM.md); no new runtime/dependency upgrade is authorized by this document.

## Purpose and native organization

Motion explains entry, position, expansion and continuity. It must not compete with reading, worship or media. Normal section pages use one main lazy scroll owner with stable keys/content types. Use a native nested-scroll/app-bar relationship where appropriate; avoid nested unbounded LazyColumns or a manually dragged whole page.

Android documents pinned, enter-always and exit-until-collapsed app bars. For a section with a large hero, prefer a deliberate compact header that re-expands at the top rather than flickering on every small upward scroll. This is Athar's UX choice, not a universal requirement. See [official app-bar behavior](https://developer.android.com/develop/ui/compose/components/app-bars).

## Transition and motion rules

| Interaction | Normal policy | Reduced-motion policy |
|---|---|---|
|First reveal of original decorative art|Optional1.03→1.00 scale within the existing normal220ms token; once per destination entry, not on recomposition|Final state immediately; no scale|
|UI heading/support/CTA|Optional fade with at most8dp translation; CTA stagger no more than40ms, no delayed functionality|Immediate visible final state and identical semantics|
|Home source to section hero|Share only stable original art/container identity where supported on the pinned Compose version; otherwise use existing native navigation continuity|No shared morph, scale or spatial travel; preserve the destination and focus|
|Hero to compact header|Follow native scroll position; keep context and a reachable action without rebuilding feature state|Prefer a stable compact/header or ordinary direct scrolling; remove decorative interpolation|
|Parallax|Optional6–12dp maximum background displacement, clipped to the art area, only after target-device profiling|Zero displacement|
|Audio/selection/comments expansion|Native sheet/panel state and one content scroll owner; fast restrained settling using existing tokens|Immediate supported state change, with the same expand/collapse/close alternatives|
|Press/selected feedback|Existing native ripple, color/shape and restrained token behavior; no layout shifting|Native semantics/feedback remain; no decorative scale|
|Auth or actor boundary|Immediate removal of private content; never shared or delayed|Identical immediate removal|

Canonical Quran verses, recited words and sacred text are never reveal/scale/parallax/shared-transition targets. Do not animate a Quran bitmap/page or generate decorative sacred text. No confetti, flames, streak pressure, continuous glow, moving particles, looping worship effects or new reveal/scroll haptics.

## Scroll and state rules

Keep the current remembered transition tree and target-owned state; do not swap whole animation containers when reduction changes. Preserve form drafts, selection, scroll position and actual player identity. Include actor/feature identity in state ownership. Closing comments or collapsing audio must not lose the lesson/surah/source/resume context.

Use UI-owned animation state; ViewModels own content and playback facts, not frame-by-frame motion. Derive coarse compact/expanded thresholds with the existing supported derivedStateOf; defer frequent draw/placement reads with suitable lambda modifiers/graphics layers. Do not read raw scroll offsets high in the entire screen tree or decode images per frame. This follows [Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices) and [phase-aware performance guidance](https://developer.android.com/develop/ui/compose/performance/phases). Newer documentation APIs do not justify upgrading Athar's pinned dependencies.

## Real sheets and gesture conflicts

The lower rounded surface can be static content. A grip/handle is shown only for a real implemented sheet/panel. Supply visible and accessible expand/collapse/close controls, sensible Back behavior, safe/IME insets and restored focus. The media seek bar owns horizontal seeking; a selection surface must not steal it. A sheet starts consuming vertical content scroll only at its defined expanded boundary.

Use native Material3 sheet state/lifecycle when it fits. Hidden modal sheets leave composition; a separate playback owner remains alive. Full-height comments are preferable on short/large-text screens when an inline panel cannot preserve usable player/content space. See [official bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets). Existing Athar watch in-flow/full-height decisions remain until comparative device tests justify a change.

## Media and provider constraints

Keep the official YouTube viewport, identity, controls, branding and provider requirements intact. Decorative scrims, rounded overlays, handles and custom controls must not cover any part of the embedded player. Put Athar metadata/actions outside it. The reference's custom streaming controls are inspiration for Athar-owned audio, not permission to replace YouTube controls. Preserve the existing provider minimum geometry, source identity, loading/buffering/error/fallback and configuration retention. See [YouTube minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality) and [Athar watch research](YOUTUBE_WATCH_RESEARCH.md).

Audio hierarchy/sheets consume existing playback state; collapsing or selecting a list does not recreate the service/player. Avoid unexpected autoplay. Countdown displays use validated timestamps/timezone, refresh on resume and only the necessary visible cadence; a decorative hero never starts a worship timer or background loop.

## Accessibility and reduction

Honor both system-disabled animation and any caller/app request for stronger reduction; a caller cannot re-enable system-disabled motion. Preserve the tested mid-transition snap/draft behavior from21.1. Keep actions functional immediately, single heading/focus semantics,48dp targets and alternatives to every gesture. Large text may use compact/static layout instead of squeezing controls. Test reduction changed while a transition is running, background/resume, TalkBack focus and interrupted/cancelled gestures.

## Performance and Definition of Done

Target smooth60Hz scrolling on the actual API27 phone, with bounded local artwork and lazy content. No whole-screen real-time blur, background video, heavyweight animation runtime or multi-layer per-frame image processing. Profile a release-like candidate, not just a debug screenshot; record build/hash/device, frame-time/jank data, allocations/peak memory, scroll/expand journeys and comparison baseline. Debug Compose tests prove semantics/state, not release jank. Android recommends release performance verification in [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance).

An implementing task is not complete until build/JVM/test APK/lint/resources, relevant feature regressions, Arabic/English, Light/Dark, reduction, large text/landscape/adaptive behavior and physical scroll/playback gates pass with docs/commit/push. Keep unavailable tablet/TalkBack/release gates explicitly open rather than claiming them from a phone screenshot.
