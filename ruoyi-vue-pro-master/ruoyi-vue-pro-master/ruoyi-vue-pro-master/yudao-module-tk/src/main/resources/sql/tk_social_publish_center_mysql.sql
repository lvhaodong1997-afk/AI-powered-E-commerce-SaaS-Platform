-- Additive Meta publishing migration. Run before enabling tk.social.enabled.
-- Existing TikTok data and schema are untouched. Re-running preserves existing data.

CREATE TABLE IF NOT EXISTS tk_social_account (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  platform varchar(32) NOT NULL,
  account_type varchar(32) DEFAULT NULL,
  external_account_id varchar(128) NOT NULL,
  provider_user_id varchar(128) DEFAULT NULL,
  account_name varchar(255) DEFAULT NULL,
  username varchar(255) DEFAULT NULL,
  access_token_ciphertext text,
  token_type varchar(32) DEFAULT NULL,
  scopes text,
  status varchar(32) NOT NULL,
  fail_reason varchar(512) DEFAULT NULL,
  token_expires_at datetime DEFAULT NULL,
  last_validated_at datetime DEFAULT NULL,
  last_auth_time datetime DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_account (tenant_id, platform, external_account_id, deleted),
  KEY idx_social_account_scope (tenant_id, company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_social_auth_session (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  session_id varchar(128) NOT NULL,
  platform varchar(32) NOT NULL,
  state_hash varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  payload_ciphertext mediumtext,
  fail_reason varchar(512) DEFAULT NULL,
  expire_time datetime NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_session (session_id),
  UNIQUE KEY uk_social_state (state_hash),
  KEY idx_social_session_expiry (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_social_media (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  media_type varchar(32) NOT NULL,
  file_name varchar(255) NOT NULL,
  content_type varchar(128) NOT NULL,
  file_size bigint NOT NULL,
  object_key varchar(1024) DEFAULT NULL,
  public_url text NOT NULL,
  status varchar(32) NOT NULL,
  width int DEFAULT NULL,
  height int DEFAULT NULL,
  duration_seconds double DEFAULT NULL,
  frame_rate double DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_social_media_scope (tenant_id, creator, create_time),
  KEY idx_social_media_cleanup (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_social_publish_task (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  media_id bigint DEFAULT NULL,
  title varchar(255) NOT NULL,
  instagram_caption text,
  facebook_message text,
  status varchar(32) NOT NULL,
  idempotency_key varchar(80) NOT NULL,
  request_hash varchar(64) NOT NULL,
  target_count int NOT NULL DEFAULT 0,
  success_count int NOT NULL DEFAULT 0,
  failed_count int NOT NULL DEFAULT 0,
  pending_count int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_idempotency (tenant_id, creator, idempotency_key),
  KEY idx_social_task_scope (tenant_id, creator, create_time),
  KEY idx_social_task_media (media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_social_publish_detail (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  publish_task_id bigint NOT NULL,
  social_account_id bigint NOT NULL,
  platform varchar(32) NOT NULL,
  account_name varchar(255) DEFAULT NULL,
  status varchar(32) NOT NULL,
  platform_status varchar(128) DEFAULT NULL,
  external_container_id varchar(128) DEFAULT NULL,
  external_media_id varchar(128) DEFAULT NULL,
  external_post_id varchar(128) DEFAULT NULL,
  publish_url text,
  lease_token varchar(64) DEFAULT NULL,
  error_code varchar(128) DEFAULT NULL,
  error_message varchar(512) DEFAULT NULL,
  retry_count int NOT NULL DEFAULT 0,
  poll_count int NOT NULL DEFAULT 0,
  next_retry_time datetime DEFAULT NULL,
  lease_until datetime DEFAULT NULL,
  last_sync_time datetime DEFAULT NULL,
  published_time datetime DEFAULT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_target (publish_task_id, social_account_id),
  KEY idx_social_due (status, next_retry_time),
  KEY idx_social_lease (status, lease_until),
  KEY idx_social_container (platform, external_container_id),
  KEY idx_social_post (platform, external_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta账号查询', 'tk:social-account:query', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-account:query' AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta账号授权', 'tk:social-account:authorize', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-account:authorize' AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta账号管理', 'tk:social-account:update', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-account:update' AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta发布查询', 'tk:social-publish:query', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-publish:query' AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta发布创建', 'tk:social-publish:create', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-publish:create' AND existing.deleted = b'0')
LIMIT 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT (SELECT COALESCE(MAX(m.id),6000)+1 FROM system_menu m), 'Meta发布重试', 'tk:social-publish:retry', 3, 10, parent.id, '', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM system_menu parent
WHERE parent.permission = 'tk:video-publish-center:query' AND parent.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission = 'tk:social-publish:retry' AND existing.deleted = b'0')
LIMIT 1;
