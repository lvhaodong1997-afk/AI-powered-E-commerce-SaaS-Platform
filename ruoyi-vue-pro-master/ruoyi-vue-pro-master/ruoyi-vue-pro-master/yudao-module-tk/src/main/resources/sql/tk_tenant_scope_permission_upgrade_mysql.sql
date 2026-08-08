SET NAMES utf8mb4;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6025, '公司管理', 'tk:company:query', 2, 5, 6000, 'company', 'ep:office-building', 'tk/company/index', 'TkCompany', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6026, '公司创建', 'tk:company:create', 3, 1, 6025, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6027, '公司编辑', 'tk:company:update', 3, 2, 6025, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6028, '公司删除', 'tk:company:delete', 3, 3, 6025, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6029, '业务日志', 'tk:business-log:query', 2, 6, 6000, 'business-log', 'ep:document', 'tk/business-log/index', 'TkBusinessLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`),
  `visible` = VALUES(`visible`);

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND index_name = 'idx_tk_generation_task_tenant_creator'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_tenant_creator` (`tenant_id`, `creator`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND index_name = 'idx_tk_reference_analysis_tenant_creator'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `tk_reference_analysis` ADD KEY `idx_tk_reference_analysis_tenant_creator` (`tenant_id`, `creator`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_tiktok_publish_task' AND index_name = 'idx_tk_publish_task_tenant_creator'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `tk_tiktok_publish_task` ADD KEY `idx_tk_publish_task_tenant_creator` (`tenant_id`, `creator`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'tk_business_log' AND index_name = 'idx_tk_business_log_tenant_operator'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `tk_business_log` ADD KEY `idx_tk_business_log_tenant_operator` (`tenant_id`, `operator_id`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
