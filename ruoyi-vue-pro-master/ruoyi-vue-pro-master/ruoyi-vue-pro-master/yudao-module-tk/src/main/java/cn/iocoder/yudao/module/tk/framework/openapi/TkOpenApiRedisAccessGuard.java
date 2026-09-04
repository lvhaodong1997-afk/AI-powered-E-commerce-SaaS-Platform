package cn.iocoder.yudao.module.tk.framework.openapi;

import org.redisson.api.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Component
public class TkOpenApiRedisAccessGuard implements TkOpenApiAccessGuard {

    private final RedissonClient redissonClient;

    public TkOpenApiRedisAccessGuard(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void checkAndConsume(String clientId, String nonce, int rateLimitPerMinute,
                                int dailyQuota, long nonceTtlSeconds) {
        try {
            RBucket<String> nonceBucket = redissonClient.getBucket("tk:open-api:nonce:" + clientId + ":" + nonce);
            if (!nonceBucket.trySet("1", nonceTtlSeconds, TimeUnit.SECONDS)) {
                throw TkOpenApiException.unauthorized("OPEN_API_NONCE_REPLAYED", "nonce has already been used");
            }
            checkRate(clientId, rateLimitPerMinute);
            checkDailyQuota(clientId, dailyQuota);
        } catch (TkOpenApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw TkOpenApiException.unavailable("OPEN_API_GUARD_UNAVAILABLE", "request guard is temporarily unavailable");
        }
    }

    private void checkRate(String clientId, int limit) {
        if (limit <= 0) {
            return;
        }
        RRateLimiter limiter = redissonClient.getRateLimiter("tk:open-api:rate:" + clientId);
        Duration duration = Duration.ofMinutes(1);
        RateLimiterConfig config = limiter.getConfig();
        if (config == null) {
            limiter.trySetRate(RateType.OVERALL, limit, duration);
        } else if (config.getRate() != limit) {
            limiter.setRate(RateType.OVERALL, limit, duration);
        }
        limiter.expire(duration.multipliedBy(2));
        if (!limiter.tryAcquire()) {
            throw TkOpenApiException.tooManyRequests("OPEN_API_RATE_LIMITED", "request rate limit exceeded");
        }
    }

    private void checkDailyQuota(String clientId, int quota) {
        if (quota <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        RAtomicLong counter = redissonClient.getAtomicLong("tk:open-api:quota:" + clientId + ":" + today);
        long used = counter.incrementAndGet();
        if (used == 1) {
            long seconds = java.time.Duration.between(java.time.Instant.now(),
                    today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).getSeconds();
            counter.expire(Math.max(60, seconds), TimeUnit.SECONDS);
        }
        if (used > quota) {
            throw TkOpenApiException.tooManyRequests("OPEN_API_QUOTA_EXCEEDED", "daily request quota exceeded");
        }
    }
}
