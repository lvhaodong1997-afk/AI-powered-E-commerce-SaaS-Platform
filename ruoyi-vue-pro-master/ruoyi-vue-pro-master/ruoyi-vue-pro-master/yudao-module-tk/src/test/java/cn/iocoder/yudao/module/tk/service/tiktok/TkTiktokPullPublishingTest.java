package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishService;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokPullPublishingTest {
    private static final String ORIGINAL = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/TK/video.mp4";
    private static final String PULL = "https://tkpublish.fnn.net.cn/TK/video.mp4";

    @Test void openPublishCarriesMappedUrlAndSizeWithoutLocalDownload() {
        TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class);
        when(resolver.resolve(ORIGINAL, 7L, "tkpublish.fnn.net.cn"))
                .thenReturn(TkTiktokPullSourceResolver.PullDecision.eligible(PULL, 123L));
        TkOpenTiktokPublishService service = openService();
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        try {
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource",
                    TkOpenTiktokMediaDO.builder().fileUrl(ORIGINAL).build(), "tkpublish.fnn.net.cn", 7L, true);
            assertEquals(true, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(PULL, ReflectionTestUtils.getField(source, "pullUrl"));
            assertEquals(123L, ReflectionTestUtils.getField(source, "size"));
            assertNull(ReflectionTestUtils.getField(source, "file"));
        } finally { service.destroy(); }
    }

    @Test void failedPullPreflightUsesExistingFileEvenWhenDomainIsVerified(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("old.mp4");
        Files.write(file, new byte[]{1, 2, 3});
        TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class);
        when(resolver.resolve(PULL, 7L, "tkpublish.fnn.net.cn"))
                .thenReturn(TkTiktokPullSourceResolver.PullDecision.fallback("HEAD_FAILED"));
        TkOpenTiktokPublishService service = openService();
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        try {
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource",
                    TkOpenTiktokMediaDO.builder().fileUrl(PULL).scheduledLocalPath(file.toString()).build(),
                    "tkpublish.fnn.net.cn", 7L, true);
            assertEquals(false, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(file.toAbsolutePath(), ReflectionTestUtils.getField(source, "file"));
        } finally { service.destroy(); }
    }

    @Test void webPublishUsesMappedUrlBeforeAnySignedReadUrlRefresh() {
        TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class);
        when(resolver.resolve(ORIGINAL, 7L, "tkpublish.fnn.net.cn"))
                .thenReturn(TkTiktokPullSourceResolver.PullDecision.eligible(PULL, 123L));
        TkTiktokApiClient api = mock(TkTiktokApiClient.class);
        when(api.getVerifiedPullDomain()).thenReturn("tkpublish.fnn.net.cn");
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "apiClient", api);
        try {
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveUploadSource",
                    ORIGINAL, "video/mp4", 7L, true);
            assertEquals(true, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(PULL, ReflectionTestUtils.getField(source, "pullUrl"));
            assertEquals(123L, ReflectionTestUtils.getField(source, "videoSize"));
            assertNull(ReflectionTestUtils.getField(source, "videoFile"));
        } finally { service.destroy(); }
    }

    @Test void ineligibleNewTaskUsesFileEvenForPreviouslyVerifiedDomain(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("fallback.mp4");
        Files.write(file, new byte[]{1, 2, 3});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().fileUrl(PULL).uploadMode("REMOTE").build();
        TkOpenTiktokMediaService mediaService = mock(TkOpenTiktokMediaService.class);
        doAnswer(call -> { media.setScheduledLocalPath(file.toString()); return null; })
                .when(mediaService).prepareForScheduledPublish(media);
        TkOpenTiktokPublishService service = openService();
        ReflectionTestUtils.setField(service, "mediaService", mediaService);
        try {
            Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource",
                    media, "tkpublish.fnn.net.cn", 7L, false);
            assertEquals(false, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(file.toAbsolutePath(), ReflectionTestUtils.getField(source, "file"));
        } finally { service.destroy(); }
    }

    @Test void existingPublishIdNeverEntersWebInitializationAgain() {
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        try {
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processDetail",
                    TkTiktokPublishDetailDO.builder().status("PENDING").publishId("official-id").build()));
        } finally { service.destroy(); }
    }

    private TkOpenTiktokPublishService openService() {
        return new TkOpenTiktokPublishService(mock(TkOpenTiktokPublishTaskMapper.class),
                mock(TkOpenTiktokPublishDetailMapper.class), mock(TkOpenTiktokMediaMapper.class),
                mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class),
                null, null, null, null);
    }
}
