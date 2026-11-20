-- Additive X OpenAPI client isolation. Apply after tk_open_x_upgrade_mysql.sql
-- and tk_open_x_multiuser_upgrade_mysql.sql. Safe to rerun.
-- Does not rewrite historical migrations, delete data, or reassign workbench accounts
-- that have no open_client_id. Rollback: keep these tables; revert the JAR.
-- Tokens stay in historical social account rows until first OpenAPI use copies them
-- into the dedicated connection row with a new encryption context; workbench rows
-- are never updated by that copy or by later refresh/revoke.

DROP PROCEDURE IF EXISTS tk_add_open_x_client_isolation;
DELIMITER $$
CREATE PROCEDURE tk_add_open_x_client_isolation()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_api_client' AND column_name = 'return_url_whitelist') THEN
        ALTER TABLE tk_open_api_client
            ADD COLUMN return_url_whitelist text NULL COMMENT 'JSON array of exact HTTPS return URLs';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_connection') THEN
        CREATE TABLE tk_open_x_connection (
            id bigint NOT NULL AUTO_INCREMENT,
            connection_id varchar(64) NOT NULL,
            client_id varchar(128) NOT NULL,
            external_account_id varchar(128) NOT NULL,
            display_name varchar(256) DEFAULT NULL,
            username varchar(256) DEFAULT NULL,
            access_token_cipher text,
            refresh_token_cipher text,
            token_expires_at datetime DEFAULT NULL,
            scopes varchar(512) DEFAULT NULL,
            auth_status varchar(32) NOT NULL,
            end_user_id varchar(128) DEFAULT NULL,
            source_social_session_id bigint DEFAULT NULL,
            source_social_account_id bigint DEFAULT NULL,
            fail_reason varchar(255) DEFAULT NULL,
            last_auth_time datetime DEFAULT NULL,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_connection_id (connection_id),
            KEY idx_open_x_connection_client (client_id, auth_status, id),
            KEY idx_open_x_connection_account (client_id, external_account_id, deleted)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_auth_session') THEN
        CREATE TABLE tk_open_x_auth_session (
            id bigint NOT NULL AUTO_INCREMENT,
            auth_session_id varchar(64) NOT NULL,
            client_id varchar(128) NOT NULL,
            oauth_state_hash char(64) DEFAULT NULL,
            code_verifier_cipher text,
            return_url varchar(1024) DEFAULT NULL,
            client_state varchar(256) DEFAULT NULL,
            end_user_id varchar(128) DEFAULT NULL,
            consent_hash char(64) DEFAULT NULL,
            authorize_url varchar(2048) DEFAULT NULL,
            status varchar(32) NOT NULL,
            connection_id varchar(64) DEFAULT NULL,
            fail_reason varchar(255) DEFAULT NULL,
            expire_time datetime NOT NULL,
            ticket_hash char(64) DEFAULT NULL,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_auth_session_id (auth_session_id),
            UNIQUE KEY uk_open_x_auth_state (oauth_state_hash),
            KEY idx_open_x_auth_client (client_id, status, id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_return_ticket') THEN
        CREATE TABLE tk_open_x_return_ticket (
            id bigint NOT NULL AUTO_INCREMENT,
            ticket_hash char(64) NOT NULL,
            ticket_cipher text,
            client_id varchar(128) NOT NULL,
            auth_session_id varchar(64) NOT NULL,
            purpose varchar(32) NOT NULL,
            status varchar(32) NOT NULL,
            expire_time datetime NOT NULL,
            consumed_time datetime DEFAULT NULL,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_ticket_hash (ticket_hash),
            KEY idx_open_x_ticket_session (client_id, auth_session_id, purpose, status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_media') THEN
        CREATE TABLE tk_open_x_media (
            id bigint NOT NULL AUTO_INCREMENT,
            upload_id varchar(64) NOT NULL,
            media_id varchar(64) DEFAULT NULL,
            client_id varchar(128) NOT NULL,
            file_name varchar(256) NOT NULL,
            file_size bigint NOT NULL,
            content_type varchar(128) DEFAULT NULL,
            sha256 char(64) DEFAULT NULL,
            upload_mode varchar(16) NOT NULL,
            object_key varchar(512) DEFAULT NULL,
            file_url varchar(1024) DEFAULT NULL,
            uploaded_size bigint DEFAULT 0,
            uploaded_chunks text,
            status varchar(32) NOT NULL,
            fail_reason varchar(255) DEFAULT NULL,
            expire_time datetime NOT NULL,
            completed_time datetime DEFAULT NULL,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_media_upload (upload_id),
            UNIQUE KEY uk_open_x_media_id (media_id),
            KEY idx_open_x_media_client (client_id, status, id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_publish_task') THEN
        CREATE TABLE tk_open_x_publish_task (
            id bigint NOT NULL AUTO_INCREMENT,
            task_id varchar(64) NOT NULL,
            client_id varchar(128) NOT NULL,
            connection_id varchar(64) NOT NULL,
            media_id varchar(64) NOT NULL,
            text varchar(280) NOT NULL,
            end_user_id varchar(128) DEFAULT NULL,
            idempotency_key varchar(128) NOT NULL,
            payload_hash char(64) NOT NULL,
            status varchar(32) NOT NULL,
            fail_code varchar(64) DEFAULT NULL,
            fail_reason varchar(255) DEFAULT NULL,
            tweet_id varchar(32) DEFAULT NULL,
            publish_url varchar(512) DEFAULT NULL,
            platform_status varchar(32) DEFAULT NULL,
            external_container_id varchar(64) DEFAULT NULL,
            upload_offset bigint DEFAULT 0,
            next_retry_time datetime DEFAULT NULL,
            lease_token varchar(64) DEFAULT NULL,
            lease_until datetime DEFAULT NULL,
            poll_count int DEFAULT 0,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_task_id (task_id),
            UNIQUE KEY uk_open_x_task_idempotency (client_id, idempotency_key),
            KEY idx_open_x_task_queue (status, next_retry_time, id),
            KEY idx_open_x_task_connection (client_id, connection_id, id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END$$
DELIMITER ;
CALL tk_add_open_x_client_isolation();
DROP PROCEDURE tk_add_open_x_client_isolation;

-- Copy completed OpenAPI X sessions into dedicated connections. Workbench sessions
-- without open_client_id are left untouched. Existing tokens remain on the source
-- social account until first OpenAPI use; this insert never copies ciphertext.
INSERT INTO tk_open_x_connection (
    connection_id, client_id, external_account_id, display_name, username,
    auth_status, end_user_id, source_social_session_id, source_social_account_id,
    last_auth_time, create_time, update_time, deleted
)
SELECT
    CONCAT('xconn_migrated_', latest.session_id),
    latest.open_client_id,
    latest.external_account_id,
    latest.account_name,
    latest.username,
    latest.auth_status,
    latest.open_end_user_id,
    latest.session_id,
    latest.open_account_id,
    latest.last_auth_time,
    NOW(),
    NOW(),
    b'0'
FROM (
    SELECT s.id AS session_id, s.open_client_id, a.external_account_id, a.account_name, a.username,
           CASE WHEN s.status = 'SUCCESS' AND a.status = 'AUTHORIZED' THEN 'AUTHORIZED' ELSE 'REVOKED' END AS auth_status,
           s.open_end_user_id, s.open_account_id, a.last_auth_time
    FROM tk_social_auth_session s
    INNER JOIN tk_social_account a ON a.id = s.open_account_id AND a.deleted = b'0'
    INNER JOIN (
        SELECT s2.open_client_id, a2.external_account_id, MAX(s2.id) AS max_id
        FROM tk_social_auth_session s2
        INNER JOIN tk_social_account a2 ON a2.id = s2.open_account_id AND a2.deleted = b'0'
        WHERE s2.deleted = b'0'
          AND s2.platform = 'X'
          AND s2.open_client_id IS NOT NULL
          AND s2.open_client_id <> ''
          AND s2.status IN ('SUCCESS', 'REVOKED')
          AND a2.external_account_id IS NOT NULL
          AND a2.external_account_id <> ''
        GROUP BY s2.open_client_id, a2.external_account_id
    ) picked ON picked.max_id = s.id
    WHERE s.deleted = b'0'
) latest
WHERE NOT EXISTS (
    SELECT 1 FROM tk_open_x_connection existing
    WHERE existing.deleted = b'0'
      AND existing.client_id = latest.open_client_id
      AND existing.external_account_id = latest.external_account_id
);