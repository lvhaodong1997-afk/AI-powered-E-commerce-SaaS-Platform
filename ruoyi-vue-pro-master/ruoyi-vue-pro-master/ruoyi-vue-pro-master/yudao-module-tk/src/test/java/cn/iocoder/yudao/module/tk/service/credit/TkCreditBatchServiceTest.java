package cn.iocoder.yudao.module.tk.service.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TkCreditBatchServiceTest {
    JdbcTemplate db;
    TransactionTemplate tx;
    TkCreditBatchService ledger;
    Instant now = Instant.parse("2026-01-31T10:00:00Z");

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        db = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,account_count INT,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_tenant_credit_account(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT UNIQUE,total_credits BIGINT,remaining_credits BIGINT,frozen_credits BIGINT,deleted BIT DEFAULT 0,update_time TIMESTAMP)");
        db.execute("CREATE TABLE tk_credit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,biz_type VARCHAR(64),biz_id BIGINT,action VARCHAR(32),credits BIGINT,status VARCHAR(32),before_remaining_credits BIGINT,after_remaining_credits BIGINT,before_frozen_credits BIGINT,after_frozen_credits BIGINT,remark VARCHAR(2000),creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_credit_batch(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,source_key VARCHAR(80) UNIQUE,initial_credits BIGINT,available BIGINT,frozen BIGINT,used BIGINT,expired BIGINT,refund_reserved BIGINT,refunded BIGINT,expires_at TIMESTAMP NULL)");
        db.execute("CREATE TABLE tk_credit_batch_allocation(log_id BIGINT,batch_id BIGINT,credits BIGINT,status VARCHAR(32),PRIMARY KEY(log_id,batch_id))");
        db.update("INSERT INTO system_tenant(id,account_count) VALUES(8,100),(9,0)");
        db.update("INSERT INTO tk_tenant_credit_account(tenant_id,total_credits,remaining_credits,frozen_credits) VALUES(8,100,90,10),(9,0,0,0)");
        db.update("INSERT INTO tk_credit_log(id,tenant_id,credits,status) VALUES(7,8,10,'IN_PROGRESS')");
        ledger = new TkCreditBatchService(db, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void legacyBalancesAndFrozenWorkArePreserved() {
        tx.execute(s -> {ledger.prepare(8L); assertEquals(10, ledger.complete(8L,7L,false)); return null;});
        assertEquals(100, number("SELECT available FROM tk_credit_batch WHERE tenant_id=8"));
        assertEquals(0, number("SELECT frozen FROM tk_credit_batch WHERE tenant_id=8"));
    }

    @Test void paidGrantIsUniqueAndSixCalendarMonthsStartAtPayment() {
        tx.execute(s -> {ledger.prepare(9L); assertTrue(ledger.grantPayment(9L,"TKP_a",250,now)); assertFalse(ledger.grantPayment(9L,"TKP_a",250,now)); return null;});
        assertEquals(250,number("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals(Instant.parse("2026-07-31T10:00:00Z"),db.queryForObject("SELECT expires_at FROM tk_credit_batch WHERE source_key='TKP_a'",java.sql.Timestamp.class).toInstant());
    }

    @Test void lateReleaseDoesNotResurrectExpiredPoints() {
        tx.execute(s -> {ledger.prepare(9L); ledger.grantPayment(9L,"TKP_a",250,now); ledger.allocate(9L,20L,20); db.update("UPDATE tk_tenant_credit_account SET remaining_credits=230,frozen_credits=20 WHERE tenant_id=9"); return null;});
        ledger = new TkCreditBatchService(db,Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"),ZoneOffset.UTC));
        tx.execute(s -> {ledger.prepare(9L); assertEquals(0,ledger.complete(9L,20L,false)); return null;});
        assertEquals(250,number("SELECT expired FROM tk_credit_batch WHERE source_key='TKP_a'"));
        assertEquals(0,number("SELECT available FROM tk_credit_batch WHERE source_key='TKP_a'"));
        assertEquals(0,number("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
    }

    @Test void spendBlocksRefundAndReservationBlocksSpend() {
        tx.execute(s -> {ledger.prepare(9L);ledger.grantPayment(9L,"TKP_a",250,now);ledger.reserveRefund(9L,"TKP_a");return null;});
        assertThrows(IllegalStateException.class,()->tx.execute(s->{ledger.allocate(9L,21L,1);return null;}));
        tx.execute(s -> {assertTrue(ledger.confirmRefund(9L,"TKP_a"));assertTrue(ledger.confirmRefund(9L,"TKP_a"));return null;});
        assertEquals(0,number("SELECT total_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals(250,number("SELECT refunded FROM tk_credit_batch WHERE source_key='TKP_a'"));
    }

    @Test void concurrentDuplicateGrantsCreditOnlyOnce() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> grant=()->tx.execute(s->{ledger.prepare(9L);return ledger.grantPayment(9L,"TKP_a",250,now);});
            Future<Boolean> a=pool.submit(grant),b=pool.submit(grant);
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(250,number("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        } finally {pool.shutdownNow();}
    }
    long number(String sql) {return db.queryForObject(sql,Long.class);}
}
