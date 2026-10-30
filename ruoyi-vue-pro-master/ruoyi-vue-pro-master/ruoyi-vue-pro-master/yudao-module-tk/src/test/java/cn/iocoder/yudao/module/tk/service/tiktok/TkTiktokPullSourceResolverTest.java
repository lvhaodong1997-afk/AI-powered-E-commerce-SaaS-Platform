package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokPullSourceResolverTest {
    private final Map<String, String> settings = new HashMap<>();
    private final HttpURLConnection connection = mock(HttpURLConnection.class);
    private int validations;
    private int opens;
    private TkTiktokPullSourceResolver resolver;
    private TkApiKeyConfigService config;
    private static final String SOURCE = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/video/a%20b.mp4";
    @BeforeEach void setup() throws Exception {
        config = mock(TkApiKeyConfigService.class);
        when(config.getValue(anyString(), anyString())).thenAnswer(i -> settings.get(i.getArgument(1)));
        when(config.getValueOrDefault(anyString(), anyString(), anyString())).thenAnswer(i -> settings.getOrDefault(i.getArgument(1), i.getArgument(2)));
        settings.put("pull-from-url-enabled", "true");
        settings.put("pull-account-allowlist", "7");
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentLengthLong()).thenReturn(1234L);
        resolver = new TkTiktokPullSourceResolver(config, uri -> validations++, uri -> { opens++; return connection; });
    }
    @Test void mapsRawPathAndUsesBoundedHeadOnly() throws Exception {
        TkTiktokPullSourceResolver.PullDecision result = resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn");
        assertTrue(result.isEligible());
        assertEquals("https://tkpublish.fnn.net.cn/video/a%20b.mp4", result.getPullUrl());
        assertEquals(1234L, result.getFileSize());
        verify(connection).setRequestMethod("HEAD");
        verify(connection).setInstanceFollowRedirects(false);
        verify(connection).setConnectTimeout(5000);
        verify(connection).setReadTimeout(5000);
        verify(connection, never()).getInputStream();
        verify(connection).disconnect();
        assertEquals(1, validations);
    }
    @Test void candidateHasNoNetworkIo() {
        assertTrue(resolver.isCandidate(SOURCE, 7L, "https://tkpublish.fnn.net.cn/video/"));
        assertEquals(0, validations); assertEquals(0, opens);
    }
    @Test void rejectsUnsafeUrlsBeforeNetwork() {
        String[] urls = { SOURCE + "?signature=secret", SOURCE + "#fragment", SOURCE.replace("https://", "https://user@"), SOURCE.replace(".aliyuncs.com", ".aliyuncs.com.evil.com"), SOURCE.replace("/video/", "/../"), SOURCE.replace("/video/", "/%2e%2e/"), SOURCE.replace("/video/", "/a%2fb/"), SOURCE.replace("/video/", "/%252e%252e/"), SOURCE.replace(".com/", ".com:443/"), SOURCE.replace("https:", "http:") };
        for (String url : urls) assertFalse(resolver.resolve(url, 7L, "tkpublish.fnn.net.cn").isEligible(), url);
        assertEquals(0, opens);
    }
    @Test void enforcesGatesAndPropertyBoundaries() {
        assertFalse(resolver.isCandidate(SOURCE, 8L, "tkpublish.fnn.net.cn"));
        assertFalse(resolver.isCandidate(SOURCE, 7L, "https://tkpublish.fnn.net.cn/videos/"));
        assertFalse(resolver.isCandidate(SOURCE, 7L, "https://tkpublish.fnn.net.cn.evil.com/"));
        settings.put("pull-account-allowlist", "");
        assertFalse(resolver.isCandidate(SOURCE, 7L, "tkpublish.fnn.net.cn"));
        settings.put("pull-account-allowlist", "*");
        assertTrue(resolver.isCandidate(SOURCE, 8L, "fnn.net.cn"));
        settings.remove("pull-from-url-enabled");
        assertFalse(resolver.isCandidate(SOURCE, 7L, "tkpublish.fnn.net.cn"));
    }
    @Test void preservesTargetUrlAndHonorsUrlPrefix() {
        String target = "https://tkpublish.fnn.net.cn/video/a%20b.mp4";
        assertEquals(target, resolver.resolve(target, 7L, "https://tkpublish.fnn.net.cn/video/").getPullUrl());
        assertFalse(resolver.isCandidate(SOURCE, 7L, "https://tkpublish.fnn.net.cn/vide"));
    }
    @Test void preflightFailuresRemainIneligible() throws Exception {
        for (int status : new int[]{302, 403, 500}) {
            when(connection.getResponseCode()).thenReturn(status);
            assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
        }
        when(connection.getResponseCode()).thenThrow(new SocketTimeoutException());
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
    }
    @Test void rejectsMissingOrZeroLengthAndPrivateDns() throws Exception {
        when(connection.getContentLengthLong()).thenReturn(-1L, 0L);
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
        resolver = new TkTiktokPullSourceResolver(config, uri -> {throw new IllegalArgumentException("private");}, uri -> connection);
        // DNS rejection must happen before opening an HTTP connection.
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
    }
    @Test void rejectsOversizeAndNonVideoMime() throws Exception {
        when(connection.getContentLengthLong()).thenReturn(4_000_000_001L);
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
        when(connection.getContentLengthLong()).thenReturn(1234L);
        when(connection.getContentType()).thenReturn("text/html");
        assertFalse(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
        when(connection.getContentType()).thenReturn("video/mp4");
        assertTrue(resolver.resolve(SOURCE, 7L, "tkpublish.fnn.net.cn").isEligible());
    }}
