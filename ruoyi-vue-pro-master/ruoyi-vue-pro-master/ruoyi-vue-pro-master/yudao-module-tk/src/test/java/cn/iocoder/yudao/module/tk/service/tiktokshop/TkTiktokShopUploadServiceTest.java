package cn.iocoder.yudao.module.tk.service.tiktokshop;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okio.Buffer;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TkTiktokShopUploadServiceTest {
    @TempDir Path dir;
    @Test void chunksMergeTinyTailAndCoverMaximum() {
        long mb=1024*1024;
        assertEquals(Arrays.asList(24*mb),TkTiktokShopUploadService.chunkSizes(24*mb));
        assertEquals(Arrays.asList(20*mb,24*mb),TkTiktokShopUploadService.chunkSizes(44*mb));
        assertEquals(500*mb,TkTiktokShopUploadService.chunkSizes(500*mb).stream().mapToLong(Long::longValue).sum());
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopUploadService.chunkSizes(500*mb+1));
    }
    @Test void uploadUrlIsExactHttpsOfficialHostWithRoutingPreserved() {
        String url="https://open-api.tiktokglobalshop.com/upload/abc?routing=hello%2Fworld";
        assertEquals("hello/world",TkTiktokShopUploadService.uploadUrl(url,"key","token",1).queryParameter("routing"));
        assertEquals("1",TkTiktokShopUploadService.uploadUrl(url,"key","token",1).queryParameter("chunk_num"));
        for(String bad:Arrays.asList("http://open-api.tiktokglobalshop.com/x","https://open-api.tiktokglobalshop.com.evil.test/x","https://u:p@open-api.tiktokglobalshop.com/x","https://open-api.tiktokglobalshop.com:444/x","https://open-api.tiktokglobalshop.com/x#frag","https://open-api.tiktokglobalshop.com/x?app_key=bad"))
            assertThrows(IllegalArgumentException.class,()->TkTiktokShopUploadService.uploadUrl(bad,"key","token",1));
    }
    @Test void multipartIsSignedAndDoesNotReplay503()throws Exception{
        TkTiktokShopProperties p=new TkTiktokShopProperties();p.setEnabled(true);p.setAppKey("app");p.setAppSecret("secret");p.setRedirectUri("https://app.example/callback");p.setFrontendOrigin("https://app.example");p.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
        server.createContext("/affiliate_creator/202505/videos/video_files",exchange->{calls.incrementAndGet();assertEquals("POST",exchange.getRequestMethod());assertEquals("fake-access",exchange.getRequestHeaders().getFirst("x-tts-access-token"));String body=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(body.contains("name=\"data\""));assertTrue(body.contains("synthetic-video"));assertTrue(exchange.getRequestURI().getQuery().contains("sign="));byte[] response="{\"code\":0,\"data\":{\"video_file\":{\"id\":\"file\"}}}".getBytes();if(calls.get()==1){exchange.getResponseHeaders().add("Retry-After","0");exchange.sendResponseHeaders(503,response.length);}else exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
        try{Path video=dir.resolve("video.mp4");Files.write(video,"synthetic-video".getBytes());TkTiktokShopUploadService s=new TkTiktokShopUploadService(p,mock(TkTiktokShopApiClient.class),new ObjectMapper(),new OkHttpClient(),Clock.systemUTC(),"http://127.0.0.1:"+server.getAddress().getPort());TkTiktokShopApiException e=assertThrows(TkTiktokShopApiException.class,()->s.upload(video,"video/mp4","fake-access"));assertNull(e.getCause());assertEquals(1,calls.get());assertEquals("file",s.upload(video,"video/mp4","fake-access"));assertEquals(2,calls.get());}finally{server.stop(0);}
    }
    @Test void fileRangesUseExactOffsetsAndStopAtDeclaredLength()throws Exception{Path video=dir.resolve("range.mp4");Files.write(video,new byte[]{0,1,2,3,4,5,6});Buffer output=new Buffer();TkTiktokShopUploadService.range(video,2,3,"video/mp4").writeTo(output);assertArrayEquals(new byte[]{2,3,4},output.readByteArray());assertThrows(IOException.class,()->TkTiktokShopUploadService.range(video,6,2,"video/mp4").writeTo(new Buffer()));}
    @Test void largeInitUsesCreatorTargetAndRejectsReturnedUntrustedDestination()throws Exception{
        TkTiktokShopProperties p=new TkTiktokShopProperties();p.setEnabled(true);p.setAppKey("app");p.setAppSecret("secret");p.setRedirectUri("https://app.example/callback");p.setFrontendOrigin("https://app.example");p.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));ObjectMapper json=new ObjectMapper();TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
        when(api.post(anyString(),anyString(),any())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"upload_url\":\"https://untrusted.example/upload\",\"upload_token\":\"fake-upload\"}"),null));
        Path path=dir.resolve("large.mp4");try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"rw")){file.setLength(11L*1024*1024);}
        TkTiktokShopUploadService service=new TkTiktokShopUploadService(p,api,json);assertThrows(IllegalArgumentException.class,()->service.upload(path,"video/mp4","fake-access"));
        verify(api).post(eq("/open/202512/file/init"),eq("fake-access"),argThat(body->{com.fasterxml.jackson.databind.JsonNode n=json.valueToTree(body);return "[POST]/affiliate_creator/202603/videos".equals(n.path("target_path").asText())&&"video".equals(n.path("file_type").asText())&&n.path("total_chunk_count").asInt()==1&&n.path("file_size").asLong()==11L*1024*1024&&!n.has("shop_cipher");}));
    }
}
