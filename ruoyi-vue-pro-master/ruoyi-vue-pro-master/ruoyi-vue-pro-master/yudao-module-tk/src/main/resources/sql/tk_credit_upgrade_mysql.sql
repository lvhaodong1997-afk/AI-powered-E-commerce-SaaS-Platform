SET NAMES utf8mb4;

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

INSERT INTO `tk_tenant_credit_account` (`tenant_id`, `total_credits`, `remaining_credits`, `frozen_credits`, `warning_threshold`)
SELECT `id`, IFNULL(`account_count`, 0), IFNULL(`account_count`, 0), 0, 100
FROM `system_tenant`
WHERE `deleted` = b'0'
ON DUPLICATE KEY UPDATE
  `remaining_credits` = GREATEST(0, `remaining_credits` + VALUES(`total_credits`) - `total_credits`),
  `total_credits` = VALUES(`total_credits`),
  `warning_threshold` = VALUES(`warning_threshold`);

INSERT INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'CREDIT', 'reference-analysis-cost', '1', '开始分析扣除积分，可按租户配置', 0),
  (1, 'CREDIT', 'generation-task-cost', '1', '生成混剪视频扣除积分，可按租户配置', 0)
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`), `remark` = VALUES(`remark`);
