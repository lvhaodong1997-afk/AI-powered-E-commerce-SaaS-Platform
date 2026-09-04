package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TkOpenTiktokAuthService {

    private static final int SESSION_MINUTES = 15;
    private final TkOpenTiktokAuthSessionMapper sessionMapper;
    private final TkOpenTiktokConnectionMapper connectionMapper;
    private final TkOpenPublishPlatformRegistry platformRegistry;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkOpenApiCallbackService callbackService;
    private final String redirectUri;

    public TkOpenTiktokAuthService(TkOpenTiktokAuthSessionMapper sessionMapper,
                                   TkOpenTiktokConnectionMapper connectionMapper,
                                   TkOpenPublishPlatformRegistry platformRegistry,
                                   TkOpenApiSecretCipher secretCipher,
                                   TkOpenApiCallbackService callbackService,
                                   @Value("${tk.open-api.tiktok-redirect-uri:https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback}")
                                   String redirectUri) {
        this.sessionMapper = sessionMapper;
        this.connectionMapper = connectionMapper;
        this.platformRegistry = platformRegistry;
        this.secretCipher = secretCipher;
        this.callbackService = callbackService;
        this.redirectUri = redirectUri;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokAuthVO.SessionResp createSession(TkOpenTiktokAuthVO.SessionCreateReq request) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        TkOpenPublishPlatformAdapter adapter = platform();
        if (!adapter.isConfigured()) {
            throw TkOpenApiException.unavailable("TIKTOK_CONFIG_REQUIRED", "TikTok application is not configured");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        String authSessionId = TkOpenApiIds.next("auth");
        String authorizeUrl = null;
        String qrToken = null;
        String qrUrl = null;
        if ("QR_CODE".equals(request.getAuthMode())) {
            TkOpenPublishPlatformAdapter.QrCodeResult result = adapter.createQrCode(state);
            if (!result.isSuccess() || StrUtil.hasBlank(result.getToken(), result.getUrl())) {
                throw TkOpenApiException.unavailable("AUTHORIZATION_FAILED",
                        StrUtil.blankToDefault(result.getFailReason(), "TikTok QR authorization failed"));
            }
            qrToken = result.getToken();
            qrUrl = result.getUrl();
        } else {
            authorizeUrl = adapter.buildAuthorizeUrl(state, redirectUri);
        }
        TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .authSessionId(authSessionId)
                .clientId(clientId)
                .externalAccountId(request.getExternalAccountId())
                .clientState(request.getClientState())
                .authMode(request.getAuthMode())
                .oauthState(state)
                .qrcodeToken(qrToken)
                .qrcodeUrl(qrUrl)
                .authorizeUrl(authorizeUrl)
                .status("WAITING")
                .expireTime(LocalDateTime.now().plusMinutes(SESSION_MINUTES))
                .build();
        sessionMapper.insert(session);
        return toSessionResp(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokAuthVO.SessionStatusResp getSession(String authSessionId) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        TkOpenTiktokAuthSessionDO session = requireSessionForUpdate(clientId, authSessionId);
        expireIfNeeded(session);
        if ("QR_CODE".equals(session.getAuthMode()) && "WAITING".equals(session.getStatus())
                && StrUtil.isNotBlank(session.getQrcodeToken())) {
            pollQrSession(session);
        }
        return toStatusResp(session);
    }

    public List<TkOpenTiktokAuthVO.ConnectionResp> getConnections(String externalAccountId, String status) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        List<TkOpenTiktokAuthVO.ConnectionResp> result = new ArrayList<>();
        for (TkOpenTiktokConnectionDO connection : connectionMapper.selectListByClient(clientId, externalAccountId, status)) {
            result.add(toConnectionResp(connection));
        }
        return result;
    }

    public void disconnect(String connectionId) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(clientId, connectionId);
        if (connection == null) {
            throw TkOpenApiException.notFound("CONNECTION_NOT_FOUND", "connection does not exist");
        }
        connectionMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokConnectionDO.class)
                .eq(TkOpenTiktokConnectionDO::getId, connection.getId())
                .set(TkOpenTiktokConnectionDO::getAuthStatus, "DISCONNECTED")
                .set(TkOpenTiktokConnectionDO::getTokenStatus, "REVOKED")
                .set(TkOpenTiktokConnectionDO::getAccessTokenCipher, "")
                .set(TkOpenTiktokConnectionDO::getRefreshTokenCipher, null));
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokAuthCallbackResult handleCallback(String code, String state, String error, String description) {
        if (StrUtil.isBlank(state)) {
            return TkOpenTiktokAuthCallbackResult.of(false, "OAuth state is missing");
        }
        TkOpenTiktokAuthSessionDO session = sessionMapper.selectByOauthStateForUpdate(state);
        if (session == null) {
            return TkOpenTiktokAuthCallbackResult.of(false, "Authorization session does not exist or has expired");
        }
        if ("SUCCESS".equals(session.getStatus())) {
            return TkOpenTiktokAuthCallbackResult.of(true, "TikTok authorization completed");
        }
        if (!"WAITING".equals(session.getStatus())) {
            return TkOpenTiktokAuthCallbackResult.of(false,
                    StrUtil.blankToDefault(session.getFailReason(), "Authorization session is no longer active"));
        }
        if (session.getExpireTime().isBefore(LocalDateTime.now())) {
            return TkOpenTiktokAuthCallbackResult.of(false, "Authorization session does not exist or has expired");
        }
        if (StrUtil.isNotBlank(error) || StrUtil.isBlank(code)) {
            failSession(session, StrUtil.blankToDefault(description, StrUtil.blankToDefault(error, "TikTok did not return code")));
            return TkOpenTiktokAuthCallbackResult.of(false, session.getFailReason());
        }
        return completeAuthorization(session, code);
    }

    private void pollQrSession(TkOpenTiktokAuthSessionDO session) {
        TkOpenPublishPlatformAdapter.QrStatusResult result = platform().checkQrCode(session.getQrcodeToken());
        if (!result.isSuccess()) {
            failSession(session, result.getFailReason());
            return;
        }
        if ("confirmed".equalsIgnoreCase(result.getStatus()) && StrUtil.isNotBlank(result.getAuthorizationCode())) {
            completeAuthorization(session, result.getAuthorizationCode());
        } else if ("expired".equalsIgnoreCase(result.getStatus())) {
            session.setStatus("EXPIRED");
            session.setFailReason("QR authorization has expired");
            sessionMapper.updateById(session);
        }
    }

    private TkOpenTiktokAuthCallbackResult completeAuthorization(TkOpenTiktokAuthSessionDO session, String code) {
        try {
            TkOpenPublishPlatformAdapter.OAuthTokenResult token = platform().exchangeCode(code,
                    "QR_CODE".equals(session.getAuthMode()) ? null : redirectUri);
            if (!token.isSuccess()) {
                failSession(session, token.getFailReason());
                return TkOpenTiktokAuthCallbackResult.of(false, session.getFailReason());
            }
            TkOpenPublishPlatformAdapter.PlatformUser user = platform().queryUserInfo(token.getAccessToken());
            TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndExternalAccountId(
                    session.getClientId(), session.getExternalAccountId());
            if (connection == null) {
                connection = TkOpenTiktokConnectionDO.builder()
                        .connectionId(TkOpenApiIds.next("conn"))
                        .clientId(session.getClientId())
                        .externalAccountId(session.getExternalAccountId())
                        .build();
            }
            connection.setOpenId(token.getOpenId());
            connection.setDisplayName(user.isSuccess() ? user.getDisplayName() : token.getOpenId());
            connection.setUsername(user.isSuccess() ? user.getUsername() : token.getOpenId());
            connection.setAvatarUrl(user.isSuccess() ? user.getAvatarUrl() : null);
            connection.setScopes(token.getScopes());
            connection.setAccessTokenCipher(secretCipher.encrypt(token.getAccessToken()));
            connection.setRefreshTokenCipher(secretCipher.encrypt(token.getRefreshToken()));
            connection.setAccessTokenExpireTime(LocalDateTime.now().plusSeconds(defaultLong(token.getAccessTokenExpiresIn(), 86400L)));
            connection.setRefreshTokenExpireTime(LocalDateTime.now().plusSeconds(defaultLong(token.getRefreshTokenExpiresIn(), 31536000L)));
            connection.setTokenStatus("NORMAL");
            connection.setAuthStatus("AUTHORIZED");
            connection.setLastAuthTime(LocalDateTime.now());
            connection.setFailReason(null);
            if (connection.getId() == null) connectionMapper.insert(connection); else connectionMapper.updateById(connection);
            session.setStatus("SUCCESS");
            session.setConnectionId(connection.getConnectionId());
            session.setAccountName(connection.getDisplayName());
            session.setFailReason(null);
            sessionMapper.updateById(session);
            callbackService.enqueue(session.getClientId(), "authorization.completed", "CONNECTION",
                    connection.getConnectionId(), authPayload(session, connection, "AUTHORIZED"));
            return TkOpenTiktokAuthCallbackResult.of(true, "TikTok authorization completed");
        } catch (Exception ex) {
            failSession(session, "TikTok authorization failed: " + ex.getMessage());
            return TkOpenTiktokAuthCallbackResult.of(false, session.getFailReason());
        }
    }

    private void failSession(TkOpenTiktokAuthSessionDO session, String reason) {
        session.setStatus("FAILED");
        session.setFailReason(StrUtil.maxLength(StrUtil.blankToDefault(reason, "Authorization failed"), 1000));
        sessionMapper.updateById(session);
        callbackService.enqueue(session.getClientId(), "authorization.failed", "AUTH_SESSION",
                session.getAuthSessionId(), authPayload(session, null, "FAILED"));
    }

    private Map<String, Object> authPayload(TkOpenTiktokAuthSessionDO session,
                                            TkOpenTiktokConnectionDO connection, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authSessionId", session.getAuthSessionId());
        payload.put("connectionId", connection == null ? null : connection.getConnectionId());
        payload.put("externalAccountId", session.getExternalAccountId());
        payload.put("accountName", connection == null ? null : connection.getDisplayName());
        payload.put("status", status);
        payload.put("failReason", session.getFailReason());
        payload.put("clientState", session.getClientState());
        return payload;
    }

    private TkOpenTiktokAuthSessionDO requireSessionForUpdate(String clientId, String sessionId) {
        TkOpenTiktokAuthSessionDO session = sessionMapper.selectByClientAndSessionIdForUpdate(clientId, sessionId);
        if (session == null) {
            throw TkOpenApiException.notFound("AUTH_SESSION_NOT_FOUND", "authorization session does not exist");
        }
        return session;
    }

    private void expireIfNeeded(TkOpenTiktokAuthSessionDO session) {
        if ("WAITING".equals(session.getStatus()) && session.getExpireTime().isBefore(LocalDateTime.now())) {
            session.setStatus("EXPIRED");
            session.setFailReason("Authorization session has expired");
            sessionMapper.updateById(session);
        }
    }

    private TkOpenPublishPlatformAdapter platform() {
        return platformRegistry.getRequired("TIKTOK");
    }

    private long defaultLong(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private TkOpenTiktokAuthVO.SessionResp toSessionResp(TkOpenTiktokAuthSessionDO session) {
        TkOpenTiktokAuthVO.SessionResp response = new TkOpenTiktokAuthVO.SessionResp();
        response.setAuthSessionId(session.getAuthSessionId());
        response.setExternalAccountId(session.getExternalAccountId());
        response.setClientState(session.getClientState());
        response.setAuthMode(session.getAuthMode());
        response.setAuthorizeUrl(session.getAuthorizeUrl());
        response.setQrcodeUrl(session.getQrcodeUrl());
        response.setStatus(session.getStatus());
        response.setExpireTime(session.getExpireTime());
        return response;
    }

    private TkOpenTiktokAuthVO.SessionStatusResp toStatusResp(TkOpenTiktokAuthSessionDO session) {
        TkOpenTiktokAuthVO.SessionStatusResp response = new TkOpenTiktokAuthVO.SessionStatusResp();
        response.setAuthSessionId(session.getAuthSessionId());
        response.setExternalAccountId(session.getExternalAccountId());
        response.setClientState(session.getClientState());
        response.setConnectionId(session.getConnectionId());
        response.setAccountName(session.getAccountName());
        response.setStatus(session.getStatus());
        response.setFailReason(session.getFailReason());
        response.setExpireTime(session.getExpireTime());
        return response;
    }

    private TkOpenTiktokAuthVO.ConnectionResp toConnectionResp(TkOpenTiktokConnectionDO connection) {
        TkOpenTiktokAuthVO.ConnectionResp response = new TkOpenTiktokAuthVO.ConnectionResp();
        response.setConnectionId(connection.getConnectionId());
        response.setExternalAccountId(connection.getExternalAccountId());
        response.setAccountName(connection.getDisplayName());
        response.setUsername(connection.getUsername());
        response.setAvatarUrl(connection.getAvatarUrl());
        response.setAuthStatus(connection.getAuthStatus());
        response.setTokenStatus(connection.getTokenStatus());
        response.setLastAuthTime(connection.getLastAuthTime());
        return response;
    }
}
