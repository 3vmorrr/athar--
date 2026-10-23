# API and Cost Research

**Checked:** 2026-10-23 against official provider documentation linked below. Claims are current as observed on that date and must be rechecked before launch. No credentials were requested and no authenticated API was called. “No public price found” is not a promise of free access.

## Summary

| Candidate | Proposed use | Access / key | Public API price evidence | Main constraint / fallback |
|---|---|---|---|---|
| Quranpedia | Quran text, tafsir/translation where returned, reciter/audio metadata and permitted search | No authentication; live read API | **$0 API usage** is expressly stated for live API querying by apps/sites/bots/research tools | 120/minute and 10,000/day per IP; no bulk scrape/republish; live use + modest cache, fallback to clear temporary-unavailable state |
| Sunnah.com | Hadith search/content | API key requested through GitHub issue; access not assured | **No public API price found** in official developer page | API covers a portion only; no key means defer or carefully licensed editorial subset; never scrape |
| Quran Foundation | Alternative Quran content/audio/tafsir/search | Developer Console app, pre-live credentials, request production permissions/scopes; server-only client secret | **No public API price found** in official docs checked | Production access approval and scopes; pre-live content limited; do not expose secrets; fallback to Quranpedia if terms/features approved |
| YouTube IFrame Player | Play admin-entered video IDs | Embed-only use has no YouTube Data API key in proposed design; no OAuth credential for IFrame embed | **No public IFrame embed usage price found**; no paid-cost claim made | Playback depends on owner settings, availability, browser/network and policies; fallback to link to YouTube watch page with unavailable state |

Hosting, domain, file storage, backup, email, and development costs are separate and unknown until deployment and volume are selected. “Free” does not mean a free hosting or storage plan.

## Quranpedia API

- Official docs: [API documentation and usage policy](https://api.quranpedia.net/). The documentation describes read-only JSON endpoints, no authentication, ayah/surah and mushaf data, translations, tafsir, reciters, books, search and other endpoints. Confirm the specific endpoint and source metadata before committing a feature.
- Official usage policy says live API use is free, no auth/no fees; limit 120 requests/minute and 10,000/day per IP. It says app attribution is optional for live queries (link appreciated); dataset publication requires attribution. It prohibits bulk scraping and republishing and notes ongoing corrections.
- Plan: server-side provider adapter with bounded timeout, schema validation, cache only as terms permit, low request volume, and provider/source label. Never crawl a corpus or present a stale mirror. Re-check endpoint-specific permissions for search, audio, translations, tafsir and display before launch.
- Price certainty: **$0 for documented live API usage only**, per stated terms. Hosting/domain/storage/development are not included.
- Risk/fallback: rate limiting, upstream downtime, corrected content, and endpoint changes. Show retryable unavailable state; use an approved Quran Foundation integration as alternative only after credentials/scopes and terms are confirmed. Do not silently switch source or lose attribution.

## Sunnah.com API

- Official developer page: [Sunnah.com Developers](https://sunnah.com/developers); API specification linked there at [Sunnah API docs](https://sunnah.stoplight.io/docs/api/).
- Developer page states API access covers a portion of its data and that an API key is required, requested by creating an issue on its GitHub repository. It says additional data may be added after manual checking. No offline dump was available on the page checked.
- Price certainty: **no public API price found** in the official developer page checked. Do not call it free or assign a fee without written official confirmation.
- Plan: request a key only when ready; design search as explicitly partial/indexed, display collection/book/chapter/reference and grade only if actually supplied, and keep provider URL. If approval/key is unavailable, defer the integration or use an editorially approved/licensed subset whose permission is recorded. Never scrape the site.
- Risks: key approval, changing partial coverage, rate limits/terms not established from the developer landing page; confirm linked API docs and written terms before launch. Provider outage must not appear as a complete empty corpus.

## Quran Foundation developer APIs

- Official pages: [Get API access](https://api-docs.quran.com/request-access/), [Content API quickstart](https://api-docs.quran.com/docs/quickstart/), [OAuth scopes](https://api-docs.quran.com/docs/user_related_apis_versioned/scopes/).
- Current flow in official docs: create/register app in Developer Console; receive pre-live credentials; test in pre-live; request production permissions for required scopes. Content calls use OAuth2 Client Credentials with `content` scope; content search may need separate `search` scope. User APIs use user consent/Authorization Code + PKCE and individual scopes. Qira'at read access, where applicable, has separate `qiraat.read`/`qiraat` scope and is production-only per docs checked.
- Content API client secret is backend-only. Pre-live Quran dataset described in quickstart is limited to Al-Fatihah and Al-Baqarah; full dataset needs approved production access. Token lifetime documented as 3600 seconds; cache token, retry one 401 once, back off for 429/transient 5xx, and avoid blind retries.
- Price certainty: **no public API price found** in official access/quickstart documentation checked. Do not infer free/paid from access availability.
- Plan: viable alternative/future source if credentials, needed scopes, production permission, content attribution/license, and PHP integration path are approved. PHP can use raw HTTP from backend; no requirement to use a JavaScript SDK. Keep user API consent separate from Athar account sign-in.
- Risks/fallback: pre-live limited data, scope/production approval, terms and future policy updates. Use Quranpedia if suitable terms/coverage are verified; keep public content available through a clear source failure state if both unavailable.

## YouTube IFrame Player API

- Official sources: [IFrame Player API reference](https://developers.google.com/youtube/iframe_api_reference), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies), [Required minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality), [Player parameters](https://developers.google.com/youtube/player_parameters).
- Proposed use: admin enters and validates a video ID; Athar embeds via the official IFrame Player API and listens to documented state/progress events. No remote video search is proposed, so YouTube Data API and its API key/quota are not needed for embed-only behavior. IFrame API docs show API-script/embed setup without a developer API key; do not imply this removes Google/YouTube policy or privacy obligations.
- Preserve player branding, native controls and ads; do not overlay/obscure controls, download/extract content, use hidden autoplay, or use interactions to trigger likes/subscriptions. The policy docs are subject to change. Review applicable terms, required minimum functionality, privacy/consent, and embed behavior before release.
- Price certainty: **no public IFrame embed usage price found** in the official docs checked. Do not claim a paid API cost. Separate hosting and any consent/analytics implementation costs.
- Playback limitations: videos may be private, deleted, non-embeddable or restricted by browser/network/policy; autoplay may be blocked; event-based position can be unavailable/unreliable. Save best-effort checkpoints at a throttled interval and state that resume is approximate. Fallback to a clearly labeled link to YouTube when embedding fails.

## Source policy and launch gate

For every external religious source store provider/source URL, collection/book/chapter or edition, language, license/terms URL, attribution, provider identifier, checked date, and any editorial review. Do not silently substitute third-party sources with unclear provenance. Before implementation, record selected source per feature, rights decision, credential owner, allowed caching/display, and degradation path. Recheck source docs and terms immediately before launch because this review is date-bound.

## Current implementation selections (reviewed 2026-10-23)

| Feature | Selected live source | Access and integration | Known constraint |
|---|---|---|---|
| Quran text, source tafsir widget, and Quran-related books index | [Quranpedia API](https://api.quranpedia.net/) | No key; text by selected surah/ayah; source tafsir widget embedded on demand; books through paginated search endpoint. | Live queries only; no bulk mirror. Bibliographic index only, not PDF delivery. |
| Quran search and audio | [Al Quran Cloud API/CDN](https://alquran.cloud/api) | No key; Arabic search uses `quran-uthmani`; audio streams by surah or global ayah through CDN. | Audio is licensed to the provider for free non-commercial redistribution; this app streams it and does not bundle files. Translation editions require individual attribution/rights review. |
| Adhkar and duas | [Islamic Network Pray API](https://islamic.network/api/pray/) | Free/open, no key; paginated `adhkaars`/`duas`/`salawaats`, detail endpoint includes source when known. | Provider disclaims warranties. Use only provided source/attribution; never invent repetition counts. |
| Hadith search | [Dorar Hadith Encyclopedia API](https://dorar.net/api-services/hadith) | Official `GET /api/v1/hadith/search`; Arabic query; Bearer token requested through a manually reviewed application. No key is issued until Dorar approves the request. | Must show Dorar attribution, the supplied grade, and each result's Dorar URL. No scraping, bulk download, or re-hosting. Cache, if added, must remain within the official 24–48 hour limit. Search remains unavailable until an approved token is configured in the admin settings. |
| Lesson player | [YouTube IFrame Player / embed](https://developers.google.com/youtube/iframe_api_reference) | Admin-entered video ID in the official player; no Data API key for embed-only behavior. | Embed and availability remain subject to YouTube policies and video owner settings. Athar comments are first-party, authenticated, and held for administrator review; YouTube comments are not imported. |

Kafela was considered but not selected: a live request on 2026-10-23 returned that its backing D1 database had been deleted. Sunnah.com remains an alternative only if a key and terms are obtained. API-level access being free does not establish rights to all contributed content; preserve per-record source and keep editorial/legal review as a launch gate.

The official Dorar service was selected after the previous no-key index returned no Arabic-query coverage. Access is approval-gated rather than advertised as free. Apply at [Dorar API access request](https://dorar.net/index.php/api-services/request); enter the issued token in the administrator's encrypted service settings after approval. The API route proxies requests server-side so the key is never sent to visitors. Until the approved key exists, the UI reports that search has not been configured.
