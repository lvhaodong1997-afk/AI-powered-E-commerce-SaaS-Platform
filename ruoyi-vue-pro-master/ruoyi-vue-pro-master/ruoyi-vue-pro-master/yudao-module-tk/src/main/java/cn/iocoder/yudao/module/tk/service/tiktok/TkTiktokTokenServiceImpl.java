package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkTiktokTokenServiceImpl implements TkTiktokTokenService {

    private static final long ACCESS_REFRESH_AHEAD_MINUTES = 5L;
    private static final long ACTIVE_ACCOUNT_DAYS = 30L;
    private static final long SCHEDULE_REFRESH_AHEAD_MINUTES = 30L;
    private static final int LOCK_WAIT_SECONDS = 10;
    private static final int LOCK_LEASE_SECONDS = 60;
    private static final String LOCK_KEY_PREFIX = "tk:tiktok:token:refresh:";

    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkTiktokTokenCipher tokenCipher;
    @Resource
    private TkTiktokApiClient apiClient;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public String getValidAccessToken(Long accountId) {
        TkTiktokAccountDO account = requireAccount(accountId);
        if (canReuseAccessToken(account, LocalDateTime.now().plusMinutes(ACCESS_REFRESH_AHEAD_MINUTES))) {
            return requireDecryptedAccessToken(account);
        }
        return refreshAccessToken(accountId, false);
    }

    @Override
    public String forceRefreshAccessToken(Long accountId) {
        requireAccount(accountId);
        return refreshAccessToken(accountId, true);
    }

    @Override
    public int refreshExpiringActiveAccounts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();
        List<TkTiktokAccountDO> accounts = accountMapper.selectExpiringActiveAccounts(
                now.minusDays(ACTIVE_ACCOUNT_DAYS), now.plusMinutes(SCHEDULE_REFRESH_AHEAD_MINUTES), safeLimit);
        int refreshed = 0;
        for (TkTiktokAccountDO account : accounts) {
            try {
                forceRefreshAccessToken(account.getId());
                refreshed++;
            } catch (Exception ex) {
                log.warn("[refreshExpiringActiveAccounts][accountId({}) tenantId({}) errorType({})]",
                        account.getId(), account.getTenantId(), ex.getClass().getSimpleName());
            }
        }
        return refreshed;
    }

    private String refreshAccessToken(Long accountId, boolean force) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + accountId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("TikTok Token 正在刷新，请稍后重试");
            }
            TkTiktokAccountDO account = requireAccount(accountId);
            LocalDateTime now = LocalDateTime.now();
            if (!force && canReuseAccessToken(account, now.plusMinutes(ACCESS_REFRESH_AHEAD_MINUTES))) {
                return requireDecryptedAccessToken(account);
            }
            validateRefreshAuthorization(account, now);
            String refreshToken = tokenCipher.decrypt(account.getRefreshTokenCipher());
            if (StrUtil.isBlank(refreshToken)) {
                throw markReauthorizationRequired(account, "TikTok Refresh Token 无法读取，请重新授权账号");
            }
            TkTiktokApiClient.TokenRefreshResult result = apiClient.refreshAccessToken(refreshToken);
            if (!result.isSuccess()) {
                handleRefreshFailure(account, result);
            }
            persistRefreshedTokens(account, result, refreshToken, now);
            return result.getAccessToken();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TikTok Token 刷新被中断，请稍后重试", ex);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private TkTiktokAccountDO requireAccount(Long accountId) {
        TkTiktokAccountDO account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalStateException("TikTok 账号不存在");
        }
        if (!"AUTHORIZED".equals(account.getAuthStatus())) {
            throw new IllegalStateException("TikTok 账号授权已失效，请重新授权账号");
        }
        return account;
    }

    private boolean canReuseAccessToken(TkTiktokAccountDO account, LocalDateTime refreshThreshold) {
        return StrUtil.isNotBlank(account.getAccessTokenCipher())
                && account.getAccessTokenExpireTime() != null
                && account.getAccessTokenExpireTime().isAfter(refreshThreshold);
    }

    private String requireDecryptedAccessToken(TkTiktokAccountDO account) {
        String accessToken = tokenCipher.decrypt(account.getAccessTokenCipher());
        if (StrUtil.isBlank(accessToken)) {
            throw new IllegalStateException("TikTok Access Token 无法读取，请重新授权账号");
        }
        return accessToken;
    }

    private void validateRefreshAuthorization(TkTiktokAccountDO account, LocalDateTime now) {
        if (StrUtil.isBlank(account.getRefreshTokenCipher())) {
            throw markReauthorizationRequired(account, "TikTok 账号缺少 Refresh Token，请重新授权账号");
        }
        if (account.getRefreshTokenExpireTime() == null || !account.getRefreshTokenExpireTime().isAfter(now)) {
            throw markReauthorizationRequired(account, "TikTok Refresh Token 已过期，请重新授权账号");
        }
    }

    private void handleRefreshFailure(TkTiktokAccountDO account, TkTiktokApiClient.TokenRefreshResult result) {
        String reason = sanitizedReason(result.getFailReason(), "TikTok Token 刷新失败");
        if (isReauthorizationRequired(result.getErrorCode())) {
            throw markReauthorizationRequired(account, reason + "，请重新授权账号");
        }
        account.setTokenStatus("REFRESH_FAILED");
        account.setFailReason(reason);
        accountMapper.updateById(account);
        throw new IllegalStateException(reason);
    }

    private boolean isReauthorizationRequired(String errorCode) {
        return "invalid_grant".equals(errorCode)
                || "invalid_refresh_token".equals(errorCode)
                || "refresh_token_invalid".equals(errorCode)
                || "refresh_token_expired".equals(errorCode);
    }

    private IllegalStateException markReauthorizationRequired(TkTiktokAccountDO account, String reason) {
        String safeReason = sanitizedReason(reason, "TikTok 授权已失效，请重新授权账号");
        account.setTokenStatus("INVALID");
        account.setAuthStatus("UNAUTHORIZED");
        account.setFailReason(safeReason);
        accountMapper.updateById(account);
        return new IllegalStateException(safeReason);
    }

    private void persistRefreshedTokens(TkTiktokAccountDO account,
                                        TkTiktokApiClient.TokenRefreshResult result,
                                        String currentRefreshToken,
                                        LocalDateTime now) {
        String rotatedRefreshToken = StrUtil.blankToDefault(result.getRefreshToken(), currentRefreshToken);
        account.setAccessTokenCipher(tokenCipher.encrypt(result.getAccessToken()));
        account.setRefreshTokenCipher(tokenCipher.encrypt(rotatedRefreshToken));
        account.setAccessTokenExpireTime(now.plusSeconds(positiveOrDefault(result.getAccessTokenExpiresIn(), 86_400L)));
        if (result.getRefreshTokenExpiresIn() != null && result.getRefreshTokenExpiresIn() > 0) {
            account.setRefreshTokenExpireTime(now.plusSeconds(result.getRefreshTokenExpiresIn()));
        }
        account.setScopes(StrUtil.blankToDefault(result.getScopes(), account.getScopes()));
        account.setTokenStatus("VALID");
        account.setAuthStatus("AUTHORIZED");
        account.setFailReason(null);
        accountMapper.updateById(account);
    }

    private long positiveOrDefault(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String sanitizedReason(String reason, String fallback) {
        String safe = StrUtil.blankToDefault(reason, fallback)
                .replaceAll("(?i)(access_token|refresh_token)\\s*[:=]\\s*[^，,\\s]+", "$1=***");
        return StrUtil.sub(safe, 0, 512);
    }

}
