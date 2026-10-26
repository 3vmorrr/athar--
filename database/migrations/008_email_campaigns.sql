USE athar;

ALTER TABLE users
    ADD COLUMN email_marketing_opt_in TINYINT(1) NOT NULL DEFAULT 0 AFTER email_verified_at;

CREATE TABLE email_campaigns (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    status ENUM('draft','sending','paused','completed') NOT NULL DEFAULT 'draft',
    created_by BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_campaign_status_created (status, created_at),
    CONSTRAINT fk_campaign_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_campaign_recipients (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    email VARCHAR(190) NOT NULL,
    status ENUM('pending','processing','sent','failed','skipped') NOT NULL DEFAULT 'pending',
    failure_note VARCHAR(255) NULL,
    sent_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_campaign_user (campaign_id, user_id),
    KEY idx_campaign_recipient_status (campaign_id, status, id),
    KEY idx_campaign_sent_at (status, sent_at),
    CONSTRAINT fk_campaign_recipient_campaign FOREIGN KEY (campaign_id) REFERENCES email_campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_recipient_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_send_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category ENUM('transactional','campaign') NOT NULL DEFAULT 'transactional',
    sent_at DATETIME NOT NULL,
    KEY idx_email_send_events_window (sent_at, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO schema_migrations (version) VALUES ('008_email_campaigns');
