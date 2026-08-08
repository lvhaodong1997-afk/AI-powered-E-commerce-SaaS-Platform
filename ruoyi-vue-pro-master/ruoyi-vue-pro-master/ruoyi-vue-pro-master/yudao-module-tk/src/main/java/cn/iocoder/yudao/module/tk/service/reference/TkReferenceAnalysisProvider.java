package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_REFERENCE_ANALYSIS_PROVIDER_INVALID;

public final class TkReferenceAnalysisProvider {

    public static final String GEMINI = "GEMINI";
    public static final String DASHSCOPE_VIDEO = "DASHSCOPE_VIDEO";

    private TkReferenceAnalysisProvider() {
    }

    public static String normalize(String provider) {
        String value = StrUtil.blankToDefault(provider, GEMINI).trim().toUpperCase();
        if (!GEMINI.equals(value) && !DASHSCOPE_VIDEO.equals(value)) {
            throw exception(TK_REFERENCE_ANALYSIS_PROVIDER_INVALID);
        }
        return value;
    }
}
