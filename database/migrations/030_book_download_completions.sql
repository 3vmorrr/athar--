-- Preserve mixed legacy request counters; only the new explicit completion kind
-- can increment the separate completed-download metric. Appending preserves ENUM indices.
ALTER TABLE activity_events MODIFY activity_type ENUM('quran_session','lesson_completion','adhkar_completion','book_download','book_download_completed') NOT NULL;
-- Portable MySQL/MariaDB retry after an interrupted DDL before the ledger insert.
SET @athar030_ddl = (SELECT IF(COUNT(*)=0,'ALTER TABLE account_daily_stats ADD COLUMN book_completions SMALLINT UNSIGNED NOT NULL DEFAULT 0',IF(SUM(data_type='smallint' AND column_type LIKE '%unsigned' AND is_nullable='NO' AND column_default='0' AND extra='')=1,'DO 0','SELECT athar030_column_contract_mismatch FROM information_schema.columns LIMIT 0')) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account_daily_stats' AND column_name='book_completions');
PREPARE athar030_statement FROM @athar030_ddl;
EXECUTE athar030_statement;
DEALLOCATE PREPARE athar030_statement;
INSERT INTO schema_migrations(version) VALUES ('030_book_download_completions');
