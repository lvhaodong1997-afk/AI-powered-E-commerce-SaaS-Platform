-- Apply after tk_channel_publish_upgrade_mysql.sql and before starting the new server.
DROP PROCEDURE IF EXISTS tk_add_open_x_columns;
DELIMITER $$
CREATE PROCEDURE tk_add_open_x_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_auth_session' AND column_name = 'open_client_id') THEN
        ALTER TABLE tk_social_auth_session ADD COLUMN open_client_id varchar(128) NULL,
            ADD COLUMN open_account_id bigint NULL, ADD COLUMN open_consent_hash char(64) NULL,
            ADD KEY idx_social_open_account (tenant_id, platform, open_account_id, status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'open_x_task_id') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN open_x_task_id varchar(48) NULL,
            ADD COLUMN open_x_connection_id bigint NULL, ADD COLUMN open_x_generation_task_id bigint NULL,
            ADD UNIQUE KEY uk_social_open_x_task (open_x_task_id);
    END IF;
END$$
DELIMITER ;
CALL tk_add_open_x_columns();
DROP PROCEDURE tk_add_open_x_columns;
