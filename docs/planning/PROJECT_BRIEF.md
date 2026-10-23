# Athar (أثر) — Project Brief

**Inspection and research date:** 2026-10-23
**Status:** Planning draft for owner review
**Repository:** [`3vmorrr/athar--`](https://github.com/3vmorrr/athar--)

## Vision

Athar is a free, Arabic-first place to read and listen to the Quran, explore attributed Islamic source material, learn from curated lessons, and maintain private worship plans. Guests can use core discovery and playback immediately. An optional Athar account adds private bookmarks, reading/video continuity, favorites, and preferences across devices.

Athar is a presentation and personal-organization platform, not a source of original fatwas or generated religious commentary. Religious text, translations, tafsir, hadith grades, and adhkar references must preserve source and attribution metadata. Missing source data is stated plainly.

## Audience and needs

- Arabic-speaking readers and listeners who want a calm, readable RTL experience on phones and desktop.
- Learners who want to find source-attributed books, scholars, lesson series, and individual lessons.
- People who want private continuity and a flexible khatmah plan without public competition or guilt-based streaks.
- Editors and administrators who curate content and respond to user reports with traceable actions.

## Goals

1. Make Quran reading/listening, curated learning, and adhkar accessible to guests without registration.
2. Make source attribution, content rights, and uncertainty visible at the point of use.
3. Provide optional private account features with clear export/deletion controls.
4. Keep the architecture understandable, secure, responsive, accessible, and economical to operate.
5. Preserve the authoritative Athar brand palette and Arabic-first layout described in `UX_AND_DESIGN.md`.

## Scope

**MVP:** guest Quran reader/player using an approved source; attributed Quran search if permitted; limited hadith lookup only if an approved API key and coverage are available, otherwise a clearly labeled unavailable/curated pilot state; selected sourced tafsir; small editorially reviewed adhkar collection; curated books with verified rights; scholars, series and YouTube lessons; optional account, bookmarks and progress, khatmah planner; scoped admin tools; secure content uploads; owner-only protected downloadable backups and a local-development-only database reset with production execution denied server-side.

First-party comments are **out of MVP**. If later enabled, they are optional, authenticated-only, moderated Athar content. Do not imply YouTube comments are available.

**Later:** broader licensed collections, richer search, additional integrations after approval, comment/report workflow if staffed, analytics beyond essential operational metrics, and any public memorial feature only as a separately scoped feature using its reserved palette.

## Non-goals

- Issuing, interpreting, or generating fatwas or tafsir.
- Scraping or bulk mirroring API/provider content against terms.
- Downloading or extracting YouTube audio/video, replacing the YouTube player, suppressing its branding/ads, or gating viewing on engagement.
- Requiring an account to browse/read/listen to public content.
- Public worship rankings, coercive streaks, or social pressure.
- Unrestricted SQL or database credentials exposed to dashboard users; production database reset execution.
- Choosing a PHP framework or deployment provider without repository/deployment evidence.

## Repository baseline and assumptions

Implementation update 2026-10-23: the local project now contains a PHP 8.0-compatible server-rendered app, MySQL/MariaDB schema, public source-integrated routes, optional accounts, private Quran bookmarks/position, a khatmah plan, and a role-protected content editor. Local XAMPP inspection found PHP 8.0.30 and MariaDB 10.4.32. These versions are development-only and are not endorsed for production deployment. Production hosting, email, secrets, backup encryption, and recovery policy remain open.

The exact color values supplied by the owner are Athar’s authoritative palette. The owner-provided logo has now been added at `public/assets/images/athar-logo.png`. All API research in `API_RESEARCH.md` was checked against linked official documentation on 2026-10-23. External prices exclude hosting, storage, domain, and development, which remain deployment-dependent.

## Success signals

- Guests reach and use Quran, lesson, book, and adhkar content without an account wall.
- Source/edition/reference and rights status are present for every published religious item.
- Signed-in users can see, export, and delete their own private progress.
- Admin actions are role-checked and sensitive actions leave an audit record.
- Search and media states remain usable when an upstream provider is slow or unavailable.
- Arabic content remains readable and controls work by keyboard, touch, and assistive technology.
