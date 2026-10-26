# Follow persistence and API

Task: 12.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Separate from favorites

Following is a distinct relationship from saving/favoriting content. The existing \`user_favorites\` contract is unchanged.

Two ownership-safe tables back follows:

- \`scholar_follows(user_id, scholar_id)\`
- \`series_follows(user_id, series_id)\`

Their composite primary keys make repeated follow operations idempotent, target indexes support future new-content audience resolution, and foreign-key cascades remove follows for deleted users/content.

These table names intentionally match the existing campaign audience resolver, which already expects \`scholar_follows\` and \`series_follows\`.

## API

Authenticated endpoints:

- \`GET /api/v1/me/follows\` with optional \`target_type=scholar|series\`
- \`GET /api/v1/me/follows/<type>/<id>\`
- \`PUT /api/v1/me/follows/<type>/<id>\`
- \`DELETE /api/v1/me/follows/<type>/<id>\`

PUT and DELETE are idempotent. Follow creation requires the target to be currently public under the same catalog visibility rules users browse with.

Unfollow deliberately does not require current visibility, so a client retaining an older target id can remove its own stale relationship after content is hidden.

## Visibility and privacy

Follow lists only return currently visible content. A hidden scholar, hidden series, or series whose parent scholar is hidden cannot be followed and does not leak through \`/me/follows\`.

A hidden-but-retained row may remain until the user removes it or the target is deleted; it is never exposed as visible content. Deleting a target cascades its follow rows.

Follow state is owner scoped and contains no worship activity, progress, contact graph, notification consent, or campaign preference.

Acceptance remains pending for isolated migration/API tests in the integration lane plus native 12.2/12.3 journeys, offline rollback behavior, accessibility/localization, and final audience-resolution tests when push delivery becomes available.
