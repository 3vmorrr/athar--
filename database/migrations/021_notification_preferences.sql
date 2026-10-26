CREATE TABLE notification_preferences (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    master_enabled TINYINT(1) NOT NULL DEFAULT 0,
    consent_version SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    consented_at DATETIME NULL,
    lesson_reminders TINYINT(1) NOT NULL DEFAULT 0,
    following_updates TINYINT(1) NOT NULL DEFAULT 0,
    quran_routine TINYINT(1) NOT NULL DEFAULT 0,
    adhkar_routine TINYINT(1) NOT NULL DEFAULT 0,
    salawat_friday TINYINT(1) NOT NULL DEFAULT 0,
    custom_routines TINYINT(1) NOT NULL DEFAULT 0,
    khatmah TINYINT(1) NOT NULL DEFAULT 0,
    night_prayer TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_notification_lesson (master_enabled,lesson_reminders,user_id),
    KEY idx_notification_following (master_enabled,following_updates,user_id),
    CONSTRAINT fk_notification_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('021_notification_preferences');
