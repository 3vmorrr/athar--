CREATE TABLE scholar_follows (
    user_id BIGINT UNSIGNED NOT NULL,
    scholar_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,scholar_id),
    KEY idx_scholar_follow_target (scholar_id,user_id),
    CONSTRAINT fk_scholar_follow_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_scholar_follow_scholar FOREIGN KEY (scholar_id) REFERENCES scholars(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE series_follows (
    user_id BIGINT UNSIGNED NOT NULL,
    series_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,series_id),
    KEY idx_series_follow_target (series_id,user_id),
    CONSTRAINT fk_series_follow_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_series_follow_series FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('023_follows');
