# Immersive hero implementation status

Task: 21.19  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The shared native hero layer now implements the reusable contract defined by Task 21.18 without changing feature repositories, authentication, provider playback or backend APIs.

## Original fallback registry

\`AtharHeroArtworkRegistry\` provides stable IDs for the original native Canvas scenes:

- \`hero.quran\`
- \`hero.knowledge\`
- \`hero.adhkar\`
- \`hero.night\`
- \`hero.books\`
- \`hero.audio\`
- \`state.empty\`
- \`state.offline\`
- \`state.error\`

Home, Upcoming and Generic use the neutral Knowledge geometry as their local fallback. Every descriptor is local/native, network-independent, contains no baked text or people and is not automatically mirrored. The registry describes fallback artwork only; it does not claim a raster resource or external asset license.

State fallbacks are intentionally neutral: empty uses a quiet path/open-space motif, offline uses muted connection geometry and error uses restrained layered panels. None contains sacred text, guilt/streak imagery or identity claims.

## Adaptive layout policy

\`AtharHeroLayoutPolicy\` is a pure policy shared by the composable and JVM tests.

- normal phone layout keeps the expanded art treatment;
- narrow widths stack CTA controls;
- 130%+ text stacks actions and expands text allowance;
- 160%+ text removes decorative artwork first and removes layered overlap;
- collapsed heroes use a compact height/title budget;
- titles retain multiple lines instead of being forced to one line at large text.

Primary and secondary actions enforce a minimum 48dp target.

The compact header follows the same large-text rule: at 130%+ it becomes a vertical layout instead of squeezing status/action beside the title.

## Motion and scroll

The hero still consumes a caller-owned collapse fraction and creates no second scroll owner.

The decorative entry reveal is now the documented one-shot 1.03→1.00 scale keyed by the optional feature/actor \`transitionKey\`. Reduced motion snaps directly to the final state. Collapse parallax remains at the documented 8dp maximum and is converted to physical pixels through Compose density instead of using an accidental raw-pixel value.

No sacred text is animated. No auth transition is wrapped or delayed. No player/service state is recreated.

## Layered content

The lower content surface remains a static rounded layer with no fake grip. At 160%+ text its negative overlap is removed so decorative layering cannot consume usable text/action space.

Acceptance remains pending for build/JVM/test APK/lint/resources and the required Arabic/English, light/dark, 200% font, landscape, reduced-motion, TalkBack/tablet where available, and physical release-like scroll/memory measurements. Screen-specific adoption remains owned by the later Work Package 21 tasks.
