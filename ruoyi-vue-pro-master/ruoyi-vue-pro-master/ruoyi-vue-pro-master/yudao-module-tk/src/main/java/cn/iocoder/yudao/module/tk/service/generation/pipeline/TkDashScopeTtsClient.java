package cn.iocoder.yudao.module.tk.service.generation.pipeline;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TkDashScopeTtsClient implements TkVoiceTtsClient {

    private static final String KEY_API_KEY = "api-key";
    private static final String KEY_TTS_URL = "tts-url";
    private static final String KEY_TTS_MODEL = "tts-model";
    private static final String KEY_VOICE = "voice";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_INSTRUCTION = "instruction";

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    @Override
    public String provider() {
        return TkTtsProviderEnum.DASHSCOPE;
    }

    @Override
    public String audioFormat() {
        return getAudioFormat();
    }

    @Override
    public byte[] synthesize(TkVoiceSynthesisRequest request) {
        return synthesize(request.getText(), request.getVoiceCode(), request.getTargetLanguage());
    }

    public byte[] synthesize(String text, String voice, String targetLanguage) {
        TkGenerationProperties.DashScope dashScope = generationProperties.getDashscope();
        String apiKey = getConfig(KEY_API_KEY, dashScope.getApiKey());
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未配置 DashScope api-key，请维护 tk_api_key_config 或 tk.generation.dashscope.api-key");
        }

        String resolvedVoice = StrUtil.blankToDefault(voice, getConfig(KEY_VOICE, dashScope.getVoice()));
        if (StrUtil.isBlank(resolvedVoice)) {
            throw new IllegalStateException("未配置 DashScope voice，请在任务 voiceCode/video_id 或 tk_api_key_config 中配置复刻音色 ID");
        }

        String audioUrl = requestAudioUrl(text, resolvedVoice, targetLanguage, apiKey, dashScope);
        return downloadAudio(audioUrl, dashScope);
    }

    public String getAudioFormat() {
        return getConfig(KEY_FORMAT, generationProperties.getDashscope().getFormat());
    }

    private String requestAudioUrl(String text, String voice, String targetLanguage, String apiKey, TkGenerationProperties.DashScope dashScope) {
        String url = getConfig(KEY_TTS_URL, dashScope.getTtsUrl());
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(resolveTimeoutMillis(dashScope))
                .body(JsonUtils.toJsonString(buildRequest(text, voice, targetLanguage, dashScope)))
                .execute()) {
            String body = response.body();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("DashScope TTS 调用失败，HTTP {}：{}", response.getStatus(), body));
            }
            JsonNode root = JsonUtils.parseTree(body);
            JsonNode audioUrl = root.path("output").path("audio").path("url");
            if (audioUrl.isMissingNode() || StrUtil.isBlank(audioUrl.asText())) {
                throw new IllegalStateException("DashScope TTS 未返回 output.audio.url：" + body);
            }
            return audioUrl.asText();
        }
    }

    private Map<String, Object> buildRequest(String text, String voice, String targetLanguage, TkGenerationProperties.DashScope dashScope) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", text);
        input.put("voice", voice);
        input.put("format", getConfig(KEY_FORMAT, dashScope.getFormat()));
        input.put("sample_rate", dashScope.getSampleRate());
        input.put("volume", dashScope.getVolume());
        input.put("rate", dashScope.getRate());
        input.put("pitch", dashScope.getPitch());
        String language = resolveLanguage(text, targetLanguage, dashScope);
        if (StrUtil.isNotBlank(language)) {
            input.put("language_hints", Collections.singletonList(language));
        }
        String instruction = buildInstruction(targetLanguage, dashScope);
        if (StrUtil.isNotBlank(instruction)) {
            input.put("instruction", instruction);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", getConfig(KEY_TTS_MODEL, dashScope.getTtsModel()));
        request.put("input", input);
        return request;
    }

    private byte[] downloadAudio(String audioUrl, TkGenerationProperties.DashScope dashScope) {
        try (HttpResponse response = HttpRequest.get(audioUrl)
                .timeout(resolveTimeoutMillis(dashScope))
                .execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("DashScope 音频下载失败，HTTP {}：{}", response.getStatus(), response.body()));
            }
            return response.bodyBytes();
        }
    }

    private String getConfig(String key, String defaultValue) {
        return apiKeyConfigService.getValueOrDefault(TkApiKeyProviderEnum.DASHSCOPE.getProvider(), key, defaultValue);
    }

    private String resolveLanguage(String text, String targetLanguage, TkGenerationProperties.DashScope dashScope) {
        if (StrUtil.isNotBlank(targetLanguage)) {
            return TkLanguageSupport.ttsLanguageHint(targetLanguage);
        }
        String configuredLanguage = getConfig(KEY_LANGUAGE, dashScope.getLanguage());
        if (StrUtil.isBlank(configuredLanguage)) {
            return null;
        }
        if (!StrUtil.equalsIgnoreCase(configuredLanguage, "auto")) {
            return configuredLanguage;
        }
        if (text != null && text.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)) {
            return "zh";
        }
        if (text != null && text.codePoints().anyMatch(codePoint -> "ÄÖÜäöüß".indexOf(codePoint) >= 0)) {
            return "de";
        }
        return "en";
    }

    private String buildInstruction(String targetLanguage, TkGenerationProperties.DashScope dashScope) {
        String baseInstruction = getConfig(KEY_INSTRUCTION, dashScope.getInstruction());
        String languageInstruction = TkLanguageSupport.ttsInstruction(targetLanguage);
        return StrUtil.isBlank(baseInstruction) ? languageInstruction : baseInstruction + " " + languageInstruction;
    }

    private int resolveTimeoutMillis(TkGenerationProperties.DashScope dashScope) {
        return (dashScope.getTimeoutSeconds() == null ? 120 : dashScope.getTimeoutSeconds()) * 1000;
    }

}
