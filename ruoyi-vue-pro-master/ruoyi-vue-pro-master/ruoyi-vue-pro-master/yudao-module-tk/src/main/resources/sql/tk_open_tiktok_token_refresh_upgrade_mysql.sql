-- Pre-refresh Open API TikTok connection tokens, including accounts that have not published recently.
SET @schema_name = DATABASE();
SET @open_connection_token_index_sql = (
  SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @schema_name
              AND table_name = 'tk_open_tiktok_connection'
              AND index_name = 'idx_tk_open_connection_token_refresh'),
    'SELECT 1',
    'ALTER TABLE `tk_open_tiktok_connection` ADD KEY `idx_tk_open_connection_token_refresh` (`auth_status`, `deleted`, `access_token_expire_time`, `refresh_token_expire_time`)')
);
PREPARE open_connection_token_index_stmt FROM @open_connection_token_index_sql;
EXECUTE open_connection_token_index_stmt;
DEALLOCATE PREPARE open_connection_token_index_stmt;
