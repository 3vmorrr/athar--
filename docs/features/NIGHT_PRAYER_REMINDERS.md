# Night Prayer and last-third reminders

Task: 14.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Sourced calculation

Night reminders reuse the existing \`PrayerCalculator\` and saved Night Prayer preferences: city label, coordinates, IANA timezone, calculation method, madhab and high-latitude rule. The reminder feature never reads GPS and never invents a parallel prayer-time implementation.

The user can choose:

- calculated start of the last third; or
- a fixed local clock time that must actually fall between the calculated Maghrib and next Fajr for that Islamic night.

The calculated reminder expires at Fajr. A fixed time before Maghrib is interpreted on the following civil date only when it remains inside the same Maghrib-to-Fajr night.

## Unavailable states

Missing saved preferences, an unavailable/polar calculation, or no eligible night are explicit states. Athar does not silently substitute a generic clock time when the sourced calculation fails.

Night preference reads/saves now notify the reminder manager. An enabled rule keeps an encrypted local snapshot so background reconciliation can work offline, but changing the authoritative city/method settings invalidates the previous schedule and updates the snapshot.

## Local exemptions and privacy

Locally stored exemption dates are checked before scheduling. An exempt Night Prayer date is skipped without creating an “absence”, “failure” or worship-completion record. Exemption dates are not uploaded by this feature.

The reminder rule and calculation snapshot are encrypted under \`private:night-prayer-reminder:v1\`. WorkManager receives owner/environment/reminder ids only.

## Consent, DST and control

The \`night_prayer\` category requires explicit consent and the Android permission prompt can only launch from an enable/save action. Delivery still rechecks consent immediately before posting.

Fixed-time scheduling uses the saved IANA timezone, preserving the chosen local wall-clock time across DST. Only the next eligible night is retained.

The Tonight screen exposes calculation source, preview, Last Third/fixed-time controls and explicit unavailable states. The unified reminder center counts Night Prayer and can open or disable it directly.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and physical API27/API33+ actual delivery, polar/high-latitude, DST, local-exemption, offline/restart, Arabic RTL/English LTR, large-font, landscape, dark-mode and accessibility evidence.
