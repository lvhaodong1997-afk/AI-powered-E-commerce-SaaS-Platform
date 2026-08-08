package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TkDashScopeVoiceEnrollmentClient {

    private static final String DEFAULT_URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization";
    private static final String DEFAULT_MODEL = "voice-enrollment";

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    public String createVoice(String sampleUrl, String prefix) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", "create_voice");
        input.put("target_model", config("voice-clone-target-model", generationProperties.getDashscope().getTtsModel()));
        input.put("prefix", normalizePrefix(prefix));
        input.put("url", sampleUrl);
        JsonNode response = request(input, null);
        String voice = extractVoiceId(response);
        if (StrUtil.isBlank(voice)) {
            throw new IllegalStateException("DashScope 音色复刻未返回 output.voice_id：" + response);
        }
        return voice;
    }

    public void deleteVoice(String voiceCode) {
        if (StrUtil.isBlank(voiceCode)) {
            return;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", "delete_voice");
        input.put("voice_id", voiceCode);
        Map<String, Object> parameters = new LinkedHashMap<>();
        request(input, parameters);
    }

    private JsonNode request(Map<String, Object> input, Map<String, Object> parameters) {
        String apiKey = config("api-key", generationProperties.getDashscope().getApiKey());
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未配置 DashScope api-key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config("voice-clone-model", DEFAULT_MODEL));
        body.put("input", input);
        if (parameters != null) {
            body.put("parameters", parameters);
        }
        try (HttpResponse response = HttpRequest.post(config("voice-clone-url", DEFAULT_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout((generationProperties.getDashscope().getTimeoutSeconds() == null
                        ? 120 : generationProperties.getDashscope().getTimeoutSeconds()) * 1000)
                .body(JsonUtils.toJsonString(body)).execute()) {
            String responseBody = response.body();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("DashScope 音色复刻调用失败，HTTP {}：{}",
                        response.getStatus(), responseBody));
            }
            return JsonUtils.parseTree(responseBody);
        }
    }

    private String config(String key, String fallback) {
        return apiKeyConfigService.getValueOrDefault(TkApiKeyProviderEnum.DASHSCOPE.getProvider(), key, fallback);
    }

    private String normalizePrefix(String value) {
        String normalized = StrUtil.blankToDefault(value, "tkvoice").toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        if (normalized.length() < 2) {
            normalized = "tk" + normalized;
        }
        return normalized.substring(0, Math.min(normalized.length(), 10));
    }

    static String extractVoiceId(JsonNode response) {
        JsonNode output = response.path("output");
        return StrUtil.blankToDefault(output.path("voice_id").asText(), output.path("voice").asText());
    }

}
