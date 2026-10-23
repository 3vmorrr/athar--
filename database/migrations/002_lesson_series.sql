USE athar;

CREATE TABLE IF NOT EXISTS series (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    scholar_id BIGINT UNSIGNED NULL,
    title VARCHAR(220) NOT NULL,
    description TEXT NULL,
    source_url VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_series_public (is_published, sort_order),
    CONSTRAINT fk_series_scholar FOREIGN KEY (scholar_id) REFERENCES scholars(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE lessons ADD COLUMN series_id BIGINT UNSIGNED NULL AFTER scholar_id;
ALTER TABLE lessons ADD KEY idx_lesson_series_order (series_id, sort_order);
ALTER TABLE lessons ADD CONSTRAINT fk_lesson_series FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE SET NULL;
INSERT IGNORE INTO schema_migrations (version) VALUES ('002_lesson_series');
