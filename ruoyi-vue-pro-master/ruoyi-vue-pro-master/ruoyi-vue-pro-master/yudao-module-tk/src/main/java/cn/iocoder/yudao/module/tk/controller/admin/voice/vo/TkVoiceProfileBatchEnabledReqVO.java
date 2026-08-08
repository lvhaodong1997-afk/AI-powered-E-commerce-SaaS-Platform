package cn.iocoder.yudao.module.tk.controller.admin.voice.vo;

import lombok.Data;

import java.util.List;

@Data
public class TkVoiceProfileBatchEnabledReqVO {

    private List<Long> ids;
    private Boolean enabled;

}
