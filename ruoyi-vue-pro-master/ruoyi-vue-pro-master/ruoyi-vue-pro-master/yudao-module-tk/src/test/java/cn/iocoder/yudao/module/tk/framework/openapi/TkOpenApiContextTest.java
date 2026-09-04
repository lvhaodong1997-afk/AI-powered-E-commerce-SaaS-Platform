package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TkOpenApiContextTest {

    @Test
    void shouldScopeClientAndAlwaysClearThreadLocal() {
        TkOpenApiPrincipal principal = new TkOpenApiPrincipal("client_b", "Application B", "publish,auth");

        String result = TkOpenApiContext.call(principal, "req_1", () -> {
            assertEquals("client_b", TkOpenApiContext.getRequiredPrincipal().getClientId());
            assertEquals("req_1", TkOpenApiContext.getRequestId());
            return "ok";
        });

        assertEquals("ok", result);
        assertNull(TkOpenApiContext.getPrincipal());
        assertNull(TkOpenApiContext.getRequestId());
    }

    @Test
    void shouldClearContextAfterException() {
        assertThrows(IllegalStateException.class, () -> TkOpenApiContext.call(
                new TkOpenApiPrincipal("client_c", "Application C", "auth"), "req_2",
                () -> { throw new IllegalStateException("boom"); }));

        assertNull(TkOpenApiContext.getPrincipal());
    }

    @Test
    void shouldGenerateOpaquePrefixedIds() {
        String id = TkOpenApiIds.next("conn");

        assertTrue(id.matches("conn_[0-9a-f]{32}"));
    }

}
