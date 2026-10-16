-- Open TikTok publish queue permissions. Run after the existing video publish center menu exists.
-- The statements are idempotent and grant the platform-wide controls to super administrators.

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id), 6000) + 1 FROM system_menu m),
       'Open TikTok queue query', 'tk:open-tiktok-publish:queue-query', 3, 10, parent.id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = 'tk:open-tiktok-publish:queue-query'
                    AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id), 6000) + 1 FROM system_menu m),
       'Open TikTok queue resume', 'tk:open-tiktok-publish:queue-resume', 3, 11, parent.id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = 'tk:open-tiktok-publish:queue-resume'
                    AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id), 6000) + 1 FROM system_menu m),
       'Open TikTok queue sync', 'tk:open-tiktok-publish:queue-sync', 3, 12, parent.id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = 'tk:open-tiktok-publish:queue-sync'
                    AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id), 6000) + 1 FROM system_menu m),
       'Open TikTok queue cancel', 'tk:open-tiktok-publish:queue-cancel', 3, 13, parent.id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = 'tk:open-tiktok-publish:queue-cancel'
                    AND existing.deleted = b'0')
LIMIT 1;

SET @schema_name = DATABASE();
SET @queue_index_sql = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @schema_name
              AND table_name = 'tk_open_tiktok_publish_detail'
              AND index_name = 'idx_tk_open_publish_detail_queue_date_status'),
    'SELECT 1',
    'ALTER TABLE `tk_open_tiktok_publish_detail` ADD KEY `idx_tk_open_publish_detail_queue_date_status` (`create_time`, `status`, `id`)')
);
PREPARE queue_index_stmt FROM @queue_index_sql;
EXECUTE queue_index_stmt;
DEALLOCATE PREPARE queue_index_stmt;

-- Queue controls are platform-wide because the management page aggregates Open API clients.
-- Grant them only to existing super administrators; this does not broaden tenant roles.
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_row.`id`, menu_row.`id`, 'admin', NOW(), 'admin', NOW(), b'0', role_row.`tenant_id`
FROM `system_role` role_row
JOIN `system_menu` menu_row
  ON menu_row.`permission` IN (
    'tk:open-tiktok-publish:queue-query',
    'tk:open-tiktok-publish:queue-resume',
    'tk:open-tiktok-publish:queue-sync',
    'tk:open-tiktok-publish:queue-cancel')
 AND menu_row.`deleted` = b'0'
WHERE role_row.`code` = 'super_admin' AND role_row.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id` = role_row.`id`
      AND existing.`menu_id` = menu_row.`id`
      AND existing.`deleted` = b'0'
  );
