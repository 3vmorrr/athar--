# Native notification inbox

Task: 16.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Android now exposes a native Notification Inbox list and detail flow backed by the owner-scoped 16.1 API.

The response contract is strict: known notification categories only, bounded source/title/body fields, descending unique IDs, bounded cursor metadata, UTC timestamps and allowlisted \`NotificationDeepLinks\` destinations. Arbitrary URLs or unknown destination tokens fail closed.

The list exposes unread count, loading, empty, first-page error, pagination error and explicit Load More. Opening an unread row marks it read through the idempotent server mutation; a failed mutation is visible and retryable. Deletion waits for server confirmation before removing the item locally.

The detail surface may open only allowlisted related content. \`NotificationDestination.Inbox\` intentionally has no recursive content action.

Navigation now registers a real \`inbox\` route. Owner-bound notification launches targeting Inbox open it, and Account exposes a manual Inbox tool. Home/Quran/Adhkar/Night/Lesson/Series destinations use the existing allowlisted navigation path.

This task is online-first by design. Task 16.3 owns encrypted local history, offline read/delete merge, restart persistence and offline badge synchronization.

Acceptance remains pending for Android assemble/unit/instrumentation/lint and physical Arabic RTL/English LTR loading/error/retry/read/delete/deep-link, large-font, landscape, dark-mode and accessibility evidence.
