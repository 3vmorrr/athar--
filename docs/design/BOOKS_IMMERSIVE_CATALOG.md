# Books immersive catalog

Task: 21.20  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The dedicated \`catalog/books\` route now uses an immersive Books catalog instead of the generic Knowledge list.

The screen keeps the existing \`GET books?q=&page=\` contract and cached/offline behavior owned by \`DataScreen\`; no backend route, ownership rule, download path or reader state was changed.

## Truthful editorial hierarchy

The hero uses the original native \`AtharHeroArtwork.Books\` fallback. Search is a real action and scrolls to the catalog controls, snapping under reduced motion.

Book cards are built only from returned catalog metadata. Required fields are positive id and nonblank title. Author, description, publication year and language are displayed only when supplied by the API. Missing values are omitted rather than invented.

The visual book block is abstract native geometry/iconography and never fabricates a real cover, title or author.

Each card opens the existing \`BookDetailScreen\`, which remains authoritative for rights basis, source URL safety, download permission, durable download state, favorites and the native reader.

The Books tab inside Knowledge routes to this same dedicated catalog so users do not get two divergent book experiences.

## Accessibility and adaptive behavior

The hero inherits Task 21.19 large-text/reduced-motion behavior. Search and pagination controls use minimum 48dp targets. Cards allow multiline title/author and use FlowRow metadata chips so RTL/LTR and larger text do not depend on a fixed horizontal metadata row.

Acceptance remains pending for assemble/unit/test APK/lint/resources plus physical Arabic RTL/English LTR, light/dark, offline/error/retry, download/reader continuity, 200% text, landscape, reduced motion, TalkBack/tablet where available and release-like scroll/memory evidence.
