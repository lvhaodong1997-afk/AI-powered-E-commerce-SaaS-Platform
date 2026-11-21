-- Apply after tk_open_x_client_isolation_upgrade_mysql.sql. Safe to rerun.
-- Legacy resources remain in the client-only namespace; never infer ownership from end_user_id.
DROP PROCEDURE IF EXISTS tk_add_open_x_workspace_isolation;
DELIMITER $$
CREATE PROCEDURE tk_add_open_x_workspace_isolation()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_auth_session' AND column_name = 'workspace_id') THEN
        ALTER TABLE tk_open_x_auth_session ADD COLUMN workspace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_connection' AND column_name = 'workspace_id') THEN
        ALTER TABLE tk_open_x_connection ADD COLUMN workspace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_media' AND column_name = 'workspace_id') THEN
        ALTER TABLE tk_open_x_media ADD COLUMN workspace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_publish_task' AND column_name = 'workspace_id') THEN
        ALTER TABLE tk_open_x_publish_task ADD COLUMN workspace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
                   AND table_name = 'tk_open_x_connection' AND index_name = 'idx_open_x_connection_workspace') THEN
        ALTER TABLE tk_open_x_connection ADD INDEX idx_open_x_connection_workspace (client_id, workspace_id, id);
    END IF;
END$$
DELIMITER ;
CALL tk_add_open_x_workspace_isolation();
DROP PROCEDURE tk_add_open_x_workspace_isolation;
