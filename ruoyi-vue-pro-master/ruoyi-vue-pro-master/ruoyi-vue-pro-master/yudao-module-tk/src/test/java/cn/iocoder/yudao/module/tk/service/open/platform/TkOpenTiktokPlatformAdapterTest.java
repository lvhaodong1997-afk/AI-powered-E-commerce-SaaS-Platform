package cn.iocoder.yudao.module.tk.service.open.platform;

import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TkOpenTiktokPlatformAdapterTest {

    @Test
    void guardedUploadsForwardSameHookToExistingSessionClient() {
        TkTiktokApiClient client = mock(TkTiktokApiClient.class);
        TkOpenPublishPlatformAdapter adapter = new TkOpenTiktokPlatformAdapter(client);
        Path video = Paths.get("video.mov");
        String uploadUrl = "https://upload.example/video?upload_id=same&upload_token=test";
        Runnable beforeChunk = () -> { };

        adapter.uploadVideo(uploadUrl, video, "video/quicktime", beforeChunk);
        adapter.resumeUploadVideo(uploadUrl, video, "video/quicktime", 32_000_000L, beforeChunk);

        verify(client).resumeUploadVideoChunks(uploadUrl, video, "video/quicktime", 0L, beforeChunk);
        verify(client).resumeUploadVideoChunks(uploadUrl, video, "video/quicktime", 32_000_000L, beforeChunk);
        verifyNoMoreInteractions(client);
    }

    @Test
    void defaultGuardedUploadRunsHookBeforeLegacyApi() {
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class, CALLS_REAL_METHODS);
        Path video = Paths.get("video.mp4");
        java.util.List<String> calls = new java.util.ArrayList<>();
        doAnswer(invocation -> { calls.add("upload"); return null; })
                .when(adapter).uploadVideo("url", video, "video/mp4");

        adapter.uploadVideo("url", video, "video/mp4", () -> calls.add("hook"));

        assertEquals(java.util.Arrays.asList("hook", "upload"), calls);
    }

    @Test
    void defaultGuardedResumeRunsHookBeforeLegacyApi() {
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class, CALLS_REAL_METHODS);
        Path video = Paths.get("video.mp4");
        java.util.List<String> calls = new java.util.ArrayList<>();
        doAnswer(invocation -> { calls.add("resume"); return null; })
                .when(adapter).resumeUploadVideo("url", video, "video/mp4", 32_000_000L);

        adapter.resumeUploadVideo("url", video, "video/mp4", 32_000_000L, () -> calls.add("hook"));

        assertEquals(java.util.Arrays.asList("hook", "resume"), calls);
    }

    @Test
    void defaultGuardFailurePropagatesWithoutCallingLegacyApis() {
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class, CALLS_REAL_METHODS);
        Path video = Paths.get("video.mp4");
        IllegalStateException fenced = new IllegalStateException("lease lost");
        Runnable beforeChunk = () -> { throw fenced; };

        assertSame(fenced, assertThrows(IllegalStateException.class,
                () -> adapter.uploadVideo("url", video, "video/mp4", beforeChunk)));
        assertSame(fenced, assertThrows(IllegalStateException.class,
                () -> adapter.resumeUploadVideo("url", video, "video/mp4", 32_000_000L, beforeChunk)));

        verify(adapter, never()).uploadVideo("url", video, "video/mp4");
        verify(adapter, never()).resumeUploadVideo("url", video, "video/mp4", 32_000_000L);
    }

    @Test
    void resumeDelegatesExistingUrlAndByteCountWithoutReinitializing() {
        TkTiktokApiClient client = mock(TkTiktokApiClient.class);
        TkOpenPublishPlatformAdapter adapter = new TkOpenTiktokPlatformAdapter(client);
        Path video = Paths.get("video.mov");
        String uploadUrl = "https://upload.example/video?upload_id=same&upload_token=test";

        adapter.resumeUploadVideo(uploadUrl, video, "video/quicktime", 32_000_000L);

        verify(client).resumeUploadVideoChunks(uploadUrl, video, "video/quicktime", 32_000_000L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void fetchStatusPropagatesUploadedBytesAndExistingFields() {
        TkTiktokApiClient client = mock(TkTiktokApiClient.class);
        when(client.fetchPostStatus("access", "publish")).thenReturn(new TkTiktokApiClient.PostStatusResult(
                true, "PROCESSING_UPLOAD", null, null, Collections.singletonList("123"), 32_000_000L));

        TkOpenPublishPlatformAdapter.PublishStatusResult result =
                new TkOpenTiktokPlatformAdapter(client).fetchPostStatus("access", "publish");

        assertTrue(result.isSuccess());
        assertEquals("PROCESSING_UPLOAD", result.getStatus());
        assertNull(result.getFailReason());
        assertNull(result.getErrorCode());
        assertEquals(Collections.singletonList("123"), result.getPublicPostIds());
        assertEquals(32_000_000L, result.getUploadedBytes());
    }

    @Test
    void legacyStatusConstructorsKeepProgressUnknown() {
        TkOpenPublishPlatformAdapter.PublishStatusResult shortResult =
                new TkOpenPublishPlatformAdapter.PublishStatusResult(false, "FAILED", "expired", "access_token_invalid");
        TkOpenPublishPlatformAdapter.PublishStatusResult withIds =
                new TkOpenPublishPlatformAdapter.PublishStatusResult(true, "PUBLISH_COMPLETE", null, null,
                        Collections.singletonList("123"));

        assertNull(shortResult.getUploadedBytes());
        assertTrue(shortResult.getPublicPostIds().isEmpty());
        assertTrue(shortResult.isAccessTokenInvalid());
        assertNull(withIds.getUploadedBytes());
        assertEquals(Collections.singletonList("123"), withIds.getPublicPostIds());
    }

    @Test
    void defaultResumeRejectsUnsupportedPlatformWithoutFallingBackToUpload() {
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class, CALLS_REAL_METHODS);
        Path video = Paths.get("video.mp4");

        assertThrows(UnsupportedOperationException.class,
                () -> adapter.resumeUploadVideo("https://upload.example/video", video, "video/mp4", 0L));

        verify(adapter).resumeUploadVideo("https://upload.example/video", video, "video/mp4", 0L);
        verifyNoMoreInteractions(adapter);
    }
}
