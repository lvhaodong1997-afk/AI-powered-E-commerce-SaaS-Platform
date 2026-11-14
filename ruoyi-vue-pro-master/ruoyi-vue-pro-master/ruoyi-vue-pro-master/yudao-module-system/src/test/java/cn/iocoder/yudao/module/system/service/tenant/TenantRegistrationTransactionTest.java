package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantRegistrationMapper;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.aop.DynamicLocalTransactionInterceptor;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real H2 unique indexes and actual DSTransactional interceptor, with JDBC adapters at service boundaries. */
class TenantRegistrationTransactionTest {
    DynamicRoutingDataSource dataSource;
    JdbcTemplate jdbc;
    TenantRegistrationProvisioner service;
    AtomicBoolean failAfterReferral = new AtomicBoolean();
    AtomicInteger remoteConsumes = new AtomicInteger();

    @BeforeEach void setup() {
        dataSource = new DynamicRoutingDataSource(Collections.emptyList());
        dataSource.addDataSource("master", new DriverManagerDataSource("jdbc:h2:mem:register" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", ""));
        dataSource.afterPropertiesSet();
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        TenantRegistrationMapper mapper = mock(TenantRegistrationMapper.class);
        when(mapper.selectByRequestId(anyString())).thenAnswer(i -> selectRegistration("request_id", i.getArgument(0)));
        when(mapper.selectByMobile(anyString())).thenAnswer(i -> selectRegistration("mobile", i.getArgument(0)));
        when(mapper.insert(any(TenantRegistrationDO.class))).thenAnswer(i -> {
            TenantRegistrationDO row = i.getArgument(0);
            jdbc.update("INSERT INTO system_tenant_registration(request_id,mobile,payload_hash) VALUES(?,?,?)", row.getRequestId(), row.getMobile(), row.getPayloadHash());
            row.setId(jdbc.queryForObject("SELECT id FROM system_tenant_registration WHERE request_id=?", Long.class, row.getRequestId())); return 1;
        });
        when(mapper.updateById(any(TenantRegistrationDO.class))).thenAnswer(i -> {
            TenantRegistrationDO row = i.getArgument(0);
            return jdbc.update("UPDATE system_tenant_registration SET tenant_id=?,user_id=? WHERE id=?",row.getTenantId(),row.getUserId(),row.getId());
        });
        TenantService tenants = mock(TenantService.class);
        when(tenants.createTenant(any())).thenAnswer(i -> {
            TenantSaveReqVO req=i.getArgument(0);
            jdbc.update("INSERT INTO system_tenant(name,contact_name,contact_mobile,status,package_id,expire_time,account_count,member_limit) VALUES(?,?,?,0,?,DATEADD('DAY',30,CURRENT_TIMESTAMP),0,1)",req.getName(),req.getContactName(),req.getContactMobile(),req.getPackageId());
            Long id=jdbc.queryForObject("SELECT id FROM system_tenant WHERE name=?",Long.class,req.getName());
            jdbc.update("INSERT INTO system_users(username,nickname,password,status,tenant_id) VALUES(?,?,?,0,?)",req.getUsername(),req.getContactName(),"encoded",id);
            Long uid=jdbc.queryForObject("SELECT id FROM system_users WHERE tenant_id=?",Long.class,id);
            jdbc.update("UPDATE system_tenant SET contact_user_id=? WHERE id=?",uid,id);
            return id;
        });
        when(tenants.getTenant(anyLong())).thenAnswer(i -> new TenantDO().setId(i.getArgument(0)).setContactUserId(jdbc.queryForObject("SELECT contact_user_id FROM system_tenant WHERE id=?",Long.class,(Object)i.getArgument(0))));
        TenantInviteService invites=mock(TenantInviteService.class);
        when(invites.validateInvite(any())).thenReturn(99L);
        when(invites.createCode(anyLong())).thenAnswer(i->{ jdbc.update("INSERT INTO system_tenant_invite(tenant_id,invite_code) VALUES(?,?)",i.getArgument(0),"ABCDEFGHIJ"); return "ABCDEFGHIJ"; });
        doAnswer(i->{ jdbc.update("INSERT INTO system_tenant_referral(inviter_tenant_id,invited_tenant_id) VALUES(?,?)",(Object)i.getArgument(0),(Object)i.getArgument(1));
            if(failAfterReferral.get()) { throw new IllegalStateException("simulated local failure after remote verification"); } return null;
        }).when(invites).bind(anyLong(),anyLong());
        PhoneVerificationClient phone=mock(PhoneVerificationClient.class);
        doAnswer(i->{remoteConsumes.incrementAndGet(); return null;}).when(phone).consume(any());
        TenantRegistrationProperties props=new TenantRegistrationProperties(); props.setDefaultPackageId(5L);props.setValidityDays(30);props.setPhoneSecret("12345678901234567890123456789012");
        TenantRegistrationProvisioner target=new TenantRegistrationProvisioner();
        ReflectionTestUtils.setField(target,"registrationMapper",mapper);ReflectionTestUtils.setField(target,"tenantService",tenants);
        ReflectionTestUtils.setField(target,"inviteService",invites);ReflectionTestUtils.setField(target,"phoneClient",phone);ReflectionTestUtils.setField(target,"properties",props);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new DynamicLocalTransactionInterceptor(true));
        service=(TenantRegistrationProvisioner)proxy.getProxy();
    }
    @AfterEach void close() { dataSource.destroy(); }

    TenantRegistrationDO selectRegistration(String field,String value) {
        List<TenantRegistrationDO> rows=jdbc.query("SELECT * FROM system_tenant_registration WHERE "+field+"=?",(rs,n)->new TenantRegistrationDO()
                .setId(rs.getLong("id")).setRequestId(rs.getString("request_id")).setMobile(rs.getString("mobile"))
                .setPayloadHash(rs.getString("payload_hash")).setTenantId((Long)rs.getObject("tenant_id")).setUserId((Long)rs.getObject("user_id")),value);
        return rows.isEmpty()?null:rows.get(0);
    }
    TenantRegistrationReqVO request(String id) {
        TenantRegistrationReqVO req=new TenantRegistrationReqVO();req.setRequestId(id);req.setTenantName("Business"+id);req.setUsername("owner"+id);
        req.setNickname("Owner");req.setPassword("Password1");req.setMobile("13800138000");req.setSmsRequestId("sms123");req.setSmsCode("123456");req.setInviteCode("ABCDEFGHIJ");return req;
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }

    @Test void localFailureRollsBackTenantOwnerOwnCodeReferralAndReservationThenSameRequestRecovers() {
        failAfterReferral.set(true);TenantRegistrationReqVO req=request("request1");
        assertThrows(RuntimeException.class,()->service.provision(req));
        for(String table:Arrays.asList("system_tenant_registration","system_tenant","system_users","system_tenant_invite","system_tenant_referral")) { assertEquals(0,count(table),table); }
        assertEquals(1,remoteConsumes.get());
        failAfterReferral.set(false);TenantRegistrationDO result=service.provision(req);assertNotNull(result.getUserId());
        assertEquals(2,remoteConsumes.get());assertEquals(1,count("system_tenant"));assertEquals(1,count("system_tenant_referral"));
        assertEquals(result.getUserId(),service.provision(req).getUserId());assertEquals(2,remoteConsumes.get());
    }
    @Test void concurrentPhoneRequestsCreateOneIndependentTenant() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> first=()->{start.await();try{service.provision(request("request1"));return true;}catch(RuntimeException conflict){return false;}};
        Callable<Boolean> second=()->{start.await();try{service.provision(request("request2"));return true;}catch(RuntimeException conflict){return false;}};
        try {Future<Boolean>a=pool.submit(first),b=pool.submit(second);start.countDown();assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));}
        finally {pool.shutdownNow();}
        assertEquals(1,count("system_tenant_registration"));assertEquals(1,count("system_tenant"));assertEquals(1,count("system_users"));assertEquals(1,remoteConsumes.get());
    }
    @Test void databaseRejectsRequestCodeAndReferralRebinding() {
        jdbc.update("INSERT INTO system_tenant_registration(request_id,mobile,payload_hash) VALUES('r','13800138000','hash')");
        assertThrows(DuplicateKeyException.class,()->jdbc.update("INSERT INTO system_tenant_registration(request_id,mobile,payload_hash) VALUES('r','13900139000','hash')"));
        assertThrows(DuplicateKeyException.class,()->jdbc.update("INSERT INTO system_tenant_registration(request_id,mobile,payload_hash) VALUES('r2','13800138000','hash')"));
        jdbc.update("INSERT INTO system_tenant_invite(tenant_id,invite_code) VALUES(1,'ABCDEFGHIJ')");
        assertThrows(DuplicateKeyException.class,()->jdbc.update("INSERT INTO system_tenant_invite(tenant_id,invite_code) VALUES(2,'ABCDEFGHIJ')"));
        jdbc.update("INSERT INTO system_tenant_referral(inviter_tenant_id,invited_tenant_id) VALUES(1,2)");
        assertThrows(DuplicateKeyException.class,()->jdbc.update("INSERT INTO system_tenant_referral(inviter_tenant_id,invited_tenant_id) VALUES(3,2)"));
    }
}
