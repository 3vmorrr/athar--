-- Additive migration. The migration runner selects the target database explicitly.
ALTER TABLE users ADD COLUMN suspended_at DATETIME NULL AFTER session_version;
-- MariaDB's legacy implicit TIMESTAMP default made expires_at change on every failed attempt.
ALTER TABLE auth_tokens
    ADD COLUMN hash_scheme VARCHAR(24) NOT NULL DEFAULT 'legacy-sha256' AFTER token_hash,
    MODIFY expires_at DATETIME NOT NULL,
    MODIFY consumed_at DATETIME NULL;

CREATE TABLE api_sessions (
    id CHAR(32) NOT NULL PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    family_id CHAR(32) NOT NULL,
    access_hash CHAR(64) NOT NULL,
    refresh_hash CHAR(64) NOT NULL,
    scope ENUM('verification','full') NOT NULL,
    session_version INT UNSIGNED NOT NULL,
    expires_at DATETIME NOT NULL,
    refresh_expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    revoked_reason VARCHAR(24) NULL,
    replaced_by CHAR(32) NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_api_access (access_hash),
    UNIQUE KEY uq_api_refresh (refresh_hash),
    KEY idx_api_session_owner (user_id,revoked_at),
    KEY idx_api_session_family (family_id,revoked_at),
    KEY idx_api_session_expiry (refresh_expires_at),
    CONSTRAINT fk_api_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_rate_buckets (
    bucket_hash CHAR(64) NOT NULL,
    window_start BIGINT UNSIGNED NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (bucket_hash,window_start),
    KEY idx_rate_cleanup (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('010_versioned_identity');
