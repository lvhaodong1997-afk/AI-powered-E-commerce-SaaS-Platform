package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 配音音色试听 Request VO")
@Data
public class TkVoicePreviewReqVO {

    @Schema(description = "音色供应商：DASHSCOPE / MIMO")
    private String ttsProvider;

    @Schema(description = "DashScope 系统音色编码")
    @JsonAlias({"voice_code", "video_id"})
    private String voiceCode;

    @Schema(description = "租户自定义音色编号", example = "12")
    private Long voiceProfileId;

    @Schema(description = "文案和配音目标语言")
    @JsonAlias({"target_language", "language"})
    private String targetLanguage;

    @Schema(description = "MiMo 音色模式：PRESET / VOICE_DESIGN / VOICE_CLONE")
    private String mimoVoiceMode;

    @Schema(description = "MiMo 预置音色编码")
    private String mimoVoiceCode;

    @Schema(description = "MiMo 音色设计描述")
    private String mimoVoicePrompt;

    @Schema(description = "MiMo 音色复刻样本音频地址")
    private String mimoVoiceSampleUrl;

}
