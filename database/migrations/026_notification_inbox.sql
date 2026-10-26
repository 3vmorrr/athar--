CREATE TABLE notification_inbox (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(40) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(120) NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    destination VARCHAR(120) NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    read_at DATETIME NULL,
    deleted_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_notification_inbox_dedupe (user_id,dedupe_key),
    KEY idx_notification_inbox_page (user_id,deleted_at,expires_at,id),
    KEY idx_notification_inbox_unread (user_id,read_at,deleted_at,expires_at,id),
    CONSTRAINT fk_notification_inbox_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('026_notification_inbox');
