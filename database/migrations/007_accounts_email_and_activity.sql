USE athar;

ALTER TABLE users
    ADD COLUMN display_name VARCHAR(80) NULL AFTER email,
    ADD COLUMN username VARCHAR(24) NULL AFTER display_name,
    ADD UNIQUE KEY uq_users_username (username);

ALTER TABLE auth_tokens
    MODIFY token_type ENUM('verify_email','password_reset') NOT NULL,
    ADD COLUMN failed_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER token_hash;

ALTER TABLE books ADD COLUMN download_url VARCHAR(500) NULL AFTER source_url;

CREATE TABLE platform_settings (
    setting_key VARCHAR(80) NOT NULL PRIMARY KEY,
    setting_value MEDIUMTEXT NOT NULL,
    updated_by BIGINT UNSIGNED NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_settings_actor FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_daily_stats (
    user_id BIGINT UNSIGNED NOT NULL,
    activity_date DATE NOT NULL,
    quran_sessions SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    lesson_completions SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    adhkar_completions SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    book_downloads SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, activity_date),
    CONSTRAINT fk_daily_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_comments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    body VARCHAR(2000) NOT NULL,
    status ENUM('pending','visible','hidden') NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    moderated_at TIMESTAMP NULL,
    moderated_by BIGINT UNSIGNED NULL,
    KEY idx_comments_lesson_status (lesson_id,status,created_at),
    KEY idx_comments_user_created (user_id,created_at),
    CONSTRAINT fk_comment_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_moderator FOREIGN KEY (moderated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('007_accounts_email_and_activity');
