SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name
          AND table_name = 'tk_open_tiktok_publish_detail'
          AND index_name = 'idx_tk_open_publish_dispatch'),
  'SELECT ''idx_tk_open_publish_dispatch already exists''',
  'ALTER TABLE `tk_open_tiktok_publish_detail` ADD INDEX `idx_tk_open_publish_dispatch` (`status`, `create_time`, `id`)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
