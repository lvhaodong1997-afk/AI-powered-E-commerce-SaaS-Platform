package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared application + creator quota, independent of access-token refresh and entry point. */
@Component
public class TkTiktokPublishRequestBudget {

    private final RedissonClient redissonClient;

    public TkTiktokPublishRequestBudget(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public boolean tryAcquireInit(String appKey, String openId) {
        return tryAcquire("init", 6, appKey, openId);
    }

    public boolean tryAcquireStatus(String appKey, String openId) {
        return tryAcquire("status", 30, appKey, openId);
    }

    private boolean tryAcquire(String operation, long limit, String appKey, String openId) {
        if (StrUtil.hasBlank(appKey, openId)) {
            return false;
        }
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(
                    "tk:tiktok:publish:budget:" + operation + ":" + identityHash(appKey, openId));
            limiter.trySetRate(RateType.OVERALL, limit, 1, RateIntervalUnit.MINUTES);
            return limiter.tryAcquire();
        } catch (RuntimeException ex) {
            // Fail closed; upstream calls must never bypass a failed shared budget.
            return false;
        }
    }

    private static String identityHash(String appKey, String openId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Length prefix prevents ambiguous concatenations without changing the actual identity.
            byte[] bytes = digest.digest((appKey.length() + ":" + appKey + openId)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
