SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'segment_duration_config'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `segment_duration_config` text DEFAULT NULL COMMENT ''用户自定义用途秒数配置'' AFTER `clip_seconds`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
