package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.module.tk.service.credit.*;
import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkPaymentOrderStoreTest {
    JdbcTemplate db;TransactionTemplate tx;TkPaymentOrderStore store;ObjectMapper json=new ObjectMapper();ObjectNode purchase;String order;
    @BeforeEach void setup(){
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,account_count INT,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_tenant_credit_account(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT UNIQUE,total_credits BIGINT,remaining_credits BIGINT,frozen_credits BIGINT,deleted BIT DEFAULT 0,update_time TIMESTAMP)");
        db.execute("CREATE TABLE tk_credit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,biz_type VARCHAR(64),biz_id BIGINT,action VARCHAR(32),credits BIGINT,status VARCHAR(32),before_remaining_credits BIGINT,after_remaining_credits BIGINT,before_frozen_credits BIGINT,after_frozen_credits BIGINT,remark VARCHAR(2000),creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BIT DEFAULT 0)");
        new ResourceDatabasePopulator(new ClassPathResource("sql/tk_online_payment_upgrade_mysql.sql")).execute(ds);
        db.update("INSERT INTO system_tenant(id,account_count) VALUES(9,0)");
        db.update("INSERT INTO tk_tenant_credit_account(tenant_id,total_credits,remaining_credits,frozen_credits) VALUES(9,0,0,0)");
        TkCreditBatchService batches=new TkCreditBatchService(db);
        tx.execute(s->{batches.prepare(9L);return null;});
        TkPaymentProperties properties=new TkPaymentProperties();properties.setLiveMode(true);
        store=new TkPaymentOrderStore(db,json,batches,mock(TkCreditServiceImpl.class),properties);
        purchase=json.createObjectNode().put("packageCode","P1000").put("quantity",1).put("paymentMethod","alipay").put("version","2026-11-v1");
        order=tx.execute(s->store.create(9,10,"idempotency-key-0001",purchase)).path("orderNo").asText();
    }
    ObjectNode snapshot(String status,long version){return json.createObjectNode().put("orderNo",order).put("tenantId","9").put("userId","10").put("packageCode","P1000").put("quantity",1).put("paymentMethod","alipay").put("catalogVersion","2026-11-v1").put("livemode",true).put("baseAmountCents",100000).put("amountCents",100000).put("currency","CNY").put("points",250).putNull("fx").put("status",status).put("version",version).put("paymentId","pi_example").put("paidAt",Instant.now().minusSeconds(60).toString());}
    @Test void retriesKeepSameOrderAndRejectChangedPurchase(){
        assertEquals(order,tx.execute(s->store.create(9,10,"idempotency-key-0001",purchase)).path("orderNo").asText());
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->store.create(9,10,"idempotency-key-0001",purchase.deepCopy().put("packageCode","P5000"))));
        assertThrows(IllegalArgumentException.class,()->store.get(order,8L));
    }
    @Test void paymentCreditAndRefundAreAtomicIdempotentAndCannotRegress(){
        ObjectNode paid=snapshot("paid",2);
        tx.execute(s->store.apply(order,paid));tx.execute(s->store.apply(order,paid));
        assertEquals(250,db.queryForObject("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9",Long.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM tk_credit_log WHERE biz_type='ONLINE_PAYMENT'",Long.class));
        ObjectNode refunded=paid.deepCopy().put("status","refunded").put("version",3);
        tx.execute(s->store.apply(order,refunded));tx.execute(s->store.apply(order,paid));
        assertEquals("REFUNDED",store.get(order,9L).path("creditStatus").asText());
        assertEquals(0,db.queryForObject("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9",Long.class));
    }
    @Test void interruptedLocalTransactionCanReplayOriginalPaidSnapshot(){
        ObjectNode paid=snapshot("paid",2);
        tx.execute(s->{store.apply(order,paid);s.setRollbackOnly();return null;});
        assertEquals("created",store.get(order,9L).path("status").asText());
        assertEquals(0,db.queryForObject("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9",Long.class));
        tx.execute(s->store.apply(order,paid));
        assertEquals("CREDITED",store.get(order,9L).path("creditStatus").asText());
    }
    @Test void tamperedTenantAndAmountNeverCredit(){
        ObjectNode quoted=snapshot("quoted",0);
        tx.execute(s->store.apply(order,quoted));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->store.apply(order,quoted.deepCopy().put("tenantId","8").put("status","paid").put("version",2))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->store.apply(order,quoted.deepCopy().put("amountCents",1).put("status","paid").put("version",2))));
        assertEquals(0,db.queryForObject("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9",Long.class));
    }
    @Test void completedOrdersStopPollingButFailedRefundNotificationsAreRecovered(){
        tx.execute(s->store.apply(order,snapshot("paid",2)));
        db.update("UPDATE tk_payment_order SET next_check_at=CURRENT_TIMESTAMP");assertFalse(store.due().contains(order));
        store.failed(order);db.update("UPDATE tk_payment_order SET next_check_at=CURRENT_TIMESTAMP");assertTrue(store.due().contains(order));
    }
}
