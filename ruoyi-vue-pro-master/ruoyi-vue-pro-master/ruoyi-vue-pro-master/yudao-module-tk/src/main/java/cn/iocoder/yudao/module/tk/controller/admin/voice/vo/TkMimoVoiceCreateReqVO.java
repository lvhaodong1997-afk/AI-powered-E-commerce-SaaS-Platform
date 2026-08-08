package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import lombok.Data;

@Data
public class TkMimoVoiceCreateReqVO {

    private String name;
    private String prompt;
    private String sampleUrl;
    private Boolean consentConfirmed;
    private String tags;

}
