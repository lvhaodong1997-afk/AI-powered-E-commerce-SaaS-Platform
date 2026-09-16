package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialPublishCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAccountService;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Executes production worker/service SQL using real MyBatis-Plus mappers and Spring transactions.
 * Only remote platform/token/media-URL and caller-identity boundaries are stubbed.
 * Each test owns a separate H2 database; it never connects to the application database.
 */
class TkSocialPublishPersistenceTest {
    private static final Map<ch.qos.logback.classic.Logger, ch.qos.logback.classic.Level> LOG_LEVELS = new HashMap<>();

    @BeforeAll
    static void quietFixtureLogging() {
        for (String name : Arrays.asList("org.springframework", "org.mybatis", "org.apache.ibatis",
                "com.baomidou.mybatisplus", "org.hibernate.validator", "cn.iocoder.yudao.module.tk.dal.mysql.social")) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(name);
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger logback = (ch.qos.logback.classic.Logger) logger;
                LOG_LEVELS.put(logback, logback.getLevel());
                logback.setLevel(ch.qos.logback.classic.Level.WARN);
            }
        }
    }

    @AfterAll
    static void restoreFixtureLogging() {
        LOG_LEVELS.forEach(ch.qos.logback.classic.Logger::setLevel);
        LOG_LEVELS.clear();
    }

    private static final long TENANT = 2L;
    private static final long COMPANY = 3L;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TkSocialPublishDetailMapper details;
    private TkSocialPublishTaskMapper tasks;
    private TkSocialAccountMapper accountMapper;
    private TkSocialPublishService publisher;
    private TkSocialPublishWorker worker;
    private TkSocialPlatformClient platform;
    private ValidatorFactory validators;
    private final ClaimReadBarrier claimReadBarrier = new ClaimReadBarrier();

    @BeforeEach
    void setupRealDatabaseAndMappers() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:meta_publish_"
                + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // Reuse production columns, nullability and unique keys, not a hand-written substitute.
        String migration;
        try (InputStream stream = new ClassPathResource("sql/tk_social_publish_center_mysql.sql").getInputStream()) {
            migration = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
        }
        int menuStart = migration.indexOf("INSERT INTO system_menu");
        assertTrue(menuStart > 0, "Keep this harness aligned with the additive production migration");
        String schema = migration.substring(0, menuStart)
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "")
                .replace("bit(1)", "tinyint").replace("b'0'", "0");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(claimReadBarrier);
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
                strictInsertFill(object, "creator", String.class, "7");
                strictInsertFill(object, "updater", String.class, "7");
            }
            @Override public void updateFill(MetaObject object) {
                strictUpdateFill(object, "updateTime", LocalDateTime.class, LocalDateTime.now());
                strictUpdateFill(object, "updater", String.class, "7");
            }
        });
        MybatisSqlSessionFactoryBean builder = new MybatisSqlSessionFactoryBean();
        builder.setDataSource(dataSource);
        builder.setConfiguration(configuration);
        builder.setGlobalConfig(global);
        builder.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionFactory factory = builder.getObject();
        assertNotNull(factory);
        configuration.addMapper(TkSocialPublishTaskMapper.class);
        configuration.addMapper(TkSocialPublishDetailMapper.class);
        configuration.addMapper(TkSocialAccountMapper.class);
        configuration.addMapper(TkSocialMediaMapper.class);
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory);
        tasks = sessions.getMapper(TkSocialPublishTaskMapper.class);
        details = sessions.getMapper(TkSocialPublishDetailMapper.class);
        accountMapper = sessions.getMapper(TkSocialAccountMapper.class);
        TkSocialMediaMapper mediaMapper = sessions.getMapper(TkSocialMediaMapper.class);

        TkSocialAccountService credentials = mock(TkSocialAccountService.class);
        when(credentials.requireReadable(anyLong())).thenAnswer(call -> accountMapper.selectById((Long) call.getArgument(0)));
        when(credentials.getValidToken(any(TkSocialAccountDO.class))).thenReturn("test-only-token");
        TkSocialMediaService urls = mock(TkSocialMediaService.class);
        TkDataScopeService scope = mock(TkDataScopeService.class);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, TENANT, "TENANT_ADMIN", COMPANY));
        when(scope.getWritableCompanyId(isNull())).thenReturn(COMPANY);
        validators = Validation.buildDefaultValidatorFactory();
        publisher = new TkSocialPublishService(tasks, details, credentials, urls, scope,
                new DataSourceTransactionManager(dataSource), validators.getValidator());
        ReflectionTestUtils.setField(publisher, "enabled", true);
        platform = mock(TkSocialPlatformClient.class);
        worker = new TkSocialPublishWorker(details, tasks, accountMapper, mediaMapper, credentials, urls, platform, publisher);
        insertAccount(4L);
        insertAccount(5L);
    }

    @AfterEach
    void releaseOnlyThisTestsDatabase() throws Exception {
        claimReadBarrier.reads = null;
        if (claimReadBarrier.releaseSnapshot != null) claimReadBarrier.releaseSnapshot.countDown();
        if (validators != null) validators.close();
        // JdbcTemplate reads Statement warnings after execution. H2 SHUTDOWN has already
        // closed that statement, so warning inspection incorrectly reports error 90121.
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void productionMigrationMatchesMapperColumnsTypesAndTaskDetailNullability() throws Exception {
        for (Class<?> entity : Arrays.asList(TkSocialPublishTaskDO.class, TkSocialPublishDetailDO.class,
                TkSocialAccountDO.class, TkSocialMediaDO.class)) {
            TableInfo table = TableInfoHelper.getTableInfo(entity);
            assertNotNull(table, "MyBatis must initialize mapper metadata for " + entity.getSimpleName());
            Map<String, ColumnMetadata> columns = columns(table.getTableName());
            assertMappedColumn(columns, table.getKeyColumn(), table.getKeyType());
            table.getFieldList().forEach(field -> assertMappedColumn(columns, field.getColumn(), field.getPropertyType()));
        }
        Map<String, ColumnMetadata> taskColumns = columns("tk_social_publish_task");
        assertNullability(taskColumns, DatabaseMetaData.columnNoNulls,
                "id", "tenant_id", "company_id", "title", "status", "idempotency_key", "request_hash",
                "target_count", "success_count", "failed_count", "pending_count", "creator", "create_time",
                "updater", "update_time", "deleted");
        assertNullability(taskColumns, DatabaseMetaData.columnNullable,
                "media_id", "instagram_caption", "facebook_message");
        Map<String, ColumnMetadata> detailColumns = columns("tk_social_publish_detail");
        assertNullability(detailColumns, DatabaseMetaData.columnNoNulls,
                "id", "tenant_id", "company_id", "publish_task_id", "social_account_id", "platform", "status",
                "retry_count", "poll_count", "creator", "create_time", "updater", "update_time", "deleted");
        assertNullability(detailColumns, DatabaseMetaData.columnNullable,
                "account_name", "platform_status", "external_container_id", "external_media_id", "external_post_id",
                "publish_url", "lease_token", "error_code", "error_message", "next_retry_time", "lease_until",
                "last_sync_time", "published_time");
        assertEquals(80, taskColumns.get("idempotency_key").size);
        assertEquals(64, taskColumns.get("request_hash").size);
        assertEquals(64, detailColumns.get("lease_token").size);
        assertEquals(512, detailColumns.get("error_message").size);
    }

    @Test
    void concurrentWorkersReadingTheSamePendingSnapshotHaveOnlyOneSuccessfulClaim() throws Exception {
        seedTask(10L, "PENDING", 1);
        seedDetail(100L, 10L, 4L, "PENDING");
        AtomicInteger remoteCalls = new AtomicInteger();
        when(platform.advance(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> { remoteCalls.incrementAndGet(); return success("post-once"); });
        // Both real SELECTs finish before either worker can execute its conditional UPDATE.
        claimReadBarrier.reads = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> worker.process(100L, TENANT));
            Future<?> second = pool.submit(() -> worker.process(100L, TENANT));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            claimReadBarrier.reads = null;
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(1, remoteCalls.get(), "The losing DB CAS must not call the platform");
        assertEquals("SUCCESS", detail().getStatus());
        assertEquals("post-once", detail().getExternalPostId());
        assertNull(detail().getLeaseToken());
        assertNull(detail().getLeaseUntil());
        assertNull(detail().getNextRetryTime());
        assertParent(10L, "SUCCESS", 1, 0, 0);
    }

    @Test
    void staleLeaseCannotOverwriteNewOwnerEvenWhenStatusStillProcessing() {
        seedTask(10L, "PROCESSING", 1);
        seedDetail(100L, 10L, 4L, "PENDING");
        when(platform.advance(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    String oldLease = detail().getLeaseToken();
                    assertNotNull(oldLease);
                    // Simulate a lease owner change during slow HTTP; keep status identical so
                    // this specifically proves the token predicate, not just the status predicate.
                    assertEquals(1, jdbc.update("UPDATE tk_social_publish_detail SET lease_token=?,"
                            + "lease_until=?,external_container_id=? WHERE id=? AND lease_token=?",
                            "new-owner", LocalDateTime.now().plusMinutes(5), "new-checkpoint", 100L, oldLease));
                    return success("stale-post");
                });
        worker.process(100L, TENANT);
        assertEquals("PROCESSING", detail().getStatus());
        assertEquals("new-owner", detail().getLeaseToken());
        assertEquals("new-checkpoint", detail().getExternalContainerId());
        assertNull(detail().getExternalPostId());
        assertNull(detail().getPublishedTime());
        assertParent(10L, "PROCESSING", 0, 0, 1);
    }

    @Test
    void claimWinnerReloadsCheckpointWrittenAfterItsOriginalPendingRead() throws Exception {
        seedTask(10L, "PENDING", 1);
        seedDetail(100L, 10L, 4L, "PENDING");
        AtomicInteger calls = new AtomicInteger();
        when(platform.advance(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    if (calls.incrementAndGet() == 1) {
                        assertNull(call.getArgument(6));
                        TkSocialPlatformClient.PublishResult waiting = new TkSocialPlatformClient.PublishResult();
                        waiting.setStatus("WAITING");
                        waiting.setContainerId("checkpoint-created-by-A");
                        waiting.setPlatformStatus("IN_PROGRESS");
                        return waiting;
                    }
                    assertEquals("checkpoint-created-by-A", call.getArgument(6),
                            "Worker B must reload after CAS rather than repeat A's container mutation");
                    assertEquals("IN_PROGRESS", call.getArgument(7));
                    return success("resumed-post");
                });
        claimReadBarrier.pausedThreadName = "meta-stale-checkpoint-worker";
        claimReadBarrier.snapshotRead = new CountDownLatch(1);
        claimReadBarrier.releaseSnapshot = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> new Thread(r, claimReadBarrier.pausedThreadName));
        try {
            Future<?> workerB = pool.submit(() -> worker.process(100L, TENANT));
            assertTrue(claimReadBarrier.snapshotRead.await(5, TimeUnit.SECONDS));
            worker.process(100L, TENANT); // A sees PENDING, claims, and commits a new checkpoint.
            assertEquals("PENDING", detail().getStatus());
            assertEquals("checkpoint-created-by-A", detail().getExternalContainerId());
            // Advance eligibility deterministically, without sleeps or changing B's old snapshot.
            jdbc.update("UPDATE tk_social_publish_detail SET next_retry_time=? WHERE id=100", LocalDateTime.now().minusMinutes(1));
            claimReadBarrier.releaseSnapshot.countDown();
            workerB.get(15, TimeUnit.SECONDS);
        } finally {
            claimReadBarrier.releaseSnapshot.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(2, calls.get());
        assertEquals("SUCCESS", detail().getStatus());
        assertEquals("resumed-post", detail().getExternalPostId());
        assertParent(10L, "SUCCESS", 1, 0, 0);
    }

    @Test
    void expiredLeaseStaysUnknownAfterLatePlatformSuccessAndCannotBeRequeued() {
        seedTask(10L, "PENDING", 1);
        seedDetail(100L, 10L, 4L, "PENDING");
        when(platform.advance(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    jdbc.update("UPDATE tk_social_publish_detail SET lease_until=? WHERE id=100", LocalDateTime.now().minusMinutes(1));
                    worker.expire(detail());
                    return success("too-late");
                });
        worker.process(100L, TENANT);
        assertEquals("UNKNOWN", detail().getStatus());
        assertEquals("WORKER_INTERRUPTED", detail().getErrorCode());
        assertNull(detail().getLeaseToken());
        assertNull(detail().getLeaseUntil());
        assertNull(detail().getNextRetryTime());
        assertNull(detail().getExternalPostId());
        assertParent(10L, "UNKNOWN", 0, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> publisher.retry(100L));
        publisher.sync(10L);
        worker.process(100L, TENANT);
        assertEquals("UNKNOWN", detail().getStatus());
        assertNull(detail().getNextRetryTime());
        verify(platform, times(1)).advance(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void explicitContainerFailureRetryClearsNullableCheckpointsAndResetsParentAtomically() {
        seedTask(10L, "FAILED", 1);
        seedDetail(100L, 10L, 4L, "FAILED");
        poisonFailedDetail("FB_VIDEO_PROCESSING_FAILED");
        publisher.aggregate(10L, TENANT);
        publisher.retry(100L);
        TkSocialPublishDetailDO row = detail();
        assertEquals("PENDING", row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertEquals(0, row.getPollCount());
        assertNotNull(row.getNextRetryTime());
        assertNull(row.getErrorCode());
        assertNull(row.getErrorMessage());
        assertNull(row.getLeaseToken());
        assertNull(row.getLeaseUntil());
        assertNull(row.getExternalContainerId());
        assertNull(row.getExternalMediaId());
        assertNull(row.getExternalPostId());
        assertNull(row.getPlatformStatus());
        assertNull(row.getPublishUrl());
        assertParent(10L, "PENDING", 0, 0, 1);
    }

    @Test
    void reauthorizationRetryPreservesResumablePlatformCheckpoint() {
        seedTask(10L, "REAUTH_REQUIRED", 1);
        seedDetail(100L, 10L, 4L, "REAUTH_REQUIRED");
        poisonFailedDetail("AUTH_EXPIRED");
        publisher.retry(100L);
        assertEquals("PENDING", detail().getStatus());
        assertEquals("old-container", detail().getExternalContainerId());
        assertEquals("IN_PROGRESS", detail().getPlatformStatus());
        assertNull(detail().getErrorCode());
        assertEquals(0, detail().getRetryCount());
        assertParent(10L, "PENDING", 0, 0, 1);
    }

    @Test
    void retryReloadsFailureCodeUnderParentLockBeforeDecidingCheckpointReset() throws Exception {
        seedTask(10L, "FAILED", 1);
        seedDetail(100L, 10L, 4L, "FAILED");
        poisonFailedDetail("AUTH_EXPIRED");
        claimReadBarrier.pausedThreadName = "meta-stale-retry-worker";
        claimReadBarrier.snapshotRead = new CountDownLatch(1);
        claimReadBarrier.releaseSnapshot = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> new Thread(r, claimReadBarrier.pausedThreadName));
        try {
            Future<?> retry = pool.submit(() -> publisher.retry(100L));
            assertTrue(claimReadBarrier.snapshotRead.await(5, TimeUnit.SECONDS));
            // The old AUTH_EXPIRED snapshot would preserve the container. A committed,
            // explicit processing failure now requires discarding it before another attempt.
            jdbc.update("UPDATE tk_social_publish_detail SET error_code='FB_VIDEO_PROCESSING_FAILED',"
                    + "external_container_id='newly-failed-container' WHERE id=100");
            claimReadBarrier.releaseSnapshot.countDown();
            retry.get(15, TimeUnit.SECONDS);
        } finally {
            claimReadBarrier.releaseSnapshot.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals("PENDING", detail().getStatus());
        assertNull(detail().getExternalContainerId(), "Retry must use the fresh failure code after locking the parent");
        assertNull(detail().getPlatformStatus());
        assertNull(detail().getExternalPostId());
        assertNull(detail().getErrorCode());
        assertEquals(0, detail().getRetryCount());
        assertParent(10L, "PENDING", 0, 0, 1);
    }

    @Test
    void aggregateReadsCommittedDetailsAndDoesNotFinishWhileAnyTargetIsPending() {
        seedTask(10L, "PENDING", 4);
        seedDetail(100L, 10L, 4L, "SUCCESS");
        seedDetail(101L, 10L, 5L, "FAILED");
        seedDetail(102L, 10L, 6L, "PENDING");
        seedDetail(103L, 10L, 7L, "UNKNOWN");
        seedDetail(104L, 10L, 8L, "PENDING");
        seedDetail(105L, 10L, 9L, "PENDING");
        jdbc.update("UPDATE tk_social_publish_detail SET tenant_id=99 WHERE id=104");
        jdbc.update("UPDATE tk_social_publish_detail SET deleted=1 WHERE id=105");
        publisher.aggregate(10L, TENANT);
        assertParent(10L, "PROCESSING", 1, 2, 1);
        jdbc.update("UPDATE tk_social_publish_detail SET status='FAILED' WHERE id=102");
        publisher.aggregate(10L, TENANT);
        assertParent(10L, "UNKNOWN", 1, 3, 0);
        jdbc.update("UPDATE tk_social_publish_detail SET status='FAILED' WHERE id=103");
        publisher.aggregate(10L, TENANT);
        assertParent(10L, "PARTIAL_SUCCESS", 1, 3, 0);
        jdbc.update("UPDATE tk_social_publish_detail SET status='SUCCESS' WHERE id IN (101,102,103)");
        publisher.aggregate(10L, TENANT);
        assertParent(10L, "SUCCESS", 4, 0, 0);
        assertEquals(4, tasks.selectById(10L).getTargetCount());
        publisher.aggregate(10L, 99L);
        assertParent(10L, "SUCCESS", 4, 0, 0);
    }

    @Test
    void parentUpdateConstraintFailureRollsBackRetryAndAllNullResets() {
        seedTask(10L, "FAILED", 1);
        seedDetail(100L, 10L, 4L, "FAILED");
        poisonFailedDetail("FB_VIDEO_PROCESSING_FAILED");
        publisher.aggregate(10L, TENANT);
        jdbc.execute("ALTER TABLE tk_social_publish_task ADD CONSTRAINT reject_pending_parent CHECK (status <> 'PENDING')");
        assertThrows(DataIntegrityViolationException.class, () -> publisher.retry(100L));
        assertEquals("FAILED", detail().getStatus());
        assertEquals("FB_VIDEO_PROCESSING_FAILED", detail().getErrorCode());
        assertEquals("old-container", detail().getExternalContainerId());
        assertEquals("old-lease", detail().getLeaseToken());
        assertEquals(3, detail().getRetryCount());
        assertEquals(240, detail().getPollCount());
        assertNull(detail().getNextRetryTime());
        assertParent(10L, "FAILED", 0, 1, 0);
    }

    @Test
    void laterTargetInsertFailureRollsBackTheTaskAndEarlierTargets() {
        jdbc.execute("ALTER TABLE tk_social_publish_detail ADD CONSTRAINT reject_second_target CHECK (social_account_id <> 5)");
        assertThrows(DataIntegrityViolationException.class, () -> publisher.create(request("rollback-test-key")));
        assertEquals(0, count("tk_social_publish_task"));
        assertEquals(0, count("tk_social_publish_detail"));
        jdbc.execute("ALTER TABLE tk_social_publish_detail DROP CONSTRAINT reject_second_target");
        assertNotNull(publisher.create(request("rollback-test-key")), "Rollback must also release the idempotency key");
        assertEquals(1, count("tk_social_publish_task"));
        assertEquals(2, count("tk_social_publish_detail"));
    }

    @Test
    void outcomeAndParentAggregateRollBackTogetherLeavingLeaseForUnknownRecovery() {
        seedTask(10L, "PENDING", 1);
        seedDetail(100L, 10L, 4L, "PENDING");
        jdbc.execute("ALTER TABLE tk_social_publish_task ADD CONSTRAINT reject_success_parent CHECK (status <> 'SUCCESS')");
        when(platform.advance(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success("remote-post-exists"));
        assertThrows(DataIntegrityViolationException.class, () -> worker.process(100L, TENANT));
        assertEquals("PROCESSING", detail().getStatus(), "Result must roll back together with failed parent aggregation");
        assertNotNull(detail().getLeaseToken(), "The committed claim must remain available for interrupted-worker recovery");
        assertNotNull(detail().getLeaseUntil());
        assertNull(detail().getExternalPostId());
        assertNull(detail().getPublishedTime());
        assertParent(10L, "PENDING", 0, 0, 1);
        jdbc.update("UPDATE tk_social_publish_detail SET lease_until=? WHERE id=100", LocalDateTime.now().minusMinutes(1));
        worker.expire(detail());
        assertEquals("UNKNOWN", detail().getStatus());
        assertParent(10L, "UNKNOWN", 0, 1, 0);
        verify(platform, times(1)).advance(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void idempotentCreateUsesDatabaseRowsAndUniqueKeyRatherThanHashComparisonAlone() {
        TkSocialPublishCreateReqVO initial = request("idempotent-test-key");
        Long first = publisher.create(initial);
        TkSocialPublishCreateReqVO reordered = request("idempotent-test-key");
        reordered.setAccountIds(Arrays.asList(5L, 4L, 4L));
        assertEquals(first, publisher.create(reordered));
        assertEquals(1, count("tk_social_publish_task"));
        assertEquals(2, count("tk_social_publish_detail"));
        assertParent(first, "PENDING", 0, 0, 2);
        reordered.setFacebookMessage("different content");
        assertThrows(IllegalArgumentException.class, () -> publisher.create(reordered));
        assertEquals(1, count("tk_social_publish_task"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tk_social_publish_task (tenant_id,company_id,creator,title,status,idempotency_key,request_hash)"
                        + " VALUES (2,3,'7','duplicate','PENDING','idempotent-test-key','hash')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tk_social_publish_detail (tenant_id,company_id,publish_task_id,social_account_id,platform,status)"
                        + " VALUES (2,3,?,4,'FACEBOOK_PAGE','PENDING')", first));
        assertEquals(2, count("tk_social_publish_detail"));
    }

    private void insertAccount(long id) {
        jdbc.update("INSERT INTO tk_social_account (id,tenant_id,company_id,platform,external_account_id,account_name,status,creator)"
                + " VALUES (?,2,3,'FACEBOOK_PAGE',?,?,'AUTHORIZED','7')", id, "page-" + id, "Page " + id);
    }

    private void seedTask(long id, String status, int targets) {
        jdbc.update("INSERT INTO tk_social_publish_task (id,tenant_id,company_id,title,facebook_message,status,"
                        + "idempotency_key,request_hash,target_count,success_count,failed_count,pending_count,creator)"
                        + " VALUES (?,2,3,'DB integration','Text',?,?,?, ?,0,0,?,'7')",
                id, status, "seed-key-" + id, "seed-hash", targets, targets);
    }

    private void seedDetail(long id, long taskId, long accountId, String status) {
        jdbc.update("INSERT INTO tk_social_publish_detail (id,tenant_id,company_id,publish_task_id,social_account_id,platform,status,next_retry_time,creator)"
                        + " VALUES (?,2,3,?,?,'FACEBOOK_PAGE',?,?,'7')",
                id, taskId, accountId, status, "PENDING".equals(status) ? LocalDateTime.now().minusMinutes(1) : null);
    }

    private void poisonFailedDetail(String code) {
        jdbc.update("UPDATE tk_social_publish_detail SET error_code=?,error_message='old error',retry_count=3,poll_count=240,"
                + "lease_token='old-lease',lease_until=?,external_container_id='old-container',external_media_id='old-media',"
                + "external_post_id='old-post',platform_status='IN_PROGRESS',publish_url='https://example.invalid/old',next_retry_time=NULL WHERE id=100",
                code, LocalDateTime.now().minusMinutes(1));
    }

    private TkSocialPublishDetailDO detail() { return details.selectById(100L); }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private void assertParent(long id, String status, int success, int failed, int pending) {
        TkSocialPublishTaskDO task = tasks.selectById(id);
        assertNotNull(task);
        assertEquals(status, task.getStatus());
        assertEquals(success, task.getSuccessCount());
        assertEquals(failed, task.getFailedCount());
        assertEquals(pending, task.getPendingCount());
    }

    private static TkSocialPlatformClient.PublishResult success(String postId) {
        TkSocialPlatformClient.PublishResult result = new TkSocialPlatformClient.PublishResult();
        result.setStatus("SUCCESS");
        result.setPostId(postId);
        result.setPublishUrl("https://example.invalid/" + postId);
        return result;
    }

    private static TkSocialPublishCreateReqVO request(String key) {
        TkSocialPublishCreateReqVO request = new TkSocialPublishCreateReqVO();
        request.setAccountIds(Arrays.asList(4L, 5L));
        request.setTitle("Persistence integration");
        request.setFacebookMessage("Facebook text avoids unrelated media preflight");
        request.setIdempotencyKey(key);
        return request;
    }

    private Map<String, ColumnMetadata> columns(String table) throws Exception {
        Map<String, ColumnMetadata> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rows = connection.getMetaData().getColumns(null, "PUBLIC", table.toUpperCase(Locale.ROOT), null)) {
            while (rows.next()) {
                result.put(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                        new ColumnMetadata(rows.getInt("DATA_TYPE"), rows.getInt("NULLABLE"), rows.getInt("COLUMN_SIZE")));
            }
        }
        assertFalse(result.isEmpty(), "Migration must create table " + table);
        return result;
    }

    private static void assertMappedColumn(Map<String, ColumnMetadata> columns, String name, Class<?> javaType) {
        ColumnMetadata column = columns.get(name.toLowerCase(Locale.ROOT));
        assertNotNull(column, "Production migration is missing mapped column " + name);
        int type = column.type;
        if (javaType == Long.class) assertEquals(Types.BIGINT, type, name);
        else if (javaType == Integer.class) assertEquals(Types.INTEGER, type, name);
        else if (javaType == Double.class) assertTrue(type == Types.DOUBLE || type == Types.FLOAT, name);
        else if (javaType == Boolean.class) assertTrue(type == Types.TINYINT || type == Types.BOOLEAN || type == Types.BIT, name);
        else if (javaType == LocalDateTime.class) assertEquals(Types.TIMESTAMP, type, name);
        else if (javaType == String.class) assertTrue(type == Types.VARCHAR || type == Types.LONGVARCHAR || type == Types.CLOB, name);
        else fail("Add an explicit SQL type check for " + name + " (" + javaType.getName() + ")");
    }

    private static void assertNullability(Map<String, ColumnMetadata> columns, int nullable, String... names) {
        for (String name : names) {
            assertNotNull(columns.get(name), "Missing migration column " + name);
            assertEquals(nullable, columns.get(name).nullable, "Unexpected nullability for " + name);
        }
    }

    private static class ColumnMetadata {
        final int type;
        final int nullable;
        final int size;
        ColumnMetadata(int type, int nullable, int size) { this.type = type; this.nullable = nullable; this.size = size; }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    private static class ClaimReadBarrier implements Interceptor {
        private volatile CountDownLatch reads;
        private volatile String pausedThreadName;
        private volatile CountDownLatch snapshotRead;
        private volatile CountDownLatch releaseSnapshot;

        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result = invocation.proceed();
            CountDownLatch gate = reads;
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (gate != null && statement.getId().equals(TkSocialPublishDetailMapper.class.getName() + ".selectById")) {
                gate.countDown();
                assertTrue(gate.await(5, TimeUnit.SECONDS), "Both workers must read PENDING before either claims");
            }
            if (statement.getId().equals(TkSocialPublishDetailMapper.class.getName() + ".selectById")
                    && Thread.currentThread().getName().equals(pausedThreadName)
                    && snapshotRead != null && snapshotRead.getCount() == 1) {
                snapshotRead.countDown();
                assertTrue(releaseSnapshot.await(10, TimeUnit.SECONDS), "Worker A must commit before releasing B's old snapshot");
            }
            return result;
        }
    }
}
