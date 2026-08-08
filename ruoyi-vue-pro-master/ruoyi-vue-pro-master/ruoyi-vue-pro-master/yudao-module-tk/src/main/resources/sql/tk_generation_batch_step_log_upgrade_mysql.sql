SET NAMES utf8mb4;
SET @schema_name := DATABASE();

CREATE TABLE IF NOT EXISTS `tk_generation_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Generation batch ID',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant ID',
  `batch_no` varchar(64) NOT NULL COMMENT 'Batch number',
  `name` varchar(160) NOT NULL COMMENT 'Batch name',
  `company_id` bigint NOT NULL COMMENT 'Company ID',
  `library_id` bigint NOT NULL COMMENT 'Material library ID',
  `source_url` varchar(512) DEFAULT NULL COMMENT 'Source URL',
  `target_language` varchar(16) DEFAULT NULL COMMENT 'Target language',
  `script_count` int NOT NULL DEFAULT 0 COMMENT 'Script count',
  `videos_per_script` int NOT NULL DEFAULT 1 COMMENT 'Videos per script',
  `expected_video_count` int NOT NULL DEFAULT 0 COMMENT 'Expected video count',
  `created_task_count` int NOT NULL DEFAULT 0 COMMENT 'Created task count',
  `success_task_count` int NOT NULL DEFAULT 0 COMMENT 'Successful task count',
  `failed_task_count` int NOT NULL DEFAULT 0 COMMENT 'Failed task count',
  `running_task_count` int NOT NULL DEFAULT 0 COMMENT 'Running task count',
  `progress_percent` int NOT NULL DEFAULT 0 COMMENT 'Progress percent',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Status',
  `fail_summary` varchar(512) DEFAULT NULL COMMENT 'Failure summary',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_generation_batch_no` (`tenant_id`, `batch_no`, `deleted`),
  KEY `idx_tk_generation_batch_scope` (`tenant_id`, `company_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK generation batch';

CREATE TABLE IF NOT EXISTS `tk_generation_step_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Step log ID',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant ID',
  `task_id` bigint NOT NULL COMMENT 'Generation task ID',
  `batch_id` bigint DEFAULT NULL COMMENT 'Generation batch ID',
  `step_code` varchar(64) NOT NULL COMMENT 'Step code',
  `step_name` varchar(128) NOT NULL COMMENT 'Step name',
  `status` varchar(32) NOT NULL COMMENT 'Status',
  `start_time` datetime NOT NULL COMMENT 'Start time',
  `end_time` datetime DEFAULT NULL COMMENT 'End time',
  `duration_millis` bigint DEFAULT NULL COMMENT 'Duration in milliseconds',
  `fail_code` varchar(64) DEFAULT NULL COMMENT 'Failure code',
  `fail_reason` varchar(1000) DEFAULT NULL COMMENT 'Failure reason',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT 'Retry count',
  `worker_id` varchar(128) DEFAULT NULL COMMENT 'Worker ID',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted flag',
  PRIMARY KEY (`id`),
  KEY `idx_tk_generation_step_task` (`tenant_id`, `task_id`, `id`),
  KEY `idx_tk_generation_step_batch` (`tenant_id`, `batch_id`, `step_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK generation step log';

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'batch_id'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `batch_id` bigint DEFAULT NULL COMMENT ''Generation batch ID'' AFTER `business_trace_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'script_index'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `script_index` int DEFAULT NULL COMMENT ''Script index in batch'' AFTER `batch_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'video_index'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `video_index` int DEFAULT NULL COMMENT ''Video index for script'' AFTER `script_index`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_batch'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_batch` (`tenant_id`, `batch_id`, `script_index`, `video_index`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
