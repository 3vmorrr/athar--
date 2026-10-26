# Quran wird reminders

Task: 13.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Genuine progress boundary

The Quran reminder never treats opening the Quran, navigating to an ayah, searching, listening or viewing tafsir as completion.

Progress is counted only when the user explicitly saves a reading position through the existing resume-marker action. The daily tracker uses the previously saved canonical Quran position as its baseline and only increases the day's high-water mark when a later saved position moves forward.

Backward movement never subtracts or fabricates progress. On a new local day, the previous saved position becomes the new baseline. A first-ever saved position is conservative: it establishes the baseline and does not claim earlier reading.

## Rule

The encrypted local rule contains:

- daily saved-progress target, 1–200 ayahs;
- local reminder time;
- selected ISO weekdays;
- IANA timezone;
- rule revision/activation time;
- optional quiet-hour window for the shared notification policy.

The UI currently exposes target, time and days and uses the device IANA timezone at save time. Quiet-hour fields are supported by the planner/foundation and remain available for the unified reminder controls.

State lives only under \`private:quran-wird-state:v1\`. It is not uploaded to the API.

## Scheduling

The manager scans a 14-day horizon but retains only the next owner-scoped occurrence for the rule using the WP10 encrypted scheduler. Each day has a stable idempotency key, so editing a rule cannot produce a second notification for a day already delivered.

If the saved target is genuinely completed before the reminder, reconciliation removes that day's pending reminder. A periodic 24-hour maintenance worker extends the horizon; account switching cancels the previous owner's maintenance work.

Schedules use \`ZonedDateTime\` with the stored IANA timezone, preserving the requested local clock time across DST changes.

## Consent and withdrawal

Enabling the rule explicitly enables only the \`quran_routine\` notification category and requests Android notification permission only from that user action.

Disabling the rule removes all pending Quran-wird reminder specs. The generic local worker also rechecks master consent and exact category consent immediately before delivery, so a retained old schedule cannot bypass later withdrawal.

## Privacy

WorkManager receives owner/environment/opaque reminder ids only. Target, days, saved Quran position, daily baseline and progress remain encrypted locally. Campaign audience code never reads this state.

Acceptance remains pending for Android build/unit/instrumentation, API27/API33+ permission flows, real DST/timezone/device tests, process death, quiet-hour integration, account switching, RTL/LTR, large font, landscape and accessibility evidence.
