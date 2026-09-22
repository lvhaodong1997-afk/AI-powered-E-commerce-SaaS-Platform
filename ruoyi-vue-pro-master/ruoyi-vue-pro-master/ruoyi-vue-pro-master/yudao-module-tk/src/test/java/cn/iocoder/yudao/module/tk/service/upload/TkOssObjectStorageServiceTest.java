package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOssObjectStorageServiceTest {
    @Test void identityChecksRejectSameSizeReplacementAndVersionChanges() {
        TkOssObjectStorageClient.ObjectMetadata original = new TkOssObjectStorageClient.ObjectMetadata(1024, null, "etag1", "v1");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> TkOssObjectStorageService.requireIdentity(original, original));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> TkOssObjectStorageService.requireIdentity(original,
                new TkOssObjectStorageClient.ObjectMetadata(1024, null, "etag2", "v1")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> TkOssObjectStorageService.requireIdentity(original,
                new TkOssObjectStorageClient.ObjectMetadata(1024, null, "etag1", "v2")));
    }
    @Test void boundedCopyDoesNotWriteExcessBytes() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> TkOssObjectStorageService.copyBounded(
                new java.io.ByteArrayInputStream(new byte[100]), output, 64));
        assertTrue(output.size() <= 64);
    }
    @Test void ownedKeysRejectCrossTenantAndUrlInputs() {
        TkOssObjectStorageService service = new TkOssObjectStorageService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.validateOwnedKey("tk/100/200/social-media/a.mp4", 100L, 200L));
        for (String key : new String[]{"tk/999/200/social-media/a.mp4", "tk/100/200/../a.mp4", "https://evil.example/a.mp4"})
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.validateOwnedKey(key, 100L, 200L));
    }

    @Test
    void resolveReadUrlSignsHistoricalPrivateOssUrl() {
        TkOssObjectStorageService service = new TkOssObjectStorageService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss");
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss-cn-example.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://cdn.example.com");
        properties.getUpload().getOss().setAccessKeyId("access-key");
        properties.getUpload().getOss().setAccessKeySecret("access-secret");
        properties.getUpload().getOss().setReadUrlExpireSeconds(3600);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        String resolved = service.resolveReadUrl(
                "https://cdn.example.com/tk/100/200/generation-openings/opening%20one.mp4");

        assertTrue(resolved.startsWith(
                "https://cdn.example.com/tk/100/200/generation-openings/opening%20one.mp4?OSSAccessKeyId=access-key&Expires="));
        assertTrue(resolved.contains("&Signature="));
    }

    @Test
    void resolveReadUrlLeavesExternalUrlUnchanged() {
        TkOssObjectStorageService service = new TkOssObjectStorageService();

        assertEquals("https://example.com/opening.mp4", service.resolveReadUrl("https://example.com/opening.mp4"));
    }

    @Test
    void deleteRequestDoesNotAddUnsignedDefaultContentType() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/object.mp4", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpURLConnection connection = TkOssObjectStorageService.openDeleteConnection(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/object.mp4");
            try {
                assertEquals(204, connection.getResponseCode());
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop(0);
        }

        assertNull(contentType.get());
    }

}
