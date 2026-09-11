package cn.iocoder.yudao.module.tk.service.open.ocr;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkAiImageInput;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Base64;

import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_OPEN_OCR_AI_FAILED;

@Service
@Slf4j
public class TkOpenOcrServiceImpl implements TkOpenOcrService {

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final String OCR_PROMPT = "请识别图片中的全部文字，只返回识别出的文字，不要解释，不要使用 Markdown。";
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList("image/jpeg", "image/png", "image/webp")));

    @Resource
    private TkGeminiClient geminiClient;

    @Override
    public String recognize(MultipartFile file) {
        validate(file);
        try {
            String contentType = file.getContentType().toLowerCase();
            String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
            String result = StrUtil.trimToNull(geminiClient.generateText(OCR_PROMPT,
                    Collections.singletonList(new TkAiImageInput(contentType, base64Data))));
            if (StrUtil.isBlank(result)) {
                throw new IllegalStateException("OCR returned empty content");
            }
            return result;
        } catch (Exception ex) {
            log.error("[recognize][image OCR failed]", ex);
            throw ServiceExceptionUtil.exception(TK_OPEN_OCR_AI_FAILED);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("图片文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ServiceExceptionUtil.invalidParamException("图片文件不能超过 20MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw ServiceExceptionUtil.invalidParamException("仅支持 JPG、PNG、WEBP 图片");
        }
    }

}
