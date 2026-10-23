# Roadmap and Open Questions

**Planning date:** 2026-10-23. Dates/sprint lengths are intentionally omitted until ownership, capacity, access approvals, and hosting are known.

**Implementation snapshot:** phases 1–4 have working foundations in the current application, but MVP exit criteria and production readiness are not yet met. See the local tracker outside this repository for the exact completed and remaining items. Do not infer deployment readiness from the existence of account or admin routes.

## Phased roadmap

### Phase 0 — Decisions and readiness

- Owner approves source choices, editorial/rights review owner, account/privacy policy, and comments policy. Protected downloadable backup and local-development-only database reset are in scope; confirm operational parameters, not whether to omit them.
- Confirm local/production PHP and database versions, deployment host, upload storage, backup RPO/RTO, domain, email, and secret management.
- Set backup artifact encryption/key management, owner access policy, 24-hour default artifact expiry, durable recovery retention, maximum archive size, and deletion/recovery runbook. Reset remains blocked in production by design.
- Request Quran Foundation/Sunnah.com access only if those providers are selected; verify endpoint-specific rights, limits, and price at implementation kickoff.
- Set up UI/content review for Arabic typography, Quran text/diacritics, contrast, and responsive layouts.

**Exit:** decisions and provider access/terms are recorded; no blocking rights/hosting uncertainty for selected MVP content.

### Phase 1 — Secure foundation and public shell

- Establish supported PHP/MariaDB local environment, structured MVC-style modules, migration/versioning, session/CSRF/authz foundations, logging, error handling, secret configuration, protected backup artifact lifecycle, local reset safety gate, and baseline responsive RTL design system.
- Build public navigation, source/rights model, loading/error/empty patterns, pagination, and accessible account entry points.

**Exit:** guest public flows work; authorization and CSRF apply to protected examples; migrations and restore approach are documented; palette tokens are contrast checked in actual component pairs.

### Phase 2 — Guest knowledge MVP

- Quran reader and recitation using selected permitted provider; source-attributed selected tafsir/translation and permitted search.
- Small editorially reviewed adhkar collection with accessible count control.
- Books catalog and permission-checked upload/download; scholar pages, series, standalone lessons, official YouTube embeds.
- Arabic hadith search uses the official Dorar API; it stays unavailable until the application passes manual access review and an administrator stores the approved token.

**Exit:** feature acceptance criteria in `PRODUCT_REQUIREMENTS.md` pass; guest access remains open; provider failures are graceful; all published content has source/rights data.

### Phase 3 — Optional accounts and personal worship tools

- Registration, secure login/reset, bookmarks, reading position, favorites, best-effort video progress, preferences, guest progress merge with explicit consent, khatmah plans, export and deletion.

**Exit:** user data ownership/privacy checks pass; export/deletion flows and retention are documented; no public worship ranking.

### Phase 4 — Admin and production readiness

- Role-scoped CRUD, source/review workflow, aggregate metrics, user support actions, audit trail, upload controls, moderation only if separately approved, and owner-operated backup/restore.
- Conduct owner-only backup-download and restore exercise, verify reset denial in production configuration and reset recovery in isolated local development, SMTP delivery, permissions review, source-term review, accessibility/usability review, monitoring and incident rehearsal.

**Exit:** no unreviewed roles/actions; encrypted backup download, integrity verification, expiry/deletion and restore are verified; local reset safeguards and recovery are verified; production reset denial is verified server-side; release requirements and operational owner are identified.

## Dependencies and risks

| Risk/dependency | Effect | Mitigation / decision point |
|---|---|---|
| Empty repository/local Git root points at user home | No stack/version/history facts; broad status noise | Keep docs limited to project directory; correct repository/worktree metadata before implementation without touching unrelated files. |
| Quran source approval/terms/feature support | Search, audio, tafsir availability may change | Verify endpoint-specific use and production permissions before promise; source fallback states are explicit. |
| Dorar API approval | Hadith search stays unavailable | Submit the official application early; keep the page in a clear unavailable state until a token is approved and configured. |
| Rights for PDFs, covers, translations, audio, adhkar | Unlicensed republication risk | Require permission/edition metadata and publication gate. |
| YouTube video policy/availability/browser restrictions | Playback and resume not guaranteed | Official player only, guest play, native controls/ads, event-throttled approximate progress, watch-page fallback. |
| Arabic search, shaping, font coverage | Poor reading/findability | Validate Arabic normalization/search behavior and font glyph shaping with representative content and devices. |
| Unknown host, storage, email and backups | Cost, reliability and recovery unknown | Choose provider and RPO/RTO before deployment; API price does not include infrastructure. |
| Admin moderation capacity | Unsafe/unanswered comments | Comments remain pending until an administrator reviews and publishes them; disable comment posting if no reviewer is available. |

## Owner decisions needed

1. Apply for Dorar API access, provide a review account or staging link as requested, and configure the approved token in the administrator settings.
2. Choose first Quran provider after endpoint-specific terms and feature checks: Quranpedia live API, Quran Foundation after app/scope approval, or defer a feature.
3. Name an editorial reviewer and define approval/source expectations for tafsir, hadith, adhkar, translations, and PDF rights.
4. Decide whether email/password account MVP is sufficient or whether external identity/account linking is desired; Athar accounts do not require Quran Foundation OAuth by default.
5. Assign an administrator to review pending lesson comments and establish the public moderation policy.
6. Select deployment host, PHP/MySQL versions, SMTP provider, file storage, budget, data region, backup RPO/RTO, maximum archive size, encryption-key handling, durable retention, and cleanup ownership. Service settings are excluded from protected backups and must be reconfigured after a new-host restore.
7. Confirm account deletion/audit retention language and whether book PDFs should stream in-browser, download, or both per rights.

## Next steps

1. Review and approve/revise these planning documents.
2. Resolve the owner decisions above and record provider access/rights.
3. Select a supported production host/runtime and set up HTTPS, a dedicated database account, managed secrets, outbound email, and private file storage.
4. Complete a source/editorial rights review, backup-encryption and restore runbook, upload scanning policy, accessibility review, and production security review before launch.

## Inspection record

Initial planning inspection on 2026-10-23 preceded implementation and is retained in the project brief's history. Current PHP and database details are in `ARCHITECTURE.md`; feature acceptance criteria are in `PRODUCT_REQUIREMENTS.md`.
