package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.google.common.base.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokPullSourceResolverCacheTest {
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger opens = new AtomicInteger();
    private TkApiKeyConfigService config;
    private HttpURLConnection connection;
    private TkTiktokPullSourceResolver resolver;
    private static final String URL = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/videos/immutable.mp4";
    private static final String PROPERTY = "tkpublish.fnn.net.cn";
    private final Ticker ticker = new Ticker() { public long read() { return nanos.get(); } };

    @BeforeEach void setup() throws Exception {
        settings.put("pull-from-url-enabled", "true"); settings.put("pull-account-allowlist", "*");
        config = mock(TkApiKeyConfigService.class);
        when(config.getValueOrDefault(anyString(), anyString(), anyString()))
                .thenAnswer(i -> settings.getOrDefault(i.getArgument(1), i.getArgument(2)));
        connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentLengthLong()).thenReturn(321L);
        resolver = new TkTiktokPullSourceResolver(config, uri -> {}, uri -> {
            opens.incrementAndGet(); return connection;
        }, ticker);
        TenantContextHolder.setTenantId(100L);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void successfulHeadSharedAcrossAccountsButPolicyCheckedOnEveryCall() {
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertTrue(resolver.resolve(URL, 2L, PROPERTY).isEligible());
        assertEquals(1, opens.get());
        settings.put("pull-from-url-enabled", "false");
        assertFalse(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        settings.put("pull-from-url-enabled", "true");
        settings.put("pull-account-allowlist", "2");
        assertFalse(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertFalse(resolver.resolve(URL, 2L, "unverified.example.com").isEligible());
        assertEquals(1, opens.get());
    }
    @Test void tenantIsolationAndUnknownTenantCannotReuseAnotherTenantsMetadata() {
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        TenantContextHolder.setTenantId(200L);
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertEquals(2, opens.get());
        TenantContextHolder.clear();
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertEquals(4, opens.get());
    }
    @Test void ttlExpiresAndForcesFreshMetadataReadWithoutSleeping() {
        assertEquals(321L, resolver.resolve(URL, 1L, PROPERTY).getFileSize());
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(21));
        try { when(connection.getContentLengthLong()).thenReturn(654L); } catch (Exception ex) { throw new RuntimeException(ex); }
        assertEquals(654L, resolver.resolve(URL, 2L, PROPERTY).getFileSize());
        assertEquals(2, opens.get());
    }
    @Test void failuresNotCachedAndSuccessfulRetryCanPopulateCache() throws Exception {
        when(connection.getResponseCode()).thenReturn(403, 200);
        assertFalse(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertTrue(resolver.resolve(URL, 2L, PROPERTY).isEligible());
        assertEquals(2, opens.get());
    }
    @Test void simultaneousAccountsCoalesceOneInflightHeadPerTenant() throws Exception {
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch followerStarted = new CountDownLatch(1);
        resolver = new TkTiktokPullSourceResolver(config, uri -> {}, uri -> {
            opens.incrementAndGet(); ownerEntered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new java.io.IOException("test timed out"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new java.io.IOException(ex); }
            return connection;
        }, ticker);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> owner = pool.submit(() -> resolveInTenant(1L));
            assertTrue(ownerEntered.await(2, TimeUnit.SECONDS));
            Future<Boolean> follower = pool.submit(() -> { followerStarted.countDown(); return resolveInTenant(2L); });
            assertTrue(followerStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> follower.get(100, TimeUnit.MILLISECONDS));
            assertEquals(1, opens.get());
            release.countDown();
            assertTrue(owner.get(2, TimeUnit.SECONDS));
            assertTrue(follower.get(2, TimeUnit.SECONDS));
            assertEquals(1, opens.get());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    private boolean resolveInTenant(Long account) {
        TenantContextHolder.setTenantId(100L);
        try { return resolver.resolve(URL, account, PROPERTY).isEligible(); }
        finally { TenantContextHolder.clear(); }
    }
    @Test void oldEntriesEvictedWhenCacheCapacityReached() {
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        for (int i = 0; i < 2048; i++) assertTrue(resolver.resolve(URL.replace("immutable", "key" + i), 1L, PROPERTY).isEligible());
        int before = opens.get();
        assertTrue(resolver.resolve(URL, 1L, PROPERTY).isEligible());
        assertEquals(before + 1, opens.get());
    }
}