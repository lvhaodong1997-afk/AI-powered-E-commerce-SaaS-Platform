-- Additive/repeatable. Apply before deploying; disabling publish-oss restores legacy writes.
-- Existing session URLs remain NULL (historical material bucket); never rewrite old file URLs.
SET @tk_dual_ddl = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tk_upload_session' AND COLUMN_NAME = 'storage_url'), 'SELECT 1', 'ALTER TABLE `tk_upload_session` ADD COLUMN `storage_url` varchar(2048) DEFAULT NULL COMMENT ''Pinned unsigned OSS destination''');
PREPARE tk_dual_stmt FROM @tk_dual_ddl;
EXECUTE tk_dual_stmt;
DEALLOCATE PREPARE tk_dual_stmt;

CREATE TABLE IF NOT EXISTS `tk_publish_file_copy` (
  `id` char(64) NOT NULL COMMENT 'SHA256 of source bucket/key/version/etag/size and target bucket',
  `source_bucket` varchar(128) NOT NULL,
  `source_key` varchar(1024) NOT NULL COMMENT 'Includes authorized tenant/company or API client scope',
  `source_etag` varchar(128) NOT NULL,
  `source_version` varchar(256) DEFAULT NULL,
  `source_size` bigint NOT NULL,
  `target_url` varchar(2048) NOT NULL,
  `target_etag` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `claim_token` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_copy_recovery` (`status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='On-demand reusable publishing copies; no material migration';
