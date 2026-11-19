package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkChannelVideoClientTest {
    private final TkChannelHttp http = mock(TkChannelHttp.class);
    private final TkChannelVideoClient client = new TkChannelVideoClient(http);
    private final TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
    private final TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();

    @Test void xWaitsForProcessingThenPostsWithThePersistedMediaId() throws Exception {
        detail.setPlatform("x"); detail.setPlatformStatus("X_PROCESSING"); detail.setExternalContainerId("123");
        task.setChannelText("Example");
        when(http.json(eq("GET"), contains("command=STATUS"), eq("Bearer token"), isNull(), eq(false)))
                .thenReturn(TkChannelHttp.JSON.readTree("{\"data\":{\"processing_info\":{\"state\":\"succeeded\"}}}"));
        TkChannelVideoClient.Checkpoint ready = client.advance(task, detail, "token", null, 10, null);
        assertEquals("X_READY", ready.getStage());
        verify(http, never()).json(eq("POST"), endsWith("/tweets"), anyString(), any(), anyBoolean());
        detail.setPlatformStatus(ready.getStage());
        when(http.json(eq("POST"), endsWith("/tweets"), anyString(), argThat(body -> body.toString().contains("123")), eq(true)))
                .thenReturn(TkChannelHttp.JSON.readTree("{\"data\":{\"id\":\"456\"}}"));
        assertEquals("456", client.advance(task, detail, "token", null, 10, null).getPostId());
    }

    @Test void youtubeProbeRecoversVideoIdAfterTheFinalUploadResponseWasLost() throws Exception {
        detail.setPlatform("youtube"); detail.setPlatformStatus("YT_UPLOAD");
        String url = "https://www.googleapis.com/upload/youtube/v3/videos?upload_id=test";
        when(http.send(eq("PUT"), eq(url), eq("Bearer token"), eq("video/mp4"), any(),
                eq(TkChannelOAuthClient.params("Content-Range", "bytes */1024")), eq(false)))
                .thenReturn(new TkChannelHttp.Response(201, null, null, TkChannelHttp.JSON.readTree("{\"id\":\"AbcDefGhI01\"}")));
        TkChannelVideoClient.Checkpoint recovered = client.advance(task, detail, "token", null, 1024, url);
        assertEquals("YT_PROCESSING", recovered.getStage());
        assertEquals("AbcDefGhI01", recovered.getPostId());
        assertNull(recovered.getUploadUrl());
        verify(http, times(1)).send(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), anyBoolean());
    }

    @Test void xPostTimeoutIsNotRetryable() {
        detail.setPlatform("x"); detail.setPlatformStatus("X_READY"); detail.setExternalContainerId("123");
        when(http.json(eq("POST"), endsWith("/tweets"), anyString(), any(), eq(true))).thenThrow(TkChannelHttp.failure(0, true));
        TkSocialPlatformException failure = assertThrows(TkSocialPlatformException.class,
                () -> client.advance(task, detail, "token", null, 10, null));
        assertTrue(failure.isUncertain());
        assertFalse(failure.isRetryable());
    }
}
