# Stitch UI migration status

Branch: `chatgpt/stitch-ui-integration`
Base: `preview/athar-ui-showcase@12f82f19610d9a4d68250ad93d78af7dbcfa6412`

This file tracks **actual code/UI migration state**, not planned task counts.

## Status meanings

- **BASELINE PRESENT**: native feature/screen exists before Stitch migration.
- **MIGRATED**: Stitch composition has been applied without replacing real behavior.
- **PARTIAL**: some Stitch structure is applied or the functionality exists but the dedicated surface is missing.
- **GAP**: no equivalent dedicated native screen exists yet.
- **VARIANT**: visual state/theme variant, not a separate feature.

## Screen matrix

| ID | Screen | Baseline | Migration |
|---|---|---|---|
| A01 | Splash | BASELINE PRESENT | queued |
| A02 | Onboarding | BASELINE PRESENT | queued |
| A07 | Login | BASELINE PRESENT | queued |
| B01 | Home | BASELINE PRESENT | **MIGRATED checkpoint 1** |
| B01 dark | Home night variant | VARIANT | theme adaptation queued |
| C01 | Quran Hub | BASELINE PRESENT | queued |
| C04 | Mushaf Reader | BASELINE PRESENT | queued |
| C04 dark | Mushaf night reading | VARIANT | queued |
| C11 | Tafsir | BASELINE PRESENT | queued |
| D02 | Audio Player | BASELINE PRESENT | queued |
| E01 | Adhkar Hub | BASELINE PRESENT | queued |
| E06 | Dhikr Reader/Counter | BASELINE PRESENT inside module | queued |
| E06 dark | Night dhikr | VARIANT | queued |
| F01 | Knowledge Hub | BASELINE PRESENT | queued |
| G01 | Lesson Watch | BASELINE PRESENT | queued |
| I01 | Books Catalog | BASELINE PRESENT | queued |
| I04 | Book Detail | BASELINE PRESENT | queued |
| J01 | Night Prayer Hub | BASELINE PRESENT | queued |
| J01 dark | Night Prayer dark | VARIANT | queued |
| K01 | Khatmah Planner | BASELINE PRESENT | queued |
| L01 | Global Search | **GAP dedicated screen** | queued after API/index contract |
| M01 | Account Overview | BASELINE PRESENT | queued |
| M05 | Downloads/Offline | PARTIAL distributed infrastructure | dedicated surface queued |
| M08 | Reading/Appearance | PARTIAL embedded settings | dedicated surface queued |

## Completed in checkpoint 1

- Created authoritative Stitch-to-Compose implementation specification.
- Preserved official Athar palette; generated Stitch palette cannot override brand source of truth.
- Preserved official Android logo resource.
- Added screen-specific hero shape support without changing existing callers.
- Home hero now uses the approved compact margin/rounded composition.
- Home Quran continuation now reads the encrypted real saved reading position.
- Quran preview text comes from the canonical bundled Quran dataset.
- No hardcoded fake Quran position, progress percentage, location, prayer time, or religious attribution was added.
- Quick-action order now follows the approved Home hierarchy: Quran, Night Prayer, Adhkar, Knowledge.
- Added a branch-only GitHub Actions Kotlin compile gate.

## Home checkpoint deliberately not implemented yet

These Stitch details are **not** hardcoded because the current Home contract does not provide verified values:

- city/location badge;
- Hijri/time-context badge;
- last-third start time;
- 75% Quran progress bar;
- daily dhikr 18/24 card;
- attributed daily benefit/quote.

They will only appear after being connected to their real repositories/contracts. This is intentional correctness, not missing polish.

## Next code checkpoint

1. Verify Home compile gate.
2. Bring the top Home app bar closer to the Stitch composition while preserving navigation and accessibility.
3. Quran Hub + Mushaf migration.
4. Adhkar + Night Prayer.
5. Learning/Watch + Books.
6. Khatmah/Account/offline/settings.
7. Implement missing global search after a real aggregate-search contract is defined.

## Merge rule

Nothing from this branch should be merged into a Codex integration branch merely because the visual diff looks correct. The corresponding compile/test/device gate must be recorded first.
