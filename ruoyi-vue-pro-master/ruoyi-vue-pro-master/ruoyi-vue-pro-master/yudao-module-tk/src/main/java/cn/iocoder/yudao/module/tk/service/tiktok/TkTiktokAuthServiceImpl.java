package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkTiktokAuthServiceImpl implements TkTiktokAuthService {

    private static final int SESSION_MINUTES = 15;
    private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String PROVIDER = "TIKTOK";

    @Resource
    private TkTiktokApiClient apiClient;
    @Resource
    private TkTiktokTokenCipher tokenCipher;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkTiktokAuthSessionMapper authSessionMapper;
    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkApiKeyConfigService configService;

    @Override
    public TkTiktokAuthRedirectRespVO createRedirectUrl(TkTiktokAuthRedirectReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long tenantId = resolveWritableTenantId(scope);
        Long companyId = resolveCompatibleCompanyId(reqVO.getCompanyId(), scope);
        String state = IdUtil.fastSimpleUUID();
        String redirectUri = StrUtil.blankToDefault(reqVO.getRedirectUri(), apiClient.getRedirectUri());
        boolean configured = apiClient.isConfigured() && StrUtil.isNotBlank(redirectUri);
        String authorizeUrl = configured ? buildAuthorizeUrl(state, redirectUri) : null;

        TkTiktokAuthSessionDO session = TkTiktokAuthSessionDO.builder()
                .companyId(companyId)
                .userId(scope.getUserId())
                .authType("REDIRECT")
                .state(state)
                .authorizeUrl(authorizeUrl)
                .status(configured ? "WAITING" : "CONFIG_REQUIRED")
                .failReason(configured ? null : "请先配置 TIKTOK/client-key、client-secret、redirect-uri")
                .expireTime(LocalDateTime.now().plusMinutes(SESSION_MINUTES))
                .build();
        session.setTenantId(tenantId);
        TenantUtils.execute(tenantId, () -> authSessionMapper.insert(session));

        TkTiktokAuthRedirectRespVO respVO = new TkTiktokAuthRedirectRespVO();
        respVO.setSessionId(session.getId());
        respVO.setState(state);
        respVO.setAuthorizeUrl(authorizeUrl);
        respVO.setStatus(session.getStatus());
        respVO.setFailReason(session.getFailReason());
        return respVO;
    }

    @Override
    public TkTiktokAuthCallbackResult handleCallback(String code, String state, String error, String errorDescription) {
        TkTiktokAuthSessionDO session = selectSessionByState(state);
        if (session == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            return TkTiktokAuthCallbackResult.of(false, "TikTok 授权失败：授权会话不存在或已过期，请回到视频发布中心重新发起授权");
        }
        if (StrUtil.isNotBlank(error)) {
            session.setStatus("FAILED");
            session.setFailReason(StrUtil.blankToDefault(errorDescription, error));
            authSessionMapper.updateById(session);
            return TkTiktokAuthCallbackResult.of(false, "TikTok 授权失败：" + session.getFailReason());
        }
        if (StrUtil.isBlank(code)) {
            session.setStatus("FAILED");
            session.setFailReason("TikTok 未返回授权 code");
            authSessionMapper.updateById(session);
            return TkTiktokAuthCallbackResult.of(false, "TikTok 授权失败：未返回授权 code");
        }
        try {
            TenantUtils.execute(session.getTenantId(), () -> exchangeCodeAndSaveAccount(session, code));
        } catch (Exception ex) {
            session.setStatus("FAILED");
            session.setFailReason("TikTok 授权失败：" + ex.getMessage());
            TenantUtils.execute(session.getTenantId(), () -> authSessionMapper.updateById(session));
        }
        if ("SUCCESS".equals(session.getStatus())) {
            return TkTiktokAuthCallbackResult.of(true, "TikTok 授权完成，账号列表已刷新");
        }
        return TkTiktokAuthCallbackResult.of(false, StrUtil.blankToDefault(session.getFailReason(), "TikTok 授权失败"));
    }

    @Override
    public TkTiktokQrCodeRespVO startQrCode(TkTiktokQrCodeStartReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long tenantId = resolveWritableTenantId(scope);
        Long companyId = resolveCompatibleCompanyId(reqVO.getCompanyId(), scope);
        String state = IdUtil.fastSimpleUUID();

        String clientTicket = IdUtil.fastSimpleUUID();
        String qrcodeToken = null;
        String qrcodeUrl = null;
        String status = "CONFIG_REQUIRED";
        String failReason = "请先配置 TIKTOK/client-key、client-secret、token-secret";
        if (apiClient.isConfigured()) {
            try {
                JsonNode root = apiClient.createQrCode(state);
                if (root.hasNonNull("error")) {
                    status = "FAILED";
                    failReason = "TikTok 二维码创建失败：" + tiktokError(root);
                } else {
                    qrcodeToken = root.path("token").asText(null);
                    qrcodeUrl = root.path("scan_qrcode_url").asText(null);
                    if (StrUtil.isAllNotBlank(qrcodeToken, qrcodeUrl)) {
                        qrcodeUrl = applyClientTicket(qrcodeUrl, clientTicket);
                        status = "WAITING";
                        failReason = null;
                    } else {
                        status = "FAILED";
                        failReason = "TikTok 二维码创建失败：TikTok 未返回二维码地址或 token";
                    }
                }
            } catch (Exception ex) {
                status = "FAILED";
                failReason = "TikTok 二维码创建失败：" + ex.getMessage();
            }
        }

        TkTiktokAuthSessionDO session = TkTiktokAuthSessionDO.builder()
                .companyId(companyId)
                .userId(scope.getUserId())
                .authType("QR_CODE")
                .state(state)
                .clientTicket(clientTicket)
                .qrcodeToken(qrcodeToken)
                .qrcodeUrl(qrcodeUrl)
                .status(status)
                .failReason(failReason)
                .expireTime(LocalDateTime.now().plusMinutes(SESSION_MINUTES))
                .build();
        session.setTenantId(tenantId);
        TenantUtils.execute(tenantId, () -> authSessionMapper.insert(session));
        return toQrCodeResp(session);
    }

    @Override
    public TkTiktokQrCodeRespVO getQrCodeStatus(String clientTicket) {
        TkTiktokAuthSessionDO session = selectSessionByClientTicket(clientTicket);
        if (session == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            throw exception(TK_TIKTOK_AUTH_SESSION_NOT_EXISTS);
        }
        if (!apiClient.isConfigured() || !isQrPollingStatus(session.getStatus()) || StrUtil.isBlank(session.getQrcodeToken())) {
            return toQrCodeResp(session);
        }
        try {
            JsonNode root = apiClient.checkQrCode(session.getQrcodeToken());
            if (root.hasNonNull("error")) {
                session.setStatus("FAILED");
                session.setFailReason("TikTok 二维码状态查询失败：" + tiktokError(root));
                authSessionMapper.updateById(session);
                return toQrCodeResp(session);
            }
            String returnedTicket = root.path("client_ticket").asText(null);
            if (StrUtil.isNotBlank(returnedTicket) && !StrUtil.equals(returnedTicket, session.getClientTicket())) {
                session.setStatus("FAILED");
                session.setFailReason("TikTok 二维码状态校验失败：client_ticket 不匹配");
                authSessionMapper.updateById(session);
                return toQrCodeResp(session);
            }
            String status = root.path("status").asText(session.getStatus());
            session.setStatus(normalizeQrStatus(status));
            String code = extractQrAuthCode(root);
            if ("confirmed".equalsIgnoreCase(status) && StrUtil.isNotBlank(code)) {
                TenantUtils.execute(session.getTenantId(), () -> exchangeCodeAndSaveAccount(session, code));
            } else {
                authSessionMapper.updateById(session);
            }
        } catch (Exception ex) {
            session.setStatus("FAILED");
            session.setFailReason("TikTok 二维码状态查询失败：" + ex.getMessage());
            authSessionMapper.updateById(session);
        }
        return toQrCodeResp(session);
    }

    private TkTiktokAuthSessionDO selectSessionByState(String state) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> authSessionMapper.selectByState(state));
        }
        return authSessionMapper.selectByState(state);
    }

    private TkTiktokAuthSessionDO selectSessionByClientTicket(String clientTicket) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> authSessionMapper.selectByClientTicket(clientTicket));
        }
        return authSessionMapper.selectByClientTicket(clientTicket);
    }

    private Long resolveWritableTenantId(TkUserScope scope) {
        if (scope.getTenantId() == null || scope.getTenantId() <= 0) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        return scope.getTenantId();
    }

    private Long resolveCompatibleCompanyId(Long requestedCompanyId, TkUserScope scope) {
        Long companyId = dataScopeService.getWritableCompanyId(requestedCompanyId);
        return companyId == null ? scope.getTenantId() : companyId;
    }

    private void exchangeCodeAndSaveAccount(TkTiktokAuthSessionDO session, String code) {
        String redirectUri = "QR_CODE".equalsIgnoreCase(session.getAuthType())
                ? null : resolveSessionRedirectUri(session);
        JsonNode root = apiClient.exchangeCode(code, redirectUri, session.getCodeVerifier());
        JsonNode error = root.path("error");
        if (isTiktokError(error)) {
            session.setStatus("FAILED");
            session.setFailReason("TikTok token 换取失败：" + tiktokError(root));
            authSessionMapper.updateById(session);
            return;
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
            data = root;
        }
        String openId = firstText(data, "open_id", "openId", "openid");
        if (StrUtil.isBlank(openId)) {
            session.setStatus("FAILED");
            session.setFailReason("TikTok token 响应缺少 open_id：" + summarizeTokenResponse(root));
            authSessionMapper.updateById(session);
            return;
        }
        TkTiktokAccountDO account = accountMapper.selectByTenantIdAndOpenId(session.getTenantId(), openId);
        if (account == null) {
            account = TkTiktokAccountDO.builder()
                    .companyId(session.getCompanyId())
                    .openId(openId)
                    .displayName("TikTok账号 " + StrUtil.subSuf(openId, Math.max(0, openId.length() - 6)))
                    .username(openId)
                    .allowComment(true)
                    .allowDuet(false)
                    .allowStitch(false)
                    .commercialContent(false)
                    .brandContent(false)
                    .aigcContent(true)
                    .status(0)
                    .build();
            account.setTenantId(session.getTenantId());
        }
        String accessToken = firstText(data, "access_token", "accessToken");
        String refreshToken = firstText(data, "refresh_token", "refreshToken");
        account.setScopes(StrUtil.blankToDefault(firstText(data, "scope", "scopes"), apiClient.getDefaultScopes()));
        account.setAccessTokenCipher(tokenCipher.encrypt(accessToken));
        account.setRefreshTokenCipher(tokenCipher.encrypt(refreshToken));
        account.setAccessTokenExpireTime(LocalDateTime.now().plusSeconds(firstLong(data, 86_400, "expires_in", "expiresIn")));
        account.setRefreshTokenExpireTime(LocalDateTime.now().plusSeconds(firstLong(data, 31_536_000, "refresh_expires_in", "refreshExpiresIn")));
        account.setTokenStatus("VALID");
        account.setAuthStatus("AUTHORIZED");
        account.setLastAuthTime(LocalDateTime.now());
        account.setFailReason(null);
        applyUserInfo(account, apiClient.queryUserInfo(accessToken));
        if (account.getId() == null) {
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }
        session.setStatus("SUCCESS");
        session.setFailReason(null);
        authSessionMapper.updateById(session);
    }

    static void applyUserInfo(TkTiktokAccountDO account, TkTiktokApiClient.UserInfo userInfo) {
        if (account == null || userInfo == null || !userInfo.isSuccess()) {
            return;
        }
        if (StrUtil.isNotBlank(userInfo.getDisplayName())
                && (StrUtil.isBlank(account.getDisplayName())
                || isGeneratedDisplayName(account.getDisplayName(), account.getOpenId()))) {
            account.setDisplayName(userInfo.getDisplayName().trim());
        }
        if (StrUtil.isNotBlank(userInfo.getUsername())) {
            account.setUsername(userInfo.getUsername().trim());
        }
        if (StrUtil.isNotBlank(userInfo.getAvatarUrl())) {
            account.setAvatarUrl(userInfo.getAvatarUrl().trim());
        }
    }

    private static boolean isGeneratedDisplayName(String displayName, String openId) {
        if (StrUtil.isBlank(displayName) || StrUtil.isBlank(openId)) {
            return false;
        }
        String suffix = StrUtil.subSuf(openId, Math.max(0, openId.length() - 6));
        return displayName.startsWith("TikTok") && displayName.contains(suffix);
    }

    private String buildAuthorizeUrl(String state, String redirectUri) {
        StringBuilder builder = new StringBuilder(AUTH_URL);
        builder.append("?client_key=").append(encode(apiClient.getClientKey()));
        builder.append("&scope=").append(encode(apiClient.getDefaultScopes()));
        builder.append("&response_type=code");
        builder.append("&redirect_uri=").append(encode(redirectUri));
        builder.append("&state=").append(encode(state));
        return builder.toString();
    }

    private TkTiktokQrCodeRespVO toQrCodeResp(TkTiktokAuthSessionDO session) {
        TkTiktokQrCodeRespVO respVO = new TkTiktokQrCodeRespVO();
        respVO.setSessionId(session.getId());
        respVO.setClientTicket(session.getClientTicket());
        respVO.setQrcodeUrl(StrUtil.blankToDefault(session.getQrcodeUrl(), session.getAuthorizeUrl()));
        respVO.setStatus(session.getStatus());
        respVO.setFailReason(session.getFailReason());
        respVO.setExpireTime(session.getExpireTime());
        return respVO;
    }

    private String applyClientTicket(String qrcodeUrl, String clientTicket) {
        String encodedTicket = encode(clientTicket);
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

    static String extractQrAuthCode(JsonNode root) {
        String code = root.path("code").asText(null);
        if (StrUtil.isNotBlank(code)) {
            return java.net.URLDecoder.decode(code, StandardCharsets.UTF_8);
        }
        String redirectUri = root.path("redirect_uri").asText(null);
        if (StrUtil.isBlank(redirectUri)) {
            return null;
        }
        String marker = "code=";
        int index = redirectUri.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = redirectUri.indexOf('&', start);
        String encodedCode = end < 0 ? redirectUri.substring(start) : redirectUri.substring(start, end);
        return java.net.URLDecoder.decode(encodedCode, StandardCharsets.UTF_8);
    }

    private String resolveSessionRedirectUri(TkTiktokAuthSessionDO session) {
        String redirectUri = extractAuthorizeUrlParam(session.getAuthorizeUrl(), "redirect_uri");
        if (StrUtil.isNotBlank(redirectUri)) {
            return redirectUri;
        }
        return StrUtil.blankToDefault(configService.getValue(PROVIDER, "redirect-uri"), apiClient.getRedirectUri());
    }

    private String extractAuthorizeUrlParam(String authorizeUrl, String name) {
        if (StrUtil.isBlank(authorizeUrl)) {
            return null;
        }
        String marker = name + "=";
        int index = authorizeUrl.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int start = index + marker.length();
        int end = authorizeUrl.indexOf('&', start);
        String encodedValue = end < 0 ? authorizeUrl.substring(start) : authorizeUrl.substring(start, end);
        return java.net.URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
    }

    private String normalizeQrStatus(String status) {
        if ("confirmed".equalsIgnoreCase(status)) {
            return "SUCCESS";
        }
        if ("expired".equalsIgnoreCase(status)) {
            return "FAILED";
        }
        return StrUtil.blankToDefault(status, "WAITING").toUpperCase();
    }

    private boolean isQrPollingStatus(String status) {
        return "WAITING".equalsIgnoreCase(status)
                || "NEW".equalsIgnoreCase(status)
                || "SCANNED".equalsIgnoreCase(status);
    }

    private String tiktokError(JsonNode root) {
        String error = root.path("error").asText("unknown_error");
        JsonNode nestedError = root.path("error");
        if (nestedError.isObject()) {
            error = nestedError.path("code").asText("unknown_error");
        }
        String description = root.path("error_description").asText(null);
        if (StrUtil.isBlank(description) && nestedError.isObject()) {
            description = nestedError.path("message").asText(null);
        }
        return StrUtil.isBlank(description) ? error : error + " - " + description;
    }

    private boolean isTiktokError(JsonNode error) {
        if (error == null || error.isMissingNode() || error.isNull()) {
            return false;
        }
        if (error.isObject()) {
            String code = error.path("code").asText("ok");
            return !"ok".equalsIgnoreCase(code);
        }
        return StrUtil.isNotBlank(error.asText(null));
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText(null);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private long firstLong(JsonNode node, long defaultValue, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (StrUtil.isNotBlank(value.asText(null))) {
                return value.asLong(defaultValue);
            }
        }
        return defaultValue;
    }

    private String summarizeTokenResponse(JsonNode root) {
        String body = root == null ? "" : root.toString();
        body = body.replaceAll("\"access_token\"\\s*:\\s*\"[^\"]*\"", "\"access_token\":\"***\"");
        body = body.replaceAll("\"refresh_token\"\\s*:\\s*\"[^\"]*\"", "\"refresh_token\":\"***\"");
        body = body.replaceAll("\"accessToken\"\\s*:\\s*\"[^\"]*\"", "\"accessToken\":\"***\"");
        body = body.replaceAll("\"refreshToken\"\\s*:\\s*\"[^\"]*\"", "\"refreshToken\":\"***\"");
        return body.length() > 240 ? body.substring(0, 240) : body;
    }

    private String encode(String value) {
        return URLEncoder.encode(StrUtil.blankToDefault(value, ""), StandardCharsets.UTF_8);
    }

}
