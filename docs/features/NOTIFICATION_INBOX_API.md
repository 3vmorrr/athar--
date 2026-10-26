# Private notification inbox API

Task: 16.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The notification inbox is a private server-side history independent of whether a provider push is available or successfully delivered.

Schema 026 stores owner, category, bounded title/body, allowlisted app destination, source reference, owner-scoped dedupe key, read/delete timestamps and expiry.

API:
- \`GET /api/v1/me/notification-inbox?per_page=20&cursor=...\`
- \`GET /api/v1/me/notification-inbox/<id>\`
- \`PUT /api/v1/me/notification-inbox/<id>/read\`
- \`DELETE /api/v1/me/notification-inbox/<id>\`

Pagination is descending-id cursor based. Cursors include owner/id plus an HMAC using the API token key, so they cannot be modified or reused by another account.

Read is idempotent. Delete is owner-scoped soft deletion and is idempotent for the owner. Deleted and expired rows are absent from list/detail and unread counts.

The internal \`record\` method is idempotent per owner/dedupe key and never rewrites retained content on replay. Destination values are restricted to the same app allowlist used by notifications; arbitrary URLs are rejected.

This schema stores notification copy/history, not provider tokens, Quran positions, adhkar counters, Khatmah workload, Night Prayer exemptions or campaign audience definitions.

Task 16.2 owns native presentation; 16.3 adds offline/read-state merge and badge persistence.

Acceptance remains pending for isolated migration/API execution in the integration lane and final end-to-end inbox generation by delivery producers.
