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

-- Run with publishing workers stopped. Attach one existing event to each current
-- terminal attempt, preserving delivered events and their original eventId.
-- Multiple legacy events remain as audit history; only the latest receives a key.
UPDATE tk_open_api_event e
JOIN (
    SELECT MAX(old.id) AS event_row_id,
           CONCAT(d.client_id, '|publish.', LOWER(d.status), '|PUBLISH_DETAIL|',
                  d.detail_id, '|', COALESCE(d.retry_count, 0)) AS expected_key
    FROM tk_open_tiktok_publish_detail d
    JOIN tk_open_api_event old ON old.client_id = d.client_id
         AND old.resource_type = 'PUBLISH_DETAIL' AND old.resource_id = d.detail_id
         AND old.event_type = CONCAT('publish.', LOWER(d.status)) AND old.deleted = 0
    WHERE d.deleted = 0 AND d.status IN ('SUCCESS', 'FAILED')
      AND (COALESCE(d.retry_count, 0) = 0 OR
           (d.publish_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(old.payload_json, '$.publishId')) = d.publish_id))
    GROUP BY d.client_id, d.detail_id, d.status, d.retry_count
) legacy ON legacy.event_row_id = e.id
LEFT JOIN tk_open_api_event keyed ON keyed.client_id = e.client_id
     AND keyed.dedupe_key = legacy.expected_key
SET e.dedupe_key = legacy.expected_key
WHERE e.dedupe_key IS NULL AND keyed.id IS NULL;

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
