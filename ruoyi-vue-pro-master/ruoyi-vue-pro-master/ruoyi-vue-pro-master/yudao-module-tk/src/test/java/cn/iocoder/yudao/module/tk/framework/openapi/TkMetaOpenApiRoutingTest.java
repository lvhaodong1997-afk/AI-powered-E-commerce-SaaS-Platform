package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TkMetaOpenApiRoutingTest {
    private final TkOpenApiAuthenticationFilter filter = new TkOpenApiAuthenticationFilter(null, null, 8388608);

    @Test
    void platformRoutesRequireDistinctSignedPermissions() {
        for (String platform : new String[]{"facebook", "instagram"}) {
            String root = "/admin-api/tk/open/v1/" + platform;
            assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", root + "/connections")));
            assertEquals(platform + ".auth", ReflectionTestUtils.invokeMethod(filter, "requiredPermission", root + "/connections"));
            assertEquals(platform + ".publish", ReflectionTestUtils.invokeMethod(filter, "requiredPermission", root + "/publish/tasks"));
            assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", root + "/provider-media/metatask_0123456789abcdef0123456789abcdef")));
            assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", root + "/provider-media/metatask_0123456789abcdef0123456789abcdef")));
        }
    }
}
