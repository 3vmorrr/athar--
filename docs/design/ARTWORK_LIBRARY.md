# Athar original artwork library

Status: specification, task21.18. No new image/vector resources are claimed as implemented. [HERO_ASSET_SPEC.json](HERO_ASSET_SPEC.json) is a planned-asset specification, not an inventory of generated artwork. Shared local fallbacks/registry belong to21.19; section artwork is accepted with its actual screen task.

## Purpose and original visual treatment

Create coherent, reusable, lightweight original Athar scenes: layered architectural/geometric depth, warm light and a controlled emerald/teal treatment. Images are decorative scenery; real titles, Quran text, names, progress and status remain native accessible text. Use the supplied logo unchanged from [brand guidance](BRAND_GUIDELINES.md); never generate a substitute logo.

Six user inspiration images are conceptual references only. Do not extract or redistribute their artwork, portraits, Quran pixels, branding or exact compositions. Do not use random stock in the final product. An original native vector/Canvas scene is an acceptable lightweight fallback; it must have meaningful depth/layers, not merely a flat colored card.

## Planned reusable directions

| Asset ID | Original scene / treatment | Temporary/final fallback rule |
|---|---|---|
|hero.quran|Quiet arch, gentle geometric light, warm emerald; no cluttered Mushaf photograph or readable glyphs|Original arch/geometry with native scrim/context|
|hero.knowledge|Warm abstract library/arch with anonymous blank book spines|Original shelf/light geometry; no invented scholars or book text|
|hero.adhkar|Quiet dawn light, restrained organic/geometric detail|Original dawn layers; no streak/flame symbols|
|hero.night|Deep emerald/blue-green sky with subtle original silhouette|Original night/arch/silhouette, static highlight|
|hero.books|Original book composition or permitted real catalog covers|Original blank-spine book scene; never fabricate a real title/author/cover|
|hero.audio|Original calm audio/architectural composition|Original static wave/arch geometry or initials; no unverified reciter portrait|
|state.empty|Neutral open space/path motif with a useful real next action|Original quiet geometry, no spiritual judgment|
|state.offline|Muted scene with clear native saved/offline status|Bundled original motif; core hero does not require a network image|
|state.error|Neutral layered surface with readable retry/context|Original minimal geometry; no alarming sacred imagery|

No actual resource filename/hash is assigned until a resource exists and is reviewed. While21.19 is not implemented, the current tested premium surface remains a temporary existing fallback; it does not count as immersive-hero acceptance.

Home can reuse the appropriate original Quran/Knowledge/Night scene for its real context, with Knowledge's neutral architecture as a default. Upcoming and Watch use actual permitted thumbnails when available, otherwise the Knowledge scene. Compact reading/Tafsir inherit Quran's scene treatment without a large image. These are planned consumer mappings, not additional shipped resources.

## Provenance and content rules

Every accepted asset must record its ID, actual resource/path, SHA-256, dimensions/encoded bytes, author/source, rights/license or creation provenance, palette treatment, focal/safe zones, text/sacred-text flags and whether mirroring is safe. Original AI-assisted raster artwork, if used later, records generation/edit provenance and is inspected for accidental lettering/religious-text corruption. No paid runtime or remote image-generation dependency is introduced into the app.

Use actual permitted catalog covers/thumbnails/portraits only when their identity and source are reliable. Do not infer a reciter's identity from the references or assign a generic person's face to a named scholar. Do not mirror real portraits, real sites or readable covers automatically for RTL. Original abstract geometry can have explicitly approved mirrored variants. Never bake account names, private notes, times, progress or Quran text into art. Hero content must remain correct without the picture.

## Performance budgets

Initial engineering budgets, to be measured rather than described as achieved:

- Core original scenes/fallbacks are local; no external stock/CDN dependency for startup/offline structure.
- Prefer a lightweight native vector/geometry scene. For a phone raster variant, target at most160KiB encoded and a requested decode around768×480 or smaller for the actual view; avoid decoding a full-resolution poster.
- Target at most2MiB decoded artwork for the visible phone hero and4MiB total resident hero artwork. Reuse/cancel requests and release offscreen heavy art. Tablet requests scale to actual bounds and require a separately measured budget, initially4MiB for one hero.
- At most one dominant illustration and a small number of cheap scrim/gradient layers. No animated image loops, live blur, per-frame color filters or image decoding.
- Reserve layout space; if art fails, render the local fallback without moving important controls or triggering endless retry. Treat these as art budgets, never as limits that clip200-percent text.

Coil3.3.0 is already present for images. Reuse existing infrastructure and lifecycle; do not add another loader/animation package merely for heroes. Downloaded book/media ownership and private retained data are not disposable hero-image cache.

## Accessibility, motion and review

Decorative artwork is omitted from redundant TalkBack announcements. Actual cover/portrait meaning is conveyed by the nearby native title/author; avoid duplicate labels. Validate actual image/scrim contrast in both themes and crops. Static/reduced-motion art keeps the same information/actions. Never animate canonical verses or readable sacred text contained in an image.

Review asset provenance, absence of accidental text/identity claims, visual consistency, crop/safe zone, RTL/LTR, small/large displays, low-memory/offline/error fallback and actual target-device scroll. A planned asset or attractive standalone render is not an implemented native screen.
