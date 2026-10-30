package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopTokenCipherTest {
    static TkTiktokShopProperties properties() {
        TkTiktokShopProperties p = new TkTiktokShopProperties();
        p.setEnabled(true); p.setAppKey("fake-key"); p.setAppSecret("fake-secret");
        p.setRedirectUri("https://callback.example/tk/tiktok-shop/auth/callback");
        p.setFrontendOrigin("https://ui.example");
        p.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return p;
    }
    @Test void randomizedRoundtripAndContextIsolation() {
        TkTiktokShopTokenCipher cipher = new TkTiktokShopTokenCipher(properties());
        String encrypted = cipher.encrypt("secret-access", 1L,"app","open");
        assertFalse(encrypted.contains("secret-access"));
        assertNotEquals(encrypted, cipher.encrypt("secret-access",1L,"app","open"));
        assertEquals("secret-access",cipher.decrypt(encrypted,1L,"app","open"));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(encrypted,2L,"app","open"));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(encrypted,1L,"other","open"));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(encrypted,1L,"app","other"));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(encrypted+"!",1L,"app","open"));
    }
    @Test void missingDedicatedKeyFailsClosedWithoutSecretFallback() {
        TkTiktokShopProperties p=properties(); p.setTokenEncryptionKey(null);
        assertThrows(IllegalStateException.class,()->new TkTiktokShopTokenCipher(p).encrypt("a",1L,"app","open"));
    }
}
