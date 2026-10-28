# Functional integration baseline — 28 October 2026

The explicit user request resumes WP1–WP24 functional integration and supersedes the previous visual-review pause. Preserve existing UI, protected private state and all prior accepted behavior. Cosmetic work is deferred.

Fetched refs: target `645d09bb6c4a1585aca336b36ed55c974150697f`; source `7392e93b088990c3478cf32197781f831eb10f83`. Source worktree: `C:/xampp/athar-parallel`. Existing UI branch commit `7459f46` and uncommitted UI preserved in `f66a05a`; target advanced only through these local checkpoint commits. No parallel merge. Unrelated `public/index.php` and recovery-runner edits remain unstaged, with all 50 original dirty paths backed up under ignored `artifacts/functional-integration/checkpoint-20261028/`.

Target baseline: debug APK, JVM tests, test APK and lint PASS (3m53s). Source Android baseline FAILS before Kotlin compilation: Gradle java-extension name shadows `java.io.File`; a local review-only import correction reveals a generated-assets Provider incompatible with Android SourceSet API. Keep these as source defects for their WP9/WP24 integration.

Source backend baseline on a dedicated disposable MariaDB instance passes domain6382, Quran13352, assets9, media15, tafsir18 and identity43 assertions, then fails `catalog_progress_test.php`: Khatmah fixture omitted newly required `expected_revision`. No valuable database touched. Source PHP syntax:104/109 pass; four Push value objects require a newer PHP syntax than the workstation supports, and unsubscribe HTML quoting in `public/index.php` is invalid. Correct during the corresponding integration slices; current target already retains its corrected unsubscribe handler.

Working-file security screening has no findings. Two historical credential-URL hits were inspected and are deliberately invalid example.invalid rejection fixtures. Raw reports and logs remain ignored. No provider/device proof is inferred.

The preliminary [149-task ledger](../progress/FUNCTIONAL_INTEGRATION_LEDGER.json) records candidate source history, affected modules, dependencies, contract/worker/migration risk and required acceptance per task. Candidate histories are module-scoped inventory, not completed semantic review. Existing 26 COMPLETE tasks are retained; no source statuses copied. WP1 is a historical foundation predating the refined149-task queue and remains covered by regression.
