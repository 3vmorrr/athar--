-- Additive and resumable after an interrupted ALTER before ledger insertion.
-- Existing private plans are retained; no inferred reading/completion or activity.
SET @athar031_ddl = (SELECT IF(COUNT(*)=0,'ALTER TABLE khatmah_plans ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1',IF(SUM(data_type='int' AND column_type LIKE '%unsigned' AND is_nullable='NO' AND column_default='1' AND extra='')=1,'DO 0','SELECT athar031_column_contract_mismatch FROM information_schema.columns LIMIT 0')) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='khatmah_plans' AND column_name='revision');
PREPARE athar031_statement FROM @athar031_ddl;
EXECUTE athar031_statement;
DEALLOCATE PREPARE athar031_statement;
INSERT INTO schema_migrations(version) VALUES ('031_khatmah_revision');
