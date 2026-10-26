# Tafsir source selection and reading

Task: 03.3  
Implementation branch: `parallel/admin-foundation`  
Status: **IMPLEMENTED / RUNTIME ACCEPTANCE PENDING**

## State separation

The previous Tafsir screen reused one generic data document for both the book
catalog and the selected book content. After content loaded, reopening the source
picker could therefore interpret tafsir content rows as source rows.

The screen now owns a dedicated ViewModel with two independent states:

- catalog page/query/offline/error state;
- selected-book reading page/offline/error state.

Both states continue to use the existing encrypted, owner-scoped
`ContentRepository`; no second cache is introduced.

## Provider contract

Quranpedia responses now expose the same explicit pagination object for book
catalog and content:

`page`, `per_page`, `total`, `pages`, and `has_more`.

Provider page/per-page parameters are strictly positive and bounded. Content is
still sanitized to plain text before it reaches Android, source URLs are
allowlisted, and malformed provider responses fail closed.

## Native UX

- Surah/ayah input uses the same mixed Arabic/Western digit normalization as the
  Quran navigation work.
- The currently saved reading position is used as the initial Tafsir reference.
- Source search and source paging live in an adaptive bottom sheet rather than a
  fixed-height alert.
- Reading content has explicit previous/next paging, total/page labels, empty
  state and source attribution.
- Cached offline content is clearly labeled.
- A source failure with no cache gets an explicit unavailable state and manual
  retry; an already-rendered page is retained if a later page request fails.
- No automatic destructive retry loop is introduced.

## Verification included

- PHP provider assertions cover catalog paging/search, content paging,
  sanitization, canonical boundaries, source attribution and malformed upstream
  payloads.
- Android contract tests reject inconsistent pagination, untrusted source URLs
  and malformed content.

Do not mark 03.3 COMPLETE until the integration lane runs the repository's PHP,
Gradle, Android test, lint and resource gates plus Arabic RTL and English LTR
API27 device journeys, offline cached/first-load failure, unavailable/retry,
source search/paging, large-text/landscape, accessibility, diff/secret review
and exact remote-SHA verification.
