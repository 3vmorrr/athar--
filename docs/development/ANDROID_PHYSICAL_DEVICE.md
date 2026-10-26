# Physical Android development

Long instrumentation journeys must keep only their test Activity/window awake while active, then restore/close it. Do not change the phone's global timeout or disable its secure lock. If the display is asleep, normal `KEYCODE_WAKEUP` and `wm dismiss-keyguard` may resume an already authorized nonsecure session; a secure credential prompt requires the user's physical action. Lazy-list tests must scroll the containing list to a known index before looking up a virtualized header/control; absence from the semantics tree is not proof that the user cannot scroll to it.

The25–26 October checkpoint successfully updated/launched the physical API27 app, exercised the real USB API and Arabic/English upcoming fixture journeys. Use [the physical report](../testing/PHYSICAL_DEVICE_REPORT.md) for exact scope; this does not certify API33 notification permission, independent Admin, real broadcast or release smoothness. Reapply reverse mappings after an old-device install/reconnect.

The current user explicitly authorized installation and testing on the attached physical Android phone. This supersedes the previous emulator-only precaution. Select an exact detected serial for every ADB mutation; never use an unqualified install or target all devices. Device identifiers belong only in necessary ignored local logs, not committed reports.

## Safe USB workflow

```powershell
.\scripts\start-local.ps1 -LoopbackOnly
.\scripts\android-install-debug.ps1
```

The deployment script resolves the SDK from `android/local.properties`, Android SDK environment variables or the installed local SDK. It detects exactly one physical device, requires state `device`, checks the actual local API, builds with the existing Gradle wrapper and `ATHAR_API_BASE_URL=http://127.0.0.1:8002/api/v1/`, configures the matching USB reverse, installs with `-r`, and launches the real main activity. If multiple phones are attached, provide `-DeviceSerial` explicitly. Do not commit the serial.

The phone's loopback URL reaches the computer only because the explicit ADB reverse exists. Without that tunnel it points to the phone. No LAN IP is embedded in production source. Release builds still require HTTPS, and only Debug has narrowly scoped development HTTP exceptions.

The script does not uninstall, clear application data, downgrade versions, grant all permissions, clear Logcat or overwrite a conflicting reverse mapping. Signature/storage/authorization failures must be reported and resolved without automatically erasing data. An API-origin change selects a different encrypted account/cache namespace; previous local records are retained.

After installation, inspect the actual activity/UI and application-scoped startup logs. Install success alone is not launch, network, localization or feature acceptance. Preserve existing preferences/data. An existing installation cannot prove a fresh-install default by clearing it; report that limitation and test Arabic/RTL/Light explicitly.

## Integration fixtures

Instrumented fixture tests can use `nativeApiSmoke=true` and `nativeFixtureHost=127.0.0.1` with a device-specific reverse for8004. The test-only host allowlist permits only loopback/emulator bridge and a fixed generated-fixture port. Production contains no endpoint/authentication override hook. Never register a real account or send mass email/push during automated tests.

## Current device baseline

Detected2026-10-25: Samsung SM-M105G, Android8.1.0/API27, ARM32 (`armeabi-v7a`),720x1520. This meets the application's minSdk26. Record actual install/startup/phone-to-API and UI outcomes separately. API33 notification permission behavior requires another supported Android target; API27 still needs channel/settings and real delivery tests.

## Required milestone checks

Build → install/update → launch → Arabic RTL/light → API/database → implemented feature journeys → English LTR → restore Arabic → app-scoped crash/ANR review. Repeat after substantial milestones. Full independent Admin/upcoming/push tests remain open until their implementations exist; this does not block testing already implemented features.

Official reference: [Android Debug Bridge](https://developer.android.com/tools/adb). Emulator cold boots can use `scripts/wait-android-qa.ps1`; its target is deliberately fixed to the dedicated QA emulator and does not select a physical phone.
