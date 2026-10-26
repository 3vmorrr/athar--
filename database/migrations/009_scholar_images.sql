USE athar;

ALTER TABLE scholars
    ADD COLUMN image_key VARCHAR(80) NULL AFTER rights_basis;

INSERT INTO schema_migrations (version) VALUES ('009_scholar_images');
