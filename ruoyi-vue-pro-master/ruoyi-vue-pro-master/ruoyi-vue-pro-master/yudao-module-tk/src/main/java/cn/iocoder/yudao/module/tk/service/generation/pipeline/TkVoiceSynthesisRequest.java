package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TkVoiceSynthesisRequest {

    private String text;
    private String voiceCode;
    private String targetLanguage;
    private String mimoVoiceMode;
    private String mimoVoiceCode;
    private String mimoVoicePrompt;
    private String mimoVoiceSampleUrl;
    private boolean finalSynthesis;

}
