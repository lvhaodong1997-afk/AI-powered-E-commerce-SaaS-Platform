package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokApiClientTest {

    @Test
    void parseTokenRefreshExtractsRotatedTokensAndExpiry() {
        TkTiktokApiClient.TokenRefreshResult result = TkTiktokApiClient.parseTokenRefresh(JsonUtils.parseTree(
                "{\"access_token\":\"access-new\",\"expires_in\":86400,\"open_id\":\"open-1\","
                        + "\"refresh_expires_in\":31536000,\"refresh_token\":\"refresh-rotated\","
                        + "\"scope\":\"user.info.basic,video.publish\",\"token_type\":\"Bearer\"}"
        ));

        assertTrue(result.isSuccess());
        assertEquals("access-new", result.getAccessToken());
        assertEquals("refresh-rotated", result.getRefreshToken());
        assertEquals(86400L, result.getAccessTokenExpiresIn());
        assertEquals(31536000L, result.getRefreshTokenExpiresIn());
        assertEquals("user.info.basic,video.publish", result.getScopes());
        assertEquals("open-1", result.getOpenId());
        assertNull(result.getErrorCode());
        assertNull(result.getFailReason());
    }

    @Test
    void parseTokenRefreshPreservesOauthErrorCodeAndLogId() {
        TkTiktokApiClient.TokenRefreshResult result = TkTiktokApiClient.parseTokenRefresh(JsonUtils.parseTree(
                "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token expired\",\"log_id\":\"log-123\"}"
        ));

        assertFalse(result.isSuccess());
        assertEquals("invalid_grant", result.getErrorCode());
        assertEquals("invalid_grant：Refresh token expired，log_id=log-123", result.getFailReason());
        assertNull(result.getAccessToken());
    }

    @Test
    void apiResultsClassifyOnlyExactAccessTokenInvalidErrors() {
        TkTiktokApiClient.CreatorInfo creatorInfo = TkTiktokApiClient.parseCreatorInfo(JsonUtils.parseTree(
                "{\"error\":{\"code\":\"access_token_invalid\",\"message\":\"expired\",\"log_id\":\"creator-log\"}}"
        ));
        TkTiktokApiClient.PublishResult publishResult = TkTiktokApiClient.parsePublishResult(JsonUtils.parseTree(
                "{\"error\":{\"code\":\"scope_not_authorized\",\"message\":\"missing scope\",\"log_id\":\"publish-log\"}}"
        ));
        TkTiktokApiClient.PostStatusResult statusResult = TkTiktokApiClient.parsePostStatusResult(JsonUtils.parseTree(
                "{\"error\":{\"code\":\"access_token_invalid\",\"message\":\"expired\",\"log_id\":\"status-log\"}}"
        ));

        assertEquals("access_token_invalid", creatorInfo.getErrorCode());
        assertTrue(creatorInfo.isAccessTokenInvalid());
        assertEquals("access_token_invalid：expired，log_id=creator-log", creatorInfo.getFailReason());
        assertEquals("scope_not_authorized", publishResult.getErrorCode());
        assertFalse(publishResult.isAccessTokenInvalid());
        assertEquals("access_token_invalid", statusResult.getErrorCode());
        assertTrue(statusResult.isAccessTokenInvalid());
    }

    @Test
    void parseUserInfoExtractsBasicProfile() {
        TkTiktokApiClient.UserInfo userInfo = TkTiktokApiClient.parseUserInfo(JsonUtils.parseTree(
                "{\"data\":{\"user\":{\"open_id\":\"open-1\",\"union_id\":\"union-1\",\"display_name\":\"Shop Main\",\"username\":\"shop_main\",\"avatar_url\":\"https://cdn.example/avatar.png\"}},\"error\":{\"code\":\"ok\"}}"
        ));

        assertTrue(userInfo.isSuccess());
        assertEquals("open-1", userInfo.getOpenId());
        assertEquals("union-1", userInfo.getUnionId());
        assertEquals("Shop Main", userInfo.getDisplayName());
        assertEquals("shop_main", userInfo.getUsername());
        assertEquals("https://cdn.example/avatar.png", userInfo.getAvatarUrl());
    }

    @Test
    void parseUserInfoReturnsFailureOnApiError() {
        TkTiktokApiClient.UserInfo userInfo = TkTiktokApiClient.parseUserInfo(JsonUtils.parseTree(
                "{\"error\":{\"code\":\"access_token_invalid\",\"message\":\"token invalid\",\"log_id\":\"abc\"}}"
        ));

        assertEquals(false, userInfo.isSuccess());
        assertEquals("access_token_invalid：token invalid，log_id=abc", userInfo.getFailReason());
        assertNull(userInfo.getDisplayName());
    }

    @Test
    void parsePostStatusExtractsPublicPostIds() {
        TkTiktokApiClient.PostStatusResult result = TkTiktokApiClient.parsePostStatusResult(JsonUtils.parseTree(
                "{\"data\":{\"status\":\"PUBLISH_COMPLETE\","
                        + "\"publicaly_available_post_id\":[1234123412345678567,1010102020203030303]},"
                        + "\"error\":{\"code\":\"ok\"}}"
        ));

        assertTrue(result.isSuccess());
        assertEquals("PUBLISH_COMPLETE", result.getStatus());
        assertEquals(Arrays.asList("1234123412345678567", "1010102020203030303"),
                result.getPublicPostIds());
    }

    @Test
    void parseVideoQueryExtractsFirstAvailableShareUrl() {
        TkTiktokApiClient.VideoQueryResult result = TkTiktokApiClient.parseVideoQueryResult(JsonUtils.parseTree(
                "{\"data\":{\"videos\":["
                        + "{\"id\":\"123\",\"share_url\":\"https://www.tiktok.com/@demo/video/123\"}]},"
                        + "\"error\":{\"code\":\"ok\"}}"
        ));

        assertTrue(result.isSuccess());
        assertEquals("https://www.tiktok.com/@demo/video/123", result.getShareUrl());
    }

    @Test
    void uploadVideoChunksClassifies403AsExpiredUploadUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> exchange.sendResponseHeaders(403, -1));
        server.start();
        try {
            TkTiktokApiClient.UploadException error = org.junit.jupiter.api.Assertions.assertThrows(
                    TkTiktokApiClient.UploadException.class,
                    () -> new TkTiktokApiClient().uploadVideoChunks(
                            "http://localhost:" + server.getAddress().getPort() + "/upload",
                            new byte[]{1, 2, 3}, 3, 1));

            assertEquals(403, error.getStatus());
            assertTrue(error.isUploadUrlExpired());
            assertTrue(error.isRecoverable());
            assertFalse(new TkTiktokApiClient.UploadException(400).isRecoverable());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileUploadStreamsMimeAndRangeAndRetriesTransientServerError() throws Exception {
        Path video = Files.createTempFile("tiktok-upload", ".mov");
        Files.write(video, new byte[]{1, 2, 3, 4});
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            assertEquals("video/quicktime", exchange.getRequestHeaders().getFirst("Content-Type"));
            assertEquals("4", exchange.getRequestHeaders().getFirst("Content-Length"));
            assertEquals("bytes 0-3/4", exchange.getRequestHeaders().getFirst("Content-Range"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            new TkTiktokApiClient().uploadVideoChunks(
                    "http://localhost:" + server.getAddress().getPort() + "/upload", video, "video/quicktime");
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
            Files.deleteIfExists(video);
        }
    }

}
