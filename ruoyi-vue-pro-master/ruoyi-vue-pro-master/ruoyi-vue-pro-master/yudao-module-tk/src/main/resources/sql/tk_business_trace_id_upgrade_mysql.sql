SET NAMES utf8mb4;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_business_log' AND column_name = 'business_trace_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_business_log` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_business_log' AND index_name = 'idx_tk_business_log_trace'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `tk_business_log` ADD KEY `idx_tk_business_log_trace` (`tenant_id`, `business_trace_id`, `create_time`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'business_trace_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_trace'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_trace` (`tenant_id`, `business_trace_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND column_name = 'business_trace_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_tiktok_publish_task` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND index_name = 'idx_tk_tiktok_publish_task_trace'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `tk_tiktok_publish_task` ADD KEY `idx_tk_tiktok_publish_task_trace` (`tenant_id`, `business_trace_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'business_trace_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_detail' AND index_name = 'idx_tk_tiktok_publish_detail_trace'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `tk_tiktok_publish_detail` ADD KEY `idx_tk_tiktok_publish_detail_trace` (`tenant_id`, `business_trace_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
