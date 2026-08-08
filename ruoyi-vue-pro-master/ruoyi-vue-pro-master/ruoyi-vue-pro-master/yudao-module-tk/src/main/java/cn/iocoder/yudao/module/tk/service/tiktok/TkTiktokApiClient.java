package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TkTiktokApiClient {

    public static final String PROVIDER = "TIKTOK";

    private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String QR_CODE_URL = "https://open.tiktokapis.com/v2/oauth/get_qrcode/";
    private static final String QR_CODE_STATUS_URL = "https://open.tiktokapis.com/v2/oauth/check_qrcode/";
    private static final String USER_INFO_URL = "https://open.tiktokapis.com/v2/user/info/";
    private static final String CREATOR_INFO_URL = "https://open.tiktokapis.com/v2/post/publish/creator_info/query/";
    private static final String DIRECT_POST_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";
    private static final String INBOX_POST_URL = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/";
    private static final String STATUS_URL = "https://open.tiktokapis.com/v2/post/publish/status/fetch/";

    @Resource
    private TkApiKeyConfigService configService;

    public boolean isConfigured() {
        return StrUtil.isAllNotBlank(getClientKey(), getClientSecret());
    }

    public String getClientKey() {
        return configService.getValue(PROVIDER, "client-key");
    }

    public String getClientSecret() {
        return configService.getValue(PROVIDER, "client-secret");
    }

    public String getRedirectUri() {
        return configService.getValue(PROVIDER, "redirect-uri");
    }

    public String getDefaultScopes() {
        return configService.getValueOrDefault(PROVIDER, "default-scopes", "user.info.basic,video.publish,video.upload");
    }

    public String getDefaultPostMode() {
        return configService.getValueOrDefault(PROVIDER, "default-post-mode", "DIRECT_POST");
    }

    public String getVerifiedPullDomain() {
        return configService.getValue(PROVIDER, "verified-pull-domain");
    }

    public JsonNode exchangeCode(String code, String redirectUri, String codeVerifier) {
        Map<String, Object> form = new HashMap<>();
        form.put("client_key", getClientKey());
        form.put("client_secret", getClientSecret());
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri);
        if (StrUtil.isNotBlank(codeVerifier)) {
            form.put("code_verifier", codeVerifier);
        }
        log.info("[exchangeCode][redirectUri({}) codeLength({}) codeVerifier({})]", redirectUri,
                StrUtil.length(code), StrUtil.isNotBlank(codeVerifier));
        return postForm(TOKEN_URL, form);
    }

    public JsonNode createQrCode(String state) {
        Map<String, Object> form = new HashMap<>();
        form.put("client_key", getClientKey());
        form.put("scope", getDefaultScopes());
        form.put("state", state);
        return postForm(QR_CODE_URL, form);
    }

    public JsonNode checkQrCode(String qrcodeToken) {
        Map<String, Object> form = new HashMap<>();
        form.put("client_key", getClientKey());
        form.put("client_secret", getClientSecret());
        form.put("token", qrcodeToken);
        return postForm(QR_CODE_STATUS_URL, form);
    }

    public CreatorInfo queryCreatorInfo(String accessToken) {
        if (StrUtil.isBlank(accessToken)) {
            return new CreatorInfo(false, "账号缺少 Access Token", new ArrayList<>(), false, false, false, null);
        }
        JsonNode root = postJson(CREATOR_INFO_URL, accessToken, new HashMap<>());
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new CreatorInfo(false, formatApiError(error, "TikTok creator_info 查询失败"),
                    new ArrayList<>(), false, false, false, null);
        }
        JsonNode data = root.path("data");
        List<String> privacyLevelOptions = new ArrayList<>();
        data.path("privacy_level_options").forEach(option -> {
            if (StrUtil.isNotBlank(option.asText())) {
                privacyLevelOptions.add(option.asText().trim());
            }
        });
        Integer maxDuration = data.path("max_video_post_duration_sec").isNumber()
                ? data.path("max_video_post_duration_sec").asInt() : null;
        return new CreatorInfo(true, null, privacyLevelOptions,
                data.path("comment_disabled").asBoolean(false),
                data.path("duet_disabled").asBoolean(false),
                data.path("stitch_disabled").asBoolean(false),
                maxDuration);
    }

    public UserInfo queryUserInfo(String accessToken) {
        if (StrUtil.isBlank(accessToken)) {
            return new UserInfo(false, "账号缺少 Access Token", null, null, null, null, null);
        }
        try {
            JsonNode root = getJson(USER_INFO_URL
                    + "?fields=open_id,union_id,avatar_url,display_name", accessToken);
            return parseUserInfo(root);
        } catch (Exception ex) {
            return new UserInfo(false, "TikTok user_info 查询失败：" + ex.getMessage(),
                    null, null, null, null, null);
        }
    }

    static UserInfo parseUserInfo(JsonNode root) {
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new UserInfo(false, formatApiError(error, "TikTok user_info 查询失败"),
                    null, null, null, null, null);
        }
        JsonNode user = root.path("data").path("user");
        return new UserInfo(true, null,
                user.path("open_id").asText(null),
                user.path("union_id").asText(null),
                user.path("display_name").asText(null),
                user.path("username").asText(null),
                user.path("avatar_url").asText(null));
    }

    public PublishResult initVideoPost(String accessToken, String postMode, Map<String, Object> payload) {
        if (StrUtil.isBlank(accessToken)) {
            return new PublishResult(false, null, null, "账号缺少 Access Token");
        }
        JsonNode root = postJson("UPLOAD_TO_INBOX".equals(postMode) ? INBOX_POST_URL : DIRECT_POST_URL, accessToken, payload);
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new PublishResult(false, null, null, formatApiError(error, "TikTok 初始化发布失败"));
        }
        JsonNode data = root.path("data");
        return new PublishResult(true, data.path("publish_id").asText(null),
                data.path("upload_url").asText(null), null);
    }

    public void uploadVideoChunks(String uploadUrl, byte[] videoBytes, int chunkSize, int totalChunkCount) {
        int total = videoBytes.length;
        int offset = 0;
        for (int chunkIndex = 0; chunkIndex < totalChunkCount; chunkIndex++) {
            int end = chunkIndex == totalChunkCount - 1 ? total - 1 : offset + chunkSize - 1;
            byte[] chunk = java.util.Arrays.copyOfRange(videoBytes, offset, end + 1);
            try (HttpResponse response = HttpRequest.put(uploadUrl)
                    .header("Content-Type", "video/mp4")
                    .header("Content-Length", String.valueOf(chunk.length))
                    .header("Content-Range", "bytes " + offset + "-" + end + "/" + total)
                    .body(chunk)
                    .timeout(120_000)
                    .execute()) {
                if (!isSuccessfulUploadStatus(response.getStatus())) {
                    throw new IllegalStateException("TikTok 分片上传失败，HTTP " + response.getStatus());
                }
            }
            offset = end + 1;
        }
    }

    public PostStatusResult fetchPostStatus(String accessToken, String publishId) {
        if (StrUtil.isBlank(accessToken) || StrUtil.isBlank(publishId)) {
            return new PostStatusResult(false, "FAILED", "账号缺少 Access Token 或发布编号");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("publish_id", publishId);
        JsonNode root = postJson(STATUS_URL, accessToken, payload);
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new PostStatusResult(false, "FAILED", formatApiError(error, "TikTok 状态查询失败"));
        }
        JsonNode data = root.path("data");
        return new PostStatusResult(true, data.path("status").asText("PROCESSING"),
                data.path("fail_reason").asText(null));
    }

    private JsonNode postForm(String url, Map<String, Object> form) {
        String body = form.entrySet().stream()
                .map(entry -> encodeForm(entry.getKey()) + "=" + encodeForm(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .timeout(30_000)
                .execute()) {
            return JsonUtils.parseTree(response.body());
        }
    }

    private String encodeForm(String value) {
        return URLEncoder.encode(StrUtil.blankToDefault(value, ""), StandardCharsets.UTF_8);
    }

    private JsonNode postJson(String url, String accessToken, Map<String, Object> payload) {
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(JsonUtils.toJsonString(payload))
                .timeout(30_000)
                .execute()) {
            return JsonUtils.parseTree(response.body());
        }
    }

    private JsonNode getJson(String url, String accessToken) {
        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", "Bearer " + accessToken)
                .timeout(10_000)
                .execute()) {
            return JsonUtils.parseTree(response.body());
        }
    }

    private boolean isSuccessfulUploadStatus(int status) {
        return status == 201 || status == 206;
    }

    private static JsonNode getErrorNode(JsonNode root) {
        JsonNode error = root.path("error");
        if (StrUtil.isBlank(error.path("code").asText(null))) {
            JsonNode dataError = root.path("data").path("error");
            if (StrUtil.isNotBlank(dataError.path("code").asText(null))) {
                return dataError;
            }
        }
        return error;
    }

    private static String formatApiError(JsonNode error, String fallback) {
        String code = error.path("code").asText("");
        String message = StrUtil.blankToDefault(error.path("message").asText(), fallback);
        String logId = StrUtil.blankToDefault(error.path("log_id").asText(), error.path("logid").asText());
        String reason = StrUtil.isBlank(code) || "ok".equals(code) ? message : code + "：" + message;
        return StrUtil.isBlank(logId) ? reason : reason + "，log_id=" + logId;
    }

    @Data
    @AllArgsConstructor
    public static class CreatorInfo {
        private boolean success;
        private String failReason;
        private List<String> privacyLevelOptions;
        private boolean commentDisabled;
        private boolean duetDisabled;
        private boolean stitchDisabled;
        private Integer maxVideoPostDurationSec;
    }

    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private boolean success;
        private String failReason;
        private String openId;
        private String unionId;
        private String displayName;
        private String username;
        private String avatarUrl;
    }

    @Data
    @AllArgsConstructor
    public static class PublishResult {
        private boolean success;
        private String publishId;
        private String uploadUrl;
        private String failReason;
    }

    @Data
    @AllArgsConstructor
    public static class PostStatusResult {
        private boolean success;
        private String status;
        private String failReason;
    }

}
