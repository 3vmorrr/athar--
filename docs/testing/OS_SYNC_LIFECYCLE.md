# Task02.8 — actual OS synchronization lifecycle acceptance

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
