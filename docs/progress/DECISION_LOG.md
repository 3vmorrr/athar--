# Engineering decision log

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

Further irreversible, recurring-cost or scope-changing decisions require explicit user authorization. Routine reversible implementation choices are documented and executed.
