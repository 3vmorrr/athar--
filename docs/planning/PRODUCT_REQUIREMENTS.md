# Product Requirements

**Status:** Draft for owner review. Acceptance criteria below define the proposed MVP boundary. “Must” statements are product requirements, not claims of existing implementation.

## Shared journeys and rules

### Guest to optional account

1. Guest opens a public area, browses/searches, and reads/listens/plays without registration.
2. A sign-in prompt appears only when the visitor chooses a save/continuity action.
3. Before registration, the browser may offer to merge supported local Quran position/bookmarks into the new account. Show the items and destination account, require explicit confirmation, validate each item server-side, deduplicate, and preserve the newer valid position. Never silently merge or upload unrelated browser data. If browser storage is unavailable, say so and continue sign-up.

**Acceptance:** guest read/listen/play routes work without a session; save controls explain the account requirement; local progress remains local until consent; sign-out and another device do not reveal private data.

### Arabic, sources, and resilience

Public content is RTL-first when Arabic is selected, supports mixed-direction references, and retains source attribution through lists, detail screens, and sharing. API errors/timeouts show a localized retryable state and do not erase local or unrelated content. Every loading, empty, error, and unavailable state has an accessible explanation.

**Acceptance:** Arabic page structure uses RTL semantics; keyboard focus is visible; source and unavailable metadata remain visible; bounded searches paginate; failures do not expose secrets or raw provider traces.

## Feature requirements and acceptance criteria

### 1. Quran reading and listening — MVP

- Browse the 114 surahs and navigate by surah/ayah; display Arabic in a comfortable, selectable RTL reading layout with adjustable text size and adequate line spacing.
- Play recitation audio only from a selected provider-authorized audio URL/player; show reciter and source. Provide play/pause, progress, loading, error, and unavailable states.
- Search only through a provider endpoint and terms that allow the requested text/search behavior. If no permitted search integration is live, explain the limitation rather than silently searching a partial local copy.
- Guests read/listen; signed-in users may bookmark ayahs and save last-read position.
- Store provider/source version and ayah identifiers with personal references; do not duplicate provider text via crawling.

**Acceptance:** direct surah/ayah navigation works; audio includes reciter/source labels and a failure state; search labels its coverage; guest access needs no account; saved position/bookmarks persist only for the signed-in owner; opt-in guest merge follows the shared flow.

### 2. Hadith — conditional MVP

- Search/browse available hadith using source-provided text and actual collection/book/chapter/reference metadata.
- Display grade only if the selected record supplies a grade, with source attribution; absent grade is labeled “not provided,” not inferred.
- Clearly label the provider’s indexed/search coverage as partial where applicable. Never present an API search result set as a comprehensive hadith corpus.
- If Sunnah.com access is not granted, do not scrape. Either defer public hadith search or publish only a separately rights-reviewed, editor-curated sample with coverage clearly stated.

**Acceptance:** each result identifies its source and reference; grade field is omitted or explicitly unavailable when absent; coverage notice is visible; a missing key/provider failure cannot be mistaken for an empty complete corpus.

### 3. Tafsir — MVP subset

- Present tafsir only when an identified provider/source returns it for the selected ayah.
- Include book, author, edition/source, and language when available; show exactly which metadata is unknown.
- Make unavailable tafsir or translation explicit. No paraphrasing, summarization, or generated commentary.

**Acceptance:** selecting an ayah shows attributed text or a clear unavailable state; metadata stays with the passage; no fabricated text appears during provider failure.

### 4. Books — MVP curated catalog

- Admin enters title, author, description, keywords, language, optional cover and publication metadata, rights holder/permission basis, source, and permitted actions.
- Admin uploads only authorized PDFs. Enforce configured upload size, extension and detected MIME/signature checks, generated storage names, malware scanning where available, and private/non-executable storage outside the public web root where deployment allows.
- Users browse/search/paginate, open details, and read or download only when rights allow. Serve with safe content type, `Content-Disposition: attachment` for downloads, `X-Content-Type-Options: nosniff`, authorization checks, and no path derived from the submitted filename.

**Acceptance:** invalid, oversized, mismatched, or unauthorized uploads are rejected; original filename is never trusted as storage path; a record without permission metadata cannot be published; only permitted download/view actions are exposed.

### 5. Scholars, lessons, and video series — MVP

- Admin manages scholar name, image, short biography, language, source, and visibility.
- Scholar page lists ordered series and standalone lessons. Series contains ordered lesson records with title, description, canonical YouTube URL/video ID, optional duration/thumbnail, and publication state.
- Validate and normalize only supported YouTube video URLs/IDs. Play with official YouTube IFrame Player; preserve branding, native controls and ads. Do not download/extract media or gate playback behind likes, subscriptions, comments, or rewards.
- Guests may play. Signed-in users may save a series and store last lesson/time. Update on meaningful player events and a modest throttle (proposed every 30 seconds while playing plus pause/end/page-hide best effort), not every second. Validate duration and lesson ownership on the server.
- Explain autoplay restrictions, removed/private/non-embeddable videos, browser policy limitations, and that position restoration is best effort.

**Acceptance:** guests can play public lesson; only valid YouTube URLs create a player; embed errors have recovery/help state; player controls/branding remain visible; progress saves at throttled event checkpoints and never promises exact restoration; series order is deterministic.

### 6. Comments and moderation — later / decision gate

Initial release has no comments. If approved later, use first-party Athar comments, authenticated users only; escaped plain text, bounded length, CSRF protection, rate limits, user reporting, moderation queue, hide/remove/restore actions, scoped permissions, and audit history. YouTube comments are not included without a specifically verified, policy-compliant integration.

**Acceptance if enabled:** guests cannot post; text renders as text; report and moderation transitions are attributable/audited; throttling and length limits are enforced server-side; no UI claims to show YouTube comments.

### 7. Adhkar — MVP editorial dataset

- Browse/search by reviewed category and occasion.
- Show original text, count when the source supports it, and traceable source/reference; distinguish editor metadata from source text.
- Prefer a small editorially reviewed dataset with documented permission and reviewers over an unverified API.
- Count/progress control supports keyboard, screen reader labels/live updates, reset, and touch targets of at least 44×44 CSS px.

**Acceptance:** every published item has source and review status; count updates by keyboard and touch without relying on color alone; unknown counts are not invented.

### 8. Account and worship tools — MVP

- Registration/login is optional for public use. Account enables ayah bookmarks, favorites for books/series, Quran position, video position, and preferences.
- Khatmah plan supports target completion date and/or understandable daily workload (pages, juz, or chosen units). Store plan start/date, target, completed amount, pause/finish status, and last calculation date.
- Recalculate remaining workload across remaining days from actual remaining pages/juz and current date. Missed days increase the remaining daily target; explain this before showing it. User can pause, edit, restart, or finish; no public ranking or guilt language.
- Provide authenticated export of personal profile and progress in a documented machine-readable format. Deletion requires reauthentication or equivalent step-up, confirmation, session revocation, and deletion/anonymization policy for user-owned data; disclose legally required audit retention.

**Acceptance:** user can read their plan, pause/edit/finish; remaining amount recomputes from remaining units/days; only owner/admin with approved support scope can access private data; export is authenticated; deletion behavior is explained and enforced.

### 9. Admin dashboard — MVP

- Role-scoped dashboard overview, meaningful aggregate content/activity metrics, CRUD for scholars, series/videos, books, adhkar and curated source records; user management; profile/settings; audit history for sensitive actions; owner-only backup creation/download; separately confirmed restore; and local-development database reset.
- Public/authenticated/moderator/admin permissions are server-enforced per operation (details in `SECURITY_AND_PRIVACY.md`). Admin capabilities map to individual permissions, not a broad “logged in” test.
- Comments/report queue remains disabled until first-party comments are approved and staffed.
- Database reset is in scope as a local-development/admin feature. It is owner-only and must be denied server-side in production regardless of UI visibility; require step-up authentication, POST-only, CSRF, typed exact environment and destructive phrase, a recent verified full backup, a write lock, and an audit event outside the database being reset. Before confirmation, enumerate exactly what database rows and uploaded files will be removed and what schema/configuration remains. Reset and recovery behavior are specified in `SECURITY_AND_PRIVACY.md` and `ADMIN_AND_OPERATIONS.md`.
- Downloadable backup is owner-only by default. Require step-up authentication and CSRF-protected POST to create it and a separate step-up-authenticated, CSRF-protected POST to issue a short-lived single-use download grant. Store an encrypted archive outside the public web root with restrictive permissions; audit creation, grant issuance, download, expiry, and deletion; verify integrity before making it available. Include the database, schema/migration metadata, necessary restore manifest, and uploaded book PDFs/content-managed assets. Exclude secrets, logs, temporary/cache data, and external YouTube media. Restore is a separate owner-only action with explicit confirmation.
- Never provide database credentials or arbitrary SQL in the UI.

**Acceptance:** every route/action checks role and record scope server-side; sensitive actions create actor/time/target/reason audit records; reset is owner-only, step-up authenticated, POST+CSRF protected, explicitly confirms environment and destructive phrase, refuses production server-side, holds a write lock, requires a recent verified full backup, reports what is deleted/retained, and writes its audit event outside the reset database. Owner can create and download an encrypted backup only after step-up and CSRF checks; generated files are private, integrity-checked, expire on the documented schedule, and are downloadable only through an authorized short-lived single-use grant. Backup contents include DB, restore metadata and necessary uploaded assets; restore is separate and explicitly confirmed. No dashboard response reveals SQL or credentials.

## MVP exit criteria

All MVP acceptance checks above pass in implementation review; approved source rights/access are recorded; no production credentials appear in client code/logs; Arabic and keyboard flows are reviewed at mobile and desktop sizes; privacy/export/deletion and backup restore procedures are operationally understood. Protected backup download and local-development reset are in scope; first-party comments remain a separate later decision.
