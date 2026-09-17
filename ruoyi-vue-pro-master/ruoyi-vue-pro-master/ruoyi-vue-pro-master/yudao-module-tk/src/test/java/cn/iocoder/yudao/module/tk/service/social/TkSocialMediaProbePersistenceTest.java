package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StreamUtils;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real SQL CAS/unique-key tests; never connects to the application's database or OSS. */
class TkSocialMediaProbePersistenceTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path probeTemp;
    private TkSocialMediaMapper mapper;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    @BeforeEach void setup() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:probe_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        String schema;
        try (InputStream input = new ClassPathResource("sql/tk_social_publish_center_mysql.sql").getInputStream()) {
            schema = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS tk_social_media (");
        schema = schema.substring(start, schema.indexOf(";", start)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "")
                .replace("bit(1)", "tinyint").replace("b'0'", "0");
        jdbc.execute(schema);
        MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        GlobalConfig global = new GlobalConfig(); global.setBanner(false);
        GlobalConfig.DbConfig db = new GlobalConfig.DbConfig(); db.setIdType(IdType.AUTO); db.setLogicDeleteValue("1"); db.setLogicNotDeleteValue("0");
        global.setDbConfig(db);
        MybatisSqlSessionFactoryBean builder = new MybatisSqlSessionFactoryBean();
        builder.setDataSource(dataSource); builder.setConfiguration(config); builder.setGlobalConfig(global);
        org.apache.ibatis.session.SqlSessionFactory factory = builder.getObject();
        config.addMapper(TkSocialMediaMapper.class);
        mapper = new SqlSessionTemplate(factory).getMapper(TkSocialMediaMapper.class);
        jdbc.update("INSERT INTO tk_social_media(id,tenant_id,company_id,creator,media_type,file_name,content_type,file_size,public_url,status,metadata_status,upload_id) "
                + "VALUES (1,100,200,'7','VIDEO','a.mp4','video/mp4',1024,'https://assets.example/a.mp4','PROCESSING','PENDING','upload1')");
    }
    @AfterEach void shutdown() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
    }
    @Test void claimsAreScopedExclusiveAndExpiredLeaseIsRecoverable() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(0, mapper.claimInspection(1L, 999L, 200L, "wrong", now, now.plusMinutes(20), 3));
        assertEquals(0, mapper.claimInspection(1L, 100L, 999L, "wrong", now, now.plusMinutes(20), 3));
        assertEquals(1, mapper.claimInspection(1L, 100L, 200L, "first", now, now.plusMinutes(20), 3));
        assertEquals(0, mapper.claimInspection(1L, 100L, 200L, "second", now, now.plusMinutes(20), 3));
        jdbc.update("UPDATE tk_social_media SET inspection_lease_until=? WHERE id=1", now.minusSeconds(1));
        assertEquals(1, mapper.claimInspection(1L, 100L, 200L, "second", now, now.plusMinutes(20), 3));
        assertEquals(2, mapper.selectById(1L).getInspectionAttempts());
        assertEquals("second", mapper.selectById(1L).getInspectionLeaseToken());
    }
    @Test void expiredWorkerCannotCommitWithoutAnotherWorkerReclaiming() {
        TkSocialMediaService service = mock(TkSocialMediaService.class);
        doAnswer(call -> {
            TkSocialMediaDO row = call.getArgument(0);
            row.setPublishObjectKey("tk/100/200/social-media-publish/attempt-new.mp4");
            jdbc.update("UPDATE tk_social_media SET inspection_lease_until=? WHERE id=1", LocalDateTime.now().minusSeconds(1));
            return null;
        }).when(service).inspectOwnedSource(any());
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) { job.process(mapper.selectById(1L)); }
        assertEquals("PROCESSING", mapper.selectById(1L).getStatus());
        assertEquals("INSPECTING", mapper.selectById(1L).getMetadataStatus());
        verify(service).deletePublishCopy(argThat(m -> "tk/100/200/social-media-publish/attempt-new.mp4".equals(m.getPublishObjectKey())));
    }
    @Test void lastAttemptCrashBecomesFailedAndDoesNotDownloadAgain() {
        jdbc.update("UPDATE tk_social_media SET metadata_status='INSPECTING',inspection_attempts=3,inspection_lease_token='old',inspection_lease_until=? WHERE id=1",
                LocalDateTime.now().minusMinutes(1));
        TkSocialMediaService service = mock(TkSocialMediaService.class);
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) { job.process(mapper.selectById(1L)); }
        assertEquals("FAILED", mapper.selectById(1L).getStatus());
        assertNull(mapper.selectById(1L).getInspectionLeaseToken()); verifyNoInteractions(service);
    }
    @Test void transientFailureSchedulesBoundedRetryWithoutLeakingSdkMessage() {
        TkSocialMediaService service = mock(TkSocialMediaService.class);
        doThrow(new IllegalStateException("https://secret.example/?Signature=NEVER_EXPOSE")).when(service).inspectOwnedSource(any());
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) { job.process(mapper.selectById(1L)); }
        TkSocialMediaDO result = mapper.selectById(1L);
        assertEquals("PROCESSING", result.getStatus()); assertEquals("PENDING", result.getMetadataStatus());
        assertEquals(1, result.getInspectionAttempts()); assertNotNull(result.getInspectionNextRetry());
        assertFalse(result.getMetadataError().contains("NEVER_EXPOSE")); assertNull(result.getInspectionLeaseToken());
    }
    @Test void prolongedSharedCapacityContentionDoesNotExhaustAttemptsAndLaterSucceeds() throws Exception {
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(inspector, "tempDirectory", probeTemp.toString());
        TkSocialMediaService service = mock(TkSocialMediaService.class);
        doAnswer(call -> {
            try (TkSocialVideoInspector.Workspace ignored = inspector.openWorkspace()) {
                TkSocialMediaDO row = call.getArgument(0);
                row.setWidth(1080); row.setHeight(1920); row.setFrameRate(25D); row.setDurationSeconds(10D);
                row.setVideoCodec("h264"); row.setInspectedAt(LocalDateTime.now());
            }
            return null;
        }).when(service).inspectOwnedSource(any());
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) {
            // A synchronous importer holds the real shared capacity across six retry windows.
            // Advance due timestamps directly, avoiding a multi-minute wall-clock sleep.
            try (TkSocialVideoInspector.Workspace heldByImport = inspector.openWorkspace()) {
                for (int cycle = 0; cycle < 6; cycle++) {
                    jdbc.update("UPDATE tk_social_media SET inspection_next_retry=? WHERE id=1", LocalDateTime.now().minusSeconds(1));
                    job.process(mapper.selectById(1L));
                    TkSocialMediaDO waiting = mapper.selectById(1L);
                    assertEquals("PROCESSING", waiting.getStatus()); assertEquals("PENDING", waiting.getMetadataStatus());
                    assertEquals(0, waiting.getInspectionAttempts(), "Capacity waits must not spend an inspection attempt");
                    assertNotNull(waiting.getInspectionNextRetry()); assertNull(waiting.getInspectionLeaseToken());
                }
            }
            jdbc.update("UPDATE tk_social_media SET inspection_next_retry=? WHERE id=1", LocalDateTime.now().minusSeconds(1));
            job.process(mapper.selectById(1L));
        }
        TkSocialMediaDO ready = mapper.selectById(1L);
        assertEquals("READY", ready.getStatus()); assertEquals("VERIFIED", ready.getMetadataStatus());
        assertEquals(1, ready.getInspectionAttempts()); assertEquals(25D, ready.getFrameRate());
        assertNull(ready.getMetadataError()); assertNull(ready.getInspectionNextRetry());
        verify(service, never()).deletePublishCopy(any());
    }
    @Test void sameUploadIdCannotInsertDuplicateMediaForSameTenant() {
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO tk_social_media(tenant_id,company_id,creator,media_type,file_name,content_type,file_size,public_url,status,upload_id) "
                        + "VALUES(100,200,'7','VIDEO','a.mp4','video/mp4',1024,'https://assets.example/a.mp4','PROCESSING','upload1')"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tk_social_media", Integer.class));
    }
}
