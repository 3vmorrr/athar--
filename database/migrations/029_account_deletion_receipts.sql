-- Minimal, hashed recovery capability survives the user row's atomic deletion.
-- No account email, credentials, worship data or plaintext recovery secret is retained.
CREATE TABLE account_deletion_receipts (
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    owner_id BIGINT UNSIGNED NOT NULL,
    proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_deletion_receipt_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO schema_migrations(version) VALUES ('029_account_deletion_receipts');
