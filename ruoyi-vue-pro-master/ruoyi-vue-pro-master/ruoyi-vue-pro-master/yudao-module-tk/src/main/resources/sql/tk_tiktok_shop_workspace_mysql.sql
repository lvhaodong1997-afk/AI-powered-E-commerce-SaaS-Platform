-- Apply after tk_tiktok_shop_publish_mysql.sql. Grant the menu to Shop roles explicitly.
SELECT COALESCE(MAX(id), 0) + 1 INTO @shop_workspace_id FROM system_menu;
SELECT MIN(parent_id) INTO @shop_workspace_parent FROM system_menu
WHERE permission = 'tk:video-publish-center:query' AND type = 2 AND deleted = b'0';
SELECT COUNT(*) INTO @shop_workspace_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-workspace:query' AND deleted = b'0';

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         component_name, status, visible, keep_alive, always_show,
                         creator, create_time, updater, update_time, deleted)
SELECT @shop_workspace_id, 'TikTok Shop', 'tk:tiktok-shop-workspace:query', 2, 5,
       @shop_workspace_parent, 'tiktok-shop', 'ep:shop', 'tk/tiktok-shop/index',
       'TkTiktokShopWorkspace', 0, b'1', b'0', b'1',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @shop_workspace_parent IS NOT NULL AND @shop_workspace_exists = 0;

-- Preserve button IDs and existing role grants while moving them under their own menu.
SELECT MIN(id) INTO @shop_workspace_id FROM system_menu
WHERE permission = 'tk:tiktok-shop-workspace:query' AND deleted = b'0';
UPDATE system_menu SET parent_id = @shop_workspace_id, updater = 'admin', update_time = NOW()
WHERE @shop_workspace_id IS NOT NULL AND parent_id <> @shop_workspace_id
  AND type = 3 AND deleted = b'0' AND permission IN (
    'tk:tiktok-shop-account:query', 'tk:tiktok-shop-account:authorize',
    'tk:tiktok-shop-publish:query', 'tk:tiktok-shop-publish:create', 'tk:tiktok-shop-publish:confirm'
  );

SELECT COALESCE(MAX(id), 0) + 1 INTO @shop_creator_menu_id FROM system_menu;
SELECT COUNT(*) INTO @shop_creator_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-creator:query' AND deleted = b'0';
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @shop_creator_menu_id, 'TikTok Shop creator query', 'tk:tiktok-shop-creator:query',
       3, 30, @shop_workspace_id, '', '', '', 0, b'0', b'0', b'1',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @shop_workspace_id IS NOT NULL AND @shop_creator_permission_exists = 0;

SELECT COALESCE(MAX(id), 0) + 1 INTO @shop_creator_menu_id FROM system_menu;
SELECT COUNT(*) INTO @shop_creator_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-creator:showcase' AND deleted = b'0';
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @shop_creator_menu_id, 'TikTok Shop showcase management', 'tk:tiktok-shop-creator:showcase',
       3, 31, @shop_workspace_id, '', '', '', 0, b'0', b'0', b'1',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @shop_workspace_id IS NOT NULL AND @shop_creator_permission_exists = 0;
