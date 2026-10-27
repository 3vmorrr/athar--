CREATE TABLE lesson_comment_moderation_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT UNSIGNED NULL,
    source_comment_id BIGINT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL,
    action ENUM('published','hidden','deleted') NOT NULL,
    previous_status ENUM('pending','visible','hidden') NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_comment_moderation_revision (source_comment_id,revision),
    KEY idx_comment_moderation_actor (actor_id,created_at),
    KEY idx_comment_moderation_created (created_at,id),
    CONSTRAINT fk_comment_moderation_comment FOREIGN KEY (comment_id) REFERENCES lesson_comments(id) ON DELETE SET NULL,
    CONSTRAINT fk_comment_moderation_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('017_admin_comment_moderation');
