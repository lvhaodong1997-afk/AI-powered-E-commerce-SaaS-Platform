-- Additive migration. Back up before applying. Existing credits are adopted lazily under account locks.
CREATE TABLE IF NOT EXISTS tk_credit_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 source_key VARCHAR(80) NOT NULL,
 initial_credits BIGINT NOT NULL,
 available BIGINT NOT NULL DEFAULT 0,
 frozen BIGINT NOT NULL DEFAULT 0,
 used BIGINT NOT NULL DEFAULT 0,
 expired BIGINT NOT NULL DEFAULT 0,
 refund_reserved BIGINT NOT NULL DEFAULT 0,
 refunded BIGINT NOT NULL DEFAULT 0,
 expires_at DATETIME(3) NULL,
 UNIQUE KEY uk_credit_batch_source(source_key),
 KEY idx_credit_batch_tenant_expiry(tenant_id,expires_at),
 KEY idx_credit_batch_expiry(expires_at,available),
 CONSTRAINT ck_credit_batch_nonnegative CHECK(available>=0 AND frozen>=0 AND used>=0 AND expired>=0 AND refund_reserved>=0 AND refunded>=0),
 CONSTRAINT ck_credit_batch_balance CHECK(initial_credits=available+frozen+used+expired+refund_reserved+refunded)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tk_credit_batch_allocation (
 log_id BIGINT NOT NULL,
 batch_id BIGINT NOT NULL,
 credits BIGINT NOT NULL,
 status VARCHAR(32) NOT NULL,
 PRIMARY KEY(log_id,batch_id),
 KEY idx_credit_allocation_batch(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tk_payment_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(36) NOT NULL,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL,
 request_json TEXT NOT NULL,
 quote_json MEDIUMTEXT NULL,
 snapshot_json MEDIUMTEXT NULL,
 payment_id VARCHAR(255) NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'created',
 credit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 version BIGINT NOT NULL DEFAULT -1,
 paid_at DATETIME(3) NULL,
 next_check_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 lease_until DATETIME(3) NULL,
 check_failures INT NOT NULL DEFAULT 0,
 force_check BIT NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_payment_order_no(order_no),
 UNIQUE KEY uk_payment_order_idempotency(tenant_id,idempotency_key),
 UNIQUE KEY uk_payment_provider_id(payment_id),
 KEY idx_payment_order_reconcile(next_check_at,lease_until,status),
 KEY idx_payment_order_tenant(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tk_payment_event (
 nonce CHAR(32) NOT NULL PRIMARY KEY,
 expires_at DATETIME(3) NOT NULL,
 KEY idx_payment_event_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Preflight: this query MUST return no rows. Do not auto-repair balance mismatches.
SELECT a.tenant_id,a.frozen_credits,COALESCE(SUM(l.credits),0) AS logged_frozen
FROM tk_tenant_credit_account a LEFT JOIN tk_credit_log l
 ON l.tenant_id=a.tenant_id AND l.deleted=0 AND l.status='IN_PROGRESS'
WHERE a.deleted=0 GROUP BY a.tenant_id,a.frozen_credits
HAVING a.frozen_credits<>COALESCE(SUM(l.credits),0);
