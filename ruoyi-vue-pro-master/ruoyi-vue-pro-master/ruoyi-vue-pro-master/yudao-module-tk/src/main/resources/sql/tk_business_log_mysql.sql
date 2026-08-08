SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tk_business_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '业务日志编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '业务流水号',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '业务编号',
  `level` varchar(16) NOT NULL DEFAULT 'INFO' COMMENT '日志级别：INFO/WARN/ERROR',
  `action` varchar(64) NOT NULL COMMENT '业务动作',
  `status` varchar(32) DEFAULT NULL COMMENT '业务状态',
  `message` varchar(512) DEFAULT NULL COMMENT '摘要',
  `detail_json` text DEFAULT NULL COMMENT '详情 JSON',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tk_business_log_trace` (`tenant_id`, `business_trace_id`, `create_time`),
  KEY `idx_tk_business_log_biz` (`tenant_id`, `biz_type`, `biz_id`),
  KEY `idx_tk_business_log_level_time` (`level`, `create_time`),
  KEY `idx_tk_business_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 统一业务日志';
