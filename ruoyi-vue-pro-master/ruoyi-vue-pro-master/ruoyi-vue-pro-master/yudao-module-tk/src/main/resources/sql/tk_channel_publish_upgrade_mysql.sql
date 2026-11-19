-- Apply after tk_social_publish_center_mysql.sql, before deploying the new server.
DROP PROCEDURE IF EXISTS tk_add_channel_publish_columns;
DELIMITER $$
CREATE PROCEDURE tk_add_channel_publish_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'channel') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel varchar(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'channel_text') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel_text text NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'channel_privacy') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel_privacy varchar(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'channel_for_kids') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel_for_kids bit(1) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_task' AND column_name = 'channel_synthetic') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel_synthetic bit(1) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_detail' AND column_name = 'upload_session_ciphertext') THEN
        ALTER TABLE tk_social_publish_detail ADD COLUMN upload_session_ciphertext text NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'tk_social_publish_detail' AND column_name = 'upload_offset') THEN
        ALTER TABLE tk_social_publish_detail ADD COLUMN upload_offset bigint NULL;
    END IF;
END$$
DELIMITER ;
CALL tk_add_channel_publish_columns();
DROP PROCEDURE tk_add_channel_publish_columns;

SELECT MIN(parent_id) INTO @channel_parent FROM system_menu
WHERE permission = 'tk:video-publish-center:query' AND type = 2 AND deleted = b'0';

SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'X 发布', 'tk:x-publish:query', 2, 7, @channel_parent,
       'x-publish', 'simple-icons:x', 'tk/x-publish/index', 'TkXPublish', 0, b'1', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_parent IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:x-publish:query' AND deleted = b'0'
);
SELECT MIN(id) INTO @channel_menu FROM system_menu WHERE permission = 'tk:x-publish:query' AND deleted = b'0';
SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'X 授权', 'tk:x-publish:authorize', 3, 0, @channel_menu,
       '', '', '', '', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_menu IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:x-publish:authorize' AND deleted = b'0'
);
SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'X 发布', 'tk:x-publish:create', 3, 0, @channel_menu,
       '', '', '', '', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_menu IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:x-publish:create' AND deleted = b'0'
);

SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'YouTube 发布', 'tk:youtube-publish:query', 2, 8, @channel_parent,
       'youtube-publish', 'simple-icons:youtube', 'tk/youtube-publish/index', 'TkYoutubePublish', 0, b'1', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_parent IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:youtube-publish:query' AND deleted = b'0'
);
SELECT MIN(id) INTO @channel_menu FROM system_menu WHERE permission = 'tk:youtube-publish:query' AND deleted = b'0';
SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'YouTube 授权', 'tk:youtube-publish:authorize', 3, 0, @channel_menu,
       '', '', '', '', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_menu IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:youtube-publish:authorize' AND deleted = b'0'
);
SELECT COALESCE(MAX(id), 0) + 1 INTO @channel_id FROM system_menu;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @channel_id, 'YouTube 发布', 'tk:youtube-publish:create', 3, 0, @channel_menu,
       '', '', '', '', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @channel_menu IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM system_menu WHERE permission = 'tk:youtube-publish:create' AND deleted = b'0'
);
