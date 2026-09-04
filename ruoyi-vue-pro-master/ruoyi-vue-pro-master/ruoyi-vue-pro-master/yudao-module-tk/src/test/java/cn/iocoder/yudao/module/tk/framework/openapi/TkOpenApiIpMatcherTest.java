package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenApiIpMatcherTest {

    @Test
    void shouldValidateIpAllowlistRules() {
        assertTrue(TkOpenApiIpMatcher.isValidRules("203.0.113.5,198.51.100.0/24"));
        assertTrue(TkOpenApiIpMatcher.isValidRules(null));
        assertFalse(TkOpenApiIpMatcher.isValidRules("203.0.113.999"));
        assertFalse(TkOpenApiIpMatcher.isValidRules("10.0.0.0/99"));
    }
}
