package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkDeepSeekClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generateTextSendsOriginalCopyAndPromptToDeepSeekAndReturnsFinalContent() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readBody(exchange.getRequestBody()));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"新的文案\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        TkDeepSeekClient client = new TkDeepSeekClient();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getDeepseek().setTimeoutSeconds(3);
        properties.getDeepseek().setMaxOutputTokens(321);
        ReflectionTestUtils.setField(client, "generationProperties", properties);
        ReflectionTestUtils.setField(client, "apiKeyConfigService", new MapBackedConfigService(new HashMap<String, String>() {{
            put("api-key", "test-key");
            put("base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            put("model", "deepseek-v4-flash");
            put("timeout-seconds", "3");
            put("retry-count", "0");
            put("retry-delay-ms", "1");
        }}));

        assertEquals("新的文案", client.generateText("原始文案", "改写得更加口语化"));
        assertEquals("Bearer test-key", authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"deepseek-v4-flash\""));
        assertTrue(requestBody.get().contains("\"thinking\":{\"type\":\"disabled\"}"));
        assertTrue(requestBody.get().contains("\"max_tokens\":321"));
        assertTrue(requestBody.get().contains("原始文案"));
        assertTrue(requestBody.get().contains("改写得更加口语化"));
    }

    @Test
    void verifyTextRequestsJsonObjectOutput() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(readBody(exchange.getRequestBody()));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"segments\\\":[]}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        TkDeepSeekClient client = new TkDeepSeekClient();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getDeepseek().setTimeoutSeconds(3);
        ReflectionTestUtils.setField(client, "generationProperties", properties);
        ReflectionTestUtils.setField(client, "apiKeyConfigService", new MapBackedConfigService(new HashMap<String, String>() {{
            put("api-key", "test-key");
            put("base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            put("model", "deepseek-v4-flash");
            put("timeout-seconds", "3");
            put("retry-count", "0");
            put("retry-delay-ms", "1");
        }}));

        assertEquals("{\"segments\":[]}", client.verifyText("原始文案", "只做文字校验"));
        assertTrue(requestBody.get().contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertTrue(requestBody.get().contains("只做文字校验"));
        assertTrue(requestBody.get().contains("标点和语义断句是必做项"));
        assertTrue(requestBody.get().contains("原文没有标点时"));
    }

    private String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
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
            assertEquals(TkApiKeyProviderEnum.DEEPSEEK.getProvider(), provider);
            return values.getOrDefault(configKey, defaultValue);
        }
    }
}
