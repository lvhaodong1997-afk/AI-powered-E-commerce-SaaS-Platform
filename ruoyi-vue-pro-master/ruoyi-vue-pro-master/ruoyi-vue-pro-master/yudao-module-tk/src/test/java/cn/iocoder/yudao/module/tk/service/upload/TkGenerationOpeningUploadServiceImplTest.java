package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationOpeningUploadCompleteRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkGenerationOpeningUploadServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void completedOpeningUploadReturnsTrustedUrlWithoutCreatingMaterialVideo() throws Exception {
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        StubUploadSessionService uploadSessionService = new StubUploadSessionService();
        TkGenerationOpeningUploadServiceImpl service = createService(libraryService, dataScopeService,
                uploadSessionService);
        TkMaterialLibraryDO library = library();
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));

        byte[] content = validMp4Bytes("opening".getBytes(StandardCharsets.UTF_8));
        TkUploadSessionRespVO created = service.createSession(10L, "opening.mp4", (long) content.length, "video/mp4");
        service.uploadChunk(created.getUploadId(), 0, new MockMultipartFile("chunk", "0.part",
                "application/octet-stream", java.util.Arrays.copyOfRange(content, 0, 32)));
        service.uploadChunk(created.getUploadId(), 1, new MockMultipartFile("chunk", "1.part",
                "application/octet-stream", java.util.Arrays.copyOfRange(content, 32, content.length)));

        TkGenerationOpeningUploadCompleteRespVO completed = service.complete(created.getUploadId());

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("opening.mp4", completed.getFileName());
        assertEquals(content.length, completed.getFileSize());
        Path output = ((TkChunkUploadStorageServiceImpl) ReflectionTestUtils.getField(service, "chunkStorageService"))
                .getStorageService().resolveRelativePath("tk/100/200/generation-openings/"
                        + created.getUploadId() + "-opening.mp4");
        assertEquals(content.length, Files.size(output));
        assertEquals("COMPLETED", uploadSessionService.session.getStatus());
        assertEquals("local", uploadSessionService.storageMode);
    }

    @Test
    void rejectsCompletedOpeningUploadOwnedByAnotherUser() {
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        StubUploadSessionService uploadSessionService = new StubUploadSessionService();
        TkGenerationOpeningUploadServiceImpl service = createService(libraryService, dataScopeService,
                uploadSessionService);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkUploadSessionDO otherUserSession = session("upload-opening-2");
        otherUserSession.setCreator("99");
        uploadSessionService.session = otherUserSession;

        assertThrows(RuntimeException.class,
                () -> service.validateCompletedUpload("upload-opening-2", 10L));
    }

    private TkGenerationOpeningUploadServiceImpl createService(TkMaterialLibraryService libraryService,
                                                                TkDataScopeService dataScopeService,
                                                                TkUploadSessionService uploadSessionService) {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setChunkSizeBytes(32);
        properties.getUpload().setMaxFileSizeBytes(1024L);
        TkChunkUploadStorageServiceImpl storage = new TkChunkUploadStorageServiceImpl();
        ReflectionTestUtils.setField(storage, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(storage, "generationProperties", properties);
        TkGenerationOpeningUploadServiceImpl service = new TkGenerationOpeningUploadServiceImpl();
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "uploadSessionService", uploadSessionService);
        ReflectionTestUtils.setField(service, "chunkStorageService", storage);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        return service;
    }

    private TkMaterialLibraryDO library() {
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(200L).name("Demo").build();
        library.setTenantId(100L);
        return library;
    }

    private TkUploadSessionDO session(String uploadId) {
        TkUploadSessionDO session = new TkUploadSessionDO()
                .setUploadId(uploadId)
                .setCompanyId(200L)
                .setLibraryId(10L)
                .setFileName("opening.mp4")
                .setFileSize(39L)
                .setContentType("video/mp4")
                .setStorageMode("local")
                .setStatus("UPLOADING");
        session.setTenantId(100L);
        session.setCreator("7");
        return session;
    }

    private byte[] validMp4Bytes(byte[] payload) {
        byte[] bytes = new byte[32 + payload.length];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[20] = 'm';
        bytes[21] = 'o';
        bytes[22] = 'o';
        bytes[23] = 'v';
        System.arraycopy(payload, 0, bytes, 32, payload.length);
        return bytes;
    }

    private static class StubUploadSessionService extends TkUploadSessionService {

        private TkUploadSessionDO session;
        private String storageMode;

        @Override
        public void create(String uploadId, TkMaterialLibraryDO library, String fileName, Long fileSize,
                           String contentType, String storageMode) {
            this.storageMode = storageMode;
            this.session = new TkUploadSessionDO()
                    .setUploadId(uploadId)
                    .setCompanyId(library.getCompanyId())
                    .setLibraryId(library.getId())
                    .setFileName(fileName)
                    .setFileSize(fileSize)
                    .setContentType(contentType)
                    .setStorageMode(storageMode)
                    .setStatus("UPLOADING");
            this.session.setTenantId(library.getTenantId());
            this.session.setCreator("7");
        }

        @Override
        public TkUploadSessionDO validateAccessible(String uploadId) {
            return session;
        }

        @Override
        public TkUploadSessionDO validateCompletedAccessible(String uploadId) {
            return session;
        }

        @Override
        public void markCompleted(String uploadId) {
            session.setStatus("COMPLETED");
        }
    }
}
