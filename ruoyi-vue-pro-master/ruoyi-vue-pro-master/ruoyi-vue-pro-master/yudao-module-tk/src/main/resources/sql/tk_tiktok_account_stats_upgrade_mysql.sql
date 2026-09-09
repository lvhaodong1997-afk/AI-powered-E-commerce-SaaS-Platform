SET NAMES utf8mb4;

SET @schema_name := DATABASE();

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND column_name = 'follower_count'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `follower_count` bigint DEFAULT NULL COMMENT ''follower count'' AFTER `avatar_url`',
    'SELECT 1');
PREPARE tk_tiktok_account_follower_stmt FROM @sql;
EXECUTE tk_tiktok_account_follower_stmt;
DEALLOCATE PREPARE tk_tiktok_account_follower_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND column_name = 'following_count'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `following_count` bigint DEFAULT NULL COMMENT ''following count'' AFTER `follower_count`',
    'SELECT 1');
PREPARE tk_tiktok_account_following_stmt FROM @sql;
EXECUTE tk_tiktok_account_following_stmt;
DEALLOCATE PREPARE tk_tiktok_account_following_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND column_name = 'likes_count'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `likes_count` bigint DEFAULT NULL COMMENT ''likes count'' AFTER `following_count`',
    'SELECT 1');
PREPARE tk_tiktok_account_likes_stmt FROM @sql;
EXECUTE tk_tiktok_account_likes_stmt;
DEALLOCATE PREPARE tk_tiktok_account_likes_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND column_name = 'video_count'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `video_count` bigint DEFAULT NULL COMMENT ''video count'' AFTER `likes_count`',
    'SELECT 1');
PREPARE tk_tiktok_account_video_count_stmt FROM @sql;
EXECUTE tk_tiktok_account_video_count_stmt;
DEALLOCATE PREPARE tk_tiktok_account_video_count_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND column_name = 'stats_updated_at'),
    'ALTER TABLE `tk_tiktok_account` ADD COLUMN `stats_updated_at` datetime DEFAULT NULL COMMENT ''stats update time'' AFTER `video_count`',
    'SELECT 1');
PREPARE tk_tiktok_account_stats_time_stmt FROM @sql;
EXECUTE tk_tiktok_account_stats_time_stmt;
DEALLOCATE PREPARE tk_tiktok_account_stats_time_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account')
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_account'
                          AND index_name = 'idx_tk_tiktok_account_stats'),
    'ALTER TABLE `tk_tiktok_account` ADD KEY `idx_tk_tiktok_account_stats` (`tenant_id`, `auth_status`, `follower_count`)',
    'SELECT 1');
PREPARE tk_tiktok_account_stats_index_stmt FROM @sql;
EXECUTE tk_tiktok_account_stats_index_stmt;
DEALLOCATE PREPARE tk_tiktok_account_stats_index_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = @schema_name AND table_name = 'tk_api_key_config'
                         AND column_name = 'config_value'),
    'UPDATE `tk_api_key_config` SET `config_value` = CONCAT(TRIM(TRAILING '','' FROM `config_value`), '',user.info.stats'') WHERE `provider` = ''TIKTOK'' AND `config_key` = ''default-scopes'' AND `config_value` <> '''' AND FIND_IN_SET(''user.info.stats'', REPLACE(`config_value`, '' '', '''')) = 0',
    'SELECT 1');
PREPARE tk_tiktok_stats_scope_stmt FROM @sql;
EXECUTE tk_tiktok_stats_scope_stmt;
DEALLOCATE PREPARE tk_tiktok_stats_scope_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = @schema_name AND table_name = 'tk_api_key_config'
                         AND column_name = 'config_value'),
    'UPDATE `tk_api_key_config` SET `config_value` = CONCAT(TRIM(TRAILING '','' FROM `config_value`), '',user.info.profile'') WHERE `provider` = ''TIKTOK'' AND `config_key` = ''default-scopes'' AND `config_value` <> '''' AND FIND_IN_SET(''user.info.profile'', REPLACE(`config_value`, '' '', '''')) = 0',
    'SELECT 1');
PREPARE tk_tiktok_profile_scope_stmt FROM @sql;
EXECUTE tk_tiktok_profile_scope_stmt;
DEALLOCATE PREPARE tk_tiktok_profile_scope_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_content_video')
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                        WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_content_video'
                          AND index_name = 'idx_tk_content_video_account_status_time'),
    'ALTER TABLE `tk_tiktok_content_video` ADD KEY `idx_tk_content_video_account_status_time` (`tenant_id`, `account_id`, `status`, `video_create_time`, `id`)',
    'SELECT 1');
PREPARE tk_tiktok_content_video_stats_index_stmt FROM @sql;
EXECUTE tk_tiktok_content_video_stats_index_stmt;
DEALLOCATE PREPARE tk_tiktok_content_video_stats_index_stmt;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6042, 'TikTok Account Stats', 'tk:tiktok-account-stats:query', 2, 6, 6000, 'tiktok-account-stats', 'ep:data-analysis', 'tk/tiktok-account-stats/index', 'TkTiktokAccountStats', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`), `icon` = VALUES(`icon`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `status` = VALUES(`status`), `visible` = VALUES(`visible`),
  `keep_alive` = VALUES(`keep_alive`), `always_show` = VALUES(`always_show`), `deleted` = VALUES(`deleted`);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.role_id, 6042, 'admin', NOW(), 'admin', NOW(), b'0', rm.tenant_id
FROM `system_role_menu` rm
WHERE rm.menu_id = 6000
  AND rm.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` existing
    WHERE existing.role_id = rm.role_id
      AND existing.menu_id = 6042
      AND existing.deleted = b'0'
  );
