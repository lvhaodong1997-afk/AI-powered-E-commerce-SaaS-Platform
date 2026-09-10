SET @schema_name := DATABASE();

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'public_post_id'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `public_post_id` varchar(128) DEFAULT NULL COMMENT ''TikTok 公开视频编号'' AFTER `publish_url`',
    'SELECT 1');
PREPARE tk_open_publish_detail_public_post_id_stmt FROM @sql;
EXECUTE tk_open_publish_detail_public_post_id_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_public_post_id_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'view_count'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `view_count` bigint DEFAULT NULL COMMENT ''TikTok 播放量'' AFTER `public_post_id`',
    'SELECT 1');
PREPARE tk_open_publish_detail_view_count_stmt FROM @sql;
EXECUTE tk_open_publish_detail_view_count_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_view_count_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'like_count'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `like_count` bigint DEFAULT NULL COMMENT ''TikTok 点赞数'' AFTER `view_count`',
    'SELECT 1');
PREPARE tk_open_publish_detail_like_count_stmt FROM @sql;
EXECUTE tk_open_publish_detail_like_count_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_like_count_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'comment_count'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `comment_count` bigint DEFAULT NULL COMMENT ''TikTok 评论数'' AFTER `like_count`',
    'SELECT 1');
PREPARE tk_open_publish_detail_comment_count_stmt FROM @sql;
EXECUTE tk_open_publish_detail_comment_count_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_comment_count_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'share_count'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `share_count` bigint DEFAULT NULL COMMENT ''TikTok 分享数'' AFTER `comment_count`',
    'SELECT 1');
PREPARE tk_open_publish_detail_share_count_stmt FROM @sql;
EXECUTE tk_open_publish_detail_share_count_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_share_count_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'metrics_status'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `metrics_status` varchar(32) DEFAULT NULL COMMENT ''视频指标状态'' AFTER `share_count`',
    'SELECT 1');
PREPARE tk_open_publish_detail_metrics_status_stmt FROM @sql;
EXECUTE tk_open_publish_detail_metrics_status_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_metrics_status_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'metrics_fail_reason'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `metrics_fail_reason` varchar(1024) DEFAULT NULL COMMENT ''视频指标失败原因'' AFTER `metrics_status`',
    'SELECT 1');
PREPARE tk_open_publish_detail_metrics_fail_reason_stmt FROM @sql;
EXECUTE tk_open_publish_detail_metrics_fail_reason_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_metrics_fail_reason_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND column_name = 'metrics_last_sync_time'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD COLUMN `metrics_last_sync_time` datetime DEFAULT NULL COMMENT ''视频指标同步时间'' AFTER `metrics_fail_reason`',
    'SELECT 1');
PREPARE tk_open_publish_detail_metrics_last_sync_stmt FROM @sql;
EXECUTE tk_open_publish_detail_metrics_last_sync_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_metrics_last_sync_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_detail')
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_detail' AND index_name = 'idx_tk_open_publish_detail_metrics'),
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD KEY `idx_tk_open_publish_detail_metrics` (`metrics_status`, `metrics_last_sync_time`)',
    'SELECT 1');
PREPARE tk_open_publish_detail_metrics_index_stmt FROM @sql;
EXECUTE tk_open_publish_detail_metrics_index_stmt;
DEALLOCATE PREPARE tk_open_publish_detail_metrics_index_stmt;

UPDATE `tk_tiktok_account`
SET `fail_reason` = NULL
WHERE `auth_status` = 'AUTHORIZED'
  AND `token_status` IN ('VALID', 'NORMAL')
  AND `status` = 0
  AND `fail_reason` = '用户已解绑 TikTok 授权';
