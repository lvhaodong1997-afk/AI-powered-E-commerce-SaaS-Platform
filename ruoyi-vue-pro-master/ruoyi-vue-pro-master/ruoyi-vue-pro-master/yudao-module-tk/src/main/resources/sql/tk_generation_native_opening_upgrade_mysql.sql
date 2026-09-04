SET NAMES utf8mb4;

-- Adds selectable native-opening processing and the server-probed opening duration.
SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'opening_process_mode'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `opening_process_mode` varchar(16) DEFAULT NULL COMMENT ''黄金开头处理模式：NATIVE 原生，STANDARD 普通处理'' AFTER `opening_video_name`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'opening_duration_ms'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `opening_duration_ms` bigint DEFAULT NULL COMMENT ''FFprobe 实测黄金开头时长毫秒'' AFTER `opening_process_mode`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
