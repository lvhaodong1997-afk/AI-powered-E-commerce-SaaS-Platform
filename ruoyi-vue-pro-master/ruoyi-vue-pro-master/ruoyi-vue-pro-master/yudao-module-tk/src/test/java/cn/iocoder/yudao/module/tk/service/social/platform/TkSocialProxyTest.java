package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TkSocialProxyTest {

    @Test
    void proxyIsDisabledByDefault() {
        assertSame(Proxy.NO_PROXY, TkSocialHttpTransport.proxy(new TkSocialProperties()));
    }

    @Test
    void enabledProxyUsesConfiguredHttpEndpoint() {
        TkSocialProperties properties = new TkSocialProperties();
        properties.getProxy().setEnabled(true);
        properties.getProxy().setHost("127.0.0.1");
        properties.getProxy().setPort(8118);

        Proxy proxy = TkSocialHttpTransport.proxy(properties);
        InetSocketAddress address = (InetSocketAddress) proxy.address();

        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(8118, address.getPort());
    }
}
