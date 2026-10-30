# Engineering decision log

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-30:** [149-task actual source audit](ACTUAL_IMPLEMENTATION_AUDIT.md). 113 tasks have implemented source (74 in main, 39 only unmerged); 19 are accepted/closed, 55 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

Task21.18: document the additive immersive direction before implementing it; use the references for composition/interaction, not assets, sacred text, identity or fake data. Keep original local artwork/fallbacks, bounded native motion, actual status/actions and provider/private-state constraints. Return to functional02.7.2 after docs; later shared/screen tasks own code/device acceptance. Weight phase21 closure by task contribution budgets so a small specification is not treated as a full screen.

Task02.7.1: capture the actor at the UI/ViewModel boundary and before repository IO/lock suspension; reject rather than retarget a queued action. A matching ID alone is insufficient after same-actor authentication becomes Pending, so private actions require current verified/full SignedIn credentials. OTP/resend/logout retain their explicit pending-account allowance. Preserve pending rows/retained ciphertext and current presentation; no actor/global reset.

Task02.6: keep private lesson checkpoints authoritative and observed with ProtectedRead, while account HTTP responses remain disposable. Missing required checkpoint fields/invalid booleans or identity are unreadable, never default zero/completed. On ordinary lesson switches retain the ready old player composition while metadata loads; on unreadable progress suppress player/write/completion until retry. Validate account payload shape before rendering empty/default edit controls, and validate local export snapshots before opening the output stream. No exemption upload or full-export lifecycle claim.

Task02.5: persist409 conflict identity in the owned outbox and exclude blocked session IDs before the50-operation batch limit. Review fresh account/device versions; confirmation must recheck both before a target-only transaction. A remote deletion can only become a deliberately chosen new UUID, never resurrection. Only409 is a conflict; other rejected statuses retain their retry/action classification and bytes. Preserve retained unreadable rows/outbox, actor isolation and local-only exemptions. Reconcile the premium hero inside lazy history; large-text/landscape conflict review uses a native full-height scrollable dialog.

26 October upcoming continuation: home discovery is bounded to three rows; current live status requires a fresh authoritative response, cached live is labelled as saved, and offline playback actions are withheld. Preserve cached context for connectivity failure, but discard displayed rows for explicit access denial/removal. Partition ViewModel identity by account and language. Test the same stateless presentation with a localized Context, not only a direction/configuration override.

Formal checkpoint2026-10-25: audit actual source against all approved addenda; preserve native/API/legacy work and the damaged original database; verify tests/build/USB integration and scan candidate files plus reachable history before normal commit/push. No history rewrite or force-push. The24 remaining work packages refine P4–P8. Continue phase1 after checkpoint; legacy Admin is not independent Admin acceptance.

| Date | Decision | Reason / consequence |
|---|---|---|
| 2026-10-24 | Preserve all uncommitted work; use `codex/android-ecosystem` | Safe additive migration from recorded baseline |
| 2026-10-24 | Keep public legacy website, add versioned PHP backend | Existing data/features remain usable; no unnecessary language rewrite |
| 2026-10-24 | Native Kotlin/Compose; no Athar WebView wrapper | Approved product requirement |
| 2026-10-24 | Record progress inside `docs/progress/` | Latest master explicitly requires this; supersedes earlier external-tracker preference |
| 2026-10-24 | Restore optional scholar metadata and authorized PDF management only under new scope | Latest master supersedes earlier simplified-form preference; direct-link workflow remains |
| 2026-10-24 | Correct canonical Quran index before feature migration | Baseline has 109 counts; validated metadata has 114 / 6236 |
| 2026-10-24 | Reject InfinityFree for native API | Official mandatory browser challenge blocks mobile clients |
| 2026-10-24 | Keep Night Prayer explicit/self-reported and exemptions local | No inferred worship; protect sensitive information |
| 2026-10-24 | Use Google performance/permissions skills after source review | Official references; no executable scripts in selected skills |
| 2026-10-25 | YouTube IFrame API in a restricted OS WebView only for video | Official supported mechanism; native Compose remains the product; no media extraction/background bypass |
| 2026-10-25 | One-level first-party comment replies with idempotent submissions and moderation | Real backend support before reply UI; hide descendants when parent is hidden |
| 2026-10-25 | Observed playback checkpoints; completion requires an explicit action | Opening, seeking and ended events do not grant completion; next lesson is user initiated |
| 2026-10-25 | Scholar profile has separate paginated series and standalone tabs | Avoid truncating a scholar to the legacy nested twenty-item preview; inherited series scholarship is included by catalog filters |
| 2026-10-25 | Adaptive discussion plus separate scrolling native composer | Small-phone visibility failure demonstrated that a permanently tall editor can consume the comments list; full panel is chosen for short/large-font windows, and drafts remain scoped in the discussion VM |
| 2026-10-25 | Isolate compact discussion preview from draft state | Typing must not recompose or repeatedly pause the embedded provider; distinct row/count preview drives the watch parent |
| 2026-10-25 | Quran resume position is explicit, encrypted and revision checked | Resume markers are not evidence of reading/completion; equivalent newer state recovers lost acknowledgements, while different state requires a user conflict choice |
| 2026-10-25 | Defer automatic lazy-list cursor/reset requests to the next remeasure | English recreation exposed an actual measure/layout crash; requestScrollToItem is supported by pinned Compose and passes both language recreation cases |
| 2026-10-25 | Separate audio selection from loaded source identity | Retain selection across configuration recreation, filter supported editions, and keep a paused recording paused when choosing another source |
| 2026-10-25 | Normalize Arabic keyboard OTP input at the shared native input boundary | Verification and reset must accept the same digit sets; preserve leading zeroes, transmit bounded ASCII and leave passwords/server authorization untouched |
| 2026-10-25 | Native identity feedback uses stable API codes, never remote message text | Map known codes to Arabic/English resources and hide unknown internal details; profile and identity screens share presentation behavior |

| 2026-10-26 | Scope persistent work by API partition and verified actor, with bounded module-isolated retries | Keep encrypted records/outbox authoritative; cancel former-account work, never include private payloads in job input, and use six-hour eventual maintenance rather than an always-running service. USB reverse is not proof of Android network constraints; report direct worker and OS scheduling separately. |
| 2026-10-26 | Bound only disposable localized response documents | Cap retained encoded payloads at200 rows/8MiB per actor,1MiB each and seven-day insertion age. Transactional metadata-only pruning excludes private progress/cursors/outbox/exemptions. Recheck actor before returning fresh or offline content; remove corrupt disposable downloads without deleting unreadable authoritative progress. |
| 2026-10-26 | Serialize new-authentication generation boundaries with token persistence | Invalidate former requests before sign-in dispatch and again on successful session publication; same-session refresh remains coalesced. A slow token save cannot invalidate a later authentication intent, and a failed sign-in does not erase the retained session. |
| 2026-10-26 | One-task execution queue and incremental premium reconciliation | Retain24-package roadmap with explicit small tasks and actual gates; report/commit/push before next implementation. Pin premium tip0e6f4ba, preserve newer data/security code and integrate only reviewed presentation slices at overlapping features. |
| 2026-10-26 | Integrate premium primitives separately with a controlled stable page transition | Keep repositories/auth/private recovery unchanged; use existing Compose1.9 SeekableTransitionState to cancel motion without swapping the current content tree. Two-frame effect/composition tests, draft retention, contrast, disabled semantics and large-font device gates precede screen-specific adoption. No external runtime or extra haptic. |

Further irreversible, recurring-cost or scope-changing decisions require explicit user authorization. Routine reversible implementation choices are documented and executed.
