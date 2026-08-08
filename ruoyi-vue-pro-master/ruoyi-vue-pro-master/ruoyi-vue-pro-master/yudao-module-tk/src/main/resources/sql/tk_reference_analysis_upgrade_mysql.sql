SET NAMES utf8mb4;

SET @tk_generation_voice_code_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'voice_code'
);
SET @tk_generation_voice_code_sql := IF(@tk_generation_voice_code_column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `voice_code` varchar(128) DEFAULT NULL COMMENT ''DashScope 音色编码'' AFTER `voice_id`',
    'SELECT 1');
PREPARE tk_generation_voice_code_stmt FROM @tk_generation_voice_code_sql;
EXECUTE tk_generation_voice_code_stmt;
DEALLOCATE PREPARE tk_generation_voice_code_stmt;

SET @tk_generation_ref_analysis_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'reference_analysis_id'
);
SET @tk_generation_ref_analysis_sql := IF(@tk_generation_ref_analysis_column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `reference_analysis_id` bigint DEFAULT NULL COMMENT ''对标分析编号'' AFTER `voice_code`',
    'SELECT 1');
PREPARE tk_generation_ref_analysis_stmt FROM @tk_generation_ref_analysis_sql;
EXECUTE tk_generation_ref_analysis_stmt;
DEALLOCATE PREPARE tk_generation_ref_analysis_stmt;

SET @tk_generation_script_option_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'script_option_id'
);
SET @tk_generation_script_option_sql := IF(@tk_generation_script_option_column_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `script_option_id` bigint DEFAULT NULL COMMENT ''选中文案方案编号'' AFTER `reference_analysis_id`',
    'SELECT 1');
PREPARE tk_generation_script_option_stmt FROM @tk_generation_script_option_sql;
EXECUTE tk_generation_script_option_stmt;
DEALLOCATE PREPARE tk_generation_script_option_stmt;

CREATE TABLE IF NOT EXISTS `tk_reference_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对标分析编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '业务流水号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `source_url` varchar(512) NOT NULL COMMENT 'TikTok 对标链接',
  `source_domain` varchar(128) DEFAULT NULL COMMENT '来源域名',
  `resolved_video_url` varchar(1024) DEFAULT NULL COMMENT '解析后真实视频地址',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '对标视频封面',
  `product_name` varchar(128) DEFAULT NULL COMMENT '识别产品',
  `video_duration` int DEFAULT NULL COMMENT '视频时长秒',
  `publish_time` varchar(32) DEFAULT NULL COMMENT '发布时间',
  `core_selling_points` varchar(512) DEFAULT NULL COMMENT '核心卖点',
  `target_audience` varchar(512) DEFAULT NULL COMMENT '目标人群',
  `usage_scenarios` varchar(512) DEFAULT NULL COMMENT '使用场景',
  `video_structure` varchar(512) DEFAULT NULL COMMENT '视频结构',
  `analysis_result` text DEFAULT NULL COMMENT '分析结果 JSON',
  `display_analysis_result_zh` text DEFAULT NULL COMMENT '中文展示分析结果 JSON',
  `selling_points` text DEFAULT NULL COMMENT '卖点详情 JSON',
  `display_selling_points_zh` text DEFAULT NULL COMMENT '中文展示卖点详情 JSON',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_reference_analysis_trace` (`tenant_id`, `business_trace_id`, `create_time`),
  KEY `idx_tk_reference_analysis_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_reference_analysis_source` (`library_id`, `source_url`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 对标视频分析';

SET @tk_reference_trace_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'business_trace_id'
);
SET @tk_reference_trace_sql := IF(@tk_reference_trace_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `business_trace_id` varchar(64) DEFAULT NULL COMMENT ''业务流水号'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE tk_reference_trace_stmt FROM @tk_reference_trace_sql;
EXECUTE tk_reference_trace_stmt;
DEALLOCATE PREPARE tk_reference_trace_stmt;

SET @tk_reference_trace_index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND index_name = 'idx_tk_reference_analysis_trace'
);
SET @tk_reference_trace_index_sql := IF(@tk_reference_trace_index_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD KEY `idx_tk_reference_analysis_trace` (`tenant_id`, `business_trace_id`, `create_time`)',
    'SELECT 1');
PREPARE tk_reference_trace_index_stmt FROM @tk_reference_trace_index_sql;
EXECUTE tk_reference_trace_index_stmt;
DEALLOCATE PREPARE tk_reference_trace_index_stmt;

SET @tk_reference_duration_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'reference_duration'
);
SET @tk_reference_duration_sql := IF(@tk_reference_duration_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `reference_duration` int DEFAULT NULL COMMENT ''目标文案时长秒'' AFTER `target_language`',
    'SELECT 1');
PREPARE tk_reference_duration_stmt FROM @tk_reference_duration_sql;
EXECUTE tk_reference_duration_stmt;
DEALLOCATE PREPARE tk_reference_duration_stmt;

SET @tk_reference_resolved_video_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'resolved_video_url'
);
SET @tk_reference_resolved_video_sql := IF(@tk_reference_resolved_video_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `resolved_video_url` varchar(1024) DEFAULT NULL COMMENT ''解析后真实视频地址'' AFTER `source_domain`',
    'SELECT 1');
PREPARE tk_reference_resolved_video_stmt FROM @tk_reference_resolved_video_sql;
EXECUTE tk_reference_resolved_video_stmt;
DEALLOCATE PREPARE tk_reference_resolved_video_stmt;

SET @tk_reference_cover_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'cover_url'
);
SET @tk_reference_cover_sql := IF(@tk_reference_cover_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `cover_url` varchar(512) DEFAULT NULL COMMENT ''对标视频封面'' AFTER `resolved_video_url`',
    'SELECT 1');
PREPARE tk_reference_cover_stmt FROM @tk_reference_cover_sql;
EXECUTE tk_reference_cover_stmt;
DEALLOCATE PREPARE tk_reference_cover_stmt;

SET @tk_reference_display_analysis_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'display_analysis_result_zh'
);
SET @tk_reference_display_analysis_sql := IF(@tk_reference_display_analysis_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `display_analysis_result_zh` text DEFAULT NULL COMMENT ''中文展示分析结果 JSON'' AFTER `analysis_result`',
    'SELECT 1');
PREPARE tk_reference_display_analysis_stmt FROM @tk_reference_display_analysis_sql;
EXECUTE tk_reference_display_analysis_stmt;
DEALLOCATE PREPARE tk_reference_display_analysis_stmt;

SET @tk_reference_display_selling_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_analysis' AND column_name = 'display_selling_points_zh'
);
SET @tk_reference_display_selling_sql := IF(@tk_reference_display_selling_column_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `display_selling_points_zh` text DEFAULT NULL COMMENT ''中文展示卖点详情 JSON'' AFTER `selling_points`',
    'SELECT 1');
PREPARE tk_reference_display_selling_stmt FROM @tk_reference_display_selling_sql;
EXECUTE tk_reference_display_selling_stmt;
DEALLOCATE PREPARE tk_reference_display_selling_stmt;

CREATE TABLE IF NOT EXISTS `tk_reference_script_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文案方案编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `analysis_id` bigint NOT NULL COMMENT '对标分析编号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `option_no` int NOT NULL COMMENT '方案序号',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `points` varchar(255) DEFAULT NULL COMMENT '卖点组合',
  `display_title_zh` varchar(255) DEFAULT NULL COMMENT '中文展示标题',
  `display_points_zh` varchar(255) DEFAULT NULL COMMENT '中文展示卖点组合',
  `estimated_conversion_rate` decimal(5,2) DEFAULT NULL COMMENT '预估转化率',
  `conversion_level` varchar(16) DEFAULT NULL COMMENT '转化等级',
  `script_text` text DEFAULT NULL COMMENT '完整口播文案',
  `display_script_zh` text DEFAULT NULL COMMENT '中文展示口播说明',
  `selected` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认选中',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_reference_script_analysis` (`analysis_id`),
  KEY `idx_tk_reference_script_company` (`tenant_id`, `company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 对标文案方案';

SET @tk_reference_script_display_title_column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_script_option' AND column_name = 'display_title_zh'
);
SET @tk_reference_script_display_title_sql := IF(@tk_reference_script_display_title_column_exists = 0,
    'ALTER TABLE `tk_reference_script_option` ADD COLUMN `display_title_zh` varchar(255) DEFAULT NULL COMMENT ''中文展示标题'' AFTER `points`',
    'SELECT 1');
PREPARE tk_reference_script_display_title_stmt FROM @tk_reference_script_display_title_sql;
EXECUTE tk_reference_script_display_title_stmt;
DEALLOCATE PREPARE tk_reference_script_display_title_stmt;

SET @tk_reference_script_display_points_column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_script_option' AND column_name = 'display_points_zh'
);
SET @tk_reference_script_display_points_sql := IF(@tk_reference_script_display_points_column_exists = 0,
    'ALTER TABLE `tk_reference_script_option` ADD COLUMN `display_points_zh` varchar(255) DEFAULT NULL COMMENT ''中文展示卖点组合'' AFTER `display_title_zh`',
    'SELECT 1');
PREPARE tk_reference_script_display_points_stmt FROM @tk_reference_script_display_points_sql;
EXECUTE tk_reference_script_display_points_stmt;
DEALLOCATE PREPARE tk_reference_script_display_points_stmt;

SET @tk_reference_script_display_script_column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_reference_script_option' AND column_name = 'display_script_zh'
);
SET @tk_reference_script_display_script_sql := IF(@tk_reference_script_display_script_column_exists = 0,
    'ALTER TABLE `tk_reference_script_option` ADD COLUMN `display_script_zh` text DEFAULT NULL COMMENT ''中文展示口播说明'' AFTER `script_text`',
    'SELECT 1');
PREPARE tk_reference_script_display_script_stmt FROM @tk_reference_script_display_script_sql;
EXECUTE tk_reference_script_display_script_stmt;
DEALLOCATE PREPARE tk_reference_script_display_script_stmt;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6018, '对标分析', 'tk:reference:analyze', 3, 2, 6001, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6019, '对标查询', 'tk:reference:query', 3, 3, 6001, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`);
