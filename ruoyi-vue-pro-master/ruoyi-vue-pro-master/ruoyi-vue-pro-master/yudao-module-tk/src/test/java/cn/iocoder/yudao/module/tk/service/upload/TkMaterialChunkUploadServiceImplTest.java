package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialVideoParseService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkMaterialChunkUploadServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void completeMaterialUploadMergesChunksAndCreatesMaterialVideoRecord() throws Exception {
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkMaterialVideoMapper videoMapper = mock(TkMaterialVideoMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkMaterialVideoParseService parseService = mock(TkMaterialVideoParseService.class);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(200L)
                .videoCount(2)
                .totalSize(100L)
                .build();
        library.setTenantId(100L);
        when(libraryMapper.selectById(10L)).thenReturn(library);

        TkMaterialChunkUploadServiceImpl service = createService(libraryMapper, videoMapper, dataScopeService, parseService);
        byte[] content = validMp4Bytes("first".getBytes(StandardCharsets.UTF_8),
                "second".getBytes(StandardCharsets.UTF_8));

        TkUploadSessionRespVO session = service.createMaterialVideoSession(10L, "demo.mp4",
                (long) content.length, "video/mp4");
        service.uploadChunk(session.getUploadId(), 0, new MockMultipartFile("chunk", "0.part",
                "application/octet-stream", java.util.Arrays.copyOfRange(content, 0, 32)));
        service.uploadChunk(session.getUploadId(), 1, new MockMultipartFile("chunk", "1.part",
                "application/octet-stream", java.util.Arrays.copyOfRange(content, 32, content.length)));

        Long videoId = service.completeMaterialVideoUpload(session.getUploadId(), "tag1",
                "PRODUCT_SHOW", "S4_DEMO");

        ArgumentCaptor<TkMaterialVideoDO> videoCaptor = ArgumentCaptor.forClass(TkMaterialVideoDO.class);
        verify(videoMapper).insert(videoCaptor.capture());
        TkMaterialVideoDO video = videoCaptor.getValue();
        assertEquals(100L, video.getTenantId());
        assertEquals(200L, video.getCompanyId());
        assertEquals(10L, video.getLibraryId());
        assertEquals("demo.mp4", video.getFileName());
        assertEquals(content.length, video.getSize());
        assertEquals("mp4", video.getFormat());
        assertEquals("tag1", video.getTags());
        assertEquals(TkMaterialUsagePhaseEnum.PRODUCT_SHOW.getCode(), video.getUsagePhase());
        assertEquals(TkMaterialSegmentTypeEnum.S4_DEMO.getCode(), video.getSegmentType());
        assertEquals(TkMaterialVideoStatusEnum.PARSING, video.getStatus());
        assertTrue(video.getFileUrl().startsWith("https://tkassetplant.fnn.net.cn/uploads/tk/100/200/material-videos/"));

        Path finalFile = service.getStorageService().resolveLocalPath(video.getFileUrl()).orElseThrow();
        assertArrayEquals(content, Files.readAllBytes(finalFile));
        verify(dataScopeService, times(2)).validateWritable(100L, 200L);
        verify(libraryMapper).updateById(any(TkMaterialLibraryDO.class));
        verify(parseService).submit(100L, videoId);
    }

    private TkMaterialChunkUploadServiceImpl createService(TkMaterialLibraryMapper libraryMapper,
                                                           TkMaterialVideoMapper videoMapper,
                                                           TkDataScopeService dataScopeService,
                                                           TkMaterialVideoParseService parseService) {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setPublicBaseUrl("https://tkassetplant.fnn.net.cn/uploads");
        properties.getUpload().setChunkSizeBytes(32);
        TkLocalUploadStorageService storageService = new TkLocalUploadStorageService(properties);
        TkMaterialChunkUploadServiceImpl service = new TkMaterialChunkUploadServiceImpl();
        ReflectionTestUtils.setField(service, "libraryMapper", libraryMapper);
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "materialVideoParseService", parseService);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        return service;
    }

    private byte[] validMp4Bytes(byte[] firstPayload, byte[] secondPayload) {
        byte[] bytes = new byte[32 + firstPayload.length + secondPayload.length];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[20] = 'm';
        bytes[21] = 'o';
        bytes[22] = 'o';
        bytes[23] = 'v';
        System.arraycopy(firstPayload, 0, bytes, 32, firstPayload.length);
        System.arraycopy(secondPayload, 0, bytes, 32 + firstPayload.length, secondPayload.length);
        return bytes;
    }

}
