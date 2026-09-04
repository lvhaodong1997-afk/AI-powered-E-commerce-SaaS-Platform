package cn.iocoder.yudao.module.tk.service.open.copywriting;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenCopywritingServiceImplTest {

    @Test
    void rewriteUsesPromptToGenerateNewCopywritingFromOriginalCopywriting() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String generateText(String copywriting, String prompt) {
                assertEquals("原始文案", copywriting);
                assertEquals("改写为口语化短句", prompt);
                return "新的文案";
            }
        };
        TkOpenCopywritingServiceImpl service = service(client);

        assertEquals("新的文案", service.rewrite("  原始文案  ", "  改写为口语化短句  "));
    }

    @Test
    void rewriteRejectsBlankOriginalCopywriting() {
        TkDeepSeekClient client = new TkDeepSeekClient();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service(client).rewrite("  ", "改写要求"));

        assertEquals("原始文案不能为空", error.getMessage());
    }

    @Test
    void rewriteRejectsBlankPrompt() {
        TkDeepSeekClient client = new TkDeepSeekClient();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service(client).rewrite("原始文案", "  "));

        assertEquals("提示词不能为空", error.getMessage());
    }

    @Test
    void rewriteRejectsBlankModelOutput() {
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String generateText(String copywriting, String prompt) {
                return "  ";
            }
        };

        ServiceException error = assertThrows(ServiceException.class,
                () -> service(client).rewrite("原始文案", "改写要求"));

        assertEquals("文案生成服务暂不可用，请稍后重试", error.getMessage());
    }

    private TkOpenCopywritingServiceImpl service(TkDeepSeekClient client) {
        TkOpenCopywritingServiceImpl service = new TkOpenCopywritingServiceImpl();
        ReflectionTestUtils.setField(service, "deepSeekClient", client);
        return service;
    }
}
