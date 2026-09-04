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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final String VIDEO_QUERY_URL = "https://open.tiktokapis.com/v2/video/query/?fields=id,share_url";
    private static final int UPLOAD_MAX_ATTEMPTS = 3;
    private static final int UPLOAD_TIMEOUT_MILLIS = 10 * 60 * 1000;

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
        return configService.getValueOrDefault(PROVIDER, "default-scopes",
                "user.info.basic,video.publish,video.upload,video.list");
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
        if (StrUtil.isNotBlank(redirectUri)) {
            form.put("redirect_uri", redirectUri);
        }
        if (StrUtil.isNotBlank(codeVerifier)) {
            form.put("code_verifier", codeVerifier);
        }
        log.info("[exchangeCode][redirectUri({}) codeLength({}) codeVerifier({})]", redirectUri,
                StrUtil.length(code), StrUtil.isNotBlank(codeVerifier));
        return postForm(TOKEN_URL, form);
    }

    public TokenRefreshResult refreshAccessToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            return TokenRefreshResult.failure("refresh_token_missing", "账号缺少 Refresh Token");
        }
        Map<String, Object> form = new HashMap<>();
        form.put("client_key", getClientKey());
        form.put("client_secret", getClientSecret());
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        return parseTokenRefresh(postForm(TOKEN_URL, form));
    }

    static TokenRefreshResult parseTokenRefresh(JsonNode root) {
        String errorCode = root.path("error").isTextual()
                ? root.path("error").asText(null) : getErrorNode(root).path("code").asText(null);
        if (StrUtil.isNotBlank(errorCode) && !"ok".equals(errorCode)) {
            String message = StrUtil.blankToDefault(root.path("error_description").asText(null),
                    getErrorNode(root).path("message").asText(null));
            String logId = StrUtil.blankToDefault(root.path("log_id").asText(null),
                    getErrorNode(root).path("log_id").asText(null));
            return TokenRefreshResult.failure(errorCode, formatApiError(errorCode, message,
                    "TikTok Token 刷新失败", logId));
        }
        JsonNode data = root.hasNonNull("access_token") ? root : root.path("data");
        String accessToken = data.path("access_token").asText(null);
        if (StrUtil.isBlank(accessToken)) {
            return TokenRefreshResult.failure("invalid_response", "TikTok Token 刷新响应缺少 Access Token");
        }
        return new TokenRefreshResult(true, accessToken, data.path("refresh_token").asText(null),
                data.path("expires_in").asLong(0L), data.path("refresh_expires_in").asLong(0L),
                data.path("scope").asText(null), data.path("open_id").asText(null), null, null);
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
            return new CreatorInfo(false, "账号缺少 Access Token", new ArrayList<>(),
                    false, false, false, null, null);
        }
        return parseCreatorInfo(postJson(CREATOR_INFO_URL, accessToken, new HashMap<>()));
    }

    static CreatorInfo parseCreatorInfo(JsonNode root) {
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new CreatorInfo(false, formatApiError(error, "TikTok creator_info 查询失败"),
                    new ArrayList<>(), false, false, false, null, error.path("code").asText(null));
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
                maxDuration, null);
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
            return new PublishResult(false, null, null, "账号缺少 Access Token", null);
        }
        return parsePublishResult(postJson("UPLOAD_TO_INBOX".equals(postMode) ? INBOX_POST_URL : DIRECT_POST_URL,
                accessToken, payload));
    }

    static PublishResult parsePublishResult(JsonNode root) {
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new PublishResult(false, null, null, formatApiError(error, "TikTok 初始化发布失败"),
                    error.path("code").asText(null));
        }
        JsonNode data = root.path("data");
        return new PublishResult(true, data.path("publish_id").asText(null),
                data.path("upload_url").asText(null), null, null);
    }

    public void uploadVideoChunks(String uploadUrl, byte[] videoBytes, int chunkSize, int totalChunkCount) {
        int total = videoBytes.length;
        int offset = 0;
        for (int chunkIndex = 0; chunkIndex < totalChunkCount; chunkIndex++) {
            int end = chunkIndex == totalChunkCount - 1 ? total - 1 : offset + chunkSize - 1;
            byte[] chunk = java.util.Arrays.copyOfRange(videoBytes, offset, end + 1);
            uploadChunkWithRetry(uploadUrl, chunk, "video/mp4", offset, total, chunkIndex, totalChunkCount);
            offset = end + 1;
        }
    }

    public void uploadVideoChunks(String uploadUrl, Path videoFile, String contentType) {
        if (videoFile == null || !Files.isRegularFile(videoFile)) {
            throw new IllegalArgumentException("TikTok 上传文件不存在");
        }
        String mimeType = normalizeVideoMimeType(contentType);
        try {
            long total = Files.size(videoFile);
            TkTiktokUploadPlanner.UploadPlan plan = TkTiktokUploadPlanner.plan(total);
            try (InputStream inputStream = Files.newInputStream(videoFile)) {
                for (int chunkIndex = 0; chunkIndex < plan.getTotalChunkCount(); chunkIndex++) {
                    int length = Math.toIntExact(plan.chunkLength(chunkIndex));
                    byte[] chunk = readChunk(inputStream, length);
                    uploadChunkWithRetry(uploadUrl, chunk, mimeType, plan.chunkOffset(chunkIndex), total,
                            chunkIndex, plan.getTotalChunkCount());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 TikTok 上传文件失败：" + ex.getMessage(), ex);
        }
    }

    private byte[] readChunk(InputStream inputStream, int length) throws IOException {
        byte[] chunk = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = inputStream.read(chunk, offset, length - offset);
            if (read < 0) {
                throw new EOFException("上传文件在分片读取过程中提前结束");
            }
            offset += read;
        }
        return chunk;
    }

    private void uploadChunkWithRetry(String uploadUrl, byte[] chunk, String contentType,
                                      long offset, long total, int chunkIndex, int totalChunkCount) {
        Throwable lastFailure = null;
        long end = offset + chunk.length - 1;
        for (int attempt = 0; attempt < UPLOAD_MAX_ATTEMPTS; attempt++) {
            try (HttpResponse response = HttpRequest.put(uploadUrl)
                    .header("Content-Type", normalizeVideoMimeType(contentType))
                    .header("Content-Length", String.valueOf(chunk.length))
                    .header("Content-Range", "bytes " + offset + "-" + end + "/" + total)
                    .body(chunk)
                    .timeout(UPLOAD_TIMEOUT_MILLIS)
                    .execute()) {
                int status = response.getStatus();
                if (isSuccessfulUploadStatus(status)) {
                    return;
                }
                UploadException error = new UploadException(status);
                if (!isRetryableUploadStatus(status)) {
                    throw error;
                }
                lastFailure = error;
            } catch (UploadException ex) {
                throw ex;
            } catch (Exception ex) {
                lastFailure = ex;
            }
            if (attempt + 1 < UPLOAD_MAX_ATTEMPTS) {
                sleepBeforeUploadRetry(attempt);
            }
        }
        throw new IllegalStateException(StrUtil.format(
                "TikTok 分片上传重试失败，分片 {}/{}，范围 bytes {}-{}",
                chunkIndex + 1, totalChunkCount, offset, end), lastFailure);
    }

    private void sleepBeforeUploadRetry(int attempt) {
        try {
            Thread.sleep(100L << attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TikTok 分片上传重试被中断", ex);
        }
    }

    private boolean isRetryableUploadStatus(int status) {
        return status >= 500 && status <= 599;
    }

    private String normalizeVideoMimeType(String contentType) {
        String mimeType = StrUtil.blankToDefault(contentType, "video/mp4").toLowerCase();
        if (!"video/mp4".equals(mimeType)
                && !"video/quicktime".equals(mimeType)
                && !"video/webm".equals(mimeType)) {
            throw new IllegalArgumentException("TikTok 不支持的视频 MIME 类型：" + contentType);
        }
        return mimeType;
    }

    public static class UploadException extends IllegalStateException {

        private final int status;

        public UploadException(int status) {
            super("TikTok 分片上传失败，HTTP " + status);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        public boolean isUploadUrlExpired() {
            return status == 403;
        }

        public boolean isRecoverable() {
            return status == 403 || status == 404 || status == 416 || isRetryableStatus(status);
        }

        private boolean isRetryableStatus(int status) {
            return status >= 500 && status <= 599;
        }
    }

    public PostStatusResult fetchPostStatus(String accessToken, String publishId) {
        if (StrUtil.isBlank(accessToken) || StrUtil.isBlank(publishId)) {
            return new PostStatusResult(false, "FAILED", "账号缺少 Access Token 或发布编号", null);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("publish_id", publishId);
        return parsePostStatusResult(postJson(STATUS_URL, accessToken, payload));
    }

    public VideoQueryResult queryVideoShareUrl(String accessToken, List<String> videoIds) {
        if (StrUtil.isBlank(accessToken) || videoIds == null || videoIds.stream().noneMatch(StrUtil::isNotBlank)) {
            return new VideoQueryResult(false, null, "账号缺少 Access Token 或公开视频编号", null);
        }
        Map<String, Object> filters = new HashMap<>();
        filters.put("video_ids", videoIds);
        Map<String, Object> payload = new HashMap<>();
        payload.put("filters", filters);
        return parseVideoQueryResult(postJson(VIDEO_QUERY_URL, accessToken, payload));
    }

    static PostStatusResult parsePostStatusResult(JsonNode root) {
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new PostStatusResult(false, "FAILED", formatApiError(error, "TikTok 状态查询失败"),
                    error.path("code").asText(null), Collections.emptyList());
        }
        JsonNode data = root.path("data");
        return new PostStatusResult(true, data.path("status").asText("PROCESSING"),
                data.path("fail_reason").asText(null), null,
                parsePublicPostIds(data.path("publicaly_available_post_id")));
    }

    static VideoQueryResult parseVideoQueryResult(JsonNode root) {
        JsonNode error = getErrorNode(root);
        if (!"ok".equals(error.path("code").asText())) {
            return new VideoQueryResult(false, null, formatApiError(error, "TikTok 视频详情查询失败"),
                    error.path("code").asText(null));
        }
        for (JsonNode video : root.path("data").path("videos")) {
            String shareUrl = video.path("share_url").asText(null);
            if (StrUtil.isNotBlank(shareUrl)) {
                return new VideoQueryResult(true, shareUrl, null, null);
            }
        }
        return new VideoQueryResult(true, null, null, null);
    }

    private static List<String> parsePublicPostIds(JsonNode idsNode) {
        List<String> ids = new ArrayList<>();
        if (idsNode.isArray()) {
            idsNode.forEach(idNode -> addPostId(ids, idNode));
        } else {
            addPostId(ids, idsNode);
        }
        return ids;
    }

    private static void addPostId(List<String> ids, JsonNode idNode) {
        if (idNode.isIntegralNumber() || idNode.isTextual()) {
            String id = StrUtil.trim(idNode.asText());
            if (StrUtil.isNotBlank(id)) {
                ids.add(id);
            }
        }
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
        return formatApiError(code, message, fallback, logId);
    }

    private static String formatApiError(String code, String message, String fallback, String logId) {
        message = StrUtil.blankToDefault(message, fallback);
        String reason = StrUtil.isBlank(code) || "ok".equals(code) ? message : code + "：" + message;
        return StrUtil.isBlank(logId) ? reason : reason + "，log_id=" + logId;
    }

    private static boolean isAccessTokenInvalid(String errorCode) {
        return "access_token_invalid".equals(errorCode);
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
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return TkTiktokApiClient.isAccessTokenInvalid(errorCode);
        }
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
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return TkTiktokApiClient.isAccessTokenInvalid(errorCode);
        }
    }

    @Data
    @AllArgsConstructor
    public static class PostStatusResult {
        private boolean success;
        private String status;
        private String failReason;
        private String errorCode;
        private List<String> publicPostIds;

        public PostStatusResult(boolean success, String status, String failReason, String errorCode) {
            this(success, status, failReason, errorCode, Collections.emptyList());
        }

        public boolean isAccessTokenInvalid() {
            return TkTiktokApiClient.isAccessTokenInvalid(errorCode);
        }
    }

    @Data
    @AllArgsConstructor
    public static class VideoQueryResult {
        private boolean success;
        private String shareUrl;
        private String failReason;
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return TkTiktokApiClient.isAccessTokenInvalid(errorCode);
        }
    }

    @Data
    @AllArgsConstructor
    public static class TokenRefreshResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private Long accessTokenExpiresIn;
        private Long refreshTokenExpiresIn;
        private String scopes;
        private String openId;
        private String errorCode;
        private String failReason;

        static TokenRefreshResult failure(String errorCode, String failReason) {
            return new TokenRefreshResult(false, null, null, null, null, null, null, errorCode, failReason);
        }
    }

}
