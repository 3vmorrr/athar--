# Athar Immersive Section Hero

Status: design specification,26 October2026, task21.18. Native implementation is NOT claimed here. Shared implementation belongs to21.19 and screen acceptance to the tasks in [the integration plan](HERO_IMPLEMENTATION_PLAN.md).

## Purpose and integration

Extend Athar's approved premium direction with an original atmospheric hero above a rounded content layer. Keep the current application, roadmap, supplied logo, Arabic-first typography and Material3 design system. This is a presentation refinement, not a replacement for newer functionality or the protected-local-data implementation.

The functional baseline is Codex commit `d9edd6f45f7d3f7c3a2532bf95df26b7c0f52b3f`; the reviewed premium source remains `0e6f4ba6e3bb94075ebc38f1187ec28c7db75d32`. Task21.1 shared components,02.5 Night conflict presentation and02.6 watch refinements are already accepted slices. Preserve them and their tests. Recompare both branches before an overlapping edit. The current Gradle BOM is2025.08.00; this direction does not authorize a library upgrade or an external animation runtime.

Authoritative companions: [brand](BRAND_GUIDELINES.md), [design system](DESIGN_SYSTEM.md), [motion](MOTION_SYSTEM.md), [section language](SECTION_VISUAL_LANGUAGE.md), [hero scroll/motion](HERO_MOTION_AND_SCROLL_BEHAVIOR.md), [artwork library](ARTWORK_LIBRARY.md), [protected recovery](../features/LOCAL_DATA_RECOVERY.md), [captured ownership](../features/OWNED_MUTATION_DISPATCH.md).

## Reference interpretation

Six user-supplied inspiration images inform composition, not production content or asset provenance. They are not copied into the repository or shipped as app images.

| Reference | Adopt as an original Athar pattern | Do not reproduce |
|---|---|---|
|1 Home|Atmospheric top, continuation emphasis, rounded lower layer, editorial sections|Exact illustration, wordmark, fabricated progress or notification dot|
|2 Audio|Clear surah/reciter/player hierarchy and an expandable selection surface|Portrait attribution, exact controls/artwork, unsupported favorites|
|3 Quran hub|A real reading continuation followed by four useful tools|Example reading position, raw background photo, identical tile layout|
|4 Watch|Media priority, scholar/series context, compact discussion and next lessons|Custom controls/scrims over YouTube, invented likes/counts, imported comments|
|5 Reader|Quiet page framing, selected-ayah actions and accessible audio context|Raster/generated Quran text, incorrect reference glyphs, fixed giant page gaps|
|6 Knowledge|Scholarly atmosphere, real continuation, organized learning routes|Unverified portraits, placeholder scholars/series or invented lesson duration|

## Screens affected

Home, Quran hub, Quran audio, Knowledge, Adhkar, Night Prayer, Upcoming/Live and Books receive section-specific atmospheric treatments. Watch uses a provider-safe media top. Quran reading and Tafsir use a compact derivative that prioritizes text. Scholar/series/details inherit the same surface, spacing and status language without requiring a large illustration on every screen.

## Visual principles

- Original layered artwork or permitted real content imagery, a controlled Athar scrim, restrained warm highlights, clear context and one primary action. A flat color alone is not the final immersive treatment.
- Retain green/ivory/copper and dark-theme tonal separation from Theme.kt. Hero-specific tones must be centralized, contrast-tested extensions, not scattered raw colors. A deep Night hero may coexist with the selected Light theme; it does not change the app preference.
- Keep the supplied Athar logo proportional and unchanged. Avoid duplicate wordmarks in both the app bar and hero. Noto Sans Arabic remains UI typography; Amiri Quran remains canonical Quran typography.
- Use the existing4/8dp spacing rhythm, restrained borders and elevation. Normal phone content margins are16dp, section separation24dp. These are starting tokens, not fixed bounds that defeat larger text.
- Prefer an original architectural/geometric scene with depth over ornamental clutter. No random stock background, live blur, moving particles or text baked into artwork.

## Behavior and state

A hero consumes immutable presentation state and callbacks from the owning feature; it does not query repositories or invent a new backend contract. Proposed shared states are loading, ready, empty, offline-ready and error/unreadable. These are presentation mappings, not replacements for ProtectedRead or feature state.

| Actual state | Required treatment |
|---|---|
|Loading|Stable layout and genuine loading feedback; no fabricated continuation/count; no looping pulse when motion is reduced|
|Ready with continuation|Show the real context and a working continuation action|
|Explicit absence|Offer a real exploration/start action; do not label absent data as saved progress|
|Offline with valid saved content|Keep the layered structure/artwork and a localized offline/saved status; restrict actions according to actual feature capability|
|Unreadable retained private data|Keep ciphertext/pending data; explain and expose recovery. Never show zero/completed or enable a resume/write based on guessed defaults|
|Content unavailable or art failure|Use a local visual fallback. A missing image must not destroy functional content; authoritative removal/security rules still take priority|
|Signed out/Pending/suspended|Existing auth gating removes private content immediately. No shared-hero or exit animation across this boundary|

Source/target transition identity includes the feature and actor. No private hero context is retained across account changes. Hero entry, selection or scrolling never creates worship/activity/completion records. Progress is explicit recorded state, not inferred devotion. Shared artwork contains no personal notes or account data.

## Adaptive layout

Use `hero + layered content` as a compositional relationship. Ordinary sections have one main lazy scroll owner. A rounded section can look layered without pretending to be draggable: show a handle only when a real supported expand/collapse interaction exists, with equivalent buttons.

For a normal phone at100-percent text, target roughly180–280dp of expanded hero content/art and expose useful content on the first screen. Treat this as an initial layout budget, not a fixed height or a universal minimum. Reading/landscape/short-height screens prefer a compact header; dedicated expanded audio can use more space when the user explicitly opens it. At150–200-percent text, reduce decorative art first, stack actions, allow text to reflow and let the content scroll. Never clip an action or shrink text to preserve the illustration.

On tablets use available width for content/playlist/tool panes and a balanced hero, rather than stretching a phone poster. Preserve safe drawing/IME areas, existing navigation rail/back behavior and playback identity. No forced portrait page proportions.

## Motion, performance and accessibility

The [motion contract](HERO_MOTION_AND_SCROLL_BEHAVIOR.md) limits reveal, collapse, shared continuity and parallax. All decorative motion obeys existing system/caller reduction. Quran verses and recited words remain static. Do not animate private auth removal or recreate a player to animate its container.

The [artwork budget](ARTWORK_LIBRARY.md) defines local fallbacks, provenance and decode limits. No per-frame image work, whole-screen live blur or many simultaneous animations. Keep animations in UI; feature ViewModels own data and continuity. Existing lazy keys/content types and state/ownership gates remain authoritative.

Every actionable target is at least48dp with localized semantics. Aim for4.5:1 ordinary text contrast,3:1 meaningful icons/large text and strong Quran reading contrast; test the actual image+scrim result. TalkBack gets one useful heading, source/status/action order and an explicit alternative to gestures. Avoid duplicate outgoing/incoming accessible headings. Decorative artwork has no redundant announcement. Mirror navigation semantics for RTL, not sacred text, real photographs or the logo.

## Do and do not

Do preserve current functionality, truthful states, original artwork, a real primary action and native adaptive behavior. Do document an intentionally skipped premium-branch visual when newer logic or viewport safety requires it. Do verify normal and reduced-motion journeys on the authorized phone.

Do not copy reference artwork/layout/branding, fabricate data or controls, add confetti/flames/streak pressure, animate sacred text, hide errors behind art, overlap YouTube controls or describe a static mockup as complete. See [screen acceptance](HERO_IMPLEMENTATION_PLAN.md) before marking an implementing task COMPLETE.
