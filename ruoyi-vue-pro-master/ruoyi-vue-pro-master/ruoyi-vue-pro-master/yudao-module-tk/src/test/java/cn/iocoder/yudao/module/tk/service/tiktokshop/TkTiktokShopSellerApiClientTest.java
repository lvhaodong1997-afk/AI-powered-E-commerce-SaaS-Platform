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
}
