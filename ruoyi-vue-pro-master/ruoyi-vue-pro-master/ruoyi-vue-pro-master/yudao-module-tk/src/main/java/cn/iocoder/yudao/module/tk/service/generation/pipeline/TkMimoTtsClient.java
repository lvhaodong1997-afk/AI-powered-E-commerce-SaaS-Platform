package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.io.FileUtil;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TkMimoTtsClient implements TkVoiceTtsClient {

    private static final String KEY_API_KEY = "api-key";
    private static final String KEY_BASE_URL = "base-url";
    private static final String KEY_PRESET_MODEL = "preset-model";
    private static final String KEY_VOICE_DESIGN_MODEL = "voice-design-model";
    private static final String KEY_VOICE_CLONE_MODEL = "voice-clone-model";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_TIMEOUT_SECONDS = "timeout-seconds";
    private static final String KEY_DEFAULT_VOICE = "default-voice";
    private static final String EXACT_NARRATION_INSTRUCTION =
            "Read the provided narration text exactly. Do not rewrite, summarize, expand, omit, translate, "
                    + "or replace any words. The generated audio must match the narration text word for word.";

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    @Override
    public String provider() {
        return TkTtsProviderEnum.MIMO;
    }

    @Override
    public String audioFormat() {
        return getConfig(KEY_FORMAT, generationProperties.getMimo().getFormat());
    }

    @Override
    public byte[] synthesize(TkVoiceSynthesisRequest request) {
        TkGenerationProperties.Mimo mimo = generationProperties.getMimo();
        String apiKey = getConfig(KEY_API_KEY, mimo.getApiKey());
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("未配置 MiMo api-key，请维护 tk_api_key_config 或 tk.generation.mimo.api-key");
        }

        String url = resolveRequestUrl(getConfig(KEY_BASE_URL, mimo.getBaseUrl()));
        String model = resolveModel(request, mimo);
        Map<String, Object> payload = buildRequest(request, model, mimo);
        try (HttpResponse response = HttpRequest.post(url)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(resolveTimeoutMillis(mimo))
                .body(JsonUtils.toJsonString(payload))
                .execute()) {
            String body = response.body();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("MiMo TTS 调用失败，HTTP {}: {}", response.getStatus(), body));
            }
            JsonNode root = JsonUtils.parseTree(body);
            JsonNode audioData = root.path("choices").path(0).path("message").path("audio").path("data");
            if (audioData.isMissingNode() || StrUtil.isBlank(audioData.asText())) {
                throw new IllegalStateException("MiMo TTS 未返回 choices[0].message.audio.data: " + body);
            }
            return Base64.getDecoder().decode(audioData.asText());
        }
    }

    Map<String, Object> buildRequest(TkVoiceSynthesisRequest request) {
        return buildRequest(request, resolveModel(request, generationProperties.getMimo()), generationProperties.getMimo());
    }

    private Map<String, Object> buildRequest(TkVoiceSynthesisRequest request, String model, TkGenerationProperties.Mimo mimo) {
        String mode = resolveEffectiveMode(request);
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", audioFormat());
        if (TkMimoVoiceModeEnum.PRESET.equals(mode)) {
            audio.put("voice", StrUtil.blankToDefault(request == null ? null : request.getMimoVoiceCode(),
                    getConfig(KEY_DEFAULT_VOICE, mimo.getDefaultVoice())));
        } else if (TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mode)) {
            audio.put("optimize_text_preview", false);
        } else if (TkMimoVoiceModeEnum.VOICE_CLONE.equals(mode)) {
            audio.put("voice", resolveVoiceCloneDataUrl(request));
        }

        List<Map<String, String>> messages = new ArrayList<>();
        String instruction = resolveUserInstruction(request, mode);
        if (StrUtil.isNotBlank(instruction)) {
            messages.add(message("user", instruction));
        }
        messages.add(message("assistant", StrUtil.blankToDefault(request == null ? null : request.getText(), "")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("audio", audio);
        return payload;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String resolveUserInstruction(TkVoiceSynthesisRequest request, String mode) {
        if (TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mode)) {
            return StrUtil.blankToDefault(request == null ? null : request.getMimoVoicePrompt(), defaultVoiceDesignPrompt())
                    + "\n" + EXACT_NARRATION_INSTRUCTION;
        }
        String prompt = request == null ? null : request.getMimoVoicePrompt();
        if (StrUtil.isNotBlank(prompt)) {
            return prompt + "\n" + EXACT_NARRATION_INSTRUCTION;
        }
        return defaultVoiceStylePrompt(request == null ? null : request.getTargetLanguage())
                + "\n" + EXACT_NARRATION_INSTRUCTION;
    }

    private String resolveModel(TkVoiceSynthesisRequest request, TkGenerationProperties.Mimo mimo) {
        String mode = resolveEffectiveMode(request);
        if (TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mode)) {
            return getConfig(KEY_VOICE_DESIGN_MODEL, mimo.getVoiceDesignModel());
        }
        if (TkMimoVoiceModeEnum.VOICE_CLONE.equals(mode)) {
            return getConfig(KEY_VOICE_CLONE_MODEL, mimo.getVoiceCloneModel());
        }
        return getConfig(KEY_PRESET_MODEL, mimo.getPresetModel());
    }

    private String resolveEffectiveMode(TkVoiceSynthesisRequest request) {
        return TkMimoVoiceModeEnum.normalize(request == null ? null : request.getMimoVoiceMode());
    }

    private String resolveVoiceCloneDataUrl(TkVoiceSynthesisRequest request) {
        String sampleUrl = StrUtil.blankToDefault(request == null ? null : request.getMimoVoiceSampleUrl(), null);
        if (StrUtil.isBlank(sampleUrl)) {
            throw new IllegalStateException("MiMo 音色复刻模式需要传入样本音频地址");
        }
        if (sampleUrl.startsWith("data:")) {
            return sampleUrl;
        }
        try (HttpResponse response = HttpRequest.get(sampleUrl).execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("MiMo 样本音频下载失败，HTTP {}: {}", response.getStatus(), response.body()));
            }
            byte[] bytes = response.bodyBytes();
            String contentType = StrUtil.blankToDefault(response.header("Content-Type"), detectMimeType(sampleUrl));
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }
    }

    private String defaultVoiceDesignPrompt() {
        return "Use a natural, clear, and confident short-video voice.";
    }

    private String defaultVoiceStylePrompt(String targetLanguage) {
        return StrUtil.isNotBlank(targetLanguage)
                ? TkLanguageSupport.ttsInstruction(targetLanguage)
                : "Use a natural, clear, and confident short-video voice.";
    }

    private String getConfig(String key, String defaultValue) {
        return apiKeyConfigService.getValueOrDefault(TkApiKeyProviderEnum.MIMO.getProvider(), key, defaultValue);
    }

    private String resolveRequestUrl(String baseUrl) {
        String url = StrUtil.blankToDefault(baseUrl, generationProperties.getMimo().getBaseUrl());
        if (url.contains("/chat/completions")) {
            return url;
        }
        return url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
    }

    private String detectMimeType(String sampleUrl) {
        String ext = FileUtil.extName(sampleUrl).toLowerCase();
        if ("mp3".equals(ext)) {
            return "audio/mpeg";
        }
        if ("m4a".equals(ext)) {
            return "audio/mp4";
        }
        return "audio/wav";
    }

    private int resolveTimeoutMillis(TkGenerationProperties.Mimo mimo) {
        return (mimo.getTimeoutSeconds() == null ? 120 : mimo.getTimeoutSeconds()) * 1000;
    }
}
