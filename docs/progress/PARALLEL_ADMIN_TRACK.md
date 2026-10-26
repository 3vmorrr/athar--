# Parallel Admin / Engagement handoff

## 2026-10-27 — execution handoff checkpoint

- Source of truth branch: `parallel/admin-foundation`; verified head before this documentation commit: `65c5f551845227a9be55926ee92750031021bb01`.
- Last landed implementation task: **16.1 — Private notification inbox API**.
- Next implementation task: **16.2 — Native notification inbox**.
- Tasks **11.1 through 16.1** listed in `TASK_QUEUE.json > implementation_checkpoint` have landed implementation and are marked **PARTIAL**, not COMPLETE, until their queue-defined build/test/device/integration gates pass.
- **15.3** remains **BLOCKED** on an authorized free FCM project/configuration and approved physical test device. Never fabricate provider/device proof.
- Downstream implementation may continue from landed PARTIAL code dependencies; acceptance closure remains a separate gate.
- The earlier queue pointer to 02.7.2 was stale for this branch and must not cause an agent to restart already-landed engagement/reminder/push/inbox work.

Date: 2026-10-26  
Implementation branch: `parallel/admin-foundation`  
Integration target: `codex/android-ecosystem`  
Observed target head during final static review: `ddec0d350776dba293bbc60a5e8e61424f4434a9`  
Common merge base: `5e46b37cfdba6ed64c27273f0aaafa1ce3eaaa4e`

## Queue reconciliation after WP16

On 2026-10-27 the executable queue was reconciled again after WP16. Existing WP17–20 code documented below is represented as PARTIAL / implementation landed / acceptance pending. 20.5 stays BLOCKED. The next independent implementation task is 21.19; do not restart Admin/campaign/email code merely because older queue rows said NOT STARTED.

## Status rule

Nothing in this document is a runtime PASS unless a test report says so. This
parallel environment implemented and statically reviewed the Admin/engagement
lane, but it did not execute the repository's PHP/MySQL/Android acceptance
suite. Treat the tasks below as **IMPLEMENTED / ACCEPTANCE PENDING** until the
normal disposable fixture, HTTP, browser, worker and physical-device gates pass.

The main task queue has now been reconciled on 2026-10-27 to this branch's actual execution head. `TASK_QUEUE.json`/`.md` mark landed-but-unaccepted tasks as PARTIAL, keep 15.3 externally BLOCKED, and set 16.2 as the next implementation task.

## Work Package 17 — Independent Admin foundation

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

### 17.1 Independent Admin authentication

Implemented:

- isolated Admin session cookie `ATHARADMIN`, scoped to `/admin`
- HttpOnly, SameSite=Strict and HTTPS-aware Secure cookie behavior
- independent `/admin/login` and `/admin/logout`
- password verification through the shared identity layer without reusing the
  public website session or issuing Android/API bearer credentials
- explicit Admin/Owner role gate with generic denial for regular users
- session ID and CSRF rotation after authentication
- absolute/idle Admin session limits
- live role, suspension and `session_version` revalidation
- HTTP acceptance fixture in `tests/admin_auth_http_test.py`

### 17.2 Bilingual Admin shell

Implemented:

- separate Admin layout and login view
- Arabic RTL and English LTR shell
- drawer navigation, mobile behavior and reduced-motion-aware navigation
- Admin-specific CSS/JS instead of coupling the shell to the public site

### 17.3 Authorization matrix

Implemented in `admin/authorization.php` with role/capability checks before
mutating actions. Owner-only operations remain separate from ordinary Admin
content operations.

### 17.4–17.6 User management, audit and recovery

Implemented on the parallel branch:

- user lookup and safe profile/account controls
- suspension/restoration with session revocation
- owner-only destructive/recovery operations with reauthentication
- audit trails for privileged actions
- backup/recovery controls kept separate from ordinary Admin capability

## Work Package 18 — Admin content and services

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

### 18.1–18.4 Scholars, series, lessons, upcoming/live

The independent Admin can manage the existing editorial models while preserving
the shared API/database ownership. Upcoming/live editing uses the existing
lesson-event model rather than a second event schema.

### 18.5 Books

Rights/source metadata is mandatory. Download metadata is optional and validated
independently instead of forcing every book to expose a downloadable file.

### 18.6 Adhkar

Migration `016_admin_editorial_control.sql` adds editorial revision state and
revision snapshots. Admin create/update/publication/delete uses compare-and-swap
revision checks and records actor-attributed snapshots.

### 18.7 Discussion moderation

Migration `017_admin_comment_moderation.sql` adds durable moderation decision
history. Publish/hide/delete requires a reason and expected revision. Deletion
keeps a moderation event after the comment row is gone. Admin UI distinguishes
top-level comments and replies.

### 18.8 Secure service settings

- SMTP and Dorar credentials remain encrypted at rest
- secrets are never rendered back to the Admin
- diagnostics expose readiness, not secret values
- SMTP test mail can only target one encrypted, owner-authorized test recipient
- changing/clearing that recipient requires owner password confirmation

### 18.9 Public Night Prayer defaults

The Admin controls only public defaults, source attribution and explanatory
copy. The public endpoint is `GET /api/v1/night-prayer/config`.

The Admin code does **not** read or expose private Night Prayer sessions, private
notes, exemption information or personal worship history.

## Work Package 19 — Push campaign administration

Implementation status: **SERVER/ADMIN IMPLEMENTED / PROVIDER DELIVERY PENDING**

Migration `018_engagement_campaigns.sql` adds:

- channel preferences
- campaign drafts/scheduling state
- recipient snapshots
- idempotent delivery keys
- aggregate campaign events/history

Implemented Admin/server behavior:

- bilingual Push / Email / combined composer
- draft editing with revision checks
- Arabic/English preview
- consent-safe audience resolution
- locale targeting
- optional follow/reminder audiences when their canonical tables exist
- quiet-hour and frequency deferral
- TTL handling
- explicit owner password + literal `CONFIRM <campaign-id>` before scheduling
- atomic channel readiness: Push+Email cannot silently degrade to Email-only
- cancel before processing
- aggregate delivery counts/reasons/history without recipient identities
- no targeting from Quran, Adhkar, Khatmah, Night Prayer or worship-completion
  history

Push delivery is intentionally **not** claimed complete. The campaign service
keeps Push unavailable until both conditions are true:

1. the canonical `notification_devices` table exists, and
2. `platform_settings.push_campaign_delivery_ready = 1`.

The canonical notification/device/FCM lane must set that flag only after its own
delivery worker and revocation behavior pass acceptance. If the Codex lane uses
a different canonical device-table name, reconcile this check during merge
rather than creating a duplicate table.

## Work Package 20 — Email campaigns and queue

### 20.1 Transactional branding/reliability

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

Email HTML is locale-aware (Arabic RTL / English LTR) and keeps the Athar
branding. OTP/reset copy is localized from the user's language preference.

### 20.2 Consent and unsubscribe

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

- campaign email consent is distinct from transactional account email
- legacy `email_marketing_opt_in` is honored for compatibility
- the engagement preference API is
  `GET/PATCH /api/v1/me/engagement-preferences`
- opt-out cancels queued campaign mail only
- OTP/password-reset mail remains allowed
- newly generated unsubscribe links are signed v2 links with expiry
- legacy v1 unsubscribe links remain accepted for already delivered messages

### 20.3 Prioritized durable email queue

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

Migration `019_email_outbox.sql` and
`backend/src/Engagement/EmailOutboxService.php` provide:

- transactional priority ahead of campaigns
- idempotency keys
- row claiming/locking
- stale-lock recovery
- TTL
- bounded exponential retry
- campaign quota deferral without blocking transactional messages
- consent re-check immediately before campaign delivery
- campaign-recipient status reconciliation

Worker entry point:

```bash
php bin/process-email-outbox.php 20
```

No bulk SMTP send remains in an Admin HTTP request. The legacy synchronous
`campaign-create`, `campaign-batch` and `campaign-retry-failed` execution
path was removed from `public/index.php`.

### 20.4 Admin Email / combined campaigns

Implementation status: **IMPLEMENTED / ACCEPTANCE PENDING**

The Campaign Center supports Email and Push+Email drafts, preview, scheduling,
history and an email-template test. Test mail can only be queued to the
encrypted, owner-authorized test recipient from Service Settings.

Final Email campaign confirmation is blocked until SMTP host, username,
encrypted password and valid From address are configured **and**
`ATHAR_BASE_URL` is a valid public HTTPS URL, because unsubscribe links must
not resolve to the local fallback address.

### 20.5 Authorized Gmail inbox proof

Status: **BLOCKED — EXTERNAL ACCEPTANCE REQUIRED**

Do not mark complete until an authorized real inbox confirms receipt from the
configured Gmail/App Password path. Never send to an arbitrary address during
acceptance.

## Additional Android offline hardening — task 02.7.2

Implementation status: **IMPLEMENTED / RUNTIME ACCEPTANCE PENDING**

The branch now also contains the additive Night Prayer permanent-operation
rejection recovery required by task 02.7.2. It preserves original encrypted
outbox bytes, separates durable 404/422 operation rejection from 409 conflicts
and temporary 429/5xx failures, prevents one rejected session from starving
healthy work, and adds explicit owner-bound retry / repair / use-account
recovery in Arabic and English.

The implementation deliberately uses encrypted private rejection markers in the
existing `cached_documents` table, so this increment does not require a Room
schema reset or destructive migration. Focused storage and dialog tests are
included. Treat this work as acceptance-pending until the Codex workstation runs
the normal Gradle, instrumentation and physical-device gates documented in
`docs/features/NIGHT_PRAYER_REJECTIONS.md`.

During integration, preserve any newer 02.7.x logic on
`codex/android-ecosystem` and reconcile this task semantically rather than
replacing the whole Night Prayer/offline files.

## Database changes on this branch

- `016_admin_editorial_control.sql`
- `017_admin_comment_moderation.sql`
- `018_engagement_campaigns.sql`
- `019_email_outbox.sql`

Apply through the normal migration runner on the disposable development schema
before HTTP/worker acceptance.

## Merge conflict assessment

At final review the Codex branch had advanced three commits from the common
merge base, and those commits were confined to Android/offline/Night Prayer
client work plus progress docs. No backend/Admin file overlap was observed in
those three commits.

Likely integration hotspots because this parallel branch intentionally changes
shared server entry points:

- `public/index.php`
- `backend/api.php`
- `backend/src/Http/ApiKernel.php`
- `backend/src/Identity/OtpService.php`
- `backend/src/Learning/DiscussionService.php`
- `backend/src/NightPrayer/NightPrayerService.php`
- `app/identity.php`
- `app/mailer.php`

Preserve any newer Codex logic in these files during merge. Do not overwrite
newer offline/sync or Android behavior.

## Required acceptance before task-queue closure

Run on the normal development workstation with the disposable database:

1. PHP syntax checks for every changed PHP file.
2. Apply migrations 016–019 and verify forward migration from the current
   development schema.
3. Run `python tests/admin_auth_http_test.py`.
4. Exercise Admin login/logout, RBAC, suspension/recovery and audit paths.
5. Exercise scholar/series/lesson/event/book/adhkar/comment/service/Night Prayer
   Admin forms in Arabic RTL and English LTR.
6. Verify Adhkar and comment revision conflicts with two concurrent sessions.
7. Verify Campaign draft/edit/preview/owner-confirm/cancel and no-zero-recipient
   behavior.
8. Verify opt-out stops queued campaign Email but does not stop OTP/reset.
9. Run the outbox worker against fixture SMTP/failure cases and verify retry,
   stale-lock recovery, TTL and campaign reconciliation.
10. Perform the authorized Gmail inbox proof for 20.5.
11. After the canonical Push/device lane lands, connect its provider worker,
    validate revocation/logout/TTL/dedupe, then set
    `push_campaign_delivery_ready=1`.
12. Only after those gates pass, update the main Codex task queue from
    IMPLEMENTED/PARTIAL to TESTED/COMPLETE as appropriate.

## Integration policy

This branch is ready for review as a **draft integration**, not for an
unreviewed merge. Do not force-push or force-merge. Recompare with
`codex/android-ecosystem` immediately before integration and resolve any new
shared-file changes deliberately.
