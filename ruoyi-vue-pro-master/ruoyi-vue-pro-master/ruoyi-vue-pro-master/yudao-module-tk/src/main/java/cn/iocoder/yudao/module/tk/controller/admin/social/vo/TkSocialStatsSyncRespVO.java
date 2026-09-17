package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import lombok.Data;

@Data
public class TkSocialStatsSyncRespVO {
    private boolean accepted;
    private String syncStatus;
    private int nextPollAfterSeconds;
}
