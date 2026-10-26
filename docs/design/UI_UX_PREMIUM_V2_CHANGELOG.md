# Athar Premium UI/UX v2 — change log and review

## Codex incremental reconciliation — task21.1,26 October2026

Pinned source: UI tip `0e6f4ba6e3bb94075ebc38f1187ec28c7db75d32`, common ancestor6989a8a. Newer Codex functional checkpoints309efa5 (owned WorkManager),13402f6 (bounded cache) andc5062b6 (authentication races) are retained. This section describes the current integration slice; the original branch review below remains historical and does not certify all screens as merged/tested.

- Integrated: shared `PremiumComponents.kt` spacing/hero/section/quick-action/status/metric primitives and the reviewed `Motion.kt` timing/spring tokens. Imported design documentation only; no whole-branch merge or older repository/data replacement.
- Modified intentionally: explicit hero foreground color; heading/button accessibility roles; stacked section action at large font; real disabled quick-action semantics/feedback; optional caller flag can further reduce motion but cannot enable it against the system preference. One remembered SeekableTransitionState drives animateTo/snapTo, preserving AnimatedContent's stable current-page/keyed state rather than swapping the container and losing a form draft. No disabled-motion press scaling. Mid-transition outgoing removal (two test frames for effect/composition handoff) and draft retention are separate test gates.
- Not integrated in this task: Home/navigation, auth/account, Quran/audio/tafsir, Adhkar, Night Prayer, watch/discussion/scholar/series/upcoming and loading-screen presentation. Each is separately queued and will be reconciled with newer logic/tests when touched. Shared primitives are functional infrastructure, not a claim that those screens have been redesigned here.
- Preserved: ProtectedRead states/retries, private ciphertext/pending operations, all repositories/sync/API/schema/provider logic, account boundaries, Quran text/fonts and immediate MainActivity authentication gating. No dependency upgrade, external animation runtime or new haptic was added.
- Validation:47 JVM/production+test APK/lint and292 resources PASS. API27 Arabic4 component +3 protected/normal cases PASS; English8 component/protected/reader/counter/watch cases PASS; main API1 PASS and scoped startup zero matching critical failures. Light/dark/100–200-percent font/role/disabled/contrast/draft/motion cases and Arabic/English captures reviewed. Earlier single-frame failures and final two-frame effect/composition budget are explicit in TEST_REPORT. Full-app tablet/TalkBack/release-jank acceptance remains21.15–21.17/22.*, irrespective of this component slice. The next checkpoint receipt records the resulting Codex commit SHA without a self-referential hash.

The user's current incremental-integration policy supersedes the original suggestion to merge the entire branch at once. Every accepted slice still requires its applicable build, safety, device, language/theme and reduced-motion gates.

### Task02.5 — Night Prayer conflict surface, verified slice

Validation:47 JVM/build/lint/305 resources PASS; final API27 Arabic10/10, English15/15 and main API1/1 PASS; Arabic/English portrait/landscape fixture captures reviewed. Native review adapts at180-percent text/short height; dark150–200-percent, loading/error and retained-editor gates pass. Earlier footer/lint/lazy-node/resource harness failures remain in TEST_REPORT. UI tip0e6f4ba rechecked unchanged before commit. The resulting commit SHA is recorded in the next checkpoint receipt; no full-branch merge.

Shared foundation commit: `981a9e1293b4270b5e9d2accc8f0a50174bd0763` (pushed/verified). Re-read the current Night Prayer file and pinned premium diff before editing. Reconciled the premium history surface/hero and section transition with new durable per-record conflict observation/review; preserved ProtectedRead/retry/gates and the newer owned sync/cache/auth logic. The hero is inside the history LazyColumn rather than a tall fixed wrapper above every section, preserving space on small/landscape screens. Calendar/tools presentation and all other premium screens remain separate queued slices. No older ViewModel/repository was copied over newer logic. Final build/device evidence is recorded immediately above; broader Night Prayer tools/editor/tablet/TalkBack/release acceptance remains open.

### Task02.6 — watch/account recovery, verified slice

Reviewed current files against pinned0e6f4ba before editing. Integrated only headlineSmall for watch title and larger bordered low-surface comments preview. Preserve current player/source identity, lazy/first-party comments, playlist/moderation logic and all newer private checkpoint ownership/recovery. Full account presentation stays separately queued.Final50 JVM/build/lint/306 resources PASS; API27 Arabic5/English15 plus main API1 PASS; Arabic/English watch/comments captures inspected, dark150-percent account error/retry checked. Keep ready player composition during ordinary metadata switching; isolate unreadable checkpoints behind retry without writes. Earlier USB reverse loss/4 failed English HTTP cases is retained; restored mapping/full rerun passed. Full provider/tablet/TalkBack/release profiling and account presentation remain later gates. The next receipt records the resulting integration commit SHA.

### Task02.7.1 — ownership-only overlapping call sites, verified

Reviewed two-sided Quran/Adhkar/Night tools/account/auth/watch/series changes against pinned0e6f4ba. This task changes actor binding and pending-auth composition identity, retaining current presentation and already accepted premium slices. No additional presentation is imported: broader visual reconciliation stays in its21.* tasks so an ownership safety fix does not become an unrelated screen redesign. Auth removal stays immediate, without animated signed-in content retention. Final53 JVM/build/lint/306 resources PASS; API27 Arabic10/English32 +main API1 PASS, including ordinary reader/counter/prayer/watch/auth and retained data regression. No new presentation/animation is claimed. The user’s new immersive-hero refinement is queued as21.18 documentation before any such UI implementation; later shared/screen tasks are explicit and retain the functional roadmap.

Date: 2026-10-26  
Branch: `uiux/athar-premium-v2`  
Base branch: `codex/android-ecosystem`  
Base commit at branch creation: `ce0fb4237b29bc088bd570e687e6becccc4fd330`

## Purpose

This branch is a presentation-layer pass for the native Android application. It strengthens Athar's visual identity, information hierarchy, motion language and interaction polish while deliberately preserving application behavior.

The branch is intentionally isolated so the ongoing backend/core implementation can continue independently.

## Hard guardrails

This pass must not change:

- PHP backend behavior
- REST routes or payloads
- database migrations or schemas
- authentication/session rules
- Room entities/DAO contracts
- repositories or synchronization semantics
- Quran canonical data
- lesson progress rules
- Adhkar counter semantics
- Night Prayer ownership/privacy rules
- notification or engagement business logic
- provider behavior

At the time this document was created, the branch diff against `codex/android-ecosystem` contained Android Compose UI files and design documentation only. No backend, migration, API or database file was changed.

## Design direction

Athar should feel calm, warm, Arabic-first and premium rather than like a generic Material sample or analytics dashboard.

The visual language uses the existing Athar palette and typography:

- green remains the primary brand/action color
- warm neutral surfaces remain the default background family
- the existing secondary warm/gold tone is retained for supporting emphasis
- Noto Sans Arabic remains the UI typeface
- Amiri Quran remains reserved for Quran text
- rounded geometry is retained, but repeated raw Material cards are replaced by a smaller set of recognizable Athar surfaces

The design deliberately avoids excessive Islamic ornament. Identity comes from hierarchy, color, spacing, typography, motion and repeated component behavior rather than decorative patterns on every screen.

## Shared UI system added

### `PremiumComponents.kt`

Added reusable visual primitives:

- `AtharSpacing`
- `AtharHeroSurface`
- `AtharSectionHeader`
- `AtharQuickAction`
- `AtharStatusPill`
- `AtharMetric`

These provide one shared language for hero areas, quick actions, status treatment, section structure, metrics and spacing.

They contain presentation behavior only.

## Motion v2

### `Motion.kt`

Updated the shared motion tokens:

- Fast: 140ms
- Normal: 220ms
- Slow: 320ms
- small spring: stiffness 620 / damping 0.88
- soft spring: stiffness 420 / damping 0.90

The ordinary page transition now uses a restrained fade plus a small spring settle.

Reduced-motion behavior remains supported and bypasses the decorative animation.

No animation is allowed to delay authentication/security state changes.

### Motion philosophy

Use motion to communicate continuity and state, not to gamify worship.

Allowed examples:

- subtle card press
- selected navigation state
- bookmark/favorite outline-to-filled feedback
- soft content transition
- calm progress transition
- restrained loading pulse

Avoid:

- confetti
- flames
- bouncing Quran text
- looping sacred text
- exaggerated worship milestone celebrations
- shame/guilt streak animation

## Home redesign

### `HomeScreen.kt`

The home screen was reorganized away from a statistics-first dashboard and toward a journey-first entry point.

Changes:

- strengthened the welcome hero
- kept Quran reading as the strongest primary action
- added a consistent 2x2 quick-action area:
  - Quran
  - Knowledge
  - Adhkar
  - Night Prayer
- kept Upcoming/Live content prominent
- placed lessons and books before aggregate activity metrics
- moved activity totals lower so they support the journey instead of dominating it
- restyled the Adhkar invitation
- unified lesson/book card geometry and press feedback
- improved the empty-state surface

No home API fields or backend response assumptions were added.

## Navigation polish

### `MainShell.kt`

Changes:

- navigation rail and bottom bar now use Athar surface colors rather than raw defaults
- selected destination receives a restrained animated icon scale
- selected indicator uses the existing primary container
- top app bar uses a calmer surface treatment
- Knowledge receives an Athar hero header
- Account receives a personal hero header
- Account tool changes use the common reduced-motion-aware page transition

Navigation routes, arguments and destination logic are unchanged.

## Quran experience

### `QuranScreen.kt`

The Quran entry screen was changed from four large plain buttons into a Quran Hub.

The hub now provides:

- a Quran hero surface
- a primary Read action
- four consistent native action tiles:
  - Read
  - Listen
  - Tafsir
  - Radio

Reader/search/audio/tafsir business logic remains unchanged.

### Quran control sheets

The following reader controls now use Material 3 modal bottom sheets rather than alert-style dialogs:

- Surah selector
- Quran font size
- Juz/Hizb/Page number entry

The same existing actions are called:

- `vm.select(...)`
- `vm.section(...)`
- `graph.settings.quranFontSize(...)`

No canonical Quran indexing, saved position, bookmark or search logic changed.

## Adhkar

### `AdhkarScreen.kt`

Changes:

- current category/progress becomes a branded focus hero
- the active dhikr uses the shared premium surface treatment
- source text, counter progress and navigation remain intact
- sequential completion/advance/reset/conflict behavior is untouched

No counter repository call was changed.

## Night Prayer

### `NightPrayerScreen.kt`

Changes:

- added a calm Night Prayer hero with existing Athar iconography
- improved visual separation between History, Calendar and Tools
- section transitions use the shared reduced-motion-aware transition
- session summary uses the same surface language as the rest of the application
- duplicate visual heading inside History was removed

Night Prayer repository, sync, CAS/conflict, encrypted notes, session save/delete and privacy behavior remain unchanged.

The larger product features still planned for the Night Prayer module—such as full timer/tonight experience, yearly insights and reminder integration—remain feature work rather than being faked by this UI pass.

## Upcoming / Live

### `UpcomingScreen.kt`

List cards were intentionally simplified.

Changes:

- unified premium surface and outline
- shared status pill
- stronger title hierarchy
- series metadata is reserved for the detail view
- duration is reserved for the detail view
- list titles are limited to two lines
- Live remains visually distinguishable without inventing a live state
- detailed course/topics/book/replay context remains on the detail surface

Event state, countdown, UTC handling, offline behavior, replay gating and open actions are unchanged.

## Lessons / Watch

### `WatchScreen.kt`

The existing watch experience is already one of the strongest screens, so this pass intentionally avoids a rewrite.

Changes:

- lesson title hierarchy is stronger
- comments preview uses the common large Athar surface and border treatment

Existing player/WebView identity, progress, completion, comment flow, series switching, dialogs and provider policy are unchanged.

A future bottom-sheet conversion for full comments/playlist should only happen together with the existing playback-state device regression suite.

## Scholar and Series

### `ScholarScreen.kt`

- scholar identity/portrait now sits in the shared hero treatment
- biography/source/tab behavior remains unchanged

### `SeriesScreen.kt`

- series title, favorite state, scholar link, progress, description and Continue action are grouped into the Athar hero treatment
- pagination and lesson behavior remain unchanged

## Account and Knowledge

### `MainShell.kt`

Knowledge:

- receives a branded hero
- Upcoming remains directly accessible
- existing content filters/search/pagination are unchanged

Account:

- receives a personal identity header
- profile/khatmah/bookmarks/favorites/export selection remains the same
- transitions between tools are visually smoother

Profile/security/export/delete network behavior was not changed.

## Existing micro-interaction system retained

`MarkToggle.kt` already provides the correct interaction direction:

- outline/filled icon transition
- tiny scale feedback
- haptic acknowledgement
- pending state
- rollback on failure

This was kept rather than adding a second competing favorite/bookmark animation system.

## Loading, empty and error states

The existing loading skeleton already respects reduced motion. The premium pass keeps it native and lightweight.

The shared `QuietEmptyState` was refined to use the Athar surface/icon language.

A future screen-specific skeleton pass can differentiate player/home/book/catalog geometry, but should be implemented only with screenshot and performance validation rather than adding decorative animation blindly.

## Dark mode and localization

All new surfaces use `MaterialTheme.colorScheme` semantic roles, not hard-coded new screen colors. This allows the existing complete light/dark Athar palettes to drive the new components.

Layouts continue to use Compose start/end-aware layout primitives and the existing localized string resources. No Arabic or English business strings were replaced by hard-coded UI copy in this branch.

The default product behavior remains Arabic + RTL + Light.

## External animation/assets decision

Research included Material 3 / Material 3 Expressive, Mobbin, Rive and Lottie.

No Rive or Lottie dependency or third-party animation asset is added in this branch.

Reason:

- this project still targets an older Samsung/API27 ARM32 physical device in acceptance
- external animation runtimes change APK size and memory behavior
- exact third-party asset licensing must be reviewed per asset
- most of Athar's motion is better served by native Compose
- worship-related completion should remain calm and non-gamified

Rive/Lottie remain appropriate only for a small future set such as an original splash/onboarding/neutral empty-state asset, after license, reduced-motion and device profiling gates.

## Design references

Reviewed during this pass:

- Android Material 3 / Material 3 Expressive:
  https://developer.android.com/develop/ui/compose/designsystems/material3
- Compose animation:
  https://developer.android.com/develop/ui/compose/animation/introduction
- Android rendering/performance:
  https://developer.android.com/topic/performance/issues/render
- Compose Material 3 releases:
  https://developer.android.com/jetpack/androidx/releases/compose-material3
- Mobbin product-pattern library:
  https://mobbin.com/
- Rive:
  https://rive.app/
- LottieFiles:
  https://lottiefiles.com/

References are used for design patterns and platform guidance only. Athar does not copy proprietary brand assets, layouts or code.

## Files changed by the premium-v2 pass

Expected UI/design scope:

- `android/app/src/main/java/org/athar/app/core/ui/PremiumComponents.kt`
- `android/app/src/main/java/org/athar/app/core/ui/Motion.kt`
- `android/app/src/main/java/org/athar/app/core/ui/HomeScreen.kt`
- `android/app/src/main/java/org/athar/app/core/ui/MainShell.kt`
- `android/app/src/main/java/org/athar/app/quran/QuranScreen.kt`
- `android/app/src/main/java/org/athar/app/adhkar/AdhkarScreen.kt`
- `android/app/src/main/java/org/athar/app/learning/UpcomingScreen.kt`
- `android/app/src/main/java/org/athar/app/learning/WatchScreen.kt`
- `android/app/src/main/java/org/athar/app/learning/ScholarScreen.kt`
- `android/app/src/main/java/org/athar/app/learning/SeriesScreen.kt`
- `android/app/src/main/java/org/athar/app/nightprayer/NightPrayerScreen.kt`
- `docs/design/MOTION_SYSTEM.md`
- this document

If the final compare shows backend/database/API files, stop and review before merging.

## Verification status

The branch was created through the GitHub connector and its diff was reviewed against `codex/android-ecosystem`.

The execution environment used for this review could not clone GitHub into the local container, so a Gradle build was not falsely reported as executed here.

Before merge, run the repository's normal Android gate on the development workstation:

```powershell
android\gradlew.bat -p android assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=1
```

Then install/update the existing app data on the authorized physical phone and verify at minimum:

- Arabic RTL Light home
- English LTR Light home
- Dark theme
- system animations disabled
- Quran hub and each bottom sheet
- exact Quran resume position
- Adhkar counter and auto-advance
- Night Prayer history/calendar/tools
- Upcoming list/detail/live/cancelled/replay/offline
- lesson playback/comments/series switching
- scholar/series pages
- account tool switching
- 130% font scale
- phone landscape
- tablet
- TalkBack focus order
- network loss/error/retry
- repeated rapid taps
- release/profileable jank measurement

Do not mark this UI pass VERIFIED until those gates pass.

## Merge rule

Do not merge this branch merely because screenshots look better.

Merge only after:

1. Android compile/tests/lint pass.
2. Existing behavioral/device journeys still pass.
3. No backend/API/database diff is present.
4. Arabic RTL and English LTR are visually reviewed.
5. reduced motion works.
6. physical API27 acceptance has no new blocker.


## Synchronization with ongoing Codex work

During this UI/UX pass, `codex/android-ecosystem` advanced from the branch-creation base to:

`6989a8a42ece5438972df13c186ea37c076b9874`

That commit added protected local-read/recovery behavior for Quran reading position, Adhkar counters, Night Prayer sessions and private exemptions.

The premium branch was not allowed to overwrite that work. It was merged as a true second parent, then the visual layer was re-applied on top of the latest source for the three overlapping screens.

Merge commit:

`b1444026b8159b6993ac4f3aaf255708bd32d77d`

After that merge, the premium branch compared against `codex/android-ecosystem` as **ahead, behind 0**. The final diff contains presentation-layer Compose files and design documentation only.

### Recovery logic explicitly preserved

The UI pass retains:

- `ProtectedRead.Loading / Ready / Unreadable`
- retry behavior for unreadable local records
- disabled unsafe writes when retained private data cannot be decoded
- existing ciphertext/pending-operation preservation
- account isolation
- Night Prayer unreadable-state gating
- Quran reading-position unreadable-state gating
- Adhkar counter unreadable-state gating

Visual redesign must never reinterpret unreadable private data as empty/zero/default state.

## Additional full-app polish after the first pass

The complete review continued beyond the first high-priority screens.

### Authentication

`auth/AuthScreens.kt`

- login/register/recovery/verification now use the common Athar hero surface
- existing OTP normalization, validation and callbacks are unchanged
- no authentication state transition was animated or delayed

### Account tools / Khatmah

`core/ui/AccountToolsScreen.kt`

- export and Khatmah receive the shared hierarchy
- calculated daily workload is promoted into the Khatmah hero
- bookmarks/favorites empty state uses the shared Athar empty treatment
- export, save, delete and navigation requests are unchanged

### Discussion

`learning/DiscussionPanel.kt`

- individual comments use consistent Athar surfaces instead of loose divider-only rows
- moderation state uses the shared status treatment
- sorting, pagination, replies and composer behavior are unchanged

### Upcoming on Home

`learning/UpcomingHomeSection.kt`

- section heading now uses the common section component
- event cards use the same status and border language as the full Upcoming screen
- the server-bounded three-item query and minute refresh behavior are unchanged

### Quran audio

`media/AudioScreen.kt`

- added a branded audio/radio hero
- follow-along ayahs now use calm selected/non-selected surfaces
- Surah and reciter selection use native Material bottom sheets
- Media3 playback, source identity, timings, radio behavior and selection ownership are unchanged

### Tafsir

`quran/TafsirScreen.kt`

- controls and selected-book metadata use the shared Athar hierarchy
- Tafsir book selection is now a native bottom sheet
- provider/source validation and API pagination remain unchanged

### Night Prayer tools

`nightprayer/NightToolsScreen.kt`

- verse calculator, last-third calculator and local exemption sections use consistent section hierarchy
- calculation methods, timezone handling and private exemption storage are unchanged

### Screen-aware loading

`core/ui/LoadingSkeleton.kt` and `core/ui/DataScreen.kt`

Added three native skeleton families:

- List
- Grid
- Detail

`DataScreen` selects the visual skeleton from the content surface without changing the request/cache state machine. Reduced motion continues to disable the opacity pulse.

## Screens deliberately not rewritten

A complete review does not mean every file must change.

### Onboarding

The current onboarding already has:

- original Athar illustrations
- pager hierarchy
- animated restrained progress indicator
- reduced-motion support
- responsive width and scrolling
- Arabic/English resource usage

It was therefore retained rather than redesigned for cosmetic churn.

### Player engine / repositories / ViewModels

No UI requirement justified changing:

- `YouTubePlayer.kt`
- lesson repository/state rules
- audio ViewModel/playback service
- Quran repository/index
- Room entities/DAO
- authentication repository
- backend/provider code

Those remain out of scope for this presentation branch.

## Final presentation-layer diff scope

At the final review checkpoint, the intended branch-only source differences are limited to native Compose presentation files such as:

- Adhkar screen
- Auth screens
- Account tools
- shared DataScreen/loading surfaces
- Home
- navigation shell
- motion and premium UI primitives
- discussion/scholar/series/upcoming/watch presentation
- audio presentation/selectors
- Night Prayer presentation/tools
- Quran hub/reader controls/Tafsir presentation
- design documentation

No `backend/`, `database/`, migration, PHP API, Room repository or provider file is intended to differ from the latest Codex branch.

## Static integrity review

Because this environment cannot run the repository's local Gradle/Android SDK toolchain, build success is **not** claimed.

A source-level structural check was run over every changed Kotlin file after synchronization:

- no Git conflict markers
- balanced code brackets/braces/parentheses after excluding strings/comments
- latest Codex protected-read logic retained in overlapping screens

All changed Kotlin files passed that structural check.

This is a sanity check, not a compiler, lint, instrumentation or physical-device result. The existing merge rule and Android gate below remain mandatory.
