package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - MiniMax 系统音色选项")
@Data
public class TkMiniMaxVoiceOptionRespVO {

    private String value;
    private String label;
    private String voiceId;
    private String model;
    private String language;
    private String country;
    private String gender;
    private String description;
    private String previewUrl;
    private Boolean favorite;
    private Boolean isDefault;
}
