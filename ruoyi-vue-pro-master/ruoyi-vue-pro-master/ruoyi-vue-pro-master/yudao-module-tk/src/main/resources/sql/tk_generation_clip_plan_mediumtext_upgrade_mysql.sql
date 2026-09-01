SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tk_generation_task'
      AND column_name = 'clip_plan'
      AND data_type = 'text'
  ),
  'ALTER TABLE `tk_generation_task` MODIFY COLUMN `clip_plan` mediumtext DEFAULT NULL COMMENT ''混剪片段清单''',
  'SELECT ''clip_plan is already larger than TEXT or does not exist'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
