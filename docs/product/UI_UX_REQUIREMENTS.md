# ATHAR MASTER PROMPT ADDENDUM
## Premium Native Android UI/UX, Motion Design, Icons, Micro-Interactions & Visual Polish

This addendum extends ALL previous Athar master prompts.

The requirements below are mandatory for the **native Android application built with Kotlin + Jetpack Compose**.

The goal is not merely to make Athar "look good."

The goal is to make Athar feel like a carefully designed, premium, modern Android application where every interaction feels smooth, intentional, responsive, calm, and polished.

---

# 1. DESIGN QUALITY IS A CORE PRODUCT REQUIREMENT

Treat UI/UX quality as seriously as functionality.

Athar must NOT look like:

- A converted website
- A WebView application
- A generic Material template
- A student project
- A collection of unrelated screens
- A default Jetpack Compose sample
- An admin dashboard adapted to mobile
- A UI generated without a coherent design system

It should feel like one carefully designed native Android product.

The application must have:

- Strong visual hierarchy
- Excellent typography
- Consistent spacing
- Consistent iconography
- Beautiful state transitions
- Meaningful micro-interactions
- Responsive touch feedback
- Smooth scrolling
- Elegant loading states
- Beautiful empty states
- Thoughtful error states
- Subtle animations
- Consistent navigation
- High-quality Arabic RTL support
- Excellent Quran reading experience
- Premium onboarding
- Cohesive light/dark themes if dark mode is implemented

Preserve Athar's existing official colors and branding.

Do NOT redesign the brand.

Polish and expand the existing identity.

---

# 2. RESEARCH BEFORE IMPLEMENTATION

Before finalizing the Android UI, research current professional mobile UI/UX and motion-design practices.

Study official guidance and high-quality references from:

## Android / Material

https://m3.material.io/

https://developer.android.com/develop/ui/compose

https://developer.android.com/develop/ui/compose/animation/introduction

https://developer.android.com/topic/performance/vitals/render

Use these as primary references for native Android behavior.

---

# 3. ICON SOURCES

Use a consistent icon system.

Primary icon source:

## Material Symbols

https://fonts.google.com/icons

Use Material Symbols whenever an appropriate icon exists.

Material Symbols should be preferred because they integrate naturally with Android and provide a large, consistent visual language.

Use appropriate:

- Weight
- Optical size
- Fill state
- Size
- Tint

Do not randomly mix Filled, Outlined and Rounded styles.

Choose an Athar icon style and document it.

---

## Secondary icon source — Lucide

https://lucide.dev/

Use Lucide only when Material Symbols does not provide an appropriate icon or when Lucide better matches an intentionally defined component family.

Do not randomly mix Lucide and Material icons on the same screen.

If both systems are required, define explicit rules in the design system.

Prefer vector icons.

Avoid PNG icons when vectors are available.

Never use emojis as substitutes for proper interface icons.

---

# 4. ANIMATION SOURCES

## LottieFiles

Research:

https://lottiefiles.com/

Use Lottie only where an illustration or expressive animation genuinely improves the experience.

Potential uses include:

- Onboarding
- Empty states
- Successful account creation
- Email verification
- Download completion
- Khatmah completion
- Night Prayer milestone
- No internet
- Search empty state
- Backup success in Admin

Do NOT add a Lottie animation to every screen.

Animations must support the experience rather than decorate it unnecessarily.

### Licensing

Before adding ANY LottieFiles asset:

1. Open its specific asset page.
2. Verify its current license.
3. Confirm that the asset is free.
4. Confirm application use is permitted.
5. Record its author.
6. Record its original URL.
7. Record its license.
8. Store this information in the project documentation.

Create:

`docs/design/THIRD_PARTY_VISUAL_ASSETS.md`

Do not use premium assets.

Do not assume every search result on LottieFiles is free.

Prefer adapting permitted free animations to Athar's existing colors when the license allows modification.

---

# 5. RIVE — OPTIONAL ONLY

Research:

https://rive.app/

Rive may be considered for advanced interactive animation.

However:

**Rive must NOT become a required paid dependency.**

Before using a Rive asset or workflow, verify:

- Current licensing
- Export requirements
- Runtime licensing
- Asset licensing
- Whether the required workflow remains completely free

If creating or exporting the required asset requires payment, do not use it.

Athar must remain buildable without a paid Rive subscription.

For most UI motion, prefer native Jetpack Compose animation.

---

# 6. PREFER NATIVE COMPOSE MOTION

Most application animation should be implemented using native Jetpack Compose animation APIs rather than external animation files.

Use appropriate APIs such as:

- AnimatedVisibility
- AnimatedContent
- animateContentSize
- animate*AsState
- updateTransition
- Crossfade where appropriate
- AnimatedVectorDrawable
- Shared transitions where stable and appropriate
- Spring animations
- Tween animations
- Physics-based animation where appropriate

Use external animation assets mainly for illustrations and special moments.

Native motion should handle ordinary interaction.

---

# 7. CREATE AN ATHAR MOTION DESIGN SYSTEM

Do not choose animation values randomly on every screen.

Create:

`docs/design/MOTION_SYSTEM.md`

Define reusable motion tokens.

For example:

```text
motion.instant
motion.fast
motion.normal
motion.slow

motion.standardEasing
motion.enterEasing
motion.exitEasing

motion.smallSpring
motion.mediumSpring
```

Actual timing values must be selected after testing.

Motion should generally feel quick and responsive.

Avoid making users wait for animation.

---

# 8. MICRO-INTERACTIONS

Implement polished micro-interactions throughout Athar.

Examples:

## Buttons

When tapped:

- Immediate visual feedback
- Appropriate ripple/press indication
- Subtle scale/shape feedback where appropriate
- Loading state when an operation takes time
- Prevent duplicate submissions

Never make a button appear unresponsive.

---

## Favorites

When a user favorites:

- Animate the icon state
- Change fill smoothly
- Provide subtle haptic feedback where appropriate
- Optimistically update when safe
- Roll back elegantly if synchronization fails

Do not display a large popup for a simple favorite action.

---

## Quran bookmarks

When bookmarking an ayah:

- Smooth bookmark transition
- Immediate local response
- Small confirmation
- Haptic feedback where appropriate

The interaction should feel instant.

---

## Adhkar counter

This