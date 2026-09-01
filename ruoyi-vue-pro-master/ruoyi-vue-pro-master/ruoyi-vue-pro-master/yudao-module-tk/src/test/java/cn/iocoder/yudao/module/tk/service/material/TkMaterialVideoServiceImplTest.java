package cn.iocoder.yudao.module.tk.service.material;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkMaterialVideoServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void getSegmentSummaryValidatesLibraryScopeAndCountsAvailableVideos() {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, libraryMapper, dataScopeService,
                mock(FileApi.class), mock(TkMaterialVideoParseService.class));
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(200L)
                .build();
        library.setTenantId(100L);
        when(libraryMapper.selectById(10L)).thenReturn(library);
        when(videoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, TkMaterialSegmentTypeEnum.S1_HOOK.getCode()),
                material(2L, TkMaterialSegmentTypeEnum.S1_HOOK.getCode()),
                material(3L, TkMaterialSegmentTypeEnum.S4_DEMO.getCode()),
                material(4L, null)
        ));

        Map<String, Long> summary = service.getSegmentSummary(10L);

        verify(dataScopeService).validateReadable(100L, 200L, null);
        assertEquals(2L, summary.get(TkMaterialSegmentTypeEnum.S1_HOOK.getCode()));
        assertEquals(1L, summary.get(TkMaterialSegmentTypeEnum.S4_DEMO.getCode()));
        assertEquals(1L, summary.get(TkMaterialSegmentTypeEnum.GENERAL.getCode()));
    }

    @Test
    void uploadKeepsSegmentGeneralWhenOnlyUsagePhaseIsProvided() throws Exception {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        FileApi fileApi = mock(FileApi.class);
        TkMaterialVideoParseService parseService = mock(TkMaterialVideoParseService.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, libraryMapper, dataScopeService, fileApi, parseService);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(200L)
                .build();
        library.setTenantId(100L);
        when(libraryMapper.selectById(10L)).thenReturn(library);
        when(fileApi.createFile(any(byte[].class), eq("demo.mp4"), eq("tk/100/200/material-videos"), eq("video/mp4")))
                .thenReturn("https://example.com/demo.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", validMp4Bytes());

        service.uploadMaterialVideo(10L, file, "demo", "PRODUCT_SHOW", null);

        ArgumentCaptor<TkMaterialVideoDO> captor = ArgumentCaptor.forClass(TkMaterialVideoDO.class);
        verify(videoMapper).insert(captor.capture());
        assertEquals(TkMaterialUsagePhaseEnum.PRODUCT_SHOW.getCode(), captor.getValue().getUsagePhase());
        assertEquals(TkMaterialSegmentTypeEnum.GENERAL.getCode(), captor.getValue().getSegmentType());
    }

    @Test
    void updateUsagePhaseDoesNotOverwriteExistingSegmentType() {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, mock(TkMaterialLibraryMapper.class),
                mock(TkDataScopeService.class), mock(FileApi.class), mock(TkMaterialVideoParseService.class));
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .id(45L)
                .companyId(200L)
                .segmentType(TkMaterialSegmentTypeEnum.S4_DEMO.getCode())
                .build();
        video.setTenantId(100L);
        when(videoMapper.selectById(45L)).thenReturn(video);

        service.updateUsagePhase(java.util.Collections.singletonList(45L), "RESULT_EFFECT");

        ArgumentCaptor<TkMaterialVideoDO> captor = ArgumentCaptor.forClass(TkMaterialVideoDO.class);
        verify(videoMapper).updateById(captor.capture());
        assertEquals(TkMaterialUsagePhaseEnum.RESULT_EFFECT.getCode(), captor.getValue().getUsagePhase());
        assertEquals(null, captor.getValue().getSegmentType());
    }

    @Test
    void deleteMaterialVideoDeletesVideoAndCoverFilesBeforeRecord() {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        FileApi fileApi = mock(FileApi.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, libraryMapper,
                dataScopeService, fileApi, mock(TkMaterialVideoParseService.class));
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .id(88L)
                .companyId(200L)
                .libraryId(10L)
                .fileUrl("https://cdn.example.com/material/demo.mp4")
                .coverUrl("https://cdn.example.com/material/demo-cover.jpg")
                .size(1024L)
                .build();
        video.setTenantId(100L);
        when(videoMapper.selectById(88L)).thenReturn(video);
        when(libraryMapper.selectById(10L)).thenReturn(TkMaterialLibraryDO.builder()
                .id(10L)
                .videoCount(3)
                .totalSize(4096L)
                .build());

        service.deleteMaterialVideo(88L);

        verify(fileApi).deleteFileByUrl("https://cdn.example.com/material/demo.mp4");
        verify(fileApi).deleteFileByUrl("https://cdn.example.com/material/demo-cover.jpg");
        verify(videoMapper).deleteBatchIds(Collections.singletonList(88L));
    }

    @Test
    void deleteMaterialVideoKeepsRecordWhenFileDeleteFails() {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        FileApi fileApi = mock(FileApi.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, mock(TkMaterialLibraryMapper.class),
                mock(TkDataScopeService.class), fileApi, mock(TkMaterialVideoParseService.class));
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .id(89L)
                .companyId(200L)
                .libraryId(10L)
                .fileUrl("https://cdn.example.com/material/fail.mp4")
                .build();
        video.setTenantId(100L);
        when(videoMapper.selectById(89L)).thenReturn(video);
        org.mockito.Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(fileApi).deleteFileByUrl("https://cdn.example.com/material/fail.mp4");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.deleteMaterialVideo(89L));

        verify(videoMapper, never()).deleteBatchIds(any());
        verify(videoMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(videoMapper, never()).deleteById(any(TkMaterialVideoDO.class));
        verify(videoMapper, never()).deleteById(any(), anyBoolean());
    }

    @Test
    void deleteMaterialVideoDeletesRecordWhenOssCleanupFails() {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        FileApi fileApi = mock(FileApi.class);
        TkMaterialOssUploadService ossUploadService = mock(TkMaterialOssUploadService.class);
        TkMaterialVideoServiceImpl service = createService(videoMapper, libraryMapper,
                mock(TkDataScopeService.class), fileApi, mock(TkMaterialVideoParseService.class),
                null, ossUploadService);
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .id(91L)
                .companyId(200L)
                .libraryId(10L)
                .fileUrl("https://tk-material-factory.oss-cn-beijing.aliyuncs.com/tk/166/166/material-videos/demo.mp4?Expires=1")
                .size(1024L)
                .build();
        video.setTenantId(100L);
        when(videoMapper.selectById(91L)).thenReturn(video);
        when(libraryMapper.selectById(10L)).thenReturn(TkMaterialLibraryDO.builder()
                .id(10L)
                .videoCount(1)
                .totalSize(1024L)
                .build());
        when(ossUploadService.isEnabled()).thenReturn(true);
        when(ossUploadService.isManagedUrl(video.getFileUrl())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("oss delete forbidden"))
                .when(ossUploadService).deleteByUrl(video.getFileUrl());

        service.deleteMaterialVideo(91L);

        verify(ossUploadService).deleteByUrl(video.getFileUrl());
        verify(fileApi, never()).deleteFileByUrl(video.getFileUrl());
        verify(videoMapper).deleteBatchIds(Collections.singletonList(91L));
    }

    @Test
    void deleteMaterialVideoDeletesLocalChunkUploadFileBeforeRecord() throws Exception {
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        FileApi fileApi = mock(FileApi.class);
        TkLocalUploadStorageService storageService = createStorageService();
        TkMaterialVideoServiceImpl service = createService(videoMapper, mock(TkMaterialLibraryMapper.class),
                mock(TkDataScopeService.class), fileApi, mock(TkMaterialVideoParseService.class), storageService);
        String relativePath = "tk/100/200/material-videos/upload-demo.mp4";
        Path localFile = storageService.resolveRelativePath(relativePath);
        Files.createDirectories(localFile.getParent());
        Files.write(localFile, new byte[]{1, 2, 3});
        String fileUrl = storageService.toPublicUrl(relativePath);
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .id(90L)
                .companyId(200L)
                .libraryId(10L)
                .fileUrl(fileUrl)
                .build();
        video.setTenantId(100L);
        when(videoMapper.selectById(90L)).thenReturn(video);

        service.deleteMaterialVideo(90L);

        assertFalse(Files.exists(localFile));
        verify(fileApi).deleteFileByUrl(fileUrl);
        verify(videoMapper).deleteBatchIds(Collections.singletonList(90L));
    }

    private TkMaterialVideoServiceImpl createService(TkMaterialVideoMapper videoMapper,
                                                     TkMaterialLibraryMapper libraryMapper,
                                                     TkDataScopeService dataScopeService,
                                                     FileApi fileApi,
                                                     TkMaterialVideoParseService parseService) {
        return createService(videoMapper, libraryMapper, dataScopeService, fileApi, parseService, null);
    }

    private TkMaterialVideoServiceImpl createService(TkMaterialVideoMapper videoMapper,
                                                     TkMaterialLibraryMapper libraryMapper,
                                                     TkDataScopeService dataScopeService,
                                                     FileApi fileApi,
                                                     TkMaterialVideoParseService parseService,
                                                     TkLocalUploadStorageService storageService) {
        return createService(videoMapper, libraryMapper, dataScopeService, fileApi, parseService,
                storageService, null);
    }

    private TkMaterialVideoServiceImpl createService(TkMaterialVideoMapper videoMapper,
                                                     TkMaterialLibraryMapper libraryMapper,
                                                     TkDataScopeService dataScopeService,
                                                     FileApi fileApi,
                                                     TkMaterialVideoParseService parseService,
                                                     TkLocalUploadStorageService storageService,
                                                     TkMaterialOssUploadService ossUploadService) {
        TkMaterialVideoServiceImpl service = new TkMaterialVideoServiceImpl();
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);
        ReflectionTestUtils.setField(service, "libraryMapper", libraryMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "materialVideoParseService", parseService);
        if (storageService != null) {
            ReflectionTestUtils.setField(service, "localUploadStorageService", storageService);
        }
        if (ossUploadService != null) {
            ReflectionTestUtils.setField(service, "materialOssUploadService", ossUploadService);
        }
        return service;
    }

    private TkLocalUploadStorageService createStorageService() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setPublicBaseUrl("https://tkassetplant.fnn.net.cn/uploads");
        return new TkLocalUploadStorageService(properties);
    }

    private byte[] validMp4Bytes() {
        byte[] bytes = new byte[32];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[20] = 'm';
        bytes[21] = 'o';
        bytes[22] = 'o';
        bytes[23] = 'v';
        return bytes;
    }

    private TkMaterialVideoDO material(Long id, String segmentType) {
        return TkMaterialVideoDO.builder()
                .id(id)
                .libraryId(10L)
                .segmentType(segmentType)
                .build();
    }

}
