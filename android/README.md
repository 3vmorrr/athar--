# Athar native Android application

This is a Kotlin/Jetpack Compose application, not a WebView wrapper. It is an in-progress implementation; the repository tracker distinguishes implemented foundations from remaining feature acceptance. The existing PHP website remains in place.

## Build

Use JDK17 and Android SDK36. Open this directory in Android Studio, or set `JAVA_HOME` and `ANDROID_HOME`. Android Studio can create the ignored `local.properties` SDK path. Windows properties paths must escape the drive colon (`C\:/...`). The committed Gradle9.1 wrapper verifies the official distribution checksum.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --max-workers=2
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build. The default API base is `http://10.0.2.2:8002/api/v1/` for the emulator. Start the PHP server on that port against a separately configured healthy database with migrations applied. Do not point the app at an unmigrated or corrupt database.

```powershell
.\gradlew.bat assembleDebug -PATHAR_API_BASE_URL=https://your-api.example/api/v1/
```

Release tasks require an HTTPS URL. Release signing/distribution and production endpoint selection are not configured. Release builds reject cleartext; debug cleartext is restricted to emulator/loopback hosts. Debug user CA trust supports local HTTPS testing only. Do not weaken the release network security configuration.

## Tests

```powershell
.\gradlew.bat testDebugUnitTest
# Start a compatible AVD/device, then:
.\gradlew.bat connectedDebugAndroidTest
```

JVM tests cover canonical Quran references, Night Prayer rules and session refresh races. Instrumented tests use real Android Keystore and in-memory Room to check private storage/sync, and validate the bundled Quran offline. They do not send real email or train/run a speech model.

Reports: `app/build/reports/`; Room schema: `app/schemas/`. No destructive Room migration fallback is enabled. Account-owned state/cache is partitioned by user ID; Compose ViewModels with private state are keyed by account. Secrets and cached payloads use Android Keystore AES-GCM; sensitive fields are not logged. App backup/transfer is disabled. Room indexing metadata is not a full encrypted-database implementation.

## Assets and dependencies

Arabic is the default language, English resources are parallel, and application locales persist through AppCompat. Light is the default appearance. Phone bottom navigation and tablet navigation rail share the same features. Quran text uses the bundled Amiri Quran font and verbatim Tanzil1.1 text with attribution/license metadata. The pre-build task checks bundled text/index/logo bytes against shared reference files. Sources and source checksums are under `../shared/`; original logo under `../docs/design/assets/`.

Prayer calculations use the official Java Adhan1.2.1 implementation (MIT), callable from Kotlin. Adhan2 versions0.0.7 and0.0.6 were evaluated but require newer Kotlin metadata/JDK binaries than this toolchain. No compatibility checks are disabled. Calculation/display should still be compared with local authoritative timetables and tested at polar/high-latitude boundaries.

Manual dependency injection keeps the initial graph small. Retrofit/OkHttp handles the shared JSON API, DataStore handles device settings, Room handles private local records/outbox, and coroutine dispatchers keep storage/search/calculation off composition. Media3 and WorkManager dependencies are present for subsequent media/reminder integration; their presence is not proof those modules are complete.

See [implementation tracker](../docs/progress/TASK_TRACKER.md) and [executed verification](../docs/testing/TEST_REPORT.md).

## Latest focused verification — 2026-10-25

Debug/test APK builds and lint pass.12 JVM tests and4 current API36 storage tests pass. The same authenticated real-HTTP UI journey passes Arabic phone light, Arabic phone dark at130% font scale, Arabic tablet and English tablet cases. See [journey setup and reviewed screenshots](../docs/testing/NATIVE_UI_JOURNEY.md), including failed attempts and test isolation. This is not full module acceptance, a signed production release or a performance certification.
