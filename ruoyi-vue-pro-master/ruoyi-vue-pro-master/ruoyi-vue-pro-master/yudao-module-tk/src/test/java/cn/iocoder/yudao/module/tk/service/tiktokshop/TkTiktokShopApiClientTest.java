package cn.iocoder.yudao.module.tk.service.tiktokshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopApiClientTest {
    private static final Clock CLOCK=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
    @Test void exactUtf8BodySignatureHeaderAndRequestId() throws Exception {
        AtomicReference<byte[]> sent=new AtomicReference<>(); AtomicReference<String> query=new AtomicReference<>();
        AtomicReference<String> token=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/creator/test",exchange->{
            sent.set(read(exchange.getRequestBody())); query.set(exchange.getRequestURI().getRawQuery());
            token.set(exchange.getRequestHeaders().getFirst("x-tts-access-token"));
            byte[] out="{\"code\":0,\"request_id\":\"req-123\",\"data\":{\"ok\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,out.length); exchange.getResponseBody().write(out); exchange.close();
        }); server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopApiClient client=new TkTiktokShopApiClient(TkTiktokShopTokenCipherTest.properties(),new ObjectMapper(),new OkHttpClient(),CLOCK,base,base);
            Map<String,String> body=Collections.singletonMap("title","商品 & video");
            TkTiktokShopApiClient.Result result=client.post("/creator/test","fake-access",body);
            byte[] expected=new ObjectMapper().writeValueAsBytes(body);
            assertArrayEquals(expected,sent.get()); assertEquals("fake-access",token.get());
            Map<String,String> params=new TreeMap<>(); params.put("app_key","fake-key"); params.put("timestamp","1800000000");
            String sign=TkTiktokShopRequestSigner.sign("/creator/test",params,expected,"application/json","fake-secret");
            assertTrue(query.get().contains("sign="+sign)); assertFalse(query.get().contains("fake-access"));
            assertEquals("req-123",result.getRequestId()); assertTrue(result.getData().path("ok").asBoolean());
        } finally { server.stop(0); }
    }
    @Test void upstreamErrorsAreSanitizedAndRedirectsNotFollowed() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/bad",e->{byte[] out="{\"code\":105005,\"request_id\":\"r1\",\"message\":\"secret-token fake-secret\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(400,out.length);e.getResponseBody().write(out);e.close();});
        server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","/bad");e.sendResponseHeaders(302,-1);e.close();}); server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopApiClient client=new TkTiktokShopApiClient(TkTiktokShopTokenCipherTest.properties(),new ObjectMapper(),new OkHttpClient(),CLOCK,base,base);
            TkTiktokShopApiException error=assertThrows(TkTiktokShopApiException.class,()->client.get("/bad","secret-token",Collections.emptyMap()));
            assertEquals("105005",error.getCode()); assertEquals("r1",error.getRequestId()); assertTrue(error.isReauthorizationRequired());
            assertFalse(error.toString().contains("secret-token")); assertFalse(error.toString().contains("fake-secret"));assertNull(error.getCause());
            assertThrows(TkTiktokShopApiException.class,()->client.get("/redirect","secret-token",Collections.emptyMap()));
            assertThrows(IllegalArgumentException.class,()->client.get("//evil.example/path","t",Collections.emptyMap()));
            RuntimeException header=assertThrows(RuntimeException.class,()->client.get("/bad","secret-token\r\nsecret",Collections.emptyMap()));
            assertFalse(header.toString().contains("secret-token"));
        } finally {server.stop(0);}
    }
    @Test void tokenUnixExpiriesAndCreatorScopeAreValidated() throws Exception {
        ObjectMapper json=new ObjectMapper();
        String raw="{\"access_token\":\"a\",\"refresh_token\":\"r\",\"open_id\":\"open\",\"user_type\":1,\"granted_scopes\":[\"creator.video.write\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}";
        TkTiktokShopApiClient.TokenBundle value=TkTiktokShopApiClient.TokenBundle.parse(json.readTree(raw),CLOCK);
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(1800003600),ZoneOffset.UTC),value.getAccessExpireTime());
        assertFalse(value.toString().contains("access_token"));
        for (String invalid:new String[]{raw.replace("\"user_type\":1","\"user_type\":0"),raw.replace("creator.video.write","creator.other"),raw.replace("\"open_id\":\"open\"","\"open_id\":\"\""),raw.replace("1800003600","3600")})
            assertThrows(TkTiktokShopApiException.class,()->TkTiktokShopApiClient.TokenBundle.parse(json.readTree(invalid),CLOCK));
    }
    @Test void tokenEndpointsUseExactGetContractAndProfileCategoryStaysAString() throws Exception {
        AtomicReference<String> request=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v2/token/",e->{
            request.set(e.getRequestMethod()+" "+e.getRequestURI());
            byte[] out=("{\"code\":0,\"data\":{\"access_token\":\"a\",\"refresh_token\":\"r\",\"open_id\":\"token-open\",\"user_type\":1,\"granted_scopes\":[\"creator.video.write\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}}").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();
        });
        server.createContext("/affiliate_creator/202508/profiles",e->{byte[] out="{\"code\":0,\"data\":{\"username\":\"real\",\"selection_region\":\"US\",\"creator_user_open_id\":\"profile-open\",\"user_type\":\"TIKTOK_SHOP_OFFICIAL_ACCOUNT\"}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);e.close();});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopApiClient client=new TkTiktokShopApiClient(TkTiktokShopTokenCipherTest.properties(),new ObjectMapper(),new OkHttpClient(),CLOCK,base,base);
            assertEquals("token-open",client.exchangeCode("fake-code").getOpenId());
            assertTrue(request.get().startsWith("GET /api/v2/token/get?"));assertTrue(request.get().contains("auth_code=fake-code"));assertTrue(request.get().contains("grant_type=authorized_code"));assertTrue(request.get().contains("app_secret=fake-secret"));
            client.refreshToken("fake-refresh");assertTrue(request.get().startsWith("GET /api/v2/token/refresh?"));assertTrue(request.get().contains("refresh_token=fake-refresh"));assertTrue(request.get().contains("grant_type=refresh_token"));
            assertEquals("profile-open",client.getCreatorProfile("a").getCreatorUserOpenId());
        }finally{server.stop(0);}
    }
    @Test void boundsResponseAndSanitizesConnectionFailureWithoutAutomaticRetry() throws Exception {
        java.util.concurrent.atomic.AtomicInteger attempts=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/large",e->{e.sendResponseHeaders(200,2*1024*1024);e.close();});
        server.createContext("/broken",e->{attempts.incrementAndGet();e.close();});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopApiClient client=new TkTiktokShopApiClient(TkTiktokShopTokenCipherTest.properties(),new ObjectMapper(),new OkHttpClient(),CLOCK,base,base);
            assertEquals("RESPONSE_TOO_LARGE",assertThrows(TkTiktokShopApiException.class,()->client.get("/large","a",Collections.emptyMap())).getCode());
            TkTiktokShopApiException failure=assertThrows(TkTiktokShopApiException.class,()->client.post("/broken","fake-access",Collections.singletonMap("title","fake-title")));
            assertEquals(1,attempts.get());assertEquals("TRANSPORT_ERROR",failure.getCode());assertNull(failure.getCause());assertFalse(failure.toString().contains("127.0.0.1"));assertFalse(failure.toString().contains("fake-access"));
        }finally{server.stop(0);}
    }
    @Test void postDoesNotReplayAfter503RetryAfterZero() throws Exception {
        assert503NotReplayed(client->client.post("/creator/test","fake-access",Collections.singletonMap("title","fake-title")));
    }
    @Test void codeExchangeDoesNotReplayAfter503RetryAfterZero() throws Exception {
        assert503NotReplayed(client->client.exchangeCode("fake-code"));
    }
    @Test void rotatingRefreshDoesNotReplayAfter503RetryAfterZero() throws Exception {
        assert503NotReplayed(client->client.refreshToken("fake-refresh"));
    }
    @Test void networkGuardRejectsSecondExchangeAfter421() throws Exception {
        assertSecondExchangeRejected(421);
    }
    @Test void networkGuardRejectsSecondExchangeAfter503() throws Exception {
        assertSecondExchangeRejected(503);
    }
    private static void assertSecondExchangeRejected(int status) throws Exception {
        OkHttpClient transport=TkTiktokShopHttpTransport.createClient();
        Request original=new Request.Builder().url("https://auth.tiktok-shops.com/api/v2/token/get?auth_code=fake-code").build();
        Response response=new Response.Builder().request(original).protocol(Protocol.HTTP_2).code(status).message("test").build();
        // Model two logical calls reusing one Request, then a follow-up on each call.
        for(int logicalCall=0;logicalCall<2;logicalCall++) {
            AtomicReference<Request> marked=new AtomicReference<>();
            Interceptor.Chain application=mock(Interceptor.Chain.class);
            when(application.request()).thenReturn(original);
            when(application.proceed(any(Request.class))).thenAnswer(invocation->{marked.set(invocation.getArgument(0));return response;});
            transport.interceptors().get(0).intercept(application);
            Interceptor.Chain network=mock(Interceptor.Chain.class);
            when(network.request()).thenAnswer(invocation->marked.get());
            when(network.proceed(any(Request.class))).thenReturn(response);
            Interceptor guard=transport.networkInterceptors().get(0);
            assertSame(response,guard.intercept(network));
            java.io.IOException failure=assertThrows(java.io.IOException.class,()->guard.intercept(network));
            verify(network,times(1)).proceed(any(Request.class));
            assertNull(failure.getCause());assertFalse(failure.toString().contains("fake-code"));assertFalse(failure.toString().contains("tiktok-shops.com"));
        }
    }
    private static void assert503NotReplayed(java.util.function.Consumer<TkTiktokShopApiClient> operation) throws Exception {
        java.util.concurrent.atomic.AtomicInteger sends=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            read(e.getRequestBody());
            boolean first=sends.incrementAndGet()==1;
            if(first)e.getResponseHeaders().set("Retry-After","0");
            byte[] out=("{\"code\":0,\"data\":{\"access_token\":\"a\",\"refresh_token\":\"r\",\"open_id\":\"open\",\"user_type\":1,\"granted_scopes\":[\"creator.video.write\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}}").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(first?503:200,out.length);e.getResponseBody().write(out);e.close();
        });server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            TkTiktokShopApiClient client=new TkTiktokShopApiClient(TkTiktokShopTokenCipherTest.properties(),new ObjectMapper(),new OkHttpClient(),CLOCK,base,base);
            TkTiktokShopApiException failure=assertThrows(TkTiktokShopApiException.class,()->operation.accept(client));
            assertEquals(1,sends.get());assertEquals("TRANSPORT_ERROR",failure.getCode());assertNull(failure.getCause());
            for(String secret:new String[]{"fake-access","fake-code","fake-refresh","fake-secret","127.0.0.1"})
                assertFalse(failure.toString().contains(secret));
        }finally{server.stop(0);}
    }
    private static byte[] read(java.io.InputStream input) throws java.io.IOException {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
}
