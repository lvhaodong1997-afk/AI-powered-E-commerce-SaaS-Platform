SET @tk_generation_script_title_column_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tk_generation_task'
      AND column_name = 'script_title'
);
SET @tk_generation_script_title_sql := IF(
    @tk_generation_script_title_column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `script_title` varchar(255) DEFAULT NULL COMMENT ''生成时选择的文案标题'' AFTER `script_option_id`',
    'SELECT 1'
);
PREPARE tk_generation_script_title_stmt FROM @tk_generation_script_title_sql;
EXECUTE tk_generation_script_title_stmt;
DEALLOCATE PREPARE tk_generation_script_title_stmt;

UPDATE `tk_generation_task` task
INNER JOIN `tk_reference_script_option` `option`
    ON `option`.`id` = task.`script_option_id`
   AND `option`.`tenant_id` = task.`tenant_id`
   AND `option`.`deleted` = b'0'
SET task.`script_title` = `option`.`title`
WHERE task.`script_option_id` IS NOT NULL
  AND (task.`script_title` IS NULL OR TRIM(task.`script_title`) = '');
