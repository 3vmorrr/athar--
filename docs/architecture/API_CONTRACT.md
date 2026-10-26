# API v1 contract and implementation status
Base `/api/v1`. JSON UTF-8, TLS in production. Success `{ "data": ..., "meta": {"request_id": "..."} }`; errors `{ "error": {"code": "...", "message": "...", "fields": {}}, "meta": ... }`. Lists have bounded `page`/`per_page` (default 20, maximum 50), total/next metadata. Validate JSON/object shape and content type; reject oversized/unknown-sensitive fields. Error status: 400 malformed, 401 expired/missing auth, 403 insufficient verification/role, 404 unavailable resource, 409 conflict, 422 invalid fields, 429 rate limit, 502 provider failure, 503 unavailable service.

| Method / route | Actor/scope | Behavior |
|---|---|---|
| GET /health | unauthenticated, minimal info | version/availability; no secrets/database details |
| POST /auth/register | rate limited | display_name/username/email/password/password_confirmation; pending verified activation; honest delivery status |
| POST /auth/login | rate limited | email/password; verified full session or verification-only token |
| POST /auth/verify-email | verification token | six digits, keyed hash, expiry and transactional attempts; full session after success |
| POST /auth/resend | verification token, shared limits | issue/deliver OTP; failure never claims recipient delivery |
| POST /auth/forgot-password | shared limits | generic response; bounded SMTP attempt |
| POST /auth/reset-password | shared limits | email/code/password/confirmation, revoke all sessions |
| POST /auth/refresh | refresh secret | transactional one-use rotation; reject replay |
| POST /auth/logout | bearer | revoke current session family |
| GET/PATCH/DELETE /me | verified bearer | own profile; validated mutation; password/confirmation for deletion |
| GET /me/export | verified bearer | own data only; no secrets/exempt local data |
| GET/PATCH /me/preferences | verified bearer | allowlisted language/theme/reading settings |
| GET/PUT /quran/position | verified bearer | canonical position with conflict revision |
| GET/POST/DELETE /quran/bookmarks; DELETE /quran/bookmarks/{id} | verified bearer | owned canonical references; coordinate DELETE body `{surah,ayah}` is idempotent and cannot affect another account |
| GET /quran/metadata | verified bearer | versioned 114-surah index and source metadata |
| GET /quran/surahs,/ayahs,/search,/daily,/calculate | verified bearer | implemented canonical text/sections/Arabic search/target/range services |
| GET /quran/reciters,/audio,/radios,/timing | verified bearer | implemented fixed MP3Quran adapter; fixture-tested; native playback verification pending |
| GET /quran/tafsirs; GET /quran/tafsir | verified bearer | Quranpedia category3 tafsir catalog/search/paging and canonical surah/ayah/book lookup; source metadata, bounded private cache and provider budget |
| GET /books,/scholars,/series,/lessons,/adhkar | verified bearer | published catalog, pagination and details |
| GET /books/{id}/download | verified bearer | published/right-authorized file or direct link; audit aggregate event once |
| GET/PUT/DELETE /me/favorites | verified bearer | owned published book/lesson/series targets |
| GET/PUT /me/khatmah,/me/lesson-progress | verified bearer | implemented typed owned state/idempotent completion |
| GET/PUT /me/adhkar-progress | verified bearer | account-owned reviewed-item counter; source repeat target, expected_revision CAS and idempotent completion event |
| GET/POST /lessons/{id}/comments | verified bearer | visible plus own pending comments; spam limits |
| GET /search/dorar | verified bearer | planned approved fixed-scope native proxy; not implemented; legacy web proxy remains |
| GET /night-prayer/overview,/analytics,/calendar | verified bearer | own computed records, no admin private access |
| GET/POST /night-prayer/sessions | verified bearer | paginated owned history; stable UUID/idempotency key |
| GET/PUT/DELETE /night-prayer/sessions/{uuid} | verified bearer | ownership + expected revision; 409 conflicts; deletion clears private content |
| GET/PUT /night-prayer/preferences | verified bearer | allowlisted nonsensitive preferences; no exemption fields |
| POST /night-prayer/sync | verified bearer | bounded mutation batch/cursor pull; transaction/idempotency/tombstones |

Admin web routes use their own cookie/CSRF/role boundary and shared services; any admin JSON endpoint applies the same role policy independently. No API for private exemption upload or admin viewing private prayer logs is designed. Rows explicitly marked planned are not implemented. Identity, canonical Quran, catalog/progress and Night Prayer routes have isolated service/kernel tests; MP3Quran uses injected fixture tests and separate live catalog inspection. Full HTTP/native acceptance remains incomplete.


## Request/response details

- Send `Authorization: Bearer <access_token>` and `Accept-Language: ar` or `en`. Access tokens last15 minutes; refresh tokens rotate once, with a bounded family lifetime. Verification-only credentials can verify/resend/logout, never read main content.
- Register returns202 with `user`, `session` and `delivery_status` (`accepted` or `unavailable`). Provider acceptance does not guarantee inbox delivery. Refresh returns `session` and may omit `user`; clients retain their authenticated profile.
- `PUT /me/password`: current_password, password, password_confirmation. Delete account requires password; it returns a deleted status and invalidates sessions.
- Catalog pagination lives under `data.pagination`: page, per_page, total, has_more. Most lists default20/max50. Quran text search defaults25/max50 and ayah retrieval defaults50/max100. Published scholar/series associations are enforced, including favorite listings.
- `PUT /me/favorites`: target_type (book/lesson/series), target_id. The same body with DELETE removes a favorite.
- `GET /books/{id}/download` returns a rights-authorized HTTPS direct URL. Optional Idempotency-Key UUID records one aggregate event. No arbitrary download proxy is implemented.
- `PUT /quran/position`: surah, ayah, expected_revision. Initial revision0, stale edits409 with owned current state.

### Night Prayer example

POST `/night-prayer/sessions` with verified bearer:

```json
{"id":"11111111-1111-4111-8111-111111111111","mutation_id":"22222222-2222-4222-8222-222222222222","expected_revision":0,"night_date":"2026-10-24","timezone":"Africa/Cairo","boundary_source":"manual","entry_type":"range","start_index":1,"end_index":100,"note":"Private note"}
```

Server computes100 inclusive verses and returns only id/revision/deleted acknowledgement. Range count cannot be forged. Manual entries instead require entry_type=manual and a positive verse_count. Optional boundary instants must be paired, offset-qualified ISO timestamps; calculated context requires actual boundaries. Night date is the local Maghrib date. Notes are encrypted at rest and never copied into replay records. DELETE requires mutation_id and expected_revision only; it clears all private session content before retaining the tombstone.

Sync accepts `{cursor,operations:[{id,operation:"put"|"delete",data:{...}}]}` with at most50 operations. Per-operation failures preserve409 conflict/current owned state. Response includes results, deduplicated changes, next_cursor and has_more. Persist cursor only after changes/acknowledgements commit locally. Exemption-related fields are rejected and have no server endpoint. Analytics requires explicit current_night/night_in_progress context for a current streak; absent context returns null current streak rather than guessing midnight boundaries.

### MP3Quran example

GET `/quran/reciters?surah=1&page=1&per_page=25&q=...` returns stable reciter IDs and editions with actual supported surah arrays. Resolve `/quran/audio?reciter=1&edition=1&surah=1`. Audio includes an HTTPS URL and timing availability for the exact catalog folder. `/quran/timing` uses the same tuple and returns ayah/start_ms/end_ms. Missing timing does not block whole-surah audio. `/quran/radios` exposes validated provider-listed HTTPS stations. Client-supplied URLs are never fetched server-side.

## Native learning increment

### Upcoming/live events (migration014)

- GET `/upcoming-lessons`: verified native session, paginated visible scheduled/live events; `Accept-Language` selects Arabic or available English metadata. Response includes authoritative server time. Passing the scheduled time never promotes an event to live.
- GET `/upcoming-lessons/{lesson_id}`: visible event detail, including cancellation/replay state. Hidden/draft/hidden-parent content is not public.
- GET `/admin/lesson-events`: verified admin/owner list, including editorial states.
- GET/PUT `/admin/lessons/{lesson_id}/event`: verified admin/owner; writes require `expected_revision`. Stale writes return409. Explicit offset-qualified `scheduled_at` is normalized to UTC; `timezone` preserves IANA context. Live is an explicit audited transition. Translation edits, replay/publication and change facts are transactional. Change facts do not dispatch reminders or push messages.

GET `/me/adhkar-progress?item_id={id}` provides an exact owner-scoped counter lookup. The bounded overview is not proof an older counter is absent. Native cold-device counter initialization uses the exact lookup; pending offline work is retained.

Adhkar catalog: GET `/adhkar/categories` returns all reviewed, published category/count pairs. Existing `/adhkar?category=...` pages only that category. Null repeat_count remains unspecified; clients must not manufacture a repetition target.

- GET `/scholars/{id}/profile`: published scholar metadata without legacy nested previews. Paginate `/series?scholar_id={id}` and `/lessons?scholar_id={id}&standalone=1` separately. Lesson scholarship inherits from its series only when no direct scholar is assigned; standalone excludes series lessons.

- GET `/lessons/{id}/watch`: published lesson, account-owned progress/favorite, series position/current+next preview and first-party discussion preview. Opening is read-only.
- GET `/series/{id}/overview`: published metadata, owned completion totals/favorite and continue-watching target.
- GET `/series/{id}/lessons`: full pagination, stable sort_order/id ascending, owned progress.
- GET `/lessons/{id}/discussion`: root page, or replies using parent_id; newest/oldest, page/per_page <=50. Status includes owner-only pending comments; hidden parents suppress their threads. Counts describe accessible top-level rows, with separate reply counts.
- POST same route: body2..2000 characters, client_id UUID, optional root parent_id. Idempotent replay does not consume posting rate again. Reusing a mutation ID for a different body/lesson/parent returns409. All posts initially pending.
- GET `/admin/comments`: verified admin/owner only, status filter and pagination.
- PUT `/admin/comments/{id}`: verified admin/owner, status and expected_revision; conflicts409 and an audit record on mutation. No private prayer access.

Legacy `/comments` array routes remain top-level only; they do not expose orphaned reply bodies. Migration013 adds nullable root links, mutation keys/hashes and moderation revision without rewriting existing comments. Local runtime update followed a private verified logical backup; original recovery source files remain unchanged.
