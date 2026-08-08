package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCleanupFileMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isNull;
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
