# Physical Android integration —2026-10-25

##26 October — cross-module lost-response/storage reopen02.7

API27 Arabic19/19 (76.557s), English19/19 (79.079s) PASS. Test-only transport loses committed responses from real fixture API; named Room disk files close/reopen, then each operation retries three times. Actual export totals increase exactly one lesson/one adhkar completion, with no reading activity; Night two-edit replay stays revision2 and deletion replay stays revision3. Existing storage/ownership and all four native module journeys pass.56 unchanged JVM/build/test APK/lint/316 resources and secret/diff review PASS. No production behavior changed. Device read-only connectivity is now validated active Wi-Fi;02.8 OS reconnect/process/reboot acceptance remains separate and in progress.

##26 October — permanent rejection recovery02.7.2

Final API27 SM-M105G Arabic10/10 (40.074s), English29/29 (78.1s), main API1/1 (5.563s) PASS. New real Room/Keystore/storage tests retain422/404 rejection bytes, mixed429/503/401/408 results, reopen/stale/owner/corruption/malformed/healthy-queue outcomes; additive v1→v2 migration compares all private table rows and flags. Real fixture HTTP journey exercises422/cancel/recreation/unchanged retry/validated correction/landscape while another record synchronizes. Dark200-percent deletion/recovery actions remain reachable. Owned Arabic/English settled captures inspected. Full56 JVM/build/test APK/lint/316 resources pass. Initial migration-helper runtime incompatibility and Gradle-daemon loss retained in TEST_REPORT; neither is hidden by suppression. No data reset/uninstall/global settings change or real message send.

##26 October — context/related-reading final gate

After the context and compact preview update, final physical Arabic3/3 (22.414s) and English3/3 (21.911s) passed. Actual fixture Admin API→DB→native detail carried bilingual course/topics/explicit pages, and book detail navigation returned correctly to the event. Final unavailable-thumbnail captures were inspected in both languages. The initial lazy-header lookup and device-sleep failures remain in TEST_REPORT; test windows alone now keep themselves awake.27 JVM cases,291 resources and current APK/test packaging/lint pass. This does not certify independent Admin or real provider broadcast playback.

##26 October — upcoming home continuation

Current native build/test APK/lint passed;26 JVM cases and287 resources passed. Physical Arabic3/3 (21.487s) and English3/3 (20.883s) reruns passed for home discovery/navigation, event transitions, cached-live/unavailable/replay presentation, and real encrypted Room cache isolation/removal. Arabic/English home captures were inspected. An initial English isolated-Compose harness case failed because only its configuration/direction was localized; supplying the matching localized Context fixed the test surface, and both full reruns passed. The initial failure is preserved in the test report. This is fixture/API and presentation acceptance, not physical YouTube live playback, reminder delivery or independent Admin UI acceptance.

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
