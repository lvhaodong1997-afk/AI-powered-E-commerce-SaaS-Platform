package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopSellerCatalogServiceTest {
    TkTiktokShopSellerAuthService auth=mock(TkTiktokShopSellerAuthService.class);
    TkTiktokShopSellerTokenService tokens=mock(TkTiktokShopSellerTokenService.class);
    TkTiktokShopSellerApiClient api=mock(TkTiktokShopSellerApiClient.class);
    TkTiktokShopSellerCatalogService catalog=new TkTiktokShopSellerCatalogService(auth,tokens,api);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    TkTiktokShopSellerShopDO shop(){TkTiktokShopSellerShopDO s=new TkTiktokShopSellerShopDO();s.setId(20L);s.setAccountId(10L);s.setTenantId(1L);s.setCompanyId(1L);s.setShopCipher("stored-cipher");s.setStatus("AUTHORIZED");return s;}
    TkTiktokShopSellerAccountDO account(String scopes){TkTiktokShopSellerAccountDO a=new TkTiktokShopSellerAccountDO();a.setId(10L);a.setTenantId(1L);a.setCompanyId(1L);a.setGrantedScopes(scopes);a.setAuthStatus("AUTHORIZED");return a;}
    @Test void productAndOrderPagesStayScopedAndMinimized() {
        when(auth.requireReadableShop(20L)).thenReturn(shop());
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.product.basic\",\"seller.order.info\"]"));
        when(tokens.getAccessToken(1L,1L,10L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("access",4L));
        when(api.searchProducts("access","stored-cipher",20,null)).thenReturn(new TkTiktokShopSellerApiClient.CatalogPage<>(List.of(new TkTiktokShopSellerApiClient.ProductSummary("101","Mug","ACTIVATE")),2L,"NEXT"));
        when(api.searchOrders("access","stored-cipher",20,"CUR")).thenReturn(new TkTiktokShopSellerApiClient.CatalogPage<>(List.of(new TkTiktokShopSellerApiClient.OrderSummary("O1","UNPAID",1800000000L,"12.30","USD")),1L,null));
        SellerProductPage products=catalog.listProducts(20L,1L,null,null);
        assertEquals("101",products.getProducts().get(0).getId());assertEquals(2L,products.getTotal());assertEquals("NEXT",products.getNextPageToken());
        SellerOrderPage orders=catalog.listOrders(20L,1L,20,"CUR");
        assertEquals("O1",orders.getOrders().get(0).getId());assertEquals("12.30",orders.getOrders().get(0).getTotalAmount());
        verify(api).searchProducts("access","stored-cipher",20,null);verify(api).searchOrders("access","stored-cipher",20,"CUR");
    }
    @Test void mismatchedCompanyOrMissingScopeNeverTouchesTokens() {
        when(auth.requireReadableShop(20L)).thenReturn(shop());
        assertThrows(IllegalArgumentException.class,()->catalog.listProducts(20L,2L,20,null));
        verifyNoInteractions(tokens,api);
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.authorization.info\"]"));
        TkTiktokShopApiException missing=assertThrows(TkTiktokShopApiException.class,()->catalog.listProducts(20L,1L,20,null));
        assertEquals("INSUFFICIENT_SCOPE",missing.getCode());verifyNoInteractions(tokens,api);
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.fs.order&fufillment.management\"]"));
        when(tokens.getAccessToken(1L,1L,10L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("access",4L));
        when(api.searchOrders("access","stored-cipher",20,null)).thenReturn(new TkTiktokShopSellerApiClient.CatalogPage<>(List.of(),0L,null));
        assertEquals(0L,catalog.listOrders(20L,null,20,null).getTotal());
    }
    @Test void authorizationFailuresAreReportedOnceWithoutRetry() {
        when(auth.requireReadableShop(20L)).thenReturn(shop());
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.product.basic\"]"));
        when(tokens.getAccessToken(1L,1L,10L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("access",4L));
        when(api.searchProducts(anyString(),anyString(),anyInt(),any())).thenThrow(new TkTiktokShopApiException("105002","req-1"));
        TkTiktokShopApiException error=assertThrows(TkTiktokShopApiException.class,()->catalog.listProducts(20L,1L,20,null));
        assertEquals("105002",error.getCode());assertEquals("req-1",error.getRequestId());
        verify(tokens).reportRejectedToken(1L,1L,10L,4L,error);verify(api,times(1)).searchProducts(anyString(),anyString(),anyInt(),any());
    }
    @Test void catalogPermissionDenialIsNotReportedAsTokenRejection() {
        when(auth.requireReadableShop(20L)).thenReturn(shop());
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.product.basic\"]"));
        when(tokens.getAccessToken(1L,1L,10L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("access",4L));
        when(api.searchProducts(anyString(),anyString(),anyInt(),any())).thenThrow(new TkTiktokShopApiException("105005","req-2"));
        TkTiktokShopApiException error=assertThrows(TkTiktokShopApiException.class,()->catalog.listProducts(20L,1L,20,null));
        assertEquals(TkTiktokShopApiException.SELLER_FEATURE_ACCESS_DENIED,error.getCode());
        assertEquals("req-2",error.getRequestId());
        verify(tokens,never()).reportRejectedToken(anyLong(),anyLong(),anyLong(),anyLong(),any());
    }
    @Test void invalidPageBoundsAreRejectedBeforeTokenUse() {
        when(auth.requireReadableShop(20L)).thenReturn(shop());
        when(auth.requireReadable(10L)).thenReturn(account("[\"seller.product.basic\"]"));
        assertThrows(IllegalArgumentException.class,()->catalog.listProducts(20L,1L,101,null));
        assertThrows(IllegalArgumentException.class,()->catalog.listProducts(20L,1L,20,"x".repeat(4097)));
        verifyNoInteractions(tokens,api);
    }
}
