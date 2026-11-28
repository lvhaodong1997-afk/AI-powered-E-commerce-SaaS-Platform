CREATE TABLE IF NOT EXISTS tk_open_meta_auth_session (
    id varchar(48) NOT NULL PRIMARY KEY,
    client_id varchar(64) NOT NULL, workspace_id char(36) NOT NULL, platform varchar(16) NOT NULL,
    state_hash char(64) NOT NULL, status varchar(24) NOT NULL, expires_at datetime(3) NOT NULL,
    candidate_cipher mediumtext, connection_id varchar(48), error_code varchar(64),
    UNIQUE KEY uk_meta_state (state_hash), KEY idx_meta_auth_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_open_meta_connection (
    id varchar(48) NOT NULL PRIMARY KEY,
    client_id varchar(64) NOT NULL, workspace_id char(36) NOT NULL, platform varchar(16) NOT NULL,
    external_id varchar(100) NOT NULL, account_name varchar(255) NOT NULL,
    token_cipher text, token_expires_at datetime(3), status varchar(24) NOT NULL,
    UNIQUE KEY uk_meta_connection_scope (client_id, workspace_id, platform, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_open_meta_media (
    id varchar(48) NOT NULL PRIMARY KEY,
    client_id varchar(64) NOT NULL, workspace_id char(36) NOT NULL, platform varchar(16) NOT NULL,
    content_type varchar(32) NOT NULL, file_size bigint NOT NULL, sha256 char(64) NOT NULL,
    status varchar(24) NOT NULL, expires_at datetime(3) NOT NULL,
    KEY idx_meta_media_expiry (expires_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS tk_open_meta_task (
    id varchar(48) NOT NULL PRIMARY KEY,
    client_id varchar(64) NOT NULL, workspace_id char(36) NOT NULL, platform varchar(16) NOT NULL,
    connection_id varchar(48) NOT NULL, media_id varchar(48), media_type varchar(12) NOT NULL,
    body_text varchar(2200) NOT NULL, idempotency_key varchar(128) NOT NULL, request_hash char(64) NOT NULL,
    status varchar(24) NOT NULL, next_at datetime(3) NOT NULL, expires_at datetime(3) NOT NULL,
    lease_id varchar(48), lease_until datetime(3), container_id varchar(100), platform_status varchar(64),
    provider_media_id varchar(100), provider_post_id varchar(128), publish_url varchar(1024), error_code varchar(64),
    notified tinyint NOT NULL DEFAULT 0,
    UNIQUE KEY uk_meta_task_idempotency (client_id, workspace_id, platform, idempotency_key),
    KEY idx_meta_task_due (platform, status, next_at), KEY idx_meta_task_lease (status, lease_until),
    KEY idx_meta_task_notice (platform, notified, status), KEY idx_meta_task_media (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
