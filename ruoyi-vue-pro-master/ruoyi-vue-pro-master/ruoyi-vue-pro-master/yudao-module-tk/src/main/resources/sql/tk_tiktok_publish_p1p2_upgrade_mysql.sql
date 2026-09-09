SET NAMES utf8mb4;

SET @schema_name := DATABASE();

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_post')
        AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_post' AND column_name = 'title' AND column_type <> 'text'),
    'ALTER TABLE `tk_tiktok_publish_post` MODIFY COLUMN `title` TEXT NULL', 'SELECT 1');
PREPARE tk_publish_post_title_stmt FROM @sql;
EXECUTE tk_publish_post_title_stmt;
DEALLOCATE PREPARE tk_publish_post_title_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_content_video')
        AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_content_video' AND column_name = 'title' AND column_type <> 'text'),
    'ALTER TABLE `tk_tiktok_content_video` MODIFY COLUMN `title` TEXT NULL', 'SELECT 1');
PREPARE tk_content_video_title_stmt FROM @sql;
EXECUTE tk_content_video_title_stmt;
DEALLOCATE PREPARE tk_content_video_title_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'link_capture_status'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `link_capture_status` varchar(32) DEFAULT NULL COMMENT ''公开视频链接状态'' AFTER `publish_url`');
PREPARE tk_publish_detail_link_status_stmt FROM @sql;
EXECUTE tk_publish_detail_link_status_stmt;
DEALLOCATE PREPARE tk_publish_detail_link_status_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'link_retry_count'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `link_retry_count` int NOT NULL DEFAULT 0 COMMENT ''公开视频链接重试次数'' AFTER `link_capture_status`');
PREPARE tk_publish_detail_link_retry_stmt FROM @sql;
EXECUTE tk_publish_detail_link_retry_stmt;
DEALLOCATE PREPARE tk_publish_detail_link_retry_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'link_next_retry_time'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `link_next_retry_time` datetime DEFAULT NULL COMMENT ''下次公开视频链接重试时间'' AFTER `link_retry_count`');
PREPARE tk_publish_detail_link_next_retry_stmt FROM @sql;
EXECUTE tk_publish_detail_link_next_retry_stmt;
DEALLOCATE PREPARE tk_publish_detail_link_next_retry_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'link_last_error'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `link_last_error` varchar(512) DEFAULT NULL COMMENT ''公开视频链接最近错误'' AFTER `link_next_retry_time`');
PREPARE tk_publish_detail_link_error_stmt FROM @sql;
EXECUTE tk_publish_detail_link_error_stmt;
DEALLOCATE PREPARE tk_publish_detail_link_error_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'public_post_count'), 'SELECT 1', 'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `public_post_count` int NOT NULL DEFAULT 0 COMMENT ''公开视频数量'' AFTER `link_last_error`');
PREPARE tk_publish_detail_public_count_stmt FROM @sql;
EXECUTE tk_publish_detail_public_count_stmt;
DEALLOCATE PREPARE tk_publish_detail_public_count_stmt;

CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `company_id` bigint NOT NULL,
  `publish_detail_id` bigint NOT NULL,
  `publish_task_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `publish_id` varchar(128) DEFAULT NULL,
  `public_post_id` varchar(128) NOT NULL,
  `share_url` varchar(512) DEFAULT NULL,
  `embed_link` varchar(512) DEFAULT NULL,
  `embed_html` text,
  `title` text,
  `video_description` varchar(2200) DEFAULT NULL,
  `video_create_time` bigint DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `cover_url` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PUBLICLY_AVAILABLE',
  `fail_reason` varchar(512) DEFAULT NULL,
  `first_seen_time` datetime DEFAULT NULL,
  `last_sync_time` datetime DEFAULT NULL,
  `no_longer_public_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_publish_post_detail_public` (`tenant_id`, `publish_detail_id`, `public_post_id`, `deleted`),
  KEY `idx_tk_publish_post_task` (`tenant_id`, `publish_task_id`),
  KEY `idx_tk_publish_post_account_status` (`tenant_id`, `account_id`, `status`),
  KEY `idx_tk_publish_post_create_time` (`tenant_id`, `video_create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok 发布产生的公开视频';

CREATE TABLE IF NOT EXISTS `tk_tiktok_content_video` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `company_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `open_id` varchar(128) DEFAULT NULL,
  `video_id` varchar(128) NOT NULL,
  `title` text,
  `video_description` varchar(2200) DEFAULT NULL,
  `share_url` varchar(512) DEFAULT NULL,
  `embed_link` varchar(512) DEFAULT NULL,
  `embed_html` text,
  `cover_image_url` varchar(512) DEFAULT NULL,
  `video_create_time` bigint DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `width` int DEFAULT NULL,
  `like_count` bigint DEFAULT NULL,
  `comment_count` bigint DEFAULT NULL,
  `share_count` bigint DEFAULT NULL,
  `view_count` bigint DEFAULT NULL,
  `aigc` bit(1) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PUBLIC',
  `fail_reason` varchar(512) DEFAULT NULL,
  `last_sync_time` datetime DEFAULT NULL,
  `no_longer_public_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_content_video_account_video` (`tenant_id`, `account_id`, `video_id`, `deleted`),
  KEY `idx_tk_content_video_company_status` (`tenant_id`, `company_id`, `status`),
  KEY `idx_tk_content_video_create_time` (`tenant_id`, `video_create_time`),
  KEY `idx_tk_content_video_account_status_time` (`tenant_id`, `account_id`, `status`, `video_create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok 账号公开视频';

CREATE TABLE IF NOT EXISTS `tk_tiktok_webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(128) NOT NULL,
  `client_key` varchar(128) DEFAULT NULL,
  `user_open_id` varchar(128) DEFAULT NULL,
  `event_type` varchar(128) NOT NULL,
  `publish_id` varchar(128) DEFAULT NULL,
  `post_id` varchar(128) DEFAULT NULL,
  `payload_json` mediumtext NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'RECEIVED',
  `fail_reason` varchar(512) DEFAULT NULL,
  `received_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_webhook_event_id` (`event_id`),
  KEY `idx_tk_webhook_event_publish` (`publish_id`, `event_type`),
  KEY `idx_tk_webhook_event_status` (`status`, `received_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok Webhook 事件';

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account' AND column_name = 'active_open_id'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `active_open_id` varchar(128) GENERATED ALWAYS AS (IF(`deleted` = 0, `open_id`, NULL)) STORED AFTER `deleted`', 'SELECT 1');
PREPARE tk_account_active_open_id_column_stmt FROM @sql;
EXECUTE tk_account_active_open_id_column_stmt;
DEALLOCATE PREPARE tk_account_active_open_id_column_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account' AND index_name = 'uk_tk_tiktok_account_open_id'),
    'ALTER TABLE `tk_tiktok_account` DROP INDEX `uk_tk_tiktok_account_open_id`', 'SELECT 1');
PREPARE tk_account_legacy_unique_stmt FROM @sql;
EXECUTE tk_account_legacy_unique_stmt;
DEALLOCATE PREPARE tk_account_legacy_unique_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account' AND index_name = 'uk_tk_tiktok_account_active_open_id'),
    'ALTER TABLE `tk_tiktok_account` ADD UNIQUE KEY `uk_tk_tiktok_account_active_open_id` (`tenant_id`, `active_open_id`)', 'SELECT 1');
PREPARE tk_account_active_unique_stmt FROM @sql;
EXECUTE tk_account_active_unique_stmt;
DEALLOCATE PREPARE tk_account_active_unique_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = @schema_name AND table_name = 'tk_api_key_config'
                         AND column_name = 'config_value'),
    'UPDATE `tk_api_key_config` SET `config_value` = CONCAT(TRIM(TRAILING '','' FROM `config_value`), '',video.list'') WHERE `provider` = ''TIKTOK'' AND `config_key` = ''default-scopes'' AND `config_value` <> '''' AND FIND_IN_SET(''video.list'', `config_value`) = 0',
    'SELECT 1');
PREPARE tk_tiktok_video_list_scope_stmt FROM @sql;
EXECUTE tk_tiktok_video_list_scope_stmt;
DEALLOCATE PREPARE tk_tiktok_video_list_scope_stmt;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6040, 'TikTok 内容展示', 'tk:tiktok-content-display:query', 2, 5, 6000, 'tiktok-content-display', 'ep:video-camera', 'tk/tiktok-content-display/index', 'TkTiktokContentDisplay', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6041, 'TikTok 内容同步', 'tk:tiktok-content-display:sync', 3, 1, 6040, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `status` = VALUES(`status`), `visible` = VALUES(`visible`),
  `keep_alive` = VALUES(`keep_alive`), `always_show` = VALUES(`always_show`), `deleted` = VALUES(`deleted`);
