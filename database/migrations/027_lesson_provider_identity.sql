-- Keep opaque provider IDs exactly distinct without changing existing row IDs/data.
-- 016-026 are reserved by the parallel implementation chain; do not renumber them.
ALTER TABLE lessons MODIFY youtube_id CHAR(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

INSERT INTO schema_migrations(version) VALUES ('027_lesson_provider_identity');
