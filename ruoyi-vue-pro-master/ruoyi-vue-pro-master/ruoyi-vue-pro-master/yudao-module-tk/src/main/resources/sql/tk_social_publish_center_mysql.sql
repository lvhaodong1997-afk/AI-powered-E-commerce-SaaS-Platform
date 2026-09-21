-- Additive Meta publishing migration. Run before enabling tk.social.enabled.
-- Existing TikTok data and schema are untouched. Re-running preserves existing data.

CREATE TABLE IF NOT EXISTS tk_social_account (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  tenant_id bigint NOT NULL COMMENT '租户编号',
  company_id bigint NOT NULL COMMENT '公司编号',
  platform varchar(32) NOT NULL COMMENT '社交平台',
  account_type varchar(32) DEFAULT NULL COMMENT '账号类型',
  external_account_id varchar(128) NOT NULL COMMENT '外部账号编号',
  provider_user_id varchar(128) DEFAULT NULL COMMENT '平台用户编号',
  account_name varchar(255) DEFAULT NULL COMMENT '账号名称',
  username varchar(255) DEFAULT NULL COMMENT '账号用户名',
  access_token_ciphertext text COMMENT '访问令牌密文',
  token_type varchar(32) DEFAULT NULL COMMENT '令牌类型',
  scopes text COMMENT '授权范围',
  status varchar(32) NOT NULL COMMENT '账号状态',
  fail_reason varchar(512) DEFAULT NULL COMMENT '失败原因',
  token_expires_at datetime DEFAULT NULL COMMENT '令牌过期时间',
  last_validated_at datetime DEFAULT NULL COMMENT '最近校验时间',
  last_auth_time datetime DEFAULT NULL COMMENT '最近授权时间',
  creator varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_account (tenant_id, platform, external_account_id, deleted),
  KEY idx_social_account_scope (tenant_id, company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TK 社交平台账号';

CREATE TABLE IF NOT EXISTS tk_social_auth_session (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  tenant_id bigint NOT NULL COMMENT '租户编号',
  company_id bigint NOT NULL COMMENT '公司编号',
  session_id varchar(128) NOT NULL COMMENT '授权会话编号',
  platform varchar(32) NOT NULL COMMENT '社交平台',
  state_hash varchar(64) NOT NULL COMMENT '状态哈希',
  status varchar(32) NOT NULL COMMENT '会话状态',
  payload_ciphertext mediumtext COMMENT '会话载荷密文',
  fail_reason varchar(512) DEFAULT NULL COMMENT '失败原因',
  expire_time datetime NOT NULL COMMENT '过期时间',
  creator varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_session (session_id),
  UNIQUE KEY uk_social_state (state_hash),
  KEY idx_social_session_expiry (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TK 社交平台授权会话';

CREATE TABLE IF NOT EXISTS tk_social_media (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  tenant_id bigint NOT NULL COMMENT '租户编号',
  company_id bigint NOT NULL COMMENT '公司编号',
  media_type varchar(32) NOT NULL COMMENT '媒体类型',
  file_name varchar(255) NOT NULL COMMENT '文件名',
  content_type varchar(128) NOT NULL COMMENT '内容类型',
  file_size bigint NOT NULL COMMENT '文件大小（字节）',
  object_key varchar(1024) DEFAULT NULL COMMENT '对象存储键',
  public_url text NOT NULL COMMENT '公开访问地址',
  status varchar(32) NOT NULL COMMENT '媒体状态',
  width int DEFAULT NULL COMMENT '视频宽度（像素）',
  height int DEFAULT NULL COMMENT '视频高度（像素）',
  duration_seconds double DEFAULT NULL COMMENT '媒体时长（秒）',
  frame_rate double DEFAULT NULL COMMENT '视频帧率',
  upload_id varchar(80) DEFAULT NULL COMMENT '上传编号',
  metadata_status varchar(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '元数据状态',
  metadata_source varchar(32) DEFAULT NULL COMMENT '元数据来源',
  metadata_error varchar(512) DEFAULT NULL COMMENT '元数据错误信息',
  video_codec varchar(64) DEFAULT NULL COMMENT '视频编码格式',
  audio_codec varchar(64) DEFAULT NULL COMMENT '音频编码格式',
  video_bitrate bigint DEFAULT NULL COMMENT '视频码率（比特/秒）',
  audio_bitrate bigint DEFAULT NULL COMMENT '音频码率（比特/秒）',
  audio_sample_rate int DEFAULT NULL COMMENT '音频采样率（赫兹）',
  source_file_size bigint DEFAULT NULL COMMENT '源文件大小（字节）',
  source_etag varchar(128) DEFAULT NULL COMMENT '源文件 ETag',
  source_version_id varchar(256) DEFAULT NULL COMMENT '源文件版本编号',
  publish_object_key varchar(1024) DEFAULT NULL COMMENT '发布对象存储键',
  publish_etag varchar(128) DEFAULT NULL COMMENT '发布文件 ETag',
  publish_version_id varchar(256) DEFAULT NULL COMMENT '发布文件版本编号',
  normalized bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已标准化',
  inspection_attempts int NOT NULL DEFAULT 0 COMMENT '媒体检查次数',
  inspection_lease_token varchar(64) DEFAULT NULL COMMENT '媒体检查租约令牌',
  inspection_lease_until datetime DEFAULT NULL COMMENT '媒体检查租约截止时间',
  inspection_next_retry datetime DEFAULT NULL COMMENT '下次检查重试时间',
  inspected_at datetime DEFAULT NULL COMMENT '最近检查时间',
  creator varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_social_media_scope (tenant_id, creator, create_time),
  KEY idx_social_media_cleanup (status, create_time),
  UNIQUE KEY uk_social_media_upload (tenant_id, upload_id),
  KEY idx_social_media_probe (status, metadata_status, inspection_next_retry, inspection_lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TK 社交媒体素材';

CREATE TABLE IF NOT EXISTS tk_social_publish_task (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  tenant_id bigint NOT NULL COMMENT '租户编号',
  company_id bigint NOT NULL COMMENT '公司编号',
  media_id bigint DEFAULT NULL COMMENT '媒体编号',
  title varchar(255) NOT NULL COMMENT '发布标题',
  instagram_caption text COMMENT 'Instagram 发布文案',
  facebook_message text COMMENT 'Facebook 发布内容',
  status varchar(32) NOT NULL COMMENT '发布任务状态',
  idempotency_key varchar(80) NOT NULL COMMENT '幂等键',
  request_hash varchar(64) NOT NULL COMMENT '请求哈希',
  target_count int NOT NULL DEFAULT 0 COMMENT '目标数量',
  success_count int NOT NULL DEFAULT 0 COMMENT '成功数量',
  failed_count int NOT NULL DEFAULT 0 COMMENT '失败数量',
  pending_count int NOT NULL DEFAULT 0 COMMENT '待处理数量',
  creator varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_idempotency (tenant_id, creator, idempotency_key),
  KEY idx_social_task_scope (tenant_id, creator, create_time),
  KEY idx_social_task_media (media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TK 社交平台发布任务';

CREATE TABLE IF NOT EXISTS tk_social_publish_detail (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  tenant_id bigint NOT NULL COMMENT '租户编号',
  company_id bigint NOT NULL COMMENT '公司编号',
  publish_task_id bigint NOT NULL COMMENT '发布任务编号',
  social_account_id bigint NOT NULL COMMENT '社交账号编号',
  platform varchar(32) NOT NULL COMMENT '社交平台',
  account_name varchar(255) DEFAULT NULL COMMENT '账号名称',
  status varchar(32) NOT NULL COMMENT '发布状态',
  platform_status varchar(128) DEFAULT NULL COMMENT '平台状态',
  external_container_id varchar(128) DEFAULT NULL COMMENT '外部容器编号',
  external_media_id varchar(128) DEFAULT NULL COMMENT '外部媒体编号',
  external_post_id varchar(128) DEFAULT NULL COMMENT '外部帖子编号',
  publish_url text COMMENT '发布链接',
  lease_token varchar(64) DEFAULT NULL COMMENT '租约令牌',
  error_code varchar(128) DEFAULT NULL COMMENT '错误码',
  error_message varchar(512) DEFAULT NULL COMMENT '错误信息',
  retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
  poll_count int NOT NULL DEFAULT 0 COMMENT '轮询次数',
  next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
  lease_until datetime DEFAULT NULL COMMENT '租约截止时间',
  last_sync_time datetime DEFAULT NULL COMMENT '最近同步时间',
  published_time datetime DEFAULT NULL COMMENT '发布时间',
  creator varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_target (publish_task_id, social_account_id),
  KEY idx_social_due (status, next_retry_time),
  KEY idx_social_lease (status, lease_until),
  KEY idx_social_container (platform, external_container_id),
  KEY idx_social_post (platform, external_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TK 社交平台发布明细';

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
