-- Additive, repeatable MySQL migration; execute after tk_tiktok_publish_attempt_upgrade_mysql.sql.
-- Null transfer_policy is interpreted as LEGACY; no historical row rewrite.
-- Keep additive columns during application rollback. Deploy columns before enabling PULL.

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_open_tiktok_publish_task' AND COLUMN_NAME = 'transfer_policy'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `transfer_policy` varchar(32) DEFAULT ''LEGACY''');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_task' AND COLUMN_NAME = 'transfer_policy'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `transfer_policy` varchar(32) DEFAULT ''LEGACY''');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_open_tiktok_publish_attempt' AND COLUMN_NAME = 'pull_url_cipher'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_attempt` ADD COLUMN `pull_url_cipher` text DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_open_tiktok_publish_detail' AND COLUMN_NAME = 'next_poll_time'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `next_poll_time` datetime DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_open_tiktok_publish_detail' AND COLUMN_NAME = 'downloaded_bytes'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `downloaded_bytes` bigint DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'transfer_source'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `transfer_source` varchar(32) DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'pull_url_cipher'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `pull_url_cipher` text DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'source_file_size'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `source_file_size` bigint DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'init_phase'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `init_phase` varchar(40) DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'init_started_at'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `init_started_at` datetime DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'next_poll_time'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `next_poll_time` datetime DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND COLUMN_NAME = 'downloaded_bytes'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `downloaded_bytes` bigint DEFAULT NULL');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_open_tiktok_publish_detail' AND INDEX_NAME = 'idx_pull_due'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_detail` ADD INDEX `idx_pull_due` (`status`, `next_poll_time`, `id`)');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;

SET @tk_pull_ddl = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_tiktok_publish_detail' AND INDEX_NAME = 'idx_pull_due'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD INDEX `idx_pull_due` (`status`, `next_poll_time`, `id`)');
PREPARE tk_pull_stmt FROM @tk_pull_ddl;
EXECUTE tk_pull_stmt;
DEALLOCATE PREPARE tk_pull_stmt;
