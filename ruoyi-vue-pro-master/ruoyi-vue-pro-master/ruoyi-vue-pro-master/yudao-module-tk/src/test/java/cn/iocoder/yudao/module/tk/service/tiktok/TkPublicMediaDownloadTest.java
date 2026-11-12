package cn.iocoder.yudao.module.tk.service.tiktok;

import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TkPublicMediaDownloadTest {
    @TempDir Path directory;
    @Test void rejectsRedirectsBeforeAnySecondConnection() throws Exception {
        for(String location : Arrays.asList("http://127.0.0.1/private", "https://10.0.0.1/private", "http://169.254.169.254/latest", "https://public.example/redirect")) {
            AtomicInteger calls=new AtomicInteger();
            assertThrows(IOException.class,()->new TkTiktokMediaTransfer().downloadPublicToFile("https://public.example/video.mp4",directory.resolve("out"),100,
                    host->Collections.singletonList(InetAddress.getByName("8.8.8.8")), client->{
                        assertFalse(client.followRedirects());assertFalse(client.followSslRedirects());assertEquals(Proxy.NO_PROXY,client.proxy());
                        return request->{ calls.incrementAndGet();try{assertEquals("8.8.8.8",client.dns().lookup(request.url().host()).get(0).getHostAddress());}catch(IOException e){throw new RuntimeException(e);}
                            Call call=org.mockito.Mockito.mock(Call.class, invocation -> "execute".equals(invocation.getMethod().getName()) ? (new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("redirect").header("Location",location).body(ResponseBody.create(new byte[0],null)).build()) : null);return call;};
                    }));
            assertEquals(1,calls.get());assertFalse(Files.exists(directory.resolve("out")));
        }
    }
    @Test void validatesEveryAddressAtActualDnsLookupAndBlocksRebinding() throws Exception {
        AtomicInteger lookups=new AtomicInteger(),connections=new AtomicInteger();
        Dns dns=host->Collections.singletonList(InetAddress.getByName(lookups.incrementAndGet()==1?"8.8.8.8":"127.0.0.1"));
        new TkTiktokMediaTransfer().downloadPublicToFile("https://public.example/video.mp4",directory.resolve("ok"),100,dns,client->request->{
            Call call=org.mockito.Mockito.mock(Call.class,invocation->{if(!"execute".equals(invocation.getMethod().getName())) return null;client.dns().lookup(request.url().host());connections.incrementAndGet();return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(ResponseBody.create(new byte[]{1,2,3},null)).build();});return call;});
        assertEquals(3,Files.size(directory.resolve("ok")));
        assertThrows(IOException.class,()->new TkTiktokMediaTransfer().downloadPublicToFile("https://public.example/video.mp4",directory.resolve("bad"),100,dns,client->request->{
            Call call=org.mockito.Mockito.mock(Call.class,invocation->{if(!"execute".equals(invocation.getMethod().getName())) return null;client.dns().lookup(request.url().host());connections.incrementAndGet();return null;});return call;}));
        assertEquals(1,connections.get());
        for(String ip:Arrays.asList("127.0.0.1","10.0.0.1","169.254.169.254","100.64.0.1","::1","fc00::1","ff02::1"))
            assertThrows(UnknownHostException.class,()->TkTiktokMediaTransfer.publicDns(host->Arrays.asList(InetAddress.getByName("8.8.8.8"),InetAddress.getByName(ip))).lookup("public.example"));
    }
}
