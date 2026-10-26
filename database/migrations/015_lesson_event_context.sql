CREATE TABLE lesson_event_context (
    event_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    course_title_ar VARCHAR(200) NULL,
    course_title_en VARCHAR(200) NULL,
    topics_ar_json TEXT NOT NULL,
    topics_en_json TEXT NOT NULL,
    related_book_id BIGINT UNSIGNED NULL,
    related_page_start INT UNSIGNED NULL,
    related_page_end INT UNSIGNED NULL,
    CONSTRAINT fk_event_context_event FOREIGN KEY (event_id) REFERENCES lesson_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_context_book FOREIGN KEY (related_book_id) REFERENCES books(id) ON DELETE SET NULL,
    CONSTRAINT ck_event_context_topics_ar CHECK (JSON_VALID(topics_ar_json)),
    CONSTRAINT ck_event_context_topics_en CHECK (JSON_VALID(topics_en_json)),
    CONSTRAINT ck_event_context_pages CHECK (
        (related_page_start IS NULL AND related_page_end IS NULL) OR
        (related_page_start IS NOT NULL AND related_page_end IS NOT NULL AND related_page_start >= 1 AND related_page_end >= related_page_start)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('015_lesson_event_context');
