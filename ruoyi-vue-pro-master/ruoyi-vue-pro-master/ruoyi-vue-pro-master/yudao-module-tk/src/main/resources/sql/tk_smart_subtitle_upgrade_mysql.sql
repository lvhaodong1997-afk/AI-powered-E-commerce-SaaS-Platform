SET @schema_name = DATABASE();

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_enabled` bit(1) NOT NULL DEFAULT b''1'' COMMENT ''是否烧录字幕'' AFTER `subtitle_url`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_enabled'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_style` varchar(64) DEFAULT ''classic_white'' COMMENT ''字幕样式'' AFTER `subtitle_enabled`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_style'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_position_mode` varchar(64) DEFAULT ''smart_safe'' COMMENT ''字幕位置模式'' AFTER `subtitle_style`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_position_mode'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_keyword_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否关键词高亮'' AFTER `subtitle_position_mode`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_keyword_enabled'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_keywords` varchar(1000) DEFAULT NULL COMMENT ''手动字幕关键词'' AFTER `subtitle_keyword_enabled`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_keywords'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_keyword_mode` varchar(64) DEFAULT ''auto_manual'' COMMENT ''关键词模式'' AFTER `subtitle_keywords`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_keyword_mode'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_karaoke_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否逐字卡拉OK'' AFTER `subtitle_keyword_mode`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_karaoke_enabled'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_active_color` varchar(32) DEFAULT ''#35F27A'' COMMENT ''当前朗读高亮色'' AFTER `subtitle_karaoke_enabled`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_active_color'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_keyword_color` varchar(32) DEFAULT ''#FFD84D'' COMMENT ''关键词高亮色'' AFTER `subtitle_active_color`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_keyword_color'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_font_size` varchar(32) DEFAULT ''medium'' COMMENT ''字幕字号'' AFTER `subtitle_keyword_color`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_font_size'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_timeline_url` varchar(512) DEFAULT NULL COMMENT ''字幕时间轴文件'' AFTER `subtitle_font_size`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_timeline_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_visual_analysis_url` varchar(512) DEFAULT NULL COMMENT ''字幕视觉分析文件'' AFTER `subtitle_timeline_url`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_visual_analysis_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_layout_url` varchar(512) DEFAULT NULL COMMENT ''字幕布局文件'' AFTER `subtitle_visual_analysis_url`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_layout_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `subtitle_ass_url` varchar(512) DEFAULT NULL COMMENT ''ASS 字幕文件'' AFTER `subtitle_layout_url`',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'subtitle_ass_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
