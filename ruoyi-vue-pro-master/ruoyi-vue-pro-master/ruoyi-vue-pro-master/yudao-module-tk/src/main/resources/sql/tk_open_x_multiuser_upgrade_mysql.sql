-- Additive multi-user support for X OpenAPI. Apply after tk_open_x_upgrade_mysql.sql.
-- Safe to rerun. Does not rewrite historical migrations or delete data.
DROP PROCEDURE IF EXISTS tk_add_open_x_multiuser;
DELIMITER $$
CREATE PROCEDURE tk_add_open_x_multiuser()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_auth_session' AND column_name = 'open_end_user_id') THEN
        ALTER TABLE tk_social_auth_session
            ADD COLUMN open_end_user_id varchar(128) NULL,
            ADD KEY idx_social_open_end_user (tenant_id, open_client_id, open_end_user_id, status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'open_end_user_id') THEN
        ALTER TABLE tk_social_publish_task
            ADD COLUMN open_end_user_id varchar(128) NULL,
            ADD KEY idx_social_open_x_end_user (tenant_id, open_end_user_id, idempotency_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_end_user') THEN
        CREATE TABLE tk_open_x_end_user (
            id bigint NOT NULL AUTO_INCREMENT,
            client_id varchar(128) NOT NULL,
            end_user_id varchar(128) NOT NULL,
            tenant_id bigint NOT NULL,
            company_id bigint NOT NULL,
            owner_user_id bigint NOT NULL,
            creator varchar(64) DEFAULT '',
            create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updater varchar(64) DEFAULT '',
            update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted bit(1) NOT NULL DEFAULT b'0',
            PRIMARY KEY (id),
            UNIQUE KEY uk_open_x_end_user (client_id, end_user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END$$
DELIMITER ;
CALL tk_add_open_x_multiuser();
DROP PROCEDURE tk_add_open_x_multiuser;
