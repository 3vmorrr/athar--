# Reminder lifecycle reconciliation

Task: 10.6  
Implementation branch: `parallel/admin-foundation`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## System lifecycle inputs

Athar requests `RECEIVE_BOOT_COMPLETED` only to restore user-created local reminder work. It does not request exact-alarm, location or background-service privileges.

A non-exported manifest receiver listens only for boot completed, manual wall-clock change, timezone change, and app package replacement.

The receiver never reads account or worship data and carries no owner, schedule, title, body or private state in the broadcast path. It only coalesces a unique environment-scoped reconciliation work item.

## Reconciliation worker

The worker validates the API environment partition, restores the existing authenticated session when necessary, and exits successfully when no verified account is signed in.

For the current verified owner it rebuilds unique WorkManager jobs from Athar's encrypted local reminder source of truth. Past-due jobs therefore run promptly and are rejected by normal TTL/dedupe policy if stale. Quiet hours and notification permission are still evaluated by the actual reminder worker immediately before delivery.

Unreadable encrypted reminder state is not reset. Reconciliation fails closed and leaves retained ciphertext untouched.

## Timezone behavior

A timezone broadcast does not silently rewrite a saved city, prayer method or explicit reminder timezone. It only rebuilds pending work so policy reevaluates the retained specification with its declared timezone.

Feature packages that intentionally define a reminder as following the device timezone must update their own encrypted reminder specification before reconciliation. This foundation does not infer or rewrite worship schedules.

## Boot behavior

WorkManager already persists its database across reboot; explicit reconciliation is an ownership and policy safety net. It recreates missing unique work from Athar's encrypted source of truth instead of trusting stale WorkManager state.

## Acceptance still required

The integration lane needs device checks for reboot, app update, manual time change, timezone change, signed-out boot, account switch during reconciliation, denied notification permission, stale TTL cleanup, corrupted encrypted state and OEM delayed-work behavior before task 10.6 can be marked COMPLETE.
