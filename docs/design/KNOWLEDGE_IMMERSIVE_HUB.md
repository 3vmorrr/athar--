# Knowledge immersive hub

Task: 21.21  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Knowledge now uses a dedicated immersive hub instead of the compact generic catalog shell.

## Real continuation

The hub reads the encrypted owner-scoped lesson-progress cache and selects the most recently updated lesson that is both started and incomplete. It does not use highest percentage, highest id, an app-open event or inferred interest.

When that real local progress exists, the screen attempts to resolve the existing published lesson detail through the current ContentRepository. If available, the hero may show the returned lesson title, series title and scholar name. The series CTA exists only when the returned lesson has a positive series id.

If detail is unavailable offline, the real lesson id still powers Continue while the hero uses generic saved-progress copy. No title, scholar or series is fabricated.

If encrypted lesson progress is unreadable, the hero presents the protected-data warning instead of treating it as no continuation.

## Editorial hierarchy

The hero uses the original native Knowledge artwork and one main LazyColumn. The lower layered surface contains real Lessons/Series/Scholars/Books navigation plus search.

Books routes to the dedicated Task 21.20 catalog. Other cards parse only fields returned by their existing catalog endpoints and open the existing lesson/series/scholar routes.

When no continuation exists, the primary action becomes Explore lessons and scrolls to the exploration controls; reduced motion snaps instead of animated scrolling. The secondary action opens the real Upcoming surface.

## Ownership and contracts

No backend route or progress schema changed. LessonRepository gained only an owner-verified read helper that preserves each cached row's existing \`updatedAt\` so the UI can choose the latest truthful incomplete checkpoint.

No progress write, completion event, favorite, follow or activity event is created by rendering or scrolling the hero.

Acceptance remains pending for assemble/unit/test APK/lint/resources plus physical Arabic RTL/English LTR, light/dark, real continuation, absent continuation, unreadable/offline/error/retry, 200% text, landscape, reduced motion, TalkBack/tablet where available and release-like scroll/memory evidence.
