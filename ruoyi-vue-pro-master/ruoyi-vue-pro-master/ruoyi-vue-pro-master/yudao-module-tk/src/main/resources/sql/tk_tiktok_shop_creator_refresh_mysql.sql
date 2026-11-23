SET @shop_refresh_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_account' AND column_name='authorization_version'),
    'SELECT 1', 'ALTER TABLE tk_tiktok_shop_account ADD COLUMN authorization_version bigint NOT NULL DEFAULT 0');
PREPARE shop_refresh_stmt FROM @shop_refresh_ddl;
EXECUTE shop_refresh_stmt;
DEALLOCATE PREPARE shop_refresh_stmt;

SET @shop_refresh_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_account' AND column_name='refresh_retry_after'),
    'SELECT 1', 'ALTER TABLE tk_tiktok_shop_account ADD COLUMN refresh_retry_after datetime DEFAULT NULL');
PREPARE shop_refresh_stmt FROM @shop_refresh_ddl;
EXECUTE shop_refresh_stmt;
DEALLOCATE PREPARE shop_refresh_stmt;
