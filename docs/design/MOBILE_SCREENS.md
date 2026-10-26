# Mobile information architecture

## Additive immersive section refinement

The approved direction is [section visual language](SECTION_VISUAL_LANGUAGE.md), with exact task order in [HERO_IMPLEMENTATION_PLAN](HERO_IMPLEMENTATION_PLAN.md). Preserve the existing home/quran/knowledge/adhkar/account navigation and genuine detail routes. Start with Home, Quran, Night, Watch, Audio, Upcoming and Adhkar in the visual phase; Books/Knowledge follow as independent units. Screen designs/interactions/device gates must be implemented and verified; reference images and these documents are not completed screens.
Entry graph: splash → first-launch onboarding → auth/verification/recovery → authenticated graph. Main destinations: Home, Quran, Knowledge, Adhkar, Account. Night Prayer has prominent home/Quran links and a nested destination; it does not become a sixth crowded navigation item.

Quran branches into reading, listening, tafsir, search and local review before opening scope-specific screens. Knowledge branches into scholars/series/lessons/books/Dorar. Details provide source, favorites, related content and clear return behavior. Account sections isolate profile/security/preferences/progress/khatmah/privacy. Compact layouts use bottom navigation; wider windows use rail and optional master/detail. Every graph has back handling, deep-link validation, saved state and authentication boundary tests.
