USE athar;

ALTER TABLE books
    ADD COLUMN keywords VARCHAR(1000) NULL AFTER description,
    ADD COLUMN publication_year SMALLINT UNSIGNED NULL AFTER language,
    ADD COLUMN rights_holder VARCHAR(180) NULL AFTER rights_basis;

INSERT INTO schema_migrations (version) VALUES ('006_book_catalog_metadata');
