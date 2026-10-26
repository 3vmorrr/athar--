CREATE TABLE engagement_channel_preferences (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    locale ENUM('ar','en') NOT NULL DEFAULT 'ar',
    push_campaign_opt_in TINYINT(1) NOT NULL DEFAULT 0,
    email_campaign_opt_in TINYINT(1) NOT NULL DEFAULT 0,
    quiet_start TIME NULL,
    quiet_end TIME NULL,
    timezone VARCHAR(80) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_engagement_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO engagement_channel_preferences (user_id,email_campaign_opt_in)
SELECT id,email_marketing_opt_in FROM users
ON DUPLICATE KEY UPDATE email_campaign_opt_in=VALUES(email_campaign_opt_in);

CREATE TABLE engagement_campaigns (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    channel ENUM('push','email','push_email') NOT NULL,
    status ENUM('draft','scheduled','processing','completed','partially_failed','cancelled') NOT NULL DEFAULT 'draft',
    audience_type ENUM('all_eligible','locale','scholar_followers','series_followers','lesson_reminders','test_recipient') NOT NULL DEFAULT 'all_eligible',
    audience_ref_id BIGINT UNSIGNED NULL,
    audience_locale ENUM('ar','en') NULL,
    title_ar VARCHAR(180) NOT NULL,
    body_ar VARCHAR(1000) NOT NULL,
    title_en VARCHAR(180) NOT NULL,
    body_en VARCHAR(1000) NOT NULL,
    deep_link VARCHAR(500) NULL,
    image_url VARCHAR(500) NULL,
    scheduled_at DATETIME NULL,
    schedule_timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    ttl_seconds INT UNSIGNED NOT NULL DEFAULT 86400,
    min_frequency_hours SMALLINT UNSIGNED NOT NULL DEFAULT 6,
    respect_quiet_hours TINYINT(1) NOT NULL DEFAULT 1,
    revision INT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    confirmed_by BIGINT UNSIGNED NULL,
    confirmed_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_engagement_campaign_status (status,scheduled_at,id),
    KEY idx_engagement_campaign_creator (created_by,created_at),
    CONSTRAINT fk_engagement_campaign_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_engagement_campaign_confirmer FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE engagement_campaign_recipients (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    channel ENUM('push','email') NOT NULL,
    locale ENUM('ar','en') NOT NULL DEFAULT 'ar',
    status ENUM('queued','processing','sent','failed','skipped','cancelled') NOT NULL DEFAULT 'queued',
    payload_json JSON NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    locked_at DATETIME NULL,
    sent_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_engagement_campaign_recipient (campaign_id,user_id,channel),
    UNIQUE KEY uq_engagement_delivery_dedupe (dedupe_key),
    KEY idx_engagement_delivery_queue (channel,status,next_attempt_at,expires_at,id),
    KEY idx_engagement_delivery_user (user_id,channel,status,created_at),
    CONSTRAINT fk_engagement_delivery_campaign FOREIGN KEY (campaign_id) REFERENCES engagement_campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_engagement_delivery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE engagement_campaign_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    summary_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_engagement_campaign_event (campaign_id,id),
    KEY idx_engagement_campaign_event_actor (actor_id,created_at),
    CONSTRAINT fk_engagement_event_campaign FOREIGN KEY (campaign_id) REFERENCES engagement_campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_engagement_event_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations(version) VALUES ('018_engagement_campaigns');
