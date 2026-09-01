SET @schema_name := DATABASE();

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'tenant_id'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT ''租户编号'' AFTER `id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'company_id'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `company_id` bigint DEFAULT NULL COMMENT ''公司编号'' AFTER `tenant_id`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND index_name = 'idx_tenant_company_status'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD KEY `idx_tenant_company_status` (`tenant_id`, `company_id`, `status`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND index_name = 'idx_tenant_creator'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD KEY `idx_tenant_creator` (`tenant_id`, `creator`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
