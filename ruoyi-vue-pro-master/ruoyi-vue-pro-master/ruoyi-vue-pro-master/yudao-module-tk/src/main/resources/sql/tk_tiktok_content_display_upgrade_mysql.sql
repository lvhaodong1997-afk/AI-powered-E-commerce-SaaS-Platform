SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM `information_schema`.`columns`
    WHERE `table_schema` = @schema_name
      AND `table_name` = 'tk_api_key_config'
      AND `column_name` = 'config_value'
  ) AND EXISTS (
    SELECT 1
    FROM `tk_api_key_config`
    WHERE `provider` = 'TIKTOK'
      AND `config_key` = 'default-scopes'
      AND `config_value` <> ''
      AND FIND_IN_SET('video.list', `config_value`) = 0
  ),
  'UPDATE `tk_api_key_config` SET `config_value` = CONCAT(TRIM(TRAILING '','' FROM `config_value`), '',video.list'') WHERE `provider` = ''TIKTOK'' AND `config_key` = ''default-scopes'' AND `config_value` <> '''' AND FIND_IN_SET(''video.list'', `config_value`) = 0',
  'SELECT ''TikTok default-scopes already contains video.list or is not configured'''
);
PREPARE tk_tiktok_content_display_stmt FROM @sql;
EXECUTE tk_tiktok_content_display_stmt;
DEALLOCATE PREPARE tk_tiktok_content_display_stmt;
