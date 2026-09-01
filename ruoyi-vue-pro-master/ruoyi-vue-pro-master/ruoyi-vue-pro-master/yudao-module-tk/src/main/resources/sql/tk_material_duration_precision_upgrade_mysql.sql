SET @schema_name := DATABASE();

SET @sql := IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name
          AND table_name = 'tk_material_video' AND column_name = 'duration_ms'),
  'SELECT ''duration_ms already exists''',
  'ALTER TABLE `tk_material_video` ADD COLUMN `duration_ms` bigint DEFAULT NULL COMMENT ''素材真实时长，单位毫秒'' AFTER `duration`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
