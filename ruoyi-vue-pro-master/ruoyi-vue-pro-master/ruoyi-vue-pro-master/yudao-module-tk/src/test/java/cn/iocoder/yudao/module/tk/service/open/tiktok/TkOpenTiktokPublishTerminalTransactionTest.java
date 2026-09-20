package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real H2/MyBatis writes and Spring annotation-driven proxy; no mock transaction flags. */
class TkOpenTiktokPublishTerminalTransactionTest {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TkOpenTiktokPublishDetailMapper details;
    private TkOpenTiktokPublishTaskMapper tasks;
    private TkOpenTiktokPublishAttemptMapper attempts;
    private TkOpenApiEventMapper events;
    private FailingAfterEnqueue callbacks;
    private TkOpenTiktokPublishTerminalService service;

    @BeforeEach
    void setupRealDatabaseAndTransactionalProxy() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:tiktok_terminal_"
                + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        String migration;
        try (InputStream input = new ClassPathResource("sql/tk_tiktok_open_api_mysql.sql").getInputStream()) {
            migration = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        // Use existing production DDL for the participating tables, preserving unique keys/nullability.
        for (String name : new String[]{"tk_open_api_client", "tk_open_tiktok_publish_task",
                "tk_open_tiktok_publish_detail", "tk_open_api_event"}) {
            int start = migration.indexOf("CREATE TABLE IF NOT EXISTS `" + name + "`");
            assertTrue(start >= 0, name);
            String ddl = migration.substring(start, migration.indexOf(';', start) + 1)
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "")
                    .replace("bit(1)", "tinyint").replace("b'0'", "0").replace("b'1'", "1");
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
            }
        }
        // Task 1 owns the additive attempt migration. This isolated fixture covers its agreed columns.
        jdbc.execute("CREATE TABLE tk_open_tiktok_publish_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "detail_id VARCHAR(64) NOT NULL,task_id VARCHAR(64) NOT NULL,client_id VARCHAR(64) NOT NULL,"
                + "attempt_no INT NOT NULL,phase VARCHAR(64),publish_id VARCHAR(128),heartbeat_time TIMESTAMP,"
                + "terminal_source VARCHAR(64),terminal_time TIMESTAMP,last_error VARCHAR(1024),"
                + "next_reconcile_time TIMESTAMP,reconcile_count INT DEFAULT 0,upload_url_cipher VARCHAR(4096),"
                + "upload_source VARCHAR(64),started_time TIMESTAMP,owner_token VARCHAR(128),"
                + "file_sha256 VARCHAR(128),file_size BIGINT,"
                + "creator VARCHAR(64) DEFAULT '',updater VARCHAR(64) DEFAULT '',"
                + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "deleted TINYINT DEFAULT 0,UNIQUE(detail_id,attempt_no))");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        GlobalConfig global = new GlobalConfig();
        global.setBanner(false);
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.AUTO);
        dbConfig.setLogicDeleteValue("1");
        dbConfig.setLogicNotDeleteValue("0");
        global.setDbConfig(dbConfig);
        global.setMetaObjectHandler(new MetaObjectHandler() {
            @Override public void insertFill(MetaObject object) {
                strictInsertFill(object, "createTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(object, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
            @Override public void updateFill(MetaObject object) {
                strictUpdateFill(object, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        });
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(global);
        // Production interceptors parse and render SQL even when these open API tables have no rules.
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor plugins =
                new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(
                        new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor(
                (com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler)
                        (table, where, statementId) -> null));
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor());
        factory.setPlugins(plugins);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        org.apache.ibatis.session.SqlSessionFactory sessionFactory = factory.getObject();
        assertNotNull(sessionFactory);
        configuration.addMapper(TkOpenTiktokPublishDetailMapper.class);
        configuration.addMapper(TkOpenTiktokPublishTaskMapper.class);
        configuration.addMapper(TkOpenTiktokPublishAttemptMapper.class);
        configuration.addMapper(TkOpenApiEventMapper.class);
        configuration.addMapper(TkOpenApiClientMapper.class);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sessionFactory);
        details = sessions.getMapper(TkOpenTiktokPublishDetailMapper.class);
        tasks = sessions.getMapper(TkOpenTiktokPublishTaskMapper.class);
        attempts = sessions.getMapper(TkOpenTiktokPublishAttemptMapper.class);
        events = sessions.getMapper(TkOpenApiEventMapper.class);
        callbacks = new FailingAfterEnqueue(events, sessions.getMapper(TkOpenApiClientMapper.class));
        ProxyFactory proxy = new ProxyFactory(new TkOpenTiktokPublishTerminalService(details, tasks, callbacks, attempts));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        service = (TkOpenTiktokPublishTerminalService) proxy.getProxy();
        assertTrue(AopUtils.isAopProxy(service));
        jdbc.update("INSERT INTO tk_open_tiktok_publish_task (id,task_id,client_id,media_id,post_mode,privacy_level,"
                + "account_count,pending_count,status,fail_reason,external_request_id)"
                + " VALUES (1,'t','c','m','DIRECT_POST','SELF_ONLY',1,1,'PROCESSING','old failure','external')");
        jdbc.update("INSERT INTO tk_open_tiktok_publish_detail (id,detail_id,task_id,client_id,connection_id,status,"
                + "publish_id,retry_count,fail_reason,metrics_status,metrics_fail_reason)"
                + " VALUES (1,'d','t','c','connection','PROCESSING','publish',0,'old failure','WAITING_PUBLISH','old metrics failure')");
        jdbc.update("INSERT INTO tk_open_tiktok_publish_attempt (id,detail_id,task_id,client_id,attempt_no,phase,"
                + "publish_id,last_error,next_reconcile_time)"
                + " VALUES (1,'d','t','c',0,'POLLING','publish','old error',CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void closeOnlyThisTestsResources() throws Exception {
        if (callbacks != null) callbacks.destroy();
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void claimingQueuedDetailAlsoMarksTaskProcessingInSameTransaction() {
        jdbc.update("DELETE FROM tk_open_tiktok_publish_attempt");
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='PENDING',publish_id=NULL WHERE id=1");
        jdbc.update("UPDATE tk_open_tiktok_publish_task SET status='PENDING' WHERE id=1");
        ProxyFactory proxy = new ProxyFactory(new TkOpenTiktokPublishAttemptService(attempts, details, tasks));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        TkOpenTiktokPublishAttemptService claims = (TkOpenTiktokPublishAttemptService) proxy.getProxy();

        assertNotNull(claims.claim(snapshot()));
        assertEquals("PROCESSING", snapshot().getStatus());
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals(0, eventCount());
    }

    @Test
    void commitsPrivateSuccessEvidenceSummaryAndSingleCallbackWithoutPublicId() {
        assertTrue(service.confirm(snapshot(), "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        assertEquals("SUCCESS", snapshot().getStatus());
        assertNull(snapshot().getPublicPostId());
        assertNull(snapshot().getFailReason());
        assertNull(snapshot().getMetricsFailReason());
        assertEquals("WAITING_PUBLIC", snapshot().getMetricsStatus());
        TkOpenTiktokPublishTaskDO task = tasks.selectById(1L);
        assertEquals("SUCCESS", task.getStatus());
        assertEquals(1, task.getSuccessCount());
        assertEquals(0, task.getPendingCount());
        assertNull(task.getFailReason());
        TkOpenTiktokPublishAttemptDO attempt = attempts.selectCurrent("d", 0);
        assertEquals("CONFIRMED_TERMINAL", attempt.getPhase());
        assertEquals("STATUS_API", attempt.getTerminalSource());
        assertNotNull(attempt.getTerminalTime());
        assertNull(attempt.getLastError());
        assertNull(attempt.getNextReconcileTime());
        assertEquals(1, eventCount());
    }

    @Test
    void rollbackAfterRealOutboxInsertRestoresDetailAttemptTaskAndOutbox() {
        callbacks.failAfterInsert = true;
        assertThrows(IllegalStateException.class,
                () -> service.confirm(snapshot(), "PUBLISH_COMPLETE", "post", null, "WEBHOOK"));
        assertTrue(callbacks.observedActiveTransaction);
        assertEquals("SUCCESS", callbacks.observedDetailStatus);
        assertEquals("SUCCESS", callbacks.observedTaskStatus);
        assertEquals("CONFIRMED_TERMINAL", callbacks.observedAttemptPhase);
        assertEquals(1, callbacks.observedEvents);
        assertEquals("PROCESSING", snapshot().getStatus());
        assertNull(snapshot().getPublicPostId());
        assertEquals("old failure", snapshot().getFailReason());
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals(1, tasks.selectById(1L).getPendingCount());
        assertEquals("POLLING", attempts.selectCurrent("d", 0).getPhase());
        assertNull(attempts.selectCurrent("d", 0).getTerminalTime());
        assertEquals(0, eventCount());
        callbacks.failAfterInsert = false;
        assertTrue(service.confirm(snapshot(), "PUBLISH_COMPLETE", "post", null, "WEBHOOK"));
        assertEquals(1, eventCount());
    }

    @Test
    void duplicateEvidenceAndLaterPublicIdKeepOneOriginalCallback() {
        TkOpenTiktokPublishDetailDO expected = snapshot();
        assertTrue(service.confirm(expected, "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        String originalPayload = event().getPayloadJson();
        LocalDateTime evidenceTime = attempts.selectCurrent("d", 0).getTerminalTime();
        assertTrue(service.confirm(expected, "PUBLISH_COMPLETE", "post", null, "WEBHOOK"));
        assertTrue(service.confirm(expected, "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        assertEquals("post", snapshot().getPublicPostId());
        assertEquals("SYNCING", snapshot().getMetricsStatus());
        assertEquals(1, eventCount());
        assertEquals(originalPayload, event().getPayloadJson());
        assertEquals(evidenceTime, attempts.selectCurrent("d", 0).getTerminalTime());
    }

    @Test
    void contradictoryTerminalEvidenceCannotOverwriteEitherOutcome() {
        TkOpenTiktokPublishDetailDO expected = snapshot();
        assertTrue(service.confirm(expected, "FAILED", null, "official rejected", "STATUS_API"));
        assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", "post", null, "WEBHOOK"));
        assertEquals("FAILED", snapshot().getStatus());
        assertEquals("official rejected", snapshot().getFailReason());
        assertEquals(1, eventCount());
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='SUCCESS',fail_reason=NULL WHERE id=1");
        assertFalse(service.confirm(snapshot(), "FAILED", null, "late failure", "STATUS_API"));
        assertEquals("SUCCESS", snapshot().getStatus());
        assertNull(snapshot().getFailReason());
    }

    @Test
    void missingLegacyAttemptDoesNotBlockAuthoritativeConfirmation() {
        jdbc.update("DELETE FROM tk_open_tiktok_publish_attempt");
        assertTrue(service.confirm(snapshot(), "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        assertEquals("SUCCESS", snapshot().getStatus());
        assertEquals(1, eventCount());
    }

    @Test
    void localPreInitializationFailuresCanFailWithoutPublishId() {
        prepareLocalAttempt("MATERIAL_PREPARATION");
        assertTrue(service.confirmLocal(snapshot(), "owner", "invalid media", "PRE_INIT_FAILURE"));
        assertEquals("FAILED", snapshot().getStatus());
        assertEquals("PRE_INIT_FAILURE", attempts.selectCurrent("d", 0).getTerminalSource());
        assertEquals("invalid media", attempts.selectCurrent("d", 0).getLastError());
        assertEquals("FAILED", tasks.selectById(1L).getStatus());
        assertEquals(1, eventCount());
        assertFalse(service.confirmLocal(snapshot(), "owner", "duplicate", "PRE_INIT_FAILURE"));
        assertTrue(service.repairCallback(snapshot()));
        assertEquals(1, eventCount());
    }

    @Test
    void localEvidenceCannotFailLegacyRowWithoutAttempt() {
        prepareLocalAttempt("INIT_SENT");
        jdbc.update("DELETE FROM tk_open_tiktok_publish_attempt");
        for (String source : new String[]{"PRE_INIT_FAILURE", "INIT_REJECTED"}) {
            assertFalse(service.confirm(snapshot(), "FAILED", null, "invalid request", source));
            assertFalse(service.confirmLocal(snapshot(), "owner", "invalid request", source));
        }
        assertEquals("PROCESSING", snapshot().getStatus());
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals(0, eventCount());
    }

    @Test
    void definiteInitRejectionRequiresOwnedInitSentAttempt() {
        prepareLocalAttempt("INIT_SENT");
        assertFalse(service.confirm(snapshot(), "FAILED", null, "invalid request", "INIT_REJECTED"));
        assertFalse(service.confirmLocal(snapshot(), "stale-owner", "invalid request", "INIT_REJECTED"));
        assertFalse(service.confirmLocal(snapshot(), "owner", "late pre-init error", "PRE_INIT_FAILURE"));
        assertEquals(0, eventCount());
        assertTrue(service.confirmLocal(snapshot(), "owner", "invalid request", "INIT_REJECTED"));
        assertEquals("FAILED", snapshot().getStatus());
        assertEquals("INIT_REJECTED", attempts.selectCurrent("d", 0).getTerminalSource());
        assertEquals(1, eventCount());
    }

    @Test
    void localFailureRollsBackAllWritesWhenOutboxInsertFails() {
        prepareLocalAttempt("READY_TO_INIT");
        callbacks.failAfterInsert = true;
        assertThrows(IllegalStateException.class,
                () -> service.confirmLocal(snapshot(), "owner", "invalid media", "PRE_INIT_FAILURE"));
        assertTrue(callbacks.observedActiveTransaction);
        assertEquals("FAILED", callbacks.observedDetailStatus);
        assertEquals("FAILED", callbacks.observedTaskStatus);
        assertEquals("CONFIRMED_TERMINAL", callbacks.observedAttemptPhase);
        assertEquals(1, callbacks.observedEvents);
        assertEquals("PROCESSING", snapshot().getStatus());
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals("READY_TO_INIT", attempts.selectCurrent("d", 0).getPhase());
        assertNull(attempts.selectCurrent("d", 0).getTerminalSource());
        assertNull(attempts.selectCurrent("d", 0).getTerminalTime());
        assertEquals(0, eventCount());
        callbacks.failAfterInsert = false;
        assertTrue(service.confirmLocal(snapshot(), "owner", "invalid media", "PRE_INIT_FAILURE"));
        assertEquals(1, eventCount());
    }

    @Test
    void fencedWorkerCannotTerminalizeNewOwnerOfSameRetry() throws Exception {
        prepareLocalAttempt("MATERIAL_PREPARATION");
        TkOpenTiktokPublishDetailDO stale = snapshot();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try (Connection takeover = dataSource.getConnection()) {
            takeover.setAutoCommit(false);
            try (java.sql.Statement statement = takeover.createStatement()) {
                statement.executeUpdate("UPDATE tk_open_tiktok_publish_attempt SET owner_token='new-owner' WHERE id=1");
            }
            Future<Boolean> staleFailure = worker.submit(() -> {
                started.countDown();
                return service.confirmLocal(stale, "owner", "old worker failed", "PRE_INIT_FAILURE");
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            // The attempt is still locked by takeover; the stale worker cannot complete against its old image.
            assertThrows(TimeoutException.class, () -> staleFailure.get(200, TimeUnit.MILLISECONDS));
            takeover.commit();
            assertFalse(staleFailure.get(10, TimeUnit.SECONDS));
            assertEquals("PROCESSING", snapshot().getStatus());
            assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
            assertEquals("new-owner", attempts.selectCurrent("d", 0).getOwnerToken());
            assertEquals("MATERIAL_PREPARATION", attempts.selectCurrent("d", 0).getPhase());
            assertNull(attempts.selectCurrent("d", 0).getTerminalSource());
            assertEquals(0, eventCount());
            assertTrue(service.confirmLocal(snapshot(), "new-owner", "current worker failed", "PRE_INIT_FAILURE"));
            assertEquals("current worker failed", snapshot().getFailReason());
            assertEquals(1, eventCount());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void incompatibleAttemptIdIsNotMarkedAsTerminal() {
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET publish_id='other' WHERE id=1");
        assertFalse(service.confirm(snapshot(), "PUBLISH_COMPLETE", "post", null, "STATUS_API"));
        assertEquals("POLLING", attempts.selectCurrent("d", 0).getPhase());
        assertNull(attempts.selectCurrent("d", 0).getTerminalSource());
        assertEquals("PROCESSING", snapshot().getStatus());
        assertEquals(0, eventCount());
    }

    @Test
    void blankAttemptIdIsCompatibleWithDurablySavedDetailId() {
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET publish_id=NULL WHERE id=1");
        assertTrue(service.confirm(snapshot(), "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        assertEquals("CONFIRMED_TERMINAL", attempts.selectCurrent("d", 0).getPhase());
    }

    @Test
    void repairsVerifiedTerminalSummaryAndMissingOutboxIdempotently() {
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='SUCCESS',tiktok_status='PUBLISH_COMPLETE',fail_reason=NULL WHERE id=1");
        jdbc.update("DELETE FROM tk_open_tiktok_publish_attempt");
        assertTrue(service.repairCallback(snapshot()));
        assertTrue(service.repairCallback(snapshot()));
        assertEquals("SUCCESS", tasks.selectById(1L).getStatus());
        assertEquals(1, eventCount());
        assertNull(attempts.selectCurrent("d", 0), "repair must not invent evidence");
    }

    @Test
    void repairAlsoRollsBackSummaryAndInsertedOutboxOnFailure() {
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='FAILED',tiktok_status='FAILED',fail_reason='official' WHERE id=1");
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET phase='CONFIRMED_TERMINAL',"
                + "terminal_source='STATUS_API',terminal_time=CURRENT_TIMESTAMP WHERE id=1");
        callbacks.failAfterInsert = true;
        assertThrows(IllegalStateException.class, () -> service.repairCallback(snapshot()));
        assertTrue(callbacks.observedActiveTransaction);
        assertEquals("FAILED", callbacks.observedTaskStatus);
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals(0, eventCount());
        assertEquals("FAILED", snapshot().getStatus());
    }

    @Test
    void repairRejectsLegacyFailureEvenWithIdAndGenericFailedStatus() {
        jdbc.update("DELETE FROM tk_open_tiktok_publish_attempt");
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='FAILED',tiktok_status='FAILED',"
                + "fail_reason='TikTok publish failed: request timed out' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET publish_id=NULL WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        assertEquals("FAILED", snapshot().getStatus(), "historical rows are audited, never rewritten");
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
        assertEquals(0, eventCount());
    }

    @Test
    void repairRequiresCompleteCompatibleEvidenceNotJustTerminalPhase() {
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='FAILED',tiktok_status='FAILED' WHERE id=1");
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET phase='CONFIRMED_TERMINAL' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET terminal_source='STATUS_API' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET terminal_time=CURRENT_TIMESTAMP,"
                + "terminal_source='TIMEOUT' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET terminal_source='STATUS_API',publish_id='other' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET publish_id='publish',phase='INIT_SENT' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET phase='CONFIRMED_TERMINAL' WHERE id=1");
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET tiktok_status='RECOVERY_REQUIRED' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        assertEquals(0, eventCount());
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET tiktok_status='FAILED' WHERE id=1");
        assertTrue(service.repairCallback(snapshot()));
        assertTrue(service.repairCallback(snapshot()));
        assertEquals(1, eventCount());
    }

    @Test
    void repairingVerifiedTerminalPreservesDeliveredPayloadAndTime() {
        assertTrue(service.confirm(snapshot(), "FAILED", null, "official", "STATUS_API"));
        jdbc.update("UPDATE tk_open_api_event SET status='DELIVERED',delivered_time=CURRENT_TIMESTAMP");
        TkOpenApiEventDO delivered = event();
        assertTrue(service.repairCallback(snapshot()));
        assertEquals(1, eventCount());
        assertEquals(delivered.getId(), event().getId());
        assertEquals(delivered.getStatus(), event().getStatus());
        assertEquals(delivered.getPayloadJson(), event().getPayloadJson());
        assertEquals(delivered.getDeliveredTime(), event().getDeliveredTime());
    }

    @Test
    void repairSkipsLegacyHeuristicsAndPreservesDeliveredHistory() {
        assertTrue(service.confirm(snapshot(), "FAILED", null, "official", "STATUS_API"));
        jdbc.update("UPDATE tk_open_api_event SET status='DELIVERED',delivered_time=CURRENT_TIMESTAMP");
        String payload = event().getPayloadJson();
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET fail_reason='TikTok publish was not found after reconciliation' WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='SUCCESS',publish_id=NULL,fail_reason=NULL WHERE id=1");
        assertFalse(service.repairCallback(snapshot()));
        assertEquals(1, eventCount());
        assertEquals("DELIVERED", event().getStatus());
        assertEquals(payload, event().getPayloadJson());
        assertNotNull(event().getDeliveredTime());
    }

    @Test
    void repairRejectsSnapshotWhenRetryChangesBeforeLock() {
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='FAILED' WHERE id=1");
        TkOpenTiktokPublishDetailDO old = snapshot();
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET status='PROCESSING',retry_count=1,publish_id='new' WHERE id=1");
        assertFalse(service.repairCallback(old));
        assertEquals(0, eventCount());
        assertEquals("PROCESSING", tasks.selectById(1L).getStatus());
    }

    @Test
    void aggregatesMixedTaskOutcomesAndKeepsOtherClientsOut() {
        jdbc.update("INSERT INTO tk_open_tiktok_publish_detail (detail_id,task_id,client_id,connection_id,status)"
                + " VALUES ('sibling','t','c','other','FAILED'),('foreign','t','another-client','x','PROCESSING')");
        assertTrue(service.confirm(snapshot(), "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        TkOpenTiktokPublishTaskDO task = tasks.selectById(1L);
        assertEquals("PARTIAL_SUCCESS", task.getStatus());
        assertEquals(1, task.getSuccessCount());
        assertEquals(1, task.getFailedCount());
        assertEquals(0, task.getPendingCount());
    }

    @Test
    void simultaneousOppositeOutcomesSerializeToOneWinnerAndOneCallback() throws Exception {
        TkOpenTiktokPublishDetailDO firstSnapshot = snapshot();
        TkOpenTiktokPublishDetailDO secondSnapshot = snapshot();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> success = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.confirm(firstSnapshot, "PUBLISH_COMPLETE", null, null, "WEBHOOK");
            });
            Future<Boolean> failed = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.confirm(secondSnapshot, "FAILED", null, "official", "STATUS_API");
            });
            start.countDown();
            assertNotEquals(success.get(10, TimeUnit.SECONDS), failed.get(10, TimeUnit.SECONDS));
            assertEquals(1, eventCount());
            assertEquals(snapshot().getStatus(), tasks.selectById(1L).getStatus());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private TkOpenTiktokPublishDetailDO snapshot() {
        return details.selectByClientAndDetailId("c", "d");
    }

    private void prepareLocalAttempt(String phase) {
        jdbc.update("UPDATE tk_open_tiktok_publish_detail SET publish_id=NULL WHERE id=1");
        jdbc.update("UPDATE tk_open_tiktok_publish_attempt SET publish_id=NULL,owner_token='owner',phase=? WHERE id=1", phase);
    }

    private int eventCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tk_open_api_event", Integer.class);
    }

    private TkOpenApiEventDO event() {
        return events.selectListByClient("c", 10).get(0);
    }

    private class FailingAfterEnqueue extends TkOpenApiCallbackService {
        boolean failAfterInsert;
        boolean observedActiveTransaction;
        String observedDetailStatus;
        String observedTaskStatus;
        String observedAttemptPhase;
        int observedEvents;

        FailingAfterEnqueue(TkOpenApiEventMapper eventMapper, TkOpenApiClientMapper clientMapper) {
            super(eventMapper, clientMapper, null, null);
        }

        @Override
        public String enqueueOnce(String clientId, String eventType, String resourceType, String resourceId,
                                  Map<String, Object> payload, int publishAttempt) {
            String eventId = super.enqueueOnce(clientId, eventType, resourceType, resourceId, payload, publishAttempt);
            if (failAfterInsert) {
                observedActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                observedDetailStatus = snapshot().getStatus();
                observedTaskStatus = tasks.selectById(1L).getStatus();
                observedAttemptPhase = attempts.selectCurrent("d", 0).getPhase();
                observedEvents = eventCount();
                throw new IllegalStateException("injected after real outbox insert");
            }
            return eventId;
        }
    }
}
