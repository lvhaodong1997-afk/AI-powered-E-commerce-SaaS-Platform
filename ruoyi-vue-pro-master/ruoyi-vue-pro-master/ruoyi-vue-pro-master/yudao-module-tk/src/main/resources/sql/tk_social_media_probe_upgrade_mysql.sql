-- Additive and idempotent. Run BEFORE deploying the probe backend.
-- No DML: historical metadata remains UNVERIFIED. Existing publications are not rewritten.

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='upload_id')=0,
  'ALTER TABLE tk_social_media ADD COLUMN upload_id varchar(80) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='metadata_status')=0,
  'ALTER TABLE tk_social_media ADD COLUMN metadata_status varchar(32) NOT NULL DEFAULT ''UNVERIFIED''', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='metadata_source')=0,
  'ALTER TABLE tk_social_media ADD COLUMN metadata_source varchar(32) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='metadata_error')=0,
  'ALTER TABLE tk_social_media ADD COLUMN metadata_error varchar(512) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='video_codec')=0,
  'ALTER TABLE tk_social_media ADD COLUMN video_codec varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='audio_codec')=0,
  'ALTER TABLE tk_social_media ADD COLUMN audio_codec varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='video_bitrate')=0,
  'ALTER TABLE tk_social_media ADD COLUMN video_bitrate bigint DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='audio_bitrate')=0,
  'ALTER TABLE tk_social_media ADD COLUMN audio_bitrate bigint DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='audio_sample_rate')=0,
  'ALTER TABLE tk_social_media ADD COLUMN audio_sample_rate int DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='source_file_size')=0,
  'ALTER TABLE tk_social_media ADD COLUMN source_file_size bigint DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='source_etag')=0,
  'ALTER TABLE tk_social_media ADD COLUMN source_etag varchar(128) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='source_version_id')=0,
  'ALTER TABLE tk_social_media ADD COLUMN source_version_id varchar(256) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='publish_object_key')=0,
  'ALTER TABLE tk_social_media ADD COLUMN publish_object_key varchar(1024) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='publish_etag')=0,
  'ALTER TABLE tk_social_media ADD COLUMN publish_etag varchar(128) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='publish_version_id')=0,
  'ALTER TABLE tk_social_media ADD COLUMN publish_version_id varchar(256) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='normalized')=0,
  'ALTER TABLE tk_social_media ADD COLUMN normalized bit(1) NOT NULL DEFAULT b''0''', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='inspection_attempts')=0,
  'ALTER TABLE tk_social_media ADD COLUMN inspection_attempts int NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='inspection_lease_token')=0,
  'ALTER TABLE tk_social_media ADD COLUMN inspection_lease_token varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='inspection_lease_until')=0,
  'ALTER TABLE tk_social_media ADD COLUMN inspection_lease_until datetime DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='inspection_next_retry')=0,
  'ALTER TABLE tk_social_media ADD COLUMN inspection_next_retry datetime DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND column_name='inspected_at')=0,
  'ALTER TABLE tk_social_media ADD COLUMN inspected_at datetime DEFAULT NULL', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND index_name='uk_social_media_upload')=0,
  'ALTER TABLE tk_social_media ADD UNIQUE KEY uk_social_media_upload (tenant_id, upload_id)', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

SET @probe_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tk_social_media' AND index_name='idx_social_media_probe')=0,
  'ALTER TABLE tk_social_media ADD KEY idx_social_media_probe (status, metadata_status, inspection_next_retry, inspection_lease_until)', 'SELECT 1');
PREPARE probe_stmt FROM @probe_ddl;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;
