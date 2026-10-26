# Offline notification inbox and badge

Task: 16.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The private notification inbox now persists an encrypted owner-scoped local state under \`private:notification-inbox:v1\`.

The cache retains at most 200 validated notification items plus cursor metadata, last known total unread count, and bounded pending read/delete IDs. It never stores provider credentials or WorkManager payload content.

## Offline mutations

Opening an unread cached item marks it read locally first, decrements the local badge exactly once, and records an idempotent pending read. Deleting a cached item removes it locally immediately and records a pending delete; delete supersedes a pending read for the same row.

Pending mutations survive process restart. WorkManager inputs contain only owner id and the 12-hex API environment partition. A signed-in owner with retained pending work is rescheduled after process recreation/account restoration.

On the network worker, deletes are sent before reads. Server 404 is terminal success for a retained mutation because the owner row is already deleted/expired; other authorization/client failures fail closed, while 408/425/429/5xx and IO receive bounded retry.

## Refresh and merge

Before any server refresh the repository flushes pending mutations. The server's unread count is then authoritative.

A first-page refresh replaces the ID range covered by that page while retaining older cached pages. This removes deleted/expired first-page rows without discarding older retained history. Every merge deduplicates by inbox ID, keeps descending order and caps retained rows.

Expired cached rows are hidden locally even while offline and no longer contribute to the visible offline badge.

## Account and badge isolation

The existing Room owner column plus verified-session checks isolate encrypted inbox state by account. The Account inbox chip observes the same local state and displays its unread badge, so restart/offline presentation does not depend on an in-memory response.

Changing accounts creates a different ViewModel key and owner flow; former-account pending work is cancelled and never retargeted.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and physical restart/offline read/delete/network restoration/account-switch/badge, Arabic RTL/English LTR, large-font, landscape, dark-mode and accessibility evidence.
