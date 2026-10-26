ALTER TABLE adhkar_items
    ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1 AFTER sort_order;

CREATE TABLE adhkar_item_revisions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    adhkar_id BIGINT UNSIGNED NULL,
    source_item_id BIGINT UNSIGNED NOT NULL,
    revision INT UNSIGNED NOT NULL,
    action ENUM('created','updated','publication_changed','deleted') NOT NULL,
    snapshot_json JSON NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_adhkar_item_revision (source_item_id, revision),
    KEY idx_adhkar_revision_item (source_item_id, created_at),
    KEY idx_adhkar_revision_actor (actor_id, created_at),
    CONSTRAINT fk_adhkar_revision_item FOREIGN KEY (adhkar_id) REFERENCES adhkar_items(id) ON DELETE SET NULL,
    CONSTRAINT fk_adhkar_revision_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE lesson_comments
    ADD COLUMN moderation_reason VARCHAR(500) NULL AFTER moderated_by;

INSERT INTO schema_migrations(version) VALUES ('016_admin_editorial_control');
