CREATE TABLE lesson_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT UNSIGNED NOT NULL,
    status ENUM('draft','scheduled','live','published','cancelled') NOT NULL DEFAULT 'draft',
    scheduled_at DATETIME(3) NULL,
    schedule_timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    estimated_duration_minutes SMALLINT UNSIGNED NULL,
    is_live_event TINYINT(1) NOT NULL DEFAULT 0,
    reminders_enabled TINYINT(1) NOT NULL DEFAULT 0,
    replay_available TINYINT(1) NOT NULL DEFAULT 0,
    visibility ENUM('public','private') NOT NULL DEFAULT 'public',
    live_confirmed_at DATETIME(3) NULL,
    live_source VARCHAR(24) NULL,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uq_lesson_event (lesson_id),
    KEY idx_upcoming_public (visibility,status,scheduled_at,id),
    CONSTRAINT fk_event_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_event_editor FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_translations (
    lesson_id BIGINT UNSIGNED NOT NULL,
    locale CHAR(2) NOT NULL,
    title VARCHAR(220) NOT NULL,
    description TEXT NULL,
    PRIMARY KEY (lesson_id,locale),
    CONSTRAINT fk_lesson_translation FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_event_changes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL,
    change_type VARCHAR(24) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_event_change (event_id,revision),
    KEY idx_event_change_cursor (id,event_id),
    CONSTRAINT fk_event_change FOREIGN KEY (event_id) REFERENCES lesson_events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('014_lesson_events');
