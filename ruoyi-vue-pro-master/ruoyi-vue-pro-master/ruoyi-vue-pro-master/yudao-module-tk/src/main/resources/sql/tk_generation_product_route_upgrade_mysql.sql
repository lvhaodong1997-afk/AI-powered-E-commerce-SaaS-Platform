SET NAMES utf8mb4;

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

SET @route_version_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_route' AND column_name = 'route_version'
);
SET @route_version_sql = IF(@route_version_exists = 0,
  'ALTER TABLE `tk_generation_route` ADD COLUMN `route_version` int NOT NULL DEFAULT 1 COMMENT ''Route version'' AFTER `route_config`',
  'SELECT ''tk_generation_route.route_version already exists''');
PREPARE stmt FROM @route_version_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @traffic_weight_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_route' AND column_name = 'traffic_weight'
);
SET @traffic_weight_sql = IF(@traffic_weight_exists = 0,
  'ALTER TABLE `tk_generation_route` ADD COLUMN `traffic_weight` int DEFAULT 100 COMMENT ''Traffic weight'' AFTER `route_version`',
  'SELECT ''tk_generation_route.traffic_weight already exists''');
PREPARE stmt FROM @traffic_weight_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ab_group_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_route' AND column_name = 'ab_group'
);
SET @ab_group_sql = IF(@ab_group_exists = 0,
  'ALTER TABLE `tk_generation_route` ADD COLUMN `ab_group` varchar(32) DEFAULT NULL COMMENT ''A/B group'' AFTER `traffic_weight`',
  'SELECT ''tk_generation_route.ab_group already exists''');
PREPARE stmt FROM @ab_group_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @last_publish_time_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_route' AND column_name = 'last_publish_time'
);
SET @last_publish_time_sql = IF(@last_publish_time_exists = 0,
  'ALTER TABLE `tk_generation_route` ADD COLUMN `last_publish_time` datetime DEFAULT NULL COMMENT ''Last publish time'' AFTER `ab_group`',
  'SELECT ''tk_generation_route.last_publish_time already exists''');
PREPARE stmt FROM @last_publish_time_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_category_code_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'product_category_code'
);
SET @product_category_code_sql = IF(@product_category_code_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `product_category_code` varchar(32) DEFAULT NULL COMMENT ''Product category code'' AFTER `material_purpose`',
  'SELECT ''tk_generation_task.product_category_code already exists''');
PREPARE stmt FROM @product_category_code_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @generation_route_code_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'generation_route_code'
);
SET @generation_route_code_sql = IF(@generation_route_code_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `generation_route_code` varchar(64) DEFAULT NULL COMMENT ''Generation route code'' AFTER `product_category_code`',
  'SELECT ''tk_generation_task.generation_route_code already exists''');
PREPARE stmt FROM @generation_route_code_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @generation_route_config_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'generation_route_config'
);
SET @generation_route_config_sql = IF(@generation_route_config_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `generation_route_config` text DEFAULT NULL COMMENT ''Generation route config JSON'' AFTER `generation_route_code`',
  'SELECT ''tk_generation_task.generation_route_config already exists''');
PREPARE stmt FROM @generation_route_config_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `tk_generation_route`
  (`tenant_id`, `material_purpose`, `product_category_code`, `route_code`, `route_name`, `route_config`, `route_version`, `traffic_weight`, `ab_group`, `last_publish_time`, `enabled`, `remark`)
VALUES
  (0, 'ECOMMERCE', 'DEFAULT', 'ECOM_DEFAULT', 'Default ecommerce route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]',
   1, 100, 'control', NULL, b'1', 'Fallback route'),
  (0, 'ECOMMERCE', '01', 'ECOM_APPAREL', 'Apparel shoes bags route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":5}]',
   1, 100, 'control', NULL, b'1', '01 apparel shoes bags'),
  (0, 'ECOMMERCE', '02', 'ECOM_BEAUTY', 'Beauty personal care route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]',
   1, 100, 'control', NULL, b'1', '02 beauty personal care'),
  (0, 'ECOMMERCE', '03', 'ECOM_FOOD_DRINK', 'Food drink route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":5}]',
   1, 100, 'control', NULL, b'1', '03 food drink'),
  (0, 'ECOMMERCE', '04', 'ECOM_HOME_LIFE', 'Home life route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S7_LIFESTYLE","duration":5}]',
   1, 100, 'control', NULL, b'1', '04 home life'),
  (0, 'ECOMMERCE', '05', 'ECOM_DIGITAL_3C', '3C digital route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S6_DETAIL","duration":4}]',
   1, 100, 'control', NULL, b'1', '05 3C digital'),
  (0, 'ECOMMERCE', '06', 'ECOM_APPLIANCE', 'Home appliance route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S6_DETAIL","duration":4}]',
   1, 100, 'control', NULL, b'1', '06 home appliance'),
  (0, 'ECOMMERCE', '07', 'ECOM_MOTHER_BABY', 'Mother baby children route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]',
   1, 100, 'control', NULL, b'1', '07 mother baby children'),
  (0, 'ECOMMERCE', '08', 'ECOM_SPORT_OUTDOOR', 'Sport outdoor route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S7_LIFESTYLE","duration":5}]',
   1, 100, 'control', NULL, b'1', '08 sport outdoor'),
  (0, 'ECOMMERCE', '09', 'ECOM_PET', 'Pet supplies route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]',
   1, 100, 'control', NULL, b'1', '09 pet supplies'),
  (0, 'ECOMMERCE', '10', 'ECOM_AUTO', 'Auto supplies route',
   '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]',
   1, 100, 'control', NULL, b'1', '10 auto supplies'),
  (0, 'LEAD_GENERATION', 'DEFAULT', 'LEAD_DEFAULT', 'Default lead generation route', NULL,
   1, 100, 'control', NULL, b'1', 'Fallback lead route');

UPDATE `tk_generation_route`
SET `route_config` = CASE `product_category_code`
  WHEN 'DEFAULT' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]'
  WHEN '01' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":5}]'
  WHEN '02' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]'
  WHEN '03' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":5}]'
  WHEN '04' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S7_LIFESTYLE","duration":5}]'
  WHEN '05' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S6_DETAIL","duration":4}]'
  WHEN '06' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S6_DETAIL","duration":4}]'
  WHEN '07' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]'
  WHEN '08' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S7_LIFESTYLE","duration":5}]'
  WHEN '09' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S2_PAIN","duration":3},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]'
  WHEN '10' THEN '[{"segmentType":"S1_HOOK","duration":3},{"segmentType":"S3_REVEAL","duration":4},{"segmentType":"S4_DEMO","duration":4},{"segmentType":"S5_PROOF","duration":4}]'
  ELSE `route_config`
END,
`traffic_weight` = IFNULL(`traffic_weight`, 100),
`ab_group` = COALESCE(NULLIF(`ab_group`, ''), 'control')
WHERE `tenant_id` = 0
  AND `material_purpose` = 'ECOMMERCE'
  AND `deleted` = b'0'
  AND (`route_config` IS NULL OR TRIM(`route_config`) = '');

UPDATE `tk_generation_route`
SET `traffic_weight` = IFNULL(`traffic_weight`, 100),
    `ab_group` = COALESCE(NULLIF(`ab_group`, ''), 'control')
WHERE `tenant_id` = 0
  AND `material_purpose` = 'LEAD_GENERATION'
  AND `deleted` = b'0';
