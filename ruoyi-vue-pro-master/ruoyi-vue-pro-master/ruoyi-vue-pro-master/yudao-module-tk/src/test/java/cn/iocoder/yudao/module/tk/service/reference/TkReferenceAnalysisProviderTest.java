package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkReferenceAnalysisProviderTest {

    @Test
    void normalizeDefaultsAndValidatesProvider() {
        assertEquals("GEMINI", TkReferenceAnalysisProvider.normalize(null));
        assertEquals("GEMINI", TkReferenceAnalysisProvider.normalize("gemini"));
        assertEquals("DASHSCOPE_VIDEO", TkReferenceAnalysisProvider.normalize("dashscope_video"));
        assertThrows(ServiceException.class, () -> TkReferenceAnalysisProvider.normalize("unknown"));
    }
}
