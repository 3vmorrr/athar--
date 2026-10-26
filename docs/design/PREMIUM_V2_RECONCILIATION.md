# Premium v2 reconciliation ledger

Reviewed26 October2026. Remote branch fetched read-only: `origin/uiux/athar-premium-v2` at `0e6f4ba6e3bb94075ebc38f1187ec28c7db75d32`. Common ancestor with the active Codex branch: `6989a8a42ece5438972df13c186ea37c076b9874`. Read its `UI_UX_PREMIUM_V2_CHANGELOG.md` and `MOTION_SYSTEM.md`, including the static-only validation disclaimer and protected-local-read synchronization notes.

The branch-only diff from the common ancestor has20 Compose presentation files plus two design documents. No backend, migration, repository, provider or Room replacement is justified. Direct comparison of the current Codex branch to the UI tip also shows newer Codex data/sync files: those differences are deliberately retained, never reverted to make the diff look smaller. The uncommitted/current session task touches SessionRepository/tests; the UI branch has no competing change in that repository. AuthScreens presentation is separately queued.

| Area | Queue task | Reconciliation decision / validation |
|---|---|---|
| Shared premium primitives/motion/docs |21.1|Next dependency; inspect full diff, import narrowly, compile/device/reduced-motion checks before acceptance |
| Night conflict/recovery surface |02.5,21.8|Preserve ProtectedRead, ciphertext/pending records and new conflict logic before presentation |
| Watch/account unreadable state |02.6|Preserve current checkpoint and owner guards; do not replace whole older files |
| Home/navigation |21.2|Retain current routes/owner bindings/three-item upcoming query |
| Auth/account |07.1,21.3|Retain02.4 generation boundaries, OTP normalization and immediate verified-access removal |
| Quran/tafsir/audio/adhkar |03.*,04.*,05.*,21.4–21.7|Reconcile when feature files are touched; retain protected resume/counter state and player identity |
| Scholar/series/watch/discussion/upcoming |12.*,21.9–21.13|Retain pagination/provider/moderation/event availability and newer tests |
| Loading/large text/RTL/theme/accessibility |21.14–21.17|Presentation source is not a device/contrast/TalkBack/performance pass |

No premium UI source has yet been integrated at this ledger snapshot. No full merge/cherry-pick/checkout replacement has been performed. Branch review is not visual acceptance. Preserve calm Arabic-first Material3 surfaces, native reduced-motion-aware transitions and no decorative sacred-text animation. Before each overlapping edit, refresh/read the relevant two-sided diff and record the chosen change here and in the premium changelog when integration occurs.
