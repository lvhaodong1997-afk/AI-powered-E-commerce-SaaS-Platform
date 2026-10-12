package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationRespVO;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkLanguageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TkManualScriptTranslationService {

    private static final int MAX_SCRIPT_LENGTH = 3000;

    @Resource
    private TkDeepSeekClient deepSeekClient;

    public TkManualScriptTranslationRespVO translate(TkManualScriptTranslationReqVO request) {
        String sourceText = request == null ? null : StrUtil.trim(request.getSourceText());
        if (StrUtil.isBlank(sourceText) || sourceText.length() > MAX_SCRIPT_LENGTH) {
            throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "原始文案需为 1 至 3000 字符");
        }
        String targetLanguage = StrUtil.trim(request.getTargetLanguage());
        if (!TkLanguageSupport.isSupportedTranslationLanguage(targetLanguage)) {
            throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "不支持的目标语言");
        }
        String translatedText;
        try {
            translatedText = StrUtil.trim(deepSeekClient.translateText(sourceText, targetLanguage));
        } catch (IllegalStateException ex) {
            log.warn("[translate] DeepSeek translation failed, language={}", targetLanguage, ex);
            throw new ServiceException(GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(), "翻译失败，请重试或缩短原文后重新翻译");
        }
        if (StrUtil.isBlank(translatedText)) {
            throw new ServiceException(GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(), "翻译服务未返回有效译文，请重试");
        }
        if (translatedText.length() > MAX_SCRIPT_LENGTH) {
            throw new ServiceException(GlobalErrorCodeConstants.ERROR_CONFIGURATION.getCode(), "译文超过 3000 字符，请缩短原文后重新翻译");
        }
        TkManualScriptTranslationRespVO response = new TkManualScriptTranslationRespVO();
        response.setSourceText(sourceText);
        response.setTargetLanguage(targetLanguage);
        response.setTranslatedText(translatedText);
        return response;
    }
}
