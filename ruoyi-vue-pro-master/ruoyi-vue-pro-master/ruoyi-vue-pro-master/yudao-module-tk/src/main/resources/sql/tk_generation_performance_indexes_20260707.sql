SET @schema_name := DATABASE();

SET @sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task'
          AND index_name = 'idx_tk_generation_task_scope_status_id'
    ),
    'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_scope_status_id` (`tenant_id`, `company_id`, `status`, `id`)',
    'SELECT ''idx_tk_generation_task_scope_status_id already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task'
          AND index_name = 'idx_tk_generation_task_scope_id'
    ),
    'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_scope_id` (`tenant_id`, `company_id`, `id`)',
    'SELECT ''idx_tk_generation_task_scope_id already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task'
          AND index_name = 'idx_tk_generation_task_creator_status_id'
    ),
    'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_creator_status_id` (`tenant_id`, `creator`, `status`, `id`)',
    'SELECT ''idx_tk_generation_task_creator_status_id already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task'
          AND index_name = 'idx_tk_generation_task_creator_id'
    ),
    'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_creator_id` (`tenant_id`, `creator`, `id`)',
    'SELECT ''idx_tk_generation_task_creator_id already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
