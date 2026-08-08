CREATE TABLE IF NOT EXISTS `tk_voice_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '音色编号',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(100) NOT NULL COMMENT '音色名称',
  `provider` varchar(32) NOT NULL DEFAULT 'DASHSCOPE' COMMENT '供应商',
  `model` varchar(64) DEFAULT NULL COMMENT '目标模型',
  `voice_code` varchar(160) DEFAULT NULL COMMENT '供应商音色编码',
  `sample_file_url` varchar(512) NOT NULL COMMENT '授权参考音频',
  `preview_file_url` varchar(512) DEFAULT NULL COMMENT '试听音频',
  `status` varchar(20) NOT NULL COMMENT 'CLONING/READY/FAILED/DISABLED',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `language` varchar(20) DEFAULT 'auto' COMMENT '语言',
  `consent_confirmed` bit(1) NOT NULL COMMENT '是否确认授权',
  `consent_operator` bigint DEFAULT NULL COMMENT '授权确认操作人',
  `consent_time` datetime DEFAULT NULL COMMENT '授权确认时间',
  `provider_request_id` varchar(128) DEFAULT NULL COMMENT '供应商请求编号',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `expire_time` datetime DEFAULT NULL COMMENT '供应商音色过期时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最后使用时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_voice_profile_tenant_status` (`tenant_id`, `status`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TK 租户自定义音色';

SET @voice_profile_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'voice_profile_id'
);
SET @voice_profile_column_sql = IF(@voice_profile_column_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `voice_profile_id` bigint DEFAULT NULL COMMENT ''租户自定义音色编号'' AFTER `voice_code`',
  'SELECT ''tk_generation_task.voice_profile_id already exists''');
PREPARE stmt FROM @voice_profile_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `tk_voice_profile`
  MODIFY COLUMN `sample_file_url` varchar(512) DEFAULT NULL COMMENT 'Voice sample URL';

SET @tk_voice_profile_source_type_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'source_type'
);
SET @tk_voice_profile_source_type_sql = IF(@tk_voice_profile_source_type_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `source_type` varchar(32) DEFAULT NULL COMMENT ''Voice source type'' AFTER `voice_code`',
  'SELECT ''tk_voice_profile.source_type already exists''');
PREPARE stmt FROM @tk_voice_profile_source_type_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_mimo_mode_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'mimo_voice_mode'
);
SET @tk_voice_profile_mimo_mode_sql = IF(@tk_voice_profile_mimo_mode_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `mimo_voice_mode` varchar(32) DEFAULT NULL COMMENT ''MiMo voice mode'' AFTER `source_type`',
  'SELECT ''tk_voice_profile.mimo_voice_mode already exists''');
PREPARE stmt FROM @tk_voice_profile_mimo_mode_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_mimo_prompt_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'mimo_voice_prompt'
);
SET @tk_voice_profile_mimo_prompt_sql = IF(@tk_voice_profile_mimo_prompt_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `mimo_voice_prompt` text DEFAULT NULL COMMENT ''MiMo voice design prompt'' AFTER `mimo_voice_mode`',
  'SELECT ''tk_voice_profile.mimo_voice_prompt already exists''');
PREPARE stmt FROM @tk_voice_profile_mimo_prompt_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_mimo_sample_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'mimo_sample_url'
);
SET @tk_voice_profile_mimo_sample_sql = IF(@tk_voice_profile_mimo_sample_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `mimo_sample_url` varchar(512) DEFAULT NULL COMMENT ''MiMo voice clone sample URL'' AFTER `mimo_voice_prompt`',
  'SELECT ''tk_voice_profile.mimo_sample_url already exists''');
PREPARE stmt FROM @tk_voice_profile_mimo_sample_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_tags_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'tags'
);
SET @tk_voice_profile_tags_sql = IF(@tk_voice_profile_tags_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `tags` varchar(255) DEFAULT NULL COMMENT ''Voice tags'' AFTER `mimo_sample_url`',
  'SELECT ''tk_voice_profile.tags already exists''');
PREPARE stmt FROM @tk_voice_profile_tags_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_sort_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'sort'
);
SET @tk_voice_profile_sort_sql = IF(@tk_voice_profile_sort_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `sort` int DEFAULT NULL COMMENT ''Sort order'' AFTER `tags`',
  'SELECT ''tk_voice_profile.sort already exists''');
PREPARE stmt FROM @tk_voice_profile_sort_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_remark_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile' AND column_name = 'remark'
);
SET @tk_voice_profile_remark_sql = IF(@tk_voice_profile_remark_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD COLUMN `remark` varchar(255) DEFAULT NULL COMMENT ''Remark'' AFTER `sort`',
  'SELECT ''tk_voice_profile.remark already exists''');
PREPARE stmt FROM @tk_voice_profile_remark_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_voice_profile_provider_source_idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_voice_profile'
    AND index_name = 'idx_tk_voice_profile_tenant_provider_source'
);
SET @tk_voice_profile_provider_source_idx_sql = IF(@tk_voice_profile_provider_source_idx_exists = 0,
  'ALTER TABLE `tk_voice_profile` ADD INDEX `idx_tk_voice_profile_tenant_provider_source` (`tenant_id`, `provider`, `source_type`)',
  'SELECT ''idx_tk_voice_profile_tenant_provider_source already exists''');
PREPARE stmt FROM @tk_voice_profile_provider_source_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
