package cn.iocoder.yudao.module.tk.service.open.ocr;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkAiImageInput;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenOcrServiceImplTest {

    @Test
    void recognizeSendsPngAsVisionInputAndReturnsText() {
        TkGeminiClient client = new TkGeminiClient() {
            @Override
            public String generateText(String prompt, List<TkAiImageInput> images) {
                assertEquals("请识别图片中的全部文字，只返回识别出的文字，不要解释，不要使用 Markdown。", prompt);
                assertEquals(1, images.size());
                assertEquals("image/png", images.get(0).getMimeType());
                assertEquals("cG5n", images.get(0).getBase64Data());
                return "识别出的文字";
            }
        };

        TkOpenOcrServiceImpl service = service(client);

        assertEquals("识别出的文字", service.recognize(new MockMultipartFile(
                "file", "sample.png", "image/png", "png".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void recognizeRejectsEmptyFile() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service(new TkGeminiClient()).recognize(
                        new MockMultipartFile("file", "sample.png", "image/png", new byte[0])));

        assertEquals("图片文件不能为空", error.getMessage());
    }

    @Test
    void recognizeRejectsNonImageFile() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service(new TkGeminiClient()).recognize(
                        new MockMultipartFile("file", "sample.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8))));

        assertEquals("仅支持 JPG、PNG、WEBP 图片", error.getMessage());
    }

    @Test
    void recognizeRejectsImageOverTwentyMegabytes() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service(new TkGeminiClient()).recognize(
                        new MockMultipartFile("file", "sample.png", "image/png", new byte[20 * 1024 * 1024 + 1])));

        assertEquals("图片文件不能超过 20MB", error.getMessage());
    }

    private TkOpenOcrServiceImpl service(TkGeminiClient client) {
        TkOpenOcrServiceImpl service = new TkOpenOcrServiceImpl();
        ReflectionTestUtils.setField(service, "geminiClient", client);
        return service;
    }
}
