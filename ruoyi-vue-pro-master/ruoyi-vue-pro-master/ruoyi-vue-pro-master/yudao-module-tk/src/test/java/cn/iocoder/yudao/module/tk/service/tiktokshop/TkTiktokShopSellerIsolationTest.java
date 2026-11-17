package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopSellerIsolationTest {
    @Test void authSessionsMustSupportStoredTypeRouting() {
        assertTrue(java.util.Arrays.stream(TkTiktokShopAuthService.class.getMethods()).anyMatch(m->m.getName().equals("createSession") && m.getParameterCount()==2),"Session creation must accept explicit authType");
    }
    @Test void sellerStorageAndRefreshMustRemainSeparate() {
        assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerAuthService"));
        assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerTokenService"));
    }
    @Test void sellerTokenParserMustBeDistinctFromCreatorParser() throws Exception {
        assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient"),"Seller API must exist without weakening creator identity checks");
    }
    @Test void sellerCipherMustHaveExplicitIdentityDomain() throws Exception {
        assertTrue(java.util.Arrays.stream(TkTiktokShopTokenCipher.class.getMethods()).anyMatch(m->m.getName().equals("encryptSeller")),"Seller encryption requires explicit environment-separated AAD");
    }
    @Test void sellerAadCannotDecryptAsCreatorOrAnotherEnvironment() {
        TkTiktokShopTokenCipher c=new TkTiktokShopTokenCipher(cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopSellerPropertiesTest.configuredSeller());
        String value=c.encryptSeller("secret-token",1L,"same-app","PRODUCTION","same-open");
        assertEquals("secret-token",c.decryptSeller(value,1L,"same-app","PRODUCTION","same-open"));
        assertThrows(IllegalStateException.class,()->c.decrypt(value,1L,"same-app","same-open"));
        assertThrows(IllegalStateException.class,()->c.decrypt(value,1L,"SELLER:PRODUCTION:8:same-app","SELLER:same-open"));
        assertThrows(IllegalStateException.class,()->c.decryptSeller(value,1L,"same-app","TEST","same-open"));
        assertThrows(IllegalStateException.class,()->c.decryptSeller(value,2L,"same-app","PRODUCTION","same-open"));
    }
    @Test void creatorAndSellerParsersRejectEachOthersTokenTypeAndMissingScope() throws Exception {
        ObjectMapper json=new ObjectMapper();Clock clock=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
        String raw="{\"access_token\":\"a\",\"refresh_token\":\"r\",\"open_id\":\"open\",\"user_type\":0,\"granted_scopes\":[\"seller.authorization.info\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}";
        assertEquals("open",TkTiktokShopApiClient.TokenBundle.parse(json.readTree(raw),clock,0,TkTiktokShopSellerApiClient.REQUIRED_SCOPE).getOpenId());
        assertThrows(TkTiktokShopApiException.class,()->TkTiktokShopApiClient.TokenBundle.parse(json.readTree(raw),clock));
        for(String bad:new String[]{raw.replace("user_type\":0","user_type\":1"),raw.replace("seller.authorization.info","creator.video.write"),raw.replace("1800003600","1")})assertThrows(TkTiktokShopApiException.class,()->TkTiktokShopApiClient.TokenBundle.parse(json.readTree(bad),clock,0,TkTiktokShopSellerApiClient.REQUIRED_SCOPE));
    }
}
