# Premium UI source research — 27 October 2026

These are inspected source snapshots, not screenshot imitation. Downloads live privately in `artifacts/premium-ui/references/`. No Flutter dependency or source is imported. New Kotlin components are original adaptations. Existing official logo, Noto Sans Arabic, Amiri Quran, and Material Symbols license/provenance records remain in `THIRD_PARTY_VISUAL_ASSETS.md` and `android/visual-sources/`.

| Project | Inspected revision | License |
|---|---|---|
| [Now in Android](https://github.com/android/nowinandroid) | `2982e63cb691ad49cd009aab43dc206fee6699c2` | Apache-2.0 |
| [Compose samples / Jetcaster](https://github.com/android/compose-samples/tree/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetcaster) | `25631e8f544a9a1e3f01e296eaf1b4c28428196c` | Apache-2.0 |
| [Wonderous](https://github.com/gskinnerTeam/flutter-wonderous-app) | `747b945a7e5239356bf2664261aa2f3b020b8898` | MIT; artwork has separate rights, not imported |
| [Liquid Glass Widgets](https://github.com/sdegenaar/liquid_glass_widgets) | `6478c02ec6540cda296ba9c441e33826d65cdcc1` | MIT |
| [Flutter Animations Gallery](https://github.com/bizz84/flutter_animations_gallery) | `c841df92a68e2d1beeb82b3c70bf31b58eaf7bda` | Check upstream license before any reuse; no code/assets copied |

## Findings and source mapping

Paths below are exact within their pinned repository. Compatibility is relative to Athar's resolved Compose 1.9.0 / Material3 from BOM 2025.08.00, Navigation Compose 2.9.3, Media3 1.8.0, Android API26+.

| Athar component | Project / exact source file | Technique | Compose equivalent | Compatibility / reuse | Performance risk |
|---|---|---|---|---|---|
| Complete type hierarchy | NIA `core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Type.kt` | Explicit 15-role scale, per-role line height/weight/tracking | `Typography`, bundled Arabic font, content direction | Adaptable Kotlin pattern; do not copy its LTR/Left or Latin tracking | Large Arabic text must wrap and keep diacritics |
| Theme | NIA `core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Theme.kt` | Light/dark semantic schemes, optional dynamic theming, shared typography | Existing `AtharTheme` | Adaptable pattern; keep Athar identity and complete surface roles | Cover-derived color may fail contrast; excluded |
| Navigation/scaffold | NIA `app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt` | Adaptive navigation, controlled inset consumption, destination organization | Existing bar/rail policy and NavHost | Architectural concept; no migration to NIA's dependencies | Double inset consumption, state loss on resizing |
| Content cards | NIA `core/ui/src/main/kotlin/com/google/samples/apps/nowinandroid/core/ui/NewsResourceCard.kt` | Native clickable card, image, text hierarchy, semantics, bookmark action | Catalog cards / section headers | Original implementation; avoid unrelated drag/drop | Decode at displayed size, stable lazy identity |
| Loading/testing | NIA `feature/foryou/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouScreen.kt` and `feature/foryou/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouScreenScreenshotTests.kt` | Screen state rendering and form-factor image coverage | Existing DataScreen/protected reads, real instrumentation captures | Patterns only; keep current test infrastructure | Never treat compile as visual acceptance |
| Expanded player | Jetcaster `Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/player/PlayerScreen.kt` | Artwork/description/slider/control hierarchy; adaptive arrangements and command callbacks | Original premium controls connected to AudioViewModel | UI organization only | Progress polling must stop outside active lifecycle |
| Playback truth / queue | Jetcaster `Jetcaster/core/domain/src/main/java/com/example/jetcaster/core/player/MockEpisodePlayer.kt` and `EpisodePlayer.kt` | StateFlow combines episode/queue/time/speed; timer simulates playback | Retain Athar's MediaController / MediaSessionService | **Mock engine, not working audio playback.** Not reused | Simulated timer must never replace actual player position |
| Hero identity | Wonderous `lib/ui/wonder_illustrations/common/wonder_hero.dart` and `wonder_title_text.dart` | Stable Hero tags / rectangle interpolation; animation can be disabled | SharedTransitionLayout + sharedElement where source and destination coexist | Flutter concept, original Kotlin; local API compilation required | Keep shared transitions on visible selected covers |
| Layered reveal | Wonderous `lib/ui/wonder_illustrations/common/wonder_illustration_builder.dart` | Foreground/midground/background builders, reversible controller; disabled animation final state | Existing native Canvas hero + graphicsLayer | Native reimplementation; no Dart import | Avoid large offscreen layers and continuous redraw |
| Floating surface | Glass `lib/widgets/containers/glass_container.dart`, `lib/widgets/shared/glass_solid_surface.dart` | Surface presets, shared material authority, solid theme-aware fallback | Tonal `Surface`, subtle border, almost opaque fill | Visual/engineering inspiration; API27 solid fallback is default | No list blur or shader; avoid GPU cost |
| Quality strategy | Glass `lib/utils/glass_quality_adapter.dart` | Measured warm-up/raster percentile/hysteresis adaptation | Future measured quality tuning; not needed for solid fallback | Concept only; no unmeasured adaptive-quality claim | No claim of 60fps without profiling |
| Micro-motion | Gallery `lib/samples/explicit/staggered_animations.dart`, `lib/samples/implicit/animated_container.dart`, `lib/samples/explicit/ring_animation.dart` | State controller and interval/curve animation; bounded progress | AtharMotion, state-driven press/progress/entrance | Flutter concepts; do not copy gallery's 1500ms demonstration timing | Do not stagger every reading row or animate verse layout |

## Feasibility and actual gaps

Athar already uses Media3 ExoPlayer inside `PlaybackService`, trusted-controller checks, HTTPS media allowlisting, audio focus/noisy handling, and automatic session notifications. Preserve it. The inspected Jetcaster does not prove real streaming, background audio, media notifications, or recovery. Its mock queue is not Athar's queue requirement. Athar currently plays a selected surah, clipped ayah, or radio; it has no audiobook or lecture-audio queue. Do not add decorative next/previous/queue controls.

Shared-transition APIs exist in resolved animation 1.9.0; limit opt-in to the cover component/shell and retain a no-animation path. No Navigation3, new Grid/FlexBox/MediaQuery, RuntimeShader, or experimental Expressive component dependency is required. Ordinary Material3 components plus Athar motion tokens support this milestone.

Arabic UI uses Noto Sans Arabic (OFL) and Quran uses Amiri Quran (OFL), already bundled and documented. Preserve both; zero UI tracking avoids applying NIA's Latin letterspacing/forced LTR to Arabic. The UI/UX skill's generated fashion/Latin-font direction was rejected as unsuitable; only relevant Compose/accessibility guidance was retained.
