# ATHAR MASTER PROMPT ADDENDUM
## Upcoming Lessons, Live Events, Smart Reminders, Push Notifications, Email Campaigns & Worship Reminder System

This specification extends all previous Athar requirements.

Do NOT restart the project.

Continue from the current implementation and integrate these features into:

- Native Android application
- Shared PHP/MySQL backend
- Independent Admin Dashboard
- Existing Athar accounts and content
- Existing lesson/series/scholar system
- Quran
- Adhkar
- Night Prayer
- Khatmah

The system must remain:

- Arabic-first
- RTL-first
- Light-theme by default
- English fully supported
- Privacy-conscious
- Free/open-source wherever possible
- Highly performant
- Extensible

The purpose is to create a complete but respectful engagement system.

This is an Islamic application.

Notifications must help the user maintain useful habits and discover beneficial content without becoming spammy, manipulative, guilt-inducing, or game-like.

---

# 1. UPCOMING LESSONS

Add first-class support for scheduled lessons.

A lesson must support statuses such as:

```text
draft
scheduled
live
published
cancelled
```

A scheduled lesson may contain:

- Title
- Arabic title
- English title
- Scholar
- Series
- Course/collection
- Description
- Thumbnail
- Scheduled start date/time
- Estimated duration
- YouTube URL / live URL when applicable
- Whether the lesson will be live
- Whether reminders are enabled
- Whether replay becomes available later
- Visibility
- Related topics
- Related book
- Related book pages where applicable

Store all canonical timestamps safely and convert them correctly for the user's timezone.

Do not hardcode Egypt time into the data model.

---

# 2. UPCOMING LESSONS UI

Create a dedicated Android section:

Arabic:

`الدروس القادمة`

English:

`Upcoming Lessons`

Possible card:

```text
┌─────────────────────────────┐
│ Thumbnail                   │
│                             │
└─────────────────────────────┘

شرح كتاب ...

الشيخ ...

الاثنين • 8:00 م

يبدأ بعد ساعتين

[ ذكّرني ]
```

The countdown must be calculated dynamically.

Do not persist strings such as:

"starts in 2 hours"

Persist the actual timestamp and calculate the display value.

---

# 3. LIVE NOW

When a scheduled lesson reaches its live state, prominently indicate:

`مباشر الآن`

`LIVE NOW`

Use a tasteful live indicator.

The Home screen may contain:

`مباشر الآن`

when an active lesson exists.

Tapping it opens the lesson/player.

Do not fake live status merely because the scheduled time has arrived if the backend has reliable actual live-state information.

Design fallback rules where automatic live verification is unavailable.

---

# 4. LESSON REMINDER BUTTON

Every upcoming lesson should optionally provide:

`ذكّرني`

When enabled:

`تم ضبط التذكير`

Allow the user to disable it again.

Possible reminder schedule:

- 1 day before
- 1 hour before
- 15 minutes before
- At start

But do NOT send all four automatically.

Allow either:

- sensible application default
- user-selected timing

Recommended default:

`15 minutes before`

Allow the user to configure this.

---

# 5. LOCAL + SERVER REMINDERS

Design reminders intelligently.

Use local Android scheduling when it provides a more reliable/private solution for personal reminders.

Use server push for:

- Schedule changes
- Lesson cancellation
- Live-now announcements
- New content
- Admin campaigns

Avoid requiring the server for every personal recurring reminder when the device can handle it safely.

Prevent duplicate notifications when local and server reminder systems overlap.

Document the deduplication strategy.

---

# 6. LESSON CHANGES

If the administrator changes a scheduled lesson:

Old:

`8:00 PM`

New:

`9:30 PM`

Users who requested reminders must receive an appropriate update.

Arabic example:

`تم تغيير موعد الدرس`

`الموعد الجديد: 9:30 م`

If cancelled:

`تم إلغاء درس اليوم`

Do not continue sending obsolete reminders.

---

# 7. NEW CONTENT NOTIFICATIONS

Allow users to optionally receive notifications for new content.

Examples:

`درس جديد`

`تمت إضافة درس جديد للشيخ ...`

`حلقة جديدة من سلسلة ...`

`كتاب جديد`

`تمت إضافة كتاب ...`

`سلسلة جديدة`

`تمت إضافة سلسلة جديدة ...`

Allow granular subscription.

---

# 8. FOLLOW SCHOLARS

Add optional:

`متابعة`

to scholar profiles.

A user may follow selected scholars.

Then:

`إشعارات الشيخ`

can be enabled/disabled.

When a new lesson or series from that scholar is published, eligible followers may receive:

`درس جديد للشيخ ...`

Do not automatically subscribe users to every scholar.

---

# 9. FOLLOW SERIES

Allow:

`متابعة السلسلة`

If enabled, the user may be notified when a new lesson is added.

Example:

`حلقة جديدة`

`تمت إضافة الدرس 12 إلى سلسلة ...`

Tapping opens that lesson directly.

---

# 10. ATHAR REMINDER CENTER

Create a dedicated screen:

Arabic:

`التذكيرات والإشعارات`

English:

`Reminders & Notifications`

Organize preferences into clear categories.

Example:

```text
الدروس
────────────────
الدروس التي طلبت تذكيري بها     ✓
الدروس الجديدة                   ✓
السلاسل التي أتابعها             ✓
العلماء الذين أتابعهم            ✓

القرآن
────────────────
الورد اليومي                     ✓
إكمال الختمة                     ✓

الأذكار
────────────────
أذكار الصباح                     ✓
أذكار المساء                     ✓
الصلاة على النبي ﷺ              ✓

قيام الليل
────────────────
تذكير قيام الليل                 ○

المحتوى
────────────────
الكتب الجديدة                    ○
المحتوى الجديد                   ✓

البريد الإلكتروني
────────────────
تذكيرات البريد                   ○
```

Do not overwhelm users with dozens of toggles on one screen.

Use grouped settings.

---

# 11. ADHKAR REMINDERS

Integrate reminders with the existing Adhkar module.

Support optional reminders such as:

`أذكار الصباح`

`أذكار المساء`

The user chooses whether to enable them.

Where appropriate, provide recommended time ranges but allow customization.

Do not assume every user wants reminders.

---

# 12. PERSONAL WIRD REMINDERS

Allow users to create personal reminder routines.

Examples:

`ورد القرآن`

`الأذكار`

`الصلاة على النبي ﷺ`

`قيام الليل`

Potential configuration:

```text
Reminder:
الصلاة على النبي ﷺ

Days:
Every day

Time:
8:00 PM

Enabled:
Yes
```

Support recurring schedules.

---

# 13. SALAWAT REMINDERS

Add an optional respectful reminder category for الصلاة على النبي ﷺ.

Example notification:

Title:

`الصلاة على النبي ﷺ`

Body:

`اللهم صلِّ وسلم على نبينا محمد ﷺ`

Alternative messages may be maintained as approved content.

Do not use manipulative text such as:

"You haven't prayed upon the Prophet today."

Do not shame the user.

---

# 14. FRIDAY REMINDERS

Support Friday-specific reminders.

Examples:

`أكثر من الصلاة على النبي ﷺ`

Potential optional reminder for سورة الكهف if this feature is approved as part of the product.

Make Friday reminders configurable.

Do not bombard users with multiple Friday notifications.

---

# 15. QURAN WIRD

Integrate notifications with Quran reading progress.

Allow:

`تذكير الورد اليومي`

The reminder should know whether today's configured reading target has been completed.

If already completed:

DO NOT send an unnecessary completion reminder.

If incomplete at the configured time:

Send a gentle reminder.

Example:

`وردك اليومي`

`يمكنك متابعة القراءة من حيث توقفت.`

Tapping the notification should deep-link directly to the saved Quran position.

---

# 16. KHATMAH REMINDERS

Integrate with the Khatmah system.

If a user has an active plan:

- Determine today's target
- Determine progress
- Remind only when appropriate
- Deep-link to the required Quran position

Example:

`ورد الختمة`

`تبقى لك جزء من ورد اليوم.`

Avoid guilt-based wording.

---

# 17. ADHKAR COMPLETION-AWARE REMINDERS

This is important.

If the user already completed the morning adhkar:

Do not later send:

`لا تنس أذكار الصباح`

The reminder engine should check completion state where technically practical.

Same principle for:

- Evening adhkar
- Quran Wird
- Khatmah
- User-configured routines

This makes Athar feel intelligent rather than spammy.

---

# 18. NIGHT PRAYER REMINDER

Integrate with the existing Night Prayer module.

Allow:

`تذكير قيام الليل`

Potential options:

- User-selected time
- Last third of the night
- X minutes before last-third start

If Last Third is selected, use the existing prayer/night calculation engine.

Do not hardcode a universal time.

Do not require constant background location access.

Reuse stored/manual location and prayer calculation preferences.

---

# 19. SMART REMINDER ENGINE

Create a centralized reminder engine.

Do NOT implement every feature with completely separate scheduling logic.

Conceptually:

```text
ReminderEngine
    |
    ├── LessonReminder
    ├── QuranReminder
    ├── KhatmahReminder
    ├── AdhkarReminder
    ├── SalawatReminder
    ├── NightPrayerReminder
    └── CustomReminder
```

Each reminder should have:

- Unique ID
- Type
- Schedule
- Timezone
- Enabled state
- Deep link
- Notification channel
- Last execution
- Next execution
- Optional completion condition

Design it to support future reminder types.

---

# 20. ANDROID NOTIFICATION CHANNELS

Create meaningful Android notification channels.

Potential channels:

```text
Lessons
Personal Reminders
Quran & Khatmah
Adhkar
Night Prayer
Athar Updates
```

Do not put everything into one notification channel.

Allow Android users to control categories using system settings.

---

# 21. ANDROID NOTIFICATION PERMISSION

Correctly handle Android notification permission requirements.

Do NOT request notification permission immediately on first launch without context.

First explain why Athar may send notifications.

For example, when the user first enables:

`ذكّرني`

show a short explanation.

Then request the Android permission.

If denied:

- Do not repeatedly harass the user.
- Explain how to enable it later from Settings.
- Keep the app fully usable.

---

# 22. DEEP LINKS

Every actionable notification should open the correct destination.

Examples:

Upcoming lesson
→ lesson page

Live lesson
→ player

New series lesson
→ specific lesson

Quran Wird
→ exact Quran position

Khatmah
→ target Quran position

Morning Adhkar
→ morning adhkar

Evening Adhkar
→ evening adhkar

Night Prayer
→ Night Prayer dashboard

Scholar notification
→ scholar or lesson

Book notification
→ book details

Do not simply open Home for every notification.

---

# 23. ADMIN NOTIFICATION CAMPAIGNS

Build a professional campaign system inside the independent Admin Dashboard.

Add:

`الحملات`

or:

`Notifications & Campaigns`

Admins with appropriate permission may create campaigns.

---

# 24. CAMPAIGN TYPES

Support:

## Push Notification

Android push notification.

## Email

Email campaign.

## Push + Email

Both channels where the user has opted in.

Architect the system so additional channels could be added later without redesigning the entire database.

---

# 25. CAMPAIGN COMPOSER

Admin campaign creation should contain:

```text
Campaign name

Channel:
○ Push
○ Email
○ Push + Email

Audience

Arabic title
Arabic body

English title
English body

Image — optional

Deep link / destination

Schedule:
○ Send now
○ Schedule

Date
Time
Timezone

Preview

Send test
```

Do not send immediately when the admin is merely editing.

Require an explicit final confirmation.

---

# 26. AUDIENCE SEGMENTATION

Allow privacy-safe audience targeting.

Examples:

`All eligible users`

`Arabic users`

`English users`

`Followers of scholar X`

`Followers of series X`

`Users who requested reminder for lesson X`

`Users interested in new books`

Do NOT create invasive behavioral profiling.

Avoid sensitive religious-behavior targeting.

For example:

DO NOT create an Admin segment:

"Users who haven't prayed Qiyam for 7 days."

Individual worship behavior must not become a marketing/admin targeting tool.

---

# 27. WORSHIP DATA PRIVACY

Critical rule:

Administrators must NOT have routine access to private individual worship behavior.

This includes detailed personal data about:

- Night Prayer
- Quran Wird
- Adhkar completion
- Salawat
- Private reminders
- Exempt periods

Admin analytics should be aggregate and privacy-safe.

Marketing campaigns must not expose or exploit individual worship records.

---

# 28. EMAIL CAMPAIGNS

Use the existing SMTP architecture where appropriate.

Support properly formatted email messages.

Email should contain:

- Athar identity
- Localized content
- Clear title
- Message
- Optional relevant CTA
- Unsubscribe/preferences mechanism where applicable

Email must render correctly on mobile.

Do not send enormous images.

Do not embed unnecessary trackers.

---

# 29. EMAIL PREFERENCES

Users must be able to control email communication separately from push.

Example:

```text
البريد الإلكتروني

الدروس القادمة        ✓
المحتوى الجديد         ○
تحديثات أثر             ○
```

Transactional/security emails must remain separate from optional engagement emails.

For example:

OTP/password reset/security alerts

must NOT depend on marketing email preference.

---

# 30. UNSUBSCRIBE

Promotional/content emails must support an appropriate unsubscribe/preferences mechanism.

A user who disables optional email campaigns must not stop receiving necessary:

- Account verification
- Password reset
- Security messages

Separate:

`Transactional`

from:

`Engagement/Content`

---

# 31. EMAIL QUEUE

Do NOT send thousands of emails synchronously inside one PHP HTTP request.

Create a queue/outbox architecture.

Conceptually:

```text
campaign
    ↓
campaign_recipients
    ↓
email_outbox
    ↓
worker
    ↓
SMTP
```

Support:

- Pending
- Processing
- Sent
- Failed
- Retry

Implement safe retry behavior.

Avoid duplicate sends.

---

# 32. PUSH INFRASTRUCTURE

Research and select an appropriate zero-cost or free-tier push architecture compatible with native Android.

A likely solution is Firebase Cloud Messaging if it satisfies current project requirements.

Abstract provider-specific logic behind a service interface.

Example:

```text
PushProvider

sendToDevice()
sendToUsers()
sendCampaign()
```

Do not scatter provider-specific code throughout the backend.

---

# 33. DEVICE TOKENS

Store push device registrations securely.

A user may have:

- Multiple phones
- Reinstalled app
- Expired token

Support:

- Register token
- Refresh token
- Remove token
- Logout cleanup
- Invalid-token cleanup

Never assume one user = one device.

---

# 34. NOTIFICATION INBOX

Add an in-app:

`الإشعارات`

screen.

This should show relevant server-generated notifications.

Examples:

- Lesson starts soon
- New lesson
- New series episode
- Schedule changed
- New book
- Important Athar announcement

Each entry may contain:

- Icon
- Title
- Short body
- Time
- Read/unread state
- Destination

Support:

`تحديد الكل كمقروء`

Do not necessarily store every purely local personal reminder on the server.

---

# 35. NOTIFICATION BADGE

Show a subtle unread indicator.

For example on:

`الإشعارات`

Do not use aggressive badge mechanics.

---

# 36. CAMPAIGN ANALYTICS

Provide basic privacy-safe campaign analytics.

Examples:

```text
Recipients
Queued
Sent
Failed
Opened app through notification
```

Do not build invasive tracking.

Do not claim email opens are accurate if tracking is not implemented.

Do not add tracking pixels merely for vanity metrics.

---

# 37. CAMPAIGN HISTORY

Admin should see:

```text
Campaign
Status
Channel
Audience
Scheduled
Sent
Failed
Created by
Created at
```

Statuses:

```text
draft
scheduled
processing
completed
partially_failed
cancelled
```

Allow cancellation of campaigns that have not yet been dispatched.

---

# 38. TEST NOTIFICATION

Before launching a campaign, Admin must be able to:

`إرسال اختبار`

Send only to the current admin/test device/email.

This is especially important for:

- Arabic text
- Deep links
- Images
- RTL
- Email formatting

---

# 39. CAMPAIGN PREVIEW

Preview:

### Android

How the push approximately appears.

### Arabic Email

Mobile-width preview.

### English Email

Mobile-width preview.

Validate missing translations before sending.

---

# 40. CAMPAIGN SAFETY

Prevent accidental mass sends.

For large campaigns:

Show:

`سيتم إرسال هذه الحملة إلى 8,421 مستخدمًا مؤهلًا.`

Then require confirmation.

Implement RBAC.

Not every Admin role should automatically have permission to send global campaigns.

Potential permissions:

```text
campaign.view
campaign.create
campaign.schedule
campaign.send
campaign.cancel
```

Owner retains ultimate control.

---

# 41. QUIET HOURS

Support user-configurable quiet hours.

Example:

```text
عدم الإزعاج

من: 11:00 م
إلى: 7:00 ص
```

Non-time-critical engagement notifications should respect quiet hours.

A user-requested lesson reminder may follow the user's explicit schedule.

Security/account notifications are separate.

---

# 42. FREQUENCY CONTROL

Protect users from notification overload.

Implement a notification policy.

For general engagement campaigns:

- Avoid excessive daily messages.
- Deduplicate similar notifications.
- Avoid sending several campaigns within minutes.
- Respect preferences.

Do not optimize for maximum notification volume.

Optimize for relevance.

---

# 43. LOCALIZATION

Every system must support Arabic and English.

Default:

```text
Arabic
RTL
Light Theme
```

Notification content must use the recipient's preferred language where available.

Campaigns should support separate:

```text
title_ar
body_ar

title_en
body_en
```

Do not machine-mix languages at send time.

---

# 44. TIMEZONES

All scheduled functionality must be timezone-safe.

This includes:

- Upcoming lessons
- Campaigns
- Personal reminders
- Night Prayer
- Friday reminders
- Adhkar
- Quran Wird

Store canonical timestamps correctly.

Render in user-local timezone.

Recurring personal reminders must preserve intended local wall-clock behavior when timezone changes.

---

# 45. OFFLINE BEHAVIOR

Personal reminders should continue working where reasonably possible even when the user temporarily has no internet.

For example:

- Morning Adhkar reminder
- Evening Adhkar reminder
- Salawat reminder
- Personal Quran reminder

Server campaigns naturally require connectivity.

When connectivity returns:

Do NOT dump a large number of obsolete notifications on the user.

Define expiration windows.

---

# 46. EXPIRATION

Notifications need TTL/expiration rules.

Example:

A notification:

`الدرس سيبدأ بعد 15 دقيقة`

is useless six hours later.

Do not display stale time-sensitive push messages after their relevance expires.

---

# 47. DATABASE DESIGN

Design normalized migrations for concepts such as:

```text
scheduled_lessons

lesson_reminders

scholar_follows

series_follows

notification_preferences

notification_devices

notification_inbox

notification_campaigns

campaign_audiences

campaign_deliveries

email_outbox
```

Exact schema names may differ according to the existing architecture.

Do not duplicate user data unnecessarily.

Use indexes for dispatch queries.

---

# 48. API DESIGN

Extend `/api/v1/` cleanly.

Potential endpoints:

```text
GET    /upcoming-lessons

POST   /lessons/{id}/reminder
DELETE /lessons/{id}/reminder

POST   /scholars/{id}/follow
DELETE /scholars/{id}/follow

POST   /series/{id}/follow
DELETE /series/{id}/follow

GET    /notification-preferences
PATCH  /notification-preferences

POST   /devices
DELETE /devices/{id}

GET    /notifications
POST   /notifications/{id}/read
POST   /notifications/read-all
```

Admin campaign endpoints must remain protected by appropriate RBAC.

Do not blindly implement these exact URLs if the established API conventions use another consistent pattern.

---

# 49. ANDROID BACKGROUND WORK

Follow current Android platform best practices.

Use appropriate Android scheduling mechanisms for deferrable persistent background work.

Do not create a permanent background service merely to check reminders every minute.

Do not wake the device unnecessarily.

Battery efficiency is mandatory.

---

# 50. BOOT / TIME CHANGE

Personal reminder scheduling should survive appropriate lifecycle events.

Test:

- Device restart
- Timezone change
- Manual clock change
- App update
- Force stop behavior
- Permission revocation

Reconcile schedules safely where Android permits.

---

# 51. NOTIFICATION UX

Notifications should be concise.

Good:

Title:
`ورد القرآن`

Body:
`تابع وردك من حيث توقفت.`

Bad:

A huge paragraph containing multiple religious reminders and announcements.

One notification = one clear purpose.

---

# 52. CONTENT MANAGEMENT

Admin should manage reusable approved reminder text where appropriate.

Examples:

- Salawat reminders
- General Adhkar reminder templates
- Friday reminder templates

But personal schedules belong to users.

Admin must not manipulate individual worship schedules.

---

# 53. HOME SCREEN INTEGRATION

The Home screen may intelligently surface:

`مباشر الآن`

`درس قادم`

`أكمل وردك`

`أكمل السلسلة`

But avoid showing everything simultaneously.

Prioritize context.

Example hierarchy:

Live lesson
→ highest temporal priority

Upcoming lesson user explicitly follows
→ next

Continue Quran/Khatmah
→ personal continuity

Continue series
→ content continuity

---

# 54. USER CONTROL

The user must always feel in control.

Provide:

- Enable
- Disable
- Change time
- Change frequency
- Change channel
- Quiet hours
- Email preferences
- System notification settings shortcut where useful

Do not silently enable every reminder after account creation.

---

# 55. ONBOARDING

Do not ask the user to configure twenty notification options during onboarding.

Keep onboarding simple.

Later, introduce reminders contextually.

Examples:

When opening Morning Adhkar:

`هل تريد تذكيرًا بأذكار الصباح؟`

When following a series:

`إشعارات الحلقات الجديدة؟`

When viewing an upcoming lesson:

`ذكّرني`

This is better than one giant permission wall.

---

# 56. DESIGN

Follow the previously defined Athar premium UI/motion system.

Animations should be:

- Calm
- Fast
- Native
- Subtle

For example, tapping:

`ذكّرني`

may smoothly transition:

Bell outline
→
Bell enabled

with subtle haptic confirmation.

Do not use celebratory/confetti animation for enabling religious reminders.

---

# 57. TESTING

Write automated tests for at least:

- Reminder scheduling
- Timezone conversion
- Recurring reminders
- Lesson rescheduling
- Lesson cancellation
- Notification preference enforcement
- Quiet hours
- Completion-aware suppression
- Duplicate prevention
- Device-token rotation
- Invalid push token cleanup
- Campaign scheduling
- Campaign cancellation
- Email retry
- Deep links
- Arabic/English selection
- Notification expiration
- Permission denied
- Permission revoked

Test actual Android notifications on a physical device.

---

# 58. IMPORTANT PRIVACY PRINCIPLE

Never turn private worship activity into Admin surveillance.

The system may know enough locally or privately to provide the user's requested functionality.

That does NOT imply Admin should see:

"Ahmed missed his Wird."

or:

"User X hasn't completed Adhkar."

Do not implement such dashboards.

Aggregate health metrics are acceptable where genuinely needed and privacy-safe.

---

# 59. IMPLEMENTATION PHASES

Do not try to implement this entire system in one unsafe change.

Recommended sequence:

### Phase A
Upcoming lessons + database/API + Android UI.

### Phase B
Lesson reminder + local scheduling + deep links.

### Phase C
Follow scholars/series + new-content notifications.

### Phase D
Central notification preferences and Android channels.

### Phase E
Quran/Adhkar/Khatmah/Night Prayer personal reminders.

### Phase F
Push infrastructure + device registration.

### Phase G
Notification inbox.

### Phase H
Admin Push Campaign system.

### Phase I
Email campaigns + queue + preferences/unsubscribe.

### Phase J
Quiet hours, deduplication, frequency controls and analytics.

### Phase K
Full QA, performance, security, Arabic RTL and English LTR testing.

If the existing implementation plan suggests a safer ordering, integrate these phases into it rather than blindly replacing it.

---

# 60. DEFINITION OF DONE

This feature group is NOT complete merely because notifications can appear.

It is complete when:

- Upcoming lessons work.
- Live state works.
- Users can request/cancel lesson reminders.
- Schedule changes are handled.
- Scholar/series following works.
- Quran reminders work.
- Adhkar reminders work.
- Khatmah reminders work.
- Salawat reminders work.
- Night Prayer reminders work.
- Completion-aware suppression works where designed.
- Android notification permission is handled correctly.
- Notification channels work.
- Deep links work.
- Notification preferences work.
- Push works.
- Multiple devices are supported.
- Notification Inbox works.
- Admin campaigns work.
- Scheduled campaigns work.
- Test-send works.
- Email campaigns work.
- Email queue/retry works.
- Optional email unsubscribe/preferences work.
- Transactional emails remain unaffected.
- Arabic RTL works.
- English LTR works.
- Default language remains Arabic.
- Default theme remains Light.
- Quiet hours work.
- Deduplication works.
- Stale notifications expire.
- Worship privacy requirements are respected.
- Automated tests pass.
- Android build passes.
- Relevant features have been tested on a physical Android device.
- Documentation and TASK_TRACKER are updated.

After implementing these requirements, continue with the remaining Athar roadmap.

Do NOT stop the overall project after completing this subsystem.

The native Android application remains the primary product.