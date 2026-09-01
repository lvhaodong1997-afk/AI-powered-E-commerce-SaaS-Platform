package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - TK 音频导出任务创建 Request VO")
@Data
public class TkAudioExportTaskCreateReqVO {

    private Long companyId;

    @NotBlank(message = "请求标识不能为空")
    @Size(max = 64, message = "请求标识不能超过 64 个字符")
    private String requestId;

    @NotBlank(message = "音频文案不能为空")
    @Size(max = 3000, message = "音频文案不能超过 3000 个字符")
    private String scriptText;

    private String ttsProvider;
    private String voiceCode;
    private Long voiceProfileId;
    private String mimoVoiceMode;
    private String mimoVoiceCode;
    private String mimoVoicePrompt;
    private String mimoVoiceSampleUrl;
    private String targetLanguage;
}
