SET @voice_enabled_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'tk_generation_task' AND column_name = 'voice_enabled'
);
SET @voice_enabled_sql = IF(@voice_enabled_exists = 0,
  'ALTER TABLE `tk_generation_task` ADD COLUMN `voice_enabled` bit(1) NOT NULL DEFAULT b''1'' COMMENT ''是否生成AI口播'' AFTER `voice_profile_id`',
  'SELECT ''tk_generation_task.voice_enabled already exists''');
PREPARE stmt FROM @voice_enabled_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
