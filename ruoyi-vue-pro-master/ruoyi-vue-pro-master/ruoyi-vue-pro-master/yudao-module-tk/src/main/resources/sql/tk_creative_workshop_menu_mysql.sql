SET NAMES utf8mb4;

INSERT INTO `system_menu` (
  `id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
  `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
  `updater`, `update_time`, `deleted`
)
VALUES
  (6005, 'AI创意工坊', 'tk:creative-workshop:query', 2, 5, 6000, 'creative-workshop', 'ep:magic-stick', 'tk/creative-workshop/index', 'TkCreativeWorkshop', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6025, '创意生成', 'tk:creative-workshop:generate', 3, 1, 6005, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`),
  `visible` = VALUES(`visible`),
  `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`),
  `deleted` = VALUES(`deleted`);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.role_id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', rm.tenant_id
FROM `system_role_menu` rm
JOIN (
  SELECT 6005 AS id
  UNION ALL
  SELECT 6025 AS id
) m
WHERE rm.menu_id = 6000
  AND rm.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` existing
    WHERE existing.role_id = rm.role_id
      AND existing.menu_id = m.id
      AND existing.deleted = b'0'
  );
