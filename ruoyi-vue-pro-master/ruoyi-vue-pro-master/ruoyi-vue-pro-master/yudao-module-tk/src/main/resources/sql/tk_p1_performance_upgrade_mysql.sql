SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_daily_sequence'),
  'SELECT ''idx_tk_generation_task_daily_sequence already exists''',
  'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_daily_sequence` (`tenant_id`, `creator`, `create_time`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_library_status_time'),
  'SELECT ''idx_tk_generation_task_library_status_time already exists''',
  'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_library_status_time` (`tenant_id`, `library_id`, `status`, `create_time`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
