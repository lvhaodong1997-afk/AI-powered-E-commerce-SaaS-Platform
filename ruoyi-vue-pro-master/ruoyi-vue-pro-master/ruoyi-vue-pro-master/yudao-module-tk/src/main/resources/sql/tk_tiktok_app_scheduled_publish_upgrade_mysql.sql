-- 本应用 TikTok 定时发布能力增量升级（可重复执行）
SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'scheduled_at') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `scheduled_at` datetime DEFAULT NULL COMMENT ''计划发布时间'' AFTER `fail_reason`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'schedule_status') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `schedule_status` varchar(32) DEFAULT NULL COMMENT ''定时发布状态'' AFTER `scheduled_at`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'schedule_version') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `schedule_version` int NOT NULL DEFAULT 0 COMMENT ''定时发布版本号'' AFTER `schedule_status`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'started_at') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `started_at` datetime DEFAULT NULL COMMENT ''实际开始时间'' AFTER `schedule_version`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'finished_at') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `finished_at` datetime DEFAULT NULL COMMENT ''实际完成时间'' AFTER `started_at`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'scheduled_local_path') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `scheduled_local_path` varchar(1024) DEFAULT NULL COMMENT ''定时发布持久化素材路径'' AFTER `finished_at`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'scheduled_media_status') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `scheduled_media_status` varchar(32) DEFAULT NULL COMMENT ''定时发布素材状态'' AFTER `scheduled_local_path`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'scheduled_media_fail_reason') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `scheduled_media_fail_reason` varchar(512) DEFAULT NULL COMMENT ''定时发布素材失败原因'' AFTER `scheduled_media_status`',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;

SET @tk_sql = IF((SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task'
    AND index_name = 'idx_tk_tiktok_publish_task_schedule') = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD KEY `idx_tk_tiktok_publish_task_schedule` (`status`, `schedule_status`, `scheduled_at`)',
  'SELECT 1'); PREPARE tk_stmt FROM @tk_sql; EXECUTE tk_stmt; DEALLOCATE PREPARE tk_stmt;
