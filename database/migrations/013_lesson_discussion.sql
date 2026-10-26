ALTER TABLE lesson_comments
    ADD COLUMN parent_id BIGINT UNSIGNED NULL,
    ADD COLUMN client_id CHAR(36) NULL,
    ADD COLUMN request_hash CHAR(64) NULL,
    ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1,
    ADD KEY idx_comment_thread (lesson_id,parent_id,status,id),
    ADD UNIQUE KEY uq_comment_client (user_id,client_id),
    ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES lesson_comments(id) ON DELETE CASCADE;

INSERT INTO schema_migrations (version) VALUES ('013_lesson_discussion');
