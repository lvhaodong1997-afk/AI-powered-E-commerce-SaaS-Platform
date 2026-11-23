package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopExceptionHandler;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopSellerReadTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TkTiktokShopSellerApiClient api = mock(TkTiktokShopSellerApiClient.class);
    private final TkTiktokShopSellerReadService service = new TkTiktokShopSellerReadService(api, null, null);

    @Test void stripsBuyerDataFromOrders() throws Exception {
        when(api.post("/order/202309/orders/search", "token", Map.of("shop_cipher", "cipher", "page_size", "20"), Map.of()))
                .thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"orders\":[{\"id\":\"123\",\"buyer_email\":\"private\",\"payment\":{\"total_amount\":\"12.00\",\"currency\":\"USD\"}}]}"), "request"));
        TkTiktokShopSellerReadService.Page result = service.read(TkTiktokShopSellerReadService.View.ORDERS, "token", "cipher", null, null, null, null);
        assertEquals(Map.of("id", "123", "amount", "12.00", "currency", "USD"), result.getRows().get(0));
    }

    @Test void rejectsEmptyAnalyticsDateRangeBeforeSending() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        assertThrows(IllegalArgumentException.class, () -> service.read(TkTiktokShopSellerReadService.View.VIDEOS, "token", "cipher", null, date, date, null));
        verifyNoInteractions(api);
    }

    @Test void videoMetricsUseDocumentedCountersAndPreserveMissingValues() throws Exception {
        when(api.get(eq("/analytics/202605/shop_videos/performance"),eq("token"),any())).thenReturn(
                new TkTiktokShopApiClient.Result(json.readTree("{\"videos\":[{\"id\":\"v\",\"views\":20,\"product_impressions\":5,\"product_clicks\":0,\"gmv\":{\"amount\":\"1.23\",\"currency\":\"USD\"}}],\"next_page_token\":\"next\",\"latest_available_date\":\"2026-06-01\"}"),null));
        TkTiktokShopSellerReadService.Page page=service.read(TkTiktokShopSellerReadService.View.VIDEOS,"token","cipher",null,LocalDate.of(2026,6,1),LocalDate.of(2026,6,2),null);
        assertEquals(Map.of("id","v","views","20","impressions","5","clicks","0","amount","1.23","currency","USD"),page.getRows().get(0));
        assertEquals("next",page.getNextPageToken());assertEquals("2026-06-01",page.getLatestAvailableDate());
    }

    @Test void deniedStatementsDoNotBlockOtherSellerQueries() throws Exception {
        TkTiktokShopSellerAuthService auth=mock(TkTiktokShopSellerAuthService.class);
        TkTiktokShopSellerTokenService tokens=mock(TkTiktokShopSellerTokenService.class);
        TkTiktokShopSellerShopDO shop=new TkTiktokShopSellerShopDO();
        shop.setTenantId(1L);shop.setCompanyId(2L);shop.setAccountId(3L);shop.setShopCipher("cipher");
        when(auth.requireReadableShop(4L)).thenReturn(shop);
        when(tokens.getAccessToken(1L,2L,3L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("token",5L));
        when(api.get(eq("/finance/202309/statements"),eq("token"),anyMap()))
                .thenThrow(new TkTiktokShopApiException("105005","finance-request"));
        TkTiktokShopSellerReadService reader=new TkTiktokShopSellerReadService(api,auth,tokens);

        TkTiktokShopApiException denied=assertThrows(TkTiktokShopApiException.class,
                () -> reader.query(4L,TkTiktokShopSellerReadService.View.STATEMENTS,null,null,null,null));
        assertEquals("finance-request",denied.getRequestId());
        ResponseEntity<CommonResult<Object>> response=new TkTiktokShopExceptionHandler().upstream(denied);
        assertEquals(HttpStatus.FORBIDDEN,response.getStatusCode());
        assertEquals(105005,response.getBody().getCode());
        verify(tokens,never()).reportRejectedToken(anyLong(),anyLong(),anyLong(),anyLong(),any());
        for (TkTiktokShopSellerReadService.View view : new TkTiktokShopSellerReadService.View[]{TkTiktokShopSellerReadService.View.PRODUCTS,
                TkTiktokShopSellerReadService.View.ORDERS,TkTiktokShopSellerReadService.View.PACKAGES}) {
            when(api.post(eq(view.path),eq("token"),anyMap(),anyMap())).thenReturn(
                    new TkTiktokShopApiClient.Result(json.readTree("{\""+view.array+"\":[{\"id\":\"1\"}]}"),"ok"));
            assertEquals("1",reader.query(4L,view,null,null,null,null).getRows().get(0).get("id"));
        }
    }

    @Test void analyticsDenialDoesNotInvalidateSellerAuthorizationButTokenRejectionStillDoes() {
        TkTiktokShopSellerAuthService auth=mock(TkTiktokShopSellerAuthService.class);
        TkTiktokShopSellerTokenService tokens=mock(TkTiktokShopSellerTokenService.class);
        TkTiktokShopSellerShopDO shop=new TkTiktokShopSellerShopDO();
        shop.setTenantId(1L);shop.setCompanyId(2L);shop.setAccountId(3L);shop.setShopCipher("cipher");
        when(auth.requireReadableShop(4L)).thenReturn(shop);
        when(tokens.getAccessToken(1L,2L,3L)).thenReturn(new TkTiktokShopSellerTokenService.AccessToken("token",5L));
        TkTiktokShopSellerReadService reader=new TkTiktokShopSellerReadService(api,auth,tokens);
        LocalDate start=LocalDate.of(2026,6,1);
        TkTiktokShopApiException denied=new TkTiktokShopApiException("105005","request-1");
        TkTiktokShopApiException invalid=new TkTiktokShopApiException("105001","request-2");
        when(api.get(eq("/analytics/202605/shop_videos/performance"),eq("token"),anyMap())).thenThrow(denied,invalid);

        TkTiktokShopApiException result=assertThrows(TkTiktokShopApiException.class,
                () -> reader.query(4L,TkTiktokShopSellerReadService.View.VIDEOS,null,start,start.plusDays(1),null));
        verify(tokens,never()).reportRejectedToken(anyLong(),anyLong(),anyLong(),anyLong(),any());
        assertEquals("request-1",result.getRequestId());
        assertEquals(HttpStatus.FORBIDDEN,new TkTiktokShopExceptionHandler().upstream(result).getStatusCode());
        TkTiktokShopApiException reconnect=assertThrows(TkTiktokShopApiException.class,
                () -> reader.query(4L,TkTiktokShopSellerReadService.View.VIDEOS,null,start,start.plusDays(1),null));
        assertEquals("request-2",reconnect.getRequestId());
        assertEquals(HttpStatus.CONFLICT,new TkTiktokShopExceptionHandler().upstream(reconnect).getStatusCode());
        assertEquals(1060000018,new TkTiktokShopExceptionHandler().upstream(reconnect).getBody().getCode());
        verify(tokens).reportRejectedToken(1L,2L,3L,5L,invalid);
    }
}
