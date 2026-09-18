-- TK 开放 API 发布重启恢复与终态回调幂等升级
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `tk_open_api_event` ADD COLUMN `dedupe_key` varchar(256) DEFAULT NULL COMMENT ''回调事件幂等键'' AFTER `resource_id`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tk_open_api_event'
    AND COLUMN_NAME = 'dedupe_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `tk_open_api_event` ADD UNIQUE KEY `uk_tk_open_event_dedupe` (`client_id`, `dedupe_key`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tk_open_api_event'
    AND INDEX_NAME = 'uk_tk_open_event_dedupe'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
