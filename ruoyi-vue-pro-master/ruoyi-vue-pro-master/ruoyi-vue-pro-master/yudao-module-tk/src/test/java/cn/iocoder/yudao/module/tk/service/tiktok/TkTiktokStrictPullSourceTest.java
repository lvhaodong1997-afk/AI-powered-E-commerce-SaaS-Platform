package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokStrictPullSourceTest {
    private final HttpURLConnection connection = mock(HttpURLConnection.class);
    private final AtomicReference<URI> opened = new AtomicReference<>();
    private TkTiktokPullSourceResolver resolver;
    private static final String ORIGIN = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/video/a%20b.mp4";
    private static final String PROPERTY = "tkpublish.fnn.net.cn";
    private static String signed() {
        return ORIGIN + "?OSSAccessKeyId=key&Expires=" + (Instant.now().getEpochSecond() + 7200) + "&Signature=a%2Bb";
    }
    @BeforeEach void setup() throws Exception {
        TkApiKeyConfigService config = mock(TkApiKeyConfigService.class);
        when(config.getValueOrDefault(anyString(), anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(1);
            return "pull-from-url-enabled".equals(key) || "pull-strict-enabled".equals(key) ? "true"
                    : "pull-account-allowlist".equals(key) ? "*" : call.getArgument(2);
        });
        resolver = new TkTiktokPullSourceResolver(config, uri -> {}, uri -> { opened.set(uri); return connection; });
        when(connection.getResponseCode()).thenReturn(206);
        when(connection.getHeaderField("Content-Range")).thenReturn("bytes 0-0/123456");
        when(connection.getContentLengthLong()).thenReturn(1L);
        when(connection.getContentType()).thenReturn("video/mp4");
        when(connection.getInputStream()).thenAnswer(call -> new ByteArrayInputStream(new byte[]{1}));
    }
    @Test void signedGetKeepsAuthorizationAndUsesTotalRangeSize() throws Exception {
        String raw = signed();
        TkTiktokPullSourceResolver.PullDecision result = resolver.resolveStrict(raw, 7L, PROPERTY);
        assertTrue(result.isEligible(), result.getReasonCode());
        assertEquals(raw.substring(raw.indexOf('?') + 1), opened.get().getRawQuery());
        assertEquals(PROPERTY, opened.get().getHost());
        assertEquals(123456L, result.getFileSize());
        verify(connection).setRequestMethod("GET");
        verify(connection).setRequestProperty("Range", "bytes=0-0");
        verify(connection).setInstanceFollowRedirects(false);
        verify(connection).disconnect();
    }
    @Test void ignoredRangeDoesNotReadWholeVideo() throws Exception {
        ByteArrayInputStream body = spy(new ByteArrayInputStream(new byte[65536]));
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentLengthLong()).thenReturn(65536L);
        when(connection.getInputStream()).thenReturn(body);
        assertTrue(resolver.resolveStrict(signed(), 7L, PROPERTY).isEligible());
        assertTrue(body.available() >= 65536 - 1024);
        verify(body).close();
    }
    @Test void expiredSignatureAndBoundHostCannotBeReusedAcrossHosts() {
        TkTiktokPullSourceResolver.PullDecision expired = resolver.resolveStrict(ORIGIN + "?OSSAccessKeyId=k&Expires=1&Signature=s", 7L, PROPERTY);
        assertEquals("PULL_URL_EXPIRED", expired.getReasonCode());
        String bound = ORIGIN + "?x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-credential=k"
                + "&x-oss-date=20990101T000000Z&x-oss-expires=7200&x-oss-signature=s&x-oss-additional-headers=host";
        assertEquals("SIGNED_HEADERS_UNSUPPORTED", resolver.resolveStrict(bound, 7L, PROPERTY).getReasonCode());
        assertNull(opened.get());
    }
    @Test void accessDeniedIsTerminalAndServerErrorsAreRetryable() throws Exception {
        when(connection.getResponseCode()).thenReturn(403);
        assertEquals("PULL_ACCESS_DENIED", resolver.resolveStrict(signed(), 7L, PROPERTY).getReasonCode());
        when(connection.getResponseCode()).thenReturn(503);
        assertEquals("PULL_TEMPORARILY_UNAVAILABLE", resolver.resolveStrict(signed(), 7L, PROPERTY).getReasonCode());
    }
    @Test void malformedPartialResponseIsRejected() throws Exception {
        when(connection.getHeaderField("Content-Range")).thenReturn("bytes 0-100/123456");
        assertEquals("INVALID_CONTENT_RANGE", resolver.resolveStrict(signed(), 7L, PROPERTY).getReasonCode());
    }
    private TkTiktokPullSourceResolver.PullDecision photo(String url) throws Exception {
        return (TkTiktokPullSourceResolver.PullDecision) TkTiktokPullSourceResolver.class
                .getMethod("resolvePhotoStrict", String.class, Long.class, String.class).invoke(resolver, url, 7L, PROPERTY);
    }
    @Test void photoStrictAcceptsFinalJpegWithBoundedRangeAndVideoRemainsStrict() throws Exception {
        when(connection.getContentType()).thenReturn("image/jpeg");
        assertTrue(photo(signed()).isEligible());
        assertEquals("INVALID_CONTENT_TYPE", resolver.resolveStrict(signed(),7L,PROPERTY).getReasonCode());
        verify(connection,times(2)).setRequestMethod("GET");
        verify(connection,times(2)).setRequestProperty("Range","bytes=0-0");
    }
    @Test void photoStrictRetainsSizeSignatureAndContentChecks() throws Exception {
        when(connection.getContentType()).thenReturn("image/jpeg");
        when(connection.getHeaderField("Content-Range")).thenReturn("bytes 0-0/20971521");
        assertEquals("INVALID_FILE_SIZE",photo(signed()).getReasonCode());
        when(connection.getHeaderField("Content-Range")).thenReturn("bytes 0-0/123456");
        when(connection.getContentType()).thenReturn("video/mp4");
        assertEquals("INVALID_CONTENT_TYPE",photo(signed()).getReasonCode());
        assertEquals("PULL_URL_EXPIRED",photo(ORIGIN+"?OSSAccessKeyId=k&Expires=1&Signature=s").getReasonCode());
    }
}
