-- Apply after tk_tiktok_shop_workspace_mysql.sql. Assign the new permissions to the appropriate roles explicitly.
CREATE TABLE IF NOT EXISTS tk_tiktok_shop_photo (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  account_id bigint NOT NULL,
  idempotency_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash char(43) NOT NULL,
  title text NOT NULL,
  product_id varchar(128) NOT NULL,
  stage varchar(24) NOT NULL,
  photo_id varchar(128) DEFAULT NULL,
  post_status varchar(64) NOT NULL DEFAULT '',
  fail_code varchar(128) DEFAULT NULL,
  request_id varchar(128) DEFAULT NULL,
  creator varchar(64) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tk_shop_photo_request (tenant_id, company_id, account_id, creator, idempotency_key),
  KEY idx_tk_shop_photo_history (tenant_id, company_id, account_id, creator, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT MIN(id) INTO @shop_p1_parent FROM system_menu
WHERE permission = 'tk:tiktok-shop-workspace:query' AND deleted = b'0';
SELECT COALESCE(MAX(id), 0) INTO @shop_p1_base FROM system_menu;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @shop_p1_base + p.offset_id, p.name, p.permission, 3, 31 + p.offset_id, @shop_p1_parent,
       '', '', '', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS offset_id, 'TikTok Shop seller authorization' AS name, 'tk:tiktok-shop-seller:authorize' AS permission
    UNION ALL SELECT 2, 'TikTok Shop seller operations query', 'tk:tiktok-shop-seller:query'
    UNION ALL SELECT 3, 'TikTok Shop analytics query', 'tk:tiktok-shop-analytics:query'
) AS p
LEFT JOIN system_menu AS m ON m.permission = p.permission AND m.deleted = b'0'
WHERE @shop_p1_parent IS NOT NULL AND m.id IS NULL;
