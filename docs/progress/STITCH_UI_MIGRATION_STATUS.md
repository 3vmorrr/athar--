# Stitch UI migration status

Branch: `chatgpt/stitch-ui-integration`  
Base: `preview/athar-ui-showcase@12f82f19610d9a4d68250ad93d78af7dbcfa6412`

This file tracks **actual native code/UI state**, not planned task counts.

## Status meanings

- **MIGRATED** — Stitch hierarchy/composition was applied while preserving real native behavior.
- **IMPLEMENTED** — a dedicated native surface that was genuinely missing has now been added.
- **REVIEWED-PRESERVED** — the native implementation was reviewed against Stitch and deliberately retained because replacing it would regress behavior, correctness, startup, accessibility, or data integrity.
- **THEME-VALIDATED** — the requested dark/night variant is provided by the canonical Material theme without hardcoded per-screen colors.
- **PARTIAL-BY-CONTRACT** — the visual reference contains features/data for which the application currently has no real storage/API contract; fake controls/data were intentionally not added.

## Screen matrix

| ID | Screen | Current migration state | Notes |
|---|---|---|---|
| A01 | Splash | **REVIEWED-PRESERVED** | Uses Android SplashScreen API, official `athar_logo`, canonical page color; no HTML splash runtime. |
| A02 | Onboarding | **REVIEWED-PRESERVED** | Native 4-page pager, real RTL/LTR, reduced motion, Skip/Continue. |
| A07 | Login/Auth | **REVIEWED-PRESERVED** | Native validation, OTP, forgot/reset, IME-safe flow retained. |
| B01 | Home | **MIGRATED** | Compact expressive hero, real reading continuation, real home/upcoming data, corrected quick-action hierarchy. |
| B01 dark | Home night variant | **THEME-VALIDATED** | Canonical Athar dark palette; no hardcoded Stitch night colors. |
| C01 | Quran Hub | **MIGRATED** | Calm internal layout, real continuation, direct canonical 114-surah index. |
| C04 | Mushaf Reader | **REVIEWED-PRESERVED** | Existing reader already has continuous Mushaf flow, inline ayah markers, long-press actions, no bottom reading bar. |
| C04 dark | Mushaf night reading | **THEME-VALIDATED** | Reader inherits canonical dark surfaces/text; Quran font stays independent. |
| C11 | Tafsir | **MIGRATED** | Refined reading surfaces; Quran text and tafsir content remain real/canonical/API-backed. |
| D02 | Audio Player | **MIGRATED** | Visual header compacted; Media3 playback/background behavior retained. |
| E01 | Adhkar Hub | **MIGRATED** | Giant section hero replaced with calmer contextual header. |
| E06 | Dhikr Reader/Counter | **REVIEWED-PRESERVED** | Integrated native counter/reminder/sync flow retained rather than split into a fake duplicate screen. |
| E06 dark | Night dhikr | **THEME-VALIDATED** | No hardcoded colors; Material dark contract drives the reader. |
| F01 | Knowledge Hub | **MIGRATED** | Uses real retained lesson continuation/progress, not Stitch percentages. |
| G01 | Lesson Watch | **REVIEWED-PRESERVED** | Existing player-top + description/actions + collapsible comments + related/series flow already matches product requirement. |
| I01 | Books Catalog | **MIGRATED** | Quiet library intro/search composition over real catalog API and pagination. |
| I04 | Book Detail | **MIGRATED** | Calm metadata card; rights, source, favorite, secure download and local-reader behavior preserved. |
| J01 | Night Prayer Hub | **MIGRATED** | Night visual identity retained but hero is compact/inset; private data/conflict/rejection behavior preserved. |
| J01 dark | Night Prayer dark | **THEME-VALIDATED** | Canonical dark tokens drive the section. |
| K01 | Khatmah Planner | **REVIEWED-PRESERVED** | Existing calm `AtharHeroSurface`, real plan metrics/conflicts/reminders/editor retained. |
| L01 | Global Search | **IMPLEMENTED** | Native search across canonical Quran + books + lessons + series + scholars + adhkar. No fake hadith domain. |
| M01 | Account Overview | **MIGRATED** | Giant profile hero replaced by quiet identity card; tools kept real. |
| M05 | Downloads/Offline | **IMPLEMENTED / PARTIAL-BY-CONTRACT** | Dedicated offline library for real downloaded books, real file sizes/open/delete. Stitch audio/lesson downloads are not shown because those storage contracts do not exist yet. |
| M08 | Reading/Appearance | **IMPLEMENTED / PARTIAL-BY-CONTRACT** | Dedicated real theme + Quran font-size settings. Unsupported Stitch toggles are not rendered as fake controls. |

## Implemented correctness rules

- Official brand primary remains `#24694F`; generated Stitch `#005039` is not the app primary.
- Official logo remains `android/app/src/main/res/drawable-nodpi/athar_logo.png`.
- Arabic remains default and RTL-first; English/LTR resources remain paired.
- Light remains the default theme.
- Quran text remains canonical and uses Amiri Quran.
- No fake city, Hijri date, prayer time, third-of-night time, progress percentage, dhikr count, user statistic, religious quote, or attribution was inserted.
- No fake hadith catalog/search source was invented.
- Book download metadata now stores the real server-provided title locally alongside the verified PDF/digest.
- Offline-library disk scans and deletion run off the UI thread.
- Book local copies remain explicitly removable without changing favorites or remote catalog metadata.
- Home/Quran/Adhkar/Night screens contain no hardcoded light/night color forks; they inherit the canonical theme.

## Build/compile repairs completed on this branch

The baseline branch could not reach Kotlin compilation. During this migration the following blockers were repaired rather than hidden:

- Gradle `java.io.File` Kotlin DSL resolution.
- generated assets Provider/source-set registration.
- missing Compose/Kotlin imports (`dp`, `rememberSaveable`, coroutine `launch`, etc.).
- stale `MarkToggle` call contract.
- Khatmah reminder planner invocation and composable-scope metrics.
- Night-plan suspend Quran lookup.
- stale notification string references.
- Quran Material3 opt-in/import.
- Tafsir delegated smart-cast.
- Android PCM 16-bit constant.
- WorkManager 2.10.x flow usage/import and worker response-body nullability.
- invalid `CoroutineWorker.onStopped` override.

A full `:app:compileDebugKotlin` GitHub Actions gate passed on run **41108335586** at commit `94632f6d884f21ccb3f5621796d16f1e12c3c06c` before the final offline-library I/O polish. The latest head must continue to pass the CI gate.

## Deliberately omitted Stitch-only data/controls

These are not counted as defects until a real product/data contract exists:

- Home city/location and Hijri/time-context badges.
- Hardcoded third-of-night start/end times.
- Fake 75% Quran progress.
- Fake daily dhikr 18/24 progress.
- Attributed daily benefit/quote without a reviewed source.
- Hadith search results without a canonical hadith source.
- Audio/lesson offline storage totals when the app currently persists verified book PDFs only.
- Reading settings such as alternate Quran font families, continuous-scroll toggle, automatic ayah audio highlight, default offline tafsir, or cloud preference backup where no corresponding persisted setting/behavior exists.

## Validation gates

1. **Compile gate:** `:app:compileDebugKotlin` — must pass on latest head.
2. **Unit gate:** `:app:testDebugUnitTest` — enabled in CI after compile.
3. **Static review:** Arabic/English resources, theme inheritance, navigation and existing feature contracts.
4. **Device gate:** physical-device/emulator validation for touch, IME, Media3, WorkManager, notification permission flows, reader rendering, RTL/LTR, font scale and screenshots.
5. **Merge rule:** no blind merge into the Codex integration branch; only validated commits/chunks should be integrated.

## Remaining work after CI green

The visual migration itself is no longer blocked by missing dedicated Stitch screens. Remaining work is validation and visual-diff refinement on real device(s): screenshot comparison at matched viewports, large-font/accessibility pass, English/LTR pass, dark pass, and instrumentation/device journeys.
