package cn.iocoder.yudao.module.tk.service.open.platform;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

@Component
public class TkOpenTiktokPlatformAdapter implements TkOpenPublishPlatformAdapter {

    private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private final TkTiktokApiClient apiClient;

    public TkOpenTiktokPlatformAdapter(TkTiktokApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public String platform() {
        return "TIKTOK";
    }

    @Override
    public boolean isConfigured() {
        return apiClient.isConfigured();
    }

    @Override
    public String buildAuthorizeUrl(String state, String redirectUri) {
        return AUTH_URL + "?client_key=" + encode(apiClient.getClientKey())
                + "&scope=" + encode(apiClient.getDefaultScopes())
                + "&response_type=code&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    @Override
    public OAuthTokenResult exchangeCode(String code, String redirectUri) {
        return parseToken(apiClient.exchangeCode(code, redirectUri, null));
    }

    @Override
    public QrCodeResult createQrCode(String state) {
        JsonNode root = apiClient.createQrCode(state);
        if (hasError(root)) {
            return new QrCodeResult(false, null, null, errorMessage(root));
        }
        return new QrCodeResult(true, root.path("token").asText(null),
                root.path("scan_qrcode_url").asText(null), null);
    }

    @Override
    public QrStatusResult checkQrCode(String token) {
        JsonNode root = apiClient.checkQrCode(token);
        if (hasError(root)) {
            return new QrStatusResult(false, "FAILED", null, errorMessage(root));
        }
        String status = root.path("status").asText("WAITING");
        String code = root.path("code").asText(null);
        if (StrUtil.isBlank(code)) {
            code = extractQueryValue(root.path("redirect_uri").asText(null), "code");
        }
        return new QrStatusResult(true, status, code, null);
    }

    @Override
    public PlatformUser queryUserInfo(String accessToken) {
        TkTiktokApiClient.UserInfo user = apiClient.queryUserInfo(accessToken);
        return new PlatformUser(user.isSuccess(), user.getOpenId(), user.getDisplayName(), user.getUsername(),
                user.getAvatarUrl(), user.getFailReason());
    }

    @Override
    public OAuthTokenResult refreshAccessToken(String refreshToken) {
        TkTiktokApiClient.TokenRefreshResult result = apiClient.refreshAccessToken(refreshToken);
        return new OAuthTokenResult(result.isSuccess(), result.getAccessToken(), result.getRefreshToken(),
                result.getAccessTokenExpiresIn(), result.getRefreshTokenExpiresIn(), result.getScopes(),
                result.getOpenId(), result.getErrorCode(), result.getFailReason());
    }

    @Override
    public CreatorCapabilities queryCreatorInfo(String accessToken) {
        TkTiktokApiClient.CreatorInfo result = apiClient.queryCreatorInfo(accessToken);
        return new CreatorCapabilities(result.isSuccess(), result.getFailReason(), result.getPrivacyLevelOptions(),
                result.isCommentDisabled(), result.isDuetDisabled(), result.isStitchDisabled(), result.getErrorCode());
    }

    @Override
    public PublishInitResult initVideoPost(String accessToken, String postMode, Map<String, Object> payload) {
        TkTiktokApiClient.PublishResult result = apiClient.initVideoPost(accessToken, postMode, payload);
        return new PublishInitResult(result.isSuccess(), result.getPublishId(), result.getUploadUrl(),
                result.getFailReason(), result.getErrorCode());
    }

    @Override
    public void uploadVideo(String uploadUrl, Path videoFile, String contentType) {
        apiClient.uploadVideoChunks(uploadUrl, videoFile, contentType);
    }

    @Override
    public PublishStatusResult fetchPostStatus(String accessToken, String publishId) {
        TkTiktokApiClient.PostStatusResult result = apiClient.fetchPostStatus(accessToken, publishId);
        return new PublishStatusResult(result.isSuccess(), result.getStatus(), result.getFailReason(), result.getErrorCode());
    }

    @Override
    public String defaultPostMode() {
        return apiClient.getDefaultPostMode();
    }

    @Override
    public String verifiedPullDomain() {
        return apiClient.getVerifiedPullDomain();
    }

    private OAuthTokenResult parseToken(JsonNode root) {
        JsonNode error = root.path("error");
        if (hasError(root)) {
            return new OAuthTokenResult(false, null, null, null, null, null, null,
                    error.isObject() ? error.path("code").asText("authorization_failed") : error.asText("authorization_failed"),
                    errorMessage(root));
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
            data = root;
        }
        String accessToken = text(data, "access_token", "accessToken");
        String openId = text(data, "open_id", "openId", "openid");
        if (StrUtil.hasBlank(accessToken, openId)) {
            return new OAuthTokenResult(false, null, null, null, null, null, null,
                    "invalid_response", "TikTok token response is incomplete");
        }
        return new OAuthTokenResult(true, accessToken, text(data, "refresh_token", "refreshToken"),
                number(data, 86400L, "expires_in", "expiresIn"),
                number(data, 31536000L, "refresh_expires_in", "refreshExpiresIn"),
                text(data, "scope", "scopes"), openId, null, null);
    }

    private boolean hasError(JsonNode root) {
        JsonNode error = root.path("error");
        return !(error.isMissingNode() || error.isNull() || (error.isTextual() && error.asText().isEmpty())
                || (error.isObject() && "ok".equalsIgnoreCase(error.path("code").asText())));
    }

    private String errorMessage(JsonNode root) {
        JsonNode error = root.path("error");
        if (error.isObject()) {
            return StrUtil.blankToDefault(error.path("message").asText(), error.path("code").asText("TikTok API error"));
        }
        return StrUtil.blankToDefault(root.path("error_description").asText(), error.asText("TikTok API error"));
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (StrUtil.isNotBlank(value)) return value;
        }
        return null;
    }

    private Long number(JsonNode node, long fallback, String... names) {
        for (String name : names) {
            if (node.hasNonNull(name)) return node.path(name).asLong(fallback);
        }
        return fallback;
    }

    private String extractQueryValue(String url, String name) {
        if (StrUtil.isBlank(url)) return null;
        String marker = name + "=";
        int start = url.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = url.indexOf('&', start);
        String value = end < 0 ? url.substring(start) : url.substring(start, end);
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
