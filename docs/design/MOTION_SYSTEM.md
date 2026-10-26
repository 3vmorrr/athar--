# Athar native motion system

Applies to the new native UI/UX addendum in `../product/UI_UX_REQUIREMENTS.md`. The received attachment ends at the beginning of its Adhkar-counter section; no missing text is assumed. Existing complete feature requirements still apply.

## Tokens implemented in Motion.kt

| Token | Initial value | Purpose |
|---|---|---|
| Instant | 0ms | security boundaries, reduced-motion state changes |
| Fast | 120ms | icon/selection/feedback changes |
| Normal | 200ms | small page/content changes |
| Slow | 280ms | occasional explanatory transitions only |
| Standard | FastOutSlowIn | ordinary state changes |
| Enter | LinearOutSlowIn | appearing content |
| Exit | FastOutLinearIn | disappearing content |
| Small spring | stiffness700, damping0.85 | subtle press feedback; never delays the actual action |

Values are implementation defaults, not final performance certification. Test on the emulator/physical-device matrix and adjust centrally rather than inventing timings per screen. Screen transitions use at most1% scale with a short opacity change. Avoid large travel, bouncing text, repeated celebration, looped sacred text or motion that hides errors. User input dispatches immediately; network duration is separate from animation.

Authentication transitions are intentionally not animated: logout, account suspension and removal of verification access must remove private content immediately. Ordinary authenticated page transitions use the shared wrapper. State transitions must not reuse one account's ViewModel/cache for another account.

Android animator-disable preference is checked at composition and on resume. Compose also observes the platform duration scale. Reduced motion uses immediate transitions; manual pager navigation skips programmatic animation. System haptic settings remain authoritative. No forced vibration or audio notification is part of decorative UI feedback.

Use Material ripple for press feedback. Bookmark/favorite actions should use a small icon-state transition and brief feedback, with pending state and rollback on failure. Counters must acknowledge durable local changes quickly, retain completed-cycle UUIDs across resets, and never count animation as a completion.

Original onboarding illustrations are native Canvas geometry with Athar's colors, not purchased animation assets. Material Symbols are vector paths; no emoji UI icons. There are no Lottie or Rive runtime/asset dependencies. Introducing an external animation requires a specific free asset/license review recorded in THIRD_PARTY_VISUAL_ASSETS.md.

## Research and validation

Primary references inspected2026-10-25:

- https://developer.android.com/develop/ui/compose/animation/introduction
- https://developer.android.com/topic/performance/issues/render
- https://m3.material.io/
- https://lottiefiles.com/ (contains both free and premium assets; landing-page marketing is not an asset license)
- https://rive.app/ (optional; no paid editor/export workflow adopted)

Measure frame time, startup, scroll and memory with release/profileable builds; report actual device/configuration. A passing debug build or attractive screenshot does not establish60/90/120fps. Ensure large text, phone landscape, tablet, RTL/LTR, system-disabled animation, screen reader, offline/error and repeated-tap behavior. Executed outcomes belong in `../testing/TEST_REPORT.md`.
