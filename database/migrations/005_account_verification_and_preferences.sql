USE athar;

ALTER TABLE users
    ADD COLUMN email_verified_at DATETIME NULL AFTER password_hash,
    ADD COLUMN preferences_json JSON NULL AFTER session_version;

INSERT INTO schema_migrations (version) VALUES ('005_account_verification_and_preferences');
