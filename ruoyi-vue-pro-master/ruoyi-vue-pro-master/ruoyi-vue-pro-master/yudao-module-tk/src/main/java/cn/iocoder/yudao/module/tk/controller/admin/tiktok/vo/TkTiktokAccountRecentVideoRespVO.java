package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

@Data
public class TkTiktokAccountRecentVideoRespVO {

    private String videoId;
    private Long viewCount;
    private Long createTime;
}
