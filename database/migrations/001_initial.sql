CREATE DATABASE IF NOT EXISTS athar CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE athar;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('user','admin') NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quran_bookmarks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    surah TINYINT UNSIGNED NOT NULL,
    ayah SMALLINT UNSIGNED NOT NULL,
    label VARCHAR(120) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_bookmark (user_id, surah, ayah),
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quran_positions (
    user_id BIGINT UNSIGNED PRIMARY KEY,
    surah TINYINT UNSIGNED NOT NULL,
    ayah SMALLINT UNSIGNED NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_position_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS khatmah_plans (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    start_date DATE NOT NULL,
    target_date DATE NOT NULL,
    total_pages SMALLINT UNSIGNED NOT NULL DEFAULT 604,
    completed_pages SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('active','paused','finished') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_plan_owner (user_id, status),
    UNIQUE KEY uq_plan_user (user_id),
    CONSTRAINT fk_plan_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS scholars (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    biography TEXT NULL,
    language VARCHAR(12) NOT NULL DEFAULT 'ar',
    source_url VARCHAR(500) NULL,
    rights_basis VARCHAR(500) NULL,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lessons (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    scholar_id BIGINT UNSIGNED NULL,
    title VARCHAR(220) NOT NULL,
    description TEXT NULL,
    youtube_id CHAR(11) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_lesson_youtube (youtube_id),
    KEY idx_lesson_public (is_published, sort_order),
    CONSTRAINT fk_lesson_scholar FOREIGN KEY (scholar_id) REFERENCES scholars(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS books (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    author VARCHAR(180) NOT NULL,
    description TEXT NULL,
    language VARCHAR(12) NOT NULL DEFAULT 'ar',
    source_url VARCHAR(500) NOT NULL,
    rights_basis VARCHAR(500) NOT NULL,
    file_key VARCHAR(80) NULL,
    file_size BIGINT UNSIGNED NULL,
    can_download TINYINT(1) NOT NULL DEFAULT 0,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_book_public (is_published, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS adhkar_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    formula TEXT NOT NULL,
    source_reference VARCHAR(300) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    repeat_count SMALLINT UNSIGNED NULL,
    category VARCHAR(80) NOT NULL DEFAULT 'عام',
    is_reviewed TINYINT(1) NOT NULL DEFAULT 0,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_adhkar_public (is_published, category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT UNSIGNED NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id VARCHAR(80) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_recent (created_at, actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(80) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT IGNORE INTO schema_migrations (version) VALUES ('001_initial');
