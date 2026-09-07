package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTranscriptTextVerifyServiceImplTest {

    @Test
    void verifyOnlySendsFullTranscriptAndReturnsVerifiedText() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                assertEquals("原始文案", copywriting);
                assertFalse(prompt.contains("segments"));
                assertFalse(prompt.contains("words"));
                assertFalse(prompt.contains("start"));
                assertFalse(prompt.contains("end"));
                assertTrue(prompt.contains("标点和语义断句是必做项"));
                assertTrue(prompt.contains("原文没有标点时"));
                return "{\"text\":\"原始文案。\"}";
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("原始文案");

        assertEquals("原始文案。", result.getTranscriptText());
    }

    @Test
    void verifyDoesNotBatchByTimelineSegments() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                assertEquals("完整原始文案", copywriting);
                assertFalse(prompt.contains("segments"));
                assertFalse(prompt.contains("words"));
                return "{\"text\":\"完整原始文案。\"}";
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("完整原始文案");

        assertEquals("完整原始文案。", result.getTranscriptText());
    }

    @Test
    void verifyRejectsModelOutputThatAddsLargeContent() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "{\"text\":\"第一段。这里增加了大量模型自己编造的营销内容，不能接受。\"}";
            }
        };

        TkTranscriptTextVerifyServiceImpl service = service(client);

        assertThrows(IllegalStateException.class, () -> service.verify("第一段"));
    }

    @Test
    void verifyParsesJsonWrappedByMarkdownAndExplanatoryText() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "校验结果如下：\n```json\n{\"text\":\"第一段。\"}\n```";
            }
        };

        TkTranscriptTextVerifyResult result = service(client).verify("第一段");

        assertEquals("第一段。", result.getTranscriptText());
    }

    @Test
    void verifyRejectsInvalidJson() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String verifyText(String copywriting, String prompt) {
                return "{\"text\":\"第一段。\"";
            }
        };

        assertThrows(IllegalStateException.class, () -> service(client).verify("第一段"));
    }

    private TkTranscriptTextVerifyServiceImpl service(TkDeepSeekClient client) {
        TkTranscriptTextVerifyServiceImpl service = new TkTranscriptTextVerifyServiceImpl();
        ReflectionTestUtils.setField(service, "deepSeekClient", client);
        return service;
    }

}
