# Athar Android UI work

For Android UI, load `.agents/skills/athar-premium-compose-design/SKILL.md` and only the relevant focused skills it routes to. Functional requirements, Quran text accuracy, private-data ownership, and supported APIs take precedence over third-party examples.

The 2026-10-28 user directive supersedes the premium UI pause: continue functional integration and acceptance across WP1-WP24. Preserve existing UI work and defer cosmetic redesign. Use `docs/progress/FUNCTIONAL_INTEGRATION_LEDGER.json` for per-task source review and evidence; do not merge the parallel branch wholesale.

Preserve pre-existing dirty changes and other workstreams. The baseline is in ignored `artifacts/premium-ui/checkpoint/`; do not stage unrelated backend/test changes. Keep device identifiers, private captures, and reference clones out of commits.

Follow `docs/progress/FUNCTIONAL_INTEGRATION_STRATEGY.md`: focused validation per task, subsystem gates after2–5 related tasks, WP/milestone/final gates, immediate targeted checks for high-risk changes, and batched authorized device acceptance. Preserve premium design while fixing functionality; do not simplify it for testing. Continue automatically after normal commit/push/remote verification.
