package cn.iocoder.yudao.module.tk.controller.admin.audiopreset;

import cn.iocoder.yudao.module.tk.controller.admin.bgm.TkBgmAssetController;
import cn.iocoder.yudao.module.tk.service.audiopreset.TkAudioAudiencePresetService;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TkAudioSettingsErrorsTest {
    @Test
    void stalePresetErrorReachesClientAsActionableBusinessResponse() throws Exception {
        TkAudioAudiencePresetService service = mock(TkAudioAudiencePresetService.class);
        when(service.get(12L)).thenThrow(new IllegalArgumentException("预设已更新，请刷新重试"));
        TkAudioAudiencePresetController controller = new TkAudioAudiencePresetController();
        ReflectionTestUtils.setField(controller, "presetService", service);
        mvc(controller).perform(get("/tk/audio-audience-preset/get").param("id", "12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("预设已更新，请刷新重试"));
    }

    @Test
    void referencedMusicDeletionErrorExplainsRequiredConfirmation() throws Exception {
        TkBgmAssetService service = mock(TkBgmAssetService.class);
        doThrow(new IllegalArgumentException("音乐仍被预设引用，请确认解除引用"))
                .when(service).deleteUserBgm(88L, false);
        TkBgmAssetController controller = new TkBgmAssetController();
        ReflectionTestUtils.setField(controller, "bgmAssetService", service);
        mvc(controller).perform(delete("/tk/bgm/delete").param("id", "88"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("音乐仍被预设引用，请确认解除引用"));
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TkAudioSettingsExceptionHandler()).build();
    }
}
