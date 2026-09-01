CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `company_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_url` varchar(512) NOT NULL,
  `cover_url` varchar(512) DEFAULT NULL,
  `file_size` bigint NOT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'READY',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_tk_publish_media_scope` (`tenant_id`, `company_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TikTok 用户发布视频';

SET @schema_name := DATABASE();
SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_upload_session' AND column_name = 'library_id'),
  'ALTER TABLE `tk_upload_session` MODIFY COLUMN `library_id` bigint DEFAULT NULL COMMENT ''素材库编号，可为空''',
  'SELECT ''library_id column not found'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
