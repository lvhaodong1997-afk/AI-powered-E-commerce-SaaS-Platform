SET NAMES utf8mb4;

UPDATE `system_tenant`
SET `name` = 'TK自动混剪'
WHERE `id` = 1 AND `name` IN ('秀美源码', '芋道源码');

SET @tk_user_level_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'system_users' AND column_name = 'tk_user_level'
);
SET @tk_user_level_sql := IF(@tk_user_level_column_exists = 0,
    'ALTER TABLE `system_users` ADD COLUMN `tk_user_level` varchar(32) NULL DEFAULT ''COMPANY_ADMIN'' COMMENT ''TK 用户级别：PLATFORM_ADMIN/COMPANY_ADMIN/COMPANY_USER''',
    'SELECT 1');
PREPARE tk_user_level_stmt FROM @tk_user_level_sql;
EXECUTE tk_user_level_stmt;
DEALLOCATE PREPARE tk_user_level_stmt;

SET @tk_company_id_column_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'system_users' AND column_name = 'tk_company_id'
);
SET @tk_company_id_sql := IF(@tk_company_id_column_exists = 0,
    'ALTER TABLE `system_users` ADD COLUMN `tk_company_id` bigint NULL COMMENT ''TK 所属公司编号''',
    'SELECT 1');
PREPARE tk_company_id_stmt FROM @tk_company_id_sql;
EXECUTE tk_company_id_stmt;
DEALLOCATE PREPARE tk_company_id_stmt;

UPDATE `system_users`
SET `tk_user_level` = 'PLATFORM_ADMIN', `tk_company_id` = NULL
WHERE `id` = 1;

CREATE TABLE IF NOT EXISTS `tk_company` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公司编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT '公司名称',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 公司';

CREATE TABLE IF NOT EXISTS `tk_material_library` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '素材库编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `name` varchar(128) NOT NULL COMMENT '素材库名称',
  `category` varchar(64) DEFAULT NULL COMMENT '分类',
  `scene` varchar(64) DEFAULT NULL COMMENT '使用场景',
  `material_purpose` varchar(32) NOT NULL DEFAULT 'ECOMMERCE' COMMENT '素材类型：ECOMMERCE/LEAD_GENERATION',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签',
  `description` varchar(512) DEFAULT NULL COMMENT '描述',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面',
  `video_count` int NOT NULL DEFAULT 0 COMMENT '视频数量',
  `total_size` bigint NOT NULL DEFAULT 0 COMMENT '总容量',
  `defaulted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认库',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_material_library_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_material_library_purpose` (`tenant_id`, `material_purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 素材库';

CREATE TABLE IF NOT EXISTS `tk_material_video` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '素材视频编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_url` varchar(512) NOT NULL COMMENT '文件地址',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面地址',
  `duration` bigint DEFAULT NULL COMMENT '时长秒',
  `size` bigint DEFAULT NULL COMMENT '大小字节',
  `resolution` varchar(32) DEFAULT NULL COMMENT '分辨率',
  `format` varchar(16) DEFAULT NULL COMMENT '格式',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签',
  `usage_phase` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '素材用途：ATTENTION/PRODUCT_SHOW/RESULT_EFFECT/GENERAL',
  `segment_type` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '素材分段：S1_HOOK/S2_PAIN/S3_REVEAL/S4_DEMO/S5_PROOF/S6_DETAIL/S7_LIFESTYLE/GENERAL',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_material_video_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_material_video_library` (`library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 素材视频';

CREATE TABLE IF NOT EXISTS `tk_voice_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '音色编号',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(100) NOT NULL COMMENT '音色名称',
  `provider` varchar(32) NOT NULL DEFAULT 'DASHSCOPE' COMMENT '供应商',
  `model` varchar(64) DEFAULT NULL COMMENT '目标模型',
  `voice_code` varchar(160) DEFAULT NULL COMMENT '供应商音色编码',
  `source_type` varchar(32) DEFAULT NULL COMMENT '音色来源类型',
  `mimo_voice_mode` varchar(32) DEFAULT NULL COMMENT 'MiMo 音色模式',
  `mimo_voice_prompt` text DEFAULT NULL COMMENT 'MiMo 音色设计提示词',
  `mimo_sample_url` varchar(512) DEFAULT NULL COMMENT 'MiMo 克隆样本地址',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签',
  `sort` int DEFAULT NULL COMMENT '排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `sample_file_url` varchar(512) DEFAULT NULL COMMENT '授权参考音频',
  `preview_file_url` varchar(512) DEFAULT NULL COMMENT '试听音频',
  `status` varchar(20) NOT NULL COMMENT '复刻状态',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `language` varchar(20) DEFAULT 'auto' COMMENT '语言',
  `consent_confirmed` bit(1) NOT NULL COMMENT '是否确认授权',
  `consent_operator` bigint DEFAULT NULL COMMENT '授权确认操作人',
  `consent_time` datetime DEFAULT NULL COMMENT '授权确认时间',
  `provider_request_id` varchar(128) DEFAULT NULL COMMENT '供应商请求编号',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最后使用时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_voice_profile_tenant_status` (`tenant_id`, `status`, `enabled`),
  KEY `idx_tk_voice_profile_tenant_provider_source` (`tenant_id`, `provider`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 租户自定义音色';

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

CREATE TABLE IF NOT EXISTS `tk_generation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '生成任务编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '业务流水号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `source_url` varchar(512) NOT NULL COMMENT 'TikTok 对标链接',
  `product_id` bigint DEFAULT NULL COMMENT '产品编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `template_id` bigint DEFAULT NULL COMMENT '模板编号',
  `voice_id` bigint DEFAULT NULL COMMENT '配音编号',
  `voice_code` varchar(128) DEFAULT NULL COMMENT 'DashScope 音色编码',
  `voice_profile_id` bigint DEFAULT NULL COMMENT '租户自定义音色编号',
  `voice_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否生成AI口播',
  `target_language` varchar(16) NOT NULL DEFAULT 'zh-cn' COMMENT '文案和配音目标语言',
  `material_purpose` varchar(32) NOT NULL DEFAULT 'ECOMMERCE' COMMENT '素材类型：ECOMMERCE电商素材，LEAD_GENERATION引流素材',
  `product_category_code` varchar(32) DEFAULT NULL COMMENT '商品种类编码',
  `generation_route_code` varchar(64) DEFAULT NULL COMMENT '生成路由编码',
  `generation_route_config` text DEFAULT NULL COMMENT '生成路由配置',
  `analysis_provider` varchar(32) NOT NULL DEFAULT 'GEMINI' COMMENT '分析引擎',
  `analysis_model` varchar(64) DEFAULT NULL COMMENT '分析模型',
  `reference_analysis_id` bigint DEFAULT NULL COMMENT '对标分析编号',
  `script_option_id` bigint DEFAULT NULL COMMENT '选中文案方案编号',
  `opening_video_url` varchar(512) DEFAULT NULL COMMENT '黄金三秒开头视频',
  `opening_video_name` varchar(255) DEFAULT NULL COMMENT '黄金三秒文件名',
  `opening_process_mode` varchar(16) DEFAULT NULL COMMENT '黄金开头处理模式：NATIVE 原生，STANDARD 普通处理',
  `opening_duration_ms` bigint DEFAULT NULL COMMENT 'FFprobe 实测黄金开头时长毫秒',
  `reference_duration` int DEFAULT NULL COMMENT '对标视频时长秒',
  `target_duration` int DEFAULT NULL COMMENT '目标成片时长秒',
  `clip_seconds` int NOT NULL DEFAULT 3 COMMENT '素材裁剪秒数',
  `segment_duration_config` text DEFAULT NULL COMMENT '用户自定义用途秒数配置',
  `prompt_text` text DEFAULT NULL COMMENT 'AI 提示词或手动引流文案',
  `script_text` text DEFAULT NULL COMMENT 'AI 输出文案',
  `segment_timeline` text DEFAULT NULL COMMENT 'AI 输出分段调度时间轴',
  `audio_url` varchar(512) DEFAULT NULL COMMENT '配音音频',
  `bgm_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用背景音乐',
  `bgm_asset_id` bigint DEFAULT NULL COMMENT 'BGM 素材编号',
  `bgm_source_type` varchar(20) DEFAULT NULL COMMENT 'BGM 来源类型',
  `bgm_url` varchar(512) DEFAULT NULL COMMENT 'BGM 音频地址',
  `bgm_volume` decimal(4,3) DEFAULT NULL COMMENT 'BGM 混音音量',
  `subtitle_url` varchar(512) DEFAULT NULL COMMENT '字幕文件',
  `subtitle_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否烧录字幕',
  `subtitle_style` varchar(64) DEFAULT 'classic_white' COMMENT '字幕样式',
  `subtitle_position_mode` varchar(64) DEFAULT 'smart_safe' COMMENT '字幕位置模式',
  `subtitle_keyword_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否关键词高亮',
  `subtitle_keywords` varchar(1000) DEFAULT NULL COMMENT '手动字幕关键词',
  `subtitle_keyword_mode` varchar(64) DEFAULT 'auto_manual' COMMENT '关键词模式',
  `subtitle_karaoke_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否逐字卡拉OK',
  `subtitle_active_color` varchar(32) DEFAULT '#35F27A' COMMENT '当前朗读高亮色',
  `subtitle_keyword_color` varchar(32) DEFAULT '#FFD84D' COMMENT '关键词高亮色',
  `subtitle_font_size` varchar(32) DEFAULT 'medium' COMMENT '字幕字号',
  `subtitle_timeline_url` varchar(512) DEFAULT NULL COMMENT '字幕时间轴文件',
  `subtitle_visual_analysis_url` varchar(512) DEFAULT NULL COMMENT '字幕视觉分析文件',
  `subtitle_layout_url` varchar(512) DEFAULT NULL COMMENT '字幕布局文件',
  `subtitle_ass_url` varchar(512) DEFAULT NULL COMMENT 'ASS 字幕文件',
  `clip_plan` mediumtext DEFAULT NULL COMMENT '混剪片段清单',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `progress` int NOT NULL DEFAULT 0 COMMENT '进度',
  `output_url` varchar(512) DEFAULT NULL COMMENT '输出视频',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `fail_code` varchar(64) DEFAULT NULL COMMENT '失败错误码',
  `current_step` varchar(128) DEFAULT NULL COMMENT '当前执行步骤',
  `precheck_result` text DEFAULT NULL COMMENT '生成预检结果',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `last_retry_time` datetime DEFAULT NULL COMMENT '最近重试时间',
  `worker_id` varchar(128) DEFAULT NULL COMMENT '执行节点',
  `heartbeat_time` datetime DEFAULT NULL COMMENT '执行心跳时间',
  `step_started_at` datetime DEFAULT NULL COMMENT '当前步骤开始时间',
  `step_finished_at` datetime DEFAULT NULL COMMENT '当前步骤结束时间',
  `title` varchar(128) DEFAULT NULL COMMENT '任务标题',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_generation_task_trace` (`tenant_id`, `business_trace_id`),
  KEY `idx_tk_generation_task_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_generation_task_queue` (`status`, `heartbeat_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 智能生成任务';

CREATE TABLE IF NOT EXISTS `tk_generation_route` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Route ID',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant ID, 0 means global default',
  `material_purpose` varchar(32) NOT NULL DEFAULT 'ECOMMERCE' COMMENT 'Material purpose',
  `product_category_code` varchar(32) NOT NULL DEFAULT 'DEFAULT' COMMENT 'Product category code',
  `route_code` varchar(64) NOT NULL COMMENT 'Generation route code',
  `route_name` varchar(128) NOT NULL COMMENT 'Generation route name',
  `route_config` text DEFAULT NULL COMMENT 'Route config JSON',
  `route_version` int NOT NULL DEFAULT 1 COMMENT 'Route version',
  `traffic_weight` int DEFAULT 100 COMMENT 'Traffic weight',
  `ab_group` varchar(32) DEFAULT NULL COMMENT 'A/B group',
  `last_publish_time` datetime DEFAULT NULL COMMENT 'Last publish time',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Enabled flag',
  `remark` varchar(255) DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_generation_route_lookup` (`tenant_id`, `material_purpose`, `product_category_code`, `deleted`),
  KEY `idx_tk_generation_route_lookup` (`tenant_id`, `material_purpose`, `product_category_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK generation route';

CREATE TABLE IF NOT EXISTS `tk_generation_route_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'History ID',
  `route_id` bigint NOT NULL COMMENT 'Route ID',
  `route_version` int NOT NULL COMMENT 'Route version',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant ID, 0 means global default',
  `material_purpose` varchar(32) NOT NULL DEFAULT 'ECOMMERCE' COMMENT 'Material purpose',
  `product_category_code` varchar(32) NOT NULL DEFAULT 'DEFAULT' COMMENT 'Product category code',
  `route_code` varchar(64) NOT NULL COMMENT 'Generation route code',
  `route_name` varchar(128) NOT NULL COMMENT 'Generation route name',
  `route_config` text DEFAULT NULL COMMENT 'Route config JSON',
  `traffic_weight` int DEFAULT 100 COMMENT 'Traffic weight',
  `ab_group` varchar(32) DEFAULT NULL COMMENT 'A/B group',
  `last_publish_time` datetime DEFAULT NULL COMMENT 'Last publish time',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Enabled flag',
  `change_reason` varchar(255) DEFAULT NULL COMMENT 'Change reason',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted flag',
  PRIMARY KEY (`id`),
  KEY `idx_tk_generation_route_history_route` (`route_id`, `route_version`),
  KEY `idx_tk_generation_route_history_lookup` (`tenant_id`, `material_purpose`, `product_category_code`, `route_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK generation route history';

CREATE TABLE IF NOT EXISTS `tk_api_key_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `provider` varchar(32) NOT NULL COMMENT '服务商：GEMINI/DASHSCOPE',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` text NULL COMMENT '配置值',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_api_key_config_provider_key` (`tenant_id`, `provider`, `config_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 外部服务密钥配置';

CREATE TABLE IF NOT EXISTS `tk_tenant_credit_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '积分账户编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `total_credits` bigint NOT NULL DEFAULT 0 COMMENT '总额度',
  `remaining_credits` bigint NOT NULL DEFAULT 0 COMMENT '剩余额度',
  `frozen_credits` bigint NOT NULL DEFAULT 0 COMMENT '在途积分',
  `warning_threshold` bigint NOT NULL DEFAULT 100 COMMENT '低额提醒阈值',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_tenant_credit_account_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 租户积分账户';

CREATE TABLE IF NOT EXISTS `tk_credit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '积分流水编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务编号',
  `action` varchar(32) NOT NULL COMMENT '动作',
  `credits` bigint NOT NULL COMMENT '积分数',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `before_remaining_credits` bigint DEFAULT NULL COMMENT '变更前剩余',
  `after_remaining_credits` bigint DEFAULT NULL COMMENT '变更后剩余',
  `before_frozen_credits` bigint DEFAULT NULL COMMENT '变更前在途',
  `after_frozen_credits` bigint DEFAULT NULL COMMENT '变更后在途',
  `remark` text DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_credit_log_biz` (`tenant_id`, `biz_type`, `biz_id`, `status`),
  KEY `idx_tk_credit_log_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 租户积分流水';

CREATE TABLE IF NOT EXISTS `tk_audio_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '音频导出任务编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `company_id` bigint DEFAULT NULL COMMENT '企业编号',
  `request_id` varchar(64) NOT NULL COMMENT '客户端幂等请求标识',
  `script_text` text NOT NULL COMMENT '配音文案',
  `tts_provider` varchar(32) NOT NULL COMMENT '配音提供方',
  `voice_code` varchar(255) DEFAULT NULL COMMENT 'DashScope 音色编码',
  `voice_profile_id` bigint DEFAULT NULL COMMENT '音色档案编号',
  `mimo_voice_mode` varchar(32) DEFAULT NULL COMMENT 'MiMo 音色模式',
  `mimo_voice_code` varchar(255) DEFAULT NULL COMMENT 'MiMo 预置音色编码',
  `mimo_voice_prompt` text DEFAULT NULL COMMENT 'MiMo 音色设计提示词',
  `mimo_voice_sample_url` varchar(1024) DEFAULT NULL COMMENT 'MiMo 音色克隆样本地址',
  `target_language` varchar(32) NOT NULL COMMENT '配音语言',
  `audio_url` varchar(1024) DEFAULT NULL COMMENT '生成音频地址',
  `status` varchar(32) NOT NULL COMMENT '状态：PROCESSING/SUCCESS/FAILED',
  `fail_reason` text DEFAULT NULL COMMENT '失败原因',
  `credit_log_id` bigint DEFAULT NULL COMMENT '积分流水编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_audio_export_task_request` (`tenant_id`, `request_id`, `deleted`),
  KEY `idx_tk_audio_export_task_status` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 独立音频导出任务';

CREATE TABLE IF NOT EXISTS `tk_reference_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对标分析编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '业务流水号',
  `company_id` bigint NOT NULL COMMENT '公司编号',
  `library_id` bigint NOT NULL COMMENT '素材库编号',
  `source_url` varchar(512) NOT NULL COMMENT 'TikTok 对标链接',
  `target_language` varchar(16) NOT NULL DEFAULT 'zh-cn' COMMENT '文案目标语言',
  `reference_duration` int DEFAULT NULL COMMENT '目标文案时长秒',
  `material_purpose` varchar(32) NOT NULL DEFAULT 'ECOMMERCE' COMMENT '素材类型：ECOMMERCE电商素材，LEAD_GENERATION引流素材',
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
  `segment_timeline` text DEFAULT NULL COMMENT '分段调度时间轴',
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

INSERT INTO `tk_company` (`id`, `tenant_id`, `name`, `status`, `contact_name`, `contact_phone`)
VALUES
  (1, 1, '美妆护肤旗舰公司', 0, '张经理', '13800000001'),
  (2, 1, '3C数码出海公司', 0, '李经理', '13800000002')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `tk_material_library` (`id`, `tenant_id`, `company_id`, `name`, `category`, `scene`, `tags`, `description`, `video_count`, `total_size`, `defaulted`, `status`)
VALUES
  (1, 1, 1, '补水保湿精华', '美妆护肤', '带货混剪', '补水,精华,护肤', '用于 TikTok 电商智能混剪的素材库', 0, 0, b'1', 0),
  (2, 1, 1, '时尚女包', '服饰箱包', '场景展示', '女包,通勤,穿搭', '用于 TikTok 电商智能混剪的素材库', 0, 0, b'0', 0),
  (3, 1, 2, '蓝牙耳机', '3C数码', '功能卖点', '耳机,降噪,开箱', '用于 TikTok 电商智能混剪的素材库', 0, 0, b'1', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `category` = VALUES(`category`),
  `scene` = VALUES(`scene`),
  `tags` = VALUES(`tags`),
  `description` = VALUES(`description`),
  `defaulted` = VALUES(`defaulted`),
  `status` = VALUES(`status`);

INSERT INTO `tk_generation_task` (`id`, `tenant_id`, `company_id`, `source_url`, `library_id`, `status`, `progress`, `title`, `output_url`)
VALUES
  (1, 1, 1, 'https://www.tiktok.com/@demo/video/123456789', 1, 'SUCCESS', 100, '补水精华 · 爆款文案版', '/exports/demo-1.mp4'),
  (2, 1, 1, 'https://www.tiktok.com/@demo/video/223456789', 2, 'SUCCESS', 100, '时尚女包 · 场景展示版', '/exports/demo-2.mp4'),
  (3, 1, 2, 'https://www.tiktok.com/@demo/video/323456789', 3, 'RENDERING', 75, '蓝牙耳机 · 体验测评版', NULL)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'GEMINI', 'api-key', '', 'Gemini 文案生成 API Key，留空时使用环境变量 GEMINI_API_KEY', 0),
  (1, 'GEMINI', 'base-url', 'https://yunwu.ai/v1', 'Gemini 文案生成接口基础地址', 0),
  (1, 'GEMINI', 'text-model', 'gemini-3.1-flash-lite-preview', 'Gemini 文案生成模型', 0),
  (1, 'GEMINI', 'api-format', 'openai', 'Gemini 调用协议：gemini/openai', 0),
  (1, 'GEMINI', 'timeout-seconds', '90', 'Gemini 请求超时时间（秒）', 0),
  (1, 'GEMINI', 'retry-count', '2', 'Gemini 临时失败重试次数', 0),
  (1, 'GEMINI', 'retry-delay-ms', '1500', 'Gemini 重试退避起始间隔（毫秒）', 0),
  (1, 'GEMINI', 'analysis-prompt', '你是 TikTok 跨境电商短视频策略分析师、爆款拆解专家和结构化内容生成模型。\n你的任务是：基于随请求提供的对标视频关键帧、真实视频元数据和链接信息，分析视频内容，提炼可用于 TK 自动混剪 SaaS 系统落库的数据，并生成可用于后续混剪和发布的文案标题方案。\n严格要求：\n1. 只依据关键帧画面、真实视频元数据、可见字幕、可见商品、可见场景进行分析。\n2. 严禁根据素材库名称、素材库类目、素材库场景、素材库标签猜测商品、卖点、人群或视频结构。\n3. 素材库相关字段只能用于后续文案适配，不能作为商品识别证据。\n4. 如果画面或元数据无法确认具体商品，productName 必须写成“未识别具体商品”。\n5. 核心卖点、目标人群、使用场景、视频结构、文案方案必须能被画面或元数据支撑。\n6. 不要输出 Markdown，不要解释，不要补充多余文本，只输出合法 JSON。\n7. 所有表达要适合 TikTok 电商短视频，短句化、强钩子、适合字幕展示。\n8. 不得生成夸大、绝对化、医疗功效、违规承诺或无法从视频证明的内容。\n输出 JSON 结构必须如下：\n{\n  "productName": "产品名；无法确认时写未识别具体商品",\n  "videoDuration": 32,\n  "publishTime": "无法从元数据确认时返回空字符串",\n  "coreSellingPoints": ["核心卖点1", "核心卖点2", "核心卖点3"],\n  "targetAudience": ["目标人群1", "目标人群2"],\n  "usageScenarios": ["使用场景1", "使用场景2"],\n  "videoStructure": ["开头钩子", "产品展示", "功能演示", "场景证明", "行动号召"],\n  "sellingPoints": [\n    {"title": "卖点标题", "desc": "基于画面可见内容提炼的卖点描述", "count": 3, "badge": "核心卖点"}\n  ],\n  "scriptOptions": [\n    {"title": "适合 TikTok 发布的短视频标题", "points": "卖点A｜卖点B", "displayTitleZh": "后台展示中文标题", "displayPointsZh": "后台展示中文卖点", "estimatedConversionRate": 8.92, "conversionLevel": "高", "scriptText": "完整口播文案，短句化，适合字幕和配音", "displayScriptZh": "后台展示中文口播文案"}\n  ]\n}\n文案方案要求：\n1. scriptOptions 必须输出 12 条。\n2. estimatedConversionRate 范围必须在 4.80 到 9.50 之间。\n3. 前 2 条 conversionLevel 为“高”，其余为“中”或“低”。\n4. 每条 title 不超过 60 个中文字符或等量目标语言长度。\n5. scriptText 必须适合 20-60 秒短视频口播，包含：前 3 秒钩子、卖点展开、场景证明、轻行动号召。\n6. 每条文案角度必须不同，避免同质化。\n\n对标链接：{}\n解析到的视频地址：{}\n真实视频时长：{}秒\n真实视频分辨率：{}\n关键帧时间点：{}\n素材库名称（仅用于文案适配，不能作为识别依据）：{}\n素材库类目（仅用于文案适配，不能作为识别依据）：{}\n素材库场景（仅用于文案适配，不能作为识别依据）：{}\n素材库标签（仅用于文案适配，不能作为识别依据）：{}\n用户参考时长：{}秒\n', 'Gemini 对标视频分析提示词模板', 0),
  (1, 'GEMINI', 'script-regeneration-prompt', '你是 TikTok 跨境电商短视频文案策划专家。\n你的任务是：只基于系统中已保存的对标分析结果，重新生成一组新的文案标题方案，用于 TK 自动混剪 SaaS 的文案换一批功能。\n严格要求：\n1. 不要重新分析视频。\n2. 不要改写 productName、核心卖点、目标人群、使用场景、视频结构和卖点细节。\n3. 必须围绕已保存分析结果生成新标题和新口播脚本。\n4. 标题角度必须与上一批明显不同，避免重复表达。\n5. 不要输出 Markdown，不要解释，只输出合法 JSON。\n6. 不得生成夸大、绝对化、医疗功效、违规承诺或分析结果中没有依据的内容。\n输出 JSON 结构必须如下：\n{\n  "scriptOptions": [\n    {"title": "新的 TikTok 短视频标题", "points": "卖点A｜卖点B", "displayTitleZh": "后台展示中文标题", "displayPointsZh": "后台展示中文卖点", "estimatedConversionRate": 8.92, "conversionLevel": "高", "scriptText": "完整口播文案，短句化，适合字幕和配音", "displayScriptZh": "后台展示中文口播文案"}\n  ]\n}\n文案方案要求：\n1. scriptOptions 必须输出 12 条。\n2. estimatedConversionRate 范围必须在 4.80 到 9.50 之间。\n3. 前 2 条 conversionLevel 为“高”，其余为“中”或“低”。\n4. 每条 title 不超过 60 个中文字符或等量目标语言长度。\n5. 每条 scriptText 必须包含：前 3 秒钩子、卖点展开、场景证明、轻行动号召。\n6. 12 条方案要分别覆盖不同角度，例如：痛点型、测评型、反差型、场景型、礼物型、限时型、清单型、对比型。\n\n对标链接：{}\n产品名：{}\n视频时长：{}秒\n核心卖点：{}\n目标人群：{}\n使用场景：{}\n视频结构：{}\n卖点细节 JSON：{}\n素材库名称：{}\n素材库类目：{}\n素材库场景：{}\n素材库标签：{}\n', 'Gemini 重新生成文案方案提示词模板', 0),
  (1, 'GEMINI', 'generation-script-prompt', '你是 TikTok 跨境电商短视频编导、口播脚本策划和混剪节奏设计师。\n你的任务是：基于用户选择的对标视频链接、素材库信息和目标成片时长，生成一条可直接用于 AI 配音、字幕切分和自动混剪的带货脚本。\n严格要求：\n1. 输出纯文案，不要 Markdown，不要解释，不要标题前缀。\n2. 文案必须适合口播配音和逐句字幕。\n3. 每一句尽量短，控制在 8-18 个中文字符或等量目标语言长度。\n4. 前 3 秒必须是强钩子，用来承接用户上传的黄金三秒视频。\n5. 后续结构必须包含：卖点展开、场景证明、信任背书、行动号召。\n6. 文案节奏要适合短视频混剪，每 2-4 秒一个信息点。\n7. 不要生成虚假价格、虚假折扣、绝对化承诺、医疗功效或平台违规表达。\n8. 如果商品信息不足，使用更稳妥的泛化表达，不要编造具体功能。\n9. 默认使用中文；如果后续语言指令要求其他目标语言，以后续语言指令为准。\n10. 不要出现“以下是”“这是一段脚本”等说明性文字。\n内容结构：\n- 第 1 段：黄金三秒钩子，制造停留理由。\n- 第 2 段：指出用户痛点或使用场景。\n- 第 3 段：展示核心卖点。\n- 第 4 段：强化场景证明或使用体验。\n- 第 5 段：轻量行动号召，适合 TikTok 发布。\n\n对标视频链接：{}\n素材库：{}\n类目：{}\n场景：{}\n标签：{}\n目标成片时长：{}秒\n素材规则：黄金三秒开头固定使用用户上传视频；后续素材每段裁剪{}秒；文案需要适配配音和逐句字幕。', 'Gemini 生成任务兜底文案提示词模板', 0),
  (1, 'DASHSCOPE', 'api-key', '', 'DashScope API Key，留空时使用环境变量 DASHSCOPE_API_KEY', 0),
  (1, 'DASHSCOPE', 'video-api-key', '', '百炼视频理解专用 API Key，留空时使用环境变量 DASHSCOPE_VIDEO_API_KEY', 0),
  (1, 'DASHSCOPE', 'tts-url', 'https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer', 'DashScope 非流式语音合成地址', 0),
  (1, 'DASHSCOPE', 'tts-model', 'cosyvoice-v3.5-plus', 'DashScope TTS 模型', 0),
  (1, 'DASHSCOPE', 'voice', '', 'DashScope 默认复刻音色 ID；任务 voiceCode 为空时使用', 0),
  (1, 'DASHSCOPE', 'format', 'mp3', '音频格式', 0),
  (1, 'DASHSCOPE', 'language', 'auto', '默认语言提示，auto 按文案自动识别', 0),
  (1, 'DASHSCOPE', 'instruction', '请以自然、清晰的语气朗读,不要有换气声、吸气声、呼吸声或任何气口,句子之间不要停顿。语速为1.1倍，文字中出现的, '' 不要进行停顿', 'DashScope 朗读指令', 0),
  (1, 'DASHSCOPE', 'workspace-id', '', '百炼华北2北京业务空间 ID', 0),
  (1, 'DASHSCOPE', 'video-model', 'qwen3.7-plus', '百炼视频理解模型', 0),
  (1, 'DASHSCOPE', 'video-fps', '2', '视频理解抽帧频率', 0),
  (1, 'DASHSCOPE', 'video-timeout-seconds', '300', '视频理解超时秒数', 0),
  (1, 'DASHSCOPE', 'video-enable-thinking', 'false', '视频理解思考模式', 0),
  (1, 'DASHSCOPE', 'video-temperature', '0.2', '视频理解温度', 0),
  (1, 'MIMO', 'api-key', '', 'MiMo TTS API Key，留空时使用环境变量 MIMO_API_KEY', 0),
  (1, 'MIMO', 'base-url', 'https://api.xiaomimimo.com/v1', 'MiMo API 基础地址', 0),
  (1, 'MIMO', 'preset-model', 'mimo-v2.5-tts', 'MiMo 预置音色模型', 0),
  (1, 'MIMO', 'voice-design-model', 'mimo-v2.5-tts-voicedesign', 'MiMo 音色设计模型', 0),
  (1, 'MIMO', 'voice-clone-model', 'mimo-v2.5-tts-voiceclone', 'MiMo 音色复刻模型', 0),
  (1, 'MIMO', 'format', 'wav', 'MiMo 音频格式', 0),
  (1, 'MIMO', 'optimize-text-preview', 'true', 'MiMo 音色设计是否优化文本预览', 0),
  (1, 'MIMO', 'default-voice', 'Mia', 'MiMo 默认预置音色', 0),
  (1, 'MIMO', 'timeout-seconds', '120', 'MiMo 请求超时时间（秒）', 0),
  (1, 'TIKTOK', 'client-key', '', 'TikTok Login Kit / Content Posting API Client Key', 0),
  (1, 'TIKTOK', 'client-secret', '', 'TikTok Login Kit / Content Posting API Client Secret', 0),
  (1, 'TIKTOK', 'redirect-uri', '', 'TikTok 官方授权回调地址，需要与开发者后台一致', 0),
  (1, 'TIKTOK', 'default-scopes', 'user.info.basic,video.publish,video.upload,video.list', '默认授权范围', 0),
  (1, 'TIKTOK', 'default-post-mode', 'DIRECT_POST', '默认发布模式：DIRECT_POST/UPLOAD_TO_INBOX', 0),
  (1, 'TIKTOK', 'verified-pull-domain', '', 'TikTok 已验证的视频拉取域名；为空时优先使用文件上传', 0),
  (1, 'TIKTOK', 'token-secret', '', 'TikTok Token AES-GCM 加密密钥，生产环境必须配置', 0),
  (1, 'CREDIT', 'reference-analysis-cost', '1', '开始分析扣除积分，可按租户配置', 0),
  (1, 'CREDIT', 'generation-task-cost', '1', '生成混剪视频扣除积分，可按租户配置', 0)
ON DUPLICATE KEY UPDATE
  `config_value` = IF(VALUES(`provider`) = 'GEMINI'
    AND VALUES(`config_key`) IN ('analysis-prompt', 'script-regeneration-prompt', 'generation-script-prompt'),
    VALUES(`config_value`), `config_value`),
  `remark` = VALUES(`remark`);

INSERT INTO `tk_tenant_credit_account` (`tenant_id`, `total_credits`, `remaining_credits`, `frozen_credits`, `warning_threshold`)
SELECT `id`, IFNULL(`account_count`, 0), IFNULL(`account_count`, 0), 0, 100
FROM `system_tenant`
WHERE `deleted` = b'0'
ON DUPLICATE KEY UPDATE
  `remaining_credits` = GREATEST(0, `remaining_credits` + VALUES(`total_credits`) - `total_credits`),
  `total_credits` = VALUES(`total_credits`),
  `warning_threshold` = VALUES(`warning_threshold`);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6000, 'TK素材工厂', '', 1, 60, 0, '/tk', 'ep:video-play', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6001, '首页', 'tk:dashboard:query', 2, 1, 6000, 'dashboard', 'ep:home-filled', 'tk/dashboard/index', 'TkDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6002, '素材库', 'tk:material-library:query', 2, 2, 6000, 'material-library', 'ep:folder', 'tk/material-library/index', 'TkMaterialLibrary', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6003, '生成记录', 'tk:generation:query', 2, 3, 6000, 'generation', 'ep:video-camera', 'tk/generation/index', 'TkGeneration', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6004, '视频发布中心', 'tk:video-publish-center:query', 2, 4, 6000, 'video-publish-center', 'ep:promotion', 'tk/video-publish-center/index', 'TkVideoPublishCenter', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6011, '素材库创建', 'tk:material-library:create', 3, 1, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6012, '素材库编辑', 'tk:material-library:update', 3, 2, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6013, '素材库删除', 'tk:material-library:delete', 3, 3, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6014, '视频查询', 'tk:material-video:query', 3, 4, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6015, '视频上传', 'tk:material-video:upload', 3, 5, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6016, '视频删除', 'tk:material-video:delete', 3, 6, 6002, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6017, '生成创建', 'tk:generation:create', 3, 1, 6003, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6018, '对标分析', 'tk:reference:analyze', 3, 2, 6001, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6019, '对标查询', 'tk:reference:query', 3, 3, 6001, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6020, 'TikTok账号授权', 'tk:tiktok-account:authorize', 3, 1, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6021, 'TikTok账号配置', 'tk:tiktok-account:update', 3, 2, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6022, 'TikTok账号分组', 'tk:tiktok-account-group:manage', 3, 3, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6023, 'TikTok发布创建', 'tk:tiktok-publish:create', 3, 4, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6024, 'TikTok发布重试', 'tk:tiktok-publish:retry', 3, 5, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`);
