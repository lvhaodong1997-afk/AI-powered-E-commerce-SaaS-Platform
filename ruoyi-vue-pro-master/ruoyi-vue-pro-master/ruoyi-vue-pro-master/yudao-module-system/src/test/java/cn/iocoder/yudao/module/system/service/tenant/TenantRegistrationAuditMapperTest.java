package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TenantRegistrationAuditMapperTest {
    JdbcTemplate jdbc;
    SqlSessionTemplate mappers;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:audit"
                + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        mappers = TenantRegistrationMapperTestSupport.createMappers(dataSource);
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void anonymousReservationInsertAndCompletionUpdateUseDatabaseAuditDefault() {
        TenantRegistrationMapper mapper = mappers.getMapper(TenantRegistrationMapper.class);
        TenantRegistrationDO row = new TenantRegistrationDO().setRequestId("request").setMobile("13800138000").setPayloadHash("hash");
        assertEquals(1, mapper.insert(row));
        assertAudit("system_tenant_registration", "", "");
        row.setTenantId(20L).setUserId(21L);
        assertEquals(1, mapper.updateById(row));
        assertEquals(21L, mapper.selectById(row.getId()).getUserId());
        assertAudit("system_tenant_registration", "", "");
        assertNull(SecurityFrameworkUtils.getLoginUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void anonymousTenantInsertUsesDatabaseAuditDefault() {
        TenantMapper mapper = mappers.getMapper(TenantMapper.class);
        TenantDO row = tenant();
        assertEquals(1, mapper.insert(row));
        assertAudit("system_tenant", "", "");
        // TenantServiceImpl sets contactUserId using a fresh partial object; production updater is nullable.
        assertEquals(1, mapper.updateById(new TenantDO().setId(row.getId()).setContactUserId(21L)));
        assertEquals(21L, mapper.selectById(row.getId()).getContactUserId());
        assertEquals("", jdbc.queryForObject("SELECT creator FROM system_tenant", String.class));
        assertNull(jdbc.queryForObject("SELECT updater FROM system_tenant", String.class));
    }

    @Test void anonymousInviteInsertUsesDatabaseAuditDefault() {
        assertEquals(1, mappers.getMapper(TenantInviteMapper.class).insert(invite()));
        assertAudit("system_tenant_invite", "", "");
    }

    @Test void anonymousReferralInsertUsesDatabaseAuditDefault() {
        assertEquals(1, mappers.getMapper(TenantReferralMapper.class).insert(referral()));
        assertAudit("system_tenant_referral", "", "");
    }

    @Test void authenticatedInsertsKeepActualActorId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(42L), null));
        insertAll(row -> {});
        for (String table : tables()) assertAudit(table, "42", "42");
    }

    @Test void anonymousInsertsPreserveExplicitAuditValues() {
        insertAll(row -> { row.setCreator("import-source"); row.setUpdater("operator"); });
        for (String table : tables()) assertAudit(table, "import-source", "operator");
    }

    @Test void authenticatedInsertsPreserveExplicitAuditValues() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(42L), null));
        insertAll(row -> { row.setCreator("import-source"); row.setUpdater("operator"); });
        for (String table : tables()) assertAudit(table, "import-source", "operator");
    }

    @Test void anonymousInsertsFillMissingUpdaterWithoutReplacingExplicitCreator() {
        insertAll(row -> row.setCreator("import-source"));
        for (String table : tables()) assertAudit(table, "import-source", "");
    }

    @Test void authenticatedInsertsFillMissingCreatorWithoutReplacingExplicitUpdater() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(42L), null));
        insertAll(row -> row.setUpdater("operator"));
        for (String table : tables()) assertAudit(table, "42", "operator");
    }

    void insertAll(Consumer<BaseDO> audit) {
        TenantRegistrationDO registration = new TenantRegistrationDO().setRequestId("request").setMobile("13800138000").setPayloadHash("hash");
        TenantDO tenant = tenant(); TenantInviteDO invite = invite(); TenantReferralDO referral = referral();
        audit.accept(registration); audit.accept(tenant); audit.accept(invite); audit.accept(referral);
        mappers.getMapper(TenantRegistrationMapper.class).insert(registration);
        mappers.getMapper(TenantMapper.class).insert(tenant);
        mappers.getMapper(TenantInviteMapper.class).insert(invite);
        mappers.getMapper(TenantReferralMapper.class).insert(referral);
    }

    TenantDO tenant() {
        return new TenantDO().setName("Workspace").setContactName("Owner").setStatus(0).setPackageId(111L)
                .setExpireTime(LocalDateTime.now().plusYears(10)).setAccountCount(0).setMemberLimit(1);
    }
    TenantInviteDO invite() { return new TenantInviteDO().setTenantId(20L).setInviteCode("ABCDEFGHIJ"); }
    TenantReferralDO referral() { return new TenantReferralDO().setInviterTenantId(10L).setInvitedTenantId(20L); }
    String[] tables() { return new String[]{"system_tenant_registration", "system_tenant", "system_tenant_invite", "system_tenant_referral"}; }
    void assertAudit(String table, String creator, String updater) {
        assertEquals(creator, jdbc.queryForObject("SELECT creator FROM " + table, String.class));
        assertEquals(updater, jdbc.queryForObject("SELECT updater FROM " + table, String.class));
        assertNotNull(jdbc.queryForObject("SELECT create_time FROM " + table, LocalDateTime.class));
        assertNotNull(jdbc.queryForObject("SELECT update_time FROM " + table, LocalDateTime.class));
    }
}
