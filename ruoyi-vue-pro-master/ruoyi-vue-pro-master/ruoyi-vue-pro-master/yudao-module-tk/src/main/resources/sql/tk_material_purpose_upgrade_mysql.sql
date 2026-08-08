SET @schema_name := DATABASE();

SET @tk_reference_material_purpose_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_reference_analysis' AND column_name = 'material_purpose'
);
SET @tk_reference_material_purpose_sql := IF(@tk_reference_material_purpose_exists = 0,
    'ALTER TABLE `tk_reference_analysis` ADD COLUMN `material_purpose` varchar(32) NOT NULL DEFAULT ''ECOMMERCE'' COMMENT ''素材类型：ECOMMERCE电商素材，LEAD_GENERATION引流素材'' AFTER `reference_duration`',
    'SELECT 1'
);
PREPARE tk_reference_material_purpose_stmt FROM @tk_reference_material_purpose_sql;
EXECUTE tk_reference_material_purpose_stmt;
DEALLOCATE PREPARE tk_reference_material_purpose_stmt;

UPDATE `tk_reference_analysis`
SET `material_purpose` = 'ECOMMERCE'
WHERE `material_purpose` IS NULL OR `material_purpose` = '';

SET @tk_generation_material_purpose_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_generation_task' AND column_name = 'material_purpose'
);
SET @tk_generation_material_purpose_sql := IF(@tk_generation_material_purpose_exists = 0,
    'ALTER TABLE `tk_generation_task` ADD COLUMN `material_purpose` varchar(32) NOT NULL DEFAULT ''ECOMMERCE'' COMMENT ''素材类型：ECOMMERCE电商素材，LEAD_GENERATION引流素材'' AFTER `target_language`',
    'SELECT 1'
);
PREPARE tk_generation_material_purpose_stmt FROM @tk_generation_material_purpose_sql;
EXECUTE tk_generation_material_purpose_stmt;
DEALLOCATE PREPARE tk_generation_material_purpose_stmt;

UPDATE `tk_generation_task`
SET `material_purpose` = 'ECOMMERCE'
WHERE `material_purpose` IS NULL OR `material_purpose` = '';
