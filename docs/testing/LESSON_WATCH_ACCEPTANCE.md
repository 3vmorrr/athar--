# Native learning acceptance record

Date:2026-10-25. Debug functional checks are distinct from release performance, physical-device and deployment acceptance. Primary product remains native Android; these changes do not redesign the public PHP website.

## Executed evidence

| Scope | Observed result | Evidence |
|---|---|---|
| Shared backend | PASS9 suites /19916 assertions, including40 catalog and35 learning | dedicated3308 runner; `storage/engineering/adhkar-category-backend-retry.log` |
| Actual HTTP | PASS12 checks: watch/series/replies/authorization/scholar metadata/standalone filter | generated8004 fixture; `learning-http-current.log` |
| Native candidate build | Debug/test APK,15 JVM tests and lint passed including separate composer, preview isolation and native player bounds | `player-native-layout-build.log` |
| Native storage | PASS5 tests, including encrypted lesson restart/offline/explicit completion/account separation | API36; `watch-storage-device.log` |
| Fresh-install defaults | PASS on English-OS API36 and API34: Arabic, RTL, light, onboarding untouched | `lesson-fresh-default-retry.log`; `api34-fresh-default-retry.log` |
| Adhkar Arabic journey | PASS count/reset/automatic next/last entry/unspecified repetition count | API34 small phone; `api34-adhkar-ar.log`; guest system ANRs remain an environment limitation |
| Watch Arabic tall phone | Reached inline comments/replies; full-dialog selector initially matched the underlying preview too | API36 failed invocation; selector refined; complete rerun pending |
| Watch Arabic small phone | PASS complete journey: replies, draft close/reopen, pending submission, explicit completion, lesson switch, series and scholar | `api34-watch-composer-ar.log`; viewport360x640dp |
| English phone | PASS watch/series/scholar and adhkar journeys | `api34-learning-en.log`,2 tests; locale explicitly en, LTR, light |
| Official YouTube | PASS actual user-gesture playback, landscape configuration, same WebView identity, saved position and no inferred completion | `api34-player-native-layout.log`; outcome explicitly `playing`; native height405px/layoutHeight=-1, CSS iframe360x203 |
| Tablet/landscape watch | PASS Arabic and English at1280x800dp and130% font | `api34-watch-tablet-ar.log`; `api34-watch-tablet-en.log` |
| Tall Arabic phone | PASS inline comments/replies, expand/close, lesson switch, series and scholar | `api34-watch-inline-ar-retry.log`;360x800dp; both comments surfaces exercised |

Logs named without a prefix above are under ignored `storage/engineering/`. They contain test-fixture evidence, not deployment artifacts. Fixtures use generated isolated schemas and dummy content; the provider sample is the official IFrame documentation sample, not invented religious content.

Screenshot selection correction: selecting the last Compose root captured underlying watch metadata instead of the native dialog. Dialog assertions did pass, but those images are not evidence of the panel appearance. The capture helper now uses the instrumentation display screenshot, including the actual top window. Corrected tablet/full and phone/inline artifacts were visually inspected. One immediate phone/full capture caught a platform fade and showed the underlying page; the helper now allows350ms for native window animation before capture. The production surface was not made transparent to generate the image.

Debug diagnostic `learning-gfx-en-diagnostic-1.log` observed291 rendered frames,187 janky frames(64.26%),34ms median and200ms95th percentile. This headless SwiftShader/low-memory debug instrumentation result is poor and does not certify device/release performance. Draft state was found to reach the watch parent; a distinct preview row/count flow now isolates editor updates from the provider. Rerun diagnostics after the change, then perform proper release profiling at P7 rather than attributing all latency to application code or claiming smoothness.

## Focused rerun protocol

Build before installing, and stop the compiler daemon before booting a memory-constrained emulator. Target `emulator-5554` explicitly; never use an unqualified ADB mutation when a physical phone is connected. Wait for `sys.boot_completed=1` and the package service before installation. Use the dedicated `Athar_API34_QA` guest or a documented compatible QA guest. Do not erase another AVD to solve an infrastructure failure.

Install main/test debug APKs. On a clean dedicated sandbox, run FreshInstallTest with `freshInstallSmoke=true`. Run LessonWatchTest with `nativeApiSmoke=true`, `nativeLanguage=ar`; repeat English after explicitly setting the per-app locale to en. Subsequent Arabic size cases may set `submitComment=false` after the actual composer submission case has passed, preserving the real posting rate limit. Run AdhkarJourneyTest in both languages and NativeStorageTest. OfficialPlayerTest additionally requires `liveYouTubeSmoke=true` and Arabic locale; record provider_error separately from actual playing/rotation.

Inspect the generated native screenshots, actual pending-comment response and private progress acknowledgement. Opening a lesson must leave progress unchanged; explicit completion must persist. Record debug `dumpsys gfxinfo` as diagnostic only, including emulator/test overhead. Release jank acceptance remains P7.

## Failed infrastructure executions

The original API36 cold boot was killed for LOW_MEMORY. A later3072MB guest stalled its ADB transport while the isolated database logged host out-of-memory. Backend passed after that dedicated guest was stopped. API34 first boot temporarily lacked the package service; installation was retried only after boot completion. The fresh API34 guest also displayed system/Bluetooth error dialogs; Bluetooth was disabled on the emulator, not on the attached phone. A lint invocation encountered inconsistent Kotlin modules after a source file was added during analysis; sources were frozen and the complete build was rerun without disabling the detector. These invocations are not reported as passed application tests.
