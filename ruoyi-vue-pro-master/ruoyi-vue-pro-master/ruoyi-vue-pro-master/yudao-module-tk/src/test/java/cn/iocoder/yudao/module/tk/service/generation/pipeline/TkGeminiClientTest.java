package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkGeminiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generateTextRetriesReadTimeoutsBeforeReturningOpenAiCompatibleResponse() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                sleep(1500);
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok after retry\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        TkGeminiClient client = new TkGeminiClient();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getGemini().setTimeoutSeconds(1);
        ReflectionTestUtils.setField(client, "generationProperties", properties);
        ReflectionTestUtils.setField(client, "apiKeyConfigService", new MapBackedConfigService(new HashMap<String, String>() {{
            put("api-key", "test-key");
            put("base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            put("text-model", "test-model");
            put("api-format", "openai");
            put("timeout-seconds", "1");
            put("retry-count", "1");
            put("retry-delay-ms", "1");
        }}));

        assertEquals("ok after retry", client.generateText("hello", Collections.emptyList()));
        assertEquals(2, attempts.get());
    }

    @Test
    void generateTextUsesReferenceDownloadProxyForOpenAiCompatibleRequest() throws Exception {
        AtomicInteger proxyHits = new AtomicInteger();
        try (ServerSocket proxyServer = new ServerSocket(0)) {
            Thread proxyThread = new Thread(() -> {
                try (Socket socket = proxyServer.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     OutputStream os = socket.getOutputStream()) {
                    String requestLine = reader.readLine();
                    if (requestLine == null || !requestLine.contains("POST")) {
                        throw new IOException("unexpected request line: " + requestLine);
                    }
                    String headerLine;
                    while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                        // consume request headers
                    }
                    proxyHits.incrementAndGet();
                    byte[] body = "{\"choices\":[{\"message\":{\"content\":\"proxied ok\"}}]}".getBytes(StandardCharsets.UTF_8);
                    String header = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    os.write(header.getBytes(StandardCharsets.UTF_8));
                    os.write(body);
                    os.flush();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            proxyThread.setDaemon(true);
            proxyThread.start();

            TkGeminiClient client = new TkGeminiClient();
            TkGenerationProperties properties = new TkGenerationProperties();
            properties.getGemini().setTimeoutSeconds(3);
            properties.getReferenceDownload().setProxy("http://127.0.0.1:" + proxyServer.getLocalPort());
            ReflectionTestUtils.setField(client, "generationProperties", properties);
            ReflectionTestUtils.setField(client, "apiKeyConfigService", new MapBackedConfigService(new HashMap<String, String>() {{
                put("api-key", "test-key");
                put("base-url", "http://127.0.0.1:65535");
                put("text-model", "test-model");
                put("api-format", "openai");
                put("timeout-seconds", "3");
                put("retry-count", "0");
                put("retry-delay-ms", "1");
            }}));

            assertEquals("proxied ok", client.generateText("hello", Collections.emptyList()));
            assertEquals(1, proxyHits.get());
        }
    }

    private void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
    }

    private static class MapBackedConfigService implements TkApiKeyConfigService {

        private final Map<String, String> values;

        private MapBackedConfigService(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String getValue(String provider, String configKey) {
            return values.get(configKey);
        }

        @Override
        public String getValueOrDefault(String provider, String configKey, String defaultValue) {
            assertEquals(TkApiKeyProviderEnum.GEMINI.getProvider(), provider);
            return values.getOrDefault(configKey, defaultValue);
        }
    }
}
