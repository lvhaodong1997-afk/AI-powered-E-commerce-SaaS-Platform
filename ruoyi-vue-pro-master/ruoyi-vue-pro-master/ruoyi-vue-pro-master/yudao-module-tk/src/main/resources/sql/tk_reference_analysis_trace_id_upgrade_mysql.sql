SET NAMES utf8mb4;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'business_trace_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND index_name = 'idx_tk_reference_analysis_trace'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD KEY `idx_tk_reference_analysis_trace` (`tenant_id`, `business_trace_id`, `create_time`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
