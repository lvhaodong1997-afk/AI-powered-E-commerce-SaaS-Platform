package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_DASHSCOPE_VIDEO_CALL_FAILED;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_DASHSCOPE_VIDEO_CONFIG_MISSING;

@Component
public class TkDashScopeVideoAnalysisClient implements TkReferenceAiAnalysisClient {

    private static final String DEFAULT_MODEL = "qwen3.7-plus";
    static final String API_KEY_CONFIG_KEY = "video-api-key";

    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    @Override
    public String provider() {
        return TkReferenceAnalysisProvider.DASHSCOPE_VIDEO;
    }

    @Override
    public TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context) {
        String apiKey = config(API_KEY_CONFIG_KEY, System.getenv("DASHSCOPE_VIDEO_API_KEY"));
        String workspaceId = config("workspace-id", "");
        if (StrUtil.isBlank(apiKey)) {
            throw exception(TK_DASHSCOPE_VIDEO_CONFIG_MISSING, API_KEY_CONFIG_KEY);
        }
        if (StrUtil.isBlank(workspaceId)) {
            throw exception(TK_DASHSCOPE_VIDEO_CONFIG_MISSING, "workspace-id");
        }
        if (StrUtil.isBlank(context.getResolvedVideoUrl())) {
            throw exception(TK_DASHSCOPE_VIDEO_CALL_FAILED, "resolved video URL is empty");
        }
        String model = config("video-model", DEFAULT_MODEL);
        double fps = doubleConfig("video-fps", 2D);
        if (fps < 0.1D || fps > 10D) {
            throw exception(TK_DASHSCOPE_VIDEO_CONFIG_MISSING, "video-fps must be between 0.1 and 10");
        }
        Map<String, Object> body = buildRequestBody(model, context.getResolvedVideoUrl(), context.getPrompt(), fps,
                booleanConfig("video-enable-thinking", false), doubleConfig("video-temperature", 0.2D));
        String url = StrUtil.format("https://{}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions",
                workspaceId.trim());
        int timeout = intConfig("video-timeout-seconds", 300) * 1000;
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .body(JsonUtils.toJsonString(body))
                .execute()) {
            String responseBody = response.body();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw exception(TK_DASHSCOPE_VIDEO_CALL_FAILED,
                        StrUtil.format("HTTP {}, request-id {}, {}", response.getStatus(),
                                response.header("x-request-id"), StrUtil.maxLength(responseBody, 512)));
            }
            String content = extractContent(JsonUtils.parseTree(responseBody));
            if (StrUtil.isBlank(content)) {
                throw exception(TK_DASHSCOPE_VIDEO_CALL_FAILED, "empty assistant content");
            }
            return new TkReferenceAiAnalysisResult(provider(), model, content);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(TK_DASHSCOPE_VIDEO_CALL_FAILED, StrUtil.maxLength(ex.getMessage(), 512));
        }
    }

    static Map<String, Object> buildRequestBody(String model, String videoUrl, String prompt, double fps,
                                                 boolean enableThinking, double temperature) {
        Map<String, Object> videoUrlValue = new LinkedHashMap<>();
        videoUrlValue.put("url", videoUrl);
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("type", "video_url");
        video.put("video_url", videoUrlValue);
        video.put("fps", fps);
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("text", prompt);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", Arrays.asList(video, text));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", Arrays.asList(message));
        body.put("enable_thinking", enableThinking);
        body.put("temperature", temperature);
        return body;
    }

    static String extractContent(JsonNode response) {
        return response.path("choices").path(0).path("message").path("content").asText();
    }

    private String config(String key, String fallback) {
        return apiKeyConfigService.getValueOrDefault(TkApiKeyProviderEnum.DASHSCOPE.getProvider(), key, fallback);
    }

    private int intConfig(String key, int fallback) {
        try {
            return Integer.parseInt(config(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double doubleConfig(String key, double fallback) {
        try {
            return Double.parseDouble(config(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean booleanConfig(String key, boolean fallback) {
        return Boolean.parseBoolean(config(key, String.valueOf(fallback)));
    }
}
