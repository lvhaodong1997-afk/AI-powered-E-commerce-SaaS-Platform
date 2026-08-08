package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TkDashScopeVideoAnalysisClientTest {

    @Test
    void usesDedicatedVideoApiKeyConfig() {
        assertEquals("video-api-key", TkDashScopeVideoAnalysisClient.API_KEY_CONFIG_KEY);
    }

    @Test
    void buildsVideoRequestAndExtractsContent() {
        Map<String, Object> body = TkDashScopeVideoAnalysisClient.buildRequestBody(
                "qwen3.7-plus", "https://cdn.example/video.mp4", "analysis prompt", 2D, false, 0.2D);
        String json = JsonUtils.toJsonString(body);
        assertEquals("qwen3.7-plus", JsonUtils.parseTree(json).path("model").asText());
        assertEquals("video_url", JsonUtils.parseTree(json).path("messages").path(0)
                .path("content").path(0).path("type").asText());
        assertEquals("https://cdn.example/video.mp4", JsonUtils.parseTree(json).path("messages").path(0)
                .path("content").path(0).path("video_url").path("url").asText());
        assertFalse(JsonUtils.parseTree(json).path("enable_thinking").asBoolean());
        assertEquals("ok", TkDashScopeVideoAnalysisClient.extractContent(JsonUtils.parseTree(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")));
    }
}
