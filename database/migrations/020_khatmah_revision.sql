ALTER TABLE khatmah_plans
    ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1 AFTER status;

INSERT INTO schema_migrations (version) VALUES ('020_khatmah_revision');
