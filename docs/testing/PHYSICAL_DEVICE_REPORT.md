# Physical Android integration —2026-10-25

## Checkpoint revalidation25–26 October

After safe service restart on26 October, the actual phone→8002 probe passed again (1 case,5.473s), then the main activity launched successfully. A process-scoped600-line logcat capture found zero matching fatal/ANR/SQLite/linkage/cleartext failures. Instrumentation had exited the process before this separate launch, so the capture was taken only after relaunch; logs were never globally cleared. This is a scoped smoke result, not full stability certification.

Updated Debug and test APKs with non-streaming `-r`, retained data, verified USB reverse and launched successfully. Actual physical API probe passed1 case (5.743s). New upcoming UI/API journeys passed Arabic1 case (18.415s) and English1 case (18.454s); scheduled/past-scheduled/manual-live/published/re-scheduled/cancelled/empty states were verified. Arabic scheduled and English live screenshots were inspected; long content remains scrollable and play is server-gated. Test scholar/series names are deliberately synthetic English fixture content, not untranslated ordinary UI. No real broadcast or independent Admin UI is claimed. Evidence: `checkpoint-physical-api.log`, `checkpoint-upcoming-ar.log`, `checkpoint-upcoming-en.log` in ignored engineering storage. Earlier rows below describe their original dated runs; this paragraph supersedes “upcoming not yet tested” for this narrow scope. Reminder/push/campaign delivery remains unimplemented.


Target: Samsung SM-M105G, Android8.1/API27, ARM32,720x1520. Serial is intentionally omitted. Existing app data was retained; no uninstall/clear-data or global permission grants occurred.

| Gate | Actual result |
|---|---|
| Android build | PASS — actual Gradle USB Debug build, then test APK/JVM/lint build |
| Physical device detected | YES — authorized ADB device |
| APK installed | YES — non-streaming `-r` update; initial streamed attempt failed without a diagnostic reason |
| App launched | YES — actual main activity resumed; observed launch1370ms, not a release startup benchmark |
| Local Backend | RUNNING — established8002 API |
| Database | RUNNING — healthy isolated3309; corrupt original remains preserved |
| Local Admin | Legacy login reachable; independent dashboard NOT IMPLEMENTED, no independent-Admin acceptance claimed |
| Phone → API | PASS — Retrofit request inside the actual application through verified USB reverse |
| Admin → API → phone | PENDING — protected API work exists; complete independent UI pipeline is not implemented |
| Arabic RTL | PASS for startup, identity inputs/feedback, watch/series/comments and reader journeys |
| English LTR | PASS for identity input/feedback; wider feature journey pending |
| Light theme | PASS for selected mode on the retained installation; this is not a fresh-data reset/default proof |
| Watch/comments/series/scholar | PASS — existing fixture journey; no real comment/email dispatch |
| Quran resume | PASS — actual fixture API save, exact resume and three recreations |
| Adhkar journey | Initial run FAILED; after cold-device/revision recovery fix, Arabic and English hardware reruns PASS |
| Critical startup crashes | No matching fatal/ANR/SQLite/linkage/cleartext failure in the scoped startup capture; not full stability certification |
| Upcoming/live/reminder/push/campaign | NOT YET TESTED — future implementations remain open |

Evidence in ignored local engineering logs: `physical-install-traditional.log`, `physical-test-apk-install.log`, `physical-backend-smoke.log` (1 passing case), `physical-auth-ar.log` (2 passing cases), `physical-auth-en.log` (2 passing cases), `physical-core-ar.log` (watch/reader pass, adhkar fails). Actual app output confirms loopback8002, Arabic/RTL/light and retained onboarding state. The first-launch image was inspected.

The failed adhkar case is not reported as a pass or hidden by resetting the database. A dedicated counter regression now covers importing an existing server revision on a cold device, offline intent preservation, response-loss recovery, true conflict resolution and owner isolation. A targeted owned progress lookup avoids mistaking an older item outside the overview's500-row window for a missing counter. The updated Debug app was rebuilt and deployed through the actual safe deployment script.8 native storage cases pass on the physical device (`physical-storage-counter.log`,12.529s). Arabic adhkar rerun passes (`physical-adhkar-ar-retry.log`,13.06s) and English passes (`physical-adhkar-en.log`,12.947s). The initial failed result is retained.

The API27 device cannot certify API33 runtime notification permission flows. Future permission/channel/denial tests need an appropriate additional target, while actual notification delivery must still be exercised on physical hardware. Full notification/reminder acceptance is separate from installation success.
