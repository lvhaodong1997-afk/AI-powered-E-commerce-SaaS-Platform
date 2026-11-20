package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkChannelHttpTest {
    private final TkSocialProperties social = new TkSocialProperties();
    private final TkChannelHttp http = new TkChannelHttp(social);

    @Test void routesXApiCallsThroughTheConfiguredProxy() {
        social.getProxy().setEnabled(true);
        social.getProxy().setHost(" proxy.example ");
        social.getProxy().setPort(3128);
        for (String path : new String[]{"/oauth2/token", "/users/me", "/media/upload?command=STATUS", "/tweets"}) {
            Proxy proxy = http.proxyFor(URI.create(TkChannelOAuthClient.X_API + path));
            assertEquals(Proxy.Type.HTTP, proxy.type());
            assertEquals(InetSocketAddress.createUnresolved("proxy.example", 3128), proxy.address());
        }
    }

    @Test void routesYouTubeApiCallsThroughTheConfiguredProxy() {
        social.getProxy().setEnabled(true);
        social.getProxy().setHost("proxy.example");
        social.getProxy().setPort(3128);
        for (String url : new String[]{"https://oauth2.googleapis.com/token", "https://www.googleapis.com:443/youtube/v3/channels",
                "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable"}) {
            Proxy proxy = http.proxyFor(URI.create(url));
            assertEquals(Proxy.Type.HTTP, proxy.type(), url);
            assertEquals(InetSocketAddress.createUnresolved("proxy.example", 3128), proxy.address(), url);
        }
    }

    @Test void leavesOtherTargetsAndDisabledProxyOnTheExistingConnectionPath() {
        URI x = URI.create(TkChannelOAuthClient.X_API + "/users/me");
        assertSame(Proxy.NO_PROXY, http.proxyFor(x));
        assertSame(Proxy.NO_PROXY, http.proxyFor(URI.create("https://oauth2.googleapis.com/token")));
        social.getProxy().setEnabled(true);
        for (String url : new String[]{"http://oauth2.googleapis.com/token", "https://oauth2.googleapis.com.example/token",
                "https://www.googleapis.com:444/youtube/v3/channels", "https://user@www.googleapis.com/youtube/v3/channels",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "http://api.x.com/2/users/me", "https://api.x.com.example/2/users/me",
                "https://api.x.com:444/2/users/me", "https://user@api.x.com/2/users/me"}) {
            assertSame(Proxy.NO_PROXY, http.proxyFor(URI.create(url)));
        }
        assertThrows(IllegalStateException.class, () -> http.proxyFor(x));
    }
}
