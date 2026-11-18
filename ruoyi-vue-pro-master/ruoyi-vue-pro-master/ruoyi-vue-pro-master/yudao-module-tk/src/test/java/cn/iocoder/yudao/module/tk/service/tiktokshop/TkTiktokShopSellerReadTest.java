package cn.iocoder.yudao.module.tk.service.tiktokshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
