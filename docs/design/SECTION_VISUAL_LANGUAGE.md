# Athar section visual language

Status: specification for task21.18, not an implemented screen inventory. Apply with [Immersive Section Hero](IMMERSIVE_SECTION_HERO.md), [artwork](ARTWORK_LIBRARY.md) and [motion/scroll](HERO_MOTION_AND_SCROLL_BEHAVIOR.md). Defaults remain Arabic, RTL and Light; explicit English is LTR and Dark remains optional.

## Purpose and editorial structure

Each destination should feel related to Athar while expressing its content. Use a meaningful title, one short supporting context line, a real continuation/exploration action and a readable transition into the lower content surface. Avoid repeating the heading as several descriptions. Secondary sections use title/support/action hierarchy, not more full-size heroes.

The current routes remain the foundation: home, quran, knowledge, adhkar, account, night-prayer, upcoming, catalog/books and content lessons/series/scholars/books. Audio/Tafsir currently open from Quran's selected mode. Proposed visuals do not invent new routes or persistence.

## Section treatments and data binding

| Section / task | Visual context | Truthful content and primary action | Lower content |
|---|---|---|---|
|Home21.2|Original warm emerald architecture, modest depth|Arabic greeting/context where known; actual reading/lesson continuation, otherwise exploration. No fake unread badge|Useful quick actions, genuine next event, published recent lessons/books and owned activity where available|
|Quran hub21.4|Calm arch/geometric light, warm green, uncluttered|القرآن الكريم / Quran; real protected reading position and متابعة القراءة / Continue reading only when readable. Otherwise browse/recovery|المصحف / Read, الاستماع / Listen, التفسير / Tafsir, الإذاعة / Radio, genuine saved references|
|Night Prayer21.8|Original deep emerald/blue-green night scene; subtle silhouette|قيام الليل / Night Prayer. Show last-third/countdown only from validated current date, zone, location/method context. ابدأ جلسة / Start session only when the timer journey exists; current manual logging can offer تسجيل جلسة / Log a session|Goal/history/calendar/tools based on real records; local-only exemptions stay local|
|Watch21.9|Provider-safe media first; branded context around the video|Actual lesson/scholar/series/title/progress. Official player remains the top priority|Compact first-party comments, description, actual ordered current/next lessons and lazy playlist|
|Audio21.5|Original audio atmosphere or verified permitted reciter imagery|Actual selected surah/reciter/read mode and actual playback state/duration; native play/pause/seek actions|Real reciter/surah/mode lists in accessible selection surfaces; no invented portraits or favorites|
|Upcoming/Live21.13|Permitted event thumbnail or original learning art|مباشر الآن / Live now only when fresh authoritative state says live. Otherwise next actual event; cached status stays saved/offline|Working watch/reminder actions only when supported, real schedule/context/related book|
|Adhkar21.7|Quiet original dawn scene, current category context|Active category, readable persisted counter state and actual progress; no fabricated aggregate or completion|Existing sequential item/counter/reset/next flow, source/formula readability|
|Books21.20|Real permitted covers or original Athar book composition|Actual book/catalog context and functional browse/continue/download action|Rights/metadata/download/reader state and genuine catalog rows|
|Knowledge21.21|Original warm library/arch atmosphere|العلم / Knowledge; actual last lesson/series when reliably available, otherwise explore|Actual lessons/scholars/series/books navigation and editorial content, with existing pagination|

Reference example surah positions, lesson names, timestamps, portraits and counts are not seed data. A continuation cannot be inferred from an unordered set of checkpoints. If an aggregation does not exist, the hero uses a truthful general destination until its feature task provides one.

## Reading, Tafsir and detail variants

Quran reading uses a compact atmospheric header above a quiet, legible page. Optional lightweight non-text framing must preserve canonical verse text, numbers, shaping, line height and responsive flow. Never use reference pixels/generated text as the Quran source; never insert huge fixed blank page heights or word widths. A selected ayah action surface must stay reachable without hiding the selected content unnecessarily. Tafsir shares the compact language and prioritizes explanation text.

Scholar/series detail pages may use permitted actual imagery, clear title and real content counts/progress. No biography or portrait is fabricated. Discussion remains first-party Athar data with actual moderation/reply support; reference hearts/attachment controls/counts do not authorize fake features.

## Material and typography

Keep Theme.kt semantic roles and the established brand palette. A local deep hero can use a reviewed inverse/brand tonal treatment, with a stable readable scrim. Use warm secondary highlights sparingly; surrounding Light surfaces remain light and Dark surfaces remain distinguishable. Do not introduce per-screen raw hex themes or make Night Prayer change the application preference.

Use Noto Sans Arabic UI roles, deliberate regular/medium/semibold hierarchy and the existing Arabic line spacing. Quran remains Amiri Quran, without letter spacing or transformed glyphs. English controls are localized; Arabic religious source text retains its correct writing direction inside LTR UI. Do not mirror real photographs, readable book covers or the supplied logo to achieve RTL.

Use current AtharIcons/Material Symbols consistently, not reference stickers or emoji as controls. Keep48dp targets even for24dp visual icons. One primary CTA is prominent; additional actions are quieter and scroll/wrap appropriately. Text growth takes priority over image size.

## State, accessibility and ownership

Use the same status vocabulary across hero and content: genuine loading, explicit empty, saved/offline, error/retry and protected unreadable. State is communicated with text/shape as well as color. Never convert an unreadable retained reading/counter/lesson/session into zero. A malformed account response is not an empty list or editable zero plan. Auth/owner changes remove private content immediately, not through a graceful hero exit.

In TalkBack order, announce the section heading, useful context/status and primary action, then lower content. Do not announce decorative scenery or duplicate the same title as image alt text. A visual handle requires an actual draggable surface and accessible expand/collapse/close alternatives; a static rounded content layer has no fake handle.

## Performance, motion and acceptance

No animated backgrounds, live blur, bouncing verses, looping Quran or worship celebration. Use only the bounded motion contract and local artwork budgets. Reuse feature/player state rather than reloading on scroll or sheet expansion. At reduced motion, static/compact alternatives retain identical content/actions.

Each screen must pass Arabic RTL, English LTR, Light/Dark, readable large text, phone/landscape/tablet, loading/empty/error/offline, protected data, reduction and actual target-device scroll/playback tests. Screenshot resemblance alone is not acceptance. See [the implementation queue mapping](HERO_IMPLEMENTATION_PLAN.md).
