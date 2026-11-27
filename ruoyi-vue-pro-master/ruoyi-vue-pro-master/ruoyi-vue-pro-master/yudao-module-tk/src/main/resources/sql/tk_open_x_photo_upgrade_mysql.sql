-- Apply after X client/workspace isolation and the channel publishing migrations.
DROP PROCEDURE IF EXISTS tk_add_x_photo_columns;
DELIMITER $$
CREATE PROCEDURE tk_add_x_photo_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='tk_open_x_media' AND column_name='media_type') THEN
        ALTER TABLE tk_open_x_media ADD COLUMN media_type varchar(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='tk_open_x_publish_task' AND column_name='photo_ids') THEN
        ALTER TABLE tk_open_x_publish_task ADD COLUMN photo_ids varchar(512) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                   AND table_name='tk_social_publish_task' AND column_name='channel_photo_ids') THEN
        ALTER TABLE tk_social_publish_task ADD COLUMN channel_photo_ids varchar(512) NULL;
    END IF;
END$$
DELIMITER ;
CALL tk_add_x_photo_columns();
DROP PROCEDURE tk_add_x_photo_columns;
