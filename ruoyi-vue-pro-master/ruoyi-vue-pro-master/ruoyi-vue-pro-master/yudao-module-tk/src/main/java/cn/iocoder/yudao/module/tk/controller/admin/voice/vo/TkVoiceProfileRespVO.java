package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TkVoiceProfileRespVO {
    private Long id;
    private String name;
    private String voiceCode;
    private String sourceType;
    private String mimoVoiceMode;
    private String mimoVoicePrompt;
    private String mimoSampleUrl;
    private String tags;
    private Integer sort;
    private String remark;
    private String provider;
    private String model;
    private String sampleFileUrl;
    private String previewFileUrl;
    private String status;
    private Boolean enabled;
    private String language;
    private String errorMessage;
    private LocalDateTime createTime;
}
