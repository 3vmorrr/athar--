# Athar Premium Design System

This specification supersedes outdated visual portions of older design plans. Functionality, accessibility, data ownership, tests, and provider requirements remain authoritative. Runtime implementation lives in the existing `core/ui` theme/components; this is not a second UI framework. Source traceability: [research](PREMIUM_SOURCE_RESEARCH.md). Installed guidance: [skill manifest](../engineering/PREMIUM_SKILLS.json).

## Tokens and technical rationale

NIA supplies organization and hierarchy; Athar retains green/ivory/copper. Semantic pairs use primary/onPrimary, primaryContainer/onPrimaryContainer, secondary/onSecondary, tertiaryContainer/onTertiaryContainer, surface/onSurface, surfaceContainerLow, surfaceContainerHigh, onSurfaceVariant, outline/outlineVariant, and errorContainer/onErrorContainer. Hero foreground/scrim and audio accent are centralized. Glass is a tonal floating surface with edge highlight; solid rendering is the API27-compatible default. Cover art never determines readable foreground colors.

Every UI role uses bundled Noto Sans Arabic, zero tracking, and scalable sp. These are Athar decisions adapting Material's role structure, with extra Arabic line-height room:

| Role | Size / line height sp | Weight | Use |
|---|---|---|---|
| displayLarge / Medium / Small | 48/68, 40/56, 34/48 | 600 | Rare editorial identity, wide screens |
| headlineLarge / Medium / Small | 30/44, 26/38, 23/34 | 700,700,600 | Screen/hero headings |
| titleLarge / Medium / Small | 20/32, 17/28, 15/26 | 600 | Sections, cards, compact identities |
| bodyLarge / Medium / Small | 16/28, 14/25, 12/22 | 400 | Reading support, copy, metadata |
| labelLarge / Medium / Small | 14/24, 12/22, 11/20 | 600,500,500 | Actions, navigation, captions |
| Quran | User setting; existing roughly 1.95× line height | Amiri Quran native | Actual Quran only; preserve RTL/text/bismillah |

Arabic and English share hierarchy and responsive layout; do not force English letter spacing onto connected Arabic. No fixed text container heights. Start/end layout follows locale, numbers/clock strings retain appropriate direction. Large-text layouts stack actions and allow content to scroll.

Spacing is 4/8/12/16/20/24/32dp. Screen gutter 20dp on phone; max-width content and wider gutters on large windows. Cards use 16/20dp padding, sections 24/32dp gaps. Existing shapes: 8/12/18/24/28dp; use medium for dense cards, large for feature cards, extraLarge for floating surfaces/sheets. System/keyboard insets are consumed once. Material Symbols: 24dp default, 20dp metadata, 32dp player; all controls ≥48dp. Outline icons are consistent; the native selected indicator and semantics identify active navigation. Only directional arrows mirror.

## Motion decisions

| Pattern | Trigger / start → end | Spec | Interruption / reverse | Reduced motion / cost |
|---|---|---|---|---|
| Press | touch 1 → .97 scale | existing small spring 620/.88 | native interaction release retargets | scale 1; draw-only |
| Navigation selection | selected 1 →1.08 icon | same small spring | retarget | fixed icon; native indicator |
| Peer destination | selected tab → target content | 220ms enter /140ms exit | NavHost owns overlap/reverse | immediate |
| Hero reveal | stable identity 1.03→1 artwork | 220ms Enter | identity-scoped, no replay on ordinary recompose | final artwork; graphicsLayer |
| Cover to detail | same book ID, list rectangle → detail rectangle | 320ms Standard | shared scope returns to source, fast back interrupts | no shared modifier |
| Player expansion | mini player action → expanded controls | navigation fade; shared playback state | Back restores prior destination | immediate; no duplicated engine |
| Loading/progress | actual loading/position | native indicator; stable timeline | actual state can interrupt | truthful static state, no fabricated percentages |

No scroll/reorder animation on Quran text. No permanent decorative motion or backdrop blur. Debug testing validates behavior, not release frame performance.

## Audit and screen blueprints

The source has ten route patterns plus feature-internal modes, rather than an invented set of screens. Shared shell: compact five-destination bottom bar, expanded/short-window rail; title/back bar; persistent service-backed mini-player when media exists. Portrait design baseline 360×800dp; 320dp/split-window and larger fonts must scroll/stack. Wide content is bounded. Every row below inherits semantic colors/type/spacing, localized native controls, RTL order, safe areas, and loading/error/retry from the owning state. A UI identity change must not replace repositories or business logic.

| Actual screen / source | Ordered layout and component mapping | Controls/states/motion and preservation |
|---|---|---|
| Onboarding / `auth/OnboardingScreen.kt` | Logo/skip → illustration pager → headline/body → progress/continue | Existing four pages, accessible step, reduced-motion pager; final callback unchanged |
| Login, register, reset / `auth/AuthScreens.kt` | Bounded brand identity → screen title → current form → feedback/primary action → mode links | Preserve password managers, validation, email workflows, no animated auth leakage |
| Verification / same file | Brand/title → pending-email instructions → code form/feedback → verify/resend/cancel | Preserve real pending state and rate limits |
| Home / `core/ui/HomeScreen.kt` | Prominent native hero → layered quick actions → upcoming → real activity metrics → latest lessons → books | Real account data only; own existing dirty work preserved; catalog and detail actions maintained |
| Knowledge: lessons/series/scholars/books / `core/ui/MainShell.kt` | Compact editorial identity → upcoming link → category selection/search → image/identity/content rows → paging | Real query/page/state; books get cover identity; empty/error explicit, stable item IDs |
| Book detail / same file | Cover/title/author identity → real description → permitted download → favorite | Shared cover ID if a matching source exists; no invented in-app book reader/progress |
| Quran tools / `quran/QuranScreen.kt` | Compact Quran identity → read/listen/tafsir/radio action rows | All four modes preserved, standard icon family |
| Quran reader / same file | Saved-position/conflict → read/search/font controls → section selector → verses/actions | No decorative hero in reading measure; preserve exact text/scroll/bookmarks/local unreadable recovery |
| Tafsir / `quran/TafsirScreen.kt` | Title/selector → edition → verse → explanation | Preserve provider selection/paging/font controls; calm long-form measure |
| Audio / `media/AudioScreen.kt` | Audio identity → actual now-playing artwork/title → progress/controls → reciter/edition/surah/ayah selection → follow-along | One Media3 state, actual seek/speed/repeat capabilities, loading/error, clipped ayah preserved |
| Radio / same file | Radio identity → live controls when playing → real station list | Never show fake duration or seek for live stream |
| Adhkar / `adhkar/AdhkarScreen.kt` | Compact identity → category strip → item position → formula/counter/source → next/restart | Existing offline owned progress and non-fixed-count behavior; no changed Quran font/formula |
| Night Prayer / `nightprayer/NightPrayerScreen.kt` | Compact identity → history/calendar/tools modes → actual state | Preserve protected reads, no invented piety scores |
| Night history / same file | Summary/actions → retained-state warnings → session list → add/edit/recovery dialogs | All conflict/rejection/undo semantics remain, real counts only |
| Night calendar / `NightCalendarScreen.kt` | Month navigation → totals/privacy → grid or accessible agenda → selected-date entries | Existing large-font narrow fallback, real exclusions, no timed date motion |
| Night tools / `NightToolsScreen.kt` | Calculator → location/method/timing → remaining tools | Fields/real calculations remain, no promised reading times |
| Watch / `learning/WatchScreen.kt` | Provider viewport → lesson identity/actions → details/series/discussion | No overlays covering player, progress provider remains truthful, fullscreen/back unchanged |
| Series / `learning/SeriesScreen.kt` | Compact content identity → favorite/scholar/progress/description/resume → lesson playlist/paging | Actual completion/resume data, same IDs and commands |
| Scholar / `learning/ScholarScreen.kt` | Portrait/name identity → biography/source → series/standalone selector → cards/paging | Only existing safe image path and source URL; own pagination per mode |
| Discussion / `learning/DiscussionPanel.kt` | Existing comments/selected lesson → reply form → moderation/feedback | Preserve permissions/reply and paginated content, no visual proxy for submission |
| Upcoming list/detail / `learning/UpcomingScreen.kt` | Compact calendar identity → real event cards → time/provider/state → real join/watch action | Expired/cancelled/live/offline states preserved; countdown only actual event state |
| Account/profile/settings / `core/ui/MainShell.kt` | Account modes → profile fields/save → language/theme → security → signout/delete | Functional state untouched; no account data in shared screenshots |
| Khatmah, bookmarks, favorites, export / `AccountToolsScreen.kt` | Existing selected-tool identity → data/controls → pagination/export feedback | Preserve ownership, private retention/error/retry; no fake progress |
| Dialogs, sheets, empty/error/loading / shared and feature files | Existing Material overlays plus Athar theme; icon/state/heading/body/retry hierarchy | Native semantics, recoverable state, no false empty when protected data is unreadable |

Planned but absent: in-app book reading, audiobook catalog/queue, arbitrary lecture audio, and broader OS acceptance. Their requirements remain planned rather than being fabricated for this milestone.

## Work packages / acceptance

PUI1 installs/pins guidance and preserves checkpoint; PUI2 completes tokens/shared components/navigation; PUI3 content/library/cover continuity; PUI4 Quran/reading/adhkar; PUI5 service-backed mini/expanded audio; PUI6 prayer/calendar/learning/account consistency; PUI7 builds/tests/device evidence and visual review. These packages change execution order; they do not close functional tasks. Required acceptance and outstanding matrices are recorded in `docs/progress/PREMIUM_UI_MILESTONE.json` and the device review report.
