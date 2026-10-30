package cn.iocoder.yudao.module.tk.service.tiktok;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TkTiktokPublishRequestBudgetTest {
    @Test void sharesIdentityAcrossInstancesAndSeparatesOperationsWithoutLeakingIdentity() {
        RedissonClient redis = mock(RedissonClient.class);
        RRateLimiter limiter = mock(RRateLimiter.class);
        when(redis.getRateLimiter(anyString())).thenReturn(limiter);
        when(limiter.tryAcquire()).thenReturn(true, false, true);
        TkTiktokPublishRequestBudget first = new TkTiktokPublishRequestBudget(redis);
        TkTiktokPublishRequestBudget second = new TkTiktokPublishRequestBudget(redis);
        assertTrue(first.tryAcquireInit("app-marker", "user-marker"));
        assertFalse(second.tryAcquireInit("app-marker", "user-marker"));
        assertTrue(second.tryAcquireStatus("app-marker", "user-marker"));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redis, times(3)).getRateLimiter(keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(2));
        assertFalse(keys.getValue().contains("user-marker"));
        assertFalse(keys.getValue().contains("app-marker"));
        verify(limiter, times(2)).trySetRate(RateType.OVERALL, 6, 1, RateIntervalUnit.MINUTES);
        verify(limiter).trySetRate(RateType.OVERALL, 30, 1, RateIntervalUnit.MINUTES);
    }
    @Test void failsClosedForMissingIdentityAndRedisFailure() {
        RedissonClient redis = mock(RedissonClient.class);
        TkTiktokPublishRequestBudget budget = new TkTiktokPublishRequestBudget(redis);
        assertFalse(budget.tryAcquireInit(null, "user"));
        assertFalse(budget.tryAcquireStatus("app", " "));
        verifyNoInteractions(redis);
        when(redis.getRateLimiter(anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertFalse(budget.tryAcquireInit("app", "user"));
    }
}
