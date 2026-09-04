package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TkOpenApiRedisAccessGuardTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    @SuppressWarnings("unchecked")
    private final RBucket<String> nonceBucket = mock(RBucket.class);
    private final RRateLimiter rateLimiter = mock(RRateLimiter.class);
    private final RAtomicLong quotaCounter = mock(RAtomicLong.class);
    private final TkOpenApiRedisAccessGuard guard = new TkOpenApiRedisAccessGuard(redissonClient);

    @BeforeEach
    void setUp() {
        doReturn(nonceBucket).when(redissonClient).getBucket(anyString());
    }

    @Test
    void shouldRejectDuplicateNonceBeforeRateAndQuotaChecks() {
        when(nonceBucket.trySet("1", 300, TimeUnit.SECONDS)).thenReturn(false);

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> guard.checkAndConsume("client-a", "nonce-1", 120, 10_000, 300));

        assertEquals("OPEN_API_NONCE_REPLAYED", error.getCode());
        verify(redissonClient, never()).getRateLimiter(anyString());
        verify(redissonClient, never()).getAtomicLong(anyString());
    }

    @Test
    void shouldRejectRateLimitBeforeConsumingDailyQuota() {
        when(nonceBucket.trySet("1", 300, TimeUnit.SECONDS)).thenReturn(true);
        when(redissonClient.getRateLimiter("tk:open-api:rate:client-a")).thenReturn(rateLimiter);
        when(rateLimiter.getConfig()).thenReturn(null);
        when(rateLimiter.tryAcquire()).thenReturn(false);

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> guard.checkAndConsume("client-a", "nonce-2", 120, 10_000, 300));

        assertEquals("OPEN_API_RATE_LIMITED", error.getCode());
        verify(rateLimiter).trySetRate(RateType.OVERALL, 120, Duration.ofMinutes(1));
        verify(rateLimiter).expire(Duration.ofMinutes(2));
        verify(redissonClient, never()).getAtomicLong(anyString());
    }

    @Test
    void shouldRejectWhenDailyQuotaIsExceeded() {
        when(nonceBucket.trySet("1", 300, TimeUnit.SECONDS)).thenReturn(true);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(quotaCounter);
        when(quotaCounter.incrementAndGet()).thenReturn(11L);

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> guard.checkAndConsume("client-a", "nonce-3", 0, 10, 300));

        assertEquals("OPEN_API_QUOTA_EXCEEDED", error.getCode());
        verify(quotaCounter).incrementAndGet();
        verify(quotaCounter, never()).expire(anyLong(), any(TimeUnit.class));
    }
}
