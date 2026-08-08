SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'publish_url'),
    'SELECT ''tk_tiktok_publish_detail.publish_url already exists''',
    'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `publish_url` varchar(512) DEFAULT NULL COMMENT ''TikTok 发布链接'' AFTER `publish_id`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'publish_url_registered_time'),
    'SELECT ''tk_tiktok_publish_detail.publish_url_registered_time already exists''',
    'ALTER TABLE `tk_tiktok_publish_detail` ADD COLUMN `publish_url_registered_time` datetime DEFAULT NULL COMMENT ''发布链接登记时间'' AFTER `retry_count`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND column_name = 'account_id' AND is_nullable = 'YES'),
    'SELECT ''tk_tiktok_publish_detail.account_id already nullable''',
    'ALTER TABLE `tk_tiktok_publish_detail` MODIFY COLUMN `account_id` bigint DEFAULT NULL COMMENT ''璐﹀彿缂栧彿'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'tk_tiktok_publish_detail' AND index_name = 'idx_tk_tiktok_publish_detail_generation_publish'),
    'SELECT ''idx_tk_tiktok_publish_detail_generation_publish already exists''',
    'ALTER TABLE `tk_tiktok_publish_detail` ADD KEY `idx_tk_tiktok_publish_detail_generation_publish` (`generation_task_id`, `publish_url_registered_time`)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
