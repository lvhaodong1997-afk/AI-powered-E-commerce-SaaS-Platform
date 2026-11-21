package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAccess;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXMediaServiceTest {
    private static final String WORKSPACE = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties);
    private final TkOpenXMediaMapper mapper = mock(TkOpenXMediaMapper.class);
    private TkOpenXMediaService service;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.publish"), "req");
        TkGenerationProperties generation = new TkGenerationProperties();
        service = new TkOpenXMediaService(access, mapper, mock(TkLocalUploadStorageService.class),
                generation, mock(TkOssObjectStorageClient.class));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        TkOpenApiContext.clear();
    }

    @Test
    void requireReadyRejectsOtherClientsFailedExpiredAndMissingMedia() {
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(null);
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1", null)).getCode());
        TkOpenXMediaDO failed = new TkOpenXMediaDO().setClientId("client-a").setMediaId("media-1")
                .setStatus("FAILED").setExpireTime(LocalDateTime.now().plusHours(1)).setFileSize(10L);
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(failed);
        assertEquals("MEDIA_NOT_READY", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1", null)).getCode());
        failed.setStatus("READY").setExpireTime(LocalDateTime.now().minusMinutes(1));
        assertEquals("MEDIA_NOT_READY", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1", null)).getCode());
    }

    @Test
    void anotherWorkspaceCannotReadCompleteOrAttachAnUploadFromTheSameClient() {
        String workspaceId = OTHER_WORKSPACE;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("workspaceId=" + workspaceId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TkOpenXMediaDO owned = new TkOpenXMediaDO().setClientId("client-a").setWorkspaceId(WORKSPACE)
                .setUploadId("upload-1").setMediaId("media-1").setStatus("READY").setFileSize(10L)
                .setExpireTime(LocalDateTime.now().plusHours(1));
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(owned);
        when(mapper.selectByClientAndUploadId("client-a", "upload-1")).thenReturn(owned);
        when(mapper.selectByClientAndUploadIdForUpdate("client-a", "upload-1")).thenReturn(owned);
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.getStatus("upload-1")).getCode());
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.complete("upload-1", 10L, null)).getCode());
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1", OTHER_WORKSPACE)).getCode());
        assertSame(owned, service.requireReady("client-a", "media-1", WORKSPACE));
    }

    @Test
    void completeRejectsExpiredOrNonUploadingSessions() {
        TkOpenXMediaDO expired = new TkOpenXMediaDO().setId(1L).setClientId("client-a")
                .setUploadId("upload-1").setStatus("UPLOADING")
                .setExpireTime(LocalDateTime.now().minusMinutes(1)).setFileSize(10L);
        when(mapper.selectByClientAndUploadIdForUpdate("client-a", "upload-1")).thenReturn(expired);
        assertEquals("MEDIA_UPLOAD_EXPIRED", assertThrows(TkOpenApiException.class,
                () -> service.complete("upload-1", 10L, null)).getCode());
        expired.setExpireTime(LocalDateTime.now().plusHours(1)).setStatus("FAILED");
        assertEquals("MEDIA_UPLOAD_STATUS_INVALID", assertThrows(TkOpenApiException.class,
                () -> service.complete("upload-1", 10L, null)).getCode());
    }

    @Test
    void otherClientCannotReadUploadStatus() {
        when(mapper.selectByClientAndUploadId("client-a", "upload-other")).thenReturn(null);
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.getStatus("upload-other")).getCode());
    }

    @Test
    void signsReadyMediaOnItsOriginalBucket() {
        TkGenerationProperties generation = new TkGenerationProperties();
        TkGenerationProperties.Oss storage = generation.getUpload().getOss();
        storage.setBucket("material-bucket");
        storage.setEndpoint("oss-cn-beijing.aliyuncs.com");
        storage.setPublicBaseUrl("https://material.example.com");
        storage.setAccessKeyId("material-key");
        storage.setAccessKeySecret("material-secret");
        TkOssObjectStorageService oss = new TkOssObjectStorageService();
        ReflectionTestUtils.setField(oss, "generationProperties", generation);
        service = new TkOpenXMediaService(access, mapper, mock(TkLocalUploadStorageService.class), generation, oss);
        String key = "tk/open-api-x/client-a/date/upload-1.mp4";
        TkOpenXMediaDO ready = new TkOpenXMediaDO().setClientId("client-a").setWorkspaceId(WORKSPACE)
                .setMediaId("media-1").setStatus("READY").setUploadMode("OSS").setFileSize(10L)
                .setExpireTime(LocalDateTime.now().plusHours(1)).setObjectKey(key)
                .setFileUrl("https://material.example.com/" + key);
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(ready);

        java.net.URI read = java.net.URI.create(service.resolveReadUrl(service.requireReady("client-a", "media-1", WORKSPACE)));

        assertEquals("material-bucket.oss-cn-beijing.aliyuncs.com", read.getHost());
        assertEquals("/" + key, read.getPath());
        assertTrue(read.getRawQuery().startsWith("OSSAccessKeyId=material-key&Expires="));
        assertTrue(read.getRawQuery().contains("&Signature="));

        ready.setFileUrl("https://material.example.com/" + key.replace("upload-1", "upload-2"));
        assertEquals("MEDIA_SOURCE_UNAVAILABLE", assertThrows(TkOpenApiException.class,
                () -> service.resolveReadUrl(ready)).getCode());
    }

    @Test
    void invalidFileIsRejectedBeforeInsert() {
        assertEquals("MEDIA_FILE_INVALID", assertThrows(TkOpenApiException.class,
                () -> service.create("note.txt", 12L, "text/plain", null)).getCode());
        verify(mapper, never()).insert(any(TkOpenXMediaDO.class));
    }
}
