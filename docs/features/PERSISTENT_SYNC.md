# Owned persistent synchronization

Status: functional implementation; hardware acceptance below remains partial. Uses the already included AndroidX WorkManager2.10.3; `work-testing`2.10.3 is test-only. No paid service, notification campaign, exact alarm, custom receiver or new dangerous permission is introduced.

## Policy

Private progress remains in its existing encrypted Room records/outbox. Local edits notify an application-scoped coordinator; notifications coalesce for1.5 seconds and inspect only the changed modules before enqueueing. Foreground uploads remain available. An already active unique job is kept rather than cancelled/replaced, avoiding needless request/acknowledgement races. A separate six-hour maintenance job covers cross-device pulls and a process-death gap between committing a local edit and notifying WorkManager. It first waits six hours; sign-in separately requests a short job. WorkManager/OS constraints can delay execution; this is eventual synchronization, not exact-time delivery.

Jobs are scoped by API-environment partition and actor ID. Work input contains those identifiers only, never credentials, notes, Quran references, exemption dates or queued payloads. Network connected, battery-not-low and storage-not-low constraints apply. Retry uses exponential30-second backoff, at most eight attempts for one run, and a90-second coroutine budget. Cancellation propagates; no always-running service keeps the app alive. Lifecycle/storage/constraint behavior follows the official [persistent-work overview](https://developer.android.com/develop/background-work/background-tasks/persistent), [unique-work guide](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work), and [request/retry guide](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Ownership and privacy

The Worker restores authentication only when the session is still restoring, requires a verified matching current actor, checks ownership between modules, and passes the expected actor into each repository's sync boundary. Logout/account change cancels former owned work. A stale account/environment job cannot repurpose its private queue for the next signed-in account. Old-environment jobs are cancelled without sending credentials to a new host.

Reading, counters, lessons and regular Night Prayer records synchronize through existing authenticated APIs. Exemption periods remain local and are never added to job input or outgoing operations. Retained unreadable data fails closed; another healthy module can still progress. Network/server throttling retries, revocation stops, and invalid local/server-rejected work requires attention without deleting pending data. A successful background run means unblocked work was processed; it does not resolve conflicts requiring a user's choice or certify that every feature is complete. Night Prayer pull continuation and stable mutation IDs preserve bounded-batch progress.

## Verification gates

Use actual Worker construction with the official [TestListenableWorkerBuilder approach](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl). Normal fixture app graphs disable automatic persistent scheduling so unrelated UI tests cannot leave jobs behind. Explicit worker/scheduler cases use only the generated fixture account/partition; schedule tests use reserved synthetic actor IDs and cancel only their own unique names. Ordinary data-layer defaults remain no-op callbacks outside production graph wiring.

Required: policy/actor/cancellation/timeout/retry tests; real Worker/Room/Keystore/HTTP acknowledgement and exemption exclusion; persistent unique scheduling/cancellation; API27 hardware and ordinary journey regression; merged-manifest permission/component review. Manual worker invocation does not prove OS scheduling, reboot or a real process-death/network-restoration journey. Keep those acceptance distinctions explicit. No real account, app data or device key is reset for these tests.

###26 October execution

38 JVM cases,292 paired resources and production/test APK/lint pass. The initial unit candidate failed an assertion about cancellation-object identity: coroutine debug stack recovery preserves type/message, not object identity. The corrected assertion passes without changing production cancellation behavior.

Samsung API27 worker/Room/Keystore/HTTP case passes1/1 (5.765s): rejects wrong environment/actor, acknowledges the dedicated generated sync actor's pending counter, preserves local exemption ciphertext and retains a damaged record as action-required. The phone has no active Internet network; USB reverse alone does not satisfy WorkManager CONNECTED. An initial unconditional scheduler-await case timed out after60 seconds; the network-aware rerun verifies the work remains ENQUEUED with unchanged pending data, then separately exercises direct Worker HTTP over USB. This is not an OS network-restoration or reboot pass. Network settings were not modified.

Merged manifest reviewed: existing WorkManager boot receiver is non-exported, job service is protected by BIND_JOB_SERVICE, initialization provider and foreground service are non-exported. No custom IPC or dangerous permission was added. Full process-death/reboot/network-restoration and release acceptance remain open.


###26 October later connected-scheduler subgate (02.8 PARTIAL)

The earlier missing-network condition changed: read-only inspection now sees an active validated Internet network. Strengthened PrivateSyncWorkerTest with requireValidatedScheduler=true so actual installed WorkManager success is mandatory and the offline direct-doWork fallback cannot count. Strict Arabic1/1 (5.171s) and English1/1 (5.172s) pass on API27;56 unchanged JVM/build/test APK/lint/316 resources pass. This advances only connected scheduling. Real network transition, OS process death and reboot remain unexecuted; the [lifecycle acceptance plan](../testing/OS_SYNC_LIFECYCLE.md) records the stable-origin requirement and remaining harness work. No global setting or app/key/database reset occurred.
