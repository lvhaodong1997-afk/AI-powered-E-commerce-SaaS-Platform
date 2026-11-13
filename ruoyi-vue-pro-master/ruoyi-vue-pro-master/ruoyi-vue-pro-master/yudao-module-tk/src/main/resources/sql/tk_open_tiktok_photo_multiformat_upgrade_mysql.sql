-- Additive, repeatable photo conversion upgrade. Apply before the application release.
-- Existing VIDEO rows and URLs retain their original semantics. No material migration.

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_publish_task' AND COLUMN_NAME='media_type'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `media_type` varchar(16) NOT NULL DEFAULT ''VIDEO''');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_publish_task' AND COLUMN_NAME='photo_cover_index'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `photo_cover_index` int DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_publish_task' AND COLUMN_NAME='auto_add_music'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `auto_add_music` bit(1) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_publish_task' AND COLUMN_NAME='media_id' AND IS_NULLABLE='NO'), 'ALTER TABLE `tk_open_tiktok_publish_task` MODIFY COLUMN `media_id` varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_publish_task' AND COLUMN_NAME='caption' AND CHARACTER_MAXIMUM_LENGTH<4000), 'ALTER TABLE `tk_open_tiktok_publish_task` MODIFY COLUMN `caption` varchar(4000) DEFAULT NULL', 'SELECT 1');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_publish_task_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `media_id` varchar(64) NOT NULL,
  `sort_index` int NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_photo_order` (`client_id`,`task_id`,`sort_index`),
  KEY `idx_photo_media_reference` (`client_id`,`media_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='media_type'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `media_type` varchar(16) NOT NULL DEFAULT ''VIDEO''');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_file_name'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_file_name` varchar(512) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_file_size'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_file_size` bigint DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_content_type'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_content_type` varchar(128) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_sha256'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_sha256` varchar(64) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_object_key'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_object_key` varchar(1024) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_local_path'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_local_path` varchar(2048) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_etag'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_etag` varchar(128) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_version_id'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_version_id` varchar(256) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='width'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `width` int DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='height'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `height` int DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='conversion_claim_token'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `conversion_claim_token` varchar(64) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='conversion_lease_until'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `conversion_lease_until` datetime DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND COLUMN_NAME='source_cleaned_at'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `source_cleaned_at` datetime DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='purpose'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `purpose` varchar(16) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_object_key'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_object_key` varchar(1024) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_local_path'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_local_path` varchar(2048) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_sha256'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_sha256` varchar(64) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_etag'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_etag` varchar(128) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_version_id'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_version_id` varchar(256) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='conversion_claim_token'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `conversion_claim_token` varchar(64) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='conversion_lease_until'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `conversion_lease_until` datetime DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='result_media_id'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `result_media_id` bigint DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='fail_reason'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `fail_reason` varchar(1024) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='photo_width'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `photo_width` int DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='photo_height'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `photo_height` int DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='final_sha256'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `final_sha256` varchar(64) DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND COLUMN_NAME='source_cleaned_at'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `source_cleaned_at` datetime DEFAULT NULL');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_open_tiktok_media' AND INDEX_NAME='idx_photo_conversion_recovery'), 'SELECT 1', 'ALTER TABLE `tk_open_tiktok_media` ADD INDEX `idx_photo_conversion_recovery` (media_type,status,conversion_lease_until)');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;

SET @tk_photo_ddl = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tk_upload_session' AND INDEX_NAME='idx_photo_conversion_recovery'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD INDEX `idx_photo_conversion_recovery` (purpose,status,conversion_lease_until)');
PREPARE tk_photo_stmt FROM @tk_photo_ddl;
EXECUTE tk_photo_stmt;
DEALLOCATE PREPARE tk_photo_stmt;
