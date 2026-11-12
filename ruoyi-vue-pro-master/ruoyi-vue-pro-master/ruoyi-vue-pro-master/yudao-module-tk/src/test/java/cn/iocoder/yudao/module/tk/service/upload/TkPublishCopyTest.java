package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkPublishCopyTest {
    @Test void wireCopySignsExactlyTheSentContentTypeAndOssHeaders() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> failure = new java.util.concurrent.atomic.AtomicReference<>();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/copy", exchange -> {
            String type = exchange.getRequestHeaders().getFirst("Content-Type");
            String date = exchange.getRequestHeaders().getFirst("Date");
            String query = exchange.getRequestURI().getRawQuery();
            String canonicalHeaders = query == null ? "x-oss-copy-source:/source/key.mp4\nx-oss-copy-source-if-match:etag\nx-oss-object-acl:default\n" : "";
            String canonical = canonicalHeaders + "/tk-material-factory/key.mp4" + (query == null ? "" : "?" + query);
            String auth = "OSS tk-material-factory-key:" + TkOssRestSigner.sign(exchange.getRequestMethod(), "", type, date, canonical, "secret");
            if (!auth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) failure.set("Wire content type differs from signed content type: " + type);
            byte[] body = "<CopyObjectResult/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            TkOssObjectStorageService storage = new TkOssObjectStorageService() {
                @Override java.net.HttpURLConnection openCopyConnection(String url) throws java.io.IOException {
                    String query = java.net.URI.create(url).getRawQuery();
                    return (java.net.HttpURLConnection) java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/copy" + (query == null ? "" : "?" + query)).toURL().openConnection();
                }
            };
            ReflectionTestUtils.setField(storage, "generationProperties", TkDualBucketStorageTest.configured());
            Map<String,String> headers = new TreeMap<>(); headers.put("x-oss-copy-source", "/source/key.mp4"); headers.put("x-oss-copy-source-if-match", "etag");
            headers.put("x-oss-object-acl", "default");
            storage.copyRequest("PUT", "key.mp4", "", headers, ""); assertNull(failure.get());
            storage.copyRequest("GET", "key.mp4", "acl", Collections.emptyMap(), ""); assertNull(failure.get());
            storage.copyRequest("POST", "key.mp4", "uploads", Collections.singletonMap("Content-Type", "video/webm"), ""); assertNull(failure.get());
            storage.copyRequest("POST", "key.mp4", "uploadId=abc", Collections.emptyMap(), "<CompleteMultipartUpload/>"); assertNull(failure.get());
        } finally { server.stop(0); }
    }
    @Test void smallCopyUsesConditionalSourceIdentity() {
        FakeStorage source = new FakeStorage(1024), target = new FakeStorage(1024);
        target.copyFrom(source, "tk/1/2/old.mp4", "tk/1/2/copy.mp4", source.metadata);
        assertEquals(Collections.singletonList("PUT:"), target.requests);
        assertEquals("\"etag\"", target.headers.get("x-oss-copy-source-if-match"));
        assertEquals("default", target.headers.get("x-oss-object-acl"), "destination must inherit publish bucket read permission");
        assertTrue(target.headers.get("x-oss-copy-source").startsWith("/tk-material-factory/tk/1/2/old.mp4"));
    }
    @Test void largeCopyUsesMultipartAndAbortsOnFailure() {
        FakeStorage source = new FakeStorage(140L * 1024 * 1024), target = new FakeStorage(source.metadata.getContentLength());
        target.copyFrom(source, "tk/1/2/old.mp4", "tk/1/2/copy.mp4", source.metadata);
        assertEquals(5, target.requests.size());
        assertTrue(target.requests.get(0).startsWith("POST:uploads"));
        assertTrue(target.requests.get(4).startsWith("POST:uploadId="));
        assertEquals("video/mp4", target.initiateContentType, "multipart init must retain source MIME");
        assertEquals("default", target.initiateAcl, "multipart destination must inherit publish bucket permission");
        FakeStorage failed = new FakeStorage(source.metadata.getContentLength()); failed.failPart = true;
        assertThrows(IllegalStateException.class, () -> failed.copyFrom(source, "tk/1/2/old.mp4", "tk/1/2/copy.mp4", source.metadata));
        assertTrue(failed.requests.get(failed.requests.size() - 1).startsWith("DELETE:uploadId="));
    }
    static class FakeStorage extends TkOssObjectStorageService {
        final ObjectMetadata metadata; final List<String> requests = new ArrayList<>();
        Map<String,String> headers; String initiateContentType, initiateAcl; boolean failPart;
        FakeStorage(long size) { metadata = new ObjectMetadata(size, null, "\"etag\"", null, "video/mp4"); ReflectionTestUtils.setField(this, "generationProperties", TkDualBucketStorageTest.configured()); }
        @Override public ObjectMetadata headObject(String key) { return metadata; }
        @Override String copyRequest(String method, String key, String query, Map<String,String> h, String body) {
            requests.add(method + ":" + query); headers = h;
            if (query.startsWith("partNumber=")) { if (failPart) throw new IllegalStateException("retryable"); return "<CopyPartResult><ETag>etag</ETag></CopyPartResult>"; }
            if ("uploads".equals(query)) { initiateContentType = h.get("Content-Type"); initiateAcl = h.get("x-oss-object-acl"); return "<InitiateMultipartUploadResult><UploadId>upload1</UploadId></InitiateMultipartUploadResult>"; }
            return query.isEmpty() ? "<CopyObjectResult/>" : "<CompleteMultipartUploadResult/>";
        }
    }
}
