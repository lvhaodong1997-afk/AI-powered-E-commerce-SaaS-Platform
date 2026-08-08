SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO `system_role` (`name`, `code`, `sort`, `data_scope`, `status`, `type`, `remark`,
                           `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '普通用户', 'tenant_user', 1, 1, 0, 1, '系统自动生成',
       'codex-default-tenant-user', NOW(), 'codex-default-tenant-user', NOW(), b'0', t.`id`
FROM `system_tenant` t
WHERE t.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role` r
    WHERE r.`tenant_id` = t.`id`
      AND r.`code` = 'tenant_user'
      AND r.`deleted` = b'0'
  );

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT tenant_user_role.`id`, admin_role_menu.`menu_id`,
       'codex-default-tenant-user', NOW(), 'codex-default-tenant-user', NOW(), b'0', tenant_user_role.`tenant_id`
FROM `system_role` tenant_user_role
INNER JOIN `system_role` admin_role
        ON admin_role.`tenant_id` = tenant_user_role.`tenant_id`
       AND admin_role.`code` = 'tenant_admin'
       AND admin_role.`deleted` = b'0'
INNER JOIN `system_role_menu` admin_role_menu
        ON admin_role_menu.`role_id` = admin_role.`id`
       AND admin_role_menu.`deleted` = b'0'
WHERE tenant_user_role.`code` = 'tenant_user'
  AND tenant_user_role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` existing
    WHERE existing.`role_id` = tenant_user_role.`id`
      AND existing.`menu_id` = admin_role_menu.`menu_id`
      AND existing.`deleted` = b'0'
  );

INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT u.`id`, tenant_user_role.`id`,
       'codex-default-tenant-user', NOW(), 'codex-default-tenant-user', NOW(), b'0', u.`tenant_id`
FROM `system_users` u
INNER JOIN `system_role` tenant_user_role
        ON tenant_user_role.`tenant_id` = u.`tenant_id`
       AND tenant_user_role.`code` = 'tenant_user'
       AND tenant_user_role.`deleted` = b'0'
WHERE u.`deleted` = b'0'
  AND u.`tk_user_level` IN ('TENANT_USER', 'COMPANY_USER')
  AND NOT EXISTS (
    SELECT 1
    FROM `system_user_role` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`deleted` = b'0'
  );

COMMIT;
