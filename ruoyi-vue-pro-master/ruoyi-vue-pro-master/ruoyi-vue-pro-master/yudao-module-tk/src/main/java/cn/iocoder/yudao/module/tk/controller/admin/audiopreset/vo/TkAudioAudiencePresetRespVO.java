package cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TkAudioAudiencePresetRespVO extends TkAudioAudiencePresetSaveReqVO {
    private String configStatus;
    private Long companyId;
}
