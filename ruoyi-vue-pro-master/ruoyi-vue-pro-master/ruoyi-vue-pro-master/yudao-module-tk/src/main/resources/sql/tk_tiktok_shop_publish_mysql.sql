-- Additive TikTok Shop module. Apply explicitly before enabling tk.tiktok-shop.enabled.
-- Existing TikTok and Meta tables are untouched. No credentials are stored by this migration.

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_auth_session (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  user_id bigint NOT NULL,
  state_hash varchar(64) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/PROCESSING/SUCCESS/FAILED',
  expire_time datetime NOT NULL,
  consumed_time datetime DEFAULT NULL,
  fail_reason varchar(1024) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_auth_state (state_hash),
  KEY idx_shop_auth_expiry (status, expire_time),
  KEY idx_shop_auth_scope (tenant_id, company_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok Shop authorization state';

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_account (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  app_key varchar(128) NOT NULL,
  open_id varchar(128) NOT NULL,
  display_name varchar(255) DEFAULT NULL,
  username varchar(255) DEFAULT NULL,
  market varchar(16) NOT NULL DEFAULT 'US',
  user_type int DEFAULT NULL,
  granted_scopes json DEFAULT NULL,
  access_token_cipher text,
  refresh_token_cipher text,
  access_token_expire_time datetime DEFAULT NULL,
  refresh_token_expire_time datetime DEFAULT NULL,
  auth_status varchar(32) NOT NULL,
  token_version bigint NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_account (tenant_id, app_key, open_id),
  KEY idx_shop_account_scope (tenant_id, company_id, auth_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok Shop creator account';

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_publish_task (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  account_id bigint NOT NULL,
  generation_task_id bigint DEFAULT NULL,
  media_id bigint DEFAULT NULL,
  source_path varchar(2048) NOT NULL COMMENT 'Backend-resolved local path only; never an input URL',
  source_fingerprint varchar(128) NOT NULL,
  file_name varchar(512) NOT NULL,
  file_size bigint NOT NULL,
  title varchar(2048) NOT NULL,
  product_id varchar(128) NOT NULL,
  product_link_title varchar(255) DEFAULT NULL,
  product_snapshot json DEFAULT NULL,
  idempotency_key varchar(128) NOT NULL,
  request_hash varchar(64) NOT NULL,
  stage varchar(64) NOT NULL,
  file_id varchar(128) DEFAULT NULL,
  precheck_task_id varchar(128) DEFAULT NULL,
  precheck_result json DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  confirmed_version int DEFAULT NULL,
  confirmed_by bigint DEFAULT NULL,
  confirmed_at datetime DEFAULT NULL,
  video_id varchar(128) DEFAULT NULL,
  request_id varchar(128) DEFAULT NULL,
  owner_token varchar(128) DEFAULT NULL,
  lease_expire_time datetime DEFAULT NULL,
  next_poll_time datetime DEFAULT NULL,
  poll_count int NOT NULL DEFAULT 0,
  fail_code varchar(128) DEFAULT NULL,
  fail_reason varchar(1024) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_task_idempotency (tenant_id, creator, idempotency_key),
  KEY idx_shop_worker (stage, next_poll_time, lease_expire_time),
  KEY idx_shop_task_scope (tenant_id, company_id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok Shop durable publishing task';

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_publish_attempt (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  task_id bigint NOT NULL,
  attempt_no int NOT NULL,
  request_hash varchar(64) NOT NULL,
  stage varchar(64) NOT NULL,
  request_id varchar(128) DEFAULT NULL,
  video_id varchar(128) DEFAULT NULL,
  response_json json DEFAULT NULL COMMENT 'Sanitized metadata only; never tokens, raw URLs or media',
  owner_token varchar(128) DEFAULT NULL,
  lease_expire_time datetime DEFAULT NULL,
  fail_code varchar(128) DEFAULT NULL,
  fail_reason varchar(1024) DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_attempt (task_id, attempt_no),
  KEY idx_shop_attempt_scope (tenant_id, company_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok Shop publication attempt audit';

-- Permissions only: administrators must explicitly assign these to the intended roles.
-- Run this migration serially in one session, as with other explicit schema migrations.
-- Read target-table values in separate statements to avoid MySQL INSERT target subqueries.
-- Aggregates overwrite session variables even when no active parent/permission exists.
SELECT COALESCE(MAX(id), 0) INTO @tk_shop_menu_id FROM system_menu;
SELECT MIN(id) INTO @tk_shop_parent_id FROM system_menu
WHERE permission = 'tk:video-publish-center:query' AND deleted = b'0';
SELECT COUNT(*) INTO @tk_shop_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-account:query' AND deleted = b'0';
SET @tk_shop_menu_id = @tk_shop_menu_id + 1;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @tk_shop_menu_id,
       'TikTok Shop account query', 'tk:tiktok-shop-account:query', 3, 20, @tk_shop_parent_id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @tk_shop_parent_id IS NOT NULL AND @tk_shop_permission_exists = 0;

SELECT COUNT(*) INTO @tk_shop_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-account:authorize' AND deleted = b'0';
SET @tk_shop_menu_id = @tk_shop_menu_id + 1;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @tk_shop_menu_id,
       'TikTok Shop account authorize', 'tk:tiktok-shop-account:authorize', 3, 21, @tk_shop_parent_id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @tk_shop_parent_id IS NOT NULL AND @tk_shop_permission_exists = 0;

SELECT COUNT(*) INTO @tk_shop_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-publish:query' AND deleted = b'0';
SET @tk_shop_menu_id = @tk_shop_menu_id + 1;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @tk_shop_menu_id,
       'TikTok Shop publish query', 'tk:tiktok-shop-publish:query', 3, 22, @tk_shop_parent_id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @tk_shop_parent_id IS NOT NULL AND @tk_shop_permission_exists = 0;

SELECT COUNT(*) INTO @tk_shop_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-publish:create' AND deleted = b'0';
SET @tk_shop_menu_id = @tk_shop_menu_id + 1;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @tk_shop_menu_id,
       'TikTok Shop publish create', 'tk:tiktok-shop-publish:create', 3, 23, @tk_shop_parent_id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @tk_shop_parent_id IS NOT NULL AND @tk_shop_permission_exists = 0;

SELECT COUNT(*) INTO @tk_shop_permission_exists FROM system_menu
WHERE permission = 'tk:tiktok-shop-publish:confirm' AND deleted = b'0';
SET @tk_shop_menu_id = @tk_shop_menu_id + 1;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component,
                         status, visible, keep_alive, always_show, creator, create_time,
                         updater, update_time, deleted)
SELECT @tk_shop_menu_id,
       'TikTok Shop publish confirm', 'tk:tiktok-shop-publish:confirm', 3, 24, @tk_shop_parent_id,
       '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE @tk_shop_parent_id IS NOT NULL AND @tk_shop_permission_exists = 0;
