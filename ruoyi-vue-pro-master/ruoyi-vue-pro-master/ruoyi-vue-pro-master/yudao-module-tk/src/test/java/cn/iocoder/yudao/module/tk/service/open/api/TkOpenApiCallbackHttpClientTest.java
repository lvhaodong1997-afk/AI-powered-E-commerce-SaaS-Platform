package cn.iocoder.yudao.module.tk.service.open.api;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.DnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenApiCallbackHttpClientTest {

    private final TkOpenApiCallbackHttpClient client = new TkOpenApiCallbackHttpClient();

    @Test
    void shouldPinDnsToTheValidatedAddressSet() throws Exception {
        InetAddress first = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress second = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        DnsResolver resolver = client.createPinnedDnsResolver(
                "callbacks.partner.example", new InetAddress[]{first, second});

        assertArrayEquals(new InetAddress[]{first, second}, resolver.resolve("callbacks.partner.example"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("rebound.internal"));
    }

    @Test
    void shouldDisableAutomaticRedirects() {
        RequestConfig config = client.createRequestConfig();

        assertFalse(config.isRedirectsEnabled());
    }

    @Test
    void shouldKeepOriginalHttpsHostnameInRequestUri() {
        URI callbackUri = URI.create("https://callbacks.partner.example/tk/events");

        HttpPost request = client.createRequest(callbackUri, Collections.emptyMap(), "{}");

        assertEquals(callbackUri, request.getURI());
        assertEquals("callbacks.partner.example", request.getURI().getHost());
    }
}
