package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopExceptionHandler;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopCreatorBrowseTest {
    private final TkTiktokShopAuthService auth = mock(TkTiktokShopAuthService.class);
    private final TkTiktokShopTokenService tokens = mock(TkTiktokShopTokenService.class);
    private final TkTiktokShopApiClient api = mock(TkTiktokShopApiClient.class);
    private final TkTiktokShopCreatorBrowseService service = new TkTiktokShopCreatorBrowseService(auth, tokens, api);

    @BeforeEach void connectReadPolicy() { doCallRealMethod().when(tokens).read(any(),any()); }

    @Test
    void marketplaceForwardsFiltersAndOnlyReturnsDisplayFields() throws Exception {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setId(1L);
        account.setTenantId(2L);
        account.setCompanyId(3L);
        account.setAuthorizationVersion(0L);
        when(auth.requireReadable(1L)).thenReturn(account);
        when(tokens.getAccessToken(2L, 3L, 1L)).thenReturn(new TkTiktokShopTokenService.AccessToken("fixture", 4, 0L));
        ObjectMapper json = new ObjectMapper();
        when(api.post(eq("/affiliate_creator/202405/open_collaborations/products/search"), eq("fixture"),
                argThat(q -> "20".equals(q.get("page_size")) && "cursor".equals(q.get("page_token"))),
                argThat(body -> json.valueToTree(body).path("title_keywords").path(0).asText().equals("Tea"))))
                .thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"products\":[{\"id\":\"9007199254740993\",\"title\":\"Tea\",\"sales_price\":{\"currency\":\"USD\",\"minimum_amount\":\"3.20\"},\"internal\":\"do not expose\"}],\"next_page_token\":\"next\"}"), "r"));
        TkTiktokShopCreatorBrowseService.Page page = service.list(1L, "marketplace", "cursor", "Tea");
        assertEquals("next", page.getNextPageToken());
        assertEquals("9007199254740993", page.getRows().get(0).getId());
        assertEquals("3.20", page.getRows().get(0).getPrice());
        assertFalse(json.writeValueAsString(page).contains("do not expose"));
    }

    @Test
    void marketplaceAccessDenialDoesNotInvalidatePublishingAuthorization() {
        stubMarketplaceError("105005");
        TkTiktokShopApiException error = assertThrows(TkTiktokShopApiException.class,
                () -> service.list(1L, "marketplace", null, "Tea"));
        assertEquals("r", error.getRequestId());
        assertFalse(error.isReauthorizationRequired());
        verify(tokens, never()).reportRejectedToken(anyLong(), anyLong(), anyLong(), anyLong(), any());
        ResponseEntity<CommonResult<Object>> response = new TkTiktokShopExceptionHandler().upstream(error);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(105005, response.getBody().getCode().intValue());
    }

    @Test
    void samplesDenialDoesNotInvalidatePublishingAuthorization() {
        stubMarketplaceError("105005");
        when(api.post(eq("/affiliate_creator/202412/sample_applications/search"),eq("fixture"),anyMap(),anyMap()))
                .thenThrow(new TkTiktokShopApiException("105005","sample"));
        TkTiktokShopApiException rejected=assertThrows(TkTiktokShopApiException.class,()->service.list(1L,"samples",null,null));
        assertEquals(TkTiktokShopApiException.CREATOR_PERMISSION_DENIED,rejected.getCode());
        assertEquals(HttpStatus.FORBIDDEN,new TkTiktokShopExceptionHandler().upstream(rejected).getStatusCode());
        verify(tokens,never()).reportRejectedToken(anyLong(),anyLong(),anyLong(),anyLong(),any());
    }

    private TkTiktokShopApiException stubMarketplaceError(String code) {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setId(1L);
        account.setTenantId(2L);
        account.setCompanyId(3L);
        account.setAuthorizationVersion(0L);
        when(auth.requireReadable(1L)).thenReturn(account);
        when(tokens.getAccessToken(2L, 3L, 1L)).thenReturn(new TkTiktokShopTokenService.AccessToken("fixture", 4, 0L));
        TkTiktokShopApiException error = new TkTiktokShopApiException(code, "r");
        when(api.post(eq("/affiliate_creator/202405/open_collaborations/products/search"), eq("fixture"), anyMap(), anyMap()))
                .thenThrow(error);
        return error;
    }

    @Test
    void rejectsUnknownDatasetsAndOutOfScopeAccountsBeforeCallingProvider() {
        assertThrows(IllegalArgumentException.class, () -> service.list(1L, "/other", null, null));
        when(auth.requireReadable(1L)).thenThrow(new IllegalArgumentException("out of scope"));
        assertThrows(IllegalArgumentException.class, () -> service.list(1L, "orders", null, null));
        verifyNoInteractions(tokens, api);
    }

    @Test
    void showcaseWritesRequireWritableAccountAndCheckNestedProviderErrors() throws Exception {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setId(1L);
        account.setTenantId(2L);
        account.setCompanyId(3L);
        when(auth.requireWritable(1L)).thenReturn(account);
        when(tokens.getAccessToken(2L, 3L, 1L)).thenReturn(new TkTiktokShopTokenService.AccessToken("fixture", 4, 0L));
        ObjectMapper json = new ObjectMapper();
        when(api.delete(eq("/affiliate_creator/202409/showcases/products"), eq("fixture"),
                eq(Collections.singletonMap("product_ids", Collections.singletonList("123")))))
                .thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"code\":16001001}"), "r"));
        assertThrows(TkTiktokShopApiException.class, () -> service.updateShowcase(1L, "123", false));
        verify(auth).requireWritable(1L);
        verify(api).delete("/affiliate_creator/202409/showcases/products", "fixture",
                Collections.singletonMap("product_ids", Collections.singletonList("123")));
        assertThrows(IllegalArgumentException.class, () -> service.updateShowcase(1L, "/malformed", true));
        verifyNoMoreInteractions(api);
    }
}
