package cn.iocoder.yudao.module.tk.service.open.copywriting;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_OPEN_COPYWRITING_AI_FAILED;

@Service
@Slf4j
public class TkOpenCopywritingServiceImpl implements TkOpenCopywritingService {

    @Resource
    private TkDeepSeekClient deepSeekClient;

    @Override
    public String rewrite(String copywriting, String prompt) {
        String normalizedCopywriting = StrUtil.trimToNull(copywriting);
        if (StrUtil.isBlank(normalizedCopywriting)) {
            throw ServiceExceptionUtil.invalidParamException("原始文案不能为空");
        }
        String normalizedPrompt = StrUtil.trimToNull(prompt);
        if (StrUtil.isBlank(normalizedPrompt)) {
            throw ServiceExceptionUtil.invalidParamException("提示词不能为空");
        }
        try {
            String result = StrUtil.trimToNull(deepSeekClient.generateText(normalizedCopywriting, normalizedPrompt));
            if (StrUtil.isBlank(result)) {
                throw new IllegalStateException("DeepSeek returned empty content");
            }
            return result;
        } catch (Exception ex) {
            log.error("[rewrite][DeepSeek copywriting rewrite failed]", ex);
            throw ServiceExceptionUtil.exception(TK_OPEN_COPYWRITING_AI_FAILED);
        }
    }

}
