package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTranscriptTextVerifyServiceImplTest {

    @Test
    void verifyKeepsOriginalSegmentTimesAndRebuildsVerifiedTranscript() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                assertEquals("原始文案", copywriting);
                assertFalse(prompt.contains("\"start\""));
                assertFalse(prompt.contains("\"end\""));
                assertFalse(prompt.contains("\"words\""));
                assertTrue(prompt.contains("\"text\":\"第一段\""));
                assertTrue(prompt.contains("标点和语义断句是必做项"));
                assertTrue(prompt.contains("原文没有标点时"));
                return "{\"segments\":["
                        + "{\"index\":0,\"text\":\"第一段正文\"},"
                        + "{\"index\":1,\"text\":\"第二段正文\"}]}";
            }
        };

        TkTranscriptTextVerifyServiceImpl service = service(client);
        TkTranscriptTextVerifyResult result = service.verify("原始文案",
                "[{\"id\":1,\"start\":0.5,\"end\":2.0,\"text\":\"第一段\"},"
                        + "{\"id\":2,\"start\":2.0,\"end\":4.5,\"text\":\"第二段\"}]");

        assertEquals("第一段正文\n第二段正文", result.getTranscriptText());
        JsonNode segments = JsonUtils.parseTree(result.getSegmentsJson());
        assertEquals(0.5D, segments.get(0).path("start").asDouble());
        assertEquals(2.0D, segments.get(0).path("end").asDouble());
        assertEquals("第一段正文", segments.get(0).path("text").asText());
        assertEquals(2.0D, segments.get(1).path("start").asDouble());
        assertEquals(4.5D, segments.get(1).path("end").asDouble());
        assertEquals("第二段正文", segments.get(1).path("text").asText());
    }

    @Test
    void verifyBatchesTextOnlyAndKeepsOriginalTimeline() {
        AtomicInteger calls = new AtomicInteger();
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                assertEquals("完整原始文案", copywriting);
                assertFalse(prompt.contains("\"start\""));
                assertFalse(prompt.contains("\"end\""));
                assertFalse(prompt.contains("\"words\""));
                int call = calls.getAndIncrement();
                int count = countPromptSegments(prompt);
                StringBuilder response = new StringBuilder("{\"segments\":[");
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        response.append(',');
                    }
                    response.append("{\"index\":").append(i)
                            .append(",\"text\":\"").append(promptSegmentText(prompt, i)).append("已校验\"}");
                }
                return response.append("]}").toString();
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("完整原始文案", buildSegments(81));

        assertEquals(5, calls.get());
        JsonNode segments = JsonUtils.parseTree(result.getSegmentsJson());
        assertEquals(81, segments.size());
        assertEquals(0.1D, segments.get(0).path("start").asDouble());
        assertEquals(0.2D, segments.get(0).path("end").asDouble());
        assertEquals("词0", segments.get(0).path("words").get(0).path("text").asText());
        assertEquals("原文0已校验", segments.get(0).path("text").asText());
        assertEquals(80.1D, segments.get(80).path("start").asDouble());
        assertEquals(80.2D, segments.get(80).path("end").asDouble());
        assertEquals("词80", segments.get(80).path("words").get(0).path("text").asText());
        assertEquals("原文80已校验", segments.get(80).path("text").asText());
    }

    @Test
    void verifyRejectsModelOutputThatChangesSegmentOrderOrCount() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "{\"segments\":[{\"id\":2,\"text\":\"第二段\"}]}";
            }
        };

        TkTranscriptTextVerifyServiceImpl service = service(client);

        assertThrows(IllegalStateException.class, () -> service.verify("原始文案",
                "[{\"id\":1,\"start\":0,\"end\":1,\"text\":\"第一段\"},"
                        + "{\"id\":2,\"start\":1,\"end\":2,\"text\":\"第二段\"}]"));
    }

    @Test
    void verifyRejectsModelOutputThatAddsLargeContent() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "{\"segments\":[{\"id\":1,\"text\":\"第一段。这里增加了大量模型自己编造的营销内容，不能接受。\"}]}";
            }
        };

        TkTranscriptTextVerifyServiceImpl service = service(client);

        assertThrows(IllegalStateException.class, () -> service.verify("第一段",
                "[{\"id\":1,\"start\":0,\"end\":1,\"text\":\"第一段\"}]"));
    }

    @Test
    void verifyRetriesInvalidJsonWithSmallerBatchesAndKeepsTimeline() {
        AtomicInteger calls = new AtomicInteger();
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                if (calls.getAndIncrement() == 0) {
                    return "{\"segments\":[{\"index\":0,\"text\":\"第一段\"}";
                }
                assertEquals(1, countPromptSegments(prompt));
                String text = prompt.contains("第二段") ? "第二段。" : "第一段。";
                return "{\"segments\":[{\"index\":0,\"text\":\"" + text + "\"}]}";
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("完整原始文案",
                "[{\"id\":1,\"start\":0.5,\"end\":2.0,\"words\":[{\"text\":\"第一\"}],\"text\":\"第一段\"},"
                        + "{\"id\":2,\"start\":2.0,\"end\":4.5,\"words\":[{\"text\":\"第二\"}],\"text\":\"第二段\"}]");

        assertEquals(3, calls.get());
        JsonNode segments = JsonUtils.parseTree(result.getSegmentsJson());
        assertEquals(2, segments.size());
        assertEquals(0.5D, segments.get(0).path("start").asDouble());
        assertEquals(2.0D, segments.get(0).path("end").asDouble());
        assertEquals("第一段。", segments.get(0).path("text").asText());
        assertEquals("第二段。", segments.get(1).path("text").asText());
    }

    @Test
    void verifyParsesJsonWrappedByMarkdownAndExplanatoryText() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "校验结果如下：\n```json\n{\"segments\":[{\"index\":0,\"text\":\"第一段。\"}]}\n```";
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("第一段",
                "[{\"id\":1,\"start\":0,\"end\":1,\"text\":\"第一段\"}]");

        assertEquals("第一段。", result.getTranscriptText());
    }

    private TkTranscriptTextVerifyServiceImpl service(TkDeepSeekClient client) {
        TkTranscriptTextVerifyServiceImpl service = new TkTranscriptTextVerifyServiceImpl();
        ReflectionTestUtils.setField(service, "deepSeekClient", client);
        return service;
    }

    private String buildSegments(int count) {
        StringBuilder segments = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                segments.append(',');
            }
            segments.append("{\"id\":").append(i)
                    .append(",\"start\":").append(i).append(".1")
                    .append(",\"end\":").append(i).append(".2")
                    .append(",\"words\":[{\"start\":").append(i).append(".1")
                    .append(",\"end\":").append(i).append(".2,\"text\":\"词").append(i)
                    .append("\"}],\"text\":\"原文").append(i).append("\"}");
        }
        return segments.append(']').toString();
    }

    private int countPromptSegments(String prompt) {
        int count = 0;
        int offset = prompt.indexOf("本批分段文字输入");
        if (offset < 0) {
            return 0;
        }
        while ((offset = prompt.indexOf("\"index\"", offset)) >= 0) {
            count++;
            offset += 7;
        }
        return count;
    }

    private String promptSegmentText(String prompt, int expectedIndex) {
        int offset = prompt.indexOf("本批分段文字输入");
        for (int i = 0; i <= expectedIndex; i++) {
            int marker = prompt.indexOf("\"text\":\"", offset);
            int start = marker + "\"text\":\"".length();
            int end = prompt.indexOf('"', start);
            if (i == expectedIndex) {
                return prompt.substring(start, end);
            }
            offset = end + 1;
        }
        return "";
    }

}
