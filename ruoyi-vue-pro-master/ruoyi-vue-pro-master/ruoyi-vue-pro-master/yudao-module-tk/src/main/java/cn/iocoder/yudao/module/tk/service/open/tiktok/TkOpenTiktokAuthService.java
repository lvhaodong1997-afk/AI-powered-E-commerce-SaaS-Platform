package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
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
import java.nio.charset.StandardCharsets;
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
    private final String launchBaseUrl;

    public TkOpenTiktokAuthService(TkOpenTiktokAuthSessionMapper sessionMapper,
                                   TkOpenTiktokConnectionMapper connectionMapper,
                                   TkOpenPublishPlatformRegistry platformRegistry,
                                   TkOpenApiSecretCipher secretCipher,
                                   TkOpenApiCallbackService callbackService,
                                   @Value("${tk.open-api.tiktok-redirect-uri:https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback}")
                                   String redirectUri,
                                   @Value("${tk.open-api.tiktok-launch-base-url:https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions}")
                                   String launchBaseUrl) {
        this.sessionMapper = sessionMapper;
        this.connectionMapper = connectionMapper;
        this.platformRegistry = platformRegistry;
        this.secretCipher = secretCipher;
        this.callbackService = callbackService;
        this.redirectUri = redirectUri;
        this.launchBaseUrl = StrUtil.removeSuffix(launchBaseUrl, "/");
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
        String clientTicket = null;
        if ("REDIRECT".equals(request.getAuthMode()) || "AUTO".equals(request.getAuthMode())) {
            authorizeUrl = adapter.buildAuthorizeUrl(state, redirectUri);
        }
        if ("QR_CODE".equals(request.getAuthMode()) || "AUTO".equals(request.getAuthMode())) {
            clientTicket = TkOpenApiIds.next("ticket");
            TkOpenPublishPlatformAdapter.QrCodeResult result;
            try {
                result = adapter.createQrCode(state);
            } catch (Exception ex) {
                result = new TkOpenPublishPlatformAdapter.QrCodeResult(false, null, null, ex.getMessage());
            }
            if (result.isSuccess() && StrUtil.isNotBlank(result.getToken()) && StrUtil.isNotBlank(result.getUrl())) {
                qrToken = result.getToken();
                qrUrl = applyClientTicket(result.getUrl(), clientTicket);
            } else if ("QR_CODE".equals(request.getAuthMode())) {
                throw TkOpenApiException.unavailable("AUTHORIZATION_FAILED",
                        StrUtil.blankToDefault(result.getFailReason(), "TikTok QR authorization failed"));
            }
        }
        TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .authSessionId(authSessionId)
                .clientId(clientId)
                .externalAccountId(request.getExternalAccountId())
                .clientState(request.getClientState())
                .authMode(request.getAuthMode())
                .oauthState(state)
                .clientTicket(clientTicket)
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
        if (isQrMode(session.getAuthMode()) && "WAITING".equals(session.getStatus())
                && StrUtil.isNotBlank(session.getQrcodeToken())) {
            pollQrSession(session);
        }
        return toStatusResp(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokAuthVO.SessionStatusResp getPublicSessionStatus(String authSessionId) {
        TkOpenTiktokAuthSessionDO session = sessionMapper.selectByAuthSessionIdForUpdate(authSessionId);
        if (session == null) {
            throw TkOpenApiException.notFound("AUTH_SESSION_NOT_FOUND", "authorization session does not exist");
        }
        expireIfNeeded(session);
        if (isQrMode(session.getAuthMode()) && "WAITING".equals(session.getStatus())
                && StrUtil.isNotBlank(session.getQrcodeToken())) {
            pollQrSession(session);
        }
        return toStatusResp(session);
    }

    public String renderLaunchPage(String authSessionId) {
        TkOpenTiktokAuthSessionDO session = sessionMapper.selectByAuthSessionId(authSessionId);
        if (session == null) {
            return renderPage("TikTok authorization unavailable", "Authorization session does not exist or has expired", null,
                    null, null);
        }
        String imageUrl = null;
        if (StrUtil.isNotBlank(session.getQrcodeUrl())) {
            try {
                imageUrl = buildQrCodeImageUrl(session.getQrcodeUrl());
            } catch (Exception ex) {
                imageUrl = null;
            }
        }
        String statusUrl = launchBaseUrl + "/" + session.getAuthSessionId() + "/launch/status";
        return renderPage("TikTok authorization", "WAITING".equals(session.getStatus())
                        ? "Complete authorization in the browser or scan the QR code."
                        : session.getStatus(), session.getAuthorizeUrl(), imageUrl, statusUrl);
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
        return completeAuthorization(session, code, false);
    }

    private void pollQrSession(TkOpenTiktokAuthSessionDO session) {
        TkOpenPublishPlatformAdapter.QrStatusResult result = platform().checkQrCode(session.getQrcodeToken());
        if (!result.isSuccess()) {
            failSession(session, result.getFailReason());
            return;
        }
        if (StrUtil.isNotBlank(result.getClientTicket())
                && !StrUtil.equals(result.getClientTicket(), session.getClientTicket())) {
            failSession(session, "TikTok QR authorization failed: client_ticket does not match");
            return;
        }
        if ("confirmed".equalsIgnoreCase(result.getStatus()) && StrUtil.isNotBlank(result.getAuthorizationCode())) {
            completeAuthorization(session, result.getAuthorizationCode(), true);
        } else if ("expired".equalsIgnoreCase(result.getStatus())) {
            session.setStatus("EXPIRED");
            session.setFailReason("QR authorization has expired");
            sessionMapper.updateById(session);
        }
    }

    private TkOpenTiktokAuthCallbackResult completeAuthorization(TkOpenTiktokAuthSessionDO session, String code,
                                                                  boolean fromQrCode) {
        try {
            TkOpenPublishPlatformAdapter.OAuthTokenResult token = platform().exchangeCode(code,
                    fromQrCode ? null : redirectUri);
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
        response.setQrcodeImageUrl(StrUtil.isBlank(session.getQrcodeUrl())
                ? null : buildQrCodeImageUrl(session.getQrcodeUrl()));
        response.setLaunchUrl(launchBaseUrl + "/" + session.getAuthSessionId() + "/launch");
        response.setStatus(session.getStatus());
        response.setExpireTime(session.getExpireTime());
        return response;
    }

    private boolean isQrMode(String authMode) {
        return "QR_CODE".equals(authMode) || "AUTO".equals(authMode);
    }

    private String buildQrCodeImageUrl(String qrcodeUrl) {
        byte[] png = QrCodeUtil.generatePng(qrcodeUrl, 320, 320);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private String applyClientTicket(String qrcodeUrl, String clientTicket) {
        String encodedTicket = java.net.URLEncoder.encode(clientTicket, StandardCharsets.UTF_8).replace("+", "%20");
        int index = qrcodeUrl.indexOf("client_ticket=");
        if (index < 0) {
            return qrcodeUrl + (qrcodeUrl.contains("?") ? "&" : "?") + "client_ticket=" + encodedTicket;
        }
        int valueStart = index + "client_ticket=".length();
        int valueEnd = qrcodeUrl.indexOf('&', valueStart);
        if (valueEnd < 0) {
            return qrcodeUrl.substring(0, valueStart) + encodedTicket;
        }
        return qrcodeUrl.substring(0, valueStart) + encodedTicket + qrcodeUrl.substring(valueEnd);
    }

    private String renderPage(String title, String message, String authorizeUrl, String qrcodeImageUrl,
                              String statusUrl) {
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(escapeHtml(title)).append("</title></head><body><h1>")
                .append(escapeHtml(title)).append("</h1><p id=\"message\">")
                .append(escapeHtml(StrUtil.blankToDefault(message, ""))).append("</p>");
        if (StrUtil.isNotBlank(qrcodeImageUrl)) {
            html.append("<img alt=\"TikTok QR code\" width=\"320\" height=\"320\" src=\"")
                    .append(escapeHtml(qrcodeImageUrl)).append("\">");
        }
        if (StrUtil.isNotBlank(authorizeUrl)) {
            html.append("<p><a href=\"").append(escapeHtml(authorizeUrl))
                    .append("\">Continue with browser authorization</a></p>");
        }
        if (StrUtil.isNotBlank(statusUrl)) {
            html.append("<script>(async function poll(){try{const r=await fetch('")
                    .append(escapeJs(statusUrl)).append("');const j=await r.json();const d=j.data||{};"
                            + "document.getElementById('message').textContent=d.status||'WAITING';"
                            + "if(d.status==='WAITING'){setTimeout(poll,3000);}else if(d.status==='SUCCESS'){"
                            + "document.getElementById('message').textContent='Authorization completed. You may close this window.';"
                            + "}else{setTimeout(poll,3000);}}catch(e){setTimeout(poll,5000);}})();</script>");
        }
        return html.append("</body></html>").toString();
    }

    private String escapeHtml(String value) {
        return StrUtil.blankToDefault(value, "").replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeJs(String value) {
        return StrUtil.blankToDefault(value, "").replace("\\", "\\\\")
                .replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n");
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
