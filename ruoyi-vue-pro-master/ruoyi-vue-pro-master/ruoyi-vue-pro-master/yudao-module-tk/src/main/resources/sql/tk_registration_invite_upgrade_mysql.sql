-- Run before enabling TK_REGISTRATION_ENABLED. MySQL 5.7+/8; rerunnable.
-- Credits stay in account_count. New member_limit preserves historic caps and actual members.
SET @tk_has_member_limit = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_tenant' AND COLUMN_NAME = 'member_limit');
SET @tk_member_ddl = IF(@tk_has_member_limit = 0,
  'ALTER TABLE system_tenant ADD COLUMN member_limit INT NULL COMMENT ''Member seats independent of credits''', 'SELECT 1');
PREPARE tk_member_stmt FROM @tk_member_ddl;
EXECUTE tk_member_stmt;
DEALLOCATE PREPARE tk_member_stmt;

UPDATE system_tenant t
LEFT JOIN (SELECT tenant_id, COUNT(*) member_count FROM system_users WHERE deleted = b'0' GROUP BY tenant_id) u
  ON u.tenant_id = t.id
SET t.member_limit = GREATEST(1, COALESCE(t.account_count, 0), COALESCE(u.member_count, 0))
WHERE t.member_limit IS NULL;
ALTER TABLE system_tenant MODIFY COLUMN member_limit INT NOT NULL DEFAULT 1 COMMENT 'Member seats independent of credits';

CREATE TABLE IF NOT EXISTS system_tenant_registration (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_id BIGINT NULL,
  user_id BIGINT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_registration_request (request_id),
  UNIQUE KEY uk_tenant_registration_phone (mobile), UNIQUE KEY uk_tenant_registration_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TK self-service registration; independent from SMS provider users';

CREATE TABLE IF NOT EXISTS system_tenant_invite (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  invite_code CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_invite_tenant (tenant_id), UNIQUE KEY uk_tenant_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One opaque referral code per business tenant';

CREATE TABLE IF NOT EXISTS system_tenant_referral (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inviter_tenant_id BIGINT NOT NULL,
  invited_tenant_id BIGINT NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_referral_invited (invited_tenant_id),
  KEY idx_tenant_referral_inviter (inviter_tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Referral attribution only; grants no team access';

-- Idempotent business tenant backfill; platform/system/expired/disabled tenants are excluded.
-- RANDOM_BYTES does not expose predictable tenant identifiers. Rare unique collisions are retried on rerun/lazy generation.
INSERT IGNORE INTO system_tenant_invite (tenant_id, invite_code)
SELECT t.id, UPPER(HEX(RANDOM_BYTES(5))) FROM system_tenant t
LEFT JOIN system_tenant_invite i ON i.tenant_id = t.id
WHERE i.id IS NULL AND t.deleted = b'0' AND t.status = 0 AND t.id > 0
  AND t.package_id > 0 AND t.expire_time > NOW();

-- New registration must always use verified /system/auth/tenant-register. Disable legacy anonymous signup.
UPDATE infra_config SET `value` = 'false' WHERE config_key = 'system.user.register-enabled' AND deleted = b'0';
