SET @schema_name = DATABASE();

SET @provider_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'tts_provider'
);
SET @provider_sql = IF(@provider_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `tts_provider` varchar(32) DEFAULT NULL COMMENT ''TTS provider'' AFTER `voice_id`',
  'SELECT 1');
PREPARE provider_stmt FROM @provider_sql;
EXECUTE provider_stmt;
DEALLOCATE PREPARE provider_stmt;

SET @mimo_mode_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'mimo_voice_mode'
);
SET @mimo_mode_sql = IF(@mimo_mode_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `mimo_voice_mode` varchar(32) DEFAULT NULL COMMENT ''MiMo voice mode'' AFTER `voice_enabled`',
  'SELECT 1');
PREPARE mimo_mode_stmt FROM @mimo_mode_sql;
EXECUTE mimo_mode_stmt;
DEALLOCATE PREPARE mimo_mode_stmt;

SET @mimo_code_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'mimo_voice_code'
);
SET @mimo_code_sql = IF(@mimo_code_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `mimo_voice_code` varchar(160) DEFAULT NULL COMMENT ''MiMo preset voice code'' AFTER `mimo_voice_mode`',
  'SELECT 1');
PREPARE mimo_code_stmt FROM @mimo_code_sql;
EXECUTE mimo_code_stmt;
DEALLOCATE PREPARE mimo_code_stmt;

SET @mimo_prompt_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'mimo_voice_prompt'
);
SET @mimo_prompt_sql = IF(@mimo_prompt_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `mimo_voice_prompt` text DEFAULT NULL COMMENT ''MiMo voice design prompt'' AFTER `mimo_voice_code`',
  'SELECT 1');
PREPARE mimo_prompt_stmt FROM @mimo_prompt_sql;
EXECUTE mimo_prompt_stmt;
DEALLOCATE PREPARE mimo_prompt_stmt;

SET @mimo_sample_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'mimo_voice_sample_url'
);
SET @mimo_sample_sql = IF(@mimo_sample_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `mimo_voice_sample_url` varchar(512) DEFAULT NULL COMMENT ''MiMo voice clone sample URL'' AFTER `mimo_voice_prompt`',
  'SELECT 1');
PREPARE mimo_sample_stmt FROM @mimo_sample_sql;
EXECUTE mimo_sample_stmt;
DEALLOCATE PREPARE mimo_sample_stmt;

UPDATE `tk_generation_task`
SET `tts_provider` = 'DASHSCOPE'
WHERE `tts_provider` IS NULL OR `tts_provider` = '';

INSERT IGNORE INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'MIMO', 'api-key', '', 'MiMo TTS API Key', 0),
  (1, 'MIMO', 'base-url', 'https://api.xiaomimimo.com/v1', 'MiMo API base URL', 0),
  (1, 'MIMO', 'preset-model', 'mimo-v2.5-tts', 'MiMo preset voice model', 0),
  (1, 'MIMO', 'voice-design-model', 'mimo-v2.5-tts-voicedesign', 'MiMo voice design model', 0),
  (1, 'MIMO', 'voice-clone-model', 'mimo-v2.5-tts-voiceclone', 'MiMo voice clone model', 0),
  (1, 'MIMO', 'format', 'wav', 'MiMo audio format', 0),
  (1, 'MIMO', 'optimize-text-preview', 'true', 'MiMo optimize text preview', 0),
  (1, 'MIMO', 'default-voice', 'Mia', 'MiMo default preset voice', 0),
  (1, 'MIMO', 'timeout-seconds', '120', 'MiMo request timeout seconds', 0);
