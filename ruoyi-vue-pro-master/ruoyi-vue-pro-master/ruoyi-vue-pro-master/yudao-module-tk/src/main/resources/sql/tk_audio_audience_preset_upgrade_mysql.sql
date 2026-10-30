-- Audience audio presets. Re-runnable on MySQL 5.7/8.0; never rewrites task volumes.
CREATE TABLE IF NOT EXISTS tk_audio_audience_preset (
 id bigint NOT NULL AUTO_INCREMENT,
 tenant_id bigint NOT NULL,
 company_id bigint NOT NULL DEFAULT 0 COMMENT '0 = tenant scope',
 user_id bigint NOT NULL,
 audience_code varchar(32) NOT NULL,
 tts_provider varchar(32) DEFAULT NULL,
 voice_config text NOT NULL,
 bgm_enabled bit(1) NOT NULL DEFAULT b'0',
 bgm_asset_id bigint DEFAULT NULL,
 bgm_volume double NOT NULL DEFAULT 0.2,
 config_status varchar(16) NOT NULL DEFAULT 'READY',
 version int NOT NULL DEFAULT 1,
 creator varchar(64) DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY (id),
 UNIQUE KEY uk_audio_preset_scope (tenant_id,company_id,user_id,audience_code),
 KEY idx_audio_preset_bgm (tenant_id,bgm_asset_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Personal audience audio presets';

SET @audio_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_generation_task' AND column_name='audience_code');
SET @audio_column_sql = IF(@audio_column_exists=0, 'ALTER TABLE tk_generation_task ADD COLUMN audience_code varchar(32) DEFAULT NULL', 'SELECT 1');
PREPARE audio_column_stmt FROM @audio_column_sql;
EXECUTE audio_column_stmt;
DEALLOCATE PREPARE audio_column_stmt;

SET @audio_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_generation_task' AND column_name='audio_preset_id');
SET @audio_column_sql = IF(@audio_column_exists=0, 'ALTER TABLE tk_generation_task ADD COLUMN audio_preset_id bigint DEFAULT NULL', 'SELECT 1');
PREPARE audio_column_stmt FROM @audio_column_sql;
EXECUTE audio_column_stmt;
DEALLOCATE PREPARE audio_column_stmt;

SET @audio_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_generation_task' AND column_name='audio_preset_version');
SET @audio_column_sql = IF(@audio_column_exists=0, 'ALTER TABLE tk_generation_task ADD COLUMN audio_preset_version int DEFAULT NULL', 'SELECT 1');
PREPARE audio_column_stmt FROM @audio_column_sql;
EXECUTE audio_column_stmt;
DEALLOCATE PREPARE audio_column_stmt;

SET @audio_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_generation_task' AND column_name='audio_mix_version');
SET @audio_column_sql = IF(@audio_column_exists=0, 'ALTER TABLE tk_generation_task ADD COLUMN audio_mix_version int NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE audio_column_stmt FROM @audio_column_sql;
EXECUTE audio_column_stmt;
DEALLOCATE PREPARE audio_column_stmt;
