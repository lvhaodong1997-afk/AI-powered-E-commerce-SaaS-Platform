SET NAMES utf8mb4;

SET @schema_name := DATABASE();

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'verified_transcript_text'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `verified_transcript_text` longtext DEFAULT NULL COMMENT ''校验后的完整口播文案'' AFTER `words_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'verified_segments_json'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `verified_segments_json` longtext DEFAULT NULL COMMENT ''校验后的分段时间轴 JSON'' AFTER `verified_transcript_text`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'text_verify_status'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `text_verify_status` varchar(32) DEFAULT NULL COMMENT ''文案校验状态'' AFTER `verified_segments_json`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'text_verify_fail_reason'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `text_verify_fail_reason` varchar(1024) DEFAULT NULL COMMENT ''文案校验失败原因'' AFTER `text_verify_status`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'text_verify_model'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `text_verify_model` varchar(128) DEFAULT NULL COMMENT ''文案校验模型'' AFTER `text_verify_fail_reason`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'tk_open_video_transcript_task' AND column_name = 'text_verify_prompt_version'),
    'SELECT 1',
    'ALTER TABLE `tk_open_video_transcript_task` ADD COLUMN `text_verify_prompt_version` varchar(32) DEFAULT NULL COMMENT ''文案校验提示词版本'' AFTER `text_verify_model`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'DEEPSEEK', 'api-key', '', 'DeepSeek 文案生成 API Key，留空时使用环境变量 DEEPSEEK_API_KEY', 0),
  (1, 'DEEPSEEK', 'base-url', 'https://api.deepseek.com', 'DeepSeek API 基础地址', 0),
  (1, 'DEEPSEEK', 'model', 'deepseek-v4-flash', 'DeepSeek 文案生成模型', 0),
  (1, 'DEEPSEEK', 'timeout-seconds', '60', 'DeepSeek 请求超时时间（秒）', 0),
  (1, 'DEEPSEEK', 'max-output-tokens', '2048', 'DeepSeek 最大输出 Token 数', 0),
  (1, 'DEEPSEEK', 'retry-count', '1', 'DeepSeek 临时失败重试次数', 0),
  (1, 'DEEPSEEK', 'retry-delay-ms', '500', 'DeepSeek 重试退避起始间隔（毫秒）', 0)
ON DUPLICATE KEY UPDATE
  `remark` = VALUES(`remark`);
