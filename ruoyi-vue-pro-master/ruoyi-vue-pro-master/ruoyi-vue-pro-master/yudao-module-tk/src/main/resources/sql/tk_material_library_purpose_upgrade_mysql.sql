SET NAMES utf8mb4;
SET @schema_name := DATABASE();

SET @tk_library_material_purpose_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'tk_material_library' AND column_name = 'material_purpose'
);
SET @tk_library_material_purpose_sql := IF(@tk_library_material_purpose_exists = 0,
    'ALTER TABLE `tk_material_library` ADD COLUMN `material_purpose` varchar(32) NOT NULL DEFAULT ''ECOMMERCE'' COMMENT ''素材类型：ECOMMERCE/LEAD_GENERATION'' AFTER `scene`',
    'SELECT 1'
);
PREPARE tk_library_material_purpose_stmt FROM @tk_library_material_purpose_sql;
EXECUTE tk_library_material_purpose_stmt;
DEALLOCATE PREPARE tk_library_material_purpose_stmt;

SET @tk_library_material_purpose_index_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'tk_material_library' AND index_name = 'idx_tk_material_library_purpose'
);
SET @tk_library_material_purpose_index_sql := IF(@tk_library_material_purpose_index_exists = 0,
    'ALTER TABLE `tk_material_library` ADD KEY `idx_tk_material_library_purpose` (`tenant_id`, `material_purpose`)',
    'SELECT 1'
);
PREPARE tk_library_material_purpose_index_stmt FROM @tk_library_material_purpose_index_sql;
EXECUTE tk_library_material_purpose_index_stmt;
DEALLOCATE PREPARE tk_library_material_purpose_index_stmt;

UPDATE `tk_material_library`
SET `material_purpose` = 'ECOMMERCE'
WHERE `material_purpose` IS NULL OR `material_purpose` = '';
