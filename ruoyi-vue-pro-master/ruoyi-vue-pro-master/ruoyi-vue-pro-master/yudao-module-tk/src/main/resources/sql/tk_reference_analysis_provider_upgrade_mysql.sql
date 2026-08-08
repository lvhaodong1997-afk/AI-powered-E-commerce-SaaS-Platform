SET @schema_name = DATABASE();

SET @provider_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_reference_analysis' AND column_name = 'analysis_provider'
);
SET @provider_sql = IF(@provider_exists = 0,
  'ALTER TABLE `tk_reference_analysis` ADD COLUMN `analysis_provider` varchar(32) NOT NULL DEFAULT ''GEMINI'' COMMENT ''分析引擎'' AFTER `material_purpose`',
  'SELECT 1');
PREPARE provider_stmt FROM @provider_sql;
EXECUTE provider_stmt;
DEALLOCATE PREPARE provider_stmt;

SET @model_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = 'tk_reference_analysis' AND column_name = 'analysis_model'
);
SET @model_sql = IF(@model_exists = 0,
  'ALTER TABLE `tk_reference_analysis` ADD COLUMN `analysis_model` varchar(64) DEFAULT NULL COMMENT ''分析模型'' AFTER `analysis_provider`',
  'SELECT 1');
PREPARE model_stmt FROM @model_sql;
EXECUTE model_stmt;
DEALLOCATE PREPARE model_stmt;

UPDATE `tk_reference_analysis`
SET `analysis_provider` = 'GEMINI'
WHERE `analysis_provider` IS NULL OR `analysis_provider` = '';
