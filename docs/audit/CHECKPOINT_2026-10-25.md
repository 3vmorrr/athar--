# Formal implementation checkpoint — 25 October 2026

This is an implementation audit, not a release certificate. Native Android is the primary product. The legacy public PHP website is retained; its embedded administration page does not satisfy the independent Admin requirement. No completed implementation was intentionally discarded.

## Scope and evidence

Reviewed the approved `product/MASTER_REQUIREMENTS.md`, `NIGHT_PRAYER_REQUIREMENTS.md`, `UI_UX_REQUIREMENTS.md` (the supplied text ends mid-section), `ENGAGEMENT_REQUIREMENTS.md`, `PHYSICAL_DEVICE_REQUIREMENTS.md`, the detailed watch requirements recorded in `features/LESSON_WATCH.md`, and architecture, feature, security, test and deployment specifications. Compared them with Android source/manifest/build configuration, backend services/routes, migrations001–014, legacy handlers, and actual test evidence. Documentation and placeholders are not counted as functional implementation. Historical baseline audits remain historical; this report supersedes their current-status implications.

Primary source anchors: `android/app/src/main/java/org/athar/app/` (auth, core, quran, media, adhkar, learning, nightprayer); `backend/src/` (Identity, Catalog, Progress, Learning, NightPrayer, Providers, Http); `database/migrations/`; `public/index.php`; `app/mailer.php`. There is no independent `admin/` application. WorkManager as a dependency is not a notification scheduler.

## Actual status

| Area | Implemented functional code | Partial implementation | Not implemented / acceptance missing |
|---|---|---|---|
| Android | Native Compose shell; explicit Arabic/RTL/light defaults; persisted language/theme; onboarding and verified-auth gates; protected tokens; bundled Quran/index/search; reader/tafsir; durable explicit reading position; Media3 playback; sequential adhkar counters; scholar/series/watch; own moderated comments/replies; explicit lesson completion and checkpoint resume | Quran tools/bookmarks/navigation and all reader states; catalogue/books; account/export/deletion/favorites; one-plan Khatmah editor; encrypted offline Night Prayer sessions/calendar/calculators/conflicts; upcoming/live native list/detail; offline synchronization coverage | Native phoneme inference/model delivery; full book download manager/PDF/offline; complete Night Prayer timer/continuation/yearly insights; complete notification/reminder/follow/inbox flows; full-device release acceptance |
| Backend/API | Versioned authenticated API, rotating credentials, database throttles/OTP, ownership, canonical Quran, catalogue/progress, provider adapters, learning/discussion/moderation, Night Prayer CAS/idempotency, upcoming/live state/CAS/visibility/UTC transitions | Legacy identity migration boundaries; general synchronization/export semantics; service health/owner backup operations; upcoming extended metadata; existing legacy email batching | Notification preferences/device tokens/follows/inbox/push dispatch APIs; independent Admin session integration; complete queued engagement email policy; approved Dorar/live SMTP acceptance |
| Independent Admin | No independent application yet | Reusable legacy CRUD/users/roles/audit/moderation/settings/Gmail/campaign/owner code and protected JSON moderation/event APIs exist | Separate login/session/shell/navigation; bilingual complete CRUD, RBAC matrix, event editor, campaigns, safe Night Prayer settings, responsive and security acceptance |
| Database | Additive migrations001–014; native identity/progress/Night Prayer/discussion/events/translations/change facts; tested constraints and conflict revisions | Private healthy development import and verified logical pre-migration backup; original corrupt XAMPP data preserved | Schemas for follows/devices/inbox/reminder policies/push campaigns/dispatch; extensible engagement email queue; full encrypted archive restore rehearsal |
| Notifications/reminders | Legacy transactional email and manual campaign batching; event change facts | Reusable email opt-out/recipient states exist; no native delivery acceptance | Central channels/preferences/contextual permission, local scheduler/reboot/timezone/quiet hours, lesson consent/reminders, follows, personal completion-aware routines, FCM lifecycle, inbox, push campaigns, TTL/dedup/frequency controls |
| UI/UX | Official brand/logo, licensed Arabic/Quran typography and icons, original watch hierarchy, collapsed comments/description/playlist, native composer, adaptive navigation, scoped motion | Arabic-first localization and English resources; loading/error/empty states in implemented screens; tablet/large-font tests for selected journeys | Full screen-by-screen visual/accessibility acceptance; screen-reader/contrast/focus audit; remaining tablet composer/account clipping/reader toolbar/landscape density findings; release motion/jank profiling |
| Tests | Domain/text, isolated database/API/provider tests; JVM tests; native storage, localization, watch/reader/counter/upcoming journeys | Provider playback demonstrated on emulator; physical USB smoke and selected journeys; fixtures clearly separated from real providers | Full Admin acceptance; real SMTP inbox delivery; push/reminders; broad network loss/lockscreen/fullscreen hardware testing; complete security/backup restore/deployment E2E |
| Physical device | Authorized Samsung API27 ARM32; actual build/update/launch, scoped startup capture, phone→local API, Arabic/English identity and counter tests, storage ownership/conflict tests, watch/reader fixture journeys | Upcoming API-driven status transitions; larger coverage and screenshot review recorded below | API33 permission behavior needs another target; physical real-provider playback/fullscreen/background/focus and release benchmarks remain open. No app-data reset/uninstall was used |

Known issues: the original XAMPP database is corrupt and remains untouched; use the isolated healthy development server. System timezone rule versions differ between Windows and the old phone/PHP runtime, so travel/current-DST device acceptance remains open despite deterministic UTC/DST domain tests. External provider access and free-host outbound HTTP/SMTP/cron capabilities must be proved. No 99% recitation-accuracy claim is supported.

## Completion estimate

| Area | Estimate | Weight |
|---|---:|---:|
| Android |52%|25%|
| Backend/API |58%|18%|
| Independent Admin |0%|15%|
| Database |72%|10%|
| Notifications/reminders |5%|12%|
| UI/UX polish |50%|10%|
| Testing/QA |40%|7%|
| Documentation |65%|3%|

Weighted overall: **41%** (40.99% before rounding). These are engineering scope estimates, approximately ±10 percentage points, not automated coverage measurements. Foundation code is substantial, but independent Admin, engagement delivery, remaining native feature completeness and release/deployment acceptance account for much of the remaining scope. Documents describing future behavior do not earn implementation credit.

## Remaining implementation phases

These24 work packages refine the existing P4–P8 plan; they do not restart P0–P3. Split a package further when evidence requires it. Each result is conditional on its verification gate.

| No. / name | Goal and major tasks | Dependencies | Expected result | Required verification |
|---|---|---|---|---|
|1 Upcoming/live completion|Complete extended metadata, home discovery/live surface, offline/error/cancellation/replay presentation and native acceptance|Existing E01 API/UI|Usable upcoming/live discovery without inferred live state|UTC/state/CAS/privacy API regression; Arabic/English hardware UI and offline cases|
|2 Offline/sync hardening|Durable work, actor boundaries, cache limits, conflict UX, corrupted encrypted-record recovery|Existing Room/repositories|Predictable retained private progress|Restart/lost-response/conflict/account-switch/corrupt-record tests|
|3 Quran reader/tools|Complete range navigation, search/tafsir/bookmarks, font preferences, copy/share and daily verse journeys|Canonical index; phase2|Integrated source-attributed Quran tools|Boundary/search/source tests; RTL/LTR/font/device/offline acceptance|
|4 Recitation/radio playback|Background, focus/interruption, lockscreen, source/timing/network transitions and radio usability|Existing Media3/provider code|Reliable compliant audio|Physical real-provider foreground/background/focus/network tests|
|5 Adhkar/dua completion|All source/category paging, search, daily completion semantics, reset/restart feedback|Existing counter; phase2|One-handed sourced counters with truthful daily progress|Skipped/repeated/NULL-count/source/offline/conflict tests|
|6 Books|Rights-aware metadata, downloads, cancellation/progress, integrity/local management/PDF opening|Catalog; phase2|Lawful usable online/offline library|Download/URL/file/rights/storage/error/device tests|
|7 Account/Khatmah|Complete profile/security/preferences/export/delete; Khatmah plan workload, pause/resume/edit/restart/finish|Identity/progress; phase2|Complete private account and planning flows|Ownership/date/export/delete/restart/RTL/LTR E2E|
|8 Night Prayer completion|Timer and measured pace, exact Quran continuation, tonight milestones, saved city/method, yearly insights, exemptions and boundary UX|Canonical index; phases2,3,7|Full Qanet-inspired original private companion|DST/travel/polar/midnight/clock/edit/delete/offline/privacy/hardware tests|
|9 Native review engine|Evaluate licensed graph/runtime/phonemes; optional verified model download, microphone lifecycle, uncertainty feedback|Model/runtime/legal compatibility|Working measured local inference or truthful documented blocker|Integrity/ARM32/memory/latency/noisy audio/error false-positive corpus; no fabricated accuracy|
|10 Notification foundation|Central opt-in preferences/channels, contextual permission, stable deep links, quiet-hours/dedup core|Identity; navigation|Safe common notification infrastructure|API27 and API33+ denial/revocation/RTL/LTR/deep-link tests|
|11 Lesson reminders|Explicit configurable consent, default single15-minute choice, local schedule, cancellation/reschedule reconciliation|Phases1,10|No stale or unsolicited lesson reminders|Clock/timezone/reboot/TTL/duplicate/status tests and actual delivery|
|12 Following|Scholar/series consent, unfollow, privacy-safe new-content subscriptions|Phases1,10|Persistent controlled content follows|Ownership/visibility/retry/unfollow/audience tests|
|13 Personal routines|Quran wird/Adhkar/Salawat/Friday/custom reminders, complete-aware day rules|Phases3,5,10|Optional local private routine reminders|Completion vs mere opening/skipping; quiet hours/reboot/timezone/device tests|
|14 Khatmah/Night reminders|Integrate genuine remaining workload/last third and user preferences|Phases7,8,10|Helpful optional private planning notifications|DST/exempt/privacy/no guilt/late delivery/cancel tests|
|15 Free push delivery|Provider abstraction, FCM configuration, multi-device tokens, rotation/logout/revocation|Phase10; external project credentials|Actual free-provider device delivery|Authorized test device delivery/token lifecycle tests; no development broadcast|
|16 Inbox/policy|Private paginated inbox/read states/badge/deep links; TTL/dedup/frequency/privacy-safe diagnostics|Phases10,12,15|Reliable user-controlled message history|Isolation/read/dedup/expiry/pagination/offline tests|
|17 Independent Admin foundation|Separate session/login/sidebar, bilingual resources, RBAC/users/audit/owner boundaries|Shared identity/services|Secure independent administration application|Session fixation/CSRF/role/owner/self-lockout/login/layout tests|
|18 Admin content/services|Scholar/series/lesson/event/book/Adhkar CRUD, images/rights/order/moderation, secure settings/Night Prayer public defaults|Phase17; existing services|Complete maintainable content operations|Validation/upload/publication/moderation/privacy/native integration tests|
|19 Push campaigns|Composer/preview/test/explicit final send, scheduling/cancel, queue/retries and safe audiences|Phases12,15,16,17|Audited controlled push operations|RBAC/duplicate/cancel/consent/TTL/dry-run/test-recipient tests|
|20 Email campaigns|Reusable queue/worker, branding/localization, engagement opt-in/unsubscribe, retries/quotas and transactional priority|Phases17,18; configured SMTP|Controlled email campaigns and reliable transactional delivery|Fake transport suites plus authorized recipient inbox proof; no bulk development sends|
|21 UX/accessibility closure|All screen states, Arabic-first typography/RTL, English LTR, tablet/font, focus/TalkBack/reduced motion|Feature phases|Consistent accessible product|Screen matrix, hardware/tablet/large-font/TalkBack/contrast checks|
|22 Performance/reliability|Release startup/jank/battery/memory/network/SQL baselines and targeted optimization/profiles|Feature and UX phases|Measured budgets and smooth representative hardware behavior|Reproducible release traces/benchmarks; regressions and low-memory/background tests|
|23 Security/restore/regression|Permissions/IPC/dependencies/rights, encrypted full backup/restore, final account/Admin/native E2E|Implemented modules|Release security and recovery gate|Disposable restore rehearsal, ownership/rates/upload/secret scans and end-to-end suite|
|24 Deployment/release|Verify free host API/HTTPS/SMTP/cron/storage, configuration, signing/versioning/reproducible APK/AAB and operations|Phases21–23; hosting/signing credentials|Documented deployable release with honest capacity limits|Hosted physical journey, release build/signature/config checks and rollback evidence|

Sequential constraints: run migrations and backup/restore steps serially; central notification consent/policy precedes delivery; real FCM acceptance requires provider configuration; Admin authorization precedes campaign sending; security checks precede commits/pushes; release follows quality gates. After stable contracts, independent Quran, audio, book and Admin work can be developed independently, and server queue work can proceed alongside local reminder UI. This is a dependency assessment, not authorization to modify shared files concurrently or skip verification. Build/lint runs freeze Android source changes.

## Git/security/verification checkpoint

Base `c4446f4521a8f3b14be6707f481cc17efd456b89`; branch `codex/android-ecosystem`; remote `https://github.com/3vmorrr/athar--`. Fetch succeeded; HEAD and `origin/main` were equal (0/0); no remote feature branch or upstream existed.22 tracked modified files plus untracked Android/backend/docs/scripts/tests/shared/training source; no tracked deletion. Existing work is preserved. `git diff --check` passed. The tracked diff includes legacy work predating the native migration, which is included as preserved checkpoint scope rather than credited as a new independent Admin.

Read-only heuristic screening examined375 candidate files and453 reachable Git blobs, known local secret-value matches, private keys/provider token/service-account patterns and credential URLs. No unresolved findings; `user:pass` media URLs are deliberately invalid negative-test fixtures. Screening cannot guarantee absence of every possible credential format. `.env`, local configuration, private runtime data/backups, SDK paths, build outputs and signing files are excluded. The69MB existing tracked ONNX web model is pre-existing history, not a new trained-model export. `training/` contains only small source/lock/readme files; experimental training is not native inference. See `scripts/checkpoint-security.py`; private evidence is in ignored `storage/engineering/`.

Verification and remote commit evidence are appended after execution. A push is not successful until its remote SHA is checked. The checkpoint is followed by phase1 work, not project completion.

### Verification completed25–26 October

- Backend: all10 isolated suites executed, **19,974 assertions PASS**, including54 upcoming-event assertions and44 catalogue/progress assertions. Disposable database migrations ran in the runner; no email was sent.
- Development DB: read-only `scripts/check-development-schema.php` executed after safe stack startup: **14 matching migration versions,33 tables CHECK OK**. Migration014 private backup/coverage proof exists; a full archive restore was not tested.
- Android: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug passed. Unit tests were then explicitly rerun with `--rerun-tasks`: **22 tests, zero failures/errors**.69 PHP files passed syntax checks;285 paired resources passed localization-format validation. After adding the read-only schema verifier, its actual execution also passed.
- Physical phone: actual non-streaming `-r` update/launch passed; API smoke **1 PASS** through the real8002 application endpoint (5.743s). Upcoming fake-event journeys **Arabic1 PASS (18.415s), English1 PASS (18.454s)**. Scheduled→past-scheduled→explicit-live→published→rescheduled→cancelled→empty state transitions exercised via protected fixture Admin API. Arabic scheduled and English live captures were inspected; no independent Admin UI or real broadcast is implied. Startup observed1582ms is a debug observation, not a release benchmark.
- The resumed26 October session restarted only configured healthy local services; no repair/migration/reset/send was implicit. Physical/API startup revalidation and final scoped crash review are recorded in the physical report.
- Resumed physical API probe passed1 case (5.473s); separate relaunch and process-scoped log capture found zero matching critical startup failures. API health and legacy Admin returned HTTP200 again.
- Existing physical regression evidence remains:8 storage cases, watch/reader fixtures, Arabic/English identity and adhkar. Broad provider/background/fullscreen and release performance gates remain open. The pre-fix counter failure remains documented.
- API health and legacy Admin HTTP200 passed. Independent Admin checks are **NOT AVAILABLE**; SMTP, Dorar approval, FCM credentials and public hosting acceptance remain external gates.

Private log names: `checkpoint-backend.log`, `checkpoint-android.log`, `checkpoint-jvm-executed.log`, `checkpoint-physical-api.log`, `checkpoint-upcoming-ar.log`, `checkpoint-upcoming-en.log`. Git commit/push evidence is recorded in the subsequent checkpoint receipt, avoiding a self-referential commit hash in this document.
