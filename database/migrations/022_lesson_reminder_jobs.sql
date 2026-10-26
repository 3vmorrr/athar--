CREATE TABLE lesson_reminder_subscriptions (
    user_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    lead_minutes TINYINT UNSIGNED NOT NULL DEFAULT 15,
    event_id BIGINT UNSIGNED NULL,
    event_revision INT UNSIGNED NULL,
    scheduled_at DATETIME(3) NULL,
    source_timezone VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,lesson_id),
    KEY idx_lesson_reminder_active (user_id,enabled,lesson_id),
    KEY idx_lesson_reminder_lesson (lesson_id,enabled,user_id),
    CONSTRAINT fk_lesson_reminder_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_reminder_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_reminder_jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    subscription_revision INT UNSIGNED NOT NULL,
    event_revision INT UNSIGNED NOT NULL,
    due_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    status ENUM('pending','outboxed','cancelled','skipped','failed') NOT NULL DEFAULT 'pending',
    dedupe_key CHAR(64) NOT NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    locked_at DATETIME NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_lesson_reminder_job (user_id,lesson_id),
    UNIQUE KEY uq_lesson_reminder_job_dedupe (dedupe_key),
    KEY idx_lesson_reminder_due (status,due_at,next_attempt_at,expires_at,id),
    CONSTRAINT fk_lesson_reminder_job_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_reminder_job_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_outbox (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(40) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    payload_json JSON NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    status ENUM('queued','processing','sent','failed','cancelled','expired') NOT NULL DEFAULT 'queued',
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    locked_at DATETIME NULL,
    sent_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_push_outbox_dedupe (dedupe_key),
    KEY idx_push_outbox_queue (status,next_attempt_at,expires_at,id),
    KEY idx_push_outbox_owner (user_id,category,status,id),
    CONSTRAINT fk_push_outbox_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('022_lesson_reminder_jobs');
