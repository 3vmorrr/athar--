# Athar design system
Source of truth: CSS tokens in `public/assets/css/app.css`. Light: primary `#24694F`, strong `#19563F`, page `#F4F3ED`, surface `#FBFCF8`, raised `#FFFFFF`, text `#202923`, border `#D9E2D9`, copper `#A85F25`. Dark: page `#101815`, surface `#17231E`, raised `#1E2D26`, secondary `#283A31`, primary `#8ADBAF`, text `#F2F6F1`, border `#40564A`, accent `#F0BD78`. Danger/warning remain semantic, not spiritual judgments.

Use a 4/8 dp spacing rhythm, 16 dp mobile margins, 24 dp section gaps, 48 dp touch targets, readable 16 sp body and distinct medium/bold hierarchy. Cards use restrained borders/radii/elevation. Quran uses licensed Amiri Quran with generous line height, no letter spacing, clipped diacritics or width placeholders that leave giant gaps. UI uses licensed Arabic family; system Latin fallback is permitted.

Reusable states: inline labeled field errors; one global mutation message; pending/empty/offline/retry panels; skeletons only while genuinely loading. Icons have accessible labels, selected states use shape/text plus color, charts expose numeric descriptions. Focus rings and TalkBack order are release requirements. Dynamic scale and tablet landscape must not clip actions.

## Native implementation increment — 2026-10-25

`android/.../core/ui/Theme.kt` defines all Material surface/container/inverse/secondary/tertiary/error roles explicitly for both themes. Primary and copper accents remain the established brand colors; additional teal roles support informational emphasis. Default Material purple surface roles are not inherited. Noto Sans Arabic is bundled with its OFL license for UI, while Amiri Quran remains dedicated to original Quran text. Interface icons are the selected official Apache-licensed Material Symbols converted to native vectors with a source/hash manifest.

The home screen uses adaptive content columns, actual published catalog data, private activity totals and direct Quran/Night Prayer/adhkar entry points. Shared bookmark/favorite toggles provide immediate optimistic state, localized semantics, subtle haptics and error rollback. Motion values and reduced-motion behavior are documented in [MOTION_SYSTEM.md](MOTION_SYSTEM.md). Compiling these components does not replace phone/tablet, font-scale and TalkBack acceptance.
