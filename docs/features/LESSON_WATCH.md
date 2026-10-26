# Native lesson watch increment
Status: IN_PROGRESS. Implements the2026-10-25 watch-screen requirements on the existing Kotlin/Compose/shared PHP foundation.

Dependencies: canonical published catalog, verified identity, owned progress, native navigation/design system. Extend these; do not replace completed modules. Research: ../design/YOUTUBE_WATCH_RESEARCH.md.

Backend: additive013 discussion migration; paginated lesson discussion and root replies; idempotent submission; same-lesson root validation; moderation revision/audit/role checks. Add watch/series endpoints that include owned progress and complete pagination, with stable sort_order/id ascending. Keep existing endpoints backward-compatible.

Native: isolated YouTube IFrame interop, lifecycle-bound player, route-scoped watch VM, compact comments preview and native composer; no imported YouTube comments. Inline comments replace the scrollable metadata beneath the player. An expanded full-height native dialog pauses/hides the provider view; no Athar sheet overlaps live YouTube controls. Dedicated series detail plus dedicated scholar profile navigation. Existing favorites provide save semantics; do not add duplicate indistinguishable Save/Favorite controls. Explicit completion and honest resume. Native outbox/persistence must not misreport unsynchronized writes as server-confirmed. Retrying a failed metadata switch retries the requested lesson, not merely the previous player's HTML.

Phase sequence: provider research/contracts -> backend/tests -> player/watch/comments/series -> build -> RTL/LTR/light/device/error verification -> updates/evidence -> next P4/P5 roadmap work. Threaded moderation is required before enabling replies publicly. Runtime migrations are explicit and backed up; production/source recovery originals are preserved.

Adaptive discussion: short windows and large font sizes open full comments directly; taller windows offer in-flow comments below the provider. Add-comment/reply opens a separate native IME-aware scrolling composer with automatic input focus. Closing retains the draft; successful pending submission closes the composer and displays review feedback. Header, fields, validation and submit remain reachable in landscape rather than reserving a permanently tall editor beneath the comments list.

Performance boundary: only a distinct preview row/count flow reaches the watch parent. Draft, validation, sending and reply-editor updates stay in the discussion/composer surfaces, so typing does not repeatedly update/pause the player. Measured debug diagnostics and remaining release gates are in testing/LESSON_WATCH_ACCEPTANCE.md.

Provider geometry: the live diagnostic exposed a zero-height body/iframe despite a correctly measured native view. Chromium's wrap-content flag forces that CSS layout height. The native WebView now explicitly uses match-parent layout parameters inside Compose's bounded area; HTML uses viewport dimensions and official setSize on ready/resize. A focused device assertion checks native parameters and actual iframe height before attempting user-gesture playback. Provider readiness alone is not acceptance.
