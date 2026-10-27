# Athar Android UI work

For Android UI, load `.agents/skills/athar-premium-compose-design/SKILL.md` and only the relevant focused skills it routes to. Functional requirements, Quran text accuracy, private-data ownership, and supported APIs take precedence over third-party examples.

The premium UI directive pauses unrelated backend/integration work at the resume point in `docs/progress/PREMIUM_UI_MILESTONE.json`. Continue UI implementation, builds, and authorized device validation; stop for the user's visual review before resuming that queue.

Preserve pre-existing dirty changes and other workstreams. The baseline is in ignored `artifacts/premium-ui/checkpoint/`; do not stage unrelated backend/test changes. Keep device identifiers, private captures, and reference clones out of commits.
