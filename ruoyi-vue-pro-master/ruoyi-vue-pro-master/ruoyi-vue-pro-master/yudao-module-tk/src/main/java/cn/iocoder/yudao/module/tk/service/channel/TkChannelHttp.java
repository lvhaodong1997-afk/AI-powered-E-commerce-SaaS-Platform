package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialHttpTransport;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
class TkChannelHttp {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String JSON_TYPE = "application/json";
    static final String FORM = "application/x-www-form-urlencoded";
    private final TkSocialProperties social;

    Proxy proxyFor(URI target) {
        if ("https".equalsIgnoreCase(target.getScheme()) && "api.x.com".equalsIgnoreCase(target.getHost())
                && (target.getPort() == -1 || target.getPort() == 443) && target.getUserInfo() == null) {
            return TkSocialHttpTransport.proxy(social);
        }
        return Proxy.NO_PROXY;
    }

    Response send(String method, String url, String authorization, String contentType, byte[] body,
                  Map<String, String> headers, boolean uncertainWrite) {
        HttpURLConnection connection = null;
        try {
            URI target = URI.create(url);
            Proxy proxy = proxyFor(target);
            URL endpoint = target.toURL();
            connection = (HttpURLConnection) (proxy == Proxy.NO_PROXY ? endpoint.openConnection() : endpoint.openConnection(proxy));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod(method);
            if (authorization != null) connection.setRequestProperty("Authorization", authorization);
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            headers.forEach(connection::setRequestProperty);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            if (status != 308 && (status < 200 || status >= 300)) throw failure(status, uncertainWrite);
            String location = connection.getHeaderField("Location");
            String range = connection.getHeaderField("Range");
            if (status == 308) return new Response(status, location, range, JSON.createObjectNode());
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = bounded(input, 1024 * 1024);
                return new Response(status, location, range, bytes.length == 0 ? JSON.createObjectNode() : JSON.readTree(bytes));
            }
        } catch (IOException e) {
            throw failure(0, uncertainWrite);
        } finally { if (connection != null) connection.disconnect(); }
    }

    JsonNode json(String method, String url, String auth, Object body, boolean write) {
        return send(method, url, auth, body == null ? null : JSON_TYPE,
                body == null ? null : JSON.valueToTree(body).toString().getBytes(StandardCharsets.UTF_8),
                Collections.emptyMap(), write).json;
    }

    static TkSocialPlatformException failure(int status, boolean write) {
        String result = TkChannelProtocol.failureState(status, write);
        return new TkSocialPlatformException("CHANNEL_HTTP_" + status, "Platform request failed (" + status + ")",
                "PENDING".equals(result), "REAUTH_REQUIRED".equals(result), "UNKNOWN".equals(result));
    }

    static byte[] bounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        for (int n; (n = input.read(block)) != -1;) {
            if (n > limit - output.size()) throw new IOException("Response exceeds size limit");
            output.write(block, 0, n);
        }
        return output.toByteArray();
    }

    static class Response {
        final int status;
        final String location, range;
        final JsonNode json;
        Response(int status, String location, String range, JsonNode json) {
            this.status = status; this.location = location; this.range = range; this.json = json;
        }
    }
}
