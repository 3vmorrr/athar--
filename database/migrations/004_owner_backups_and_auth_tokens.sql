USE athar;

ALTER TABLE users MODIFY role ENUM('user','admin','owner') NOT NULL DEFAULT 'user';
ALTER TABLE users ADD COLUMN session_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER role;

CREATE TABLE auth_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_type ENUM('verify_email','password_reset') NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_auth_token_hash (token_hash),
    KEY idx_auth_token_owner (user_id,token_type,expires_at),
    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE backup_artifacts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    created_by BIGINT UNSIGNED NULL,
    storage_key CHAR(44) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT UNSIGNED NOT NULL,
    schema_version VARCHAR(80) NOT NULL,
    status ENUM('ready','downloaded','expired') NOT NULL DEFAULT 'ready',
    verified_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    downloaded_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_backup_storage (storage_key),
    KEY idx_backup_expiry (status,expires_at),
    CONSTRAINT fk_backup_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE backup_download_grants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    backup_id BIGINT UNSIGNED NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_backup_grant_hash (token_hash),
    KEY idx_backup_grant_expiry (expires_at,consumed_at),
    CONSTRAINT fk_backup_grant_artifact FOREIGN KEY (backup_id) REFERENCES backup_artifacts(id) ON DELETE CASCADE,
    CONSTRAINT fk_backup_grant_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('004_owner_backups_and_auth_tokens');
