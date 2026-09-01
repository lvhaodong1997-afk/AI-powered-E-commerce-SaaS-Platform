SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tk_tiktok_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'TikTok璐﹀彿缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `open_id` varchar(128) NOT NULL COMMENT 'TikTok Open ID',
  `display_name` varchar(128) DEFAULT NULL COMMENT '鏄剧ず鍚嶇О',
  `username` varchar(128) DEFAULT NULL COMMENT '鐢ㄦ埛鍚?,
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '澶村儚',
  `scopes` varchar(512) DEFAULT NULL COMMENT '鎺堟潈鑼冨洿',
  `access_token_cipher` varchar(3000) DEFAULT NULL COMMENT '鍔犲瘑AccessToken',
  `refresh_token_cipher` varchar(3000) DEFAULT NULL COMMENT '鍔犲瘑RefreshToken',
  `access_token_expire_time` datetime DEFAULT NULL COMMENT 'AccessToken杩囨湡鏃堕棿',
  `refresh_token_expire_time` datetime DEFAULT NULL COMMENT 'RefreshToken杩囨湡鏃堕棿',
  `token_status` varchar(32) NOT NULL DEFAULT 'INVALID' COMMENT 'Token鐘舵€?,
  `auth_status` varchar(32) NOT NULL DEFAULT 'UNAUTHORIZED' COMMENT '鎺堟潈鐘舵€?,
  `default_privacy_level` varchar(64) DEFAULT NULL COMMENT '榛樿闅愮',
  `allow_comment` bit(1) NOT NULL DEFAULT b'1' COMMENT '鍏佽璇勮',
  `allow_duet` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍏佽鍚堟媿',
  `allow_stitch` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍏佽鎷兼帴',
  `commercial_content` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍟嗕笟鍐呭',
  `brand_content` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍝佺墝鍐呭',
  `aigc_content` bit(1) NOT NULL DEFAULT b'1' COMMENT 'AIGC鍐呭',
  `labels` varchar(255) DEFAULT NULL COMMENT '鏍囩',
  `last_auth_time` datetime DEFAULT NULL COMMENT '鏈€杩戞巿鏉冩椂闂?,
  `last_publish_time` datetime DEFAULT NULL COMMENT '鏈€杩戝彂甯冩椂闂?,
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '澶辫触鍘熷洜',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '鐘舵€?,
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_tiktok_account_open_id` (`tenant_id`, `open_id`, `deleted`),
  KEY `idx_tk_tiktok_account_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_tiktok_account_token` (`tenant_id`, `token_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok璐﹀彿';

CREATE TABLE IF NOT EXISTS `tk_tiktok_account_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'TikTok璐﹀彿鍒嗙粍缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `name` varchar(128) NOT NULL COMMENT '鍒嗙粍鍚嶇О',
  `scene` varchar(64) DEFAULT NULL COMMENT '浣跨敤鍦烘櫙',
  `labels` varchar(255) DEFAULT NULL COMMENT '鏍囩',
  `remark` varchar(512) DEFAULT NULL COMMENT '澶囨敞',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '鐘舵€?,
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_tk_tiktok_account_group_company` (`tenant_id`, `company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok璐﹀彿鍒嗙粍';

CREATE TABLE IF NOT EXISTS `tk_tiktok_account_group_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '璐﹀彿鍒嗙粍鍏崇郴缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `group_id` bigint NOT NULL COMMENT '鍒嗙粍缂栧彿',
  `account_id` bigint NOT NULL COMMENT '璐﹀彿缂栧彿',
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_tiktok_group_rel` (`tenant_id`, `group_id`, `account_id`, `deleted`),
  KEY `idx_tk_tiktok_group_rel_account` (`tenant_id`, `account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok璐﹀彿鍒嗙粍鍏崇郴';

CREATE TABLE IF NOT EXISTS `tk_tiktok_auth_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'TikTok鎺堟潈浼氳瘽缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `user_id` bigint DEFAULT NULL COMMENT '鍙戣捣鐢ㄦ埛缂栧彿',
  `auth_type` varchar(32) NOT NULL COMMENT '鎺堟潈鏂瑰紡锛歊EDIRECT/QR_CODE',
  `state` varchar(128) NOT NULL COMMENT 'OAuth state',
  `code_verifier` varchar(255) DEFAULT NULL COMMENT 'PKCE verifier',
  `code_challenge` varchar(255) DEFAULT NULL COMMENT 'PKCE challenge',
  `client_ticket` varchar(255) DEFAULT NULL COMMENT '浜岀淮鐮佺エ鎹?,
  `qrcode_token` varchar(255) DEFAULT NULL COMMENT '浜岀淮鐮佽疆璇?token',
  `qrcode_url` varchar(1000) DEFAULT NULL COMMENT '浜岀淮鐮佸湴鍧€',
  `authorize_url` varchar(1500) DEFAULT NULL COMMENT '鎺堟潈鍦板潃',
  `status` varchar(32) NOT NULL COMMENT '鐘舵€?,
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '澶辫触鍘熷洜',
  `expire_time` datetime NOT NULL COMMENT '杩囨湡鏃堕棿',
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_tiktok_auth_state` (`tenant_id`, `state`, `deleted`),
  KEY `idx_tk_tiktok_auth_ticket` (`tenant_id`, `client_ticket`),
  KEY `idx_tk_tiktok_auth_company` (`tenant_id`, `company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok鎺堟潈浼氳瘽';

CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'TikTok鍙戝竷浠诲姟缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '涓氬姟娴佹按鍙?,
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `generation_task_id` bigint DEFAULT NULL COMMENT '鐢熸垚浠诲姟缂栧彿',
  `uploaded_video_id` bigint DEFAULT NULL COMMENT '用户上传素材视频编号',
  `source_type` varchar(32) NOT NULL DEFAULT 'GENERATED' COMMENT '视频来源',
  `title` varchar(255) NOT NULL COMMENT '鍙戝竷鏍囬',
  `caption` varchar(2200) DEFAULT NULL COMMENT '鍙戝竷鏂囨',
  `video_url` varchar(512) NOT NULL COMMENT '瑙嗛鍦板潃',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面地址',
  `cover_timestamp_ms` bigint DEFAULT NULL COMMENT '视频封面时间点（毫秒）',
  `post_mode` varchar(32) NOT NULL COMMENT '鍙戝竷妯″紡锛欴IRECT_POST/UPLOAD_TO_INBOX',
  `privacy_level` varchar(64) DEFAULT NULL COMMENT '闅愮绾у埆',
  `account_count` int NOT NULL DEFAULT 0 COMMENT '璐﹀彿鏁伴噺',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '鎴愬姛鏁伴噺',
  `failed_count` int NOT NULL DEFAULT 0 COMMENT '澶辫触鏁伴噺',
  `pending_count` int NOT NULL DEFAULT 0 COMMENT '寰呭鐞嗘暟閲?,
  `status` varchar(32) NOT NULL COMMENT '鐘舵€?,
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '澶辫触鍘熷洜',
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_tk_tiktok_publish_task_trace` (`tenant_id`, `business_trace_id`),
  KEY `idx_tk_tiktok_publish_task_company` (`tenant_id`, `company_id`),
  KEY `idx_tk_tiktok_publish_task_generation` (`generation_task_id`)
  ,KEY `idx_tk_tiktok_publish_task_uploaded_video` (`tenant_id`, `uploaded_video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok鍙戝竷浠诲姟';

CREATE TABLE IF NOT EXISTS `tk_tiktok_publish_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'TikTok鍙戝竷鏄庣粏缂栧彿',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '绉熸埛缂栧彿',
  `business_trace_id` varchar(64) DEFAULT NULL COMMENT '涓氬姟娴佹按鍙?,
  `company_id` bigint NOT NULL COMMENT '鍏徃缂栧彿',
  `publish_task_id` bigint NOT NULL COMMENT '鍙戝竷浠诲姟缂栧彿',
  `generation_task_id` bigint DEFAULT NULL COMMENT '鐢熸垚浠诲姟缂栧彿',
  `uploaded_video_id` bigint DEFAULT NULL COMMENT '用户上传素材视频编号',
  `source_type` varchar(32) NOT NULL DEFAULT 'GENERATED' COMMENT '视频来源',
  `account_id` bigint NOT NULL COMMENT '璐﹀彿缂栧彿',
  `account_display_name` varchar(128) DEFAULT NULL COMMENT '璐﹀彿鍚嶇О',
  `publish_id` varchar(128) DEFAULT NULL COMMENT 'TikTok Publish ID',
  `tiktok_status` varchar(64) DEFAULT NULL COMMENT 'TikTok鐘舵€?,
  `status` varchar(32) NOT NULL COMMENT '鏈湴鐘舵€?,
  `post_mode` varchar(32) NOT NULL COMMENT '鍙戝竷妯″紡',
  `privacy_level` varchar(64) DEFAULT NULL COMMENT '闅愮绾у埆',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面地址',
  `cover_timestamp_ms` bigint DEFAULT NULL COMMENT '视频封面时间点（毫秒）',
  `allow_comment` bit(1) NOT NULL DEFAULT b'1' COMMENT '鍏佽璇勮',
  `allow_duet` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍏佽鍚堟媿',
  `allow_stitch` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍏佽鎷兼帴',
  `commercial_content` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍟嗕笟鍐呭',
  `brand_content` bit(1) NOT NULL DEFAULT b'0' COMMENT '鍝佺墝鍐呭',
  `aigc_content` bit(1) NOT NULL DEFAULT b'1' COMMENT 'AIGC鍐呭',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '澶辫触鍘熷洜',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '閲嶈瘯娆℃暟',
  `last_sync_time` datetime DEFAULT NULL COMMENT '鏈€杩戝悓姝ユ椂闂?,
  `creator` varchar(64) DEFAULT '' COMMENT '鍒涘缓鑰?,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updater` varchar(64) DEFAULT '' COMMENT '鏇存柊鑰?,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_tk_tiktok_publish_detail_trace` (`tenant_id`, `business_trace_id`),
  KEY `idx_tk_tiktok_publish_detail_task` (`publish_task_id`),
  KEY `idx_tk_tiktok_publish_detail_account` (`tenant_id`, `account_id`),
  KEY `idx_tk_tiktok_publish_detail_status` (`tenant_id`, `status`)
  ,KEY `idx_tk_tiktok_publish_detail_uploaded_video` (`tenant_id`, `uploaded_video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK TikTok鍙戝竷鏄庣粏';

INSERT INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'TIKTOK', 'client-key', '', 'TikTok Login Kit / Content Posting API Client Key', 0),
  (1, 'TIKTOK', 'client-secret', '', 'TikTok Login Kit / Content Posting API Client Secret', 0),
  (1, 'TIKTOK', 'redirect-uri', '', 'TikTok 瀹樻柟鎺堟潈鍥炶皟鍦板潃锛岄渶瑕佷笌寮€鍙戣€呭悗鍙颁竴鑷?, 0),
  (1, 'TIKTOK', 'default-scopes', 'user.info.basic,video.publish,video.upload', '榛樿鎺堟潈鑼冨洿', 0),
  (1, 'TIKTOK', 'default-post-mode', 'DIRECT_POST', '榛樿鍙戝竷妯″紡锛欴IRECT_POST/UPLOAD_TO_INBOX', 0),
  (1, 'TIKTOK', 'verified-pull-domain', '', 'TikTok 宸查獙璇佺殑瑙嗛鎷夊彇鍩熷悕锛涗负绌烘椂浼樺厛浣跨敤鏂囦欢涓婁紶', 0)
ON DUPLICATE KEY UPDATE `remark` = VALUES(`remark`);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6004, '瑙嗛鍙戝竷涓績', 'tk:video-publish-center:query', 2, 4, 6000, 'video-publish-center', 'ep:promotion', 'tk/video-publish-center/index', 'TkVideoPublishCenter', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6020, 'TikTok璐﹀彿鎺堟潈', 'tk:tiktok-account:authorize', 3, 1, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6021, 'TikTok璐﹀彿閰嶇疆', 'tk:tiktok-account:update', 3, 2, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6022, 'TikTok璐﹀彿鍒嗙粍', 'tk:tiktok-account-group:manage', 3, 3, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6023, 'TikTok鍙戝竷鍒涘缓', 'tk:tiktok-publish:create', 3, 4, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6024, 'TikTok鍙戝竷閲嶈瘯', 'tk:tiktok-publish:retry', 3, 5, 6004, '', '', '', NULL, 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`);

