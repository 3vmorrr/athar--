ALTER TABLE quran_positions ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1;
ALTER TABLE user_favorites MODIFY target_type ENUM('book','lesson','series') NOT NULL;

CREATE TABLE activity_events (
    user_id BIGINT UNSIGNED NOT NULL,
    event_id CHAR(36) NOT NULL,
    activity_type ENUM('quran_session','lesson_completion','adhkar_completion','book_download') NOT NULL,
    activity_date DATE NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (user_id,event_id),
    KEY idx_activity_cleanup (created_at),
    CONSTRAINT fk_activity_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE adhkar_progress (
    user_id BIGINT UNSIGNED NOT NULL,
    item_key VARCHAR(128) NOT NULL,
    current_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    repeat_count SMALLINT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (user_id,item_key),
    CONSTRAINT fk_adhkar_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('011_native_progress');
