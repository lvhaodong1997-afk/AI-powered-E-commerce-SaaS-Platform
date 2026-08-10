SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND column_name = 'lease_token'),
  'SELECT ''lease_token already exists''',
  'ALTER TABLE `tk_generation_task` ADD COLUMN `lease_token` varchar(64) DEFAULT NULL COMMENT ''任务租约令牌'' AFTER `heartbeat_time`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND column_name = 'lease_expire_time'),
  'SELECT ''lease_expire_time already exists''',
  'ALTER TABLE `tk_generation_task` ADD COLUMN `lease_expire_time` datetime DEFAULT NULL COMMENT ''任务租约过期时间'' AFTER `lease_token`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
          AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_lease'),
  'SELECT ''idx_tk_generation_task_lease already exists''',
  'ALTER TABLE `tk_generation_task` ADD INDEX `idx_tk_generation_task_lease` (`status`, `lease_expire_time`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `tk_upload_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `upload_id` varchar(64) NOT NULL COMMENT '上传会话编号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_size` bigint NOT NULL COMMENT '文件大小',
  `content_type` varchar(128) DEFAULT NULL COMMENT '内容类型',
  `storage_mode` varchar(16) NOT NULL COMMENT '存储模式',
  `status` varchar(16) NOT NULL COMMENT '会话状态',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `cancelled_time` datetime DEFAULT NULL COMMENT '取消时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_upload_session_upload_id` (`upload_id`, `deleted`),
  KEY `idx_tk_upload_session_owner` (`tenant_id`, `creator`, `status`, `expires_at`),
  KEY `idx_tk_upload_session_library` (`tenant_id`, `library_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 上传会话';
