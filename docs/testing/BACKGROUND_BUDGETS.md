# Background battery and network budgets

Task: 22.4  
Implementation branch: `parallel/admin-foundation`  
Status: **BUDGET POLICY + EVIDENCE HARNESS IMPLEMENTED / PHYSICAL IDLE EVIDENCE PENDING**

No battery percentage, joule estimate, wakeup count or network-byte claim is fabricated here. The accepted physical evidence still requires the authorized representative device.

## Budget classes

`BackgroundWorkBudgets` is the single reviewable policy for scheduled/background behavior.

| Work | Class | Periodic budget | Network | Battery not low | Storage not low | Retry cap |
| --- | --- | --- | --- | --- | --- | --- |
| Private encrypted sync fallback | network sync | 6h, 2h flex (max 4 windows/day) | yes | yes | yes | 8 |
| Push-device token sync | network sync | event-driven, unique/coalesced | yes | yes | yes | 5 transient attempts |
| Notification-inbox mutation sync | network sync | event-driven, unique/coalesced | yes | yes | yes | 5 transient attempts |
| Built-in/custom routine maintenance | local maintenance | 24h, 6h flex (max 1 window/day per maintenance family) | no | yes | yes | 3 |
| Local reminder delivery | user-visible local | exact one-shot work only | **no** | no | no | delivery policy decides |
| Boot/time/timezone reminder reconciliation | user-visible local | event-driven unique work | **no** | no | no | fail closed |
| Media playback | user-driven foreground media service | none | stream-owned | n/a | n/a | player/provider behavior |

The local reminder exceptions are deliberate. Network or battery-not-low constraints on the actual due reminder could silently suppress a user-visible local notification at the exact time it is needed. Maintenance and remote mutation work are the places where deferral saves battery safely.

Private sync keeps its existing 6-hour fallback but now has a 2-hour flex window. Built-in/custom 24-hour maintenance gets a 6-hour flex window and battery/storage constraints. Push-device and inbox remote-mutation workers now wait for connected network, battery-not-low and storage-not-low.

All these workers remain owner/environment scoped and unique/coalesced. Logout/account changes retain their existing cancellation behavior.

## Media behavior

The Media3 `PlaybackService` remains user-driven. It is not periodic work and is not started by the background budget machinery. It accepts only approved HTTPS audio providers, uses media playback audio attributes, rejects untrusted controllers, and stops/clears playback when the account becomes signed-out or pending.

## Non-destructive physical evidence

`scripts/android_background_budget.py` collects read-only snapshots:

- device/API/fingerprint and natural deep/light idle state;
- Athar JobScheduler/WorkManager-visible job names;
- package UID network counters from `dumpsys netstats detail` when the OEM exposes them;
- Athar-relevant batterystats checkin lines plus a source hash;
- whether the Athar playback service is present;
- explicit guarantees that the collector did not toggle Doze, change standby buckets/global settings, clear data or reset stats.

Self-test:

```bash
python scripts/android_background_budget.py self-test
```

Example retained-state evidence:

```bash
python scripts/android_background_budget.py capture \
  --serial <authorized-serial> \
  --scenario before-natural-idle \
  --output docs/testing/results/background-before.json

# Leave the phone in the approved real-world idle/background journey.
# Do not force Doze or change global settings.

python scripts/android_background_budget.py capture \
  --serial <authorized-serial> \
  --scenario after-natural-idle \
  --output docs/testing/results/background-after.json

python scripts/android_background_budget.py compare \
  --before docs/testing/results/background-before.json \
  --after docs/testing/results/background-after.json \
  --output docs/testing/results/background-delta.json
```

The comparison reports package network byte deltas when available, job-name/count changes, natural idle state and playback-service state. Batterystats lines are retained for human/device-specific review; the script intentionally does not invent cross-OEM energy numbers.

## Required acceptance journey

On the authorized API27 phone, record at minimum:

1. signed-in retained state with no active media and no pending mutation;
2. one pending private sync and its coalescing/cancellation behavior;
3. one pending inbox mutation and one push token mutation with network absent then restored;
4. built-in/custom routine maintenance across a natural idle window;
5. local due reminder while offline to prove delivery is not network-gated;
6. active audio playback, pause/stop, and account sign-out service teardown;
7. account switch/logout showing old-owner jobs cancelled and no old-owner network activity.

Arabic RTL Light is the primary device journey; the queue's English/offline/error/retained-state requirements still apply.

## Acceptance status

The code budgets, flex windows, constraints, pure policy tests and read-only evidence harness are implemented. Physical battery/network/idle evidence remains pending because this execution environment has no authorized Android device.
