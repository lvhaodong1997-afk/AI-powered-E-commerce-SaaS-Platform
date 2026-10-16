package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkOpenTiktokConnectionTokenService {

    private static final long ACCESS_REFRESH_AHEAD_MINUTES = 1L;
    private static final long SCHEDULE_REFRESH_AHEAD_MINUTES = 30L;
    private static final String LOCK_KEY_PREFIX = "tk:tiktok:open-connection:token-refresh:";

    @Resource private TkOpenTiktokConnectionMapper connectionMapper;
    @Resource private TkOpenApiSecretCipher secretCipher;
    @Resource private RedissonClient redissonClient;
    @Resource private TkOpenPublishPlatformRegistry platformRegistry;

    // Token rotation is an external side effect. Persist it before releasing the lock,
    // independently of any caller transaction that might subsequently roll back.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getValidAccessToken(TkOpenTiktokConnectionDO connection,
                                      TkOpenPublishPlatformAdapter adapter, boolean force) {
        return refreshAccessToken(connection, adapter, force, ACCESS_REFRESH_AHEAD_MINUTES);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int refreshExpiringAuthorizedConnections(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireBefore = now.plusMinutes(SCHEDULE_REFRESH_AHEAD_MINUTES);
        TkOpenPublishPlatformAdapter adapter = platformRegistry.getRequired("TIKTOK");
        int refreshed = 0;
        long afterId = 0L;
        while (!Thread.currentThread().isInterrupted()) {
            List<TkOpenTiktokConnectionDO> connections = connectionMapper.selectExpiringAuthorizedConnections(
                    now, expireBefore, afterId, pageSize);
            if (connections.isEmpty()) break;
            for (TkOpenTiktokConnectionDO connection : connections) {
                afterId = connection.getId();
                try {
                    refreshAccessToken(connection, adapter, false, SCHEDULE_REFRESH_AHEAD_MINUTES);
                    refreshed++;
                } catch (Exception ex) {
                    log.warn("[refreshExpiringAuthorizedConnections][connectionId({}) errorType({})]",
                            connection.getId(), ex.getClass().getSimpleName());
                }
                if (Thread.currentThread().isInterrupted()) break;
            }
            if (connections.size() < pageSize) break;
        }
        return refreshed;
    }

    private String refreshAccessToken(TkOpenTiktokConnectionDO connection,
                                       TkOpenPublishPlatformAdapter adapter, boolean force, long aheadMinutes) {
        requireAuthorized(connection);
        if (!force && canReuseAccessToken(connection, aheadMinutes)) {
            String token = secretCipher.decrypt(connection.getAccessTokenCipher());
            if (StrUtil.isNotBlank(token)) return token;
        }
        if (connection.getId() == null) {
            throw TkOpenApiException.notFound("CONNECTION_NOT_FOUND", "connection does not exist");
        }
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + connection.getId());
        boolean locked = false;
        TkOpenTiktokConnectionDO current = null;
        try {
            // Redisson watchdog renews the lease while the bounded TikTok HTTP call runs.
            locked = lock.tryLock(10L, TimeUnit.SECONDS);
            if (!locked) {
                throw TkOpenApiException.tooManyRequests("TOKEN_REFRESH_IN_PROGRESS",
                        "TikTok token is being refreshed, please retry later");
            }
            current = connectionMapper.selectById(connection.getId());
            requireAuthorized(current);
            boolean alreadyRotated = !Objects.equals(connection.getAccessTokenCipher(), current.getAccessTokenCipher());
            if ((!force || alreadyRotated) && canReuseAccessToken(current, aheadMinutes)) {
                String token = secretCipher.decrypt(current.getAccessTokenCipher());
                if (StrUtil.isNotBlank(token)) return token;
            }
            return refreshLocked(current, adapter);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw TkOpenApiException.unavailable("TOKEN_REFRESH_INTERRUPTED", "TikTok token refresh was interrupted");
        } finally {
            if (current != null) copyTokenState(connection, current);
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private String refreshLocked(TkOpenTiktokConnectionDO connection, TkOpenPublishPlatformAdapter adapter) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<TkOpenTiktokConnectionDO> update = tokenUpdate(connection);
        if (StrUtil.isBlank(connection.getRefreshTokenCipher())) {
            throw markFailure(connection, update, true, "TikTok connection is missing a refresh token");
        }
        if (connection.getRefreshTokenExpireTime() == null || !connection.getRefreshTokenExpireTime().isAfter(now)) {
            throw markFailure(connection, update, true, "TikTok refresh token has expired");
        }
        String refreshToken = secretCipher.decrypt(connection.getRefreshTokenCipher());
        if (StrUtil.isBlank(refreshToken)) {
            throw markFailure(connection, update, true, "TikTok refresh token cannot be read");
        }
        TkOpenPublishPlatformAdapter.OAuthTokenResult result;
        try {
            result = adapter.refreshAccessToken(refreshToken);
        } catch (Exception ex) {
            // Exception messages may contain request payloads; persist only the exception type.
            throw markFailure(connection, update, false, "TikTok token refresh failed: " + ex.getClass().getSimpleName());
        }
        if (result == null || !result.isSuccess() || StrUtil.isBlank(result.getAccessToken())) {
            throw markFailure(connection, update, result != null && isReauthorizationRequired(result.getErrorCode()),
                    sanitizedReason(result == null ? null : result.getFailReason()));
        }
        String accessCipher = secretCipher.encrypt(result.getAccessToken());
        String refreshCipher = secretCipher.encrypt(StrUtil.blankToDefault(result.getRefreshToken(), refreshToken));
        LocalDateTime accessExpiry = now.plusSeconds(positiveOrDefault(result.getAccessTokenExpiresIn(), 86400L));
        LocalDateTime refreshExpiry = result.getRefreshTokenExpiresIn() != null && result.getRefreshTokenExpiresIn() > 0
                ? now.plusSeconds(result.getRefreshTokenExpiresIn()) : connection.getRefreshTokenExpireTime();
        String scopes = StrUtil.blankToDefault(result.getScopes(), connection.getScopes());
        update.set(TkOpenTiktokConnectionDO::getAccessTokenCipher, accessCipher)
                .set(TkOpenTiktokConnectionDO::getRefreshTokenCipher, refreshCipher)
                .set(TkOpenTiktokConnectionDO::getAccessTokenExpireTime, accessExpiry)
                .set(TkOpenTiktokConnectionDO::getRefreshTokenExpireTime, refreshExpiry)
                .set(TkOpenTiktokConnectionDO::getScopes, scopes)
                .set(TkOpenTiktokConnectionDO::getTokenStatus, "NORMAL")
                .set(TkOpenTiktokConnectionDO::getFailReason, null);
        persist(update);
        connection.setAccessTokenCipher(accessCipher).setRefreshTokenCipher(refreshCipher)
                .setAccessTokenExpireTime(accessExpiry).setRefreshTokenExpireTime(refreshExpiry)
                .setScopes(scopes).setTokenStatus("NORMAL").setFailReason(null);
        return result.getAccessToken();
    }

    private LambdaUpdateWrapper<TkOpenTiktokConnectionDO> tokenUpdate(TkOpenTiktokConnectionDO connection) {
        LambdaUpdateWrapper<TkOpenTiktokConnectionDO> update = Wrappers.lambdaUpdate(TkOpenTiktokConnectionDO.class)
                .eq(TkOpenTiktokConnectionDO::getId, connection.getId())
                .eq(TkOpenTiktokConnectionDO::getAuthStatus, "AUTHORIZED");
        if (connection.getRefreshTokenCipher() == null) update.isNull(TkOpenTiktokConnectionDO::getRefreshTokenCipher);
        else update.eq(TkOpenTiktokConnectionDO::getRefreshTokenCipher, connection.getRefreshTokenCipher());
        if (connection.getAccessTokenCipher() == null) update.isNull(TkOpenTiktokConnectionDO::getAccessTokenCipher);
        else update.eq(TkOpenTiktokConnectionDO::getAccessTokenCipher, connection.getAccessTokenCipher());
        return update;
    }

    private void persist(LambdaUpdateWrapper<TkOpenTiktokConnectionDO> update) {
        if (connectionMapper.update(null, update) == 0) {
            throw TkOpenApiException.conflict("CONNECTION_CHANGED", "TikTok connection changed, please retry");
        }
    }

    private TkOpenApiException markFailure(TkOpenTiktokConnectionDO connection,
                                           LambdaUpdateWrapper<TkOpenTiktokConnectionDO> update,
                                           boolean reauthorize, String reason) {
        String safeReason = sanitizedReason(reason);
        update.set(TkOpenTiktokConnectionDO::getTokenStatus, reauthorize ? "INVALID" : "REFRESH_FAILED")
                .set(TkOpenTiktokConnectionDO::getFailReason, safeReason);
        if (reauthorize) update.set(TkOpenTiktokConnectionDO::getAuthStatus, "REAUTH_REQUIRED");
        persist(update);
        connection.setTokenStatus(reauthorize ? "INVALID" : "REFRESH_FAILED").setFailReason(safeReason);
        if (reauthorize) connection.setAuthStatus("REAUTH_REQUIRED");
        return reauthorize ? TkOpenApiException.badRequest("REAUTH_REQUIRED", safeReason)
                : TkOpenApiException.unavailable("TOKEN_REFRESH_FAILED", safeReason);
    }

    private void requireAuthorized(TkOpenTiktokConnectionDO connection) {
        if (connection == null) throw TkOpenApiException.notFound("CONNECTION_NOT_FOUND", "connection does not exist");
        if (!"AUTHORIZED".equals(connection.getAuthStatus())) {
            throw TkOpenApiException.badRequest("REAUTH_REQUIRED", "TikTok authorization is required");
        }
    }

    private boolean canReuseAccessToken(TkOpenTiktokConnectionDO connection, long aheadMinutes) {
        return StrUtil.isNotBlank(connection.getAccessTokenCipher()) && connection.getAccessTokenExpireTime() != null
                && connection.getAccessTokenExpireTime().isAfter(LocalDateTime.now().plusMinutes(aheadMinutes));
    }

    private void copyTokenState(TkOpenTiktokConnectionDO target, TkOpenTiktokConnectionDO source) {
        target.setAccessTokenCipher(source.getAccessTokenCipher()).setRefreshTokenCipher(source.getRefreshTokenCipher())
                .setAccessTokenExpireTime(source.getAccessTokenExpireTime()).setRefreshTokenExpireTime(source.getRefreshTokenExpireTime())
                .setTokenStatus(source.getTokenStatus()).setAuthStatus(source.getAuthStatus())
                .setScopes(source.getScopes()).setFailReason(source.getFailReason());
    }

    private boolean isReauthorizationRequired(String code) {
        return "invalid_grant".equals(code) || "invalid_refresh_token".equals(code)
                || "refresh_token_invalid".equals(code) || "refresh_token_expired".equals(code);
    }

    private long positiveOrDefault(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String sanitizedReason(String reason) {
        String safe = StrUtil.blankToDefault(reason, "TikTok token refresh failed")
                .replaceAll("(?i)(access_token|refresh_token)[\\\"']?\\s*[:=]\\s*[\\\"']?[^\\\"'，,\\s}]+", "$1=***");
        return StrUtil.sub(safe, 0, 512);
    }
}
