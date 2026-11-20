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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXMediaServiceTest {
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
        TkOpenApiContext.clear();
    }

    @Test
    void requireReadyRejectsOtherClientsFailedExpiredAndMissingMedia() {
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(null);
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1")).getCode());
        TkOpenXMediaDO failed = new TkOpenXMediaDO().setClientId("client-a").setMediaId("media-1")
                .setStatus("FAILED").setExpireTime(LocalDateTime.now().plusHours(1)).setFileSize(10L);
        when(mapper.selectByClientAndMediaId("client-a", "media-1")).thenReturn(failed);
        assertEquals("MEDIA_NOT_READY", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1")).getCode());
        failed.setStatus("READY").setExpireTime(LocalDateTime.now().minusMinutes(1));
        assertEquals("MEDIA_NOT_READY", assertThrows(TkOpenApiException.class,
                () -> service.requireReady("client-a", "media-1")).getCode());
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
    void invalidFileIsRejectedBeforeInsert() {
        assertEquals("MEDIA_FILE_INVALID", assertThrows(TkOpenApiException.class,
                () -> service.create("note.txt", 12L, "text/plain", null)).getCode());
        verify(mapper, never()).insert(any(TkOpenXMediaDO.class));
    }
}