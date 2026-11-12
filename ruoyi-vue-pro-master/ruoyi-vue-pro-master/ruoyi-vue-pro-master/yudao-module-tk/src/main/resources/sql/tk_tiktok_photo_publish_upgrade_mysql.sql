-- Apply before deploying the photo publishing backend. Safe to rerun on MySQL 5.7/8.
SET @schema_name := DATABASE();
-- Photos use photo_urls; video requests still require a video source.
ALTER TABLE tk_tiktok_publish_task MODIFY COLUMN video_url varchar(512) NULL COMMENT '视频地址，图文任务为空';
ALTER TABLE tk_tiktok_publish_task MODIFY COLUMN caption text NULL COMMENT '发布描述，图文最多 4000 UTF-16 字符';
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'tk_tiktok_publish_task' AND column_name = 'media_type'), 'SELECT 1',
  'ALTER TABLE tk_tiktok_publish_task ADD COLUMN media_type varchar(16) NOT NULL DEFAULT ''VIDEO'' COMMENT ''媒体类型 VIDEO/PHOTO''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'tk_tiktok_publish_task' AND column_name = 'photo_urls'), 'SELECT 1',
  'ALTER TABLE tk_tiktok_publish_task ADD COLUMN photo_urls text NULL COMMENT ''有序图片 URL JSON''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'tk_tiktok_publish_task' AND column_name = 'photo_cover_index'), 'SELECT 1',
  'ALTER TABLE tk_tiktok_publish_task ADD COLUMN photo_cover_index int NULL COMMENT ''图片封面索引，从 0 开始''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'tk_tiktok_publish_task' AND column_name = 'auto_add_music'), 'SELECT 1',
  'ALTER TABLE tk_tiktok_publish_task ADD COLUMN auto_add_music bit(1) NOT NULL DEFAULT b''0'' COMMENT ''自动添加推荐音乐''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
