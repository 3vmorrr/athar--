CREATE TABLE engagement_content_publication_state (
    target_type ENUM('lesson','series') NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    public_visible TINYINT(1) NOT NULL DEFAULT 0,
    replay_available TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (target_type,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE engagement_content_facts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    fact_type ENUM('series_published','lesson_published','lesson_replay_available') NOT NULL,
    target_type ENUM('lesson','series') NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    scholar_id BIGINT UNSIGNED NULL,
    series_id BIGINT UNSIGNED NULL,
    payload_json JSON NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    status ENUM('active','cancelled') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_engagement_content_fact_dedupe (dedupe_key),
    KEY idx_engagement_content_fact_target (target_type,target_id,status,id),
    KEY idx_engagement_content_fact_created (status,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE engagement_content_fact_sources (
    fact_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    follow_type ENUM('scholar','series') NOT NULL,
    follow_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fact_id,user_id,follow_type,follow_id),
    KEY idx_content_fact_source_user (user_id,fact_id),
    KEY idx_content_fact_source_follow (follow_type,follow_id,fact_id),
    CONSTRAINT fk_content_fact_source_fact FOREIGN KEY (fact_id) REFERENCES engagement_content_facts(id) ON DELETE CASCADE,
    CONSTRAINT fk_content_fact_source_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO engagement_content_publication_state(target_type,target_id,public_visible,replay_available)
SELECT 'series',id,is_published,0 FROM series;

INSERT INTO engagement_content_publication_state(target_type,target_id,public_visible,replay_available)
SELECT 'lesson',l.id,l.is_published,COALESCE(e.replay_available,0)
FROM lessons l LEFT JOIN lesson_events e ON e.lesson_id=l.id;

INSERT INTO schema_migrations(version) VALUES ('024_content_subscription_facts');
