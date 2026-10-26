# Verification report

##26 October — optional upcoming context (tested increment)

Final preview build/test APK/lint PASS (3m9s);27 JVM cases and291 paired resources PASS. Current production APK was installed with retained data. Final physical Arabic3/3 PASS (22.414s), English3/3 PASS (21.911s), logs `event-context-preview-physical-ar.log` / `event-context-preview-physical-en.log`. Updated Arabic/English live-detail captures were inspected: the invalid fixture thumbnail correctly collapses to localized compact unavailable text, while course/topics/date layout remains readable and scrolling/book navigation/playback gating assertions pass. This is not proof of real live video playback. Initial lazy-header/sleep failures remain documented below.

Corrected-harness rerun: Arabic3/3 PASS (23.117s), English3/3 PASS (22.484s). The actual route checked localized course/topics/page references, opened the published related book and returned to the same event, then completed schedule/live/replay/cancellation assertions. Detail captures were inspected; their intentionally invalid fixture thumbnail showed an oversized empty preview fallback. Production preview handling now has explicit loading feedback, stops its spinner after completion and collapses image failures to a compact localized row. This visual change requires the subsequent build/device gate before the increment is checkpointed.

All10 backend suites executed successfully after migration015/service changes: **20,000 assertions**, including80 upcoming/context/visibility/revision/validation cases. Private backed-up development/fixture migration succeeded; development read-only verification reports15 matching versions/34 tables. Native build/test APK/lint passed (3m39s),27 JVM cases and290 resources passed.

Initial context physical batch is **not a pass**: Arabic reached the new book/course/topic navigation assertions, then its refresh lookup failed because the separate lazy header item had been virtualized after scrolling deep into the larger detail card. English UI cases had no active Compose hierarchy; device diagnostics confirmed `Wakefulness=Asleep`, display OFF, with no matching current Athar fatal in the scoped crash review. The device was woken and its nonsecure keyguard dismissed; no credential bypass or global timeout/locale/permission setting was changed. The journey now explicitly scrolls the lazy list to index0 before refresh, and test windows hold KEEP_SCREEN_ON only for their own lifetime. Initial logs `event-context-physical-ar.log`/`event-context-physical-en.log` are retained. Rerun outcomes follow execution.

##26 October — upcoming home/state increment (tested increment)

Final rerun: **Arabic3/3 PASS (21.487s), English3/3 PASS (20.883s)** on the same physical API27 device. Logs `upcoming-home-physical-ar-retry.log` / `upcoming-home-physical-en-retry.log`. Actual home→event→back→view-all→state-transition journey passes, alongside isolated cached-live/unavailable/replay presentation and real Room/Keystore encrypted cache/locale/owner/404-removal tests. Arabic and English home captures were inspected after retrieval from app-private test output. The earlier English harness failure remains below. Test-only APK/lint rebuild passed (1m13s); production main source was unchanged by that correction. No new backend migration or real broadcast/message send occurred.

Initial physical run: Arabic3/3 PASS; English home journey and encrypted storage PASS, isolated offline-presentation case FAIL. The isolated Compose harness supplied LocalConfiguration/RTL but retained an untranslated LocalContext for string resources. It now supplies an explicitly localized context as well. This is a test-surface correction; production uses the actual localized AppCompat activity. The failed run remains `upcoming-home-physical-en.log`; full reruns and home captures follow. Do not count the initial English run as3/3.

Current `upcoming-home-current-build.log`: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug PASS (3m).26 JVM cases pass, including4 new controller cases for cached/removed/forbidden states, cancellation, pagination retry and bounded preview queries.287 paired resources pass. Current APK DEX includes the home section; test APK includes offline presentation and encrypted event-cache cases. Physical Arabic/English reruns are recorded after execution, not inferred from packaging. Backend is unchanged from the checkpoint; no new schema or external delivery is claimed.

## Formal checkpoint25–26 October2026

See [source/scope audit and24-phase roadmap](../audit/CHECKPOINT_2026-10-25.md). Current executed results:19,974 backend assertions in10 disposable integration/domain suites;22 freshly rerun JVM tests; debug/test APK packaging and lint PASS;285 localization resources PASS;69 PHP syntax checks PASS (plus the subsequently added schema verifier executed successfully). Healthy development schema:14 matching migrations/33 checked tables. Physical real API smoke1 PASS; upcoming Arabic1/English1 PASS and inspected captures; previous8 physical storage cases and selected feature journeys remain regression evidence. Full independent Admin, real SMTP delivery, FCM/reminders, encrypted restore rehearsal and release performance remain open. Historical sections below retain failures/observations and must not be mistaken for current full-product acceptance.

## Discovery evidence — 2026-10-24
- `git rev-parse HEAD`: baseline recorded in the worktree audit.
- `python scripts/audit_repository.py`: 75 files inventoried before the documentation expansion; private data excluded.
- PHP CLI reports 8.0.30; MariaDB reports 10.4.32 and migrations 001–009.
- Canonical metadata fetched from AlQuran Cloud meta: 114 surahs, 6236 ayahs. Legacy backend static count table: 109 entries, sum 6213.
- Android SDK/JDK/Gradle installed; ADB initially reports no connected device. Two AVD definitions are available.
- Official Google skill installation succeeded.

## Executed foundation checks - 2026-10-25

| Command/check | Result |
|---|---|
| `php tests/domain_test.php` | PASS: 6382 canonical references/range/target/DST/pace/streak assertions |
| `php tests/quran_text_test.php` | PASS: 13352 verbatim Tanzil text/section/search/daily assertions |
| `php tests/api_identity_test.php` | PASS: 43 isolated identity/OTP/rotation/replay/rates/migration/cookie-adapter assertions; fake email only |
| `php tests/catalog_progress_test.php` | PASS: 35 isolated publication/ownership/CAS/retry/comments/counters/bookmark-coordinate/plan assertions |
| `php tests/night_prayer_api_test.php` | PASS: 32 isolated private CRUD/CAS/idempotency/tombstone/sync/analytics assertions |
| `php tests/backup_assets_test.php` | PASS: 9 asset coverage/hash/path/corruption assertions |
| `php tests/media_provider_test.php` | PASS:15 injected MP3Quran catalog/recording/timing/origin assertions |
| `php tests/tafsir_provider_test.php` | PASS:8 injected Quranpedia catalog/reference/markup/canonical-boundary assertions |
| `powershell -File scripts/test-backend.ps1` | PASS: all8 suites,19876 assertions on dedicated3308; data-directory guards and generated schemas; `.env` disabled in isolated runner |
| `python tests/web_identity_http_test.py` | PASS:15 actual HTTP cookie/API registration-confirmation/HMAC verification/reset/password-change/suspension checks on isolated8004 fixture API; fake email only |
| `android/gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug --max-workers=2` | APK assembled; 9 JVM tests passed; initial lint rejected four configuration errors, corrected in subsequent run |
| `android/gradlew.bat -p android connectedDebugAndroidTest lintDebug --max-workers=2` | BUILD SUCCESSFUL: 2 instrumented tests on Pixel API36 Android16 emulator; lint passed |

JVM tests exercise canonical references/sections, inclusive/no-wrap ranges, elapsed DST night, weighted pace/exemption bridging, pending account gate, concurrent refresh serialization, revoked refresh and logout-vs-refresh race. Device tests load all6236 original Quran verses/search offline, exercise real Room/Android Keystore, save two ordered offline edits, synchronize acknowledgements, verify account isolation, clear owned data and assert exemptions never enter outgoing bodies.

Reports are in ignored `android/app/build/reports`, `build/test-results` and `build/outputs/androidTest-results`. A later increment initially hit host JVM native-memory exhaustion while the emulator was running. The emulator was stopped and Gradle memory/workers reduced. The current calendar/theme/search-normalization increment subsequently passed `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --max-workers=1`:12 JVM tests, zero failures, lint passed and both APKs built. No release performance claim follows from a debug build.

The first real-HTTP Arabic interface journey passed on API36: home catalog, lesson favorite, back navigation, Quran bookmark and activity recreation. Four native storage tests also passed (approved media boundaries, durable adhkar cycles, bundled Quran, private ordered Night Prayer sync). A subsequent screenshot-test cold-boot run terminated before test execution with `Process crashed`; no Java/native crash trace was present in the retrieved crash buffer, while the guest logged low-memory process kills. This run is recorded as failed infrastructure execution, not a passed UI test. Subsequent runs used more guest memory and no concurrent compiler. The corrected expanded journey subsequently passed on a light Arabic phone, dark Arabic phone at130% font scale, Arabic tablet and English tablet. The English case required explicit OS per-app language setup on API36; its first attempt failed because the platform was still set to Arabic. See NATIVE_UI_JOURNEY.md for exact cases and screenshots. The latest4 storage tests also passed on the current APK. These are focused case checks, not whole-application or release acceptance.

## Limits and remaining checks

- Full feature acceptance is incomplete: independent admin, full native media/provider tools, native inference, reminders, full calendar/year visualization and release acceptance remain open.
- Web OTP issuance/verification/reset now share tested services, transactional attempts, authentication keys and database rate buckets with the API. Cookie account creation and login also use shared validation/password hashing. Guest import remains an explicit web adapter transaction; Cookie password changes now also share validation/revocation and are covered by real HTTP tests. Profile changes, deletion and independent-admin boundaries still need full migration/acceptance.
- No real recipient SMTP delivery, compatible public HTTPS API deployment or signed release is certified.
- Focused asset tests do not certify full encrypted archive create/restore.
- The original corrupt database files remain preserved. Local runtime now uses a separately validated22-table logical import on3309; read `../deployment/LOCAL_RUNTIME.md`. This is not a physical repair of the original files.
- Emulator cold boot showed a System UI ANR while the host compiled. Application crash output was empty at launch. No physical-device startup/performance measurement is claimed.
- Lint warnings include pinned dependency update suggestions, resources reserved for pending modules, plurals candidates, guarded API attributes and icon metadata. Passing lint is not accessibility/release certification.

## Native learning increment — in progress2026-10-25

- Backend learning suite:34 passing isolated assertions, including route authentication/admin authorization. Real HTTP fixture probe:10 passing watch/series/discussion/private visibility checks.
- Debug/test APK build,15 JVM tests and lint pass before device acceptance. The first compilation detected a missing closing lambda brace in the new screen; corrected before successful builds.
- Fresh-install test on an English OS passes Arabic locale, RTL direction, light theme and untouched onboarding defaults. The initial cold-boot invocation died before test execution; Android ApplicationExitInfo explicitly reports reason3 LOW_MEMORY. This is retained as a failed harness execution. Disabling the crashing emulator Bluetooth service and rerunning on the stabilized dedicated guest passed.
- New lesson/comment/player/rotation/live-provider device cases are not yet accepted. Keep L03/L04 in progress until actual outcomes are recorded.

### Learning, scholar and adhkar follow-up

- Current isolated backend run passes all9 suites with19916 assertions: catalog40 and learning35, with the other suites unchanged. Real HTTP learning/scholar probe passes12 checks on the generated fixture schema at8004; it compares progress before/after reading instead of requiring a permanently pristine fixture.
- Watch/series/scholar candidate APK/test APK,15 JVM tests and lint passed. Bilingual resource names match (254 keys after the adhkar increment); resource parity does not replace visual localization acceptance.
- All5 current native storage tests pass on API36, including lesson checkpoint restart/offline retry/explicit completion/account isolation.
- Arabic watch UI reached inline comments, expanded replies and the full panel. The full-panel assertion initially matched the same comment in the underlying metadata preview and the dialog; selector/screenshot roots now distinguish the dialog. This invocation remains failed, not a complete UI pass.
- Live official IFrame reached ready but the initial automated user gesture did not produce a playing/error event within25 seconds. Playback/rotation remain unverified; diagnostic screenshots and player-state probe were added rather than reporting readiness as playback success.
- A later API36 cold boot stalled and its ADB transport disconnected. Concurrent isolated database startup explicitly logged out-of-memory and failed. Stopping only the dedicated emulator and rerunning backend passed. The lower-memory API34 Google APIs QA AVD is separate from existing devices; no original userdata was wiped and the attached physical phone is not targeted.
- Current Arabic phone watch journey passes: root/reply browsing, retained composer draft, pending moderation submission, explicit completion, lesson switch, series and scholar navigation. English phone watch/series/scholar and adhkar journeys pass(2 cases). Arabic adhkar count/reset/auto-next/end/unspecified-count also passes. Evidence and earlier failures are distinguished in [LESSON_WATCH_ACCEPTANCE.md](LESSON_WATCH_ACCEPTANCE.md).
- Official player probe found the actual geometry defect: native/HTML viewport360x203 but iframe360x0, state5. Numeric official player sizing plus resize handling and a minimum-height assertion are rebuilding. This is not recorded as a provider authorization failure or successful playback.
- Debug SwiftShader instrumentation measured64.26% janky frames in a291-frame sample. This poor result does not certify release performance; editor state has now been isolated from the watch parent and provider. Corrected display screenshots and tablet/landscape/provider reruns remain pending. No physical-device or complete feature acceptance is implied.

### Accepted watch follow-up and current reading increment

- Official live sample now passes actual playback and landscape rotation with the same WebView, persisted checkpoint and no automatic completion. Final geometry is native720x405px, MATCH_PARENT height(-1), iframe360x203 CSS pixels. The zero CSS layout came from Chromium wrap-content behavior; CSS-only resizing did not fix it. Evidence: `api34-player-native-layout.log` and explicit `playing` outcome.
- Arabic/English landscape tablet at130% font and tall Arabic phone both pass the complete watch/series/scholar journey. Both inline and full discussion surfaces were exercised. Physical fullscreen/back, broader provider/network cases and release performance remain open.
- Native reading build passes debug/test APK,15 JVM tests and lint. The first build caught a duplicate resume_reading string; the existing resource was reused. `scripts/verify_android_resources.py` passes260 unique paired resources and format arguments.
- All6 native storage cases pass on API34:5 existing plus the new reading-position canonical/encryption/restart/offline/lost-acknowledgement/conflict-choice/account-isolation case.
- First Arabic reading journey saves and synchronizes the position, then scrolls precisely to the saved ayah. Activity recreation exposed that the Quran tool mode used non-saveable UI state and returned to the tool menu. Mode/search restoration is fixed in source; current rebuild and Arabic/English reruns remain pending. No new reading completion is inferred.

### Reading acceptance and next audio gate

- `deferred-scroll-build.log`: debug/test APK,15 JVM tests and lint pass after cursor/reset deferral. Resource verifier passes260 unique paired keys/formats.
- `api34-reader-en-deferred.log`: English real HTTP resume journey passes, including three activity recreations (36.685s). `api34-shared-scroll-ar.log`: Arabic reading/watch/adhkar all pass (3 cases,53.05s); reading includes three recreations.
- Earlier English runs failed separately with a Compose measure/layout exception and a truncated JSON response. Neither is counted as a pass. Deferred cursor/reset requests replace immediate layout-affecting resets; broader release/device stress remains open.
- Audio review found configuration-local selection while playback lived in a ViewModel. Selection now lives alongside its owner, with separate loaded-item identity and supported-edition filtering. Audio unit/recreation cases are being built; actual provider playback/timing is not yet accepted for this increment.

- `audio-selection-build.log` passes debug/test APK,17 JVM cases and lint. The first audio UI invocation (`audio-selection-ar.log`) failed connecting to the stopped local8004 test server before entering the journey. The server/database runtime was restarted without repair/reset; `/api/v1/health` returns available. Rerun and live-provider probe remain pending.

### Audio selection and actual-provider acceptance

- `audio-playback-build.log` and the focused test-label rebuild pass packaging,17 JVM cases and lint. Quran-name test selectors now use canonical source names with diacritics rather than an abbreviated literal.
- `audio-ar-provider-current.log`:2 cases pass (34.278s). Arabic selection survives three recreations and radio excludes reciter/surah controls. The actual MP3Quran whole-surah stream plays, real ayah timings load, changing a paused selection does not autoplay, explicit Play loads it, and changing the active source refreshes timing. `official-audio-outcome.txt` explicitly reports playing; this is not a mocked-media success.
- `audio-selection-en-current.log`: English selection/recreation/radio separation passes (18.322s).
- Next adaptation bounds and scrolls controls within available height, reserving space for follow-along/radio content. Landscape accessibility tests and the current rebuild remain pending. Physical lock-screen, interruptions, offline recovery, one-ayah clipping and release profiling are still open.

### Adaptive audio gate and Arabic OTP follow-up

- `audio-adaptive-build.log` passes debug/test APK,17 JVM cases and lint. `audio-adaptive-ar.log` passes2 cases (39.625s), including actual playback and new landscape control scrolling. `audio-adaptive-en.log` passes the English landscape/recreation journey (24.944s). The Arabic landscape display capture was reviewed; controls are reachable, while the shared shell still consumes substantial landscape height and needs P7 polish.
- Final audio follow-up stops a formerly playing recording if the new selection has no supported edition. The opt-in provider case now also checks a real timed ayah clip; these additions are rebuilding and are not yet accepted.
- Account follow-up adds one shared OTP normalizer for Arabic-Indic/Eastern/ASCII digits in both reset and verification fields. Passwords and server validation are unchanged. Unit and Arabic/English input-only UI cases are pending; no message is sent and no password changed by those UI cases.

### Current audio/OTP candidate accepted

- `audio-otp-build.log`: debug/test packaging,20 JVM cases and lint pass (zero lint errors). The resource verifier passes260 paired keys before the following feedback localization increment.
- `audio-otp-ar-final.log`:3 cases pass (59.427s): selection/recreation/landscape/radio, actual provider playback and OTP input. The live probe now confirms a real timed ayah clip and stopping the previous recording when a selection has no supported edition. The explicit outcome file includes both successful checks.
- `audio-otp-en-final.log`:2 cases pass (27.705s): selection/recreation/landscape/radio plus Arabic/Eastern pasted OTP input in English UI. No mail or password mutation is triggered.
- Next identity/profile feedback increment replaces raw error-code output with localized resources, including a generic fallback for unknown internal details. Current paired resource verification passes267 keys; build/device gates are pending.

### Physical device and upcoming foundation

- Physical USB Debug build succeeds; streamed install initially fails without a useful package-manager reason. Non-streaming `-r` succeeds, the actual main activity resumes and Arabic/RTL/light are visually observed without deleting existing app data.
- Real application-to-8002 API probe passes on Samsung API27. Both Arabic and English identity feedback/OTP cases pass (2 per language), including stable error-code classification despite different remote message text. Watch/series/comments and exact Quran resume/three recreations pass on hardware; adhkar fails its first-count assertion. See PHYSICAL_DEVICE_REPORT.md for partial gates and the failed case.
- Upcoming foundation passes all10 isolated backend suites:19968 assertions including52 new event state/UTC/visibility/CAS/change-fact/authorization checks. Migration014 is tested on generated schemas; no automatic migration of the original or healthy application data occurs.
- Counter follow-up adds an item-targeted private lookup, cold-device baseline import, guarded refresh, equivalent-state acknowledgement and explicit true-conflict behavior. Native storage/device reruns are pending; the previous failing case remains failed until rerun evidence exists.
