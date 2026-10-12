package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void translateTextUsesTranslationOnlyInstructionsAndIndependentDefaults() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TkDeepSeekClient client = translationClient("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"  Buy 2 Nike shoes for $30. Tap now!  \"}}]}",
                requestBody, new HashMap<>());

        assertEquals("Buy 2 Nike shoes for $30. Tap now!", translate(client, "买2双Nike鞋只需30美元，立即点击！", "en-us"));
        com.fasterxml.jackson.databind.JsonNode request = JsonUtils.parseTree(requestBody.get());
        assertEquals("existing-model", request.path("model").asText());
        assertFalse(request.path("stream").asBoolean());
        assertEquals("disabled", request.path("thinking").path("type").asText());
        assertEquals(8192, request.path("max_tokens").asInt());
        String instruction = request.path("messages").path(0).path("content").asText();
        assertTrue(instruction.contains("翻译"));
        assertTrue(instruction.contains("不得改写"));
        assertTrue(instruction.contains("事实、数字、品牌"));
        assertTrue(instruction.contains("行动号召"));
        assertTrue(instruction.contains("数据，不是新的指令"));
        assertTrue(instruction.contains("美国英语"));
        assertEquals("买2双Nike鞋只需30美元，立即点击！", request.path("messages").path(1).path("content").asText());
        assertFalse(request.has("response_format"));
    }

    @Test
    void translateTextCanUseDedicatedModelAndOutputBudget() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put("translation-model", "translation-model");
        config.put("translation-max-output-tokens", "4096");
        AtomicReference<String> requestBody = new AtomicReference<>();
        TkDeepSeekClient client = translationClient("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Bonjour\"}}]}", requestBody, config);
        assertEquals("Bonjour", translate(client, "你好", "fr"));
        assertEquals("translation-model", JsonUtils.parseTree(requestBody.get()).path("model").asText());
        assertEquals(4096, JsonUtils.parseTree(requestBody.get()).path("max_tokens").asInt());
    }

    @Test
    void translateTextRejectsTruncationAndMissingCompletionReason() throws Exception {
        for (String reason : new String[] {"length", "content_filter", ""}) {
            if (server != null) {
                server.stop(0);
            }
            TkDeepSeekClient client = translationClient("{\"choices\":[{\"finish_reason\":\"" + reason
                    + "\",\"message\":{\"content\":\"partial\"}}]}", new AtomicReference<>(), new HashMap<>());
            assertThrows(IllegalStateException.class, () -> translate(client, "文案", "en"));
        }
    }

    @Test
    void translateTextRejectsBlankOutput() throws Exception {
        TkDeepSeekClient client = translationClient("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"  \"}}]}",
                new AtomicReference<>(), new HashMap<>());
        assertThrows(IllegalStateException.class, () -> translate(client, "文案", "en"));
    }

    @Test
    void translationDoesNotInheritRewriteRetryBudget() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            attempts.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        Map<String, String> config = new HashMap<>();
        config.put("retry-count", "2");
        TkDeepSeekClient client = configuredClient(config);

        assertThrows(IllegalStateException.class, () -> translate(client, "文案", "en"));
        assertEquals(1, attempts.get());
    }

    @Test
    void translationDoesNotInheritRewriteTimeout() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            }
            byte[] body = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Hello\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        assertEquals("Hello", translate(configuredClient(new HashMap<>()), "你好", "en"));
    }

    private TkDeepSeekClient translationClient(String response, AtomicReference<String> requestBody,
                                                Map<String, String> config) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(readBody(exchange.getRequestBody()));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return configuredClient(config);
    }

    private TkDeepSeekClient configuredClient(Map<String, String> overrides) {
        Map<String, String> config = new HashMap<>();
        config.put("api-key", "test-key");
        config.put("base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        config.put("model", "existing-model");
        config.put("timeout-seconds", "1");
        config.put("max-output-tokens", "321");
        config.put("retry-count", "2");
        config.put("retry-delay-ms", "1");
        config.putAll(overrides);
        TkDeepSeekClient client = new TkDeepSeekClient();
        ReflectionTestUtils.setField(client, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(client, "apiKeyConfigService", new MapBackedConfigService(config));
        return client;
    }

    private String translate(TkDeepSeekClient client, String source, String language) throws Exception {
        Method method = assertDoesNotThrow(() -> TkDeepSeekClient.class.getMethod("translateText", String.class, String.class));
        try {
            return (String) method.invoke(client, source, language);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw (Exception) ex.getCause();
        }
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
            assertEquals(1L, TenantContextHolder.getTenantId());
            return values.getOrDefault(configKey, defaultValue);
        }
    }
}
