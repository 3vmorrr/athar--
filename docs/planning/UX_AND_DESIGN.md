# UX and Design System

**Design authority:** the exact Athar color values supplied by the owner are authoritative; do not substitute or sample another palette. The original owner-provided logo remains at `public/assets/images/athar-logo.png`; the header uses a cropped transparent-safe derivative without duplicate brand text. Default theme is light; dark theme is an explicit saved preference. The shared layout includes responsive navigation and a linked site footer.

## Information architecture

- **Public navigation:** Home; Quran; Hadith; Tafsir (contextual from an ayah); Books; Scholars/Lessons; Adhkar; About/Sources.
- **Account area:** Saved Quran, recent reading position, favorite books/series, lesson progress, khatmah plans, preferences, export/delete account.
- **Admin area:** Overview; separate scholar/series/lesson/book/adhkar forms; sources and review status; users/roles; pending-comment review; encrypted SMTP and approved Dorar settings; owner-only backup/restore controls.
- Keep primary mobile navigation to at most five high-level destinations; group secondary public areas under a clearly labeled “More” destination. Preserve browser back/deep links to surah, ayah, book, scholar, series, lesson, and adhkar item.

## Major screens and requirements

| Screen | Main content and interaction |
|---|---|
| Home | Calm purpose statement, prominent Quran/learning discovery, recent/curated content, clear guest entry; no forced registration. |
| Quran index and reader | Surah list/search; compact surah/ayah navigation; readable Arabic text, translation/tafsir source switch where available, reciter/audio controls, bookmark/save state, loading/unavailable source states. Main text column remains readable on desktop. |
| Hadith search/detail | Arabic search using official Dorar endpoint; source text, supplied grading, narrator/reference and Dorar link; clear pending-approval/unavailable/error states. |
| Books browse/detail/reader | Search, filters, cover and bibliographic/rights metadata, allowed read/download action, accessible PDF link/reader state. |
| Scholar/series/lesson | Scholar biography; ordered series and standalone lessons; YouTube-style player with series playlist, title, description and reviewed first-party comments; save/resume only after sign-in. |
| Adhkar | Category/occasion navigation, search, original text/reference/count, keyboard/touch accessible progress control and reset. |
| Account/worship tools | Private saved items, daily aggregate activity, profile name/username, plan state, export/delete and preferences. |
| Admin | Scoped content forms/publication state, SMTP and provider settings without secret echo, comment review, audit history, and owner-only backup/restore flows. |

## RTL and content layout

- Set document language and direction (`lang="ar"`, `dir="rtl"`) for Arabic pages; use CSS logical properties so mixed Arabic/Latin references, numbers, URLs and player metadata remain legible.
- Use semantic headings, landmarks, buttons/links and form labels. Do not encode meaning through left/right or color alone.
- Arabic Quran typeface: choose a well-tested Quran/Arabic font after glyph, diacritic, shaping, licensing, and low-end Android review. Suggested candidates to evaluate: Amiri for Quran text and Noto Sans Arabic or Noto Naskh Arabic for interface/body; these are candidates, not committed dependency choices. Provide robust local/system fallback and avoid blocking first paint on remote fonts.
- Use comfortable Arabic text size and line height (target 1.8–2.0 for Quran body, adjustable), paragraph spacing and selectable text. Keep reading content around 60–75 Latin-character-equivalent width on desktop; Arabic may require visual tuning. Avoid edge-to-edge long lines.
- Layout is mobile-first, fluid, and has no horizontal page scroll. Test widths around 375, 768, 1024 and 1440 px. Preserve video aspect ratio and minimum player viewport per YouTube requirements.

## Semantic color tokens

Tokens should be used by role, not raw per-component hex. Test text contrast for every actual text/background pairing in both themes and states; keep ≥4.5:1 for regular text, ≥3:1 for large text and meaningful non-text controls, and never use color alone for state. User-provided colors are authoritative; where a listed foreground/surface pairing fails contrast, use a compliant alternate foreground from the supplied dark/light text colors or add an opaque surface/outline while retaining the brand role.

| Semantic token | Light | Dark | Use |
|---|---|---|---|
| `--color-page` | `#F5F3ED` | `#20221F` | Page background |
| `--color-surface` | `#FFFEFA` | `#292C28` | Cards, dialogs |
| `--color-surface-secondary` | `#EEECE4` | `#343732` | Secondary panels/input background |
| `--color-text` | `#292B27` | `#F1F0E9` | Primary text |
| `--color-text-muted` | `#74766F` | `#B2B4AA` | Secondary text after pairing check |
| `--color-text-secondary` | `#373A34` | `#B2B4AA` | Accessible secondary text for regular-size labels and supporting copy |
| `--color-border` | `#E4E1D8` | `#42463F` | Dividers and outlines; not sole state signal |
| `--color-primary` | `#26745B` | `#81C5A2` | Main actions/brand emphasis |
| `--color-primary-strong` | `#1C6049` | `#9AD4B5` | Hover/active or emphasis; theme contrast must be checked |
| `--color-primary-soft` | `#E5F1E9` | `#294439` | Selected/success surfaces |
| `--color-focus` | `#23765C` | `#9AD4B5` | Focus ring with offset |
| `--color-danger` | `#B75146` | `#F29A89` | Error/destructive text/icon, with text-safe pairing |
| `--color-danger-soft` | `#F9E9E5` | `#4A302C` | Error background |
| `--color-warning` | `#9B691F` | `#E8C479` | Warning text/icon, with text-safe pairing |
| `--color-warning-soft` | `#F7EFD9` | `#453B29` | Warning background |
| `--color-on-primary` | `#FFFFFF` | `#192D22` | Text on primary action; both listed pairings pass contrast |
| `--color-on-danger` | `#FFFFFF` | `#30201D` | Text on danger action; both listed pairings pass contrast |

White `#FFFFFF` is available for text/contrast where appropriate. Supporting colors for icons/illustration/state details: `#252824`, `#28775D`, `#4B9B70`, `#96978E`, `#9A9B92`, `#9B9C91`, `#C5C4BB`, `#AAA`, `#DCC8A3`, `#5E4930`, `#A4473D`, `#9E443A`, `#373A34`, `#E8F1E9`, `#D6E6D9`, `#2B3A30`, `#3C5144`. Service/account badge colors: `#985A78`, `#3478C9`, `#42966C`, `#397CA4`, `#BF5149`, `#518FA9`; badge text must be contrast-tested independently.

Reserved Memorial colors (`#FBF9F3`, `#394333`, `#5F6558`, `#687653`, `#B39B61`, `#DFD8C5`, `#292D27`, `#F0EDDF`, `#D1CEBF`, `#BDC6A7`, `#C5B47E`, `#4D5145`, `#EFEDE3`, `#353A31`) remain scoped to a dedicated memorial feature if approved; they do not replace Athar’s main palette.

Shadow/opacity tokens preserve owner-provided values `#25291E0B`, `#25291E10`, `#20231B18`, `#0003`, `#15171280`, and transparent `#00000000`. These are alpha-bearing CSS hex forms (8-digit `#RRGGBBAA`, shorthand `#RGBA` where 4 digits); verify browser support and do not interpret alpha as RGB. Prefer semantic `rgba()` equivalent if tooling requires it.

## Interaction and accessibility

- Keyboard order follows visual/reading order; all controls have accessible Arabic labels; focus remains visible and not obscured. Native links/buttons and semantic HTML are preferred.
- Touch controls target at least 44×44 CSS px with spacing. Forms have visible labels, inline errors and clear recovery. Loading, empty, success, offline, and error states explain the next step.
- Search is discoverable and bounded; filter state is URL/share-friendly where appropriate.
- Use restrained 150–250ms transitions only for feedback; no animation across Quran text while reading/listening. Respect `prefers-reduced-motion` by removing nonessential motion; do not delay playback or controls.
- YouTube iframe loads on user intent/visible viewport where practical, never hidden autoplay; respect branding, native controls, ads, consent/privacy requirements, and player restrictions.

## Contrast verification note

Palette roles are documented exactly as supplied. WCAG relative-luminance contrast was calculated for representative text/action pairs on 2026-10-23. Primary text passes on page/surface in both themes (light 12.88:1 and 14.16:1; dark 14.03:1 and 12.38:1). Dark muted text on dark page passes at 7.64:1. Light muted text fails regular-text contrast on page (4.15:1) and secondary surface (3.89:1), so use primary text or the accessible secondary token `#373A34` for small/body labels on those backgrounds; reserve `#74766F` for large text or nonessential decoration after checking its actual pair. White on light primary green is 5.63:1, white on light dark-green is 7.45:1, white on light red is 4.91:1, and white on light amber is 4.74:1. Dark-theme primary text on dark-green background passes at 9.28:1; dark foreground `#192D22` on green `#81C5A2` passes at 7.25:1. Do not use dark primary text on light green (`#292B27`/`#26745B` is 2.54:1), dark text on dark amber (`#292B27`/`#9B691F` is 3.02:1), or the danger text pairing `#292B27`/`#B75146` (2.91:1) for regular labels. Contrast varies with exact surface; recheck all actual states, badge text, focus indicators and non-text controls during implementation. Borders alone must not communicate state.

## Inspiration

Use [Hairline](https://hairline.lucasmarkes.com/) only as inspiration for restrained movement and polished feedback. Do not copy its layout, assets, or brand identity. No screenshots or assets from that site are part of this plan.
