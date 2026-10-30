# ATHAR Stitch UI implementation specification

Status: **authoritative implementation bridge**
Baseline branch: `preview/athar-ui-showcase`
Implementation branch: `chatgpt/stitch-ui-integration`
Baseline commit: `12f82f19610d9a4d68250ad93d78af7dbcfa6412`

## Purpose

This document reconciles the approved Google Stitch visual output with the native Android application already present in this repository. Stitch is a **layout, composition, interaction, hierarchy, and visual-reference source**. It is not allowed to replace verified product behavior, canonical religious data, authentication boundaries, offline behavior, or Athar's official brand tokens.

The implementation remains native Jetpack Compose. No WebView, HTML runtime, Tailwind runtime, or generated React layer is permitted.

## Sources of truth and precedence

When two references disagree, use this order:

1. Product/engineering correctness in `docs/product/*`, `docs/features/*`, architecture docs, tests, and real API contracts.
2. Official Athar brand identity and tokens in `docs/design/DESIGN_SYSTEM.md` and `docs/design/BRAND_GUIDELINES.md`.
3. Existing verified native behavior and persisted user data.
4. Stitch screen layout, content hierarchy, component composition, motion intent, and interaction placement.
5. Generated Stitch color values or sample content only when they do not conflict with items 1-4.

This specifically resolves the generated Stitch primary color `#005039` versus the established Athar brand primary `#24694F`: **use `#24694F` as the application primary**. Do not fork the app into two competing green palettes.

## Canonical brand tokens

### Light — default

- Primary: `#24694F`
- Strong emerald: `#19563F`
- Page/background: `#F4F3ED`
- Surface: `#FBFCF8`
- Raised surface: `#FFFFFF`
- Primary text: `#202923`
- Border: `#D9E2D9`
- Copper accent: `#A85F25`

### Dark

- Page/background: `#101815`
- Surface: `#17231E`
- Raised surface: `#1E2D26`
- Secondary surface: `#283A31`
- Primary: `#8ADBAF`
- Primary text: `#F2F6F1`
- Border: `#40564A`
- Accent: `#F0BD78`

### Typography and geometry

- Interface: bundled **Noto Sans Arabic**.
- Quran text only: bundled **Amiri Quran**.
- Arabic is default and RTL-first.
- Light theme is the default theme.
- 4/8dp spacing rhythm.
- 16dp compact horizontal page margin.
- 24dp normal section gap.
- Minimum interactive target: 48dp.
- No Quran letter spacing.
- Use the repository's Material 3 shape scale unless a screen-specific Stitch composition explicitly needs the 28dp expressive Home hero.

## Official logo

Use `android/app/src/main/res/drawable-nodpi/athar_logo.png`.
Do not redraw, recolor, replace, append a duplicate wordmark, or download the generated Stitch copy.

## Navigation contract

Top-level destinations remain exactly:

1. Home
2. Quran
3. Knowledge
4. Adhkar
5. Account

Night Prayer is a prominent nested destination, not a sixth bottom-navigation item.

Compact width: bottom navigation.
Wide width: navigation rail.
Deep links, saved state, auth gating, and back behavior must remain functional.

## Stitch inventory received and mapped

The received Stitch source contains 20 unique functional screen IDs plus four explicit dark/night visual variants.

| Stitch ID | Screen | Native target | Coverage at baseline | Action |
|---|---|---|---|---|
| A01 | Splash | startup/theme resources + MainActivity launch path | Partial | restyle only; preserve startup performance |
| A02 | Onboarding | `auth/OnboardingScreen.kt` | Present | migrate composition and motion |
| A07 | Login | `auth/AuthScreens.kt` | Present | migrate visual layer; preserve validation/auth |
| B01 | Home | `core/ui/HomeScreen.kt` | Present | first migration target |
| B01 dark | Home night variant | same Home + dark theme | Present theme, variant not exact | derive from canonical dark tokens |
| C01 | Quran Hub | `quran/QuranScreen.kt` | Present | migrate hub composition |
| C04 | Mushaf reader | `quran/QuranScreen.kt` + `QuranMushafPage.kt` | Present | preserve continuous Mushaf + long-press actions |
| C04 dark | Mushaf night reading | same reader | Present theme, variant not exact | adapt without bottom reading bar |
| C11 | Tafsir & Tadabbur | `quran/TafsirScreen.kt` | Present | migrate layout |
| D02 | Audio player | `media/AudioScreen.kt` | Present | migrate controls around Media3 behavior |
| E01 | Adhkar Hub | `adhkar/AdhkarScreen.kt` | Present | migrate hub |
| E06 | Dhikr reader/counter | `adhkar/AdhkarScreen.kt` | Present inside module | split/recompose only if navigation benefits |
| E06 dark | Night dhikr reader | same | Present theme | canonical dark adaptation |
| F01 | Knowledge Hub | `learning/KnowledgeHubScreen.kt` | Present | migrate composition |
| G01 | Video lesson watch | `learning/WatchScreen.kt` | Present | preserve player/comments/series behavior |
| I01 | Books catalog | `books/BooksCatalogScreen.kt` | Present | migrate catalog |
| I04 | Book detail/download | `books/BookDetailScreen.kt` | Present | preserve non-destructive local assets |
| J01 | Night Prayer Hub | `nightprayer/NightPrayerScreen.kt` | Present | migrate hub and keep nested tools |
| J01 dark | Night Prayer dark | same | Present theme | canonical dark adaptation |
| K01 | Khatmah planner | `khatmah/KhatmahScreen.kt` | Present | migrate planner |
| L01 | Global search/discovery | no dedicated all-domain Android screen at baseline | Gap | implement native aggregated search contract; do not fake results |
| M01 | Account overview | account route in `core/ui/MainShell.kt` | Present | extract/restyle progressively |
| M05 | Downloads/offline storage | distributed book/media/offline infrastructure | Partial UI | add central native offline/download screen |
| M08 | Reading/appearance settings | settings currently embedded in account/profile + Quran controls | Partial UI | extract a dedicated settings destination |

## Home migration rules

The Stitch Home hierarchy is accepted with these corrections:

1. Use the official app bar/logo from native resources.
2. Hero uses the Athar emerald/copper palette and real signed-in display name.
3. Do not hardcode city, Hijri date, prayer/third-of-night time, Quran position, dhikr progress, or percentages.
4. Quran continuation must come from the real reading-position/wird state.
5. Night Prayer status must come from Night Prayer calculations/repository.
6. Upcoming lessons must use `UpcomingHomeSection` / real server data.
7. Activity metrics use the existing authenticated `/home` payload.
8. Latest lessons/books remain backed by the existing `/home` response.
9. Any daily quote/benefit section is hidden until backed by a reviewed data source. No fabricated attribution is allowed.

## Quran non-negotiables

- Printed-Mushaf-like continuous verse flow.
- Never force one ayah per card or one ayah per line.
- Standard verse markers only.
- Long-press/tap actions may expose bookmark, copy, share, tafsir, and audio actions.
- No persistent reading bar under the Mushaf page.
- No side verse numbering.
- Do not fabricate or truncate Quran data.
- Keep Arabic Quran typography independent from interface typography.

## Motion

- Prefer native Compose motion.
- Respect reduced-motion settings.
- Press feedback is subtle and fast.
- Shared/hero transitions are reserved for meaningful section entry and detail continuity.
- No decorative animation that delays interaction.
- Home may be visually expressive; internal screens should remain premium, calm, and distinct instead of copying the same giant arc/hero everywhere.

## Data and behavior safety

Visual migration must not:

- replace repository/API state with constants;
- delete local book/PDF assets on metadata edits;
- break Media3 background audio;
- weaken auth or ownership boundaries;
- remove Room/DataStore/offline behavior;
- change Quran canonical indexing;
- bypass notification consent;
- fabricate test success;
- silently drop English/LTR support.

## Implementation order

### Gate 0 — foundation
- freeze canonical tokens;
- preserve official logo;
- verify navigation shell and auth boundaries;
- create screenshot/reference checklist.

### Gate 1 — Home
- Home shell/header;
- atmospheric hero;
- real continuation/quick actions;
- Upcoming section;
- real activity/latest content;
- compact + wide adaptation;
- Arabic RTL + English LTR;
- light + dark;
- reduced motion.

### Gate 2 — Quran
- Quran hub;
- Mushaf reader;
- tafsir;
- audio entry;
- search/navigation sheets;
- long-press actions.

### Gate 3 — worship and learning
- Adhkar hub/reader;
- Night Prayer hub/tools;
- Knowledge;
- scholars/series;
- lesson watch/upcoming.

### Gate 4 — library and personal
- Books catalog/detail/reader;
- Khatmah;
- Account;
- Reading/appearance;
- Downloads/offline.

### Gate 5 — missing dedicated surface
- Global all-domain search/discovery backed by actual APIs/indexes.

## Acceptance gates for every migrated screen

A screen is not complete until all applicable checks pass:

- native Compose build succeeds;
- no placeholder or fabricated religious/product data;
- correct Arabic RTL;
- English LTR remains usable;
- default Light theme correct;
- dark variant does not reduce Quran readability;
- font scale does not clip primary actions;
- touch targets >= 48dp;
- TalkBack labels/order reasonable;
- offline/loading/error/empty states preserved;
- navigation/back/deep links preserved;
- actual data mutation behavior preserved;
- screenshot compared against Stitch reference at the same viewport;
- physical-device validation queued/performed before release integration.

## Merge policy

Do not merge the entire visual migration as one blind change.
Work in small reviewable checkpoints. A later Codex integration pass may pull verified commits/chunks from `chatgpt/stitch-ui-integration` into its active integration branch after build/test validation.
