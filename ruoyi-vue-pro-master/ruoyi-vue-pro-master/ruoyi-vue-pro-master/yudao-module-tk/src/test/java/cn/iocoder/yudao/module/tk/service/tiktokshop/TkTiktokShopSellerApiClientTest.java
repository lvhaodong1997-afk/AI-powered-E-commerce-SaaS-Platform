package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopSellerPropertiesTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopSellerApiClientTest {
    @Test void sellerWritePathsAreShopScoped() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", e -> {
            request.set(e.getRequestMethod() + " " + e.getRequestURI() + " " + e.getRequestHeaders().getFirst("x-tts-access-token") + " " + body(e));
            byte[] out = "{\"code\":0,\"request_id\":\"write-1\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, out.length); e.getResponseBody().write(out); e.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            TkTiktokShopSellerApiClient client = new TkTiktokShopSellerApiClient(TkTiktokShopSellerPropertiesTest.configuredSeller(), new ObjectMapper(), new OkHttpClient(), Clock.systemUTC(), base, base);
            assertEquals("write-1", client.createProduct("seller", "cipher", Collections.singletonMap("idempotency_key", "request-1")).getRequestId());
            assertTrue(request.get().startsWith("POST /product/202309/products?"));
            assertTrue(request.get().contains("shop_cipher=cipher"));
            assertTrue(request.get().endsWith(" seller {\"idempotency_key\":\"request-1\"}"));
            client.updatePrice("seller", "cipher", "123", Collections.emptyMap());
            assertTrue(request.get().startsWith("POST /product/202309/products/123/prices/update?"));
            assertThrows(IllegalArgumentException.class, () -> client.updatePrice("seller", "cipher", "1/2", Collections.emptyMap()));
        } finally { server.stop(0); }
    }

    @Test void sellerCredentialsSignAuthorizedShopsAndRejectMalformedShopLists() throws Exception {
        AtomicReference<String> payload=new AtomicReference<>("{\"shops\":[{\"id\":\"123\",\"name\":\"Store\",\"region\":\"US\",\"cipher\":\"private\",\"seller_type\":\"LOCAL\",\"code\":\"C\"}]}");
        AtomicReference<String> query=new AtomicReference<>(),header=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/authorization/202309/shops",e->{query.set(e.getRequestURI().getRawQuery());header.set(e.getRequestHeaders().getFirst("x-tts-access-token"));byte[] out=("{\"code\":0,\"data\":"+payload.get()+"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();TkTiktokShopSellerApiClient client=new TkTiktokShopSellerApiClient(TkTiktokShopSellerPropertiesTest.configuredSeller(),new ObjectMapper(),new OkHttpClient(),Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC),base,base);
            assertEquals("123",client.getAuthorizedShops("seller-access").get(0).getId());assertEquals("seller-access",header.get());
            Map<String,String> params=new TreeMap<>();params.put("app_key","seller-key");params.put("timestamp","1800000000");String signature=TkTiktokShopRequestSigner.sign("/authorization/202309/shops",params,null,"application/json","seller-secret");
            assertTrue(query.get().contains("sign="+signature));assertFalse(query.get().contains("seller-access"));assertFalse(query.get().contains("seller-secret"));
            for(String bad:new String[]{"{}","{\"shops\":[{}]}","{\"shops\":[{\"id\":\"1\",\"name\":\"N\",\"region\":\"US\"}]}","{\"shops\":{}}"}){payload.set(bad);TkTiktokShopApiException ex=assertThrows(TkTiktokShopApiException.class,()->client.getAuthorizedShops("seller-access"));assertEquals("INVALID_IDENTITY",ex.getCode());assertFalse(ex.toString().contains("private"));}
        }finally{server.stop(0);}
    }
    @Test void safeSellerResponseNeverSerializesCipherOrOpenId() throws Exception {
        cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO shop=new cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO();shop.setShopCipher("secret-cipher");
        String json=new ObjectMapper().writeValueAsString(new cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.SellerShopResponse(shop));assertFalse(json.contains("cipher"));assertFalse(shop.toString().contains("secret-cipher"));
    }

    @Test void searchProductsAndOrdersSignExactBodyAndDropBuyerFields() throws Exception {
        AtomicReference<String> productQuery=new AtomicReference<>(),productHeader=new AtomicReference<>(),productBody=new AtomicReference<>();
        AtomicReference<String> orderQuery=new AtomicReference<>(),orderHeader=new AtomicReference<>(),orderBody=new AtomicReference<>();
        AtomicReference<String> productPayload=new AtomicReference<>("{\"products\":[{\"id\":101,\"title\":\"Mug\",\"status\":\"ACTIVATE\",\"extra\":\"secret\"}],\"total_count\":2,\"next_page_token\":\"NEXT\"}");
        AtomicReference<String> orderPayload=new AtomicReference<>("{\"orders\":[{\"id\":\"O1\",\"status\":\"UNPAID\",\"create_time\":1800000000,\"payment\":{\"currency\":\"USD\",\"total_amount\":\"12.30\"},\"buyer_email\":\"hidden@example.com\",\"recipient_address\":{\"phone\":\"123\"}}],\"total_count\":1,\"next_page_token\":\"ORD\"}");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/product/202502/products/search",e->{productQuery.set(e.getRequestURI().getRawQuery());productHeader.set(e.getRequestHeaders().getFirst("x-tts-access-token"));productBody.set(body(e));byte[] out=("{\"code\":0,\"request_id\":\"req-p\",\"data\":"+productPayload.get()+"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});
        server.createContext("/order/202309/orders/search",e->{orderQuery.set(e.getRequestURI().getRawQuery());orderHeader.set(e.getRequestHeaders().getFirst("x-tts-access-token"));orderBody.set(body(e));byte[] out=("{\"code\":0,\"request_id\":\"req-o\",\"data\":"+orderPayload.get()+"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopSellerApiClient client=new TkTiktokShopSellerApiClient(TkTiktokShopSellerPropertiesTest.configuredSeller(),new ObjectMapper(),new OkHttpClient(),Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC),base,base);
            TkTiktokShopSellerApiClient.CatalogPage<TkTiktokShopSellerApiClient.ProductSummary> products=client.searchProducts("seller-access","stored-cipher",20,"CUR");
            assertEquals("101",products.getItems().get(0).getId());assertEquals("Mug",products.getItems().get(0).getTitle());assertEquals(2L,products.getTotal());assertEquals("NEXT",products.getNextPageToken());
            assertEquals("{}",productBody.get());assertEquals("seller-access",productHeader.get());
            Map<String,String> productParams=new TreeMap<>();productParams.put("app_key","seller-key");productParams.put("timestamp","1800000000");productParams.put("page_size","20");productParams.put("shop_cipher","stored-cipher");productParams.put("page_token","CUR");
            String productSign=TkTiktokShopRequestSigner.sign("/product/202502/products/search",productParams,"{}".getBytes(StandardCharsets.UTF_8),"application/json","seller-secret");
            assertTrue(productQuery.get().contains("sign="+productSign));assertTrue(productQuery.get().contains("shop_cipher=stored-cipher"));assertFalse(productQuery.get().contains("seller-secret"));assertFalse(productQuery.get().contains("seller-access"));
            String productJson=new ObjectMapper().writeValueAsString(new cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.SellerProductPage(products));
            assertTrue(productJson.contains("\"id\":\"101\""));assertFalse(productJson.contains("extra"));assertFalse(productJson.contains("secret"));
            TkTiktokShopSellerApiClient.CatalogPage<TkTiktokShopSellerApiClient.OrderSummary> orders=client.searchOrders("seller-access","stored-cipher",20,null);
            assertEquals("O1",orders.getItems().get(0).getId());assertEquals(1800000000L,orders.getItems().get(0).getCreateTime());assertEquals("12.30",orders.getItems().get(0).getTotalAmount());assertEquals("USD",orders.getItems().get(0).getCurrency());assertEquals("ORD",orders.getNextPageToken());
            assertEquals("{}",orderBody.get());
            Map<String,String> orderParams=new TreeMap<>();orderParams.put("app_key","seller-key");orderParams.put("timestamp","1800000000");orderParams.put("page_size","20");orderParams.put("shop_cipher","stored-cipher");orderParams.put("sort_field","create_time");orderParams.put("sort_order","DESC");
            String orderSign=TkTiktokShopRequestSigner.sign("/order/202309/orders/search",orderParams,"{}".getBytes(StandardCharsets.UTF_8),"application/json","seller-secret");
            assertTrue(orderQuery.get().contains("sign="+orderSign));assertTrue(orderQuery.get().contains("sort_field=create_time"));
            String orderJson=new ObjectMapper().writeValueAsString(new cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.SellerOrderPage(orders));
            assertTrue(orderJson.contains("\"id\":\"O1\""));assertFalse(orderJson.contains("buyer"));assertFalse(orderJson.contains("hidden@example.com"));assertFalse(orderJson.contains("recipient"));assertFalse(orderJson.contains("phone"));
            productPayload.set("{\"products\":{}}");
            TkTiktokShopApiException bad=assertThrows(TkTiktokShopApiException.class,()->client.searchProducts("seller-access","stored-cipher",20,null));
            assertEquals("INVALID_RESPONSE",bad.getCode());assertEquals("req-p",bad.getRequestId());
        }finally{server.stop(0);}
    }
    @Test void emptyOrderSearchMayOmitOrdersOnlyWhenTotalIsZero() throws Exception {
        AtomicReference<String> payload=new AtomicReference<>("{\"total_count\":0,\"next_page_token\":\"\"}");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/order/202309/orders/search",e->{byte[] out=("{\"code\":0,\"request_id\":\"req-empty\",\"data\":"+payload.get()+"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopSellerApiClient client=new TkTiktokShopSellerApiClient(TkTiktokShopSellerPropertiesTest.configuredSeller(),new ObjectMapper(),new OkHttpClient(),Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC),base,base);
            TkTiktokShopSellerApiClient.CatalogPage<TkTiktokShopSellerApiClient.OrderSummary> page=client.searchOrders("seller-access","stored-cipher",20,null);
            assertTrue(page.getItems().isEmpty());assertEquals(0L,page.getTotal());assertNull(page.getNextPageToken());
            payload.set("{\"total_count\":0}");assertTrue(client.searchOrders("seller-access","stored-cipher",20,null).getItems().isEmpty());
            for(String bad:new String[]{"{}","{\"total_count\":1}","{\"total_count\":0,\"orders\":{}}","{\"total_count\":0,\"orders\":null}","{\"total_count\":0,\"next_page_token\":\"NEXT\"}",
                    "{\"total_count\":0,\"next_page_token\":{}}","{\"total_count\":0,\"next_page_token\":null}","{\"total_count\":0,\"next_page_token\":\" \"}",
                    "{\"total_count\":0,\"next_page_token\":\""+"x".repeat(4097)+"\"}","{\"total_count\":0,\"next_page_token\":\"\u4e2d\"}","{\"total_count\":0,\"page_token\":\"NEXT\"}"}) {
                payload.set(bad);
                TkTiktokShopApiException error=assertThrows(TkTiktokShopApiException.class,()->client.searchOrders("seller-access","stored-cipher",20,null));
                assertEquals("INVALID_RESPONSE",error.getCode());assertEquals("req-empty",error.getRequestId());
            }
        }finally{server.stop(0);}
    }
    @Test void officialBasesAreUsedForTestAndProduction() {
        assertEquals("https://open-api.tiktokglobalshop.com",TkTiktokShopSellerApiClient.apiBaseFor("TEST"));
        assertEquals("https://open-api.tiktokglobalshop.com",TkTiktokShopSellerApiClient.apiBaseFor("PRODUCTION"));
        assertEquals("https://auth.tiktok-shops.com",TkTiktokShopSellerApiClient.authBaseFor("TEST"));
        assertEquals("https://auth.tiktok-shops.com",TkTiktokShopSellerApiClient.authBaseFor("PRODUCTION"));
    }
    @Test void sellerPageBoundsAndOrderScopeAliasAreEnforced() {
        assertEquals(20,TkTiktokShopSellerApiClient.normalizePageSize(null));
        assertEquals(1,TkTiktokShopSellerApiClient.normalizePageSize(1));
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopSellerApiClient.normalizePageSize(0));
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopSellerApiClient.normalizePageSize(101));
        assertNull(TkTiktokShopSellerApiClient.normalizePageToken(null));
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopSellerApiClient.normalizePageToken("x".repeat(4097)));
        assertTrue(TkTiktokShopSellerApiClient.hasOrderScope("[\"seller.fs.order&fufillment.management\"]"));
        assertFalse(TkTiktokShopSellerApiClient.hasOrderScope("[\"seller.authorization.info\"]"));
    }

    private static String body(com.sun.net.httpserver.HttpExchange e) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;java.io.InputStream in=e.getRequestBody();
        while((n=in.read(chunk))>=0)buf.write(chunk,0,n);return new String(buf.toByteArray(),StandardCharsets.UTF_8);
    }
}
