package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokSignedPullSourceTest {
    private static final String ORIGINAL = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/TK/a%20b.mp4";
    private static final String TARGET = "https://tkpublish.fnn.net.cn/TK/a%20b.mp4";
    private static final String PROPERTY = "tkpublish.fnn.net.cn";
    private static final String V1 = "?OSSAccessKeyId=test-key&Expires=1&Signature=test%2Bsig%3D"
            + "&response-content-disposition=attachment%3Bfilename%3Dvideo.mp4";
    private final Map<String, String> settings = new HashMap<>();
    private final HttpURLConnection connection = mock(HttpURLConnection.class);
    private TkTiktokPullSourceResolver resolver;
    private URI requested;
    private int opens;

    @BeforeEach void setup() throws Exception {
        settings.put("pull-from-url-enabled", "true");
        settings.put("pull-account-allowlist", "7");
        TkApiKeyConfigService config = mock(TkApiKeyConfigService.class);
        when(config.getValueOrDefault(anyString(), anyString(), anyString()))
                .thenAnswer(call -> settings.getOrDefault(call.getArgument(1), call.getArgument(2)));
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentLengthLong()).thenReturn(123L);
        when(connection.getContentType()).thenReturn("video/mp4");
        resolver = new TkTiktokPullSourceResolver(config, uri -> {}, uri -> {
            requested = uri;
            opens++;
            return connection;
        });
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void signedRemoteMediaCanSelectPullWithoutNetworkAtTaskCreation() {
        assertTrue(resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
        assertEquals(0, opens);
    }

    @Test void v1SignatureAndDownloadNameAreRemovedBeforeAnonymousHead() throws Exception {
        TkTiktokPullSourceResolver.PullDecision result = resolver.resolve(ORIGINAL + V1, 7L, PROPERTY);
        assertTrue(result.isEligible());
        assertEquals(TARGET, result.getPullUrl());
        assertEquals(123L, result.getFileSize());
        assertEquals(URI.create(TARGET), requested);
        assertNull(requested.getRawQuery());
        verify(connection).setRequestMethod("HEAD");
        verify(connection).setInstanceFollowRedirects(false);
        verify(connection, never()).getInputStream();
    }

    @Test void knownV4AndStsParametersAlsoUseAnonymousObjectPath() {
        String query = "?x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-credential=test%2Fscope"
                + "&x-oss-date=20261029T120000Z&x-oss-expires=7200&x-oss-signature=test"
                + "&x-oss-additional-headers=host&x-oss-security-token=test";
        assertEquals(TARGET, resolver.resolve(ORIGINAL + query, 7L, PROPERTY).getPullUrl());
        assertEquals(TARGET, resolver.resolve(TARGET + V1 + "&security-token=test", 7L, PROPERTY).getPullUrl());
    }

    @Test void queryParametersChangingContentOrUnknownSemanticsStayOnFileUpload() {
        for (String query : new String[]{"versionId=old", "x-oss-process=video/snapshot,t_1000",
                "response-content-type=text/html", "download=1", "signature=lowercase"}) {
            TkTiktokPullSourceResolver.PullDecision result = resolver.resolve(ORIGINAL + V1 + "&" + query, 7L, PROPERTY);
            assertFalse(result.isEligible(), query);
            assertEquals("UNSUPPORTED_QUERY_PARAMETERS", result.getReasonCode());
        }
        assertEquals(0, opens);
    }

    @Test void malformedOrAmbiguousParameterNamesAreRejectedBeforeNetwork() {
        for (String query : new String[]{"?Expires=1&Expires=2", "?Signature=x&&Expires=1",
                "?Signature=x&", "?%53ignature=x", "?Signature", "?"}) {
            assertFalse(resolver.resolve(ORIGINAL + query, 7L, PROPERTY).isEligible(), query);
        }
        assertEquals(0, opens);
    }

    @Test void sourceQuerySupportDoesNotRelaxHostPathOrVerifiedConfiguration() {
        assertFalse(resolver.isCandidate(ORIGINAL.replace(".aliyuncs.com", ".aliyuncs.com.evil.com") + V1, 7L, PROPERTY));
        assertFalse(resolver.isCandidate(ORIGINAL.replace("/TK/", "/%2e%2e/") + V1, 7L, PROPERTY));
        assertFalse(resolver.isCandidate(ORIGINAL + V1 + "#fragment", 7L, PROPERTY));
        assertFalse(resolver.isCandidate(ORIGINAL + V1, 8L, PROPERTY));
        assertFalse(resolver.isCandidate(ORIGINAL + V1, 7L, "https://tkpublish.fnn.net.cn/TK/?Signature=x"));
        settings.put("pull-base-url", "https://tkpublish.fnn.net.cn?Signature=x");
        assertFalse(resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
        assertEquals(0, opens);
    }

    @Test void rotatingSignaturesReuseOnlyTheCanonicalAnonymousMetadata() {
        TenantContextHolder.setTenantId(1L);
        assertTrue(resolver.resolve(ORIGINAL + V1, 7L, PROPERTY).isEligible());
        assertTrue(resolver.resolve(ORIGINAL + V1.replace("test%2Bsig%3D", "next-signature"), 7L, PROPERTY).isEligible());
        assertFalse(resolver.resolve(ORIGINAL + V1 + "&versionId=old", 7L, PROPERTY).isEligible());
        assertEquals(1, opens);
    }

    @Test void openApiUsesRealSignedSourceResolutionWithoutDownloading() {
        TkOpenTiktokPublishService service = openService();
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        try {
            String original = ORIGINAL + V1;
            boolean preferPull = resolver.isCandidate(original, 7L, PROPERTY);
            assertTrue(preferPull);
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource",
                    TkOpenTiktokMediaDO.builder().fileUrl(original).uploadMode("REMOTE").fileSize(123L).build(),
                    PROPERTY, 7L, preferPull);
            assertEquals(true, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(TARGET, ReflectionTestUtils.getField(source, "pullUrl"));
            assertNull(ReflectionTestUtils.getField(source, "file"));
        } finally { service.destroy(); }
    }

    @Test void webUsesRealSignedSourceResolutionWithoutRefreshingOrDownloading() {
        TkTiktokApiClient api = mock(TkTiktokApiClient.class);
        when(api.getVerifiedPullDomain()).thenReturn(PROPERTY);
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", api);
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        try {
            assertTrue(resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveUploadSource",
                    ORIGINAL + V1, "video/mp4", 7L, resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
            assertEquals(true, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(TARGET, ReflectionTestUtils.getField(source, "pullUrl"));
            assertNull(ReflectionTestUtils.getField(source, "videoFile"));
        } finally { service.destroy(); }
    }

    @Test void privateObjectStillFallsBackToExistingFile(@TempDir Path directory) throws Exception {
        when(connection.getResponseCode()).thenReturn(403);
        Path file = directory.resolve("fallback.mp4");
        Files.write(file, new byte[]{1, 2, 3});
        TkOpenTiktokPublishService service = openService();
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        try {
            assertTrue(resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource",
                    TkOpenTiktokMediaDO.builder().fileUrl(ORIGINAL + V1).scheduledLocalPath(file.toString()).build(),
                    PROPERTY, 7L, resolver.isCandidate(ORIGINAL + V1, 7L, PROPERTY));
            assertEquals(false, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(file.toAbsolutePath(), ReflectionTestUtils.getField(source, "file"));
            assertEquals(1, opens);
        } finally { service.destroy(); }
    }

    private TkOpenTiktokPublishService openService() {
        return new TkOpenTiktokPublishService(mock(TkOpenTiktokPublishTaskMapper.class),
                mock(TkOpenTiktokPublishDetailMapper.class), mock(TkOpenTiktokMediaMapper.class),
                mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class),
                null, null, null, null);
    }
}
