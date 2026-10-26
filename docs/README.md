# Athar engineering documentation

Current source-based status: [formal checkpoint and24 remaining phases](audit/CHECKPOINT_2026-10-25.md), verified25–26 October2026. Historical baseline inventories are retained for comparison, not current implementation claims.
The approved scope is preserved in [Master requirements](product/MASTER_REQUIREMENTS.md), [Night Prayer requirements](product/NIGHT_PRAYER_REQUIREMENTS.md) and [Native UI/UX addendum](product/UI_UX_REQUIREMENTS.md). The received UI addendum ends at the beginning of the adhkar counter section; missing text has not been inferred. The new [engagement addendum](product/ENGAGEMENT_REQUIREMENTS.md) and [physical-device requirements](product/PHYSICAL_DEVICE_REQUIREMENTS.md) extend this scope. These supersede older planning assumptions when they conflict. The existing website remains supported during migration.

Start with [Repository audit](audit/REPOSITORY_AUDIT.md), [Feature inventory](audit/FEATURE_INVENTORY.md), [Implementation plan](progress/IMPLEMENTATION_PLAN.md) and [Task tracker](progress/TASK_TRACKER.md). Documentation distinguishes observed code from runtime evidence and proposed behavior. A specification does not mean a feature has shipped.

The new architecture retains PHP/MySQL, adds `/api/v1`, introduces a Kotlin/Compose Android application and gives administration a separate shell and authentication boundary. The full Night Prayer scope is mandatory. Public web browsing remains available; authenticated Android content requires a verified account. Release readiness depends on real SMTP delivery, provider approval, Android device tests and a compatible HTTPS host.

Legacy planning files are retained under [planning/](planning/). Reproduce the static source inventory with `python scripts/audit_repository.py`; see [test strategy](engineering/TEST_STRATEGY.md) before touching a database.

Local execution: [stack startup](development/LOCAL_DEVELOPMENT.md), [physical Android USB workflow](development/ANDROID_PHYSICAL_DEVICE.md), and [engagement stages](features/ENGAGEMENT.md).
