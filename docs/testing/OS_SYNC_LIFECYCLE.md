# Task02.8 — actual OS synchronization lifecycle acceptance

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-30:** [149-task actual source audit](../progress/ACTUAL_IMPLEMENTATION_AUDIT.md). 114 tasks have implemented source (79 in main, 35 only unmerged); 19 are accepted/closed, 60 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

Status: PARTIAL. Previous missing-network condition changed26 October2026: read-only ConnectivityManager/dumpsys inspection sees active CONNECTED Wi-Fi with INTERNET and VALIDATED. No SSID/IP/device identifiers are copied into this report. No global network/font/animation setting has been changed. Task02.7 disk close/reopen is not OS process death or reboot evidence.

## Ready connected-scheduler gate

Run PrivateSyncWorkerTest with nativeApiSmoke=true, nativeFixtureHost=127.0.0.1 and requireValidatedScheduler=true. The explicit flag requires real active validated connectivity and forbids using the offline direct-doWork fallback as acceptance. The test closes only fixture graph foreground repository helpers, queues the dedicated generated sync actor's job through installed WorkManager, waits for terminal SUCCEEDED, then verifies actual server state and retained exemption ciphertext. Rejection paths still exercise wrong actor/environment and unreadable bytes without erasing them. Scope stays in the generated fixture schema/partition.

## Required remaining stages

1. Establish pending records in all four modules while preserving owner/ciphertext/UUID and exact expected server revisions/activity baseline. Record only fixture-owned WorkRequest IDs and unique names; never tokens or private payloads.
2. Prove the network constraint keeps the installed request ENQUEUED with unchanged pending bytes while the test device has no qualifying connected network, then restoration causes installed-scheduler delivery without direct Worker calls. USB reverse transport availability alone is not a network-constraint transition.
3. Kill the application process without force-stop/data-clear, retain its database and WorkManager state, and prove delivery after actual OS process recreation with no foreground/manual-sync substitute.
4. Reboot the designated phone, restore only its USB reverse mapping if needed, and prove the persisted request is delivered after boot/network restoration. No uninstall, application/key/database reset, destructive migration or arbitrary pending-data replacement is allowed.
5. Inspect process-scoped startup/worker failures and repeat normal Arabic/English private module journeys. Do not infer API33 notification/release acceptance from this API27 device.

## Origin and actor boundary that the lifecycle harness must preserve

The instrumentation application selects fixture origin8004 only while the test runner supplies its arguments. Ordinary application/Worker startup after a true process exit uses the configured development origin8002 and its different partition. A fixture-origin request must be rejected/cancelled when that ordinary graph starts; this is the correct privacy boundary, not evidence of successful fixture replay. A reboot/process harness must therefore use an explicitly isolated account at the actual build origin, or a separate test-only application identity whose origin is stable across normal background starts. An existing supported compile-time debug API-base option can also produce a temporary fixture-origin debug APK, then restore the ordinary development APK after acceptance; this needs no runtime endpoint override and retains existing origin partitions. Do not weaken PrivateSyncWorker's environment/owner checks, persist a production endpoint-override hook or claim a recreated instrumentation graph proves boot delivery. Complete this harness before disruptive physical stages; keep all unexecuted gates open.


## First executed subgate

Strict connected-scheduler candidate build/test APK/lint PASS (`os-connected-scheduler-build.log`,1m40s),56 unchanged JVM cases/316 resources. API27 Arabic1/1 PASS (5.171s) with requireValidatedScheduler=true: the active validated network and installed WorkManager SUCCEEDED path were mandatory, and the offline direct Worker fallback could not satisfy the test. English strict rerun1/1 PASS (5.172s). Scoped no-secret/diff review PASS. No process-death/reboot/network-transition result is claimed;02.8 remains PARTIAL.


## Split-stage harness under implementation

OsSyncLifecycleTest and scripts/test-android-os-sync.ps1 use the existing compile-time Debug base URL at8004 for both instrumentation and ordinary background startup. The test-only osLifecycle flag and selected single method are mandatory; ordinary test runs skip these stages. Prepare signs in a fresh generated actor, closes foreground repository helpers, writes pending reading/counter/lesson/Night records and local exemptions, then enqueues the real PrivateSyncWorker with production ownership/constraints/backoff and a bounded initial delay. Only job/actor/fixture identifiers and ciphertext hashes enter the ignored receipt.

The host's check_os_lifecycle_fixture.php reads only the recorded generated actor and checks all four actual server states plus zero inferred activity; it never triggers delivery. Delivery must be observed on the server before verifyDelivered instrumentation is launched. Verification checks the exact persisted WorkRequest SUCCEEDED, a different process PID, cleared private pending flags, one Night revision and unchanged exemption ciphertext. Neither retainedWithoutNetwork nor verifyDelivered calls sync/doWork. Retained-network verification additionally requires the initial delay to have elapsed, no qualifying Internet network, exact unchanged ciphertext/outbox and attempt0/ENQUEUED.

The physical sequence will first check ordinary process exit/background execution, then a temporary Wi-Fi disconnect/restoration (mobile data is already off), then a reboot with restored USB mapping. Original Wi-Fi-on/mobile-data-off state and the normal8002 Debug build must be restored afterward. Test identities remain confined to the generated fixture schema. All stages are still unexecuted until their recorded evidence passes; no global setting has yet changed.


## Physical execution — in progress

First harness candidate failed Kotlin compilation because WorkManager's nullable WorkInfo future result was not checked. Explicit requireNotNull fixed it without suppression. Fixture-origin build2 passes56 JVM cases, production/test APK and lint (2m49s),316 resources and read-only healthy development schema15/34. Candidate/history no-secret review passes.

Process stage prepare1/1 PASS (7.663s), with all four private modules pending and a90-second delayed installed WorkRequest. The host's read-only query confirmed no server rows/activity before dispatch. Selected app process was then killed with am kill; process count0 and package stopped=false were independently observed, without force-stop/data-clear. The host observed all four server states delivered before verification started, with zero inferred activity. Process verification1/1 PASS (6.503s): exact persisted WorkRequest SUCCEEDED, different process PID, no remaining pending mutations, one Night revision and unchanged exemption ciphertext. First reconnect preparation was interrupted during an early network transition; USB reverse mappings were empty afterward and the instrumentation log incomplete. Wi-Fi-on/mobile-data-off was restored and that attempt is retained as incomplete, not accepted. Reconnect preparation is being repeated fully before changing connectivity.


Reconnect retry prepare1/1 PASS (7.028s), with a fresh generated actor and a90-second delayed request, and an empty host-observed server state. Only after the complete preparation, Wi-Fi was disabled (mobile data remained in its original off state). After the initial delay elapsed, host rows were still absent and retainedWithoutNetwork1/1 PASS: no qualifying network, exact WorkRequest ENQUEUED/attempt0 and unchanged ciphertext/outbox/exemptions. A finally block restored Wi-Fi-on and the USB mapping. Automatic post-reconnect delivery/verification is now underway. Candidate fixture APKs are retained privately for reproduction while the normal8002 build is being restored. Read-only keyguard inspection reports secure=false; no PIN/password is handled by the harness.


Reconnect background delivery was independently observed by the host before verification; exact-work verification1/1 PASS (6.383s), all four server states correct and zero inferred activity. Reboot prepare1/1 PASS (7.48s), with a new generated actor and a120-second delayed request; host state empty before reboot. A selected-device reboot has now been requested after recording boot identity. The normal8002 build has already passed full56 JVM/production/test APK/lint gates (1m59s), but is intentionally not installed until the fixture-origin reboot test completes.


## Deferred checkpoint at integration handoff

The user changed execution order: preserve this02.8 work and return only after available parallel implementation batches are integrated and stabilized. Actual process/reconnect/reboot split-stage assertions passed on the fixture-origin APK, and the normal8002 APK was restored with full build/lint and Arabic9/9 normal/sync regressions. English final regression and reconciliation against incoming changes remain open. This checkpoint does not close02.8 or change its canonical PARTIAL status. No source from the parallel lane is included in this checkpoint.
