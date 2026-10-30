package cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class TkAudioAudiencePresetSaveReqVO {
    private Long id;
    private Integer version;
    @NotBlank private String audienceCode;
    private String ttsProvider;
    private Long voiceId;

    @Size(max = 160)
    private String voiceCode;

    private Long voiceProfileId;

    @Size(max = 32)
    private String mimoVoiceMode;

    @Size(max = 160)
    private String mimoVoiceCode;

    @Size(max = 2000)
    private String mimoVoicePrompt;

    @Size(max = 1024)
    private String mimoVoiceSampleUrl;

    private Boolean bgmEnabled;
    private Long bgmAssetId;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double bgmVolume;
}
