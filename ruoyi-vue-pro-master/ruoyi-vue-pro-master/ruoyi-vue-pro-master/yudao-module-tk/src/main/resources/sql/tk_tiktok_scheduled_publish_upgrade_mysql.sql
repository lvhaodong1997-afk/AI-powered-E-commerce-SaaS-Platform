-- Additive migration for scheduled TikTok publishing. Existing immediate tasks are unchanged.
SET @schema_name := DATABASE();

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_media')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_media' AND column_name = 'scheduled_local_path'),
    'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `scheduled_local_path` varchar(2048) DEFAULT NULL COMMENT ''定时发布本地媒体路径'' AFTER `completed_time`',
    'SELECT 1');
PREPARE tk_scheduled_media_path_stmt FROM @sql;
EXECUTE tk_scheduled_media_path_stmt;
DEALLOCATE PREPARE tk_scheduled_media_path_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_media')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_media' AND column_name = 'scheduled_download_status'),
    'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `scheduled_download_status` varchar(32) DEFAULT NULL COMMENT ''定时发布媒体持久化状态'' AFTER `scheduled_local_path`',
    'SELECT 1');
PREPARE tk_scheduled_media_status_stmt FROM @sql;
EXECUTE tk_scheduled_media_status_stmt;
DEALLOCATE PREPARE tk_scheduled_media_status_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_media')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_media' AND column_name = 'scheduled_download_fail_reason'),
    'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `scheduled_download_fail_reason` varchar(1024) DEFAULT NULL COMMENT ''定时发布媒体持久化失败原因'' AFTER `scheduled_download_status`',
    'SELECT 1');
PREPARE tk_scheduled_media_reason_stmt FROM @sql;
EXECUTE tk_scheduled_media_reason_stmt;
DEALLOCATE PREPARE tk_scheduled_media_reason_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_media')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_media' AND column_name = 'scheduled_downloaded_at'),
    'ALTER TABLE `tk_open_tiktok_media` ADD COLUMN `scheduled_downloaded_at` datetime DEFAULT NULL COMMENT ''定时发布媒体持久化时间'' AFTER `scheduled_download_fail_reason`',
    'SELECT 1');
PREPARE tk_scheduled_media_time_stmt FROM @sql;
EXECUTE tk_scheduled_media_time_stmt;
DEALLOCATE PREPARE tk_scheduled_media_time_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_task')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_task' AND column_name = 'scheduled_at'),
    'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `scheduled_at` datetime DEFAULT NULL COMMENT ''计划发布时间'' AFTER `fail_reason`',
    'SELECT 1');
PREPARE tk_scheduled_task_at_stmt FROM @sql;
EXECUTE tk_scheduled_task_at_stmt;
DEALLOCATE PREPARE tk_scheduled_task_at_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_task')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_task' AND column_name = 'schedule_status'),
    'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `schedule_status` varchar(32) DEFAULT NULL COMMENT ''计划发布状态'' AFTER `scheduled_at`',
    'SELECT 1');
PREPARE tk_scheduled_task_status_stmt FROM @sql;
EXECUTE tk_scheduled_task_status_stmt;
DEALLOCATE PREPARE tk_scheduled_task_status_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_task')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_task' AND column_name = 'schedule_version'),
    'ALTER TABLE `tk_open_tiktok_publish_task` ADD COLUMN `schedule_version` int NOT NULL DEFAULT 0 COMMENT ''计划发布版本号'' AFTER `schedule_status`',
    'SELECT 1');
PREPARE tk_scheduled_task_version_stmt FROM @sql;
EXECUTE tk_scheduled_task_version_stmt;
DEALLOCATE PREPARE tk_scheduled_task_version_stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name
                       AND table_name = 'tk_open_tiktok_publish_task')
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
                        AND table_name = 'tk_open_tiktok_publish_task' AND index_name = 'idx_tk_open_publish_task_schedule'),
    'ALTER TABLE `tk_open_tiktok_publish_task` ADD KEY `idx_tk_open_publish_task_schedule` (`status`, `scheduled_at`)',
    'SELECT 1');
PREPARE tk_scheduled_task_index_stmt FROM @sql;
EXECUTE tk_scheduled_task_index_stmt;
DEALLOCATE PREPARE tk_scheduled_task_index_stmt;
