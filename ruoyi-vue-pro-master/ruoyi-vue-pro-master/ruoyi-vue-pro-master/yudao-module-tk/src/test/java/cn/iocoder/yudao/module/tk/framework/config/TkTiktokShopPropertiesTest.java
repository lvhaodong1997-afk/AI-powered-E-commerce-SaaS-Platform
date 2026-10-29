package cn.iocoder.yudao.module.tk.framework.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopPropertiesTest {
    @Test
    void disabledByDefaultWithoutInventingCallback() {
        TkTiktokShopProperties properties = new TkTiktokShopProperties();
        assertFalse(properties.isEnabled());
        assertFalse(properties.isConfigured());
        assertNull(properties.getRedirectUri());
        assertEquals("US", properties.getMarket());
        assertEquals(2, properties.getWorkers());
        assertThrows(IllegalStateException.class, properties::requireConfigured);
    }

    @Test
    void acceptsHttpsCallbackAndExactFrontendOrigin() {
        TkTiktokShopProperties properties = configured();
        assertTrue(properties.isConfigured());
        assertDoesNotThrow(properties::requireConfigured);
        properties.setFrontendOrigin("http://localhost:5173");
        assertTrue(properties.isConfigured());
    }

    @Test
    void rejectsBlankCredentialsUnsafeCallbacksAndNonOriginFrontendValues() {
        TkTiktokShopProperties properties = configured();
        for (String callback : new String[]{"http://example.com/callback", "/callback", "https://user:pass@example.com/callback", "https://example.com/callback#fragment"}) {
            properties.setRedirectUri(callback);
            assertFalse(properties.isConfigured(), callback);
            assertThrows(IllegalStateException.class, properties::requireConfigured);
        }
        properties.setRedirectUri("https://example.com/callback");
        for (String origin : new String[]{"https://example.com/path", "https://example.com?x=1", "https://example.com#x", "https://user@example.com", "file:///tmp/file", "*"}) {
            properties.setFrontendOrigin(origin);
            assertFalse(properties.isConfigured(), origin);
        }
        properties.setFrontendOrigin("https://example.com");
        properties.setAppKey(" ");
        assertFalse(properties.isConfigured());
        properties.setAppKey("test-key");
        properties.setAppSecret(" ");
        assertFalse(properties.isConfigured());
    }

    private TkTiktokShopProperties configured() {
        TkTiktokShopProperties properties = new TkTiktokShopProperties();
        properties.setEnabled(true);
        properties.setAppKey("test-key");
        properties.setAppSecret("test-secret");
        properties.setRedirectUri("https://example.com/callback");
        properties.setFrontendOrigin("https://example.com");
        return properties;
    }
}
