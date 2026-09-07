package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkOpenVideoTranscriptTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCleanupFileMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkOpenVideoTranscriptTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkFileCleanupServiceTest {

    @Test
    void cleanupExpiredFilesDeletesOnlyCleanableTkFiles() throws Exception {
        TkFileCleanupService service = new TkFileCleanupService();
        TkGenerationProperties properties = new TkGenerationProperties();
        TkCleanupFileMapper cleanupFileMapper = mock(TkCleanupFileMapper.class);
        TkGenerationTaskMapper generationTaskMapper = mock(TkGenerationTaskMapper.class);
        TkReferenceAnalysisMapper referenceAnalysisMapper = mock(TkReferenceAnalysisMapper.class);
        FileService fileService = mock(FileService.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "cleanupFileMapper", cleanupFileMapper);
        ReflectionTestUtils.setField(service, "generationTaskMapper", generationTaskMapper);
        ReflectionTestUtils.setField(service, "referenceAnalysisMapper", referenceAnalysisMapper);
        ReflectionTestUtils.setField(service, "fileService", fileService);

        when(cleanupFileMapper.selectExpiredGenerationTaskCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Arrays.asList(
                        file(1L, "tk/1/2/generation-tasks/10/20260706/generated-10.mp4", "https://host/generated-10.mp4"),
                        file(2L, "tk/1/2/material-videos/20260706/source.mp4", "https://host/source.mp4"),
                        file(3L, "tk/1/2/generation-tasks/11/20260706/generated-11.mp4", "https://host/generated-11.mp4")));
        when(generationTaskMapper.selectByIds(anySet()))
                .thenReturn(Arrays.asList(
                        task(10L, TkGenerationStatusEnum.SUCCESS),
                        task(11L, TkGenerationStatusEnum.RENDERING)));
        when(cleanupFileMapper.selectExpiredReferencePreviewCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Arrays.asList(
                        file(4L, "tk/reference-videos/20260706/reference-video.mp4", "https://host/reference-video.mp4"),
                        file(5L, "tk/1/2/material-covers/20260706/source.jpg", "https://host/source.jpg")));

        TkFileCleanupService.CleanupResult result = service.cleanupExpiredFiles();

        assertEquals(1, result.getGeneratedFileCount());
        assertEquals(1, result.getReferenceFileCount());
        verify(fileService).deleteFile(1L);
        verify(fileService).deleteFile(4L);
        verify(fileService, never()).deleteFile(2L);
        verify(fileService, never()).deleteFile(3L);
        verify(fileService, never()).deleteFile(5L);
        verify(generationTaskMapper, atLeastOnce()).update(isNull(), any());
        verify(referenceAnalysisMapper).update(isNull(), any());
    }

    @Test
    void cleanupExpiredFilesSkipsAllDeletesWhenDisabled() throws Exception {
        TkFileCleanupService service = new TkFileCleanupService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getCleanup().setEnabled(false);
        TkCleanupFileMapper cleanupFileMapper = mock(TkCleanupFileMapper.class);
        FileService fileService = mock(FileService.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "cleanupFileMapper", cleanupFileMapper);
        ReflectionTestUtils.setField(service, "fileService", fileService);

        TkFileCleanupService.CleanupResult result = service.cleanupExpiredFiles();

        assertEquals(0, result.getGeneratedFileCount());
        assertEquals(0, result.getReferenceFileCount());
        verify(cleanupFileMapper, never()).selectExpiredGenerationTaskCandidates(any(), anyInt());
        verify(fileService, never()).deleteFile(any());
    }

    @Test
    void cleanupExpiredFilesClearsAndDeletesExpiredSignedGenerationTaskUrlsWithoutFileRecords() {
        TkFileCleanupService service = new TkFileCleanupService();
        TkGenerationProperties properties = new TkGenerationProperties();
        TkCleanupFileMapper cleanupFileMapper = mock(TkCleanupFileMapper.class);
        TkGenerationTaskMapper generationTaskMapper = mock(TkGenerationTaskMapper.class);
        TkReferenceAnalysisMapper referenceAnalysisMapper = mock(TkReferenceAnalysisMapper.class);
        FileService fileService = mock(FileService.class);
        TkMaterialOssUploadService ossUploadService = mock(TkMaterialOssUploadService.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "cleanupFileMapper", cleanupFileMapper);
        ReflectionTestUtils.setField(service, "generationTaskMapper", generationTaskMapper);
        ReflectionTestUtils.setField(service, "referenceAnalysisMapper", referenceAnalysisMapper);
        ReflectionTestUtils.setField(service, "fileService", fileService);
        ReflectionTestUtils.setField(service, "ossUploadService", ossUploadService);
        String signedGeneratedUrl = "https://tk-material-factory.oss-cn-beijing.aliyuncs.com/"
                + "tk/174/174/generation-tasks/142/20260801/generated-142.mp4"
                + "?OSSAccessKeyId=demo&Expires=2101103749&Signature=abc";
        TkGenerationTaskDO expiredTask = task(142L, TkGenerationStatusEnum.SUCCESS)
                .setOutputUrl(signedGeneratedUrl)
                .setSubtitleUrl(signedGeneratedUrl);

        when(cleanupFileMapper.selectExpiredGenerationTaskCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cleanupFileMapper.selectExpiredReferencePreviewCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.emptyList());
        when(generationTaskMapper.selectExpiredTasksWithGenerationUrls(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(expiredTask));
        when(generationTaskMapper.update(isNull(), any())).thenReturn(1);
        when(ossUploadService.isEnabled()).thenReturn(true);
        when(ossUploadService.isManagedUrl(signedGeneratedUrl)).thenReturn(true);

        TkFileCleanupService.CleanupResult result = service.cleanupExpiredFiles();

        assertEquals(1, result.getGeneratedFileCount());
        verify(ossUploadService).deleteByUrl(signedGeneratedUrl);
        verify(generationTaskMapper, atLeastOnce()).update(isNull(), any());
    }

    @Test
    void cleanupExpiredPublishMediaDeletesOssObjectBeforeMediaRecord() {
        TkFileCleanupService service = createService();
        TkTiktokPublishMediaMapper mediaMapper = (TkTiktokPublishMediaMapper)
                ReflectionTestUtils.getField(service, "publishMediaMapper");
        TkMaterialOssUploadService ossUploadService = (TkMaterialOssUploadService)
                ReflectionTestUtils.getField(service, "ossUploadService");
        TkTiktokPublishMediaDO media = media(21L,
                "https://oss.example.com/tk/1/2/tiktok-publish-media/video.mp4");

        when(mediaMapper.selectExpiredCleanupCandidates(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(media));
        when(mediaMapper.claimForCleanup(21L)).thenReturn(1);
        when(ossUploadService.isEnabled()).thenReturn(true);
        when(ossUploadService.isManagedUrl(media.getFileUrl())).thenReturn(true);

        int result = service.cleanupExpiredPublishMedia();

        assertEquals(1, result);
        org.mockito.InOrder order = inOrder(mediaMapper, ossUploadService);
        order.verify(mediaMapper).claimForCleanup(21L);
        order.verify(ossUploadService).deleteByUrl(media.getFileUrl());
        order.verify(mediaMapper).deleteById(21L);
    }

    @Test
    void cleanupExpiredPublishMediaKeepsRecordWhenOssDeleteFails() {
        TkFileCleanupService service = createService();
        TkTiktokPublishMediaMapper mediaMapper = (TkTiktokPublishMediaMapper)
                ReflectionTestUtils.getField(service, "publishMediaMapper");
        TkMaterialOssUploadService ossUploadService = (TkMaterialOssUploadService)
                ReflectionTestUtils.getField(service, "ossUploadService");
        TkTiktokPublishMediaDO media = media(22L,
                "https://oss.example.com/tk/1/2/tiktok-publish-media/video.mp4");

        when(mediaMapper.selectExpiredCleanupCandidates(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(media));
        when(mediaMapper.claimForCleanup(22L)).thenReturn(1);
        when(ossUploadService.isEnabled()).thenReturn(true);
        when(ossUploadService.isManagedUrl(media.getFileUrl())).thenReturn(true);
        doThrow(new IllegalStateException("oss unavailable"))
                .when(ossUploadService).deleteByUrl(media.getFileUrl());

        int result = service.cleanupExpiredPublishMedia();

        assertEquals(0, result);
        verify(mediaMapper, never()).deleteById(22L);
        verify(mediaMapper).restoreReadyAfterCleanupFailure(22L);
    }

    @Test
    void cleanupExpiredPublishMediaDeletesLocalFile(@TempDir Path tempDir) throws Exception {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("local");
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setPublicBaseUrl("/uploads");
        TkFileCleanupService service = createService(properties);
        TkTiktokPublishMediaMapper mediaMapper = (TkTiktokPublishMediaMapper)
                ReflectionTestUtils.getField(service, "publishMediaMapper");
        TkMaterialOssUploadService ossUploadService = (TkMaterialOssUploadService)
                ReflectionTestUtils.getField(service, "ossUploadService");
        String relative = "tk/1/2/tiktok-publish-media/video.mp4";
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        TkTiktokPublishMediaDO media = media(23L, "/uploads/" + relative);

        when(mediaMapper.selectExpiredCleanupCandidates(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(media));
        when(mediaMapper.claimForCleanup(23L)).thenReturn(1);
        when(ossUploadService.isEnabled()).thenReturn(false);

        int result = service.cleanupExpiredPublishMedia();

        assertEquals(1, result);
        assertFalse(Files.exists(file));
        verify(mediaMapper).deleteById(23L);
    }

    @Test
    void cleanupExpiredTranscriptAudioDeletesFileAndClearsTaskUrl() throws Exception {
        TkFileCleanupService service = createService();
        TkCleanupFileMapper cleanupFileMapper = (TkCleanupFileMapper)
                ReflectionTestUtils.getField(service, "cleanupFileMapper");
        TkOpenVideoTranscriptTaskMapper taskMapper = (TkOpenVideoTranscriptTaskMapper)
                ReflectionTestUtils.getField(service, "transcriptTaskMapper");
        FileService fileService = (FileService) ReflectionTestUtils.getField(service, "fileService");
        String audioUrl = "https://tkassetplant.fnn.net.cn/admin-api/infra/file/29/get/"
                + "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav";
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .id(112L)
                .audioUrl(audioUrl)
                .status("SUCCESS")
                .textVerifyStatus("SUCCESS")
                .build();
        task.setCreateTime(LocalDateTime.now().minusDays(2));

        FileDO audioFile = file(30L,
                "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav", audioUrl);
        when(cleanupFileMapper.selectExpiredTranscriptAudioCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(audioFile));
        when(taskMapper.selectByIds(anySet())).thenReturn(Collections.singletonList(task));
        when(taskMapper.clearAudioUrlIfMatches(112L, audioUrl)).thenReturn(1);

        int result = service.cleanupExpiredTranscriptAudio();

        assertEquals(1, result);
        verify(fileService).deleteFile(30L);
        verify(taskMapper).clearAudioUrlIfMatches(112L, audioUrl);
    }

    @Test
    void cleanupExpiredTranscriptAudioKeepsTaskUrlWhenFileDeleteFails() throws Exception {
        TkFileCleanupService service = createService();
        TkCleanupFileMapper cleanupFileMapper = (TkCleanupFileMapper)
                ReflectionTestUtils.getField(service, "cleanupFileMapper");
        TkOpenVideoTranscriptTaskMapper taskMapper = (TkOpenVideoTranscriptTaskMapper)
                ReflectionTestUtils.getField(service, "transcriptTaskMapper");
        FileService fileService = (FileService) ReflectionTestUtils.getField(service, "fileService");
        String audioUrl = "https://tkassetplant.fnn.net.cn/admin-api/infra/file/29/get/"
                + "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav";
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .id(113L)
                .audioUrl(audioUrl)
                .status("FAILED")
                .build();
        task.setCreateTime(LocalDateTime.now().minusDays(2));

        FileDO audioFile = file(32L,
                "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav", audioUrl);
        when(cleanupFileMapper.selectExpiredTranscriptAudioCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(audioFile));
        when(taskMapper.selectByIds(anySet())).thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("file service unavailable"))
                .when(fileService).deleteFile(32L);

        int result = service.cleanupExpiredTranscriptAudio();

        assertEquals(0, result);
        verify(taskMapper, never()).clearAudioUrlIfMatches(113L, audioUrl);
    }

    @Test
    void cleanupExpiredTranscriptAudioDeletesOrphanFileRecord() throws Exception {
        TkFileCleanupService service = createService();
        TkCleanupFileMapper cleanupFileMapper = mock(TkCleanupFileMapper.class);
        TkOpenVideoTranscriptTaskMapper taskMapper = (TkOpenVideoTranscriptTaskMapper)
                ReflectionTestUtils.getField(service, "transcriptTaskMapper");
        FileService fileService = (FileService) ReflectionTestUtils.getField(service, "fileService");
        String audioUrl = "https://tkassetplant.fnn.net.cn/admin-api/infra/file/29/get/"
                + "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav";
        FileDO orphanFile = file(31L,
                "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav", audioUrl);
        ReflectionTestUtils.setField(service, "cleanupFileMapper", cleanupFileMapper);

        when(cleanupFileMapper.selectExpiredTranscriptAudioCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(orphanFile));
        when(taskMapper.selectByIds(anySet())).thenReturn(Collections.emptyList());

        int result = service.cleanupExpiredTranscriptAudio();

        assertEquals(1, result);
        verify(fileService).deleteFile(31L);
        verify(taskMapper, never()).clearAudioUrlIfMatches(any(), any());
    }

    @Test
    void cleanupExpiredTranscriptAudioKeepsProcessingTask() throws Exception {
        TkFileCleanupService service = createService();
        TkCleanupFileMapper cleanupFileMapper = (TkCleanupFileMapper)
                ReflectionTestUtils.getField(service, "cleanupFileMapper");
        TkOpenVideoTranscriptTaskMapper taskMapper = (TkOpenVideoTranscriptTaskMapper)
                ReflectionTestUtils.getField(service, "transcriptTaskMapper");
        FileService fileService = (FileService) ReflectionTestUtils.getField(service, "fileService");
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .id(114L)
                .status("PROCESSING")
                .build();
        FileDO audioFile = file(33L,
                "tk/open-video-transcripts/114/20260905/transcript-audio-114.wav",
                "https://host/tk/open-video-transcripts/114/20260905/transcript-audio-114.wav");

        when(cleanupFileMapper.selectExpiredTranscriptAudioCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.singletonList(audioFile));
        when(taskMapper.selectByIds(anySet())).thenReturn(Collections.singletonList(task));

        int result = service.cleanupExpiredTranscriptAudio();

        assertEquals(0, result);
        verify(fileService, never()).deleteFile(33L);
    }

    private TkFileCleanupService createService() {
        return createService(new TkGenerationProperties());
    }

    private TkFileCleanupService createService(TkGenerationProperties properties) {
        TkFileCleanupService service = new TkFileCleanupService();
        TkTiktokPublishMediaMapper mediaMapper = mock(TkTiktokPublishMediaMapper.class);
        TkMaterialOssUploadService ossUploadService = mock(TkMaterialOssUploadService.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "cleanupFileMapper", mock(TkCleanupFileMapper.class));
        ReflectionTestUtils.setField(service, "publishMediaMapper", mediaMapper);
        ReflectionTestUtils.setField(service, "ossUploadService", ossUploadService);
        ReflectionTestUtils.setField(service, "transcriptTaskMapper", mock(TkOpenVideoTranscriptTaskMapper.class));
        ReflectionTestUtils.setField(service, "fileService", mock(FileService.class));
        ReflectionTestUtils.setField(service, "localUploadStorageService",
                new TkLocalUploadStorageService(properties));
        return service;
    }

    private TkTiktokPublishMediaDO media(Long id, String fileUrl) {
        TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder()
                .id(id)
                .companyId(2L)
                .fileUrl(fileUrl)
                .status("READY")
                .build();
        media.setTenantId(1L);
        media.setCreateTime(LocalDateTime.now().minusDays(2));
        return media;
    }

    private FileDO file(Long id, String path, String url) {
        FileDO file = new FileDO()
                .setId(id)
                .setPath(path)
                .setUrl(url);
        file.setCreateTime(LocalDateTime.now().minusDays(2));
        return file;
    }

    private TkGenerationTaskDO task(Long id, String status) {
        return new TkGenerationTaskDO()
                .setId(id)
                .setStatus(status);
    }
}
