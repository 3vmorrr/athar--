# ATHAR MASTER PROMPT ADDENDUM
## Complete Qiyam Al-Layl & Tahajjud Companion Module
### Inspired by Qanet — Fully Integrated into Athar

This addendum extends the existing Athar master engineering prompt.

**All original requirements remain mandatory.**

You must implement a complete, production-quality Night Prayer and Tahajjud module inside the native Athar Android application, with secure backend synchronization and appropriate support in the independent administration dashboard.

## 1. Reference application research

Research the publicly documented features and user experience of:

**Qanet: Night Prayer Companion**

Official references:

- https://www.qanet.app/
- https://www.qanet.app/ar
- https://play.google.com/store/apps/details?id=com.asserayman.qanet
- https://apps.apple.com/eg/app/qanet-night-prayer-companion/id6758232380

Study its publicly described capabilities, including:

- Verse target calculator
- Verse range calculator
- Night prayer session logging
- Nightly recitation classifications
- Reading pace tracking
- Last-third-of-the-night calculations
- Prayer consistency statistics
- Current and longest streaks
- Category-specific streaks
- Monthly prayer calendar
- Yearly activity visualization
- Personal record tracking
- Optional exempt-period tracking
- Offline-first functionality
- Arabic and English localization
- Calm, supportive habit-building UX

Do not copy Qanet's source code, proprietary assets, branding, illustrations, exact interface designs, or protected content.

Implement equivalent functionality through an original Athar design using the existing Athar design system.

Verify current references before implementation. Do not invent undocumented competitor features.

---

# 2. Module identity and integration

Create a first-class Athar feature named:

**Arabic:** قيام الليل

**English:** Night Prayer

Supporting terminology:

- قيام الليل — Qiyam Al-Layl
- التهجد — Tahajjud
- حاسبة الآيات — Verse Calculator
- سجل القيام — Night Prayer Log
- الثلث الأخير — Last Third of the Night
- إحصائياتي — My Insights

The module must be integrated with:

- Existing Quran data
- Quran navigation
- Quran bookmarks
- Authenticated user accounts
- Personal statistics
- Offline persistence
- Account synchronization
- Arabic/English localization
- Athar's original visual identity

Provide entry points from the home screen, Quran reader, and an appropriate navigation destination.

Do not overcrowd the main bottom navigation.

---

# 3. Documentation-first implementation

Before implementing the module, create:

```text
docs/features/NIGHT_PRAYER.md
docs/features/VERSE_CALCULATOR.md
docs/features/NIGHT_PRAYER_ANALYTICS.md
docs/features/LAST_THIRD_CALCULATION.md
docs/features/READING_PACE.md
docs/features/NIGHT_PRAYER_PRIVACY.md

docs/architecture/NIGHT_PRAYER_DATA_MODEL.md
docs/architecture/NIGHT_PRAYER_SYNC.md

docs/design/NIGHT_PRAYER_UX.md
docs/design/NIGHT_PRAYER_MOTION.md

docs/testing/NIGHT_PRAYER_TEST_PLAN.md
```

Document all calculations, data structures, API contracts, edge cases, security requirements, and acceptance criteria.

Update the original master prompt's feature inventory, implementation plan, navigation architecture, database design, API contract, admin specifications, and testing strategy.

---

# 4. Night Prayer home screen

Create a beautiful, calm, immersive dashboard.

Show:

- A personalized greeting appropriate to the current time
- Current Islamic night date
- Tonight's recorded verse count
- Current night prayer classification
- Progress toward the next milestone
- A prominent "Start Tonight's Session" action
- Verse Calculator shortcut
- Last Third of the Night shortcut
- Current consistency streak
- Recent night prayer history
- A small, tasteful progress visualization

The design must feel peaceful and refined.

Use Athar's existing colors, typography, and spacing system.

Introduce subtle nighttime ambiance through gradients, layered surfaces, or restrained motion without replacing the established palette.

Use meaningful micro-interactions.

Do not introduce visual clutter or heavy continuous animations.

Support reduced-motion settings.

---

# 5. Verse Calculator — Target Mode

Implement an accurate Quran verse calculator.

Users must be able to:

1. Select a starting surah.
2. Select a starting ayah.
3. Choose a target number of verses.
4. Calculate the exact ending surah and ayah.
5. See the total selected verse count.
6. Open the resulting range in Athar's Quran reader.
7. Save the planned range.
8. Start a night prayer session using that range.

Provide preset targets:

- 10 verses
- 100 verses
- 1000 verses

Also support a validated custom target.

## Critical calculation requirements

Use the canonical Quran verse index already validated for Athar.

Represent Quran positions using a global, one-based ayah index.

For an inclusive range:

`endIndex = startIndex + targetCount - 1`

Handle crossing surah boundaries correctly.

Never exceed the final ayah of the Quran.

If the requested target exceeds the remaining available verses, display a clear validation message and offer a smaller target.

Do not wrap automatically from the final Quran verse to the beginning unless a separately designed and explicitly approved behavior is introduced.

Include unit tests covering:

- First ayah
- Last ayah
- Surah boundaries
- Cross-surah ranges
- Exactly 10 verses
- Exactly 100 verses
- Exactly 1000 verses
- Invalid positions
- Out-of-range targets

---

# 6. Verse Calculator — Range Mode

Allow the user to choose:

- Starting surah
- Starting ayah
- Ending surah
- Ending ayah

Display the exact inclusive verse count.

Formula:

`verseCount = endGlobalIndex - startGlobalIndex + 1`

Validate that the ending position is not before the starting position.

Provide:

- Copyable reference
- Open in Quran reader
- Save as planned recitation
- Record as a completed session

Keep calculation logic shared and testable.

---

# 7. Intelligent reading continuation

Remember the user's most recently completed Quran range.

When starting a new session, suggest the next ayah after the last recorded ending position.

Distinguish:

- Quran reading position
- Khatmah progress
- Night prayer session position

These concepts must not be silently merged.

Users must be able to override the suggested position.

If a session is edited or deleted, recompute the suggestion safely.

If the final ayah of the Quran was reached, offer an explicit restart option.

---

# 8. Night Prayer session logging

Users must be able to record one or multiple sessions per Islamic night.

Each session should support:

- Session ID
- User ID
- Islamic night identifier
- Start timestamp
- End timestamp, if available
- Start surah and ayah
- End surah and ayah
- Verse count
- Optional measured duration
- Optional personal note
- Creation timestamp
- Modification timestamp
- Synchronization status

Support both:

**Quick Entry**

Enter the number of verses manually.

**Detailed Entry**

Select the exact Quran range.

For manual entries, make it clear that the count is self-reported.

For range-based entries, calculate the verse count automatically.

Users must be able to:

- Add a session
- View session details
- Edit a session
- Delete a session
- Record multiple sessions on the same night
- Review historical sessions

Recalculate all affected totals and analytics after edits or deletions.

Do not infer that Quran playback or ordinary reading automatically means the user performed night prayer.

Night prayer records must require an explicit user action.

---

# 9. Nightly verse classifications

Implement the following progress thresholds:

| Level | Verse count |
|---|---:|
| Below first milestone | 0–9 |
| Not Negligent | 10–99 |
| Qanet | 100–999 |
| Muqantar | 1000+ |

Use appropriate Arabic terminology and source attribution.

Base the threshold explanation on the authenticated hadith reported in Sunan Abi Dawud 1398, after verifying the reference and approved religious text source.

Avoid claiming to determine a person's spiritual status, sincerity, acceptance of worship, or divine reward.

The app records self-reported recitation counts and displays the associated milestone terminology for encouragement.

Do not present the lowest tier as a judgment about the user's character.

## Progress presentation

Show:

- Current total
- Current milestone
- Next milestone
- Remaining verses to next milestone
- Optional progress indicator
- Gentle completion animation

Use Athar's colors and accessible visual distinctions.

Do not copy Qanet's exact color palette or visual hierarchy.

---

# 10. Accurate Islamic night boundaries

Night prayer statistics must not rely blindly on midnight-to-midnight calendar days.

Research and document the Islamic night convention used for tracking.

For the module, define a night as the period beginning at local Maghrib and ending at the following local Fajr, unless a verified product decision requires a different convention.

Ensure the night is assigned consistently to one night identifier.

Handle:

- Sessions before midnight
- Sessions after midnight
- Daylight-saving transitions
- Timezone changes
- Travel
- Missing prayer times
- Manually selected location
- Historical entries
- Device clock changes

Document the date-labeling convention clearly.

Do not silently move historical sessions between nights after location changes.

---

# 11. Last Third of the Night Calculator

Implement a dedicated feature showing:

- Local Maghrib time
- Following Fajr time
- Total night duration
- Start of the last third
- Remaining time until the last third
- Remaining time until Fajr

Calculate the night interval from Maghrib to the next Fajr.

For the standard calculation:

`nightDuration = nextFajr - maghrib`

`lastThirdStart = maghrib + (2 × nightDuration / 3)`

Use timezone-aware timestamps.

Never use a fixed assumption that the last third begins at a particular clock time.

## Location handling

Support:

- Optional location permission
- Approximate location when sufficient
- Manual city selection
- Saved city preferences
- Prayer calculation method selection
- Relevant madhhab/asr calculation settings where necessary
- Appropriate high-latitude handling
- Offline calculation when feasible

Use a verified, maintained, free prayer-times calculation library where possible.

Do not require a paid geolocation or prayer-time API.

Explain that calculated times can vary by method and local religious authority.

Allow the user to adjust calculation settings.

Do not request background location access unnecessarily.

## UI

Create a beautiful nighttime time visualization showing the progression from Maghrib to Fajr.

Include a subtle highlighted segment for the last third.

The visualization must be informative rather than decorative.

---

# 12. Personal reading pace tracker

Implement an optional reading pace tracker.

Users should be able to:

- Start a reading timer
- Pause the timer
- Resume
- Stop
- Save the measured duration
- Associate the duration with a verse count

Calculate:

`averageSecondsPerVerse = totalMeasuredSeconds / totalMeasuredVerses`

Use valid measured sessions only.

Estimate future session duration using the user's actual measured pace.

Display estimates clearly as approximate.

Avoid making unsupported claims such as every person being able to recite 100 verses within a fixed number of minutes.

If insufficient data exists, show an honest empty state.

Do not fabricate reading pace.

Keep the timer functional when the app temporarily moves to the background, within Android lifecycle constraints.

---

# 13. Night Prayer analytics

Build a detailed, privacy-preserving analytics experience.

## Overview metrics

- Total recorded nights
- Total recorded sessions
- Total verses
- Current consistency streak
- Longest consistency streak
- Current milestone-specific streak
- Longest milestone-specific streak
- Best recorded night
- Average verses per active night
- Recent activity
- Personal reading pace

## Monthly calendar

Display a calendar where each date reflects the associated night's recorded activity.

Provide:

- Clear color legend
- Night classification
- Total verses
- Session count
- Tap-to-open details
- Navigation between months

Do not rely on color alone to convey meaning.

## Yearly activity visualization

Create a performant annual activity visualization showing night prayer frequency and intensity.

Support:

- Year selection
- Accessible labels
- Activity intensity
- Empty days
- Exempt days, when explicitly enabled
- Correct timezone/date mapping

## Additional charts

- Verse totals over time
- Monthly consistency
- Milestone distribution
- Personal progress trends

Use charts only where meaningful.

Avoid overwhelming the user with excessive analytics.

---

# 14. Streak calculation engine

Implement deterministic, testable streak calculations.

Distinguish:

- Current active-night streak
- Longest active-night streak
- Current 10+ verse streak
- Current 100+ verse streak
- Current 1000+ verse streak
- Historical longest streaks for each category

Define clearly whether a streak remains active while the current Islamic night is still in progress.

Do not break a streak prematurely.

Use recorded Islamic night identifiers rather than naive UTC dates.

Recompute streaks correctly after historical edits.

Avoid competitive rankings and shame-based messaging.

Use calm encouragement rather than pressure.

---

# 15. Optional private exempt-period tracking

Provide an entirely optional feature for users who want certain nights excluded from consistency calculations.

Potential use cases include menstrual periods or other personally relevant exemptions.

## Privacy-first requirements

- Disabled by default
- No assumptions about the user's sex or identity
- No mandatory disclosure
- No public visibility
- No advertising or analytics use
- No unnecessary administrator access
- Clear explanation before enabling
- Easy editing and deletion
- Sensitive-data protection
- Local-only storage as the preferred default

If cross-device synchronization is offered, it must be a separate explicit opt-in, with appropriate protection and clear disclosure.

Do not automatically upload this sensitive information merely because the user is authenticated.

Provide a generic "Exclude from streak calculation" option so users do not need to disclose a reason.

Exempt nights must not inflate actual completed prayer totals.

They may preserve streak continuity according to a clearly documented, user-selected rule.

Do not provide medical conclusions or religious rulings based on tracked dates.

---

# 16. Offline-first architecture

The Night Prayer module should function without internet after the user has completed the required initial authentication and local data initialization.

Offline features should include:

- Verse calculation
- Range calculation
- Local session recording
- Editing sessions
- Deleting sessions
- Analytics
- Calendar history
- Reading pace
- Previously calculated prayer times where valid
- Manual preferences

Use Room or another justified structured local database.

Queue synchronization operations safely.

Implement idempotent server operations and stable session identifiers.

Avoid duplicate sessions after retries.

Support explicit conflict-resolution behavior.

Do not overwrite local changes silently.

Keep sensitive exempt-period data out of ordinary synchronization unless the user explicitly enables protected synchronization.

---

# 17. Backend and API

Extend the shared Athar backend.

Suggested API groups:

```text
/api/v1/night-prayer/overview
/api/v1/night-prayer/sessions
/api/v1/night-prayer/sessions/{id}
/api/v1/night-prayer/analytics
/api/v1/night-prayer/calendar
/api/v1/night-prayer/preferences
/api/v1/night-prayer/sync
```

Finalize the contract after architecture review.

Requirements:

- Authentication
- Ownership validation
- Input validation
- Pagination
- Idempotent writes
- Consistent timestamps
- Rate limiting
- Secure deletion
- Efficient queries
- Explicit API versioning

Create additive, reversible database migrations where feasible.

Preserve existing Athar user and Quran records.

Use appropriate indexes for user ID and night identifier.

Never store calculated metrics redundantly without a clear invalidation strategy.

---

# 18. Independent admin dashboard integration

Extend the Athar administration dashboard to support the Night Prayer module.

Provide:

- Module enable/disable settings
- Public educational text management
- Hadith source/reference management
- Feature announcements
- Default calculation settings
- Supported location calculation methods
- Module version and health information
- Aggregate adoption metrics, only where privacy-safe

Do not give administrators routine access to individual users' private worship logs.

Do not expose exempt-period information.

Do not implement public user rankings.

Allow administrators to manage informational content, not manipulate a user's personal worship history.

Any exceptional support operation must be separately authorized, audited, and privacy-reviewed.

---

# 19. Notifications and reminders

Provide optional, user-controlled reminders.

Examples:

- Night prayer reminder
- Last-third-of-the-night reminder
- Gentle consistency reminder

Requirements:

- Notifications disabled until explicitly enabled
- Android notification permission handling
- Configurable reminder timing
- Quiet hours
- Correct timezone behavior
- Proper rescheduling after reboot when permitted
- Efficient battery usage
- No mandatory always-running background service
- No unnecessary server-side push infrastructure

Prefer local scheduling when technically appropriate.

Account for Android exact-alarm restrictions and background execution policies.

Never claim exact delivery timing when the OS does not guarantee it.

Do not use guilt-inducing notification language.

---

# 20. Original Athar UI/UX

Create an original, premium-quality interface.

Suggested screens:

1. Night Prayer Overview
2. Verse Calculator
3. Select Quran Range
4. Start Session
5. Active Reading Session
6. Save Session
7. Night Details
8. Prayer History
9. Monthly Calendar
10. Yearly Activity
11. Personal Insights
12. Reading Pace
13. Last Third of the Night
14. Night Prayer Settings
15. Optional Exempt Nights

Use reusable Compose components.

Provide:

- Smooth screen transitions
- Meaningful progress animations
- Subtle milestone feedback
- Elegant number animations
- Accessible charts
- Proper loading states
- Polished empty states
- Offline indicators
- Consistent navigation
- Full Arabic/English localization

Use the existing Athar color palette.

Avoid copying Qanet's exact visual design.

---

# 21. Integration with existing Athar features

The new module must share verified Quran indexing and references with the existing Quran reader.

Possible user journeys:

**From Quran to Night Prayer**

The user selects a Quran range and chooses to record it as a night prayer session.

**From Night Prayer to Quran**

The user calculates a 100-verse target and opens the exact starting and ending positions in the Quran reader.

**From Night Prayer to Account**

The user opens private Night Prayer insights from the personal dashboard.

**From Night Prayer to Khatmah**

The user may explicitly apply verified completed Quran reading to a khatmah plan, but only with clear confirmation and without double counting.

Do not automatically equate Quran reading, audio playback, memorization review, and night prayer performance.

---

# 22. Comprehensive tests

Implement automated tests for:

- Inclusive verse counting
- Quran boundaries
- Cross-surah ranges
- 10/100/1000 thresholds
- Multiple sessions per night
- Editing sessions
- Deleting sessions
- Streak recomputation
- Exempt nights
- Midnight transitions
- Timezone changes
- Daylight-saving transitions
- Last-third calculations
- Missing location
- Manual city selection
- Offline recording
- Synchronization retries
- Duplicate prevention
- Authorization
- Arabic/English localization
- RTL layout
- Accessibility
- Performance

Test the Android UI and backend integration.

Never mark this module complete without verification.

---

# 23. Definition of done

The Night Prayer module is complete only when:

1. Users can calculate exact Quran verse targets.
2. Users can calculate exact verse ranges.
3. Users can open those ranges in Athar's Quran reader.
4. Users can record multiple sessions per night.
5. Users can edit and delete their records.
6. Nightly milestone classification is correct.
7. Last-third-of-the-night calculations are validated.
8. Reading pace uses genuine measured data.
9. Calendar and analytics reflect real records.
10. Streak calculations handle edge cases.
11. Optional exempt-night tracking protects privacy.
12. Offline functionality works.
13. Authenticated synchronization is reliable.
14. Admin integration respects user privacy.
15. Arabic and English are fully supported.
16. The design matches Athar's existing identity.
17. Motion is polished and performant.
18. All relevant tests pass.
19. Documentation is complete.
20. No proprietary Qanet assets or code have been copied.

**This module is a mandatory addition to the original Athar project scope. Update the master implementation plan and implement it as a fully integrated feature, not a disconnected prototype.**
