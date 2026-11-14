package cn.iocoder.yudao.module.tk.service.credit;

import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditSaveReqVO;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantServiceImpl;
import cn.iocoder.yudao.module.system.service.tenant.TenantPackageService;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SESSION-cached MyBatis mappers and JDBC share Spring's connection/transaction. */
class TkCreditPersistenceIntegrationTest {
    JdbcTemplate db; TransactionTemplate tx; TkCreditBatchService ledger; TkCreditServiceImpl service;
    TkTenantCreditAccountMapper accounts; TkCreditLogMapper logs; TenantMapper tenants; DataSourceTransactionManager manager;
    Instant now=Instant.parse("2026-11-14T00:00:00Z");
    @BeforeEach void setup() throws Exception {
        String jdbc=System.getenv("TK_CREDIT_TEST_JDBC_URL");
        DriverManagerDataSource ds=jdbc==null?new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa",""):new DriverManagerDataSource(jdbc,System.getenv("TK_CREDIT_TEST_JDBC_USER"),System.getenv("TK_CREDIT_TEST_JDBC_PASSWORD"));
        db=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        if(jdbc!=null)tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        if(jdbc!=null){
            // Opt-in disposable fixture database only; never pass a business database here.
            if(!jdbc.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/tkledger_[a-zA-Z0-9_]+(?:\\?.*)?") || !"true".equals(System.getenv("TK_CREDIT_TEST_DISPOSABLE")))throw new IllegalArgumentException("Dedicated disposable tkledger_ fixture required");
            for(String table:List.of("tk_credit_batch_allocation","tk_credit_batch","tk_credit_log","tk_tenant_credit_account","system_tenant"))db.execute("DROP TABLE IF EXISTS "+table);
        }
        db.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,name VARCHAR(255),contact_user_id BIGINT,contact_name VARCHAR(255),contact_mobile VARCHAR(32),status INT,websites VARCHAR(255),package_id BIGINT DEFAULT 111,expire_time TIMESTAMP,member_limit INT,account_count INT,creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_tenant_credit_account(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT UNIQUE,total_credits BIGINT,remaining_credits BIGINT,frozen_credits BIGINT,warning_threshold BIGINT DEFAULT 100,creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_credit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,biz_type VARCHAR(64),biz_id BIGINT,action VARCHAR(32),credits BIGINT,status VARCHAR(32),before_remaining_credits BIGINT,after_remaining_credits BIGINT,before_frozen_credits BIGINT,after_frozen_credits BIGINT,remark VARCHAR(2000),creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BIT DEFAULT 0)");
        db.execute("CREATE TABLE tk_credit_batch(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,source_key VARCHAR(80) UNIQUE,initial_credits BIGINT,available BIGINT,frozen BIGINT,used BIGINT,expired BIGINT,refund_reserved BIGINT,refunded BIGINT,expires_at TIMESTAMP NULL)");
        db.execute("CREATE TABLE tk_credit_batch_allocation(log_id BIGINT,batch_id BIGINT,credits BIGINT,status VARCHAR(32),PRIMARY KEY(log_id,batch_id))");
        db.update("INSERT INTO system_tenant(id,account_count,name) VALUES(9,0,'Before')");
        db.update("INSERT INTO tk_tenant_credit_account(tenant_id,total_credits,remaining_credits,frozen_credits) VALUES(9,0,0,0)");
        MybatisConfiguration configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.SESSION);configuration.addMapper(TkTenantCreditAccountMapper.class);configuration.addMapper(TkCreditLogMapper.class);configuration.addMapper(TenantMapper.class);
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(configuration);
        SqlSessionTemplate session=new SqlSessionTemplate(factory.getObject());
        accounts=session.getMapper(TkTenantCreditAccountMapper.class);logs=session.getMapper(TkCreditLogMapper.class);tenants=session.getMapper(TenantMapper.class);
        ledger=new TkCreditBatchService(db,Clock.fixed(now,ZoneOffset.UTC));service=new TkCreditServiceImpl();
        ReflectionTestUtils.setField(service,"creditAccountMapper",accounts);ReflectionTestUtils.setField(service,"creditLogMapper",logs);
        ReflectionTestUtils.setField(service,"batchService",ledger);ReflectionTestUtils.setField(service,"tenantMapper",tenants);
        TenantService tenantService=mock(TenantService.class);when(tenantService.getTenant(9L)).thenAnswer(i->new TenantDO().setId(9L).setAccountCount((int)number("SELECT account_count FROM system_tenant WHERE id=9")));
        ReflectionTestUtils.setField(service,"tenantService",tenantService);ReflectionTestUtils.setField(service,"businessLogService",mock(TkBusinessLogService.class));
        tx.execute(s->{ledger.prepare(9L);return null;});
    }
    void grant(long amount,Instant paid) {tx.execute(s->{ledger.grantPayment(9L,"TKP_"+UUID.randomUUID(),amount,paid);return null;});}
    long number(String sql){return db.queryForObject(sql,Long.class);}
    @Test void expiryReturnsFreshBalanceAndAdminSaveCannotResurrectExpiredCredits(){
        grant(250,now.minus(190,java.time.temporal.ChronoUnit.DAYS));
        // Reconstitute the just-expired batch to reproduce first access after expiry.
        db.update("UPDATE tk_credit_batch SET available=250,expired=0 WHERE initial_credits=250");
        db.update("UPDATE tk_tenant_credit_account SET remaining_credits=250 WHERE tenant_id=9");
        tx.execute(s->{
            assertEquals(0L,service.getTenantBalance(9L).getRemainingCredits());
            TkTenantCreditSaveReqVO req=new TkTenantCreditSaveReqVO();req.setTenantId(9L);req.setTotalCredits(250L);req.setWarningThreshold(15L);service.saveTenantCredit(req);return null;
        });
        assertEquals(0,number("SELECT remaining_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals(250,number("SELECT SUM(expired) FROM tk_credit_batch WHERE tenant_id=9"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void duplicateCompletionsCannotConsumeAnotherFrozenTask(boolean settle) throws Exception {
        grant(100,now);Long first=tx.execute(s->service.freeze(9L,"GENERATION_TASK",10));
        Long second=tx.execute(s->service.freeze(9L,"GENERATION_TASK",20));
        CyclicBarrier readBoth=new CyclicBarrier(2);TkCreditLogMapper real=logs;
        TkCreditLogMapper coordinated=(TkCreditLogMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{TkCreditLogMapper.class},(p,m,a)->{
            Object result;try{result=m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}
            if(m.getName().equals("selectInProgressById") && result!=null && ((Long)a[0]).equals(first) && reads.getAndIncrement()<2)readBoth.await(5,TimeUnit.SECONDS);
            return result;
        });
        ReflectionTestUtils.setField(service,"creditLogMapper",coordinated);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Void> complete=()->{tx.execute(s->{if(settle)service.settleByLogId(first);else service.refundByLogId(first,"failed");return null;});return null;};
            Future<Void>a=pool.submit(complete),b=pool.submit(complete);a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(20,number("SELECT frozen_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals(20,number("SELECT SUM(frozen) FROM tk_credit_batch WHERE tenant_id=9"));
        assertEquals("IN_PROGRESS",db.queryForObject("SELECT status FROM tk_credit_log WHERE id=?",String.class,second));
    }
    java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
    @Test void zeroCostTaskCanSettleAndRefundExactlyOnce(){
        Long a=tx.execute(s->service.freeze(9L,"GENERATION_TASK",0));Long b=tx.execute(s->service.freeze(9L,"GENERATION_TASK",0));
        tx.execute(s->{service.settleByLogId(a);service.settleByLogId(a);service.refundByLogId(b,"failed");service.refundByLogId(b,"again");return null;});
        assertEquals("SETTLED",db.queryForObject("SELECT status FROM tk_credit_log WHERE id=?",String.class,a));
        assertEquals("REFUNDED",db.queryForObject("SELECT status FROM tk_credit_log WHERE id=?",String.class,b));
        assertEquals(0,number("SELECT frozen_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
    }
    TenantService tenantEntry(TenantMapper mapper)throws Exception{
        TenantServiceImpl target=new TenantServiceImpl();ReflectionTestUtils.setField(target,"tenantMapper",mapper);
        TenantPackageService packages=mock(TenantPackageService.class);when(packages.validTenantPackage(111L)).thenReturn(new TenantPackageDO().setId(111L));ReflectionTestUtils.setField(target,"tenantPackageService",packages);
        try{
            Object updater=Class.forName("cn.iocoder.yudao.module.tk.service.credit.TkTenantCreditQuotaUpdater").getConstructor(TkCreditServiceImpl.class).newInstance(service);
            ReflectionTestUtils.setField(target,"creditQuotaUpdater",updater);
        }catch(ClassNotFoundException baselineWithoutAdapter){ /* RED exercises the original entry point. */ }
        ProxyFactory proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (TenantService)proxy.getProxy();
    }
    TenantSaveReqVO quota(int total){TenantSaveReqVO r=new TenantSaveReqVO();r.setId(9L);r.setName("After");r.setPackageId(111L);r.setAccountCount(total);return r;}
    @Test void actualTenantUpdateRejectsImpossibleReductionBeforeAnyCommit()throws Exception{
        grant(250,now);Long work=tx.execute(s->service.freeze(9L,"GENERATION_TASK",100));tx.execute(s->{service.settleByLogId(work);return null;});
        TenantService entry=tenantEntry(tenants);
        assertThrows(IllegalStateException.class,()->entry.updateTenant(quota(0)));
        assertEquals(250,number("SELECT account_count FROM system_tenant WHERE id=9"));
        assertEquals(250,number("SELECT total_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals("Before",db.queryForObject("SELECT name FROM system_tenant WHERE id=9",String.class));
        assertEquals(150,tx.execute(s->service.getTenantBalance(9L)).getRemainingCredits());
    }
    @Test void actualTenantUpdateHoldsAccountLockUntilTenantWriteCommits()throws Exception{
        grant(250,now);CountDownLatch beforeWrite=new CountDownLatch(1),allowWrite=new CountDownLatch(1);
        TenantMapper coordinated=(TenantMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{TenantMapper.class},(p,m,a)->{
            if(m.getName().equals("updateById")){beforeWrite.countDown();if(!allowWrite.await(10,TimeUnit.SECONDS))throw new IllegalStateException("write barrier timed out");}
            try{return m.invoke(tenants,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        TenantService entry=tenantEntry(coordinated);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> update=pool.submit(()->entry.updateTenant(quota(300)));assertTrue(beforeWrite.await(5,TimeUnit.SECONDS));
            Future<?> payment=pool.submit(()->grant(250,now));
            try{assertThrows(TimeoutException.class,()->payment.get(200,TimeUnit.MILLISECONDS));}finally{allowWrite.countDown();}
            update.get(15,TimeUnit.SECONDS);payment.get(15,TimeUnit.SECONDS);
            assertEquals(550,number("SELECT account_count FROM system_tenant WHERE id=9"));
            assertEquals(550,number("SELECT total_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
            assertEquals(550,number("SELECT SUM(available) FROM tk_credit_batch WHERE tenant_id=9"));
        }finally{allowWrite.countDown();pool.shutdownNow();}
    }

    @Test void outerTenantWriteFailureRollsBackQuotaLedgerAndTenantMirror()throws Exception{
        grant(250,now);
        TenantMapper broken=(TenantMapper)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{TenantMapper.class},(p,m,a)->{
            if(m.getName().equals("updateById"))throw new IllegalStateException("outer tenant write failed");
            try{return m.invoke(tenants,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        TenantService entry=tenantEntry(broken);
        assertThrows(IllegalStateException.class,()->entry.updateTenant(quota(300)));
        assertEquals(250,number("SELECT account_count FROM system_tenant WHERE id=9"));
        assertEquals(250,number("SELECT total_credits FROM tk_tenant_credit_account WHERE tenant_id=9"));
        assertEquals(250,number("SELECT SUM(available) FROM tk_credit_batch WHERE tenant_id=9"));
        assertEquals(0,number("SELECT COUNT(*) FROM tk_credit_batch WHERE source_key LIKE 'ADMIN:%'"));
    }
    @Test void initiallyMissingZeroCreditAccountStillInitializes(){
        db.update("DELETE FROM tk_credit_batch");db.update("DELETE FROM tk_tenant_credit_account");
        assertEquals(0L,tx.execute(s->service.getTenantBalance(9L)).getRemainingCredits());
        assertEquals(0,number("SELECT SUM(initial_credits) FROM tk_credit_batch WHERE tenant_id=9"));
    }
}
