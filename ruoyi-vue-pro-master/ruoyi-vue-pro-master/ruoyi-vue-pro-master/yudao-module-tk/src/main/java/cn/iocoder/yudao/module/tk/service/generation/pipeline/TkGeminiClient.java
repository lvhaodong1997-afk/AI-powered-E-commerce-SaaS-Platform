package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TkGeminiClient {

    private static final String KEY_API_KEY = "api-key";
    private static final String KEY_BASE_URL = "base-url";
    private static final String KEY_TEXT_MODEL = "text-model";
    private static final String KEY_API_FORMAT = "api-format";
    private static final String KEY_TIMEOUT_SECONDS = "timeout-seconds";
    private static final String KEY_RETRY_COUNT = "retry-count";
    private static final String KEY_RETRY_DELAY_MS = "retry-delay-ms";
    private static final String API_FORMAT_GEMINI = "gemini";
    private static final String API_FORMAT_OPENAI = "openai";

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    public String generateText(String prompt) {
        return generateText(prompt, Collections.emptyList());
    }

    public String generateText(String prompt, List<TkAiImageInput> images) {
        String model = getConfig(KEY_TEXT_MODEL, generationProperties.getGemini().getTextModel());
        String apiFormat = getConfig(KEY_API_FORMAT, API_FORMAT_GEMINI);
        JsonNode root;
        if (StrUtil.equalsIgnoreCase(apiFormat, API_FORMAT_OPENAI)) {
            root = postOpenAiCompatible(model, buildOpenAiRequest(model, prompt, images));
            JsonNode text = root.path("choices").path(0).path("message").path("content");
            if (text.isMissingNode() || StrUtil.isBlank(text.asText())) {
                throw new IllegalStateException("Gemini 兼容接口未返回 choices[0].message.content");
            }
            return text.asText().trim();
        }
        root = postGemini(model, buildGeminiRequest(prompt, images));
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || StrUtil.isBlank(text.asText())) {
            throw new IllegalStateException("Gemini 未返回文案内容");
        }
        return text.asText().trim();
    }

    private Map<String, Object> buildGeminiRequest(String prompt, List<TkAiImageInput> images) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", Collections.singletonList(buildGeminiContent(prompt, images)));
        return request;
    }

    private Map<String, Object> buildGeminiContent(String text, List<TkAiImageInput> images) {
        List<Object> parts = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", text);
        parts.add(textPart);
        for (TkAiImageInput image : images) {
            Map<String, Object> inlineData = new LinkedHashMap<>();
            inlineData.put("mimeType", image.getMimeType());
            inlineData.put("data", image.getBase64Data());

            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("inline_data", inlineData);
            parts.add(imagePart);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", parts);
        return content;
    }

    private Map<String, Object> buildOpenAiRequest(String model, String prompt, List<TkAiImageInput> images) {
        List<Object> content = new ArrayList<>();
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("text", prompt);
        content.add(text);
        for (TkAiImageInput image : images) {
            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", "data:" + image.getMimeType() + ";base64," + image.getBase64Data());

            Map<String, Object> imageContent = new LinkedHashMap<>();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", images.isEmpty() ? prompt : content);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", Collections.singletonList(message));
        return request;
    }

    private JsonNode postGemini(String model, Map<String, Object> request) {
        TkGenerationProperties.Gemini gemini = generationProperties.getGemini();
        String apiKey = getConfig(KEY_API_KEY, gemini.getApiKey());
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未配置 Gemini api-key，请维护 tk_api_key_config 或 tk.generation.gemini.api-key");
        }
        String url = StrUtil.format("{}/models/{}:generateContent?key={}",
                StrUtil.removeSuffix(getConfig(KEY_BASE_URL, gemini.getBaseUrl()), "/"), model, apiKey);
        return postJson(url, request, gemini, null);
    }

    private JsonNode postOpenAiCompatible(String model, Map<String, Object> request) {
        TkGenerationProperties.Gemini gemini = generationProperties.getGemini();
        String apiKey = getConfig(KEY_API_KEY, gemini.getApiKey());
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未配置 Gemini api-key，请维护 tk_api_key_config 或 tk.generation.gemini.api-key");
        }
        String url = resolveOpenAiChatCompletionsUrl(getConfig(KEY_BASE_URL, gemini.getBaseUrl()));
        return postJson(url, request, gemini, "Bearer " + apiKey);
    }

    private String resolveOpenAiChatCompletionsUrl(String baseUrl) {
        String normalized = StrUtil.removeSuffix(baseUrl, "/");
        if (StrUtil.endWithIgnoreCase(normalized, "/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private JsonNode postJson(String url, Map<String, Object> request, TkGenerationProperties.Gemini gemini,
                              String authorization) {
        int attempts = resolveRetryAttempts();
        int retryDelayMillis = resolveIntegerConfig(KEY_RETRY_DELAY_MS, "1500");
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HttpRequest httpRequest = withOptionalProxy(HttpRequest.post(url))
                    .header("Content-Type", "application/json");
            if (StrUtil.isNotBlank(authorization)) {
                httpRequest.header("Authorization", authorization);
            }
            try (HttpResponse response = httpRequest.timeout(resolveTimeoutMillis(gemini))
                    .body(JsonUtils.toJsonString(request)).execute()) {
                String body = response.body();
                if (response.getStatus() >= 200 && response.getStatus() < 300) {
                    return JsonUtils.parseTree(body);
                }
                if (!isRetryableStatus(response.getStatus()) || attempt == attempts) {
                    throw new IllegalStateException(StrUtil.format("Gemini 接口调用失败，HTTP {}：{}", response.getStatus(), body));
                }
                sleepBeforeRetry(retryDelayMillis, attempt);
            } catch (IORuntimeException | HttpException ex) {
                if (attempt == attempts) {
                    throw ex;
                }
                sleepBeforeRetry(retryDelayMillis, attempt);
            }
        }
        throw new IllegalStateException("Gemini 接口调用失败");
    }

    private HttpRequest withOptionalProxy(HttpRequest request) {
        Proxy proxy = buildProxy(generationProperties.getReferenceDownload().getProxy());
        return proxy == null ? request : request.setProxy(proxy);
    }

    private String getConfig(String key, String defaultValue) {
        return apiKeyConfigService.getValueOrDefault(TkApiKeyProviderEnum.GEMINI.getProvider(), key, defaultValue);
    }

    private int resolveTimeoutMillis(TkGenerationProperties.Gemini gemini) {
        Integer seconds = resolveIntegerConfig(KEY_TIMEOUT_SECONDS, gemini.getTimeoutSeconds() == null ? "90" : String.valueOf(gemini.getTimeoutSeconds()));
        return seconds * 1000;
    }

    private int resolveRetryAttempts() {
        Integer retryCount = resolveIntegerConfig(KEY_RETRY_COUNT, "2");
        return Math.max(1, retryCount + 1);
    }

    private int resolveIntegerConfig(String key, String defaultValue) {
        String value = getConfig(key, defaultValue);
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(StrUtil.format("Gemini 配置 {} 必须是整数：{}", key, value));
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private Proxy buildProxy(String proxyUrl) {
        if (StrUtil.isBlank(proxyUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (StrUtil.isBlank(host) || port <= 0) {
                throw new IllegalArgumentException("proxy host or port missing");
            }
            Proxy.Type type = StrUtil.equalsIgnoreCase(uri.getScheme(), "socks")
                    || StrUtil.equalsIgnoreCase(uri.getScheme(), "socks5")
                    ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Exception ex) {
            throw new IllegalStateException("TK Gemini proxy format invalid: " + proxyUrl, ex);
        }
    }

    private void sleepBeforeRetry(int retryDelayMillis, int attempt) {
        try {
            Thread.sleep((long) retryDelayMillis * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini 调用重试等待被中断", ex);
        }
    }

}
