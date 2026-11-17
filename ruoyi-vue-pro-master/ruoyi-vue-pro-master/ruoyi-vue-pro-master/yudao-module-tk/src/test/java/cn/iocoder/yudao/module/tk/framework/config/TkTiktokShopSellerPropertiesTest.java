package cn.iocoder.yudao.module.tk.framework.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TkTiktokShopSellerPropertiesTest {
    @Test void sellerConfigurationIsIndependentAndClosedByDefault() throws Exception {
        TkTiktokShopProperties p=new TkTiktokShopProperties();
        assertTrue(java.util.Arrays.stream(p.getClass().getMethods()).anyMatch(m->m.getName().equals("isSellerConfigured")),"Seller configuration capability must exist independently of creator");
        assertEquals(false,p.getClass().getMethod("isSellerConfigured").invoke(p));
        assertEquals("PRODUCTION",p.getClass().getMethod("getEnvironment").invoke(p));
    }
    public static TkTiktokShopProperties configuredSeller() {
        TkTiktokShopProperties p=new TkTiktokShopProperties();p.setRedirectUri("https://callback.example/auth/callback");p.setFrontendOrigin("https://ui.example");p.setTokenEncryptionKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        p.getSeller().setEnabled(true);p.getSeller().setAppKey("seller-key");p.getSeller().setAppSecret("seller-secret");p.getSeller().setAuthorizationUrl("https://services.us.tiktokshop.com/open/authorize?service_id=123");return p;
    }
    @Test void independentSellerConfigRejectsUnsafeOrAmbiguousUrls() {
        TkTiktokShopProperties p=configuredSeller();assertTrue(p.isSellerConfigured());assertFalse(p.isConfigured());
        for(String url:new String[]{"http://services.us.tiktokshop.com/open/authorize?service_id=1","https://evil.example/open/authorize?service_id=1","https://services.us.tiktokshop.com.evil.example/open/authorize?service_id=1","https://services.us.tiktokshop.com/open/authorize","https://services.us.tiktokshop.com/open/authorize?service_id=1&service_id=2","https://services.us.tiktokshop.com/open/authorize?service_id=1&redirect_uri=https://evil.example","https://services.tiktokshops.us/open/authorize?service_id=1","https://services.us.tiktokshop.com/open/authorize?service_id=1&state=fixed"}){p.getSeller().setAuthorizationUrl(url);assertFalse(p.isSellerConfigured(),url);}
        p.getSeller().setAuthorizationUrl("https://services.tiktokshop.com/open/authorize?service_id=ROW");assertTrue(p.isSellerConfigured());p.getSeller().setEnvironment("UNKNOWN");assertFalse(p.isSellerConfigured());
    }
}
