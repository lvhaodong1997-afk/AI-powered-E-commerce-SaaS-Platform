package cn.iocoder.yudao.module.tk.service.voice;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkDashScopeVoiceEnrollmentClientTest {

    @Test
    void extractsCurrentVoiceIdField() {
        assertEquals("cosyvoice-v3.5-plus-tk166-123",
                TkDashScopeVoiceEnrollmentClient.extractVoiceId(JsonUtils.parseTree(
                        "{\"output\":{\"voice_id\":\"cosyvoice-v3.5-plus-tk166-123\"}}")));
    }

    @Test
    void remainsCompatibleWithLegacyVoiceField() {
        assertEquals("cosyvoice-v3.5-plus-tk166-legacy",
                TkDashScopeVoiceEnrollmentClient.extractVoiceId(JsonUtils.parseTree(
                        "{\"output\":{\"voice\":\"cosyvoice-v3.5-plus-tk166-legacy\"}}")));
    }
}
