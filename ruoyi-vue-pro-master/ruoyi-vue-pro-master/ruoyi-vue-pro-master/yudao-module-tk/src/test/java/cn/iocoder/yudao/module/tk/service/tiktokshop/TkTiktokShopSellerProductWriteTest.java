package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokShopSellerProductWriteTest {
    private final TkTiktokShopProperties properties = new TkTiktokShopProperties();
    private final TkTiktokShopSellerAuthService auth = mock(TkTiktokShopSellerAuthService.class);
    private final TkTiktokShopSellerTokenService tokens = mock(TkTiktokShopSellerTokenService.class);
    private final TkTiktokShopSellerApiClient api = mock(TkTiktokShopSellerApiClient.class);
    private final TkTiktokShopSellerProductWriteService service = new TkTiktokShopSellerProductWriteService(properties, auth, tokens, api);
    private final TkTiktokShopSellerShopDO shop = new TkTiktokShopSellerShopDO();
    private final TkTiktokShopSellerAccountDO account = new TkTiktokShopSellerAccountDO();

    @BeforeEach void configure() {
        properties.getSeller().setEnvironment("TEST");
        properties.getSeller().setProductWriteTestShopId("100");
        shop.setId(1L); shop.setShopId("100"); shop.setRegion("US"); shop.setEnvironment("TEST");
        shop.setSellerType("LOCAL"); shop.setTenantId(2L); shop.setCompanyId(3L); shop.setAccountId(4L); shop.setShopCipher("cipher");
        account.setGrantedScopes("[\"seller.product.basic\",\"seller.product.write\"]");
        account.setAuthStatus("AUTHORIZED"); account.setTokenVersion(1L);
        when(auth.requireWritableShop(1L)).thenReturn(shop);
        when(auth.requireReadable(4L)).thenReturn(account);
        when(tokens.getAccessToken(2L, 3L, 4L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("token", 1));
    }

    @Test void deniesAnotherShopBeforeObtainingCredentials() {
        shop.setShopId("101");
        assertThrows(RuntimeException.class, () -> service.update(1L, "123", "456", "USD", "10", "12"));
        verifyNoInteractions(tokens, api);
    }

    @Test void deniesReadOnlyGrantBeforeObtainingCredentials() {
        account.setGrantedScopes("[\"seller.product.basic\"]");
        assertThrows(RuntimeException.class, () -> service.update(1L, "123", "456", "USD", "10", "12"));
        verifyNoInteractions(tokens, api);
    }

    @Test void rejectsStalePriceBeforeWriting() throws Exception {
        when(api.product("token", "cipher", "123")).thenReturn(detail("11.00"));
        assertThrows(RuntimeException.class, () -> service.update(1L, "123", "456", "USD", "10.00", "12.00"));
        verify(api, never()).updatePrice(anyString(), anyString(), anyString(), any());
    }

    @Test void readsBackPriceAfterWriteAndDoesNotReplayAnAmbiguousRequest() throws Exception {
        when(api.product("token", "cipher", "123")).thenReturn(detail("10.00"), detail("12.00"));
        when(api.updatePrice(eq("token"),eq("cipher"),eq("123"),any())).thenThrow(new TkTiktokShopApiException("TRANSPORT_ERROR", null));
        assertEquals("VERIFIED", service.update(1L, "123", "456", "USD", "10", "12").get("result"));
        verify(api).updatePrice(eq("token"),eq("cipher"),eq("123"), any());
    }

    @Test void acceptedCreateRetainsProductIdWhenReadbackIsUnavailable() throws Exception {
        ObjectMapper json = new ObjectMapper();
        when(api.categoryRules("token", "cipher", "123")).thenReturn(json.createObjectNode());
        when(api.categoryAttributes("token", "cipher", "123")).thenReturn(json.createArrayNode());
        when(api.createProduct(eq("token"), eq("cipher"), any())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"product_id\":\"987\"}"), "create-id"));
        when(api.product("token", "cipher", "987")).thenThrow(new TkTiktokShopApiException("TRANSPORT_ERROR", null));
        TkTiktokShopSellerProductWriteService.CreateRequest payload = new TkTiktokShopSellerProductWriteService.CreateRequest();
        payload.setTitle("Test product"); payload.setDescription("Test <tag>"); payload.setCategoryId("123");
        payload.setWarehouseId("654"); payload.setSellerSku("TEST-01"); payload.setImages(Collections.singletonList("bucket/image"));
        payload.setQuantity(1); payload.setPrice("12.34"); payload.setLength("5"); payload.setWidth("6"); payload.setHeight("7"); payload.setWeight("1");
        payload.setIdempotencyKey("10e3dfeb-4aa1-4bbb-911b-1cf5b33938fa");
        Map<String,Object> result = service.create(1L, payload);
        assertEquals("SUBMITTED", result.get("result"));
        assertEquals("987", result.get("productId"));
        assertEquals("create-id", result.get("requestId"));
        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(api).createProduct(eq("token"), eq("cipher"), captured.capture());
        Map<?,?> body = (Map<?,?>) captured.getValue();
        assertEquals(payload.getIdempotencyKey(), body.get("idempotency_key"));
        assertEquals("<p>Test &lt;tag&gt;</p>", body.get("description"));
        Map<?,?> sku = (Map<?,?>) ((java.util.List<?>) body.get("skus")).get(0);
        assertEquals(Map.of("amount", "12.34", "currency", "USD"), sku.get("price"));
    }

    private TkTiktokShopApiClient.Result detail(String price) throws Exception {
        return new TkTiktokShopApiClient.Result(new ObjectMapper().readTree("{\"id\":\"123\",\"status\":\"ACTIVATE\",\"skus\":[{\"id\":\"456\",\"price\":{\"sale_price\":\""+price+"\",\"currency\":\"USD\"}}]}"), "read");
    }
}
