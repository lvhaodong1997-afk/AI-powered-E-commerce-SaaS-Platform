SET NAMES utf8mb4;

SET @tk_publish_task_generation_nullable := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'generation_task_id'
);
SET @tk_publish_task_generation_sql := IF(@tk_publish_task_generation_nullable = 1,
  'ALTER TABLE `tk_tiktok_publish_task` MODIFY COLUMN `generation_task_id` bigint DEFAULT NULL COMMENT ''生成任务编号''',
  'SELECT 1');
PREPARE tk_publish_task_generation_stmt FROM @tk_publish_task_generation_sql;
EXECUTE tk_publish_task_generation_stmt;
DEALLOCATE PREPARE tk_publish_task_generation_stmt;

SET @tk_publish_task_uploaded_video_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'uploaded_video_id'
);
SET @tk_publish_task_uploaded_video_sql := IF(@tk_publish_task_uploaded_video_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `uploaded_video_id` bigint DEFAULT NULL COMMENT ''用户上传素材视频编号'' AFTER `generation_task_id`',
  'SELECT 1');
PREPARE tk_publish_task_uploaded_video_stmt FROM @tk_publish_task_uploaded_video_sql;
EXECUTE tk_publish_task_uploaded_video_stmt;
DEALLOCATE PREPARE tk_publish_task_uploaded_video_stmt;

SET @tk_publish_task_source_type_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'source_type'
);
SET @tk_publish_task_source_type_sql := IF(@tk_publish_task_source_type_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `source_type` varchar(32) NOT NULL DEFAULT ''GENERATED'' COMMENT ''视频来源'' AFTER `uploaded_video_id`',
  'SELECT 1');
PREPARE tk_publish_task_source_type_stmt FROM @tk_publish_task_source_type_sql;
EXECUTE tk_publish_task_source_type_stmt;
DEALLOCATE PREPARE tk_publish_task_source_type_stmt;

SET @tk_publish_task_cover_url_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'cover_url'
);
SET @tk_publish_task_cover_url_sql := IF(@tk_publish_task_cover_url_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `cover_url` varchar(512) DEFAULT NULL COMMENT ''封面地址'' AFTER `video_url`',
  'SELECT 1');
PREPARE tk_publish_task_cover_url_stmt FROM @tk_publish_task_cover_url_sql;
EXECUTE tk_publish_task_cover_url_stmt;
DEALLOCATE PREPARE tk_publish_task_cover_url_stmt;

SET @tk_publish_task_cover_timestamp_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'cover_timestamp_ms'
);
SET @tk_publish_task_cover_timestamp_sql := IF(@tk_publish_task_cover_timestamp_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `cover_timestamp_ms` bigint DEFAULT NULL COMMENT ''视频封面时间点（毫秒）'' AFTER `cover_url`',
  'SELECT 1');
PREPARE tk_publish_task_cover_timestamp_stmt FROM @tk_publish_task_cover_timestamp_sql;
EXECUTE tk_publish_task_cover_timestamp_stmt;
DEALLOCATE PREPARE tk_publish_task_cover_timestamp_stmt;

SET @tk_publish_detail_generation_nullable := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'generation_task_id'
);
SET @tk_publish_detail_generation_sql := IF(@tk_publish_detail_generation_nullable = 1,
  'ALTER TABLE `tk_tiktok_publish_detail` MODIFY COLUMN `generation_task_id` bigint DEFAULT NULL COMMENT ''生成任务编号''',
  'SELECT 1');
PREPARE tk_publish_detail_generation_stmt FROM @tk_publish_detail_generation_sql;
EXECUTE tk_publish_detail_generation_stmt;
DEALLOCATE PREPARE tk_publish_detail_generation_stmt;

SET @tk_publish_detail_uploaded_video_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'uploaded_video_id'
);
SET @tk_publish_detail_uploaded_video_sql := IF(@tk_publish_detail_uploaded_video_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `uploaded_video_id` bigint DEFAULT NULL COMMENT ''用户上传素材视频编号'' AFTER `generation_task_id`',
  'SELECT 1');
PREPARE tk_publish_detail_uploaded_video_stmt FROM @tk_publish_detail_uploaded_video_sql;
EXECUTE tk_publish_detail_uploaded_video_stmt;
DEALLOCATE PREPARE tk_publish_detail_uploaded_video_stmt;

SET @tk_publish_detail_source_type_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'source_type'
);
SET @tk_publish_detail_source_type_sql := IF(@tk_publish_detail_source_type_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `source_type` varchar(32) NOT NULL DEFAULT ''GENERATED'' COMMENT ''视频来源'' AFTER `uploaded_video_id`',
  'SELECT 1');
PREPARE tk_publish_detail_source_type_stmt FROM @tk_publish_detail_source_type_sql;
EXECUTE tk_publish_detail_source_type_stmt;
DEALLOCATE PREPARE tk_publish_detail_source_type_stmt;

SET @tk_publish_detail_cover_url_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'cover_url'
);
SET @tk_publish_detail_cover_url_sql := IF(@tk_publish_detail_cover_url_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `cover_url` varchar(512) DEFAULT NULL COMMENT ''封面地址'' AFTER `privacy_level`',
  'SELECT 1');
PREPARE tk_publish_detail_cover_url_stmt FROM @tk_publish_detail_cover_url_sql;
EXECUTE tk_publish_detail_cover_url_stmt;
DEALLOCATE PREPARE tk_publish_detail_cover_url_stmt;

SET @tk_publish_detail_cover_timestamp_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'cover_timestamp_ms'
);
SET @tk_publish_detail_cover_timestamp_sql := IF(@tk_publish_detail_cover_timestamp_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `cover_timestamp_ms` bigint DEFAULT NULL COMMENT ''视频封面时间点（毫秒）'' AFTER `cover_url`',
  'SELECT 1');
PREPARE tk_publish_detail_cover_timestamp_stmt FROM @tk_publish_detail_cover_timestamp_sql;
EXECUTE tk_publish_detail_cover_timestamp_stmt;
DEALLOCATE PREPARE tk_publish_detail_cover_timestamp_stmt;
