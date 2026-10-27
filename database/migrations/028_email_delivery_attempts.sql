-- Source019 attempt_count is a claim generation; policy deferral is not a network attempt.
ALTER TABLE email_outbox ADD COLUMN delivery_attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 AFTER attempt_count, ADD COLUMN dispatch_started_at DATETIME NULL AFTER locked_at;
-- Distinguish source018's seeded Arabic fallback from an explicitly chosen campaign locale.
ALTER TABLE engagement_channel_preferences ADD COLUMN locale_is_explicit TINYINT(1) NOT NULL DEFAULT 0 AFTER locale;
INSERT INTO schema_migrations(version) VALUES ('028_email_delivery_attempts');
