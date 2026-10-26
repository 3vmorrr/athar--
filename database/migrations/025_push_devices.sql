CREATE TABLE push_devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    environment CHAR(12) NOT NULL,
    installation_id CHAR(32) NOT NULL,
    provider ENUM('fcm_v1') NOT NULL,
    token_hash CHAR(64) NULL,
    token_cipher TEXT NULL,
    session_family_id CHAR(32) NOT NULL,
    status ENUM('active','revoked','invalid') NOT NULL DEFAULT 'active',
    revocation_reason VARCHAR(40) NULL,
    last_seen_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_push_installation (environment,installation_id),
    UNIQUE KEY uq_push_token (provider,environment,token_hash),
    KEY idx_push_device_owner (user_id,environment,status,last_seen_at),
    KEY idx_push_device_session (user_id,session_family_id,status),
    CONSTRAINT fk_push_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('025_push_devices');
