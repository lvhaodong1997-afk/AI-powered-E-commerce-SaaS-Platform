package cn.iocoder.yudao.module.tk.service.open.api;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.DnsResolver;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class TkOpenApiCallbackHttpClient {

    private static final int TIMEOUT_MILLIS = 10_000;

    public int post(URI callbackUri, InetAddress[] validatedAddresses, Map<String, String> headers, String body)
            throws IOException {
        RequestConfig requestConfig = createRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom()
                .setDnsResolver(createPinnedDnsResolver(callbackUri.getHost(), validatedAddresses))
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build()) {
            HttpPost request = createRequest(callbackUri, headers, body);
            try (CloseableHttpResponse response = client.execute(request)) {
                return response.getStatusLine().getStatusCode();
            }
        }
    }

    DnsResolver createPinnedDnsResolver(String callbackHost, InetAddress[] validatedAddresses) {
        if (validatedAddresses == null || validatedAddresses.length == 0) {
            throw new IllegalArgumentException("Callback URL host cannot be resolved");
        }
        InetAddress[] pinnedAddresses = validatedAddresses.clone();
        return requestedHost -> {
            if (!callbackHost.equalsIgnoreCase(requestedHost)) {
                throw new UnknownHostException("Callback DNS lookup is not allowed for " + requestedHost);
            }
            return pinnedAddresses.clone();
        };
    }

    RequestConfig createRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(TIMEOUT_MILLIS)
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setSocketTimeout(TIMEOUT_MILLIS)
                .build();
    }

    HttpPost createRequest(URI callbackUri, Map<String, String> headers, String body) {
        HttpPost request = new HttpPost(callbackUri);
        headers.forEach(request::setHeader);
        request.setEntity(new StringEntity(body,
                ContentType.create("application/json", StandardCharsets.UTF_8)));
        return request;
    }
}
