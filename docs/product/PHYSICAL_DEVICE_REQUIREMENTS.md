# ADDITIONAL REQUIREMENT
## Physical Android Device Build, Local Backend/Admin Environment & End-to-End Testing

Add the following requirements to the previous Athar implementation specification.

Do NOT restart or redesign the project.

After implementing the relevant features, I want the actual system to be run and tested using the **physical Android phone currently connected to this development computer**.

This is not optional emulator-only testing.

The development environment should support:

```text
Physical Android Phone
        ↓
Native Athar Android App
        ↓
Local Development API
        ↓
Local PHP Backend
        ↓
Local MySQL/MariaDB Database

Development Computer
        ↓
Local Admin Dashboard
        ↓
Same Local API / Database
```

The goal is to test the complete flow:

**Admin Dashboard → Backend/API → Database → Android App → Physical Phone**

and:

**Physical Phone → API → Database → Admin Dashboard**

---

# 1. DETECT THE CONNECTED ANDROID DEVICE

Before deployment, detect the currently connected Android device.

Use the Android SDK `adb` executable.

Do not assume `adb` is globally available in PATH.

If:

```cmd
adb devices
```

is unavailable, locate ADB from the installed Android SDK.

On Windows, common locations include:

```text
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
```

or the Android SDK path configured for the project/Android Studio.

Use the actual SDK configuration rather than hardcoding a path when possible.

Verify that the device state is:

```text
device
```

not:

```text
unauthorized
offline
```

If authorization is required, clearly report that the user must approve USB debugging on the physical phone.

Do not continue pretending physical-device deployment succeeded if ADB cannot actually access the device.

---

# 2. REPORT DEVICE INFORMATION

Once detected, report useful development information such as:

- Device model
- Android version
- API level
- ABI
- Screen resolution where useful
- Device ID/serial only in local development logs where necessary

Use this information when testing compatibility.

Do not unnecessarily persist device identifiers in the project.

---

# 3. BUILD THE ACTUAL ANDROID APPLICATION

Build the Android application using the project's Gradle wrapper.

For example:

```cmd
gradlew.bat assembleDebug
```

or the appropriate project command.

The build must actually succeed.

Do NOT merely state:

"Android project is buildable."

Run the build.

If it fails:

1. Read the actual Gradle error.
2. Identify the root cause.
3. Fix it.
4. Re-run the build.
5. Repeat until successful or until a genuine external blocker requires user action.

Do not hide compiler warnings/errors.

---

# 4. INSTALL ON THE CONNECTED PHONE

After a successful build, install the Debug build on the connected physical Android phone.

Prefer the project's appropriate Gradle installation task where possible:

```cmd
gradlew.bat installDebug
```

or install the generated APK through the Android SDK ADB executable.

If an older Athar Debug build is already installed, update it safely when possible without unnecessarily destroying app data.

If installation fails due to:

- Signature mismatch
- Version downgrade
- Insufficient storage
- Device authorization
- Unsupported Android version
- ABI issue

report the exact reason and resolve it safely.

Do NOT automatically uninstall the existing app and erase user data unless that is genuinely required.

If uninstalling is required, explain why before destructive action.

---

# 5. LAUNCH ATHAR ON THE PHONE

After installation, launch Athar on the physical device.

Verify that the correct application package/activity starts.

Do not consider deployment successful merely because APK installation succeeded.

Check startup logs for:

- Crash
- ANR
- Missing configuration
- Network security error
- API connection error
- Database migration error
- Dependency initialization error

Use Logcat/ADB appropriately.

---

# 6. START THE LOCAL DEVELOPMENT BACKEND

Start the PHP backend locally using the project's established development environment.

Use the existing architecture/configuration where available.

Do NOT create a second unrelated backend simply for testing.

The local environment should provide:

- PHP API
- Admin Dashboard
- Development database
- Required migrations
- Development configuration

Use the isolated development/test MariaDB/MySQL environment if that is safer than the previously identified damaged local database.

Do NOT modify or destroy the problematic original database files merely to make local testing easier.

---

# 7. DATABASE SAFETY

We previously identified possible InnoDB inconsistency in the original local MySQL environment.

Therefore:

**DO NOT attempt destructive repair of the original database automatically.**

For development integration testing, use the known-good isolated MariaDB/MySQL instance or create a safe dedicated development database.

Apply migrations to that environment.

Seed only development/test data where required.

Never overwrite production or irreplaceable local data.

---

# 8. START THE ADMIN DASHBOARD LOCALLY

Start the independent Athar Admin Dashboard locally.

The developer must be able to open it from the development computer using a local development address.

Examples may conceptually resemble:

```text
localhost:<port>/admin
```

but use the actual routing/ports defined by the project.

Do not hardcode an arbitrary URL if the established development architecture uses another one.

Verify:

- Admin login
- Dashboard
- Scholars
- Series
- Lessons
- Upcoming lessons
- Books
- Adhkar
- Comments/moderation
- Notifications
- Campaigns
- Settings

as features become implemented.

---

# 9. IMPORTANT — PHONE CANNOT USE LAPTOP LOCALHOST

The Android phone must NOT be configured to call:

```text
localhost
```

or:

```text
127.0.0.1
```

when the API actually runs on the laptop.

On a physical phone, `localhost` refers to the phone itself.

Configure the development connection correctly.

---

# 10. PREFERRED USB DEVELOPMENT CONNECTION

Because the phone is physically connected by USB, investigate using:

```cmd
adb reverse
```

for the local development API when appropriate.

For example conceptually:

```cmd
adb reverse tcp:<API_PORT> tcp:<API_PORT>
```

Then the Android development build may be able to reach the laptop service through the reversed port.

Use the actual API port.

Do not blindly assume a specific port.

Document this development setup.

This is preferred when it provides a reliable USB-only local development workflow.

---

# 11. LAN FALLBACK

If ADB reverse is unsuitable for part of the architecture, use the development computer's LAN IP.

For example conceptually:

```text
http://192.168.x.x:<port>
```

Determine the actual local address programmatically.

Do NOT hardcode the developer's current LAN IP into production source code.

Development endpoints must be configurable.

---

# 12. DEVELOPMENT API CONFIGURATION

Create proper build-environment configuration.

For example conceptually:

```text
Debug:
Local API

Release:
Production API
```

Do NOT require editing Kotlin source files every time the API URL changes.

Use an appropriate mechanism such as:

- BuildConfig
- Gradle properties
- local development configuration
- environment-specific configuration

Never commit secrets into the Android application.

---

# 13. HTTP DEVELOPMENT TRAFFIC

If the local backend uses plain HTTP during local development, handle Android network-security requirements safely.

Allow development HTTP only where required.

Do NOT globally weaken Release network security.

Production must use HTTPS.

Keep Debug and Release security policies separate.

---

# 14. ADMIN → PHONE TEST

Perform a real integration test.

Example:

From the local Admin Dashboard:

1. Create a test scholar if necessary.
2. Create a test series.
3. Create a lesson.
4. Set it as published.
5. Refresh/synchronize the Android application.
6. Verify that the lesson appears on the physical phone.
7. Open it.
8. Verify metadata.
9. Verify player behavior.
10. Verify series navigation.

This proves the complete pipeline works.

---

# 15. UPCOMING LESSON TEST

Test the newly requested upcoming-lessons system.

From Admin:

Create:

```text
Test Upcoming Lesson

Start:
A few minutes in the future

Reminder:
Enabled
```

On the physical Android phone:

- Confirm the upcoming lesson appears.
- Open it.
- Tap `ذكّرني`.
- Verify reminder state persists.
- Verify notification permission flow.
- Verify the scheduled notification.
- Verify deep linking.
- Verify cancellation.
- Verify rescheduling.

Then change the lesson time from Admin.

Confirm the application/backend correctly handles the new schedule.

---

# 16. LIVE LESSON TEST

Create a development live lesson.

Test:

```text
Scheduled
→
Live
→
Published/Replay
```

Verify the Android UI transitions correctly.

Test:

`مباشر الآن`

and ensure the player opens the correct content.

---

# 17. COMMENTS END-TO-END TEST

On the physical phone:

1. Open a lesson.
2. Open Comments.
3. Submit a test comment.

Then in Admin:

4. Verify the comment appears in moderation.
5. Approve it.

Then on Android:

6. Refresh/reopen comments.
7. Verify the approved comment appears.

If replies have been implemented:

8. Test replies.
9. Test collapsed/expanded reply threads.

---

# 18. PUSH NOTIFICATION TEST

When Push infrastructure is implemented:

Register the physical phone as a development device.

From Admin:

Create a test Push campaign targeted ONLY to the development/test user/device.

Send:

```text
اختبار إشعارات أثر
```

Verify on the physical phone:

- Delivery
- Arabic rendering
- RTL
- App icon
- Notification channel
- Tap behavior
- Deep link
- Read/unread inbox state where applicable

Do NOT test mass campaigns by sending them to real users.

---

# 19. EMAIL CAMPAIGN TEST

Use a safe development/test email recipient.

From Admin:

Create an email campaign.

Use:

`Send Test`

before any campaign dispatch.

Verify:

- Arabic RTL rendering
- English rendering
- Athar branding
- CTA/deep link
- Mobile layout
- Preference/unsubscribe behavior where applicable

Do not send bulk email during development testing.

---

# 20. PERSONAL REMINDER TESTS

On the physical phone test:

- Morning Adhkar
- Evening Adhkar
- Salawat
- Quran Wird
- Khatmah
- Night Prayer

For development testing, allow safe test scheduling a few minutes ahead.

Do not permanently alter production reminder defaults just to accelerate tests.

---

# 21. NOTIFICATION PERMISSION TEST

On supported Android versions test:

```text
Permission not requested
→
Feature explanation
→
Permission request
→
Allowed
```

Also test:

```text
Denied
```

Athar must remain usable when notifications are denied.

---

# 22. ARABIC-FIRST PHYSICAL TEST

The first physical-device test must verify the required default:

```text
Language = Arabic
Direction = RTL
Theme = Light
```

Check visually:

- Splash
- Onboarding
- Authentication
- Home
- Navigation
- Quran
- Adhkar
- Lessons
- Video player
- Comments
- Series
- Upcoming lessons
- Notifications
- Account

Arabic must be treated as the primary UI.

---

# 23. ENGLISH TEST

After Arabic verification:

Switch to English.

Verify:

```text
Language = English
Direction = LTR
```

Check for:

- Broken alignment
- Hardcoded Arabic
- Incorrect mirrored icons
- Clipped text
- Navigation problems

Then switch back to Arabic and verify persistence.

---

# 24. LIGHT THEME

The first physical-device test must use:

`Light Theme`

because Light is Athar's default.

If Dark Mode exists, test it separately.

Do not accidentally make Dark Mode the default because the development phone is configured for system Dark Mode unless the product specification explicitly allows system-default behavior.

Athar's specified fresh-install default remains Light.

---

# 25. PERFORMANCE ON THE REAL PHONE

Use the physical device to inspect:

- Startup speed
- Scroll smoothness
- Player performance
- Comments scrolling
- Series lists
- Quran scrolling
- Adhkar counter responsiveness
- Animation smoothness
- Image loading
- Memory
- Network requests

Watch for:

- Jank
- ANRs
- Excessive recomposition
- Slow API requests
- Main-thread work
- Memory leaks

Fix significant issues rather than merely documenting them.

---

# 26. LOGCAT

During physical testing, monitor application logs.

Investigate:

```text
FATAL EXCEPTION
ANR
Network errors
HTTP errors
Serialization errors
Database errors
WorkManager failures
Notification failures
Player failures
```

Do not expose secrets, passwords, auth tokens, SMTP credentials, or sensitive personal data in logs.

---

# 27. KEEP THE DEVELOPMENT ENVIRONMENT RUNNING

When practical during the implementation/testing session, keep:

- Local Backend
- Development database
- Admin Dashboard

available while performing integration tests.

Do not start duplicate servers on random ports every time.

Use documented stable development ports.

If a port is occupied, identify the existing process before killing anything.

Do not terminate unrelated user processes.

---

# 28. CREATE A LOCAL DEVELOPMENT STARTUP WORKFLOW

Create a convenient documented developer workflow for starting the Athar local stack.

Prefer a safe script if appropriate.

For Windows, this may conceptually be something such as:

```text
dev-start.bat
```

or an equivalent project-native solution.

It should help start the required local development services.

Do not embed passwords or secrets in the script.

---

# 29. CREATE ANDROID DEPLOYMENT WORKFLOW

Also create a convenient safe development command/script if appropriate.

Conceptually:

```text
android-install-debug.bat
```

It may:

1. Locate Android SDK/ADB.
2. Verify connected device.
3. Configure ADB reverse if needed.
4. Build Debug APK.
5. Install/update Debug APK.
6. Launch Athar.
7. Report success/failure.

Do not make the script destructive.

---

# 30. DOCUMENT THE WORKFLOW

Create/update documentation such as:

```text
docs/development/LOCAL_DEVELOPMENT.md
docs/development/ANDROID_PHYSICAL_DEVICE.md
```

Document:

```text
How to start database
How to start backend
How to start Admin
How to connect phone
How to use ADB
How to configure adb reverse
How to build Android
How to install APK
How to launch app
How to inspect Logcat
How to stop services safely
```

The next developer should not need to rediscover the entire setup.

---

# 31. DO NOT BLOCK DEVELOPMENT ON OPTIONAL FEATURES

If some future feature such as Push campaigns has not yet been implemented, do NOT stop the entire integration test waiting for it.

Test everything currently implemented.

Record remaining tests in:

`TASK_TRACKER.md`

Then continue implementation.

Repeat physical-device integration tests as new major features become available.

---

# 32. CONTINUOUS PHYSICAL DEVICE TESTING

Do not wait until the entire Athar project is complete before installing it on the phone.

After every major Android milestone:

```text
Build
→
Install
→
Launch
→
Smoke test
→
Check Logcat
→
Fix
→
Continue
```

This is mandatory.

---

# 33. IMPORTANT — ACTUALLY EXECUTE THE TESTS

Do not respond with only instructions telling me how I could install the application myself.

You have access to the development environment.

Therefore, where the environment permits it:

- Run the build.
- Detect the connected device.
- Install the app.
- Launch it.
- Start the local services.
- Run the integration tests.
- Inspect failures.
- Fix them.

Only ask me for manual action when genuinely required, such as:

- Accepting USB debugging authorization on the phone.
- Granting Android notification permission.
- Interacting with a UI flow that cannot be automated safely.
- Providing an external credential that is genuinely required.

Do not turn an executable development task into a tutorial for me.

---

# 34. REPORT AFTER EACH MAJOR PHYSICAL TEST

Provide a concise test report containing:

```text
Android build:
PASS / FAIL

Physical device detected:
YES / NO

APK installed:
YES / NO

App launched:
YES / NO

Local Backend:
RUNNING / FAILED

Local Admin:
RUNNING / FAILED

Database:
RUNNING / FAILED

Phone → API:
PASS / FAIL

Admin → API:
PASS / FAIL

Arabic RTL:
PASS / ISSUES

English LTR:
PASS / ISSUES

Light Theme default:
PASS / FAIL

Critical crashes:
NONE / details

Remaining blockers:
...
```

Do not report PASS unless it was actually tested.

---

# FINAL REQUIREMENT

The objective is not simply to produce source code.

At appropriate milestones, Athar must actually run as a complete local development system:

**Native Android app installed on the connected physical phone**

↕

**Local PHP API**

↕

**Local MariaDB/MySQL database**

↕

**Local Admin Dashboard**

Use the physical phone currently connected to the development computer as the primary real-device test target.

Continue implementing the remaining roadmap after successful testing.

Do not stop development merely because the first APK successfully installs.