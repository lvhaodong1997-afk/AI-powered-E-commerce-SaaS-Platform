package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenApiSignerTest {

    @Test
    void shouldBuildCanonicalRequestAndVerifySignature() {
        byte[] body = "{\"mediaId\":\"media_123\"}".getBytes(StandardCharsets.UTF_8);

        String canonical = TkOpenApiSigner.canonicalRequest("post",
                "/admin-api/tk/open/v1/tiktok/publish/tasks?dryRun=false",
                "1798761600", "nonce-1", body);
        String signature = TkOpenApiSigner.hmacBase64("client-secret", canonical);

        assertEquals("POST\n/admin-api/tk/open/v1/tiktok/publish/tasks?dryRun=false\n1798761600\nnonce-1\n"
                + TkOpenApiSigner.sha256Hex(body), canonical);
        assertTrue(TkOpenApiSigner.matches(signature, signature));
        assertFalse(TkOpenApiSigner.matches(signature, signature + "x"));
    }

    @Test
    void shouldUseEmptyBodyDigestForGetRequests() {
        String canonical = TkOpenApiSigner.canonicalRequest("GET", "/admin-api/tk/open/v1/tiktok/connections",
                "1798761600", "nonce-2", null);

        assertTrue(canonical.endsWith("\n" + TkOpenApiSigner.sha256Hex(new byte[0])));
    }

}
