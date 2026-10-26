# Athar native motion system

Applies to the native Android UI. Motion must support comprehension and continuity without turning worship flows into gamified or attention-seeking experiences.

## Tokens implemented in Motion.kt

| Token | Current value | Purpose |
|---|---:|---|
| Instant | 0ms | security boundaries and reduced-motion state changes |
| Fast | 140ms | icon, selection and compact feedback changes |
| Normal | 220ms | ordinary content/page transitions |
| Slow | 320ms | rare explanatory transitions only |
| LoadingPulse | 1000ms | restrained skeleton opacity pulse |
| Standard | FastOutSlowIn | ordinary state changes |
| Enter | LinearOutSlowIn | appearing content |
| Exit | FastOutLinearIn | disappearing content |
| Small spring | stiffness 620, damping 0.88 | press/selected-state feedback |
| Soft spring | stiffness 420, damping 0.90 | calm page-content settling |

The premium-v2 pass intentionally keeps motion small. Screen content uses short opacity plus at most a 1.5% scale settle. Navigation icons use a subtle selected-state scale. Cards use small press feedback. Sacred text is never bounced, looped, celebrated, or used as a decorative animation target.

Authentication transitions remain immediate: logout, suspension and removal of verified access must remove private content without an animated delay. Network duration is never disguised as animation.

Android animator-disable preference is checked at composition and on resume. Reduced motion replaces animated transitions with immediate state changes. System haptic settings remain authoritative.

## Interaction language

- Material ripple remains the default touch acknowledgement.
- Bookmark/favorite state uses the existing outline-to-filled micro-transition and a small haptic.
- Navigation selection uses a small scale response rather than large travel.
- Counter actions remain immediate and durable; animation never counts as a completion.
- Error, offline and synchronization states remain readable when all animation is disabled.
- Worship milestones must use calm progress/color changes, not confetti, flames, bouncing text or shame-oriented streak treatment.

## External animation policy

The current premium-v2 implementation deliberately stays native Compose-first. No Rive or Lottie runtime dependency was introduced in this pass, so APK size, ARM32 compatibility and startup behavior are not changed by an animation engine.

Rive or Lottie may be added later only for a small number of expressive assets such as onboarding, splash or a neutral empty state after:
1. the exact asset is approved,
2. its license is recorded,
3. Arabic/RTL and reduced-motion fallbacks exist,
4. ARM32/device memory is measured,
5. the asset does not animate Quran text or worship completion in a gamified way.

## Research and validation

References reviewed for the premium-v2 pass:

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/develop/ui/compose/animation/introduction
- https://developer.android.com/topic/performance/issues/render
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://mobbin.com/
- https://rive.app/
- https://lottiefiles.com/

Material 3 Expressive is treated as direction, not a requirement to adopt alpha APIs. Athar currently stays within the repository's existing Compose BOM and Material 3 dependency until a dedicated dependency upgrade is tested.

## Required validation before merge/release

The motion pass still needs the normal Android gate on the development workstation and physical phone:

- assembleDebug
- testDebugUnitTest
- lintDebug
- Arabic RTL light
- English LTR light
- dark theme
- reduced/system-disabled animations
- 130%+ font scale
- phone landscape
- tablet
- TalkBack focus order
- repeated taps
- offline/error states
- release/profileable frame-time and jank measurements

A visually attractive screenshot is not performance evidence.
