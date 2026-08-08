SET @schema_name := DATABASE();

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'fail_code'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `fail_code` varchar(64) DEFAULT NULL COMMENT ''失败错误码'' AFTER `fail_reason`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'current_step'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `current_step` varchar(128) DEFAULT NULL COMMENT ''当前执行步骤'' AFTER `fail_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'precheck_result'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `precheck_result` text DEFAULT NULL COMMENT ''生成预检结果'' AFTER `current_step`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'retry_count'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT ''重试次数'' AFTER `precheck_result`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'last_retry_time'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `last_retry_time` datetime DEFAULT NULL COMMENT ''最近重试时间'' AFTER `retry_count`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'worker_id'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `worker_id` varchar(128) DEFAULT NULL COMMENT ''执行节点'' AFTER `last_retry_time`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'heartbeat_time'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `heartbeat_time` datetime DEFAULT NULL COMMENT ''执行心跳时间'' AFTER `worker_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'step_started_at'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `step_started_at` datetime DEFAULT NULL COMMENT ''当前步骤开始时间'' AFTER `heartbeat_time`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'step_finished_at'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD COLUMN `step_finished_at` datetime DEFAULT NULL COMMENT ''当前步骤结束时间'' AFTER `step_started_at`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_queue'),
    'SELECT 1',
    'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_queue` (`status`, `heartbeat_time`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
