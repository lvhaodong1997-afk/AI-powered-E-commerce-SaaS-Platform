SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND column_name = 'current_step_code'),
  'SELECT ''current_step_code already exists''',
  'ALTER TABLE `tk_generation_task` ADD COLUMN `current_step_code` varchar(64) DEFAULT NULL COMMENT ''当前执行步骤代码'' AFTER `current_step`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND column_name = 'current_step_completed'),
  'SELECT ''current_step_completed already exists''',
  'ALTER TABLE `tk_generation_task` ADD COLUMN `current_step_completed` int DEFAULT NULL COMMENT ''当前步骤已完成数量'' AFTER `current_step_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND column_name = 'current_step_total'),
  'SELECT ''current_step_total already exists''',
  'ALTER TABLE `tk_generation_task` ADD COLUMN `current_step_total` int DEFAULT NULL COMMENT ''当前步骤总数量'' AFTER `current_step_completed`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
