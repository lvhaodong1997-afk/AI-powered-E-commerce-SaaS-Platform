CREATE TABLE IF NOT EXISTS `tk_bgm_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BGM 编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `company_id` bigint DEFAULT NULL COMMENT '公司编号',
  `name` varchar(100) NOT NULL COMMENT 'BGM 名称',
  `source_type` varchar(20) NOT NULL COMMENT '来源类型：SYSTEM系统，USER用户上传',
  `style` varchar(32) DEFAULT NULL COMMENT '音乐风格',
  `file_url` varchar(512) NOT NULL COMMENT '音频文件地址',
  `duration` int DEFAULT NULL COMMENT '音频时长秒',
  `format` varchar(16) DEFAULT NULL COMMENT '文件格式',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1可用，0停用',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_bgm_asset_scope` (`tenant_id`, `company_id`, `source_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK BGM 音乐素材';

SET @bgm_enabled_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'bgm_enabled'
);
SET @bgm_enabled_sql = IF(@bgm_enabled_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `bgm_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否启用背景音乐'' AFTER `audio_url`',
  'SELECT ''tk_generation_task.bgm_enabled already exists''');
PREPARE stmt FROM @bgm_enabled_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @bgm_asset_id_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'bgm_asset_id'
);
SET @bgm_asset_id_sql = IF(@bgm_asset_id_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `bgm_asset_id` bigint DEFAULT NULL COMMENT ''BGM 素材编号'' AFTER `bgm_enabled`',
  'SELECT ''tk_generation_task.bgm_asset_id already exists''');
PREPARE stmt FROM @bgm_asset_id_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @bgm_source_type_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'bgm_source_type'
);
SET @bgm_source_type_sql = IF(@bgm_source_type_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `bgm_source_type` varchar(20) DEFAULT NULL COMMENT ''BGM 来源类型'' AFTER `bgm_asset_id`',
  'SELECT ''tk_generation_task.bgm_source_type already exists''');
PREPARE stmt FROM @bgm_source_type_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @bgm_url_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'bgm_url'
);
SET @bgm_url_sql = IF(@bgm_url_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `bgm_url` varchar(512) DEFAULT NULL COMMENT ''BGM 音频地址'' AFTER `bgm_source_type`',
  'SELECT ''tk_generation_task.bgm_url already exists''');
PREPARE stmt FROM @bgm_url_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @bgm_volume_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'bgm_volume'
);
SET @bgm_volume_sql = IF(@bgm_volume_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `bgm_volume` decimal(4,3) DEFAULT NULL COMMENT ''BGM 混音音量'' AFTER `bgm_url`',
  'SELECT ''tk_generation_task.bgm_volume already exists''');
PREPARE stmt FROM @bgm_volume_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
