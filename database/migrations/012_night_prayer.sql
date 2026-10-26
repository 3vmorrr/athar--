CREATE TABLE night_prayer_sessions (
    user_id BIGINT UNSIGNED NOT NULL,
    id CHAR(36) NOT NULL,
    night_date DATE NULL,
    timezone VARCHAR(64) NULL,
    boundary_source VARCHAR(16) NULL,
    calculation_method VARCHAR(40) NULL,
    maghrib_at DATETIME NULL,
    fajr_at DATETIME NULL,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    entry_type VARCHAR(8) NULL,
    start_index SMALLINT UNSIGNED NULL,
    end_index SMALLINT UNSIGNED NULL,
    verse_count INT UNSIGNED NOT NULL DEFAULT 0,
    measured_seconds INT UNSIGNED NULL,
    note_cipher TEXT NULL,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (user_id,id),
    KEY idx_night_owner_date (user_id,is_deleted,night_date),
    CONSTRAINT fk_night_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE night_prayer_changes (
    sequence_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id CHAR(36) NOT NULL,
    revision INT UNSIGNED NOT NULL,
    changed_at DATETIME NOT NULL,
    UNIQUE KEY uq_night_change (user_id,session_id,revision),
    KEY idx_night_sync (user_id,sequence_id),
    CONSTRAINT fk_night_change_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE night_prayer_mutations (
    user_id BIGINT UNSIGNED NOT NULL,
    mutation_id CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (user_id,mutation_id),
    CONSTRAINT fk_night_mutation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE night_prayer_preferences (
    user_id BIGINT UNSIGNED PRIMARY KEY,
    preferences_json JSON NOT NULL,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_night_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('012_night_prayer');
