-- Apply after tk_tiktok_shop_publish_mysql.sql. Additive and repeat-safe; no role grants.
SET @tk_seller_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_auth_session' AND column_name='auth_type')=0, 'ALTER TABLE tk_tiktok_shop_auth_session ADD COLUMN auth_type varchar(16) NOT NULL DEFAULT ''CREATOR''', 'SELECT 1');
PREPARE tk_seller_stmt FROM @tk_seller_sql;
EXECUTE tk_seller_stmt;
DEALLOCATE PREPARE tk_seller_stmt;

SET @tk_seller_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_auth_session' AND column_name='app_key')=0, 'ALTER TABLE tk_tiktok_shop_auth_session ADD COLUMN app_key varchar(128) DEFAULT NULL', 'SELECT 1');
PREPARE tk_seller_stmt FROM @tk_seller_sql;
EXECUTE tk_seller_stmt;
DEALLOCATE PREPARE tk_seller_stmt;

SET @tk_seller_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_auth_session' AND column_name='environment')=0, 'ALTER TABLE tk_tiktok_shop_auth_session ADD COLUMN environment varchar(16) NOT NULL DEFAULT ''PRODUCTION''', 'SELECT 1');
PREPARE tk_seller_stmt FROM @tk_seller_sql;
EXECUTE tk_seller_stmt;
DEALLOCATE PREPARE tk_seller_stmt;

SET @tk_seller_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tk_tiktok_shop_auth_session' AND column_name='configuration_hash')=0, 'ALTER TABLE tk_tiktok_shop_auth_session ADD COLUMN configuration_hash varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE tk_seller_stmt FROM @tk_seller_sql;
EXECUTE tk_seller_stmt;
DEALLOCATE PREPARE tk_seller_stmt;

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_seller_account (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  environment varchar(16) NOT NULL DEFAULT 'PRODUCTION',
  app_key varchar(128) NOT NULL,
  open_id varchar(128) NOT NULL,
  display_name varchar(255) DEFAULT NULL,
  username varchar(255) DEFAULT NULL,
  market varchar(16) DEFAULT NULL,
  user_type int DEFAULT NULL,
  granted_scopes json DEFAULT NULL,
  access_token_cipher text,
  refresh_token_cipher text,
  access_token_expire_time datetime DEFAULT NULL,
  refresh_token_expire_time datetime DEFAULT NULL,
  auth_status varchar(32) NOT NULL,
  token_version bigint NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_seller_account (tenant_id, app_key, environment, open_id),
  KEY idx_seller_account_scope (tenant_id, company_id, auth_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok Shop seller account';

CREATE TABLE IF NOT EXISTS tk_tiktok_shop_seller_shop (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  company_id bigint NOT NULL,
  account_id bigint NOT NULL,
  shop_id varchar(128) NOT NULL,
  name varchar(255) NOT NULL,
  region varchar(16) NOT NULL,
  shop_cipher text,
  seller_type varchar(128) DEFAULT NULL,
  code varchar(128) DEFAULT NULL,
  status varchar(32) NOT NULL,
  environment varchar(16) NOT NULL DEFAULT 'PRODUCTION',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_seller_shop (tenant_id, account_id, shop_id),
  KEY idx_seller_shop_scope (tenant_id, company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='TikTok seller authorized shops';
