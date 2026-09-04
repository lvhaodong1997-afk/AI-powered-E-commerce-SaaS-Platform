-- TikTok Open API schema. Public authorization is scoped only by client_id.

CREATE TABLE IF NOT EXISTS `tk_open_api_client` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` varchar(64) NOT NULL,
  `client_name` varchar(128) NOT NULL,
  `client_secret_cipher` varchar(1024) NOT NULL,
  `callback_secret_cipher` varchar(1024) NOT NULL,
  `auth_callback_url` varchar(512) DEFAULT NULL,
  `publish_callback_url` varchar(512) DEFAULT NULL,
  `allowed_ips` varchar(2048) DEFAULT NULL,
  `permissions` varchar(512) NOT NULL DEFAULT 'auth,media,publish',
  `rate_limit_per_minute` int NOT NULL DEFAULT 120,
  `daily_quota` int NOT NULL DEFAULT 10000,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_api_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_auth_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_session_id` varchar(64) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `external_account_id` varchar(128) NOT NULL,
  `client_state` varchar(512) DEFAULT NULL,
  `auth_mode` varchar(32) NOT NULL,
  `oauth_state` varchar(128) NOT NULL,
  `client_ticket` varchar(128) DEFAULT NULL,
  `qrcode_token` varchar(1024) DEFAULT NULL,
  `qrcode_url` varchar(2048) DEFAULT NULL,
  `authorize_url` varchar(2048) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `connection_id` varchar(64) DEFAULT NULL,
  `account_name` varchar(256) DEFAULT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `expire_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_auth_session_id` (`auth_session_id`),
  UNIQUE KEY `uk_tk_open_auth_oauth_state` (`oauth_state`),
  KEY `idx_tk_open_auth_client_external` (`client_id`, `external_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `connection_id` varchar(64) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `external_account_id` varchar(128) NOT NULL,
  `open_id` varchar(128) NOT NULL,
  `display_name` varchar(256) DEFAULT NULL,
  `username` varchar(256) DEFAULT NULL,
  `avatar_url` varchar(1024) DEFAULT NULL,
  `scopes` varchar(1024) DEFAULT NULL,
  `access_token_cipher` text NOT NULL,
  `refresh_token_cipher` text DEFAULT NULL,
  `access_token_expire_time` datetime DEFAULT NULL,
  `refresh_token_expire_time` datetime DEFAULT NULL,
  `token_status` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `auth_status` varchar(32) NOT NULL DEFAULT 'AUTHORIZED',
  `last_auth_time` datetime DEFAULT NULL,
  `last_publish_time` datetime DEFAULT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_connection_id` (`connection_id`),
  UNIQUE KEY `uk_tk_open_connection_external` (`client_id`, `external_account_id`),
  KEY `idx_tk_open_connection_open_id` (`client_id`, `open_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `upload_id` varchar(64) NOT NULL,
  `media_id` varchar(64) DEFAULT NULL,
  `client_id` varchar(64) NOT NULL,
  `upload_mode` varchar(32) NOT NULL,
  `file_name` varchar(512) NOT NULL,
  `file_size` bigint NOT NULL,
  `content_type` varchar(128) NOT NULL,
  `sha256` varchar(64) DEFAULT NULL,
  `object_key` varchar(1024) DEFAULT NULL,
  `file_url` varchar(2048) DEFAULT NULL,
  `uploaded_size` bigint NOT NULL DEFAULT 0,
  `uploaded_chunks` text DEFAULT NULL,
  `cover_timestamp_ms` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `expire_time` datetime NOT NULL,
  `completed_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_media_upload_id` (`upload_id`),
  UNIQUE KEY `uk_tk_open_media_media_id` (`media_id`),
  KEY `idx_tk_open_media_client_status` (`client_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_publish_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `media_id` varchar(64) NOT NULL,
  `external_request_id` varchar(128) DEFAULT NULL,
  `title` varchar(512) DEFAULT NULL,
  `caption` varchar(2200) DEFAULT NULL,
  `post_mode` varchar(32) NOT NULL,
  `privacy_level` varchar(64) NOT NULL,
  `allow_comment` bit(1) NOT NULL DEFAULT b'1',
  `allow_duet` bit(1) NOT NULL DEFAULT b'0',
  `allow_stitch` bit(1) NOT NULL DEFAULT b'0',
  `commercial_content` bit(1) NOT NULL DEFAULT b'0',
  `brand_content` bit(1) NOT NULL DEFAULT b'0',
  `aigc_content` bit(1) NOT NULL DEFAULT b'1',
  `account_count` int NOT NULL,
  `success_count` int NOT NULL DEFAULT 0,
  `failed_count` int NOT NULL DEFAULT 0,
  `pending_count` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_publish_task_id` (`task_id`),
  KEY `idx_tk_open_publish_task_client` (`client_id`, `create_time`),
  KEY `idx_tk_open_publish_task_external` (`client_id`, `external_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_tiktok_publish_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `detail_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `connection_id` varchar(64) NOT NULL,
  `account_name` varchar(256) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `tiktok_status` varchar(64) DEFAULT NULL,
  `publish_id` varchar(128) DEFAULT NULL,
  `publish_url` varchar(1024) DEFAULT NULL,
  `fail_reason` varchar(1024) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `last_sync_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_publish_detail_id` (`detail_id`),
  KEY `idx_tk_open_publish_detail_task` (`client_id`, `task_id`),
  KEY `idx_tk_open_publish_detail_sync` (`status`, `last_sync_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_api_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `resource_id` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `expire_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_idempotency` (`client_id`, `idempotency_key`),
  KEY `idx_tk_open_idempotency_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_api_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `resource_type` varchar(32) DEFAULT NULL,
  `resource_id` varchar(64) DEFAULT NULL,
  `callback_url` varchar(512) DEFAULT NULL,
  `payload_json` mediumtext NOT NULL,
  `status` varchar(32) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_retry_time` datetime DEFAULT NULL,
  `last_http_status` int DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `delivered_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_open_event_id` (`event_id`),
  KEY `idx_tk_open_event_retry` (`status`, `next_retry_time`),
  KEY `idx_tk_open_event_client` (`client_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tk_open_api_request_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(128) NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `http_method` varchar(16) NOT NULL,
  `request_target` varchar(1024) NOT NULL,
  `http_status` int NOT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `duration_ms` bigint NOT NULL DEFAULT 0,
  `client_ip` varchar(64) DEFAULT NULL,
  `request_date` date NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_tk_open_request_client_date` (`client_id`, `request_date`),
  KEY `idx_tk_open_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Management page is available only to the platform super administrator.
INSERT INTO `system_menu` (
  `id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
  `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
  `updater`, `update_time`, `deleted`
)
VALUES
  (6030, '开放 API 管理', 'tk:open-api:query', 2, 7, 6000, 'open-api', 'ep:key',
   'tk/open-api/index', 'TkOpenApiManagement', 0, b'1', b'1', b'1',
   'admin', NOW(), 'admin', NOW(), b'0')
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

INSERT INTO `system_role_menu` (
  `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT role_row.id, 6030, 'admin', NOW(), 'admin', NOW(), b'0', role_row.tenant_id
FROM `system_role` role_row
WHERE role_row.code = 'super_admin'
  AND role_row.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` existing
    WHERE existing.role_id = role_row.id
      AND existing.menu_id = 6030
      AND existing.deleted = b'0'
  );
