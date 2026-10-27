---
name: athar-premium-compose-design
description: Build, review, and validate Athar's native Kotlin and Jetpack Compose screens using its approved premium design system. Use for Athar Android UI, reading, navigation, audio presentation, motion, and device visual acceptance; excludes unrelated backend development.
---

# Athar Premium Compose Design

Read this skill for each Athar UI task. Existing functionality, private-data ownership, Quran correctness, and provider restrictions come first. Then prefer official supported Android APIs, approved Athar decisions, verified reference code, and third-party advice, in that order.

## Project contracts

- The Android project is `android/`; primitives are in `org.athar.app.core.ui`. Improve existing components rather than creating a parallel theme.
- Inspect `android/app/build.gradle.kts` before choosing APIs. Initial baseline: Compose BOM `2025.08.00`, Navigation Compose `2.9.3`, Media3 `1.8.0`, minSdk 26. Do not migrate navigation or upgrade dependencies just to follow a newer skill example.
- `Theme.kt`, `PremiumComponents.kt`, `AtharIcons.kt`, and `Motion.kt` are runtime token sources. The repository's `docs/design/ATHAR_PREMIUM.md` defines the screen map; `docs/design/PREMIUM_SOURCE_RESEARCH.md` pins source files and revisions.
- Books currently provide catalog/details/download. Do not invent an EPUB reader, audiobook catalog, or book reading progress.
- The paused backend queue resumes only after the user's visual-review approval. UI edits, builds, and authorized device installation already have approval.

## Focused guidance

Read the relevant installed skill and only its needed references:

| Work | Local skill |
|---|---|
| Screen hierarchy and native components | `../mobile-android-design/SKILL.md` |
| Semantic theme, navigation, states | `../material-design-3-ui/SKILL.md` |
| Expressive emphasis/dimensions | `../m3-expressive/SKILL.md` (verify availability) |
| Component signatures and slots | `../compose-component-design/SKILL.md` |
| Player ownership/effects/lifecycle | `../compose-state-and-effects/SKILL.md` |
| Animation API choice | `../compose-animations/SKILL.md` |
| Motion purpose and reduced motion | `../animate-compose/SKILL.md` |
| Stable lazy cards and scroll | `../optimizing-lazy-layouts/SKILL.md` |
| Measured performance issues | `../jetpack-compose-performance/SKILL.md` |
| Insets and keyboard | `../edge-to-edge/SKILL.md` (avoid newer unsupported APIs) |
| Device observation/evidence | `../android-adb-control/SKILL.md` |

Unselected sibling skills are not implicitly installed. Use pinned upstream links from `docs/engineering/PREMIUM_SKILLS.json` if needed; inspect scripts before execution.

## Visual rules

- **Now in Android is primary:** explicit Material type roles, content-led sections, restrained surfaces, one navigation hierarchy, stable lazy keys/types, previewable components. Do not copy its forced LTR text styles.
- Retain Athar green/ivory/copper and the unchanged official logo. Use semantic theme roles, paired foregrounds, and independent light/dark palettes. Do not derive readable text colors directly from cover art.
- UI font: bundled Noto Sans Arabic variable (400/500/600/700), zero Arabic tracking. Define all display/headline/title/body/label roles explicitly; support scaling without clipping. Quran: preserve Amiri Quran, RTL, verse/bismillah bytes, and existing generous line heights.
- Spacing tiers: 4/8/12/16/20/24/32dp. Compact screen gutter 20dp; 24/32dp expanded. Bound long-form reading width. Use start/end alignment and avoid fixed heights around wrapping text.
- Icons: existing Material Symbols in `AtharIcons`; 24dp standard, 20dp metadata, 32dp media action; minimum 48dp interactive target. Directional arrows mirror in RTL; playback does not. Decorative icons have null descriptions; standalone controls get localized names and native selected/disabled semantics.
- Compact navigation has five destinations; expanded/landscape follows the established rail policy. Preserve tab saved state, detail back behavior, system-bar/IME padding, and immediate private-content removal at logout.
- Cards use medium/large theme shapes and tonal surfaces. Do not enclose every section in a card. Floating navigation/player may use a near-opaque surface and edge highlight. API27 fallback is the default: no backdrop blur, runtime shader, or list blur.

## Heroes and motion

Translate Wonderous identity continuity and layering natively. Keep one prominent Home hero; use compact content identity elsewhere. Do not overlap the YouTube viewport with custom controls.

`AtharMotion` is the project standard (rung 0): fast 140ms, normal 220ms, slow 320ms; `Enter`, `Exit`, `Standard`; shared stiffness/damping tokens. These are Athar design decisions, not M3 constants.

- Top-level peers cross-fade. A cover/player transforms into detail only when both ends share the same supported transition scope and stable identity.
- Check installed Compose source before adding shared transitions. A reveal/fade/scale is not a shared-element transition; describe evidence accurately.
- Use `animate*AsState` for a single target, `AnimatedContent` for swapped content, `AnimatedVisibility` for removal; reserve `Animatable` for imperative/gesture interruption. Controls remain enabled during motion.
- Read per-frame values in graphics/draw/layout lambdas. No perpetual decoration. Entrance reveals are keyed by stable content identity, not every recomposition.
- `reducedMotion()` snaps to a legible final state. Never animate Quran text rearrangement or authentication removal.

## Audio

The inspected Jetcaster player is a mock. Adapt its hierarchy and state-driven controls, not its simulated timer. Preserve `PlaybackService`, Media3, URL allowlisting, account lifecycle, notification, reciter/edition selection, ayah clipping, and follow-along timing.

Use one service-backed state for expanded and mini-player. Reconnect to controller metadata/state after navigation/recreation; closing a screen must not reset playback. Show seek/speed/repeat/queue controls only when backed by actual commands. Live radio cannot show fake seek/progress. Do not invent next/previous queue semantics.

## Compose example

```kotlin
@Composable
fun AtharContentIdentity(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        if (subtitle.isNotBlank()) Text(subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Separate app wiring from plain rendering when it improves reuse. Collect active flows with `collectAsStateWithLifecycle`; dispose controller listeners. Use primitive stable lazy keys/content types, and keep IO out of composition.

## Validation

Read [device review](references/device-review.md) before installing/capturing. Build the actual debug variant with the documented USB API origin. Preserve app data/preferences; select an exact detected serial privately. Never bypass a secure keyguard.

Review Arabic RTL, English LTR, light/dark, long labels/font scaling, portrait/landscape, keyboard/insets, loading/empty/error/retry, back/restore, and 48dp controls. Verify Quran assets unchanged and audio playback/background/navigation/stop/logout. Use existing test fixtures; do not send email or mutate genuine worship records for visuals.

Capture actual screenshots/recordings and state exactly what ran, failed, or remains untested. Debug visuals do not prove release frame-rate performance. Stop at the requested visual-review checkpoint; unresolved acceptance remains open.
