package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopProductServiceTest {
    ObjectMapper json=new ObjectMapper();
    TkTiktokShopAuthService auth=mock(TkTiktokShopAuthService.class);
    TkTiktokShopTokenService tokens=mock(TkTiktokShopTokenService.class);
    TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
    TkTiktokShopProperties props=new TkTiktokShopProperties();
    TkTiktokShopAccountDO account=new TkTiktokShopAccountDO();
    Clock clock=Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"),ZoneOffset.UTC);
    TkTiktokShopProductService service() {
        props.setSelectionProofKey(Base64.getEncoder().encodeToString(new byte[32]));
        account.setId(1L);account.setTenantId(2L);account.setCompanyId(3L);account.setCreator("4");account.setTokenVersion(5L);
        when(auth.requireReadable(1L)).thenReturn(account);
        when(tokens.getAccessToken(2L,3L,1L)).thenReturn(new TkTiktokShopTokenService.AccessToken("fake",5));
        return new TkTiktokShopProductService(auth,tokens,api,props,json,clock);
    }
    @Test void showcaseUsesRealSchemaAndBindsProof() throws Exception {
        TkTiktokShopProductService s=service();
        when(api.get(eq("/affiliate_creator/202405/showcases/products"),eq("fake"),anyMap())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"products\":[{\"id\":\"123\",\"title\":\"Tea\",\"price\":{\"original_price\":{\"minimum_amount\":\"3.20\",\"maximum_amount\":\"4.20\",\"currency\":\"USD\"}},\"main_images\":[{\"url\":\"https://img.example/tea.jpg\"}],\"status\":{\"review_status\":\"APPROVED\",\"inventory_status\":\"IN_STOCK\",\"is_hidden\":false,\"added_status\":\"ADDED\"},\"sale_regions\":[\"US\"]}],\"next_page_token\":\"next\"}"),"r"));
        TkTiktokShopProductService.ProductPage page=s.list(1L,"SHOWCASE",20,"cursor",null,"4");
        assertEquals("next",page.getNextPageToken());assertEquals("3.20",page.getProducts().get(0).getPrice());assertTrue(page.getProducts().get(0).isAvailable());
        String proof=page.getProducts().get(0).getSelectionProof();
        assertEquals("123",s.verifySelection(account,"4","123","SHOWCASE",proof).getId());
        assertThrows(IllegalArgumentException.class,()->s.verifySelection(account,"9","123","SHOWCASE",proof));
        assertThrows(IllegalArgumentException.class,()->s.verifySelection(account,"4","999","SHOWCASE",proof));
        assertThrows(IllegalArgumentException.class,()->s.verifySelection(account,"4","123","SHOWCASE",proof+"x"));
        TkTiktokShopProductService expired=new TkTiktokShopProductService(auth,tokens,api,props,json,Clock.offset(clock,Duration.ofMinutes(11)));
        assertThrows(IllegalArgumentException.class,()->expired.verifySelection(account,"4","123","SHOWCASE",proof));
        verify(api).get(anyString(),eq("fake"),argThat(q->"cursor".equals(q.get("page_token"))&&"SHOWCASE".equals(q.get("origin"))));
    }
    @Test void sourceRulesAndBoundsAreDistinct() throws Exception {
        TkTiktokShopProductService s=service();
        assertThrows(IllegalArgumentException.class,()->s.list(1L,"SHOWCASE",21,null,null,"4"));
        assertThrows(IllegalArgumentException.class,()->s.list(1L,"SHOP",101,null,null,"4"));
        when(api.get(anyString(),eq("fake"),anyMap())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"products\":[{\"id\":\"1\",\"title\":\"Shop\",\"added_status\":\"ADDABLE\\n\",\"price\":{\"amount\":\"4\",\"currency\":\"USD\"}}]}"),null));
        assertTrue(s.list(1L,"SHOP",100,null,"Tea","4").getProducts().get(0).isAvailable());
        assertFalse(s.list(1L,"SHOWCASE",20,null,null,"4").getProducts().get(0).isAvailable());
    }
}
