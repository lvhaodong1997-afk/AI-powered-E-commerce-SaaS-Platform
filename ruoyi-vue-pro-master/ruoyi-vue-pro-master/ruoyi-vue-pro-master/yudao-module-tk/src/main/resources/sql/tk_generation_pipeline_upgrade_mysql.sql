SET NAMES utf8mb4;

-- Adds the real TK generation pipeline fields to existing databases.
SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'voice_code'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `voice_code` varchar(128) DEFAULT NULL COMMENT ''DashScope 音色编码'' AFTER `voice_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'target_language'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `target_language` varchar(16) NOT NULL DEFAULT ''zh-cn'' COMMENT ''文案和配音目标语言'' AFTER `voice_code`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'opening_video_url'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `opening_video_url` varchar(512) DEFAULT NULL COMMENT ''黄金三秒开头视频'' AFTER `voice_code`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'opening_video_name'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `opening_video_name` varchar(255) DEFAULT NULL COMMENT ''黄金三秒文件名'' AFTER `opening_video_url`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'reference_duration'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `reference_duration` int DEFAULT NULL COMMENT ''对标视频时长秒'' AFTER `opening_video_name`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'target_duration'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `target_duration` int DEFAULT NULL COMMENT ''目标成片时长秒'' AFTER `reference_duration`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'clip_seconds'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `clip_seconds` int NOT NULL DEFAULT 3 COMMENT ''素材裁剪秒数'' AFTER `target_duration`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'prompt_text'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `prompt_text` varchar(1000) DEFAULT NULL COMMENT ''AI 提示词'' AFTER `clip_seconds`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'script_text'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `script_text` text DEFAULT NULL COMMENT ''AI 输出文案'' AFTER `prompt_text`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'audio_url'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `audio_url` varchar(512) DEFAULT NULL COMMENT ''配音音频'' AFTER `script_text`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'subtitle_url'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_url` varchar(512) DEFAULT NULL COMMENT ''字幕文件'' AFTER `audio_url`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'clip_plan'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `clip_plan` text DEFAULT NULL COMMENT ''混剪片段清单'' AFTER `subtitle_url`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
