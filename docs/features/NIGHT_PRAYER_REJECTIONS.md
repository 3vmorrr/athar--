# Night Prayer permanent rejection recovery

Task: 02.7.2  
Implementation branch: `parallel/admin-foundation`  
Status: **IMPLEMENTED / RUNTIME ACCEPTANCE PENDING**

## Problem

Night Prayer sync previously treated every per-operation rejection other than
409 as an exception. Durable 422/404 failures therefore remained eligible
without an actionable user state and were indistinguishable from transient
429/503 failures.

## Durable state without a schema reset

The original encrypted `outbox.payloadCipher` is never rewritten when a
permanent operation rejection arrives. A small encrypted marker is stored in
the existing private `cached_documents` table under
`night-rejection:<mutation-id>`.

This deliberately avoids a Room schema migration. Content-cache trimming only
targets localized HTTP cache keys, so these private rejection markers are not
treated as disposable downloaded content.

A session with either a 409 conflict or a permanent-rejection marker is excluded
from automatic eligible work. Other sessions remain eligible, so one rejected
session cannot starve healthy queued work.

Classification:

- **409**: existing revision-conflict review.
- **408 / 425 / 429 / 5xx**: temporary. Keep bytes, store no permanent marker,
  and surface a retryable failure after successful results in the same response
  have been committed locally.
- **Other operation-level 4xx, including 404 and 422**: durable permanent
  rejection requiring explicit recovery.

## Explicit owner-bound recovery

The rejection review validates retained local/outbox bytes and fetches the
current account state. The user may then choose:

1. **Retry unchanged** — remove only the durable marker and preserve the exact
   original outbox bytes.
2. **Repair** — edit the retained device session, validate it again, verify the
   reviewed account state is still current, then create one new mutation rebased
   to the current account revision. If the account version is already deleted,
   the repaired device version becomes a new session instead of resurrecting the
   deleted identifier.
3. **Use current account state** — explicitly remove this session's pending
   mutations and adopt the reviewed account state. A reviewed missing remote
   state can also be accepted explicitly.

A stale review fails closed with `NightRejectionChanged`. Unreadable retained
payload or marker data fails closed with `NightRetainedUnreadable`. Switching
accounts cannot act on another owner's review.

## UI

Arabic RTL and English LTR copy distinguishes a permanent rejected change from
ordinary pending sync and a 409 conflict. Rejected sessions disable ordinary
edit/delete until reviewed. Rejected pending deletions remain visible as
recovery cards. The review offers reload, retry unchanged, repair, and use
account state; large text/short screens use a full-screen review layout.

## Focused tests added

- 422 rejection survives repository recreation and does not starve a healthy
  session.
- 429/503 remain retryable and are never stored as permanent rejection state.
- Retry unchanged retains the exact mutation bytes.
- Repair requires a fresh review and creates a new rebased mutation.
- 404 missing remote deletion requires an explicit account-state choice.
- Corrupt rejection metadata keeps the original outbox bytes and fails closed.
- Account switching cannot retry, repair, or accept another owner's rejection.
- Dialog state verifies disabled-before-load behavior, error reload, Arabic/English
  resources, account-missing presentation, and large-text action reachability.

## Acceptance still required

Do not mark 02.7.2 COMPLETE from this commit alone. The normal workstation still
needs to run the repository's Gradle unit/build/androidTest/lint/resource gates,
focused storage/UI instrumentation, Arabic and English API27 journeys including
restart/offline/retry/retained-data checks, applicable large-text/landscape
checks, diff/secret review, and exact remote-SHA verification.
