package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkChunkUploadStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesCompleteChunksAndCleansTemporaryFiles() throws Exception {
        TkChunkUploadStorageServiceImpl service = createService();
        byte[] content = validMp4Bytes("opening-video".getBytes(StandardCharsets.UTF_8));
        service.createManifest("upload-1", "opening.mp4", (long) content.length, "video/mp4",
                Collections.emptyMap());
        service.saveChunk("upload-1", 0, multipart(content, 0, 32));
        service.saveChunk("upload-1", 1, multipart(content, 32, content.length));

        Path output = service.mergeAndValidate("upload-1", "tk/100/200/generation-openings/upload-1-opening.mp4");

        assertArrayEquals(content, Files.readAllBytes(output));
        service.deleteSessionFiles("upload-1");
        assertTrue(Files.notExists(service.getStorageService().getTmpDir("upload-1")));
    }

    @Test
    void rejectsMissingChunks() {
        TkChunkUploadStorageServiceImpl service = createService();
        byte[] content = validMp4Bytes("opening-video".getBytes(StandardCharsets.UTF_8));
        service.createManifest("upload-2", "opening.mp4", (long) content.length, "video/mp4",
                Collections.emptyMap());
        service.saveChunk("upload-2", 0, multipart(content, 0, 32));

        assertThrows(IllegalStateException.class,
                () -> service.mergeAndValidate("upload-2", "tk/100/200/generation-openings/upload-2-opening.mp4"));
    }

    @Test
    void rejectsInvalidVideoContainer() {
        TkChunkUploadStorageServiceImpl service = createService();
        byte[] content = "not-a-video".getBytes(StandardCharsets.UTF_8);
        service.createManifest("upload-3", "opening.mp4", (long) content.length, "video/mp4",
                Collections.emptyMap());
        service.saveChunk("upload-3", 0, new MockMultipartFile("chunk", "0.part",
                "application/octet-stream", content));

        assertThrows(RuntimeException.class,
                () -> service.mergeAndValidate("upload-3", "tk/100/200/generation-openings/upload-3-opening.mp4"));
    }

    private TkChunkUploadStorageServiceImpl createService() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setChunkSizeBytes(32);
        properties.getUpload().setMaxFileSizeBytes(1024L);
        TkChunkUploadStorageServiceImpl service = new TkChunkUploadStorageServiceImpl();
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        return service;
    }

    private MockMultipartFile multipart(byte[] content, int start, int end) {
        return new MockMultipartFile("chunk", start + ".part", "application/octet-stream",
                java.util.Arrays.copyOfRange(content, start, end));
    }

    private byte[] validMp4Bytes(byte[] payload) {
        byte[] bytes = new byte[24 + payload.length];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[16] = 'm';
        bytes[17] = 'o';
        bytes[18] = 'o';
        bytes[19] = 'v';
        System.arraycopy(payload, 0, bytes, 24, payload.length);
        return bytes;
    }

}
