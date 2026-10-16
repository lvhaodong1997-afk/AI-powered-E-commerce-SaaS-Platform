package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenTiktokConnectionTokenIntegrationTest {
    @Test
    void candidateQueryIncludesInactiveAccountsAndExcludesExpiredRevokedOrDeletedConnections() throws Exception {
        try (Fixture f = new Fixture()) {
            f.insert(1, "AUTHORIZED", 0, -1, 30);
            f.insert(2, "AUTHORIZED", 0, 20, 30);
            f.insert(3, "AUTHORIZED", 0, 90, 30);
            f.insert(4, "DISCONNECTED", 0, -1, 30);
            f.insert(5, "AUTHORIZED", 1, -1, 30);
            f.insert(6, "AUTHORIZED", 0, -1, -1);
            f.insert(7, "AUTHORIZED", 0, -1, 30);
            f.sql.update("UPDATE tk_open_tiktok_connection SET access_token_expire_time=NULL WHERE id=7");
            f.insert(8, "AUTHORIZED", 0, -1, 30);
            f.sql.update("UPDATE tk_open_tiktok_connection SET refresh_token_cipher='' WHERE id=8");
            LocalDateTime now = LocalDateTime.now();
            assertEquals(Arrays.asList(1L, 2L), f.mapper.selectExpiringAuthorizedConnections(now, now.plusMinutes(30), 0L, 2)
                    .stream().map(TkOpenTiktokConnectionDO::getId).collect(Collectors.toList()));
            assertEquals(Collections.singletonList(7L), f.mapper.selectExpiringAuthorizedConnections(now, now.plusMinutes(30), 2L, 2)
                    .stream().map(TkOpenTiktokConnectionDO::getId).collect(Collectors.toList()));
        }
    }

    @Test
    void failureInFirstPageDoesNotStarveLaterAccountsAndTransientFailureRemainsAuthorized() throws Exception {
        try (Fixture f = new Fixture()) {
            f.insert(1, "AUTHORIZED", 0, 20, 30);
            f.insert(2, "AUTHORIZED", 0, 20, 30);
            when(f.adapter.refreshAccessToken("refresh-1")).thenReturn(
                    new TkOpenPublishPlatformAdapter.OAuthTokenResult(false, null, null, null, null,
                            null, null, "server_error", "temporary"));
            when(f.adapter.refreshAccessToken("refresh-2")).thenReturn(f.success("access-2-new", "refresh-2-new"));

            assertEquals(1, f.service.refreshExpiringAuthorizedConnections(1));
            assertEquals("AUTHORIZED", f.mapper.selectById(1L).getAuthStatus());
            assertEquals("REFRESH_FAILED", f.mapper.selectById(1L).getTokenStatus());
            assertEquals("access-2-new", f.mapper.selectById(2L).getAccessTokenCipher());
        }
    }

    @Test
    void twoConcurrentForcedRefreshesSendOnlyOneTikTokRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            f.insert(1, "AUTHORIZED", 0, -1, 30);
            TkOpenTiktokConnectionDO first = f.mapper.selectById(1L);
            TkOpenTiktokConnectionDO second = f.mapper.selectById(1L);
            CountDownLatch firstRequest = new CountDownLatch(1);
            CountDownLatch secondLockAttempt = new CountDownLatch(1);
            when(f.adapter.refreshAccessToken("refresh-1")).thenAnswer(invocation -> {
                firstRequest.countDown();
                assertTrue(secondLockAttempt.await(5, TimeUnit.SECONDS));
                return f.success("access-new", "refresh-new");
            });
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> a = executor.submit(() -> f.service.getValidAccessToken(first, f.adapter, true));
                assertTrue(firstRequest.await(5, TimeUnit.SECONDS));
                Future<String> b = executor.submit(() -> {
                    secondLockAttempt.countDown();
                    return f.service.getValidAccessToken(second, f.adapter, true);
                });
                assertEquals("access-new", a.get(10, TimeUnit.SECONDS));
                assertEquals("access-new", b.get(10, TimeUnit.SECONDS));
                verify(f.adapter, times(1)).refreshAccessToken(any());
                assertEquals("refresh-new", f.mapper.selectById(1L).getRefreshTokenCipher());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test
    void callerRollbackCannotLoseRotatedRefreshTokenOrInvalidAuthorizationStatus() throws Exception {
        try (Fixture f = new Fixture()) {
            f.insert(1, "AUTHORIZED", 0, -1, 30);
            when(f.adapter.refreshAccessToken("refresh-1")).thenReturn(f.success("access-new", "refresh-new"));
            TransactionTemplate outer = new TransactionTemplate(f.transactions);
            assertThrows(IllegalStateException.class, () -> outer.execute(status -> {
                f.service.getValidAccessToken(f.mapper.selectById(1L), f.adapter, false);
                throw new IllegalStateException("later profile or publish failure");
            }));
            assertEquals("refresh-new", f.mapper.selectById(1L).getRefreshTokenCipher());
            f.insert(2, "AUTHORIZED", 0, -1, 30);
            when(f.adapter.refreshAccessToken("refresh-2")).thenReturn(
                    new TkOpenPublishPlatformAdapter.OAuthTokenResult(false, null, null, null, null,
                            null, null, "invalid_grant", "revoked"));
            assertThrows(TkOpenApiException.class, () -> outer.execute(status ->
                    f.service.getValidAccessToken(f.mapper.selectById(2L), f.adapter, false)));
            assertEquals("REAUTH_REQUIRED", f.mapper.selectById(2L).getAuthStatus());
        }
    }

    @Test
    void concurrentDisconnectCannotBeOverwrittenByRefreshSuccess() throws Exception {
        try (Fixture f = new Fixture()) {
            f.insert(1, "AUTHORIZED", 0, -1, 30);
            when(f.adapter.refreshAccessToken("refresh-1")).thenAnswer(invocation -> {
                f.sql.update("UPDATE tk_open_tiktok_connection SET auth_status='DISCONNECTED', "
                        + "access_token_cipher='', refresh_token_cipher=NULL WHERE id=1");
                return f.success("access-new", "refresh-new");
            });
            assertEquals("CONNECTION_CHANGED", assertThrows(TkOpenApiException.class, () ->
                    f.service.getValidAccessToken(f.mapper.selectById(1L), f.adapter, false)).getCode());
            TkOpenTiktokConnectionDO saved = f.mapper.selectById(1L);
            assertEquals("DISCONNECTED", saved.getAuthStatus());
            assertNull(saved.getRefreshTokenCipher());
            assertEquals("", saved.getAccessTokenCipher());
        }
    }

    private static class Fixture implements AutoCloseable {
        final UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:open_token_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        final JdbcTemplate sql = new JdbcTemplate(source);
        final DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
        final TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        final TkOpenTiktokConnectionMapper mapper;
        final TkOpenTiktokConnectionTokenService service;

        Fixture() throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
            configuration.addMapper(TkOpenTiktokConnectionMapper.class);
            mapper = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration))
                    .getMapper(TkOpenTiktokConnectionMapper.class);
            TableInfo table = TableInfoHelper.getTableInfo(TkOpenTiktokConnectionDO.class);
            String columns = table.getFieldList().stream().map(field -> field.getColumn() + " "
                    + (field.getPropertyType() == LocalDateTime.class ? "TIMESTAMP"
                    : field.getPropertyType() == Long.class || field.getPropertyType() == Boolean.class
                    ? "BIGINT" : "VARCHAR(2048)")) .collect(Collectors.joining(", "));
            sql.execute("CREATE TABLE tk_open_tiktok_connection (id BIGINT PRIMARY KEY, " + columns + ")");
            TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
            when(cipher.decrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(cipher.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
            RedissonClient redis = mock(RedissonClient.class);
            ConcurrentHashMap<String, RLock> locks = new ConcurrentHashMap<>();
            when(redis.getLock(anyString())).thenAnswer(invocation -> locks.computeIfAbsent(invocation.getArgument(0), key -> {
                ReentrantLock actual = new ReentrantLock();
                RLock lock = mock(RLock.class);
                try {
                    when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenAnswer(call ->
                            actual.tryLock(call.getArgument(0), call.getArgument(1)));
                } catch (InterruptedException ex) { throw new AssertionError(ex); }
                when(lock.isHeldByCurrentThread()).thenAnswer(call -> actual.isHeldByCurrentThread());
                doAnswer(call -> { actual.unlock(); return null; }).when(lock).unlock();
                return lock;
            }));
            TkOpenTiktokConnectionTokenService target = new TkOpenTiktokConnectionTokenService();
            ReflectionTestUtils.setField(target, "connectionMapper", mapper);
            ReflectionTestUtils.setField(target, "secretCipher", cipher);
            ReflectionTestUtils.setField(target, "redissonClient", redis);
            when(adapter.platform()).thenReturn("TIKTOK");
            // Build the registry after the adapter's platform is configured.
            ReflectionTestUtils.setField(target, "platformRegistry", new TkOpenPublishPlatformRegistry(Collections.singletonList(adapter)));
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
            service = (TkOpenTiktokConnectionTokenService) proxy.getProxy();
        }

        void insert(long id, String authStatus, int deleted, int accessMinutes, int refreshDays) {
            sql.update("INSERT INTO tk_open_tiktok_connection (id,auth_status,deleted,access_token_cipher,refresh_token_cipher,"
                            + "access_token_expire_time,refresh_token_expire_time,token_status,last_auth_time) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, authStatus, deleted, "access-" + id, "refresh-" + id,
                    LocalDateTime.now().plusMinutes(accessMinutes), LocalDateTime.now().plusDays(refreshDays),
                    "NORMAL", LocalDateTime.now().minusDays(365));
        }

        TkOpenPublishPlatformAdapter.OAuthTokenResult success(String access, String refresh) {
            return new TkOpenPublishPlatformAdapter.OAuthTokenResult(true, access, refresh, 86400L, 31536000L,
                    null, null, null, null);
        }

        @Override
        public void close() throws Exception {
            // JdbcTemplate checks warnings after execution, but SHUTDOWN closes the connection.
            try (java.sql.Connection connection = source.getConnection();
                 java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
